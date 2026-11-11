// 复习页 — 对齐安卓 feature/review/ui/study/ReviewStudyPage.kt
//
// 卡片正面（问题）→ 点击翻面（背面答案 + 四档评分按钮 Again/Hard/Good/Easy）→
// 评分成功本地移除该卡并自动落到下一张；队列清空显示完成态（真实状态驱动）。
// FSRS 调度全部在后端，本页面不做任何间隔计算。
//
// 路由入参 startCardId（缺省 -1 = 从队首开始），由队列页点击卡片时携带。
// 每次评分成功 bump 复习 tick，队列页返回时重拉计数与列表。
//
// 已知坑遵守：翻面/评分态为页面自持状态；评分失败 toast 后保留当前卡重试。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  AnswerForm,
  ReviewCardVo,
  ReviewQueueVo,
  ReviewRating,
  ReviewText,
  answerCard,
  getReviewQueue
} from '../api'
import {
  CardStateChip,
  RatingButton,
  ReviewErrorState,
  ReviewPageHeader,
  ReviewTagChip,
  StudyProgressBar,
  reviewLoadMaterialTitles
} from './ReviewUiCommon'
import { AppButton, AppCard, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { getNumberParam, pathStack } from '../../../utils/PathStackUtil'
import { bumpReviewRefresh } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'reviewStudyPage'

/** 四档评分顺序（Anki 惯例：Again/Hard/Good/Easy） */
const REVIEW_RATINGS: Array<ReviewRating> = [
  ReviewRating.AGAIN,
  ReviewRating.HARD,
  ReviewRating.GOOD,
  ReviewRating.EASY
]

@Builder
export function ReviewStudyBuilder() {
  ReviewStudyPage()
}

@Component
struct ReviewStudyPage {
  /** 剩余队列（评分成功即移除，第一张为当前卡） */
  @State cards: Array<ReviewCardVo> = []
  /** 本轮总张数（进度分母） */
  @State total: number = 0
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  /** 是否已翻面（正面问题 → 背面答案 + 评分按钮） */
  @State flipped: boolean = false
  /** 评分提交态（loading 整排禁用；错误 toast 后保留当前卡） */
  @State answerLoading: boolean = false
  /** materialId → 所属资料标题（缺失显示「未归资料」） */
  @State materialTitles: Map<number, string> = new Map<number, string>()
  /** 路由入参：起始卡片 id（-1 = 从队首开始） */
  private startCardId: number = -1
  /** 当前卡展示时刻——评分时折算 durationMs（契约 AnswerFrom.durationMs，可空） */
  private cardShownAtMs: number = 0

  async aboutToAppear(): Promise<void> {
    const param = getNumberParam(RouteNames.REVIEW_STUDY)
    this.startCardId = param !== null && param > 0 ? param : -1
    await this.load()
  }

  /** 拉取今日队列并按 startCardId 轮转到队首，随后补齐资料标题 */
  private async load(): Promise<void> {
    this.isLoadingFirst = true
    this.isFirstError = false
    try {
      const res = await getReviewQueue()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const queue: ReviewQueueVo = res.data
        const all: Array<ReviewCardVo> = queue.cards ?? []
        let ordered: Array<ReviewCardVo> = all
        if (this.startCardId > 0) {
          const target = all.find((card: ReviewCardVo) => card.id === this.startCardId)
          if (target !== undefined) {
            ordered = [target].concat(all.filter((card: ReviewCardVo) => card.id !== this.startCardId))
          }
        }
        this.cards = ordered
        this.total = ordered.length
        this.flipped = false
        const ids = new Set<number>()
        for (const card of ordered) {
          if (card.materialId !== null && card.materialId !== undefined) {
            ids.add(card.materialId)
          }
        }
        this.materialTitles = await reviewLoadMaterialTitles(ids)
        this.cardShownAtMs = Date.now()
        logger.info(TAG, `复习队列加载完成 total=${ordered.length}`)
      } else {
        logger.warn(TAG, `复习队列加载失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isFirstError = true
        showToastSafety(res.message, ReviewText.review_error_load)
      }
    } catch (err) {
      logger.error(TAG, '复习队列加载异常', err instanceof Error ? err.message : '')
      this.isFirstError = true
      showToastSafety('', ReviewText.review_error_load)
    } finally {
      this.isLoadingFirst = false
    }
  }

  /** 翻面（提交中禁止切换，避免答案/评分态错位） */
  private flip(): void {
    if (this.answerLoading) {
      return
    }
    this.flipped = !this.flipped
  }

  /** 评分提交：成功后本地移除该卡、复位翻面，自动落到下一张 */
  private async rate(rating: ReviewRating): Promise<void> {
    const current = this.cards[0]
    if (current === undefined || current.id === undefined || this.answerLoading) {
      return
    }
    const cardId = current.id
    const durationMs = Math.max(0, Date.now() - this.cardShownAtMs)
    const form: AnswerForm = { cardId: cardId, rating: rating, durationMs: durationMs }
    this.answerLoading = true
    try {
      const res = await answerCard(form)
      if (res.code === BizCode.SUCCESS) {
        this.cards = this.cards.filter((card: ReviewCardVo) => card.id !== cardId)
        this.flipped = false
        this.cardShownAtMs = Date.now()
        // 评分改变到期状态，队列页返回时需重拉计数与列表
        bumpReviewRefresh()
        logger.info(TAG, `卡片评分成功 cardId=${cardId} rating=${rating} 剩余=${this.cards.length}`)
      } else {
        logger.warn(TAG, `卡片评分被拒 cardId=${cardId} code=${res.code ?? 0}`)
        showToastSafety(res.message, ReviewText.review_error_answer)
      }
    } catch (err) {
      logger.error(TAG, '卡片评分异常', err instanceof Error ? err.message : '')
      showToastSafety('', ReviewText.review_error_answer)
    } finally {
      this.answerLoading = false
    }
  }

  /** 当前卡展示（cards[0]；空数组表示本轮完成） */
  private currentCard(): ReviewCardVo | null {
    return this.cards.length > 0 ? this.cards[0] : null
  }

  /** 当前卡所属资料标题（缺失回落「未归资料」） */
  private currentMaterialTitle(): string {
    const current = this.currentCard()
    if (current === null || current.materialId === null || current.materialId === undefined) {
      return ReviewText.review_material_chip_none
    }
    return this.materialTitles.get(current.materialId) ?? ReviewText.review_material_chip_none
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        ReviewPageHeader({ title: ReviewText.review_study_title })

        if (this.isLoadingFirst) {
          SkeletonList({ rows: 4 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isFirstError) {
          ReviewErrorState({
            message: ReviewText.review_error_load,
            onRetry: (): void => {
              this.load()
            }
          })
            .width('100%')
            .layoutWeight(1)
        } else if (this.currentCard() === null) {
          // 完成态：队列清空（真实状态），返回队列页
          Column() {
            EmptyState({ message: ReviewText.review_study_completed_message })
              .width('100%')
            // 按钮距屏边 48（页边距 24 + spaceXl 24，对齐安卓 CompletedContent，S11）
            AppButton({
              text: ReviewText.review_action_back,
              click: (): void => {
                pathStack.pop()
              }
            })
              .width('100%')
              .margin({ left: DesignDimens.SPACE_XL, right: DesignDimens.SPACE_XL, top: DesignDimens.SPACE_MD })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, bottom: DesignDimens.SPACE_LG + safeBottomVp() })
        } else {
          this.StudyContent()
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

  /** 学习内容：进度行 + 卡面（翻面）+ 评分按钮排 */
  @Builder
  StudyContent() {
    Column() {
      // 进度行：第 X / Y 张（tnum）+ 细进度条（labelMedium 12/500 锚点档，对齐安卓 S5）
      Text(ReviewText.review_study_progress_format
        .replace('%d', `${this.total - this.cards.length + 1}`)
        .replace('%d', `${this.total}`))
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .lineHeight(DesignFontSize.CAPTION + 4)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .fontFeature(DesignFontFeature.TNUM)
        .margin({ top: DesignDimens.SPACE_XS })

      StudyProgressBar({ fraction: this.total > 0 ? (this.total - this.cards.length) / this.total : 0 })
        .margin({ top: DesignDimens.SPACE_XS })

      // 卡面（点击卡本体翻面；长内容整卡可滚动查看，对齐安卓 CardFace verticalScroll + AppCard(onClick)，S4/S10）
      Column() {
        Scroll() {
          Column() {
            AppCard() {
              Column() {
                // chip 行间距 spaceXxs=4（对齐安卓 Arrangement.spacedBy(spaceXxs)，S6）
                Row({ space: DesignDimens.SPACE_XXS }) {
                  ReviewTagChip({
                    text: this.currentMaterialTitle(),
                    containerColor: DesignColor.SURFACE_VARIANT,
                    contentColor: DesignColor.TEXT_SECONDARY
                  })
                  CardStateChip({ state: this.currentCard()?.state ?? 1 })
                }

                if (!this.flipped) {
                  Text(this.currentCard()?.question ?? '')
                    .fontSize(DesignFontSize.AMOUNT_MEDIUM)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor(DesignColor.TEXT_PRIMARY)
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_MD })

                  // 引导操作的可读正文（非装饰）：TEXT_TERTIARY 双主题均破 4.5:1（S1）
                  Text(ReviewText.review_study_question_hint)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor(DesignColor.TEXT_SECONDARY)
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_LG })
                } else {
                  // 背面问题放宽到 4 行便于长问题回看全文（S3）
                  Text(this.currentCard()?.question ?? '')
                    .fontSize(DesignFontSize.BODY_SMALL)
                    .fontColor(DesignColor.TEXT_SECONDARY)
                    .maxLines(4)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_MD })

                  // 答案标签：小号绿字走 TEXT_GREEN（PRIMARY 在白卡仅 3.5:1 破线，S2）
                  Text(ReviewText.review_study_answer_label)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .fontColor(DesignColor.TEXT_GREEN)
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_MD })

                  Text(this.currentCard()?.answer ?? '')
                    .fontSize(DesignFontSize.BODY)
                    .fontColor(DesignColor.TEXT_PRIMARY)
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_XS })
                }
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
            }
          }
          .width('100%')
          .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
          .onClick(() => {
            this.flip()
          })
        }
        .width('100%')
        .height('100%')
        .scrollBar(BarState.Off)
      }
      .width('100%')
      .layoutWeight(1)

      // 评分按钮排：翻面后展示（提交中整排禁用降透明度）
      if (this.flipped) {
        Row({ space: DesignDimens.SPACE_XS }) {
          ForEach(REVIEW_RATINGS, (rating: ReviewRating) => {
            RatingButton({
              rating: rating,
              isEnabled: !this.answerLoading,
              click: (): void => {
                this.rate(rating)
              }
            })
              .layoutWeight(1)
          }, (rating: ReviewRating) => `${rating}`)
        }
        .width('100%')
        .margin({ top: DesignDimens.SPACE_MD })
      }
    }
    .width('100%')
    .layoutWeight(1)
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, bottom: DesignDimens.SPACE_LG + safeBottomVp() })
    .alignItems(HorizontalAlign.Start)
  }
}
