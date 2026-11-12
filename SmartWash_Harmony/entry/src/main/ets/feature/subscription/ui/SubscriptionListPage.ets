// 订阅列表页 — 对齐安卓 feature/subscription/ui/list/SubscriptionListPage.kt
//
// 分页（每页 10）+ 状态筛选 chips + 下拉刷新；四态：首刷骨架 / 首刷失败重试 / 空态 / 列表 + 尾部（加载骨架/失败重试）。
// 条目卡：名称/每期金额/周期描述 + 续费周期进度环 + 试用倒计时角标 + 未读提醒红点；
// canceled/finished 灰态（去色 + 状态 chip）。新建/提醒入口在页头。
//
// 跨页刷新：新建/编辑/删除/状态流转/标记已读成功 bump 订阅 tick（自增），
// 本页 @StorageLink + @Watch 消费重拉（对齐安卓 savedStateHandle CHANGED_KEY）。
//
// 已知坑遵守：条目卡内容依赖状态 → @ComponentV2 + @Param/@Event（@Builder 按值传参不响应更新）。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  SUBSCRIPTION_PAGE_SIZE,
  SubscriptionStatus,
  SubscriptionText,
  SubscriptionVo,
  pageSubscriptions,
  subscriptionCycleTypeFromRaw
} from '../api'
import {
  RenewProgressRing,
  SubscriptionBadgeColors,
  SubscriptionPageHeader,
  SubscriptionSelectableChip,
  SubscriptionStatusDot,
  SubscriptionTagChip,
  subscriptionAmountText,
  subscriptionCycleDescription,
  subscriptionFormat,
  subscriptionRenewCenterTextColor,
  subscriptionRenewFraction,
  subscriptionRenewRelativeColor,
  subscriptionRenewRelativeText,
  subscriptionRenewRingColor,
  subscriptionStatusChipContainer,
  subscriptionStatusChipContent,
  subscriptionStatusContainerColor,
  subscriptionStatusIcon,
  subscriptionStatusIconColor,
  subscriptionStatusText,
  subscriptionStatusOf,
  subscriptionTrialBadgeColors,
  subscriptionTrialBadgeText
} from './SubscriptionUiCommon'
import { AppButton, EmptyState, IconBox, SkeletonBlock, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { PagingController } from '../../../utils/PagingUtil'
import { pathStack } from '../../../utils/PathStackUtil'
import { SUBSCRIPTION_TICK_KEY } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'
import { resColor } from '../../../utils/ResourceColor'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'subscriptionListPage'

/** 状态筛选选项（null = 全部） */
interface FilterOption {
  status: SubscriptionStatus | null
  label: string
}

@Builder
export function SubscriptionListBuilder() {
  SubscriptionListPage()
}

@Component
struct SubscriptionListPage {
  @State list: Array<SubscriptionVo> = []
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  @State appendError: boolean = false
  @State statusFilter: SubscriptionStatus | null = null
  // 刷新会话门控（对齐安卓 2026-11-06 闪空态回归修复）：变更信号/筛选切换/下拉刷新都会重拉列表，
  // 会话期间「清空后、新加载尚未开始」的帧会命中空态分支闪一帧——会话期间列表非空保旧数据、
  // 列表为空走骨架；只有本轮加载结束（有数据，或确认空结果）才允许真实空态
  @State isRefreshSession: boolean = false
  // 跨页刷新信号（自增 tick）：新建/编辑/删除/状态流转/标记已读成功后 bump
  @StorageLink(SUBSCRIPTION_TICK_KEY) @Watch('onSubscriptionTick') subscriptionTick: number = 0
  private hasShown: boolean = false
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchPage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )

  async aboutToAppear(): Promise<void> {
    logger.debug(TAG, '订阅列表页挂载，开始首刷')
    this.isLoadingFirst = true
    await this.loadFirst()
    this.isLoadingFirst = false
  }

  /** 消费订阅变更 tick：重拉列表（自增保证每次变更都触发；会话门控防闪空态） */
  private onSubscriptionTick(): void {
    logger.debug(TAG, '消费到订阅变更信号，重拉列表')
    this.isRefreshSession = true
    this.loadFirst()
  }

  /** 筛选选项（全部/进行中/已暂停/已取消/已结束） */
  private filterOptions(): FilterOption[] {
    return [
      { status: null, label: SubscriptionText.subscription_filter_all },
      { status: SubscriptionStatus.ACTIVE, label: SubscriptionText.subscription_filter_active },
      { status: SubscriptionStatus.PAUSED, label: SubscriptionText.subscription_filter_paused },
      { status: SubscriptionStatus.CANCELED, label: SubscriptionText.subscription_filter_canceled },
      { status: SubscriptionStatus.FINISHED, label: SubscriptionText.subscription_filter_finished }
    ]
  }

  /**
   * 按页拉取订阅列表。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchPage(page: number): Promise<boolean> {
    const status = this.statusFilter
    try {
      const res = await pageSubscriptions(page, SUBSCRIPTION_PAGE_SIZE, status)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const records: Array<SubscriptionVo> = res.data.records
        if (page === 1) {
          // 刷新会话门控：新数据就绪后才替换旧列表（等待网络期间保留旧数据防闪「空态 + 去新建」）；
          // 空结果也在此放行（会话结束 → 真实空态）
          this.isFirstError = false
          this.isRefreshSession = false
          this.list = records
        }
        if (records.length === 0) {
          logger.debug(TAG, `分页封底 page=${page} 本页无数据`)
          this.appendError = false
          return false
        }
        this.appendError = false
        logger.debug(TAG, `分页加载成功 page=${page} count=${records.length} total=${res.data.total}`)
        return true
      }
      if (page === 1) {
        // 首刷/刷新失败：错误结束会话；已有数据保留（isFirstError 仅在列表为空时展示错误态）
        logger.warn(TAG, `订阅首刷失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isFirstError = true
        this.isRefreshSession = false
        showToastSafety(res.message, SubscriptionText.subscription_error_load)
        return false
      }
      logger.warn(TAG, `订阅追加失败 page=${page} code=${res.code ?? 0} msg=${res.message ?? ''}`)
      this.appendError = true
      showToastSafety(res.message, SubscriptionText.subscription_error_load)
      throw new Error(SubscriptionText.subscription_error_load)
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：与「追加失败」同语义（reject 供分页器记失败页）
      if (page === 1) {
        this.isFirstError = true
        this.isRefreshSession = false
        showToastSafety('', SubscriptionText.subscription_error_load)
        return false
      }
      this.appendError = true
      showToastSafety('', SubscriptionText.subscription_error_load)
      throw new Error(SubscriptionText.subscription_error_load)
    }
  }

  /**
   * 整列表重置重拉（变更信号/筛选切换/下拉刷新/错误重试共用，不亮 Refresh 指示器）。
   * 刷新会话门控：不在请求前清空列表（旧数据保留防闪空态），新数据在 fetchPage 成功后整体替换；
   * 会话开闭由各入口置位、fetchPage(1) 成功/失败复位。
   */
  async loadFirst(): Promise<void> {
    this.appendError = false
    this.paging.reset()
    await this.fetchPage(1)
  }

  /** 下拉刷新：进入刷新会话，保持列表语义由 Refresh 指示器承担 */
  private async refreshList(): Promise<void> {
    this.isRefreshing = true
    this.isRefreshSession = true
    await this.loadFirst()
    this.isRefreshing = false
  }

  /** 状态筛选切换：重建分页（对齐安卓 pagingFlow 重触发；切筛选同属刷新清空窗口，进入会话门控） */
  private onSelectFilter(status: SubscriptionStatus | null): void {
    if (this.statusFilter === status) {
      return
    }
    this.statusFilter = status
    this.isRefreshSession = true
    this.loadFirst()
  }

  private goDetail(vo: SubscriptionVo): void {
    pathStack.pushPathByName(RouteNames.SUBSCRIPTION_DETAIL, vo.subscriptionId)
  }

  private goEdit(): void {
    pathStack.pushPathByName(RouteNames.SUBSCRIPTION_EDIT, -1)
  }

  private goReminders(): void {
    pathStack.pushPathByName(RouteNames.SUBSCRIPTION_REMINDERS, null)
  }

  /** 筛选 chip 行（水平滚动，防窄屏溢出） */
  @Builder
  FilterRow() {
    Scroll() {
      Row({ space: DesignDimens.SPACE_XS }) {
        ForEach(this.filterOptions(), (option: FilterOption) => {
          SubscriptionSelectableChip({
            text: option.label,
            selected: this.statusFilter === option.status,
            onSelect: (): void => {
              this.onSelectFilter(option.status)
            }
          })
        }, (option: FilterOption) => option.label)
      }
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    }
    .width('100%')
    .align(Alignment.Start)
    .scrollable(ScrollDirection.Horizontal)
    .scrollBar(BarState.Off)
    .margin({ bottom: DesignDimens.SPACE_XS })
  }

  /** 空态/错误态（规范 §3.6：滚筒圆母题 + 引导文案 + 行动按钮） */
  @Builder
  Placeholder(message: string, actionText: string, isError: boolean) {
    Column() {
      EmptyState({ message: message }) {
        Column() {
          AppButton({
            small: true,
            text: actionText,
            click: (): void => {
              if (isError) {
                // 重试进入刷新会话（对齐安卓 retry → Loading → 骨架）：等待期显示骨架而非停留错误态
                this.isFirstError = false
                this.isRefreshSession = true
                this.loadFirst()
              } else {
                this.goEdit()
              }
            }
          })
        }
        .margin({ top: DesignDimens.SPACE_MD })
      }
    }
    .width('100%')
    .layoutWeight(1)
    .justifyContent(FlexAlign.Center)
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        SubscriptionPageHeader({
          title: SubscriptionText.subscription_title,
          action1Text: SubscriptionText.subscription_action_reminders,
          action2Text: SubscriptionText.subscription_action_new,
          onAction1: (): void => {
            this.goReminders()
          },
          onAction2: (): void => {
            this.goEdit()
          }
        })

        this.FilterRow()

        // 四态分支（对齐安卓刷新会话门控）：
        // 错误分支前置（门控不得掩盖真实错误，否则刷新失败时先闪一帧骨架）；
        // 骨架覆盖首刷与刷新会话清空帧；空态必为会话结束后的真实空结果
        if (this.isFirstError && this.list.length === 0) {
          this.Placeholder(SubscriptionText.subscription_error_load,
            SubscriptionText.subscription_action_retry, true)
        } else if (this.list.length === 0 && (this.isLoadingFirst || this.isRefreshSession)) {
          SkeletonList({ rows: 5 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.list.length === 0) {
          this.Placeholder(SubscriptionText.subscription_empty_message,
            SubscriptionText.subscription_empty_action, false)
        } else {
          Refresh({ refreshing: $$this.isRefreshing }) {
            // space = 卡片间距 16（对齐安卓 spacedBy(cardSpacing)，缺失时卡片贴死）
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.list, (vo: SubscriptionVo) => {
                ListItem() {
                  SubscriptionCard({
                    vo: vo,
                    onCardClick: (): void => {
                      this.goDetail(vo)
                    }
                  })
                }
              }, (vo: SubscriptionVo) => `${vo.subscriptionId}`)

              // 尾部：加载中骨架 / 失败重试（对齐安卓 append LoadState）
              ListItem() {
                if (this.isLoadingMore) {
                  Row() {
                    SkeletonBlock()
                      .width(120)
                      .height(14)
                  }
                  .width('100%')
                  .padding({ top: 12, bottom: 12 })
                  .justifyContent(FlexAlign.Center)
                } else if (this.appendError) {
                  // 重试是错误态唯一出路：上下 padding 16 凑足 48 热区（DESIGN-SPEC §3），字重对齐安卓 MEDIUM
                  Text(SubscriptionText.subscription_error_load)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .fontColor($r('app.color.DANGER_INK'))
                    .width('100%')
                    .textAlign(TextAlign.Center)
                    .padding({
                      top: DesignDimens.SPACE_MD,
                      bottom: DesignDimens.SPACE_MD
                    })
                    .onClick(() => {
                      this.paging.retry()
                    })
                }
              }
            }
            .width('100%')
            .height('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 8, bottom: 8 + safeBottomVp() })
            .divider({ strokeWidth: 0, color: Color.Transparent })
            .onScrollIndex((start: number, end: number) => {
              this.paging.onScrollIndex(end, this.list.length)
            })
            .onScrollFrameBegin((offset: number) => {
              this.paging.onScrollFrameBegin(offset)
              return { offsetRemain: offset }
            })
          }
          .onRefreshing(() => {
            this.refreshList()
          })
        }
      }
      .width('100%')
      .height('100%')
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 首次 onShown 与 aboutToAppear 去重（变更 tick 已由 @Watch 消费，此处仅兜底）
      if (!this.hasShown) {
        this.hasShown = true
      }
    })
  }
}

