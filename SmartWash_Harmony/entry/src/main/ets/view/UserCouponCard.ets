import { DesignColor, DesignDimens } from "../constant/DesignSystem"
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

  private getStatusColor(): number {
    if (this.userCoupon?.isUsed || this.type == UserCouponStatus.OVERDUE) {
      return DesignColor.TEXT_TERTIARY
    }
    return DesignColor.PRIMARY
  }

  private isAvailable(): boolean {
    return !this.userCoupon?.isUsed && this.type != UserCouponStatus.OVERDUE
  }

  build() {
    Column() {
      Row() {
        // 左侧金额（绿色大字）
        Column() {
          Text(`￥${this.userCoupon?.couponVo?.discount}`)
            .fontSize(24)
            .fontColor(this.getStatusColor())
            .fontWeight(FontWeight.Bold)
          Text(`${this.thresholdPrice}`)
            .fontSize(11)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 4 })
        }
        .width(90)
        .alignItems(HorizontalAlign.Center)

        // 竖线分隔
        Divider()
          .strokeWidth(0.5)
          .height(50)
          .color(DesignColor.OUTLINE)
          .margin({ left: 8, right: 8 })

        // 右侧内容
        Column() {
          Text(`${this.userCoupon?.couponVo?.title ?? `${this.userCoupon?.couponVo?.discount}元优惠券`}`)
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.isAvailable() ? DesignColor.TEXT_PRIMARY : DesignColor.TEXT_TERTIARY)

          Text(`有效期至 ${this.userCoupon?.expiredAt}`)
            .fontSize(12)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 6 })

          // 状态标签
          Row() {
            Text(`${this.couponStatus}`)
              .fontSize(12)
              .fontColor(this.getStatusColor())
              .padding({ left: 10, right: 10, top: 4, bottom: 4 })
              .borderRadius(8)
              .backgroundColor(this.isAvailable() ? DesignColor.PRIMARY_CONTAINER : DesignColor.SURFACE_VARIANT)
              .margin({ top: 8 })
          }
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
      }
      .margin({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        bottom: DesignDimens.CARD_SPACING,
        top: 8
      })
      .padding(DesignDimens.CARD_PADDING)
      .backgroundColor(this.isAvailable() ? DesignColor.SURFACE : DesignColor.SURFACE_VARIANT)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 0.5, color: DesignColor.OUTLINE })
    }
    .width("100%")
  }
}