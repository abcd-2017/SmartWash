package com.smartwash.common.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 上传前本地压缩工具：图片过大时先降采样再转 JPEG 压缩后才上传，降低流量与上传失败率。
 *
 * 统一参数：最长边 1280px（头像 512px，[AVATAR_MAX_EDGE]）、JPEG 质量 80；原图足够小
 * （字节数 ≤300KB 且最长边 ≤ 目标 maxEdge）时跳过压缩直传原图——原字节拷贝保留原格式，
 * 扩展名与 MIME 按原格式解析（后端校验魔数与扩展名一致性，文件名不能伪造格式）。
 * 压缩任一环节失败一律兜底回退原图直传，绝不阻断上传链路。
 *
 * 全部 IO 在 Dispatchers.IO；产物统一落 cacheDir 临时文件（不写外部存储），
 * 调用方上传完成后必须调 [UploadImageResult.cleanup] 清理（幂等）。
 */
object ImageCompressUtil {

    /** 统一压缩上限：最长边 1280px（菜谱封面 / 配送照片） */
    const val DEFAULT_MAX_EDGE = 1280

    /** 头像压缩上限：512px */
    const val AVATAR_MAX_EDGE = 512

    /** 统一 JPEG 编码质量 */
    const val DEFAULT_JPEG_QUALITY = 80

    /** 直通阈值：原图 ≤300KB 且最长边 ≤ maxEdge 时跳过压缩直传 */
    const val DEFAULT_SKIP_THRESHOLD_BYTES = 300L * 1024L

    private const val TAG = "ImageCompressUtil"
    private const val TEMP_FILE_PREFIX = "upload"

    /**
     * 直通格式白名单：后端图片接口（封面/头像 RecipeImageValidator 口径）只收 JPG/PNG/WebP，
     * 白名单外的原图（gif/heic/bmp 等）即使足够小也不直传，一律走压缩管线转 JPEG，
     * 否则原样直传必被后端魔数+扩展名一致性校验拒收
     */
    private val PASS_THROUGH_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** 临时文件自增序号（同毫秒内也不重名） */
    private val tempFileSeq = AtomicInteger(0)

    /**
     * 上传图片产物：统一为 cacheDir 临时文件（压缩 = JPEG 重编码；直通 = 原字节拷贝）。
     * [fileName] 为 multipart 文件名（`baseName.ext`），扩展名与字节实际格式严格一致；
     * [compressed] 标记是否发生了压缩（false = 直通 / 兜底原图直传）。
     */
    data class UploadImageResult(
        val file: File,
        val fileName: String,
        val mimeType: String,
        val compressed: Boolean,
    ) {
        /** 上传完成后清理临时文件（幂等；失败静默，cacheDir 由系统兜底回收） */
        fun cleanup() {
            runCatching { file.delete() }
        }
    }

