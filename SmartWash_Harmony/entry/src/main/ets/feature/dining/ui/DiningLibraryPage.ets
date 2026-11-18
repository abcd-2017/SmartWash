// 菜谱库页 — 对齐 Android DiningLibraryPage.kt（04 号票）
//
// 分类 chips（全部 + 目录）+ 上架菜谱列表分页（categoryId 精确筛选，新菜谱在前）
// + 右上角「新增」入口。条目点击进详情页。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight, MotionAlpha, MotionScale, MotionTiming } from '../../../constant/DesignSystem'
import { ReduceMotion } from '../../../utils/ReduceMotion'
import { BizCode } from '../../../network/HttpUtil'
import {
  DINING_PAGE_SIZE,
  DiningCategoryVo,
  DiningRecipeVo,
  favoriteDiningRecipe,
  getDiningCategories,
  getDiningRecipes,
  unfavoriteDiningRecipe
} from '../../../network/api/DiningApi'
import { diningServingCalories } from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningCalorieBadge, DiningRecipeThumb } from './DiningUiCommon'
import {
  AppButton,
  AppButtonVariant,
  EmptyState,
  SkeletonBlock,
  SkeletonList
} from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { LazyListDataSource, PagingController } from '../../../utils/PagingUtil'
import { pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

const TAG: string = 'diningLibrary'

/** 搜索模式：按菜名（keyword） */
const SEARCH_MODE_NAME: number = 0
/** 搜索模式：按食材（ingredient 反查） */
const SEARCH_MODE_INGREDIENT: number = 1

@Builder
export function DiningLibraryBuilder() {
  DiningLibraryPage()
}

@ComponentV2
struct DiningLibraryPage {
  @Local categories: Array<DiningCategoryVo> = []
  @Local selectedCategoryId: number = 0
  @Local list: Array<DiningRecipeVo> = []
  @Local isLoadingFirst: boolean = true
  @Local isFirstError: boolean = false
  @Local isRefreshing: boolean = false
  @Local isLoadingMore: boolean = false
  @Local appendError: boolean = false
  @Local keyword: string = ''
  /** 搜索模式：0=菜名（keyword）1=食材（ingredient 反查） */
  @Local searchMode: number = SEARCH_MODE_NAME
  /** 本地收藏态（菜谱 ID 集合；服务端批量填充 + 乐观更新） */
  @Local favoritedIds: Array<number> = []
  /** 收藏写操作在途（防连点重复提交） */
  @Local favoritePendingIds: Array<number> = []
  // 按压反馈 key
  @Local pressedKey: string = ''
  // reduced motion：按压取消缩放改 alpha 0.86（规范 §8）
  @Local reduceMotion: boolean = false
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchPage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )
  /** LazyForEach 数据源（读取 list，实例稳定不可重建，避免整表重建） */
  private dataSource: LazyListDataSource<DiningRecipeVo> =
    new LazyListDataSource<DiningRecipeVo>(() => this.list)
  private hasShown: boolean = false

  async aboutToAppear(): Promise<void> {
    this.reduceMotion = ReduceMotion.isEnabled()
    logger.debug(TAG, '菜谱库挂载，开始首载')
    // 分类目录失败不致命（分类 chips 缺失时列表仍可用「全部」浏览）
    try {
      const catRes = await getDiningCategories()
      if (catRes.code === BizCode.SUCCESS && catRes.data !== null) {
        this.categories = catRes.data
      } else {
        logger.warn(TAG, `分类加载失败 code=${catRes.code} message=${catRes.message ?? ''}`)
      }
    } catch (err) {
      logger.warn(TAG, `分类加载异常 ${err instanceof Error ? err.message : ''}`)
    }
    await this.loadFirst()
    this.isLoadingFirst = false
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

            Text(DiningText.dining_recipe_library_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()

            Text(DiningText.dining_recipe_action_create)
              .fontSize(DesignFontSize.PAGE_HEADER)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.PRIMARY_DARK'))
              .padding({ left: 12, right: 12, top: 12, bottom: 12 })
              .onClick(() => {
                pathStack.pushPathByName(RouteNames.DINING_RECIPE_CREATE, null)
              })
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          // 分类筛选 chips + 关键词搜索
          Column() {
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

            // 搜索模式 chips（菜名 / 食材反查，契约 §2.4/§三.6）
            Row({ space: DesignDimens.SPACE_XS }) {
              this.SearchModeChip(SEARCH_MODE_NAME, DiningText.dining_recipe_search_mode_name)
              this.SearchModeChip(SEARCH_MODE_INGREDIENT, DiningText.dining_recipe_search_mode_ingredient)
            }
            .margin({ top: DesignDimens.SPACE_SM })

            Search({
              value: this.keyword,
              placeholder: DiningText.dining_recipe_search_hint
            })
              .onChange((value: string) => {
                this.keyword = value
              })
              .onSubmit(() => {
                this.reloadFirst()
              })
              .width('100%')
              .height(44)
              .backgroundColor($r('app.color.SURFACE_VARIANT'))
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .placeholderColor($r('app.color.TEXT_TERTIARY'))
              .placeholderFont({ size: DesignFontSize.BODY })
              .textFont({ size: DesignFontSize.BODY })
              .margin({ top: DesignDimens.SPACE_SM })
          }
          .width('100%')
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_XS
          })

          if (this.isLoadingFirst) {
            Column() {
              SkeletonList({ rows: 5 })
            }
            .width('100%')
            .layoutWeight(1)
            // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
            .transition(TransitionEffect.OPACITY)
            .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
          } else if (this.isFirstError && this.list.length === 0) {
            Column() {
              EmptyState({ message: DiningText.dining_recipe_load_failed }) {
                Column() {
                  AppButton({
                    small: true,
                    text: DiningText.dining_action_retry,
                    variant: AppButtonVariant.SECONDARY,
                    click: (): void => {
                      this.reloadFirst()
                    }
                  })
                }
                .width('100%')
                .padding({ top: DesignDimens.SPACE_MD })
              }
            }
            .width('100%')
            .layoutWeight(1)
            // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
            .transition(TransitionEffect.OPACITY)
            .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
          } else if (this.list.length === 0) {
            Column() {
              EmptyState({ message: this.selectedCategoryId === 0
                ? DiningText.dining_recipe_list_empty
                : DiningText.dining_recipe_list_empty_filtered })
            }
            .width('100%')
            .layoutWeight(1)
            // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
            .transition(TransitionEffect.OPACITY)
            .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
          } else {
            Refresh({ refreshing: $$this.isRefreshing }) {
              List({ space: DesignDimens.CARD_SPACING }) {
                LazyForEach(this.dataSource, (recipe: DiningRecipeVo) => {
                  ListItem() {
                    this.RecipeCard(recipe)
                  }
                }, (recipe: DiningRecipeVo) => `recipe_${recipe.id}`)

                ListItem() {
                  if (this.isLoadingMore) {
                    Row() {
                      SkeletonBlock().width(120).height(14)
                    }
                    .width('100%')
                    .justifyContent(FlexAlign.Center)
                    .padding({ top: 12, bottom: 12 })
                  } else if (this.appendError) {
                    Text(DiningText.dining_recipe_load_failed)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor($r('app.color.DANGER_INK'))
                      .width('100%')
                      .textAlign(TextAlign.Center)
                      .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
                      .onClick(() => {
                        this.paging.retry()
                      })
                  } else if (this.list.length > 0) {
                    Text(DiningText.dining_load_all_loaded)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor($r('app.color.TEXT_TERTIARY'))
                      .width('100%')
                      .textAlign(TextAlign.Center)
                      .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
                  }
                }
              }
              .width('100%')
              .height('100%')
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                top: DesignDimens.SPACE_SM,
                bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_XL)
              })
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
              this.reloadFirst()
            })
            .layoutWeight(1)
            // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
            .transition(TransitionEffect.OPACITY)
            .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
          }
        }
        .width('100%')
        .height('100%')
        .padding({ top: safeTopVp(), bottom: safeBottomVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 新增菜谱返回后重拉（首显与 aboutToAppear 去重）
      if (!this.hasShown) {
        this.hasShown = true
        return
      }
      logger.debug(TAG, '菜谱库重回可见，重拉列表')
      this.reloadFirst()
    })
  }

  /** 分类筛选 chip */
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
      this.reloadFirst()
    })
  }

  /** 搜索模式 chip（菜名 / 食材反查） */
  @Builder
  SearchModeChip(mode: number, label: string) {
    Row() {
      Text(label)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(this.searchMode === mode ? $r('app.color.ON_PRIMARY') : $r('app.color.TEXT_SECONDARY'))
    }
    .justifyContent(FlexAlign.Center)
    .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 6, bottom: 6 })
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor(this.searchMode === mode ? $r('app.color.PRIMARY') : $r('app.color.SURFACE'))
    .border({
      width: 1,
      color: this.searchMode === mode ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE')
    })
    .onClick(() => {
      if (this.searchMode === mode) {
        return
      }
      this.searchMode = mode
      this.reloadFirst()
    })
  }

  /** 收藏按钮（48vp 热区；已收藏实心红心 / 未收藏描边灰心；乐观更新） */
  @Builder
  FavoriteButton(recipe: DiningRecipeVo) {
    Column() {
      SymbolGlyph(this.isFavorited(recipe)
        ? $r('sys.symbol.heart_fill')
        : $r('sys.symbol.heart'))
        .fontSize(20)
        .fontColor([this.isFavorited(recipe)
          ? $r('app.color.DANGER')
          : $r('app.color.TEXT_SECONDARY')])
    }
    .width(48)
    .height(48)
    .borderRadius(24)
    .backgroundColor($r('app.color.SURFACE'))
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    // 收藏按压 100ms scale 0.97（对齐安卓 pressable）；reduced 改 alpha 0.86
    .scale({
      x: !this.reduceMotion && this.pressedKey === `fav_${recipe.id}` ? MotionScale.Press : 1,
      y: !this.reduceMotion && this.pressedKey === `fav_${recipe.id}` ? MotionScale.Press : 1
    })
    .opacity(this.reduceMotion && this.pressedKey === `fav_${recipe.id}` ? MotionAlpha.PressReduced : 1)
    .animation(MotionTiming.PRESS)
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `fav_${recipe.id}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.toggleFavorite(recipe)
    })
  }

  /** 菜谱卡：封面缩略（占位键回落首字）+ 名称（两行上限）+ 食材数·份热量徽标 */
  @Builder
  RecipeCard(recipe: DiningRecipeVo) {
    Row({ space: DesignDimens.SPACE_MD }) {
      Stack({ alignContent: Alignment.TopEnd }) {
        // TD18 对齐安卓定案：分类占位键（dining/covers/category_*）视为无封面回落首字
        DiningRecipeThumb({ coverUrl: recipe.coverUrl, name: recipe.name, thumbSize: 72 })
        this.FavoriteButton(recipe)
      }
      .width(72)
      .height(72)

      Column({ space: 4 }) {
        // 两行上限（TD16 对齐安卓定案）：大字体档单行 7 字即截断，行卡纵向有余量
        Text(recipe.name)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        Row({ space: DesignDimens.SPACE_XS }) {
          Text(DiningText.ingredientsCount(recipe.ingredients.length))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .maxLines(1)
          // 份热量徽标（caloriesTotal ?: caloriesPer100g × 250 ÷ 100，皆缺显示「热量未知」）
          DiningCalorieBadge({
            calories: diningServingCalories(recipe.caloriesTotal, recipe.caloriesPer100g)
          })
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(16)
        .fontColor([$r('app.color.TEXT_TERTIARY')])
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .padding(DesignDimens.CARD_PADDING)
    // 整卡按压 100ms FastOutSlowIn scale 0.97（对齐安卓 pressable）；reduced 改 alpha 0.86
    .scale({
      x: !this.reduceMotion && this.pressedKey === `card_${recipe.id}` ? MotionScale.Press : 1,
      y: !this.reduceMotion && this.pressedKey === `card_${recipe.id}` ? MotionScale.Press : 1
    })
    .opacity(this.reduceMotion && this.pressedKey === `card_${recipe.id}` ? MotionAlpha.PressReduced : 1)
    .animation(MotionTiming.PRESS)
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `card_${recipe.id}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      pathStack.pushPathByName(RouteNames.DINING_RECIPE_DETAIL, recipe.id)
    })
  }

  /** 首载/重试/下拉刷新/筛选切换：重置分页拉第 1 页 */
  private async loadFirst(): Promise<void> {
    this.isFirstError = false
    this.paging.reset()
    this.list = []
    await this.fetchPage(1)
  }

  private reloadFirst(): void {
    this.loadFirst()
  }

  /** 按页拉取（PagingController 追加；第 1 页整体替换） */
  private async fetchPage(page: number): Promise<boolean> {
    try {
      const ingredient = this.searchMode === SEARCH_MODE_INGREDIENT ? this.keyword : ''
      const keyword = this.searchMode === SEARCH_MODE_NAME ? this.keyword : ''
      const res = await getDiningRecipes(page, DINING_PAGE_SIZE, this.selectedCategoryId, keyword, ingredient)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const records = res.data.records
        if (page === 1) {
          this.list = records
          this.dataSource.notifyReload()
        } else if (records.length > 0) {
          const startIndex: number = this.list.length
          this.list = this.list.concat(records)
          this.dataSource.notifyAppend(startIndex, records.length)
        }
        this.mergeFavorited(records)
        this.appendError = false
        logger.debug(TAG, `菜谱分页加载成功 page=${page} count=${records.length} total=${res.data.total}`)
        return records.length > 0
      }
      logger.warn(TAG, `菜谱分页加载失败 page=${page} code=${res.code} message=${res.message ?? ''}`)
      if (page === 1) {
        this.isFirstError = true
        showToastSafety(res.message, DiningText.dining_recipe_load_failed)
      } else {
        this.appendError = true
      }
      return false
    } catch (err) {
      logger.error(TAG, `菜谱分页加载异常 page=${page}`, err instanceof Error ? err.message : '')
      if (page === 1) {
        this.isFirstError = true
        showToastSafety('', DiningText.dining_recipe_load_failed)
      } else {
        this.appendError = true
      }
      return false
    }
  }

  /** 是否已收藏（本地态唯一真相；服务端 favorited 经 mergeFavorited 汇入） */
  private isFavorited(recipe: DiningRecipeVo): boolean {
    return this.favoritedIds.indexOf(recipe.id) >= 0
  }

  /** 服务端列表 favorited 汇入本地态（true 加入 / false 移除，保持与服务端一致） */
  private mergeFavorited(records: Array<DiningRecipeVo>): void {
    records.forEach((recipe: DiningRecipeVo): void => {
      const exists = this.favoritedIds.indexOf(recipe.id) >= 0
      if (recipe.favorited === true && !exists) {
        this.favoritedIds = this.favoritedIds.concat([recipe.id])
      } else if (recipe.favorited === false && exists) {
        this.favoritedIds = this.favoritedIds.filter((id: number): boolean => id !== recipe.id)
      }
    })
  }

  private setFavoritedLocal(recipeId: number, favorited: boolean): void {
    const exists = this.favoritedIds.indexOf(recipeId) >= 0
    if (favorited && !exists) {
      this.favoritedIds = this.favoritedIds.concat([recipeId])
    } else if (!favorited && exists) {
      this.favoritedIds = this.favoritedIds.filter((id: number): boolean => id !== recipeId)
    }
  }

  /**
   * 收藏/取消收藏（乐观更新 → 服务端确认；失败回滚本地态并提示）。
   * 幂等接口：POST 已收藏返回 true，DELETE 未收藏返回 false。
   */
  private async toggleFavorite(recipe: DiningRecipeVo): Promise<void> {
    if (this.favoritePendingIds.indexOf(recipe.id) >= 0) {
      return
    }
    const wasFavorited = this.isFavorited(recipe)
    this.favoritePendingIds = this.favoritePendingIds.concat([recipe.id])
    this.setFavoritedLocal(recipe.id, !wasFavorited)
    try {
      const res = wasFavorited
        ? await unfavoriteDiningRecipe(recipe.id)
        : await favoriteDiningRecipe(recipe.id)
      if (res.code === BizCode.SUCCESS) {
        this.setFavoritedLocal(recipe.id, res.data === true)
        logger.debug(TAG, `收藏状态更新 recipeId=${recipe.id} favorited=${res.data === true}`)
      } else {
        this.setFavoritedLocal(recipe.id, wasFavorited)
        logger.warn(TAG, `收藏未生效 recipeId=${recipe.id} code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dining_copy_failed)
      }
    } catch (err) {
      this.setFavoritedLocal(recipe.id, wasFavorited)
      logger.error(TAG, '收藏请求异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_copy_failed)
    } finally {
      this.favoritePendingIds = this.favoritePendingIds.filter((id: number): boolean => id !== recipe.id)
    }
  }
}
