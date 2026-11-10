// 记忆复习模块共享件 — 对齐安卓 feature/review/ui/ReviewUiCommon.kt + logic/ReviewFormat.kt。
//
// 内容：
// 1) 静态标签 chip（资料/状态/到期类型角标）；
// 2) 复习页细进度条与四档评分按钮（Anki 红/橙/绿/蓝惯例，色值全部取设计令牌）；
// 3) 页面头（返回 + 标题 + 可选右侧文字动作）、错误态（滚筒圆 + 重试）；
// 4) 建卡弹窗（问题/答案输入，提交结果由页面回调返回）；
// 5) 展示换算（到期文案、创建日期、评分/状态文案）。
//
// 已知坑遵守：
// - 内容依赖状态的组件一律 @ComponentV2 + @Param/@Event（@Builder 按值传参不响应更新）；
// - 弹窗「先关窗再回调」；弹窗内输入态由弹窗自持，避免父页状态注入不同步。

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight, MotionTiming } from '../../../constant/DesignSystem'
import {
  REVIEW_MATERIAL_TITLE_MAX_PAGES,
  REVIEW_PAGE_SIZE,
  ReviewCardState,
  ReviewCardVo,
  ReviewRating,
  ReviewText,
  pageMaterials,
  reviewCardStateFromCode
} from '../api'
import { AppButton, EmptyState } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'

// ==================== 静态标签 chip ====================

/** 静态标签 chip —— 胶囊（radiusFull）+ caption 12/500，用于条目上的资料/状态标注 */
@ComponentV2
export struct ReviewTagChip {
  @Param text: string = ''
  @Param containerColor: number = DesignColor.SURFACE_VARIANT
  @Param contentColor: number = DesignColor.TEXT_SECONDARY

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.contentColor)
      .padding({ left: 8, right: 8, top: 2, bottom: 2 })
      .backgroundColor(this.containerColor)
      .borderRadius(DesignDimens.RADIUS_FULL)
  }
}

/** 新卡角标（primaryContainer/onPrimaryContainer，对齐 toolbox 公开 chip 用色） */
@ComponentV2
export struct NewCardChip {
  build() {
    ReviewTagChip({
      text: ReviewText.review_card_new,
      containerColor: DesignColor.PRIMARY_CONTAINER,
      contentColor: DesignColor.ON_PRIMARY_CONTAINER
    })
  }
}

/**
 * 到期类型角标：Learning/Relearning = 分钟级（橙），Review = 天级（蓝）；
 * 新卡（lastReviewAt 为空）直接显示「新卡」。
 */
@ComponentV2
export struct DueTypeChip {
  @Param isNew: boolean = false
  @Param state: number = ReviewCardState.LEARNING

  /** 是否天级（Review 态）；Learning/Relearning 为分钟级 */
  private isDaily(): boolean {
    return this.state === ReviewCardState.REVIEW
  }

  build() {
    if (this.isNew) {
      NewCardChip()
    } else {
      ReviewTagChip({
        text: this.isDaily() ? ReviewText.review_due_badge_daily : ReviewText.review_due_badge_minute,
        containerColor: this.isDaily() ? DesignColor.ICON_CONTAINER_BLUE : DesignColor.WARNING_CONTAINER,
        contentColor: this.isDaily() ? DesignColor.ICON_FOREGROUND_BLUE : DesignColor.WARNING
      })
    }
  }
}

/** 卡片状态角标（学习中/复习中/重学中，复习页卡面顶部展示） */
@ComponentV2
export struct CardStateChip {
  @Param state: number = ReviewCardState.LEARNING

  build() {
    ReviewTagChip({
      text: reviewCardStateLabel(this.state),
      containerColor: DesignColor.SURFACE_VARIANT,
      contentColor: DesignColor.TEXT_SECONDARY
    })
  }
}

// ==================== 评分按钮 / 进度条 ====================

/** 评分按钮容器色（Anki 惯例红/橙/绿/蓝，全部取语义令牌；主题切换自动跟随） */
export function reviewRatingColor(rating: ReviewRating): number {
  switch (rating) {
    case ReviewRating.AGAIN:
      return DesignColor.DANGER
    case ReviewRating.HARD:
      return DesignColor.ONGOING
    case ReviewRating.GOOD:
      return DesignColor.PRIMARY
    default:
      return DesignColor.WATER
  }
}

/** 评分档 → Anki 英文惯例名 */
export function reviewRatingLabel(rating: ReviewRating): string {
  switch (rating) {
    case ReviewRating.AGAIN:
      return ReviewText.review_rating_again
    case ReviewRating.HARD:
      return ReviewText.review_rating_hard
    case ReviewRating.GOOD:
      return ReviewText.review_rating_good
    default:
      return ReviewText.review_rating_easy
  }
}

