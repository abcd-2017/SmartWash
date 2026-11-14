// 购买清单结果页 — 对齐 Android DiningShoppingListPage.kt（11 号票）
//
// 「按食材买菜 / 按菜备菜」双视图（AppTabBar 切换）：数据源为订单详情 shopping_list 快照
// JSON（结束点单时后端聚合写入，本页不重复聚合）；选菜人标识经参与者脱敏手机号回填，
// 当前用户标记「我」。FINISHED 订单从列表/点单页进入；快照缺失/未聚合给空态。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { DiningOrderDetailVo, getDiningOrderDetail } from '../../../network/api/DiningApi'
import { getUserInfo } from '../../../network/api/UserApi'
import {
  DiningRecipeShopping,
  DiningSelectorShopping,
  DiningShoppingResult,
  diningOrderStatusFromCode,
  diningTimeLabel,
  parseDiningShoppingList,
  resolveMyUserId,
  toDiningShoppingResult
} from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningStatusTag } from './DiningUiCommon'
import { AppTabBar, EmptyState, SkeletonBlock } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { getNumberParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG */
const TAG: string = 'diningShoppingList'

/** 双视图页签下标 */
const TAB_INGREDIENTS: number = 0
const TAB_PER_RECIPE: number = 1

@Builder
export function DiningShoppingListBuilder() {
  DiningShoppingListPage()
}

@ComponentV2
struct DiningShoppingListPage {
  private orderId: number = 0
  /** 当前用户 ID（参与者脱敏比对；0 = 未识别） */
  private myUserId: number = 0

  @Local detail: DiningOrderDetailVo | null = null
  @Local result: DiningShoppingResult | null = null
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  @Local activeTab: number = TAB_INGREDIENTS

  async aboutToAppear(): Promise<void> {
    this.orderId = getNumberParam(RouteNames.DINING_SHOPPING_LIST) ?? 0
    if (this.orderId <= 0) {
      logger.warn(TAG, '购买清单参数缺失 orderId')
      this.isLoading = false
      this.isError = true
      return
    }
    await this.loadDetail()
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

            Text(DiningText.dining_shopping_list_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .layoutWeight(1)
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoading) {
            Column({ space: DesignDimens.CARD_SPACING }) {
              SkeletonBlock().height(48)
              SkeletonBlock().height(96)
              SkeletonBlock().height(96)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_MD })
            .layoutWeight(1)
          } else if (this.isError || this.detail === null) {
            Column() {
              EmptyState({ message: DiningText.dining_shopping_list_load_failed })
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            Column() {
              // 标题行（订单标题 + 状态 + 结束时间）
              Row({ space: DesignDimens.SPACE_SM }) {
                Text(this.detail.title.length > 0 ? this.detail.title : DiningText.dining_order_join_unknown_title)
                  .fontSize(DesignFontSize.CARD_TITLE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                  .layoutWeight(1)
                DiningStatusTag({ status: diningOrderStatusFromCode(this.detail.status) })
              }
              .width('100%')
              .alignItems(VerticalAlign.Center)
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS })

              if (this.detail.finishedAt.length > 0) {
                Text(`${diningTimeLabel(this.detail.finishedAt)} 定稿`)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_SECONDARY'))
                  .width('100%')
                  .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 2 })
              }

              AppTabBar({
                tabs: [DiningText.dining_shopping_list_tab_ingredients, DiningText.dining_shopping_list_tab_per_recipe],
                selectedIndex: this.activeTab,
                tabSelected: (index: number): void => {
                  this.activeTab = index
                }
              })
                .margin({ top: DesignDimens.SPACE_XS })

              if (this.activeTab === TAB_INGREDIENTS) {
                this.IngredientsView()
              } else {
                this.PerRecipeView()
              }
            }
            .width('100%')
            .height('100%')
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

  /** 按食材买菜视图（食材名并集去重，保序编号列表） */
  @Builder
  IngredientsView() {
    if (this.result === null || this.result.ingredients.length === 0) {
      Column() {
        EmptyState({ message: DiningText.dining_shopping_list_empty_ingredients })
      }
      .width('100%')
      .layoutWeight(1)
    } else {
      List({ space: 0 }) {
        ForEach(this.result.ingredients, (ingredient: string, index: number) => {
          ListItem() {
            Row({ space: DesignDimens.SPACE_MD }) {
              Text(`${index + 1}`)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.PRIMARY'))
                .width(24)
                .textAlign(TextAlign.Center)
              Text(ingredient)
                .fontSize(DesignFontSize.BODY)
                .fontColor($r('app.color.TEXT_PRIMARY'))
                .layoutWeight(1)
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_MD,
              bottom: DesignDimens.SPACE_MD
            })
          }
        }, (ingredient: string) => `ing_${ingredient}`)
        ListItem() {
          Column() {
          }
          .height(Math.max(safeBottomVp(), DesignDimens.SPACE_XL))
        }
      }
      .width('100%')
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      .edgeEffect(EdgeEffect.Spring)
      .divider({ strokeWidth: 0.5, color: $r('app.color.HAIRLINE'), startMargin: DesignDimens.PAGE_PADDING, endMargin: DesignDimens.PAGE_PADDING })
    }
  }

  /** 按菜备菜视图（每道菜卡：菜名 + 选菜人 + 备注；选菜人脱敏手机号，当前用户标「我」） */
  @Builder
  PerRecipeView() {
    if (this.result === null || this.result.perRecipe.length === 0) {
      Column() {
        EmptyState({ message: DiningText.dining_shopping_list_empty_per_recipe })
      }
      .width('100%')
      .layoutWeight(1)
    } else {
      List({ space: DesignDimens.CARD_SPACING }) {
        ForEach(this.result.perRecipe, (recipe: DiningRecipeShopping) => {
          ListItem() {
            Column({ space: DesignDimens.SPACE_XS }) {
              Text(recipe.recipeName)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.TEXT_PRIMARY'))
                .width('100%')

              if (recipe.selectors.length === 0) {
                Text(DiningText.dining_selected_none)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_TERTIARY'))
                  .width('100%')
              } else {
                ForEach(recipe.selectors, (selector: DiningSelectorShopping) => {
                  Row({ space: DesignDimens.SPACE_XS }) {
                    Text(selector.isMe ? '我' : selector.label)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(selector.isMe ? $r('app.color.PRIMARY') : $r('app.color.TEXT_SECONDARY'))
                      .fontWeight(selector.isMe ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
                    if (selector.note.length > 0) {
                      Text(DiningText.selectorNote(selector.note))
                        .fontSize(DesignFontSize.CAPTION)
                        .fontColor($r('app.color.TEXT_TERTIARY'))
                        .maxLines(2)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                        .layoutWeight(1)
                    }
                  }
                  .width('100%')
                  .alignItems(VerticalAlign.Center)
                  .padding({ top: 2, bottom: 2 })
                }, (selector: DiningSelectorShopping) => `sel_${selector.label}_${selector.note}`)
              }
            }
            .alignItems(HorizontalAlign.Start)
            .width('100%')
            .backgroundColor($r('app.color.SURFACE'))
            .borderRadius(DesignDimens.CARD_RADIUS)
            .borderWidth(1)
            .borderColor($r('app.color.OUTLINE'))
            .padding(DesignDimens.CARD_PADDING)
          }
        }, (recipe: DiningRecipeShopping) => `recipe_${recipe.recipeName}_${recipe.selectors.length}`)
        ListItem() {
          Column() {
          }
          .height(Math.max(safeBottomVp(), DesignDimens.SPACE_XL))
        }
      }
      .width('100%')
      .layoutWeight(1)
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_MD })
      .scrollBar(BarState.Off)
      .edgeEffect(EdgeEffect.Spring)
    }
  }

  /** 拉详情并解析快照（快照 JSON 文本 → 双视图模型） */
  private async loadDetail(): Promise<void> {
    try {
      const res = await getDiningOrderDetail(this.orderId, null)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.detail = res.data
        // 我的定位（参与者脱敏手机号比对）
        const ids: Array<number> = []
        const phones: Array<string> = []
        res.data.members.forEach((member): void => {
          ids.push(member.userId)
          phones.push(member.phoneMasked)
        })
        // 当前手机号 best-effort（getUserInfo；失败降级不标记「我」）
        let currentPhone = ''
        try {
          const userRes = await getUserInfo()
          if (userRes.code === BizCode.SUCCESS && userRes.data !== null && userRes.data.phoneNumber !== null) {
            currentPhone = userRes.data.phoneNumber
          }
        } catch (err) {
          logger.debug(TAG, '当前用户信息获取失败，「我」标记降级')
        }
        this.myUserId = resolveMyUserId(ids, phones, currentPhone)
        const snapshot = parseDiningShoppingList(res.data.shoppingList)
        this.result = toDiningShoppingResult(snapshot, ids, phones, this.myUserId)
        logger.debug(TAG, `购买清单解析成功 食材=${snapshot.ingredientNames.length} 菜=${snapshot.perRecipe.length}`)
      } else {
        logger.warn(TAG, `购买清单详情加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        showToastSafety(res.message, DiningText.dining_shopping_list_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '购买清单加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      showToastSafety('', DiningText.dining_shopping_list_load_failed)
    }
  }
}
