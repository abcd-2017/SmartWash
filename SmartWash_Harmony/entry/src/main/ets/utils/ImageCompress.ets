// 上传前本地压缩工具 — 对齐安卓 common/utils/ImageCompressUtil.kt
//
// 图片过大时先降采样再转 JPEG 压缩后才上传，降低流量与上传失败率。
// 统一参数：最长边 1280px（头像 512px）、JPEG 质量 80；原图足够小
// （字节数 ≤300KB 且最长边 ≤ 目标 maxEdge）时跳过压缩直传原图——读原字节保留原格式，
// 扩展名与 MIME 按原格式解析（后端校验魔数与扩展名一致性，文件名不能伪造格式）。
// 压缩任一环节失败一律兜底回退原图直传，绝不阻断上传链路。
//
// 鸿蒙端实现差异（对安卓的适配说明）：
// - 鸿蒙 @ohos/axios 的 FormData 只收 ArrayBuffer（无浏览器 DOM），产物统一读入内存，
//   压缩路径同时落 cacheDir 临时文件（upload_<时间戳>_<序号>.jpg），调用方上传完成后
//   调 cleanupUploadTemp 清理；直通路径不产生临时文件（tempPath 为空串）。
// - EXIF 方向经 imageSource.getImageProperty(PropertyKey.ORIENTATION) 读取（API 11+，
//   无 EXIF 时读取失败按不旋转处理），用 pixelMap.rotate/flip 把转正烘焙进像素，
//   PackingOption 默认不写 EXIF，产物即所见方向。
// - 透明像素铺白底（对齐安卓 flattenOnWhite）：JPEG 无 alpha，直接编码透明像素会变黑底，
//   经 readPixelsToBuffer/writeBufferToPixels 逐像素合成白底（仅 RGBA_8888 且非不透明时）。

import { fileIo as fs } from '@kit.CoreFileKit'
import { image } from '@kit.ImageKit'
import { common } from '@kit.AbilityKit'
import { logger } from './Logger'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'imageCompress'

/** 统一压缩上限：最长边 1280px（菜谱封面 / 配送图片，对齐安卓 DEFAULT_MAX_EDGE） */
export const DEFAULT_MAX_EDGE: number = 1280

/** 头像压缩上限：512px（对齐安卓 AVATAR_MAX_EDGE） */
export const AVATAR_MAX_EDGE: number = 512

/** 统一 JPEG 编码质量（对齐安卓 DEFAULT_JPEG_QUALITY） */
const JPEG_QUALITY: number = 80

/** 直通阈值：原图 ≤300KB 且最长边 ≤ maxEdge 时跳过压缩直传（对齐安卓 DEFAULT_SKIP_THRESHOLD_BYTES） */
const PASS_THROUGH_MAX_BYTES: number = 300 * 1024

/** 临时文件名主干（对齐安卓 TEMP_FILE_PREFIX） */
const TEMP_FILE_PREFIX: string = 'upload'

/**
 * 直通格式白名单（对齐安卓 PASS_THROUGH_EXTENSIONS）：后端图片接口（封面/头像）只收
 * JPG/PNG/WebP，白名单外的原图即使足够小也不直传，一律压缩为 JPEG
 */
const PASS_THROUGH_EXTENSIONS: string[] = ['jpg', 'jpeg', 'png', 'webp']

/** 临时文件自增序号（同毫秒内也不重名，对齐安卓 tempFileSeq） */
let tempFileSeq: number = 0

/** MIME → 扩展名（直通产物扩展名解析首选；覆盖后端魔数白名单 JPG/PNG/WebP 及常见图片格式） */
const EXT_BY_MIME: Record<string, string> = {}
EXT_BY_MIME['image/jpeg'] = 'jpg'
EXT_BY_MIME['image/jpg'] = 'jpg'
EXT_BY_MIME['image/png'] = 'png'
EXT_BY_MIME['image/webp'] = 'webp'
EXT_BY_MIME['image/gif'] = 'gif'
EXT_BY_MIME['image/bmp'] = 'bmp'

/** 扩展名 → MIME（直通产物 multipart type 兜底推断） */
const MIME_BY_EXT: Record<string, string> = {}
MIME_BY_EXT['jpg'] = 'image/jpeg'
MIME_BY_EXT['jpeg'] = 'image/jpeg'
MIME_BY_EXT['png'] = 'image/png'
MIME_BY_EXT['webp'] = 'image/webp'
MIME_BY_EXT['gif'] = 'image/gif'
MIME_BY_EXT['bmp'] = 'image/bmp'

