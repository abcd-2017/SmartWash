import { HttpStatusCode } from "@ohos/axios"
import { getAllCoupon, getUserCoupon, receiveCoupon } from "../network/api/CouponApi"
import { CouponVo } from "../network/vo/CouponVo"
import { CouponCard } from "../view/CouponCard"
import { promptAction } from "@kit.ArkUI"
import { logger } from "../utils/Logger"
import { UserCouponVo } from "../network/vo/UserCouponVo"
import { UserCouponCard } from "../view/UserCouponCard"
import { UserCouponStatus } from "../constant/UserCouponStatus"

@Builder
export function CouponBuilder() {
  Coupon()
}

@Component
struct Coupon {
  @State coupons: Array<CouponVo> | null = []
  @State userCoupons: Array<UserCouponVo> | null = []
  private param: Record<string, Object> = {}
  private currStatus: string = UserCouponStatus.ACTIVE

  async aboutToAppear() {
    this.getCouponList()
    this.getUserCouponList()
  }

  async getCouponList() {
    let couponRes = await getAllCoupon()
    if (couponRes.code == HttpStatusCode.Ok) {
      this.coupons = []
      this.coupons = couponRes.data
    } else {
      promptAction.showToast({ message: couponRes.message })
    }
  }

  async getUserCouponList() {
    this.param['status'] = this.currStatus
    let res = await getUserCoupon(this.param)
    if (res.code == HttpStatusCode.Ok) {
      this.userCoupons = []
      this.userCoupons = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  build() {
    NavDestination() {
      Tabs() {
        TabContent() {
          if (this.coupons?.length == 0) {
            Row() {
              RowSplit().height("40")
              Text("暂无优惠券").fontSize(14)
            }
          } else {
            List() {
              ForEach(this.coupons, (coupon: CouponVo) => {
                ListItem() {
                  CouponCard({
                    coupon: coupon,
                    buttonClick: async () => {
                      let res = await receiveCoupon(coupon.couponId ?? -1)
                      if (res.code == HttpStatusCode.Ok) {
                        this.getCouponList()
                        promptAction.showToast({ message: "领取成功" })
                      } else {
                        promptAction.showToast({ message: res.message })
                      }
                    }
                  })
                }
              })
            }
          }
        }.tabBar("可领取").align(Alignment.Top)

        TabContent() {
          if (this.userCoupons?.length == 0) {
            Row() {
              RowSplit().height("40")
              Text("暂无优惠券").fontSize(14)
            }
          } else {
            List() {
              ForEach(this.userCoupons, (userCoupon: UserCouponVo) => {
                ListItem() {
                  UserCouponCard({
                    userCoupon: userCoupon,
                    type: this.currStatus
                  })
                }
              })
            }
          }
        }.tabBar("已领取").align(Alignment.Top)

        TabContent() {
          if (this.userCoupons?.length == 0) {
            Row() {
              RowSplit().height("40")
              Text("暂无使用优惠券")
                .fontSize(14)
            }
          } else {
            List() {
              ForEach(this.userCoupons, (userCoupon: UserCouponVo) => {
                ListItem() {
                  UserCouponCard({
                    userCoupon: userCoupon,
                    type: this.currStatus
                  })
                }
              })
            }
          }
        }.tabBar("领取记录").align(Alignment.Top)
      }.onChange(i => {
        switch (i) {
          case 0:
            this.getCouponList()
            break;
          case 1:
            this.currStatus = UserCouponStatus.ACTIVE
            this.getUserCouponList()
            break;
          case 2:
            this.currStatus = UserCouponStatus.OVERDUE
            this.getUserCouponList()
            break
        }
      })
      .barBackgroundColor(Color.Transparent)

    }.title("优惠券")
  }
}