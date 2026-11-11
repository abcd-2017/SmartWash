// 资料页 — 对齐安卓 feature/review/ui/material/MaterialListPage.kt
//
// 资料列表（分页 + 加载更多）+ 粘贴文本建资料（内联表单）+ 每份资料的问答卡列表（展开查看）。
// 契约 v1 未提供改卡/删卡/删资料端点（后端 ReviewWebController 亦无），故卡片仅支持新增与查看。
// 任一写入成功（建资料/建卡）后 bump 复习 tick，队列页 onShown/@Watch 消费重拉。
//
// 已知坑遵守：内容依赖状态的卡片用 @ComponentV2 + @Param/@Event；输入态由表单自身持有；
// 列表骨架/错误/空态齐备（规范 §3.6）。

import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  AddCardForm,
  AddMaterialForm,
  MaterialVo,
  REVIEW_ANSWER_MAX,
  REVIEW_PAGE_SIZE,
  REVIEW_QUESTION_MAX,
  REVIEW_RAW_TEXT_MAX,
  REVIEW_TITLE_MAX,
  ReviewCardVo,
  ReviewText,
  addCard,
  createMaterial,
  pageCards,
  pageMaterials
} from '../api'
import {
  DueTypeChip,
  ReviewCardDialog,
  ReviewCardSubmitResult,
  ReviewErrorState,
  ReviewPageHeader,
  reviewCreatedDateOf,
  reviewIsNewCard
} from './ReviewUiCommon'
import { AppButton, EmptyState, SkeletonBlock, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { bumpReviewRefresh } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'
import { resColor } from '../../../utils/ResourceColor'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'reviewMaterialList'

@Builder
export function MaterialListBuilder() {
  MaterialListPage()
}

@Component
struct MaterialListPage {
  // ---------- 资料列表 ----------
  @State materials: Array<MaterialVo> = []
  @State total: number = 0
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  @State loadMoreError: boolean = false
  // ---------- 展开的卡片区 ----------
  @State expandedMaterialId: number = -1
  @State cards: Array<ReviewCardVo> = []
  @State cardsLoading: boolean = false
  @State cardsError: boolean = false
  @State cardsTotal: number = 0
  @State cardsHasMore: boolean = false
  @State cardsLoadingMore: boolean = false
  @State cardsAppendError: boolean = false
  // ---------- 建资料内联表单 ----------
  @State createVisible: boolean = false
  @State formTitle: string = ''
  @State formRawText: string = ''
  @State creating: boolean = false
  @State createError: string = ''
  // ---------- 建卡弹窗 ----------
  private dialogController: CustomDialogController | null = null

  async aboutToAppear(): Promise<void> {
    await this.loadFirst()
  }

  /** 资料是否还有下一页 */
  private materialsHasMore(): boolean {
    return this.materials.length < this.total
  }

  /** 首刷（进入页面 / 下拉刷新 / 建资料成功后共用） */
  async loadFirst(): Promise<void> {
    this.isLoadingFirst = this.materials.length === 0
    this.isFirstError = false
    try {
      const res = await pageMaterials(1, REVIEW_PAGE_SIZE)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.materials = res.data.records
        this.total = res.data.total
        this.isFirstError = false
      } else {
        logger.warn(TAG, `资料首刷失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isFirstError = true
        showToastSafety(res.message, ReviewText.review_error_load)
      }
    } catch (err) {
      logger.error(TAG, '资料首刷异常', err instanceof Error ? err.message : '')
      this.isFirstError = true
      showToastSafety('', ReviewText.review_error_load)
    } finally {
      this.isLoadingFirst = false
    }
  }

  /** 加载更多（尾部文字按钮触发，对齐安卓 LoadMoreRow） */
  async loadMore(): Promise<void> {
    if (this.isLoadingMore || !this.materialsHasMore()) {
      return
    }
    this.isLoadingMore = true
    this.loadMoreError = false
    const nextPage = Math.floor(this.materials.length / REVIEW_PAGE_SIZE) + 1
    try {
      const res = await pageMaterials(nextPage, REVIEW_PAGE_SIZE)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.materials = this.materials.concat(res.data.records)
        this.total = res.data.total
      } else {
        logger.warn(TAG, `资料加载更多失败 page=${nextPage} code=${res.code ?? 0}`)
        this.loadMoreError = true
        showToastSafety(res.message, ReviewText.review_error_load_more)
      }
    } catch (err) {
      logger.error(TAG, '资料加载更多异常', err instanceof Error ? err.message : '')
      this.loadMoreError = true
      showToastSafety('', ReviewText.review_error_load_more)
    } finally {
      this.isLoadingMore = false
    }
  }

  /** 下拉刷新：整列表重置重拉并收起展开区 */
  private async refreshAll(): Promise<void> {
    this.isRefreshing = true
    this.expandedMaterialId = -1
    this.cards = []
    this.cardsError = false
    this.cardsHasMore = false
    await this.loadFirst()
    this.isRefreshing = false
  }

  /** 展开/收起某份资料的卡片列表（展开即拉卡片首页） */
  private toggleExpand(materialId: number): void {
    if (this.expandedMaterialId === materialId) {
      this.expandedMaterialId = -1
      this.cards = []
      this.cardsLoading = false
      this.cardsError = false
      this.cardsTotal = 0
      this.cardsHasMore = false
      this.cardsAppendError = false
      return
    }
    this.expandedMaterialId = materialId
    this.cards = []
    this.cardsLoading = true
    this.cardsError = false
    this.cardsTotal = 0
    this.cardsHasMore = false
    this.cardsLoadingMore = false
    this.cardsAppendError = false
    this.loadCards(materialId, 1, true)
  }

  /** 加载展开区卡片页（replace=true 为重拉首页/重试；false 为追加） */
  private async loadCards(materialId: number, page: number, replace: boolean): Promise<void> {
    if (replace) {
      this.cardsLoading = true
      this.cardsError = false
    } else {
      this.cardsLoadingMore = true
      this.cardsAppendError = false
    }
    try {
      const res = await pageCards(materialId, page, REVIEW_PAGE_SIZE)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        if (this.expandedMaterialId === materialId) {
          this.cards = replace ? res.data.records : this.cards.concat(res.data.records)
          this.cardsTotal = res.data.total
          this.cardsHasMore = page * REVIEW_PAGE_SIZE < res.data.total
        }
      } else if (this.expandedMaterialId === materialId) {
        logger.warn(TAG, `卡片分页失败 materialId=${materialId} page=${page} code=${res.code ?? 0}`)
        if (replace) {
          this.cardsError = true
        } else {
          this.cardsAppendError = true
        }
      }
    } catch (err) {
      logger.error(TAG, '卡片分页异常', err instanceof Error ? err.message : '')
      if (this.expandedMaterialId === materialId) {
        if (replace) {
          this.cardsError = true
        } else {
          this.cardsAppendError = true
        }
      }
    } finally {
      this.cardsLoading = false
      this.cardsLoadingMore = false
    }
  }

  /** 展开区加载失败后的重试（重拉当前展开资料的卡片首页） */
  private retryCards(): void {
    if (this.expandedMaterialId < 0) {
      return
    }
    this.loadCards(this.expandedMaterialId, 1, true)
  }

  /** 建资料内联表单开关（切换即复位提交态） */
  private toggleCreateForm(): void {
    this.createVisible = !this.createVisible
    this.createError = ''
  }

  /** 提交建资料：客户端前置校验（对齐后端 AddMaterialFrom 上限），成功后清空表单并刷新列表 */
  private async submitCreate(): Promise<void> {
    if (this.creating) {
      return
    }
    const title = this.formTitle.trim()
    const rawText = this.formRawText.trim()
    if (title.length === 0) {
      this.createError = ReviewText.review_error_title_required
      return
    }
    if (title.length > REVIEW_TITLE_MAX) {
      this.createError = ReviewText.review_error_title_too_long
      return
    }
    if (rawText.length > REVIEW_RAW_TEXT_MAX) {
      this.createError = ReviewText.review_error_raw_too_long
      return
    }
    this.creating = true
    this.createError = ''
    const form: AddMaterialForm = { title: title, rawText: rawText.length > 0 ? rawText : undefined }
    try {
      const res = await createMaterial(form)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, '复习资料创建成功')
        showToastSafety(ReviewText.review_toast_material_created)
        bumpReviewRefresh()
        this.createVisible = false
        this.formTitle = ''
        this.formRawText = ''
        await this.loadFirst()
      } else {
        logger.warn(TAG, `复习资料创建被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.createError = res.message ?? ReviewText.review_error_load
      }
    } catch (err) {
      logger.error(TAG, '复习资料创建异常', err instanceof Error ? err.message : '')
      this.createError = ReviewText.review_error_load
    } finally {
      this.creating = false
    }
  }

  /** 打开建卡弹窗（每次新建控制器，保证入参为最新） */
  private openAddCardDialog(materialId: number): void {
    let materialTitle: string = ''
    for (const material of this.materials) {
      if (material.id === materialId) {
        materialTitle = material.title ?? ''
        break
      }
    }
    const controller: CustomDialogController = new CustomDialogController({
      builder: ReviewCardDialog({
        materialTitle: materialTitle,
        onConfirm: (question: string, answer: string): Promise<ReviewCardSubmitResult> => {
          return this.submitAddCard(materialId, question, answer)
        },
        onDismiss: (): void => {
          this.dialogController = null
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true
    })
    this.dialogController = controller
    controller.open()
  }

  /**
   * 提交建卡：前置校验（对齐后端 AddCardFrom 上限），成功后刷新展开资料的卡片首页。
   * 返回结果由弹窗表达（成功关窗 / 失败内联错误）。
   */
  private async submitAddCard(materialId: number, question: string,
    answer: string): Promise<ReviewCardSubmitResult> {
    if (question.length === 0) {
      return { success: false, errorMessage: ReviewText.review_error_question_required }
    }
    if (question.length > REVIEW_QUESTION_MAX) {
      return { success: false, errorMessage: ReviewText.review_error_question_too_long }
    }
    if (answer.length === 0) {
      return { success: false, errorMessage: ReviewText.review_error_answer_required }
    }
    if (answer.length > REVIEW_ANSWER_MAX) {
      return { success: false, errorMessage: ReviewText.review_error_answer_too_long }
    }
    const form: AddCardForm = { question: question, answer: answer }
    try {
      const res = await addCard(materialId, form)
      if (res.code === BizCode.SUCCESS) {
        logger.info(TAG, `问答卡创建成功 materialId=${materialId}`)
        showToastSafety(ReviewText.review_toast_card_created)
        bumpReviewRefresh()
        if (this.expandedMaterialId === materialId) {
          await this.loadCards(materialId, 1, true)
        }
        return { success: true, errorMessage: '' }
      }
      logger.warn(TAG, `问答卡创建被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
      return { success: false, errorMessage: res.message ?? ReviewText.review_error_load }
    } catch (err) {
      logger.error(TAG, '问答卡创建异常', err instanceof Error ? err.message : '')
      return { success: false, errorMessage: ReviewText.review_error_load }
    }
  }

  // ==================== 局部Builder ====================

  /** 建资料内联表单：标题（必填 ≤100）+ 原文粘贴（可选 multiline）；错误仅提交后内联展示 */
  @Builder
  CreateMaterialForm() {
    Column() {
      Text(ReviewText.review_material_form_title_label)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(resColor('TEXT_SECONDARY'))
        .margin({ bottom: DesignDimens.SPACE_XS })

      // 弹性下限 48：130% 字体缩放时不裁切（对齐安卓 heightIn(min=48)，M12）
      TextInput({ text: this.formTitle, placeholder: ReviewText.review_material_form_title_hint })
        .constraintSize({ minHeight: 48 })
        .fontSize(DesignFontSize.BODY)
        .backgroundColor(resColor('SURFACE_VARIANT'))
        .borderRadius(DesignDimens.INPUT_RADIUS)
        .borderWidth(1)
        .borderColor(resColor('OUTLINE'))
        .placeholderColor(resColor('TEXT_TERTIARY'))
        .placeholderFont({ size: DesignFontSize.BODY })
        .maxLength(REVIEW_TITLE_MAX)
        .onChange((value: string) => {
          this.formTitle = value
        })

      Text(ReviewText.review_material_form_raw_label)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(resColor('TEXT_SECONDARY'))
        .margin({ top: DesignDimens.SPACE_LG, bottom: DesignDimens.SPACE_XS })

      // 原文粘贴改多行 TextArea：单行 TextInput 内长文本无法换行回看（对齐安卓 minLines=4 弹性多行，M9）
      TextArea({ text: this.formRawText, placeholder: ReviewText.review_material_form_raw_hint })
        .fontSize(DesignFontSize.BODY)
        .backgroundColor(resColor('SURFACE_VARIANT'))
        .borderRadius(DesignDimens.INPUT_RADIUS)
        .borderWidth(1)
        .borderColor(resColor('OUTLINE'))
        .placeholderColor(resColor('TEXT_TERTIARY'))
        .placeholderFont({ size: DesignFontSize.BODY })
        .maxLength(REVIEW_RAW_TEXT_MAX)
        .constraintSize({ minHeight: 96 })
        .onChange((value: string) => {
          this.formRawText = value
        })

      if (this.createError.length > 0) {
        Text(this.createError)
          .fontSize(DesignFontSize.CAPTION)
          .lineHeight(DesignFontSize.CAPTION + 4)
          .fontColor(resColor('ERROR'))
          .margin({ top: DesignDimens.SPACE_SM })
      }

      AppButton({
        text: ReviewText.review_material_action_create,
        loading: this.creating,
        click: (): void => {
          this.submitCreate()
        }
      })
        .margin({ top: DesignDimens.SPACE_MD })
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(resColor('SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor(resColor('OUTLINE'))
    // 表单容器补卡片档阴影（对齐安卓 GroupCard softCardShadow，M11）
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: resColor('SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    .alignItems(HorizontalAlign.Start)
  }

  /** 空态（规范 §3.6：滚筒圆母题 + 引导文案 + 行动按钮） */
  @Builder
  MaterialEmpty() {
    Column() {
      EmptyState({ message: ReviewText.review_material_empty }) {
        Column() {
          AppButton({
            small: true,
            text: ReviewText.review_material_action_new,
            click: (): void => {
              this.toggleCreateForm()
            }
          })
        }
        .margin({ top: DesignDimens.SPACE_MD })
      }
    }
    .width('100%')
    .padding({ top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_XL })
  }

  /** 加载更多行：加载中骨架块 / 失败文案（行本身即重试入口）/ 静态文字按钮（对齐安卓 LoadMoreRow） */
  @Builder
  LoadMoreRow() {
    Row() {
      if (this.isLoadingMore) {
        SkeletonBlock()
          .width(120)
          .height(14)
      } else if (this.loadMoreError) {
        // 错误文案 13/600 dangerInk 居中（对齐安卓 LoadMoreRow Error 分支，M15）
        Text(ReviewText.review_error_load_more)
          .fontSize(DesignFontSize.BUTTON_TEXT)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(resColor('DANGER_INK'))
          .textAlign(TextAlign.Center)
      } else {
        // 小号绿字走 TEXT_GREEN（PRIMARY 在页面底仅 3.22:1 破线，M4）
        Text(ReviewText.review_material_load_more)
          .fontSize(DesignFontSize.BUTTON_TEXT)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(resColor('TEXT_GREEN'))
      }
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
    .justifyContent(FlexAlign.Center)
    .alignItems(VerticalAlign.Center)
    .onClick(() => {
      // 错误态行本身即重试入口，仅加载中拦截（对齐安卓 LoadMoreRow pressable 常开，M6）
      if (!this.isLoadingMore) {
        this.loadMore()
      }
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        ReviewPageHeader({
          title: ReviewText.review_material_title,
          actionText: ReviewText.review_material_action_new,
          onAction: (): void => {
            this.toggleCreateForm()
          }
        })

        if (this.isLoadingFirst) {
          SkeletonList({ rows: 5 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isFirstError && this.materials.length === 0) {
          ReviewErrorState({
            message: ReviewText.review_error_load,
            onRetry: (): void => {
              this.loadFirst()
            }
          })
            .width('100%')
            .layoutWeight(1)
        } else {
          Refresh({ refreshing: $$this.isRefreshing }) {
            // 列表项间距 16：相邻卡片贴合仅隔描边（对齐安卓 spacedBy(cardSpacing)，M5）
            List({ space: DesignDimens.CARD_SPACING }) {
              if (this.createVisible) {
                ListItem() {
                  this.CreateMaterialForm()
                }
              }

              if (this.materials.length === 0) {
                ListItem() {
                  this.MaterialEmpty()
                }
              } else {
                ForEach(this.materials, (material: MaterialVo) => {
                  ListItem() {
                    MaterialCard({
                      material: material,
                      expanded: this.expandedMaterialId === material.id,
                      cards: this.cards,
                      cardsLoading: this.cardsLoading,
                      cardsError: this.cardsError,
                      cardsTotal: this.cardsTotal,
                      cardsHasMore: this.cardsHasMore,
                      cardsLoadingMore: this.cardsLoadingMore,
                      cardsAppendError: this.cardsAppendError,
                      onToggleExpand: (): void => {
                        this.toggleExpand(material.id ?? -1)
                      },
                      onAddCard: (): void => {
                        this.openAddCardDialog(material.id ?? -1)
                      },
                      onLoadMoreCards: (): void => {
                        this.loadCards(this.expandedMaterialId,
                          Math.floor(this.cards.length / REVIEW_PAGE_SIZE) + 1, false)
                      },
                      onRetryCards: (): void => {
                        this.retryCards()
                      }
                    })
                  }
                }, (material: MaterialVo) => `${material.id ?? 0}`)

                if (this.materialsHasMore()) {
                  ListItem() {
                    this.LoadMoreRow()
                  }
                }
              }
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

// ==================== 资料卡（内容依赖状态 → @ComponentV2 + @Param/@Event） ====================

/**
 * 资料卡：标题 + 创建日期 + 原文预览（两行）；头部整行点击展开/收起卡片列表。
 * 展开区含卡片列表头（计数 + 添加卡片）与卡片行（发丝线分隔）+ 加载更多。
 */
@ComponentV2
struct MaterialCard {
  @Require @Param material: MaterialVo
  @Param expanded: boolean = false
  @Param cards: Array<ReviewCardVo> = []
  @Param cardsLoading: boolean = false
  @Param cardsError: boolean = false
  @Param cardsTotal: number = 0
  @Param cardsHasMore: boolean = false
  @Param cardsLoadingMore: boolean = false
  @Param cardsAppendError: boolean = false
  @Event onToggleExpand: () => void = () => {
  }
  @Event onAddCard: () => void = () => {
  }
  @Event onLoadMoreCards: () => void = () => {
  }
  @Event onRetryCards: () => void = () => {
  }

  build() {
    Column() {
      // 头部整行点击展开/收起（热区 ≥48，对齐安卓 heightIn(min=48)，M8）
      Row() {
        Column() {
          Text(this.material.title ?? '')
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(resColor('TEXT_PRIMARY'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          // 创建日期是可读信息（非装饰角标）：TEXT_TERTIARY 双主题均破 4.5:1（M1）
          Text(reviewCreatedDateOf(this.material.createdAt, '—'))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(resColor('TEXT_SECONDARY'))
            .fontFeature(DesignFontFeature.TNUM)
            .margin({ top: DesignDimens.SPACE_XXS })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        // 展开/收起：caption 12 + TEXT_GREEN（对齐安卓 labelMedium + textGreen，M4）
        Text(this.expanded ? ReviewText.review_material_collapse : ReviewText.review_material_expand)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(resColor('TEXT_GREEN'))
      }
      .width('100%')
      .constraintSize({ minHeight: 48 })
      .alignItems(VerticalAlign.Center)
      .onClick(() => {
        this.onToggleExpand()
      })

      if (this.material.rawText !== null && this.material.rawText !== undefined &&
        (this.material.rawText ?? '').length > 0) {
        Text(this.material.rawText ?? '')
          .fontSize(DesignFontSize.CAPTION)
          .lineHeight(DesignFontSize.CAPTION + 4)
          .fontColor(resColor('TEXT_SECONDARY'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XS })
      }

      if (this.expanded) {
        // 展开/收起过渡：淡入淡出保守档（对齐安卓 AnimatedVisibility fade+expand 的近似，M16）
        Column() {
          Divider()
            .strokeWidth(0.5)
            .color(resColor('DIVIDER'))
            .width('100%')
            .margin({ top: DesignDimens.SPACE_SM })

          Row() {
            Text(ReviewText.review_material_cards_header_format.replace('%d', `${this.cardsTotal}`))
              .fontSize(DesignFontSize.CAPTION)
              .lineHeight(DesignFontSize.CAPTION + 4)
              .fontColor(resColor('TEXT_SECONDARY'))
              .fontFeature(DesignFontFeature.TNUM)
              .layoutWeight(1)

            Text(ReviewText.review_material_add_card)
              .fontSize(DesignFontSize.BUTTON_TEXT)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(resColor('TEXT_GREEN'))
              .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: DesignDimens.SPACE_XXS, bottom: DesignDimens.SPACE_XXS })
              .constraintSize({ minHeight: 48 })
              .onClick(() => {
                this.onAddCard()
              })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          .margin({ top: DesignDimens.SPACE_SM })

          this.ExpandedCards()
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
      }
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(resColor('SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor(resColor('OUTLINE'))
    // 资料卡补卡片档阴影（对齐安卓 AppCard softCardShadow，M11）
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: resColor('SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    .alignItems(HorizontalAlign.Start)
  }

  /** 展开区卡片列表：加载骨架 / 错误重试 / 空提示 / 问答行（发丝线分隔）+ 加载更多 */
  @Builder
  ExpandedCards() {
    Column() {
      if (this.cardsLoading) {
        Column() {
          SkeletonBlock()
            .width('70%')
            .height(14)
          SkeletonBlock()
            .width('70%')
            .height(14)
        }
        .width('100%')
        .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
        .alignItems(HorizontalAlign.Start)
      } else if (this.cardsError) {
        Row() {
          Text(ReviewText.review_error_load)
            .fontSize(DesignFontSize.CAPTION)
            .lineHeight(DesignFontSize.CAPTION + 4)
            .fontColor(resColor('DANGER_INK'))
            .layoutWeight(1)
          Text(ReviewText.review_action_retry)
            .fontSize(DesignFontSize.BUTTON_TEXT)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(resColor('TEXT_GREEN'))
            .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: DesignDimens.SPACE_XXS, bottom: DesignDimens.SPACE_XXS })
            .constraintSize({ minHeight: 48 })
            .onClick(() => {
              this.onRetryCards()
            })
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
        .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
      } else if (this.cards.length === 0) {
        // 引导性正文（非装饰角标）：TEXT_TERTIARY 双主题均破 4.5:1（M2）
        Text(ReviewText.review_material_cards_empty)
          .fontSize(DesignFontSize.CAPTION)
          .lineHeight(DesignFontSize.CAPTION + 4)
          .fontColor(resColor('TEXT_SECONDARY'))
          .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
      } else {
        ForEach(this.cards, (card: ReviewCardVo, index: number) => {
          Column() {
            Column() {
              Text(card.question ?? '')
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(resColor('TEXT_PRIMARY'))
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .width('100%')

              Text(card.answer ?? '')
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(resColor('TEXT_SECONDARY'))
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .width('100%')
                .margin({ top: DesignDimens.SPACE_XXS })

              DueTypeChip({
                isNew: reviewIsNewCard(card),
                state: card.state ?? 1
              })
                .margin({ top: DesignDimens.SPACE_XXS })
            }
            .width('100%')
            .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
            .alignItems(HorizontalAlign.Start)

            if (index < this.cards.length - 1) {
              Divider()
                .strokeWidth(0.5)
                .color(resColor('DIVIDER'))
                .width('100%')
            }
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }, (card: ReviewCardVo, index: number) => `${card.id ?? 0}-${index}`)

        if (this.cardsHasMore) {
          Row() {
            if (this.cardsLoadingMore) {
              SkeletonBlock()
                .width(120)
                .height(14)
            } else if (this.cardsAppendError) {
              Text(ReviewText.review_error_load_more)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(resColor('DANGER_INK'))
            } else {
              Text(ReviewText.review_material_load_more)
                .fontSize(DesignFontSize.BUTTON_TEXT)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(resColor('TEXT_GREEN'))
            }
          }
          .width('100%')
          .constraintSize({ minHeight: 48 })
          .justifyContent(FlexAlign.Center)
          .alignItems(VerticalAlign.Center)
          .onClick(() => {
            // 错误态行本身即重试入口，仅加载中拦截（对齐安卓整行常开可点，M6）
            if (!this.cardsLoadingMore) {
              this.onLoadMoreCards()
            }
          })
        }
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
