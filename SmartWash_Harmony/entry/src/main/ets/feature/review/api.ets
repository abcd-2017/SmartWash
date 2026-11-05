// 记忆复习域契约 — VO / 请求体 / 常量 / 文案 / 七接口封装
//
// 依据安卓 feature/review/network/ReviewApi.kt、model/ReviewModels.kt、ReviewConstant.kt
// 与后端 ReviewWebController.java（@RequestMapping("/web/auth/review")，需 ROLE_USER）。
// 路径与参数逐一对齐：
//   POST /web/auth/review/materials              body{title, rawText?}      新建资料
//   GET  /web/auth/review/materials?page&size                               资料分页
//   POST /web/auth/review/materials/{id}/cards   body{question, answer}     建卡
//   GET  /web/auth/review/cards?materialId&page&size                       卡片分页
//   GET  /web/auth/review/queue                 今日队列（到期卡 + 新卡）
//   POST /web/auth/review/answer                body{cardId, rating, durationMs?}  FSRS 评分
//   GET  /web/auth/review/stats                 近 30 天统计
//
// 调度约定：FSRS-6 全部在后端 FsrsScheduler 纯函数内重算，客户端只传 (cardId, rating 1..4)，
// 不参与任何调度计算。契约 v1 未提供改卡/删卡/删资料端点（与安卓一致），故不做删除入口。
//
// 按 feature/README.md 约定：本文件只放声明层（VO interface + 常量 + 文案 + 接口封装），
// UI 组件与页面逻辑放域内各页面文件。

import { BaseResponse } from '../../network/BaseResponse'
import { httpUtil } from '../../network/HttpUtil'
import { jsonNum, jsonStr, mapArray } from '../../network/vo/JsonParse'

// ==================== 常量（对齐安卓 ReviewConstant） ====================

/** 资料列表/卡片列表分页每页条数（对齐后端 BaseSearchFrom 惯例） */
export const REVIEW_PAGE_SIZE: number = 20

/** 建资料表单：标题上限（对齐后端 AddMaterialFrom @Size(max=100)） */
export const REVIEW_TITLE_MAX: number = 100

/** 建资料表单：原文上限（对齐后端 AddMaterialFrom @Size(max=100_000)） */
export const REVIEW_RAW_TEXT_MAX: number = 100_000

/** 建卡：问题上限（对齐后端 AddCardFrom @Size(max=1000)） */
export const REVIEW_QUESTION_MAX: number = 1000

/** 建卡：答案上限（对齐后端 AddCardFrom @Size(max=2000)） */
export const REVIEW_ANSWER_MAX: number = 2000

/** 资料标题映射最多翻页数（队列/复习页补齐「所属资料」标题的防御上限） */
export const REVIEW_MATERIAL_TITLE_MAX_PAGES: number = 10

// ==================== 枚举（对齐安卓 ReviewModels） ====================

/**
 * 复习评分四档（Anki/py-fsrs 惯例）：
 * 1=Again（想不起来）2=Hard（很费劲）3=Good（正常想起）4=Easy（秒答）。
 */
export enum ReviewRating {
  AGAIN = 1,
  HARD = 2,
  GOOD = 3,
  EASY = 4
}

/** 按 code 取评分档；越界返回 null */
export function reviewRatingFromCode(code: number): ReviewRating | null {
  switch (code) {
    case 1:
      return ReviewRating.AGAIN
    case 2:
      return ReviewRating.HARD
    case 3:
      return ReviewRating.GOOD
    case 4:
      return ReviewRating.EASY
    default:
      return null
  }
}

/**
 * 卡片状态（py-fsrs State）：
 * 1=Learning（分钟级短步长）2=Review（天级间隔）3=Relearning（遗忘后重学，分钟级）。
 */
export enum ReviewCardState {
  LEARNING = 1,
  REVIEW = 2,
  RELEARNING = 3
}

/** 按 code 取卡片状态；越界回落 Learning（与安卓 fromCode 的兜底一致） */
export function reviewCardStateFromCode(code: number | null | undefined): ReviewCardState {
  if (code === 2) {
    return ReviewCardState.REVIEW
  }
  if (code === 3) {
    return ReviewCardState.RELEARNING
  }
  return ReviewCardState.LEARNING
}

// ==================== VO（按后端 vo 字段全量声明） ====================

/** 复习资料视图（对齐后端 MaterialVo） */
export interface MaterialVo {
  id?: number
  title?: string
  /** 原文摘要（列表截断），可空 */
  rawText?: string | null
  /** ISO LocalDateTime（yyyy-MM-dd'T'HH:mm:ss） */
  createdAt?: string | null
}

