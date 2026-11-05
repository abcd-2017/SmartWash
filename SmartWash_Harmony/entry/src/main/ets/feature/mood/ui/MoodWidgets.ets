// 情绪气象站模块内共享件 — 对齐安卓 feature/mood/ui/MoodWidgets.kt + MoodLevel.kt + MoodConstant.kt。
//
// 内容：
// 1) 等级词典（端上五色块单一事实来源，颜色映射 DesignColor 既有 Token，禁自创色值）；
// 2) 月份/日期工具（ym 与本地日期互转、平移、月历网格计算）；
// 3) 今日/月历/趋势三页共用组件：心情色块行、词 chip、月切换条、小按钮胶囊、
//    错误态、分布条形、数据格、月历日格、当日详情弹窗。
//
// 已知坑遵守：@Builder 不按值传状态参数（状态型组件一律 @ComponentV2 + @Param/@Event）；
// 弹窗 onDismiss/onConfirm 先关窗再执行回调（本弹窗无导航，仍保持顺序一致）。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionTiming } from '../../../constant/DesignSystem'
import { MoodText } from '../api'

// ==================== 等级词典 ====================

/** 单个心情等级（对齐安卓 MoodLevel 枚举） */
export interface MoodLevelOption {
  value: number
  label: string
  /** 提交给后端的 color 快照（#RRGGBB，浅色主题令牌基准值；契约：快照决定历史色） */
  snapshotHex: string
}

/**
 * 五个心情色块词典（1暴雨 2阴 3多云 4晴 5大晴，与后端等级一一对应）。
 * 颜色一律映射到清氧设计系统既有 Token：
 *   1 暴雨 = danger（红粉，最差） · 2 阴 = textTertiary（灰） · 3 多云 = water（蓝） ·
 *   4 晴 = ongoing（暖阳橙） · 5 大晴 = primary（品牌绿，最好）。
 */
export const MOOD_LEVELS: Array<MoodLevelOption> = [
  { value: 1, label: MoodText.mood_level_rainstorm, snapshotHex: '#A8435A' },
  { value: 2, label: MoodText.mood_level_overcast, snapshotHex: '#A9AEA9' },
  { value: 3, label: MoodText.mood_level_cloudy, snapshotHex: '#4A9FD4' },
  { value: 4, label: MoodText.mood_level_sunny, snapshotHex: '#D98A26' },
  { value: 5, label: MoodText.mood_level_shining, snapshotHex: '#2D9B6A' }
]

/** 按等级取词典；越界（后端理论上 CHECK 拦截）返回 null */
export function moodLevelOption(level: number): MoodLevelOption | null {
  for (const item of MOOD_LEVELS) {
    if (item.value === level) {
      return item
    }
  }
  return null
}

/** 心情等级 → 心情名文案（暴雨/阴/多云/晴/大晴）；未映射 level 兜底「—」 */
export function moodLevelLabel(level: number): string {
  return moodLevelOption(level)?.label ?? MoodText.mood_trend_value_none
}

/** 心情等级 → 主题感知颜色（浅/深色自动适配，月历历史格除外——历史格按契约优先用快照色） */
export function moodLevelColor(level: number): number {
  switch (level) {
    case 1:
      return DesignColor.DANGER
    case 2:
      return DesignColor.TEXT_TERTIARY
    case 3:
      return DesignColor.WATER
    case 4:
      return DesignColor.ONGOING
    default:
      return DesignColor.PRIMARY
  }
}

/** 提交给后端的 color 快照（#RRGGBB，词典浅色基准值）；未映射 level 兜底品牌绿基准 */
export function moodSnapshotHex(level: number): string {
  return moodLevelOption(level)?.snapshotHex ?? '#2D9B6A'
}

// ==================== 月份 / 日期工具 ====================

function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

