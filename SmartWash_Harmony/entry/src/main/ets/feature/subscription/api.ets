// 订阅逃生舱域契约 — VO / 请求体 / 常量 / 文案 / 接口封装
//
// 依据安卓 feature/subscription/network/SubscriptionApi.kt、SubscriptionEnums.kt、
// SubscriptionConstant.kt 与后端 UserSubscriptionController.java
// （@RequestMapping("/web/auth/subscription")，需 ROLE_USER）。
// 路径与参数逐一对齐（含 finish / usages / reminders / stats 等安卓已实现端点）：
//   POST   /web/auth/subscription                   创建（初始 active）
//   PUT    /web/auth/subscription/{id}              编辑（全字段可选，终态拒绝）
//   DELETE /web/auth/subscription/{id}              物理删除（级联清打卡 + 提醒）
//   GET    /web/auth/subscription?page&size&status&keyword  分页（nextRenewDate 升序）
//   GET    /web/auth/subscription/{id}              详情（含派生字段）
//   POST   /web/auth/subscription/{id}/pause         active → paused
//   POST   /web/auth/subscription/{id}/resume        paused → active
//   POST   /web/auth/subscription/{id}/cancel        → canceled 终态
//   POST   /web/auth/subscription/{id}/finish        → finished 终态
//   POST   /web/auth/subscription/{id}/usages        body{usageDate?} 使用打卡
//   GET    /web/auth/subscription/{id}/usages?page&size  打卡分页（日期倒序）
//   DELETE /web/auth/subscription/{id}/usages/{usageId}  撤销打卡
//   GET    /web/auth/subscription/reminders?unread  提醒列表（上限 200）
//   POST   /web/auth/subscription/reminders/{id}/read  标记已读（幂等）
//   GET    /web/auth/subscription/stats             统计（月度应收 + 近 90 天使用）
//
// 按 feature/README.md 约定：本文件只放声明层（VO + 常量 + 文案 + 接口封装），
// UI 组件与页面逻辑放域内各页面文件，纯逻辑放 logic/SubscriptionRules.ets。

import { BaseResponse } from '../../network/BaseResponse'
import { httpUtil } from '../../network/HttpUtil'
import { jsonNum, jsonStr, mapArray } from '../../network/vo/JsonParse'
import { resString } from '../../utils/ResourceString'

// ==================== 常量（对齐安卓 SubscriptionConstant） ====================

/** 订阅列表 / 打卡列表分页每页条数（后端 BaseSearchFrom 上限 100） */
export const SUBSCRIPTION_PAGE_SIZE: number = 10

/** 提醒列表接口单次返回上限（后端固定 200 条） */
export const SUBSCRIPTION_REMINDER_LIMIT: number = 200

/** autoRenew / isRead 的 0|1 布尔编码（对齐后端 tinyint） */
export const SUBSCRIPTION_FLAG_ON: number = 1
export const SUBSCRIPTION_FLAG_OFF: number = 0

/** 名称长度上限 */
export const SUBSCRIPTION_NAME_MAX: number = 100

/** 备注长度上限 */
export const SUBSCRIPTION_NOTE_MAX: number = 500

/** 取消步骤备忘长度上限 */
export const SUBSCRIPTION_CANCEL_STEPS_MAX: number = 1000

/** 取消网址长度上限 */
export const SUBSCRIPTION_CANCEL_URL_MAX: number = 500

/** 间隔期数范围 */
export const SUBSCRIPTION_CYCLE_INTERVAL_MIN: number = 1
export const SUBSCRIPTION_CYCLE_INTERVAL_MAX: number = 36

/** 提醒提前天数范围 */
export const SUBSCRIPTION_REMIND_DAYS_MIN: number = 0
export const SUBSCRIPTION_REMIND_DAYS_MAX: number = 60

// ==================== 枚举（对齐安卓 SubscriptionEnums） ====================

/** 订阅状态机：active ↔ paused 可互转；canceled/finished 终态只读 */
export enum SubscriptionStatus {
  ACTIVE = 'active',
  PAUSED = 'paused',
  CANCELED = 'canceled',
  FINISHED = 'finished'
}

/** 按原值取状态；未知/空返回 null */
export function subscriptionStatusFromRaw(raw: string | null | undefined): SubscriptionStatus | null {
  if (raw === SubscriptionStatus.ACTIVE) {
    return SubscriptionStatus.ACTIVE
  }
  if (raw === SubscriptionStatus.PAUSED) {
    return SubscriptionStatus.PAUSED
  }
  if (raw === SubscriptionStatus.CANCELED) {
    return SubscriptionStatus.CANCELED
  }
  if (raw === SubscriptionStatus.FINISHED) {
    return SubscriptionStatus.FINISHED
  }
  return null
}

