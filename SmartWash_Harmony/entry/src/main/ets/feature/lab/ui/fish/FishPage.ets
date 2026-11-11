/**
 * 指尖鱼群页 — 对齐安卓 feature/lab/ui/fish/FishPage.kt。
 *
 * 清氧浅色「水池」（waterSoft 底）+ 约 180 条小鱼 boids 模拟（三规则 + 均匀网格邻居查询，
 * 内核 BoidsSimulator 纯逻辑）。交互：单指按住=吸引、长按(>400ms)=驱散、双击=径向冲击波。
 * 首次进入顶部提示 3s 淡出；tick 由 setInterval 驱动（约 30fps）。
 * reduced motion：默认静帧 + 用户主动「播放/暂停」开关（当前 SDK 无等价 API，常量 false）。
 */

import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import { BoidsSimulator } from '../../core/BoidsSimulator'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'
import { resColor } from '../../../../utils/ResourceColor'
import { resString } from '../../../../utils/ResourceString'

/** 页面文案 */
class FishText {
  static get title(): string { return resString('lab_toy_fish') }
  static get hint(): string { return resString('lab_fish_hint') }
  static get play(): string { return resString('lab_fish_play') }
  static get pause(): string { return resString('lab_breath_pause') }
}

/** 首次进入顶部提示停留时长（ms） */
const HINT_STAY_MS: number = 3000

/** 长按阈值（ms） */
const LONG_PRESS_MS: number = 400

/** 双击时间窗（ms） */
const DOUBLE_TAP_MS: number = 300

/** 双击距离阈值（vp） */
const DOUBLE_TAP_SLOP: number = 40

/** 长按取消移动阈值（vp） */
const LONG_PRESS_SLOP: number = 20

/** 鱼色组（primary / water / primaryDark 令牌系）：使用点即时求值——
 * 模块级 const 会在加载时冻结令牌值，深浅色切换后模拟循环仍拿旧色 */
function fishColorPalette(): number[] {
  return [resColor('PRIMARY'), resColor('WATER'), resColor('PRIMARY_DARK')]
}

@Builder
export function FishBuilder() {
  FishPage()
}

@ComponentV2
export struct FishPage {
  @Local hintVisible: boolean = true
  @Local manualPlaying: boolean = false

  private sim: BoidsSimulator = new BoidsSimulator()
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private canvasWidth: number = 0
  private canvasHeight: number = 0
  private timerId: number = -1
  private hintTimerId: number = -1
  private lastFrameMs: number = 0
  private reduceMotion: boolean = labReduceMotion()

  // 触摸交互状态
  private isDown: boolean = false
  private suppressPress: boolean = false
  private longPressTimerId: number = -1
  private lastTapUpMs: number = 0
  private lastTapX: number = 0
  private lastTapY: number = 0
  private downX: number = 0
  private downY: number = 0