/** EXIF 方向标签 → 1..8（对齐 SDK PropertyKey.ORIENTATION 注释的八种取值） */
const ORIENTATION_BY_LABEL: Record<string, number> = {}
ORIENTATION_BY_LABEL['Top-left'] = 1
ORIENTATION_BY_LABEL['Top-right'] = 2
ORIENTATION_BY_LABEL['Bottom-right'] = 3
ORIENTATION_BY_LABEL['Bottom-left'] = 4
ORIENTATION_BY_LABEL['Left-top'] = 5
ORIENTATION_BY_LABEL['Right-top'] = 6
ORIENTATION_BY_LABEL['Right-bottom'] = 7
ORIENTATION_BY_LABEL['Left-bottom'] = 8

/**
 * 上传图片产物（对齐安卓 ImageCompressUtil.UploadImageResult）：
 * - buffer：multipart 文件内容（@ohos/axios FormData 只收 ArrayBuffer，统一读入内存）；
 * - fileName：multipart 文件名（baseName.ext），扩展名与字节实际格式严格一致（后端校验魔数+扩展名）；
 * - compressed：false = 直通 / 兜底原图直传（未重编码）；true = JPEG 重编码产物；
 * - tempPath：压缩产物临时文件绝对路径（cacheDir 下）；直通不产生临时文件，为空串无需清理。
 */
export interface UploadImagePayload {
  fileName: string
  mimeType: string
  buffer: ArrayBuffer
  compressed: boolean
  tempPath: string
}

/**
 * 上传前本地压缩入口：读文件字节数与解码尺寸判断是否直通；不直通时按 maxEdge 降采样解码 →
 * EXIF 方向转正 → 透明像素铺白底 → JPEG 编码写 cacheDir 临时文件。
 *
 * @param uri 相册选择器返回的图片 uri（file://media/...）
 * @param baseName multipart 文件名主干（avatar / cover / 原显示名等），扩展名自动按实际格式追加
 * @param maxEdge 压缩后最长边上限（1280 / 头像 512）
 * @returns 上传产物（始终可上传：压缩失败兜底原图直传；仅当原图字节本身不可读时上抛，
 *          由调用方按既有上传失败口径提示）
 */
export async function prepareUploadImage(uri: string, baseName: string,
  maxEdge: number): Promise<UploadImagePayload> {
  let source: image.ImageSource | null = null
  try {
    source = image.createImageSource(uri)
    const info: image.ImageInfo = await source.getImageInfo()
    const rawWidth: number = info.size.width
    const rawHeight: number = info.size.height
    const longestEdge: number = Math.max(rawWidth, rawHeight)
    // 字节数读不到按 0 处理（不满足直通条件），走压缩管线，失败仍有原图直传兜底
    let sourceBytes: number = 0
    try {
      sourceBytes = fs.statSync(uri).size
    } catch (err) {
      logger.debug(TAG, '原图字节数读取失败，跳过直通判断')
    }

    // 直通：字节足够小 + 尺寸不超上限 + 可解码 + 格式在白名单内 → 读原字节直传，保留原格式与扩展名
    // 白名单外（gif/heic/bmp 等）即使足够小也不直传：后端图片接口（封面/头像）只收 JPG/PNG/WebP，
    // 原样直传必被魔数+扩展名一致性校验拒收，一律走压缩管线转 JPEG
    const originalExtension: string = resolveOriginalExtension(uri, info.mimeType)
    if (sourceBytes > 0 && sourceBytes <= PASS_THROUGH_MAX_BYTES && longestEdge >= 1 && longestEdge <= maxEdge &&
      PASS_THROUGH_EXTENSIONS.indexOf(originalExtension) >= 0) {
      logger.debug(TAG, `原图足够小直通 size=${sourceBytes}B edge=${longestEdge}`)
      return await passthrough(uri, baseName, info.mimeType)
    }

    // 压缩管线：尺寸无效视为非图片（交给兜底直传，由后端拒绝非图片）
    if (rawWidth <= 0 || rawHeight <= 0) {
      throw new Error('图片解码尺寸读取失败（非图片或不受支持的格式）')
    }
    return await compressToJpeg(source, rawWidth, rawHeight, baseName, maxEdge)
  } catch (err) {
    // 兜底：压缩失败回退原图直传，绝不阻断上传；原图流仍不可读时 passthrough 内部上抛
    // （与既有上传失败口径一致，调用方按上传失败提示）
    const summary: string = err instanceof Error ? err.message : ''
    logger.warn(TAG, `上传前压缩失败，兜底原图直传：${summary}`)
    return await passthrough(uri, baseName, '')
  } finally {
    await releaseQuietly(source)
  }
}

