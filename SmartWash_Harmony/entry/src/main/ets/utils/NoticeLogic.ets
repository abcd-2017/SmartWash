/**
 * 站内信纯逻辑（类型枚举 / 文案 / 相对时间分桶 / 未读角标格式化）。
 *
 * 对齐安卓 feature/notice/logic/NoticeLogic.kt + UnreadBadgeFormatter.kt：
 * - 类型枚举与后端 NoticeType 枚举名一一对应，未收录值落 UNKNOWN（回退通用文案，不崩不空白）；
 * - 时间分桶（今天/昨天/本年/更早）为纯函数，[now] 注入供单测固定「今天」，生产默认系统当前时间；
 * - 容错：null/空白/格式漂移一律返回 null，UI 隐藏时间而非展示垃圾串。
 *
 * 放置于 utils/ 公共层（对齐 TimeUtil.formatOrderTime 的「域专用格式化放 utils」先例）：
 * 消息中心页（feature/notice/ui）与我的页入口角标（view/UserInfo）共用，
 * 避免 view/ 直接引用域内实现（feature/README.md 依赖铁律）。
 * 本文件不持有 UI 依赖，可被 hypium 单测直接消费。
 */

/** 站内信类型枚举（与后端 NoticeType 枚举名一一对应） */
import { resString } from '../utils/ResourceString'
export enum NoticeType {
  /** 系统通知（维护/版本/安全类） */
  SYSTEM = 'SYSTEM',
  /** 运营公告（活动/规则类） */
  ANNOUNCEMENT = 'ANNOUNCEMENT',
  /** 未收录类型（后端扩展枚举时端上未适配），回退通用文案 */
  UNKNOWN = 'UNKNOWN'
}

/** type 字符串 → 枚举（未收录/null/空一律 UNKNOWN，不做异常分支） */
export function noticeTypeFromRaw(raw: string | null | undefined): NoticeType {
  if (raw === NoticeType.SYSTEM) {
    return NoticeType.SYSTEM
  }
  if (raw === NoticeType.ANNOUNCEMENT) {
    return NoticeType.ANNOUNCEMENT
  }
  return NoticeType.UNKNOWN
}

/** 文案表（对齐安卓 notice_* 字符串；纯逻辑层持有中文文案，UI 不拼字符串） */
export class NoticeText {
  // 页面标题与页头动作
  static get notice_title(): string { return resString('notice_title') }
  static get notice_unread_count_format(): string { return resString('notice_unread_count_format') }
  static get notice_read_all(): string { return resString('notice_read_all') }
  static get notice_read_all_confirm_title(): string { return resString('notice_read_all_confirm_title') }
  static get notice_read_all_confirm_message(): string { return resString('notice_read_all_confirm_message') }
  static get notice_read_all_success(): string { return resString('notice_read_all_success') }
  static get notice_read_all_failed(): string { return resString('notice_text_notice_read_all_failed') }

  // 类型 tag
  static get notice_type_system(): string { return resString('notice_type_system') }
  static get notice_type_announcement(): string { return resString('notice_type_announcement') }
  static get notice_type_unknown(): string { return resString('notice_type_unknown') }

  // 时间展示
  static get notice_time_today_format(): string { return resString('notice_time_today_format') }
  static get notice_time_yesterday_format(): string { return resString('notice_time_yesterday_format') }
  static get notice_time_this_year_format(): string { return resString('notice_text_notice_time_this_year_format') }
  static readonly notice_time_older_format: string = '%d-%02d-%02d'

  // 加载更多
  static get notice_load_more(): string { return resString('notice_load_more') }
  static get notice_load_more_failed(): string { return resString('notice_load_more_failed') }

  // 空态 / 错误态（说明发生了什么 + 怎么解决，规范 §3.6）
  static get notice_inbox_empty(): string { return resString('notice_inbox_empty') }
  static get notice_load_failed(): string { return resString('notice_load_failed') }
  static get notice_action_retry(): string { return resString('retry') }

