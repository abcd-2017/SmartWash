// 速记手账域契约 — VO / 常量 / 文案 / 五接口封装
//
// 依据安卓 feature/memo/network/MemoApi.kt 与后端 MemoController.java
// （@RequestMapping("/web/auth/memo")，需 ROLE_USER）。路径与参数逐一对齐：
//   POST /web/auth/memo                body{content(≤2000), tags[]} → Result<Long>
//   GET  /web/auth/memo/list?page&size&keyword?&tag? → Result<Page<MemoVo>>（置顶优先由后端排序）
//   PUT  /web/auth/memo/{id}           body{content, tags, pinned}  → Result<String>
//   DELETE /web/auth/memo/{id}                                      → Result<String>（逻辑删除）
//   GET  /web/auth/memo/tags                                        → Result<List<{tag, count}>>
//
// 更新接口端上按安卓先例做「全量提交」（content/tags/pinned 三字段必传），
// 后端为 patch 语义，全量提交天然兼容；置顶切换也复用本端点（内容与标签原样透传）。
//
// 按 feature/README.md 约定：本文件只放声明层（VO interface + 常量 + 文案 + 接口封装），
// UI 组件与页面逻辑放域内各页面文件。

import { BaseResponse } from '../../network/BaseResponse'
import { httpUtil } from '../../network/HttpUtil'
import { jsonBool, jsonNum, jsonStr } from '../../network/vo/JsonParse'

// ==================== 常量（对齐安卓 MemoConstant） ====================

/** 记录列表分页每页条数（对齐安卓 MemoConstant.PAGE_SIZE） */
export const MEMO_PAGE_SIZE: number = 10

/** 单条记录内容上限（后端契约：content ≤ 2000，端上输入即截断） */
export const MEMO_CONTENT_MAX_LENGTH: number = 2000

/** 标签聚合接口最多展示的「常用标签」数量（仅展示层裁剪，非后端契约） */
export const MEMO_FREQUENT_TAGS_LIMIT: number = 8

/** 搜索防抖间隔（毫秒，对齐安卓 common PagingUtils.pagingFlow 的 debounce 300） */
export const MEMO_SEARCH_DEBOUNCE_MS: number = 300

// ==================== VO（按后端 vo 字段声明） ====================

/**
 * 泛型分页信封（后端 MyBatis-Plus IPage 序列化：records/total/size/current）。
 * 与 review 域同构：不复用 network/vo/PageData（其 records 被硬编码成 RechargeRecordVo）。
 */
export interface MemoPageData<T> {
  records: Array<T>
  total: number
  size: number
  current: number
}

/**
 * 记录条目（GET /list 分页 records 项）。
 * ⚠️ 跨端记录：任务契约定稿字段为 createTime/updateTime，但后端 MemoVo.java 实际
 * 序列化名为 createdAt/updatedAt（LocalDateTime）——fromJSON 以契约键优先、
 * 实现键兜底，两端任一命名都可用；后端统一命名后可删兜底分支。
 */
export interface MemoVo {
  id?: number
  content?: string
  /** 标签数组（存储层逗号分隔拆分而来，无标签为空数组） */
  tags?: Array<string>
  pinned?: boolean
  createTime?: string | null
  updateTime?: string | null
}

/** 标签聚合条目（GET /tags 响应项，count 倒序；同频按标签字典序） */
export interface MemoTagCountVo {
  tag?: string
  count?: number
}

// ==================== JSON → VO 显式映射 ====================
// 背景见 network/vo/JsonParse.ets：Jackson 可能省略字段，interface 运行时不填充，
// 消费点一律经取值函数兜底，避免 undefined 透传。

/** JSON → string 数组（tags 元素是字符串而非对象，mapArray 会跳过非 object 项，须单独提取） */
function memoStringArray(raw: Object | null | undefined): Array<string> {
  if (raw === null || raw === undefined || !Array.isArray(raw)) {
    return []
  }
  const arr: Array<Object | null> = raw as Array<Object | null>
  const result: Array<string> = []
  for (let i = 0; i < arr.length; i++) {
    const item: string | null = jsonStr(arr[i])
    if (item !== null) {
      result.push(item)
    }
  }
  return result
}

/** JSON → MemoVo；缺失字段回落空值（契约键 createTime/updateTime 优先，实现键兜底） */
export function memoVoFromJSON(raw: Object): MemoVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    content: jsonStr(obj['content']) ?? '',
    tags: memoStringArray(obj['tags']),
    pinned: jsonBool(obj['pinned']) ?? false,
    createTime: jsonStr(obj['createTime']) ?? jsonStr(obj['createdAt']),
    updateTime: jsonStr(obj['updateTime']) ?? jsonStr(obj['updatedAt'])
  }
}

