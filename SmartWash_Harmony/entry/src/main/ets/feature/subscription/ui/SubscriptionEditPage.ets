// 订阅添加/编辑表单页 — 对齐安卓 feature/subscription/ui/edit/SubscriptionEditPage.kt
//
// 同页双模式：路由 id ≤ 0 = 新建，编辑模式拉详情回填。
// 字段：名称/金额/月年切换/间隔期数/下次续费日选择器/试用截止（可选）/自动续费开关/
// 提醒提前天数/取消网址（一键打开浏览器·复制）+ 取消步骤多行备忘 + 备注。
// 校验错误仅提交后展示（规范 §3.7 基线⑤）；成功后 bump 订阅 tick + 返回上一页。
//
// 已知坑遵守：弹窗「先关窗再回调」；输入态由页面自持；颜色一律取 Design* 令牌。

import { common, Want } from '@kit.AbilityKit'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BaseResponse } from '../../../network/BaseResponse'
import { BizCode } from '../../../network/HttpUtil'
import {
  SUBSCRIPTION_CYCLE_INTERVAL_MAX,
  SUBSCRIPTION_CYCLE_INTERVAL_MIN,
  SUBSCRIPTION_FLAG_ON,
  SUBSCRIPTION_FLAG_OFF,
  SUBSCRIPTION_NAME_MAX,
  SUBSCRIPTION_NOTE_MAX,
  SUBSCRIPTION_REMIND_DAYS_MAX,
  SUBSCRIPTION_REMIND_DAYS_MIN,
  SaveSubscriptionForm,
  SubscriptionCycleType,
  SubscriptionText,
  SubscriptionVo,
  UpdateSubscriptionForm,
  createSubscription,
  getSubscription,
  updateSubscription
} from '../api'
import { formatAmount, formatDate, isValidCancelUrl } from '../logic/SubscriptionRules'
import {
  SubscriptionDatePickerDialog,
  SubscriptionPageHeader,
  SubscriptionSelectableChip,
  SubscriptionToggle,
  subscriptionCopyText
} from './SubscriptionUiCommon'
import { AppButton, AppButtonVariant, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack, getNumberParam } from '../../../utils/PathStackUtil'
import { bumpSubscriptionRefresh } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'

/** 输入框描边样式（width + color） */
interface SubscriptionBorderStyle {
  width: number
  color: number
}

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'subscriptionEditPage'

@Builder
export function SubscriptionEditBuilder() {
  SubscriptionEditPage()
}

@Component
struct SubscriptionEditPage {
  // ---------- 表单字段（金额/期数/提醒天数以文本承载，提交时统一解析校验） ----------
  @State name: string = ''
  @State amount: string = ''
  @State cycleType: SubscriptionCycleType = SubscriptionCycleType.MONTH
  @State interval: string = '1'
  @State nextRenewDate: string = formatDate(new Date())
  @State trialEndDate: string = ''
  @State autoRenew: boolean = true
  @State remindDays: string = '3'
  @State cancelUrl: string = ''
  @State cancelSteps: string = ''
  @State note: string = ''
  // ---------- 状态 ----------
  @State isLoadingDetail: boolean = false
  @State isLoadError: boolean = false
  @State isSubmitting: boolean = false
  @State submitError: string = ''
  // 编辑目标 id（新建为 -1）
  private editId: number = -1
  // 输入框聚焦态（描边高亮）
  @State private nameFocused: boolean = false
  @State private amountFocused: boolean = false
  @State private intervalFocused: boolean = false
  @State private remindDaysFocused: boolean = false
  @State private cancelUrlFocused: boolean = false
  @State private cancelStepsFocused: boolean = false
  @State private noteFocused: boolean = false

  aboutToAppear(): void {
    const param = getNumberParam(RouteNames.SUBSCRIPTION_EDIT)
    this.editId = param !== null && param > 0 ? param : -1
    if (this.isEditMode()) {
      this.loadDetail()
    }
  }

  private isEditMode(): boolean {
    return this.editId > 0
  }

