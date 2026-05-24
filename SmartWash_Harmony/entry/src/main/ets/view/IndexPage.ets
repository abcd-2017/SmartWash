import { HttpStatusCode } from "@ohos/axios"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { pathStack } from "../utils/PathStackUtil"
import { promptAction } from "@kit.ArkUI"
import { CommonConstants } from "../constant/CommonConstants"
import { OrderVo } from "../network/vo/OrderVo"
import { getWashingOrder } from "../network/api/OrderApi"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { getOrderStatusDescription } from "../constant/OrderStatus"

@ComponentV2
export struct IndexPage {
  private scroller = new Scroller()
  @Local userInfo: UserInfoVo | null = null
  @Event userInfoClick: () => void
  @Local orderList: Array<OrderVo> | null = []
  @Param isActive: boolean = true
  private pollingTimer: number = -1

  aboutToAppear() {
    this.refreshUserInfo()
    if (this.isActive) {
      this.startPolling()
    }
  }

  aboutToDisappear() {
    this.stopPolling()
  }

  onDidUpdate() {
    if (this.isActive) {
      if (this.pollingTimer === -1) {
        this.getOrderList()
        this.startPolling()
      }
    } else {
      this.stopPolling()
    }
  }

  private startPolling() {
    this.pollingTimer = setInterval(() => {
      this.getOrderList()
    }, 5000)
  }

