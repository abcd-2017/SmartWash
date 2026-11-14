// 发起点单页 — 对齐 Android DiningOrderCreatePage.kt
//
// 常规模式：标题输入 + 左分类右菜品（按分类分组勾选上架菜谱）+ 固定 CTA 提交；
// 复用模式（query fromOrderId，13 号票语义）：拉源单详情回显菜单只读 + 提示 + 提交带
// fromOrderId（菜单由后端整份复制，端上只校验标题）。
// 提交成功 → replace 分享视图（等价 Android popUpTo(OrderCreate)+navigate：分享返回直回列表）。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  DINING_PAGE_SIZE,
  DiningCategoryVo,
  DiningMenuRecipe,
  DiningOrderDetailVo,
  DiningRecipeVo,
  createDiningOrder,
  getDiningCategories,
  getDiningOrderDetail,
  getDiningRecipes
} from '../../../network/api/DiningApi'
import {
  diningOrderStatusFromCode,
  DiningCategoryName,
  DiningMenuSection,
  DiningOrderFormError,
  DiningOrderStatus,
  DiningMenuRecipeRow,
  groupDiningMenu,
  validateDiningOrderForm,
  validateDiningOrderReuse
} from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningCheckbox } from './DiningUiCommon'
import { DiningOrderCreateParam, DiningOrderShareParam } from '../api'
import { AppButton, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { getObjectParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG */
const TAG: string = 'diningOrderCreate'

/** 菜谱分页防御上限（对齐 Android 创建页全量拉取 100 页防御） */
const PAGE_LIMIT: number = 100

@Builder
export function DiningOrderCreateBuilder() {
  DiningOrderCreatePage()
}

@ComponentV2
struct DiningOrderCreatePage {
  // 复用模式参数（aboutToAppear 收窄；fromOrderId > 0 = 复用模式）
  private fromOrderId: number = 0
  private sourceTitle: string = ''
  private isReuseMode: boolean = false

  @Local titleInput: string = ''
  /** 常规模式：已勾选菜谱 ID（保序，提交按勾选顺序） */
  @Local selectedIds: Array<number> = []
  @Local categories: Array<DiningCategoryVo> = []
  @Local selectedCategoryId: number = 0
  @Local recipes: Array<DiningRecipeVo> = []
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  @Local loadErrorMessage: string = ''
  @Local submitting: boolean = false
  // 复用模式：源单只读回显
  @Local reuseSections: Array<DiningMenuSection> = []
  @Local reuseRecipeCount: number = 0
  // 常规模式：分类筛选后的分页拉满进行中
  private loadingRecipes: boolean = false
  private recipeSeq: number = 0

  async aboutToAppear(): Promise<void> {
    const param = getObjectParam<DiningOrderCreateParam>(RouteNames.DINING_ORDER_CREATE)
    if (param !== null) {
      this.fromOrderId = param.fromOrderId
      this.sourceTitle = param.sourceTitle
    }
    this.isReuseMode = this.fromOrderId > 0
    logger.debug(TAG, `创建页挂载 复用模式=${this.isReuseMode} fromOrderId=${this.fromOrderId}`)
    if (this.isReuseMode) {
      await this.loadReuseSource()
    } else {
      await this.loadCategories()
    }
    this.isLoading = false
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
        Column() {
          Row() {
            Column() {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([$r('app.color.TEXT_PRIMARY')])
            }
            .width(DesignDimens.BACK_HIT_AREA)
            .height(DesignDimens.BACK_HIT_AREA)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              pathStack.pop()
            })

            Text(DiningText.dining_order_create_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoading) {
            Column() {
              SkeletonList({ rows: 5 })
            }
            .width('100%')
            .layoutWeight(1)
          } else if (this.isError) {
            Column() {
              EmptyState({ message: this.loadErrorMessage.length > 0
                ? this.loadErrorMessage
                : (this.isReuseMode ? DiningText.dining_order_detail_load_failed : DiningText.dining_order_create_load_failed) })
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            this.FormBody()
          }
        }
        .width('100%')
        .height('100%')
        .padding({ top: safeTopVp(), bottom: safeBottomVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }

  /** 表单体：标题输入 →（复用提示 | 分类+菜品列表）→ 底部 CTA */
  @Builder
  FormBody() {
    Column() {
      Scroll() {
        Column() {
          // 标题输入（ TextInput 单行；占位与标签对齐安卓 dining_order_create_* 文案）
          Text(DiningText.dining_order_create_title_label)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .width('100%')
          TextInput({ text: this.titleInput, placeholder: DiningText.dining_order_create_title_hint })
            .placeholderColor($r('app.color.TEXT_TERTIARY'))
            .placeholderFont({ size: DesignFontSize.BODY })
            .caretColor($r('app.color.PRIMARY'))
            .backgroundColor($r('app.color.SURFACE_VARIANT'))
            .borderRadius(DesignDimens.INPUT_RADIUS)
            .fontSize(DesignFontSize.BODY)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .maxLength(64)
            .width('100%')
            .height(48)
            .margin({ top: DesignDimens.SPACE_XS })
            .onChange((value: string) => {
              this.titleInput = value
            })

          if (this.isReuseMode) {
            this.ReuseHint()
            this.ReuseMenu()
          } else {
            Text(DiningText.dining_order_create_menu_label)
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .width('100%')
              .margin({ top: DesignDimens.SPACE_MD })
            this.CategoryChips()
            this.RecipeList()
          }

          Column() {
          }
          .height(DesignDimens.SPACE_MD)
        }
        .width('100%')
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_SM,
          bottom: DesignDimens.SPACE_MD
        })
      }
      .scrollBar(BarState.Off)
      .layoutWeight(1)
      .align(Alignment.TopStart)
      .edgeEffect(EdgeEffect.Spring)

      // 底部 CTA（复用模式只校验标题；常规模式校验标题+菜单）
      AppButton({
        text: DiningText.dining_order_create_submit,
        loading: this.submitting,
        click: () => {
          this.submit()
        }
      })
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_MD)
        })
    }
    .width('100%')
    .layoutWeight(1)
  }

  /** 复用提示条（浅绿胶囊提示历史菜单按原单复制不可修改） */
  @Builder
  ReuseHint() {
    Text(DiningText.reuseHint(this.sourceTitle.length > 0 ? this.sourceTitle : DiningText.dining_order_join_unknown_title,
      this.reuseRecipeCount))
      .fontSize(DesignFontSize.CAPTION)
      .fontColor($r('app.color.PRIMARY'))
      .backgroundColor($r('app.color.PRIMARY_LIGHT'))
      .borderRadius(DesignDimens.RADIUS_MD)
      .width('100%')
      .padding(DesignDimens.SPACE_MD)
      .margin({ top: DesignDimens.SPACE_MD })
  }

  /** 复用模式只读回显：源单菜单按分类分组（无勾选交互） */
  @Builder
  ReuseMenu() {
    Column({ space: DesignDimens.SPACE_MD }) {
      if (this.reuseSections.length === 0) {
        Text(DiningText.dining_order_detail_empty_menu)
          .fontSize(DesignFontSize.BODY)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')
          .padding({ top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_XL })
      }
      ForEach(this.reuseSections, (section: DiningMenuSection) => {
        Column({ space: DesignDimens.SPACE_XS }) {
          Text(section.categoryName)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .width('100%')
            .padding({ top: DesignDimens.SPACE_XS })
          ForEach(section.recipes, (row: DiningMenuRecipeRow) => {
            Text(row.name)
              .fontSize(DesignFontSize.BODY)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .width('100%')
              .padding({ top: 6, bottom: 6 })
          }, (row: DiningMenuRecipeRow) => `reuse_${row.recipeId}`)
        }
        .width('100%')
      }, (section: DiningMenuSection) => `reuse_section_${section.categoryId}`)
    }
    .width('100%')
    .margin({ top: DesignDimens.SPACE_MD })
  }

  /** 分类筛选 chips（全部 + 分类目录；横滑） */
  @Builder
  CategoryChips() {
    Scroll() {
      Row({ space: DesignDimens.SPACE_XS }) {
        this.CategoryChip(0, DiningText.dining_category_all)
        ForEach(this.categories, (category: DiningCategoryVo) => {
          this.CategoryChip(category.id, category.name)
        }, (category: DiningCategoryVo) => `cat_${category.id}`)
      }
      .padding({ left: 2, right: 2 })
    }
    .scrollable(ScrollDirection.Horizontal)
    .scrollBar(BarState.Off)
    .align(Alignment.Start)
    .width('100%')
    .margin({ top: DesignDimens.SPACE_XS })
  }

  @Builder
  CategoryChip(categoryId: number, label: string) {
    Row() {
      Text(label)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(this.selectedCategoryId === categoryId ? $r('app.color.ON_PRIMARY') : $r('app.color.TEXT_SECONDARY'))
    }
    .justifyContent(FlexAlign.Center)
    .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 10, bottom: 10 })
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor(this.selectedCategoryId === categoryId ? $r('app.color.PRIMARY') : $r('app.color.SURFACE'))
    .border({
      width: 1,
      color: this.selectedCategoryId === categoryId ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE')
    })
    .onClick(() => {
      if (this.selectedCategoryId === categoryId) {
        return
      }
      this.selectedCategoryId = categoryId
      this.reloadRecipes()
    })
  }

  /** 菜品列表（当前分类筛选；勾选行 + 已选计数） */
  @Builder
  RecipeList() {
    Column({ space: DesignDimens.SPACE_XS }) {
      Text(DiningText.selectedCount(this.selectedIds.length))
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .width('100%')
        .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_XS })

      if (this.recipes.length === 0) {
        Text(DiningText.dining_recipe_list_empty_filtered)
          .fontSize(DesignFontSize.BODY)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')
          .padding({ top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_XL })
      }
      ForEach(this.recipes, (recipe: DiningRecipeVo) => {
        this.RecipeRow(recipe)
      }, (recipe: DiningRecipeVo) => `recipe_${recipe.id}_${this.selectedIds.indexOf(recipe.id) >= 0 ? 1 : 0}`)
    }
    .width('100%')
    .margin({ top: DesignDimens.SPACE_XS })
  }

  /** 菜品行：勾选框 + 菜名 + 食材数 */
  @Builder
  RecipeRow(recipe: DiningRecipeVo) {
    Row({ space: DesignDimens.SPACE_SM }) {
      DiningCheckbox({ checked: this.selectedIds.indexOf(recipe.id) >= 0 })

      Column({ space: 2 }) {
        Text(recipe.name)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        if (recipe.ingredients.length > 0) {
          Text(DiningText.ingredientsCount(recipe.ingredients.length))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM, left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS })
    .borderRadius(DesignDimens.RADIUS_MD)
    .backgroundColor($r('app.color.SURFACE'))
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    .onClick(() => {
      this.toggleRecipe(recipe.id)
    })
  }

  /** 拉全量分类（复用模式也拉，供分组回填分类名；失败随主流程进错误态） */
  private async loadCategories(): Promise<void> {
    try {
      const res = await getDiningCategories()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.categories = res.data
      } else {
        logger.warn(TAG, `分类加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        this.loadErrorMessage = DiningText.dining_category_load_failed
        showToastSafety(res.message, DiningText.dining_category_load_failed)
        return
      }
    } catch (err) {
      logger.error(TAG, '分类加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      this.loadErrorMessage = DiningText.dining_category_load_failed
      showToastSafety('', DiningText.dining_category_load_failed)
      return
    }
    await this.reloadRecipes()
  }

  /** 按当前分类重拉菜谱（分页循环取满：新菜谱在前；防御上限 100 页） */
  private async reloadRecipes(): Promise<void> {
    this.recipeSeq++
    const seq = this.recipeSeq
    this.recipes = []
    this.loadingRecipes = true
    try {
      let page = 1
      while (page <= PAGE_LIMIT) {
        const res = await getDiningRecipes(page, DINING_PAGE_SIZE, this.selectedCategoryId, '')
        if (seq !== this.recipeSeq) {
          return // 筛选已切换，过期响应整体丢弃
        }
        if (res.code !== BizCode.SUCCESS || res.data === null) {
          logger.warn(TAG, `菜谱加载失败 page=${page} code=${res.code}`)
          showToastSafety(res.message, DiningText.dining_order_create_load_failed)
          if (this.recipes.length === 0) {
            this.isError = true
            this.loadErrorMessage = DiningText.dining_order_create_load_failed
          }
          return
        }
        this.recipes = this.recipes.concat(res.data.records)
        if (res.data.records.length < DINING_PAGE_SIZE) {
          break
        }
        page++
      }
    } catch (err) {
      logger.error(TAG, '菜谱加载异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_order_create_load_failed)
      if (this.recipes.length === 0) {
        this.isError = true
        this.loadErrorMessage = DiningText.dining_order_create_load_failed
      }
    } finally {
      this.loadingRecipes = false
    }
  }

  /** 拉源单详情（复用模式：源单须为当前用户成员；后端非成员返回业务失败） */
  private async loadReuseSource(): Promise<void> {
    // 分类目录 best-effort 预拉（分组名回填；失败不致命，兜底「分类 {id}」）
    try {
      const catRes = await getDiningCategories()
      if (catRes.code === BizCode.SUCCESS && catRes.data !== null) {
        this.categories = catRes.data
      }
    } catch (err) {
      logger.debug(TAG, '复用模式分类目录预拉失败，分组名走兜底')
    }
    try {
      const res = await getDiningOrderDetail(this.fromOrderId, null)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const detail: DiningOrderDetailVo = res.data
        if (diningOrderStatusFromCode(detail.status) !== DiningOrderStatus.ONGOING) {
          // 已结束的源单仍可复用（历史菜单复制），不拦截（后端为准）；此处仅留痕
          logger.debug(TAG, `复用源单状态=${detail.status}`)
        }
        const rows: Array<DiningMenuRecipeRow> = detail.menu.map((recipe: DiningMenuRecipe): DiningMenuRecipeRow =>
          ({ recipeId: recipe.recipeId, name: recipe.name, categoryId: recipe.categoryId }))
        this.reuseRecipeCount = rows.length
        const names: Array<DiningCategoryName> = this.categories.map((c: DiningCategoryVo): DiningCategoryName =>
          ({ id: c.id, name: c.name }))
        this.reuseSections = groupDiningMenu(rows, names, DiningText.dining_order_create_menu_uncategorized)
      } else {
        logger.warn(TAG, `源单详情加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        this.loadErrorMessage = DiningText.dining_order_detail_load_failed
        showToastSafety(res.message, DiningText.dining_order_detail_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '源单详情加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      this.loadErrorMessage = DiningText.dining_order_detail_load_failed
      showToastSafety('', DiningText.dining_order_detail_load_failed)
    }
  }

  /** 勾选/取消（上限内保序追加；上限提示） */
  private toggleRecipe(recipeId: number): void {
    const index = this.selectedIds.indexOf(recipeId)
    if (index >= 0) {
      const updated = this.selectedIds.slice()
      updated.splice(index, 1)
      this.selectedIds = updated
      return
    }
    if (this.selectedIds.length >= 100) {
      showToastSafety(DiningText.dining_order_error_menu_too_many)
      return
    }
    this.selectedIds = this.selectedIds.concat([recipeId])
  }

  /** 提交：常规 = validate + recipeIds；复用 = validateReuse + fromOrderId */
  private async submit(): Promise<void> {
    if (this.submitting) {
      return
    }
    const validation = this.isReuseMode
      ? validateDiningOrderReuse(this.titleInput)
      : validateDiningOrderForm(this.titleInput, this.selectedIds)
    if (!validation.valid) {
      showToastSafety(this.orderErrorText(validation.error))
      return
    }
    this.submitting = true
    try {
      const res = await createDiningOrder({
        title: validation.normalizedTitle,
        recipeIds: this.isReuseMode ? null : validation.normalizedRecipeIds,
        fromOrderId: this.isReuseMode ? this.fromOrderId : null
      })
      if (res.code === BizCode.SUCCESS && res.data !== null && res.data.orderId > 0) {
        logger.info(TAG, `点单创建成功 orderId=${res.data.orderId}`)
        showToastSafety(DiningText.dining_order_create_submit_success)
        const shareParam = new DiningOrderShareParam()
        shareParam.orderId = res.data.orderId
        shareParam.joinToken = res.data.joinToken
        // replace 等价 Android popUpTo(OrderCreate) inclusive + navigate：分享返回直回列表
        pathStack.replacePathByName(RouteNames.DINING_ORDER_SHARE, shareParam)
      } else {
        logger.warn(TAG, `点单创建未生效 code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dining_order_create_result_invalid)
      }
    } catch (err) {
      logger.error(TAG, '点单创建异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_order_create_result_invalid)
    }
    this.submitting = false
  }

  /** 表单错误枚举 → 文案 */
  private orderErrorText(error: DiningOrderFormError | null): string {
    if (error === DiningOrderFormError.TITLE_REQUIRED) {
      return DiningText.dining_order_error_title_required
    }
    if (error === DiningOrderFormError.TITLE_TOO_LONG) {
      return DiningText.dining_order_error_title_too_long
    }
    if (error === DiningOrderFormError.MENU_EMPTY) {
      return DiningText.dining_order_error_menu_empty
    }
    if (error === DiningOrderFormError.MENU_TOO_MANY) {
      return DiningText.dining_order_error_menu_too_many
    }
    return DiningText.dining_order_create_result_invalid
  }
}