/** 复习卡视图（对齐后端 ReviewCardVo / 安卓 ReviewCardVo） */
export interface ReviewCardVo {
  id?: number
  materialId?: number | null
  question?: string
  answer?: string
  /** 1=Learning 2=Review 3=Relearning（py-fsrs State） */
  state?: number
  /** learning/relearning 步序号，Review 态为 null */
  step?: number | null
  /** 稳定度 S（天），新卡为 null */
  stability?: number | null
  /** 难度 D（1-10），新卡为 null */
  difficulty?: number | null
  /** 下次到期时刻（ISO LocalDateTime） */
  dueAt?: string | null
  /** NULL=从未复习的新卡 */
  lastReviewAt?: string | null
  reps?: number
  lapses?: number
  createdAt?: string | null
}

/** 今日队列视图（对齐后端 ReviewQueueVo） */
export interface ReviewQueueVo {
  /** 到期卡张数 */
  dueCount?: number
  /** 注入的新卡张数 */
  newCount?: number
  /** 合并后的队列（到期卡 + 新卡） */
  cards?: Array<ReviewCardVo>
}

/** 近 30 天单日复习数（对齐后端 ReviewStatsVo.DailyCount） */
export interface ReviewDailyCount {
  /** 统计日（ISO LocalDate，yyyy-MM-dd） */
  date?: string | null
  /** 当日复习次数 */
  count?: number
}

/** 单评分次数分布（对齐后端 ReviewStatsVo.RatingShare） */
export interface ReviewRatingShare {
  /** 1=Again 2=Hard 3=Good 4=Easy */
  rating?: number
  /** 该评分次数 */
  count?: number
}

/** 复习统计视图（对齐后端 ReviewStatsVo） */
export interface ReviewStatsVo {
  /** 近 30 天每日复习数（缺日补零） */
  daily?: Array<ReviewDailyCount>
  /** 各评分次数分布（恒 4 项） */
  ratingShare?: Array<ReviewRatingShare>
}

/**
 * 泛型分页信封（后端 MyBatis-Plus IPage 序列化：records/total/size/current）。
 * 不复用 network/vo/PageData（其 records 被硬编码成 RechargeRecordVo）。
 */
export interface ReviewPageData<T> {
  records: Array<T>
  total: number
  size: number
  current: number
}

// ==================== 请求体（对齐后端 from） ====================

/** 新建资料请求体（对齐 AddMaterialFrom；title 必填 ≤100，rawText 可空 ≤100k） */
export interface AddMaterialForm {
  title: string
  rawText?: string
}

/** 建卡请求体（对齐 AddCardFrom；question/answer 必填） */
export interface AddCardForm {
  question: string
  answer: string
}

/** 复习评分请求体（对齐 AnswerFrom；rating 1..4，durationMs 可空） */
export interface AnswerForm {
  cardId: number
  rating: number
  durationMs?: number
}

// ==================== JSON → VO 显式映射 ====================
// 背景见 network/vo/JsonParse.ets：Jackson 可能省略字段，interface 运行时不填充，
// 消费点一律经 jsonStr/jsonNum/mapArray 兜底，避免 undefined 透传。

/** JSON → MaterialVo；缺失字段回落空值/0 */
export function materialVoFromJSON(raw: Object): MaterialVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    title: jsonStr(obj['title']) ?? '',
    rawText: jsonStr(obj['rawText']),
    createdAt: jsonStr(obj['createdAt'])
  }
}

/** JSON → ReviewCardVo；缺失字段回落空值/0 */
export function reviewCardVoFromJSON(raw: Object): ReviewCardVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    materialId: jsonNum(obj['materialId']),
    question: jsonStr(obj['question']) ?? '',
    answer: jsonStr(obj['answer']) ?? '',
    state: jsonNum(obj['state']) ?? ReviewCardState.LEARNING,
    step: jsonNum(obj['step']),
    stability: jsonNum(obj['stability']),
    difficulty: jsonNum(obj['difficulty']),
    dueAt: jsonStr(obj['dueAt']),
    lastReviewAt: jsonStr(obj['lastReviewAt']),
    reps: jsonNum(obj['reps']) ?? 0,
    lapses: jsonNum(obj['lapses']) ?? 0,
    createdAt: jsonStr(obj['createdAt'])
  }
}

