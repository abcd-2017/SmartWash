/**
 * 掌上星图页 — 对齐安卓 feature/lab/ui/starmap/StarMapPage.kt + StarMapViewModel.kt。
 *
 * 全屏 Canvas 暗色沉浸式星空（固定暗色令牌，同安卓 Dark* 系）。交互：
 * - 转动手机环顾夜空（@ohos.sensor 加速度计 + 磁力计 → 旋转矩阵 → 切平面投影）；
 * - 点按星星显示名称 + 星等（取最近星，点空白清除）；
 * - 顶栏夜视模式开关（深红滤镜，保护暗适应）。
 *
 * 降级：传感器不可用（模拟器 / 部分设备）时切到静态星空（正北、仰角 45°）+
 * 常显提示文案，不崩溃。生命周期：传感器订阅与帧驱动跟随页面（onHidden 暂停、
 * onShown 恢复、aboutToDisappear 兜底注销），对齐安卓 DisposableEffect。
 */

import sensor from '@ohos.sensor'
import display from '@ohos.display'
import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight }
  from '../../../../constant/DesignSystem'
import { pathStack } from '../../../../utils/PathStackUtil'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import { CelestialKind, ProjectedStar, StarMapEngine } from '../../core/StarMapEngine'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'

/** 页面文案（对齐安卓 strings_starmap.xml） */
class StarMapText {
  static readonly title: string = '掌上星图'
  static readonly hint: string = '转动手机环顾夜空'
  static readonly sensorUnavailable: string = '传感器不可用，已切换为静态星空'
  static readonly nightVision: string = '夜视模式'
  static readonly magnitudePrefix: string = '星等'
  static readonly back: string = '返回'
}

/** 垂直视场角（度）：60° 接近手机主摄视野（对齐安卓 FOV_DEGREES） */
const FOV_DEGREES: number = 60

/** 点星命中半径（vp）：≥48 触控热区（对齐安卓 TAP_RADIUS_DP） */
const TAP_RADIUS_VP: number = 48

/** 星点闪烁周期（ms）（对齐安卓 TWINKLE_PERIOD_MS） */
const TWINKLE_PERIOD_MS: number = 2400

/** 顶部提示停留时长（ms），超时淡出 */
const HINT_STAY_MS: number = 4000

/** 重绘帧间隔（ms）：约 15fps（星图变化缓慢，省电） */
const FRAME_INTERVAL_MS: number = 66

