/**
 * 指尖流体页 — 对齐安卓 feature/lab/ui/fluid/FluidPage.kt。
 *
 * 清氧设计系统画布 + stable fluids 模拟（内核 FluidSimulator 纯逻辑，CPU 数值解）。
 * 交互：手指划过注入速度 + 染料（划得越快涡旋越猛）；3 套配色预设（令牌色系）；
 * 清空重来重置种子。reduced motion：默认静帧（静态种子画面）+ 用户主动播放/暂停
 * （触觉保留），与 FishPage 降级模式一致。
 *
 * 渲染管线：染料场 → RGBA 缓冲（底色加法叠色）→ createPixelMapSync →
 * drawImage 放大上屏（GPU 双线性插值由系统承担）；帧驱动用 setInterval
 * （约 30fps，ArkTS 无 requestAnimationFrame，与 Fish/Sand 页一致）。
 * 生命周期：NavDestination onHidden 暂停、onShown 恢复，aboutToDisappear 兜底清理。
 */

import { image } from '@kit.ImageKit'
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { PageHeader } from '../../../../view/common/AppComponents'
import { FluidSimulator } from '../../core/FluidSimulator'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'

/** 页面文案（对齐安卓 strings_fluid.xml） */
class FluidText {
  static readonly title: string = '指尖流体'
  static readonly hint: string = '手指划过，搅出彩色漩涡'
  static readonly presetOxygen: string = '清氧水色'
  static readonly presetFoam: string = '泡沫白'
  static readonly presetSand: string = '落沙暖色'
  static readonly reset: string = '清空重来'
  static readonly play: string = '播放'
  static readonly pause: string = '暂停'
}

/** 首次进入顶部提示停留时长（ms） */
const HINT_STAY_MS: number = 3000

/** 帧间隔（ms）：约 30fps */
const FRAME_INTERVAL_MS: number = 33

/** 画布底色：浅色主题取 ON_PRIMARY_SOFT（对齐安卓 baseColorOf 浅色分支；鸿蒙固定亮色模式） */
const CANVAS_BASE_COLOR: number = DesignColor.ON_PRIMARY_SOFT

/** ARGB 令牌色 → [r,g,b] 0..1（渲染层只认 0..1 通道） */
function rgbOf(color: number): number[] {
  const red: number = ((color >>> 16) & 0xFF) / 255
  const green: number = ((color >>> 8) & 0xFF) / 255
  const blue: number = (color & 0xFF) / 255
  return [red, green, blue]
}

/** 预设 → 调色板（全部取自清氧令牌，禁止写死色值；对齐安卓 paletteOf） */
function paletteOf(presetIndex: number): number[][] {
  switch (presetIndex) {
    case 1:
      // 泡沫白：白 + 水色浅底 + 浅绿
      return [rgbOf(DesignColor.SURFACE), rgbOf(DesignColor.WATER_SOFT), rgbOf(DesignColor.PRIMARY_SOFT)]
    case 2:
      // 落沙暖色：暖橙 + 朱红 + 暖沙浅底
      return [rgbOf(DesignColor.ONGOING), rgbOf(DesignColor.DANGER), rgbOf(DesignColor.WARNING_CONTAINER)]
    default:
      // 清氧水色：水蓝 + 自然绿 + 水色浅底
      return [rgbOf(DesignColor.WATER), rgbOf(DesignColor.PRIMARY), rgbOf(DesignColor.WATER_SOFT)]
  }
}

@Builder
export function FluidBuilder() {
  FluidPage()
}

@ComponentV2
export struct FluidPage {
  @Local presetIndex: number = 0
  @Local hintVisible: boolean = true
  @Local manualPlaying: boolean = false

  private sim: FluidSimulator = new FluidSimulator()
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private canvasWidth: number = 0
  private canvasHeight: number = 0
  private timerId: number = -1
  private hintTimerId: number = -1
  private lastFrameMs: number = 0
  private reduceMotion: boolean = labReduceMotion()
  // 染料场 RGBA 缓冲与 PixelMap（画布尺寸变化时重建）
  private dyeBuffer: ArrayBuffer = new ArrayBuffer(0)
  private dyePixels: Uint8Array = new Uint8Array(0)
  private pixelMap: image.PixelMap | null = null
  private pixelMapFailed: boolean = false
  // 触摸轨迹（位移换算注入速度）
  private lastX: number = 0
  private lastY: number = 0

