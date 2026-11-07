/**
 * 声景混音器页 — 对齐安卓 feature/lab/ui/soundscape/SoundscapePage.kt + SoundscapeViewModel.kt。
 *
 * 结构：hero 控制卡（播放/停止 + 可视化条形）+ 5 个预设场景 chips +
 * 10 路声道混音台（底噪/自然/室内三张分组卡：开关 + 独立音量滑杆）+ 睡眠定时
 * （15/30/60 分钟，到点先淡出再停止）。音频由 SoundscapeEngine 程序化合成，
 * 零音频资产；音频不可用时播放按钮降级为不启动（UI 保持停止态）。
 *
 * 生命周期：进入页面不自动播放；NavDestination onHidden 暂停（停止播放与定时），
 * aboutToDisappear 释放引擎（对齐安卓 onCleared → engine.release()）。
 */

import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight }
  from '../../../../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { PageHeader } from '../../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../../view/common/ThemeRefresh'
import {
  SOUND_CHANNEL_COUNT,
  SOUNDSCAPE_DEFAULT_VOLUME,
  SoundChannel,
  SoundscapeEngine,
} from '../../core/SoundscapeEngine'
import { LabHaptic } from '../../core/LabCommon'

/** 页面文案（对齐安卓 strings_soundscape.xml） */
class SoundscapeText {
  static readonly title: string = '声景混音器'
  static readonly playing: string = '正在播放'
  static readonly stopped: string = '已停止'
  static readonly play: string = '播放'
  static readonly stop: string = '停止'
  static readonly presetsLabel: string = '预设场景'
  static readonly presetStudy: string = '自习室'
  static readonly presetSleep: string = '睡前'
  static readonly presetDorm: string = '宿舍夜'
  static readonly presetCampfire: string = '篝火夜话'
  static readonly presetOcean: string = '海边'
  static readonly groupNoise: string = '底噪'
  static readonly groupNature: string = '自然'
  static readonly groupIndoor: string = '室内'
  static readonly channelWhite: string = '白噪音'
  static readonly channelPink: string = '粉噪音'
  static readonly channelBrown: string = '棕噪音'
  static readonly channelRain: string = '雨声'
  static readonly channelWind: string = '风声'
  static readonly channelOcean: string = '海浪'
  static readonly channelCampfire: string = '篝火'
  static readonly channelPage: string = '翻书'
  static readonly channelKeyboard: string = '键盘'
  static readonly channelMurmur: string = '远处交谈'
  static readonly timerLabel: string = '睡眠定时'
  static readonly timerOff: string = '关闭'
  static readonly timerHint: string = '先开始播放，再设定定时器'
  static readonly volumeLabel: string = '音量'
  static readonly onLabel: string = '已开启'
  static readonly offLabel: string = '已关闭'
}

/** 预设配方：每路音量（-1 = 关闭），下标即声道序号（对齐安卓 SoundscapePreset） */
class SoundscapePresetSpec {
  readonly label: string
  readonly volumes: number[]

  constructor(label: string, volumes: number[]) {
    this.label = label
    this.volumes = volumes
  }
}

/** 预设表（对齐安卓 SoundscapeViewModel.SoundscapePreset） */
const PRESETS: SoundscapePresetSpec[] = [
  // 自习室：雨声 + 翻书 + 少量粉噪底
  new SoundscapePresetSpec(SoundscapeText.presetStudy, [-1, 20, -1, 70, -1, -1, -1, 45, -1, -1]),
  // 睡前：棕噪 + 风声
  new SoundscapePresetSpec(SoundscapeText.presetSleep, [-1, -1, 70, -1, 40, -1, -1, -1, -1, -1]),
  // 宿舍夜：键盘 + 远处交谈 + 少量棕噪底
  new SoundscapePresetSpec(SoundscapeText.presetDorm, [-1, -1, 15, -1, -1, -1, -1, -1, 55, 45]),
  // 篝火夜话：篝火 + 风 + 少量棕噪底
  new SoundscapePresetSpec(SoundscapeText.presetCampfire, [-1, -1, 20, -1, 30, -1, 70, -1, -1, -1]),
  // 海边：海浪 + 风 + 少量白噪底
  new SoundscapePresetSpec(SoundscapeText.presetOcean, [10, -1, -1, -1, 25, 75, -1, -1, -1, -1]),
]

