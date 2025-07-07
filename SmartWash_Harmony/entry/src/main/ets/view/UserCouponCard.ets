import { CommonConstants } from "../constant/CommonConstants"
import { UserCouponStatus } from "../constant/UserCouponStatus"
import { UserCouponVo } from "../network/vo/UserCouponVo"

@ComponentV2
export struct UserCouponCard {
  @Param userCoupon: UserCouponVo | null = null
  @Param type: string | null = null
  thresholdPrice: string =
    this.userCoupon?.couponVo?.threshold == 0 ?
      `满${parseInt(this.userCoupon?.couponVo?.discount?.toFixed(1) ?? "0") +
        0.01}减${this.userCoupon?.couponVo?.discount?.toFixed(1)}` :
      `满${this.userCoupon?.couponVo?.threshold?.toFixed(1)}元可用`
  couponStatus: string = ''

  aboutToAppear(): void {
    if (this.userCoupon?.isUsed) {
      this.couponStatus = "已使用"
    } else if (this.type == UserCouponStatus.OVERDUE) {
      this.couponStatus = "已过期"
    } else {
      this.couponStatus = "可使用"
    }
  }

  build() {
    Column() {
      Row() {
        //左侧内容
        Column() {
          Row() {
            Text(`${this.userCoupon?.couponVo?.discount}元优惠券`)
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
              .margin({ bottom: 8 })
          }

          Row() {
            Text(`${this.thresholdPrice}`)
              .fontSize(12)
              .margin({ bottom: 8 })
          }

          Row() {
            Text(`有效期至 ${this.userCoupon?.expiredAt}`)
              .fontSize(12)
          }
        }.layoutWeight(2)
        .alignItems(HorizontalAlign.Start)

        //右侧内容
        Column() {
          Row() {
            Text(`￥${this.userCoupon?.couponVo?.discount}`)
              .fontSize(20)
              .fontWeight(FontWeight.Bold)
              .margin({ bottom: 12 })
          }

          Row() {
            Row() {
              Text(`${this.couponStatus}`)
                .fontSize(13)
            }
          }
        }.layoutWeight(1)
        .alignItems(HorizontalAlign.End)
      }
      .margin({
        left: 16,
        right: 16,
        bottom: 12,
        top: 8
      })
      .padding(16)
      .backgroundColor($r(this.type == UserCouponStatus.ACTIVE ? 'sys.color.comp_background_primary' :
        'sys.color.background_tertiary'))
      .borderRadius(CommonConstants.BORDER_RADIUS)
      .shadow(ShadowStyle.OUTER_DEFAULT_SM)
    }
    .width("100%")
  }
}