/**
 * 钱包流水 VO（interface + 显式映射，不依赖 class 直接接 JSON）。
 *
 * 对齐后端 GET /web/auth/user/transactions（Result<Page<TransactionVo>>，MyBatis-Plus IPage 序列化）：
 * - type："recharge"（充值）/ "payment"（消费）；
 * - amount：充值正数、消费后端已下发负数，端上展示符号按 type 计算（对 amount 取绝对值）；
 * - transactionTime：LocalDateTime，HTTP 层序列化为 ISO（yyyy-MM-dd'T'HH:mm:ss），展示层防御性兼容两种格式；
 * - status：流水状态，当前后端仅产出 "success"。
 * 分页壳字段与充值记录 PageData / 工具箱 ToolboxPageData 一致（records/total/size/current）。
 */
import { jsonNum, jsonStr, mapArray } from "./JsonParse"

export interface TransactionVo {
  type?: string
  amount?: number
  description?: string
  transactionTime?: string
  status?: string
}

export function transactionVoFromJSON(raw: Object | null | undefined): TransactionVo {
  if (raw === null || raw === undefined) {
    return {}
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const vo: TransactionVo = {}
  const type: string | null = jsonStr(obj['type'])
  const amount: number | null = jsonNum(obj['amount'])
  const description: string | null = jsonStr(obj['description'])
  const transactionTime: string | null = jsonStr(obj['transactionTime'])
  const status: string | null = jsonStr(obj['status'])
  if (type !== null) {
    vo.type = type
  }
  if (amount !== null) {
    vo.amount = amount
  }
  if (description !== null) {
    vo.description = description
  }
  if (transactionTime !== null) {
    vo.transactionTime = transactionTime
  }
  if (status !== null) {
    vo.status = status
  }
  return vo
}

/** 流水分页壳（对齐 MyBatis-Plus IPage 序列化，与 PageData / ToolboxPageData 同构） */
export interface TransactionPageData {
  records: Array<TransactionVo>
  total: number
  size: number
  current: number
}

export function transactionPageFromJSON(raw: Object | null | undefined): TransactionPageData {
  if (raw === null || raw === undefined) {
    return { records: [], total: 0, size: 0, current: 0 }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    records: mapArray(obj['records'], (item: Object): TransactionVo => transactionVoFromJSON(item)),
    total: jsonNum(obj['total']) ?? 0,
    size: jsonNum(obj['size']) ?? 0,
    current: jsonNum(obj['current']) ?? 0
  }
}
