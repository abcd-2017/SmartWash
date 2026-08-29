import { HttpStatusCode } from "@ohos/axios"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
import { getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"

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
      showToastSafety(res.message, "订单详情加载失败")
    }
  }

  @Builder
  StatusCardBuilder() {
    let statusText: string = ""
    let statusColor: number = DesignColor.PRIMARY
    let statusIcon: Resource = $r('sys.symbol.checkmark_circle')
    let description: string = ""

    switch (this.orderInfo?.status) {
      case OrderStatusEnum.PENDING_PAYMENT:
        statusText = "待支付"
        statusColor = DesignColor.WARNING
        statusIcon = $r('sys.symbol.hourglass_bottomhalf_filled')
        description = "请尽快完成支付"
        break
      case OrderStatusEnum.PENDING_SHIPMENT:
        statusText = "待寄件"
        statusColor = DesignColor.PRIMARY
        statusIcon = $r('sys.symbol.paperplane')
        description = "请尽快将衣物放入寄存柜"
        break
      case OrderStatusEnum.WASHING:
        statusText = "清洗中"
        statusColor = DesignColor.PRIMARY
        statusIcon = $r('sys.symbol.wash_light')
        description = "衣物正在清洗中"
        break
      case OrderStatusEnum.READY_FOR_PICKUP:
        statusText = "待取件"
        statusColor = DesignColor.PRIMARY
        statusIcon = $r('sys.symbol.checkmark_circle')
        description = "衣物已放入寄存柜"
        break
      case OrderStatusEnum.COMPLETED:
        statusText = "已完成"
        statusColor = DesignColor.SUCCESS
        statusIcon = $r('sys.symbol.checkmark_circle')
        description = "订单已完成"
        break
      case OrderStatusEnum.CANCELED:
        statusText = "已取消"
        statusColor = DesignColor.ERROR
        statusIcon = $r('sys.symbol.xmark_circle')
        description = "订单已取消"
        break
      default:
        statusText = getOrderStatusDescription(this.orderInfo?.status ?? "-10") ?? ""
        statusColor = DesignColor.TEXT_SECONDARY
        statusIcon = $r('sys.symbol.checkmark_circle')
        description = ""
        break
    }

    Row() {
      // 左侧图标容器
      Column() {
        SymbolGlyph(statusIcon)
          .fontSize(26)
          .fontColor([statusColor])
      }
      .width(52)
      .height(52)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(statusColor & 0x1FFFFFFF)
      .justifyContent(FlexAlign.Center)

      // 右侧文字
      Column() {
        Text(statusText)
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(statusColor)

        if (description) {
          Text(description)
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 4 })
        }
      }
      .margin({ left: 16 })
      .alignItems(HorizontalAlign.Start)
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING + 8)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .backgroundColor(DesignColor.SURFACE)
    .border({ width: 0.5, color: DesignColor.OUTLINE })
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  InfoSectionBuilder(title: string, icon: Resource, builder: () => void) {
    Column() {
      // 标题行
      Row() {
        SymbolGlyph(icon)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontColor([DesignColor.PRIMARY])

        Text(title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .margin({ left: 10 })
      }
      .width("100%")
      .margin({ bottom: 12 })

      builder()
    }
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .backgroundColor(DesignColor.SURFACE)
    .border({ width: 0.5, color: DesignColor.OUTLINE })
  }

  @Builder
  InfoRowBuilder(label: string, value: string, isGreen?: boolean) {
    Row() {
      Text(label)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_SECONDARY)

      Text(value)
        .fontSize(DesignFontSize.BODY)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(isGreen ? DesignColor.PRIMARY : DesignColor.TEXT_PRIMARY)
    }
    .width("100%")
    .justifyContent(FlexAlign.SpaceBetween)
    .margin({ top: 6, bottom: 6 })
  }

  build() {
    NavDestination() {
      Scroll() {
        Column() {
          // 自定义页面标题
          Row() {
            SymbolGlyph($r('sys.symbol.chevron_left'))
              .fontSize(24)
              .fontColor([DesignColor.TEXT_PRIMARY])
              .onClick(() => {
                pathStack.back()
              })

            Text("订单详情")
              .fontSize(DesignFontSize.PAGE_TITLE)
              .fontWeight(FontWeight.Bold)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .margin({ left: 8 })
          }
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, top: 12, bottom: 8 })

          // 内容区域
          Column() {
            // 状态卡片
            this.StatusCardBuilder()

            // 套餐信息
            this.InfoSectionBuilder("套餐信息", $r('sys.symbol.laundry'), () => {
              this.InfoRowBuilder("套餐类型", `${this.orderInfo?.laundryPackageVo?.itemName}`)
              this.InfoRowBuilder("价格", `￥${this.orderInfo?.totalPrice}`)
              this.InfoRowBuilder("实付款", `￥${this.orderInfo?.payPrice}`, true)
            })
            .margin({ top: DesignDimens.CARD_SPACING })

            // 学校信息
            this.InfoSectionBuilder("学校信息", $r('sys.symbol.company'), () => {
              this.InfoRowBuilder("学校名称", `${this.orderInfo?.schoolsVo?.schoolName}`)
              this.InfoRowBuilder("地址", `${this.orderInfo?.schoolsVo?.location}`)
            })
            .margin({ top: DesignDimens.CARD_SPACING })

            // 寄存柜信息 — 待取件/待寄件时显示
            if (this.orderInfo?.status == OrderStatusEnum.READY_FOR_PICKUP ||
              this.orderInfo?.status == OrderStatusEnum.PENDING_SHIPMENT) {
              this.InfoSectionBuilder("寄存柜信息", $r('sys.symbol.externaldrive_fill_3'), () => {
                this.InfoRowBuilder("柜号", `${this.orderInfo?.lockersVo?.lockerNumber}`)
                this.InfoRowBuilder("取件码", `${this.orderInfo?.pickupCode?.split(":").pop() ?? ""}`)
              })
              .margin({ top: DesignDimens.CARD_SPACING })
            }

            // 订单信息
            this.InfoSectionBuilder("订单信息", $r('sys.symbol.list_square_bill'), () => {
              this.InfoRowBuilder("订单编号", `${this.orderInfo?.orderNo}`)
              this.InfoRowBuilder("下单时间", `${this.orderInfo?.createdAt}`)
              this.InfoRowBuilder("当前状态", `${getOrderStatusDescription(this.orderInfo?.status ?? "001")}`)
              this.InfoRowBuilder("完成时间", "-")
            })
            .margin({ top: DesignDimens.CARD_SPACING })
          }
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: 8,
            bottom: 24
          })
        }
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
      .scrollBar(BarState.Off)
    }
    .hideTitleBar(true)
  }
}
