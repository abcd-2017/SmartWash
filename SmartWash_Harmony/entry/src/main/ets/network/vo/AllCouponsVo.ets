import { CouponVo } from "./CouponVo"
import { UserCouponVo } from "./UserCouponVo"

/**
 * 优惠券聚合响应（对齐安卓 feature/coupon network/vo/coupon/AllCouponsVo.kt）。
 * 对应后端 WebUserCouponController.allCoupons：一次返回可领取 / 已领取 / 历史三类，
 * 供优惠券页一次加载、切 tab 零请求。
 */
export class AllCouponsVo {
  available: Array<CouponVo> | null = null
  claimed: Array<UserCouponVo> | null = null
  historical: Array<UserCouponVo> | null = null
}
