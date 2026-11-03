/**
 * 订单数量统计 VO（interface + 显式映射，对齐后端 /web/auth/orders/itemCount 响应）。
 *
 * 四个计数分别对应待付款 / 进行中 / 待取件 / 待寄件，消费侧经 `?.` + `?? 0` 兜底。
 */
import { jsonNum } from "./JsonParse"

export interface OrderItemCountVo {
  pendingPaymentCount: number | null
  processingCount: number | null
  pendingPickupCount: number | null
  shippedCount: number | null
}

/**
 * JSON → OrderItemCountVo 显式映射。
 * 字段缺失/类型漂移一律兜底 null。
 */
export function orderItemCountVoFromJSON(raw: Object | null | undefined): OrderItemCountVo {
  if (raw === null || raw === undefined) {
    return {
      pendingPaymentCount: null,
      processingCount: null,
      pendingPickupCount: null,
      shippedCount: null
    }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    pendingPaymentCount: jsonNum(obj['pendingPaymentCount']),
    processingCount: jsonNum(obj['processingCount']),
    pendingPickupCount: jsonNum(obj['pendingPickupCount']),
    shippedCount: jsonNum(obj['shippedCount'])
  }
}
