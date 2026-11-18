/**
 * 订单评价 VO（interface + 显式映射，对齐后端 GET /web/auth/reviews/{orderId} 的 ReviewVo）。
 *
 * 消费侧：订单详情页已完成态——回显本人已提交评价（首条）与评价提交。
 */
import { jsonNum, jsonStr } from "./JsonParse"

export interface ReviewVo {
  reviewId: number | null
  orderId: number | null
  userId: number | null
  userPhone: string | null
  rating: number | null
  content: string | null
  createdAt: string | null
}

/**
 * JSON → ReviewVo 显式映射。
 * 字段缺失/类型漂移一律兜底 null。
 */
export function reviewVoFromJSON(raw: Object | null | undefined): ReviewVo {
  if (raw === null || raw === undefined) {
    return {
      reviewId: null,
      orderId: null,
      userId: null,
      userPhone: null,
      rating: null,
      content: null,
      createdAt: null
    }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    reviewId: jsonNum(obj['reviewId']),
    orderId: jsonNum(obj['orderId']),
    userId: jsonNum(obj['userId']),
    userPhone: jsonStr(obj['userPhone']),
    rating: jsonNum(obj['rating']),
    content: jsonStr(obj['content']),
    createdAt: jsonStr(obj['createdAt'])
  }
}

/**
 * JSON 数组 → ReviewVo 列表（后端按创建时间倒序分页）。
 */
export function reviewVoListFromJSON(raw: Object | null | undefined): ReviewVo[] {
  if (raw === null || raw === undefined) {
    return []
  }
  const arr = raw as Array<Object>
  return arr.map((o: Object) => reviewVoFromJSON(o))
}
