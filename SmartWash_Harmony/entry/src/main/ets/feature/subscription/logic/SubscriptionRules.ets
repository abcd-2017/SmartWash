// 订阅表单与展示的纯逻辑（零 UI 依赖，逐行对齐安卓 feature/subscription/logic/SubscriptionRules.kt）。
//
// 取消网址校验对齐后端 SaveSubscriptionFrom（http/https 且非空白，后端仍终审），
// 日期解析/进度环分母为纯函数，解析失败一律返回 null 由 UI 防御降级。
// 说明：后端日期格式为 yyyy-MM-dd（无时区 LocalDate），按本地时区构造 Date 即可，
// 与展示/比较同基准；月末 clamp 行为与 Java LocalDate.plusMonths/plusYears 对齐。

import { SubscriptionCycleType, subscriptionStatusFromRawOrDefault, subscriptionStatusIsTerminal } from '../api'

/** 日期格式：yyyy-MM-dd（nextRenewDate/trialEndDate/remindDate/usageDate） */
const DATE_FORMAT: string = 'yyyy-MM-dd'

/** 时间戳格式：yyyy-MM-dd HH:mm:ss（createdAt/updatedAt） */
const DATE_TIME_FORMAT: string = 'yyyy-MM-dd HH:mm:ss'

/** 展示格式：yyyy-MM-dd HH:mm */
const DISPLAY_FORMAT: string = 'yyyy-MM-dd HH:mm'

/** 取消网址：必须 http/https 且非空白（对齐后端校验，端上前置） */
const CANCEL_URL_REGEX: RegExp = /^https?:\/\/\S+$/i

const DATE_REGEX: RegExp = /^(\d{4})-(\d{2})-(\d{2})$/
const DATE_TIME_REGEX: RegExp = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})$/

export function isValidCancelUrl(url: string): boolean {
  return CANCEL_URL_REGEX.test(url.trim())
}

/** 严格解析 yyyy-MM-dd；越界/非法返回 null（避免 Date 进位掩盖脏数据） */
export function parseDate(raw: string | null | undefined): Date | null {
  const text = raw ?? ''
  if (text.length === 0) {
    return null
  }
  const match = DATE_REGEX.exec(text)
  if (match === null) {
    return null
  }
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return null
  }
  const parsed = new Date(year, month - 1, day)
  if (Number.isNaN(parsed.getTime()) || parsed.getMonth() !== month - 1 || parsed.getDate() !== day) {
    return null
  }
  return parsed
}

/** Date → yyyy-MM-dd */
export function formatDate(date: Date): string {
  return formatWithPattern(date, DATE_FORMAT)
}

/** 严格解析 yyyy-MM-dd HH:mm:ss（兼容 T 分隔）；非法返回 null */
export function parseDateTime(raw: string | null | undefined): Date | null {
  const text = raw ?? ''
  if (text.length === 0) {
    return null
  }
  const match = DATE_TIME_REGEX.exec(text)
  if (match === null) {
    return null
  }
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const hour = Number(match[4])
  const minute = Number(match[5])
  const second = Number(match[6])
  if (month < 1 || month > 12 || day < 1 || day > 31 ||
    hour > 23 || minute > 59 || second > 59) {
    return null
  }
  const parsed = new Date(year, month - 1, day, hour, minute, second)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/** 后端时间戳 → 「yyyy-MM-dd HH:mm」展示；不可解析原样返回 */
export function formatDisplayDateTime(raw: string | null | undefined): string {
  const text = raw ?? ''
  const parsed = parseDateTime(text)
  if (parsed === null) {
    return text
  }
  return formatWithPattern(parsed, DISPLAY_FORMAT)
}

/** 金额两位小数（amount 为 8,2 定点；负值不可能出现——后端校验 >0） */
export function formatAmount(amount: number): string {
  return amount.toFixed(2)
}

/**
 * 当期周期总天数（进度环分母）：从下个续费日按周期三元组回退一期，
 * 与后端 SubscriptionRenewJob 的推进方向互逆（月付 plusMonths/年付 plusYears 的 clamp 行为一致）。
 */
export function cycleTotalDays(cycleType: SubscriptionCycleType, interval: number,
  nextRenewDate: Date): number {
  const safeInterval = interval > 0 ? interval : 1
  const previous = cycleType === SubscriptionCycleType.YEAR
    ? addMonthsClamped(nextRenewDate, -safeInterval * 12)
    : addMonthsClamped(nextRenewDate, -safeInterval)
  const days = daysBetween(previous, nextRenewDate)
  return days > 0 ? days : 1
}

/**
 * 续费周期进度 0..1（进度环填充度）：已消耗天数 / 当期总天数。
 * daysUntilRenew 为负（已过期待推进）视为走满 1；输入不可解析返回 null（UI 隐藏环）。
 */
export function renewProgressFraction(cycleType: SubscriptionCycleType, interval: number,
  nextRenewDate: string | null | undefined,
  daysUntilRenew: number | null | undefined): number | null {
  const renewDate = parseDate(nextRenewDate)
  if (renewDate === null || daysUntilRenew === null || daysUntilRenew === undefined) {
    return null
  }
  const total = Math.max(cycleTotalDays(cycleType, interval, renewDate), 1)
  const consumed = total - daysUntilRenew
  return Math.min(1, Math.max(0, consumed / total))
}

/** 终态（canceled/finished）只读判断：UI 显隐编辑/打卡入口 */
export function isTerminal(raw: string | null | undefined): boolean {
  return subscriptionStatusIsTerminal(subscriptionStatusFromRawOrDefault(raw))
}

/**
 * 按整月位移并做月末 clamp（对齐 Java LocalDate.plusMonths）：
 * 目标月天数不足时取该月最后一天（如 1/31 → 2/28）。
 */
function addMonthsClamped(date: Date, months: number): Date {
  const day = date.getDate()
  const target = new Date(date.getFullYear(), date.getMonth() + months, 1)
  const daysInMonth = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(day, daysInMonth))
  return target
}

/** 两个本地零点日期之间的整天数（规避 DST 偏移） */
function daysBetween(from: Date, to: Date): number {
  const a = new Date(from.getFullYear(), from.getMonth(), from.getDate()).getTime()
  const b = new Date(to.getFullYear(), to.getMonth(), to.getDate()).getTime()
  return Math.round((b - a) / 86_400_000)
}

/** 按 yyyy/MM/dd/HH/mm/ss 占位符格式化（与 utils/TimeUtil.formatDate 同实现，避免跨模块耦合） */
function formatWithPattern(date: Date, pattern: string): string {
  const year = date.getFullYear().toString()
  const month = pad2(date.getMonth() + 1)
  const day = pad2(date.getDate())
  const hour = pad2(date.getHours())
  const minute = pad2(date.getMinutes())
  const second = pad2(date.getSeconds())
  return pattern
    .replace('yyyy', year)
    .replace('MM', month)
    .replace('dd', day)
    .replace('HH', hour)
    .replace('mm', minute)
    .replace('ss', second)
}

function pad2(value: number): string {
  return value < 10 ? `0${value}` : value.toString()
}