/** 全部声道（下标即枚举值，与引擎混音序一致） */
const ALL_CHANNELS: SoundChannel[] = [
  SoundChannel.WHITE_NOISE, SoundChannel.PINK_NOISE, SoundChannel.BROWN_NOISE,
  SoundChannel.RAIN, SoundChannel.WIND, SoundChannel.OCEAN, SoundChannel.CAMPFIRE,
  SoundChannel.PAGE_FLIP, SoundChannel.KEYBOARD, SoundChannel.MURMUR,
]

/** 混音台分组（顺序即展示序，对齐安卓 NOISE/NATURE/INDOOR_CHANNELS） */
const NOISE_CHANNELS: number[] = [
  SoundChannel.WHITE_NOISE, SoundChannel.PINK_NOISE, SoundChannel.BROWN_NOISE,
]
const NATURE_CHANNELS: number[] = [
  SoundChannel.RAIN, SoundChannel.WIND, SoundChannel.OCEAN, SoundChannel.CAMPFIRE,
]
const INDOOR_CHANNELS: number[] = [
  SoundChannel.PAGE_FLIP, SoundChannel.KEYBOARD, SoundChannel.MURMUR,
]

/** 睡眠定时可选档位（分钟）；0 = 关闭（对齐安卓 TIMER_OPTIONS） */
const TIMER_OPTIONS: number[] = [0, 15, 30, 60]

/** 定时到点淡出时长（ms） */
const FADE_OUT_MS: number = 3000

/** 可视化条形相位步进（每次重绘推进量） */
const BAR_PHASE_STEP: number = 0.09

/** 可视化条形重绘间隔（ms） */
const BAR_INTERVAL_MS: number = 80

/** 单路声道的 UI 状态（开关 + 独立音量，@Trace 支撑行内精细刷新） */
@ObservedV2
class SoundChannelUi {
  readonly channel: SoundChannel
  @Trace enabled: boolean = false
  @Trace volume: number = SOUNDSCAPE_DEFAULT_VOLUME

  constructor(channel: SoundChannel) {
    this.channel = channel
  }
}

@Builder
export function SoundscapeBuilder() {
  SoundscapePage()
}

@ComponentV2
export struct SoundscapePage {
  @Local playing: boolean = false
  @Local channels: SoundChannelUi[] = []
  @Local activePresetIndex: number = -1
  @Local timerMinutes: number = 0
  @Local timerRemainingSec: number = 0
  @Local barPhase: number = 0

  private engine: SoundscapeEngine = new SoundscapeEngine()
  private barsCtx: CanvasRenderingContext2D = new CanvasRenderingContext2D(
    new RenderingContextSettings(true))
  private timerId: number = -1
  private barsTimerId: number = -1
  /** 定时到点是否已进入淡出段（只触发一次） */
  private fadeStarted: boolean = false

  aboutToAppear(): void {
    const list: SoundChannelUi[] = []
    for (let i: number = 0; i < SOUND_CHANNEL_COUNT && i < ALL_CHANNELS.length; i++) {
      list.push(new SoundChannelUi(ALL_CHANNELS[i]))
    }
    this.channels = list
    this.drawBars()
  }

  aboutToDisappear(): void {
    this.clearTimers()
    this.engine.release()
  }

  /** 清定时器（定时 + 条形动画） */
  private clearTimers(): void {
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
    if (this.barsTimerId !== -1) {
      clearInterval(this.barsTimerId)
      this.barsTimerId = -1
    }
  }

  // ==================== 交互 ====================

  /** 播放 / 停止切换（停止时同时取消睡眠定时与淡出） */
  private togglePlay(): void {
    LabHaptic.medium()
    if (this.playing) {
      this.stopPlayback()
    } else {
      this.engine.start().then((ok: boolean) => {
        this.playing = ok
        if (ok) {
          this.startBars()
        }
      })
    }
  }

  /** 停止播放并复位定时（暂停 / 定时到点 / 离开页面共用） */
  private stopPlayback(): void {
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
    this.timerMinutes = 0
    this.timerRemainingSec = 0
    this.fadeStarted = false
    this.engine.stop()
    this.playing = false
    this.stopBars()
  }

