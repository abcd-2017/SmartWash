import { HttpStatusCode } from "@ohos/axios"
import { OrderStatusList, ShowOrderStatusMap, ShowOrderStatus } from "../constant/ShowOrderStatus"
import { cancelOrder, getOrderList } from "../network/api/OrderApi"
import { promptAction } from "@kit.ArkUI"
import { OrderInfo } from "../network/vo/OrderInfo"
import { OrderCard } from "../view/OrderCard"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { LoadingFooter } from "../view/LoadingFooter"
import { showToastSafety } from "../utils/ToastUtil"
import { DesignColor, DesignDimens, DesignFontSize } from "../constant/DesignSystem"

@Builder
export function OrderListBuilder() {
  OrderList()
}

@Component
struct OrderList {
  @State orderList: Array<OrderInfo> = []
  private list = OrderStatusList()
  private orderStatus: string = "001"
  @State currIndex: number = 0
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  @State canLoad: boolean = false
  @State page: number = 1
  @State currOrderId: number | null = null

  aboutToAppear(): void {
    // 安全取参（评审 #31）：参数缺失/非法时兜底第 0 个页签
    this.currIndex = getNumberParam("OrderList") ?? 0
    this.orderStatus = this.list[this.currIndex]
    this.getOrderListByStatus()
  }

  async getOrderListByStatus() {
    let res = await getOrderList({ status: this.orderStatus, page: this.page, size: 10 })
    if (res.code === HttpStatusCode.Ok) {
      if (res.data == null || res.data?.length == 0) {
        if (this.orderList.length > 0) {
          showToastSafety("没有更多数据了")
        }
      } else {
        if (this.page == 1) {
          this.orderList = []
        }
        this.orderList.push(...res.data)
      }
    } else {
      showToastSafety(res.message, "订单加载失败")
    }
  }

  aboutToDisappear(): void {
    AppStorage.setOrCreate("refreshOrderCount", true)
    AppStorage.setOrCreate("refreshUserInfo", true)
  }

  refreshOrder() {
    this.isRefreshing = false
    this.isLoadingMore = false
    this.canLoad = false
    this.page = 1
    this.orderList = []
    this.getOrderListByStatus()
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
        .fontWeight(FontWeight.Bold)
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
          Text(ShowOrderStatusMap[title as ShowOrderStatus])
            .fontSize(DesignFontSize.BODY)
            .fontWeight(index === this.currIndex ? FontWeight.Bold : FontWeight.Normal)
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
        if ((this.orderList?.length ?? 0) > 0) {
          Refresh({ refreshing: $$this.isRefreshing }) {
            List() {
              ForEach(this.orderList, (order: OrderInfo) => {
                ListItem() {
                  OrderCard({
                    orderInfo: order,
                    cancelClick: () => {
                      this.currOrderId = order.orderId
                      promptAction.showDialog({
                        message: "确定要取消订单",
                        buttons: [
                          { text: "取消", color: DesignColor.TEXT_PRIMARY },
                          { text: "确认", color: DesignColor.PRIMARY }
                        ],
                      }, async (err, data) => {
                        if (data.index == 1) {
                          let res = await cancelOrder(order.orderId ?? -1)
                          if (res.code === HttpStatusCode.Ok) {
                            this.refreshOrder()
                            showToastSafety("取消成功")
                          } else {
                            showToastSafety(res.message, "取消失败")
                          }
                        }
                      })
                    },
                    paymentClick: () => {
                      // 返回时刷新交给 NavDestination.onShown（pop() 无 result 时 onPop 不触发，评审 #8）
                      pathStack.pushPathByName("Payment", order.orderId)
                    },
                    shipmentClick: () => {
                      pathStack.pushPathByName("PickupDelivery", order)
                    },
                    pickupClick: () => {
                      pathStack.pushPathByName("PickupDelivery", order)
                    },
                    itemClick: () => {
                      pathStack.pushPathByName("OrderDetail", order.orderId)
                    },
                  })
                }
                .padding({
                  left: DesignDimens.PAGE_PADDING,
                  right: DesignDimens.PAGE_PADDING,
                  top: 6,
                  bottom: 6
                })
              })
              ListItem() {
                LoadingFooter({ isLoading: !!this.isLoadingMore })
              }
            }
            .onScrollIndex((start: number, end: number) => {
              if (this.canLoad && end >= (this.orderList?.length ?? 0) - 1) {
                this.canLoad = false
                this.isLoadingMore = true
                setTimeout(() => {
                  this.page++
                  this.getOrderListByStatus()
                  this.isLoadingMore = false
                }, 1200)
              }
            })
            .onScrollFrameBegin((offset: number, state: ScrollState) => {
              if (offset > 5 && !this.isLoadingMore) {
                this.canLoad = true
              }
              return { offsetRemain: offset }
            })
            .height("100%")
          }
          .onRefreshing(() => {
            setTimeout(() => {
              this.page = 1
              this.getOrderListByStatus()
              this.isRefreshing = false
            }, 2000)
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
      this.isRefreshing = false
      this.isLoadingMore = false
      this.canLoad = false
      this.page = 1
      this.getOrderListByStatus()
    })
  }
}
