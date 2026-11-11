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
import { AppButton, AppButtonVariant, EmptyState } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'

// ==================== 静态标签 chip ====================

/** 静态标签 chip —— 胶囊（radiusFull）+ caption 12/500 + 行高 16，纵向内边距 4 对齐安卓 chip 高 24（跨端矩阵 #14/#15） */
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
      .lineHeight(DesignFontSize.CAPTION + 4)
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
        // 分钟级角标小字：WARNING 橙 on WARNING_CONTAINER 浅色仅 2.51:1 破线（跨端矩阵 #2），
        // 改成对 ink 档 ON_WARNING_CONTAINER（浅 #92400E ≈5.9:1 / 深 #ECB36A ≈7.6:1，双主题达标）
        contentColor: this.isDaily() ? DesignColor.ICON_FOREGROUND_BLUE : DesignColor.ON_WARNING_CONTAINER
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

/** 评分按钮禁用态透明度（规范 §3.1：禁用 = 内容 38% 透明度，M3 口径） */
const RATING_DISABLED_ALPHA: number = 0.38

/**
 * 评分按钮容器色 — 各语义色浅底容器档（粉=Again / 橙=Hard / 绿=Good / 蓝=Easy）。
 * 原语义实底 + ON_PRIMARY 白字在浅色模式低于 4.5:1 红线（HARD 2.76:1 / EASY 2.92:1 /
 * GOOD 3.49:1，跨端矩阵 #3），对齐安卓改「语义浅底 + ink 深字」成对令牌；
 * 主题切换自动跟随（深色取各令牌 Dark 档，applyDesignTheme 已回写双主题）。
 */
export function reviewRatingColor(rating: ReviewRating): number {
  switch (rating) {
    case ReviewRating.AGAIN:
      return DesignColor.ICON_CONTAINER_PINK
    case ReviewRating.HARD:
      return DesignColor.WARNING_CONTAINER
    case ReviewRating.GOOD:
      return DesignColor.ICON_CONTAINER_GREEN
    default:
      return DesignColor.WATER_SOFT
  }
}

