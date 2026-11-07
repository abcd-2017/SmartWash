/**
 * 取件提醒纯逻辑层（零系统 API / UI / 网络依赖，hypium 可单测）。
 *
 * 职责（对齐调研候选 2 拆层原则——决策与文案可独立验证，系统调用收口在 ReminderService）：
 * - 状态口径：待取件(6)登记、终态(已完成7/已取消-2/已退款-1)撤销（GLOSSARY §3.3 订单状态机）；
 * - 防重决策：登记表（orderId → reminderId）已有登记则不再发布；
 * - 触发时间：倒计时型延迟分钟 → 秒（默认 30 分钟）；
 * - 文案选择：通用版 / 带柜号版（与 GLOSSARY「待取件 = 衣物已到寄存柜」口径一致）；
 * - 登记表序列化：preferences 存 JSON 串，解析走 interface + 显式映射（严格模式红线）。
 */

import { OrderStatusEnum } from '../constant/OrderStatus'
import { jsonNum } from '../network/vo/JsonParse'

/** 提醒默认延迟（分钟）：订单进入待取件后 30 分钟提醒取件 */
export const REMINDER_DELAY_MINUTES_DEFAULT: number = 30

/**
 * 文案兜底常量（与 resources/base/element/string.json 的 pickup_reminder_* 三条同口径）。
 * ReminderService.initPickupReminder 会在启动时注入资源值，注入失败（资源缺失/上下文异常）
 * 回落到本常量——保证任何情况下提醒文案可用。
 */
export const REMINDER_TITLE_FALLBACK: string = '衣物已洗好'
export const REMINDER_CONTENT_PREFIX_FALLBACK: string = '衣物已洗好'
export const REMINDER_CONTENT_SUFFIX_FALLBACK: string = '请及时到寄存柜取件，超时可能占用公共筒位'

// 模块级文案缓存：applyReminderStrings 注入资源值后全局生效（ReminderLogic 无 context 依赖的关键）
let reminderTitle: string = REMINDER_TITLE_FALLBACK
let reminderContentPrefix: string = REMINDER_CONTENT_PREFIX_FALLBACK
let reminderContentSuffix: string = REMINDER_CONTENT_SUFFIX_FALLBACK

/**
 * 注入资源文案（EntryAbility 启动时调用）。入参 trim 后为空串按未注入处理，
 * 保留既有值（资源占位符漏配时不至于把文案清空）。
 */
export function applyReminderStrings(title: string, contentPrefix: string, contentSuffix: string): void {
  if (title.trim() !== '') {
    reminderTitle = title
  }
  if (contentPrefix.trim() !== '') {
    reminderContentPrefix = contentPrefix
  }
  if (contentSuffix.trim() !== '') {
    reminderContentSuffix = contentSuffix
  }
}

/** 提醒标题（通知栏 / 提醒中心展示） */
export function buildReminderTitle(): string {
  return reminderTitle
}

/**
 * 提醒内容（文案选择纯函数）：柜号有效（> 0）输出带柜号细节版，否则输出通用版。
 * GLOSSARY 待取件口径：订单转待取件时切换新柜子——柜号能让用户直达对应柜位。
 */
export function buildReminderContent(lockerNumber: number | null | undefined): string {
  if (lockerNumber === null || lockerNumber === undefined || lockerNumber <= 0) {
    return `${reminderContentPrefix}，${reminderContentSuffix}。`
  }
  return `${reminderContentPrefix}，已放入 ${lockerNumber} 号柜，${reminderContentSuffix}。`
}

/**
 * 倒计时触发秒数：分钟 → 秒（ReminderRequestTimer.triggerTimeInSeconds 单位为秒）。
 * 按秒级向下取整（15.5 分钟 → 930 秒），非法入参（非有限数 / ≤ 0）兜底默认延迟——
 * 上游配置漂移不得产生 0 秒提醒风暴。
 */
export function computeTriggerSeconds(delayMinutes: number): number {
  if (!Number.isFinite(delayMinutes) || delayMinutes <= 0) {
    return REMINDER_DELAY_MINUTES_DEFAULT * 60
  }
  return Math.floor(delayMinutes * 60)
}

/** 是否待取件态（登记口径：状态码 "6"，GLOSSARY §3.3） */
export function isPickupPendingStatus(status: string | null | undefined): boolean {
  return status === OrderStatusEnum.READY_FOR_PICKUP
}

/** 是否应撤销提醒的终态（已完成 / 已取消 / 已退款；-1/-2 为终态，6→7 是待取件唯一合法出口） */
export function isReminderCancelStatus(status: string | null | undefined): boolean {
  return status === OrderStatusEnum.COMPLETED
    || status === OrderStatusEnum.CANCELED
    || status === OrderStatusEnum.REFUNDED
}

