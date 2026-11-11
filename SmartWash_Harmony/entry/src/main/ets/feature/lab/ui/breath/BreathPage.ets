/**
 * 呼吸引导页 — 对齐安卓 feature/lab/ui/breath/BreathPage.kt。
 *
 * 模式选择（4-7-8 睡眠 / Box 4-4-4-4 专注）→ 全屏引导：中心 Canvas 圆环随相位
 * 放大/保持/缩小 + 相位文字 + 倒计时数字（tnum）+ 完成轮次；可暂停/继续/停止。
 * 相位状态机在本页（对齐安卓 BreathViewModel 的 tick 推进），周期 50ms。
 */

import { DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight, toColorString } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { AppButton, AppButtonVariant, AppPillTabRow, PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'
import { resColor } from '../../../../utils/ResourceColor'
import { resString } from '../../../../utils/ResourceString'

/** 呼吸相位类型 */
enum BreathPhaseType {
  INHALE,
  HOLD,
  EXHALE,
  HOLD_TAIL,
}

/** 单相位定义（类型 + 时长 ms） */
class BreathPhase {
  type: BreathPhaseType
  durationMs: number

  constructor(type: BreathPhaseType, durationMs: number) {
    this.type = type
    this.durationMs = durationMs
  }
}

/** 呼吸模式（相位表为单一事实来源） */
enum BreathMode {
  SLEEP,
  BOX,
}

/** 相位表（对齐安卓 BreathMode.phasesOf） */
function phasesOf(mode: BreathMode): BreathPhase[] {
  if (mode === BreathMode.SLEEP) {
    return [
      new BreathPhase(BreathPhaseType.INHALE, 4000),
      new BreathPhase(BreathPhaseType.HOLD, 7000),
      new BreathPhase(BreathPhaseType.EXHALE, 8000),
    ]
  }
  return [
    new BreathPhase(BreathPhaseType.INHALE, 4000),
    new BreathPhase(BreathPhaseType.HOLD, 4000),
    new BreathPhase(BreathPhaseType.EXHALE, 4000),
    new BreathPhase(BreathPhaseType.HOLD_TAIL, 4000),
  ]
}

/** 页面文案 */
class BreathText {
  static get title(): string { return resString('lab_toy_breath') }
  static get modeSleep(): string { return resString('lab_breath_mode_sleep') }
  static get modeBox(): string { return resString('lab_breath_mode_box') }
  static get modeSleepHint(): string { return resString('lab_breath_mode_sleep_hint') }
  static get modeBoxHint(): string { return resString('lab_breath_mode_box_hint') }
  static get start(): string { return resString('lab_breath_start') }
  static get pause(): string { return resString('lab_breath_pause') }
  static get resume(): string { return resString('lab_breath_resume') }
  static get stop(): string { return resString('lab_breath_stop') }
  static get phaseInhale(): string { return resString('lab_breath_phase_inhale') }
  static get phaseHold(): string { return resString('lab_breath_phase_hold') }
  static get phaseExhale(): string { return resString('lab_breath_phase_exhale') }
  static get phaseKeep(): string { return resString('lab_breath_phase_keep') }
  static get cyclesFormat(): string { return resString('breath_text_cycles_format') }
}

/** 圆环画布边长（vp） */
const RING_SIZE: number = 240

/** 呼吸圆基准半径占画布比例 */
const BASE_RADIUS_RATIO: number = 0.32

/** 吸气满相放大倍数 */
const SCALE_INHALED: number = 1.4

/**
 * 圆心文字底衬的平台占比（径向渐变 0~该值内为纯色平台，之后渐隐至透明）。
 * 呼吸圆中心为纯 water 色，深色板上 textPrimary 对 water 仅 1.99:1，文字不可读；
 * 以 waterSoft 中心晕把文字区背景固定到可控色（双板通用，无需主题分支）。
 */
const TEXT_PLATE_SOLID_RATIO: number = 0.75

/** 状态机节拍（ms） */
const TICK_MS: number = 50

@Builder
export function BreathBuilder() {
  BreathPage()
}

@ComponentV2
export struct BreathPage {
  @Local mode: BreathMode = BreathMode.SLEEP
  @Local running: boolean = false
  @Local paused: boolean = false
  @Local phaseIndex: number = 0
  @Local remainingMs: number = 0
  @Local cycles: number = 0
  @Local scaleValue: number = 1

  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private tickTimerId: number = -1
  private reduceMotion: boolean = labReduceMotion()

  aboutToDisappear(): void {
    this.clearTicker()
  }

  private clearTicker(): void {
    if (this.tickTimerId !== -1) {
      clearInterval(this.tickTimerId)
      this.tickTimerId = -1
    }
  }

  /** 选择态切换模式（未开始不推进状态机） */
  private selectMode(mode: BreathMode): void {
    if (this.running) {
      return
    }
    this.mode = mode
    this.phaseIndex = 0
    this.cycles = 0
    this.remainingMs = 0
    this.scaleValue = 1
  }

  /** 开始引导 */
  private start(): void {
    if (this.running) {
      return
    }
    this.clearTicker()
    const phases: BreathPhase[] = phasesOf(this.mode)
    this.running = true
    this.paused = false
    this.phaseIndex = 0
    this.cycles = 0
    this.remainingMs = phases[0].durationMs
    this.scaleValue = 1
    this.drawRing()
    LabHaptic.light()
    this.tickTimerId = setInterval(() => {
      this.onTick()
    }, TICK_MS)
  }

  /** 暂停：冻结倒计时与圆环 */
  private pause(): void {
    if (!this.running || this.paused) {
      return
    }
    this.clearTicker()
    this.paused = true
  }

  /** 继续：以剩余时长续起 */
  private resume(): void {
    if (!this.running || !this.paused) {
      return
    }
    this.paused = false
    this.tickTimerId = setInterval(() => {
      this.onTick()
    }, TICK_MS)
  }

  /** 停止引导：回模式选择态（保留所选模式高亮） */
  private stop(): void {
    this.clearTicker()
    this.running = false
    this.paused = false
    this.phaseIndex = 0
    this.cycles = 0
    this.remainingMs = 0
    this.scaleValue = 1
    this.drawRing()
  }

  /** tick 推进：倒计时递减，跨零切相位（尾相位回卷即完成一轮） */
  private onTick(): void {
    if (!this.running || this.paused) {
      return
    }
    const phases: BreathPhase[] = phasesOf(this.mode)
    let remaining: number = this.remainingMs - TICK_MS
    let index: number = this.phaseIndex
    let cycleCount: number = this.cycles
    while (remaining <= 0) {
      index += 1
      if (index >= phases.length) {
        index = 0
        cycleCount += 1
      }
      remaining += phases[index].durationMs
    }
    this.remainingMs = remaining
    this.phaseIndex = index
    this.cycles = cycleCount
    this.updateScale()
    this.drawRing()
  }

  /** 相位进度 → 圆环缩放（reduced motion 静止在中等尺寸） */
  private updateScale(): void {
    if (this.reduceMotion) {
      this.scaleValue = 0.75
      return
    }
    const phases: BreathPhase[] = phasesOf(this.mode)
    const phase: BreathPhase = phases[this.phaseIndex]
    const progress: number = Math.min(1, Math.max(0, 1 - this.remainingMs / phase.durationMs))
    if (phase.type === BreathPhaseType.INHALE) {
      this.scaleValue = 1 + (SCALE_INHALED - 1) * progress
    } else if (phase.type === BreathPhaseType.EXHALE) {
      this.scaleValue = SCALE_INHALED - (SCALE_INHALED - 1) * progress
    } else if (phase.type === BreathPhaseType.HOLD) {
      this.scaleValue = SCALE_INHALED
    } else {
      this.scaleValue = 1
    }
  }

  /** 绘制呼吸圆环（水色令牌系） */
  private drawRing(): void {
    const ctx: CanvasRenderingContext2D = this.ctx
    const size: number = RING_SIZE
    const cx: number = size / 2
    const cy: number = size / 2
    const baseR: number = size / 2 * BASE_RADIUS_RATIO
    ctx.clearRect(0, 0, size, size)
    // 轨道环
    ctx.lineWidth = 2
    ctx.strokeStyle = resColor('WATER_SOFT')
    ctx.beginPath()
    ctx.arc(cx, cy, baseR * SCALE_INHALED + 4, 0, Math.PI * 2, false)
    ctx.stroke()
    // 呼吸圆（水 → 水浅径向渐变）
    const radius: number = baseR * this.scaleValue
    const gradient: CanvasGradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, baseR * SCALE_INHALED)
    gradient.addColorStop(0, toColorString(resColor('WATER')))
    gradient.addColorStop(1, toColorString(resColor('WATER_SOFT')))
    ctx.fillStyle = gradient
    ctx.beginPath()
    ctx.arc(cx, cy, radius, 0, Math.PI * 2, false)
    ctx.fill()
    // 圆心文字底衬（水色晕）：呼吸圆后叠加同尺寸 waterSoft 径向渐变，
    // 0→0.75 纯色平台、1→全透明，把文字区背景固定到 waterSoft（深浅板均达标）
    const plateSoft: string = toColorString(resColor('WATER_SOFT'))
    const plateTransparent: string = toColorString(resColor('WATER_SOFT') & 0xFFFFFF)
    const plate: CanvasGradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, baseR)
    plate.addColorStop(0, plateSoft)
    plate.addColorStop(TEXT_PLATE_SOLID_RATIO, plateSoft)
    plate.addColorStop(1, plateTransparent)
    ctx.fillStyle = plate
    ctx.beginPath()
    ctx.arc(cx, cy, baseR, 0, Math.PI * 2, false)
    ctx.fill()
  }

  /** 相位文案 */
  private phaseLabel(type: BreathPhaseType): string {
    if (type === BreathPhaseType.INHALE) {
      return BreathText.phaseInhale
    }
    if (type === BreathPhaseType.HOLD) {
      return BreathText.phaseHold
    }
    if (type === BreathPhaseType.EXHALE) {
      return BreathText.phaseExhale
    }
    return BreathText.phaseKeep
  }

  /** 倒计时显示（秒，至少 1） */
  private countdownText(): string {
    return Math.max(1, Math.ceil(this.remainingMs / 1000)).toString()
  }

  /** 完成轮次文案 */
  private cyclesText(): string {
    return BreathText.cyclesFormat.replace('%d', this.cycles.toString())
  }

  /** 模式选择态 */
  @Builder
  ModeSelector() {
    Column() {
      AppPillTabRow({
        tabs: [BreathText.modeSleep, BreathText.modeBox],
        selectedIndex: this.mode,
        tabSelected: (index: number) => {
          this.selectMode(index === 0 ? BreathMode.SLEEP : BreathMode.BOX)
        }
      })

      Text(this.mode === BreathMode.SLEEP ? BreathText.modeSleepHint : BreathText.modeBoxHint)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_XS })

      Blank()

      AppButton({
        text: BreathText.start,
        click: () => {
          this.start()
        }
      })
      .width('100%')
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    }
    .width('100%')
    .height('100%')
    .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_XL })
  }

  /** 引导态 */
  @Builder
  GuideStage() {
    Column() {
      Blank()

      Stack({ alignContent: Alignment.Center }) {
        Canvas(this.ctx)
          .width(RING_SIZE)
          .height(RING_SIZE)
          .onReady(() => {
            this.drawRing()
          })

        Column() {
          Text(this.phaseLabel(phasesOf(this.mode)[this.phaseIndex].type))
            .fontSize(DesignFontSize.AMOUNT_MEDIUM)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_PRIMARY'))

          Text(this.countdownText())
            .fontSize(DesignFontSize.STATUS_LARGE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .fontFeature(DesignFontFeature.TNUM)
            .margin({ top: DesignDimens.SPACE_XXS })
        }
        .alignItems(HorizontalAlign.Center)
      }
      .width(RING_SIZE)
      .height(RING_SIZE)

      Text(this.cyclesText())
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .fontFeature(DesignFontFeature.TNUM)
        .margin({ top: DesignDimens.SPACE_MD })

      Blank()

      Row() {
        AppButton({
          text: this.paused ? BreathText.resume : BreathText.pause,
          variant: AppButtonVariant.SECONDARY,
          click: () => {
            if (this.paused) {
              this.resume()
            } else {
              this.pause()
            }
          }
        })
          .layoutWeight(1)

        AppButton({
          text: BreathText.stop,
          variant: AppButtonVariant.TEXT,
          click: () => {
            this.stop()
          }
        })
        .margin({ left: DesignDimens.SPACE_MD })
      }
      .justifyContent(FlexAlign.Center)
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    }
    .width('100%')
    .height('100%')
    .padding({ bottom: DesignDimens.SPACE_XL })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，画布 onReady 重绘与 资源色直读点
      // 重读新值（静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets；
      // 相位状态机节拍器（setInterval onTick）保持在宿主之外，页面组件本体不在子树内）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: BreathText.title })

        // 内容区显式吃满剩余高度：原 ModeSelector/GuideStage 自带 height('100%')，
        // 与 PageHeader 兄弟排列时按整屏高布局，整体下溢一个页头高（56vp），
        // 底部按钮被挤进手势条安全区（真机实测按钮下缘 2848 > 安全区 2750）。
        Column() {
          if (!this.running) {
            this.ModeSelector()
          } else {
            this.GuideStage()
          }
        }
        .width('100%')
        .layoutWeight(1)
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
