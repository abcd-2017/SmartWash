// 续费提醒列表页 — 对齐安卓 feature/subscription/ui/reminder/SubscriptionReminderPage.kt
//
// 未读/全部筛选（切换重拉），条目（订阅名 + 类型 renew/trial_end + 应提醒日），
// 点击标记已读（幂等；未读筛选下条目本地移除）。标记成功后 bump 订阅 tick，
// 订阅列表页红点随返回刷新（对齐安卓 notifySubscriptionChanged）。
//
// 已知坑遵守：条目内容依赖状态 → @ComponentV2 + @Param/@Event。

import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../constant/DesignSystem'
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
import { AppButton, EmptyState, IconBox, LoadingDots, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { bumpSubscriptionRefresh } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'
import { resColor } from '../../../utils/ResourceColor'

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
  // 切换筛选/重拉时的非空加载指示（audit-16 #5：旧列表不得静默滞留冒充新结果）
  @State isRefreshing: boolean = false
  // 正在标记已读的条目 id（防重复点击）
  @State markingId: number = -1

  async aboutToAppear(): Promise<void> {
    logger.debug(TAG, '提醒列表页挂载，开始首刷')
    await this.load()
  }

  private async load(): Promise<void> {
    this.isLoading = this.items.length === 0
    this.isRefreshing = true
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
      this.isRefreshing = false
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
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
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

        // 切换筛选/重拉的可见反馈（audit-16 #5，对齐安卓）：旧列表非空时——
        // 请求中渲染 LoadingDots；失败渲染错误行（文案 + small 重试），禁止静默滞留
        if (this.items.length > 0 && this.isRefreshing) {
          Row() {
            LoadingDots()
          }
          .width('100%')
          .justifyContent(FlexAlign.Center)
          .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
        } else if (this.items.length > 0 && this.isError) {
          Row() {
            Text(SubscriptionText.subscription_reminder_error_load)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(resColor('ERROR'))
              .layoutWeight(1)
            AppButton({
              small: true,
              text: SubscriptionText.subscription_action_retry,
              click: (): void => {
                this.load()
              }
            })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XS,
            bottom: DesignDimens.SPACE_XS
          })
        }

        if (this.isLoading) {
          SkeletonList({ rows: 5 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isError && this.items.length === 0) {
          Column() {
            EmptyState({ message: SubscriptionText.subscription_reminder_error_load }) {
              Column() {
                AppButton({
                  small: true,
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
          // 卡片间距 16（对齐安卓 verticalArrangement spacedBy(cardSpacing)，原相邻卡描边贴合）
          List({ space: DesignDimens.CARD_SPACING }) {
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
          .layoutWeight(1)
          // 列表边距对齐安卓 contentPadding：top=spaceXs(8)、bottom=max(spaceSm,navBars)（audit-16 #4）
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XS,
            bottom: DesignDimens.SPACE_SM + safeBottomVp()
          })
          .divider({ strokeWidth: 0, color: Color.Transparent })
          .scrollBar(BarState.Off)
        }
      }
      .width('100%')
      .height('100%')
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(resColor('BACKGROUND'))
      }
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
            // 已读弱化止步 textSecondary（on 白卡 4.90:1）：textTertiary 2.25:1 禁作可读正文（§10，audit-16 #2）
            .fontColor(this.isUnread() ? resColor('TEXT_PRIMARY') : resColor('TEXT_SECONDARY'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')

          Text(subscriptionFormat(SubscriptionText.subscription_reminder_date_format,
            // remindDate 空值兜底「—」，禁止格式串留洞（audit-16 #9）
            this.reminder.remindDate ?? SubscriptionText.subscription_value_none))
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor(resColor('TEXT_SECONDARY'))
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
            // 未读差异不能只靠颜色点：红点补读屏描述（DESIGN-SPEC §8/§10，audit-16 #6），
            // 已读卡由「已读」文本标签承担同一对语义
            SubscriptionStatusDot({
              color: resColor('DANGER'),
              accessibilityLabel: SubscriptionText.subscription_reminder_unread_dot
            })
              .margin({ top: DesignDimens.SPACE_XXS })
          } else {
            Text(SubscriptionText.subscription_reminder_read_tag)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              // 「已读」为可读状态标签，与主文案同口径收敛 textSecondary（audit-16 #3）
              .fontColor(resColor('TEXT_SECONDARY'))
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
    .backgroundColor(resColor('SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor(resColor('OUTLINE'))
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: resColor('SHADOW_COLOR_CARD'),
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
