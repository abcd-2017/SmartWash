// 菜谱详情页 — 对齐 Android DiningRecipeDetailPage.kt（04 号票）
//
// 封面（可直链匿名读）+ 菜名 + 分类 + 食材 chips + 步骤分段（空行分段展示）。

import { LengthMetrics } from '@kit.ArkUI'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { DiningRecipeVo, getDiningRecipeDetail } from '../../../network/api/DiningApi'
import { DiningText } from '../DiningText'
import { EmptyState, SkeletonBlock } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { getNumberParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG */
const TAG: string = 'diningRecipeDetail'

@Builder
export function DiningRecipeDetailBuilder() {
  DiningRecipeDetailPage()
}

@ComponentV2
struct DiningRecipeDetailPage {
  private recipeId: number = 0
  @Local recipe: DiningRecipeVo | null = null
  @Local isLoading: boolean = true
  @Local isError: boolean = false

  async aboutToAppear(): Promise<void> {
    this.recipeId = getNumberParam(RouteNames.DINING_RECIPE_DETAIL) ?? 0
    if (this.recipeId <= 0) {
      logger.warn(TAG, '菜谱详情参数缺失 recipeId')
      this.isLoading = false
      this.isError = true
      return
    }
    await this.load()
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

            Text(DiningText.dining_recipe_detail_title)
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
            Column({ space: DesignDimens.CARD_SPACING }) {
              SkeletonBlock().height(180)
              SkeletonBlock().height(32)
              SkeletonBlock().height(120)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_MD })
            .layoutWeight(1)
          } else if (this.isError || this.recipe === null) {
            Column() {
              EmptyState({ message: DiningText.dining_recipe_detail_load_failed })
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            Scroll() {
              Column() {
                // 封面（未上传给占位块）
                if (this.recipe.coverUrl.length > 0) {
                  Image(this.recipe.coverUrl)
                    .width('100%')
                    .aspectRatio(1.6)
                    .borderRadius(DesignDimens.CARD_RADIUS)
                    .objectFit(ImageFit.Cover)
                    .draggable(false)
                } else {
                  Column() {
                    SymbolGlyph($r('sys.symbol.fork_knife'))
                      .fontSize(48)
                      .fontColor([$r('app.color.TEXT_TERTIARY')])
                  }
                  .width('100%')
                  .aspectRatio(1.6)
                  .borderRadius(DesignDimens.CARD_RADIUS)
                  .backgroundColor($r('app.color.SURFACE_VARIANT'))
                  .justifyContent(FlexAlign.Center)
                }

                Text(this.recipe.name)
                  .fontSize(DesignFontSize.CARD_TITLE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .width('100%')
                  .margin({ top: DesignDimens.SPACE_MD })

                if (this.recipe.categoryName.length > 0) {
                  Text(this.recipe.categoryName)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.PRIMARY'))
                    .backgroundColor($r('app.color.PRIMARY_LIGHT'))
                    .borderRadius(DesignDimens.RADIUS_FULL)
                    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: 2, bottom: 2 })
                    .margin({ top: DesignDimens.SPACE_XS })
                }

                // 食材 chips
                Text(DiningText.dining_recipe_detail_ingredients_section)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .width('100%')
                  .margin({ top: DesignDimens.SECTION_SPACING })

                if (this.recipe.ingredients.length === 0) {
                  Text(DiningText.dining_recipe_detail_ingredients_empty)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.TEXT_TERTIARY'))
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_XS })
                } else {
                  Flex({ wrap: FlexWrap.Wrap, space: { main: LengthMetrics.vp(DesignDimens.SPACE_XS), cross: LengthMetrics.vp(DesignDimens.SPACE_XS) } }) {
                    ForEach(this.recipe.ingredients, (ingredient: string) => {
                      Text(ingredient)
                        .fontSize(DesignFontSize.CAPTION)
                        .fontColor($r('app.color.TEXT_PRIMARY'))
                        .backgroundColor($r('app.color.SURFACE_VARIANT'))
                        .borderRadius(DesignDimens.RADIUS_FULL)
                        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: 4, bottom: 4 })
                    }, (ingredient: string) => `ing_${ingredient}`)
                  }
                  .width('100%')
                  .margin({ top: DesignDimens.SPACE_SM })
                }

                // 步骤分段（空行分段；无空行按原文展示）
                Text(DiningText.dining_recipe_detail_steps_section)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .width('100%')
                  .margin({ top: DesignDimens.SECTION_SPACING })

                if (this.recipe.steps.length === 0) {
                  Text(DiningText.dining_recipe_detail_steps_empty)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.TEXT_TERTIARY'))
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_XS })
                } else {
                  ForEach(this.stepParagraphs(), (paragraph: string, index: number) => {
                    Row({ space: DesignDimens.SPACE_SM }) {
                      Text(`${index + 1}`)
                        .fontSize(DesignFontSize.CAPTION)
                        .fontWeight(DesignFontWeight.SEMI_BOLD)
                        .fontColor($r('app.color.ON_PRIMARY'))
                        .backgroundColor($r('app.color.PRIMARY'))
                        .borderRadius(DesignDimens.RADIUS_FULL)
                        .width(22)
                        .height(22)
                        .textAlign(TextAlign.Center)
                      Text(paragraph)
                        .fontSize(DesignFontSize.BODY)
                        .fontColor($r('app.color.TEXT_PRIMARY'))
                        .layoutWeight(1)
                    }
                    .width('100%')
                    .alignItems(VerticalAlign.Top)
                    .margin({ top: DesignDimens.SPACE_SM })
                  }, (paragraph: string, index: number) => `step_${index}`)
                }

                Column() {
                }
                .height(Math.max(safeBottomVp(), DesignDimens.SPACE_XL))
              }
              .width('100%')
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                top: DesignDimens.SPACE_SM
              })
            }
            .scrollBar(BarState.Off)
            .width('100%')
            .height('100%')
            .align(Alignment.TopStart)
            .edgeEffect(EdgeEffect.Spring)
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
  }

  /** 步骤分段（空行分段；无空行退化为原文单段） */
  private stepParagraphs(): Array<string> {
    const raw = this.recipe?.steps ?? ''
    if (raw.length === 0) {
      return new Array<string>()
    }
    const paragraphs: Array<string> = raw.split(/\n\s*\n/).map((part: string): string => part.trim())
      .filter((part: string): boolean => part.length > 0)
    if (paragraphs.length > 0) {
      return paragraphs
    }
    const single: Array<string> = [raw.trim()]
    return single
  }

  /** 拉详情（仅上架可见） */
  private async load(): Promise<void> {
    try {
      const res = await getDiningRecipeDetail(this.recipeId)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.recipe = res.data
        logger.debug(TAG, `菜谱详情加载成功 id=${res.data.id}`)
      } else {
        logger.warn(TAG, `菜谱详情加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        showToastSafety(res.message, DiningText.dining_recipe_detail_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '菜谱详情加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      showToastSafety('', DiningText.dining_recipe_detail_load_failed)
    }
  }
}
