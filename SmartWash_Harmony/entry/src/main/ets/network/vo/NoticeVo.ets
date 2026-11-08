/**
 * 站内信视图对象（GET /web/auth/notices 响应载荷，与后端 notice/vo/NoticeVo 一一对应）。
 *
 * 后端语义：isRead 为「当前登录用户」的已读状态，由服务端按 read 表批量回填；
 * 所有用户看到同一份公告列表，只是已读标记不同。
 *
 * 字段容错（对齐安卓 NoticeVo 的 safe 系约定）：Jackson 可能省略 null 字段，
 * interface 运行时不填充，一律经 JsonParse 的 jsonXxx 兜底，消费点不读可空原始字段。
 * createdAt 保留后端下发的 ISO 字符串原样，格式化由 utils/NoticeLogic 容错处理。
 */
import { jsonBool, jsonNum, jsonStr, mapArray } from "./JsonParse"

export interface NoticeVo {
  /** 公告 ID（列表 item key） */
  id: number
  /** 标题 */
  title: string
  /** 正文（可含换行，列表展示摘要） */
  content: string
  /** 类型枚举串（SYSTEM/ANNOUNCEMENT，见后端 NoticeType） */
  type: string
  /** 深链跳转目标（smartwash://... 或 https://...），空表示纯文案公告 */
  deepLink: string
  /** 当前用户是否已读（字段缺失按未读处理，宁可高亮也不误灰化） */
  isRead: boolean
  /** 发送时间（ISO yyyy-MM-dd'T'HH:mm:ss） */
  createdAt: string | null
}

/**
 * 泛型分页信封（后端 MyBatis-Plus IPage 序列化：records/total/size/current）。
 * 不复用 network/vo/PageData（其 records 被硬编码成 RechargeRecordVo）。
 */
export interface NoticePageData<T> {
  records: Array<T>
  total: number
  size: number
  current: number
}

/** JSON → NoticeVo；缺失字段回落空值/0/false（isRead 缺失 = 未读） */
export function noticeVoFromJSON(raw: Object): NoticeVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    title: jsonStr(obj['title']) ?? '',
    content: jsonStr(obj['content']) ?? '',
    type: jsonStr(obj['type']) ?? '',
    deepLink: jsonStr(obj['deepLink']) ?? '',
    isRead: jsonBool(obj['isRead']) ?? false,
    createdAt: jsonStr(obj['createdAt'])
  }
}

/** JSON → 泛型分页信封；raw 缺失兜底空页 */
export function noticePageDataFromJSON<T>(raw: Object | null | undefined,
  mapper: (item: Object) => T): NoticePageData<T> {
  if (raw === null || raw === undefined) {
    return { records: [], total: 0, size: 0, current: 0 }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    records: mapArray(obj['records'], mapper),
    total: jsonNum(obj['total']) ?? 0,
    size: jsonNum(obj['size']) ?? 0,
    current: jsonNum(obj['current']) ?? 0
  }
}
