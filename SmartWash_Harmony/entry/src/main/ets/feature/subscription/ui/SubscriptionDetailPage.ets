// 订阅详情页 — 对齐安卓 feature/subscription/ui/detail/SubscriptionDetailPage.kt
//
// 续费信息卡（金额/周期/续费日/试用/自动续费/状态）+ 状态操作（active↔pause、cancel/finish，
// 按当前状态显隐；终态只读）+ 近 90 天使用统计 + 使用打卡按钮与打卡列表（可撤销）+
// 取消路径折叠卡（网址可打开/复制）。
//
// 详情数据实时拉取；编辑经表单页（返回 bump 订阅 tick 触发本页与列表页刷新）。
// 已知坑遵守：弹窗「先关窗再回调」；颜色一律取 Design* 令牌。

import { common, Want } from '@kit.AbilityKit'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../constant/DesignSystem'
import { BaseResponse } from '../../../network/BaseResponse'
import { BizCode } from '../../../network/HttpUtil'
import {
  SUBSCRIPTION_FLAG_ON,
  SUBSCRIPTION_PAGE_SIZE,
  SubscriptionStatus,
  SubscriptionStatsItemVo,
  SubscriptionText,
  SubscriptionVo,
  UsageLogVo,
  cancelSubscription,
  checkInUsage,
  deleteSubscription,
  deleteUsage,
  finishSubscription,
  getStats,
  getSubscription,
  pageUsages,
  pauseSubscription,
  resumeSubscription,
  subscriptionCycleTypeFromRaw
} from '../api'
import { formatAmount } from '../logic/SubscriptionRules'
import {
  SubscriptionPageHeader,
  SubscriptionSectionTitle,
  SubscriptionTagChip,
  subscriptionAmountText,
  subscriptionCopyText,
  subscriptionCycleDescription,
  subscriptionFormat,
  subscriptionRenewRelativeColor,
  subscriptionRenewRelativeText,
  subscriptionStatusContainerColor,
  subscriptionStatusIcon,
  subscriptionStatusIconColor,
  subscriptionStatusOf,
  subscriptionStatusText
} from './SubscriptionUiCommon'
import { AppButton, AppButtonVariant, AppConfirmDialog, EmptyState, IconBox, SkeletonBlock, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { PagingController } from '../../../utils/PagingUtil'
import { getNumberParam, pathStack } from '../../../utils/PathStackUtil'
import { SUBSCRIPTION_TICK_KEY, bumpSubscriptionRefresh } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'
import { InfoRow } from '../../../view/common/InfoRow'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'subscriptionDetailPage'

@Builder
export function SubscriptionDetailBuilder() {
  SubscriptionDetailPage()
}

@Component
struct SubscriptionDetailPage {
  @State detail: SubscriptionVo | null = null
  @State isLoading: boolean = true
  @State isLoadError: boolean = false
  @State statsItem: SubscriptionStatsItemVo | null = null
  @State isLoadingStats: boolean = false
  @State isStatsError: boolean = false
  @State isTransitioning: boolean = false
  @State isDeleting: boolean = false
  @State isCheckingIn: boolean = false
  @State isUndoing: boolean = false
  @State usageList: Array<UsageLogVo> = []
  @State isLoadingUsageFirst: boolean = true
  @State isUsageFirstError: boolean = false
  @State isLoadingMoreUsage: boolean = false
  @State usageAppendError: boolean = false
  @State cancelPathExpanded: boolean = false
  @State pendingUndoId: number = -1
  // 跨页刷新信号（自增 tick）：编辑/删除/状态流转/打卡/标记已读成功后 bump
  @StorageLink(SUBSCRIPTION_TICK_KEY) @Watch('onSubscriptionTick') subscriptionTick: number = 0
  private subscriptionId: number = -1
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchUsagePage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMoreUsage = loading
    }
  )

  async aboutToAppear(): Promise<void> {
    const param = getNumberParam(RouteNames.SUBSCRIPTION_DETAIL)
    this.subscriptionId = param !== null && param > 0 ? param : -1
    await this.reloadAll()
  }

  /** 消费订阅变更 tick：整页重拉（自增保证每次变更都触发） */
  private onSubscriptionTick(): void {
    logger.debug(TAG, '消费到订阅变更信号，重拉详情')
    this.reloadAll()
  }

  /** 详情 + 统计 + 打卡分页整页重拉 */
  private async reloadAll(): Promise<void> {
    await Promise.all([this.loadDetail(), this.loadStats()])
    await this.resetUsages()
  }

  private async loadDetail(): Promise<void> {
    this.isLoading = this.detail === null
    this.isLoadError = false
    try {
      const res = await getSubscription(this.subscriptionId)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.detail = res.data
        this.isLoadError = false
      } else {
        logger.warn(TAG, `订阅详情加载失败 id=${this.subscriptionId} code=${res.code ?? 0}`)
        this.isLoadError = true
      }
    } catch (err) {
      logger.error(TAG, '订阅详情加载异常', err instanceof Error ? err.message : '')
      this.isLoadError = true
    } finally {
      this.isLoading = false
    }
  }

  private async loadStats(): Promise<void> {
    this.isLoadingStats = true
    this.isStatsError = false
    try {
      const res = await getStats()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.statsItem = res.data.items.find((item: SubscriptionStatsItemVo): boolean =>
          item.subscriptionId === this.subscriptionId) ?? null
        this.isStatsError = false
      } else {
        logger.warn(TAG, `订阅统计加载失败 code=${res.code ?? 0}`)
        this.isStatsError = true
      }
    } catch (err) {
      logger.error(TAG, '订阅统计加载异常', err instanceof Error ? err.message : '')
      this.isStatsError = true
    } finally {
      this.isLoadingStats = false
    }
  }

  // ---------- 使用打卡 ----------

  private async resetUsages(): Promise<void> {
    this.usageAppendError = false
    this.paging.reset()
    this.usageList = []
    this.isLoadingUsageFirst = true
    await this.fetchUsagePage(1)
    this.isLoadingUsageFirst = false
  }

  async fetchUsagePage(page: number): Promise<boolean> {
    try {
      const res = await pageUsages(this.subscriptionId, page, SUBSCRIPTION_PAGE_SIZE)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const records: Array<UsageLogVo> = res.data.records
        if (page === 1) {
          this.usageList = []
          this.isUsageFirstError = false
        }
        if (records.length === 0) {
          this.usageAppendError = false
          return false
        }
        this.usageList.push(...records)
        this.usageAppendError = false
        return true
      }
      if (page === 1) {
        this.isUsageFirstError = true
        return false
      }
      this.usageAppendError = true
      throw new Error(SubscriptionText.subscription_usage_error_load)
    } catch (err) {
      if (page === 1) {
        this.isUsageFirstError = true
        return false
      }
      this.usageAppendError = true
      throw new Error(SubscriptionText.subscription_usage_error_load)
    }
  }

  // ---------- 状态机操作 ----------

  /** 状态流转（pause/resume/cancel/finish）：成功后 toast + bump 变更信号（tick 驱动本页/列表刷新） */
  private async doTransition(action: string): Promise<void> {
    if (this.isTransitioning) {
      return
    }
    this.isTransitioning = true
    try {
      let res: BaseResponse<null>
      if (action === 'pause') {
        res = await pauseSubscription(this.subscriptionId)
      } else if (action === 'resume') {
        res = await resumeSubscription(this.subscriptionId)
      } else if (action === 'cancel') {
        res = await cancelSubscription(this.subscriptionId)
      } else {
        res = await finishSubscription(this.subscriptionId)
      }
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `订阅状态流转成功 id=${this.subscriptionId} action=${action}`)
        showToastSafety(SubscriptionText.subscription_toast_status_updated)
        bumpSubscriptionRefresh()
      } else {
        logger.warn(TAG, `订阅状态流转失败 action=${action} code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, SubscriptionText.subscription_error_submit)
      }
    } catch (err) {
      logger.error(TAG, '订阅状态流转异常', err instanceof Error ? err.message : '')
      showToastSafety('', SubscriptionText.subscription_error_submit)
    } finally {
      this.isTransitioning = false
    }
  }

  /** 删除：成功后 toast + bump + 返回列表 */
  private async deleteDetail(): Promise<void> {
    if (this.isDeleting) {
      return
    }
    this.isDeleting = true
    try {
      const res = await deleteSubscription(this.subscriptionId)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `订阅删除成功 id=${this.subscriptionId}`)
        showToastSafety(SubscriptionText.subscription_toast_delete_success)
        bumpSubscriptionRefresh()
        pathStack.pop()
      } else {
        logger.warn(TAG, `订阅删除失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, SubscriptionText.subscription_error_submit)
      }
    } catch (err) {
      logger.error(TAG, '订阅删除异常', err instanceof Error ? err.message : '')
      showToastSafety('', SubscriptionText.subscription_error_submit)
    } finally {
      this.isDeleting = false
    }
  }

  /** 今日打卡 */
  private async checkIn(): Promise<void> {
    if (this.isCheckingIn) {
      return
    }
    this.isCheckingIn = true
    try {
      const res = await checkInUsage(this.subscriptionId, {})
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `订阅打卡成功 id=${this.subscriptionId}`)
        showToastSafety(SubscriptionText.subscription_usage_check_in_done)
        bumpSubscriptionRefresh()
      } else {
        logger.warn(TAG, `订阅打卡失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, SubscriptionText.subscription_error_submit)
      }
    } catch (err) {
      logger.error(TAG, '订阅打卡异常', err instanceof Error ? err.message : '')
      showToastSafety('', SubscriptionText.subscription_error_submit)
    } finally {
      this.isCheckingIn = false
    }
  }

  /** 撤销打卡 */
  private async undoUsage(): Promise<void> {
    if (this.isUndoing || this.pendingUndoId <= 0) {
      return
    }
    this.isUndoing = true
    try {
      const res = await deleteUsage(this.subscriptionId, this.pendingUndoId)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `订阅打卡撤销成功 id=${this.subscriptionId} usageId=${this.pendingUndoId}`)
        showToastSafety(SubscriptionText.subscription_toast_undo_success)
        this.pendingUndoId = -1
        bumpSubscriptionRefresh()
      } else {
        logger.warn(TAG, `打卡撤销失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, SubscriptionText.subscription_error_submit)
      }
    } catch (err) {
      logger.error(TAG, '打卡撤销异常', err instanceof Error ? err.message : '')
      showToastSafety('', SubscriptionText.subscription_error_submit)
    } finally {
      this.isUndoing = false
    }
  }

  // ---------- 弹窗 ----------
  // 控制器必须由结构体字段持有（与 Setting.ets / DivCastPage.ets 同款）：
  // 在方法内临时 new 的控制器，框架不会把 controller 注入弹窗内容实例，
  // 弹窗内 this.controller 为 undefined，点击「确认」即抛
  // 「Cannot read property close of undefined」jscrash。
  // builder 为闭包，入参（订阅名/打卡日期）在 open() 时读取，保证内容最新。
  private cancelDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: SubscriptionText.subscription_confirm_cancel_title,
      message: subscriptionFormat(SubscriptionText.subscription_confirm_cancel_message,
        this.detail?.name ?? ''),
      isDanger: true,
      confirmText: SubscriptionText.subscription_action_cancel_sub,
      onConfirm: (): void => {
        this.doTransition('cancel')
      },
      onDismiss: (): void => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private finishDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: SubscriptionText.subscription_confirm_finish_title,
      message: subscriptionFormat(SubscriptionText.subscription_confirm_finish_message,
        this.detail?.name ?? ''),
      isDanger: true,
      confirmText: SubscriptionText.subscription_action_finish,
      onConfirm: (): void => {
        this.doTransition('finish')
      },
      onDismiss: (): void => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private deleteDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: SubscriptionText.subscription_confirm_delete_title,
      message: subscriptionFormat(SubscriptionText.subscription_confirm_delete_message,
        this.detail?.name ?? ''),
      isDanger: true,
      confirmText: SubscriptionText.subscription_action_delete,
      onConfirm: (): void => {
        this.deleteDetail()
      },
      onDismiss: (): void => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private undoDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: SubscriptionText.subscription_confirm_delete_usage_title,
      message: subscriptionFormat(SubscriptionText.subscription_confirm_delete_usage_message,
        this.undoDateText()),
      isDanger: true,
      confirmText: SubscriptionText.subscription_usage_undo,
      onConfirm: (): void => {
        this.undoUsage()
      },
      onDismiss: (): void => {
        this.pendingUndoId = -1
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private showCancelDialog(): void {
    this.cancelDialogController.open()
  }

  private showFinishDialog(): void {
    this.finishDialogController.open()
  }

  private showDeleteDialog(): void {
    this.deleteDialogController.open()
  }

  private showUndoDialog(usageId: number): void {
    this.pendingUndoId = usageId
    this.undoDialogController.open()
  }

  /** 复制取消网址 */
  private async copyCancelUrl(url: string): Promise<void> {
    const success = await subscriptionCopyText(url)
    if (success) {
      showToastSafety(SubscriptionText.subscription_copy_success)
    } else {
      showToastSafety(SubscriptionText.subscription_open_failed)
    }
  }

  /** 浏览器打开取消网址（无浏览器兜底提示，对齐安卓 openInBrowser） */
  private openInBrowser(url: string): void {
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
      context.startAbility(want).catch(() => {
        showToastSafety(SubscriptionText.subscription_open_failed)
      })
    } catch (err) {
      logger.error(TAG, '浏览器拉起失败', err instanceof Error ? err.message : '')
      showToastSafety(SubscriptionText.subscription_open_failed)
    }
  }

  // ---------- 派生 ----------

  private isTerminal(): boolean {
    return this.detail !== null &&
      (subscriptionStatusOf(this.detail.status) === SubscriptionStatus.CANCELED ||
        subscriptionStatusOf(this.detail.status) === SubscriptionStatus.FINISHED)
  }

  private status(): SubscriptionStatus {
    return this.detail !== null ? subscriptionStatusOf(this.detail.status) : SubscriptionStatus.ACTIVE
  }

  /** 待撤销打卡的使用日期（弹窗文案用；pendingUndoId 在 open 前赋值） */
  private undoDateText(): string {
    const usage = this.usageList.find((item: UsageLogVo): boolean => item.usageId === this.pendingUndoId)
    return usage?.usageDate ?? ''
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        SubscriptionPageHeader({
          title: SubscriptionText.subscription_detail_title,
          action1Text: this.isTerminal() ? '' : SubscriptionText.subscription_action_edit,
          action2Text: SubscriptionText.subscription_action_delete,
          onAction1: (): void => {
            pathStack.pushPathByName(RouteNames.SUBSCRIPTION_EDIT, this.subscriptionId)
          },
          onAction2: (): void => {
            this.showDeleteDialog()
          }
        })

        if (this.isLoading) {
          SkeletonList({ rows: 6 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isLoadError || this.detail === null) {
          Column() {
            EmptyState({ message: SubscriptionText.subscription_error_load_detail }) {
              Column() {
                AppButton({
                  small: true,
                  text: SubscriptionText.subscription_detail_retry,
                  click: (): void => {
                    this.reloadAll()
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
            Column({ space: DesignDimens.CARD_SPACING }) {
              // ---------- 续费信息卡 ----------
              Column() {
                Row() {
                  IconBox({
                    icon: subscriptionStatusIcon(this.status()),
                    sizeValue: 44,
                    iconSize: 24,
                    containerColor: subscriptionStatusContainerColor(this.status()),
                    iconColor: subscriptionStatusIconColor(this.status())
                  })

                  Text(this.detail?.name ?? '')
                    .fontSize(DesignFontSize.CARD_TITLE)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor(this.isTerminal() ? DesignColor.TEXT_TERTIARY : DesignColor.TEXT_PRIMARY)
                    .maxLines(2)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .layoutWeight(1)
                    .margin({ left: DesignDimens.SPACE_SM })

                  SubscriptionTagChip({
                    text: subscriptionStatusText(this.status()),
                    containerColor: this.isTerminal()
                      ? DesignColor.SURFACE_VARIANT
                      : subscriptionStatusContainerColor(this.status()),
                    contentColor: this.isTerminal()
                      ? DesignColor.TEXT_TERTIARY
                      : subscriptionStatusIconColor(this.status())
                  })
                }
                .width('100%')
                .alignItems(VerticalAlign.Center)

                InfoRow({
                    labelWidth: 88,
label: SubscriptionText.subscription_detail_amount,
                  value: subscriptionAmountText(this.detail?.amount ?? 0),
                  valueColor: this.isTerminal() ? DesignColor.TEXT_TERTIARY : DesignColor.PRIMARY_DARK,
                  valueWeight: DesignFontWeight.SEMI_BOLD,
                })
                InfoRow({
                    labelWidth: 88,
label: SubscriptionText.subscription_detail_cycle,
                  value: subscriptionCycleDescription(
                    subscriptionCycleTypeFromRaw(this.detail?.cycleType ?? 'month'),
                    this.detail?.cycleInterval ?? 1),
                })
                InfoRow({
                    labelWidth: 88,
label: SubscriptionText.subscription_detail_next_renew,
                  value: this.detail !== null ? subscriptionRenewRelativeText(this.detail) : '',
                  valueColor: this.detail !== null ? subscriptionRenewRelativeColor(this.detail) : DesignColor.TEXT_PRIMARY,
                })
                InfoRow({
                    labelWidth: 88,
label: SubscriptionText.subscription_detail_trial_end,
                  value: this.detail?.trialEndDate ?? SubscriptionText.subscription_value_none,
                })
                InfoRow({
                    labelWidth: 88,
label: SubscriptionText.subscription_detail_auto_renew,
                  value: this.detail?.autoRenew === SUBSCRIPTION_FLAG_ON
                    ? SubscriptionText.subscription_detail_auto_renew_on
                    : SubscriptionText.subscription_detail_auto_renew_off
                })
                InfoRow({
                    labelWidth: 88,
label: SubscriptionText.subscription_detail_remind_days,
                  value: this.detail !== null && this.detail.remindDaysBefore > 0
                    ? subscriptionFormat(SubscriptionText.subscription_detail_remind_days_format,
                      this.detail.remindDaysBefore)
                    : SubscriptionText.subscription_detail_remind_days_zero,
                })
                if ((this.detail?.note ?? '').length > 0) {
                  InfoRow({
                      labelWidth: 88,
label: SubscriptionText.subscription_detail_note,
                    value: this.detail?.note ?? ''
                  })
                }
                if (this.isTerminal()) {
                  Text(SubscriptionText.subscription_terminal_readonly)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor(DesignColor.TEXT_TERTIARY)
                    .margin({ top: DesignDimens.SPACE_XS })
                }
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .borderWidth(1)
              .borderColor(DesignColor.OUTLINE)
              .shadow({
                radius: DesignElevation.CARD_SHADOW_RADIUS,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: DesignElevation.CARD_SHADOW_OFFSET
              })

              // ---------- 状态操作（按当前状态显隐；终态只读） ----------
              if (!this.isTerminal()) {
                Column({ space: DesignDimens.SPACE_SM }) {
                  Row({ space: DesignDimens.SPACE_SM }) {
                    AppButton({
                      text: this.status() === SubscriptionStatus.ACTIVE
                        ? SubscriptionText.subscription_action_pause
                        : SubscriptionText.subscription_action_resume,
                      variant: AppButtonVariant.SECONDARY,
                      loading: this.isTransitioning,
                      click: (): void => {
                        this.doTransition(this.status() === SubscriptionStatus.ACTIVE ? 'pause' : 'resume')
                      }
                    })
                      .layoutWeight(1)
                    AppButton({
                      text: SubscriptionText.subscription_action_finish,
                      variant: AppButtonVariant.SECONDARY,
                      isEnabled: !this.isTransitioning,
                      click: (): void => {
                        this.showFinishDialog()
                      }
                    })
                      .layoutWeight(1)
                  }
                  .width('100%')

                  AppButton({
                    text: SubscriptionText.subscription_action_cancel_sub,
                    variant: AppButtonVariant.SECONDARY,
                    contentColor: DesignColor.DANGER_INK,
                    isEnabled: !this.isTransitioning,
                    click: (): void => {
                      this.showCancelDialog()
                    }
                  })
                }
                .width('100%')
              }

              // ---------- 近 90 天使用统计 ----------
              Column() {
                SubscriptionSectionTitle({ text: SubscriptionText.subscription_stats_title })
                  .margin({ bottom: DesignDimens.SPACE_XS })

                if (this.isLoadingStats) {
                  SkeletonList({ rows: 2 })
                } else if (this.isStatsError) {
                  Text(SubscriptionText.subscription_stats_error)
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor(DesignColor.TEXT_SECONDARY)
                  Text(SubscriptionText.subscription_action_retry)
                    .fontSize(DesignFontSize.BUTTON_TEXT)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor(DesignColor.PRIMARY)
                    .textAlign(TextAlign.Center)
                    .width('100%')
                    .padding({ top: 12, bottom: 12 })
                    .onClick(() => {
                      this.loadStats()
                    })
                } else {
                  InfoRow({
                      labelWidth: 88,
label: subscriptionFormat(SubscriptionText.subscription_stats_usage_format,
                      this.statsItem?.usageCount90d ?? 0),
                    value: subscriptionFormat(SubscriptionText.subscription_stats_monthly_format,
                      formatAmount(this.statsItem?.monthlyNormalized ?? 0)),
                    valueColor: DesignColor.PRIMARY_DARK,
                    valueWeight: DesignFontWeight.SEMI_BOLD,
                    })
                  Text(this.statsItem !== null && this.statsItem.perUseCost !== null
                    ? subscriptionFormat(SubscriptionText.subscription_stats_per_use_format,
                      formatAmount(this.statsItem.perUseCost ?? 0))
                    : SubscriptionText.subscription_stats_per_use_none)
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor(this.statsItem !== null && this.statsItem.perUseCost !== null
                      ? DesignColor.TEXT_PRIMARY
                      : DesignColor.TEXT_TERTIARY)
                    .fontFeature(DesignFontFeature.TNUM)
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_XS })
                }
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .borderWidth(1)
              .borderColor(DesignColor.OUTLINE)
              .shadow({
                radius: DesignElevation.CARD_SHADOW_RADIUS,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: DesignElevation.CARD_SHADOW_OFFSET
              })

              // ---------- 使用打卡 ----------
              Column() {
                SubscriptionSectionTitle({ text: SubscriptionText.subscription_usage_title })
                  .margin({ bottom: DesignDimens.SPACE_XS })

                if (!this.isTerminal()) {
                  AppButton({
                    text: SubscriptionText.subscription_usage_check_in,
                    loading: this.isCheckingIn,
                    click: (): void => {
                      this.checkIn()
                    }
                  })
                    .margin({ bottom: DesignDimens.SPACE_SM })
                }

                if (this.isLoadingUsageFirst) {
                  SkeletonList({ rows: 2 })
                } else if (this.isUsageFirstError && this.usageList.length === 0) {
                  Text(SubscriptionText.subscription_usage_error_load)
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor(DesignColor.TEXT_SECONDARY)
                  Text(SubscriptionText.subscription_action_retry)
                    .fontSize(DesignFontSize.BUTTON_TEXT)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor(DesignColor.PRIMARY)
                    .textAlign(TextAlign.Center)
                    .width('100%')
                    .padding({ top: 12, bottom: 12 })
                    .onClick(() => {
                      this.resetUsages()
                    })
                } else if (this.usageList.length === 0) {
                  Text(SubscriptionText.subscription_usage_empty)
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor(DesignColor.TEXT_TERTIARY)
                    .textAlign(TextAlign.Center)
                    .width('100%')
                    .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
                } else {
                  ForEach(this.usageList, (log: UsageLogVo, index: number) => {
                    Column() {
                      if (index > 0) {
                        Divider()
                          .color(DesignColor.HAIRLINE)
                          .strokeWidth(0.5)
                      }
                      Row() {
                        Text(log.usageDate ?? '')
                          .fontSize(DesignFontSize.BODY_SMALL)
                          .fontColor(DesignColor.TEXT_PRIMARY)
                          .fontFeature(DesignFontFeature.TNUM)
                          .layoutWeight(1)

                        if (!this.isTerminal()) {
                          Text(SubscriptionText.subscription_usage_undo)
                            .fontSize(DesignFontSize.BUTTON_TEXT)
                            .fontWeight(DesignFontWeight.SEMI_BOLD)
                            .fontColor(DesignColor.DANGER_INK)
                            .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 12, bottom: 12 })
                            .onClick(() => {
                              this.showUndoDialog(log.usageId)
                            })
                        }
                      }
                      .width('100%')
                      .alignItems(VerticalAlign.Center)
                      .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
                    }
                    .width('100%')
                  }, (log: UsageLogVo) => `${log.usageId}`)

                  if (this.isLoadingMoreUsage) {
                    SkeletonBlock()
                      .width('100%')
                      .height(14)
                      .margin({ top: DesignDimens.SPACE_XS })
                  } else if (this.usageAppendError) {
                    Text(SubscriptionText.subscription_usage_error_load)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.DANGER_INK)
                      .width('100%')
                      .textAlign(TextAlign.Center)
                      .padding({ top: 12, bottom: 12 })
                      .onClick(() => {
                        this.paging.retry()
                      })
                  }
                }
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .borderWidth(1)
              .borderColor(DesignColor.OUTLINE)
              .shadow({
                radius: DesignElevation.CARD_SHADOW_RADIUS,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: DesignElevation.CARD_SHADOW_OFFSET
              })

              // ---------- 取消路径（折叠卡） ----------
              Column() {
                Row() {
                  SubscriptionSectionTitle({ text: SubscriptionText.subscription_cancel_path_title })
                    .layoutWeight(1)

                  IconBox({
                    icon: this.cancelPathExpanded
                      ? $r('sys.symbol.chevron_up')
                      : $r('sys.symbol.chevron_down'),
                    sizeValue: 32,
                    iconSize: 20,
                    containerColor: DesignColor.ICON_CONTAINER_ORANGE,
                    iconColor: DesignColor.ICON_FOREGROUND_ORANGE
                  })
                }
                .width('100%')
                .constraintSize({ minHeight: 48 })
                .alignItems(VerticalAlign.Center)
                .onClick(() => {
                  this.cancelPathExpanded = !this.cancelPathExpanded
                })

                if (this.cancelPathExpanded) {
                  Column() {
                    if ((this.detail?.cancelUrl ?? '').length === 0 &&
                      (this.detail?.cancelSteps ?? '').length === 0) {
                      Text(SubscriptionText.subscription_cancel_path_empty)
                        .fontSize(DesignFontSize.BODY_SMALL)
                        .fontColor(DesignColor.TEXT_TERTIARY)
                    } else {
                      if ((this.detail?.cancelUrl ?? '').length > 0) {
                        Text(SubscriptionText.subscription_cancel_path_url)
                          .fontSize(DesignFontSize.BUTTON_TEXT)
                          .fontColor(DesignColor.TEXT_SECONDARY)
                        Text(this.detail?.cancelUrl ?? '')
                          .fontSize(DesignFontSize.BODY_SMALL)
                          .fontColor(DesignColor.TEXT_PRIMARY)
                          .fontFeature(DesignFontFeature.TNUM)
                          .maxLines(3)
                          .textOverflow({ overflow: TextOverflow.Ellipsis })
                          .width('100%')
                          .margin({ top: DesignDimens.SPACE_XXS })
                        Row({ space: DesignDimens.SPACE_MD }) {
                          Text(SubscriptionText.subscription_action_open_browser)
                            .fontSize(DesignFontSize.BUTTON_TEXT)
                            .fontWeight(DesignFontWeight.SEMI_BOLD)
                            .fontColor(DesignColor.PRIMARY)
                            .padding({ top: 14, bottom: 14 })
                            .onClick(() => {
                              this.openInBrowser(this.detail?.cancelUrl ?? '')
                            })
                          Text(SubscriptionText.subscription_action_copy)
                            .fontSize(DesignFontSize.BUTTON_TEXT)
                            .fontWeight(DesignFontWeight.SEMI_BOLD)
                            .fontColor(DesignColor.PRIMARY)
                            .padding({ top: 14, bottom: 14 })
                            .onClick(() => {
                              this.copyCancelUrl(this.detail?.cancelUrl ?? '')
                            })
                        }
                        .margin({ top: DesignDimens.SPACE_XS })
                      }
                      if ((this.detail?.cancelSteps ?? '').length > 0) {
                        if ((this.detail?.cancelUrl ?? '').length > 0) {
                          Divider()
                            .color(DesignColor.HAIRLINE)
                            .strokeWidth(0.5)
                            .margin({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
                        }
                        Text(SubscriptionText.subscription_cancel_path_steps)
                          .fontSize(DesignFontSize.BUTTON_TEXT)
                          .fontColor(DesignColor.TEXT_SECONDARY)
                        Text(this.detail?.cancelSteps ?? '')
                          .fontSize(DesignFontSize.BODY_SMALL)
                          .fontColor(DesignColor.TEXT_PRIMARY)
                          .width('100%')
                          .margin({ top: DesignDimens.SPACE_XXS })
                      }
                    }
                  }
                  .width('100%')
                  .alignItems(HorizontalAlign.Start)
                  .margin({ top: DesignDimens.SPACE_XS })
                  .animation({ duration: 220, curve: Curve.EaseInOut })
                }
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .borderWidth(1)
              .borderColor(DesignColor.OUTLINE)
              .shadow({
                radius: DesignElevation.CARD_SHADOW_RADIUS,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: DesignElevation.CARD_SHADOW_OFFSET
              })
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
    }
    .hideTitleBar(true)
  }
}

// 信息行统一用公共二级组件 InfoRow（view/common/InfoRow.ets，40vp / 12sp）
