/**
 * 滚筒节拍机页 — 对齐安卓 feature/lab/ui/drum/DrumPage.kt。
 *
 * BPM 锚点 + 播放/停止 + 4 节奏型 + 6 预设 + 8×16 步进网格。
 * 音频引擎 DrumEngine 负责程序化合成与 lookahead 调度；音频不可用时静默降级
 * （视觉播放头仍推进）。reduced motion：默认停止、用户主动播放（当前常量 false）。
 */

import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { AppButton, AppButtonVariant, PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import {
  DRUM_MAX_BPM,
  DRUM_MIN_BPM,
  DRUM_STEP_COUNT,
  DRUM_VOICE_COUNT,
  DrumEngine,
  DrumPattern,
  DrumVoice,
} from '../../core/DrumEngine'
import { LabHaptic, labReduceMotion } from '../../core/LabCommon'

/** 预设节奏：BPM + 8 行 × 16 步谱面（'x' 触发 / '.' 空） */
class DrumPreset {
  bpm: number
  rows: string[]

  constructor(bpm: number, rows: string[]) {
    this.bpm = bpm
    this.rows = rows
  }
}

/** 预设表（对齐安卓 DrumPreset） */
const DRUM_PRESETS: DrumPreset[] = [
  new DrumPreset(120, [
    'x...x...x...x...', '....x.......x...', 'x.x.x.x.x.x.x.x.', '................',
    '................', '................', '................', '................',
  ]),
  new DrumPreset(124, [
    'x..x....x..x....', '....x.......x...', 'x.x.x.xxx.x.x.xx', '....x.......x..x',
    '................', '................', '................', '................',
  ]),
  new DrumPreset(140, [
    'x.....x...x.....', '....x..x....x...', 'xxxxxxxxxxxxxxx.', '....x.......x...',
    '..............x.', '................', '................', 'x...............',
  ]),
  new DrumPreset(110, [
    'x..x....x..x....', '....x..x....x..x', 'x.x.x.x.x.x.x.x.', '....x.......x...',
    '........x.......', 'x.x..x.xx.x..x.x', '................', '................',
  ]),
  new DrumPreset(128, [
    'x...x...x...x..x', '....x.......x...', 'x.xxx.xxx.xxx.xx', '....x.......x...',
    '............x..x', '..x...x...x...x.', 'x.............x.', '...............x',
  ]),
  new DrumPreset(100, [
    'x.....x.x.......', '....x.......x...', 'x.x.x.x.x.x.x.x.', '............x...',
    '..x.......x.....', '................', '....x.......x...', '..............x.',
  ]),
]

/** 页面文案 */
class DrumText {
  static readonly title: string = '滚筒节拍机'
  static readonly bpmLabel: string = 'BPM'
  static readonly patternLabel: string = '节奏型'
  static readonly presetsLabel: string = '预设节奏'
  static readonly play: string = '播放'
  static readonly stop: string = '停止'
  static readonly patternA: string = 'A'
  static readonly patternB: string = 'B'
  static readonly patternC: string = 'C'
  static readonly patternD: string = 'D'
  // 预设名对齐安卓 strings_drum.xml（基础四四/机械/洗衣节奏）
  static readonly presetBasic: string = '基础四四'
  static readonly presetGroove: string = '律动'
  static readonly presetBreakbeat: string = '碎拍'
  static readonly presetLatin: string = '拉丁'
  static readonly presetRobot: string = '机械'
  static readonly presetLaundry: string = '洗衣节奏'
  static readonly voiceKick: string = '底鼓'
  static readonly voiceSnare: string = '军鼓'
  static readonly voiceHihat: string = '踩镲'
  static readonly voiceClap: string = '拍手'
  static readonly voiceTom: string = '通鼓'
  static readonly voiceCowbell: string = '牛铃'
  static readonly voiceRim: string = '边击'
  static readonly voiceCymbal: string = '镲片'
}

/** 节奏型数量 */
const PATTERN_COUNT: number = 4

/** 步进格宽（vp） */
const CELL_WIDTH: number = 24

/** 步进格行高（vp） */
const ROW_HEIGHT: number = 40

/** 声部标签列宽（vp） */
const CELL_LABEL_WIDTH: number = 44

@Builder
export function DrumBuilder() {
  DrumPage()
}

@ComponentV2
export struct DrumPage {
  @Local playing: boolean = false
  @Local bpm: number = 120
  @Local activePattern: number = 0
  @Local patterns: DrumPattern[] = []
  @Local currentStep: number = -1

  private engine: DrumEngine = new DrumEngine()
  private sliderCtx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  private sliderWidth: number = 0
  private reduceMotion: boolean = labReduceMotion()

  aboutToAppear(): void {
    this.patterns = [
      DrumPattern.fromRows(DRUM_PRESETS[0].rows),
      DrumPattern.fromRows(DRUM_PRESETS[1].rows),
      DrumPattern.fromRows(DRUM_PRESETS[2].rows),
      DrumPattern.fromRows(DRUM_PRESETS[3].rows),
    ]
    this.engine.setStepListener((step: number) => {
      this.currentStep = step
    })
  }

  aboutToDisappear(): void {
    this.engine.release()
  }

  /** 播放/停止切换 */
  private togglePlay(): void {
    LabHaptic.medium()
    if (this.playing) {
      this.engine.stop()
      this.playing = false
      this.currentStep = -1
    } else {
      this.engine.setBpm(this.bpm)
      this.engine.setPattern(this.patterns[this.activePattern])
      this.engine.start()
      this.playing = true
    }
  }

  private setBpm(value: number): void {
    const coerced: number = Math.min(DRUM_MAX_BPM, Math.max(DRUM_MIN_BPM, value))
    if (coerced === this.bpm) {
      return
    }
    this.bpm = coerced
    this.engine.setBpm(coerced)
    this.drawSlider()
  }

  private selectPattern(index: number): void {
    if (index < 0 || index >= PATTERN_COUNT || index === this.activePattern) {
      return
    }
    this.activePattern = index
    if (this.playing) {
      this.engine.setPattern(this.patterns[index])
    }
    LabHaptic.selection()
  }

  private toggleStep(voice: number, step: number): void {
    const pattern: DrumPattern | undefined = this.patterns[this.activePattern]
    if (pattern === undefined) {
      return
    }
    const updated: DrumPattern = pattern.withStep(voice, step, !pattern.isEnabled(voice, step))
    const next: DrumPattern[] = this.patterns.slice()
    next[this.activePattern] = updated
    this.patterns = next
    if (this.playing) {
      this.engine.setPattern(updated)
    }
  }

  private applyPreset(preset: DrumPreset): void {
    const pattern: DrumPattern = DrumPattern.fromRows(preset.rows)
    const next: DrumPattern[] = this.patterns.slice()
    next[this.activePattern] = pattern
    this.patterns = next
    this.bpm = preset.bpm
    this.engine.setBpm(preset.bpm)
    if (this.playing) {
      this.engine.setPattern(pattern)
    }
    LabHaptic.selection()
  }

  /** 声部名 */
  private voiceLabel(voice: number): string {
    switch (voice) {
      case DrumVoice.KICK:
        return DrumText.voiceKick
      case DrumVoice.SNARE:
        return DrumText.voiceSnare
      case DrumVoice.HIHAT:
        return DrumText.voiceHihat
      case DrumVoice.CLAP:
        return DrumText.voiceClap
      case DrumVoice.TOM:
        return DrumText.voiceTom
      case DrumVoice.COWBELL:
        return DrumText.voiceCowbell
      case DrumVoice.RIM:
        return DrumText.voiceRim
      default:
        return DrumText.voiceCymbal
    }
  }

  /** 预设名 */
  private presetLabel(index: number): string {
    switch (index) {
      case 0:
        return DrumText.presetBasic
      case 1:
        return DrumText.presetGroove
      case 2:
        return DrumText.presetBreakbeat
      case 3:
        return DrumText.presetLatin
      case 4:
        return DrumText.presetRobot
      default:
        return DrumText.presetLaundry
    }
  }

  /** BPM 滑杆绘制 */
  private drawSlider(): void {
    const ctx: CanvasRenderingContext2D = this.sliderCtx
    const width: number = this.sliderWidth
    if (width <= 0) {
      return
    }
    const height: number = 44
    const trackHeight: number = 4
    const trackY: number = height / 2
    const minBpm: number = DRUM_MIN_BPM
    const maxBpm: number = DRUM_MAX_BPM
    const fraction: number = Math.min(1, Math.max(0, (this.bpm - minBpm) / (maxBpm - minBpm)))
    ctx.clearRect(0, 0, width, height)
    // 轨道
    ctx.fillStyle = DesignColor.SURFACE_VARIANT
    this.roundRect(0, trackY - trackHeight / 2, width, trackHeight, trackHeight / 2)
    ctx.fill()
    // 已填充
    ctx.fillStyle = DesignColor.PRIMARY
    this.roundRect(0, trackY - trackHeight / 2, width * fraction, trackHeight, trackHeight / 2)
    ctx.fill()
    // 拇指
    const thumbX: number = width * fraction
    const thumbR: number = 10
    ctx.fillStyle = DesignColor.SURFACE
    ctx.beginPath()
    ctx.arc(thumbX, trackY, thumbR, 0, Math.PI * 2, false)
    ctx.fill()
    ctx.lineWidth = 2
    ctx.strokeStyle = DesignColor.PRIMARY
    ctx.beginPath()
    ctx.arc(thumbX, trackY, thumbR, 0, Math.PI * 2, false)
    ctx.stroke()
  }

  /** 手工圆角矩形（SDK 17 无 roundRect） */
  private roundRect(x: number, y: number, w: number, h: number, r: number): void {
    const ctx: CanvasRenderingContext2D = this.sliderCtx
    ctx.beginPath()
    ctx.moveTo(x + r, y)
    ctx.lineTo(x + w - r, y)
    ctx.arc(x + w - r, y + r, r, -Math.PI / 2, 0, false)
    ctx.lineTo(x + w, y + h - r)
    ctx.arc(x + w - r, y + h - r, r, 0, Math.PI / 2, false)
    ctx.lineTo(x + r, y + h)
    ctx.arc(x + r, y + h - r, r, Math.PI / 2, Math.PI, false)
    ctx.lineTo(x, y + r)
    ctx.arc(x + r, y + r, r, Math.PI, Math.PI * 1.5, false)
    ctx.closePath()
  }

  /** BPM 滑杆触摸定位 */
  private onSliderTouch(event: TouchEvent): void {
    const touch: TouchObject | undefined = event.touches.length > 0 ? event.touches[0] : undefined
    if (touch === undefined || this.sliderWidth <= 0) {
      return
    }
    if (event.type === TouchType.Down || event.type === TouchType.Move) {
      const fraction: number = Math.min(1, Math.max(0, touch.x / this.sliderWidth))
      this.setBpm(Math.round(DRUM_MIN_BPM + fraction * (DRUM_MAX_BPM - DRUM_MIN_BPM)))
    }
  }

  /** 控制卡：BPM + 播放 + 节奏型 */
  @Builder
  ControlCard() {
    Column() {
      Row() {
        Column() {
          Text(DrumText.bpmLabel)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(DesignColor.TEXT_SECONDARY)

          Text(this.bpm.toString())
            .fontSize(DesignFontSize.DATA_LARGE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .fontFeature(DesignFontFeature.TNUM)
        }
        .alignItems(HorizontalAlign.Start)

        Blank()
          .layoutWeight(1)

        // 行内播放控件（非页面 CTA）：小按钮档 36dp/r12、按内容宽（对齐安卓 small=true）
        AppButton({
          text: this.playing ? DrumText.stop : DrumText.play,
          variant: this.playing ? AppButtonVariant.SECONDARY : AppButtonVariant.PRIMARY,
          small: true,
          click: () => {
            this.togglePlay()
          }
        })
        .margin({ left: DesignDimens.SPACE_MD })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Canvas(this.sliderCtx)
        .width('100%')
        .height(44)
        .margin({ top: DesignDimens.SPACE_SM })
        .onAreaChange((oldValue: Area, newValue: Area) => {
          this.sliderWidth = Number(newValue.width)
          this.drawSlider()
        })
        .onReady(() => {
          this.drawSlider()
        })
        .onTouch((event: TouchEvent) => {
          this.onSliderTouch(event)
        })

      Text(DrumText.patternLabel)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .margin({ top: DesignDimens.SPACE_MD })

      Row({ space: DesignDimens.SPACE_XS }) {
        ForEach([0, 1, 2, 3], (index: number) => {
          Text(this.patternLabel(index))
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor(this.activePattern === index ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
            .width(36)
            .height(36)
            .borderRadius(DesignDimens.RADIUS_FULL)
            .backgroundColor(this.activePattern === index ? DesignColor.PRIMARY : DesignColor.SURFACE)
            .borderWidth(1)
            .borderColor(this.activePattern === index ? DesignColor.PRIMARY : DesignColor.OUTLINE)
            .textAlign(TextAlign.Center)
            .onClick(() => {
              this.selectPattern(index)
            })
        }, (index: number) => index.toString())
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_XS })
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
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
  }

  private patternLabel(index: number): string {
    return String.fromCharCode(65 + index)
  }

  /** 预设节奏横滑 chips */
  @Builder
  PresetSection() {
    Column() {
      Text(DrumText.presetsLabel)
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)

      Scroll() {
        Row() {
          ForEach(DRUM_PRESETS, (preset: DrumPreset, index: number) => {
            // 外层补 padding 外扩热区到 48，视觉胶囊不变（对齐安卓 pressable 前置）
            Column() {
              Text(this.presetLabel(index))
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
                  top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
                .borderRadius(DesignDimens.RADIUS_FULL)
                .backgroundColor(DesignColor.SURFACE_VARIANT)
                .borderWidth(1)
                .borderColor(DesignColor.OUTLINE)
            }
            .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
            .margin({ right: DesignDimens.SPACE_XS })
            .onClick(() => {
              this.applyPreset(preset)
            })
          }, (preset: DrumPreset, index: number) => index.toString())
        }
        .padding({ right: DesignDimens.PAGE_PADDING })
      }
      .scrollable(ScrollDirection.Horizontal)
      .scrollBar(BarState.Off)
      .align(Alignment.Start)
      .width('100%')
      .margin({ top: DesignDimens.SPACE_SM })
    }
    .width('100%')
  }

  /** 步进网格卡：左侧声部标签固定列 + 右侧 16 步横滚区 */
  @Builder
  StepGridCard() {
    Row() {
      // 声部标签列（行距与步进区对齐：48 高 + 8 间距，文字垂直居中）
      Column({ space: DesignDimens.SPACE_XS }) {
        ForEach(this.voiceIndices(), (voice: number) => {
          Column() {
            Text(this.voiceLabel(voice))
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .width(CELL_LABEL_WIDTH)
              .maxLines(1)
              .textAlign(TextAlign.End)
          }
          .height(48)
          .justifyContent(FlexAlign.Center)
        }, (voice: number) => voice.toString())
      }

      // 步进区（横滚）：行/列间距 8（相邻触控目标间距 ≥8dp）
      Scroll() {
        Column({ space: DesignDimens.SPACE_XS }) {
          ForEach(this.voiceIndices(), (voice: number) => {
            Row({ space: DesignDimens.SPACE_XS }) {
              ForEach(this.stepIndices(), (step: number) => {
                this.StepCell(voice, step)
              }, (step: number) => step.toString())
            }
          }, (voice: number) => voice.toString())
        }
        .padding({ right: DesignDimens.SPACE_MD })
      }
      .scrollable(ScrollDirection.Horizontal)
      .scrollBar(BarState.Off)
      .align(Alignment.Start)
      .layoutWeight(1)
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
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
  }

  private voiceIndices(): number[] {
    const result: number[] = []
    for (let i = 0; i < DRUM_VOICE_COUNT; i++) {
      result.push(i)
    }
    return result
  }

  private stepIndices(): number[] {
    const result: number[] = []
    for (let i = 0; i < DRUM_STEP_COUNT; i++) {
      result.push(i)
    }
    return result
  }

  /** 步进格状态色（触发 primary / 播放头 water / 拍位 outline / 空 surfaceVariant） */
  private stepCellColor(voice: number, step: number): number {
    const pattern: DrumPattern | undefined = this.patterns[this.activePattern]
    const active: boolean = pattern !== undefined && pattern.isEnabled(voice, step)
    const isCurrent: boolean = step === this.currentStep
    if (active && isCurrent) {
      return DesignColor.WATER
    }
    if (active) {
      return DesignColor.PRIMARY
    }
    // 空格播放头不再染 primarySoft（对空格底仅 1.02/1.07:1 不可辨），
    // 底色保持空格原色，由 primary 描边环承担指示
    if (step % 4 === 0) {
      return DesignColor.OUTLINE
    }
    return DesignColor.SURFACE_VARIANT
  }

  private stepCellActive(voice: number, step: number): boolean {
    const pattern: DrumPattern | undefined = this.patterns[this.activePattern]
    return pattern !== undefined && pattern.isEnabled(voice, step)
  }

  /** 空格播放头描边环（2vp primary，静态保留，reduced motion 下仍可辨） */
  private stepCellBorderWidth(voice: number, step: number): number {
    return step === this.currentStep && !this.stepCellActive(voice, step) ? 2 : 0
  }

  /** 播放头落点格放大倍数（reduced motion 关闭，保留颜色态与描边环） */
  private stepCellScale(voice: number, step: number): number {
    return step === this.currentStep && !this.reduceMotion ? 1.06 : 1
  }

  private stepCellDescription(voice: number, step: number): string {
    return `${this.voiceLabel(voice)} 第 ${step + 1} 步`
  }

  /** 单个步进格：外层补 padding 把命中区外扩到 48×48，视觉格 24×40 不变 */
  @Builder
  StepCell(voice: number, step: number) {
    Column() {
      // scale 单独包一层：避免 animation 把步进格背景色变化也动画掉
      Column() {
        Column()
          .width(CELL_WIDTH)
          .height(ROW_HEIGHT)
          .borderRadius(DesignDimens.RADIUS_SMALL)
          .backgroundColor(this.stepCellColor(voice, step))
          .borderWidth(this.stepCellBorderWidth(voice, step))
          .borderColor(DesignColor.PRIMARY)
      }
      .scale({ x: this.stepCellScale(voice, step), y: this.stepCellScale(voice, step) })
      .animation({ duration: 200, curve: Curve.EaseOut })
    }
    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM,
      top: DesignDimens.SPACE_XXS, bottom: DesignDimens.SPACE_XXS })
    .accessibilityText(this.stepCellDescription(voice, step))
    .accessibilitySelected(this.stepCellActive(voice, step))
    .onClick(() => {
      this.toggleStep(voice, step)
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，滑杆画布 onAreaChange/onReady 重绘与 DesignColor
      // 静态令牌直读点重读新值（静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets；
      // 音频引擎（aboutToAppear 初始化 + setStepListener）保持在宿主之外，页面组件本体不在子树内）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: DrumText.title })

        Scroll() {
          Column({ space: DesignDimens.CARD_SPACING }) {
            this.ControlCard()
            this.PresetSection()
            this.StepGridCard()
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_SM, bottom: DesignDimens.SECTION_SPACING })
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
