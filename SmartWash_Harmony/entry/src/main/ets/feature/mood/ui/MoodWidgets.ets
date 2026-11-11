// 情绪气象站模块内共享件 — 对齐安卓 feature/mood/ui/MoodWidgets.kt + MoodLevel.kt + MoodConstant.kt。
//
// 内容：
// 1) 等级词典（端上五色块单一事实来源，颜色映射设计系统资源色，禁自创色值）；
// 2) 月份/日期工具（ym 与本地日期互转、平移、月历网格计算）；
// 3) 今日/月历/趋势三页共用组件：心情色块行、词 chip、月切换条、小按钮胶囊、
//    错误态、分布条形、数据格、月历日格、当日详情弹窗。
//
// 已知坑遵守：@Builder 不按值传状态参数（状态型组件一律 @ComponentV2 + @Param/@Event）；
// 弹窗 onDismiss/onConfirm 先关窗再执行回调（本弹窗无导航，仍保持顺序一致）。

import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionTiming } from '../../../constant/DesignSystem'
import { MoodText } from '../api'
import { resColor } from '../../../utils/ResourceColor'
import { resString } from '../../../utils/ResourceString'

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
export function moodLevelColor(level: number): ResourceColor {
  switch (level) {
    case 1:
      return $r('app.color.DANGER')
    case 2:
      return $r('app.color.TEXT_TERTIARY')
    case 3:
      return $r('app.color.WATER')
    case 4:
      return $r('app.color.ONGOING')
    default:
      return $r('app.color.PRIMARY')
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

/** 今日文案（对齐安卓 mood_today_date_pattern「M月d日 EEEE」，星期用全称对齐 EEEE） */
export function formatTodayText(): string {
  const d = new Date()
  const weekLabels: Array<string> = [resString('mood_widgets_1'), resString('mood_widgets_2'), resString('mood_widgets_3'), resString('mood_widgets_4'), resString('mood_widgets_5'), resString('mood_widgets_6'), resString('mood_widgets_7')]
  return `${d.getMonth() + 1}月${d.getDate()}日 ${weekLabels[d.getDay()]}`
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

/**
 * sRGB 通道线性化（WCAG 2.x 相对亮度公式）：c ≤ 0.03928 除 12.92，否则 ((c+0.055)/1.055)^2.4。
 */
function linearizeChannel(channel: number): number {
  const c = channel / 255
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

/** '#RRGGBB' 快照色 → WCAG 相对亮度（0..1）；非法/空值经 normalizeMoodHex 兜底词典基准 hex */
function moodHexLuminance(hex: string): number {
  const normalized = normalizeMoodHex(hex, 0)
  if (normalized.length < 7) {
    return 0
  }
  const r = Number.parseInt(normalized.substring(1, 3), 16)
  const g = Number.parseInt(normalized.substring(3, 5), 16)
  const b = Number.parseInt(normalized.substring(5, 7), 16)
  return 0.2126 * linearizeChannel(r) + 0.7152 * linearizeChannel(g) + 0.0722 * linearizeChannel(b)
}

/** ARGB number 令牌色（资源色深浅两态值）→ WCAG 相对亮度（0..1） */
function moodTokenLuminance(color: number): number {
  const r = (color >>> 16) & 0xFF
  const g = (color >>> 8) & 0xFF
  const b = color & 0xFF
  return 0.2126 * linearizeChannel(r) + 0.7152 * linearizeChannel(g) + 0.0722 * linearizeChannel(b)
}

/** WCAG 对比度（正文红线 ≥4.5:1）；入参为两色的相对亮度，公式 (L1+0.05)/(L2+0.05) */
function moodContrastRatio(luminanceA: number, luminanceB: number): number {
  const la = luminanceA + 0.05
  const lb = luminanceB + 0.05
  return la > lb ? la / lb : lb / la
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
  @Local pressedLevel: number = 0
  @Event onSelect: (level: number) => void = () => {
  }

  build() {
    // 间距取 SPACE_XS(8)（对齐安卓审查 #4①：360 基准屏 SPACE_SM(12) 会让五等分块宽 ≈44.8vp
    // 跌破 48vp 触控红线，缩到 8 后块宽恰为 48）
    Row({ space: DesignDimens.SPACE_XS }) {
      ForEach(MOOD_LEVELS, (item: MoodLevelOption) => {
        Column() {
          Column()
            .width('100%')
            .height(56)
            // 卡内嵌套块圆角取 RADIUS_MD 档（对齐安卓审查 #14：CARD_RADIUS 16 是卡片档，跳档）
            .borderRadius(DesignDimens.RADIUS_MD)
            .backgroundColor(moodLevelColor(item.value))
            .border({
              width: this.selectedLevel === item.value ? 2 : 0,
              color: $r('app.color.TEXT_PRIMARY')
            })

          Text(item.label)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(this.selectedLevel === item.value
              ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
            .fontColor(this.selectedLevel === item.value
              ? $r('app.color.TEXT_PRIMARY') : $r('app.color.TEXT_SECONDARY'))
            .margin({ top: DesignDimens.SPACE_XS })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        // 按压反馈（对齐安卓 pressable scale 0.97，workbench cardChrome 同款模式）
        .scale({
          x: this.pressedLevel === item.value ? 0.97 : 1,
          y: this.pressedLevel === item.value ? 0.97 : 1
        })
        .animation({ duration: 100, curve: Curve.EaseOut })
        .onTouch((event: TouchEvent) => {
          if (event.type === TouchType.Down) {
            this.pressedLevel = item.value
          } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
            this.pressedLevel = 0
          }
        })
        // 读屏语义（对齐安卓审查 #6）：心情名挂到点击节点，读「暴雨，双击即可激活」
        .accessibilityText(item.label)
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
 * 选中 = 浅绿容器底深绿字，未选中 = 白底描边（对齐安卓 MoodWordChip）。
 */
@ComponentV2
export struct MoodWordChip {
  @Param text: string = ''
  @Param selected: boolean = false
  @Local pressed: boolean = false
  // 命名避开 CustomComponent 基类内置 onClick 属性（否则类型冲突）
  @Event onChipClick: () => void = () => {
  }

  build() {
    // 外层热区撑到 48×48、内容居中（对齐安卓审查 #4②：内层 Text 视觉规格不变），
    // onClick 从文字移到热区层
    Column() {
      Text(this.text)
        .fontSize(DesignFontSize.CAPTION)
        // 字重恒 SemiBold：字重随选中切换会让文字宽度跳变（对齐安卓注释）
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        // 选中态（对齐安卓审查 #8）：12sp 小字压品牌绿底白字仅 3.50:1 不过 AA——
        // 改 primaryContainer/onPrimaryContainer 成对令牌（浅色 ≈12:1，深色两值对调后同样达标）
        .fontColor(this.selected ? $r('app.color.ON_PRIMARY_CONTAINER') : $r('app.color.TEXT_SECONDARY'))
        .padding({ left: 16, right: 16, top: 6, bottom: 6 })
        .backgroundColor(this.selected ? $r('app.color.PRIMARY_CONTAINER') : $r('app.color.SURFACE'))
        .borderWidth(1)
        .borderColor(this.selected ? $r('app.color.PRIMARY_CONTAINER') : $r('app.color.OUTLINE'))
        .borderRadius(DesignDimens.RADIUS_FULL)
    }
    .constraintSize({ minWidth: 48, minHeight: 48 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    // 按压反馈（对齐安卓 pressable scale 0.97）
    .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
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
  @Local pressedSide: string = ''
  @Event onPrev: () => void = () => {
  }
  @Event onNext: () => void = () => {
  }

  build() {
    Row() {
      Column() {
        Text('‹')
          // 22 = titleLarge 档（对齐安卓审查 #21：24 非字阶档位）
          .fontSize(22)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_SECONDARY'))
      }
      .width(DesignDimens.BACK_HIT_AREA)
      .height(DesignDimens.BACK_HIT_AREA)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      // 读屏语义（对齐安卓审查 #7/MoodMonthArrow semantics）
      .accessibilityText(MoodText.mood_prev_month)
      .scale({ x: this.pressedSide === 'prev' ? 0.97 : 1, y: this.pressedSide === 'prev' ? 0.97 : 1 })
      .animation({ duration: 100, curve: Curve.EaseOut })
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.pressedSide = 'prev'
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.pressedSide = ''
        }
      })
      .onClick(() => {
        this.onPrev()
      })

      // 月份文案 = 卡片标题档 18（对齐安卓审查 #20：titleMedium，BODY 16 是正文档）
      Text(this.monthText)
        .fontSize(DesignFontSize.CARD_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .textAlign(TextAlign.Center)
        .layoutWeight(1)

      Column() {
        Text('›')
          .fontSize(22)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_SECONDARY'))
      }
      .width(DesignDimens.BACK_HIT_AREA)
      .height(DesignDimens.BACK_HIT_AREA)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .accessibilityText(MoodText.mood_next_month)
      .scale({ x: this.pressedSide === 'next' ? 0.97 : 1, y: this.pressedSide === 'next' ? 0.97 : 1 })
      .animation({ duration: 100, curve: Curve.EaseOut })
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.pressedSide = 'next'
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.pressedSide = ''
        }
      })
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
  @Param containerColor: ResourceColor = $r('app.color.SURFACE_VARIANT')
  @Param contentColor: ResourceColor = $r('app.color.TEXT_PRIMARY')
  @Local pressed: boolean = false
  // 命名避开 CustomComponent 基类内置 onClick 属性（否则类型冲突）
  @Event onAction: () => void = () => {
  }

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      // 字重 MEDIUM(500) 对齐安卓 labelMedium（审查 #36；36/48 视觉高之争待设计定调，
      // 维持 48 热区口径）
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.contentColor)
      // 垂直内边距 8 → 16 + 显式 48 高：把触控热区从 30vp 抬到 ≥48vp
      .padding({ left: 16, right: 16, top: 16, bottom: 16 })
      .height(48)
      .backgroundColor(this.containerColor)
      .borderRadius(DesignDimens.RADIUS_MD)
      // 按压反馈（对齐安卓 pressable scale 0.97）
      .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
      .animation({ duration: 100, curve: Curve.EaseOut })
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.pressed = true
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.pressed = false
        }
      })
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
  @Local pressed: boolean = false
  @Event onRetry: () => void = () => {
  }

  build() {
    Column() {
      Text(this.message)
        .fontSize(DesignFontSize.BODY)
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .textAlign(TextAlign.Center)

      Text(MoodText.mood_action_retry)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        // 重试绿字走深档 TEXT_GREEN（对齐安卓审查 #28：PRIMARY on SURFACE_VARIANT
        // 浅底仅 3.08:1 跌破 AA；2026-11-06 评审 R17/R18 专档）
        .fontColor($r('app.color.TEXT_GREEN'))
        .padding({ left: 16, right: 16, top: 8, bottom: 8 })
        .backgroundColor($r('app.color.SURFACE_VARIANT'))
        .borderRadius(DesignDimens.RADIUS_MD)
        .margin({ top: DesignDimens.SPACE_MD })
        // 按压反馈（对齐安卓 pressable scale 0.97）
        .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
        .animation({ duration: 100, curve: Curve.EaseOut })
        .onTouch((event: TouchEvent) => {
          if (event.type === TouchType.Down) {
            this.pressed = true
          } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
            this.pressed = false
          }
        })
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
  @Param color: ResourceColor = $r('app.color.PRIMARY')
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
        .fontColor($r('app.color.TEXT_SECONDARY'))
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
      .backgroundColor($r('app.color.SURFACE_VARIANT'))
      .borderRadius(DesignDimens.RADIUS_FULL)
      .align(Alignment.Start)

      Text(`${this.count}`)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.TEXT_PRIMARY'))
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
 * 数据格：标签（caption 12）+ 数字（tnum）+ 可选单位（对齐安卓 MoodStatCell）。
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
      // label = caption 12 档（对齐安卓 bodySmall 12/400，报告 #30/#39 两端口径映射）
      Text(this.label)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.TEXT_SECONDARY'))

      Row() {
        Text(this.value)
          .fontSize(this.valueSize)
          // 字重随 valueSize 联动（对齐安卓审查 #29：AmountMedium 18 档 SemiBold，
          // 仅 HEADLINE 数据大字锚点用 Bold）
          .fontWeight(this.valueSize === DesignFontSize.AMOUNT_MEDIUM
            ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .fontFeature(DesignFontFeature.TNUM)

        if (this.suffix.length > 0) {
          Text(this.suffix)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
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
 * 槽位高 48vp（对齐安卓审查 #4③：44 跌破 §3 触控红线）、内格 40 + 水平 gap 4（对齐安卓审查 #17）。
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
  @Local pressed: boolean = false
  @Event onSelect: (dateKey: string) => void = () => {
  }

  /** 格底：有记录 = 快照色；未来 = groupBackground；其余 = surfaceVariant */
  private fillColor(): ResourceColor {
    if (this.hasRecord) {
      return this.colorHex
    }
    return this.isFuture ? $r('app.color.GROUP_BACKGROUND') : $r('app.color.SURFACE_VARIANT')
  }

  /**
   * 日期数字色：有记录格弃用亮度单阈值（对齐安卓审查 #1：单阈值在「亮底配墨字」与
   * 「暗底配白字」间顾此失彼，浅色多云/晴/大晴、深色暴雨/阴跌破 4.5:1）——对墨字
   * textPrimary 与卡底 surface 两候选按 WCAG 对比度实算取高者。令牌走资源限定符
   * 随主题回写、ThemeRefresh 子树重建时重算，深浅双主题全色级达标。
   * 无记录/未来格统一 textSecondary（对齐安卓审查 #9：未来格 textTertiary 仅 1.86:1，
   * 过去/未来的层次由字重 Normal vs 今日 SemiBold 承担）。
   */
  private textColor(): ResourceColor {
    if (this.hasRecord) {
      const fillLuminance = moodHexLuminance(this.colorHex)
      const inkRatio = moodContrastRatio(moodTokenLuminance(resColor('TEXT_PRIMARY')), fillLuminance)
      const surfaceRatio = moodContrastRatio(moodTokenLuminance(resColor('SURFACE')), fillLuminance)
      return inkRatio >= surfaceRatio ? $r('app.color.TEXT_PRIMARY') : $r('app.color.SURFACE')
    }
    return $r('app.color.TEXT_SECONDARY')
  }

  /**
   * 描边宽（对齐安卓审查 #5/#33）：按下且可点 = 2（onPress 高亮，真实按压状态触发）；
   * 今日 = 1.5 墨色；有记录格 = 1（静态 affordance，区分「可点开详情」与灰格）；其余 0。
   */
  private cellBorderWidth(): number {
    if (this.pressed && this.hasRecord) {
      return 2
    }
    if (this.isToday) {
      return 1.5
    }
    return this.hasRecord ? 1 : 0
  }

  /** 描边色：按压高亮与今日描边用墨色，静态 affordance 用弱文本灰（装饰性用途） */
  private cellBorderColor(): ResourceColor {
    if ((this.pressed && this.hasRecord) || this.isToday) {
      return $r('app.color.TEXT_PRIMARY')
    }
    return $r('app.color.TEXT_TERTIARY')
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
      // 40 = 48 槽位 − 上下 gap 4×2（对齐安卓 boxSize = cellHeight − gap×2，审查 #17）
      .height(40)
      .borderRadius(DesignDimens.RADIUS_SMALL)
      .backgroundColor(this.fillColor())
      .border({
        width: this.cellBorderWidth(),
        color: this.cellBorderColor()
      })
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
    }
    .width('100%')
    .height(48)
    .justifyContent(FlexAlign.Center)
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    // 读屏语义（对齐安卓审查 #7 网格级描述的逐格落地）：有记录格读日期 + 心情名
    .accessibilityText(this.hasRecord ? `${this.day}日 ${moodLevelLabel(this.level)}` : `${this.day}日`)
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
          .fontColor($r('app.color.TEXT_PRIMARY'))
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
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .margin({ left: DesignDimens.SPACE_XS })
        }
        .alignItems(VerticalAlign.Center)

        Text(this.word.length > 0 ? this.word : MoodText.mood_day_dialog_word_empty)
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          // 空词占位改 textSecondary（对齐安卓审查 #10：18vp 大字无装饰豁免，
          // textTertiary on 白卡 2.26:1 不过线）
          .fontColor(this.word.length > 0 ? $r('app.color.TEXT_PRIMARY') : $r('app.color.TEXT_SECONDARY'))
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XS })

        Button(MoodText.mood_day_dialog_close)
          .type(ButtonType.Normal)
          .width('100%')
          // 44 → 48 对齐触控红线（报告 #25：鸿蒙独有关闭按钮保留，热区提档）
          .height(48)
          .backgroundColor(Color.Transparent)
          .fontColor($r('app.color.PRIMARY'))
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
        // bottom 12 → 20 对齐安卓弹窗纵向 20（报告 #24）
        bottom: DesignDimens.SPACE_LG
      })
      .backgroundColor($r('app.color.SURFACE'))
      .borderRadius(DesignDimens.CARD_RADIUS)
      .shadow({
        radius: DesignElevation.FLOAT_SHADOW_RADIUS,
        color: $r('app.color.SHADOW_COLOR_FLOAT'),
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
