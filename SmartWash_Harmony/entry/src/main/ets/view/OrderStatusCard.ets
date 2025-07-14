import { CommonConstants } from "../constant/CommonConstants"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
import { formatDate } from "../utils/TimeUtil"

@ComponentV2
export struct OrderStatusCard {
  @Param status: string | null = null
  @Param endTime: string | null = null

  @Builder
  OrderStatus(icon: Resource, title: string, description: string, color?: Resource) {
    Column() {
      Row() {
        SymbolGlyph(icon).fontSize(28)
          .fontColor([$r('sys.color.font_primary')])
      }

      Row() {
        Text(title)
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
      }.margin({ top: 10, bottom: 8 })

      Row() {
        Text(description)
          .fontSize(13)
      }
    }.width("100%")
    .backgroundColor(color != null ? color : $r('sys.color.comp_background_primary'))
  }

  build() {
    Column() {
      Column() {
        if (this.status == OrderStatusEnum.PENDING_PAYMENT) {
          this.OrderStatus($r('sys.symbol.hourglass_bottomhalf_filled'), "待支付", "请尽快支付")
        } else if (this.status == OrderStatusEnum.PENDING_SHIPMENT) {
          this.OrderStatus($r('sys.symbol.airplane'), "待寄件", "请尽快去寄件")
        } else if (this.status == OrderStatusEnum.WASHING) {
          this.OrderStatus($r('sys.symbol.wash_light'), "清洗中", "预计完成时间：2024-02-06 16:30")
        } else if (this.status == OrderStatusEnum.READY_FOR_PICKUP) {
          this.OrderStatus($r('sys.symbol.car_side_rear_open_1'), "待取件", "衣物已到寄存柜")
        } else if (this.status == OrderStatusEnum.COMPLETED) {
          this.OrderStatus($r('sys.symbol.checkmark_circle'), "已完成",
            `完成时间：${formatDate(new Date(this.endTime ?? ""))}`)
        } else if (this.status == OrderStatusEnum.CANCELED) {
          this.OrderStatus($r('sys.symbol.xmark_circle'), "已取消",
            `取消时间：${formatDate(new Date(this.endTime ?? ""))}`)
        } else {
          this.OrderStatus($r('sys.symbol.person_crop_circle_fill'),
            `${getOrderStatusDescription(this.status ?? "-10")}`, " ")
        }
      }
      .width("100%")
      .height(120)
      .backgroundColor($r('sys.color.comp_background_primary'))
      .shadow(ShadowStyle.OUTER_DEFAULT_SM)
      .borderRadius(CommonConstants.BORDER_RADIUS)
      .justifyContent(FlexAlign.Center)
    }
  }
}