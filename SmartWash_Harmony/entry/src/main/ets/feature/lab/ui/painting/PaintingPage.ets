/**
 * 今日一画页 — 对齐安卓 feature/lab/ui/painting/PaintingPage.kt。
 *
 * 程序化水墨山水（内核 PaintingGenerator 纯逻辑，SplitMix64 种子确定性可复现）。
 * 版式：PageHeader + 3:4 画作 hero（宣纸底 + 墨色 + 朱砂/青绿点缀）
 * + 信息卡（种子 / 日期 + 换一张 / 保存 / 输入种子）。
 *
 * 交互：换一张 = 新随机种子；保存 = Canvas.toDataURL → 缓存目录临时 PNG →
 * SaveButton 安全控件临时授权 → MediaAssetChangeRequest 写入系统相册
 * （Android 走 MediaStore 应用私有贡献免权限；鸿蒙三方应用无 WRITE_IMAGEVIDEO，
 * 按平台惯例用安全控件方案，点击即授权无需权限弹窗）。
 * 动效：生成过程直接出图、无渐进动画，与安卓一致。
 */

import { fileIo as fs, fileUri } from '@kit.CoreFileKit'
import { common } from '@kit.AbilityKit'
import { photoAccessHelper } from '@kit.MediaLibraryKit'
import { util } from '@kit.ArkTS'
import { DesignColor, DesignDimens, DesignFontFeature, DesignFontSize, DesignFontWeight } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { AppButton, PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import { showToastSafety } from '../../../../utils/ToastUtil'
import { Bird, Boat, House, InkRole, MistBand, MountainLayer, PaintingGenerator,
  PaintingScene, Pt, Seal, Sun, Tree, TreeStyle, WaterLine, paintingSeedFromDate,
  paintingSeedRandom } from '../../core/PaintingGenerator'
import { LabHaptic } from '../../core/LabCommon'

/** 页面文案（对齐安卓 strings_painting.xml） */
class PaintingText {
  static readonly title: string = '今日一画'
  static readonly seedFormat: string = '种子 '
  static readonly todaySuffix: string = ' · 今日'
  static readonly randomLabel: string = '随机种子'
  static readonly hint: string = '同一数字种子，生成同一幅山水'
  static readonly random: string = '换一张'
  static readonly backToday: string = '回到今日'
  static readonly applySeed: string = '应用'
  static readonly seedPlaceholder: string = '种子数字'
  static readonly seedInvalid: string = '请输入有效的数字种子'
  static readonly saveSuccess: string = '已保存到相册'
  static readonly saveFailed: string = '保存失败，请重试'
}

/** ARGB 令牌色替换 alpha 通道（画布 draw 用） */
function withAlpha(color: number, alpha: number): number {
  const a: number = Math.round(Math.min(1, Math.max(0, alpha)) * 255)
  return (((a << 24) | (color & 0x00FFFFFF)) >>> 0)
}

@Builder
export function PaintingBuilder() {
  PaintingPage()
}

@ComponentV2
export struct PaintingPage {
  @Local seed: number = 0
  @Local scene: PaintingScene | null = null
  @Local seedInput: string = ''
  @Local isToday: boolean = true

  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private canvasWidth: number = 0
  private canvasHeight: number = 0
  private todaySeed: number = 0
  private todayYear: number = 0
  private todayMonth: number = 0
  private todayDay: number = 0

  aboutToAppear(): void {
    const now: Date = new Date()
    this.todayYear = now.getFullYear()
    this.todayMonth = now.getMonth() + 1
    this.todayDay = now.getDate()
    this.todaySeed = paintingSeedFromDate(this.todayYear, this.todayMonth, this.todayDay)
    this.applySeed(this.todaySeed)
  }

  /** 换一张：随机六位数字种子 */
  private randomize(): void {
    this.applySeed(paintingSeedRandom())
    LabHaptic.light()
  }

  /** 回到今日：恢复日期种子 */
  private backToToday(): void {
    this.applySeed(this.todaySeed)
    LabHaptic.selection()
  }

  /** 应用种子并重新生成场景（@Monitor('scene') 驱动画布重绘） */
  private applySeed(seed: number): void {
    this.seed = seed
    this.scene = new PaintingGenerator(seed).generate()
    this.isToday = seed === this.todaySeed
  }

  /** 手动输入种子：非法输入 toast 提示并保留输入 */
  private confirmSeedInput(): void {
    const trimmed: string = this.seedInput.trim()
    if (!/^\d{1,15}$/.test(trimmed)) {
      showToastSafety(PaintingText.seedInvalid)
      return
    }
    this.applySeed(parseInt(trimmed))
    LabHaptic.selection()
  }

  @Monitor('scene')
  onSceneChange(): void {
    this.drawPainting()
  }

  /** 画布尺寸变化 → 记录尺寸并重绘（归一化场景按画布映射） */
  private onCanvasAreaChange(oldValue: Area, newValue: Area): void {
    const width: number = Number(newValue.width)
    const height: number = Number(newValue.height)
    if (width <= 0 || height <= 0) {
      return
    }
    this.canvasWidth = width
    this.canvasHeight = height
    this.drawPainting()
  }

  // ---------- 渲染（分层顺序与安卓 drawPainting 一致） ----------

  /** 墨色角色 → 设计令牌色（宣纸底 / 淡中浓墨 / 朱砂 / 青绿） */
  private inkOf(role: InkRole): number {
    switch (role) {
      case InkRole.INK_PALE:
        return DesignColor.TEXT_TERTIARY
      case InkRole.INK_LIGHT:
        return DesignColor.TEXT_SECONDARY
      case InkRole.CINNABAR:
        return DesignColor.DANGER
      case InkRole.VERDIGRIS:
        return DesignColor.PRIMARY
      default:
        return DesignColor.TEXT_PRIMARY
    }
  }

  /** 分层绘制：纸底 → 红日/飞鸟 → 远山 → 云雾 → 近山 → 水面 → 树 → 屋 → 舟 → 朱印 */
  private drawPainting(): void {
    const scene: PaintingScene | null = this.scene
    if (scene === null || this.canvasWidth <= 0 || this.canvasHeight <= 0) {
      return
    }
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    ctx.globalAlpha = 1
    ctx.fillStyle = DesignColor.SURFACE_VARIANT
    ctx.fillRect(0, 0, w, h)

    if (scene.sun !== null) {
      this.drawSun(scene.sun)
    }
    for (const bird of scene.birds) {
      this.drawBird(bird)
    }
    for (const mountain of scene.mountainsFar) {
      this.drawMountain(mountain)
    }
    for (const band of scene.mistBands) {
      this.drawMistBand(band)
    }
    for (const mountain of scene.mountainsNear) {
      this.drawMountain(mountain)
    }
    for (const line of scene.waterLines) {
      this.drawWaterLine(line)
    }
    for (const tree of scene.trees) {
      this.drawTree(tree)
    }
    for (const house of scene.houses) {
      this.drawHouse(house)
    }
    for (const boat of scene.boats) {
      this.drawBoat(boat)
    }
    this.drawSeal(scene.seal)
    ctx.globalAlpha = 1
  }

  /** 路径平滑：以相邻两点中点为终点、当前点为控制点的二次贝塞尔串联 */
  private smoothThrough(ctx: CanvasRenderingContext2D, pts: Pt[], w: number, h: number): void {
    ctx.moveTo(pts[0].x * w, pts[0].y * h)
    for (let i = 1; i < pts.length - 1; i++) {
      const p: Pt = pts[i]
      const next: Pt = pts[i + 1]
      ctx.quadraticCurveTo(p.x * w, p.y * h,
        (p.x + next.x) * 0.5 * w, (p.y + next.y) * 0.5 * h)
    }
    ctx.lineTo(pts[pts.length - 1].x * w, pts[pts.length - 1].y * h)
  }

  /** 山体：山脊折线平滑后填充至山脚，再以略深墨线勾脊 */
  private drawMountain(layer: MountainLayer): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    const pts: Pt[] = layer.ridge
    if (pts.length === 0) {
      return
    }
    const ink: number = this.inkOf(layer.role)
    ctx.beginPath()
    this.smoothThrough(ctx, pts, w, h)
    ctx.lineTo(pts[pts.length - 1].x * w, layer.baseY * h)
    ctx.lineTo(pts[0].x * w, layer.baseY * h)
    ctx.closePath()
    ctx.globalAlpha = layer.alpha
    ctx.fillStyle = ink
    ctx.fill()

    ctx.beginPath()
    this.smoothThrough(ctx, pts, w, h)
    ctx.globalAlpha = Math.min(0.90, layer.alpha + 0.20)
    ctx.strokeStyle = ink
    ctx.lineWidth = Math.max(1, w * 0.003)
    ctx.lineCap = 'round'
    ctx.stroke()
    ctx.globalAlpha = 1
  }

  /** 云雾留白带：宣纸色胶囊横带，半透明叠在远山上形成空间纵深 */
  private drawMistBand(band: MistBand): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    const bandHeight: number = band.height * h
    ctx.globalAlpha = band.alpha
    ctx.fillStyle = DesignColor.SURFACE_VARIANT
    ctx.beginPath()
    this.capsulePath(ctx, band.x * w, band.y * h, band.width * w, bandHeight)
    ctx.fill()
    ctx.globalAlpha = 1
  }

  /**
   * 胶囊（全圆角横带）路径：两端半圆 + 上下直线。
   * 不用 ctx.roundRect（该 API 自 SDK 20 起支持，当前兼容 SDK 17 不可用）。
   */
  private capsulePath(ctx: CanvasRenderingContext2D, x: number, y: number,
    width: number, height: number): void {
    const radius: number = height / 2
    ctx.moveTo(x + radius, y)
    ctx.lineTo(x + width - radius, y)
    ctx.arc(x + width - radius, y + radius, radius, -Math.PI / 2, Math.PI / 2)
    ctx.lineTo(x + radius, y + height)
    ctx.arc(x + radius, y + radius, radius, Math.PI / 2, Math.PI * 1.5)
    ctx.closePath()
  }

  /** 圆角矩形路径（朱印印面用，圆角为边长 16%） */
  private roundedRectPath(ctx: CanvasRenderingContext2D, x: number, y: number,
    width: number, height: number, radius: number): void {
    const r: number = Math.min(radius, width / 2, height / 2)
    ctx.moveTo(x + r, y)
    ctx.lineTo(x + width - r, y)
    ctx.arc(x + width - r, y + r, r, -Math.PI / 2, 0)
    ctx.lineTo(x + width, y + height - r)
    ctx.arc(x + width - r, y + height - r, r, 0, Math.PI / 2)
    ctx.lineTo(x + r, y + height)
    ctx.arc(x + r, y + height - r, r, Math.PI / 2, Math.PI)
    ctx.lineTo(x, y + r)
    ctx.arc(x + r, y + r, r, Math.PI, Math.PI * 1.5)
    ctx.closePath()
  }

  /** 水面淡墨横线 */
  private drawWaterLine(line: WaterLine): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    ctx.globalAlpha = line.alpha
    ctx.strokeStyle = this.inkOf(line.role)
    ctx.lineWidth = Math.max(1, line.width * w)
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(line.x * w, line.y * h)
    ctx.lineTo((line.x + line.length) * w, line.y * h)
    ctx.stroke()
    ctx.globalAlpha = 1
  }

  /** 树：松 = 主干 + 四层外挑枝；点叶 = 主干 + 青绿点簇 */
  private drawTree(tree: Tree): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    const baseX: number = tree.x * w
    const baseY: number = tree.y * h
    const topX: number = baseX + tree.lean * w
    const topY: number = baseY - tree.height * h
    const ink: number = withAlpha(this.inkOf(tree.role), tree.alpha)

    ctx.lineCap = 'round'
    if (tree.style === TreeStyle.PINE) {
      ctx.strokeStyle = ink
      ctx.lineWidth = w * 0.004
      ctx.beginPath()
      ctx.moveTo(baseX, baseY)
      ctx.lineTo(topX, topY)
      ctx.stroke()
      const layers: number = 4
      ctx.lineWidth = w * 0.0028
      for (let i = 1; i <= layers; i++) {
        const t: number = i / (layers + 1)
        const cx: number = baseX + (topX - baseX) * t
        const cy: number = baseY + (topY - baseY) * t
        const half: number = w * 0.022 * (1 - t * 0.55)
        ctx.beginPath()
        ctx.moveTo(cx, cy)
        ctx.lineTo(cx - half, cy - half * 0.45)
        ctx.moveTo(cx, cy)
        ctx.lineTo(cx + half, cy - half * 0.45)
        ctx.stroke()
      }
    } else {
      ctx.strokeStyle = ink
      ctx.lineWidth = w * 0.0035
      ctx.beginPath()
      ctx.moveTo(baseX, baseY)
      ctx.lineTo(topX, topY)
      ctx.stroke()
      ctx.fillStyle = withAlpha(DesignColor.PRIMARY, tree.alpha)
      for (const dot of tree.foliage) {
        ctx.beginPath()
        ctx.arc(dot.x * w, dot.y * h, w * 0.012, 0, 2 * Math.PI)
        ctx.fill()
      }
    }
  }

  /** 屋舍：宣纸留白屋身 + 墨线轮廓 + 挑檐墨顶 + 门缝 */
  private drawHouse(house: House): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    const left: number = (house.x - house.width / 2) * w
    const right: number = (house.x + house.width / 2) * w
    const bottom: number = house.y * h
    const bodyTop: number = bottom - house.height * h
    const roofTop: number = bodyTop - house.roofHeight * h
    const cx: number = house.x * w

    ctx.beginPath()
    ctx.moveTo(left, bottom)
    ctx.lineTo(left, bodyTop)
    ctx.lineTo(right, bodyTop)
    ctx.lineTo(right, bottom)
    ctx.closePath()
    ctx.globalAlpha = 1
    ctx.fillStyle = DesignColor.SURFACE_VARIANT
    ctx.fill()
    ctx.strokeStyle = DesignColor.TEXT_PRIMARY
    ctx.lineWidth = w * 0.0028
    ctx.stroke()

    const overhang: number = house.width * w * 0.18
    ctx.beginPath()
    ctx.moveTo(left - overhang, bodyTop)
    ctx.lineTo(cx, roofTop)
    ctx.lineTo(right + overhang, bodyTop)
    ctx.closePath()
    ctx.globalAlpha = 0.88
    ctx.fillStyle = DesignColor.TEXT_PRIMARY
    ctx.fill()

    ctx.globalAlpha = 1
    ctx.strokeStyle = DesignColor.TEXT_PRIMARY
    ctx.lineWidth = Math.max(1, w * 0.0025)
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(cx, bottom)
    ctx.lineTo(cx, bottom - house.height * h * 0.75)
    ctx.stroke()
  }

  /** 小舟：墨色船身 + 桅杆 + 朱砂帆 */
  private drawBoat(boat: Boat): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    const cx: number = boat.x * w
    const cy: number = boat.y * h
    const halfW: number = boat.width * w / 2
    const hullDepth: number = boat.width * h * 0.10

    ctx.beginPath()
    ctx.moveTo(cx - halfW, cy)
    ctx.quadraticCurveTo(cx, cy + hullDepth, cx + halfW, cy)
    ctx.lineTo(cx + halfW * 0.7, cy - hullDepth * 0.7)
    ctx.lineTo(cx - halfW * 0.7, cy - hullDepth * 0.7)
    ctx.closePath()
    ctx.globalAlpha = 0.90
    ctx.fillStyle = this.inkOf(boat.role)
    ctx.fill()

    const mastTop: number = cy - boat.sailHeight * h
    ctx.globalAlpha = 1
    ctx.strokeStyle = DesignColor.TEXT_PRIMARY
    ctx.lineWidth = Math.max(1, w * 0.0025)
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(cx, cy - hullDepth * 0.7)
    ctx.lineTo(cx, mastTop)
    ctx.stroke()

    ctx.beginPath()
    ctx.moveTo(cx, mastTop)
    ctx.lineTo(cx + boat.width * w * 0.30, mastTop + boat.sailHeight * h * 0.70)
    ctx.lineTo(cx, mastTop + boat.sailHeight * h * 0.70)
    ctx.closePath()
    ctx.globalAlpha = 0.70
    ctx.fillStyle = DesignColor.DANGER
    ctx.fill()
    ctx.globalAlpha = 1
  }

  /** 飞鸟：极简「人」形两笔 */
  private drawBird(bird: Bird): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    const cx: number = bird.x * w
    const cy: number = bird.y * h
    const s: number = bird.size * w
    ctx.globalAlpha = 1
    ctx.strokeStyle = this.inkOf(bird.role)
    ctx.lineWidth = Math.max(1, w * 0.002)
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(cx - s, cy)
    ctx.lineTo(cx, cy - s * 0.7)
    ctx.lineTo(cx + s, cy)
    ctx.stroke()
  }

  /** 红日：朱砂圆（画面唯一暖色锚点，70% 概率出现） */
  private drawSun(sun: Sun): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    ctx.globalAlpha = sun.alpha
    ctx.fillStyle = this.inkOf(sun.role)
    ctx.beginPath()
    ctx.arc(sun.x * this.canvasWidth, sun.y * this.canvasHeight, sun.radius * this.canvasWidth,
      0, 2 * Math.PI)
    ctx.fill()
    ctx.globalAlpha = 1
  }

  /** 朱印：朱砂圆角方 + 朱文「山」字（宣纸色三竖一横） */
  private drawSeal(seal: Seal): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const w: number = this.canvasWidth
    const h: number = this.canvasHeight
    const side: number = seal.size * w
    const left: number = seal.x * w - side / 2
    const top: number = seal.y * h - side / 2

    ctx.globalAlpha = 0.85
    ctx.fillStyle = this.inkOf(seal.role)
    ctx.beginPath()
    this.roundedRectPath(ctx, left, top, side, side, side * 0.16)
    ctx.fill()

    ctx.globalAlpha = 1
    ctx.strokeStyle = DesignColor.SURFACE_VARIANT
    ctx.lineWidth = Math.max(1, side * 0.11)
    ctx.lineCap = 'round'
    const inkLeft: number = left + side * 0.22
    const inkRight: number = left + side * 0.78
    const midX: number = left + side * 0.5
    const inkTop: number = top + side * 0.20
    const inkBottom: number = top + side * 0.80
    ctx.beginPath()
    ctx.moveTo(inkLeft, inkTop + side * 0.18)
    ctx.lineTo(inkLeft, inkBottom)
    ctx.moveTo(midX, inkTop)
    ctx.lineTo(midX, inkBottom)
    ctx.moveTo(inkRight, inkTop + side * 0.18)
    ctx.lineTo(inkRight, inkBottom)
    ctx.moveTo(inkLeft, inkBottom)
    ctx.lineTo(inkRight, inkBottom)
    ctx.stroke()
  }

  // ---------- 保存到相册（SaveButton 安全控件临时授权） ----------

  /**
   * 保存流程：Canvas.toDataURL → base64 解码 → 缓存目录临时 PNG →
   * MediaAssetChangeRequest.createImageAssetRequest → applyChanges 写入相册。
   * 仅可在 SaveButton 点击回调内调用（临时授权随点击生效）。
   */
  private async performSave(): Promise<void> {
    try {
      const context: common.Context = getContext(this)
      const base64: string = this.ctx.toDataURL('image/png')
      const comma: number = base64.indexOf(',')
      const payload: string = comma >= 0 ? base64.substring(comma + 1) : base64
      if (payload.length === 0) {
        showToastSafety(PaintingText.saveFailed)
        return
      }
      const bytes: Uint8Array = new util.Base64Helper().decodeSync(payload)
      // 复制进独立 ArrayBuffer（writeSync 只收 ArrayBuffer；顺带规避视图偏移问题）
      const buffer: ArrayBuffer = new ArrayBuffer(bytes.byteLength)
      new Uint8Array(buffer).set(bytes)
      const path: string = `${context.cacheDir}/smartwash_painting_${Date.now()}.png`
      const file: fs.File = fs.openSync(path,
        fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC)
      try {
        fs.writeSync(file.fd, buffer)
      } finally {
        fs.closeSync(file)
      }
      const uri: string = fileUri.getUriFromPath(path)
      const accessHelper: photoAccessHelper.PhotoAccessHelper =
        photoAccessHelper.getPhotoAccessHelper(context)
      const request: photoAccessHelper.MediaAssetChangeRequest =
        photoAccessHelper.MediaAssetChangeRequest.createImageAssetRequest(context, uri)
      await accessHelper.applyChanges(request)
      try {
        fs.unlinkSync(path)
      } catch (err) {
        // 临时文件清理失败忽略（缓存目录，系统可回收）
      }
      showToastSafety(PaintingText.saveSuccess)
      LabHaptic.success()
    } catch (err) {
      showToastSafety(PaintingText.saveFailed)
      LabHaptic.medium()
    }
  }

  /** 今日日期文案 */
  private dateLabel(): string {
    return `${this.todayYear}年${this.todayMonth}月${this.todayDay}日${PaintingText.todaySuffix}`
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值，
      // 画布经重建后的 onAreaChange 以新令牌重绘（机制见 view/common/ThemeRefresh.ets）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: PaintingText.title })

        Scroll() {
          Column() {
            // 画作 hero：宣纸底 + 程序化水墨，3:4 竖幅
            Canvas(this.ctx)
              .width('100%')
              .aspectRatio(0.75)
              .borderRadius(DesignDimens.RADIUS_XL)
              .clip(true)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .accessibilityDescription('今日一画水墨山水')
              .onAreaChange((oldValue: Area, newValue: Area) => {
                this.onCanvasAreaChange(oldValue, newValue)
              })

            Column() {
              Text(`${PaintingText.seedFormat}${this.seed}`)
                .fontSize(DesignFontSize.AMOUNT_MEDIUM)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .fontFeature(DesignFontFeature.TNUM)
              Text(this.isToday ? this.dateLabel() : PaintingText.randomLabel)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_SECONDARY)
                .margin({ top: DesignDimens.SPACE_XXS })

              Row({ space: DesignDimens.SPACE_SM }) {
                Column() {
                  AppButton({
                    text: PaintingText.random,
                    click: (): void => {
                      this.randomize()
                    }
                  })
                }
                .layoutWeight(1)

                // 保存到相册：安全控件（点击即临时授权，无需权限弹窗）
                SaveButton()
                  .width(132)
                  .height(48)
                  .onClick((event: ClickEvent, result: SaveButtonOnClickResult) => {
                    if (result === SaveButtonOnClickResult.SUCCESS) {
                      this.performSave()
                    } else {
                      showToastSafety(PaintingText.saveFailed)
                      LabHaptic.medium()
                    }
                  })
              }
              .width('100%')
              .margin({ top: DesignDimens.SPACE_MD })
              .alignItems(VerticalAlign.Center)

              Row() {
                Text(PaintingText.backToday)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontColor(DesignColor.TEXT_GREEN)
                  .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM,
                    top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
                  .visibility(this.isToday ? Visibility.None : Visibility.Visible)
                  .onClick(() => {
                    this.backToToday()
                  })
              }
              .width('100%')
              .justifyContent(FlexAlign.End)
              .margin({ top: DesignDimens.SPACE_XS })

              Row({ space: DesignDimens.SPACE_SM }) {
                TextInput({ placeholder: PaintingText.seedPlaceholder, text: this.seedInput })
                  .type(InputType.Number)
                  .layoutWeight(1)
                  .height(48)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .onChange((value: string) => {
                    this.seedInput = value
                  })
                // 「应用」文字热区外扩到 48（对齐安卓 48dp 下限）
                Column() {
                  Text(PaintingText.applySeed)
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor(DesignColor.TEXT_GREEN)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM })
                }
                .constraintSize({ minHeight: 48 })
                .justifyContent(FlexAlign.Center)
                .onClick(() => {
                  this.confirmSeedInput()
                })
              }
              .width('100%')
              .margin({ top: DesignDimens.SPACE_XS })
              .alignItems(VerticalAlign.Center)

              Text(PaintingText.hint)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
                .margin({ top: DesignDimens.SPACE_XS })
            }
            .width('100%')
            .padding(DesignDimens.CARD_PADDING)
            .margin({ top: DesignDimens.SPACE_XXL })
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .borderWidth(1)
            .borderColor(DesignColor.OUTLINE)
            .shadow({
              radius: 12,
              color: DesignColor.SHADOW_COLOR_CARD,
              offsetX: 0,
              offsetY: 2
            })
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XL, bottom: DesignDimens.SECTION_SPACING })
        }
        .width('100%')
        .layoutWeight(1)
        .align(Alignment.TopStart)
        .scrollBar(BarState.Off)
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
  }
}