/** 当月的 ym（yyyy-MM，按本地时区） */
export function currentYm(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`
}

/** 今日的 dateKey（yyyy-MM-dd，按本地时区） */
export function todayKey(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

export interface MoodYm {
  year: number
  month: number
}

/** yyyy-MM → MoodYm；解析失败返回 null（调用方兜底回当前月） */
export function parseYm(ym: string): MoodYm | null {
  if (ym.length !== 7) {
    return null
  }
  const year = Number(ym.substring(0, 4))
  const month = Number(ym.substring(5, 7))
  if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) {
    return null
  }
  return { year, month }
}

/** ym 平移 delta 个月（负数为往前）；解析失败回当前月 */
export function shiftYm(ym: string, delta: number): string {
  const parsed = parseYm(ym)
  if (parsed === null) {
    return currentYm()
  }
  const d = new Date(parsed.year, parsed.month - 1 + delta, 1)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`
}

/** 月份展示文案（对齐安卓 mood_month_format「%d年%d月」） */
export function formatMonthText(ym: string): string {
  const parsed = parseYm(ym)
  if (parsed === null) {
    return ym
  }
  return `${parsed.year}年${parsed.month}月`
}

/** 当月天数 */
export function daysInMonth(ym: string): number {
  const parsed = parseYm(ym)
  if (parsed === null) {
    return 30
  }
  return new Date(parsed.year, parsed.month, 0).getDate()
}

/** 当月 1 号的周一偏移（周一=0 … 周日=6），与网格 offset 对齐 */
export function firstOffset(ym: string): number {
  const parsed = parseYm(ym)
  if (parsed === null) {
    return 0
  }
  const weekday = new Date(parsed.year, parsed.month - 1, 1).getDay() // 0=周日
  return (weekday + 6) % 7
}

/** 指定日期的 dateKey（yyyy-MM-dd） */
export function dateKeyOf(ym: string, day: number): string {
  const parsed = parseYm(ym)
  if (parsed === null) {
    return ''
  }
  return `${parsed.year}-${pad2(parsed.month)}-${pad2(day)}`
}

interface MoodDate {
  year: number
  month: number
  day: number
}

function parseDateKey(key: string): MoodDate | null {
  if (key.length !== 10) {
    return null
  }
  const year = Number(key.substring(0, 4))
  const month = Number(key.substring(5, 7))
  const day = Number(key.substring(8, 10))
  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
    return null
  }
  return { year, month, day }
}

/** 日期展示文案（对齐安卓 mood_day_date_pattern「M月d日」） */
export function formatDayText(dateKey: string): string {
  const parsed = parseDateKey(dateKey)
  if (parsed === null) {
    return dateKey
  }
  return `${parsed.month}月${parsed.day}日`
}

/** 今日文案（对齐安卓 mood_today_date_pattern「M月d日 EEEE」） */
export function formatTodayText(): string {
  const d = new Date()
  const weekLabels: Array<string> = ['日', '一', '二', '三', '四', '五', '六']
  return `${d.getMonth() + 1}月${d.getDate()}日 周${weekLabels[d.getDay()]}`
}

// ==================== 颜色工具 ====================

const HEX_REGEX: RegExp = /^#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?$/

/**
 * 解析后端返回的 color 快照（#RRGGBB / #RRGGBBAA）；非法/空值兜底回词典基准 hex。
 * 月历热图历史格按契约「历史色以此为准」优先消费本函数。
 */
export function normalizeMoodHex(hex: string | null | undefined, level: number): string {
  if (hex !== null && hex !== undefined && HEX_REGEX.test(hex.trim())) {
    return hex.trim()
  }
  return moodSnapshotHex(level)
}

/** 快照色亮度（0..1，近似相对亮度）；用于决定格内日期数字用墨字还是表面色 */
function moodSnapshotLuminance(hex: string): number {
  const normalized = normalizeMoodHex(hex, 0)
  if (normalized.length < 7) {
    return 0
  }
  const r = Number.parseInt(normalized.substring(1, 3), 16)
  const g = Number.parseInt(normalized.substring(3, 5), 16)
  const b = Number.parseInt(normalized.substring(5, 7), 16)
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255
}

// ==================== 月历格子模型 ====================

/** 月历网格单元格（day=0 表示月初前的占位空格） */
export interface MoodCalendarCell {
  day: number
  dateKey: string
  level: number
  colorHex: string
  isToday: boolean
  isFuture: boolean
  hasRecord: boolean
}

