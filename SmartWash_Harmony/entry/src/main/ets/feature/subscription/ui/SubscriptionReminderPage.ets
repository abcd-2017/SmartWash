// 续费提醒列表页 — 对齐安卓 feature/subscription/ui/reminder/SubscriptionReminderPage.kt
//
// 未读/全部筛选（切换重拉），条目（订阅名 + 类型 renew/trial_end + 应提醒日），
// 点击标记已读（幂等；未读筛选下条目本地移除）。标记成功后 bump 订阅 tick，
// 订阅列表页红点随返回刷新（对齐安卓 notifySubscriptionChanged）。
//
// 已知坑遵守：条目内容依赖状态 → @ComponentV2 + @Param/@Event。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { ReminderVo, SubscriptionText, listReminders, markReminderRead } from '../api'
import {
  SubscriptionPageHeader,
  SubscriptionSelectableChip,
  SubscriptionStatusDot,
  SubscriptionTagChip,
  subscriptionFormat,
  subscriptionReminderChipContainer,
  subscriptionReminderChipContent,
  subscriptionReminderContainerColor,
  subscriptionReminderIcon,
  subscriptionReminderIconColor,
  subscriptionReminderTypeOf,
  subscriptionReminderTypeText
} from './SubscriptionUiCommon'
import { AppButton, EmptyState, IconBox, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { bumpSubscriptionRefresh } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'subscriptionReminderPage'

@Builder
export function SubscriptionReminderBuilder() {
  SubscriptionReminderPage()
}

@Component
struct SubscriptionReminderPage {
  @State unreadOnly: boolean = true
  @State items: Array<ReminderVo> = []
  @State isLoading: boolean = true
  @State isError: boolean = false
  // 正在标记已读的条目 id（防重复点击）
  @State markingId: number = -1

  async aboutToAppear(): Promise<void> {
    logger.debug(TAG, '提醒列表页挂载，开始首刷')
    await this.load()
  }

  private async load(): Promise<void> {
    this.isLoading = this.items.length === 0
    this.isError = false
    try {
      const res = await listReminders(this.unreadOnly)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.items = res.data
        this.isError = false
        logger.debug(TAG, `提醒列表加载完成 count=${res.data.length} unreadOnly=${this.unreadOnly}`)
      } else {
        logger.warn(TAG, `提醒列表加载失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isError = true
      }
    } catch (err) {
      logger.error(TAG, '提醒列表加载异常', err instanceof Error ? err.message : '')
      this.isError = true
    } finally {
      this.isLoading = false
    }
  }

  private onSelectFilter(unreadOnly: boolean): void {
    if (this.unreadOnly === unreadOnly) {
      return
    }
    this.unreadOnly = unreadOnly
    this.load()
  }

  /** 标记已读（幂等）：未读模式本地移除，全部模式重拉；成功后 bump 订阅 tick */
  private async markRead(reminderId: number): Promise<void> {
    if (this.markingId === reminderId) {
      return
    }
    this.markingId = reminderId
    try {
      const res = await markReminderRead(reminderId)
      if (res.code === BizCode.SUCCESS) {
        logger.debug(TAG, `提醒已标记已读 reminderId=${reminderId}`)
        if (this.unreadOnly) {
          this.items = this.items.filter((item: ReminderVo): boolean => item.reminderId !== reminderId)
        } else {
          await this.load()
        }
        bumpSubscriptionRefresh()
      } else {
        logger.warn(TAG, `标记已读失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, SubscriptionText.subscription_reminder_error_load)
      }
    } catch (err) {
      logger.error(TAG, '标记已读异常', err instanceof Error ? err.message : '')
      showToastSafety('', SubscriptionText.subscription_reminder_error_load)
    } finally {
      this.markingId = -1
    }
  }

  build() {
    NavDestination() {
      Column() {
        SubscriptionPageHeader({ title: SubscriptionText.subscription_reminder_title })

        Row({ space: DesignDimens.SPACE_XS }) {
          SubscriptionSelectableChip({
            text: SubscriptionText.subscription_reminder_filter_unread,
            selected: this.unreadOnly,
            onSelect: (): void => {
              this.onSelectFilter(true)
            }
          })
          SubscriptionSelectableChip({
            text: SubscriptionText.subscription_reminder_filter_all,
            selected: !this.unreadOnly,
            onSelect: (): void => {
              this.onSelectFilter(false)
            }
          })
        }
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .margin({ bottom: DesignDimens.SPACE_XS })

        if (this.isLoading) {
          SkeletonList({ rows: 5 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isError && this.items.length === 0) {
          Column() {
            EmptyState({ message: SubscriptionText.subscription_reminder_error_load }) {
              Column() {
                AppButton({
                  text: SubscriptionText.subscription_action_retry,
                  click: (): void => {
                    this.load()
                  }
                })
              }
              .margin({ top: DesignDimens.SPACE_MD })
            }
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (this.items.length === 0) {
          Column() {
            EmptyState({
              message: this.unreadOnly
                ? SubscriptionText.subscription_reminder_empty_unread
                : SubscriptionText.subscription_reminder_empty_all
            })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          List() {
            ForEach(this.items, (reminder: ReminderVo) => {
              ListItem() {
                ReminderRow({
                  reminder: reminder,
                  onRowClick: (): void => {
                    this.markRead(reminder.reminderId)
                  }
                })
              }
            }, (reminder: ReminderVo) => `${reminder.reminderId}`)
          }
          .width('100%')
          .height('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, bottom: 8 + safeBottomVp() })
          .divider({ strokeWidth: 0, color: Color.Transparent })
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

// ==================== 提醒条目卡（内容依赖状态 → @ComponentV2 + @Param/@Event） ====================

/** 提醒条目卡：类型图标盒 + 订阅名 + 应提醒日；行尾类型 chip + 未读红点/已读标注 */
@ComponentV2
export struct ReminderRow {
  @Require @Param reminder: ReminderVo
  @Event onRowClick: () => void = () => {
  }

  private reminderType() {
    return subscriptionReminderTypeOf(this.reminder.remindType)
  }

  private isUnread(): boolean {
    return this.reminder.isRead === 0
  }

  build() {
    Column() {
      Row() {
        IconBox({
          icon: subscriptionReminderIcon(this.reminderType()),
          sizeValue: 36,
          iconSize: 20,
          containerColor: subscriptionReminderContainerColor(this.reminderType()),
          iconColor: subscriptionReminderIconColor(this.reminderType())
        })

        Column() {
          Text(this.reminder.subscriptionName)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(this.isUnread() ? DesignColor.TEXT_PRIMARY : DesignColor.TEXT_TERTIARY)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')

          Text(subscriptionFormat(SubscriptionText.subscription_reminder_date_format,
            this.reminder.remindDate ?? ''))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: DesignDimens.SPACE_XXS })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

        Column() {
          SubscriptionTagChip({
            text: subscriptionReminderTypeText(this.reminderType()),
            containerColor: subscriptionReminderChipContainer(this.reminderType()),
            contentColor: subscriptionReminderChipContent(this.reminderType())
          })

          if (this.isUnread()) {
            SubscriptionStatusDot({ color: DesignColor.DANGER })
              .margin({ top: DesignDimens.SPACE_XXS })
          } else {
            Text(SubscriptionText.subscription_reminder_read_tag)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_TERTIARY)
              .margin({ top: DesignDimens.SPACE_XXS })
          }
        }
        .alignItems(HorizontalAlign.End)
        .margin({ left: DesignDimens.SPACE_XS })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
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
    .onClick(() => {
      if (this.isUnread()) {
        this.onRowClick()
      }
    })
  }
}