  private stopPolling() {
    if (this.pollingTimer !== -1) {
      clearInterval(this.pollingTimer)
      this.pollingTimer = -1
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

  async getOrderList() {
    let res = await getWashingOrder();
    if (res.code == HttpStatusCode.Ok) {
      this.orderList = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  /**
   * 根据当前小时获取问候语
   */
  private getGreeting(): string {
    let hour = new Date().getHours()
    if (hour < 12) {
      return "早上好"
    } else if (hour < 18) {
      return "下午好"
    } else {
      return "晚上好"
    }
  }

  /**
   * 根据订单状态计算进度值
   */
  private getOrderProgress(status: string | null): number {
    switch (status) {
      case "0":
        return 10
      case "1":
        return 25
      case "2":
        return 35
      case "3":
        return 55
      case "4":
        return 70
      case "5":
        return 85
      case "6":
        return 95
      case "7":
        return 100
      default:
        return 0
    }
  }

  /**
   * 问候头部 — 左侧问候语+学校地址，右侧圆形头像
   */
  @Builder
  GreetingHeader() {
    Row() {
      // 左侧问候信息
      Column() {
        Text(this.getGreeting())
          .fontSize(DesignFontSize.PAGE_TITLE)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(Color(DesignColor.ON_BACKGROUND))

        Row() {
          SymbolGlyph($r('sys.symbol.location_fill'))
            .fontSize(14)
            .fontColor([Color(DesignColor.TEXT_SECONDARY)])
          Text(this.userInfo?.schoolVo?.schoolName ?? "")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(Color(DesignColor.TEXT_SECONDARY))
            .margin({ left: 4 })
        }
        .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.Start)

      Blank()

      // 右侧圆形头像按钮
      Column() {
        SymbolGlyph($r('sys.symbol.person_crop_circle_fill'))
          .fontSize(22)
          .fontColor([Color(DesignColor.PRIMARY)])
      }
      .width(40)
      .height(40)
      .borderRadius(20)
      .backgroundColor(Color(DesignColor.PRIMARY_LIGHT))
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .onClick(() => {
        this.userInfoClick()
      })
    }
    .width("100%")
    .alignItems(VerticalAlign.Center)
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: 16,
      bottom: 8
    })
  }

  /**
   * 账户概览卡 — 渐变绿底，显示余额和去充值按钮
   */
  @Builder
  AccountOverviewCard() {
    Column() {
      Row() {
        // 左侧余额
        Column() {
          Text("账户余额")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(Color.White)
            .opacity(0.7)
          Text(`￥${(this.userInfo?.balance ?? 0).toFixed(2)}`)
            .fontSize(DesignFontSize.PAGE_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(Color.White)
            .margin({ top: 4 })
        }
        .alignItems(HorizontalAlign.Start)

        Blank()

        // 右侧充值入口
        Column() {
          Text("可用优惠券")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(Color.White)
            .opacity(0.7)
          Row() {
            Text("去充值 >")
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontColor(Color.White)
          }
          .margin({ top: 4 })
        }
        .alignItems(HorizontalAlign.End)
        .onClick(() => {
          pathStack.pushPathByName("Recharge", null)
        })
      }
      .width("100%")
      .alignItems(VerticalAlign.Bottom)
    }
    .width("100%")
    .borderRadius(DesignDimens.CARD_RADIUS)
    .padding(20)
    .linearGradient({
      direction: GradientDirection.Right,
      colors: [[Color(DesignColor.PRIMARY), 0.0], [Color(DesignColor.PRIMARY_DARK), 1.0]]
    })
  }

  /**
   * 服务入口卡片 — 白底+边框样式
   */
  @Builder
  ServiceEntryCard(icon: Resource, title: string, itemClick: () => void) {
    Column() {
      // 图标容器
      Column() {
        SymbolGlyph(icon)
          .fontSize(22)
          .fontColor([Color(DesignColor.PRIMARY)])
      }
      .width(44)
      .height(44)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(Color(DesignColor.PRIMARY_LIGHT))
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      Text(title)
        .fontSize(DesignFontSize.CARD_TITLE)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(Color(DesignColor.TEXT_PRIMARY))
        .margin({ top: 8 })
    }
    .layoutWeight(1)
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.SMALL_CARD_RADIUS)
    .border({ width: 0.5, color: Color(DesignColor.OUTLINE) })
    .backgroundColor(Color(DesignColor.SURFACE))
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .onClick(() => {
      itemClick()
    })
  }

  /**
   * 订单卡片 — 白底+边框，左侧图标+状态+订单号，右侧价格，底部进度条
   */
  @Builder
  OrderItemCard(order: OrderVo) {
    Column() {
      Row() {
        // 左侧绿色图标容器
        Column() {
          SymbolGlyph($r('sys.symbol.laundry'))
            .fontSize(20)
            .fontColor([Color(DesignColor.PRIMARY)])
        }
        .width(40)
        .height(40)
        .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
        .backgroundColor(Color(DesignColor.PRIMARY_LIGHT))
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .margin({ right: 12 })

        // 状态文字 + 订单号
        Column() {
          Text(getOrderStatusDescription(order.status ?? "") ?? "")
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(Color(DesignColor.TEXT_PRIMARY))
          Text(`订单号：${order.orderNo ?? ""}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(Color(DesignColor.TEXT_SECONDARY))
            .margin({ top: 4 })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        // 右侧价格
        Text(`￥${order.payPrice ?? 0}`)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(Color(DesignColor.PRIMARY))
      }
      .width("100%")
      .alignItems(VerticalAlign.Center)

      // 底部进度条
      Progress({
        value: this.getOrderProgress(order.status),
        total: 100,
        type: ProgressType.Linear
      })
        .width("100%")
        .height(4)
        .margin({ top: 12 })
        .borderRadius(2)
        .color(Color(DesignColor.PRIMARY))
        .backgroundColor(Color(DesignColor.PRIMARY_LIGHT))
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 0.5, color: Color(DesignColor.OUTLINE) })
    .backgroundColor(Color(DesignColor.SURFACE))
    .onClick(() => {
      pathStack.pushPathByName("OrderDetail", order.orderId)
    })
  }

  /**
   * 服务须知 — 底部提示文字
   */
  @Builder
  ServiceTips() {
    Row() {
      SymbolGlyph($r('sys.symbol.info_circle_fill'))
        .fontSize(14)
        .fontColor([Color(DesignColor.TEXT_TERTIARY)])
      Text("请在营业时间内完成取件，超时将收取额外保管费用")
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(Color(DesignColor.TEXT_TERTIARY))
        .margin({ left: 6 })
    }
    .width("100%")
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }

  build() {
    Scroll(this.scroller) {
      Column() {
        // 1. 问候头部
        this.GreetingHeader()

        // 2. 账户概览卡
        Column() {
          this.AccountOverviewCard()
        }
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

        // 3. 服务网格
        Text("洗衣服务")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(Color(DesignColor.ON_BACKGROUND))
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          .margin({ top: DesignDimens.SECTION_SPACING })

        Row() {
          this.ServiceEntryCard($r('sys.symbol.laundry'), "立即预约", () => {
            pathStack.pushPathByName("Laundry", null)
          })
          Row() {
          }.width(DesignDimens.CARD_SPACING)
          this.ServiceEntryCard($r('sys.symbol.book_pages'), "取回衣物", () => {
            pathStack.pushPathByName("PickUp", null)
          })
          Row() {
          }.width(DesignDimens.CARD_SPACING)
          this.ServiceEntryCard($r('sys.symbol.label'), "优惠券", () => {
            pathStack.pushPathByName("Coupon", null)
          })
        }
        .width("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .margin({ top: 12 })

        // 4. 进行中订单
        Row() {
          Text("进行中订单")
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(Color(DesignColor.ON_BACKGROUND))

          Blank()

          Text("查看全部")
            .fontSize(DesignFontSize.BODY)
            .fontColor(Color(DesignColor.PRIMARY))
            .onClick(() => {
              pathStack.pushPathByName("OrderList", 3)
            })
        }
        .width("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .margin({ top: DesignDimens.SECTION_SPACING })
        .alignItems(VerticalAlign.Center)

        // 订单列表
        Column() {
          if ((this.orderList?.length ?? 0) > 0) {
            ForEach(this.orderList ?? [], (order: OrderVo) => {
              this.OrderItemCard(order)
            })
          } else {
            Text("暂无进行中的订单")
              .fontSize(DesignFontSize.BODY)
              .fontColor(Color(DesignColor.TEXT_SECONDARY))
              .width("100%")
              .height(60)
              .textAlign(TextAlign.Center)
          }
        }
        .width("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .margin({ top: 12 })

        // 5. 服务须知
        this.ServiceTips()
          .margin({ top: DesignDimens.SECTION_SPACING })

        // 底部间距
        Row() {
        }.height(72)
      }
    }
    .scrollBar(BarState.Off)
    .width("100%")
    .height("100%")
    .align(Alignment.TopStart)
    .backgroundColor(Color(DesignColor.BACKGROUND))
  }

  onUserInfoChange(changedPropertyName: string) {
  }
}