  // 读屏状态（对齐安卓 strings.xml notice_state_unread/read：未读色点纯装饰，
  // 颜色不是唯一信息载体，规范 §10）
  static get notice_state_unread(): string { return resString('notice_state_unread') }
  static get notice_state_read(): string { return resString('notice_state_read') }
}

/** 类型 → 展示文案 */
export function noticeTypeText(type: NoticeType): string {
  switch (type) {
    case NoticeType.SYSTEM:
      return NoticeText.notice_type_system
    case NoticeType.ANNOUNCEMENT:
      return NoticeText.notice_type_announcement
    default:
      return NoticeText.notice_type_unknown
  }
}

/** 时间展示分桶（今天/昨天/本年/更早），由 [noticeTimeParts] 计算 */
export enum NoticeTimeBucket {
  TODAY,
  YESTERDAY,
  THIS_YEAR,
  OLDER
}

/** 时间展示零件：分桶 + 供文案格式化的原始分量 */
export interface NoticeTimeParts {
  bucket: NoticeTimeBucket
  /** HH:mm（今天/昨天用） */
  time: string
  month: number
  day: number
  year: number
}

/** 两位补零 */
function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

/**
 * 站内信时间解析 + 相对分桶（ISO yyyy-MM-dd'T'HH:mm:ss，兼容历史空格分隔格式）。
 * 容错：null/空白/格式漂移一律返回 null，UI 隐藏时间而非展示垃圾串。
 *
 * @param iso 后端下发的时间串
 * @param now 参照「现在」（默认系统当前时间；单测注入固定值）
 */
export function noticeTimeParts(iso: string | null | undefined,
  now: Date = new Date()): NoticeTimeParts | null {
  const raw = iso ?? ''
  if (raw.trim().length === 0) {
    return null
  }
  // 兼容空格分隔（历史格式）：仅替换首个空格为 T
  const normalized = raw.trim().replace(' ', 'T')
  const parsed = new Date(normalized)
  if (Number.isNaN(parsed.getTime())) {
    return null
  }
  const startOfDay = (d: Date): number =>
    new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const dayMs = 24 * 60 * 60 * 1000
  const diffDays = Math.round((startOfDay(now) - startOfDay(parsed)) / dayMs)
  let bucket = NoticeTimeBucket.OLDER
  if (diffDays === 0) {
    bucket = NoticeTimeBucket.TODAY
  } else if (diffDays === 1) {
    bucket = NoticeTimeBucket.YESTERDAY
  } else if (parsed.getFullYear() === now.getFullYear()) {
    bucket = NoticeTimeBucket.THIS_YEAR
  }
  return {
    bucket: bucket,
    time: `${pad2(parsed.getHours())}:${pad2(parsed.getMinutes())}`,
    month: parsed.getMonth() + 1,
    day: parsed.getDate(),
    year: parsed.getFullYear()
  }
}

/** 时间零件 → 中文文案（今天/昨天带时分，本年 M月d日，更早 yyyy-MM-dd） */
export function noticeTimeLabel(parts: NoticeTimeParts): string {
  switch (parts.bucket) {
    case NoticeTimeBucket.TODAY:
      return NoticeText.notice_time_today_format.replace('%s', parts.time)
    case NoticeTimeBucket.YESTERDAY:
      return NoticeText.notice_time_yesterday_format.replace('%s', parts.time)
    case NoticeTimeBucket.THIS_YEAR:
      return NoticeText.notice_time_this_year_format
        .replace('%d', `${parts.month}`)
        .replace('%d', `${parts.day}`)
    default:
      return NoticeText.notice_time_older_format
        .replace('%d', `${parts.year}`)
        .replace('%02d', pad2(parts.month))
        .replace('%02d', pad2(parts.day))
  }
}

/**
 * 未读数字角标文案（我的页入口角标消费）：
 * - null / 0 / 负数 → null（隐藏角标，不显示「0」）
 * - 1..99 → 原值
 * - >99 → 「99+」封顶（避免行尾值过长挤压标题）
 */
export function formatUnreadBadge(count: number | null): string | null {
  if (count === null || count <= 0) {
    return null
  }
  if (count > 99) {
    return '99+'
  }
  return `${count}`
}