/**
 * 删除压缩产物临时文件（上传完成后调用，对齐安卓 UploadImageResult.cleanup）：
 * 幂等；失败静默（cacheDir 由系统兜底回收）；直通路径 tempPath 为空串直接跳过。
 */
export function cleanupUploadTemp(tempPath: string): void {
  if (tempPath.length === 0) {
    return
  }
  try {
    fs.unlinkSync(tempPath)
  } catch (err) {
    logger.debug(TAG, `临时文件清理失败（忽略）：${err instanceof Error ? err.message : ''}`)
  }
}

/**
 * 图片条目 multipart 文件名主干：保留原显示名（uri 末段去扩展名，非法字符替换为下划线），
 * 拿不到时回退 upload_<时间戳>（对齐安卓 DeliverySessionViewModel.resolveImageBaseName）。
 */
export function resolveImageBaseName(uri: string): string {
  const slash: number = uri.lastIndexOf('/')
  const dot: number = uri.lastIndexOf('.')
  const end: number = dot > slash ? dot : uri.length
  const raw: string = uri.substring(slash + 1, end)
  let sanitized: string = ''
  for (let i = 0; i < raw.length; i++) {
    const code: number = raw.charCodeAt(i)
    const isDigit: boolean = code >= 48 && code <= 57
    const isAlpha: boolean = (code >= 65 && code <= 90) || (code >= 97 && code <= 122)
    const isSafe: boolean = code === 46 || code === 95 || code === 45 // . _ -
    sanitized += (isDigit || isAlpha || isSafe) ? raw.charAt(i) : '_'
  }
  return sanitized.length > 0 ? sanitized : `upload_${Date.now()}`
}

/** 直通原图：读原字节（不重编码，保留原格式），文件名/mime 按原格式解析 */
async function passthrough(uri: string, baseName: string, mimeTypeHint: string): Promise<UploadImagePayload> {
  const buffer: ArrayBuffer = await readUriBytes(uri)
  const extension: string = resolveOriginalExtension(uri, mimeTypeHint)
  return {
    fileName: `${baseName}.${extension}`,
    mimeType: resolveMimeType(extension, mimeTypeHint),
    buffer: buffer,
    compressed: false,
    tempPath: ''
  }
}

/** 压缩管线：降采样解码 → EXIF 转正 → 铺白底 → JPEG 编码 → cacheDir 临时文件 */
async function compressToJpeg(source: image.ImageSource, rawWidth: number, rawHeight: number,
  baseName: string, maxEdge: number): Promise<UploadImagePayload> {
  const longestEdge: number = Math.max(rawWidth, rawHeight)
  const orientation: number = await readOrientation(source)
  const pixelMap: image.PixelMap = await decodeScaled(source, rawWidth, rawHeight, longestEdge, maxEdge, orientation)
  const packer: image.ImagePacker = image.createImagePacker()
  try {
    await flattenTransparency(pixelMap)
    const packingOption: image.PackingOption = { format: 'image/jpeg', quality: JPEG_QUALITY }
    const packed: ArrayBuffer = await packer.packing(pixelMap, packingOption)
    const tempPath: string = await writeTempFile(packed, 'jpg')
    logger.info(TAG,
      `上传前压缩完成 baseName=${baseName} target=${packed.byteLength}B quality=${JPEG_QUALITY} temp=${tempPath.length > 0}`)
    return {
      fileName: `${baseName}.jpg`,
      mimeType: 'image/jpeg',
      buffer: packed,
      compressed: true,
      tempPath: tempPath
    }
  } finally {
    await releaseQuietly(packer)
    await releaseQuietly(pixelMap)
  }
}