/** 防御回落：未知/空按 active 处理（VO 默认值语义） */
export function subscriptionStatusFromRawOrDefault(raw: string | null | undefined): SubscriptionStatus {
  return subscriptionStatusFromRaw(raw) ?? SubscriptionStatus.ACTIVE
}

/** 终态只读判断（canceled/finished） */
export function subscriptionStatusIsTerminal(status: SubscriptionStatus): boolean {
  return status === SubscriptionStatus.CANCELED || status === SubscriptionStatus.FINISHED
}

/** 计费周期：month 月付 / year 年付 */
export enum SubscriptionCycleType {
  MONTH = 'month',
  YEAR = 'year'
}

/** 按原值取周期；未知按月付处理（与后端 month 默认一致） */
export function subscriptionCycleTypeFromRaw(raw: string | null | undefined): SubscriptionCycleType {
  return raw === SubscriptionCycleType.YEAR ? SubscriptionCycleType.YEAR : SubscriptionCycleType.MONTH
}

/** 提醒类型：renew 续费 / trial_end 试用截止 */
export enum SubscriptionReminderType {
  RENEW = 'renew',
  TRIAL_END = 'trial_end'
}

/** 按原值取提醒类型；未知按 renew 处理 */
export function subscriptionReminderTypeFromRawOrDefault(raw: string | null | undefined): SubscriptionReminderType {
  return raw === SubscriptionReminderType.TRIAL_END
    ? SubscriptionReminderType.TRIAL_END
    : SubscriptionReminderType.RENEW
}

// ==================== VO（按后端 vo 字段全量声明） ====================

/** 订阅视图（对齐后端 UserSubscriptionVo / 安卓 SubscriptionVo，字段名逐一勿改） */
export interface SubscriptionVo {
  subscriptionId: number
  userId: number
  name: string
  amount: number
  /** month/year */
  cycleType: string
  cycleInterval: number
  /** yyyy-MM-dd，可空 */
  nextRenewDate: string | null
  /** 首次锚点日（保留字段，v1 不入表单） */
  firstAnchorDate: string | null
  /** 试用截止日，可空 */
  trialEndDate: string | null
  /** 0 否 1 是 */
  autoRenew: number
  /** active/paused/canceled/finished */
  status: string
  cancelUrl: string | null
  cancelSteps: string | null
  remindDaysBefore: number
  note: string | null
  /** 距下次续费天数（负数 = 已过期待推进，后端派生） */
  daysUntilRenew: number | null
  /** 试用剩余天数（无试用为 null，后端派生） */
  trialDaysLeft: number | null
  /** 未读提醒数（后端派生） */
  unreadReminderCount: number
  createdAt: string | null
  updatedAt: string | null
}

/** 使用打卡流水视图（对齐后端 SubscriptionUsageVo / 安卓 UsageLogVo） */
export interface UsageLogVo {
  usageId: number
  subscriptionId: number
  usageDate: string | null
  note: string | null
  createdAt: string | null
}

/** 提醒视图（对齐后端 SubscriptionReminderVo / 安卓 ReminderVo） */
export interface ReminderVo {
  reminderId: number
  subscriptionId: number
  subscriptionName: string
  /** renew/trial_end */
  remindType: string
  remindDate: string | null
  /** 0 未读 1 已读 */
  isRead: number
  createdAt: string | null
}

/** 统计明细项（对齐后端 SubscriptionStatsVo.Item / 安卓 SubscriptionStatsItemVo） */
export interface SubscriptionStatsItemVo {
  subscriptionId: number
  name: string
  cycleType: string
  cycleInterval: number
  amount: number
  /** 折算月度金额（2 位小数） */
  monthlyNormalized: number
  /** 近 90 天使用次数 */
  usageCount90d: number
  /** 单次折算成本（无使用为 null） */
  perUseCost: number | null
}

/** 订阅统计视图（对齐后端 SubscriptionStatsVo / 安卓 SubscriptionStatsVo） */
export interface SubscriptionStatsVo {
  /** 月度应收总额（active 订阅折算月度金额之和，2 位小数） */
  monthlyPayable: number
  /** 活跃订阅数 */
  activeCount: number
  /** 各活跃订阅明细 */
  items: Array<SubscriptionStatsItemVo>
}

