import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { OrderStatusList, getShowOrderStatusDescription } from "../constant/ShowOrderStatus"
import { cancelOrder, getOrderList } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { OrderMonthCard } from "../view/OrderCard"
import { PickupDeliveryParam } from "./PickupDelivery"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { LoadingFooter } from "../view/LoadingFooter"
import { showToastSafety } from "../utils/ToastUtil"
import { PagingController } from "../utils/PagingUtil"
import { AppConfirmDialog } from "../view/common/AppComponents"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

// 分页每页条数
const PAGE_SIZE: number = 10

/** 月份分组段（对齐安卓 OrderListItem 混合列表项：月份标题 + 整月订单组） */
interface MonthSection {
  monthKey: string
  orders: Array<OrderInfo>
}

@Builder
export function OrderListBuilder() {
  OrderList()
}

@Component
struct OrderList {
  @State sections: Array<MonthSection> = []
  private list = OrderStatusList()
  private orderStatus: string = "001"
  @State currIndex: number = 0
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  // 取消订单确认弹窗（对齐安卓 AppConfirmDialog，替代系统 promptAction 弹窗）
  private confirmCancelController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      message: "确定要取消订单？",
      onConfirm: () => {
        this.doCancelOrder()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })
  // 待取消的订单 id（确认弹窗回调用）
  private pendingCancelOrderId: number | null = null
  // 轻量分页封装：触底加载/上锁防重/封底判定统一收敛（差异分析 01 §3.3）
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchOrderPage(page)
    },
    (loading: boolean) => {
      this.isLoadingMore = loading
    }
  )

  aboutToAppear(): void {
    // 安全取参（评审 #31）：参数缺失/非法时兜底第 0 个页签
    this.currIndex = getNumberParam(RouteNames.ORDER_LIST) ?? 0
    this.orderStatus = this.list[this.currIndex]
    this.fetchOrderPage(1)
  }

  /**
   * 按页拉取订单列表。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchOrderPage(page: number): Promise<boolean> {
    let res = await getOrderList({ status: this.orderStatus, page: page, size: PAGE_SIZE })
    if (res.code === HttpStatusCode.Ok) {
      if (res.data == null || res.data?.length == 0) {
        if (this.orderListLength() > 0) {
          showToastSafety("没有更多数据了")
        }
        return false
      }
      if (page == 1) {
        this.sections = []
      }
      this.appendOrders(res.data)
      return true
    }
    showToastSafety(res.message, "订单加载失败")
    return false
  }

  /** 当前状态下的订单总数（跨月份段累计） */
  private orderListLength(): number {
    let total = 0
    for (const s of this.sections) {
      total += s.orders.length
    }
    return total
  }

  /**
   * 追加订单并重建月份分组（D2 决策：数据源维持单状态分页拉取，
   * 列表结构对齐安卓——按 createdAt 降序、按 yyyy-MM 分组，组间倒序排列）
   */
  private appendOrders(data: Array<OrderInfo>): void {
    const flat: Array<OrderInfo> = []
    for (const s of this.sections) {
      flat.push(...s.orders)
    }
    flat.push(...data)
    flat.sort((a: OrderInfo, b: OrderInfo) => (b.createdAt ?? "").localeCompare(a.createdAt ?? ""))

    const grouped: Map<string, Array<OrderInfo>> = new Map()
    for (const order of flat) {
      const key = (order.createdAt ?? "").substring(0, 7)
      const bucket = grouped.get(key)
      if (bucket === undefined) {
        grouped.set(key, [order])
      } else {
        bucket.push(order)
      }
    }
    const keys = Array.from(grouped.keys()).sort((a: string, b: string) => b.localeCompare(a))
    const next: Array<MonthSection> = []
    for (const key of keys) {
      const orders = grouped.get(key)
      if (orders !== undefined) {
        const section: MonthSection = { monthKey: key, orders: orders }
        next.push(section)
      }
    }
    this.sections = next
  }

  aboutToDisappear(): void {
    AppStorage.setOrCreate("refreshOrderCount", true)
    AppStorage.setOrCreate("refreshUserInfo", true)
  }

  refreshOrder() {
    this.isRefreshing = false
    this.paging.reset()
    this.sections = []
    this.fetchOrderPage(1)
  }

  /** 确认取消订单：成功后 toast + 当前状态全量重拉，并打标记让订单计数同步刷新 */
  async doCancelOrder() {
    if (this.pendingCancelOrderId === null) {
      return
    }
    let res = await cancelOrder(this.pendingCancelOrderId)
    if (res.code === HttpStatusCode.Ok) {
      // 全量刷新语义（对齐安卓 loadAllOrders）：单状态分页下即整列表重置重拉，
      // 其余页签切换时本就会重新拉取；同时同步用户中心订单计数
      AppStorage.setOrCreate("refreshOrderCount", true)
      this.refreshOrder()
      showToastSafety("取消成功")
    } else {
      showToastSafety(res.message, "取消失败")
    }
    this.pendingCancelOrderId = null
  }

  @Builder
  PageHeader() {
    Row() {
      SymbolGlyph($r('sys.symbol.chevron_left'))
        .fontSize(24)
        .fontColor([DesignColor.TEXT_PRIMARY])
        .onClick(() => {
          pathStack.pop()
        })

      Text("我的订单")
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ left: 8 })
    }
    .width("100%")
    .height(56)
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(DesignColor.BACKGROUND)
  }

  @Builder
  TabBar() {
    Row() {
      ForEach(this.list, (title: string, index: number) => {
        Column() {
          Text(getShowOrderStatusDescription(title))
            .fontSize(DesignFontSize.BODY)
            .fontWeight(index === this.currIndex ? DesignFontWeight.BOLD : DesignFontWeight.NORMAL)
            .fontColor(index === this.currIndex ? DesignColor.PRIMARY : DesignColor.TEXT_SECONDARY)

          // 底部短横线指示器
          Divider()
            .strokeWidth(2)
            .width(24)
            .color(index === this.currIndex ? DesignColor.PRIMARY : Color.Transparent)
            .margin({ top: 6 })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        .onClick(() => {
          if (this.currIndex !== index) {
            this.currIndex = index
            this.orderStatus = this.list[index]
            this.refreshOrder()
          }
        })
      })
    }
    .width("100%")
    .height(48)
    .backgroundColor(DesignColor.BACKGROUND)
  }

  build() {
    NavDestination() {
      Column() {
        // 自定义页面标题
        this.PageHeader()

        // 自定义 Tab 标签栏
        this.TabBar()

        // 分隔线
        Divider()
          .strokeWidth(0.5)
          .color(DesignColor.DIVIDER)

        // 内容区域
        if (this.orderListLength() > 0) {
          Refresh({ refreshing: $$this.isRefreshing }) {
            List() {
              ForEach(this.sections, (section: MonthSection) => {
                // 月份标题（对齐安卓 "%d年%d月" 月份行）
                ListItem() {
                  Text(this.monthLabel(section.monthKey))
                    .fontSize(DesignFontSize.CAPTION)
                    .fontWeight(DesignFontWeight.SEMI_BOLD)
                    .fontColor(DesignColor.TEXT_SECONDARY)
                    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 24, bottom: 8 })
                }

                // 整月订单合并卡
                ListItem() {
                  OrderMonthCard({
                    orders: section.orders,
                    cancelClick: (order: OrderInfo) => {
                      this.pendingCancelOrderId = order.orderId
                      this.confirmCancelController.open()
                    },
                    paymentClick: (order: OrderInfo) => {
                      // 返回时刷新交给 NavDestination.onShown（pop() 无 result 时 onPop 不触发，评审 #8）
                      pathStack.pushPathByName(RouteNames.PAYMENT, order.orderId)
                    },
                    shipmentClick: (order: OrderInfo) => {
                      const param: PickupDeliveryParam = new PickupDeliveryParam()
                      param.order = order
                      param.isPickup = false
                      pathStack.pushPathByName(RouteNames.PICKUP_DELIVERY, param)
                    },
                    pickupClick: (order: OrderInfo) => {
                      const param: PickupDeliveryParam = new PickupDeliveryParam()
                      param.order = order
                      param.isPickup = true
                      pathStack.pushPathByName(RouteNames.PICKUP_DELIVERY, param)
                    },
                    itemClick: (order: OrderInfo) => {
                      pathStack.pushPathByName(RouteNames.ORDER_DETAIL, order.orderId)
                    },
                  })
                }
                .padding({
                  left: DesignDimens.PAGE_PADDING,
                  right: DesignDimens.PAGE_PADDING,
                  bottom: 6
                })
              }, (section: MonthSection) => section.monthKey)

              ListItem() {
                LoadingFooter({ isLoading: !!this.isLoadingMore })
              }
            }
            .onScrollIndex((start: number, end: number) => {
              this.paging.onScrollIndex(end, this.orderListLength())
            })
            .onScrollFrameBegin((offset: number, state: ScrollState) => {
              this.paging.onScrollFrameBegin(offset)
              return { offsetRemain: offset }
            })
            .height("100%")
          }
          .onRefreshing(() => {
            this.refreshOrder()
          })
        } else {
          // 空状态
          Column() {
            SymbolGlyph($r('sys.symbol.square_slash'))
              .fontColor([DesignColor.PRIMARY])
              .fontSize(56)
              .margin({ bottom: 16 })

            Text("暂无订单")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }
          .height("100%")
          .width("100%")
          .justifyContent(FlexAlign.Center)
        }
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 支付/寄件等 pop() 均不带 result，原 onPop 回调不触发（评审 #8）；
      // 改为每次展示时重置分页后重拉：page==1 分支会在响应返回后整体替换列表，避免追加重复数据
      this.refreshOrder()
    })
  }

  /** "yyyy-MM" → "yyyy年M月"（解析失败兜底原串） */
  private monthLabel(monthKey: string): string {
    const parts = monthKey.split("-")
    if (parts.length === 2) {
      return `${parts[0]}年${Number(parts[1])}月`
    }
    return monthKey
  }
}