// ==================== 共享组件 ====================

/**
 * 五个心情色块行（等宽横向铺满）——今日记录页的视觉锚点，一次点按即存。
 * 用 @ComponentV2 + @Param 承载选中态（@Builder 按值传状态不响应更新，见已知坑）。
 */
@ComponentV2
export struct MoodLevelRow {
  @Param selectedLevel: number = 0
  @Event onSelect: (level: number) => void = () => {
  }

  build() {
    Row({ space: DesignDimens.SPACE_SM }) {
      ForEach(MOOD_LEVELS, (item: MoodLevelOption) => {
        Column() {
          Column()
            .width('100%')
            .height(56)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .backgroundColor(moodLevelColor(item.value))
            .border({
              width: this.selectedLevel === item.value ? 2 : 0,
              color: DesignColor.TEXT_PRIMARY
            })

          Text(item.label)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(this.selectedLevel === item.value
              ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
            .fontColor(this.selectedLevel === item.value
              ? DesignColor.TEXT_PRIMARY : DesignColor.TEXT_SECONDARY)
            .margin({ top: DesignDimens.SPACE_XS })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        .onClick(() => {
          this.onSelect(item.value)
        })
      }, (item: MoodLevelOption) => `${item.value}`)
    }
    .width('100%')
  }
}

/**
 * 词 chip（"累/顺/静/燥/燃"等，可选一个，再点取消）：
 * 选中 = 品牌底白字，未选中 = 白底描边（对齐安卓 MoodWordChip）。
 */
@ComponentV2
export struct MoodWordChip {
  @Param text: string = ''
  @Param selected: boolean = false
  // 命名避开 CustomComponent 基类内置 onClick 属性（否则类型冲突）
  @Event onChipClick: () => void = () => {
  }

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      // 字重恒 SemiBold：字重随选中切换会让文字宽度跳变（对齐安卓注释）
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor(this.selected ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      .padding({ left: 16, right: 16, top: 6, bottom: 6 })
      .backgroundColor(this.selected ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .borderWidth(1)
      .borderColor(this.selected ? DesignColor.PRIMARY : DesignColor.OUTLINE)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .onClick(() => {
        this.onChipClick()
      })
  }
}

/**
 * 月切换条：‹ · 月份文案 · ›（对齐安卓 MoodMonthSwitcher）。
 * 文本箭头字符 + 48vp 触控热区，不新增图标资源。
 */
@ComponentV2
export struct MoodMonthSwitcher {
  @Param monthText: string = ''
  @Event onPrev: () => void = () => {
  }
  @Event onNext: () => void = () => {
  }

  build() {
    Row() {
      Column() {
        Text('‹')
          .fontSize(24)
          .fontColor(DesignColor.TEXT_SECONDARY)
      }
      .width(DesignDimens.BACK_HIT_AREA)
      .height(DesignDimens.BACK_HIT_AREA)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .onClick(() => {
        this.onPrev()
      })

      Text(this.monthText)
        .fontSize(DesignFontSize.BODY)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .textAlign(TextAlign.Center)
        .layoutWeight(1)

      Column() {
        Text('›')
          .fontSize(24)
          .fontColor(DesignColor.TEXT_SECONDARY)
      }
      .width(DesignDimens.BACK_HIT_AREA)
      .height(DesignDimens.BACK_HIT_AREA)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .onClick(() => {
        this.onNext()
      })
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
  }
}

/**
 * 小按钮胶囊（小按钮规格：12dp 圆角 / 横向 16dp / 36dp 高区间，对齐安卓 MoodSmallAction）。
 * 今日页页头入口（灰底墨字）与趋势页空态行动（浅绿底绿字）共用。
 */
@ComponentV2
export struct MoodSmallAction {
  @Param text: string = ''
  @Param containerColor: number = DesignColor.SURFACE_VARIANT
  @Param contentColor: number = DesignColor.TEXT_PRIMARY
  // 命名避开 CustomComponent 基类内置 onClick 属性（否则类型冲突）
  @Event onAction: () => void = () => {
  }

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor(this.contentColor)
      .padding({ left: 16, right: 16, top: 8, bottom: 8 })
      .backgroundColor(this.containerColor)
      .borderRadius(DesignDimens.RADIUS_MD)
      .onClick(() => {
        this.onAction()
      })
  }
}

/**
 * 加载失败态：说明发生了什么 + 重试按钮（对齐安卓 MoodErrorState，月历/趋势页共用）。
 */
@ComponentV2
export struct MoodErrorState {
  @Param message: string = ''
  @Event onRetry: () => void = () => {
  }

  build() {
    Column() {
      Text(this.message)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .textAlign(TextAlign.Center)

      Text(MoodText.mood_action_retry)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.PRIMARY)
        .padding({ left: 16, right: 16, top: 8, bottom: 8 })
        .backgroundColor(DesignColor.SURFACE_VARIANT)
        .borderRadius(DesignDimens.RADIUS_MD)
        .margin({ top: DesignDimens.SPACE_MD })
        .onClick(() => {
          this.onRetry()
        })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }
}

/**
 * 分布条形行（趋势页）：色点 + 等级名 + 轨道条形（宽度按占比）+ 计数（对齐安卓 MoodDistributionBar）。
 */
@ComponentV2
export struct MoodDistributionBar {
  @Param label: string = ''
  @Param color: number = DesignColor.PRIMARY
  @Param count: number = 0
  @Param fraction: number = 0

  build() {
    Row() {
      Column()
        .width(8)
        .height(8)
        .borderRadius(4)
        .backgroundColor(this.color)

      Text(this.label)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .margin({ left: DesignDimens.SPACE_XS })
        .width(44)

      Row() {
        if (this.count > 0) {
          Row()
            .height(DesignDimens.SPACE_MD)
            .width(`${(Math.max(0.04, Math.min(1, this.fraction)) * 100).toFixed(0)}%`)
            .backgroundColor(this.color)
            .borderRadius(DesignDimens.RADIUS_FULL)
        }
      }
      .layoutWeight(1)
      .height(DesignDimens.SPACE_MD)
      .backgroundColor(DesignColor.SURFACE_VARIANT)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .align(Alignment.Start)

      Text(`${this.count}`)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .fontFeature(DesignFontFeature.TNUM)
        .textAlign(TextAlign.End)
        .margin({ left: DesignDimens.SPACE_SM })
        .width(36)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
  }
}

/**
 * 数据格：标签（bodySmall）+ 数字（tnum）+ 可选单位（对齐安卓 MoodStatCell）。
 * valueSize 默认 HEADLINE 28（一屏一锚点，仅月均值用大字；记录天数传 AMOUNT_MEDIUM 18）。
 */
@ComponentV2
export struct MoodStatCell {
  @Param label: string = ''
  @Param value: string = ''
  @Param suffix: string = ''
  @Param valueSize: number = DesignFontSize.HEADLINE

  build() {
    Column() {
      Text(this.label)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontColor(DesignColor.TEXT_SECONDARY)

      Row() {
        Text(this.value)
          .fontSize(this.valueSize)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .fontFeature(DesignFontFeature.TNUM)

        if (this.suffix.length > 0) {
          Text(this.suffix)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ left: DesignDimens.SPACE_XXS, bottom: DesignDimens.SPACE_XXS })
        }
      }
      .alignItems(VerticalAlign.Bottom)
    }
    .alignItems(HorizontalAlign.Start)
  }
}

/**
 * 月历单日格：圆角方块着色 + 日期数字，今日描边；无记录灰格 / 未来格更浅（对齐安卓 MoodCalendarGrid）。
 * 历史色按契约优先用 color 快照（非法值兜底词典基准 hex）。
 */
@ComponentV2
export struct MoodDayCell {
  @Param day: number = 0
  @Param dateKey: string = ''
  @Param hasRecord: boolean = false
  @Param level: number = 0
  @Param colorHex: string = ''
  @Param isToday: boolean = false
  @Param isFuture: boolean = false
  @Event onSelect: (dateKey: string) => void = () => {
  }

  /** 格底：有记录 = 快照色；未来 = groupBackground；其余 = surfaceVariant */
  private fillColor(): string | number {
    if (this.hasRecord) {
      return this.colorHex
    }
    return this.isFuture ? DesignColor.GROUP_BACKGROUND : DesignColor.SURFACE_VARIANT
  }

  /** 日期数字色：彩色格按亮度自适应（墨字 / 表面色），未来格更浅 */
  private textColor(): number {
    if (this.hasRecord) {
      return moodSnapshotLuminance(this.colorHex) > 0.4 ? DesignColor.TEXT_PRIMARY : DesignColor.SURFACE
    }
    return this.isFuture ? DesignColor.TEXT_TERTIARY : DesignColor.TEXT_SECONDARY
  }

  build() {
    Column() {
      Column() {
        Text(`${this.day}`)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(this.textColor())
          .fontWeight(this.isToday ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
          .fontFeature(DesignFontFeature.TNUM)
      }
      .width('100%')
      .height(36)
      .borderRadius(DesignDimens.RADIUS_SMALL)
      .backgroundColor(this.fillColor())
      .border({
        width: this.isToday ? 1.5 : 0,
        color: DesignColor.TEXT_PRIMARY
      })
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
    }
    .width('100%')
    .height(44)
    .justifyContent(FlexAlign.Center)
    .onClick(() => {
      if (this.hasRecord) {
        this.onSelect(this.dateKey)
      }
    })
  }
}

/**
 * 当日详情弹窗：日期 + 心情色点与等级名 + 当日 word（契约月历不含 note，不展示备注）。
 * 对齐安卓 MoodDayDetailDialog。
 */
@CustomDialog
export struct MoodDayDetailDialog {
  // 由父组件经 CustomDialogController 注入（框架约定，不可自赋初值）
  controller: CustomDialogController
  @Prop dateText: string = ''
  @Prop level: number = 0
  @Prop word: string = ''
  @State entered: boolean = false
  onDismiss: () => void = () => {
  }

  /** 弹窗统一入场（对齐安卓 dialogEnterMotion）：首帧后再触发，弹簧 scale 0.92→1 + fade */
  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  build() {
    // 外层留白容器：customStyle 弹窗铺满屏幕宽度，卡片背景画在内层并留左右边距
    Column() {
      Column() {
        Text(this.dateText)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .width('100%')
          .margin({ bottom: DesignDimens.SPACE_SM })

        Row() {
          Column()
            .width(14)
            .height(14)
            .borderRadius(7)
            .backgroundColor(moodLevelColor(this.level))

          Text(`心情： ${moodLevelLabel(this.level)}`)
            .fontSize(DesignFontSize.BODY)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ left: DesignDimens.SPACE_XS })
        }
        .alignItems(VerticalAlign.Center)

        Text(this.word.length > 0 ? this.word : MoodText.mood_day_dialog_word_empty)
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(this.word.length > 0 ? DesignColor.TEXT_PRIMARY : DesignColor.TEXT_TERTIARY)
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XS })

        Button(MoodText.mood_day_dialog_close)
          .type(ButtonType.Normal)
          .width('100%')
          .height(44)
          .backgroundColor(Color.Transparent)
          .fontColor(DesignColor.PRIMARY)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .borderRadius(0)
          .margin({ top: DesignDimens.SPACE_MD })
          .onClick(() => {
            // 先关窗再执行回调（回调内可能导航，导航后弹窗实例已销毁）
            this.controller.close()
            this.onDismiss()
          })
      }
      .width('100%')
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: 20,
        bottom: 12
      })
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .shadow({
        radius: DesignElevation.FLOAT_SHADOW_RADIUS,
        color: DesignColor.SHADOW_COLOR_FLOAT,
        offsetX: 0,
        offsetY: DesignElevation.FLOAT_SHADOW_OFFSET
      })
      .scale({ x: this.entered ? 1 : 0.92, y: this.entered ? 1 : 0.92 })
      .opacity(this.entered ? 1 : 0)
      .onAppear(() => {
        this.playEnterMotion()
      })
    }
    .width('100%')
    .justifyContent(FlexAlign.Center)
    .padding({ left: DesignDimens.SPACE_XL, right: DesignDimens.SPACE_XL })
  }
}