/** JSON → MemoTagCountVo；缺失字段回落空值/0 */
export function memoTagCountVoFromJSON(raw: Object): MemoTagCountVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    tag: jsonStr(obj['tag']) ?? '',
    count: jsonNum(obj['count']) ?? 0
  }
}

/** JSON → 泛型分页信封；raw 缺失兜底空页（records 逐项经 mapper 显式映射） */
export function memoPageDataFromJSON<T>(raw: Object | null | undefined,
  mapper: (item: Object) => T): MemoPageData<T> {
  if (raw === null || raw === undefined) {
    return { records: [], total: 0, size: 0, current: 0 }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const recordsRaw = obj['records']
  const records: Array<T> = []
  if (recordsRaw !== null && recordsRaw !== undefined && Array.isArray(recordsRaw)) {
    const arr: Array<Object | null> = recordsRaw as Array<Object | null>
    for (let i = 0; i < arr.length; i++) {
      const item: Object | null = arr[i]
      if (item !== null && typeof item === 'object') {
        records.push(mapper(item))
      }
    }
  }
  return {
    records: records,
    total: jsonNum(obj['total']) ?? 0,
    size: jsonNum(obj['size']) ?? 0,
    current: jsonNum(obj['current']) ?? 0
  }
}

// ==================== 文案表（逐一对齐安卓 feature/memo strings.xml 的 memo_* 键） ====================

export class MemoText {
  // 页面
  static readonly memo_title: string = "速记手账"
  static readonly memo_error_load: string = "记录加载失败"
  static readonly memo_action_retry: string = "重试"
  static readonly memo_empty_message: string = "还没有记录，写下第一条吧"
  static readonly memo_empty_filtered_message: string = "没有匹配的记录"

  // 随手记输入区
  static readonly memo_composer_hint: string = "记点想法…"
  static readonly memo_composer_editing: string = "编辑中"
  static readonly memo_action_cancel_edit: string = "取消编辑"
  static readonly memo_action_save: string = "保存"
  static readonly memo_action_update: string = "更新"
  static readonly memo_composer_selected_hint: string = "已选标签，点击移除"
  static readonly memo_composer_tag_hint: string = "添加标签"
  static readonly memo_composer_tag_add: string = "添加"
  static readonly memo_composer_frequent_tags: string = "常用标签"
  static readonly memo_error_empty: string = "内容不能为空"
  static readonly memo_error_save: string = "保存失败，请重试"
  static readonly memo_toast_saved: string = "已保存"

  // 搜索与标签筛选
  static readonly memo_search_hint: string = "搜索记录"
  static readonly memo_filter_all: string = "全部"

  // 记录卡片
  static readonly memo_card_pinned: string = "置顶"
  static readonly memo_card_edited: string = "已编辑"
  static readonly memo_action_pin: string = "置顶"
  static readonly memo_action_unpin: string = "取消置顶"
  static readonly memo_action_edit: string = "编辑"
  static readonly memo_action_delete: string = "删除"
  static readonly memo_toast_pinned: string = "已置顶"
  static readonly memo_toast_unpinned: string = "已取消置顶"
  static readonly memo_toast_deleted: string = "已删除"
  static readonly memo_error_action: string = "操作失败，请重试"
}

/** 字数计数展示（对齐安卓 memo_counter_format「%1$d/%2$d」） */
export function memoCounterFormat(current: number, max: number): string {
  return `${current}/${max}`
}

/** 标签计数展示（对齐安卓 memo_tag_with_count_format「%1$s · %2$d」） */
export function memoTagWithCountFormat(tag: string, count: number): string {
  return `${tag} · ${count}`
}

/**
 * 记录时间展示格式化（纯函数，对齐安卓 MemoTimeFormat.display）。
 * 解析依次尝试 ISO（yyyy-MM-ddTHH:mm[:ss[.SSS]]）与「yyyy-MM-dd HH:mm[:ss]」，失败回落原文。
 * 展示规则：本年 → 「M月d日 HH:mm」；跨年 → 「yyyy年M月d日」。
 */
export function memoTimeDisplay(raw: string | null | undefined): string {
  const text = raw ?? ''
  if (text.trim().length === 0) {
    return ''
  }
  const time = parseMemoTime(text)
  if (time === null) {
    return text
  }
  const now = new Date()
  if (time.getFullYear() === now.getFullYear()) {
    return `${time.getMonth() + 1}月${time.getDate()}日 ${pad2(time.getHours())}:${pad2(time.getMinutes())}`
  }
  return `${time.getFullYear()}年${time.getMonth() + 1}月${time.getDate()}日`
}

/** 两位补零（时间展示用） */
function pad2(value: number): string {
  return value < 10 ? `0${value}` : `${value}`
}

/**
 * 后端时间串解析（避免依赖 Date 字符串解析的引擎差异，用正则取字段本地构造）。
 * 覆盖 ISO（含可选秒/毫秒）与空格分隔两种形态；不匹配返回 null。
 */
function parseMemoTime(text: string): Date | null {
  const matched = text.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?/)
  if (matched === null) {
    return null
  }
  const year = Number(matched[1])
  const month = Number(matched[2])
  const day = Number(matched[3])
  const hour = Number(matched[4])
  const minute = Number(matched[5])
  const second = matched[6] === undefined ? 0 : Number(matched[6])
  const time = new Date(year, month - 1, day, hour, minute, second)
  return Number.isNaN(time.getTime()) ? null : time
}

