// 购买清单结果页 — 对齐 Android DiningShoppingListPage.kt（11 号票）
//
// 「按食材买菜 / 按菜备菜」双视图（AppTabBar 切换）：数据源为订单详情 shopping_list 快照
// JSON（结束点单时后端聚合写入，本页不重复聚合）；选菜人标识经参与者脱敏手机号回填，
// 当前用户标记「我」。FINISHED 订单从列表/点单页进入；快照缺失/未聚合给空态。

import { pasteboard } from '@kit.BasicServicesKit'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { DiningOrderDetailVo, getDiningOrderDetail } from '../../../network/api/DiningApi'
import { getUserInfo } from '../../../network/api/UserApi'
import {
  DiningIngredientTotalUi,
  DiningRecipeShopping,
  DiningSelectorShopping,
  DiningShoppingResult,
  buildShoppingListText,
  diningOrderStatusFromCode,
  diningTimeLabel,
  formatDiningAmount,
  parseDiningShoppingList,
  resolveMyUserId,
  toDiningShoppingResult
} from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningCheckbox, DiningStatusTag } from './DiningUiCommon'
import { AppTabBar, EmptyState, SkeletonBlock } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { getNumberParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { getValue, setValue } from '../../../utils/StorageUtil'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

const TAG: string = 'diningShoppingList'

/** 双视图页签下标 */
const TAB_INGREDIENTS: number = 0
const TAB_PER_RECIPE: number = 1

/** 按食材行渲染模型（快照 v2 聚合行 / v1 纯名行统一承载，勾选态按名对齐） */
interface DiningIngredientRow {
  name: string
  /** 数量文本（如 1130g）；null = v1 行或来源未给数量 */
  amountText: string | null
  /** 该配料出现的菜数（0 = 隐藏「N 道菜」徽标） */
  dishCount: number
}

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
  /** 已买食材（本地态，按订单持久化）：买菜时勾选，重启仍在；仅影响展示与导出文本 */
  @Local checked: string[] = []

  async aboutToAppear(): Promise<void> {
    this.orderId = getNumberParam(RouteNames.DINING_SHOPPING_LIST) ?? 0
    if (this.orderId <= 0) {
      logger.warn(TAG, '购买清单参数缺失 orderId')
      this.isLoading = false
      this.isError = true
      return
    }
    await this.loadDetail()
    await this.loadChecked()
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

            Text(DiningText.dining_shopping_list_copy)
              .fontSize(DesignFontSize.BUTTON_TEXT)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_GREEN'))
              .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 6, bottom: 6 })
              .onClick(() => {
                this.copyList()
              })
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

              // 热量汇总卡（快照 v2 存在性降级：仅携带 v2 字段的快照渲染，老快照整卡隐藏）
              if (this.hasCalorieSummary()) {
                this.CalorieSummaryCard()
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

  /**
   * 热量汇总卡（契约 §3.3 快照 v2 / §5.4）：总热量大字 + kcal 单位 + 未知数提示行。
   * 估算口径（250g/份假设，见 DiningLogic.DINING_PORTION_GRAMS），不做超标提醒与人均
   * （Android §5.4 先行，鸿蒙后续批次补齐）。
   */
  @Builder
  CalorieSummaryCard() {
    Column({ space: DesignDimens.SPACE_XS }) {
      Text(DiningText.dining_shopping_list_calorie_card_title)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.TEXT_SECONDARY'))
      Row({ space: DesignDimens.SPACE_XS }) {
        Text(this.totalCaloriesText())
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
        Text(DiningText.dining_unit_kcal)
          .fontSize(DesignFontSize.BODY)
          .fontColor($r('app.color.TEXT_SECONDARY'))
      }
      .alignItems(VerticalAlign.Bottom)
      if (this.calorieUnknownCount() > 0) {
        Text(DiningText.calorieUnknownHint(this.calorieUnknownCount()))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.ONGOING_INK'))
      }
    }
    .alignItems(HorizontalAlign.Start)
    .width('100%')
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .padding(DesignDimens.CARD_PADDING)
    .margin({ top: DesignDimens.SPACE_SM })
  }

  /** 按食材买菜视图（食材名并集去重，保序）：行首自绘勾选框标记「已买」，买菜时对着勾掉 */
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
        ListItem() {
          Text(DiningText.shoppingCheckedProgress(this.checkedCountOf(), this.result.ingredients.length))
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .width('100%')
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_SM,
              bottom: DesignDimens.SPACE_XS
            })
        }
        ForEach(this.ingredientRows(), (row: DiningIngredientRow) => {
          ListItem() {
            Row({ space: DesignDimens.SPACE_MD }) {
              DiningCheckbox({ checked: this.checked.indexOf(row.name) >= 0, boxSize: 22 })
              Text(row.name)
                .fontSize(DesignFontSize.BODY)
                .fontColor(this.checked.indexOf(row.name) >= 0
                  ? $r('app.color.TEXT_TERTIARY')
                  : $r('app.color.TEXT_PRIMARY'))
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .layoutWeight(1)
              if (row.amountText !== null || row.dishCount > 0) {
                Text(this.amountMetaText(row))
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_SECONDARY'))
              }
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_MD,
              bottom: DesignDimens.SPACE_MD
            })
            .onClick(() => {
              this.toggleChecked(row.name)
            })
          }
        }, (row: DiningIngredientRow) => `ing_${row.name}`)
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

  /** 已买食材数（与当前清单交集，避免陈旧项撑大进度） */
  private checkedCountOf(): number {
    const result = this.result
    if (result === null) {
      return 0
    }
    let count = 0
    result.ingredients.forEach((ingredient: string): void => {
      if (this.checked.indexOf(ingredient) >= 0) {
        count++
      }
    })
    return count
  }

  /** 是否携带 v2 热量/聚合数据（老快照 v2 字段为 null → 隐藏热量卡，按 v1 降级渲染） */
  private hasCalorieSummary(): boolean {
    const result = this.result
    if (result === null) {
      return false
    }
    return result.ingredientTotals !== null || result.totalCalories !== null
      || result.calorieUnknownDishCount !== null
  }

  /** 热量未知菜数（v1 快照/未给返回 0，不渲染提示行） */
  private calorieUnknownCount(): number {
    const result = this.result
    return result !== null && result.calorieUnknownDishCount !== null ? result.calorieUnknownDishCount : 0
  }

  /** 总热量大字值（已知 → 「≈1830」；未知/v1 快照 → 「热量未知」） */
  private totalCaloriesText(): string {
    const result = this.result
    const formatted = result !== null ? formatDiningAmount(result.totalCalories) : null
    if (formatted === null) {
      return DiningText.dining_shopping_list_total_calories_unknown
    }
    return DiningText.totalCaloriesValue(formatted)
  }

  /**
   * 按食材行模型（契约 §3.3 v1 优先回落）：v2 聚合行（名称 + 总量单位 + 菜数）优先；
   * 老快照 ingredientTotals 为 null → 回落 ingredientNames 逐名渲染。
   */
  private ingredientRows(): Array<DiningIngredientRow> {
    const result = this.result
    if (result === null) {
      return []
    }
    if (result.ingredientTotals !== null) {
      return result.ingredientTotals.map((total: DiningIngredientTotalUi): DiningIngredientRow => {
        const amount = formatDiningAmount(total.totalValue)
        const row: DiningIngredientRow = {
          name: total.name,
          amountText: amount !== null ? `${amount}${total.unit}` : null,
          dishCount: total.dishCount
        }
        return row
      })
    }
    return result.ingredients.map((name: string): DiningIngredientRow => {
      const row: DiningIngredientRow = { name: name, amountText: null, dishCount: 0 }
      return row
    })
  }

  /** 聚合行尾部说明（数量 · N 道菜；数量缺失只出菜数） */
  private amountMetaText(row: DiningIngredientRow): string {
    const amount = row.amountText !== null ? row.amountText : ''
    if (row.dishCount > 0) {
      return `${amount} · ${DiningText.ingredientDishCount(row.dishCount)}`
    }
    return amount
  }

  /** 已买态存储键（按订单隔离） */
  private checkedKey(): string {
    return `dining_shopping_checked_${this.orderId}`
  }

  /** 读取本单已买食材（换行分隔；缺失/读取失败降级空集，不崩溃） */
  private async loadChecked(): Promise<void> {
    try {
      const raw = await getValue(this.checkedKey(), '')
      if (typeof raw === 'string' && raw.length > 0) {
        this.checked = raw.split('\n')
      }
    } catch (err) {
      logger.debug(TAG, '已买态读取失败，降级空集')
    }
  }

  /** 切换「已买」并落盘（写失败仅不落盘，不阻断勾选交互） */
  private async toggleChecked(ingredient: string): Promise<void> {
    const next: string[] = []
    let found = false
    this.checked.forEach((item: string): void => {
      if (item === ingredient) {
        found = true
      } else {
        next.push(item)
      }
    })
    if (!found) {
      next.push(ingredient)
    }
    this.checked = next
    try {
      await setValue(this.checkedKey(), next.join('\n'))
    } catch (err) {
      logger.debug(TAG, '已买态保存失败')
    }
  }

  /** 复制清单：勾选态 + 按菜视图拼文本，发给家人/自己对着买 */
  private copyList(): void {
    const result = this.result
    if (result === null) {
      return
    }
    const detail = this.detail
    const title = detail !== null ? detail.title : ''
    const text = buildShoppingListText(title, result, this.checked)
    try {
      const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text)
      pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
        showToastSafety(DiningText.dining_shopping_list_copy_done)
      }).catch(() => {
        showToastSafety(DiningText.dining_copy_failed)
      })
    } catch (err) {
      showToastSafety(DiningText.dining_copy_failed)
    }
  }
}
