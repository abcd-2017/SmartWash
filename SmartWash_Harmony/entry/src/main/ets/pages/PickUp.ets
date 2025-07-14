import { HttpStatusCode } from "@ohos/axios"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { UserInfo } from "../view/UserInfo"
import { promptAction } from "@kit.ArkUI"
import { OrderInfo } from "../network/vo/OrderInfo"
import { CommonConstants } from "../constant/CommonConstants"
import { getOrderList } from "../network/api/OrderApi"
import { OrderStatusEnum } from "../constant/OrderStatus"
import { LoadingFooter } from "../view/LoadingFooter"
import { pathStack } from "../utils/PathStackUtil"

@Builder
export function PickUpBuilder() {
  PickUp()
}

@Component
struct PickUp {
  @State userInfo: UserInfoVo | null = null
  @State orderList: Array<OrderInfo> = []
  @State page: number = 1
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  @State canLoad: boolean = false

  aboutToAppear(): void {
    this.refreshUserInfo()
    this.getPickupOrder()
  }

  async getPickupOrder() {
    let res = await getOrderList({
      status: OrderStatusEnum.READY_FOR_PICKUP,
      page: this.page, size: 10
    })
    if (res.code == HttpStatusCode.Ok) {
      if (res.data == null || res.data.length == 0) {
        if (this.orderList?.length > 0) {
          promptAction.showToast({ message: "没有更多数据了" })
        }
      } else {
        if (this.page == 1) {
          this.orderList = []
        }
        this.orderList?.push(...res.data)
      }
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  async refreshUserInfo() {
    let res = await getUserInfo()
    if (res.code == HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  refreshOrder() {
    this.isRefreshing = false
    this.isLoadingMore = false
    this.canLoad = false
    this.page = 1
    this.orderList = []
    this.getPickupOrder()
  }

  @Builder
  PickupOrderCard(order: OrderInfo) {
    Column() {
      Column() {
        Row() {
          Column() {
            Row() {
              Text(`订单号：${order.orderNo}`)
                .fontSize(14)
            }

            Row() {
              Text(`寄存柜：${order.lockersVo?.lockerNumber}`)
                .fontSize(14)
            }.margin({ top: 4 })
          }.alignItems(HorizontalAlign.Start)

          ColumnSplit().layoutWeight(1)
          Column() {
            Row() {
              Text(`￥${order.totalPrice}`)
                .fontSize(18)
                .fontWeight(FontWeight.Bold)
                .fontColor($r('sys.color.brand'))
            }
          }
        }.width("100%")

        Row() {
          Text("取件码：").fontSize(14)
          ColumnSplit().layoutWeight(1)
          Text(`${order.pickupCode?.split(":")[2]}`)
            .fontSize(20)
            .fontColor($r('sys.color.brand'))
            .fontWeight(FontWeight.Bold)
        }.margin({ top: 4 })
      }
      .padding(16)
      .backgroundColor($r('sys.color.comp_background_primary'))
      .borderRadius(CommonConstants.BORDER_RADIUS)
      .shadow(ShadowStyle.OUTER_DEFAULT_SM)
      .onClick(() => {
        pathStack.pushPathByName("PickupDelivery", order)
      })
    }
    .padding({
      left: 16,
      right: 16,
      top: 4,
      bottom: 4
    })
  }

  build() {
    NavDestination() {
      Scroll() {
        Column() {
          Row() {
            SymbolGlyph($r('sys.symbol.company'))
              .fontColor([$r('sys.color.font_primary')])
              .fontSize(18)
              .margin({ right: 4 })
            Text(`${this.userInfo?.schoolVo?.schoolName}`)
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
          }.justifyContent(FlexAlign.Start)
          .width("100%")
          .padding({ left: 16, right: 16, top: 4 })

          if ((this.orderList?.length ?? 0) > 0) {
            Refresh({ refreshing: $$this.isRefreshing }) {
              List({ space: 8 }) {
                ForEach(this.orderList, (order: OrderInfo) => {
                  ListItem() {
                    this.PickupOrderCard(order)
                  }
                })
                ListItem() {
                  LoadingFooter({ isLoading: this.isLoadingMore })
                }
              }.onScrollIndex((start: number, end: number) => {
                // 当达到列表末尾时，触发新数据加载
                if (this.canLoad && end >= (this.orderList.length) - 1) {
                  this.canLoad = false
                  this.isLoadingMore = true
                  setTimeout(() => {
                    this.page++
                    this.getPickupOrder()
                    this.isLoadingMore = false
                  }, 1200)
                }
              }).onScrollFrameBegin((offset: number, state: ScrollState) => {
                // 只有当向上滑动时触发新数据加载
                if (offset > 5 && !this.isLoadingMore) {
                  this.canLoad = true
                }
                return { offsetRemain: offset }
              })
            }.onRefreshing(() => {
              setTimeout(() => {
                this.page = 1
                this.getPickupOrder()
                this.isRefreshing = false
              }, 2000)
            }).height("100%")
            .padding({ top: 12, bottom: 8 })
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
        }
        .width("100%")
        .height("100%")
        .align(Alignment.Top)
        .backgroundColor($r('sys.color.background_primary'))
      }.scrollBar(BarState.Off)
    }.title("取件").onShown(() => {
      this.getPickupOrder()
    })
  }
}