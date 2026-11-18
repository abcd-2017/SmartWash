// 菜谱详情页 — 对齐 Android DiningRecipeDetailPage.kt（04 号票 + TD22/TD24 对齐批次）
//
// 封面（可直链匿名读）+ 菜名 + 分类 + 份热量徽标 + 食材 chips + 步骤渲染（TD24：
// 剥离 markdown「**」与原文自带编号，「-」开头行渲染为缩进浅色子注解）+ 头部收藏
// 入口（TD22：乐观切换 + 服务端失败回滚 + spring 弹跳反馈）。

import { LengthMetrics } from '@kit.ArkUI'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontFeature, DesignFontSize, DesignFontWeight, MotionAlpha, MotionScale, MotionTiming } from '../../../constant/DesignSystem'
import { ReduceMotion } from '../../../utils/ReduceMotion'
import { BizCode } from '../../../network/HttpUtil'
import { DiningRecipeVo, favoriteDiningRecipe, getDiningRecipeDetail, unfavoriteDiningRecipe } from '../../../network/api/DiningApi'
import { DiningStepLine, DiningStepLineKind, diningServingCalories, parseDiningStepLines } from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningCalorieBadge } from './DiningUiCommon'
import { EmptyState, SkeletonBlock } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { getNumberParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

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
  /** 收藏态（详情返回填充；页内乐观切换 + 服务端失败回滚，TD22 对齐安卓定案） */
  @Local favorited: boolean = false
  /** 收藏切换进行中（防连击重复请求） */
  @Local favoriting: boolean = false
  // 收藏入口按压缩放态 + reduced motion 降级
  @Local pressed: boolean = false
  @Local reduceMotion: boolean = false

  async aboutToAppear(): Promise<void> {
    this.reduceMotion = ReduceMotion.isEnabled()
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

            // 收藏入口（TD22 对齐安卓定案）：此前仅列表卡片可收藏，详情页无入口；
            // 已收藏实心红心（dangerInk）+ 恒定放大 1.15（spring 弹跳过渡），未收藏描边灰心
            if (this.recipe !== null) {
              Column() {
                SymbolGlyph(this.favorited ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'))
                  .fontSize(26)
                  .fontColor([this.favorited ? $r('app.color.DANGER_INK') : $r('app.color.TEXT_SECONDARY')])
                  .scale({
                    x: this.favorited ? 1.15 : 1,
                    y: this.favorited ? 1.15 : 1
                  })
                  .animation({ curve: MotionTiming.BOUNCY })
              }
              .width(DesignDimens.BACK_HIT_AREA)
              .height(DesignDimens.BACK_HIT_AREA)
              .justifyContent(FlexAlign.Center)
              .alignItems(HorizontalAlign.Center)
              .accessibilityText(this.favorited ? DiningText.dining_recipe_unfavorite : DiningText.dining_recipe_favorite)
              // 收藏入口按压 100ms scale 0.97（对齐安卓 pressable）；reduced 改 alpha 0.86
              .scale({
                x: !this.reduceMotion && this.pressed ? MotionScale.Press : 1,
                y: !this.reduceMotion && this.pressed ? MotionScale.Press : 1
              })
              .opacity(this.reduceMotion && this.pressed ? MotionAlpha.PressReduced : 1)
              .animation(MotionTiming.PRESS)
              .onTouch((event: TouchEvent) => {
                if (event.type === TouchType.Down) {
                  this.pressed = true
                } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                  this.pressed = false
                }
              })
              .onClick(() => {
                this.toggleFavorite()
              })
            }
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
            // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
            .transition(TransitionEffect.OPACITY)
            .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
          } else if (this.isError || this.recipe === null) {
            Column() {
              EmptyState({ message: DiningText.dining_recipe_detail_load_failed })
            }
            .width('100%')
            .layoutWeight(1)
            // 内容三态交叉淡变 300ms LinearOutSlowIn（对齐安卓 Crossfade(300)），reduced 降 180ms
            .transition(TransitionEffect.OPACITY)
            .animation(ReduceMotion.spec(MotionTiming.ENTER_CARD))
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

                // 份热量徽标（caloriesTotal ?: caloriesPer100g × 250 ÷ 100，皆缺显示「热量未知」；
                // 估算口径展示，不做超标提醒——后续批次）
                DiningCalorieBadge({
                  calories: diningServingCalories(this.recipe.caloriesTotal, this.recipe.caloriesPer100g)
                })
                  .margin({ top: DesignDimens.SPACE_XS })

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
                  // 步骤渲染（TD24 对齐安卓定案）：主步骤自动编号（"N." 主色 + tnum），子注解
                  // （「-」开头原文行）缩进浅色不编号；源文本已剥离「**」加粗与原文自带编号
                  ForEach(this.stepRenderLines(), (line: DiningStepRenderLine, index: number) => {
                    if (line.kind === DiningStepLineKind.MAIN) {
                      Row({ space: DesignDimens.SPACE_XS }) {
                        Text(`${line.no}.`)
                          .fontSize(DesignFontSize.BODY)
                          .fontColor($r('app.color.PRIMARY_DARK'))
                          .fontFeature(DesignFontFeature.TNUM)
                        Text(line.text)
                          .fontSize(DesignFontSize.BODY)
                          .fontColor($r('app.color.TEXT_PRIMARY'))
                          .layoutWeight(1)
                      }
                      .width('100%')
                      .alignItems(VerticalAlign.Top)
                      .margin({ top: index === 0 ? 0 : DesignDimens.SPACE_SM })
                    } else {
                      Row() {
                        Text(line.text)
                          .fontSize(DesignFontSize.BODY_SMALL)
                          .fontColor($r('app.color.TEXT_SECONDARY'))
                          .layoutWeight(1)
                      }
                      .width('100%')
                      .padding({ left: DesignDimens.SPACE_LG })
                      .margin({ top: index === 0 ? 0 : DesignDimens.SPACE_SM })
                    }
                  }, (line: DiningStepRenderLine, index: number) => `step_${index}_${line.kind}`)
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
  }

  /** 步骤渲染行（含主步骤已解析序号；子注解 no=0 不消费） */
  private stepRenderLines(): Array<DiningStepRenderLine> {
    const parsed = parseDiningStepLines(this.recipe?.steps ?? '')
    const result: Array<DiningStepRenderLine> = []
    let mainNo = 0
    for (let i = 0; i < parsed.length; i++) {
      const line = parsed[i]
      if (line.kind === DiningStepLineKind.MAIN) {
        mainNo++
        result.push({ kind: line.kind, text: line.text, no: mainNo })
      } else {
        result.push({ kind: line.kind, text: line.text, no: 0 })
      }
    }
    return result
  }

  /** 拉详情（仅上架可见） */
  private async load(): Promise<void> {
    try {
      const res = await getDiningRecipeDetail(this.recipeId)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.recipe = res.data
        this.favorited = res.data.favorited === true
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

  /**
   * 收藏切换（TD22 对齐安卓定案）：乐观更新本地态，服务端失败回滚；在途忽略防连击。
   * 幂等接口：POST 已收藏返回 true，DELETE 未收藏返回 false。
   */
  private async toggleFavorite(): Promise<void> {
    const recipe = this.recipe
    if (this.favoriting || recipe === null || recipe.id <= 0) {
      return
    }
    const wasFavorited = this.favorited
    this.favoriting = true
    this.favorited = !wasFavorited
    try {
      const res = wasFavorited
        ? await unfavoriteDiningRecipe(recipe.id)
        : await favoriteDiningRecipe(recipe.id)
      if (res.code === BizCode.SUCCESS) {
        this.favorited = res.data === true
        logger.debug(TAG, `收藏状态更新 recipeId=${recipe.id} favorited=${res.data === true}`)
      } else {
        this.favorited = wasFavorited
        logger.warn(TAG, `收藏未生效 recipeId=${recipe.id} code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dining_recipe_favorite_failed)
      }
    } catch (err) {
      this.favorited = wasFavorited
      logger.error(TAG, '收藏请求异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_recipe_favorite_failed)
    } finally {
      this.favoriting = false
    }
  }
}

/** 步骤渲染行模型（主步骤携带渲染序号） */
interface DiningStepRenderLine {
  kind: DiningStepLineKind
  text: string
  /** 主步骤渲染序号（1 起）；子注解恒 0 */
  no: number
}