/**
 * 泛型分页信封（后端 MyBatis-Plus IPage 序列化：records/total/size/current）。
 * 不复用 network/vo/PageData（其 records 被硬编码成 RechargeRecordVo）。
 */
export interface SubscriptionPageData<T> {
  records: Array<T>
  total: number
  size: number
  current: number
}

// ==================== 请求体（对齐后端 from / 安卓请求体） ====================

/** 创建订阅请求体（对齐 AddSubscriptionFrom / 安卓 SaveSubscriptionRequest） */
export interface SaveSubscriptionForm {
  name: string
  amount: number
  cycleType: string
  cycleInterval: number
  nextRenewDate: string
  trialEndDate?: string | null
  autoRenew: number
  remindDaysBefore: number
  cancelUrl?: string | null
  cancelSteps?: string | null
  note?: string | null
}

/** 编辑订阅请求体（全字段可选，null = 不改；对齐 UpdateSubscriptionFrom / 安卓 UpdateSubscriptionRequest） */
export interface UpdateSubscriptionForm {
  name?: string | null
  amount?: number | null
  cycleType?: string | null
  cycleInterval?: number | null
  nextRenewDate?: string | null
  trialEndDate?: string | null
  autoRenew?: number | null
  remindDaysBefore?: number | null
  cancelUrl?: string | null
  cancelSteps?: string | null
  note?: string | null
}

/** 使用打卡请求体（对齐 AddUsageFrom；usageDate 不传默认今天。安卓契约仅 usageDate） */
export interface AddUsageForm {
  usageDate?: string | null
}

// ==================== JSON → VO 显式映射 ====================
// 背景见 network/vo/JsonParse.ets：Jackson 可能省略字段，interface 运行时不填充，
// 消费点一律经 jsonStr/jsonNum/mapArray 兜底，避免 undefined 透传。

/** JSON → SubscriptionVo；缺失字段回落空值/0（对齐安卓 VO 默认值） */
export function subscriptionVoFromJSON(raw: Object): SubscriptionVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    subscriptionId: jsonNum(obj['subscriptionId']) ?? 0,
    userId: jsonNum(obj['userId']) ?? 0,
    name: jsonStr(obj['name']) ?? '',
    amount: jsonNum(obj['amount']) ?? 0,
    cycleType: jsonStr(obj['cycleType']) ?? 'month',
    cycleInterval: jsonNum(obj['cycleInterval']) ?? 1,
    nextRenewDate: jsonStr(obj['nextRenewDate']),
    firstAnchorDate: jsonStr(obj['firstAnchorDate']),
    trialEndDate: jsonStr(obj['trialEndDate']),
    autoRenew: jsonNum(obj['autoRenew']) ?? 1,
    status: jsonStr(obj['status']) ?? 'active',
    cancelUrl: jsonStr(obj['cancelUrl']),
    cancelSteps: jsonStr(obj['cancelSteps']),
    remindDaysBefore: jsonNum(obj['remindDaysBefore']) ?? 0,
    note: jsonStr(obj['note']),
    daysUntilRenew: jsonNum(obj['daysUntilRenew']),
    trialDaysLeft: jsonNum(obj['trialDaysLeft']),
    unreadReminderCount: jsonNum(obj['unreadReminderCount']) ?? 0,
    createdAt: jsonStr(obj['createdAt']),
    updatedAt: jsonStr(obj['updatedAt'])
  }
}

/** JSON → UsageLogVo */
export function usageLogVoFromJSON(raw: Object): UsageLogVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    usageId: jsonNum(obj['usageId']) ?? 0,
    subscriptionId: jsonNum(obj['subscriptionId']) ?? 0,
    usageDate: jsonStr(obj['usageDate']),
    note: jsonStr(obj['note']),
    createdAt: jsonStr(obj['createdAt'])
  }
}

/** JSON → ReminderVo */
export function reminderVoFromJSON(raw: Object): ReminderVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    reminderId: jsonNum(obj['reminderId']) ?? 0,
    subscriptionId: jsonNum(obj['subscriptionId']) ?? 0,
    subscriptionName: jsonStr(obj['subscriptionName']) ?? '',
    remindType: jsonStr(obj['remindType']) ?? 'renew',
    remindDate: jsonStr(obj['remindDate']),
    isRead: jsonNum(obj['isRead']) ?? 0,
    createdAt: jsonStr(obj['createdAt'])
  }
}

