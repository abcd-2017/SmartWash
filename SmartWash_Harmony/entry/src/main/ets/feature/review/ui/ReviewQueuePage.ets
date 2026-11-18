// 今日队列页 — 对齐安卓 feature/review/ui/queue/ReviewQueuePage.kt
//
// 顶部「今日 N 张」计数条（数据大字锚点 + 到期/新卡分解 + 开始复习 CTA）+
// 队列列表（卡面预览、所属资料 chip、到期类型角标 Learning 分钟级/Review 天级）。
//
// 跨页刷新：资料页建资料/建卡、复习页每次评分成功后 bump 复习 tick（自增）。
// V2 无 @StorageLink/@Watch，页面重新可见（onShown）时手动比对 AppStorage tick 重拉
// （对齐安卓 savedStateHandle CHANGED_KEY 的返回即消费语义）。
//
// 已知坑遵守：列表条目内容依赖状态 → 队列条目卡用 @ComponentV2 + @Param/@Event。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionScale, MotionTiming } from '../../../constant/DesignSystem'
import { ReduceMotion } from '../../../utils/ReduceMotion'
import { BizCode } from '../../../network/HttpUtil'
import { ReviewCardVo, ReviewQueueVo, ReviewText, getReviewQueue } from '../api'
import {
  DueTypeChip,
  ReviewErrorState,
  ReviewPageHeader,
  ReviewTagChip,
  reviewDueTextOf,
  reviewIsNewCard,
  reviewLoadMaterialTitles
} from './ReviewUiCommon'
import { AnimatedNumber, AppButton, AppCard, EmptyState, SkeletonBlock } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { REVIEW_TICK_KEY } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'
import { resColor } from '../../../utils/ResourceColor'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'reviewQueuePage'

@Builder
export function ReviewQueueBuilder() {
  ReviewQueuePage()
}

@ComponentV2
struct ReviewQueuePage {
  @Local queue: ReviewQueueVo | null = null
  @Local materialTitles: Map<number, string> = new Map<number, string>()
  @Local isLoadingFirst: boolean = true
  @Local isFirstError: boolean = false
  @Local isRefreshing: boolean = false
  // 分钟粒度时钟基准：全部队列卡共用同一 now，「N 分钟后到期」随页面停留推进
  // （对齐安卓 produceState 分钟级 ticker，跨端矩阵 #4）
  @Local now: number = Date.now()
  /** 到期时钟定时器 id（aboutToDisappear 必须清理，鸿蒙红线） */
  private dueTickerId: number = -1
  // 跨页刷新信号（自增 tick）：资料页建资料/建卡、复习页评分成功后 bump。
  // V2 不订阅 AppStorage，仅记录上次消费值，onShown 时比对差值触发重拉
  private lastReviewTick: number = 0

  async aboutToAppear(): Promise<void> {
    // 先记录当前 tick，避免首次 onShown 误判为「有变更」而重复首刷
    this.lastReviewTick = AppStorage.get<number>(REVIEW_TICK_KEY) ?? 0
    // 每 30s 推进一次时钟基准，驱动全部 QueueCard 的到期文案重算
    this.dueTickerId = setInterval(() => {
      this.now = Date.now()
    }, 30_000)
    await this.load()
  }

  aboutToDisappear(): void {
    if (this.dueTickerId !== -1) {
      clearInterval(this.dueTickerId)
      this.dueTickerId = -1
    }
  }

  /** onShown 手动消费复习变更：仅当 tick 自上次可见以来自增才重拉（V2 无 @Watch） */
  private consumeReviewTick(): void {
    const current: number = AppStorage.get<number>(REVIEW_TICK_KEY) ?? 0
    if (current !== this.lastReviewTick) {
      logger.debug(TAG, `消费到复习变更信号 current=${current} last=${this.lastReviewTick}，重拉队列`)
      this.lastReviewTick = current
      this.load()
    }
  }

