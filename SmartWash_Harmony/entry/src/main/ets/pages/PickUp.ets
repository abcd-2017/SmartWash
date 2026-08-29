import { HttpStatusCode } from "@ohos/axios"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getOrderList } from "../network/api/OrderApi"
import { OrderStatusEnum } from "../constant/OrderStatus"
import { LoadingFooter } from "../view/LoadingFooter"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

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
    Promise.all([this.refreshUserInfo(), this.getPickupOrder()])
  }

  async getPickupOrder() {
    let res = await getOrderList({
      status: OrderStatusEnum.READY_FOR_PICKUP,
      page: this.page, size: 10
    })
    if (res.code === HttpStatusCode.Ok) {
      if (res.data == null || res.data.length == 0) {
        if (this.orderList?.length > 0) {
          showToastSafety("没有更多数据了")
        }
      } else {
        if (this.page == 1) {
          this.orderList = []
        }
        this.orderList?.push(...res.data)
      }
    } else {
      showToastSafety(res.message, "取件订单加载失败")
    }
  }

  async refreshUserInfo() {
    let res = await getUserInfo()
    if (res.code === HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      showToastSafety(res.message, "用户信息加载失败")
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
        // 订单号 + 寄存柜号
        Row() {
          Column() {
            Row() {
              Text(`订单号：${order.orderNo}`)
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }

            Row() {
              Text(`寄存柜：${order.lockersVo?.lockerNumber}`)
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }.margin({ top: 4 })
          }.alignItems(HorizontalAlign.Start)

          ColumnSplit().layoutWeight(1)
          Column() {
            Row() {
              Text(`￥${order.totalPrice}`)
                .fontSize(18)
                .fontWeight(FontWeight.Bold)
                .fontColor(DesignColor.PRIMARY)
            }
          }
        }.width("100%")

        // 分隔线
        Row()
          .width("100%")
          .height(0.5)
          .backgroundColor(DesignColor.DIVIDER)
          .margin({ top: 12, bottom: 12 })

        // 取件码
        Row() {
          Text("取件码")
            .fontSize(DesignFontSize.BODY)
            .fontColor(DesignColor.TEXT_SECONDARY)
          ColumnSplit().layoutWeight(1)
          Text(`${order.pickupCode?.split(":").pop() ?? ""}`)
            .fontSize(24)
            .fontColor(DesignColor.PRIMARY)
            .fontWeight(FontWeight.Bold)
        }.width("100%")
      }
      .padding(DesignDimens.CARD_PADDING)
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 0.5, color: DesignColor.OUTLINE })
      .onClick(() => {
        pathStack.pushPathByName("PickupDelivery", order)
      })
    }
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: 4,
      bottom: 4
    })
  }

  build() {
    NavDestination() {
      Column() {
        // 自定义 PageHeader
        Row() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DesignColor.TEXT_PRIMARY])
            .onClick(() => {
              pathStack.pop() // NavPathStack 无 back()，出栈统一用 pop()
            })

          Text("取件")
            .fontSize(DesignFontSize.PAGE_TITLE)
            .fontWeight(FontWeight.Bold)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ left: 8 })
        }
        .width("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, top: 12, bottom: 8 })

        // 学校信息行
        Row() {
          SymbolGlyph($r('sys.symbol.company'))
            .fontColor([DesignColor.PRIMARY])
            .fontSize(18)
            .margin({ right: 8 })
          Text(`${this.userInfo?.schoolVo?.schoolName}`)
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(FontWeight.Bold)
            .fontColor(DesignColor.TEXT_PRIMARY)
        }.justifyContent(FlexAlign.Start)
        .width("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 4, bottom: 4 })

        if ((this.orderList?.length ?? 0) > 0) {
          Refresh({ refreshing: $$this.isRefreshing }) {
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.orderList, (order: OrderInfo) => {
                ListItem() {
                  this.PickupOrderCard(order)
                }
              })
              ListItem() {
                LoadingFooter({ isLoading: this.isLoadingMore })
              }
            }.onScrollIndex((start: number, end: number) => {
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
            SymbolGlyph($r('sys.symbol.square_slash'))
              .fontColor([DesignColor.TEXT_TERTIARY])
              .fontSize(48)
              .margin({ bottom: 16 })

            Text("暂无可取件订单")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }.height("100%")
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
      // 每次展示先重置分页再拉取，避免从寄件页返回后 page>1 继续追加重复数据（评审 #14）
      this.isRefreshing = false
      this.isLoadingMore = false
      this.canLoad = false
      this.page = 1
      this.getPickupOrder()
    })
  }
}
