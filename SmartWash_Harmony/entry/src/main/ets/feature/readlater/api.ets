// 稍后读（AI 摘要）域契约 — VO / 表单 / 常量 / 文案 / 格式化 / 六接口封装
//
// 依据安卓 feature/readlater/network/ReadLaterApi.kt 与后端 ReadLaterController.java
// （@RequestMapping("/web/auth/readlater")，需 ROLE_USER）。路径与参数逐一对齐：
//   POST   /web/auth/readlater              body{url(https), tags[]} → data=Long（标题由后端抓取）
//   GET    /web/auth/readlater/list         ?page&size&status?&tag?&keyword? → data=IPage<ReadLaterVo>
//                                           （status asc + created_at desc：未读优先、时间倒序）
//   POST   /web/auth/readlater/{id}/digest  → data=String（AI 摘要，后端抓正文调 LLM，可能较慢）
//   PUT    /web/auth/readlater/{id}         body{tags?, status?} → data=String（patch 语义）
//   DELETE /web/auth/readlater/{id}         → data=String
//   POST   /web/auth/readlater/{id}/shorten → data=String（完整短链地址，端上复制到剪贴板）
//
// patch 语义（与 memo 域「全量提交」的差异点）：表单字段 undefined = 不构造该键 = 后端不更新；
// tags 传数组（含空数组 = 清空标签）才整体替换，status 传 0/1 才切换。
//
// 按 feature/README.md 约定：本文件只放声明层（VO interface + 常量 + 文案 + 接口封装），
// UI 组件与页面逻辑放域内页面文件。

import { BaseResponse } from '../../network/BaseResponse'
import { httpUtil } from '../../network/HttpUtil'
import { jsonNum, jsonStr, mapArray } from '../../network/vo/JsonParse'

/** 列表分页每页条数（对齐安卓 ReadLaterConstant.PAGE_SIZE） */
export const READ_LATER_PAGE_SIZE: number = 10

/** 收藏状态：未读（后端 status 字段取值，Tab 一/二对应） */
export const READ_LATER_STATUS_UNREAD: number = 0

/** 收藏状态：已读 */
export const READ_LATER_STATUS_READ: number = 1

/** 收藏 URL 的 https 前缀（契约仅收 https，端上先行简单校验兜底） */
export const READ_LATER_HTTPS_PREFIX: string = 'https://'

/** 单条收藏标签数上限（对齐安卓 ReadLaterConstant.TAG_MAX_COUNT，端上软约束） */
export const READ_LATER_TAG_MAX_COUNT: number = 5

/** 单个标签长度上限（对齐安卓 ReadLaterConstant.TAG_MAX_LENGTH） */
export const READ_LATER_TAG_MAX_LENGTH: number = 12

/** 收藏 URL 长度上限（后端 AddReadLaterFrom @Size(max=2048)） */
export const READ_LATER_URL_MAX_LENGTH: number = 2048

/** 搜索关键词长度上限（后端 SearchReadLaterFrom @Size(max=100)） */
export const READ_LATER_KEYWORD_MAX_LENGTH: number = 100

/**
 * 收藏条目视图对象（对齐后端 ReadLaterVo / 安卓 ReadLaterVo）。
 * 字段按后端返回全量声明；Jackson 可能省略 null 字段，故可空字段一律可选。
 */
export interface ReadLaterVo {
  id: number
  url: string
  title: string
  /** AI 摘要，未生成时为 null */
  digest: string | null
  /** 标签数组（后端由逗号分隔存储拆分），无标签为空数组 */
  tags: Array<string>
  /** 0 未读 1 已读 */
  status: number
  createdAt: string | null
  updatedAt: string | null
}

/** JSON → ReadLaterVo 显式映射；缺失字段回落空值/0（对齐安卓 VO 默认值） */
export function readLaterVoFromJSON(raw: Object): ReadLaterVo {
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    id: jsonNum(obj['id']) ?? 0,
    url: jsonStr(obj['url']) ?? '',
    title: jsonStr(obj['title']) ?? '',
    digest: jsonStr(obj['digest']),
    tags: mapArray(obj['tags'], (item: Object): string => jsonStr(item) ?? '')
      .filter((tag: string): boolean => tag.length > 0),
    status: jsonNum(obj['status']) ?? 0,
    createdAt: jsonStr(obj['createdAt']),
    updatedAt: jsonStr(obj['updatedAt'])
  }
}

/**
 * 泛型分页信封（后端 MyBatis-Plus IPage 序列化：records/total/size/current）。
 * 不复用 network/vo/PageData（其 records 被硬编码成 RechargeRecordVo）。
 */
