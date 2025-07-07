import { CouponVo } from "./CouponVo"

export class UserCouponVo {
  userCouponId: number | null = null
  phoneNumber: string | null = null
  couponVo: CouponVo | null = null
  isUsed: boolean | null = null
  expiredAt: string | null = null
  usedAt: string | null = null
  receivedAt: string | null = null
  orderNo: string | null = null
}