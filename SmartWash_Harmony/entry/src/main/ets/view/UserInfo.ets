import { HttpStatusCode } from "@ohos/axios"
import { CommonConstants } from "../constant/CommonConstants"
import { getOrderItemCount } from "../network/api/OrderApi"
import { getUserInfo } from "../network/api/UserApi"
import { OrderItemCountVo } from "../network/vo/OrderItemCountVo"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { pathStack } from "../utils/PathStackUtil"
import { AccountInfoCard } from "./AccountInfoCard"
import { ItemTitle } from "./ItemTitle"
import { OrderCountCard } from "./OrderCountCard"
import { SettingRow } from "./SettingRow"
import { promptAction } from "@kit.ArkUI"
import { ShowOrderStatusEnum, ShowOrderStatusMap } from "../constant/ShowOrderStatus"

@Component
export struct UserInfo {
  private scroller: Scroller = new Scroller()
  @State userInfo: UserInfoVo | null = null
  @StorageLink('refreshUserInfo') @Watch("onUserInfoChange") refreshFlag: boolean = false
  @State orderItemCount: OrderItemCountVo | null = null
  @StorageLink('refreshOrderCount') @Watch("onOrderCountChange") orderCountFlag: boolean = false

  //监听用户信息刷新状态变化
  onUserInfoChange() {
    if (this.refreshFlag) {
      this.refreshUserInfo()
    }
    this.refreshFlag = false
  }

  //监听订单数量刷新状态变化
  onOrderCountChange() {
    if (this.orderCountFlag) {
      this.getOrderCount()
    }
    this.orderCountFlag = false
  }

  aboutToAppear() {
    this.refreshUserInfo()
    this.getOrderCount()
  }

  async refreshUserInfo() {
    let res = await getUserInfo()
    if (res.code == HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  async getOrderCount() {
    let res = await getOrderItemCount({
      pendingPaymentStatus: ShowOrderStatusEnum.PENDING_PAYMENT,
      processingStatus: ShowOrderStatusEnum.WASHING,
      pendingPickupStatus: ShowOrderStatusEnum.READY_FOR_PICKUP,
      shippedStatus: ShowOrderStatusEnum.PENDING_SHIPMENT,
    })
    if (res.code == HttpStatusCode.Ok) {
      this.orderItemCount = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  //跳转到订单列表页
  jumpToOrderList(index: number) {
    pathStack.pushPath({
      name: "OrderList", param: index
    })
  }

  build() {
    Scroll(this.scroller) {
      Column() {
        Row() {
          Text("个人中心")
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .margin({ top: 8 })

          SymbolGlyph($r('sys.symbol.gearshape'))
            .fontSize(24)
            .padding(12)
            .fontColor([$r('sys.color.font_primary')])
            .onClick(() => {
              pathStack.pushPathByName("Setting", null)
            })
        }.justifyContent(FlexAlign.SpaceBetween)
        .width("100%")

        RowSplit().height("32")

        Row() {
          Column() {
            SymbolGlyph($r('sys.symbol.person_crop_circle_fill'))
              .fontSize(48)
              .fontColor([$r('sys.color.icon_primary')])
          }
          .width(48)
          .height(48)
          .backgroundColor($r('sys.color.comp_background_primary'))
          .borderRadius(40)
          .justifyContent(FlexAlign.Center)
          .margin(8)

          Column() {
            Row() {
              Text(`${this.userInfo?.phoneNumber ?? "未登录"}`)
                .fontSize(18)
                .fontWeight(FontWeight.Bold)
            }

            Row() {
              Text(`学号：${this.userInfo?.studentId}`)
                .fontSize(14)
            }
          }.alignItems(HorizontalAlign.Start)
        }
        .width("100%")

        RowSplit().height("16")
        ItemTitle({ title: "账户信息" })
        RowSplit().height("16")

        Column() {
          AccountInfoCard({
            icon: $r('sys.symbol.yuansign_moneybag'),
            titleContent: "账户余额",
            descContent: `￥${this.userInfo?.balance}`,
            buttonContent: "充值",
            click: () => {
              pathStack.pushPathByName("Recharge", "")
            }
          })
          RowSplit().height("16")

          AccountInfoCard({
            icon: $r('sys.symbol.envelope'),
            titleContent: "校园卡",
            descContent: `${this.userInfo?.campusCard == null ? "未绑定" : this.userInfo.campusCard}`,
            buttonContent: `${this.userInfo?.campusCard == null ? "绑定" : "解绑"}`,
            click: () => {
              pathStack.pushPathByName("BingCampusCardAlert", this.userInfo, popInfo => {
                this.refreshUserInfo()
              }, false)
            }
          })

          RowSplit().height("16")
          AccountInfoCard({
            icon: $r('sys.symbol.label'),
            titleContent: "优惠券",
            descContent: "可领取优惠券",
            buttonContent: "领取",
            click: () => {
              pathStack.pushPathByName("Coupon", null)
            }
          })
        }

        RowSplit().height("22")

        ItemTitle({ title: "订单管理" })
        RowSplit().height("6")
        List() {
          ListItem() {
            OrderCountCard({
              icon: $r('sys.symbol.clock'),
              content: "待支付",
              orderCount: `${this.orderItemCount?.pendingPaymentCount ?? 0}`,
              orderCardClick: () => {
                this.jumpToOrderList(1)
              }
            })
          }

          ListItem() {
            OrderCountCard({
              icon: $r('sys.symbol.laundry'),
              content: "清洗中",
              orderCount: `${this.orderItemCount?.processingCount ?? 0}`,
              orderCardClick: () => {
                this.jumpToOrderList(3)
              }
            })
          }

          ListItem() {
            OrderCountCard({
              icon: $r('sys.symbol.envelope_open'),
              content: "待取件",
              orderCount: `${this.orderItemCount?.pendingPickupCount ?? 0}`,
              orderCardClick: () => {
                this.jumpToOrderList(4)
              }
            })
          }

          ListItem() {
            OrderCountCard({
              icon: $r('sys.symbol.arrow_counterclockwise_clock'),
              content: "历史订单",
              orderCount: "-1",
              orderCardClick: () => {
                this.jumpToOrderList(0)
              }
            })
          }
        }.listDirection(Axis.Horizontal)
        .scrollBar(BarState.Off)
        .width("100%")

        RowSplit().height("16")
        ItemTitle({ title: "其他功能" })
        RowSplit().height("16")
        Column() {
          SettingRow({
            icon: $r('sys.symbol.service'),
            title: "联系客服",
            description: "在线咨询"
          })
          Column() {
            Divider().strokeWidth(1).width("85%")
          }.width("100%")
          .alignItems(HorizontalAlign.End)
          .padding({ right: "8%" })

          SettingRow({
            icon: $r('sys.symbol.questionmark_circle'),
            title: "常见问题",
            description: "使用指南"
          })
        }.width("100%")
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)
        .backgroundColor($r('sys.color.comp_background_primary'))

        RowSplit().height("72")
      }
    }
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor($r('sys.color.background_primary'))
    .padding({ left: 16, right: 16 })
  }
}