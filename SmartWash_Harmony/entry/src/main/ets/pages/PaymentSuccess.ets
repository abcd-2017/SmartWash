import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { OrderItemRow } from "../view/OrderItemRow"
import { AppButton, LoadingState } from "../view/common/AppComponents"
import { PickupDeliveryParam } from "./PickupDelivery"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

@Builder
export function PaymentSuccessBuilder() {
  PaymentSuccess()
}

@Component
struct PaymentSuccess {
  @State orderId: number | null = null
  @State orderInfo: OrderInfo | null = null
  // 加载/失败态（对齐安卓全屏 Loading + Error toast，失败不再静默）
  @State isLoading: boolean = true
  @State loadFailed: boolean = false

  async aboutToAppear() {
    // 安全取参（评审 #31）：参数缺失/非法时返回 null，不再 as 强转崩溃
    this.orderId = getNumberParam(RouteNames.PAYMENT_SUCCESS)
    if (this.orderId != null) {
      let res = await getOrderInfo(this.orderId)
      if (res.code == HttpStatusCode.Ok) {
        this.orderInfo = res.data
      } else {
        showToastSafety(res.message, "订单信息加载失败")
        this.loadFailed = true
      }
    } else {
      this.loadFailed = true
    }
    this.isLoading = false
  }

  build() {
    NavDestination() {
      Stack() {
        Column() {
          // 庆祝区域（对齐安卓 PaySuccessPage：primaryLight 卡 + 绿色 Check 圆 + 大标题 + 感谢语）
          Column() {
            Column() {
              SymbolGlyph($r('sys.symbol.checkmark'))
                .fontSize(28)
                .fontColor([DesignColor.ON_PRIMARY])
            }
            .width(44)
            .height(44)
            .borderRadius(22)
            .backgroundColor(DesignColor.PRIMARY)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)

            Text("支付成功")
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.BOLD)
              .fontColor(DesignColor.PRIMARY)
              .margin({ top: 16 })

            Text("感谢使用智慧洗衣")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .margin({ top: 4 })
          }
          .width("100%")
          .padding({ top: 32, bottom: 32 })
          // 对齐安卓 PaySuccessPage.kt:149-193：庆祝卡按 DESIGN-SPEC v0.4 §3.1 唯一卡片画法
          // 取 surface 白底 + 1px outline 描边 + level1 阴影（旧实现为无描边无阴影的 primaryLight 块）
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.LEVEL_1,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: 2
          })
          .margin({ top: DesignDimens.SECTION_SPACING })

          // 订单信息卡
          Text("订单信息")
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .width("100%")
            .margin({ top: DesignDimens.SECTION_SPACING, bottom: 12 })

          Column() {
            OrderItemRow({ title: "订单号", content: `${this.orderInfo?.orderNo}` })
            OrderItemRow({ title: "支付金额", content: `￥${this.orderInfo?.payPrice}` })
            OrderItemRow({ title: "下单时间", content: `${this.orderInfo?.createdAt}` })
          }
          .width("100%")
          .padding(DesignDimens.CARD_PADDING)
          // 对齐安卓 PaySuccessPage.kt:204-240：surface 白底 + 1px outline 描边 + level1 阴影
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.LEVEL_1,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: 2
          })

          // 寄存柜提示卡
          Text("寄存柜信息")
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .width("100%")
            .margin({ top: DesignDimens.SECTION_SPACING, bottom: 12 })

          Row() {
            // 物流图标容器（对齐安卓 LocalShipping IconBox）
            Column() {
              SymbolGlyph($r('sys.symbol.truck_box_fill'))
                .fontSize(20)
                .fontColor([DesignColor.PRIMARY])
            }
            .width(36)
            .height(36)
            .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
            .backgroundColor(DesignColor.PRIMARY_LIGHT)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)

            Column() {
              Text("寄存柜信息")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_PRIMARY)

              // 柜号空值兜底显示 "-"（空值语义），不再误显示 "-1柜"（评审 #34，安卓待对齐）
              Text(`请前往寄存柜 ${this.orderInfo?.lockersVo?.lockerNumber ?? "-"} 柜存放衣物`)
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .margin({ top: 4 })

              Text("寄存柜将在24小时内处理您的订单")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
                .margin({ top: 4 })
            }
            .alignItems(HorizontalAlign.Start)
            .margin({ left: 12 })
          }
          .width("100%")
          .padding(DesignDimens.CARD_PADDING)
          // 对齐安卓 PaySuccessPage.kt:252-300：surface 白底 + 1px outline 描边 + level1 阴影
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.LEVEL_1,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: 2
          })
          .alignItems(VerticalAlign.Top)

          Blank()

          // 底部按钮：立即寄件（对齐安卓强制 DELIVERY 类型语义，显式传参）
          Column() {
            AppButton({
              text: "立即寄件",
              click: () => {
                const param: PickupDeliveryParam = new PickupDeliveryParam()
                param.order = this.orderInfo
                param.isPickup = false
                pathStack.pop()
                pathStack.pushPathByName(RouteNames.PICKUP_DELIVERY, param)
              }
            })
          }
          .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
        }
        .width("100%")
        .height("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .alignItems(HorizontalAlign.Start)

        // 加载态
        if (this.isLoading) {
          // 加载态（对齐安卓 LoadingState：骨架屏，§3.6 禁转圈）
          LoadingState()
          .width("100%")
          .height("100%")
          .backgroundColor(DesignColor.BACKGROUND)
          .zIndex(2)
        }

        // 失败态：提示重试（重新进入该页即重试）
        if (!this.isLoading && this.loadFailed) {
          Column() {
            Text("订单信息加载失败")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }
          .width("100%")
          .height("100%")
          .justifyContent(FlexAlign.Center)
          .backgroundColor(DesignColor.BACKGROUND)
          .zIndex(2)
        }
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .title("支付成功")
  }
}