  /** 拉取今日队列并补齐「所属资料」标题 */
  private async load(): Promise<void> {
    this.isLoadingFirst = this.queue === null
    this.isFirstError = false
    try {
      const res = await getReviewQueue()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.queue = res.data
        const ids = new Set<number>()
        const cards = res.data.cards ?? []
        for (const card of cards) {
          if (card.materialId !== null && card.materialId !== undefined) {
            ids.add(card.materialId)
          }
        }
        this.materialTitles = await reviewLoadMaterialTitles(ids)
        this.isFirstError = false
        logger.info(TAG, `今日队列加载完成 cards=${cards.length}`)
      } else {
        logger.warn(TAG, `今日队列加载失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isFirstError = true
        showToastSafety(res.message, ReviewText.review_error_load)
      }
    } catch (err) {
      logger.error(TAG, '今日队列加载异常', err instanceof Error ? err.message : '')
      this.isFirstError = true
      showToastSafety('', ReviewText.review_error_load)
    } finally {
      this.isLoadingFirst = false
    }
  }

  /** 下拉刷新：保持列表，仅亮 Refresh 指示器 */
  private async refreshAll(): Promise<void> {
    this.isRefreshing = true
    await this.load()
    this.isRefreshing = false
  }

  /** 开始复习（从队首） */
  private startStudy(): void {
    pathStack.pushPathByName(RouteNames.REVIEW_STUDY, null)
  }

  /** 点击队列条目：从该卡开始复习 */
  private startStudyFrom(cardId: number): void {
    pathStack.pushPathByName(RouteNames.REVIEW_STUDY, cardId)
  }

  /** 空态行动：去资料页建卡 */
  private goMaterials(): void {
    pathStack.pushPathByName(RouteNames.REVIEW_MATERIAL_LIST, null)
  }

  /** 队列卡片（queue 为空时兜底空数组） */
  private queueCards(): Array<ReviewCardVo> {
    return this.queue?.cards ?? []
  }

  /** 所属资料标题（缺失回落「未归资料」） */
  private materialTitleOf(card: ReviewCardVo): string {
    if (card.materialId === null || card.materialId === undefined) {
      return ReviewText.review_material_chip_none
    }
    return this.materialTitles.get(card.materialId) ?? ReviewText.review_material_chip_none
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
      Column() {
        ReviewPageHeader({
          title: ReviewText.review_queue_title,
          // 队列非空时资料页同样需要可达（对齐安卓 PageHeader actions）：页头常显「资料」入口，
          // 修复「有到期卡时无法建卡/管理资料」的断路
          actionText: ReviewText.review_queue_action_materials,
          onAction: (): void => {
            this.goMaterials()
          }
        })

        if (this.isLoadingFirst) {
          QueueSkeleton()
            .width('100%')
            .layoutWeight(1)
        } else if (this.isFirstError && this.queue === null) {
          ReviewErrorState({
            message: ReviewText.review_error_load,
            onRetry: (): void => {
              this.load()
            }
          })
            .width('100%')
            .layoutWeight(1)
        } else {
          if (this.queueCards().length === 0) {
            // 空态：滚筒圆母题 + 引导 + 行动按钮（跳资料页建卡）
            Column() {
              EmptyState({ message: ReviewText.review_queue_empty_message }) {
                Column() {
                  // 48vp 主按钮档（默认档）：本屏唯一主行动，触控 ≥48 对齐安卓空态 AppButton（跨端矩阵 #10）
                  AppButton({
                    text: ReviewText.review_queue_empty_action,
                    click: (): void => {
                      this.goMaterials()
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
            Refresh({ refreshing: $$this.isRefreshing }) {
              List() {
                // 计数条 —— 每屏唯一锚点：数据大字（数字变化不跳动）
                ListItem() {
                  AppCard() {
                    Column() {
                      // 数字变化走弹簧淡入淡出（对齐安卓 AnimatedContent numberSpec；shared AnimatedNumber）
                      AnimatedNumber({
                        value: ReviewText.review_queue_count_format.replace('%d', `${this.queueCards().length}`),
                        fontSize: DesignFontSize.DATA_LARGE,
                        fontWeight: DesignFontWeight.BOLD,
                        fontColor: $r('app.color.TEXT_PRIMARY')
                      })

                      AnimatedNumber({
                        value: ReviewText.review_queue_breakdown_format
                          .replace('%d', `${this.queue?.dueCount ?? 0}`)
                          .replace('%d', `${this.queue?.newCount ?? 0}`),
                        fontSize: DesignFontSize.BODY_SMALL,
                        fontWeight: DesignFontWeight.NORMAL,
                        fontColor: $r('app.color.TEXT_SECONDARY')
                      })
                        .margin({ top: DesignDimens.SPACE_XS })

                      AppButton({
                        text: ReviewText.review_queue_action_start,
                        click: (): void => {
                          this.startStudy()
                        }
                      })
                        .margin({ top: DesignDimens.SPACE_MD })
                    }
                    .width('100%')
                    .alignItems(HorizontalAlign.Start)
                  }
                }

                ForEach(this.queueCards(), (card: ReviewCardVo) => {
                  ListItem() {
                    QueueCard({
                      card: card,
                      materialTitle: this.materialTitleOf(card),
                      now: this.now,
                      onCardClick: (): void => {
                        this.startStudyFrom(card.id ?? -1)
                      }
                    })
                  }
                  // 列表项进出场（对齐安卓 animatedListItem 弹簧；ArkUI 无项级 placement，淡入淡出近似）
                  .transition(ReduceMotion.listItemTransition())
                }, (card: ReviewCardVo) => `${card.id ?? 0}`)
              }
              .width('100%')
              .height('100%')
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 8, bottom: 8 + safeBottomVp() })
              .divider({ strokeWidth: 0, color: Color.Transparent })
              .scrollBar(BarState.Off)
            }
            .onRefreshing(() => {
              this.refreshAll()
            })
          }
        }
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // V2 无 @StorageLink/@Watch：从复习页/资料页返回时手动比对复习 tick，有变更即重拉
      this.consumeReviewTick()
    })
  }
}

// ==================== 队列条目卡（内容依赖状态 → @ComponentV2 + @Param/@Event） ====================

/**
 * 队列条目卡：问题预览（两行）+ 所属资料 chip + 到期类型角标 + 右侧到期时间。
 * 整卡点击从该卡开始复习（对齐安卓 QueueCard）。
 */
@ComponentV2
struct QueueCard {
  @Require @Param card: ReviewCardVo
  @Param materialTitle: string = ''
  /** 页面级分钟时钟基准（父页 ticker 每 30s 推进，全部卡片同一 now） */
  @Param now: number = 0
  @Event onCardClick: () => void = () => {
  }
  // 按压态（对齐安卓 pressable scale 0.97 + 100ms FastOutSlowIn，reduced 180ms）
  @Local pressed: boolean = false

  build() {
    Column() {
      Row() {
        Column() {
          Text(this.card.question ?? '')
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .lineHeight(DesignFontSize.BODY_SMALL + 6)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')

          Row({ space: DesignDimens.SPACE_XXS }) {
            ReviewTagChip({
              text: this.materialTitle,
              containerColor: $r('app.color.SURFACE_VARIANT'),
              contentColor: $r('app.color.TEXT_SECONDARY')
            })
            DueTypeChip({
              isNew: reviewIsNewCard(this.card),
              state: this.card.state ?? 1
            })
          }
          .margin({ top: DesignDimens.SPACE_XS })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        // 到期时间是 FSRS 队列核心决策信息，用次文本档：TEXT_TERTIARY on 白卡
        // 浅色仅 2.25:1 破线（跨端矩阵 #1），TEXT_SECONDARY 浅色 4.9:1 达标；
        // 12/500 + 行高 16 对齐安卓 labelSmall（跨端矩阵 #15）
        // 新卡不显示到期（对齐安卓 dueDisplayText 按 isNewCard 短路）：语义由「新卡」角标承担，
        // 避免新卡条目同时出现「新卡」+「7 小时后到期」两份冲突信息
        Text(reviewIsNewCard(this.card) ? '' : reviewDueTextOf(this.card.dueAt, this.now))
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .lineHeight(DesignFontSize.CAPTION + 4)
          .fontFeature(DesignFontFeature.TNUM)
          .margin({ left: DesignDimens.SPACE_SM })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .alignItems(HorizontalAlign.Start)
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
      this.onCardClick()
    })
  }
}

/**
 * 队列页首刷骨架 — 形状对齐真实 QueueCard：卡内边距 20（AppCard 默认档，不重复加 padding）+
 * 全宽问题文本块两行 + chips 胶囊行（26%/20%）+ 右侧到期小块 14%
 * （通用 SkeletonList 是「40dp 左圆块 + 两行文本」的带图标行结构，与无图标全宽 QueueCard
 * 形状不符 — 对齐安卓 QueueSkeleton，跨端矩阵 #5）。
 * 微光扫过由 SkeletonBlock 承担（reduce-motion 静态灰降级为全局裁决项，矩阵 #17）。
 */
@ComponentV2
struct QueueSkeleton {
  @Param rows: number = 5

  /** ForEach 数据源（ArkTS 不支持在 build 内构造数组，收敛到方法） */
  private rowKeys(): number[] {
    const keys: number[] = []
    for (let i = 0; i < this.rows; i++) {
      keys.push(i)
    }
    return keys
  }

  build() {
    Column({ space: DesignDimens.CARD_SPACING }) {
      ForEach(this.rowKeys(), (row: number) => {
        AppCard() {
          Row() {
            Column() {
              SkeletonBlock()
                .width('90%')
                .height(DesignDimens.SPACE_LG)
              SkeletonBlock()
                .width('60%')
                .height(DesignDimens.SPACE_LG)
                .margin({ top: DesignDimens.SPACE_XS })
              Row({ space: DesignDimens.SPACE_XXS }) {
                SkeletonBlock({ blockRadius: DesignDimens.RADIUS_FULL })
                  .width('26%')
                  .height(DesignDimens.SPACE_XL)
                SkeletonBlock({ blockRadius: DesignDimens.RADIUS_FULL })
                  .width('20%')
                  .height(DesignDimens.SPACE_XL)
              }
              .margin({ top: DesignDimens.SPACE_XS })
            }
            .alignItems(HorizontalAlign.Start)
            .layoutWeight(1)

            SkeletonBlock()
              .width('14%')
              .height(DesignDimens.SPACE_MD)
              .margin({ left: DesignDimens.SPACE_SM })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
        }
      }, (row: number) => row.toString())
    }
    .width('100%')
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: DesignDimens.SPACE_XS,
      bottom: DesignDimens.SPACE_XS
    })
  }
}