  /** 开关某路声道（手动调整即脱离预设） */
  private toggleChannel(channel: SoundChannel): void {
    LabHaptic.light()
    const ui: SoundChannelUi | undefined = this.channels[channel]
    if (ui === undefined) {
      return
    }
    ui.enabled = !ui.enabled
    this.activePresetIndex = -1
    this.engine.setChannelEnabled(channel, ui.enabled)
  }

  /** 设置某路音量（0..100，越界自动夹取；手动调整即脱离预设） */
  private changeVolume(channel: SoundChannel, volume: number): void {
    const coerced: number = Math.min(100, Math.max(0, volume))
    const ui: SoundChannelUi | undefined = this.channels[channel]
    if (ui === undefined || ui.volume === coerced) {
      return
    }
    ui.volume = coerced
    this.activePresetIndex = -1
    this.engine.setVolume(channel, coerced)
  }

  /** 一键套用预设（同时设置开关与音量；未列出的声道关闭） */
  private applyPreset(index: number): void {
    if (index < 0 || index >= PRESETS.length) {
      return
    }
    LabHaptic.selection()
    const preset: SoundscapePresetSpec = PRESETS[index]
    for (let c: number = 0; c < SOUND_CHANNEL_COUNT; c++) {
      const ui: SoundChannelUi | undefined = this.channels[c]
      if (ui === undefined) {
        continue
      }
      const volume: number = preset.volumes[c]
      ui.enabled = volume >= 0
      if (volume >= 0) {
        ui.volume = volume
      }
      this.engine.setChannelEnabled(c, volume >= 0)
      if (volume >= 0) {
        this.engine.setVolume(c, volume)
      }
    }
    this.activePresetIndex = index
  }

  /**
   * 设置睡眠定时（分钟；0 = 关闭）。仅在播放中可设置新定时；
   * 到点先淡出 FADE_OUT_MS 再停止（对齐安卓 SoundscapeViewModel.setTimer）。
   */
  private selectTimer(minutes: number): void {
    LabHaptic.selection()
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
    this.fadeStarted = false
    this.engine.cancelFadeOut()

    if (minutes <= 0) {
      this.timerMinutes = 0
      this.timerRemainingSec = 0
      return
    }
    if (!this.playing) {
      return
    }
    this.timerMinutes = minutes
    this.timerRemainingSec = minutes * 60
    this.timerId = setInterval(() => {
      this.timerTick()
    }, 1000)
  }

  /** 睡眠定时每秒步进：剩余 ≤3s 触发一次淡出，到 0 停止并复位 */
  private timerTick(): void {
    this.timerRemainingSec -= 1
    if (this.timerRemainingSec <= FADE_OUT_MS / 1000 && !this.fadeStarted) {
      this.fadeStarted = true
      this.engine.fadeOut(FADE_OUT_MS)
    }
    if (this.timerRemainingSec <= 0) {
      this.stopPlayback()
    }
  }

  // ==================== 条形动画 ====================

  private startBars(): void {
    if (this.barsTimerId !== -1) {
      return
    }
    this.barsTimerId = setInterval(() => {
      this.barPhase += BAR_PHASE_STEP
      this.drawBars()
    }, BAR_INTERVAL_MS)
  }

  private stopBars(): void {
    if (this.barsTimerId !== -1) {
      clearInterval(this.barsTimerId)
      this.barsTimerId = -1
    }
    this.barPhase = 0
    this.drawBars()
  }

  /** 声景可视化条形（5 根，白）：播放中按正弦相位起伏，停止为静止条 */
  private drawBars(): void {
    const width: number = 56
    const height: number = 40
    const ctx: CanvasRenderingContext2D = this.barsCtx
    ctx.clearRect(0, 0, width, height)
    const count: number = 5
    const barWidth: number = width / (count * 2 - 1)
    const gap: number = barWidth
    ctx.fillStyle = DesignColor.ON_PRIMARY
    for (let i: number = 0; i < count; i++) {
      const center: number = i / (count - 1)
      const wave: number = Math.sin((center * 1.6 + this.barPhase * 2) * Math.PI) * 0.5 + 0.5
      const barHeight: number = height * (0.25 + 0.75 * wave)
      const left: number = i * (barWidth + gap)
      const top: number = (height - barHeight) / 2
      const r: number = barWidth / 2
      ctx.beginPath()
      ctx.moveTo(left + r, top)
      ctx.lineTo(left + barWidth - r, top)
      ctx.arc(left + barWidth - r, top + r, r, -Math.PI / 2, 0, false)
      ctx.lineTo(left + barWidth, top + barHeight - r)
      ctx.arc(left + barWidth - r, top + barHeight - r, r, 0, Math.PI / 2, false)
      ctx.lineTo(left + r, top + barHeight)
      ctx.arc(left + r, top + barHeight - r, r, Math.PI / 2, Math.PI, false)
      ctx.lineTo(left, top + r)
      ctx.arc(left + r, top + r, r, Math.PI, Math.PI * 1.5, false)
      ctx.closePath()
      ctx.fill()
    }
  }

