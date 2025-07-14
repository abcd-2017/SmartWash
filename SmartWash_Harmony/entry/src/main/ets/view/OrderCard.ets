import { CommonConstants } from "../constant/CommonConstants"
import { getOrderStatusDescription, OrderStatusEnum, OrderStatusMap } from "../constant/OrderStatus"
import { OrderInfo } from "../network/vo/OrderInfo"

@ComponentV2
export struct OrderCard {
  @Param orderInfo: OrderInfo | null = null
  @Event cancelClick: () => void
  @Event paymentClick: () => void
  @Event shipmentClick: () => void
  @Event pickupClick: () => void
  @Event itemClick: () => void

  build() {

    Column() {
      Column() {
        Row() {
          Column() {
            SymbolGlyph($r('sys.symbol.laundry'))
              .fontColor([$r('sys.color.icon_primary')])
              .fontSize("26")
          }.margin({ right: 12 })

          Column() {
            Row() {
              Text(`${this.orderInfo?.laundryPackageVo?.itemName}`)
                .fontWeight(FontWeight.Bold).fontSize("16")
            }

            Row() {
              Text(`${this.orderInfo?.laundryPackageVo?.description ?? ""}`)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
            }
          }.layoutWeight(1)
          .alignItems(HorizontalAlign.Start)

          Column() {
            Text(`￥${this.orderInfo?.payPrice}`)
              .fontSize(16)
              .fontColor($r('sys.color.brand'))
          }.alignItems(HorizontalAlign.End)
        }

        RowSplit().height("10")
        Divider().strokeWidth(1).width("98%")
        RowSplit().height("14")

        Column() {
          Row() {
            Text(`订单号：${this.orderInfo?.orderNo}`)
              .fontSize(12)
              .fontWeight(FontWeight.Normal)
          }.margin({ bottom: 4 })

          Row() {
            Text(`下单时间：${this.orderInfo?.createdAt}`)
              .fontSize(12).fontWeight(FontWeight.Normal)
          }
        }.width("100%")
        .alignItems(HorizontalAlign.Start)

        RowSplit().height("14")
        Column() {
          if (this.orderInfo?.status == OrderStatusEnum.PENDING_PAYMENT) {
            Row() {
              Text("取消订单")
                .fontSize(14)
                .padding(10)
                .margin({ right: 24 })
                .onClick(() => {
                  this.cancelClick()
                })
              Button("去支付").onClick(() => {
                this.paymentClick()
              }).fontSize(14)
            }
          } else if (this.orderInfo?.status == OrderStatusEnum.PENDING_SHIPMENT) {
            Button("去寄件").onClick(() => {
              this.shipmentClick()
            }).fontSize(14)
          } else if (this.orderInfo?.status == OrderStatusEnum.WASHING) {
            Row() {
              SymbolGlyph($r('sys.symbol.clock'))
                .fontColor([$r('sys.color.font_primary')])
                .margin({ right: 8 })

              Text("清洗中").fontSize(14)
            }
          } else if (this.orderInfo?.status == OrderStatusEnum.READY_FOR_PICKUP) {
            Button("去取件").onClick(() => {
              this.pickupClick()
            }).fontSize(14)
          } else {
            Text(`${getOrderStatusDescription(this.orderInfo?.status)}`).fontSize(14)
          }
        }.width("100%")
        .alignItems(HorizontalAlign.End)
      }
      .margin({
        left: 16,
        right: 16,
        bottom: 12,
        top: 8
      })
      .padding(16)
      .backgroundColor($r('sys.color.comp_background_primary'))
      .borderRadius(CommonConstants.BORDER_RADIUS)
      .shadow(ShadowStyle.OUTER_DEFAULT_SM)
      .onClick(() => {
        this.itemClick()
      })
    }
    .width("100%")
  }
}