/** ARGB 数值色 → 画布 rgba() 字符串（alpha 显式传入） */
function rgbaOf(color: number, alpha: number): string {
  const red: number = (color >>> 16) & 0xFF
  const green: number = (color >>> 8) & 0xFF
  const blue: number = color & 0xFF
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

/** 星名散列（单星闪烁相位错峰；ArkTS 无 String.hashCode） */
function starHash(name: string): number {
  let h: number = 0
  for (let i: number = 0; i < name.length; i++) {
    h = (h * 31 + name.charCodeAt(i)) | 0
  }
  return h & 0xFF
}

@Builder
export function StarMapBuilder() {
  StarMapPage()
}

@ComponentV2
export struct StarMapPage {
  /** 传感器状态：-1 未检测 / 1 可用 / 0 不可用（降级静态星空） */
  @Local sensorsState: number = -1
  @Local nightVision: boolean = false
  @Local hintVisible: boolean = true
  @Local selectedName: string = ''
  @Local selectedMagnitude: number = 0

  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(
    new RenderingContextSettings(true))
  private canvasWidth: number = 0
  private canvasHeight: number = 0
  private timerId: number = -1
  private hintTimerId: number = -1
  private reduceMotion: boolean = labReduceMotion()

  /** 无传感器降级矩阵（正北、仰角 45°） */
  private readonly fallbackMatrix: number[] = StarMapEngine.fallbackRotationMatrix()
  private rotationMatrix: number[] = this.fallbackMatrix
  private projected: ProjectedStar[] = []

  // 传感器采样缓存（每次回调即重算姿态）
  private gravity: number[] | null = null
  private magnetic: number[] | null = null
  private accSubscribed: boolean = false
  private magSubscribed: boolean = false

  private accCallback = (data: sensor.AccelerometerResponse) => {
    this.gravity = [data.x, data.y, data.z]
    this.updateRotation()
  }

  private magCallback = (data: sensor.MagneticFieldResponse) => {
    this.magnetic = [data.x, data.y, data.z]
    this.updateRotation()
  }

  aboutToAppear(): void {
    this.startSensors()
    this.scheduleHintHide()
    this.startLoop()
  }

  aboutToDisappear(): void {
    this.stopSensors()
    this.stopLoop()
    if (this.hintTimerId !== -1) {
      clearTimeout(this.hintTimerId)
      this.hintTimerId = -1
    }
  }

  // ==================== 传感器 ====================

  /** 开始采样；不可用（模拟器 / 无磁力计）时标记降级，不崩溃。
   * 权限说明：加速度计需 module.json5 声明 ohos.permission.ACCELEROMETER（已声明）；
   * 磁力计 SensorId.MAGNETIC_FIELD 免权限（SDK @ohos.sensor.d.ts 订阅无 @permission
   * 标注，权限白名单也无此名），勿再为其追加 requestPermissions */
  private startSensors(): void {
    if (this.accSubscribed || this.magSubscribed) {
      return
    }
    const options: sensor.Options = { interval: 'game' }
    let accOk: boolean = false
    let magOk: boolean = false
    try {
      sensor.on(sensor.SensorId.ACCELEROMETER, this.accCallback, options)
      accOk = true
    } catch (err) {
      accOk = false
    }
    try {
      sensor.on(sensor.SensorId.MAGNETIC_FIELD, this.magCallback, options)
      magOk = true
    } catch (err) {
      magOk = false
    }
    if (!accOk || !magOk) {
      // 只订到一路也视为不可用（姿态解算需要两路），按需注销已订阅的部分
      if (accOk) {
        try {
          sensor.off(sensor.SensorId.ACCELEROMETER, this.accCallback)
        } catch (err) {
        }
      }
      if (magOk) {
        try {
          sensor.off(sensor.SensorId.MAGNETIC_FIELD, this.magCallback)
        } catch (err) {
        }
      }
      this.sensorsState = 0
      this.rotationMatrix = this.fallbackMatrix
      return
    }
    this.accSubscribed = accOk
    this.magSubscribed = magOk
    this.sensorsState = 1
  }

  /** 停止采样（重复调用安全） */
  private stopSensors(): void {
    if (this.accSubscribed) {
      try {
        sensor.off(sensor.SensorId.ACCELEROMETER, this.accCallback)
      } catch (err) {
      }
      this.accSubscribed = false
    }
    if (this.magSubscribed) {
      try {
        sensor.off(sensor.SensorId.MAGNETIC_FIELD, this.magCallback)
      } catch (err) {
      }
      this.magSubscribed = false
    }
  }

  /** 两路数据齐了就重算姿态矩阵（按屏幕方向重映射；取不到旋转角按 0） */
  private updateRotation(): void {
    const g: number[] | null = this.gravity
    const m: number[] | null = this.magnetic
    if (g === null || m === null) {
      return
    }
    const matrix: number[] | null = StarMapEngine.computeRotationMatrix(g, m)
    if (matrix === null) {
      return
    }
    let rotation: number = 0
    try {
      rotation = display.getDefaultDisplaySync().rotation
    } catch (err) {
      rotation = 0
    }
    this.rotationMatrix = StarMapEngine.remapForScreenRotation(matrix, rotation)
  }

  // ==================== 帧驱动 / 绘制 ====================

  private startLoop(): void {
    if (this.timerId !== -1) {
      return
    }
    this.timerId = setInterval(() => {
      this.drawFrame()
    }, FRAME_INTERVAL_MS)
  }

  private stopLoop(): void {
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
  }

  private scheduleHintHide(): void {
    // reduced motion：提示常显，取消淡出（规范 §8）
    if (this.reduceMotion || this.hintTimerId !== -1) {
      return
    }
    this.hintTimerId = setTimeout(() => {
      this.hintVisible = false
      this.hintTimerId = -1
    }, HINT_STAY_MS)
  }

  /** 星空帧：重投影（姿态 / 时间变化）→ 底色渐变 → 星点 → 环饰 → 夜视滤镜 */
  private drawFrame(): void {
    const width: number = this.canvasWidth
    const height: number = this.canvasHeight
    if (width <= 0 || height <= 0) {
      return
    }
    const ctx: CanvasRenderingContext2D = this.ctx
    this.projected = StarMapEngine.projectSky(this.rotationMatrix, width, height, FOV_DEGREES,
      Date.now(), 1)

    // 夜空底：暗色令牌纵向微渐变（上深下略亮）
    const gradient = ctx.createLinearGradient(0, 0, 0, height)
    gradient.addColorStop(0, rgbaOf(DesignColor.DARK_BACKGROUND, 1))
    gradient.addColorStop(1, rgbaOf(DesignColor.DARK_GROUP_BACKGROUND, 1))
    ctx.fillStyle = gradient
    ctx.fillRect(0, 0, width, height)

    // 全局闪烁相位（时间驱动，单星相位按名称散列错峰）
    const twinkle: number = (Date.now() % TWINKLE_PERIOD_MS) / TWINKLE_PERIOD_MS

    for (let i: number = 0; i < this.projected.length; i++) {
      const star: ProjectedStar = this.projected[i]
      const isSelected: boolean = star.name === this.selectedName
      let color: number = DesignColor.DARK_TEXT_PRIMARY
      if (this.nightVision) {
        color = DesignColor.DARK_ERROR
      } else if (star.kind === CelestialKind.SUN) {
        color = DesignColor.DARK_WARNING
      } else if (star.kind === CelestialKind.MOON) {
        color = DesignColor.DARK_TEXT_SECONDARY
      } else if (star.kind === CelestialKind.PLANET) {
        color = DesignColor.DARK_WATER
      }
      const phase: number = starHash(star.name) / 255 * Math.PI * 2
      // reduced motion：星点常亮（规范 §8：取消闪烁）
      const twinkleFactor: number = this.reduceMotion
        ? 1 : 0.82 + 0.18 * Math.sin(twinkle * Math.PI * 2 + phase)
      const alpha: number = Math.min(1, Math.max(0, star.alpha * twinkleFactor))

      ctx.globalAlpha = alpha
      ctx.fillStyle = rgbaOf(color, 1)
      ctx.beginPath()
      ctx.arc(star.x, star.y, star.radiusPx, 0, Math.PI * 2, false)
      ctx.fill()

      // 太阳 / 月亮加一圈细环，便于在星点中识别
      if (star.kind === CelestialKind.SUN || star.kind === CelestialKind.MOON) {
        ctx.globalAlpha = alpha * 0.55
        ctx.lineWidth = 1
        ctx.strokeStyle = rgbaOf(color, 1)
        ctx.beginPath()
        ctx.arc(star.x, star.y, star.radiusPx + 3, 0, Math.PI * 2, false)
        ctx.stroke()
      }
      // 选中态：水色描边环（夜视模式跟随红色滤镜）
      if (isSelected) {
        ctx.globalAlpha = 0.95
        ctx.lineWidth = 2
        ctx.strokeStyle = rgbaOf(this.nightVision ? DesignColor.DARK_ERROR
          : DesignColor.DARK_WATER, 1)
        ctx.beginPath()
        ctx.arc(star.x, star.y, star.radiusPx + 6, 0, Math.PI * 2, false)
        ctx.stroke()
      }
    }
    ctx.globalAlpha = 1

    // 夜视模式：整体罩一层深红滤镜（保护暗适应）
    if (this.nightVision) {
      ctx.fillStyle = rgbaOf(DesignColor.DARK_ERROR, 0.12)
      ctx.fillRect(0, 0, width, height)
    }
  }

  /** 点星选中：取最近天体，超出命中半径视为点空白清除 */
  private onCanvasTouch(event: TouchEvent): void {
    if (event.type !== TouchType.Down) {
      return
    }
    const touch: TouchObject | undefined = event.touches.length > 0 ? event.touches[0] : undefined
    if (touch === undefined) {
      return
    }
    const star: ProjectedStar | null = StarMapEngine.findNearest(this.projected, touch.x, touch.y,
      TAP_RADIUS_VP)
    if (star !== null) {
      this.selectedName = star.name
      this.selectedMagnitude = star.magnitude
      LabHaptic.light()
    } else {
      this.selectedName = ''
      this.selectedMagnitude = 0
    }
  }

  private toggleNightVision(): void {
    this.nightVision = !this.nightVision
    LabHaptic.selection()
  }

  /** 顶栏内容色：夜视模式整体转深红 */
  private headerColor(): number {
    return this.nightVision ? DesignColor.DARK_ERROR : DesignColor.DARK_TEXT_PRIMARY
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐，与其他 38 页包法一致）：本页虽固定用 DARK_*
      // readonly 令牌（不随主题回写变化），仍统一包主题重建宿主——色模式翻转时 if 双分支
      // 重建子树，画布重绘与覆盖层直读点保持与其他页同构（机制见 view/common/ThemeRefresh.ets）
      ThemeRefreshV2() {
      Stack() {
        Canvas(this.ctx)
          .width('100%')
          .height('100%')
          .onAreaChange((oldValue: Area, newValue: Area) => {
            this.canvasWidth = Number(newValue.width)
            this.canvasHeight = Number(newValue.height)
          })
          .onTouch((event: TouchEvent) => {
            this.onCanvasTouch(event)
          })

        // 顶栏 + 提示（覆盖在星空之上）
        Column() {
          this.HeaderBar()

          if (this.sensorsState === 0) {
            // 降级：传感器不可用，常显提示（不淡出）
            this.HintPill(StarMapText.sensorUnavailable, true)
          } else if (this.sensorsState === 1 && this.hintVisible) {
            this.HintPill(StarMapText.hint, this.nightVision)
          }
        }
        .width('100%')
        .alignItems(HorizontalAlign.Center)

        if (this.selectedName !== '') {
          Row() {
            Column() {
              SymbolGlyph($r('sys.symbol.ohos_star'))
                .fontSize(24)
                .fontColor([this.nightVision ? DesignColor.DARK_ERROR
                  : DesignColor.DARK_ICON_FOREGROUND_PURPLE])
            }
            .width(44)
            .height(44)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
            .backgroundColor(DesignColor.DARK_ICON_CONTAINER_PURPLE)

            Column() {
              Text(this.selectedName)
                .fontSize(DesignFontSize.CARD_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(this.headerColor())

              Text(`${StarMapText.magnitudePrefix} ${this.selectedMagnitude.toFixed(1)}`)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(this.nightVision ? rgbaOf(DesignColor.DARK_ERROR, 0.8)
                  : DesignColor.DARK_TEXT_SECONDARY)
                .fontFeature(DesignFontFeature.TNUM)
                .margin({ top: DesignDimens.SPACE_XXS })
            }
            .alignItems(HorizontalAlign.Start)
            .margin({ left: DesignDimens.SPACE_MD })
          }
          .padding(DesignDimens.CARD_PADDING)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .backgroundColor(DesignColor.DARK_SURFACE)
          .borderWidth(1)
          .borderColor(DesignColor.DARK_OUTLINE)
          .alignItems(VerticalAlign.Center)
          .align(Alignment.Bottom)
          .margin({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
            bottom: DesignDimens.SPACE_XL + safeBottomVp() })
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor(DesignColor.DARK_BACKGROUND)
      .alignContent(Alignment.TopStart)
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      this.startSensors()
      this.startLoop()
    })
    .onHidden(() => {
      this.stopSensors()
      this.stopLoop()
    })
  }

  /** 顶栏：返回 + 标题 + 夜视模式开关（暗色沉浸式，布局对齐安卓 StarMapHeader） */
  @Builder
  HeaderBar() {
    Row() {
      Column() {
        SymbolGlyph($r('sys.symbol.chevron_left'))
          .fontSize(24)
          .fontColor([this.headerColor()])
      }
      .width(DesignDimens.BACK_HIT_AREA)
      .height(DesignDimens.BACK_HIT_AREA)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .accessibilityDescription(StarMapText.back)
      .onClick(() => {
        pathStack.pop()
      })

      Text(StarMapText.title)
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(this.headerColor())

      Blank()

      // 外层补 padding 外扩热区到 52，36 视觉容器不变（对齐安卓 pressable 前置）
      Column() {
        Column() {
          SymbolGlyph($r('sys.symbol.moon'))
            .fontSize(20)
            .fontColor([this.nightVision ? DesignColor.DARK_ERROR
              : DesignColor.DARK_ICON_FOREGROUND_PURPLE])
        }
        .width(36)
        .height(36)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .borderRadius(DesignDimens.RADIUS_MD)
        .backgroundColor(DesignColor.DARK_ICON_CONTAINER_PURPLE)
      }
      .padding(DesignDimens.SPACE_XS)
      .accessibilityText(StarMapText.nightVision)
      .onClick(() => {
        this.toggleNightVision()
      })
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .backgroundColor(rgbaOf(DesignColor.DARK_BACKGROUND, 0.72))
    .padding({ left: 12, right: DesignDimens.PAGE_PADDING,
      top: safeTopVp() + 2, bottom: 6 })
  }

  /** 顶部提示胶囊 */
  @Builder
  HintPill(text: string, nightVision: boolean) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(nightVision ? DesignColor.DARK_ERROR : DesignColor.DARK_TEXT_SECONDARY)
      .backgroundColor(rgbaOf(DesignColor.DARK_SURFACE, 0.85))
      .borderRadius(DesignDimens.RADIUS_FULL)
      .borderWidth(1)
      .borderColor(DesignColor.DARK_OUTLINE)
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
        top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
      .margin({ top: DesignDimens.SPACE_SM })
      .opacity(this.sensorsState === 0 ? 1 : (this.hintVisible ? 1 : 0))
      .animation({ duration: 220, curve: Curve.EaseOut })
  }
}
