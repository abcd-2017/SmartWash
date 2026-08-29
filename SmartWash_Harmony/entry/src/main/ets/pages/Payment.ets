import { HttpStatusCode } from "@ohos/axios"
import { calculationOrder, getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { pathStack } from "../utils/PathStackUtil"
import { promptAction } from "@kit.ArkUI"
import { getCanUseCoupon } from "../network/api/CouponApi"
import { UserCouponVo } from "../network/vo/UserCouponVo"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { payment } from "../network/api/PaymentApi"
import { showToastSafety } from "../utils/ToastUtil"
import { PaymentType } from "../constant/PaymentType"
import { DesignColor, DesignDimens, DesignFontSize } from "../constant/DesignSystem"

@Builder
export function PaymentBuilder() {
  Payment()
}

@Component
struct Payment {
  @State orderId: number | null = null
  @State orderInfo: OrderInfo | null = null
  @State userCoupon: Array<UserCouponVo> | null = []
  @State userInfo: UserInfoVo | null = null
  @State selectCoupon: UserCouponVo | null = null
  @State showSheet: boolean = false
  @State selectCouponId: number | null = null
  // 支付请求进行中标记：按钮展示 loading 并拦截连点，防止重复扣款（评审 #12）
  @State paying: boolean = false

  async aboutToAppear() {
    // 上游（Laundry/OrderList）统一传 number 类型 orderId；
    // Number() 显式转换替代 as 强转（评审 #6），NaN 视为参数缺失/非法
    const raw = pathStack.getParamByName("Payment")[0]
    const id = Number(raw)
    this.orderId = Number.isNaN(id) ? null : id
    if (this.orderId !== null) {
      let res = await getOrderInfo(this.orderId)
      if (res.code === HttpStatusCode.Ok) {
        this.orderInfo = res.data
      } else {
        showToastSafety(res.message, "订单信息加载失败")
      }
    }
  }

  async getCurrUserInfo() {
    let res = await getUserInfo()
    if (res.code === HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      showToastSafety(res.message, "用户信息加载失败")
    }
  }

  async getUserCoupons() {
    let couponRes = await getCanUseCoupon(this.orderId ?? -1)
    if (couponRes.code === HttpStatusCode.Ok) {
      this.userCoupon = couponRes.data
    } else {
      showToastSafety(couponRes.message, "优惠券加载失败")
    }
  }

  async calculationOrder(coupon: UserCouponVo) {
    this.selectCouponId = coupon.userCouponId
    this.showSheet = false
    this.selectCoupon = coupon
    if (this.orderId !== null) {
      let res = await calculationOrder(this.orderId, coupon.userCouponId ?? -1)
      if (res.code === HttpStatusCode.Ok) {
        this.orderInfo = res.data
      } else {
        showToastSafety(res.message, "优惠券试算失败")
      }
    }
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

      Text("支付")
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
  BottomSheet() {
    Column() {
      // 标题栏
      Row() {
        Text("选择优惠券")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(FontWeight.Bold)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Blank()

        Row() {
          Text("去领取")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.PRIMARY)

          SymbolGlyph($r('sys.symbol.chevron_right'))
            .fontSize(14)
            .fontColor([DesignColor.PRIMARY])
            .margin({ left: 4 })
        }
        .onClick(() => {
          this.showSheet = false
          pathStack.pushPathByName("Coupon", null)
        })
      }
      .width("100%")
      .margin({ top: 8, bottom: 16 })

      if (this.userCoupon == null || this.userCoupon.length == 0) {
        Column() {
          Text("暂无优惠券")
            .fontSize(DesignFontSize.BODY)
            .fontColor(DesignColor.TEXT_SECONDARY)
        }
        .height(120)
        .width("100%")
        .justifyContent(FlexAlign.Center)
      } else {
        List({ space: DesignDimens.CARD_SPACING }) {
          ForEach(this.userCoupon, (coupon: UserCouponVo) => {
            ListItem() {
              Row() {
                Column() {
                  Row() {
                    Text(`${coupon.couponVo?.discount?.toFixed(1)}元优惠券`)
                      .fontSize(DesignFontSize.CARD_TITLE)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(DesignColor.TEXT_PRIMARY)
                  }

                  Row() {
                    Text(`${coupon.couponVo?.threshold == 0 ?
                      `满${parseInt(coupon.couponVo.discount?.toFixed(1) ?? "0") + 0.01}减${coupon.couponVo.discount?.toFixed(1)}` :
                      `满${coupon.couponVo?.threshold?.toFixed(1)}元可用`}`)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.TEXT_SECONDARY)
                  }
                  .margin({ top: 6, bottom: 6 })

                  Row() {
                    Text(`有效期至${coupon.expiredAt}`)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.TEXT_TERTIARY)
                  }
                }
                .alignItems(HorizontalAlign.Start)
                .layoutWeight(1)

                Radio({ value: `${coupon.userCouponId}`, group: "userCoupon" })
                  .checked(this.selectCouponId == coupon.userCouponId)
                  .onChange((flag) => {
                    if (flag) {
                      this.calculationOrder(coupon)
                    }
                  })
              }
              .width("100%")
              .borderRadius(DesignDimens.SMALL_CARD_RADIUS)
              .backgroundColor(DesignColor.SURFACE)
              .border({ width: 0.5, color: DesignColor.OUTLINE })
              .padding(DesignDimens.CARD_PADDING)
              .justifyContent(FlexAlign.SpaceBetween)
              .onClick(() => {
                this.calculationOrder(coupon)
              })
            }
          })
        }
      }
    }
    .width("100%")
    .padding(DesignDimens.PAGE_PADDING)
  }

  build() {
    NavDestination() {
      Column() {
        // 自定义页面标题
        this.PageHeader()

        // 分隔线
        Divider()
          .strokeWidth(0.5)
          .color(DesignColor.DIVIDER)

        // 内容区域
        Column() {
          // ========== 订单信息卡片 ==========
          Column() {
            Text("订单信息")
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontWeight(FontWeight.Bold)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .margin({ bottom: 12 })

            // 订单号
            Row() {
              Text("订单号")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)

              Blank()

              Text(this.orderInfo?.orderNo ?? "")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_PRIMARY)
            }
            .width("100%")
            .margin({ bottom: 8 })

            // 服务类型
            Row() {
              Text("服务类型")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)

              Blank()

              Text(this.orderInfo?.laundryPackageVo?.itemName ?? "")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_PRIMARY)
            }
            .width("100%")
            .margin({ bottom: 8 })

            // 预计完成时间
            Row() {
              Text("预计完成时间")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)

              Blank()

              Text("24小时内")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_PRIMARY)
            }
            .width("100%")

            // 分隔线
            Divider()
              .strokeWidth(0.5)
              .color(DesignColor.DIVIDER)
              .margin({ top: 12, bottom: 12 })

            // 应付金额
            Row() {
              Text("应付金额")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_PRIMARY)

              Blank()

              Text(`￥${this.orderInfo?.payPrice}`)
                .fontSize(DesignFontSize.SECTION_TITLE)
                .fontWeight(FontWeight.Bold)
                .fontColor(DesignColor.PRIMARY)
            }
            .width("100%")
          }
          .width("100%")
          .padding(DesignDimens.CARD_PADDING)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 0.5, color: DesignColor.OUTLINE })
          .alignItems(HorizontalAlign.Start)

          // ========== 支付方式卡片 ==========
          Column() {
            Text("支付方式")
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontWeight(FontWeight.Bold)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .margin({ bottom: 12 })

            Row() {
              // 绿色图标容器
              Column() {
                SymbolGlyph($r('sys.symbol.creditcard'))
                  .fontSize(20)
                  .fontColor([DesignColor.ON_PRIMARY])
              }
              .width(40)
              .height(40)
              .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
              .backgroundColor(DesignColor.PRIMARY)
              .justifyContent(FlexAlign.Center)

              Column() {
                Text("余额支付")
                  .fontSize(DesignFontSize.BODY)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(DesignColor.TEXT_PRIMARY)

                Text(`当前余额￥${(this.userInfo?.balance ?? 0)}`)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(DesignColor.TEXT_SECONDARY)
                  .margin({ top: 2 })
              }
              .alignItems(HorizontalAlign.Start)
              .margin({ left: 12 })
              .layoutWeight(1)

              Radio({ value: "payType", group: "payType" })
                .checked(true)
            }
            .width("100%")
          }
          .width("100%")
          .padding(DesignDimens.CARD_PADDING)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 0.5, color: DesignColor.OUTLINE })
          .alignItems(HorizontalAlign.Start)

          // ========== 优惠券卡片 ==========
          Row() {
            SymbolGlyph($r('sys.symbol.label'))
              .fontSize(20)
              .fontColor([DesignColor.PRIMARY])
              .margin({ right: 10 })

            Text("优惠券")
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontWeight(FontWeight.Bold)
              .fontColor(DesignColor.TEXT_PRIMARY)

            Blank()

            Text(this.selectCoupon == null ? "不使用" : `-￥${this.selectCoupon.couponVo?.discount?.toFixed(1)}`)
              .fontSize(DesignFontSize.BODY)
              .fontColor(this.selectCoupon == null ? DesignColor.TEXT_SECONDARY : DesignColor.PRIMARY)

            SymbolGlyph($r('sys.symbol.chevron_right'))
              .fontSize(14)
              .fontColor([DesignColor.TEXT_TERTIARY])
              .margin({ left: 4 })
          }
          .width("100%")
          .padding(DesignDimens.CARD_PADDING)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 0.5, color: DesignColor.OUTLINE })
          .bindSheet($$this.showSheet, this.BottomSheet(), {
            height: "50%",
            showClose: false,
            dragBar: false
          })
          .onClick(() => {
            this.showSheet = true
          })

          // 占位
          Blank()

          // ========== 确认支付按钮 ==========
          Button(this.paying ? "支付中..." : `确认支付￥${this.orderInfo?.payPrice}`)
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(FontWeight.Bold)
            .fontColor(DesignColor.ON_PRIMARY)
            .backgroundColor(DesignColor.PRIMARY)
            .borderRadius(DesignDimens.BUTTON_RADIUS)
            .width("100%")
            .height(48)
            .enabled(!this.paying)
            .onClick(async (event: ClickEvent) => {
              // 防连点：请求进行中直接忽略，防止重复扣款
              if (this.paying) {
                return
              }
              if ((this.userInfo?.balance ?? 0) >= (this.orderInfo?.payPrice ?? 0)) {
                this.paying = true
                try {
                  let res = await payment({
                    orderId: this.orderId,
                    paymentType: `${PaymentType.PURSE}`,
                    userCouponId: this.selectCoupon?.userCouponId ?? null
                  })
                  if (res.code === HttpStatusCode.Ok) {
                    showToastSafety(res.data, "支付成功")
                    pathStack.pop()
                    pathStack.pushPathByName("PaymentSuccess", this.orderId)
                  } else {
                    showToastSafety(res.message, "支付失败，请稍后重试")
                  }
                } finally {
                  this.paying = false
                }
              } else {
                promptAction.showDialog({
                  title: "余额不足",
                  message: "当前余额不足，请先充值",
                  buttons: [
                    { text: "取消", color: DesignColor.TEXT_PRIMARY },
                    { text: "确认", color: DesignColor.PRIMARY }
                  ],
                }, async (err, data) => {
                  if (data.index == 1) {
                    pathStack.pushPathByName("Recharge", null)
                  }
                })
              }
            })
        }
        .width("100%")
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: 12,
          bottom: 12
        })
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      Promise.all([this.getUserCoupons(), this.getCurrUserInfo()])
    })
  }
}
