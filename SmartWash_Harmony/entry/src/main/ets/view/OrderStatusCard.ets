import { DesignColor, DesignDimens } from "../constant/DesignSystem"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
import { formatDate } from "../utils/TimeUtil"

@ComponentV2
export struct OrderStatusCard {
  @Param status: string | null = null
  @Param endTime: string | null = null

  @Builder
  OrderStatus(icon: Resource, title: string, description: string, iconBgColor?: number, titleColor?: number) {
    Row() {
      // 左侧大图标容器
      Column() {
        SymbolGlyph(icon)
          .fontSize(26)
          .fontColor([titleColor ?? DesignColor.PRIMARY])
      }
      .width(52)
      .height(52)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(iconBgColor ?? DesignColor.PRIMARY_CONTAINER)
      .justifyContent(FlexAlign.Center)

      // 右侧文字
      Column() {
        Text(title)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(titleColor ?? DesignColor.PRIMARY)

        Text(description)
          .fontSize(13)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 4 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 14 })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
  }

  build() {
    Column() {
      Column() {
        if (this.status == OrderStatusEnum.PENDING_PAYMENT) {
          this.OrderStatus($r('sys.symbol.hourglass_bottomhalf_filled'), "待支付", "请尽快支付",
            DesignColor.WARNING_CONTAINER, DesignColor.WARNING)
        } else if (this.status == OrderStatusEnum.PENDING_SHIPMENT) {
          this.OrderStatus($r('sys.symbol.airplane'), "待寄件", "请尽快去寄件",
            DesignColor.PRIMARY_CONTAINER, DesignColor.PRIMARY)
        } else if (this.status == OrderStatusEnum.WASHING) {
          this.OrderStatus($r('sys.symbol.wash_light'), "清洗中", "衣物正在清洗中",
            DesignColor.PRIMARY_CONTAINER, DesignColor.PRIMARY)
        } else if (this.status == OrderStatusEnum.READY_FOR_PICKUP) {
          this.OrderStatus($r('sys.symbol.car_side_rear_open_1'), "待取件", "衣物已到寄存柜",
            DesignColor.SUCCESS_CONTAINER, DesignColor.SUCCESS)
        } else if (this.status == OrderStatusEnum.COMPLETED) {
          this.OrderStatus($r('sys.symbol.checkmark_circle'), "已完成",
            `完成时间：${formatDate(new Date(this.endTime ?? ""))}`,
            DesignColor.SUCCESS_CONTAINER, DesignColor.SUCCESS)
        } else if (this.status == OrderStatusEnum.CANCELED) {
          this.OrderStatus($r('sys.symbol.xmark_circle'), "已取消",
            `取消时间：${formatDate(new Date(this.endTime ?? ""))}`,
            DesignColor.SECONDARY_CONTAINER, DesignColor.SECONDARY)
        } else {
          this.OrderStatus($r('sys.symbol.person_crop_circle_fill'),
            `${getOrderStatusDescription(this.status ?? "-10")}`, " ",
            DesignColor.SECONDARY_CONTAINER, DesignColor.SECONDARY)
        }
      }
      .width("100%")
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 0.5, color: DesignColor.OUTLINE })
    }
  }
}