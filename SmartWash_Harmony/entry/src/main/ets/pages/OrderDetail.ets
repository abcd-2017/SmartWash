import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
import { getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { AppInfoDialog } from "../view/common/AppComponents"

// @BuilderParam 的默认空插槽（ArkTS 要求默认值必须是 @Builder 函数，不能用内联箭头函数）
@Builder
function emptyContent() {
}

@Builder
export function OrderDetailBuilder() {
  OrderDetail()
}

/**
 * 订单状态区的展示元数据。
 * ArkTS @Builder 内仅允许 UI 语法（不允许 let/switch 局部语句），
 * 状态 → 文案/颜色/图标/描述 的映射收敛为类 + 私有方法计算。
 */
class StatusMeta {
  text: string = ""
  color: number = DesignColor.PRIMARY
  icon: Resource = $r('sys.symbol.checkmark_circle')
  description: string = ""
}

/** 进度时间线节点（5 节点固定流程，对齐安卓 ProgressTimeline） */
class TimelineNode {
  status: string = ""
  label: string = ""

  constructor(status: string, label: string) {
    this.status = status
    this.label = label
  }
}

@Component
struct OrderDetail {
  @State orderInfo: OrderInfo | null = null
  @State orderId: number | null = null
  // 加载态：详情请求期间全屏 Loading（对齐安卓 LoadingState）
  @State isLoading: boolean = true
  // 取件码弹窗文案（open 前赋值，弹窗以当次值初始化）
  @State pickupCodeMessage: string = ""
  // 取件码弹窗（对齐安卓 AppInfoDialog：取件码 + 柜号）
  private pickupCodeDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({ message: this.pickupCodeMessage }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })
  // 时间线节点（按订单流程顺序，保持 5 个状态不变）
  private nodes: Array<TimelineNode> = [
    new TimelineNode(OrderStatusEnum.PENDING_PAYMENT, "待支付"),
    new TimelineNode(OrderStatusEnum.PENDING_SHIPMENT, "待寄件"),
    new TimelineNode(OrderStatusEnum.WASHING, "清洗中"),
    new TimelineNode(OrderStatusEnum.READY_FOR_PICKUP, "待取件"),
    new TimelineNode(OrderStatusEnum.COMPLETED, "已完成")
  ]

  async aboutToAppear() {
    // 安全取参（评审 #31）：参数缺失/非法时返回 null，不再 as 强转崩溃
    this.orderId = getNumberParam(RouteNames.ORDER_DETAIL)
    if (this.orderId === null) {
      // 参数缺失（异常路由进入）：提示后停留空态，不发起无效请求
      showToastSafety("订单信息加载失败")
      this.isLoading = false
      return
    }
    let res = await getOrderInfo(this.orderId)
    if (res.code == HttpStatusCode.Ok) {
      this.orderInfo = res.data
    } else {
      showToastSafety(res.message, "订单详情加载失败")
    }
    this.isLoading = false
  }

  /**
   * 取件码解析 — 统一取冒号分段第 3 段（D4，对齐安卓 split(":").getOrNull(2)），
   * 防后端分段格式加段后两端解析语义漂移。
   */
  private parsePickupCode(): string {
    return this.orderInfo?.pickupCode?.split(":")[2] ?? ""
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
        meta.color = DesignColor.PRIMARY_DARK
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
        meta.color = DesignColor.DANGER
        meta.icon = $r('sys.symbol.xmark_circle')
        meta.description = "订单已取消"
        break
      default:
        meta.text = getOrderStatusDescription(this.orderInfo?.status)
        meta.color = DesignColor.TEXT_SECONDARY
        meta.icon = $r('sys.symbol.checkmark_circle')
        meta.description = ""
        break
    }
    return meta
  }

  /** 当前状态在时间线中的节点下标（未知状态兜底第 0 节点，对齐安卓 coerceAtLeast(0)） */
  private currentIndex(): number {
    const status = this.orderInfo?.status
    const index = this.nodes.findIndex((node: TimelineNode): boolean => node.status === status)
    return index >= 0 ? index : 0
  }

  /** 时间线首节点副行：真实下单时间 HH:mm（格式异常不显示，对齐安卓 D-OD2） */
  private firstNodeTime(): string {
    const created = this.orderInfo?.createdAt ?? ""
    if (created.length >= 16) {
      const time = created.substring(11, 16)
      if (time.length === 5 && time.includes(":")) {
        return time
      }
    }
    return ""
  }

  @Builder
  StatusHeader() {
    // 状态区：居中大图标 + 状态大字 + 副行提示（对齐安卓无卡片形态，插画降级为 SymbolGlyph）
    Column() {
      SymbolGlyph(this.statusMeta().icon)
        .fontSize(64)
        .fontColor([this.statusMeta().color])

      Text(this.statusMeta().text)
        .fontSize(DesignFontSize.DATA_LARGE)
        .fontWeight(DesignFontWeight.BOLD)
        .fontColor(this.statusMeta().color)
        .margin({ top: 12 })

      if (this.statusMeta().description !== "") {
        Text(this.statusMeta().description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 6 })
      }
    }
    .width("100%")
    .alignItems(HorizontalAlign.Center)
    .padding({ top: 10, bottom: 14 })
  }

  @Builder
  TimelineNodeContent(node: TimelineNode, index: number) {
    // 左侧竖列：节点圆点 + 竖 rail（非末行固定行高，rail 撑满剩余高度保证连续）
    Column() {
      if (index === this.currentIndex()) {
        // now 节点：20 光环底（primaryContainer）+ 内层 12 主色实心
        Column() {
          Column()
            .width(12)
            .height(12)
            .borderRadius(6)
            .backgroundColor(DesignColor.PRIMARY)
        }
        .width(20)
        .height(20)
        .borderRadius(10)
        .backgroundColor(DesignColor.PRIMARY_CONTAINER)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      } else {
        // done → 主色实心；未完成 → outline 实心
        Column()
          .width(12)
          .height(12)
          .borderRadius(6)
          .backgroundColor(index < this.currentIndex() ? DesignColor.PRIMARY : DesignColor.OUTLINE)
      }
      if (index < this.nodes.length - 1) {
        // rail 2dp：已完成段主色、未完成 hairline
        Column()
          .width(2)
          .layoutWeight(1)
          .backgroundColor(index < this.currentIndex() ? DesignColor.PRIMARY : DesignColor.HAIRLINE)
          .margin({ top: 2 })
      }
    }
    .width(20)
    .height("100%")
    .alignItems(HorizontalAlign.Center)

    Column() {
      Text(node.label)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(index === this.currentIndex() ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
        .fontColor(index === this.currentIndex() ? DesignColor.PRIMARY_DARK :
          (index < this.currentIndex() ? DesignColor.TEXT_PRIMARY : DesignColor.TEXT_TERTIARY))

      // 副行仅首节点显示真实下单时间 HH:mm
      if (index === 0 && this.firstNodeTime() !== "") {
        Text(this.firstNodeTime())
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .margin({ top: 4 })
      }
    }
    .alignItems(HorizontalAlign.Start)
    .margin({ left: 12 })
  }

  @Builder
  TimelineNodeRow(node: TimelineNode, index: number) {
    // 非末行固定行高（rail 可撑满），末行自适应高度
    if (index < this.nodes.length - 1) {
      Row() {
        this.TimelineNodeContent(node, index)
      }
      .width("100%")
      .height(56)
      .alignItems(VerticalAlign.Top)
    } else {
      Row() {
        this.TimelineNodeContent(node, index)
      }
      .width("100%")
      .alignItems(VerticalAlign.Top)
    }
  }

  @Builder
  ProgressTimelineCard() {
    Column() {
      Text("进度时间线")
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ bottom: 16 })

      Column() {
        ForEach(this.nodes, (node: TimelineNode, index: number) => {
          this.TimelineNodeRow(node, index)
        }, (node: TimelineNode) => node.status)
      }
      .padding({ left: 24 })
    }
    .width("100%")
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

  @Builder
  BottomActionBar() {
    Column() {
      Divider()
        .strokeWidth(0.5)
        .color(DesignColor.OUTLINE)

      Row() {
        // 联系客服（次按钮，D-OD12：待客服功能立项，先以 toast 兜底）
        Column() {
          Text("联系客服")
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
        }
        .layoutWeight(1)
        .height(52)
        .justifyContent(FlexAlign.Center)
        .borderRadius(DesignDimens.BUTTON_RADIUS)
        .backgroundColor(DesignColor.SURFACE_VARIANT)
        .onClick(() => {
          showToastSafety("客服暂未开通，请稍后再试")
        })

        // 查看取件码（主按钮）：待寄件/待取件且取件码非空弹窗，否则提示
        Column() {
          Text("查看取件码")
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.ON_PRIMARY)
        }
        .layoutWeight(1)
        .height(52)
        .justifyContent(FlexAlign.Center)
        .borderRadius(DesignDimens.BUTTON_RADIUS)
        .backgroundColor(DesignColor.PRIMARY)
        .margin({ left: 12 })
        .onClick(() => {
          const status = this.orderInfo?.status
          const code = this.parsePickupCode()
          if ((status === OrderStatusEnum.PENDING_SHIPMENT || status === OrderStatusEnum.READY_FOR_PICKUP) &&
            code !== "") {
            this.pickupCodeMessage = `取件码 ${code} · ${this.orderInfo?.lockersVo?.lockerNumber ?? "-"} 号柜`
            this.pickupCodeDialogController.open()
          } else {
            showToastSafety("暂无取件码，请稍后再试")
          }
        })
      }
      .width("100%")
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_MD,
        bottom: DesignDimens.SPACE_MD
      })
    }
    .width("100%")
    .backgroundColor(DesignColor.SURFACE)
  }

  build() {
    NavDestination() {
      Stack({ alignContent: Alignment.Bottom }) {
        Column() {
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
                  .fontSize(DesignFontSize.ROOT_TITLE)
                  .fontWeight(DesignFontWeight.BOLD)
                  .fontColor(DesignColor.TEXT_PRIMARY)
                  .margin({ left: 8 })
              }
              .width("100%")
              .padding({ left: DesignDimens.PAGE_PADDING, top: 12, bottom: 8 })

              // 内容区域
              Column() {
                // 状态区（大图标 + 大字状态）
                this.StatusHeader()

                // 进度时间线（5 节点）
                Column() {
                  this.ProgressTimelineCard()
                }
                .width("100%")
                .margin({ top: DesignDimens.CARD_SPACING })

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

                // 寄存柜信息 — 待取件/待寄件时显示（保留鸿蒙条件显示，信息密度更优）
                if (this.orderInfo?.status == OrderStatusEnum.READY_FOR_PICKUP ||
                  this.orderInfo?.status == OrderStatusEnum.PENDING_SHIPMENT) {
                  InfoSectionCard({
                    title: "寄存柜信息",
                    icon: $r('sys.symbol.externaldrive_fill_3'),
                    content: () => {
                      this.InfoRowBuilder("柜号", `${this.orderInfo?.lockersVo?.lockerNumber}`)
                      this.InfoRowBuilder("取件码", this.parsePickupCode())
                    }
                  })
                  .margin({ top: DesignDimens.CARD_SPACING })
                }

                // 订单信息（后端无完成时间字段，删除原硬编码「完成时间 -」行）
                InfoSectionCard({
                  title: "订单信息",
                  icon: $r('sys.symbol.list_square_bill'),
                  content: () => {
                    this.InfoRowBuilder("订单编号", `${this.orderInfo?.orderNo}`)
                    this.InfoRowBuilder("下单时间", `${this.orderInfo?.createdAt}`)
                    this.InfoRowBuilder("当前状态", `${getOrderStatusDescription(this.orderInfo?.status)}`)
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
          .layoutWeight(1)
          .width("100%")
          .scrollBar(BarState.Off)
        }
        .width("100%")
        .height("100%")
        .backgroundColor(DesignColor.BACKGROUND)

        // 底部操作条（联系客服 + 查看取件码）
        Column() {
          this.BottomActionBar()
        }
        .width("100%")
        .zIndex(1)

        // 加载态
        if (this.isLoading) {
          Column() {
            LoadingProgress()
              .width(40)
              .height(40)
              .color(DesignColor.PRIMARY)
          }
          .width("100%")
          .height("100%")
          .justifyContent(FlexAlign.Center)
          .backgroundColor(DesignColor.BACKGROUND)
          .zIndex(2)
        }
      }
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
