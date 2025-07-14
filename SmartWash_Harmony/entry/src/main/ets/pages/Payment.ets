import { HttpStatusCode } from "@ohos/axios"
import { calculationOrder, getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { pathStack } from "../utils/PathStackUtil"
import { promptAction } from "@kit.ArkUI"
import { getCanUseCoupon } from "../network/api/CouponApi"
import { UserCouponVo } from "../network/vo/UserCouponVo"
import { CommonConstants } from "../constant/CommonConstants"
import { ItemTitle } from "../view/ItemTitle"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { payment } from "../network/api/PaymentApi"
import { PaymentType } from "../constant/PaymentType"

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

  async aboutToAppear() {
    this.orderId = pathStack.getParamByName("Payment")[0] as number
    if (this.orderId != null) {
      let res = await getOrderInfo(this.orderId)
      if (res.code == HttpStatusCode.Ok) {
        this.orderInfo = res.data
      } else {
        promptAction.showToast({ message: res.message })
      }
    }
  }

  async getCurrUserInfo() {
    let res = await getUserInfo()
    if (res.code == HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  async getUserCoupons() {
    //获取已经领取的优惠券列表
    let couponRes = await getCanUseCoupon(this.orderId ?? -1)
    if (couponRes.code == HttpStatusCode.Ok) {
      this.userCoupon = couponRes.data
    } else {
      promptAction.showToast({ message: couponRes.message })
    }
  }

  async calculationOrder(coupon: UserCouponVo) {
    this.selectCouponId = coupon.userCouponId
    this.showSheet = false
    this.selectCoupon = coupon
    if (this.orderId != null) {
      let res = await calculationOrder(this.orderId, coupon.userCouponId ?? -1)
      if (res.code == HttpStatusCode.Ok) {
        this.orderInfo = res.data
      } else {
        promptAction.showToast({ message: res.message })
      }
    }
  }

  @Builder
  BottomSheet() {
    Column() {
      Row() {
        Column() {
          Text("选择优惠券")
            .fontSize(16)
            .fontWeight(FontWeight.Bold)
        }

        ColumnSplit().layoutWeight(1)

        Column() {
          Row() {
            Text("去领取")
              .fontSize(14)
              .fontColor($r('sys.color.brand'))

            SymbolGlyph($r('sys.symbol.chevron_right')).fontSize(14)
              .fontColor([$r('sys.color.font_primary')])
              .margin({ left: 6 })
          }
        }.onClick(() => {
          this.showSheet = false
          pathStack.pushPathByName("Coupon", null)
        })
      }.margin({ top: 6 })

      RowSplit().height("24")

      if (this.userCoupon == null || this.userCoupon.length == 0) {
        Text("暂无优惠券")
          .height("100%")
          .align(Alignment.Center)
      } else {
        List({ space: 12 }) {
          ForEach(this.userCoupon, (coupon: UserCouponVo) => {
            ListItem() {
              Row() {
                Column() {
                  Row() {
                    Text(`${coupon.couponVo?.discount?.toFixed(1)}元优惠券`)
                      .fontWeight(FontWeight.Bold)
                  }

                  Row() {
                    Text(`${coupon.couponVo?.threshold == 0 ?
                      `满${parseInt(coupon.couponVo.discount?.toFixed(1) ?? "0") +
                        0.01}减${coupon.couponVo.discount?.toFixed(1)}` :
                      `满${coupon.couponVo?.threshold?.toFixed(1)}元可用`}`)
                      .fontSize(13)
                  }.margin({ top: 6, bottom: 8 })

                  Row() {
                    Text(`有效期至${coupon.expiredAt}`)
                      .fontSize(13)
                  }
                }.alignItems(HorizontalAlign.Start)

                Column() {
                  Radio({ value: `${coupon.userCouponId}`, group: "userCoupon" })
                    .checked(this.selectCouponId == coupon.userCouponId)
                    .onChange((flag) => {
                      if (flag) {
                        this.calculationOrder(coupon)
                      }
                    })
                }
              }
              .width("100%")
              .borderRadius(CommonConstants.BORDER_RADIUS)
              .backgroundColor($r('sys.color.comp_emphasize_tertiary'))
              .padding(18)
              .justifyContent(FlexAlign.SpaceBetween)
              .onClick(() => {
                this.calculationOrder(coupon)
              })
            }
          })
        }
      }
    }.width("100%")
    .padding(16)
  }

  build() {
    NavDestination() {
      Column() {
        Column() {
          Row() {
            ItemTitle({ title: "订单信息" })
          }.margin({ bottom: 4 })

          Row() {
            Text(`订单号：${this.orderInfo?.orderNo}`)
              .fontSize(14)
            Text(`￥${this.orderInfo?.totalPrice}`)
              .fontSize(14)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('sys.color.brand'))
          }.width("100%").justifyContent(FlexAlign.SpaceBetween).margin({ top: 4, bottom: 4 })

          Row() {
            Text(`服务类型：${this.orderInfo?.laundryPackageVo?.itemName}`)
              .fontSize(14)
          }.margin({ top: 4, bottom: 4 })

          Row() {
            Text(`预计完成时间：24小时内`)
              .fontSize(14)
          }.margin({ top: 4 })
        }
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .padding(18)
        .alignItems(HorizontalAlign.Start)

        RowSplit().height("16")
        Column() {
          Row() {
            ItemTitle({ title: "支付方式" })
          }.margin({ bottom: 12 })

          Row() {
            Column() {
              Radio({ value: "payType", group: "payType" })
                .margin({ left: 6, right: 12 })
                .checked(true)
            }

            RowSplit().height("12")
            Column() {
              Row() {
                Text("余额支付")
                  .fontSize(15)
              }.margin({ bottom: 4 })

              Row() {
                Text(`当前余额￥${(this.userInfo?.balance ?? 0)}`)
                  .fontSize(13)
              }
            }.alignItems(HorizontalAlign.Start)
          }.width("100%")
        }
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .padding(18)
        .alignItems(HorizontalAlign.Start)

        RowSplit().height("16")
        Column() {
          Row() {
            SymbolGlyph($r('sys.symbol.label'))
              .fontSize(20)
              .fontColor([$r('sys.color.font_primary')])
              .margin({ right: 6 })

            Text("优惠券")
              .fontSize(16)
              .fontWeight(FontWeight.Bold)

            ColumnSplit().layoutWeight(1)

            Text(`${this.selectCoupon == null ? "不使用" : "-￥" + this.selectCoupon.couponVo?.discount?.toFixed(1)}`)
              .fontSize(14)
            SymbolGlyph($r('sys.symbol.chevron_right')).fontSize(14)
              .fontColor([$r('sys.color.font_primary')])
              .margin({ left: 6 })
          }
        }
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .padding(18)
        .bindSheet($$this.showSheet, this.BottomSheet(), {
          height: "50%",
          showClose: false,
          dragBar: false
        })
        .onClick(() => {
          this.showSheet = true
        })

        RowSplit().height("24")
        Button(`确认支付￥${this.orderInfo?.payPrice}`).onClick(async (event: ClickEvent) => {
          if ((this.userInfo?.balance ?? 0) >= (this.orderInfo?.payPrice ?? 0)) {
            let res = await payment({
              orderId: this.orderId,
              paymentType: `${PaymentType.PURSE}`,
              userCouponId: this.selectCoupon?.userCouponId ?? null
            })
            if (res.code == HttpStatusCode.Ok) {
              promptAction.showToast({ message: res.data })
              pathStack.pop()
              pathStack.pushPathByName("PaymentSuccess", this.orderId)
            } else {
              promptAction.showToast({ message: res.message })
            }
          } else {
            promptAction.showDialog({
              title: "余额不足",
              message: "当前余额不足，请先充值",
              buttons: [{ text: "取消", color: $r('sys.color.font_primary') },
                { text: "确认", color: $r('sys.color.font_primary') }],
            }, async (err, data) => {
              if (data.index == 1) {
                pathStack.pushPathByName("Recharge", null)
              }
            })
          }
        }).fontSize(16)
          .width("100%")
      }
      .width("100%")
      .height("100%")
      .align(Alignment.Top)
      .backgroundColor($r('sys.color.background_primary'))
      .padding({ left: 16, right: 16, top: 4 })
    }.title("支付")
    .onShown(() => {
      this.getUserCoupons()
      this.getCurrUserInfo()
    })
  }
}