/** 评分按钮文字色 — 与容器成对的 ink 深字档（浅色最低 5.1:1 / 深色最低 6.5:1，均达 AA） */
export function reviewRatingContentColor(rating: ReviewRating): number {
  switch (rating) {
    case ReviewRating.AGAIN:
      return DesignColor.DANGER_INK
    case ReviewRating.HARD:
      return DesignColor.ON_WARNING_CONTAINER
    case ReviewRating.GOOD:
      return DesignColor.TEXT_GREEN_ON_TINT
    default:
      return DesignColor.TEXT_PRIMARY
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
 * 四档评分按钮 — 胶囊圆角（radiusFull），两行文案：Anki 英文惯例名（button 15/600）+ 中文语义（caption 12）。
 * 配色「语义浅底 + ink 深字」成对令牌（见 reviewRatingColor / reviewRatingContentColor，跨端矩阵 #3）。
 * 高度 minHeight 48 随内容弹性增高：系统字体放大 130%+ 时不裁切两行文案
 * （对齐安卓 heightIn(min=48)，跨端矩阵 #6）。
 * 提交中整排禁用 = 内容 38% 透明度（规范 §3.1 M3 口径，跨端矩阵 #7），
 * 且 .enabled 暴露禁用态（读屏可感知，onClick 内拦截保留作双保险）。
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
        .fontColor(reviewRatingContentColor(this.rating))
        .textAlign(TextAlign.Center)
      Text(reviewRatingSemanticLabel(this.rating))
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(reviewRatingContentColor(this.rating))
        .textAlign(TextAlign.Center)
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor(reviewRatingColor(this.rating))
    .opacity(this.isEnabled ? 1 : RATING_DISABLED_ALPHA)
    .enabled(this.isEnabled)
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
 * 宽度百分比 + 弹簧过渡（MotionTiming.SPRING 从当前值起动画、可中断，
 * 对齐安卓 animateFloatAsState(defaultSpring)，跨端矩阵 #16；
 * reduce-motion 全局基建缺失为跨端裁决项，此处不单独处理 — 矩阵 #17）。
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
        .animation({ curve: MotionTiming.SPRING })
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
        // 文字按钮档 §3.3：13/600 + TEXT_GREEN + 热区 ≥48（对齐安卓 PageHeader actions，M7）
        Text(this.actionText)
          .fontSize(DesignFontSize.BUTTON_TEXT)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_GREEN)
          .padding({ left: 12, right: 12, top: 12, bottom: 12 })
          .constraintSize({ minHeight: 48 })
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
          // 48vp 主按钮档（默认档）：本屏唯一主行动，触控 ≥48 对齐安卓空·错态 AppButton（跨端矩阵 #10）
          // 水平内缩 PAGE_PADDING：错误态按钮不通栏贴屏边（对齐安卓 AppButton padding(horizontal=pagePadding)，S11）
          AppButton({
            text: ReviewText.review_action_retry,
            click: (): void => {
              this.onRetry()
            }
          })
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
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
              // 可读上下文信息（非装饰）：TEXT_TERTIARY 双主题均破 4.5:1（M3）
              Text(this.materialTitle)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .margin({ top: DesignDimens.SPACE_XXS })
            }

            Column() {
              this.FormLabel(ReviewText.review_card_dialog_question_label)
              // 多行 TextArea：单行 TextInput 内长文本无法换行回看（对齐安卓 minLines=2，M9）
              TextArea({ text: this.question, placeholder: ReviewText.review_card_dialog_question_hint })
                .fontSize(DesignFontSize.BODY)
                .backgroundColor(DesignColor.SURFACE_VARIANT)
                .borderRadius(DesignDimens.INPUT_RADIUS)
                .borderWidth(1)
                .borderColor(DesignColor.OUTLINE)
                .placeholderColor(DesignColor.TEXT_TERTIARY)
                .placeholderFont({ size: DesignFontSize.BODY })
                .maxLength(1000)
                .constraintSize({ minHeight: 48 })
                .onChange((value: string) => {
                  this.question = value
                })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .margin({ top: DesignDimens.SPACE_MD })

            Column() {
              this.FormLabel(ReviewText.review_card_dialog_answer_label)
              // 多行 TextArea（对齐安卓 minLines=3，M9）
              TextArea({ text: this.answer, placeholder: ReviewText.review_card_dialog_answer_hint })
                .fontSize(DesignFontSize.BODY)
                .backgroundColor(DesignColor.SURFACE_VARIANT)
                .borderRadius(DesignDimens.INPUT_RADIUS)
                .borderWidth(1)
                .borderColor(DesignColor.OUTLINE)
                .placeholderColor(DesignColor.TEXT_TERTIARY)
                .placeholderFont({ size: DesignFontSize.BODY })
                .maxLength(2000)
                .constraintSize({ minHeight: 72 })
                .onChange((value: string) => {
                  this.answer = value
                })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .margin({ top: DesignDimens.SPACE_MD })

            if (this.errorMessage.length > 0) {
              Text(this.errorMessage)
                .fontSize(DesignFontSize.CAPTION)
                .lineHeight(DesignFontSize.CAPTION + 4)
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

        // 按钮行改 AppButton 胶囊 CTA 口径（取消=次按钮 / 确认=主按钮，对齐安卓弹窗按钮行，M10）；
        // 确认提交中由 AppButton 内嵌 LoadingProgress + 禁点（规范 §3.1，M20）
        Row({ space: DesignDimens.SPACE_SM }) {
          AppButton({
            text: ReviewText.review_dialog_action_cancel,
            variant: AppButtonVariant.SECONDARY,
            isEnabled: !this.submitting,
            click: (): void => {
              this.controller.close()
            }
          })
            .layoutWeight(1)

          AppButton({
            text: ReviewText.review_dialog_action_confirm,
            loading: this.submitting,
            click: (): void => {
              this.handleConfirm()
            }
          })
            .layoutWeight(1)
        }
        .width('100%')
        .padding({
          left: DesignDimens.SPACE_LG,
          right: DesignDimens.SPACE_LG,
          top: DesignDimens.SPACE_MD,
          bottom: DesignDimens.SPACE_MD
        })
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
 * now 为可选时钟基准：队列页传入页面级分钟粒度 ticker 的 now，保证全部卡片同一基准、
 * 随停留时间推进（对齐安卓 produceState ticker，跨端矩阵 #4）；缺省回落 Date.now()，
 * 既有调用点（Study/Material 两页）签名兼容不受影响。
 * 契约约定复习/到期判断以服务端时刻为准，客户端本地时钟仅做展示换算。
 */
export function reviewDueTextOf(dueAt: string | null | undefined, now?: number): string {
  if (dueAt === null || dueAt === undefined || dueAt.length < 10) {
    return ''
  }
  const due = new Date(dueAt)
  if (Number.isNaN(due.getTime())) {
    return ''
  }
  const nowMs: number = now ?? Date.now()
  const diffMs = due.getTime() - nowMs
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