export interface ReadLaterPageData<T> {
  records: Array<T>
  total: number
  size: number
  current: number
}

/** JSON → 泛型分页信封；raw 缺失兜底空页 */
export function readLaterPageDataFromJSON<T>(raw: Object | null | undefined,
  mapper: (item: Object) => T): ReadLaterPageData<T> {
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

/** 收藏请求体表单（对齐 AddReadLaterFrom / 安卓 AddReadLaterRequest） */
export interface AddReadLaterForm {
  /** 收藏链接（必填，仅 https） */
  url: string
  /** 标签（可选，不传 = 无标签） */
  tags?: Array<string>
}

/**
 * 更新请求体表单（对齐 UpdateReadLaterFrom / 安卓 UpdateReadLaterRequest）。
 * patch 语义：字段 undefined = 不传该键 = 后端不更新（与 memo 全量提交不同）。
 */
export interface UpdateReadLaterForm {
  /** 标签整体替换；undefined = 不更新（空数组 = 清空标签） */
  tags?: Array<string>
  /** 0 未读 1 已读；undefined = 不更新 */
  status?: number
}

/**
 * 稍后读文案表 — 键名逐一对齐安卓 feature/readlater/src/main/res/values/strings.xml 的
 * readlater_* 资源。便于跨端文案核对。
 */
export class ReadLaterText {
  // 页面
  static readonly readlater_title: string = "稍后读"
  static readonly readlater_url_hint: string = "粘贴 https:// 链接"
  static readonly readlater_action_collect: string = "收藏"
  static readonly readlater_tag_hint: string = "添加标签"
  static readonly readlater_tag_action_add: string = "添加"
  static readonly readlater_tab_unread: string = "未读"
  static readonly readlater_tab_read: string = "已读"
  static readonly readlater_search_hint: string = "搜索标题或关键词"
  static readonly readlater_tag_filter_all: string = "全部"

  // 列表状态
  static readonly readlater_empty_message: string = "还没有收藏，粘贴链接保存一篇吧"
  static readonly readlater_error_load: string = "加载失败"
  static readonly readlater_action_retry: string = "重试"

  // 卡片
  static readonly readlater_digest_entry: string = "生成 AI 摘要"
  static readonly readlater_digest_generating: string = "生成中…"
  static readonly readlater_action_digest: string = "生成摘要"
  static readonly readlater_action_mark_read: string = "标记已读"
  static readonly readlater_action_mark_unread: string = "标记未读"
  static readonly readlater_action_shorten: string = "转短链"
  static readonly readlater_action_delete: string = "删除"

  // 提示
  static readonly readlater_toast_invalid_url: string = "请输入合法的 https:// 链接"
  static readonly readlater_toast_add_success: string = "收藏成功"
  static readonly readlater_toast_digest_done: string = "摘要已生成"
  static readonly readlater_toast_copy_done: string = "短链已复制"
  static readonly readlater_toast_delete_done: string = "已删除"
  static readonly readlater_toast_failed: string = "操作失败，请重试"
}

// ==================== 展示格式化（对齐安卓 ReadLaterFormat） ====================

/**
 * https 简单校验：https:// 前缀 + host 非空且不含空白。
 * 只做端上快捷拦截（省一次注定失败的网络请求），完整合法性以后端校验为准。
 */
export function isValidHttpsUrl(url: string): boolean {
  if (!url.startsWith(READ_LATER_HTTPS_PREFIX)) {
    return false
  }
  const host = url.substring(READ_LATER_HTTPS_PREFIX.length).split('/')[0].trim()
  return host.length > 0 && !host.includes(' ')
}

/** 展示用域名（小写、去 www. 前缀；解析失败回退原文） */
export function readLaterHostOf(url: string): string {
  let rest = url
  if (rest.startsWith(READ_LATER_HTTPS_PREFIX)) {
    rest = rest.substring(READ_LATER_HTTPS_PREFIX.length)
  } else if (rest.startsWith('http://')) {
    rest = rest.substring('http://'.length)
  }
  const host = rest.split('/')[0].trim().toLowerCase()
  if (host.startsWith('www.')) {
    return host.substring('www.'.length)
  }
  return host
}

/** 收藏时间展示：yyyy-MM-ddTHH:mm:ss / yyyy-MM-dd HH:mm:ss → 「yyyy-MM-dd HH:mm」，非法原样返回 */
export function formatReadLaterCreatedAt(raw: string | null | undefined): string {
  const text = raw ?? ''
  if (text.length >= 16 && text.charAt(4) === '-' && text.charAt(7) === '-' &&
    (text.charAt(10) === 'T' || text.charAt(10) === ' ')) {
    return `${text.substring(0, 10)} ${text.substring(11, 16)}`
  }
  return text
}

// ==================== 六接口封装 ====================

/**
 * 收藏链接。url 仅收 https（端上先行 isValidHttpsUrl 拦截）；标题由后端抓取，端上不传。
 */
export async function addReadLater(form: AddReadLaterForm): Promise<BaseResponse<number>> {
  // ArkTS 禁止裸对象字面量（arkts-no-untyped-obj-literals），按索引赋值构造请求体。
  // tags 对齐安卓 AddReadLaterRequest（默认 emptyList）：恒携带该键（空数组 = 无标签）。
  const body: Record<string, Object> = {}
  body['url'] = form.url
  body['tags'] = form.tags ?? []
  return httpUtil.post<number>('/web/auth/readlater', body)
}

/**
 * 我的收藏分页。status/tag/keyword 均可选（不传 = 全部）；
 * keyword 模糊匹配 url + title + digest；后端按未读优先、时间倒序返回。
 */
export async function pageReadLaters(page: number, size: number, status?: number | null,
  tag?: string | null, keyword?: string | null): Promise<BaseResponse<ReadLaterPageData<ReadLaterVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  if (status !== undefined && status !== null) {
    params['status'] = status
  }
  if (tag !== undefined && tag !== null && tag.length > 0) {
    params['tag'] = tag
  }
  if (keyword !== undefined && keyword !== null && keyword.length > 0) {
    params['keyword'] = keyword
  }
  const res = await httpUtil.get<Object>('/web/auth/readlater/list', params)
  return {
    data: res.data === null ? null : readLaterPageDataFromJSON<ReadLaterVo>(res.data, readLaterVoFromJSON),
    code: res.code,
    message: res.message
  }
}

