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
  static readonly subscription_title: string = "订阅管理"
  static readonly subscription_edit_title_new: string = "新建订阅"
  static readonly subscription_edit_title_edit: string = "编辑订阅"
  static readonly subscription_detail_title: string = "订阅详情"
  static readonly subscription_reminder_title: string = "续费提醒"
  static readonly subscription_action_new: string = "新建"
  static readonly subscription_action_reminders: string = "提醒"

  // 列表页
  static readonly subscription_filter_all: string = "全部"
  static readonly subscription_filter_active: string = "进行中"
  static readonly subscription_filter_paused: string = "已暂停"
  static readonly subscription_filter_canceled: string = "已取消"
  static readonly subscription_filter_finished: string = "已结束"
  static readonly subscription_empty_message: string = "还没有订阅，把正在按期扣费的服务登记进来吧"
  static readonly subscription_empty_action: string = "新建订阅"
  static readonly subscription_error_load: string = "订阅加载失败"
  static readonly subscription_action_retry: string = "重试"
  static readonly subscription_amount_per_period: string = "¥%s/期"
  static readonly subscription_cycle_monthly: string = "每月"
  static readonly subscription_cycle_every_n_months: string = "每 %d 个月"
  static readonly subscription_cycle_yearly: string = "每年"
  static readonly subscription_cycle_every_n_years: string = "每 %d 年"
  static readonly subscription_renew_date_value: string = "下次续费 %s"
  static readonly subscription_renew_in_days_format: string = "%d 天后续费"
  static readonly subscription_renew_today: string = "今日续费"
  static readonly subscription_renew_overdue_format: string = "已逾期 %d 天"
  static readonly subscription_renew_days_compact: string = "%d天"
  static readonly subscription_trial_left_format: string = "试用剩 %d 天"
  static readonly subscription_trial_last_day: string = "试用今日截止"
  static readonly subscription_unread_reminders_format: string = "%d 条未读提醒"

  // 状态文案
  static readonly subscription_status_active: string = "进行中"
  static readonly subscription_status_paused: string = "已暂停"
  static readonly subscription_status_canceled: string = "已取消"
  static readonly subscription_status_finished: string = "已结束"

  // 添加/编辑表单
  static readonly subscription_form_name_label: string = "名称"
  static readonly subscription_form_name_hint: string = "如 iCloud+、健身房年卡"
  static readonly subscription_form_amount_label: string = "每期金额（元）"
  static readonly subscription_form_amount_hint: string = "21.00"
  static readonly subscription_form_cycle_label: string = "计费周期"
  static readonly subscription_form_cycle_month: string = "月付"
  static readonly subscription_form_cycle_year: string = "年付"
  static readonly subscription_form_interval_label: string = "间隔期数"
  static readonly subscription_form_interval_helper: string = "1-36，填 3 表示每 3 期扣一次"
  static readonly subscription_form_next_renew_label: string = "下次续费日"
  static readonly subscription_form_pick_date: string = "选择日期"
  static readonly subscription_form_trial_label: string = "试用截止（可选）"
  static readonly subscription_form_trial_none: string = "未设置"
  static readonly subscription_form_trial_clear: string = "清除"
  static readonly subscription_form_auto_renew_label: string = "自动续费"
  static readonly subscription_form_auto_renew_helper: string = "开启后到期自动顺延续费日"
  static readonly subscription_form_remind_label: string = "提醒提前天数"
  static readonly subscription_form_remind_helper: string = "0-60 天，0 表示仅到期当天提醒"
  static readonly subscription_form_cancel_url_label: string = "取消网址（可选）"
  static readonly subscription_form_cancel_url_hint: string = "https://…"
  static readonly subscription_form_cancel_url_helper: string = "须以 http:// 或 https:// 开头"
  static readonly subscription_action_open_browser: string = "打开"
  static readonly subscription_action_copy: string = "复制"
  static readonly subscription_copy_success: string = "已复制"
  static readonly subscription_open_failed: string = "未找到可打开链接的应用"
  static readonly subscription_form_cancel_steps_label: string = "取消步骤备忘（可选）"
  static readonly subscription_form_cancel_steps_hint: string = "如：设置 → Apple ID → 订阅 → iCloud+ → 取消"
  static readonly subscription_form_note_label: string = "备注（可选）"
  static readonly subscription_form_note_hint: string = "如：与家人共享"
  static readonly subscription_submit_create: string = "创建"
  static readonly subscription_submit_save: string = "保存"
  static readonly subscription_toast_create_success: string = "创建成功"
  static readonly subscription_toast_save_success: string = "已保存"
  static readonly subscription_error_name_required: string = "请填写订阅名称"
  static readonly subscription_error_name_too_long: string = "名称不能超过 100 字"
  static readonly subscription_error_amount_invalid: string = "金额须为大于 0 的数字"
  static readonly subscription_error_next_renew_required: string = "请选择下次续费日"
  static readonly subscription_error_interval_invalid: string = "间隔期数须为 1-36 的整数"
  static readonly subscription_error_remind_days_invalid: string = "提醒提前天数须为 0-60 的整数"
  static readonly subscription_error_cancel_url_invalid: string = "取消网址须以 http:// 或 https:// 开头"
  static readonly subscription_error_cancel_steps_too_long: string = "取消步骤不能超过 1000 字"
  static readonly subscription_error_note_too_long: string = "备注不能超过 500 字"
  static readonly subscription_error_load_detail: string = "详情加载失败"
  static readonly subscription_error_submit: string = "提交失败，请重试"

  // 详情页
  static readonly subscription_detail_amount: string = "每期金额"
  static readonly subscription_detail_cycle: string = "计费周期"
  static readonly subscription_detail_next_renew: string = "下次续费日"
  static readonly subscription_detail_trial_end: string = "试用截止"
  static readonly subscription_detail_auto_renew: string = "自动续费"
  static readonly subscription_detail_auto_renew_on: string = "开启"
  static readonly subscription_detail_auto_renew_off: string = "关闭"
  static readonly subscription_detail_status: string = "状态"
  static readonly subscription_detail_remind_days: string = "提前提醒"
  static readonly subscription_detail_remind_days_format: string = "提前 %d 天"
  static readonly subscription_detail_remind_days_zero: string = "仅当天提醒"
  static readonly subscription_detail_note: string = "备注"
  static readonly subscription_value_none: string = "—"
  static readonly subscription_action_edit: string = "编辑"
  static readonly subscription_action_pause: string = "暂停"
  static readonly subscription_action_resume: string = "恢复"
  static readonly subscription_action_cancel_sub: string = "取消订阅"
  static readonly subscription_action_finish: string = "结束使用"
  static readonly subscription_action_delete: string = "删除"
  static readonly subscription_confirm_cancel_title: string = "取消订阅"
  static readonly subscription_confirm_cancel_message: string = "取消后不再续费且不可恢复为进行中（数据保留），确定取消「%s」？"
  static readonly subscription_confirm_finish_title: string = "结束使用"
  static readonly subscription_confirm_finish_message: string = "标记为结束后该订阅只读、不可再打卡，确定结束「%s」？"
  static readonly subscription_confirm_delete_title: string = "删除订阅"
  static readonly subscription_confirm_delete_message: string = "删除将同时清空打卡记录与提醒，且不可恢复，确定删除「%s」？"
  static readonly subscription_confirm_delete_usage_title: string = "撤销打卡"
  static readonly subscription_confirm_delete_usage_message: string = "确定撤销 %s 的使用打卡？"
  static readonly subscription_terminal_readonly: string = "订阅已结束，信息仅供查看"
  static readonly subscription_toast_status_updated: string = "状态已更新"
  static readonly subscription_toast_delete_success: string = "已删除"
  static readonly subscription_detail_retry: string = "重新加载"

  // 使用统计
  static readonly subscription_stats_title: string = "使用统计（近 90 天）"
  static readonly subscription_stats_usage_format: string = "近 90 天使用 %d 次"
  static readonly subscription_stats_per_use_format: string = "单次折算 ¥%s"
  static readonly subscription_stats_per_use_none: string = "窗口内暂无使用，暂无单次成本"
  static readonly subscription_stats_monthly_format: string = "月度折算 ¥%s"
  static readonly subscription_stats_error: string = "统计加载失败"

  // 使用打卡
  static readonly subscription_usage_title: string = "使用打卡"
  static readonly subscription_usage_check_in: string = "今日打卡"
  static readonly subscription_usage_check_in_done: string = "已打卡"
  static readonly subscription_usage_empty: string = "还没有使用记录，用一次打一次卡"
  static readonly subscription_usage_error_load: string = "记录加载失败"
  static readonly subscription_usage_undo: string = "撤销"
  static readonly subscription_usage_today: string = "今天"
  static readonly subscription_toast_undo_success: string = "已撤销"

  // 取消路径
  static readonly subscription_cancel_path_title: string = "取消路径"
  static readonly subscription_cancel_path_url: string = "取消网址"
  static readonly subscription_cancel_path_steps: string = "取消步骤"
  static readonly subscription_cancel_path_empty: string = "未填写取消路径"

  // 提醒列表
  static readonly subscription_reminder_filter_unread: string = "未读"
  static readonly subscription_reminder_filter_all: string = "全部"
  static readonly subscription_reminder_empty_unread: string = "没有未读提醒"
  static readonly subscription_reminder_empty_all: string = "暂无提醒"
  static readonly subscription_reminder_error_load: string = "提醒加载失败"
  static readonly subscription_reminder_type_renew: string = "续费提醒"
  static readonly subscription_reminder_type_trial_end: string = "试用截止"
  static readonly subscription_reminder_date_format: string = "应提醒 %s"
  static readonly subscription_reminder_read_tag: string = "已读"
  /** 未读红点读屏描述（颜色不是唯一信息载体，§10；对齐安卓 subscription_reminder_unread_dot） */
  static readonly subscription_reminder_unread_dot: string = "未读提醒"

  // 日期弹窗
  static readonly subscription_date_confirm: string = "确定"
  static readonly subscription_date_cancel: string = "取消"
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