// ==================== 订阅条目卡（内容依赖状态 → @ComponentV2 + @Param/@Event） ====================

/**
 * 订阅条目卡（规范 §3.1 唯一卡片画法）：图标盒 36 + 名称（含未读红点）+ 周期描述 +
 * 续费进度环 + 金额（tnum）+ 试用倒计时角标 + 续费日相对描述 + 状态 chip；终态整卡去灰。
 */
@ComponentV2
export struct SubscriptionCard {
  @Require @Param vo: SubscriptionVo
  @Event onCardClick: () => void = () => {
  }

  /** 状态（防御回落 active） */
  private status(): SubscriptionStatus {
    return subscriptionStatusOf(this.vo.status)
  }

  /** 终态只读（canceled/finished） */
  private isTerminal(): boolean {
    return this.status() === SubscriptionStatus.CANCELED || this.status() === SubscriptionStatus.FINISHED
  }

  /** 进度环是否渲染（终态与不可解析数据不渲染） */
  private showProgress(): boolean {
    return !this.isTerminal() && subscriptionRenewFraction(this.vo) !== null
  }

  /** 进度环填充度（调用前经 showProgress 判定，不可解析兜底 0） */
  private progressFraction(): number {
    return subscriptionRenewFraction(this.vo) ?? 0
  }

