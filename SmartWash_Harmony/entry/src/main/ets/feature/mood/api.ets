// 情绪气象站域契约 — VO / 表单 / 常量 / 文案 / 三接口封装
//
// 依据安卓 feature/mood/network/MoodApi.kt 与后端 MoodController.java
// （@RequestMapping("/web/auth/mood")，需 ROLE_USER）。路径与参数逐一对齐：
//   PUT  /web/auth/mood/today    body{level:1..5, color, word?, note?}，同日覆盖 upsert
//   GET  /web/auth/mood/calendar?ym=yyyy-MM  当月有记录的日期（升序，空月空数组）
//   GET  /web/auth/mood/stats?ym=yyyy-MM     各等级计数 / 记录天数 / 月均值
//
// 按 feature/README.md 约定：本文件只放声明层（VO interface + 常量 + 文案 + 接口封装），
// UI 组件与页面逻辑放域内各页面文件。

import { BaseResponse } from '../../network/BaseResponse'
import { httpUtil } from '../../network/HttpUtil'
import { resString } from '../../utils/ResourceString'

/** 情绪等级下界（1=暴雨），与后端 CHECK 约束一致 */
export const MOOD_LEVEL_MIN: number = 1

/** 情绪等级上界（5=大晴），与后端 CHECK 约束一致 */
export const MOOD_LEVEL_MAX: number = 5

/** 词最大长度（后端 word ≤20 字符，空白按 null 存） */
export const MOOD_WORD_MAX_LENGTH: number = 20

/** 一句话备注最大长度（后端 note ≤200 字符，空白按 null 存） */
export const MOOD_NOTE_MAX_LENGTH: number = 200

/**
 * 今日情绪记录视图对象（PUT /web/auth/mood/today 的返回体）。
 * 字段按后端 MoodRecordVo 全量声明；Jackson 可能省略 null 字段，故可空字段一律可选。
 */
export interface MoodRecordVo {
  /** 记录日期，yyyy-MM-dd */
  date?: string
  /** 1暴雨 2阴 3多云 4晴 5大晴 */
  level?: number
  /** 快照当日颜色 */
  color?: string
  /** 一个词，可空 */
  word?: string | null
  /** 一句话备注，可空 */
  note?: string | null
  updatedAt?: string | null
}

/**
 * 月历单日视图对象（GET /web/auth/mood/calendar 响应项）。
 * 契约不含 note（当日备注不回看），仅返回有记录的日期。
 */
export interface MoodCalendarDayVo {
  /** 记录日期，yyyy-MM-dd */
  date?: string
  /** 1暴雨 2阴 3多云 4晴 5大晴 */
  level?: number
  /** 快照当日颜色 */
  color?: string
  /** 一个词，可空 */
  word?: string | null
}

/** 单个情绪等级的月度计数（GROUP BY mood_level） */
export interface MoodLevelCountVo {
  /** 1暴雨 2阴 3多云 4晴 5大晴 */
  level?: number
  /** 该等级当月的记录天数 */
  recordCount?: number
}

/**
 * 月度趋势统计视图对象（GET /web/auth/mood/stats）。
 * distribution 只含出现过的等级（缺的等级端上补 0）。
 */
export interface MoodStatsVo {
  /** 统计月份，yyyy-MM */
  ym?: string
  /** 该月有记录的天数（一天一条，即记录条数） */
  recordedDays?: number
  /** 月均值（等级均值，保留2位；无记录时为 null） */
  averageLevel?: number | null
  /** 各等级计数分布（按等级升序；空月为空数组） */
  distribution?: Array<MoodLevelCountVo>
}

/** 记录/覆盖今日情绪请求体（对齐后端 SaveMoodFrom；word/note 空白按 null 存） */
export interface SaveMoodForm {
  /** 1..5，越界后端返回 code=201 */
  level: number
  /** 提交时刻的颜色快照（#RRGGBB） */
  color: string
  word?: string
  note?: string
}

/**
 * 情绪气象站文案表 — 键名逐一对齐安卓 feature/mood/src/main/res/values/strings.xml。
 * 便于跨端文案核对。
 */
export class MoodText {
  // 页面标题
  static get mood_title(): string { return resString('mood_title') }
  static get mood_calendar_title(): string { return resString('mood_calendar_title') }
  static get mood_trend_title(): string { return resString('mood_trend_title') }
  static get mood_action_calendar(): string { return resString('mood_action_calendar') }
  static get mood_action_trend(): string { return resString('mood_action_trend') }

  // 五个心情（1暴雨 2阴 3多云 4晴 5大晴）
  static get mood_level_rainstorm(): string { return resString('mood_level_rainstorm') }
  static get mood_level_overcast(): string { return resString('mood_level_overcast') }
  static get mood_level_cloudy(): string { return resString('mood_level_cloudy') }
  static get mood_level_sunny(): string { return resString('mood_level_sunny') }
  static get mood_level_shining(): string { return resString('mood_level_shining') }

  // 今日记录页
  static get mood_today_question(): string { return resString('mood_today_question') }
  static get mood_today_hint(): string { return resString('mood_today_hint') }
  static get mood_today_recorded(): string { return resString('mood_today_recorded') }
  static get mood_word_section_title(): string { return resString('mood_word_section_title') }
  static get mood_note_section_title(): string { return resString('mood_note_section_title') }
  static get mood_note_hint(): string { return resString('mood_note_hint') }
  static get mood_error_load(): string { return resString('mood_error_load') }
  static get mood_error_save(): string { return resString('mood_error_save') }
  static get mood_action_retry(): string { return resString('mood_action_retry') }

