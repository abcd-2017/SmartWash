/**
 * 学校名单条目 VO（interface + 显式映射，对齐后端 /web/schools/allName 响应元素）。
 */
import { jsonNum, jsonStr } from "./JsonParse"

export interface SchoolName {
  schoolId: number | null
  schoolName: string | null
}

/**
 * JSON → SchoolName 显式映射。
 * 字段缺失/类型漂移一律兜底 null。
 */
export function schoolNameFromJSON(raw: Object | null | undefined): SchoolName {
  if (raw === null || raw === undefined) {
    return { schoolId: null, schoolName: null }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    schoolId: jsonNum(obj['schoolId']),
    schoolName: jsonStr(obj['schoolName'])
  }
}