// ==================== 五接口封装 ====================

/**
 * 新建一条随手记，返回记录 id。content 必填（≤2000，端上输入即截断）；
 * tags 空数组表示无标签（后端归一为 NULL 落库）。
 */
export async function createMemo(content: string, tags: Array<string>): Promise<BaseResponse<number>> {
  // ArkTS 禁止裸对象字面量（arkts-no-untyped-obj-literals），按索引赋值构造请求体
  const body: Record<string, Object> = {}
  body['content'] = content
  body['tags'] = tags
  return httpUtil.post<number>('/web/auth/memo', body)
}

/**
 * 记录列表（分页）：keyword 对 content 模糊匹配，tag 精确过滤；
 * 置顶优先 + 更新时间倒序由后端排序，端上不重排。keyword/tag 空白时不传该键。
 */
export async function pageMemos(page: number, size: number, keyword: string,
  tag: string): Promise<BaseResponse<MemoPageData<MemoVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  if (keyword.trim().length > 0) {
    params['keyword'] = keyword.trim()
  }
  if (tag.trim().length > 0) {
    params['tag'] = tag.trim()
  }
  const res = await httpUtil.get<Object>('/web/auth/memo/list', params)
  // data 缺失兜底空页；records 逐项经 memoVoFromJSON 显式映射
  const pageData = memoPageDataFromJSON<MemoVo>(res.data, memoVoFromJSON)
  return { data: pageData, code: res.code, message: res.message }
}

/**
 * 更新记录（content/tags/pinned 全量提交，对齐安卓先例；置顶切换复用本端点，
 * 内容与标签原样透传）。返回服务端提示文案（如「更新成功」）。
 */
export async function updateMemo(id: number, content: string, tags: Array<string>,
  pinned: boolean): Promise<BaseResponse<string>> {
  const body: Record<string, Object> = {}
  body['content'] = content
  body['tags'] = tags
  body['pinned'] = pinned
  return httpUtil.put<string>(`/web/auth/memo/${id}`, body)
}

/** 删除记录（后端逻辑删除；端上成功即从列表移除） */
export async function deleteMemo(id: number): Promise<BaseResponse<string>> {
  return httpUtil.delete<string>(`/web/auth/memo/${id}`)
}

/**
 * 标签聚合（tag + count，count 倒序），驱动筛选行与输入区「常用标签」。
 * 空数组是合法成功（用户还没有任何标签）。
 */
export async function listMemoTags(): Promise<BaseResponse<Array<MemoTagCountVo>>> {
  const res = await httpUtil.get<Object>('/web/auth/memo/tags')
  const tags: Array<MemoTagCountVo> = []
  if (res.data !== null && Array.isArray(res.data)) {
    const arr: Array<Object | null> = res.data as Array<Object | null>
    for (let i = 0; i < arr.length; i++) {
      const item: Object | null = arr[i]
      if (item !== null && typeof item === 'object') {
        tags.push(memoTagCountVoFromJSON(item))
      }
    }
  }
  return { data: tags, code: res.code, message: res.message }
}

/**
 * 契约核对（路径/参数与安卓 MemoApi.kt 一致；冲突时以后端实现为准）：
 * | 接口 | 安卓 MemoApi.kt | 后端 MemoController | 本域 |
 * |---|---|---|---|
 * | POST   | web/auth/memo body{content,tags[]} | @PostMapping + AddMemoFrom | ✓ |
 * | GET    | web/auth/memo/list?page&size&keyword?&tag? | @GetMapping("/list") + SearchMemoFrom | ✓ |
 * | PUT    | web/auth/memo/{id} body{content,tags,pinned} | @PutMapping("/{id}") + UpdateMemoFrom | ✓ |
 * | DELETE | web/auth/memo/{id} | @DeleteMapping("/{id}") | ✓ |
 * | GET    | web/auth/memo/tags | @GetMapping("/tags") | ✓ |
 * 注：安卓 Retrofit 注解为相对路径（无前导斜杠），复用 baseUrl 后等价于本域 '/web/auth/memo/**'。
 */
