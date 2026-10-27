import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
import { OrderInfo } from "../network/vo/OrderInfo"
import { formatOrderTime } from "../utils/TimeUtil"
import { Debouncer } from "../utils/DebounceUtil"

/**
 * 单个自然月的订单合并卡（对齐安卓 MonthGroupCard，差异分析 02 §3.3）：
 * 每月一张卡，行间 0.5 发丝线分隔；行内含状态文字+语义色点、订单号·套餐名、
 * 相对时间（今天/昨天/M月d日）、价格（已取消置灰）与按状态的操作按钮（节流防抖）。
 * 卡壳不可点，行点击进详情；动作按钮自身消费点击，与行点击不冲突。
 */
@ComponentV2
export struct OrderMonthCard {
  @Param orders: Array<OrderInfo> = []
  @Event cancelClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  @Event paymentClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  @Event shipmentClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  @Event pickupClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  @Event itemClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  // 动作按钮节流（对齐安卓 rememberDebouncedClick：去支付/去寄件/去取件连点防护）
  private debouncer: Debouncer = new Debouncer()

  aboutToDisappear(): void {
    this.debouncer.cancel()
  }

  /** 订单状态 → 左侧图标容器语义色（对齐安卓 OrderRow：蓝=待寄件 / 青=已完成 / 粉=已取消 / 其余橙） */
  private iconColors(status: string | null | undefined): [number, number] {
    if (status === OrderStatusEnum.PENDING_SHIPMENT) {
      return [DesignColor.ICON_CONTAINER_BLUE, DesignColor.ICON_FOREGROUND_BLUE]
    }
    if (status === OrderStatusEnum.COMPLETED) {
      return [DesignColor.ICON_CONTAINER_TEAL, DesignColor.ICON_FOREGROUND_TEAL]
    }
    if (status === OrderStatusEnum.CANCELED) {
      return [DesignColor.ICON_CONTAINER_PINK, DesignColor.ICON_FOREGROUND_PINK]
    }
    return [DesignColor.ICON_CONTAINER_ORANGE, DesignColor.ICON_FOREGROUND_ORANGE]
  }

  /** 状态色点语义（规范 §3.5：完成=success / 取消=danger / 其余=warning） */
  private statusDotColor(status: string | null | undefined): number {
    if (status === OrderStatusEnum.COMPLETED) {
      return DesignColor.SUCCESS
    }
    if (status === OrderStatusEnum.CANCELED) {
      return DesignColor.DANGER
    }
    return DesignColor.WARNING
  }

  @Builder
  ActionButton(label: string, action: () => void) {
    Row() {
      Text(label)
        .fontSize(DesignFontSize.BODY)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(DesignColor.ON_PRIMARY)
    }
    .height(36)
    .padding({ left: 16, right: 16 })
    .justifyContent(FlexAlign.Center)
    .borderRadius(DesignDimens.BUTTON_RADIUS)
    .backgroundColor(DesignColor.PRIMARY)
    .onClick(() => {
      this.debouncer.throttle(action, 500)
    })
  }

  @Builder
  OrderRow(order: OrderInfo) {
    Row() {
      // 左侧图标容器：按状态映射语义色（IconBox 规格 36/18）
      Column() {
        SymbolGlyph($r('sys.symbol.laundry'))
          .fontSize(18)
          .fontColor([this.iconColors(order.status)[1]])
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(this.iconColors(order.status)[0])
      .justifyContent(FlexAlign.Center)
      .margin({ right: 12 })

      // 主体信息
      Column() {
        // 状态文字 + 语义色点
        Row() {
          Text(getOrderStatusDescription(order.status))
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)

          Row()
            .width(6)
            .height(6)
            .borderRadius(3)
            .backgroundColor(this.statusDotColor(order.status))
            .margin({ left: 8 })
        }
        .alignItems(VerticalAlign.Center)

        Text(`${order.orderNo ?? ""} · ${order.laundryPackageVo?.itemName ?? ""}`)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 5 })
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .maxLines(1)

        Text(formatOrderTime(order.createdAt))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 2 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      // 右侧价格 + 操作按钮
      Column() {
        Text(`￥${order.payPrice}`)
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.BOLD)
          // 已取消价格置灰（对齐安卓 textTertiary 分支）
          .fontColor(order.status === OrderStatusEnum.CANCELED ?
            DesignColor.TEXT_TERTIARY : DesignColor.PRIMARY)

        Column() {
          if (order.status === OrderStatusEnum.PENDING_PAYMENT) {
            Row() {
              Text("取消订单")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
                .padding({ left: 8, right: 8, top: 10, bottom: 10 })
                .onClick(() => {
                  this.cancelClick(order)
                })

              this.ActionButton("去支付", () => {
                this.paymentClick(order)
              })
            }
            .alignItems(VerticalAlign.Center)
          } else if (order.status === OrderStatusEnum.PENDING_SHIPMENT) {
            this.ActionButton("去寄件", () => {
              this.shipmentClick(order)
            })
          } else if (order.status === OrderStatusEnum.WASHING) {
            Text("清洗中")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
          } else if (order.status === OrderStatusEnum.READY_FOR_PICKUP) {
            this.ActionButton("去取件", () => {
              this.pickupClick(order)
            })
          } else {
            Text(getOrderStatusDescription(order.status))
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }
        }
        .alignItems(HorizontalAlign.End)
        .margin({ top: 8 })
      }
      .alignItems(HorizontalAlign.End)
    }
    .width("100%")
    .alignItems(VerticalAlign.Top)
    .padding({
      left: DesignDimens.CARD_PADDING,
      right: DesignDimens.CARD_PADDING,
      top: 15,
      bottom: 15
    })
    .onClick(() => {
      this.itemClick(order)
    })
  }

  build() {
    Column() {
      ForEach(this.orders, (order: OrderInfo, index: number) => {
        this.OrderRow(order)
        if (index < this.orders.length - 1) {
          Divider()
            .strokeWidth(0.5)
            .color(DesignColor.HAIRLINE)
            .margin({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
        }
      }, (order: OrderInfo) => order.orderId?.toString())
    }
    .width("100%")
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 0.5, color: DesignColor.OUTLINE })
  }
}
