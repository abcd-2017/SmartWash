// 订阅模块共享件 — 对齐安卓 feature/subscription/ui/SubscriptionUiCommon.kt。
//
// 内容：
// 1) 页面头（返回 + 标题 + 可选两个文字动作）；
// 2) 静态标签 chip（状态/类型/角标）与可选筛选 chip；
// 3) 自绘开关（自动续费）与续费周期进度环；
// 4) 日期选择弹窗（下次续费日/试用截止共用）；
// 5) 状态/周期/提醒类型/续费相对描述等展示换算。
//
// 已知坑遵守：
// - 内容依赖状态的组件一律 @ComponentV2 + @Param/@Event（@Builder 按值传参不响应更新）；
// - 弹窗「先关窗再回调」；颜色一律取 Design* 令牌（深色模式自动生效）。

import { pasteboard } from '@kit.BasicServicesKit'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionTiming } from '../../../constant/DesignSystem'
import { SubscriptionCycleType, SubscriptionReminderType, SubscriptionStatus, SubscriptionText, SubscriptionVo, subscriptionCycleTypeFromRaw, subscriptionReminderTypeFromRawOrDefault, subscriptionStatusFromRawOrDefault, subscriptionStatusIsTerminal } from '../api'
import { formatAmount, formatDate, renewProgressFraction } from '../logic/SubscriptionRules'
import { pathStack } from '../../../utils/PathStackUtil'

/** 试用角标配色（容器底 + 文字色） */
export interface SubscriptionBadgeColors {
  container: number
  content: number
}

// ==================== 页面头 ====================

/**
 * 页面头（对齐安卓 PageHeader）：返回箭头 48 热区 + 标题 22/600 + 可选两个右侧文字动作。
 * 动作文案为空串时不渲染（列表页「提醒/新建」、详情页「编辑/删除」按状态显隐）。
 */
@ComponentV2
export struct SubscriptionPageHeader {
  @Param title: string = ''
  @Param showBack: boolean = true
  @Param action1Text: string = ''
  @Param action2Text: string = ''
  /** 动作 2 语义色（默认主色；危险动作传 DANGER_INK，对齐安卓页头「删除」dangerInk） */
  @Param action2Color: number = DesignColor.PRIMARY_DARK
  @Event onAction1: () => void = () => {
  }
  @Event onAction2: () => void = () => {
  }

  build() {
    Row() {
      if (this.showBack) {
        Column() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DesignColor.TEXT_PRIMARY])
        }
        .width(DesignDimens.BACK_HIT_AREA)
        .height(DesignDimens.BACK_HIT_AREA)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .onClick(() => {
          pathStack.pop()
        })
      }

      Text(this.title)
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)

      Blank()

      if (this.action1Text.length > 0) {
        Text(this.action1Text)
          // 文字按钮 13sp/600 规格经 v0.4 字阶迁移落 labelMedium 12（对齐安卓 audit-15 #14）
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.PRIMARY_DARK)
          .padding({ left: 12, right: 12, top: 12, bottom: 12 })
          // 相邻触控目标间距 ≥8（DESIGN-SPEC §3）：两个动作热区边界原先彼此贴合
          .margin({ right: DesignDimens.SPACE_XS })
          .onClick(() => {
            this.onAction1()
          })
      }
      if (this.action2Text.length > 0) {
        Text(this.action2Text)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(this.action2Color)
          .padding({ left: 12, right: 12, top: 12, bottom: 12 })
          .onClick(() => {
            this.onAction2()
          })
      }
    }
    .width('100%')
    .height(56)
    // 行首让位 12：48dp 热区盒居中后图形左缘落在 24（对齐安卓 PageHeader）
    .padding({ left: this.showBack ? DesignDimens.SPACE_SM : DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
    .alignItems(VerticalAlign.Center)
  }
}

// ==================== chip / 开关 / 进度环 ====================

