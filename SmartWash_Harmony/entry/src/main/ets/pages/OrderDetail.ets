import { HttpStatusCode } from "@ohos/axios"
import { CommonConstants } from "../constant/CommonConstants"
import { getOrderStatusDescription, OrderStatusEnum, OrderStatusMap } from "../constant/OrderStatus"
import { getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { pathStack } from "../utils/PathStackUtil"
import { OrderItemRow } from "../view/OrderItemRow"
import { OrderStatusCard } from "../view/OrderStatusCard"
import { promptAction } from "@kit.ArkUI"

@Builder
export function OrderDetailBuilder() {
  OrderDetail()
}

@Component
struct OrderDetail {
  @State orderInfo: OrderInfo | null = null
  @State orderId: number | null = null

  async aboutToAppear() {
    this.orderId = pathStack.getParamByName("OrderDetail")[0] as number
    let res = await getOrderInfo(this.orderId)
    if (res.code == HttpStatusCode.Ok) {
      this.orderInfo = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  build() {
    NavDestination() {
      Column() {
        OrderStatusCard({
          status: this.orderInfo?.status,
          endTime: this.orderInfo?.updatedAt
        })

        RowSplit().height("20")

        Column() {
          Row() {
            SymbolGlyph($r('sys.symbol.company'))
              .fontColor([$r('sys.color.font_primary')])
              .fontSize(18)
            Text("学校信息")
              .fontSize(16)
              .margin({ left: 10 })
          }.justifyContent(FlexAlign.Start)
          .width("100%")
          .margin({ bottom: 10 })

          OrderItemRow({ title: "学校名称", content: `${this.orderInfo?.schoolsVo?.schoolName}` })
          OrderItemRow({ title: "地址", content: `${this.orderInfo?.schoolsVo?.location}` })
        }.padding(16)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)

        if (this.orderInfo?.status == OrderStatusEnum.READY_FOR_PICKUP ||
          this.orderInfo?.status == OrderStatusEnum.PENDING_SHIPMENT) {
          RowSplit().height("20")
          Column() {
            Row() {
              SymbolGlyph($r('sys.symbol.externaldrive_fill_3'))
                .fontColor([$r('sys.color.font_primary')])
                .fontSize(18)
              Text("寄存柜信息")
                .fontSize(16)
                .margin({ left: 10 })
            }.justifyContent(FlexAlign.Start)
            .width("100%")
            .margin({ bottom: 10 })

            OrderItemRow({ title: "柜号", content: `${this.orderInfo?.lockersVo?.lockerNumber}` })
            OrderItemRow({ title: "取件码", content: `${this.orderInfo.pickupCode?.split(":")[2] ?? ""}` })
          }.padding(16)
          .backgroundColor($r('sys.color.comp_background_primary'))
          .borderRadius(CommonConstants.BORDER_RADIUS)
          .shadow(ShadowStyle.OUTER_DEFAULT_SM)
        }

        RowSplit().height("20")
        Column() {
          Row() {
            SymbolGlyph($r('sys.symbol.laundry'))
              .fontColor([$r('sys.color.font_primary')])
              .fontSize(18)
            Text("套餐信息")
              .fontSize(16)
              .margin({ left: 10 })
          }.justifyContent(FlexAlign.Start)
          .width("100%")
          .margin({ bottom: 10 })

          OrderItemRow({ title: "套餐类型", content: `${this.orderInfo?.laundryPackageVo?.itemName}` })
          OrderItemRow({ title: "价格", content: `￥${this.orderInfo?.totalPrice}` })
          OrderItemRow({ title: "实付款", content: `￥${this.orderInfo?.payPrice}` })
        }.padding(16)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)

        RowSplit().height("20")
        Column() {
          Row() {
            SymbolGlyph($r('sys.symbol.list_square_bill'))
              .fontColor([$r('sys.color.font_primary')])
              .fontSize(18)
            Text("订单信息")
              .fontSize(16)
              .margin({ left: 10 })
          }.justifyContent(FlexAlign.Start)
          .width("100%")
          .margin({ bottom: 10 })

          OrderItemRow({ title: "订单编号", content: `${this.orderInfo?.orderNo}` })
          OrderItemRow({ title: "下单时间", content: `${this.orderInfo?.createdAt}` })
          OrderItemRow({ title: "当前状态", content: `${getOrderStatusDescription(this.orderInfo?.status ?? "001")}` })
          OrderItemRow({ title: "完成时间", content: "-" })
        }.padding(16)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)
      }.padding(16)
    }.title("订单详情")
  }
}