  /** 编辑模式回填：详情 → 表单快照（日期解析失败防御为空，由用户重选） */
  private async loadDetail(): Promise<void> {
    this.isLoadingDetail = true
    this.isLoadError = false
    try {
      const res = await getSubscription(this.editId)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const vo = res.data
        this.name = vo.name
        this.amount = formatAmount(vo.amount)
        this.cycleType = vo.cycleType === SubscriptionCycleType.YEAR
          ? SubscriptionCycleType.YEAR : SubscriptionCycleType.MONTH
        this.interval = this.clampInt(vo.cycleInterval, SUBSCRIPTION_CYCLE_INTERVAL_MIN,
          SUBSCRIPTION_CYCLE_INTERVAL_MAX).toString()
        this.nextRenewDate = vo.nextRenewDate !== null && vo.nextRenewDate.length >= 10
          ? vo.nextRenewDate.substring(0, 10)
          : formatDate(new Date())
        this.trialEndDate = vo.trialEndDate !== null && vo.trialEndDate.length >= 10
          ? vo.trialEndDate.substring(0, 10)
          : ''
        this.autoRenew = vo.autoRenew === SUBSCRIPTION_FLAG_ON
        this.remindDays = this.clampInt(vo.remindDaysBefore, SUBSCRIPTION_REMIND_DAYS_MIN,
          SUBSCRIPTION_REMIND_DAYS_MAX).toString()
        this.cancelUrl = vo.cancelUrl ?? ''
        this.cancelSteps = vo.cancelSteps ?? ''
        this.note = vo.note ?? ''
        this.isLoadError = false
        logger.debug(TAG, `订阅详情回填完成 id=${this.editId}`)
      } else {
        logger.warn(TAG, `订阅详情加载失败 id=${this.editId} code=${res.code ?? 0}`)
        this.isLoadError = true
      }
    } catch (err) {
      logger.error(TAG, '订阅详情加载异常', err instanceof Error ? err.message : '')
      this.isLoadError = true
    } finally {
      this.isLoadingDetail = false
    }
  }

  private clampInt(value: number, min: number, max: number): number {
    if (Number.isNaN(value)) {
      return min
    }
    return Math.min(max, Math.max(min, value))
  }

  /** 客户端前置校验（镜像后端 SaveSubscriptionFrom 边界）；失败返回错误文案，null = 通过 */
  private validate(): string | null {
    const name = this.name.trim()
    if (name.length === 0) {
      return SubscriptionText.subscription_error_name_required
    }
    if (name.length > SUBSCRIPTION_NAME_MAX) {
      return SubscriptionText.subscription_error_name_too_long
    }
    const amountText = this.amount.trim()
    const amountValue = Number(amountText)
    if (amountText.length === 0 || Number.isNaN(amountValue) || amountValue <= 0) {
      return SubscriptionText.subscription_error_amount_invalid
    }
    if (this.nextRenewDate.length === 0) {
      return SubscriptionText.subscription_error_next_renew_required
    }
    const intervalValue = Number(this.interval.trim())
    if (!Number.isInteger(intervalValue) ||
      intervalValue < SUBSCRIPTION_CYCLE_INTERVAL_MIN ||
      intervalValue > SUBSCRIPTION_CYCLE_INTERVAL_MAX) {
      return SubscriptionText.subscription_error_interval_invalid
    }
    const remindValue = Number(this.remindDays.trim())
    if (!Number.isInteger(remindValue) ||
      remindValue < SUBSCRIPTION_REMIND_DAYS_MIN ||
      remindValue > SUBSCRIPTION_REMIND_DAYS_MAX) {
      return SubscriptionText.subscription_error_remind_days_invalid
    }
    const url = this.cancelUrl.trim()
    if (url.length > 0 && (url.length > 500 || !isValidCancelUrl(url))) {
      return SubscriptionText.subscription_error_cancel_url_invalid
    }
    if (this.cancelSteps.trim().length > 1000) {
      return SubscriptionText.subscription_error_cancel_steps_too_long
    }
    if (this.note.trim().length > SUBSCRIPTION_NOTE_MAX) {
      return SubscriptionText.subscription_error_note_too_long
    }
    return null
  }

  /** 提交（新建 → POST；编辑 → PUT）。成功 toast + bump 变更信号 + 返回 */
  private async submit(): Promise<void> {
    if (this.isSubmitting) {
      return
    }
    const error = this.validate()
    if (error !== null) {
      this.submitError = error
      return
    }
    this.isSubmitting = true
    this.submitError = ''
    const name = this.name.trim()
    const amountValue = Number(this.amount.trim())
    const intervalValue = Number(this.interval.trim())
    const remindValue = Number(this.remindDays.trim())
    const trial = this.trialEndDate.length > 0 ? this.trialEndDate : null
    const url = this.cancelUrl.trim().length > 0 ? this.cancelUrl.trim() : null
    const steps = this.cancelSteps.trim().length > 0 ? this.cancelSteps.trim() : null
    const note = this.note.trim().length > 0 ? this.note.trim() : null
    const autoRenew = this.autoRenew ? SUBSCRIPTION_FLAG_ON : SUBSCRIPTION_FLAG_OFF
    const cycleTypeRaw: string = this.cycleType
    try {
      let res: BaseResponse<SubscriptionVo>
      if (this.isEditMode()) {
        const form: UpdateSubscriptionForm = {
          'name': name,
          'amount': amountValue,
          'cycleType': cycleTypeRaw,
          'cycleInterval': intervalValue,
          'nextRenewDate': this.nextRenewDate,
          'trialEndDate': trial,
          'autoRenew': autoRenew,
          'remindDaysBefore': remindValue,
          'cancelUrl': url,
          'cancelSteps': steps,
          'note': note
        }
        res = await updateSubscription(this.editId, form)
      } else {
        const form: SaveSubscriptionForm = {
          'name': name,
          'amount': amountValue,
          'cycleType': cycleTypeRaw,
          'cycleInterval': intervalValue,
          'nextRenewDate': this.nextRenewDate,
          'trialEndDate': trial,
          'autoRenew': autoRenew,
          'remindDaysBefore': remindValue,
          'cancelUrl': url,
          'cancelSteps': steps,
          'note': note
        }
        res = await createSubscription(form)
      }
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        logger.info(TAG, `${this.isEditMode() ? '更新' : '创建'}订阅成功 name=${name}`)
        showToastSafety(this.isEditMode()
          ? SubscriptionText.subscription_toast_save_success
          : SubscriptionText.subscription_toast_create_success)
        bumpSubscriptionRefresh()
        pathStack.pop()
      } else {
        logger.warn(TAG, `订阅提交失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.submitError = res.message ?? SubscriptionText.subscription_error_submit
      }
    } catch (err) {
      logger.error(TAG, '订阅提交异常', err instanceof Error ? err.message : '')
      this.submitError = SubscriptionText.subscription_error_submit
    } finally {
      this.isSubmitting = false
    }
  }

  // ========== 日期选择弹窗控制器 ==========
  // 控制器必须由结构体字段持有（与 Setting.ets / DivCastPage.ets 同款）：
  // 在方法内临时 new 的控制器，框架不会把 controller 注入弹窗内容实例，
  // 弹窗内 this.controller 为 undefined，点击「确定」即抛
  // 「Cannot read property close of undefined」jscrash。
  // builder 为闭包，initialDate 在 open() 时读取，保证初始日期为当前表单值。
  private renewDatePickerController: CustomDialogController = new CustomDialogController({
    builder: SubscriptionDatePickerDialog({
      initialDate: this.nextRenewDate,
      title: SubscriptionText.subscription_form_next_renew_label,
      onConfirm: (date: string): void => {
        this.nextRenewDate = date
      },
      onDismiss: (): void => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private trialDatePickerController: CustomDialogController = new CustomDialogController({
    builder: SubscriptionDatePickerDialog({
      initialDate: this.trialEndDate,
      title: SubscriptionText.subscription_form_trial_label,
      onConfirm: (date: string): void => {
        this.trialEndDate = date
      },
      onDismiss: (): void => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private openRenewDatePicker(): void {
    this.renewDatePickerController.open()
  }

  private openTrialDatePicker(): void {
    this.trialDatePickerController.open()
  }

  /** 复制取消网址 */
  private async copyCancelUrl(): Promise<void> {
    const text = this.cancelUrl.trim()
    if (text.length === 0) {
      return
    }
    const success = await subscriptionCopyText(text)
    if (success) {
      showToastSafety(SubscriptionText.subscription_copy_success)
    } else {
      showToastSafety(SubscriptionText.subscription_open_failed)
    }
  }

  /** 浏览器打开取消网址（无浏览器兜底提示，对齐安卓 openInBrowser） */
  private openInBrowser(): void {
    const url = this.cancelUrl.trim()
    if (url.length === 0) {
      return
    }
    try {
      const context = getContext(this) as common.UIAbilityContext
      const want: Want = {
        action: 'ohos.want.action.viewData',
        uri: url,
        entities: ['entity.system.browsable']
      }
      // startAbility 返回 Promise：异步 reject（无浏览器/参数非法）try/catch 抓不到，必须 .catch 兜底
      context.startAbility(want).catch(() => {
        showToastSafety(SubscriptionText.subscription_open_failed)
      })
    } catch (err) {
      logger.error(TAG, '浏览器拉起失败', err instanceof Error ? err.message : '')
      showToastSafety(SubscriptionText.subscription_open_failed)
    }
  }

  /** 表单字段标签（caption 12/500 次文本） */
  @Builder
  FormLabel(text: string) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(DesignColor.TEXT_SECONDARY)
      .margin({ bottom: DesignDimens.SPACE_XS })
  }

  /** 输入框通用样式（surfaceVariant 底 + outline 描边 + 聚焦主色） */
  private inputBorder(focused: boolean): SubscriptionBorderStyle {
    return { width: 1, color: focused ? DesignColor.PRIMARY : DesignColor.OUTLINE }
  }

  build() {
    NavDestination() {
      Column() {
        SubscriptionPageHeader({
          title: this.isEditMode()
            ? SubscriptionText.subscription_edit_title_edit
            : SubscriptionText.subscription_edit_title_new
        })

        if (this.isEditMode() && this.isLoadingDetail) {
          SkeletonList({ rows: 6 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isEditMode() && this.isLoadError) {
          Column() {
            EmptyState({ message: SubscriptionText.subscription_error_load_detail }) {
              Column() {
                AppButton({
                  text: SubscriptionText.subscription_detail_retry,
                  click: (): void => {
                    this.loadDetail()
                  }
                })
              }
              .margin({ top: DesignDimens.SPACE_MD })
            }
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          Scroll() {
            Column() {
              Column() {
                // ---------- 名称 ----------
                this.FormLabel(SubscriptionText.subscription_form_name_label)
                TextInput({ text: this.name, placeholder: SubscriptionText.subscription_form_name_hint })
                  .height(48)
                  .fontSize(DesignFontSize.BODY)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .border(this.inputBorder(this.nameFocused))
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY })
                  .onFocus(() => {
                    this.nameFocused = true
                  })
                  .onBlur(() => {
                    this.nameFocused = false
                  })
                  .onChange((value: string) => {
                    this.name = value
                  })

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 每期金额 ----------
                this.FormLabel(SubscriptionText.subscription_form_amount_label)
                TextInput({ text: this.amount, placeholder: SubscriptionText.subscription_form_amount_hint })
                  .height(48)
                  .fontSize(DesignFontSize.BODY)
                  .type(InputType.NUMBER_DECIMAL)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .border(this.inputBorder(this.amountFocused))
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY })
                  .onFocus(() => {
                    this.amountFocused = true
                  })
                  .onBlur(() => {
                    this.amountFocused = false
                  })
                  .onChange((value: string) => {
                    this.amount = value
                  })

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 计费周期 ----------
                this.FormLabel(SubscriptionText.subscription_form_cycle_label)
                Row({ space: DesignDimens.SPACE_XS }) {
                  SubscriptionSelectableChip({
                    text: SubscriptionText.subscription_form_cycle_month,
                    selected: this.cycleType === SubscriptionCycleType.MONTH,
                    onSelect: (): void => {
                      this.cycleType = SubscriptionCycleType.MONTH
                    }
                  })
                  SubscriptionSelectableChip({
                    text: SubscriptionText.subscription_form_cycle_year,
                    selected: this.cycleType === SubscriptionCycleType.YEAR,
                    onSelect: (): void => {
                      this.cycleType = SubscriptionCycleType.YEAR
                    }
                  })
                }

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 间隔期数 ----------
                this.FormLabel(SubscriptionText.subscription_form_interval_label)
                TextInput({ text: this.interval, placeholder: '1' })
                  .height(48)
                  .fontSize(DesignFontSize.BODY)
                  .type(InputType.Number)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .border(this.inputBorder(this.intervalFocused))
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY })
                  .onFocus(() => {
                    this.intervalFocused = true
                  })
                  .onBlur(() => {
                    this.intervalFocused = false
                  })
                  .onChange((value: string) => {
                    this.interval = value
                  })
                Text(SubscriptionText.subscription_form_interval_helper)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(DesignColor.TEXT_TERTIARY)
                  .margin({ top: DesignDimens.SPACE_XS })

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 下次续费日 ----------
                this.FormLabel(SubscriptionText.subscription_form_next_renew_label)
                AppButton({
                  text: this.nextRenewDate.length > 0
                    ? this.nextRenewDate
                    : SubscriptionText.subscription_form_pick_date,
                  variant: AppButtonVariant.SECONDARY,
                  click: (): void => {
                    this.openRenewDatePicker()
                  }
                })

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 试用截止（可选，可清除） ----------
                this.FormLabel(SubscriptionText.subscription_form_trial_label)
                Row({ space: DesignDimens.SPACE_SM }) {
                  AppButton({
                    text: this.trialEndDate.length > 0
                      ? this.trialEndDate
                      : SubscriptionText.subscription_form_trial_none,
                    variant: AppButtonVariant.SECONDARY,
                    click: (): void => {
                      this.openTrialDatePicker()
                    }
                  })
                    .layoutWeight(1)

                  if (this.trialEndDate.length > 0) {
                    Text(SubscriptionText.subscription_form_trial_clear)
                      .fontSize(DesignFontSize.BUTTON_TEXT)
                      .fontWeight(DesignFontWeight.SEMI_BOLD)
                      .fontColor(DesignColor.PRIMARY)
                      .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 14, bottom: 14 })
                      .onClick(() => {
                        this.trialEndDate = ''
                      })
                  }
                }
                .width('100%')

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 自动续费 ----------
                Row() {
                  Column() {
                    Text(SubscriptionText.subscription_form_auto_renew_label)
                      .fontSize(DesignFontSize.BODY)
                      .fontColor(DesignColor.TEXT_PRIMARY)
                    Text(SubscriptionText.subscription_form_auto_renew_helper)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.TEXT_TERTIARY)
                      .margin({ top: DesignDimens.SPACE_XXS })
                  }
                  .alignItems(HorizontalAlign.Start)
                  .layoutWeight(1)

                  SubscriptionToggle({
                    checked: this.autoRenew,
                    onChange: (checked: boolean): void => {
                      this.autoRenew = checked
                    }
                  })
                }
                .width('100%')
                .alignItems(VerticalAlign.Center)

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 提醒提前天数 ----------
                this.FormLabel(SubscriptionText.subscription_form_remind_label)
                TextInput({ text: this.remindDays, placeholder: '3' })
                  .height(48)
                  .fontSize(DesignFontSize.BODY)
                  .type(InputType.Number)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .border(this.inputBorder(this.remindDaysFocused))
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY })
                  .onFocus(() => {
                    this.remindDaysFocused = true
                  })
                  .onBlur(() => {
                    this.remindDaysFocused = false
                  })
                  .onChange((value: string) => {
                    this.remindDays = value
                  })
                Text(SubscriptionText.subscription_form_remind_helper)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(DesignColor.TEXT_TERTIARY)
                  .margin({ top: DesignDimens.SPACE_XS })

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 取消网址 ----------
                this.FormLabel(SubscriptionText.subscription_form_cancel_url_label)
                TextInput({ text: this.cancelUrl, placeholder: SubscriptionText.subscription_form_cancel_url_hint })
                  .height(48)
                  .fontSize(DesignFontSize.BODY)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .border(this.inputBorder(this.cancelUrlFocused))
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY })
                  .onFocus(() => {
                    this.cancelUrlFocused = true
                  })
                  .onBlur(() => {
                    this.cancelUrlFocused = false
                  })
                  .onChange((value: string) => {
                    this.cancelUrl = value
                  })
                Text(SubscriptionText.subscription_form_cancel_url_helper)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(DesignColor.TEXT_TERTIARY)
                  .margin({ top: DesignDimens.SPACE_XS })
                if (this.cancelUrl.trim().length > 0) {
                  Row({ space: DesignDimens.SPACE_MD }) {
                    Text(SubscriptionText.subscription_action_open_browser)
                      .fontSize(DesignFontSize.BUTTON_TEXT)
                      .fontWeight(DesignFontWeight.SEMI_BOLD)
                      .fontColor(DesignColor.PRIMARY)
                      .padding({ top: 14, bottom: 14 })
                      .onClick(() => {
                        this.openInBrowser()
                      })
                    Text(SubscriptionText.subscription_action_copy)
                      .fontSize(DesignFontSize.BUTTON_TEXT)
                      .fontWeight(DesignFontWeight.SEMI_BOLD)
                      .fontColor(DesignColor.PRIMARY)
                      .padding({ top: 14, bottom: 14 })
                      .onClick(() => {
                        this.copyCancelUrl()
                      })
                  }
                  .margin({ top: DesignDimens.SPACE_XS })
                }

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 取消步骤备忘 ----------
                this.FormLabel(SubscriptionText.subscription_form_cancel_steps_label)
                TextInput({ text: this.cancelSteps, placeholder: SubscriptionText.subscription_form_cancel_steps_hint })
                  .height(96)
                  .fontSize(DesignFontSize.BODY)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .border(this.inputBorder(this.cancelStepsFocused))
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY })
                  .onFocus(() => {
                    this.cancelStepsFocused = true
                  })
                  .onBlur(() => {
                    this.cancelStepsFocused = false
                  })
                  .onChange((value: string) => {
                    this.cancelSteps = value
                  })

                Column().height(DesignDimens.SPACE_LG)

                // ---------- 备注 ----------
                this.FormLabel(SubscriptionText.subscription_form_note_label)
                TextInput({ text: this.note, placeholder: SubscriptionText.subscription_form_note_hint })
                  .height(96)
                  .fontSize(DesignFontSize.BODY)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .border(this.inputBorder(this.noteFocused))
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY })
                  .onFocus(() => {
                    this.noteFocused = true
                  })
                  .onBlur(() => {
                    this.noteFocused = false
                  })
                  .onChange((value: string) => {
                    this.note = value
                  })
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .borderWidth(1)
              .borderColor(DesignColor.OUTLINE)

              if (this.submitError.length > 0) {
                Text(this.submitError)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontColor(DesignColor.ERROR)
                  .margin({ top: DesignDimens.SPACE_SM })
              }

              AppButton({
                text: this.isEditMode()
                  ? SubscriptionText.subscription_submit_save
                  : SubscriptionText.subscription_submit_create,
                loading: this.isSubmitting,
                click: (): void => {
                  this.submit()
                }
              })
                .margin({ top: DesignDimens.SPACE_MD })
            }
            .width('100%')
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_SM,
              bottom: DesignDimens.SPACE_LG + safeBottomVp()
            })
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .align(Alignment.TopStart)
          .scrollBar(BarState.Off)
        }
      }
      .width('100%')
      .height('100%')
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}