  // ==================== 文案 / 视觉映射 ====================

  private channelLabel(channel: SoundChannel): string {
    switch (channel) {
      case SoundChannel.WHITE_NOISE:
        return SoundscapeText.channelWhite
      case SoundChannel.PINK_NOISE:
        return SoundscapeText.channelPink
      case SoundChannel.BROWN_NOISE:
        return SoundscapeText.channelBrown
      case SoundChannel.RAIN:
        return SoundscapeText.channelRain
      case SoundChannel.WIND:
        return SoundscapeText.channelWind
      case SoundChannel.OCEAN:
        return SoundscapeText.channelOcean
      case SoundChannel.CAMPFIRE:
        return SoundscapeText.channelCampfire
      case SoundChannel.PAGE_FLIP:
        return SoundscapeText.channelPage
      case SoundChannel.KEYBOARD:
        return SoundscapeText.channelKeyboard
      default:
        return SoundscapeText.channelMurmur
    }
  }

  private channelIcon(channel: SoundChannel): Resource {
    switch (channel) {
      case SoundChannel.WHITE_NOISE:
        return $r('sys.symbol.waveform')
      case SoundChannel.PINK_NOISE:
        return $r('sys.symbol.slider_horizontal_2')
      case SoundChannel.BROWN_NOISE:
        return $r('sys.symbol.moon')
      case SoundChannel.RAIN:
        return $r('sys.symbol.heavy_rain')
      case SoundChannel.WIND:
        return $r('sys.symbol.air')
      case SoundChannel.OCEAN:
        return $r('sys.symbol.water_waves')
      case SoundChannel.CAMPFIRE:
        return $r('sys.symbol.flame')
      case SoundChannel.PAGE_FLIP:
        return $r('sys.symbol.book_pages')
      case SoundChannel.KEYBOARD:
        return $r('sys.symbol.keyboard')
      default:
        return $r('sys.symbol.person_2_fill')
    }
  }

  /** 图标容器浅底（对齐安卓 channelContainerColor：白/雨/海浪蓝、粉/翻书紫、棕/篝火橙、风/键盘青、交谈粉） */
  private channelContainerColor(channel: SoundChannel): number {
    switch (channel) {
      case SoundChannel.PINK_NOISE:
      case SoundChannel.PAGE_FLIP:
        return DesignColor.ICON_CONTAINER_PURPLE
      case SoundChannel.BROWN_NOISE:
      case SoundChannel.CAMPFIRE:
        return DesignColor.ICON_CONTAINER_ORANGE
      case SoundChannel.WIND:
      case SoundChannel.KEYBOARD:
        return DesignColor.ICON_CONTAINER_TEAL
      case SoundChannel.MURMUR:
        return DesignColor.ICON_CONTAINER_PINK
      default:
        return DesignColor.ICON_CONTAINER_BLUE
    }
  }

  private channelIconTint(channel: SoundChannel): number {
    switch (channel) {
      case SoundChannel.PINK_NOISE:
      case SoundChannel.PAGE_FLIP:
        return DesignColor.ICON_FOREGROUND_PURPLE
      case SoundChannel.BROWN_NOISE:
      case SoundChannel.CAMPFIRE:
        return DesignColor.ICON_FOREGROUND_ORANGE
      case SoundChannel.WIND:
      case SoundChannel.KEYBOARD:
        return DesignColor.ICON_FOREGROUND_TEAL
      case SoundChannel.MURMUR:
        return DesignColor.ICON_FOREGROUND_PINK
      default:
        return DesignColor.ICON_FOREGROUND_BLUE
    }
  }