/** 评分档 → 中文语义文案 */
export function reviewRatingSemanticLabel(rating: ReviewRating): string {
  switch (rating) {
    case ReviewRating.AGAIN:
      return ReviewText.review_rating_again_semantic
    case ReviewRating.HARD:
      return ReviewText.review_rating_hard_semantic
    case ReviewRating.GOOD:
      return ReviewText.review_rating_good_semantic
    default:
      return ReviewText.review_rating_easy_semantic
  }
}

/**
 * 四档评分按钮 — 高 48vp / 胶囊圆角 / 实色语义底 / onPrimary 文字。
 * 两行文案：Anki 英文惯例名（button 15/600）+ 中文语义（caption 12）。
 * 提交中整排禁用降透明度（对齐安卓 RatingButton enabled=false）。
 */
@ComponentV2
export struct RatingButton {
  @Param rating: ReviewRating = ReviewRating.AGAIN
  // 命名注意：不能叫 enabled（与 CustomComponent 基类的同名属性方法冲突导致编译错误，同 AppButton.isEnabled）
  @Param isEnabled: boolean = true
  @Event click: () => void = () => {
  }

  build() {
    Column() {
      Text(reviewRatingLabel(this.rating))
        .fontSize(DesignFontSize.BUTTON)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.ON_PRIMARY)
        .textAlign(TextAlign.Center)
      Text(reviewRatingSemanticLabel(this.rating))
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.ON_PRIMARY)
        .textAlign(TextAlign.Center)
    }
    .width('100%')
    .height(48)
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor(reviewRatingColor(this.rating))
    .opacity(this.isEnabled ? 1 : 0.5)
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .onClick(() => {
      if (this.isEnabled) {
        this.click()
      }
    })
  }
}

/**
 * 细进度条（复习页进度意象）：surfaceVariant 槽 + primary 填充。
 * 宽度百分比 + animation 过渡（对齐安卓弹簧过渡的等价降级）。
 */
@ComponentV2
export struct StudyProgressBar {
  @Param fraction: number = 0

  build() {
    Stack({ alignContent: Alignment.Start }) {
      Column()
        .width('100%')
        .height(4)
        .backgroundColor(DesignColor.SURFACE_VARIANT)

      Column()
        .width(`${Math.min(100, Math.max(0, this.fraction * 100))}%`)
        .height(4)
        .backgroundColor(DesignColor.PRIMARY)
        .animation({ duration: 220, curve: Curve.EaseInOut })
    }
    .width('100%')
    .height(4)
    .clip(true)
    .borderRadius(DesignDimens.RADIUS_FULL)
  }
}

// ==================== 页面头 / 错误态 ====================

/**
 * 页面头（对齐安卓 PageHeader）：返回箭头 48 热区 + 标题 22/600 + 可选右侧文字动作。
 * 右侧动作为空串时不渲染（资料页「新建」经本组件接入）。
 */
@ComponentV2
export struct ReviewPageHeader {
  @Param title: string = ''
  @Param actionText: string = ''
  @Event onAction: () => void = () => {
  }

  build() {
    Row() {
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

      Text(this.title)
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)

      Blank()

      if (this.actionText.length > 0) {
        Text(this.actionText)
          .fontSize(DesignFontSize.PAGE_HEADER)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.PRIMARY_DARK)
          .padding({ left: 12, right: 12, top: 12, bottom: 12 })
          .onClick(() => {
            this.onAction()
          })
      }
    }
    .width('100%')
    .height(56)
    // 行首让位 12：48dp 热区盒居中后图形左缘落在 24（对齐安卓 PageHeader）
    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
    .alignItems(VerticalAlign.Center)
  }
}

/**
 * 错误态（规范 §3.6）— EmptyState 滚筒圆母题 + 说明文案 + 重试按钮。
 * 用于三页首刷失败/展开区失败（对齐安卓 EmptyState + AppButton）。
 */
@ComponentV2
export struct ReviewErrorState {
  @Param message: string = ''
  @Event onRetry: () => void = () => {
  }