  aboutToAppear(): void {
    this.sim.setPalette(paletteOf(this.presetIndex))
    this.sim.setBaseColor(rgbOf(CANVAS_BASE_COLOR))
    if (!this.reduceMotion) {
      this.startLoop()
      this.hintTimerId = setTimeout(() => {
        this.hintVisible = false
      }, HINT_STAY_MS)
    }
  }

  aboutToDisappear(): void {
    this.clearLoop()
    if (this.hintTimerId !== -1) {
      clearTimeout(this.hintTimerId)
      this.hintTimerId = -1
    }
    this.releasePixelMap()
  }

  private get playing(): boolean {
    return this.reduceMotion ? this.manualPlaying : true
  }

  private clearLoop(): void {
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
  }

  /** 帧驱动：模拟步进 + 染料场上屏（暂停即停止重绘=静帧） */
  private startLoop(): void {
    if (this.timerId !== -1) {
      return
    }
    this.timerId = setInterval(() => {
      const now: number = Date.now()
      const dtMs: number = this.lastFrameMs === 0 ? FRAME_INTERVAL_MS
        : Math.min(50, Math.max(1, now - this.lastFrameMs))
      this.lastFrameMs = now
      this.sim.frame(Math.min(0.05, Math.max(0.001, dtMs / 1000)), this.playing)
      this.drawFluid()
    }, FRAME_INTERVAL_MS)
  }

  /** 画布尺寸变化 → 注入内核并重建染料 PixelMap */
  private onCanvasAreaChange(oldValue: Area, newValue: Area): void {
    const width: number = Number(newValue.width)
    const height: number = Number(newValue.height)
    if (width <= 0 || height <= 0) {
      return
    }
    this.canvasWidth = width
    this.canvasHeight = height
    this.sim.resize(width, height)
    this.rebuildPixelMap()
    this.drawFluid()
  }

  /** 染料场规格变化 → 重建 RGBA 缓冲与 PixelMap（低频路径，允许重建开销） */
  private rebuildPixelMap(): void {
    this.releasePixelMap()
    this.pixelMapFailed = false
    const cols: number = this.sim.dyeColumns
    const rows: number = this.sim.dyeRowCount
    if (cols <= 0 || rows <= 0) {
      return
    }
    this.dyeBuffer = new ArrayBuffer(cols * rows * 4)
    this.dyePixels = new Uint8Array(this.dyeBuffer)
    const options: image.InitializationOptions = {
      size: { width: cols, height: rows },
      pixelFormat: image.PixelMapFormat.RGBA_8888,
      srcPixelFormat: image.PixelMapFormat.RGBA_8888
    }
    try {
      this.pixelMap = image.createPixelMapSync(this.dyeBuffer, options)
    } catch (err) {
      // 低频：创建失败时按纯色画布降级，不打断页面
      this.pixelMap = null
      this.pixelMapFailed = true
    }
  }

  private releasePixelMap(): void {
    const pm: image.PixelMap | null = this.pixelMap
    if (pm !== null) {
      try {
        pm.release()
      } catch (err) {
        // 释放失败忽略（GC 兜底）
      }
    }
    this.pixelMap = null
  }

  /** 染料场 → PixelMap → drawImage 放大上屏（底色已在缓冲内做加法叠色） */
  private drawFluid(): void {
    if (this.canvasWidth <= 0 || this.canvasHeight <= 0) {
      return
    }
    this.ctx.fillStyle = CANVAS_BASE_COLOR
    this.ctx.fillRect(0, 0, this.canvasWidth, this.canvasHeight)
    const pm: image.PixelMap | null = this.pixelMap
    if (pm === null || this.pixelMapFailed) {
      return
    }
    const cols: number = this.sim.dyeColumns
    const rows: number = this.sim.dyeRowCount
    if (cols <= 0 || rows <= 0 || this.dyePixels.length !== cols * rows * 4) {
      return
    }
    this.sim.fillDyeBuffer(this.dyePixels)
    const area: image.PositionArea = {
      pixels: this.dyeBuffer,
      offset: 0,
      stride: cols * 4,
      region: { x: 0, y: 0, size: { width: cols, height: rows } }
    }
    try {
      pm.writePixelsSync(area)
      this.ctx.drawImage(pm, 0, 0, this.canvasWidth, this.canvasHeight)
    } catch (err) {
      // 高频路径静默降级：写缓冲失败仅保留底色，下帧重试
    }
  }

