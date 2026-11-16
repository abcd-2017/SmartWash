import { jsonBool, jsonNum, jsonStr } from "./JsonParse"

export class CouponVo {
  couponId: number | null = null
  title: string | null = null
  description: string | null = null
  discount: number | null = null
  threshold: number | null = null
  startTime: string | null = null
  endTime: string | null = null
  validDays: number | null = null
  isNewUser: boolean | null = null
  status: string | null = null
}

/**
 * JSON → CouponVo 显式映射（axios 反序列化产物字段可能缺失，统一经 JsonParse 兜底）。
 * 供分页接口（UserCouponVo.couponVo）复用；既有 allCoupons 聚合接口仍按原样透传。
 */
export function couponFromJSON(raw: Object | null | undefined): CouponVo {
  const vo: CouponVo = new CouponVo()
  if (raw === null || raw === undefined) {
    return vo
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  vo.couponId = jsonNum(obj['couponId'])
  vo.title = jsonStr(obj['title'])
  vo.description = jsonStr(obj['description'])
  vo.discount = jsonNum(obj['discount'])
  vo.threshold = jsonNum(obj['threshold'])
  vo.startTime = jsonStr(obj['startTime'])
  vo.endTime = jsonStr(obj['endTime'])
  vo.validDays = jsonNum(obj['validDays'])
  vo.isNewUser = jsonBool(obj['isNewUser'])
  vo.status = jsonStr(obj['status'])
  return vo
}