/** JSON → ReviewQueueVo；cards 缺失兜底空数组 */
export function reviewQueueVoFromJSON(raw: Object): ReviewQueueVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    dueCount: jsonNum(obj['dueCount']) ?? 0,
    newCount: jsonNum(obj['newCount']) ?? 0,
    cards: mapArray(obj['cards'], (item: Object): ReviewCardVo => reviewCardVoFromJSON(item))
  }
}

/** JSON → ReviewStatsVo；daily/ratingShare 缺失兜底空数组 */
export function reviewStatsVoFromJSON(raw: Object): ReviewStatsVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    daily: mapArray(obj['daily'], (item: Object): ReviewDailyCount => {
      const daily: Record<string, Object> = item as Record<string, Object>
      return { date: jsonStr(daily['date']), count: jsonNum(daily['count']) ?? 0 }
    }),
    ratingShare: mapArray(obj['ratingShare'], (item: Object): ReviewRatingShare => {
      const share: Record<string, Object> = item as Record<string, Object>
      return { rating: jsonNum(share['rating']) ?? 0, count: jsonNum(share['count']) ?? 0 }
    })
  }
}

/** JSON → 泛型分页信封；raw 缺失兜底空页 */
export function reviewPageDataFromJSON<T>(raw: Object | null | undefined,
  mapper: (item: Object) => T): ReviewPageData<T> {
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

// ==================== 文案表（逐一对齐安卓 review_* 字符串） ====================

export class ReviewText {
  // 页面标题
  static readonly review_queue_title: string = "今日复习"
  static readonly review_study_title: string = "复习"
  static readonly review_material_title: string = "复习资料"

  // 今日队列页
  static readonly review_queue_count_format: string = "今日 %d 张"
  static readonly review_queue_breakdown_format: string = "到期 %d 张 · 新卡 %d 张"
  static readonly review_queue_action_start: string = "开始复习"
  static readonly review_queue_empty_message: string = "今日没有需要复习的卡片，先去资料页建卡吧"
  static readonly review_queue_empty_action: string = "去资料页建卡"
  static readonly review_card_new: string = "新卡"
  static readonly review_due_badge_minute: string = "分钟级"
  static readonly review_due_badge_daily: string = "天级"
  static readonly review_due_now: string = "已到期"
  static readonly review_due_in_minutes: string = "%d 分钟后到期"
  static readonly review_due_in_hours: string = "%d 小时后到期"
  static readonly review_due_in_days: string = "%d 天后到期"
  static readonly review_material_chip_none: string = "未归资料"

  // 卡片状态（py-fsrs State）
  static readonly review_state_learning: string = "学习中"
  static readonly review_state_review: string = "复习中"
  static readonly review_state_relearning: string = "重学中"

  // 复习页
  static readonly review_study_progress_format: string = "第 %d / %d 张"
  static readonly review_study_question_hint: string = "点击卡片查看答案"
  static readonly review_study_answer_label: string = "答案"
  static readonly review_rating_again: string = "Again"
  static readonly review_rating_hard: string = "Hard"
  static readonly review_rating_good: string = "Good"
  static readonly review_rating_easy: string = "Easy"
  static readonly review_rating_again_semantic: string = "想不起来"
  static readonly review_rating_hard_semantic: string = "有点费劲"
  static readonly review_rating_good_semantic: string = "正常想起"
  static readonly review_rating_easy_semantic: string = "轻松秒答"
  static readonly review_study_completed_message: string = "今日复习完成，休息一下吧"
  static readonly review_action_back: string = "返 回"
  static readonly review_error_answer: string = "评分提交失败，请重试"

  // 资料页
  static readonly review_material_empty: string = "还没有复习资料，粘贴一段文本开始建卡吧"
  static readonly review_material_action_new: string = "新建"
  static readonly review_material_form_title_label: string = "资料标题"
  static readonly review_material_form_title_hint: string = "例如：操作系统期中考点"
  static readonly review_material_form_raw_label: string = "原文粘贴（可选）"
  static readonly review_material_form_raw_hint: string = "粘贴教材或笔记原文，便于回顾上下文"
  static readonly review_material_action_create: string = "创建资料"
  static readonly review_toast_material_created: string = "资料已创建"
  static readonly review_material_expand: string = "展开"
  static readonly review_material_collapse: string = "收起"
  static readonly review_material_cards_header_format: string = "问答卡（%d）"
  static readonly review_material_add_card: string = "添加卡片"
  static readonly review_material_cards_empty: string = "还没有卡片，添加第一张吧"
  static readonly review_material_load_more: string = "加载更多"
  static readonly review_error_load_more: string = "加载更多失败，点击重试"
  static readonly review_card_dialog_title: string = "添加问答卡"
  static readonly review_card_dialog_question_label: string = "问题"
  static readonly review_card_dialog_question_hint: string = "例如：进程与线程的区别？"
  static readonly review_card_dialog_answer_label: string = "答案"
  static readonly review_card_dialog_answer_hint: string = "例如：进程是资源分配单位，线程是调度单位"
  static readonly review_dialog_action_confirm: string = "确认"
  static readonly review_dialog_action_cancel: string = "取消"
  static readonly review_toast_card_created: string = "卡片已创建"

  // 校验与通用错误
  static readonly review_action_retry: string = "重试"
  static readonly review_error_load: string = "加载失败，请重试"
  static readonly review_error_title_required: string = "请输入资料标题"
  static readonly review_error_title_too_long: string = "资料标题最长 100 字"
  static readonly review_error_raw_too_long: string = "资料原文过长"
  static readonly review_error_question_required: string = "请输入问题"
  static readonly review_error_answer_required: string = "请输入答案"
  static readonly review_error_question_too_long: string = "问题最长 1000 字"
  static readonly review_error_answer_too_long: string = "答案最长 2000 字"
}

// ==================== 七接口封装 ====================

/**
 * 新建资料。title 必填（≤100 字），rawText 可空（≤100k）。
 * 成功后返回新资料 id。
 */
export async function createMaterial(form: AddMaterialForm): Promise<BaseResponse<number>> {
  // ArkTS 禁止裸对象字面量（arkts-no-untyped-obj-literals），按索引赋值构造请求体。
  // rawText 空白时「不传该键」——后端 AddMaterialFrom 字段缺省即 null，与显式传 null 等价。
  const body: Record<string, Object> = {}
  body['title'] = form.title
  if (form.rawText !== undefined && form.rawText.length > 0) {
    body['rawText'] = form.rawText
  }
  return httpUtil.post<number>('/web/auth/review/materials', body)
}

/** 资料分页（按创建时间倒序）；page 从 1 起 */
export async function pageMaterials(page: number,
  size: number): Promise<BaseResponse<ReviewPageData<MaterialVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  const res = await httpUtil.get<Object>('/web/auth/review/materials', params)
  return {
    data: res.data === null ? null : reviewPageDataFromJSON<MaterialVo>(res.data, materialVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/** 建卡（手动问答卡）：初始 state=Learning, step=0, due=now；返回新卡片 id */
export async function addCard(materialId: number, form: AddCardForm): Promise<BaseResponse<number>> {
  const body: Record<string, Object> = {}
  body['question'] = form.question
  body['answer'] = form.answer
  return httpUtil.post<number>(`/web/auth/review/materials/${materialId}/cards`, body)
}

/** 卡片分页（按资料筛选）；page 从 1 起 */
export async function pageCards(materialId: number, page: number,
  size: number): Promise<BaseResponse<ReviewPageData<ReviewCardVo>>> {
  const params: Record<string, Object> = {}
  params['materialId'] = materialId
  params['page'] = page
  params['size'] = size
  const res = await httpUtil.get<Object>('/web/auth/review/cards', params)
  return {
    data: res.data === null ? null : reviewPageDataFromJSON<ReviewCardVo>(res.data, reviewCardVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/** 今日队列：到期卡在前（state,due 升序，上限 50）+ 新卡注入队尾（每次最多 20 张） */
export async function getReviewQueue(): Promise<BaseResponse<ReviewQueueVo>> {
  const res = await httpUtil.get<Object>('/web/auth/review/queue')
  return {
    data: res.data === null ? null : reviewQueueVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/**
 * 复习评分：rating 1..4（1=Again 2=Hard 3=Good 4=Easy），durationMs 可空；
 * 返回更新后的卡片。FSRS 调度全部在后端完成。
 */
export async function answerCard(form: AnswerForm): Promise<BaseResponse<ReviewCardVo>> {
  const body: Record<string, Object> = {}
  body['cardId'] = form.cardId
  body['rating'] = form.rating
  if (form.durationMs !== undefined) {
    body['durationMs'] = form.durationMs
  }
  const res = await httpUtil.post<Object>('/web/auth/review/answer', body)
  return {
    data: res.data === null ? null : reviewCardVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}

/** 复习统计：近 30 天每日复习数（缺日补零）+ 各评分次数分布 */
export async function reviewStats(): Promise<BaseResponse<ReviewStatsVo>> {
  const res = await httpUtil.get<Object>('/web/auth/review/stats')
  return {
    data: res.data === null ? null : reviewStatsVoFromJSON(res.data),
    code: res.code,
    message: res.message
  }
}
