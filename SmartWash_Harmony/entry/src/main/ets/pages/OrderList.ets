import { HttpStatusCode } from "@ohos/axios"
import { OrderStatusList, ShowOrderStatus, ShowOrderStatusMap } from "../constant/ShowOrderStatus"
import { cancelOrder, getOrderList } from "../network/api/OrderApi"
import { promptAction } from "@kit.ArkUI"
import { OrderInfo } from "../network/vo/OrderInfo"
import { OrderCard } from "../view/OrderCard"
import { pathStack } from "../utils/PathStackUtil"
import { LoadingFooter } from "../view/LoadingFooter"

@Builder
export function OrderListBuilder() {
  OrderList()
}

@Component
struct OrderList {
  @State orderList: Array<OrderInfo> = []
  private list = OrderStatusList()
  private orderStatus: string = "001"
  private currIndex: number = 0;
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  @State canLoad: boolean = false
  @State page: number = 1
  @State currOrderId: number | null = null

  aboutToAppear(): void {
    this.currIndex = pathStack.getParamByName("OrderList")[0] as number
    this.orderStatus = this.list[this.currIndex]
    this.getOrderListByStatus()
  }

  async getOrderListByStatus() {
    let res = await getOrderList({ status: this.orderStatus, page: this.page, size: 10 })
    if (res.code == HttpStatusCode.Ok) {
      if (res.data == null || res.data?.length == 0) {
        if (this.orderList.length > 0) {
          promptAction.showToast({ message: "没有更多数据了" })
        }
      } else {
        if (this.page == 1) {
          this.orderList = []
        }
        this.orderList.push(...res.data)
      }
    } else {
      promptAction.showToast({ message: res.message })
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

  build() {
    NavDestination() {
      Column() {
        Tabs({ index: this.currIndex }) {
          ForEach(this.list, (title: string) => {
            TabContent() {
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
                              buttons: [{ text: "取消", color: $r('sys.color.font_primary') },
                                { text: "确认", color: $r('sys.color.font_primary') }],
                            }, async (err, data) => {
                              if (data.index == 1) {
                                let res = await cancelOrder(order.orderId ?? -1)
                                if (res.code == HttpStatusCode.Ok) {
                                  this.refreshOrder()
                                  promptAction.showToast({ message: "取消成功" })
                                } else {
                                  promptAction.showToast({ message: res.message })
                                }
                              }
                            })
                          },
                          paymentClick: () => {
                            pathStack.pushPathByName("Payment", order, (popInfo) => {
                              this.refreshOrder()
                            })
                          },
                          shipmentClick: () => {
                            pathStack.pushPathByName("PickupDelivery", order, (popInfo) => {
                              this.refreshOrder()
                            })
                          },
                          pickupClick: () => {
                            pathStack.pushPathByName("PickupDelivery", order, (popInfo) => {
                              this.refreshOrder()
                            })
                          },
                          itemClick: () => {
                            pathStack.pushPathByName("OrderDetail", order.orderId)
                          },
                        })
                      }
                    })
                    ListItem() {
                      LoadingFooter({ isLoading: !!this.isLoadingMore })
                    }
                  }.onScrollIndex((start: number, end: number) => {
                    // 当达到列表末尾时，触发新数据加载
                    if (this.canLoad && end >= (this.orderList?.length ?? 0) - 1) {
                      this.canLoad = false
                      this.isLoadingMore = true
                      //插入新数据
                      setTimeout(() => {
                        this.page++
                        this.getOrderListByStatus()
                        this.isLoadingMore = false
                      }, 1200)
                    }
                  }).onScrollFrameBegin((offset: number, state: ScrollState) => {
                    // 只有当向上滑动时触发新数据加载
                    if (offset > 5 && !this.isLoadingMore) {
                      this.canLoad = true
                    }
                    return { offsetRemain: offset }
                  }).height("100%")
                }.onRefreshing(() => {
                  setTimeout(() => {
                    //下拉刷新
                    this.page = 1
                    this.getOrderListByStatus()
                    this.isRefreshing = false
                  }, 2000)
                })
              } else {
                Column() {
                  Row() {
                    SymbolGlyph($r('sys.symbol.square_slash'))
                      .fontColor([$r('sys.color.icon_primary')])
                      .fontSize(42)
                  }.margin({ bottom: 16 })

                  Row() {
                    Text("暂无订单")
                  }
                }.height("100%")
                .width("100%")
                .justifyContent(FlexAlign.Center)
              }
            }.tabBar(ShowOrderStatusMap[title])
            .align(Alignment.Top)
          })
        }.barMode(BarMode.Scrollable)
        .onChange(i => {
          this.currIndex = i
          this.orderStatus = this.list[i]
          this.refreshOrder()
        })
      }
    }.title("我的订单")
  }
}