// 我的收藏页 — 对齐 Android DiningRecipeFavoritesPage.kt（契约 §三.4）
//
// 最小落地形态：分页列表 + 返回 + 卡片取消收藏（数据走 favorites 分页接口，favorited 恒 true）。
// 复用菜谱库列表结构（DINING_PAGE_SIZE 分页 + LazyForEach），不做搜索/分类筛选（交互从简）。
// 入口：订单列表页页头「我的收藏」。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  DINING_PAGE_SIZE,
  DiningRecipeVo,
  getDiningFavoriteRecipes,
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

const TAG: string = 'diningFavoriteList'

@Builder
export function DiningFavoriteListBuilder() {
  DiningFavoriteListPage()
}

@ComponentV2
struct DiningFavoriteListPage {
  @Local list: Array<DiningRecipeVo> = []
  @Local isLoadingFirst: boolean = true
  @Local isFirstError: boolean = false
  @Local isRefreshing: boolean = false
  @Local isLoadingMore: boolean = false
  @Local appendError: boolean = false
  @Local pendingIds: Array<number> = []
  @Local pressedKey: string = ''
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchPage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )
  private dataSource: LazyListDataSource<DiningRecipeVo> =
    new LazyListDataSource<DiningRecipeVo>(() => this.list)
  private hasShown: boolean = false

  async aboutToAppear(): Promise<void> {
    logger.debug(TAG, '我的收藏挂载，开始首载')
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

            Text(DiningText.dining_recipe_favorites_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoadingFirst) {
            Column() {
              SkeletonList({ rows: 5 })
            }
            .width('100%')
            .layoutWeight(1)
          } else if (this.isFirstError && this.list.length === 0) {
            Column() {
              EmptyState({ message: DiningText.dining_recipe_favorites_load_failed }) {
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
          } else if (this.list.length === 0) {
            Column() {
              EmptyState({ message: DiningText.dining_recipe_favorites_empty })
            }
            .width('100%')
            .layoutWeight(1)
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
                    Text(DiningText.dining_recipe_favorites_load_failed)
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
      if (!this.hasShown) {
        this.hasShown = true
        return
      }
      logger.debug(TAG, '收藏页重回可见，重拉列表')
      this.reloadFirst()
    })
  }

  /** 收藏卡：封面缩略（占位键回落首字）+ 名称（两行上限）+ 食材数·份热量 + 取消收藏 */
  @Builder
  RecipeCard(recipe: DiningRecipeVo) {
    Row({ space: DesignDimens.SPACE_MD }) {
      Stack({ alignContent: Alignment.TopEnd }) {
        // TD18 对齐安卓定案：分类占位键（dining/covers/category_*）视为无封面回落首字
        DiningRecipeThumb({ coverUrl: recipe.coverUrl, name: recipe.name, thumbSize: 72 })
        Column() {
          SymbolGlyph($r('sys.symbol.heart_fill'))
            .fontSize(20)
            .fontColor([$r('app.color.DANGER')])
        }
        .width(48)
        .height(48)
        .borderRadius(24)
        .backgroundColor($r('app.color.SURFACE'))
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .onClick(() => {
          this.toggleFavorite(recipe)
        })
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
    .onClick(() => {
      pathStack.pushPathByName(RouteNames.DINING_RECIPE_DETAIL, recipe.id)
    })
  }

  private async loadFirst(): Promise<void> {
    this.isFirstError = false
    this.paging.reset()
    this.list = []
    await this.fetchPage(1)
  }

  private reloadFirst(): void {
    this.loadFirst()
  }

  private async fetchPage(page: number): Promise<boolean> {
    try {
      const res = await getDiningFavoriteRecipes(page, DINING_PAGE_SIZE)
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
        this.appendError = false
        logger.debug(TAG, `收藏分页加载成功 page=${page} count=${records.length} total=${res.data.total}`)
        return records.length > 0
      }
      logger.warn(TAG, `收藏分页加载失败 page=${page} code=${res.code} message=${res.message ?? ''}`)
      if (page === 1) {
        this.isFirstError = true
        showToastSafety(res.message, DiningText.dining_recipe_favorites_load_failed)
      } else {
        this.appendError = true
      }
      return false
    } catch (err) {
      logger.error(TAG, `收藏分页加载异常 page=${page}`, err instanceof Error ? err.message : '')
      if (page === 1) {
        this.isFirstError = true
        showToastSafety('', DiningText.dining_recipe_favorites_load_failed)
      } else {
        this.appendError = true
      }
      return false
    }
  }

  /** 取消收藏（乐观移除 → DELETE；失败重拉恢复） */
  private async toggleFavorite(recipe: DiningRecipeVo): Promise<void> {
    if (this.pendingIds.indexOf(recipe.id) >= 0) {
      return
    }
    this.pendingIds = this.pendingIds.concat([recipe.id])
    const snapshot = this.list
    this.list = this.list.filter((item: DiningRecipeVo): boolean => item.id !== recipe.id)
    this.dataSource.notifyReload()
    try {
      const res = await unfavoriteDiningRecipe(recipe.id)
      if (res.code !== BizCode.SUCCESS) {
        logger.warn(TAG, `取消收藏未生效 recipeId=${recipe.id} code=${res.code}`)
        this.list = snapshot
        this.dataSource.notifyReload()
        showToastSafety(res.message, DiningText.dining_copy_failed)
      }
    } catch (err) {
      logger.error(TAG, '取消收藏异常', err instanceof Error ? err.message : '')
      this.list = snapshot
      this.dataSource.notifyReload()
      showToastSafety('', DiningText.dining_copy_failed)
    } finally {
      this.pendingIds = this.pendingIds.filter((id: number): boolean => id !== recipe.id)
    }
  }
}