/** 静态标签 chip —— 胶囊（radiusFull），用于条目上的状态/类型/角标标注 */
@ComponentV2
export struct SubscriptionTagChip {
  @Param text: string = ''
  @Param containerColor: number = DesignColor.SURFACE_VARIANT
  @Param contentColor: number = DesignColor.TEXT_SECONDARY

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.contentColor)
      .fontFeature(DesignFontFeature.TNUM)
      .padding({
        left: DesignDimens.SPACE_XS,
        right: DesignDimens.SPACE_XS,
        top: DesignDimens.SPACE_XXS,
        bottom: DesignDimens.SPACE_XXS
      })
      .backgroundColor(this.containerColor)
      .borderRadius(DesignDimens.RADIUS_FULL)
  }
}

/**
 * 状态筛选 chip —— 选中 = 品牌底白字，未选中 = 白底描边
 * （模式对齐 ToolboxSelectableChip / AppPillTabRow）。
 */
@ComponentV2
export struct SubscriptionSelectableChip {
  @Param text: string = ''
  @Param selected: boolean = false
  @Event onSelect: () => void = () => {
  }

  build() {
    // 双层结构对齐安卓：外层 48 最小热区（DESIGN-SPEC §3 触控目标 ≥48），onClick 上移容器；
    // 内层胶囊纵向 padding 8（视觉高 ≈32，对齐安卓 16/8 胶囊）
    Column() {
      Text(this.text)
        .fontSize(DesignFontSize.CAPTION)
        // 字重统一 SemiBold：字重随选中切换会让文字宽度跳变，兄弟 chip 连续位移观感更差
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(this.selected ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
        .padding({
          left: DesignDimens.SPACE_MD,
          right: DesignDimens.SPACE_MD,
          top: DesignDimens.SPACE_XS,
          bottom: DesignDimens.SPACE_XS
        })
        .borderRadius(DesignDimens.RADIUS_FULL)
        .backgroundColor(this.selected ? DesignColor.PRIMARY : DesignColor.SURFACE)
        // 描边常驻 1dp、选中切换在 outline ↔ 透明间过渡（对齐安卓，选中不再多一层同色描边）
        .border({
          width: 1,
          color: this.selected ? Color.Transparent : DesignColor.OUTLINE
        })
        .animation(MotionTiming.ENTER)
    }
    .constraintSize({ minHeight: 48 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .onClick(() => {
      this.onSelect()
    })
  }
}

/**
 * 自绘开关（自动续费）—— 轨道 44×24 胶囊，thumb 20 圆点；
 * 全库无既有 Switch 先例（规范 §7 禁 state layer），故自绘。
 */
@ComponentV2
export struct SubscriptionToggle {
  @Param checked: boolean = false
  @Event onChange: (checked: boolean) => void = (_: boolean) => {
  }

  build() {
    // 双层结构对齐安卓（audit-14 #7）：外层 48 最小热区（DESIGN-SPEC §3 触控目标 ≥48×48），
    // onClick 上移外层，24 轨道居中视觉尺寸不变（同 SelectableChip 已修写法）
    Column() {
      Row() {
        Column()
          .width(20)
          .height(20)
          .borderRadius(10)
          .backgroundColor(DesignColor.SURFACE)
          .margin({ left: this.checked ? 22 : 2 })
      }
      .width(44)
      .height(24)
      .borderRadius(DesignDimens.RADIUS_FULL)
      // 未选中轨道从装饰档 textTertiary 收敛 textSecondary（#A9AEA9 on 白 2.25:1 低于
      // 图形控件 3:1 非文本对比下限，DESIGN-SPEC §10；thumb 白色在次文本色轨道上辨识足够）
      .backgroundColor(this.checked ? DesignColor.PRIMARY : DesignColor.TEXT_SECONDARY)
      .justifyContent(FlexAlign.Start)
      .alignItems(VerticalAlign.Center)
      .animation(MotionTiming.ENTER)
    }
    .constraintSize({ minHeight: 48 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .onClick(() => {
      this.onChange(!this.checked)
    })
  }
}

/**
 * 续费周期进度环（44vp · 4vp 描边）：进度 = 已消耗天数 / 当期总天数。
 * 环心为距续费天数（tnum，文案接线 subscription_renew_days_compact）。
 * 环线与环心文字拆色（对齐安卓 2026-11-11 修复）：环线走图形口径（ringColor），
 * 环心 12fp 数字走文字级达标令牌（centerTextColor，§10 ≥4.5:1）。
 * 数据变化经 @Monitor 触发重绘（修复 ForEach 同 key 复用时弧线停留旧值）；
 * fraction 以 220ms easeOut 逐帧插值过渡（ArkUI Canvas 无逐帧弹簧直驱，端限制近似，
 * 时长对齐 MotionTiming 弹簧收敛档），颜色变化立即重绘。
 */
@ComponentV2
export struct RenewProgressRing {
  @Param fraction: number = 0
  @Param daysLeft: number = 0
  @Param ringColor: number = DesignColor.PRIMARY
  @Param trackColor: number = DesignColor.OUTLINE
  @Param centerTextColor: number = DesignColor.TEXT_GREEN
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  /** 弧线插值显示值（@Param fraction 不可变，逐帧过渡经此字段重绘） */
  private displayedFraction: number = 0
  /** 过渡帧定时器（新动画前/组件销毁时必须清理） */
  private animTimer: number = -1

  aboutToAppear(): void {
    this.displayedFraction = this.fraction
    this.drawRing()
  }

  aboutToDisappear(): void {
    this.clearAnimTimer()
  }

  /** @Param 数据变化：重绘弧线（fraction 走插值过渡，颜色跳变重绘） */
  @Monitor('fraction', 'ringColor', 'trackColor')
  onRingDataChange(): void {
    this.startFractionTransition()
  }

  private clearAnimTimer(): void {
    if (this.animTimer !== -1) {
      clearInterval(this.animTimer)
      this.animTimer = -1
    }
  }

  /** fraction 220ms easeOut 插值逐帧重绘；颜色等非进度变化（delta 为 0）直接重绘 */
  private startFractionTransition(): void {
    this.clearAnimTimer()
    const from: number = this.displayedFraction
    const delta: number = this.fraction - from
    if (delta === 0) {
      this.drawRing()
      return
    }
    const start: number = Date.now()
    const duration: number = 220
    this.animTimer = setInterval(() => {
      const t: number = Math.min(1, (Date.now() - start) / duration)
      this.displayedFraction = from + delta * (1 - Math.pow(1 - t, 3))
      this.drawRing()
      if (t >= 1) {
        this.clearAnimTimer()
      }
    }, 16)
  }

  private drawRing(): void {
    const size: number = 44
    const stroke: number = 4
    const radius: number = (size - stroke) / 2
    const cx: number = size / 2
    const cy: number = size / 2
    const clamped: number = Math.min(1, Math.max(0, this.displayedFraction))

    this.ctx.clearRect(0, 0, size, size)
    this.ctx.lineCap = 'round'
    // 轨迹圆
    this.ctx.lineWidth = stroke
    this.ctx.strokeStyle = this.trackColor
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius, 0, Math.PI * 2, false)
    this.ctx.stroke()
    // 进度弧（起始 -90°）
    this.ctx.lineWidth = stroke
    this.ctx.strokeStyle = this.ringColor
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * clamped, false)
    this.ctx.stroke()
  }

  build() {
    Stack() {
      Canvas(this.ctx)
        .width(44)
        .height(44)
        .onReady(() => {
          this.drawRing()
        })

      Text(subscriptionFormat(SubscriptionText.subscription_renew_days_compact, Math.abs(this.daysLeft)))
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(this.centerTextColor)
        .fontFeature(DesignFontFeature.TNUM)
    }
    .width(44)
    .height(44)
  }
}

/** 区块标题（详情页卡头，SectionTitle 统一档） */
@ComponentV2
export struct SubscriptionSectionTitle {
  @Param text: string = ''

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.SECTION_TITLE)
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor(DesignColor.TEXT_PRIMARY)
  }
}

/** 状态小圆点（未读提醒/今日打卡标记；颜色随语义给定） */
@ComponentV2
export struct SubscriptionStatusDot {
  @Param color: number = DesignColor.DANGER
  @Param sizeValue: number = 8
  /** 读屏语义文案（空串 = 无语义）：如「N 条未读提醒」（§10 颜色不是唯一信息载体） */
  @Param accessibilityLabel: string = ''

