// 订阅模块共享件 — 对齐安卓 feature/subscription/ui/SubscriptionUiCommon.kt。
//
// 内容：
// 1) 页面头（返回 + 标题 + 可选两个文字动作）；
// 2) 静态标签 chip（状态/类型/角标）与可选筛选 chip；
// 3) 续费周期进度环（自动续费开关已上收公共 view/common/AppSwitch.ets）；
// 4) 日期选择弹窗（下次续费日/试用截止共用）；
// 5) 状态/周期/提醒类型/续费相对描述等展示换算。
//
// 已知坑遵守：
// - 内容依赖状态的组件一律 @ComponentV2 + @Param/@Event（@Builder 按值传参不响应更新）；
// - 弹窗「先关窗再回调」；颜色一律取 Design* 令牌（深色模式自动生效）。

import { pasteboard } from '@kit.BasicServicesKit'
import { AnimatorOptions, AnimatorResult } from '@ohos.animator'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionScale, MotionTiming } from '../../../constant/DesignSystem'
import { ReduceMotion } from '../../../utils/ReduceMotion'
import { SubscriptionCycleType, SubscriptionReminderType, SubscriptionStatus, SubscriptionText, SubscriptionVo, subscriptionCycleTypeFromRaw, subscriptionReminderTypeFromRawOrDefault, subscriptionStatusFromRawOrDefault, subscriptionStatusIsTerminal } from '../api'
import { formatAmount, formatDate, renewProgressFraction } from '../logic/SubscriptionRules'
import { AnimatedNumber } from '../../../view/common/AppComponents'
import { pathStack } from '../../../utils/PathStackUtil'
import { resolveArgb } from '../../../utils/ResourceColor'
import { resString } from '../../../utils/ResourceString'

