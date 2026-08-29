import { HttpStatusCode } from "@ohos/axios"
import { getAllCoupon, getUserCoupon, receiveCoupon } from "../network/api/CouponApi"
import { CouponVo } from "../network/vo/CouponVo"
import { UserCouponVo } from "../network/vo/UserCouponVo"
import { UserCouponStatus } from "../constant/UserCouponStatus"
import { CouponStatus } from "../constant/CouponStatus"
import { showToastSafety } from "../utils/ToastUtil"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

@Builder
export function CouponBuilder() {
  Coupon()
}

@Component
struct Coupon {
  @State coupons: Array<CouponVo> | null = []
  @State userCoupons: Array<UserCouponVo> | null = []
  @State selectedIndex: number = 0
  private param: Record<string, Object> = {}
  private currStatus: string = UserCouponStatus.ACTIVE

  // Tab 标题列表
  private readonly tabTitles: string[] = ["可领取", "已领取", "领取记录"]

  async aboutToAppear() {
    Promise.all([this.getCouponList(), this.getUserCouponList()])
  }

  async getCouponList() {
    let couponRes = await getAllCoupon()
    if (couponRes.code == HttpStatusCode.Ok) {
      this.coupons = []
      this.coupons = couponRes.data
    } else {
      showToastSafety(couponRes.message, "优惠券加载失败")
    }
  }

  async getUserCouponList() {
    this.param['status'] = this.currStatus
    let res = await getUserCoupon(this.param)
    if (res.code == HttpStatusCode.Ok) {
      this.userCoupons = []
      this.userCoupons = res.data
    } else {
      showToastSafety(res.message, "优惠券加载失败")
    }
  }

  // 切换 Tab
  onTabChange(index: number) {
    this.selectedIndex = index
    switch (index) {
      case 0:
        this.getCouponList()
        break
      case 1:
        this.currStatus = UserCouponStatus.ACTIVE
        this.getUserCouponList()
        break
      case 2:
        this.currStatus = UserCouponStatus.OVERDUE
        this.getUserCouponList()
        break
    }
  }

  build() {
    NavDestination() {
      Column() {
        // ========== 自定义标题栏 ==========
        Row() {
          Text("优惠券")
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
        }
        .width("100%")
        .height(56)
        .justifyContent(FlexAlign.Center)
        .alignItems(VerticalAlign.Center)

        // ========== 自定义标签栏 ==========
        Row() {
          ForEach(this.tabTitles, (title: string, index: number) => {
            Column() {
              Text(title)
                .fontSize(DesignFontSize.BODY)
                .fontWeight(this.selectedIndex == index ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
                .fontColor(this.selectedIndex == index ? DesignColor.PRIMARY : DesignColor.TEXT_SECONDARY)

              // 底部指示器
              Divider()
                .width(20)
                .height(3)
                .color(this.selectedIndex == index ? DesignColor.PRIMARY : Color.Transparent)
                .borderRadius(1.5)
                .margin({ top: 6 })
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              this.onTabChange(index)
            })
          })
        }
        .width("100%")
        .height(48)
        .backgroundColor(DesignColor.SURFACE)
        .border({ width: { bottom: 0.5 }, color: DesignColor.DIVIDER })

        // ========== 内容区域 ==========
        if (this.selectedIndex == 0) {
          // 可领取 Tab
          if (this.coupons?.length == 0) {
            Column() {
              Text("暂无优惠券")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }
            .width("100%")
            .height("100%")
            .justifyContent(FlexAlign.Center)
          } else {
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.coupons, (coupon: CouponVo) => {
                ListItem() {
                  AvailableCouponCard({
                    coupon: coupon,
                    onReceive: (couponId: number) => {
                      this.onReceiveCoupon(couponId)
                    }
                  })
                }
              })
            }
            .width("100%")
            .layoutWeight(1)
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING })
          }
        } else if (this.selectedIndex == 1) {
          // 已领取 Tab
          if (this.userCoupons?.length == 0) {
            Column() {
              Text("暂无优惠券")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }
            .width("100%")
            .height("100%")
            .justifyContent(FlexAlign.Center)
          } else {
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.userCoupons, (userCoupon: UserCouponVo) => {
                ListItem() {
                  ClaimedCouponCard({ userCoupon: userCoupon })
                }
              })
            }
            .width("100%")
            .layoutWeight(1)
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING })
          }
        } else {
          // 领取记录 Tab
          if (this.userCoupons?.length == 0) {
            Column() {
              Text("暂无使用优惠券")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }
            .width("100%")
            .height("100%")
            .justifyContent(FlexAlign.Center)
          } else {
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.userCoupons, (userCoupon: UserCouponVo) => {
                ListItem() {
                  HistoricalCouponCard({ userCoupon: userCoupon })
                }
              })
            }
            .width("100%")
            .layoutWeight(1)
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING })
          }
        }
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .title("优惠券")
    .width("100%")
    .height("100%")
    .backgroundColor(DesignColor.BACKGROUND)
  }

  // 领取优惠券
  async onReceiveCoupon(couponId: number) {
    let res = await receiveCoupon(couponId)
    if (res.code == HttpStatusCode.Ok) {
      this.getCouponList()
      showToastSafety("领取成功")
    } else {
      showToastSafety(res.message, "领取失败")
    }
  }
}