  /** 定时档位文案 */
  private timerOptionLabel(minutes: number): string {
    if (minutes === 0) {
      return SoundscapeText.timerOff
    }
    return `${minutes} 分钟`
  }

  /** 剩余时间 mm:ss（秒补零，tnum） */
  private timerRemainingLabel(): string {
    const total: number = Math.max(0, this.timerRemainingSec)
    const minutes: number = Math.floor(total / 60)
    const seconds: number = total % 60
    const secondText: string = seconds < 10 ? `0${seconds}` : `${seconds}`
    return `剩余 ${minutes}:${secondText}`
  }

  // ==================== 构建 ====================

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值，
      // 子树内滑杆等画布经重建后的 onReady/onAreaChange 以新令牌重绘
      // （机制见 view/common/ThemeRefresh.ets；播放启停保持在宿主之外不受重建影响）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: SoundscapeText.title })

        Scroll() {
          Column({ space: DesignDimens.CARD_SPACING }) {
            this.HeroCard()
            this.PresetSection()
            this.MixerSection()
            this.TimerCard()
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
    // 离开可视区（被其他页面覆盖）即停止播放与定时，防音频后台泄漏
    .onHidden(() => {
      this.stopPlayback()
    })
  }

  /** hero 控制卡：品牌渐变底 + 白字 + 白胶囊 CTA + 条形可视化 */
  @Builder
  HeroCard() {
    Column() {
      Row() {
        Column() {
          Text(this.playing ? SoundscapeText.playing : SoundscapeText.stopped)
            .fontSize(DesignFontSize.HEADLINE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.ON_PRIMARY)

          if (this.activePresetIndex >= 0) {
            Text(PRESETS[this.activePresetIndex].label)
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor(DesignColor.GLASS_TEXT_ACTIVE)
              .margin({ top: DesignDimens.SPACE_XXS })
          }
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        Canvas(this.barsCtx)
          .width(56)
          .height(40)
          .onReady(() => {
            this.drawBars()
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Row() {
        Text(this.playing ? SoundscapeText.stop : SoundscapeText.play)
          .fontSize(DesignFontSize.BUTTON)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.PRIMARY_DARK)
      }
      .width('100%')
      .height(48)
      .justifyContent(FlexAlign.Center)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .backgroundColor(DesignColor.ON_PRIMARY)
      .margin({ top: DesignDimens.SPACE_MD })
      .onClick(() => {
        this.togglePlay()
      })
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .linearGradient({
      angle: 135,
      colors: [
        [DesignColor.AUTH_GRADIENT_TOP, 0],
        [DesignColor.AUTH_GRADIENT_MID, 0.5],
        [DesignColor.AUTH_GRADIENT_BOTTOM, 1],
      ],
    })
  }

  /** 预设场景横向胶囊 chips：选中 = primary 实底白字，未选中 = surfaceVariant + 描边 */
  @Builder
  PresetSection() {
    Column() {
      Text(SoundscapeText.presetsLabel)
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)

      Scroll() {
        Row() {
          ForEach(PRESETS, (preset: SoundscapePresetSpec, index: number) => {
            Text(preset.label)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(this.activePresetIndex === index ? DesignColor.ON_PRIMARY
                : DesignColor.TEXT_PRIMARY)
              .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD,
                top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
              .borderRadius(DesignDimens.RADIUS_FULL)
              .backgroundColor(this.activePresetIndex === index ? DesignColor.PRIMARY
                : DesignColor.SURFACE_VARIANT)
              .borderWidth(1)
              .borderColor(this.activePresetIndex === index ? DesignColor.PRIMARY
                : DesignColor.OUTLINE)
              .margin({ right: DesignDimens.SPACE_XS })
              .onClick(() => {
                this.applyPreset(index)
              })
          }, (preset: SoundscapePresetSpec, index: number) => `${preset.label}_${index}`)
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

  /** 混音台：底噪 / 自然 / 室内三张分组卡 */
  @Builder
  MixerSection() {
    Column({ space: DesignDimens.CARD_SPACING }) {
      this.MixerGroup(SoundscapeText.groupNoise, NOISE_CHANNELS)
      this.MixerGroup(SoundscapeText.groupNature, NATURE_CHANNELS)
      this.MixerGroup(SoundscapeText.groupIndoor, INDOOR_CHANNELS)
    }
    .width('100%')
  }

  /** 单分组卡：组标题 + 声道行（图标容器 + 名称 + 开关 + 音量滑杆），组内发丝线分隔 */
  @Builder
  MixerGroup(title: string, groupChannels: number[]) {
    Column() {
      Text(title)
        .fontSize(DesignFontSize.CARD_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)

      ForEach(groupChannels, (channel: number, index: number) => {
        if (index > 0) {
          Divider()
            .strokeWidth(0.5)
            .color(DesignColor.DIVIDER)
            .width('100%')
            .margin({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
        }
        ChannelRow({
          ui: this.channels[channel],
          label: this.channelLabel(channel),
          icon: this.channelIcon(channel),
          containerColor: this.channelContainerColor(channel),
          iconTint: this.channelIconTint(channel),
          onToggle: () => {
            this.toggleChannel(channel)
          },
          onVolumeChange: (volume: number) => {
            this.changeVolume(channel, volume)
          },
        })
      }, (channel: number) => `channel_${channel}`)
    }
    .alignItems(HorizontalAlign.Start)
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

  /** 睡眠定时卡：关闭 / 15 / 30 / 60 分钟四档；仅播放中可设置；选中档显示剩余时间 */
  @Builder
  TimerCard() {
    Column() {
      Text(SoundscapeText.timerLabel)
        .fontSize(DesignFontSize.CARD_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)

      Row({ space: DesignDimens.SPACE_XS }) {
        ForEach(TIMER_OPTIONS, (minutes: number) => {
          Column() {
            Text(this.timerOptionLabel(minutes))
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(this.timerOptionColor(minutes))
          }
          .layoutWeight(1)
          .height(36)
          .justifyContent(FlexAlign.Center)
          .borderRadius(DesignDimens.RADIUS_MD)
          .backgroundColor(minutes === this.timerMinutes ? DesignColor.PRIMARY_SOFT
            : DesignColor.SURFACE_VARIANT)
          .borderWidth(1)
          .borderColor(minutes === this.timerMinutes ? DesignColor.PRIMARY
            : Color.Transparent)
          .onClick(() => {
            if (this.playing || minutes === 0) {
              this.selectTimer(minutes)
            }
          })
        }, (minutes: number) => minutes.toString())
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_SM })

      if (this.timerMinutes > 0) {
        Text(this.timerRemainingLabel())
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .fontFeature(DesignFontFeature.TNUM)
          .margin({ top: DesignDimens.SPACE_SM })
      } else if (!this.playing) {
        Text(SoundscapeText.timerHint)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .margin({ top: DesignDimens.SPACE_XS })
      }
    }
    .alignItems(HorizontalAlign.Start)
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

  /** 定时档位文字色：选中 = onPrimarySoft；可选 = textSecondary；禁用 = textTertiary */
  private timerOptionColor(minutes: number): number {
    if (minutes === this.timerMinutes) {
      return DesignColor.ON_PRIMARY_SOFT
    }
    if (this.playing || minutes === 0) {
      return DesignColor.TEXT_SECONDARY
    }
    return DesignColor.TEXT_TERTIARY
  }
}

/**
 * 单路声道行：32vp 图标容器 + 名称 + 自绘开关；开启后展开独立音量滑杆（0-100）。
 * 对齐安卓 ChannelRow（56dp 行高、44dp 起始缩进）。
 */
@ComponentV2
struct ChannelRow {
  @Param ui: SoundChannelUi = new SoundChannelUi(SoundChannel.WHITE_NOISE)
  @Param label: string = ''
  @Param icon: Resource = $r('sys.symbol.waveform')
  @Param containerColor: number = DesignColor.ICON_CONTAINER_BLUE
  @Param iconTint: number = DesignColor.ICON_FOREGROUND_BLUE
  @Event onToggle: () => void = () => {
  }
  @Event onVolumeChange: (volume: number) => void = (volume: number) => {
  }

  build() {
    Column() {
      Row() {
        Column() {
          SymbolGlyph(this.icon)
            .fontSize(20)
            .fontColor([this.iconTint])
        }
        .width(32)
        .height(32)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .borderRadius(DesignDimens.RADIUS_MD)
        .backgroundColor(this.containerColor)

        Text(this.label)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .margin({ left: DesignDimens.SPACE_SM })
          .layoutWeight(1)

        SoundToggle({
          checked: this.ui.enabled,
          accessibilityLabel: this.label,
          onChange: () => {
            this.onToggle()
          },
        })
      }
      .width('100%')
      .height(56)
      .alignItems(VerticalAlign.Center)

      if (this.ui.enabled) {
        Row() {
          VolumeSlider({
            volume: this.ui.volume,
            onChange: (volume: number) => {
              this.onVolumeChange(volume)
            },
          })
            .layoutWeight(1)

          Text(this.ui.volume.toString())
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .fontFeature(DesignFontFeature.TNUM)
            .margin({ left: DesignDimens.SPACE_SM })
            .width(24)
            .textAlign(TextAlign.End)
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
        .padding({ left: 44 })
      }
    }
    .width('100%')
  }
}

/**
 * 自绘开关（48×28 胶囊）：白圆拇指左右平移，180ms 过渡（对齐安卓 SoundToggle）。
 */
@ComponentV2
struct SoundToggle {
  @Param checked: boolean = false
  @Param accessibilityLabel: string = ''
  @Event onChange: () => void = () => {
  }

  build() {
    Stack({ alignContent: Alignment.Start }) {
      Column() {
        Circle()
          .width(20)
          .height(20)
          .fill(DesignColor.SURFACE)
          .stroke(DesignColor.OUTLINE)
          .strokeWidth(1)
          .translate({ x: this.checked ? 20 : 0 })
          .animation({ duration: 180, curve: Curve.EaseInOut })
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Start)
      .padding({ left: 4 })
    }
    .width(48)
    .height(28)
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor(this.checked ? DesignColor.PRIMARY : DesignColor.SURFACE_VARIANT)
    .borderWidth(1)
    .borderColor(this.checked ? DesignColor.PRIMARY : DesignColor.OUTLINE)
    .accessibilityText(this.checked ? `${this.accessibilityLabel}，${SoundscapeText.onLabel}`
      : `${this.accessibilityLabel}，${SoundscapeText.offLabel}`)
    .onClick(() => {
      this.onChange()
    })
  }
}

/**
 * 音量滑杆（自绘，无滑块默认样式）：Canvas 轨道 + 拖拽/点击定位，跟随手指无动画。
 * 对齐安卓 VolumeSlider（4vp 轨道 + 描边圆拇指）。
 */
@ComponentV2
struct VolumeSlider {
  @Param volume: number = 0
  @Event onChange: (volume: number) => void = (volume: number) => {
  }

  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(
    new RenderingContextSettings(true))
  private sliderWidth: number = 0

  build() {
    Canvas(this.ctx)
      .width('100%')
      .height(32)
      .onAreaChange((oldValue: Area, newValue: Area) => {
        this.sliderWidth = Number(newValue.width)
        this.draw()
      })
      .onReady(() => {
        this.draw()
      })
      .onTouch((event: TouchEvent) => {
        this.onSliderTouch(event)
      })
      .accessibilityText(`${SoundscapeText.volumeLabel} ${this.volume}`)
  }

  @Monitor('volume')
  onVolumeChanged(): void {
    this.draw()
  }

  /** 滑杆绘制：轨道 + 已填充 + 描边圆拇指 */
  private draw(): void {
    const width: number = this.sliderWidth
    if (width <= 0) {
      return
    }
    const height: number = 32
    const trackHeight: number = 4
    const trackY: number = height / 2
    const fraction: number = Math.min(1, Math.max(0, this.volume / 100))
    const ctx: CanvasRenderingContext2D = this.ctx
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
    const thumbR: number = 8
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

  /** 手工圆角矩形（SDK 17 无 roundRect，与 DrumPage 同法） */
  private roundRect(x: number, y: number, w: number, h: number, r: number): void {
    const ctx: CanvasRenderingContext2D = this.ctx
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

  /** 拖拽 / 点击定位音量 */
  private onSliderTouch(event: TouchEvent): void {
    const touch: TouchObject | undefined = event.touches.length > 0 ? event.touches[0] : undefined
    if (touch === undefined || this.sliderWidth <= 0) {
      return
    }
    if (event.type === TouchType.Down || event.type === TouchType.Move) {
      const fraction: number = Math.min(1, Math.max(0, touch.x / this.sliderWidth))
      this.onChange(Math.round(fraction * 100))
    }
  }
}
