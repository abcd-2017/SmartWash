import { DesignColor, DesignDimens, DesignFontSize } from "../constant/DesignSystem"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
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
      // 顶部：套餐名 + 状态描述 + 价格
      Row() {
        // 左侧图标
        Column() {
          SymbolGlyph($r('sys.symbol.laundry'))
            .fontColor([DesignColor.PRIMARY])
            .fontSize(26)
        }
        .margin({ right: 12 })

        // 中间：套餐名 + 状态描述
        Column() {
          Text(this.orderInfo?.laundryPackageVo?.itemName ?? "")
            .fontWeight(FontWeight.Bold)
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontColor(DesignColor.TEXT_PRIMARY)

          Text(this.orderInfo?.laundryPackageVo?.description ?? "")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .maxLines(1)
            .margin({ top: 4 })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)

        // 右侧价格
        Text(`￥${this.orderInfo?.payPrice}`)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(FontWeight.Bold)
          .fontColor(DesignColor.PRIMARY)
      }
      .width("100%")

      // 分隔线
      Divider()
        .strokeWidth(0.5)
        .color(DesignColor.DIVIDER)
        .margin({ top: 12, bottom: 12 })

      // 订单号 + 下单时间
      Column() {
        Row() {
          Text(`订单号：${this.orderInfo?.orderNo ?? ""}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
        }
        .margin({ bottom: 4 })

        Row() {
          Text(`下单时间：${this.orderInfo?.createdAt ?? ""}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
        }
      }
      .width("100%")
      .alignItems(HorizontalAlign.Start)

      // 操作按钮区域
      Column() {
        if (this.orderInfo?.status == OrderStatusEnum.PENDING_PAYMENT) {
          Row() {
            Text("取消订单")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .padding({ left: 16, right: 16, top: 8, bottom: 8 })
              .borderRadius(DesignDimens.BUTTON_RADIUS)
              .border({ width: 0.5, color: DesignColor.OUTLINE })
              .onClick(() => {
                this.cancelClick()
              })

            Button("去支付")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.ON_PRIMARY)
              .backgroundColor(DesignColor.PRIMARY)
              .borderRadius(DesignDimens.BUTTON_RADIUS)
              .height(36)
              .margin({ left: 12 })
              .onClick(() => {
                this.paymentClick()
              })
          }
          .width("100%")
          .justifyContent(FlexAlign.End)
        } else if (this.orderInfo?.status == OrderStatusEnum.PENDING_SHIPMENT) {
          Row() {
            Button("去寄件")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.ON_PRIMARY)
              .backgroundColor(DesignColor.PRIMARY)
              .borderRadius(DesignDimens.BUTTON_RADIUS)
              .height(36)
              .onClick(() => {
                this.shipmentClick()
              })
          }
          .width("100%")
          .justifyContent(FlexAlign.End)
        } else if (this.orderInfo?.status == OrderStatusEnum.WASHING) {
          Row() {
            SymbolGlyph($r('sys.symbol.clock'))
              .fontColor([DesignColor.PRIMARY])
              .fontSize(16)
              .margin({ right: 6 })

            Text("清洗中")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.PRIMARY)
          }
          .width("100%")
          .justifyContent(FlexAlign.End)
        } else if (this.orderInfo?.status == OrderStatusEnum.READY_FOR_PICKUP) {
          Row() {
            Button("去取件")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.ON_PRIMARY)
              .backgroundColor(DesignColor.PRIMARY)
              .borderRadius(DesignDimens.BUTTON_RADIUS)
              .height(36)
              .onClick(() => {
                this.pickupClick()
              })
          }
          .width("100%")
          .justifyContent(FlexAlign.End)
        } else {
          Row() {
            Text(getOrderStatusDescription(this.orderInfo?.status) ?? "")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }
          .width("100%")
          .justifyContent(FlexAlign.End)
        }
      }
      .width("100%")
      .margin({ top: 12 })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 0.5, color: DesignColor.OUTLINE })
    .onClick(() => {
      this.itemClick()
    })
  }
}
