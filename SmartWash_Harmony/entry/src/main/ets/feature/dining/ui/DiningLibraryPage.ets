// 菜谱库页 — 对齐 Android DiningLibraryPage.kt（04 号票）
//
// 分类 chips（全部 + 目录）+ 上架菜谱列表分页（categoryId 精确筛选，新菜谱在前）
// + 右上角「新增」入口。条目点击进详情页。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import {
  DINING_PAGE_SIZE,
  DiningCategoryVo,
  DiningRecipeVo,
  getDiningCategories,
  getDiningRecipes
} from '../../../network/api/DiningApi'
import { DiningText } from '../DiningText'
import {
  AppButton,
  AppButtonVariant,
  EmptyState,
  SkeletonBlock,
  SkeletonList
} from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { PagingController } from '../../../utils/PagingUtil'
import { pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG */
const TAG: string = 'diningLibrary'

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
  // 按压反馈 key
  @Local pressedKey: string = ''
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchPage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )
  private hasShown: boolean = false

  async aboutToAppear(): Promise<void> {
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

            Search({
              value: this.keyword,
              placeholder: DiningText.dining_create_name_hint
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
          } else if (this.list.length === 0) {
            Column() {
              EmptyState({ message: this.selectedCategoryId === 0
                ? DiningText.dining_recipe_list_empty
                : DiningText.dining_recipe_list_empty_filtered })
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            Refresh({ refreshing: $$this.isRefreshing }) {
              List({ space: DesignDimens.CARD_SPACING }) {
                ForEach(this.list, (recipe: DiningRecipeVo) => {
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

  /** 菜谱卡：名称 + 分类名 + 食材数（无封面的轻量行卡，对齐 Android 列表卡信息密度） */
  @Builder
  RecipeCard(recipe: DiningRecipeVo) {
    Row({ space: DesignDimens.SPACE_MD }) {
      if (recipe.coverUrl.length > 0) {
        Image(recipe.coverUrl)
          .width(72)
          .height(72)
          .borderRadius(DesignDimens.RADIUS_MD)
          .objectFit(ImageFit.Cover)
          .draggable(false)
      } else {
        Column() {
          SymbolGlyph($r('sys.symbol.fork_knife'))
            .fontSize(24)
            .fontColor([$r('app.color.TEXT_TERTIARY')])
        }
        .width(72)
        .height(72)
        .borderRadius(DesignDimens.RADIUS_MD)
        .backgroundColor($r('app.color.SURFACE_VARIANT'))
        .justifyContent(FlexAlign.Center)
      }

      Column({ space: 4 }) {
        Text(recipe.name)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        Text(DiningText.ingredientsCount(recipe.ingredients.length))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')
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
    .scale({
      x: this.pressedKey === `card_${recipe.id}` ? 0.97 : 1,
      y: this.pressedKey === `card_${recipe.id}` ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
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
      const res = await getDiningRecipes(page, DINING_PAGE_SIZE, this.selectedCategoryId, this.keyword)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const records = res.data.records
        if (page === 1) {
          this.list = records
        } else {
          this.list = this.list.concat(records)
        }
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
}