/** JSON → SubscriptionStatsItemVo */
export function subscriptionStatsItemVoFromJSON(raw: Object): SubscriptionStatsItemVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    subscriptionId: jsonNum(obj['subscriptionId']) ?? 0,
    name: jsonStr(obj['name']) ?? '',
    cycleType: jsonStr(obj['cycleType']) ?? 'month',
    cycleInterval: jsonNum(obj['cycleInterval']) ?? 1,
    amount: jsonNum(obj['amount']) ?? 0,
    monthlyNormalized: jsonNum(obj['monthlyNormalized']) ?? 0,
    usageCount90d: jsonNum(obj['usageCount90d']) ?? 0,
    perUseCost: jsonNum(obj['perUseCost'])
  }
}

/** JSON → SubscriptionStatsVo；items 缺失兜底空数组 */
export function subscriptionStatsVoFromJSON(raw: Object): SubscriptionStatsVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    monthlyPayable: jsonNum(obj['monthlyPayable']) ?? 0,
    activeCount: jsonNum(obj['activeCount']) ?? 0,
    items: mapArray(obj['items'],
      (item: Object): SubscriptionStatsItemVo => subscriptionStatsItemVoFromJSON(item))
  }
}

/** JSON → 泛型分页信封；raw 缺失兜底空页 */
export function subscriptionPageDataFromJSON<T>(raw: Object | null | undefined,
  mapper: (item: Object) => T): SubscriptionPageData<T> {
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

// ==================== 文案表（逐一对齐安卓 subscription_* 字符串） ====================

export class SubscriptionText {
  // 页面标题与页头动作
  static get subscription_title(): string { return resString('subscription_title') }
  static get subscription_edit_title_new(): string { return resString('subscription_edit_title_new') }
  static get subscription_edit_title_edit(): string { return resString('subscription_edit_title_edit') }
  static get subscription_detail_title(): string { return resString('subscription_detail_title') }
  static get subscription_reminder_title(): string { return resString('subscription_reminder_title') }
  static get subscription_action_new(): string { return resString('subscription_action_new') }
  static get subscription_action_reminders(): string { return resString('subscription_action_reminders') }

  // 列表页
  static get subscription_filter_all(): string { return resString('subscription_filter_all') }
  static get subscription_filter_active(): string { return resString('subscription_filter_active') }
  static get subscription_filter_paused(): string { return resString('subscription_filter_paused') }
  static get subscription_filter_canceled(): string { return resString('subscription_filter_canceled') }
  static get subscription_filter_finished(): string { return resString('subscription_filter_finished') }
  static get subscription_empty_message(): string { return resString('subscription_empty_message') }
  static get subscription_empty_action(): string { return resString('subscription_edit_title_new') }
  static get subscription_error_load(): string { return resString('subscription_error_load') }
  static get subscription_action_retry(): string { return resString('subscription_action_retry') }
  static get subscription_amount_per_period(): string { return resString('subscription_text_subscription_amount_per_period') }
  static get subscription_cycle_monthly(): string { return resString('subscription_cycle_monthly') }
  static get subscription_cycle_every_n_months(): string { return resString('subscription_text_subscription_cycle_every_n_months') }
  static get subscription_cycle_yearly(): string { return resString('subscription_cycle_yearly') }
  static get subscription_cycle_every_n_years(): string { return resString('subscription_text_subscription_cycle_every_n_years') }
  static get subscription_renew_date_value(): string { return resString('subscription_text_subscription_renew_date_value') }
  static get subscription_renew_in_days_format(): string { return resString('subscription_text_subscription_renew_in_days_format') }
  static get subscription_renew_today(): string { return resString('subscription_renew_today') }
  static get subscription_renew_overdue_format(): string { return resString('subscription_text_subscription_renew_overdue_format') }
  static get subscription_renew_days_compact(): string { return resString('subscription_text_subscription_renew_days_compact') }
  static get subscription_trial_left_format(): string { return resString('subscription_text_subscription_trial_left_format') }
  static get subscription_trial_last_day(): string { return resString('subscription_trial_last_day') }
  static get subscription_unread_reminders_format(): string { return resString('subscription_text_subscription_unread_reminders_format') }

  // 状态文案
  static get subscription_status_active(): string { return resString('subscription_filter_active') }
  static get subscription_status_paused(): string { return resString('subscription_filter_paused') }
  static get subscription_status_canceled(): string { return resString('subscription_filter_canceled') }
  static get subscription_status_finished(): string { return resString('subscription_filter_finished') }

  // 添加/编辑表单
  static get subscription_form_name_label(): string { return resString('subscription_form_name_label') }
  static get subscription_form_name_hint(): string { return resString('subscription_form_name_hint') }
  static get subscription_form_amount_label(): string { return resString('subscription_form_amount_label') }
  static readonly subscription_form_amount_hint: string = "21.00"
  static get subscription_form_cycle_label(): string { return resString('subscription_form_cycle_label') }
  static get subscription_form_cycle_month(): string { return resString('subscription_form_cycle_month') }
  static get subscription_form_cycle_year(): string { return resString('subscription_form_cycle_year') }
  static get subscription_form_interval_label(): string { return resString('subscription_form_interval_label') }
  static get subscription_form_interval_helper(): string { return resString('subscription_form_interval_helper') }
  static get subscription_form_next_renew_label(): string { return resString('subscription_form_next_renew_label') }
  static get subscription_form_pick_date(): string { return resString('subscription_form_pick_date') }
  static get subscription_form_trial_label(): string { return resString('subscription_form_trial_label') }
  static get subscription_form_trial_none(): string { return resString('subscription_form_trial_none') }
  static get subscription_form_trial_clear(): string { return resString('subscription_form_trial_clear') }
  static get subscription_form_auto_renew_label(): string { return resString('subscription_form_auto_renew_label') }
  static get subscription_form_auto_renew_helper(): string { return resString('subscription_form_auto_renew_helper') }
  static get subscription_form_remind_label(): string { return resString('subscription_form_remind_label') }
  static get subscription_form_remind_helper(): string { return resString('subscription_form_remind_helper') }
  static get subscription_form_cancel_url_label(): string { return resString('subscription_form_cancel_url_label') }
  static readonly subscription_form_cancel_url_hint: string = "https://…"
  static get subscription_form_cancel_url_helper(): string { return resString('subscription_form_cancel_url_helper') }
  static get subscription_action_open_browser(): string { return resString('subscription_action_open_browser') }
  static get subscription_action_copy(): string { return resString('subscription_action_copy') }
  static get subscription_copy_success(): string { return resString('subscription_copy_success') }
  static get subscription_open_failed(): string { return resString('subscription_open_failed') }
  static get subscription_form_cancel_steps_label(): string { return resString('subscription_form_cancel_steps_label') }
  static get subscription_form_cancel_steps_hint(): string { return resString('subscription_form_cancel_steps_hint') }
  static get subscription_form_note_label(): string { return resString('subscription_form_note_label') }
  static get subscription_form_note_hint(): string { return resString('subscription_form_note_hint') }
  static get subscription_submit_create(): string { return resString('subscription_submit_create') }
  static get subscription_submit_save(): string { return resString('subscription_submit_save') }
  static get subscription_toast_create_success(): string { return resString('subscription_toast_create_success') }
  static get subscription_toast_save_success(): string { return resString('subscription_toast_save_success') }
  static get subscription_error_name_required(): string { return resString('subscription_error_name_required') }
  static get subscription_error_name_too_long(): string { return resString('subscription_error_name_too_long') }
  static get subscription_error_amount_invalid(): string { return resString('subscription_error_amount_invalid') }
  static get subscription_error_next_renew_required(): string { return resString('subscription_error_next_renew_required') }
  static get subscription_error_interval_invalid(): string { return resString('subscription_error_interval_invalid') }
  static get subscription_error_remind_days_invalid(): string { return resString('subscription_error_remind_days_invalid') }
  static get subscription_error_cancel_url_invalid(): string { return resString('subscription_error_cancel_url_invalid') }
  static get subscription_error_cancel_steps_too_long(): string { return resString('subscription_error_cancel_steps_too_long') }
  static get subscription_error_note_too_long(): string { return resString('subscription_error_note_too_long') }
  static get subscription_error_load_detail(): string { return resString('subscription_error_load_detail') }
  static get subscription_error_submit(): string { return resString('subscription_error_submit') }

  // 详情页
  static get subscription_detail_amount(): string { return resString('subscription_detail_amount') }
  static get subscription_detail_cycle(): string { return resString('subscription_form_cycle_label') }
  static get subscription_detail_next_renew(): string { return resString('subscription_form_next_renew_label') }
  static get subscription_detail_trial_end(): string { return resString('subscription_detail_trial_end') }
  static get subscription_detail_auto_renew(): string { return resString('subscription_form_auto_renew_label') }
  static get subscription_detail_auto_renew_on(): string { return resString('subscription_detail_auto_renew_on') }
  static get subscription_detail_auto_renew_off(): string { return resString('subscription_detail_auto_renew_off') }
  static get subscription_detail_status(): string { return resString('subscription_detail_status') }
  static get subscription_detail_remind_days(): string { return resString('subscription_detail_remind_days') }
  static get subscription_detail_remind_days_format(): string { return resString('subscription_text_subscription_detail_remind_days_format') }
  static get subscription_detail_remind_days_zero(): string { return resString('subscription_detail_remind_days_zero') }
  static get subscription_detail_note(): string { return resString('subscription_detail_note') }
  static readonly subscription_value_none: string = "—"
  static get subscription_action_edit(): string { return resString('subscription_action_edit') }
  static get subscription_action_pause(): string { return resString('subscription_action_pause') }
  static get subscription_action_resume(): string { return resString('subscription_action_resume') }
  static get subscription_action_cancel_sub(): string { return resString('subscription_action_cancel_sub') }
  static get subscription_action_finish(): string { return resString('subscription_action_finish') }
  static get subscription_action_delete(): string { return resString('subscription_action_delete') }
  static get subscription_confirm_cancel_title(): string { return resString('subscription_action_cancel_sub') }
  static get subscription_confirm_cancel_message(): string { return resString('subscription_text_subscription_confirm_cancel_message') }
  static get subscription_confirm_finish_title(): string { return resString('subscription_action_finish') }
  static get subscription_confirm_finish_message(): string { return resString('subscription_text_subscription_confirm_finish_message') }
  static get subscription_confirm_delete_title(): string { return resString('subscription_confirm_delete_title') }
  static get subscription_confirm_delete_message(): string { return resString('subscription_text_subscription_confirm_delete_message') }
  static get subscription_confirm_delete_usage_title(): string { return resString('subscription_confirm_delete_usage_title') }
  static get subscription_confirm_delete_usage_message(): string { return resString('subscription_text_subscription_confirm_delete_usage_message') }
  static get subscription_terminal_readonly(): string { return resString('subscription_terminal_readonly') }
  static get subscription_toast_status_updated(): string { return resString('subscription_toast_status_updated') }
  static get subscription_toast_delete_success(): string { return resString('subscription_toast_delete_success') }
  static get subscription_detail_retry(): string { return resString('subscription_detail_retry') }

  // 使用统计
  static get subscription_stats_title(): string { return resString('subscription_stats_title') }
  static get subscription_stats_usage_format(): string { return resString('subscription_text_subscription_stats_usage_format') }
  static get subscription_stats_per_use_format(): string { return resString('subscription_text_subscription_stats_per_use_format') }
  static get subscription_stats_per_use_none(): string { return resString('subscription_stats_per_use_none') }
  static get subscription_stats_monthly_format(): string { return resString('subscription_text_subscription_stats_monthly_format') }
  static get subscription_stats_error(): string { return resString('subscription_stats_error') }

  // 使用打卡
  static get subscription_usage_title(): string { return resString('subscription_usage_title') }
  static get subscription_usage_check_in(): string { return resString('subscription_usage_check_in') }
  static get subscription_usage_check_in_done(): string { return resString('subscription_usage_check_in_done') }
  static get subscription_usage_empty(): string { return resString('subscription_usage_empty') }
  static get subscription_usage_error_load(): string { return resString('subscription_usage_error_load') }
  static get subscription_usage_undo(): string { return resString('subscription_usage_undo') }
  static get subscription_usage_today(): string { return resString('subscription_usage_today') }
  static get subscription_toast_undo_success(): string { return resString('subscription_toast_undo_success') }

  // 取消路径
  static get subscription_cancel_path_title(): string { return resString('subscription_cancel_path_title') }
  static get subscription_cancel_path_url(): string { return resString('subscription_cancel_path_url') }
  static get subscription_cancel_path_steps(): string { return resString('subscription_cancel_path_steps') }
  static get subscription_cancel_path_empty(): string { return resString('subscription_cancel_path_empty') }

  // 提醒列表
  static get subscription_reminder_filter_unread(): string { return resString('subscription_reminder_filter_unread') }
  static get subscription_reminder_filter_all(): string { return resString('subscription_filter_all') }
  static get subscription_reminder_empty_unread(): string { return resString('subscription_reminder_empty_unread') }
  static get subscription_reminder_empty_all(): string { return resString('subscription_reminder_empty_all') }
  static get subscription_reminder_error_load(): string { return resString('subscription_reminder_error_load') }
  static get subscription_reminder_type_renew(): string { return resString('subscription_reminder_title') }
  static get subscription_reminder_type_trial_end(): string { return resString('subscription_detail_trial_end') }
  static get subscription_reminder_date_format(): string { return resString('subscription_text_subscription_reminder_date_format') }
  static get subscription_reminder_read_tag(): string { return resString('subscription_reminder_read_tag') }
  /** 未读红点读屏描述（颜色不是唯一信息载体，§10；对齐安卓 subscription_reminder_unread_dot） */
  static get subscription_reminder_unread_dot(): string { return resString('subscription_reminder_unread_dot') }

  // 日期弹窗
  static get subscription_date_confirm(): string { return resString('subscription_date_confirm') }
  static get subscription_date_cancel(): string { return resString('subscription_date_cancel') }
}

// ==================== 接口封装 ====================

/** 创建订阅（初始 active）。成功返回新订阅 VO。 */
export async function createSubscription(form: SaveSubscriptionForm): Promise<BaseResponse<SubscriptionVo>> {
  const body: Record<string, Object> = {}
  body['name'] = form.name
  body['amount'] = form.amount
  body['cycleType'] = form.cycleType
  body['cycleInterval'] = form.cycleInterval
  body['nextRenewDate'] = form.nextRenewDate
  body['autoRenew'] = form.autoRenew
  body['remindDaysBefore'] = form.remindDaysBefore
  if (form.trialEndDate !== undefined && form.trialEndDate !== null && form.trialEndDate.length > 0) {
    body['trialEndDate'] = form.trialEndDate
  }
  if (form.cancelUrl !== undefined && form.cancelUrl !== null && form.cancelUrl.length > 0) {
    body['cancelUrl'] = form.cancelUrl
  }
  if (form.cancelSteps !== undefined && form.cancelSteps !== null && form.cancelSteps.length > 0) {
    body['cancelSteps'] = form.cancelSteps
  }
  if (form.note !== undefined && form.note !== null && form.note.length > 0) {
    body['note'] = form.note
  }
  const res = await httpUtil.post<Object>('/web/auth/subscription', body)
  return {
    data: res.data === null ? null : subscriptionVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 编辑订阅（全字段可选，null = 不改；终态拒绝）。成功返回更新后的订阅 VO。 */
export async function updateSubscription(id: number,
  form: UpdateSubscriptionForm): Promise<BaseResponse<SubscriptionVo>> {
  const body: Record<string, Object> = {}
  if (form.name !== undefined && form.name !== null) {
    body['name'] = form.name
  }
  if (form.amount !== undefined && form.amount !== null) {
    body['amount'] = form.amount
  }
  if (form.cycleType !== undefined && form.cycleType !== null) {
    body['cycleType'] = form.cycleType
  }
  if (form.cycleInterval !== undefined && form.cycleInterval !== null) {
    body['cycleInterval'] = form.cycleInterval
  }
  if (form.nextRenewDate !== undefined && form.nextRenewDate !== null) {
    body['nextRenewDate'] = form.nextRenewDate
  }
  if (form.trialEndDate !== undefined && form.trialEndDate !== null && form.trialEndDate.length > 0) {
    body['trialEndDate'] = form.trialEndDate
  }
  if (form.autoRenew !== undefined && form.autoRenew !== null) {
    body['autoRenew'] = form.autoRenew
  }
  if (form.remindDaysBefore !== undefined && form.remindDaysBefore !== null) {
    body['remindDaysBefore'] = form.remindDaysBefore
  }
  if (form.cancelUrl !== undefined && form.cancelUrl !== null && form.cancelUrl.length > 0) {
    body['cancelUrl'] = form.cancelUrl
  }
  if (form.cancelSteps !== undefined && form.cancelSteps !== null && form.cancelSteps.length > 0) {
    body['cancelSteps'] = form.cancelSteps
  }
  if (form.note !== undefined && form.note !== null && form.note.length > 0) {
    body['note'] = form.note
  }
  const res = await httpUtil.put<Object>(`/web/auth/subscription/${id}`, body)
  return {
    data: res.data === null ? null : subscriptionVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 物理删除订阅（级联清打卡流水 + 提醒）；成功以 code=200 为准 */
export async function deleteSubscription(id: number): Promise<BaseResponse<null>> {
  return httpUtil.delete<null>(`/web/auth/subscription/${id}`)
}

/** 分页列表（按 nextRenewDate 升序）；page 从 1 起，status/keyword 可选筛 */
export async function pageSubscriptions(page: number, size: number,
  status?: string | null, keyword?: string | null): Promise<BaseResponse<SubscriptionPageData<SubscriptionVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  if (status !== undefined && status !== null && status.length > 0) {
    params['status'] = status
  }
  if (keyword !== undefined && keyword !== null && keyword.length > 0) {
    params['keyword'] = keyword
  }
  const res = await httpUtil.get<Object>('/web/auth/subscription', params)
  return {
    data: res.data === null ? null : subscriptionPageDataFromJSON<SubscriptionVo>(res.data, subscriptionVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/** 详情（含 daysUntilRenew/trialDaysLeft/unreadReminderCount 派生字段） */
export async function getSubscription(id: number): Promise<BaseResponse<SubscriptionVo>> {
  const res = await httpUtil.get<Object>(`/web/auth/subscription/${id}`)
  return {
    data: res.data === null ? null : subscriptionVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 暂停（active → paused；非法流转后端拒绝） */
export async function pauseSubscription(id: number): Promise<BaseResponse<null>> {
  return httpUtil.post<null>(`/web/auth/subscription/${id}/pause`)
}

/** 恢复（paused → active） */
export async function resumeSubscription(id: number): Promise<BaseResponse<null>> {
  return httpUtil.post<null>(`/web/auth/subscription/${id}/resume`)
}

/** 取消（active/paused → canceled 终态，保留数据） */
export async function cancelSubscription(id: number): Promise<BaseResponse<null>> {
  return httpUtil.post<null>(`/web/auth/subscription/${id}/cancel`)
}

/** 结束（active/paused → finished 终态，语义为试用结束不再续用） */
export async function finishSubscription(id: number): Promise<BaseResponse<null>> {
  return httpUtil.post<null>(`/web/auth/subscription/${id}/finish`)
}

/** 使用打卡（usageDate 不传默认今天，禁未来；每订阅每天一条，重复拒绝） */
export async function checkInUsage(id: number, form: AddUsageForm): Promise<BaseResponse<null>> {
  const body: Record<string, Object> = {}
  if (form.usageDate !== undefined && form.usageDate !== null && form.usageDate.length > 0) {
    body['usageDate'] = form.usageDate
  }
  return httpUtil.post<null>(`/web/auth/subscription/${id}/usages`, body)
}

/** 打卡分页（按使用日期倒序）；page 从 1 起 */
export async function pageUsages(id: number, page: number,
  size: number): Promise<BaseResponse<SubscriptionPageData<UsageLogVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  const res = await httpUtil.get<Object>(`/web/auth/subscription/${id}/usages`, params)
  return {
    data: res.data === null ? null : subscriptionPageDataFromJSON<UsageLogVo>(res.data, usageLogVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/** 撤销打卡（物理删除） */
export async function deleteUsage(id: number, usageId: number): Promise<BaseResponse<null>> {
  return httpUtil.delete<null>(`/web/auth/subscription/${id}/usages/${usageId}`)
}

/** 提醒列表（unread=true 只看未读；remindDate 倒序，上限 200 条） */
export async function listReminders(unread?: boolean | null): Promise<BaseResponse<Array<ReminderVo>>> {
  const params: Record<string, Object> = {}
  if (unread !== undefined && unread !== null) {
    params['unread'] = unread
  }
  const res = await httpUtil.get<Object>('/web/auth/subscription/reminders', params)
  return {
    data: res.data === null ? null : mapArray(res.data,
      (item: Object): ReminderVo => reminderVoFromJSON(item)),
    code: res.code,
    message: res.message
  }
}

/** 标记已读（幂等，重复标记成功） */
export async function markReminderRead(reminderId: number): Promise<BaseResponse<null>> {
  return httpUtil.post<null>(`/web/auth/subscription/reminders/${reminderId}/read`)
}

/** 统计（月度应收总额 + 各订阅近 90 天使用次数/单次折算成本） */
export async function getStats(): Promise<BaseResponse<SubscriptionStatsVo>> {
  const res = await httpUtil.get<Object>('/web/auth/subscription/stats')
  return {
    data: res.data === null ? null : subscriptionStatsVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}
