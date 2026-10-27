// 短链表单与展示的纯逻辑（零 UI 依赖，逐行对齐安卓 feature/toolbox/logic/ShortCodeRules.kt）。
//
// target/别名校验规则对齐后端 AddShortCodeFrom（短链内核实现方案 §5.1/§5.5），
// 过期计算与展示映射为纯函数，时间一律由调用方注入保证可测。
// 说明：ExpiryPreset/ExpiryState 两个域私有枚举随本纯函数模块走（对齐安卓 ShortCodeRules.kt 同文件定义），
// 未放 api.ets —— 避免纯逻辑模块反向依赖网络层（api.ets 会 import HttpUtil）。
// 时间格式：expireAt 为无时区 ISO LocalDateTime（yyyy-MM-dd'T'HH:mm:ss），
// 鸿蒙 new Date 对「无时区日期时间串」按本地时区解析，与安卓 LocalDateTime.parse 行为一致。

import { VISIBILITY_PRIVATE } from './api'

/** 自定义别名：4-16 位字母数字及 -_（对齐后端 ShortCodeGenerator.CUSTOM_CODE / AddShortCodeFrom @Pattern） */
export const CUSTOM_CODE_REGEX: RegExp = /^[0-9a-zA-Z-_]{4,16}$/

/** 目标链接：必须 http/https 且非空白（防开放重定向的端上前置校验，后端仍终审） */
const TARGET_REGEX: RegExp = /^https?:\/\/\S+$/i

/** 请求/展示格式常量（对齐安卓 REQUEST_FORMAT / DISPLAY_FORMAT / DATE_FORMAT） */
const REQUEST_FORMAT: string = "yyyy-MM-dd'T'HH:mm:ss"
const DISPLAY_FORMAT: string = "yyyy-MM-dd HH:mm"
const DATE_FORMAT: string = "yyyy-MM-dd"

/** 过期预设（永久 / 7 天 / 30 天 / 自定义日期），M1 更新侧仅暴露 7/30/自定义 */
export enum ExpiryPreset {
  PERMANENT,
  DAYS_7,
  DAYS_30,
  CUSTOM
}

/** 过期时刻展示态：永久（null/不可解析防御为永久）/ 有效中 / 已过期 */
export enum ExpiryState {
  PERMANENT,
  ACTIVE,
  EXPIRED
}

export function isValidTarget(target: string): boolean {
  return TARGET_REGEX.test(target.trim())
}

export function isValidCustomCode(code: string): boolean {
  return CUSTOM_CODE_REGEX.test(code)
}

/**
 * 预设 → 过期时刻 ISO 串：固定时刻取 now+n 天，自定义取所选日期当天 23:59:59（过期语义为「当天可用完」）。
 * 永久 → null（不传 = 永久）；自定义未选日期 → null（UI 侧须先校验日期已选）。
 *
 * @param customDate 自定义日期的「yyyy-MM-dd」串（来自 DatePicker 本地选择结果），可为 null
 * @param now 当前时刻（调用方注入，便于单测固定过期计算结果）
 */
export function expireAtFor(preset: ExpiryPreset, customDate: string | null, now: Date): string | null {
  if (preset === ExpiryPreset.PERMANENT) {
    return null
  }
  if (preset === ExpiryPreset.DAYS_7) {
    return formatRequestDateTime(addDays(now, 7))
  }
  if (preset === ExpiryPreset.DAYS_30) {
    return formatRequestDateTime(addDays(now, 30))
  }
  if (customDate === null || customDate.length === 0) {
    return null
  }
  // 所选日期当天 23:59:59
  return `${customDate}T23:59:59`
}

/**
 * 过期时刻展示态。
 * @param expireAt ISO LocalDateTime 串，可为 null/空串（永久）
 * @param now 当前时刻（调用方注入）
 */
export function expiryStateOf(expireAt: string | null | undefined, now: Date): ExpiryState {
  const parsed = parseIsoOrNull(expireAt)
  if (parsed === null) {
    return ExpiryState.PERMANENT
  }
  return parsed.getTime() < now.getTime() ? ExpiryState.EXPIRED : ExpiryState.ACTIVE
}

/** ISO LocalDateTime → 「yyyy-MM-dd HH:mm」展示；不可解析原样返回 */
export function formatDisplayDateTime(iso: string | null | undefined): string {
  const raw = iso ?? ""
  const parsed = parseIsoOrNull(raw)
  if (parsed === null) {
    return raw
  }
  return formatWithPattern(parsed, DISPLAY_FORMAT)
}

/** Date → 「yyyy-MM-dd」（自定义过期日期按钮的已选展示） */
export function formatDate(date: Date): string {
  return formatWithPattern(date, DATE_FORMAT)
}

/** Date → 请求格式「yyyy-MM-dd'T'HH:mm:ss」 */
export function formatRequestDateTime(date: Date): string {
  return formatWithPattern(date, REQUEST_FORMAT)
}

/** 可见性映射：非 2（私有）一律按公开处理（对齐后端 1公开/2私有） */
export function isPublicVisibility(isPublic: number): boolean {
  return isPublic !== VISIBILITY_PRIVATE
}

/**
 * 解析无时区 ISO LocalDateTime。
 * 仅接受 yyyy-MM-dd'T'HH:mm:ss / 省略秒的 yyyy-MM-dd'T'HH:mm 形式；其余返回 null（按永久处理）。
 */
function parseIsoOrNull(raw: string | null | undefined): Date | null {
  const value = raw ?? ""
  if (value.length === 0) {
    return null
  }
  // 秒可省略（ISO_LOCAL_DATE_TIME 语义），统一补 :00 后按严格格式解析
  const normalized = /:\d{2}$/.test(value) ? value : `${value}:00`
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})$/.exec(normalized)
  if (match === null) {
    return null
  }
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const hour = Number(match[4])
  const minute = Number(match[5])
  const second = Number(match[6])
  // 越界直接判为不可解析（对齐 LocalDateTime.parse 的严格性，避免 Date 进位掩盖脏数据）
  if (month < 1 || month > 12 || day < 1 || day > 31 ||
    hour > 23 || minute > 59 || second > 59) {
    return null
  }
  // 以「无时区 = 本地时区」构造，保证与展示/比较同基准
  const parsed = new Date(year, month - 1, day, hour, minute, second)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/** 在保持时分秒不变的前提下加 n 天（对齐安卓 LocalDateTime.plusDays） */
function addDays(date: Date, days: number): Date {
  return new Date(
    date.getFullYear(), date.getMonth(), date.getDate() + days,
    date.getHours(), date.getMinutes(), date.getSeconds(), date.getMilliseconds()
  )
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