/** 降采样解码：最长边超限时按比例精确缩放（desiredSize），再按 EXIF 方向把像素转正 */
async function decodeScaled(source: image.ImageSource, rawWidth: number, rawHeight: number,
  longestEdge: number, maxEdge: number, orientation: number): Promise<image.PixelMap> {
  let targetWidth: number = rawWidth
  let targetHeight: number = rawHeight
  if (longestEdge > maxEdge) {
    // 等比缩放：最长边精确压到 maxEdge，其余边按比例取整（至少 1px，不放大）
    const scale: number = maxEdge / longestEdge
    targetWidth = Math.max(1, Math.floor(rawWidth * scale))
    targetHeight = Math.max(1, Math.floor(rawHeight * scale))
  }
  // EXIF 5/6/7/8 场景转正后宽高互换，但最长边不变，降采样目标按原始宽高计算即可
  const desiredSize: image.Size = { width: targetWidth, height: targetHeight }
  // 统一解码为 RGBA_8888：透明像素铺白底的逐像素合成依赖该布局
  const options: image.DecodingOptions = {
    desiredSize: desiredSize,
    desiredPixelFormat: image.PixelMapFormat.RGBA_8888
  }
  const pixelMap: image.PixelMap = await source.createPixelMap(options)
  await applyExifOrientation(pixelMap, orientation)
  return pixelMap
}

/** 按 EXIF 方向把像素转正（压缩产物不再携带方向标记，读取方按原样显示即正确） */
async function applyExifOrientation(pixelMap: image.PixelMap, orientation: number): Promise<void> {
  if (orientation === 2) {
    await pixelMap.flip(true, false)
    return
  }
  if (orientation === 3) {
    await pixelMap.rotate(180)
    return
  }
  if (orientation === 4) {
    await pixelMap.flip(false, true)
    return
  }
  // 转置 / 逆转置（5/7，部分前置摄像头产生）：先水平镜像再旋转，等价安卓矩阵先 rotate 后 scale
  if (orientation === 5) {
    await pixelMap.flip(true, false)
    await pixelMap.rotate(270)
    return
  }
  if (orientation === 7) {
    await pixelMap.flip(true, false)
    await pixelMap.rotate(90)
    return
  }
  if (orientation === 6) {
    await pixelMap.rotate(90)
    return
  }
  if (orientation === 8) {
    await pixelMap.rotate(270)
    return
  }
}

/** EXIF 方向读取（失败/无 EXIF 按 1 不旋转处理，对齐安卓 readExifOrientation） */
async function readOrientation(source: image.ImageSource): Promise<number> {
  try {
    const raw: string = await source.getImageProperty(image.PropertyKey.ORIENTATION)
    return parseOrientation(raw)
  } catch (err) {
    // PNG / 无方向标记 JPEG 无该属性，读取失败属正常
    return 1
  }
}

/** EXIF 方向值解析：兼容数值串（'1'..'8'）与标签串（'Top-left' 等），未知按 1 */
function parseOrientation(raw: string): number {
  const trimmed: string = raw.trim()
  if (trimmed.length === 1) {
    const numeric: number = parseInt(trimmed, 10)
    // NaN 与任何数比较均为 false，天然过滤非数字串
    if (numeric >= 1 && numeric <= 8) {
      return numeric
    }
  }
  const byLabel: number | undefined = ORIENTATION_BY_LABEL[trimmed]
  if (byLabel !== undefined) {
    return byLabel
  }
  return 1
}

/** 透明像素铺白底（JPEG 无 alpha，直接编码透明像素会变黑底，对齐安卓 flattenOnWhite） */
async function flattenTransparency(pixelMap: image.PixelMap): Promise<void> {
  const info: image.ImageInfo = await pixelMap.getImageInfo()
  if (info.pixelFormat !== image.PixelMapFormat.RGBA_8888 || info.alphaType === image.AlphaType.OPAQUE) {
    return
  }
  const width: number = info.size.width
  const height: number = info.size.height
  if (width <= 0 || height <= 0) {
    return
  }
  const bytesPerRow: number = info.stride > 0 ? info.stride : width * 4
  const pixelBytes: number = pixelMap.getPixelBytesNumber()
  const byteLength: number = pixelBytes > 0 ? pixelBytes : bytesPerRow * height
  const buffer: ArrayBuffer = new ArrayBuffer(byteLength)
  await pixelMap.readPixelsToBuffer(buffer)
  const view: Uint8Array = new Uint8Array(buffer)
  // 预乘 alpha（解码默认）：c' = c_premul + 255*(1-a)；非预乘：c' = c*a/255 + 255*(1-a)
  const premultiplied: boolean = info.alphaType !== image.AlphaType.UNPREMUL
  for (let row = 0; row < height; row++) {
    const rowStart: number = row * bytesPerRow
    for (let x = 0; x < width; x++) {
      const offset: number = rowStart + x * 4
      const alpha: number = view[offset + 3]
      if (alpha >= 255) {
        continue
      }
      const fade: number = 255 - alpha
      for (let c = 0; c < 3; c++) {
        const channel: number = view[offset + c]
        const overWhite: number = premultiplied ? fade : Math.floor(channel * alpha / 255)
        const blended: number = channel + overWhite
        view[offset + c] = blended > 255 ? 255 : blended
      }
    }
  }
  await pixelMap.writeBufferToPixels(buffer)
}