/** 试用角标配色（容器底 + 文字色） */
export interface SubscriptionBadgeColors {
  container: ResourceColor
  content: ResourceColor
}


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
  @Param action2Color: ResourceColor = $r('app.color.PRIMARY_DARK')
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
            .fontColor([$r('app.color.TEXT_PRIMARY')])
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
        .fontColor($r('app.color.TEXT_PRIMARY'))

      Blank()

      if (this.action1Text.length > 0) {
        Text(this.action1Text)
          // 文字按钮 13sp/600 规格经 v0.4 字阶迁移落 labelMedium 12（对齐安卓 audit-15 #14）
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.PRIMARY_DARK'))
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


/** 静态标签 chip —— 胶囊（radiusFull），用于条目上的状态/类型/角标标注 */
@ComponentV2
export struct SubscriptionTagChip {
  @Param text: string = ''
  @Param containerColor: ResourceColor = $r('app.color.SURFACE_VARIANT')
  @Param contentColor: ResourceColor = $r('app.color.TEXT_SECONDARY')

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
  // 按压态（对齐安卓 pressable scale 0.97 + 100ms FastOutSlowIn，reduced 180ms）
  @Local pressed: boolean = false

  build() {
    // 双层结构对齐安卓：外层 48 最小热区（DESIGN-SPEC §3 触控目标 ≥48），onClick 上移容器；
    // 内层胶囊纵向 padding 8（视觉高 ≈32，对齐安卓 16/8 胶囊）
    Column() {
      Text(this.text)
        .fontSize(DesignFontSize.CAPTION)
        // 字重统一 SemiBold：字重随选中切换会让文字宽度跳变，兄弟 chip 连续位移观感更差
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(this.selected ? $r('app.color.ON_PRIMARY') : $r('app.color.TEXT_SECONDARY'))
        .padding({
          left: DesignDimens.SPACE_MD,
          right: DesignDimens.SPACE_MD,
          top: DesignDimens.SPACE_XS,
          bottom: DesignDimens.SPACE_XS
        })
        .borderRadius(DesignDimens.RADIUS_FULL)
        .backgroundColor(this.selected ? $r('app.color.PRIMARY') : $r('app.color.SURFACE'))
        // 描边常驻 1dp、选中切换在 outline ↔ 透明间过渡（对齐安卓，选中不再多一层同色描边）
        .border({
          width: 1,
          color: this.selected ? Color.Transparent : $r('app.color.OUTLINE')
        })
        // 选中色走临界阻尼弹簧（对齐安卓 selectedColor，reduced 180ms）
        .animation(ReduceMotion.specCurve(MotionTiming.SELECTED))
    }
    .constraintSize({ minHeight: 48 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    // 按压反馈（对齐安卓 pressTween：100ms FastOutSlowIn，reduced 180ms）
    .scale({ x: this.pressed ? MotionScale.Press : 1, y: this.pressed ? MotionScale.Press : 1 })
    .animation(ReduceMotion.spec(MotionTiming.PRESS))
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      this.onSelect()
    })
  }
}

/**
 * 续费周期进度环（44vp · 4vp 描边）：进度 = 已消耗天数 / 当期总天数。
 * 环心为距续费天数（tnum，文案接线 subscription_renew_days_compact）。
 * 环线与环心文字拆色（对齐安卓 2026-11-11 修复）：环线走图形口径（ringColor），
 * 环心 12fp 数字走文字级达标令牌（centerTextColor，§10 ≥4.5:1）。
 * 数据变化经 @Monitor 触发重绘（修复 ForEach 同 key 复用时弧线停留旧值）；
 * fraction 以 interpolating-spring（临界阻尼 1.0 / stiffness 350）逐帧插值过渡，
 * 对齐安卓 animateFloatAsState(motionSpec(defaultSpring))；reduced motion 直接落值。
 * 颜色变化立即重绘。
 */
@ComponentV2
export struct RenewProgressRing {
  @Param fraction: number = 0
  @Param daysLeft: number = 0
  @Param ringColor: ResourceColor = $r('app.color.PRIMARY')
  @Param trackColor: ResourceColor = $r('app.color.OUTLINE')
  @Param centerTextColor: ResourceColor = $r('app.color.TEXT_GREEN')
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))
  /** 弧线插值显示值（@Param fraction 不可变，逐帧过渡经此字段重绘） */
  private displayedFraction: number = 0
  /** 弹簧动画器（Canvas 逐帧重绘；对齐安卓 animateFloatAsState(defaultSpring)） */
  private animatorResult: AnimatorResult | null = null
  /** 动画器选项 — interpolating-spring(velocity, mass, stiffness, damping)，阻尼比 1.0 临界无回弹 */
  private animOptions: AnimatorOptions = {
    duration: 0,
    easing: 'interpolating-spring(0, 1, 350, 37.4)',
    delay: 0,
    direction: "normal",
    iterations: 1,
    begin: 0,
    end: 1,
    fill: "forwards"
  }
  /** 本帧动画起点 / 终点（progress 0→1 线性映射到该区间） */
  private fromFraction: number = 0
  private targetFraction: number = 0

  aboutToAppear(): void {
    this.displayedFraction = this.fraction
    this.fromFraction = this.fraction
    this.targetFraction = this.fraction
    this.drawRing()
    try {
      const result: AnimatorResult = this.getUIContext().createAnimator(this.animOptions)
      result.onFrame = (progress: number): void => {
        this.handleFrame(progress)
      }
      this.animatorResult = result
    } catch (err) {
      // 动画器不可用（极少数环境）：退化为直接重绘，不留卡死值
      this.animatorResult = null
    }
  }

  /** 动画帧回调：progress 0→1 映射到 [fromFraction, targetFraction] 后重绘 */
  private handleFrame(progress: number): void {
    this.displayedFraction = this.fromFraction + (this.targetFraction - this.fromFraction) * progress
    this.drawRing()
  }

  aboutToDisappear(): void {
    if (this.animatorResult !== null) {
      this.animatorResult.finish()
      this.animatorResult = null
    }
  }

  /** @Param 数据变化：重绘弧线（fraction 走弹簧插值过渡，颜色跳变重绘） */
  @Monitor('fraction', 'ringColor', 'trackColor')
  onRingDataChange(): void {
    // reduced motion：取消过渡直接落值（规范 §8，对齐安卓 motionSpec 180ms 由重置为立即重绘）
    if (ReduceMotion.isEnabled()) {
      this.displayedFraction = this.fraction
      this.drawRing()
      return
    }
    this.fromFraction = this.displayedFraction
    this.targetFraction = this.fraction
    if (this.targetFraction === this.fromFraction || this.animatorResult === null) {
      this.drawRing()
      return
    }
    this.animatorResult.reset(this.animOptions)
    // reset 后重挂帧回调，确保逐帧重绘不丢失
    this.animatorResult.onFrame = (progress: number): void => {
      this.handleFrame(progress)
    }
    this.animatorResult.play()
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
    this.ctx.strokeStyle = resolveArgb(this.trackColor)
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, radius, 0, Math.PI * 2, false)
    this.ctx.stroke()
    // 进度弧（起始 -90°）
    this.ctx.lineWidth = stroke
    this.ctx.strokeStyle = resolveArgb(this.ringColor)
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

      // 环心距续费天数：数字变化走弹簧淡入淡出（对齐安卓 AnimatedContent numberSpec）
      AnimatedNumber({
        value: subscriptionFormat(SubscriptionText.subscription_renew_days_compact, Math.abs(this.daysLeft)),
        fontSize: DesignFontSize.CAPTION,
        fontWeight: DesignFontWeight.MEDIUM,
        fontColor: this.centerTextColor
      })
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
      .fontColor($r('app.color.TEXT_PRIMARY'))
  }
}