// ========== 可领取优惠券卡片 ==========
@ComponentV2
struct AvailableCouponCard {
  @Param coupon: CouponVo | null = null
  @Event onReceive: (couponId: number) => void = (couponId: number) => {}

  build() {
    let isReceived: boolean = (this.coupon?.status ?? "0") == CouponStatus.RECEIVE.toString()
    let thresholdText: string = this.coupon?.threshold == 0 ?
      "无门槛" :
      `满${this.coupon?.threshold?.toFixed(0)}元可用`
    let dateText: string = `${this.coupon?.startTime?.split(" ")[0] ?? ""} - ${this.coupon?.endTime?.split(" ")[0] ?? ""}`

    Row() {
      // 左侧金额
      Column() {
        Text(`￥${this.coupon?.discount?.toFixed(0) ?? "0"}`)
          .fontSize(24)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(DesignColor.PRIMARY)
      }
      .width(80)
      .alignItems(HorizontalAlign.Center)
      .justifyContent(FlexAlign.Center)

      // 竖线分隔
      Divider()
        .width(0.5)
        .height(48)
        .color(DesignColor.DIVIDER)
        .vertical(true)

      // 右侧信息
      Column() {
        Text(this.coupon?.title ?? "")
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        Text(dateText)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .margin({ top: 4 })

        Text(thresholdText)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 2 })

        if (isReceived) {
          Text("已领取")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(8)
            .padding({ left: 16, right: 16, top: 6, bottom: 6 })
            .margin({ top: 8 })
        } else {
          Text("领取")
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(Color.White)
            .backgroundColor(DesignColor.PRIMARY)
            .borderRadius(8)
            .padding({ left: 16, right: 16, top: 6, bottom: 6 })
            .margin({ top: 8 })
            .onClick(() => {
              this.onReceive(this.coupon?.couponId ?? -1)
            })
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .padding({ left: 16 })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 0.5, color: DesignColor.OUTLINE })
  }
}

// ========== 已领取优惠券卡片 ==========
@ComponentV2
struct ClaimedCouponCard {
  @Param userCoupon: UserCouponVo | null = null

  build() {
    let thresholdText: string = this.userCoupon?.couponVo?.threshold == 0 ?
      "无门槛" :
      `满${this.userCoupon?.couponVo?.threshold?.toFixed(0)}元可用`
    let dateText: string = `有效期至 ${this.userCoupon?.expiredAt ?? ""}`

    Row() {
      // 左侧金额
      Column() {
        Text(`￥${this.userCoupon?.couponVo?.discount?.toFixed(0) ?? "0"}`)
          .fontSize(24)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(DesignColor.PRIMARY)
      }
      .width(80)
      .alignItems(HorizontalAlign.Center)
      .justifyContent(FlexAlign.Center)

      // 竖线分隔
      Divider()
        .width(0.5)
        .height(48)
        .color(DesignColor.DIVIDER)
        .vertical(true)

      // 右侧信息
      Column() {
        Text(this.userCoupon?.couponVo?.title ?? "")
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        Text(dateText)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .margin({ top: 4 })

        Text(thresholdText)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 2 })

        Text("可使用")
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(Color.White)
          .backgroundColor(DesignColor.PRIMARY)
          .borderRadius(8)
          .padding({ left: 16, right: 16, top: 6, bottom: 6 })
          .margin({ top: 8 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .padding({ left: 16 })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 0.5, color: DesignColor.OUTLINE })
  }
}

// ========== 领取记录优惠券卡片 ==========
@ComponentV2
struct HistoricalCouponCard {
  @Param userCoupon: UserCouponVo | null = null

  build() {
    let thresholdText: string = this.userCoupon?.couponVo?.threshold == 0 ?
      "无门槛" :
      `满${this.userCoupon?.couponVo?.threshold?.toFixed(0)}元可用`
    let dateText: string = `有效期至 ${this.userCoupon?.expiredAt ?? ""}`
    let statusText: string = this.userCoupon?.isUsed ? "已使用" : "已过期"

    Row() {
      // 左侧金额
      Column() {
        Text(`￥${this.userCoupon?.couponVo?.discount?.toFixed(0) ?? "0"}`)
          .fontSize(24)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(DesignColor.PRIMARY)
      }
      .width(80)
      .alignItems(HorizontalAlign.Center)
      .justifyContent(FlexAlign.Center)

      // 竖线分隔
      Divider()
        .width(0.5)
        .height(48)
        .color(DesignColor.DIVIDER)
        .vertical(true)

      // 右侧信息
      Column() {
        Text(this.userCoupon?.couponVo?.title ?? "")
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        Text(dateText)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .margin({ top: 4 })

        Text(thresholdText)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 2 })

        Text(statusText)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 8 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .padding({ left: 16 })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 0.5, color: DesignColor.OUTLINE })
    .opacity(0.6)
  }
}