/** 读原字节（文件句柄用完即关，对齐既有 uploadAvatar 读法） */
async function readUriBytes(uri: string): Promise<ArrayBuffer> {
  const stat = fs.statSync(uri)
  const file: fs.File = fs.openSync(uri, fs.OpenMode.READ_ONLY)
  try {
    const buffer: ArrayBuffer = new ArrayBuffer(stat.size)
    fs.readSync(file.fd, buffer)
    return buffer
  } finally {
    fs.closeSync(file)
  }
}

/** 原图扩展名：图片元数据 mime 反查优先，回退 uri 显示名后缀（仅收 1-5 位字母数字），最终兜底 jpg */
function resolveOriginalExtension(uri: string, mimeTypeHint: string): string {
  const hint: string = mimeTypeHint.toLowerCase()
  if (hint.length > 0) {
    const byMime: string | undefined = EXT_BY_MIME[hint]
    if (byMime !== undefined) {
      return byMime
    }
  }
  const fromUri: string = extractUriExtension(uri)
  if (fromUri.length > 0) {
    return fromUri
  }
  return 'jpg'
}

/** 直通产物的 MIME：图片元数据声明优先，回退按扩展名推断，最终兜底 image/jpeg */
function resolveMimeType(extension: string, mimeTypeHint: string): string {
  if (mimeTypeHint.length > 0) {
    return mimeTypeHint
  }
  const byExt: string | undefined = MIME_BY_EXT[extension]
  if (byExt !== undefined) {
    return byExt
  }
  return 'image/jpeg'
}

/** uri 末段扩展名提取（仅收 1-5 位字母数字，防畸形后缀/查询参数污染） */
function extractUriExtension(uri: string): string {
  const dot: number = uri.lastIndexOf('.')
  const slash: number = uri.lastIndexOf('/')
  if (dot < 0 || dot < slash || dot === uri.length - 1) {
    return ''
  }
  const ext: string = uri.substring(dot + 1).toLowerCase()
  if (ext.length < 1 || ext.length > 5) {
    return ''
  }
  for (let i = 0; i < ext.length; i++) {
    const code: number = ext.charCodeAt(i)
    const isDigit: boolean = code >= 48 && code <= 57
    const isAlpha: boolean = (code >= 65 && code <= 90) || (code >= 97 && code <= 122)
    if (!isDigit && !isAlpha) {
      return ''
    }
  }
  return ext
}

/** 压缩产物写 cacheDir 临时文件：upload_<时间戳>_<序号>.<ext>（序号防同毫秒撞名）；cache 目录不可用时返回空串 */
async function writeTempFile(buffer: ArrayBuffer, extension: string): Promise<string> {
  const context: common.Context | undefined = getContext() as common.Context | undefined
  const cacheDir: string = context === undefined ? '' : context.cacheDir
  if (cacheDir.length === 0) {
    logger.warn(TAG, 'cache 目录不可用，压缩产物仅驻留内存上传')
    return ''
  }
  tempFileSeq++
  const fileName: string = `${TEMP_FILE_PREFIX}_${Date.now()}_${tempFileSeq}.${extension}`
  const path: string = `${cacheDir}/${fileName}`
  const file: fs.File = fs.openSync(path, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC)
  try {
    fs.writeSync(file.fd, buffer)
  } finally {
    fs.closeSync(file)
  }
  return path
}

/** 释放 image 资源（ImageSource/ImagePacker/PixelMap）：失败静默，不阻断主流程 */
async function releaseQuietly(target: image.ImageSource | image.ImagePacker | image.PixelMap | null): Promise<void> {
  if (target === null) {
    return
  }
  try {
    await target.release()
  } catch (err) {
    logger.debug(TAG, 'image 资源释放失败（忽略）')
  }
}