  // 词 chips（天气词，可选一个）
  static get mood_word_tired(): string { return resString('mood_word_tired') }
  static get mood_word_smooth(): string { return resString('mood_word_smooth') }
  static get mood_word_quiet(): string { return resString('mood_word_quiet') }
  static get mood_word_restless(): string { return resString('mood_word_restless') }
  static get mood_word_burning(): string { return resString('mood_word_burning') }

  // 月历热图页
  static get mood_prev_month(): string { return resString('mood_prev_month') }
  static get mood_next_month(): string { return resString('mood_next_month') }
  static get mood_week_mon(): string { return resString('mood_week_mon') }
  static get mood_week_tue(): string { return resString('mood_week_tue') }
  static get mood_week_wed(): string { return resString('mood_week_wed') }
  static get mood_week_thu(): string { return resString('mood_week_thu') }
  static get mood_week_fri(): string { return resString('mood_week_fri') }
  static get mood_week_sat(): string { return resString('mood_week_sat') }
  static get mood_week_sun(): string { return resString('mood_week_sun') }
  static get mood_calendar_day_empty_hint(): string { return resString('mood_calendar_day_empty_hint') }
  static get mood_day_dialog_word_empty(): string { return resString('mood_day_dialog_word_empty') }
  static get mood_day_dialog_close(): string { return resString('lab_soundscape_timer_off') }

  // 趋势页
  static get mood_trend_stats_days(): string { return resString('mood_trend_stats_days') }
  static get mood_trend_stats_average(): string { return resString('mood_trend_stats_average') }
  static get mood_trend_distribution_title(): string { return resString('mood_trend_distribution_title') }
  static readonly mood_trend_value_none: string = "—"
  static get mood_trend_days_unit(): string { return resString('mood_trend_days_unit') }
  static get mood_trend_empty_message(): string { return resString('mood_trend_empty_message') }
  static get mood_trend_empty_action(): string { return resString('mood_trend_empty_action') }
  static get mood_trend_error_load(): string { return resString('mood_trend_error_load') }
}

/** 本月记录天数展示（对齐安卓 mood_calendar_recorded_days_format「本月记录 %1$d 天」） */
export function moodRecordedDaysFormat(days: number): string {
  return `本月记录 ${days} 天`
}

// ==================== 三接口封装 ====================

/**
 * 记录/覆盖今日情绪。upsert 语义（同一天重复提交即覆盖，服务端唯一索引原子裁决）。
 * color 取词典基准 hex（契约：快照决定历史色，端上改词典不改历史）。
 */
export async function saveMoodToday(form: SaveMoodForm): Promise<BaseResponse<MoodRecordVo>> {
  // ArkTS 禁止裸对象字面量（arkts-no-untyped-obj-literals），按索引赋值构造请求体。
  // word/note 空白时「不传该键」——后端 SaveMoodFrom 字段缺省即 null，与显式传 null 等价。
  const body: Record<string, Object> = {}
  body['level'] = form.level
  body['color'] = form.color
  if (form.word !== undefined && form.word.length > 0) {
    body['word'] = form.word
  }
  if (form.note !== undefined && form.note.length > 0) {
    body['note'] = form.note
  }
  return httpUtil.put<MoodRecordVo>('/web/auth/mood/today', body)
}

/**
 * 月历热图数据。ym 格式 yyyy-MM；只返回当月有记录的日期（升序），空月返回空数组。
 * 空数组是合法成功，data 缺失（null）才按失败处理。
 */
export async function moodMonthCalendar(ym: string): Promise<BaseResponse<Array<MoodCalendarDayVo>>> {
  const params: Record<string, Object> = {}
  params['ym'] = ym
  return httpUtil.get<Array<MoodCalendarDayVo>>('/web/auth/mood/calendar', params)
}

/**
 * 月度趋势统计。ym 格式 yyyy-MM；空月 recordedDays=0、averageLevel=null、distribution=[]。
 */
export async function moodMonthStats(ym: string): Promise<BaseResponse<MoodStatsVo>> {
  const params: Record<string, Object> = {}
  params['ym'] = ym
  return httpUtil.get<MoodStatsVo>('/web/auth/mood/stats', params)
}

/**
 * 契约核对（路径/参数与安卓 MoodApi.kt 一致；冲突时以后端实现为准）：
 * | 接口 | 安卓 MoodApi.kt | 后端 MoodController | 本域 |
 * |---|---|---|---|
 * | PUT  | web/auth/mood/today body{level,color,word?,note?} | @PutMapping("/today") + SaveMoodFrom | ✓ |
 * | GET  | web/auth/mood/calendar?ym | @GetMapping("/calendar") @RequestParam("ym") | ✓ |
 * | GET  | web/auth/mood/stats?ym | @GetMapping("/stats") @RequestParam("ym") | ✓ |
 * 注：安卓 Retrofit 注解为 @PUT("web/auth/mood/today")（无前导斜杠），
 * 复用 baseUrl 后等价于本域 '/web/auth/mood/today'。
 */