  build() {
    Column()
      .width(this.sizeValue)
      .height(this.sizeValue)
      .borderRadius(this.sizeValue / 2)
      .backgroundColor(this.color)
      .accessibilityText(this.accessibilityLabel)
  }
}

// ==================== 日期选择弹窗 ====================

/**
 * 日期选择弹窗（下次续费日/试用截止共用）。
 * 选中结果以「yyyy-MM-dd」本地日期串回传（与 SubscriptionRules.parseDate 互逆）。
 * 结构对齐 ToolboxDatePickerDialog（CustomDialogController + 内联 DatePicker）。
 */
@CustomDialog
export struct SubscriptionDatePickerDialog {
  // 由父组件经 CustomDialogController 注入（框架约定，不可自赋初值）
  controller: CustomDialogController
  /** 已选日期「yyyy-MM-dd」，空串表示未选（默认今天） */
  @Prop initialDate: string = ''
  @Prop title: string = '选择日期'
  @State selected: Date = new Date()
  @State entered: boolean = false
  onConfirm: (date: string) => void = () => {
  }
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

  aboutToAppear(): void {
    if (this.initialDate.length >= 10) {
      const year = Number(this.initialDate.substring(0, 4))
      const month = Number(this.initialDate.substring(5, 7))
      const day = Number(this.initialDate.substring(8, 10))
      this.selected = new Date(year, month - 1, day)
    }
  }

