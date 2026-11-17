// 发起点单页 — 对齐 Android DiningOrderCreatePage.kt
//
// 常规模式：标题输入 + 左分类右菜品（按分类分组勾选上架菜谱）+ 固定 CTA 提交；
// 复用模式（query fromOrderId，13 号票语义）：拉源单详情回显菜单只读 + 提示 + 提交带
// fromOrderId（菜单由后端整份复制，端上只校验标题）。
// 提交成功 → replace 分享视图（等价 Android popUpTo(OrderCreate)+navigate：分享返回直回列表）。

import { KeyboardAvoidMode } from '@kit.ArkUI'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontFeature, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  DietaryPreferenceVo,
  DINING_PAGE_SIZE,
  DiningCategoryVo,
  DiningMenuRecipe,
  DiningOrderDetailVo,
  DiningRecipeVo,
  createDiningOrder,
  getDiningCategories,
  getDiningDietaryPreference,
  getDiningOrderDetail,
  getDiningRecipeDetail,
  getDiningRecipes
} from '../../../network/api/DiningApi'
import {
  DINING_PEOPLE_MAX,
  DINING_PEOPLE_MIN,
  DiningAllergenHit,
  DiningOrderFormError,
  DiningPortionVerdict,
  DiningSelectedDish,
  DiningCategoryName,
  DiningMenuRecipeRow,
  DiningMenuSection,
  DiningOrderStatus,
  coerceDiningPeopleCount,
  diningAllergenHits,
  diningOrderStatusFromCode,
  diningPortionAdvice,
  diningSelectedTotalCalories,
  diningServingCalories,
  formatDiningAmount,
  groupDiningMenu,
  validateDiningOrderForm,
  validateDiningOrderReuse
} from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningCheckbox } from './DiningUiCommon'
import { DINING_DEFAULT_CALORIE_TARGET } from '../../../network/vo/DiningVo'
import { DiningOrderCreateParam, DiningOrderShareParam } from '../api'
import { AppButton, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { LoadingFooter } from '../../../view/LoadingFooter'
import { logger } from '../../../utils/Logger'
import { LazyListDataSource, PagingController } from '../../../utils/PagingUtil'
import { getObjectParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

const TAG: string = 'diningOrderCreate'

/** 过敏原缓存条目（详情接口按需拉取结果） */
interface DiningAllergenCacheEntry {
  recipeId: number
  allergens: Array<string>
}

/** 份量建议展示模型（文案 + 判定色档） */
interface DiningPortionAdviceText {
  text: string
  verdict: DiningPortionVerdict
}

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
  // 常规模式：触底加载更多态 / 追加失败态（驱动列表 footer）
  @Local isLoadingMore: boolean = false
  @Local appendError: boolean = false
  /** LazyForEach 数据源（读取 recipes，实例稳定不可重建，避免整表重建） */
  private dataSource: LazyListDataSource<DiningRecipeVo> =
    new LazyListDataSource<DiningRecipeVo>(() => this.recipes)
  /** 轻量分页封装：首屏第 1 页 + 触底加载 / 上锁防重 / 封底与失败重试 */
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchRecipePage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )
  /** 筛选切换序号：过期响应整体丢弃，避免旧分类结果污染新列表 */
  private recipeSeq: number = 0

  // ---- 忌口 / 份量（契约 §2.1/§2.5/§三.1/§三.5）----
  /** 用户忌口标签（GET 饮食偏好；空集=无忌口） */
  @Local userAllergens: Array<string> = []
  /** 每日热量目标（kcal；默认 2000） */
  @Local calorieTarget: number = DINING_DEFAULT_CALORIE_TARGET
  /** 人数本地态（1-20，默认 1；不落库） */
  @Local peopleCount: number = DINING_PEOPLE_MIN
  /** 已选菜 VO（保序，供热量合计/忌口警示派生） */
  @Local selectedRecipes: Array<DiningRecipeVo> = []
  /** 过敏原缓存（recipeId → 详情接口返回的过敏原码；按需拉取，失败不缓存） */
  @Local allergenCache: Array<DiningAllergenCacheEntry> = []
  /** 过敏原拉取在途（防重复请求） */
  @Local allergenPendingIds: Array<number> = []

  aboutToDisappear(): void {
    // 键盘避让兜底复位（对齐 Workbench 先例）：路由守卫已收口，此处防焦点残留泄漏
    this.restoreKeyboardAvoidResize()
  }

  /**
   * 键盘避让 NONE 落位（仅标题输入聚焦期间，TD15 对齐安卓定案）：
   * 本页是「表单头 + 长列表 + 固定 CTA」混合页，全局默认 RESIZE 会整窗压缩——键盘弹出
   * 列表被压缩、底部 CTA 弹起跳变（与安卓 imePadding 整页上收同源问题）。改 NONE 让
   * 键盘直接覆盖列表下半部（输入场景列表本就无需交互），整页不上收。
   * 路由守卫（PathStackUtil.installKeyboardAvoidGuard）在导航切换时复位 RESIZE，
   * 失焦/离页页内自查兜底复位，避免影响登录/充值等需要避让的输入页。
   */
  private applyKeyboardAvoidNone(): void {
    try {
      this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.NONE)
      logger.debug(TAG, `键盘避让模式已设 NONE，回读=${this.getUIContext().getKeyboardAvoidMode()}`)
    } catch (err) {
      logger.warn(TAG, '设置键盘避让模式失败')
    }
  }

  /** 恢复全局 RESIZE（输入失焦 / 离开本页）：失败静默不阻塞 */
  private restoreKeyboardAvoidResize(): void {
    try {
      this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE)
    } catch (err) {
      logger.warn(TAG, '恢复键盘避让模式失败')
    }
  }

  async aboutToAppear(): Promise<void> {
    const param = getObjectParam<DiningOrderCreateParam>(RouteNames.DINING_ORDER_CREATE)
    if (param !== null) {
      this.fromOrderId = param.fromOrderId
      this.sourceTitle = param.sourceTitle
    }
    this.isReuseMode = this.fromOrderId > 0
    logger.debug(TAG, `创建页挂载 复用模式=${this.isReuseMode} fromOrderId=${this.fromOrderId}`)
    // 偏好首载（best-effort：失败按默认目标 + 无忌口降级，不阻断发起点单）
    await this.loadDietaryPreference()
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

  /** 标题输入（TextInput 单行；占位与标签对齐安卓 dining_order_create_* 文案） */
  @Builder
  TitleInput() {
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
      // 键盘弹出前设 NONE（Workbench 2026-11-05 实测：聚焦期设置才稳定生效），失焦复位
      .onFocus(() => {
        this.applyKeyboardAvoidNone()
      })
      .onBlur(() => {
        this.restoreKeyboardAvoidResize()
      })
      .onChange((value: string) => {
        this.titleInput = value
      })
  }

  /** 表单体：标题输入 →（复用提示+只读菜单 | 分类+菜品列表）→ 底部 CTA */
  @Builder
  FormBody() {
    Column() {
      if (this.isReuseMode) {
        // 复用模式：源单菜单只读且可能较长，整体滚动（视觉与改造前一致）
        Scroll() {
          Column() {
            this.TitleInput()
            this.ReuseHint()
            this.ReuseMenu()
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
      } else {
        // 固定头部：标题输入 + 菜单标签 + 分类 chips + 已选计数（不随列表滚动）
        Column() {
          this.TitleInput()
          Text(DiningText.dining_order_create_menu_label)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .width('100%')
            .margin({ top: DesignDimens.SPACE_MD })
          this.CategoryChips()
          Text(DiningText.selectedCount(this.selectedIds.length))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .width('100%')
            .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_XS })
        }
        .width('100%')
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_SM,
          bottom: DesignDimens.SPACE_MD
        })

        // 菜品列表（List + LazyForEach 懒加载；首屏第 1 页，触底按需加载更多）
        List({ space: DesignDimens.SPACE_XS }) {
          if (this.recipes.length === 0) {
            ListItem() {
              Text(DiningText.dining_recipe_list_empty_filtered)
                .fontSize(DesignFontSize.BODY)
                .fontColor($r('app.color.TEXT_SECONDARY'))
                .width('100%')
                .padding({ top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_XL })
            }
          }

          LazyForEach(this.dataSource, (recipe: DiningRecipeVo) => {
            ListItem() {
              this.RecipeRow(recipe)
            }
          // 键含勾选状态指纹：勾选变化若键不变，LazyForEach 复用旧子组件导致勾选框不刷新
          }, (recipe: DiningRecipeVo) => `recipe_${recipe.id}_${this.selectedIds.indexOf(recipe.id) >= 0 ? 1 : 0}`)

          ListItem() {
            this.ListFooter()
          }
        }
        .width('100%')
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        .edgeEffect(EdgeEffect.Spring)
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          bottom: DesignDimens.SPACE_MD
        })
        .onScrollIndex((start: number, end: number) => {
          // listLength 为 ListItem 真实数量（菜品行 + footer）
          this.paging.onScrollIndex(end, this.dataSource.totalCount() + 1)
        })
        .onScrollFrameBegin((offset: number) => {
          this.paging.onScrollFrameBegin(offset)
          return { offsetRemain: offset }
        })

        // 忌口警示 + 人数份量建议（常规模式；固定于列表与 CTA 之间）
        this.WarningAndPortionBar()
      }

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

  /** 列表 footer：加载更多 spinner / 失败重试 / 已全部加载 */
  @Builder
  ListFooter() {
    if (this.isLoadingMore) {
      LoadingFooter({ isLoading: true })
    } else if (this.appendError) {
      Text(DiningText.dining_order_create_load_failed)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.DANGER_INK'))
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
        .onClick(() => {
          this.paging.retry()
        })
    } else if (this.recipes.length > 0) {
      Text(DiningText.dining_load_all_loaded)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.TEXT_TERTIARY'))
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
    }
  }

  /**
   * 忌口警示 + 人数份量建议条（契约 §三.1/§三.5）：
   * - 忌口警示：选中菜 allergens ∩ 用户忌口，逐条「含忌口：<忌口名>（<菜名>）」红色系；
   * - 人数 stepper（1-20，本地态）+ 份量建议（人均 vs 目标÷3 → 够/偏多/偏少）。
   */
  @Builder
  WarningAndPortionBar() {
    Column() {
      if (this.allergenWarningHits().length > 0) {
        Column({ space: 2 }) {
          Text(DiningText.dining_allergen_warning_title)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.DANGER_INK'))
            .width('100%')
          ForEach(this.allergenWarningHits(), (hit: DiningAllergenHit) => {
            Text(DiningText.allergenWarningItem(DiningText.allergenLabel(hit.allergen), hit.recipeName))
              .fontSize(DesignFontSize.CAPTION)
              .fontColor($r('app.color.DANGER_INK'))
              .width('100%')
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }, (hit: DiningAllergenHit): string => `allergen_${hit.recipeId}_${hit.allergen}`)
        }
        .alignItems(HorizontalAlign.Start)
        .width('100%')
        .backgroundColor($r('app.color.ICON_CONTAINER_PINK'))
        .borderRadius(DesignDimens.RADIUS_MD)
        .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
      }

      Row({ space: DesignDimens.SPACE_SM }) {
        Text(DiningText.dining_portion_people_label)
          .fontSize(DesignFontSize.BODY)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .layoutWeight(1)
        SymbolGlyph($r('sys.symbol.minus'))
          .fontSize(20)
          .fontColor([this.peopleCount > DINING_PEOPLE_MIN ? $r('app.color.TEXT_PRIMARY') : $r('app.color.TEXT_TERTIARY')])
          .width(48)
          .height(48)
          .onClick(() => {
            this.peopleCount = coerceDiningPeopleCount(this.peopleCount - 1)
          })
        Text(`${this.peopleCount}`)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .textAlign(TextAlign.Center)
        SymbolGlyph($r('sys.symbol.plus'))
          .fontSize(20)
          .fontColor([this.peopleCount < DINING_PEOPLE_MAX ? $r('app.color.TEXT_PRIMARY') : $r('app.color.TEXT_TERTIARY')])
          .width(48)
          .height(48)
          .onClick(() => {
            this.peopleCount = coerceDiningPeopleCount(this.peopleCount + 1)
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .padding({ top: DesignDimens.SPACE_XS })

      if (this.portionAdviceText() !== null) {
        Text(this.portionAdviceText()?.text ?? '')
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(this.portionAdviceText()?.verdict === DiningPortionVerdict.ENOUGH
            ? $r('app.color.TEXT_GREEN')
            : $r('app.color.TEXT_SECONDARY'))
          .width('100%')
          .padding({ top: DesignDimens.SPACE_XS })
      }
    }
    .width('100%')
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      bottom: DesignDimens.SPACE_XS
    })
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

  /**
   * 菜品行副行（TD16 对齐安卓定案）：热量前置——大字体/窄屏截断时优先保住热量语义
   * （旧序「N 种食材 · ≈107 …」热量必被截）；热量缺失渲染「热量未知」，无食材只出热量。
   */
  private recipeSubLine(recipe: DiningRecipeVo): string {
    const parts: Array<string> = []
    const calories = diningServingCalories(recipe.caloriesTotal, recipe.caloriesPer100g)
    const calorieText = DiningText.calorieBadge(calories)
    parts.push(calorieText !== null ? calorieText : DiningText.dining_nutrition_calorie_unknown)
    if (recipe.ingredients.length > 0) {
      parts.push(DiningText.ingredientsCount(recipe.ingredients.length))
    }
    return parts.join(' · ')
  }

  /** 菜品行：勾选框 + 菜名（两行上限）+ 热量前置副行 */
  @Builder
  RecipeRow(recipe: DiningRecipeVo) {
    Row({ space: DesignDimens.SPACE_SM }) {
      DiningCheckbox({ checked: this.selectedIds.indexOf(recipe.id) >= 0 })

      Column({ space: 2 }) {
        // 两行上限（TD16）：大字体档单行只剩 3-4 字，两行保住菜名可读性
        Text(recipe.name)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.recipeSubLine(recipe))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .fontFeature(DesignFontFeature.TNUM)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
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

  /**
   * 按当前分类重拉菜谱首屏（重置分页到第 1 页，整体替换数据源）。
   * 触底加载由 PagingController 驱动 fetchRecipePage(page>1)。
   */
  private async reloadRecipes(): Promise<void> {
    this.recipeSeq++
    this.paging.reset()
    this.recipes = []
    this.appendError = false
    this.dataSource.notifyReload()
    await this.fetchRecipePage(1)
  }

  /**
   * 按页拉取菜谱（PagingController 触底追加；第 1 页整体替换）。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  private async fetchRecipePage(page: number): Promise<boolean> {
    const seq = this.recipeSeq
    try {
      const res = await getDiningRecipes(page, DINING_PAGE_SIZE, this.selectedCategoryId, '')
      if (seq !== this.recipeSeq) {
        return false // 筛选已切换，过期响应整体丢弃
      }
      if (res.code !== BizCode.SUCCESS || res.data === null) {
        logger.warn(TAG, `菜谱分页未生效 page=${page} code=${res.code} message=${res.message ?? ''}`)
        if (page === 1) {
          this.isError = true
          this.loadErrorMessage = DiningText.dining_order_create_load_failed
          showToastSafety(res.message, DiningText.dining_order_create_load_failed)
          return false
        }
        this.appendError = true
        // 抛出以便 PagingController 记录失败页，footer 提供重试
        throw new Error(`菜谱第 ${page} 页加载失败`)
      }
      const records = res.data.records
      if (page === 1) {
        this.recipes = records
        this.dataSource.notifyReload()
      } else if (records.length > 0) {
        const startIndex: number = this.recipes.length
        this.recipes = this.recipes.concat(records)
        this.dataSource.notifyAppend(startIndex, records.length)
      }
      this.appendError = false
      logger.debug(TAG, `菜谱分页加载成功 page=${page} count=${records.length} total=${res.data.total}`)
      return records.length > 0
    } catch (err) {
      if (seq !== this.recipeSeq) {
        return false
      }
      logger.error(TAG, `菜谱分页加载异常 page=${page}`, err instanceof Error ? err.message : '')
      if (page === 1) {
        this.isError = true
        this.loadErrorMessage = DiningText.dining_order_create_load_failed
        showToastSafety('', DiningText.dining_order_create_load_failed)
        return false
      }
      this.appendError = true
      throw new Error(err instanceof Error ? err.message : '菜谱加载失败')
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

  /** 勾选/取消（上限内保序追加；上限提示；勾选后按需补齐过敏原） */
  private toggleRecipe(recipeId: number): void {
    const index = this.selectedIds.indexOf(recipeId)
    if (index >= 0) {
      const updated = this.selectedIds.slice()
      updated.splice(index, 1)
      this.selectedIds = updated
      this.selectedRecipes = this.selectedRecipes.filter((recipe: DiningRecipeVo): boolean => recipe.id !== recipeId)
      return
    }
    if (this.selectedIds.length >= 100) {
      showToastSafety(DiningText.dining_order_error_menu_too_many)
      return
    }
    const recipe = this.recipes.find((item: DiningRecipeVo): boolean => item.id === recipeId)
    if (recipe !== undefined) {
      this.selectedRecipes = this.selectedRecipes.concat([recipe])
    }
    this.selectedIds = this.selectedIds.concat([recipeId])
    this.ensureAllergen(recipeId)
  }

  /** 拉取饮食偏好（best-effort：失败按默认目标 + 无忌口降级，不阻断发起点单） */
  private async loadDietaryPreference(): Promise<void> {
    try {
      const res = await getDiningDietaryPreference()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const preference: DietaryPreferenceVo = res.data
        this.userAllergens = preference.allergens === null ? [] : preference.allergens
        this.calorieTarget = preference.calorieTarget !== null && preference.calorieTarget > 0
          ? preference.calorieTarget
          : DINING_DEFAULT_CALORIE_TARGET
        logger.debug(TAG, `饮食偏好加载成功 忌口=${this.userAllergens.length} 热量目标=${this.calorieTarget}`)
      } else {
        logger.warn(TAG, `饮食偏好加载失败 code=${res.code} message=${res.message ?? ''}`)
      }
    } catch (err) {
      logger.error(TAG, '饮食偏好加载异常', err instanceof Error ? err.message : '')
    }
  }

  /** 过敏原缓存读取（未缓存返回 null，不阻塞渲染） */
  private allergenOf(recipeId: number): Array<string> | null {
    const entry = this.allergenCache.find((item: DiningAllergenCacheEntry): boolean => item.recipeId === recipeId)
    return entry !== undefined ? entry.allergens : null
  }

  /** 已选菜过敏原事实（缓存缺失按无过敏原参与，缓存补齐后警示自动出现） */
  private selectedDishes(): Array<DiningSelectedDish> {
    return this.selectedRecipes.map((recipe: DiningRecipeVo): DiningSelectedDish => ({
      recipeId: recipe.id,
      recipeName: recipe.name,
      allergens: this.allergenOf(recipe.id)
    }))
  }

  /** 忌口命中（选中菜 allergens ∩ 用户忌口） */
  private allergenWarningHits(): Array<DiningAllergenHit> {
    return diningAllergenHits(this.selectedDishes(), this.userAllergens)
  }

  /** 按需拉取菜谱详情缓存过敏原（失败静默，下次勾选重试） */
  private async ensureAllergen(recipeId: number): Promise<void> {
    if (recipeId <= 0 || this.allergenOf(recipeId) !== null) {
      return
    }
    if (this.allergenPendingIds.indexOf(recipeId) >= 0) {
      return
    }
    this.allergenPendingIds = this.allergenPendingIds.concat([recipeId])
    try {
      const res = await getDiningRecipeDetail(recipeId)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const allergens = res.data.allergens === null ? [] : res.data.allergens
        this.allergenCache = this.allergenCache.concat([{ recipeId: recipeId, allergens: allergens }])
        logger.debug(TAG, `过敏原缓存 recipeId=${recipeId} count=${allergens.length}`)
      } else {
        logger.warn(TAG, `过敏原加载失败 recipeId=${recipeId} code=${res.code}`)
      }
    } catch (err) {
      logger.error(TAG, `过敏原加载异常 recipeId=${recipeId}`, err instanceof Error ? err.message : '')
    } finally {
      this.allergenPendingIds = this.allergenPendingIds.filter((id: number): boolean => id !== recipeId)
    }
  }

  /** 份量建议文案（总热量未知返回 null，页面不渲染建议） */
  private portionAdviceText(): DiningPortionAdviceText | null {
    const total = diningSelectedTotalCalories(this.selectedRecipes)
    const advice = diningPortionAdvice(total, this.peopleCount, this.calorieTarget)
    if (advice === null) {
      return null
    }
    const perCapitaText = formatDiningAmount(advice.perCapita) ?? ''
    if (advice.verdict === DiningPortionVerdict.ENOUGH) {
      return { text: DiningText.portionAdviceEnough(advice.peopleCount, perCapitaText), verdict: advice.verdict }
    }
    if (advice.verdict === DiningPortionVerdict.MORE) {
      return { text: DiningText.portionAdviceMore(advice.peopleCount, perCapitaText), verdict: advice.verdict }
    }
    return { text: DiningText.portionAdviceLess(advice.peopleCount, perCapitaText), verdict: advice.verdict }
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