/** 提醒动作决策结果 */
export enum ReminderAction {
  /** 无动作（中间态 / 已按当前口径处理过） */
  NONE = 0,
  /** 登记提醒（待取件且未登记） */
  REGISTER = 1,
  /** 撤销提醒（终态且仍有登记） */
  CANCEL = 2
}

/**
 * 防重决策核心：待取件未登记 → REGISTER；终态已登记 → CANCEL；其余一律 NONE。
 * 同订单重复登记的防线就在这一步（列表刷新/页面重进都会重复触发同步）。
 */
export function decideReminderAction(status: string | null | undefined,
  alreadyRegistered: boolean): ReminderAction {
  if (isPickupPendingStatus(status)) {
    return alreadyRegistered ? ReminderAction.NONE : ReminderAction.REGISTER
  }
  if (isReminderCancelStatus(status)) {
    return alreadyRegistered ? ReminderAction.CANCEL : ReminderAction.NONE
  }
  return ReminderAction.NONE
}

/** 登记表条目：orderId → 系统 reminderId 映射（preferences JSON 串持久化） */
export interface ReminderRegistryEntry {
  orderId: number
  reminderId: number
  registeredAt: number
}

/**
 * 序列化登记表为 JSON 串（字段全 number，手工拼接防字段漂移；空表输出 "[]"）。
 * 不用 JSON.stringify(Object)：ArkTS 严格模式下 interface 不保证运行时形状，手工拼接最稳。
 */
export function serializeRegistry(entries: Array<ReminderRegistryEntry>): string {
  const parts: Array<string> = []
  for (const entry of entries) {
    parts.push(`{"orderId":${entry.orderId},"reminderId":${entry.reminderId},"registeredAt":${entry.registeredAt}}`)
  }
  return `[${parts.join(',')}]`
}

/**
 * 解析登记表：非法 JSON / 非数组 / 条目字段缺失或类型漂移一律跳过，绝不抛异常。
 * 读侧容错是登记表作为「唯一事实源」的自愈基础——脏数据最多等于空表（重建登记）。
 */
export function parseRegistry(raw: string | null | undefined): Array<ReminderRegistryEntry> {
  if (raw === null || raw === undefined || raw === '') {
    return []
  }
  let parsed: Object | null = null
  try {
    parsed = JSON.parse(raw)
  } catch (err) {
    return []
  }
  if (parsed === null || !Array.isArray(parsed)) {
    return []
  }
  // Array.isArray 收窄后 ArkTS 类型系统仍视为 Object：与 network/vo/JsonParse.mapArray 同款收窄惯例
  const arr: Array<Object | null> = parsed as Array<Object | null>
  const result: Array<ReminderRegistryEntry> = []
  for (let i = 0; i < arr.length; i++) {
    const item: Object | null = arr[i]
    if (item === null || typeof item !== 'object') {
      continue
    }
    const rec: Record<string, Object> = item as Record<string, Object>
    const orderId: number | null = jsonNum(rec['orderId'])
    const reminderId: number | null = jsonNum(rec['reminderId'])
    const registeredAt: number | null = jsonNum(rec['registeredAt'])
    if (orderId !== null && reminderId !== null) {
      const entry: ReminderRegistryEntry = {
        orderId: orderId,
        reminderId: reminderId,
        registeredAt: registeredAt ?? 0
      }
      result.push(entry)
    }
  }
  return result
}

/** 查询登记：已登记返回系统 reminderId，否则 null */
export function findRegisteredReminderId(entries: Array<ReminderRegistryEntry>, orderId: number): number | null {
  for (const entry of entries) {
    if (entry.orderId === orderId) {
      return entry.reminderId
    }
  }
  return null
}

/** 移除登记并返回新数组（不修改入参，调用方直接以返回值为准） */
export function removeRegistryEntry(entries: Array<ReminderRegistryEntry>,
  orderId: number): Array<ReminderRegistryEntry> {
  const next: Array<ReminderRegistryEntry> = []
  for (const entry of entries) {
    if (entry.orderId !== orderId) {
      next.push(entry)
    }
  }
  return next
}

/** 追加/覆盖登记：同订单先移除再追加，保证登记表内同订单至多一条 */
export function upsertRegistryEntry(entries: Array<ReminderRegistryEntry>,
  entry: ReminderRegistryEntry): Array<ReminderRegistryEntry> {
  const next = removeRegistryEntry(entries, entry.orderId)
  next.push(entry)
  return next
}
