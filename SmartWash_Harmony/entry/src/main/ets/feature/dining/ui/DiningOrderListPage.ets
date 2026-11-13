// 吃啥订单列表页（域首页）— 对齐 Android DiningOrderListPage.kt
//
// 「我发起的 / 我参与的」分区（isCreator 派生）+ 订单卡（标题/状态/时间/卡内「再来一单」）
// + FAB 发起点单 + 右上角菜谱库入口。深链 smartwash://dining 落点、工作台入口 target 同款。
// 卡片点击：ONGOING → 点单页；FINISHED → 购买清单结果页。
// 返回本页（离栈再回）经 onShown 重拉（对齐 Android 列表返回不刷新遗留的修法）。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { DiningOrderListVo, getMyDiningOrders } from '../../../network/api/DiningApi'
import { diningOrderStatusFromCode, DiningOrderStatus, diningTimeLabel } from '../DiningLogic'
import { DiningOrderCreateParam } from '../api'
import { DiningText } from '../DiningText'
import { DiningStatusTag } from './DiningUiCommon'
import { AppButton, AppButtonVariant, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'diningOrderList'

/** FAB 尺寸（vp）：对齐 Android dining FAB 56dp 圆形自绘 */
const FAB_SIZE: number = 56

@Builder
export function DiningOrderListBuilder() {
  DiningOrderListPage()
}

@ComponentV2
struct DiningOrderListPage {
  /** 我发起的订单（创建时间倒序，接口原序） */
  @Local createdOrders: Array<DiningOrderListVo> = []
  /** 我参与的订单 */
  @Local joinedOrders: Array<DiningOrderListVo> = []
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  // 按压反馈 key（对齐项目口径：onTouch Down/Up + scale 0.97）
  @Local pressedKey: string = ''
  private hasShown: boolean = false

  aboutToAppear(): void {
    logger.debug(TAG, '订单列表页挂载，开始首载')
    this.load()
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
        Stack({ alignContent: Alignment.BottomEnd }) {
          Column() {
            // 页头：返回 + 标题 + 右上角「菜谱库」（对齐 ToolboxShortCodeList 页头画法）
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

              Text(DiningText.dining_title)
                .fontSize(DesignFontSize.ROOT_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.TEXT_PRIMARY'))

              Blank()

              Text(DiningText.dining_order_action_recipe_library)
                .fontSize(DesignFontSize.PAGE_HEADER)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor($r('app.color.PRIMARY_DARK'))
                .padding({ left: 12, right: 12, top: 12, bottom: 12 })
                .onClick(() => {
                  pathStack.pushPathByName(RouteNames.DINING_LIBRARY, null)
                })
            }
            .width('100%')
            .height(56)
            .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
            .alignItems(VerticalAlign.Center)

            // 主体
            if (this.isLoading) {
              Column({ space: DesignDimens.SPACE_MD }) {
                SkeletonList({ rows: 3 })
                SkeletonList({ rows: 2 })
              }
              .width('100%')
              .layoutWeight(1)
            } else if (this.isError) {
              Column() {
                EmptyState({ message: DiningText.dining_order_list_load_failed }) {
                  Column() {
                    AppButton({
                      small: true,
                      text: DiningText.dining_action_retry,
                      variant: AppButtonVariant.SECONDARY,
                      click: () => {
                        this.load()
                      }
                    })
                  }
                  .width('100%')
                  .padding({ top: DesignDimens.SPACE_MD })
                }
              }
              .width('100%')
              .layoutWeight(1)
            } else if (this.createdOrders.length === 0 && this.joinedOrders.length === 0) {
              Column() {
                EmptyState({ message: DiningText.dining_order_list_empty })
              }
              .width('100%')
              .layoutWeight(1)
            } else {
              Scroll() {
                Column() {
                  if (this.createdOrders.length > 0) {
                    this.SectionHeader(DiningText.dining_order_section_created)
                    ForEach(this.createdOrders, (order: DiningOrderListVo) => {
                      this.OrderCard(order)
                    }, (order: DiningOrderListVo) => `created_${order.id}`)
                  }
                  if (this.joinedOrders.length > 0) {
                    this.SectionHeader(DiningText.dining_order_section_joined)
                    ForEach(this.joinedOrders, (order: DiningOrderListVo) => {
                      this.OrderCard(order)
                    }, (order: DiningOrderListVo) => `joined_${order.id}`)
                  }
                  Column() {
                  }
                  .height(Math.max(safeBottomVp() + FAB_SIZE, DesignDimens.SPACE_XXL))
                }
                .width('100%')
              }
              .scrollBar(BarState.Off)
              .width('100%')
              .height('100%')
              .align(Alignment.TopStart)
              .edgeEffect(EdgeEffect.Spring)
            }
          }
          .width('100%')
          .height('100%')
          .padding({ top: safeTopVp(), bottom: safeBottomVp() })

          // FAB 发起点单（自绘圆形 + 品牌渐变 + 十字，对齐 Android dining FAB 自绘）
          Column() {
            SymbolGlyph($r('sys.symbol.plus'))
              .fontSize(24)
              .fontColor([$r('app.color.ON_PRIMARY')])
          }
          .width(FAB_SIZE)
          .height(FAB_SIZE)
          .borderRadius(FAB_SIZE / 2)
          .linearGradient({
            angle: 135,
            colors: [[$r('app.color.PRIMARY'), 0], [$r('app.color.PRIMARY_DARK'), 1]]
          })
          .shadow({
            radius: DesignElevation.CARD_SHADOW_RADIUS,
            color: $r('app.color.SHADOW_COLOR_CARD'),
            offsetX: 0,
            offsetY: DesignElevation.CARD_SHADOW_OFFSET
          })
          .margin({
            right: DesignDimens.PAGE_PADDING,
            bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_LG) + DesignDimens.SPACE_MD
          })
          .scale({
            x: this.pressedKey === 'fab' ? 0.94 : 1,
            y: this.pressedKey === 'fab' ? 0.94 : 1
          })
          .animation({ duration: 100, curve: Curve.EaseOut })
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Down) {
              this.pressedKey = 'fab'
            } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
              this.pressedKey = ''
            }
          })
          .onClick(() => {
            pathStack.pushPathByName(RouteNames.DINING_ORDER_CREATE, new DiningOrderCreateParam())
          })
        }
        .width('100%')
        .height('100%')
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 离栈再回（创建/加入/结束点单后）重拉列表；首显与 aboutToAppear 去重
      if (!this.hasShown) {
        this.hasShown = true
        return
      }
      logger.debug(TAG, '列表页重回可见，重拉订单')
      this.load()
    })
  }

  /** 「我发起的 / 我参与的」分区标题（区块标题档 18/600） */
  @Builder
  SectionHeader(title: string) {
    Text(title)
      .fontSize(DesignFontSize.SECTION_TITLE)
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor($r('app.color.TEXT_PRIMARY'))
      .width('100%')
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_MD,
        bottom: DesignDimens.SPACE_XS
      })
  }

  /** 订单卡：标题 + 状态标签 + 时间 + 「再来一单」（AppCard 唯一画法 + pressable） */
  @Builder
  OrderCard(order: DiningOrderListVo) {
    Column() {
      Row({ space: DesignDimens.SPACE_SM }) {
        Text(order.title.length > 0 ? order.title : DiningText.dining_order_join_unknown_title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)

        DiningStatusTag({ status: diningOrderStatusFromCode(order.status) })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Row({ space: DesignDimens.SPACE_SM }) {
        if (order.isCreator) {
          Text(DiningText.dining_order_creator_badge)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.PRIMARY'))
            .backgroundColor($r('app.color.PRIMARY_LIGHT'))
            .borderRadius(DesignDimens.RADIUS_FULL)
            .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 2, bottom: 2 })
        }
        Text(diningTimeLabel(order.createdAt))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .layoutWeight(1)

        Text(DiningText.dining_order_action_reuse)
          .fontSize(DesignFontSize.BUTTON_TEXT)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_GREEN'))
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
          .scale({
            x: this.pressedKey === `reuse_${order.id}` ? 0.94 : 1,
            y: this.pressedKey === `reuse_${order.id}` ? 0.94 : 1
          })
          .animation({ duration: 100, curve: Curve.EaseOut })
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Down) {
              this.pressedKey = `reuse_${order.id}`
            } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
              this.pressedKey = ''
            }
          })
          .onClick(() => {
            this.goCreateForReuse(order)
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .margin({ top: DesignDimens.SPACE_XS })
    }
    .width('100%')
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .padding(DesignDimens.CARD_PADDING)
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    .margin({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, bottom: DesignDimens.CARD_SPACING })
    .scale({
      x: this.pressedKey === `card_${order.id}` ? 0.97 : 1,
      y: this.pressedKey === `card_${order.id}` ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `card_${order.id}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.goOrderBody(order)
    })
  }

  /** 拉取我的订单并按 isCreator 派生分区 */
  private async load(): Promise<void> {
    this.isLoading = this.createdOrders.length === 0 && this.joinedOrders.length === 0
    this.isError = false
    try {
      const res = await getMyDiningOrders()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const created: Array<DiningOrderListVo> = []
        const joined: Array<DiningOrderListVo> = []
        res.data.forEach((order: DiningOrderListVo): void => {
          if (order.isCreator) {
            created.push(order)
          } else {
            joined.push(order)
          }
        })
        this.createdOrders = created
        this.joinedOrders = joined
        logger.debug(TAG, `订单列表加载成功 发起=${created.length} 参与=${joined.length}`)
      } else {
        logger.warn(TAG, `订单列表未生效 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        showToastSafety(res.message, DiningText.dining_order_list_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '订单列表加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      showToastSafety('', DiningText.dining_order_list_load_failed)
    }
    this.isLoading = false
  }

  /** 卡片点击：进行中 → 点单页；已结束/未知 → 购买清单结果页（未知态保守降级同 FINISHED） */
  private goOrderBody(order: DiningOrderListVo): void {
    if (diningOrderStatusFromCode(order.status) === DiningOrderStatus.ONGOING) {
      pathStack.pushPathByName(RouteNames.DINING_ORDER_DETAIL, order.id)
    } else {
      pathStack.pushPathByName(RouteNames.DINING_SHOPPING_LIST, order.id)
    }
  }

  /** 「再来一单」：创建页复用模式（携带源单 ID 与标题） */
  private goCreateForReuse(order: DiningOrderListVo): void {
    const param = new DiningOrderCreateParam()
    param.fromOrderId = order.id
    param.sourceTitle = order.title
    pathStack.pushPathByName(RouteNames.DINING_ORDER_CREATE, param)
  }
}
