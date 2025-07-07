import { CommonConstants } from "../constant/CommonConstants"
import { CouponStatus } from "../constant/CouponStatus"
import { CouponVo } from "../network/vo/CouponVo"

@ComponentV2
export struct CouponCard {
  @Param coupon: CouponVo | null = null
  @Event buttonClick: () => void
  thresholdPrice: string =
    this.coupon?.threshold == 0 ?
      `满${parseInt(this.coupon.discount?.toFixed(1) ?? "0") + 0.01}减${this.coupon.discount?.toFixed(1)}` :
      `满${this.coupon?.threshold?.toFixed(1)}元可用`
  isReceive: boolean = (this.coupon?.status ?? "0") == CouponStatus.RECEIVE.toString()

  build() {
    Column() {
      Row() {
        //左侧内容
        Column() {
          Row() {
            Text(`${this.coupon?.discount?.toFixed(1)}元`)
              .fontSize(20)
              .fontColor($r('sys.color.brand'))
              .fontWeight(FontWeight.Bold)
              .margin({ bottom: 8 })
          }

          Row() {
            Text(`${this.thresholdPrice}`)
              .fontSize(14)
          }
        }.layoutWeight(1)
        .alignItems(HorizontalAlign.Start)

        //右侧内容
        Column() {
          Row() {
            Text(`${this.coupon?.title}`)
          }

          Row() {
            Text(`${this.coupon?.startTime?.split(" ")[0]}-${this.coupon?.endTime?.split(" ")[0]}`)
              .fontSize(12)
              .margin({ top: 10, bottom: 12 })
          }

          Row() {
            Button(`${this.isReceive ? "已领取" : "立即领取"}`)
              .onClick((event: ClickEvent) => {
                if (!this.isReceive) {
                  this.buttonClick()
                }
              })
              .margin({ bottom: 6 })
              .backgroundColor($r(`${this.isReceive ? 'sys.color.background_fourth' : 'sys.color.brand'}`))
              .stateEffect(!this.isReceive)
              .fontSize(14)
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
      .backgroundColor($r('sys.color.comp_background_primary'))
      .borderRadius(CommonConstants.BORDER_RADIUS)
      .shadow(ShadowStyle.OUTER_DEFAULT_SM)
    }
    .width("100%")
  }
}