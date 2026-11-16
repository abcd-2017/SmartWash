import { jsonBool, jsonNum, jsonObj, jsonStr, mapArray } from "./JsonParse"
import { CouponVo, couponFromJSON } from "./CouponVo"

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

/**
 * JSON → UserCouponVo 显式映射（嵌套 couponVo 一并映射，避免依赖 class 直接接 JSON）。
 * 供分页接口 GET /web/auth/userCoupon/getUserCoupon 使用。
 */
export function userCouponFromJSON(raw: Object | null | undefined): UserCouponVo {
  const vo: UserCouponVo = new UserCouponVo()
  if (raw === null || raw === undefined) {
    return vo
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  vo.userCouponId = jsonNum(obj['userCouponId'])
  vo.phoneNumber = jsonStr(obj['phoneNumber'])
  vo.couponVo = couponFromJSON(jsonObj(obj['couponVo']))
  vo.isUsed = jsonBool(obj['isUsed'])
  vo.expiredAt = jsonStr(obj['expiredAt'])
  vo.usedAt = jsonStr(obj['usedAt'])
  vo.receivedAt = jsonStr(obj['receivedAt'])
  vo.orderNo = jsonStr(obj['orderNo'])
  return vo
}

/**
 * 用户优惠券分页壳（对齐后端 Result<Page<UserCouponVo>>，MyBatis-Plus IPage 序列化：
 * records/total/size/current）。total 供端上判断是否还有更多。
 */
export interface UserCouponPageData {
  records: Array<UserCouponVo>
  total: number
  size: number
  current: number
}

/** JSON → 用户优惠券分页壳；raw 缺失兜底空页 */
export function userCouponPageFromJSON(raw: Object | null | undefined): UserCouponPageData {
  if (raw === null || raw === undefined) {
    return { records: [], total: 0, size: 0, current: 0 }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    records: mapArray(obj['records'],
      (item: Object): UserCouponVo => userCouponFromJSON(item)),
    total: jsonNum(obj['total']) ?? 0,
    size: jsonNum(obj['size']) ?? 0,
    current: jsonNum(obj['current']) ?? 0
  }
}
