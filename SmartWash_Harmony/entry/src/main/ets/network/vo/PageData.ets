/**
 * 充值记录分页响应（interface + 显式映射，对齐后端 MyBatis-Plus IPage 序列化）。
 *
 * 注意：本结构专用于充值记录接口（/web/auth/recharge/list），records 元素类型为
 * RechargeRecordVo。工具箱短链域有自带泛型分页结构（feature/toolbox/api.ets 的
 * ToolboxPageData<T>），不复用本文件。
 */
import { jsonNum, mapArray } from "./JsonParse"
import { RechargeRecordVo, rechargeRecordVoFromJSON } from './RechargeRecordVo';

export interface PageData {
  records: Array<RechargeRecordVo>
  total: number
  size: number
  current: number
}

/**
 * JSON → PageData 显式映射。
 * 分页标量字段缺失兜底 0，records 缺失/非数组兜底空数组（mapArray 已处理）。
 */
export function pageDataFromJSON(raw: Object | null | undefined): PageData {
  if (raw === null || raw === undefined) {
    return { records: [], total: 0, size: 0, current: 0 }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    records: mapArray(obj['records'], (item: Object): RechargeRecordVo => rechargeRecordVoFromJSON(item)),
    total: jsonNum(obj['total']) ?? 0,
    size: jsonNum(obj['size']) ?? 0,
    current: jsonNum(obj['current']) ?? 0
  }
}