  build() {
    // 外层留白容器：customStyle 弹窗铺满屏幕宽度，卡片背景画在内层并留左右边距
    Column() {
      Column() {
        Text(this.title)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .width('100%')
          .margin({ bottom: 12 })

        DatePicker({ selected: this.selected })
          .onDateChange((value: Date) => {
            this.selected = value
          })
          .width('100%')
          .height(200)

        Row() {
          Button(SubscriptionText.subscription_date_cancel)
            .type(ButtonType.Normal)
            .layoutWeight(1)
            .height(44)
            .backgroundColor(Color.Transparent)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .fontSize(DesignFontSize.BODY)
            .borderRadius(0)
            .onClick(() => {
              this.onDismiss()
              this.controller.close()
            })

          Divider()
            .vertical(true)
            .color(DesignColor.DIVIDER)
            .strokeWidth(0.5)
            .height(44)

          Button(SubscriptionText.subscription_date_confirm)
            .type(ButtonType.Normal)
            .layoutWeight(1)
            .height(44)
            .backgroundColor(Color.Transparent)
            .fontColor(DesignColor.PRIMARY)
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .borderRadius(0)
            .onClick(() => {
              // 先关窗再回调：回调内可能导航/改状态，导航后弹窗实例已销毁
              this.controller.close()
              this.onConfirm(formatDate(this.selected))
            })
        }
        .width('100%')
        .margin({ top: 8 })
      }
      .width('100%')
      .padding({ top: 20, bottom: 20 })
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

// ==================== 展示换算（对齐安卓 SubscriptionUiCommon 函数） ====================

/**
 * 文案占位符填充：模板中的 %s / %d 按序替换（对齐安卓 stringResource 格式化）。
 * 仅替换首个匹配，逐参消费；无占位符时原样返回。
 */
export function subscriptionFormat(template: string, ...args: (string | number)[]): string {
  let result = template
  for (const arg of args) {
    const token = typeof arg === 'number' ? '%d' : '%s'
    const index = result.indexOf(token)
    if (index < 0) {
      break
    }
    result = result.substring(0, index) + `${arg}` + result.substring(index + token.length)
  }
  return result
}

/** 订阅状态文案（列表 chip / 详情信息行共用） */
export function subscriptionStatusText(status: SubscriptionStatus): string {
  switch (status) {
    case SubscriptionStatus.PAUSED:
      return SubscriptionText.subscription_status_paused
    case SubscriptionStatus.CANCELED:
      return SubscriptionText.subscription_status_canceled
    case SubscriptionStatus.FINISHED:
      return SubscriptionText.subscription_status_finished
    default:
      return SubscriptionText.subscription_status_active
  }
}

/** 周期描述：月/年 × 间隔（1 期收敛为「每月/每年」） */
export function subscriptionCycleDescription(cycleType: SubscriptionCycleType, interval: number): string {
  if (cycleType === SubscriptionCycleType.YEAR) {
    return interval <= 1
      ? SubscriptionText.subscription_cycle_yearly
      : subscriptionFormat(SubscriptionText.subscription_cycle_every_n_years, interval)
  }
  return interval <= 1
    ? SubscriptionText.subscription_cycle_monthly
    : subscriptionFormat(SubscriptionText.subscription_cycle_every_n_months, interval)
}

/** 续费相对描述（「下次续费 yyyy-MM-dd · N 天后/今日/已逾期 N 天」） */
export function subscriptionRenewRelativeText(vo: SubscriptionVo): string {
  const dateText = vo.nextRenewDate ?? ''
  const base = subscriptionFormat(SubscriptionText.subscription_renew_date_value, dateText)
  const daysLeft = vo.daysUntilRenew
  if (daysLeft === null || daysLeft === undefined) {
    return base
  }
  if (daysLeft < 0) {
    return `${base} · ${subscriptionFormat(SubscriptionText.subscription_renew_overdue_format, -daysLeft)}`
  }
  if (daysLeft === 0) {
    return `${base} · ${SubscriptionText.subscription_renew_today}`
  }
  return `${base} · ${subscriptionFormat(SubscriptionText.subscription_renew_in_days_format, daysLeft)}`
}

/** 续费相对描述配色（已逾期 dangerInk / 今日 ongoingInk / 其余次文本；终态弱化止步 textSecondary） */
export function subscriptionRenewRelativeColor(vo: SubscriptionVo): number {
  const status = subscriptionStatusFromRawOrDefault(vo.status)
  // 终态弱化止步 textSecondary（on 白卡 4.9:1）：装饰档 textTertiary 禁作可读正文（DESIGN-SPEC §10）
  if (subscriptionStatusIsTerminal(status)) {
    return DesignColor.TEXT_SECONDARY
  }
  const daysLeft = vo.daysUntilRenew ?? 1
  if (daysLeft < 0) {
    return DesignColor.DANGER_INK
  }
  if (daysLeft === 0) {
    return DesignColor.ONGOING_INK
  }
  return DesignColor.TEXT_SECONDARY
}

/** 进度环配色：暂停弱化、逾期 danger、临近（≤3 天）ongoing、其余主色 */
export function subscriptionRenewRingColor(vo: SubscriptionVo): number {
  const status = subscriptionStatusFromRawOrDefault(vo.status)
  if (status === SubscriptionStatus.PAUSED) {
    return DesignColor.TEXT_TERTIARY
  }
  const daysLeft = vo.daysUntilRenew ?? 0
  if (daysLeft < 0) {
    return DesignColor.DANGER
  }
  if (daysLeft <= 3) {
    return DesignColor.ONGOING
  }
  return DesignColor.PRIMARY
}

/**
 * 进度环环心文字色（环线/环心拆色，对齐安卓 2026-11-11 修复）：环线走图形口径（subscriptionRenewRingColor），
 * 环心 12fp 数字走文字级达标令牌（§10 ≥4.5:1）——按状态同色相映射：
 * 暂停 textSecondary / 逾期 dangerInk / 临近（≤3 天）onWarningContainer / 常态 textGreen。
 */
export function subscriptionRenewCenterTextColor(vo: SubscriptionVo): number {
  const status = subscriptionStatusFromRawOrDefault(vo.status)
  if (status === SubscriptionStatus.PAUSED) {
    return DesignColor.TEXT_SECONDARY
  }
  const daysLeft = vo.daysUntilRenew ?? 0
  if (daysLeft < 0) {
    return DesignColor.DANGER_INK
  }
  if (daysLeft <= 3) {
    return DesignColor.ON_WARNING_CONTAINER
  }
  return DesignColor.TEXT_GREEN
}

/** 进度环填充度（不可解析返回 null，UI 隐藏环） */
export function subscriptionRenewFraction(vo: SubscriptionVo): number | null {
  const cycleType = subscriptionCycleTypeFromRaw(vo.cycleType)
  return renewProgressFraction(cycleType, vo.cycleInterval, vo.nextRenewDate, vo.daysUntilRenew)
}

/** 状态图标（六色语义：进行中的循环箭头 / 暂停 / 取消叉 / 结束勾） */
export function subscriptionStatusIcon(status: SubscriptionStatus): Resource {
  switch (status) {
    case SubscriptionStatus.PAUSED:
      return $r('sys.symbol.pause')
    case SubscriptionStatus.CANCELED:
      return $r('sys.symbol.xmark')
    case SubscriptionStatus.FINISHED:
      return $r('sys.symbol.checkmark')
    default:
      return $r('sys.symbol.clock_arrow_2_circlepath')
  }
}

/** 状态图标盒浅底色 */
export function subscriptionStatusContainerColor(status: SubscriptionStatus): number {
  switch (status) {
    case SubscriptionStatus.PAUSED:
      return DesignColor.ICON_CONTAINER_ORANGE
    case SubscriptionStatus.CANCELED:
      return DesignColor.ICON_CONTAINER_PINK
    case SubscriptionStatus.FINISHED:
      return DesignColor.ICON_CONTAINER_GREEN
    default:
      return DesignColor.ICON_CONTAINER_TEAL
  }
}

/** 状态图标前景色 */
export function subscriptionStatusIconColor(status: SubscriptionStatus): number {
  switch (status) {
    case SubscriptionStatus.PAUSED:
      return DesignColor.ICON_FOREGROUND_ORANGE
    case SubscriptionStatus.CANCELED:
      return DesignColor.ICON_FOREGROUND_PINK
    case SubscriptionStatus.FINISHED:
      return DesignColor.ICON_FOREGROUND_GREEN
    default:
      return DesignColor.ICON_FOREGROUND_TEAL
  }
}

/** 状态 chip 底色 */
export function subscriptionStatusChipContainer(status: SubscriptionStatus): number {
  switch (status) {
    case SubscriptionStatus.PAUSED:
      return DesignColor.WARNING_CONTAINER
    case SubscriptionStatus.CANCELED:
      return DesignColor.ICON_CONTAINER_PINK
    default:
      return DesignColor.SURFACE_VARIANT
  }
}

/** 状态 chip 文字色（对齐安卓 statusChipContent 2026-11-11 修复值） */
export function subscriptionStatusChipContent(status: SubscriptionStatus): number {
  switch (status) {
    // PAUSED：onWarningContainer 与 warningContainer 成对（6.47:1）；ongoingInk 压橙浅底仅 2.52:1（§10 红线）
    case SubscriptionStatus.PAUSED:
      return DesignColor.ON_WARNING_CONTAINER
    case SubscriptionStatus.CANCELED:
      return DesignColor.DANGER_INK
    // FINISHED（default）：textSecondary on surfaceVariant 4.51:1；textTertiary 禁作可读标签（DESIGN-SPEC §10）
    default:
      return DesignColor.TEXT_SECONDARY
  }
}

/** 提醒类型文案 */
export function subscriptionReminderTypeText(type: SubscriptionReminderType): string {
  return type === SubscriptionReminderType.TRIAL_END
    ? SubscriptionText.subscription_reminder_type_trial_end
    : SubscriptionText.subscription_reminder_type_renew
}

/** 提醒类型图标（renew 铃铛 / trial_end 沙漏） */
export function subscriptionReminderIcon(type: SubscriptionReminderType): Resource {
  return type === SubscriptionReminderType.TRIAL_END
    ? $r('sys.symbol.hourglass')
    : $r('sys.symbol.bell')
}

/** 提醒类型图标盒浅底色 */
export function subscriptionReminderContainerColor(type: SubscriptionReminderType): number {
  return type === SubscriptionReminderType.TRIAL_END
    ? DesignColor.ICON_CONTAINER_PINK
    : DesignColor.ICON_CONTAINER_ORANGE
}

/** 提醒类型图标前景色 */
export function subscriptionReminderIconColor(type: SubscriptionReminderType): number {
  return type === SubscriptionReminderType.TRIAL_END
    ? DesignColor.ICON_FOREGROUND_PINK
    : DesignColor.ICON_FOREGROUND_ORANGE
}

/** 提醒类型 chip 底色 */
export function subscriptionReminderChipContainer(type: SubscriptionReminderType): number {
  return type === SubscriptionReminderType.TRIAL_END
    ? DesignColor.ICON_CONTAINER_PINK
    : DesignColor.WARNING_CONTAINER
}

/** 提醒类型 chip 文字色 */
export function subscriptionReminderChipContent(type: SubscriptionReminderType): number {
  return type === SubscriptionReminderType.TRIAL_END
    ? DesignColor.DANGER_INK
    // RENEW 与 statusChipContent(PAUSED) 同源裁定：onWarningContainer 成对令牌
    // （浅 #92400E 6.47:1）；ongoingInk 压 warningContainer 仅 2.51:1，禁作 12sp 标签色（§10）
    : DesignColor.ON_WARNING_CONTAINER
}

/** 试用倒计时角标文案（无试用/已过期返回空串，UI 不渲染） */
export function subscriptionTrialBadgeText(vo: SubscriptionVo): string {
  const daysLeft = vo.trialDaysLeft
  if (daysLeft === null || daysLeft === undefined || daysLeft < 0) {
    return ''
  }
  return daysLeft === 0
    ? SubscriptionText.subscription_trial_last_day
    : subscriptionFormat(SubscriptionText.subscription_trial_left_format, daysLeft)
}

/** 试用角标配色：≤3 天转粉（临近截止），其余橙（待办语义）；无试用/已过期回落中性档 */
export function subscriptionTrialBadgeColors(vo: SubscriptionVo): SubscriptionBadgeColors {
  const daysLeft = vo.trialDaysLeft
  if (daysLeft === null || daysLeft === undefined || daysLeft < 0) {
    return { container: DesignColor.SURFACE_VARIANT, content: DesignColor.TEXT_SECONDARY }
  }
  return daysLeft <= 3
    ? { container: DesignColor.ICON_CONTAINER_PINK, content: DesignColor.DANGER_INK }
    // 非紧急分支换 onWarningContainer（容器/内容成对令牌，6.47:1）：
    // ongoingInk v0.4 与 ongoing 同值 #D98A26，压橙浅底仅 2.52:1，不达正文红线（§10）
    : { container: DesignColor.WARNING_CONTAINER, content: DesignColor.ON_WARNING_CONTAINER }
}

/** 每期金额展示（¥xx.xx/期） */
export function subscriptionAmountText(amount: number): string {
  return subscriptionFormat(SubscriptionText.subscription_amount_per_period, formatAmount(amount))
}

/** 提醒类型（防御回落 renew；未知按 renew 展示） */
export function subscriptionReminderTypeOf(raw: string | null | undefined): SubscriptionReminderType {
  return subscriptionReminderTypeFromRawOrDefault(raw)
}

/** 状态（防御回落 active） */
export function subscriptionStatusOf(raw: string | null | undefined): SubscriptionStatus {
  return subscriptionStatusFromRawOrDefault(raw)
}

/**
 * 复制文本到剪贴板（取消网址「复制」与详情页共用）。
 * @returns 是否复制成功（失败由调用方提示）
 */
export async function subscriptionCopyText(text: string): Promise<boolean> {
  if (text.length === 0) {
    return false
  }
  try {
    const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text)
    await pasteboard.getSystemPasteboard().setData(data)
    return true
  } catch (err) {
    return false
  }
}
