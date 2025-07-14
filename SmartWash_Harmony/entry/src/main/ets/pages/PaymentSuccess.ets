import { HttpStatusCode } from "@ohos/axios"
import { getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { pathStack } from "../utils/PathStackUtil"
import { promptAction } from "@kit.ArkUI"
import { CommonConstants } from "../constant/CommonConstants"
import { OrderItemRow } from "../view/OrderItemRow"

@Builder
export function PaymentSuccessBuilder() {
  PaymentSuccess()
}

@Component
struct PaymentSuccess {
  @State orderId: number | null = null
  @State orderInfo: OrderInfo | null = null

  async aboutToAppear() {
    this.orderId = pathStack.getParamByName("PaymentSuccess")[0] as number
    if (this.orderId != null) {
      let res = await getOrderInfo(this.orderId)
      if (res.code == HttpStatusCode.Ok) {
        this.orderInfo = res.data
      } else {
        promptAction.showToast({ message: res.message })
      }
    }
  }

  build() {
    NavDestination() {
      Column() {
        Stack({ alignContent: Alignment.Bottom }) {
          Column() {
            Row() {
              SymbolGlyph($r('sys.symbol.checkmark_circle_fill'))
                .fontSize(48)
                .fontColor([$r('sys.color.icon_primary')])
            }.width("100%")
            .alignItems(VerticalAlign.Center)
            .justifyContent(FlexAlign.Center)
            .padding(24)

            RowSplit().height(16)

            Row() {
              Column() {
                Row() {
                  Text("订单信息")
                    .fontWeight(FontWeight.Bold)
                    .fontSize(16)
                }.justifyContent(FlexAlign.Start)
                .width("100%")
                .margin({ bottom: 10 })

                OrderItemRow({ title: "订单号", content: `${this.orderInfo?.orderNo}` })
                OrderItemRow({ title: "支付金额", content: `￥${this.orderInfo?.payPrice}` })
                OrderItemRow({ title: "下单时间", content: `${this.orderInfo?.createdAt}` })
              }
            }.borderRadius(CommonConstants.BORDER_RADIUS)
            .shadow(ShadowStyle.OUTER_DEFAULT_SM)
            .backgroundColor($r('sys.color.comp_background_primary'))
            .padding(18)

            RowSplit().height(28)

            Row() {
              Column() {
                Text("寄存柜信息")
                  .fontWeight(FontWeight.Bold)
                  .fontSize(16)
                Text(`请前往寄存柜${this.orderInfo?.lockersVo?.lockerNumber ?? -1}柜存放衣物`)
                  .fontSize(13)
                  .margin({ top: 6, bottom: 6 })
                Text("寄存柜将在24小时内处理您的订单")
                  .fontSize(13)
              }.alignItems(HorizontalAlign.Start)
              .width("100%")
            }.borderRadius(CommonConstants.BORDER_RADIUS)
            .shadow(ShadowStyle.OUTER_DEFAULT_SM)
            .backgroundColor($r('sys.color.comp_background_primary'))
            .padding(18)
          }.height("100%")

          Button("立即寄件").onClick((event: ClickEvent) => {
            pathStack.pop()
            pathStack.pushPathByName("PickupDelivery", this.orderInfo)
          }).width("100%")
            .fontSize(16)
        }
      }
      .width("100%")
      .height("100%")
      .align(Alignment.Top)
      .backgroundColor($r('sys.color.background_primary'))
      .padding({ left: 16, right: 16 })
    }.title("支付成功")
  }
}