/** 状态小圆点（未读提醒/今日打卡标记；颜色随语义给定） */
@ComponentV2
export struct SubscriptionStatusDot {
  @Param color: ResourceColor = $r('app.color.DANGER')
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
  @Prop title: string = resString('subscription_form_pick_date')
  @State selected: Date = new Date()
  @State entered: boolean = false
  // reduced motion：缩放归零只留纯淡入（规范 §8）
  @State reduceMotion: boolean = false
  onConfirm: (date: string) => void = () => {
  }
  onDismiss: () => void = () => {
  }

  /** 弹窗统一入场（对齐安卓 dialogEnterMotion）：首帧后再触发，弹簧 scale 0.92→1 + fade */
  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(ReduceMotion.spec(MotionTiming.DIALOG_ENTER), () => {
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
          .fontColor($r('app.color.TEXT_PRIMARY'))
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
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .fontSize(DesignFontSize.BODY)
            .borderRadius(0)
            .onClick(() => {
              this.onDismiss()
              this.controller.close()
            })

          Divider()
            .vertical(true)
            .color($r('app.color.DIVIDER'))
            .strokeWidth(0.5)
            .height(44)

          Button(SubscriptionText.subscription_date_confirm)
            .type(ButtonType.Normal)
            .layoutWeight(1)
            .height(44)
            .backgroundColor(Color.Transparent)
            .fontColor($r('app.color.PRIMARY'))
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
      .backgroundColor($r('app.color.SURFACE'))
      .borderRadius(DesignDimens.CARD_RADIUS)
      .shadow({
        radius: DesignElevation.FLOAT_SHADOW_RADIUS,
        color: $r('app.color.SHADOW_COLOR_FLOAT'),
        offsetX: 0,
        offsetY: DesignElevation.FLOAT_SHADOW_OFFSET
      })
      .scale({
        x: this.entered ? 1 : (this.reduceMotion ? 1 : MotionScale.DialogEnter),
        y: this.entered ? 1 : (this.reduceMotion ? 1 : MotionScale.DialogEnter)
      })
      .opacity(this.entered ? 1 : 0)
      .onAppear(() => {
        this.reduceMotion = ReduceMotion.isEnabled()
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
export function subscriptionRenewRelativeColor(vo: SubscriptionVo): ResourceColor {
  const status = subscriptionStatusFromRawOrDefault(vo.status)
  // 终态弱化止步 textSecondary（on 白卡 4.9:1）：装饰档 textTertiary 禁作可读正文（DESIGN-SPEC §10）
  if (subscriptionStatusIsTerminal(status)) {
    return $r('app.color.TEXT_SECONDARY')
  }
  const daysLeft = vo.daysUntilRenew ?? 1
  if (daysLeft < 0) {
    return $r('app.color.DANGER_INK')
  }
  if (daysLeft === 0) {
    return $r('app.color.ONGOING_INK')
  }
  return $r('app.color.TEXT_SECONDARY')
}

/** 进度环配色：暂停弱化、逾期 danger、临近（≤3 天）ongoing、其余主色 */
export function subscriptionRenewRingColor(vo: SubscriptionVo): ResourceColor {
  const status = subscriptionStatusFromRawOrDefault(vo.status)
  if (status === SubscriptionStatus.PAUSED) {
    return $r('app.color.TEXT_TERTIARY')
  }
  const daysLeft = vo.daysUntilRenew ?? 0
  if (daysLeft < 0) {
    return $r('app.color.DANGER')
  }
  if (daysLeft <= 3) {
    return $r('app.color.ONGOING')
  }
  return $r('app.color.PRIMARY')
}

/**
 * 进度环环心文字色（环线/环心拆色，对齐安卓 2026-11-11 修复）：环线走图形口径（subscriptionRenewRingColor），
 * 环心 12fp 数字走文字级达标令牌（§10 ≥4.5:1）——按状态同色相映射：
 * 暂停 textSecondary / 逾期 dangerInk / 临近（≤3 天）onWarningContainer / 常态 textGreen。
 */
export function subscriptionRenewCenterTextColor(vo: SubscriptionVo): ResourceColor {
  const status = subscriptionStatusFromRawOrDefault(vo.status)
  if (status === SubscriptionStatus.PAUSED) {
    return $r('app.color.TEXT_SECONDARY')
  }
  const daysLeft = vo.daysUntilRenew ?? 0
  if (daysLeft < 0) {
    return $r('app.color.DANGER_INK')
  }
  if (daysLeft <= 3) {
    return $r('app.color.ON_WARNING_CONTAINER')
  }
  return $r('app.color.TEXT_GREEN')
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

export function subscriptionStatusContainerColor(status: SubscriptionStatus): ResourceColor {
  switch (status) {
    case SubscriptionStatus.PAUSED:
      return $r('app.color.ICON_CONTAINER_ORANGE')
    case SubscriptionStatus.CANCELED:
      return $r('app.color.ICON_CONTAINER_PINK')
    case SubscriptionStatus.FINISHED:
      return $r('app.color.ICON_CONTAINER_GREEN')
    default:
      return $r('app.color.ICON_CONTAINER_TEAL')
  }
}

export function subscriptionStatusIconColor(status: SubscriptionStatus): ResourceColor {
  switch (status) {
    case SubscriptionStatus.PAUSED:
      return $r('app.color.ICON_FOREGROUND_ORANGE')
    case SubscriptionStatus.CANCELED:
      return $r('app.color.ICON_FOREGROUND_PINK')
    case SubscriptionStatus.FINISHED:
      return $r('app.color.ICON_FOREGROUND_GREEN')
    default:
      return $r('app.color.ICON_FOREGROUND_TEAL')
  }
}

export function subscriptionStatusChipContainer(status: SubscriptionStatus): ResourceColor {
  switch (status) {
    case SubscriptionStatus.PAUSED:
      return $r('app.color.WARNING_CONTAINER')
    case SubscriptionStatus.CANCELED:
      return $r('app.color.ICON_CONTAINER_PINK')
    default:
      return $r('app.color.SURFACE_VARIANT')
  }
}

/** 状态 chip 文字色（对齐安卓 statusChipContent 2026-11-11 修复值） */
export function subscriptionStatusChipContent(status: SubscriptionStatus): ResourceColor {
  switch (status) {
    // PAUSED：onWarningContainer 与 warningContainer 成对（6.47:1）；ongoingInk 压橙浅底仅 2.52:1（§10 红线）
    case SubscriptionStatus.PAUSED:
      return $r('app.color.ON_WARNING_CONTAINER')
    case SubscriptionStatus.CANCELED:
      return $r('app.color.DANGER_INK')
    // FINISHED（default）：textSecondary on surfaceVariant 4.51:1；textTertiary 禁作可读标签（DESIGN-SPEC §10）
    default:
      return $r('app.color.TEXT_SECONDARY')
  }
}

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

export function subscriptionReminderContainerColor(type: SubscriptionReminderType): ResourceColor {
  return type === SubscriptionReminderType.TRIAL_END
    ? $r('app.color.ICON_CONTAINER_PINK')
    : $r('app.color.ICON_CONTAINER_ORANGE')
}

export function subscriptionReminderIconColor(type: SubscriptionReminderType): ResourceColor {
  return type === SubscriptionReminderType.TRIAL_END
    ? $r('app.color.ICON_FOREGROUND_PINK')
    : $r('app.color.ICON_FOREGROUND_ORANGE')
}

export function subscriptionReminderChipContainer(type: SubscriptionReminderType): ResourceColor {
  return type === SubscriptionReminderType.TRIAL_END
    ? $r('app.color.ICON_CONTAINER_PINK')
    : $r('app.color.WARNING_CONTAINER')
}

export function subscriptionReminderChipContent(type: SubscriptionReminderType): ResourceColor {
  return type === SubscriptionReminderType.TRIAL_END
    ? $r('app.color.DANGER_INK')
    // RENEW 与 statusChipContent(PAUSED) 同源裁定：onWarningContainer 成对令牌
    // （浅 #92400E 6.47:1）；ongoingInk 压 warningContainer 仅 2.51:1，禁作 12sp 标签色（§10）
    : $r('app.color.ON_WARNING_CONTAINER')
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
    return { container: $r('app.color.SURFACE_VARIANT'), content: $r('app.color.TEXT_SECONDARY') }
  }
  return daysLeft <= 3
    ? { container: $r('app.color.ICON_CONTAINER_PINK'), content: $r('app.color.DANGER_INK') }
    // 非紧急分支换 onWarningContainer（容器/内容成对令牌，6.47:1）：
    // ongoingInk v0.4 与 ongoing 同值 #D98A26，压橙浅底仅 2.52:1，不达正文红线（§10）
    : { container: $r('app.color.WARNING_CONTAINER'), content: $r('app.color.ON_WARNING_CONTAINER') }
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
