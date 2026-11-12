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
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../../constant/DesignSystem'
import { DarkModeStore } from '../../../../constant/DarkModeStore'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { AppPillTabRow, PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import { FluidSimulator } from '../../core/FluidSimulator'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'
import { resColor } from '../../../../utils/ResourceColor'
import { resString } from '../../../../utils/ResourceString'

/** 页面文案（对齐安卓 strings_fluid.xml） */
class FluidText {
  static get title(): string { return resString('lab_toy_fluid') }
  static get hint(): string { return resString('lab_toy_fluid_desc') }
  static get presetOxygen(): string { return resString('lab_fluid_preset_oxygen') }
  static get presetFoam(): string { return resString('lab_fluid_preset_foam') }
  static get presetSand(): string { return resString('lab_fluid_preset_sand') }
  static get reset(): string { return resString('lab_fluid_reset') }
  static get play(): string { return resString('lab_fish_play') }
  static get pause(): string { return resString('lab_breath_pause') }
  static get unsupported(): string { return resString('lab_fluid_unsupported') }
}

/** 首次进入顶部提示停留时长（ms） */
const HINT_STAY_MS: number = 3000

/** 帧间隔（ms）：约 30fps */
const FRAME_INTERVAL_MS: number = 33

/**
 * 画布底色（对齐安卓 baseColorOf）：浅色用深绿 ON_PRIMARY_SOFT #0B3625，
 * 深色用次深表面 SURFACE_VARIANT #262A27——两套主题都保证足够暗，染料加法叠加后才显色。
 * 必须使用点即时求值——模块级 const 会在加载时冻结令牌值，深浅色切换后模拟循环仍拿旧色。
 */
function canvasBaseColor(isDark: boolean): number {
  return isDark ? resColor('SURFACE_VARIANT') : resColor('ON_PRIMARY_SOFT')
}

/** ARGB 令牌色 → [r,g,b] 0..1（渲染层只认 0..1 通道） */
function rgbOf(color: number): number[] {
  const red: number = ((color >>> 16) & 0xFF) / 255
  const green: number = ((color >>> 8) & 0xFF) / 255
  const blue: number = (color & 0xFF) / 255
  return [red, green, blue]
}

/**
 * 预设 → 调色板（全部取自清氧令牌，禁止写死色值；对齐安卓 paletteOf）。
 * 染料是画在深底（浅板 onPrimarySoft / 深板 surfaceVariant）上的加法叠加前景图形，
 * 禁止用「容器浅底」令牌（深色下取深色容器值，对底仅 1.02-1.13:1 不可辨）：
 * 容器系染料槽按 isDark 分流，深板改取配对前景/图形级亮色，逐槽对底核算 ≥1.5:1。
 */
function paletteOf(isDark: boolean, presetIndex: number): number[][] {
  switch (presetIndex) {
    case 1:
      // 泡沫白：浅板白 + 水色浅底 + 浅绿；深板近白前景 + 水色 + 亮绿
      return [
        rgbOf(isDark ? resColor('TEXT_PRIMARY') : resColor('SURFACE')),
        rgbOf(isDark ? resColor('WATER') : resColor('WATER_SOFT')),
        rgbOf(isDark ? resColor('ON_PRIMARY_SOFT') : resColor('PRIMARY_SOFT')),
      ]
    case 2:
      // 落沙暖色：暖橙 + 朱红 + 暖沙浅底；深板第三槽改亮暖橙前景
      return [
        rgbOf(resColor('ONGOING')),
        rgbOf(resColor('DANGER')),
        rgbOf(isDark ? resColor('ON_WARNING_CONTAINER') : resColor('WARNING_CONTAINER')),
      ]
    default:
      // 清氧水色：水蓝 + 自然绿 + 水色浅底；深板第三槽改亮绿前景
      return [
        rgbOf(resColor('WATER')),
        rgbOf(resColor('PRIMARY')),
        rgbOf(isDark ? resColor('ON_PRIMARY_SOFT') : resColor('WATER_SOFT')),
      ]
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
  // 已注入调色板的主题/预设键（主题切换或预设切换时重建，避免每帧分配）
  private paletteKey: string = ''
  // 触摸轨迹（位移换算注入速度）
  private lastX: number = 0
  private lastY: number = 0

  aboutToAppear(): void {
    const isDark: boolean = DarkModeStore.getInstance().dark
    this.sim.setPalette(paletteOf(isDark, this.presetIndex))
    this.sim.setBaseColor(rgbOf(canvasBaseColor(isDark)))
    this.paletteKey = `${isDark ? 'd' : 'l'}_${this.presetIndex}`
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
    // 底色/调色板使用点即时求值：深浅色切换后（ThemeRefreshV2 重建 build）下一帧即取新令牌，
    // 同步回写 sim 内叠色基准，确保染料加法叠色与画布底色一致
    const isDark: boolean = DarkModeStore.getInstance().dark
    const paletteKey: string = `${isDark ? 'd' : 'l'}_${this.presetIndex}`
    if (paletteKey !== this.paletteKey) {
      this.sim.setPalette(paletteOf(isDark, this.presetIndex))
      this.paletteKey = paletteKey
    }
    const base: number = canvasBaseColor(isDark)
    this.sim.setBaseColor(rgbOf(base))
    this.ctx.fillStyle = base
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
    const isDark: boolean = DarkModeStore.getInstance().dark
    this.sim.setPalette(paletteOf(isDark, index))
    this.paletteKey = `${isDark ? 'd' : 'l'}_${index}`
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

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: FluidText.title })

        Stack({ alignContent: Alignment.Center }) {
          Canvas(this.ctx)
            .width('100%')
            .height('100%')
            .borderRadius(DesignDimens.RADIUS_XL)
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
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor($r('app.color.TEXT_PRIMARY'))
                .backgroundColor($r('app.color.SURFACE'))
                .borderRadius(DesignDimens.RADIUS_FULL)
                .borderWidth(1)
                .borderColor($r('app.color.OUTLINE'))
                .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
                  top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Center)
            .margin({ top: DesignDimens.SPACE_MD })
          }

          // GL 失败态兜底：surface 衬底胶囊 + textPrimary 文案（对齐安卓 unsupported 路径）
          if (this.pixelMapFailed) {
            Text(FluidText.unsupported)
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .backgroundColor($r('app.color.SURFACE'))
              .borderRadius(DesignDimens.RADIUS_FULL)
              .borderWidth(1)
              .borderColor($r('app.color.OUTLINE'))
              .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
                top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
          }
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_XL })

        Column() {
          // 配色预设：AppPillTabRow 自带页面左右边距与 48 热区（对齐安卓）
          AppPillTabRow({
            tabs: [FluidText.presetOxygen, FluidText.presetFoam, FluidText.presetSand],
            selectedIndex: this.presetIndex,
            tabSelected: (index: number) => {
              this.selectPreset(index)
            }
          })
          Row() {
            // 清空重来：重铺静态种子
            Column() {
              Text(FluidText.reset)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor($r('app.color.TEXT_PRIMARY'))
            }
            .layoutWeight(1)
            .justifyContent(FlexAlign.Center)
            .constraintSize({ minHeight: 48 })
            .borderRadius(DesignDimens.BUTTON_RADIUS)
            .backgroundColor($r('app.color.SURFACE'))
            .borderWidth(1)
            .borderColor($r('app.color.OUTLINE'))
            .onClick(() => {
              this.sim.reset()
              this.drawFluid()
              LabHaptic.medium()
            })

            if (this.reduceMotion) {
              Column() {
                Text(this.playing ? FluidText.pause : FluidText.play)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
              }
              .layoutWeight(1)
              .justifyContent(FlexAlign.Center)
              .constraintSize({ minHeight: 48 })
              .borderRadius(DesignDimens.BUTTON_RADIUS)
              .backgroundColor($r('app.color.SURFACE'))
              .borderWidth(1)
              .borderColor($r('app.color.OUTLINE'))
              .margin({ left: DesignDimens.SPACE_MD })
              .onClick(() => {
                this.togglePlay()
              })
            }
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          .margin({ top: DesignDimens.SPACE_MD })
          .alignItems(VerticalAlign.Center)
        }
        .width('100%')
        .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_LG })
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
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
