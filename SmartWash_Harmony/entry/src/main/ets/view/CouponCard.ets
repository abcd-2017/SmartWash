import { DesignColor, DesignDimens } from "../constant/DesignSystem"
import { CouponStatus } from "../constant/CouponStatus"
import { CouponVo } from "../network/vo/CouponVo"

@ComponentV2
export struct CouponCard {
  @Param coupon: CouponVo | null = null
  @Event buttonClick: () => void
  thresholdPrice: string =
    this.coupon?.threshold == 0 ?
      `满${parseFloat(this.coupon?.discount?.toFixed(1) ?? "0") + 0.01}减${this.coupon?.discount?.toFixed(1)}` :
      `满${this.coupon?.threshold?.toFixed(1)}元可用`
  isReceive: boolean = (this.coupon?.status ?? "0") == CouponStatus.RECEIVE.toString()

  build() {
    Column() {
      Row() {
        // 左侧金额（绿色大字）
        Column() {
          Text(`￥${this.coupon?.discount?.toFixed(1)}`)
            .fontSize(24)
            .fontColor(DesignColor.PRIMARY)
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
          Text(`${this.coupon?.title}`)
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor(DesignColor.TEXT_PRIMARY)

          Text(`${this.coupon?.startTime?.split(" ")[0] ?? ""} - ${this.coupon?.endTime?.split(" ")[0] ?? ""}`)
            .fontSize(12)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 6 })

          Row() {
            Button(`${this.isReceive ? "已领取" : "立即领取"}`)
              .onClick((event: ClickEvent) => {
                if (!this.isReceive) {
                  this.buttonClick()
                }
              })
              .fontSize(13)
              .height(32)
              .backgroundColor(this.isReceive ? DesignColor.SURFACE_VARIANT : DesignColor.PRIMARY)
              .fontColor(this.isReceive ? DesignColor.TEXT_SECONDARY : DesignColor.ON_PRIMARY)
              .borderRadius(DesignDimens.BUTTON_RADIUS)
              .stateEffect(!this.isReceive)
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
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 0.5, color: DesignColor.OUTLINE })
    }
    .width("100%")
  }
}