  /** 触摸注入：按下与拖动都产生 splat（位移换算注入速度） */
  private onCanvasTouch(event: TouchEvent): void {
    const touch: TouchObject | undefined = event.touches.length > 0 ? event.touches[0] : undefined
    if (touch === undefined) {
      return
    }
    const x: number = touch.x
    const y: number = touch.y
    if (event.type === TouchType.Down) {
      this.lastX = x
      this.lastY = y
      this.sim.enqueueSplat(x, y, 0, 0)
      LabHaptic.light()
    } else if (event.type === TouchType.Move) {
      const dx: number = x - this.lastX
      const dy: number = y - this.lastY
      this.lastX = x
      this.lastY = y
      this.sim.enqueueSplat(x, y, dx, dy)
    } else {
      this.lastX = 0
      this.lastY = 0
    }
  }

  private selectPreset(index: number): void {
    this.presetIndex = index
    this.sim.setPalette(paletteOf(index))
    LabHaptic.selection()
  }

  private togglePlay(): void {
    this.manualPlaying = !this.manualPlaying
    if (this.manualPlaying) {
      this.startLoop()
    } else {
      this.clearLoop()
    }
    LabHaptic.selection()
  }

  /** 配色预设胶囊（3 套，语义档位对齐安卓 AppPillTabRow） */
  @Builder
  PresetTabs() {
    Row({ space: DesignDimens.SPACE_SM }) {
      ForEach([FluidText.presetOxygen, FluidText.presetFoam, FluidText.presetSand],
        (label: string, index: number) => {
          Text(label)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(this.presetIndex === index ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
            .fontColor(this.presetIndex === index ? DesignColor.ON_PRIMARY : DesignColor.TEXT_PRIMARY)
            .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
              top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
            .borderRadius(DesignDimens.RADIUS_FULL)
            .backgroundColor(this.presetIndex === index ? DesignColor.PRIMARY : DesignColor.SURFACE)
            .borderWidth(1)
            .borderColor(this.presetIndex === index ? DesignColor.PRIMARY : DesignColor.OUTLINE)
            .onClick(() => {
              this.selectPreset(index)
            })
        }, (label: string) => label)
    }
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }

  build() {
    NavDestination() {
      Column() {
        PageHeader({ title: FluidText.title })

        Stack() {
          Canvas(this.ctx)
            .width('100%')
            .height('100%')
            .borderRadius(DesignDimens.CARD_RADIUS)
            .clip(true)
            .onAreaChange((oldValue: Area, newValue: Area) => {
              this.onCanvasAreaChange(oldValue, newValue)
            })
            .onTouch((event: TouchEvent) => {
              this.onCanvasTouch(event)
            })

          if (this.hintVisible) {
            Column() {
              Text(FluidText.hint)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .backgroundColor(DesignColor.SURFACE)
                .borderRadius(DesignDimens.RADIUS_FULL)
                .borderWidth(1)
                .borderColor(DesignColor.OUTLINE)
                .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
                  top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Center)
            .margin({ top: DesignDimens.SPACE_MD })
          }
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_MD })

        Column() {
          this.PresetTabs()
          Row() {
            // 清空重来：重铺静态种子
            Column() {
              Text(FluidText.reset)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_PRIMARY)
            }
            .layoutWeight(1)
            .justifyContent(FlexAlign.Center)
            .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
            .borderRadius(DesignDimens.BUTTON_RADIUS)
            .backgroundColor(DesignColor.SURFACE)
            .borderWidth(1)
            .borderColor(DesignColor.OUTLINE)
            .onClick(() => {
              this.sim.reset()
              this.drawFluid()
              LabHaptic.medium()
            })

            if (this.reduceMotion) {
              Column() {
                Text(this.playing ? FluidText.pause : FluidText.play)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontColor(DesignColor.TEXT_PRIMARY)
              }
              .layoutWeight(1)
              .justifyContent(FlexAlign.Center)
              .constraintSize({ minHeight: DesignDimens.TOUCH_TARGET_MIN })
              .borderRadius(DesignDimens.BUTTON_RADIUS)
              .backgroundColor(DesignColor.SURFACE)
              .borderWidth(1)
              .borderColor(DesignColor.OUTLINE)
              .margin({ left: DesignDimens.SPACE_MD })
              .onClick(() => {
                this.togglePlay()
              })
            }
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_LG })
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      if (this.playing) {
        this.startLoop()
      }
    })
    .onHidden(() => {
      this.clearLoop()
    })
  }
}
