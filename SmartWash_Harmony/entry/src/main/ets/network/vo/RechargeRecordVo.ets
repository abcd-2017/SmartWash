/**
 * 充值记录 VO（interface + 显式映射，不依赖 class 直接接 JSON）。
 *
 * 字段可选是后端真实形态（Jackson 可能省略 null 字段），消费侧统一经可选链/?? 兜底。
 */
import { jsonNum, jsonStr } from "./JsonParse"

export interface RechargeRecordVo {
  recordId?: number
  amount?: number
  rechargeType?: string
  rechargeTime?: string
}

/**
 * JSON → RechargeRecordVo 显式映射。
 * 字段缺失/类型漂移时省略该键（保持 interface 可选语义），与原 interface 可选字段一致。
 */
export function rechargeRecordVoFromJSON(raw: Object | null | undefined): RechargeRecordVo {
  if (raw === null || raw === undefined) {
    return {}
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const recordId: number | null = jsonNum(obj['recordId'])
  const amount: number | null = jsonNum(obj['amount'])
  const rechargeType: string | null = jsonStr(obj['rechargeType'])
  const rechargeTime: string | null = jsonStr(obj['rechargeTime'])
  const vo: RechargeRecordVo = {}
  if (recordId !== null) {
    vo.recordId = recordId
  }
  if (amount !== null) {
    vo.amount = amount
  }
  if (rechargeType !== null) {
    vo.rechargeType = rechargeType
  }
  if (rechargeTime !== null) {
    vo.rechargeTime = rechargeTime
  }
  return vo
}