  build() {
    Column() {
      EmptyState({ message: this.message }) {
        Column() {
          AppButton({
            small: true,
            text: ReviewText.review_action_retry,
            click: (): void => {
              this.onRetry()
            }
          })
        }
        .margin({ top: DesignDimens.SPACE_MD })
      }
    }
    .width('100%')
    // 高度撑满调用方 layoutWeight 分配的空间（对齐 MoodErrorState 的可用写法）：
    // 原 layoutWeight(1) 在 if/else 包裹层内不生效，容器不拉伸导致 EmptyState 落于顶部
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

// ==================== 建卡弹窗 ====================

/** 建卡提交结果（页面回调返回；success=false 时 errorMessage 由弹窗内联展示） */
export interface ReviewCardSubmitResult {
  success: boolean
  errorMessage: string
}

/**
 * 添加问答卡弹窗（对齐安卓 ReviewCardEditDialog）：
 * 问题 + 答案（multiline，正文 16sp）；确认提交（loading 态按钮承担禁用），错误仅提交后内联展示。
 *
 * 输入态由弹窗自持（@State），提交逻辑在页面；弹窗只负责收集与结果表达。
 * 确认成功「先关窗再回调 onDismiss」，与全局弹窗约定一致。
 */
@CustomDialog
export struct ReviewCardDialog {
  // 由父组件经 CustomDialogController 注入（框架约定，不可自赋初值）
  controller: CustomDialogController
  @Prop materialTitle: string = ''
  @State question: string = ''
  @State answer: string = ''
  @State submitting: boolean = false
  @State errorMessage: string = ''
  @State entered: boolean = false
  onConfirm: (question: string, answer: string) => Promise<ReviewCardSubmitResult> =
    async (): Promise<ReviewCardSubmitResult> => {
      return { success: false, errorMessage: '' }
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

  /** 提交：loading 期间忽略重复点击；成功关窗后回调，失败内联展示错误 */
  private async handleConfirm(): Promise<void> {
    if (this.submitting) {
      return
    }
    this.submitting = true
    this.errorMessage = ''
    try {
      const result: ReviewCardSubmitResult =
        await this.onConfirm(this.question.trim(), this.answer.trim())
      if (result.success) {
        // 先关窗再回调：回调内会刷新卡片列表/置变更信号，导航后弹窗实例已销毁
        this.controller.close()
        this.onDismiss()
      } else {
        this.errorMessage = result.errorMessage
      }
    } catch (err) {
      this.errorMessage = ReviewText.review_error_load
    } finally {
      this.submitting = false
    }
  }

  /** 表单字段标签（captionStrong 12/500 次文本，规范 §3.4 副文案层级） */
  @Builder
  FormLabel(text: string) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(DesignColor.TEXT_SECONDARY)
      .margin({ bottom: DesignDimens.SPACE_XS })
  }

  build() {
    // 外层留白容器：customStyle 弹窗铺满屏幕宽度，卡片背景画在内层并留左右边距（问题16）
    Column() {
      Column() {
        Scroll() {
          Column() {
            Text(ReviewText.review_card_dialog_title)
              .fontSize(DesignFontSize.STATUS_LARGE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)

            if (this.materialTitle.length > 0) {
              Text(this.materialTitle)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_TERTIARY)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .margin({ top: DesignDimens.SPACE_XXS })
            }

            Column() {
              this.FormLabel(ReviewText.review_card_dialog_question_label)
              TextInput({ text: this.question, placeholder: ReviewText.review_card_dialog_question_hint })
                .fontSize(DesignFontSize.BODY)
                .backgroundColor(DesignColor.SURFACE_VARIANT)
                .borderRadius(DesignDimens.INPUT_RADIUS)
                .borderWidth(1)
                .borderColor(DesignColor.OUTLINE)
                .placeholderColor(DesignColor.TEXT_TERTIARY)
                .placeholderFont({ size: DesignFontSize.BODY })
                .maxLength(1000)
                .height(96)
                .onChange((value: string) => {
                  this.question = value
                })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .margin({ top: DesignDimens.SPACE_MD })

            Column() {
              this.FormLabel(ReviewText.review_card_dialog_answer_label)
              TextInput({ text: this.answer, placeholder: ReviewText.review_card_dialog_answer_hint })
                .fontSize(DesignFontSize.BODY)
                .backgroundColor(DesignColor.SURFACE_VARIANT)
                .borderRadius(DesignDimens.INPUT_RADIUS)
                .borderWidth(1)
                .borderColor(DesignColor.OUTLINE)
                .placeholderColor(DesignColor.TEXT_TERTIARY)
                .placeholderFont({ size: DesignFontSize.BODY })
                .maxLength(2000)
                .height(120)
                .onChange((value: string) => {
                  this.answer = value
                })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .margin({ top: DesignDimens.SPACE_MD })

            if (this.errorMessage.length > 0) {
              Text(this.errorMessage)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.ERROR)
                .margin({ top: DesignDimens.SPACE_SM })
            }
          }
          .width('100%')
          .padding({
            left: DesignDimens.SPACE_XL,
            right: DesignDimens.SPACE_XL,
            top: DesignDimens.SPACE_LG,
            bottom: DesignDimens.SPACE_LG
          })
          .alignItems(HorizontalAlign.Start)
        }
        .width('100%')
        .align(Alignment.TopStart)
        .scrollBar(BarState.Off)

        Divider()
          .strokeWidth(0.5)
          .color(DesignColor.DIVIDER)
          .width('100%')

        Row() {
          Button(ReviewText.review_dialog_action_cancel)
            .type(ButtonType.Normal)
            .layoutWeight(1)
            .height(48)
            .backgroundColor(Color.Transparent)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .fontSize(DesignFontSize.BODY)
            .borderRadius(0)
            .onClick(() => {
              this.controller.close()
            })

          Divider()
            .vertical(true)
            .color(DesignColor.DIVIDER)
            .strokeWidth(0.5)
            .height(48)

          Button(ReviewText.review_dialog_action_confirm)
            .type(ButtonType.Normal)
            .layoutWeight(1)
            .height(48)
            .backgroundColor(Color.Transparent)
            .fontColor(DesignColor.PRIMARY)
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .borderRadius(0)
            .enabled(!this.submitting)
            .opacity(this.submitting ? 0.5 : 1)
            .onClick(() => {
              this.handleConfirm()
            })
        }
        .width('100%')
      }
      .width('100%')
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

// ==================== 展示换算（对齐安卓 logic/ReviewFormat.kt） ====================

function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

/** 是否从未复习的新卡（对齐安卓 ReviewCardVo.isNewCard：lastReviewAt 为空） */
export function reviewIsNewCard(card: ReviewCardVo): boolean {
  return card.lastReviewAt === null || card.lastReviewAt === undefined
}

/**
 * 到期时刻 → 展示文案（新卡不显示到期，语义由「新卡」角标承担）：
 * 已到期 / N 分钟后到期 / N 小时后到期 / N 天后到期；缺失/非法返回空串。
 * 契约约定复习/到期判断以服务端时刻为准，客户端本地时钟仅做展示换算。
 */
export function reviewDueTextOf(dueAt: string | null | undefined): string {
  if (dueAt === null || dueAt === undefined || dueAt.length < 10) {
    return ''
  }
  const due = new Date(dueAt)
  if (Number.isNaN(due.getTime())) {
    return ''
  }
  const diffMs = due.getTime() - Date.now()
  if (diffMs < 60_000) {
    return ReviewText.review_due_now
  }
  const minutes = Math.floor(diffMs / 60_000)
  if (minutes < 60) {
    return ReviewText.review_due_in_minutes.replace('%d', `${minutes}`)
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return ReviewText.review_due_in_hours.replace('%d', `${hours}`)
  }
  const days = Math.floor(hours / 24)
  return ReviewText.review_due_in_days.replace('%d', `${days}`)
}

/** createdAt（ISO LocalDateTime）→ 展示日期 yyyy-MM-dd；缺失/非法回落 fallback */
export function reviewCreatedDateOf(createdAt: string | null | undefined, fallback: string): string {
  if (createdAt === null || createdAt === undefined || createdAt.length < 10) {
    return fallback
  }
  const parsed = new Date(createdAt)
  if (Number.isNaN(parsed.getTime())) {
    return fallback
  }
  return `${parsed.getFullYear()}-${pad2(parsed.getMonth() + 1)}-${pad2(parsed.getDate())}`
}

/** 卡片状态 code → 文案（学习中/复习中/重学中） */
export function reviewCardStateLabel(state: number): string {
  const mapped = reviewCardStateFromCode(state)
  switch (mapped) {
    case ReviewCardState.REVIEW:
      return ReviewText.review_state_review
    case ReviewCardState.RELEARNING:
      return ReviewText.review_state_relearning
    default:
      return ReviewText.review_state_learning
  }
}

/**
 * 按 materialId 集合补齐「所属资料」标题（分页翻页直至覆盖，防御上限 10 页）。
 * 资料标题仅为展示辅助，拉取失败不阻塞调用方（返回已覆盖部分，缺失回落「未归资料」）。
 * 队列页与复习页共用（对齐安卓 ReviewQueueViewModel / ReviewStudyViewModel 的 loadMaterialTitles）。
 */
export async function reviewLoadMaterialTitles(ids: Set<number>): Promise<Map<number, string>> {
  const titles = new Map<number, string>()
  if (ids.size === 0) {
    return titles
  }
  try {
    for (let page = 1; page <= REVIEW_MATERIAL_TITLE_MAX_PAGES; page++) {
      const res = await pageMaterials(page, REVIEW_PAGE_SIZE)
      if (res.data === null) {
        break
      }
      for (const material of res.data.records) {
        if (material.id !== undefined && ids.has(material.id)) {
          titles.set(material.id, material.title ?? '')
        }
      }
      if (titles.size >= ids.size) {
        break
      }
      if (page * REVIEW_PAGE_SIZE >= res.data.total) {
        break
      }
      if (res.data.records.length === 0) {
        break
      }
    }
  } catch (err) {
    logger.warn('reviewUiCommon', '资料标题补齐失败，回落「未归资料」')
  }
  return titles
}