/**
 * 生成 AI 摘要（后端抓正文调 LLM，可能较慢，端上按条目给 loading 态）。
 * 平台未配置模型时后端返回业务失败，message 带具体文案（toast 优先采用）。
 */
export async function generateReadLaterDigest(id: number): Promise<BaseResponse<string>> {
  return httpUtil.post<string>(`/web/auth/readlater/${id}/digest`)
}

/**
 * 更新标签/已读状态（patch 语义，与 memo 全量提交不同）：
 * tags/status 为 undefined 时不构造该键，后端字段缺省即不更新；
 * tags 传空数组表示清空标签（必须与「不更新」区分开）。
 */
export async function updateReadLater(id: number, form: UpdateReadLaterForm): Promise<BaseResponse<string>> {
  const body: Record<string, Object> = {}
  if (form.tags !== undefined) {
    body['tags'] = form.tags
  }
  if (form.status !== undefined) {
    body['status'] = form.status
  }
  return httpUtil.put<string>(`/web/auth/readlater/${id}`, body)
}

/**
 * 删除收藏（后端逻辑删除，仅本人记录可删）。
 * 后端 Result.ok(String)：成功以 code === 200 判定。
 */
export async function deleteReadLater(id: number): Promise<BaseResponse<string>> {
  return httpUtil.delete<string>(`/web/auth/readlater/${id}`)
}

/** 一键转短链：返回完整短链地址，端上复制到剪贴板并提示 */
export async function shortenReadLater(id: number): Promise<BaseResponse<string>> {
  return httpUtil.post<string>(`/web/auth/readlater/${id}/shorten`)
}

/**
 * 契约核对（路径/参数与安卓 ReadLaterApi.kt 一致；冲突时以后端实现为准）：
 * | 接口 | 安卓 ReadLaterApi.kt | 后端 ReadLaterController | 本域 |
 * |---|---|---|---|
 * | POST   | web/auth/readlater body{url,tags[]} | @PostMapping + AddReadLaterFrom | ✓ |
 * | GET    | web/auth/readlater/list ?page&size&status?&tag?&keyword? | @GetMapping("/list") + SearchReadLaterFrom | ✓ |
 * | POST   | web/auth/readlater/{id}/digest | @PostMapping("/{id}/digest") | ✓ |
 * | PUT    | web/auth/readlater/{id} body{tags?,status?} | @PutMapping("/{id}") + UpdateReadLaterFrom（patch：null 不更新） | ✓ |
 * | DELETE | web/auth/readlater/{id} | @DeleteMapping("/{id}") | ✓ |
 * | POST   | web/auth/readlater/{id}/shorten | @PostMapping("/{id}/shorten") | ✓ |
 * 注：安卓 Retrofit 注解无前导斜杠，复用 baseUrl 后等价于本域 '/web/auth/readlater/**'。
 */
