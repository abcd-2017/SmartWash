/**
 * 进行中订单轻量 VO（interface + 显式映射，对齐安卓 OrderBrief / 后端 ShowOrderVo）。
 *
 * 字段以后端 ShowOrderVo（SmartWash/src/main/java/com/smartwash/vo/order/ShowOrderVo.java）
 * 为事实源：仅含 orderId/schoolsVo/lockersVo/userCouponId/orderNo/laundryPackageVo/
 * totalPrice/payPrice/createdAt/updatedAt/status/pickupCode。
 *
 * 历史幽灵字段 userId / laundryItemsId 已删除：后端不下发这两个字段（只读确认），
 * 原 class 直连 JSON 时它们恒为 null，属无用字段。
 */
import { jsonNum, jsonStr } from "./JsonParse"

export interface OrderVo {
  orderId: number | null
  schoolId: number | null
  lockerId: number | null
  orderNo: string | null
  totalPrice: number | null
  payPrice: number | null
  status: string | null
  pickupCode: string | null
  createdAt: string | null
  updatedAt: string | null
}

/**
 * JSON → OrderVo 显式映射。
 * schoolId / lockerId 来自嵌套 schoolsVo / lockersVo 对象（后端 ShowOrderVo 结构），
 * 缺失时兜底 null。
 */
export function orderVoFromJSON(raw: Object | null | undefined): OrderVo {
  if (raw === null || raw === undefined) {
    return {
      orderId: null,
      schoolId: null,
      lockerId: null,
      orderNo: null,
      totalPrice: null,
      payPrice: null,
      status: null,
      pickupCode: null,
      createdAt: null,
      updatedAt: null
    }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const schoolsVo: Record<string, Object> | null =
    obj['schoolsVo'] !== null && typeof obj['schoolsVo'] === 'object'
      ? obj['schoolsVo'] as Record<string, Object> : null
  const lockersVo: Record<string, Object> | null =
    obj['lockersVo'] !== null && typeof obj['lockersVo'] === 'object'
      ? obj['lockersVo'] as Record<string, Object> : null
  return {
    orderId: jsonNum(obj['orderId']),
    schoolId: schoolsVo !== null ? jsonNum(schoolsVo['schoolId']) : null,
    lockerId: lockersVo !== null ? jsonNum(lockersVo['lockerId']) : null,
    orderNo: jsonStr(obj['orderNo']),
    totalPrice: jsonNum(obj['totalPrice']),
    payPrice: jsonNum(obj['payPrice']),
    status: jsonStr(obj['status']),
    pickupCode: jsonStr(obj['pickupCode']),
    createdAt: jsonStr(obj['createdAt']),
    updatedAt: jsonStr(obj['updatedAt'])
  }
}