  aboutToAppear(): void {
    if (!this.reduceMotion) {
      this.startLoop()
    }
    if (!this.reduceMotion) {
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
    if (this.longPressTimerId !== -1) {
      clearTimeout(this.longPressTimerId)
      this.longPressTimerId = -1
    }
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

  /** 帧驱动：tick + 重绘（暂停即停止重绘=静帧） */
  private startLoop(): void {
    if (this.timerId !== -1) {
      return
    }
    this.timerId = setInterval(() => {
      const now: number = Date.now()
      const dtMs: number = this.lastFrameMs === 0 ? 16 : Math.min(50, Math.max(1, now - this.lastFrameMs))
      this.lastFrameMs = now
      this.sim.tick(dtMs)
      this.drawPool()
    }, 30)
  }

  /** 画布尺寸变化 → 注入内核并重铺鱼群 */
  private onCanvasAreaChange(oldValue: Area, newValue: Area): void {
    const width: number = Number(newValue.width)
    const height: number = Number(newValue.height)
    if (width <= 0 || height <= 0) {
      return
    }
    this.canvasWidth = width
    this.canvasHeight = height
    this.sim.resize(width, height)
    this.drawPool()
  }

  /** 触摸交互：按住吸引 / 长按驱散 / 双击冲击波 */
  private onCanvasTouch(event: TouchEvent): void {
    const touch: TouchObject | undefined = event.touches.length > 0 ? event.touches[0] : undefined
    if (touch === undefined) {
      return
    }
    const x: number = touch.x
    const y: number = touch.y
    if (event.type === TouchType.Down) {
      this.isDown = true
      this.suppressPress = false
      this.downX = x
      this.downY = y
      const now: number = Date.now()
      const dx: number = x - this.lastTapX
      const dy: number = y - this.lastTapY
      if (now - this.lastTapUpMs < DOUBLE_TAP_MS && Math.abs(dx) < DOUBLE_TAP_SLOP &&
        Math.abs(dy) < DOUBLE_TAP_SLOP) {
        // 双击：径向冲击波，抑制本次按住吸引
        this.sim.pulse(x, y)
        LabHaptic.medium()
        this.suppressPress = true
        this.isDown = false
        this.lastTapUpMs = 0
        return
      }
      this.sim.pointerDown(x, y)
      LabHaptic.light()
      if (this.longPressTimerId !== -1) {
        clearTimeout(this.longPressTimerId)
      }
      this.longPressTimerId = setTimeout(() => {
        if (this.isDown && !this.suppressPress) {
          this.sim.engageRepel(this.downX, this.downY)
          LabHaptic.heavy()
        }
      }, LONG_PRESS_MS)
    } else if (event.type === TouchType.Move) {
      if (!this.isDown || this.suppressPress) {
        return
      }
      const dx: number = x - this.downX
      const dy: number = y - this.downY
      if (dx * dx + dy * dy > LONG_PRESS_SLOP * LONG_PRESS_SLOP && this.longPressTimerId !== -1) {
        clearTimeout(this.longPressTimerId)
        this.longPressTimerId = -1
      }
      this.sim.pointerDown(x, y)
    } else {
      // Up / Cancel
      if (this.longPressTimerId !== -1) {
        clearTimeout(this.longPressTimerId)
        this.longPressTimerId = -1
      }
      if (this.isDown && !this.suppressPress) {
        this.sim.pointerUp()
        this.lastTapUpMs = Date.now()
        this.lastTapX = x
        this.lastTapY = y
      }
      this.isDown = false
      this.suppressPress = false
    }
  }

  /** 绘制水池与鱼群 */
  private drawPool(): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const width: number = this.canvasWidth
    const height: number = this.canvasHeight
    if (width <= 0 || height <= 0) {
      return
    }
    ctx.clearRect(0, 0, width, height)
    ctx.fillStyle = resColor('WATER_SOFT')
    ctx.fillRect(0, 0, width, height)
    // 鱼色每帧求值一次（非逐鱼，避免高频路径重复分配数组）：
    // 深浅色切换（ThemeRefreshV2 重建子树）后下一帧即取新令牌
    const fishColors: number[] = fishColorPalette()
    for (const boid of this.sim.boidsList) {
      const size: number = 4 + boid.sizeFactor * 4
      ctx.save()
      ctx.translate(boid.x, boid.y)
      ctx.rotate(Math.atan2(boid.vy, boid.vx))
      ctx.fillStyle = fishColors[boid.colorIndex % fishColors.length]
      ctx.beginPath()
      ctx.moveTo(1.2 * size, 0)
      ctx.lineTo(-1.2 * size, -0.6 * size)
      ctx.lineTo(-1.2 * size, 0.6 * size)
      ctx.closePath()
      ctx.fill()
      ctx.restore()
    }
  }

  /** reduced motion 播放/暂停开关 */
  @Builder
  PlayToggleButton() {
    Row({ space: DesignDimens.SPACE_XS }) {
      SymbolGlyph(this.playing ? $r('sys.symbol.pause') : $r('sys.symbol.play'))
        .fontSize(20)
        .fontColor([$r('app.color.PRIMARY')])

      Text(this.playing ? FishText.pause : FishText.play)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontColor($r('app.color.TEXT_PRIMARY'))
    }
    // 视觉胶囊不变，热区抬到 48dp 下限（对齐安卓 sizeIn(minHeight=48)）
    .constraintSize({ minHeight: 48 })
    .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
      top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
    .borderRadius(DesignDimens.RADIUS_MD)
    .backgroundColor($r('app.color.SURFACE'))
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .onClick(() => {
      this.manualPlaying = !this.manualPlaying
      if (this.manualPlaying) {
        this.startLoop()
      } else {
        this.clearLoop()
      }
      LabHaptic.selection()
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: FishText.title })

        Stack() {
          Canvas(this.ctx)
            .width('100%')
            .height('100%')
            .borderRadius(DesignDimens.CARD_RADIUS)
            .clip(true)
            .borderWidth(1)
            .borderColor($r('app.color.OUTLINE'))
            .onAreaChange((oldValue: Area, newValue: Area) => {
              this.onCanvasAreaChange(oldValue, newValue)
            })
            .onReady(() => {
              this.drawPool()
            })
            .onTouch((event: TouchEvent) => {
              this.onCanvasTouch(event)
            })

          if (this.hintVisible) {
            Column() {
              Text(FishText.hint)
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor($r('app.color.TEXT_GREEN'))
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

          if (this.reduceMotion) {
            Column() {
              this.PlayToggleButton()
            }
            .width('100%')
            .height('100%')
            .justifyContent(FlexAlign.End)
            .alignItems(HorizontalAlign.Center)
            .padding({ bottom: DesignDimens.SECTION_SPACING })
          }
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          bottom: DesignDimens.SPACE_MD })
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }
}