  /** 距续费天数（缺失兜底 0） */
  private daysLeft(): number {
    return this.vo.daysUntilRenew ?? 0
  }

  /** 试用角标文案（无试用/已过期返回空串） */
  private trialBadgeText(): string {
    return subscriptionTrialBadgeText(this.vo)
  }

  /** 试用角标配色（无试用/已过期回落中性档） */
  private trialBadgeColors(): SubscriptionBadgeColors {
    return subscriptionTrialBadgeColors(this.vo)
  }

  build() {
    Column() {
      Row() {
        IconBox({
          icon: subscriptionStatusIcon(this.status()),
          sizeValue: 36,
          iconSize: 20,
          containerColor: subscriptionStatusContainerColor(this.status()),
          iconColor: subscriptionStatusIconColor(this.status())
        })

        Column() {
          Row() {
            // flexShrink 替代 layoutWeight：名称占内容宽可压缩省略（近似安卓 weight(fill=false)），红点紧跟名称
            Text(this.vo.name)
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(this.isTerminal() ? $r('app.color.TEXT_SECONDARY') : $r('app.color.TEXT_PRIMARY'))
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .flexShrink(1)

            if (this.vo.unreadReminderCount > 0) {
              // 未读红点补读屏语义（§10 颜色不是唯一信息载体）：朗读「N 条未读提醒」
              SubscriptionStatusDot({
                color: $r('app.color.DANGER'),
                accessibilityLabel: subscriptionFormat(SubscriptionText.subscription_unread_reminders_format,
                  this.vo.unreadReminderCount)
              })
                .margin({ left: DesignDimens.SPACE_XS })
            }

            Blank()
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)

          Text(subscriptionCycleDescription(subscriptionCycleTypeFromRaw(this.vo.cycleType), this.vo.cycleInterval))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .fontFeature(DesignFontFeature.TNUM)
            .margin({ top: DesignDimens.SPACE_XXS })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

        if (this.showProgress()) {
          // 环线/环心拆色：环线走图形口径，环心 12fp 数字走文字级达标令牌（§10 ≥4.5:1）
          RenewProgressRing({
            fraction: this.progressFraction(),
            daysLeft: this.daysLeft(),
            ringColor: subscriptionRenewRingColor(this.vo),
            centerTextColor: subscriptionRenewCenterTextColor(this.vo)
          })
            .margin({ left: DesignDimens.SPACE_XS })
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Row() {
        Text(subscriptionAmountText(this.vo.amount))
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          // 终态弱化止步 textSecondary（on 白卡 4.9:1）：装饰档 textTertiary 禁作可读正文（DESIGN-SPEC §10）
          .fontColor(this.isTerminal() ? $r('app.color.TEXT_SECONDARY') : $r('app.color.PRIMARY_DARK'))
          .fontFeature(DesignFontFeature.TNUM)

        if (this.trialBadgeText().length > 0) {
          SubscriptionTagChip({
            text: this.trialBadgeText(),
            containerColor: this.trialBadgeColors().container,
            contentColor: this.trialBadgeColors().content
          })
            .margin({ left: DesignDimens.SPACE_SM })
        }

        Blank()

        if (this.status() !== SubscriptionStatus.ACTIVE) {
          // 状态 chip 消费专用配色（原误接线图标盒配色：PAUSED 2.52 / CANCELED 3.81 / FINISHED 3.30:1 不达标）
          SubscriptionTagChip({
            text: subscriptionStatusText(this.status()),
            containerColor: subscriptionStatusChipContainer(this.status()),
            contentColor: subscriptionStatusChipContent(this.status())
          })
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .margin({ top: DesignDimens.SPACE_SM })

      Text(subscriptionRenewRelativeText(this.vo))
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(subscriptionRenewRelativeColor(this.vo))
        .fontFeature(DesignFontFeature.TNUM)
        .width('100%')
        .margin({ top: DesignDimens.SPACE_XXS })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    .onClick(() => {
      this.onCardClick()
    })
  }
}