    /**
     * 上传前压缩入口：读文件大小与解码边界判断是否直通；不直通时降采样解码 →
     * EXIF 方向转正 → 最长边压到 maxEdge 内 → JPEG 编码写 cacheDir 临时文件。
     *
     * @param baseName multipart 文件名主干（avatar / cover / 原显示名等），扩展名自动按实际格式追加
     */
    suspend fun prepareUploadImage(
        context: Context,
        uri: Uri,
        baseName: String = TEMP_FILE_PREFIX,
        maxEdge: Int = DEFAULT_MAX_EDGE,
        quality: Int = DEFAULT_JPEG_QUALITY,
        skipThresholdBytes: Long = DEFAULT_SKIP_THRESHOLD_BYTES,
    ): UploadImageResult = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val size = readSourceSize(resolver, uri)
            val bounds = readDecodeBounds(resolver, uri)
            val decodable = bounds.outWidth > 0 && bounds.outHeight > 0
            val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)

            // 直通：字节足够小 + 尺寸不超上限 + 可解码 + 格式在白名单内 → 拷贝原字节，保留原格式与扩展名
            val originalExtension = resolveOriginalExtension(resolver, uri)
            if (size in 1..skipThresholdBytes && decodable && longestEdge in 1..maxEdge &&
                originalExtension in PASS_THROUGH_EXTENSIONS
            ) {
                return@withContext passthrough(context, uri, baseName)
            }

            // 压缩管线：2 的幂降采样 → EXIF 转正 → 最长边精确缩到上限 → 白底铺底 → JPEG 编码
            if (!decodable) error("图片解码边界读取失败（非图片或不受支持的格式）")
            val orientation = readExifOrientation(resolver, uri)
            val options = BitmapFactory.Options().apply { inSampleSize = calcInSampleSize(longestEdge, maxEdge) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("图片解码失败 uri=$uri")
            val rotated = rotateForExif(decoded, orientation)
            // 缩放基准必须用降采样后位图自身的最长边：decoded 的 longestEdge 是原图尺寸，
            // 与 inSampleSize 后的实际尺寸差 sample 倍，混用会把产物再缩一个采样倍
            val rotatedLongest = maxOf(rotated.width, rotated.height)
            val scaled = if (rotatedLongest > maxEdge) {
                Bitmap.createScaledBitmap(
                    rotated,
                    scaledSide(rotated.width, rotatedLongest, maxEdge),
                    scaledSide(rotated.height, rotatedLongest, maxEdge),
                    true,
                )
            } else {
                rotated
            }
            // PNG 等带透明通道的图直接编码 JPEG 透明像素会变黑底，先铺白底再压缩
            val flattened = if (scaled.hasAlpha()) flattenOnWhite(scaled) else scaled
            val file = File(context.cacheDir, tempFileName("jpg"))
            file.outputStream().use { flattened.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            val finalEdge = maxOf(flattened.width, flattened.height)
            // 中间位图可能互相复用同一实例，去重后统一回收
            setOf(decoded, rotated, scaled, flattened).forEach { it.recycle() }
            AppLogger.info(
                TAG,
                "上传前压缩完成 source=${size}B target=${file.length()}B edge=$finalEdge quality=$quality",
            )
            UploadImageResult(file, "$baseName.jpg", "image/jpeg", compressed = true)
        } catch (e: CancellationException) {
            // 取消不作为压缩失败，直接上抛（上传中页面销毁/会话取消场景）
            throw e
        } catch (e: Exception) {
            // 兜底：压缩失败回退原图直传，绝不阻断上传；原图流仍不可读时上抛（与既有上传失败口径一致）
            AppLogger.warn(TAG, "上传前压缩失败，兜底原图直传：${e.message}")
            passthrough(context, uri, baseName)
        }
    }

    /** 直通原图：原字节拷贝进 cacheDir 临时文件（不重编码，保留原格式），文件名/mime 按原格式解析 */
    private fun passthrough(context: Context, uri: Uri, baseName: String): UploadImageResult {
        val resolver = context.contentResolver
        val extension = resolveOriginalExtension(resolver, uri)
        val file = File(context.cacheDir, tempFileName(extension))
        resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: error("原图流不可读 uri=$uri")
        return UploadImageResult(file, "$baseName.$extension", resolveMimeType(resolver, uri, extension), compressed = false)
    }
    // 直通仅发生在 prepareUploadImage 白名单分支，兜底直传不受白名单限制（保留「绝不阻断」语义）

    /** 原图字节数：优先 AssetFileDescriptor 元数据，失败回退 SIZE 列查询，仍失败返回 -1（不满足直通） */
    private fun readSourceSize(resolver: ContentResolver, uri: Uri): Long {
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { return it.length }
        }
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        }
        return -1L
    }

    /** 解码边界（inJustDecodeBounds 不占位图内存；流不可读或格式不支持时 outWidth ≤ 0） */
    private fun readDecodeBounds(resolver: ContentResolver, uri: Uri): BitmapFactory.Options =
        BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options ->
            runCatching {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }

    /** EXIF 方向（androidx.exifinterface 支持 JPEG/PNG/WebP/HEIF；读取失败按无旋转处理） */
    private fun readExifOrientation(resolver: ContentResolver, uri: Uri): Int =
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /** 采样倍数：2 的幂降采样，保证解码后最长边仍不小于 maxEdge（精确缩放交给 createScaledBitmap） */
    private fun calcInSampleSize(longestEdge: Int, maxEdge: Int): Int {
        var sample = 1
        while (longestEdge / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** 等比缩放后的目标边长：最长边精确压到 maxEdge，其余边按比例取整（至少 1px，不放大） */
    private fun scaledSide(side: Int, longestEdge: Int, maxEdge: Int): Int =
        ((side.toLong() * maxEdge) / longestEdge).toInt().coerceIn(1, side)

    /** 按 EXIF 方向把像素转正（压缩产物不再携带方向标记，读取方按原样显示即正确） */
    private fun rotateForExif(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            // 转置 / 逆转置：旋转与镜像复合（部分前置摄像头产生），矩阵先 scale 后 rotate
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /** 透明通道铺白底（JPEG 无 alpha，直接编码透明像素会变黑底） */
    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return output
    }

    /** 原图扩展名：mime 反查优先，回退显示名扩展名（仅收字母数字、防畸形后缀），最终兜底 jpg */
    private fun resolveOriginalExtension(resolver: ContentResolver, uri: Uri): String {
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        if (!mime.isNullOrBlank()) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it.lowercase(Locale.US) }
        }
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val name = if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                    val ext = name?.substringAfterLast('.')?.lowercase(Locale.US)
                    if (!ext.isNullOrBlank() && ext.length <= 5 && ext.all { it in 'a'..'z' || it in '0'..'9' }) {
                        return ext
                    }
                }
            }
        }
        return "jpg"
    }

    /** 直通产物的 MIME：provider 声明优先，回退按扩展名推断，最终兜底 image/jpeg */
    private fun resolveMimeType(resolver: ContentResolver, uri: Uri, extension: String): String {
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        if (!mime.isNullOrBlank()) return mime
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
    }

    /** cacheDir 临时文件名：upload_<时间戳>_<序号>.<ext>（序号防同毫秒撞名） */
    private fun tempFileName(extension: String): String =
        "$TEMP_FILE_PREFIX" + "_${System.currentTimeMillis()}_${tempFileSeq.incrementAndGet()}.$extension"
}
