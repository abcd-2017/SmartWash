import { HttpStatusCode } from "@ohos/axios"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
import { getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"

// @BuilderParam 的默认空插槽（ArkTS 要求默认值必须是 @Builder 函数，不能用内联箭头函数）
@Builder
function emptyContent() {
}

@Builder
export function OrderDetailBuilder() {
  OrderDetail()
}

/**
 * 订单状态卡片的展示元数据。
 * ArkTS @Builder 内仅允许 UI 语法（不允许 let/switch 局部语句），
 * 状态 → 文案/颜色/图标/描述 的映射收敛为类 + 私有方法计算。
 */
class StatusMeta {
  text: string = ""
  color: number = DesignColor.PRIMARY
  icon: Resource = $r('sys.symbol.checkmark_circle')
  description: string = ""
}

@Component
struct OrderDetail {
  @State orderInfo: OrderInfo | null = null
  @State orderId: number | null = null

  async aboutToAppear() {
    // 安全取参（评审 #31）：参数缺失/非法时返回 null，不再 as 强转崩溃
    this.orderId = getNumberParam("OrderDetail")
    if (this.orderId === null) {
      // 参数缺失（异常路由进入）：提示后停留空态，不发起无效请求
      showToastSafety("订单信息加载失败")
      return
    }
    let res = await getOrderInfo(this.orderId)
    if (res.code == HttpStatusCode.Ok) {
      this.orderInfo = res.data
    } else {
      showToastSafety(res.message, "订单详情加载失败")
    }
  }

  // 订单状态 → 展示元数据映射（原写在 @Builder 内的 switch 不满足 ArkTS 语法）
  private statusMeta(): StatusMeta {
    const meta: StatusMeta = new StatusMeta()
    switch (this.orderInfo?.status) {
      case OrderStatusEnum.PENDING_PAYMENT:
        meta.text = "待支付"
        meta.color = DesignColor.WARNING
        meta.icon = $r('sys.symbol.hourglass_bottomhalf_filled')
        meta.description = "请尽快完成支付"
        break
      case OrderStatusEnum.PENDING_SHIPMENT:
        meta.text = "待寄件"
        meta.color = DesignColor.PRIMARY
        meta.icon = $r('sys.symbol.paperplane')
        meta.description = "请尽快将衣物放入寄存柜"
        break
      case OrderStatusEnum.WASHING:
        meta.text = "清洗中"
        meta.color = DesignColor.PRIMARY
        meta.icon = $r('sys.symbol.wash_light')
        meta.description = "衣物正在清洗中"
        break
      case OrderStatusEnum.READY_FOR_PICKUP:
        meta.text = "待取件"
        meta.color = DesignColor.PRIMARY
        meta.icon = $r('sys.symbol.checkmark_circle')
        meta.description = "衣物已放入寄存柜"
        break
      case OrderStatusEnum.COMPLETED:
        meta.text = "已完成"
        meta.color = DesignColor.SUCCESS
        meta.icon = $r('sys.symbol.checkmark_circle')
        meta.description = "订单已完成"
        break
      case OrderStatusEnum.CANCELED:
        meta.text = "已取消"
        meta.color = DesignColor.ERROR
        meta.icon = $r('sys.symbol.xmark_circle')
        meta.description = "订单已取消"
        break
      default:
        meta.text = getOrderStatusDescription(this.orderInfo?.status ?? "-10") ?? ""
        meta.color = DesignColor.TEXT_SECONDARY
        meta.icon = $r('sys.symbol.checkmark_circle')
        meta.description = ""
        break
    }
    return meta
  }

  @Builder
  StatusCardBuilder() {
    Row() {
      // 左侧图标容器
      Column() {
        SymbolGlyph(this.statusMeta().icon)
          .fontSize(26)
          .fontColor([this.statusMeta().color])
      }
      .width(52)
      .height(52)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(this.statusMeta().color & 0x1FFFFFFF)
      .justifyContent(FlexAlign.Center)

      // 右侧文字
      Column() {
        Text(this.statusMeta().text)
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.statusMeta().color)

        if (this.statusMeta().description) {
          Text(this.statusMeta().description)
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
                pathStack.pop()
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
            InfoSectionCard({
              title: "套餐信息",
              icon: $r('sys.symbol.laundry'),
              content: () => {
                this.InfoRowBuilder("套餐类型", `${this.orderInfo?.laundryPackageVo?.itemName}`)
                this.InfoRowBuilder("价格", `￥${this.orderInfo?.totalPrice}`)
                this.InfoRowBuilder("实付款", `￥${this.orderInfo?.payPrice}`, true)
              }
            })
            .margin({ top: DesignDimens.CARD_SPACING })

            // 学校信息
            InfoSectionCard({
              title: "学校信息",
              icon: $r('sys.symbol.company'),
              content: () => {
                this.InfoRowBuilder("学校名称", `${this.orderInfo?.schoolsVo?.schoolName}`)
                this.InfoRowBuilder("地址", `${this.orderInfo?.schoolsVo?.location}`)
              }
            })
            .margin({ top: DesignDimens.CARD_SPACING })

            // 寄存柜信息 — 待取件/待寄件时显示
            if (this.orderInfo?.status == OrderStatusEnum.READY_FOR_PICKUP ||
              this.orderInfo?.status == OrderStatusEnum.PENDING_SHIPMENT) {
              InfoSectionCard({
                title: "寄存柜信息",
                icon: $r('sys.symbol.externaldrive_fill_3'),
                content: () => {
                  this.InfoRowBuilder("柜号", `${this.orderInfo?.lockersVo?.lockerNumber}`)
                  this.InfoRowBuilder("取件码", `${this.orderInfo?.pickupCode?.split(":").pop() ?? ""}`)
                }
              })
              .margin({ top: DesignDimens.CARD_SPACING })
            }

            // 订单信息
            InfoSectionCard({
              title: "订单信息",
              icon: $r('sys.symbol.list_square_bill'),
              content: () => {
                this.InfoRowBuilder("订单编号", `${this.orderInfo?.orderNo}`)
                this.InfoRowBuilder("下单时间", `${this.orderInfo?.createdAt}`)
                this.InfoRowBuilder("当前状态", `${getOrderStatusDescription(this.orderInfo?.status ?? "001")}`)
                this.InfoRowBuilder("完成时间", "-")
              }
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

/**
 * 通用信息分组卡片（标题行 + 内容插槽）。
 * 原实现为 @Builder 方法接收 `builder: () => void` 参数并链式 .margin()——
 * ArkTS 中 @Builder 调用返回 void、且 builder() 不满足 UI 语法，故改为子组件 + @BuilderParam。
 */
@Component
struct InfoSectionCard {
  @Prop title: string = ""
  icon: Resource = $r('sys.symbol.checkmark_circle')
  @BuilderParam content: () => void = emptyContent

  build() {
    Column() {
      // 标题行
      Row() {
        SymbolGlyph(this.icon)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontColor([DesignColor.PRIMARY])

        Text(this.title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .margin({ left: 10 })
      }
      .width("100%")
      .margin({ bottom: 12 })

      this.content()
    }
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .backgroundColor(DesignColor.SURFACE)
    .border({ width: 0.5, color: DesignColor.OUTLINE })
  }
}
