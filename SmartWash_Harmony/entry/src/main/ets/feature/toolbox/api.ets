// 工具箱短链域契约 — VO / 表单 / 常量 / 文案 / 六接口封装
//
// 依据差异分析 04-new-features.md §2.2；路径与参数逐一对齐安卓
// feature/toolbox/network/ToolboxApi.kt 与后端 ToolboxShortCodeController.java
// （@RequestMapping("/web/auth/toolbox/short-codes")，需 ROLE_USER）。
// 冲突时以后端实现为准（见文末「契约核对」注释）。
//
// 按 feature/README.md 约定：本文件只放声明层（VO interface + 常量 + 文案 + 接口封装），
// UI 组件与页面逻辑放域内各页面文件。

import { BaseResponse } from '../../network/BaseResponse'
import { httpUtil } from '../../network/HttpUtil'

/** 列表/明细分页每页条数（对齐 ToolboxConstant.PAGE_SIZE；后端 BaseSearchFrom 上限 100） */
export const TOOLBOX_PAGE_SIZE: number = 10

/** 可见性：1公开 2私有（对齐 ToolboxConstant.VISIBILITY_*，后端 is_public） */
export const VISIBILITY_PUBLIC: number = 1
export const VISIBILITY_PRIVATE: number = 2

/** 内容类型：M1 仅短链（1短链 2活码 3交付箱 4图床 5时间胶囊） */
export const CONTENT_TYPE_SHORT_LINK: number = 1

/**
 * 分页信封（后端 MyBatis-Plus IPage 序列化：records/total/size/current）。
 * 不复用 network/vo/PageData（该 interface 被硬编码成 RechargeRecordVo），此处按域自带泛型分页结构。
 */
export interface ToolboxPageData<T> {
  records: Array<T>
  total: number
  size: number
  current: number
}

/**
 * 短码视图对象（对齐后端 ShortCodeVo / 安卓 ShortCodeVo）。
 * 字段按后端返回全量声明；Jackson 可能省略 null 字段，故可空字段一律可选。
 */
export interface ShortCodeVo {
  id?: number
  code?: string
  /** 1短链 2活码 3交付箱 4图床 5时间胶囊 */
  contentType?: number
  target?: string | null
  /** 1公开 2私有 */
  isPublic?: number
  unlockAt?: string | null
  /** ISO LocalDateTime（yyyy-MM-dd'T'HH:mm:ss，无时区），null = 永久 */
  expireAt?: string | null
  maxVisits?: number | null
  clickCount?: number
  /** 完整跳转 URL：{toolbox.public-base-url}/web/t/{code}；二维码由客户端本地生成 */
  shortUrl?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

/** 访问明细视图对象（IP 仅存前 8 位摘要，后端不暴露明文） */
export interface ShortCodeStatsVo {
  id?: number
  code?: string
  ipHash?: string | null
  userAgent?: string | null
  referer?: string | null
  createdAt?: string | null
}

/** 创建短码请求体（对齐 AddShortCodeFrom；expireAt 不传 = 永久） */
export interface AddShortCodeForm {
  target: string
  customCode?: string
  expireAt?: string
  isPublic: number
}

/** 更新短码请求体（对齐 UpdateShortCodeFrom；字段不传即不修改） */
export interface UpdateShortCodeForm {
  target?: string
  expireAt?: string
}

/**
 * 详情页路由入参（对齐安卓 ShortCodeDetailViewModel 的 9 个 query 参数）。
 * 基础字段一律由列表路由携带，详情页禁止二次请求（resolve 会计点击统计）。
 */
export class ToolboxShortCodeDetailParam {
  id: number = 0
  code: string = ''
  contentType: number = CONTENT_TYPE_SHORT_LINK
  isPublic: number = VISIBILITY_PUBLIC
  clickCount: number = 0
  target: string = ''
  expireAt: string = ''
  shortUrl: string = ''
  createdAt: string = ''
}

/**
 * 工具箱短链文案表 — 键名逐一对齐安卓 feature/toolbox/src/main/res/values/strings.xml 的 toolbox_* 资源。
 * 便于跨端文案核对。
 */
export class ToolboxText {
  static readonly toolbox_title: string = "短链管理"
  static readonly toolbox_create_title: string = "新建短链"
  static readonly toolbox_detail_title: string = "短链详情"
  static readonly toolbox_action_new: string = "新建"
  static readonly toolbox_empty_message: string = "还没有短链，创建一个吧"
  static readonly toolbox_empty_action: string = "新建短链"
  static readonly toolbox_error_load: string = "短链加载失败"
  static readonly toolbox_action_retry: string = "重试"
  static readonly toolbox_type_short_link: string = "短链"
  static readonly toolbox_visibility_public_value: string = "公开"
  static readonly toolbox_visibility_private_value: string = "私有"
  static readonly toolbox_expiry_permanent: string = "永久有效"
  static readonly toolbox_expiry_expired: string = "已过期"
  static readonly toolbox_expiry_until_format: string = "有效期至 %s"
  static readonly toolbox_clicks_unit: string = "次点击"
  static readonly toolbox_value_none: string = "—"
  static readonly toolbox_target_label: string = "目标链接"
  static readonly toolbox_target_hint: string = "https://example.com/very-long-link"
  static readonly toolbox_target_helper: string = "必填，以 http:// 或 https:// 开头"
  static readonly toolbox_custom_code_label: string = "自定义短码（可选）"
  static readonly toolbox_custom_code_hint: string = "4-16 位字母数字或 -_"
  static readonly toolbox_visibility_label: string = "可见性"
  static readonly toolbox_visibility_public_desc: string = "任何人可经短链跳转"
  static readonly toolbox_visibility_private_desc: string = "仅自己可解析打开"
  static readonly toolbox_expiry_label: string = "有效期"
  static readonly toolbox_expiry_preset_permanent: string = "永久"
  static readonly toolbox_expiry_preset_7d: string = "7 天"
  static readonly toolbox_expiry_preset_30d: string = "30 天"
  static readonly toolbox_expiry_preset_custom: string = "自定义"
  static readonly toolbox_expiry_custom_pick: string = "选择过期日期"
  static readonly toolbox_submit_create: string = "创建"
  static readonly toolbox_toast_create_success: string = "创建成功"
  static readonly toolbox_error_invalid_target: string = "目标链接须以 http:// 或 https:// 开头"
  static readonly toolbox_error_invalid_custom_code: string = "短码须为 4-16 位字母数字或 -_"
  static readonly toolbox_error_custom_date_required: string = "请先选择过期日期"
  static readonly toolbox_error_submit: string = "提交失败，请重试"
  static readonly toolbox_label_code: string = "短码"
  static readonly toolbox_label_type: string = "类型"
  static readonly toolbox_label_visibility: string = "可见性"
  static readonly toolbox_label_clicks: string = "点击次数"
  static readonly toolbox_label_created: string = "创建时间"
  static readonly toolbox_label_expiry: string = "有效期"
  static readonly toolbox_label_target: string = "目标链接"
  static readonly toolbox_action_renew: string = "修改有效期"
  static readonly toolbox_renew_title: string = "修改有效期"
  static readonly toolbox_action_delete: string = "删除"
  static readonly toolbox_confirm_delete_title: string = "删除短链"
  static readonly toolbox_qr_section_title: string = "扫码访问"
  static readonly toolbox_private_open_hint: string = "私有短码将经服务器解析后打开（计入一次点击）"
  static readonly toolbox_action_copy: string = "复制短链"
  static readonly toolbox_action_open: string = "打开链接"
  static readonly toolbox_copy_success: string = "短链已复制"
  static readonly toolbox_open_failed: string = "未找到可打开链接的应用"
  static readonly toolbox_resolve_failed: string = "解析失败，请重试"
  static readonly toolbox_toast_renew_success: string = "有效期已更新"
  static readonly toolbox_toast_delete_success: string = "已删除"
  static readonly toolbox_qr_loading: string = "二维码生成中"
  static readonly toolbox_qr_failed: string = "二维码生成失败，点击重试"
  static readonly toolbox_stats_title: string = "访问明细"
  static readonly toolbox_stats_empty: string = "暂无访问记录"
  static readonly toolbox_stats_error_load: string = "访问明细加载失败"
}


/** 删除确认文案（%s 占位短码） */
export function toolboxConfirmDeleteMessage(code: string): string {
  return `删除后短链立即失效且不可恢复，确定删除「${code}」？`
}

/** 有效期展示（对齐安卓 toolbox_expiry_until_format「有效期至 %1$s」） */
export function toolboxExpiryUntil(display: string): string {
  return ToolboxText.toolbox_expiry_until_format.replace('%s', display)
}

/** 点击次数展示（对齐安卓 toolbox_clicks_format "%1$d 次点击"） */
export function toolboxClicksFormat(clickCount: number): string {
  return `${clickCount} ${ToolboxText.toolbox_clicks_unit}`
}

// ==================== 六接口封装 ====================

/**
 * 创建短码。target 必填（http/https）；expireAt 不传 = 永久。
 * 后端 AddShortCodeFrom：customCode 不传则随机生成 7 位短码。
 */
export async function createShortCode(form: AddShortCodeForm): Promise<BaseResponse<ShortCodeVo>> {
  // ArkTS 禁止裸对象字面量（arkts-no-untyped-obj-literals），按索引赋值构造请求体。
  // customCode/expireAt 为空时「不传该键」——后端 AddShortCodeFrom 字段缺省即 null
  // （null customCode = 随机短码；null expireAt = 永久），与显式传 null 等价。
  const body: Record<string, Object> = {}
  body['target'] = form.target
  if (form.customCode !== undefined && form.customCode.length > 0) {
    body['customCode'] = form.customCode
  }
  if (form.expireAt !== undefined && form.expireAt.length > 0) {
    body['expireAt'] = form.expireAt
  }
  body['isPublic'] = form.isPublic
  return httpUtil.post<ShortCodeVo>('web/auth/toolbox/short-codes', body)
}

/**
 * 我的短码分页（按创建时间倒序）。
 * 后端 list(SearchShortCodeFrom)：page/size 默认 1/10，contentType 筛选仅管理端使用。
 */
export async function pageShortCodes(page: number, size: number)
  : Promise<BaseResponse<ToolboxPageData<ShortCodeVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  return httpUtil.get<ToolboxPageData<ShortCodeVo>>('web/auth/toolbox/short-codes', params)
}

/**
 * 更新短码（续期/改向）：target/expireAt 不传即不修改。
 * 后端 UpdateShortCodeFrom 的 LocalDateTime expireAt 反序列化按 ISO 串解析（与安卓一致）。
 */
export async function updateShortCode(id: number, form: UpdateShortCodeForm)
  : Promise<BaseResponse<ShortCodeVo>> {
  const body: Record<string, Object> = {}
  if (form.target !== undefined) {
    body['target'] = form.target
  }
  if (form.expireAt !== undefined) {
    body['expireAt'] = form.expireAt
  }
  return httpUtil.put<ShortCodeVo>(`web/auth/toolbox/short-codes/${id}`, body)
}

/**
 * 删除短码（物理删除并清空跳转缓存）。
 * 后端 Result.ok(null)：data 字段为 null（或省略），成功一律以 code === 200 判定，勿判 data 非空。
 */
export async function deleteShortCode(id: number): Promise<BaseResponse<string>> {
  return httpUtil.delete<string>(`web/auth/toolbox/short-codes/${id}`)
}

/** 访问明细分页（IP 仅存摘要） */
export async function pageShortCodeStats(id: number, page: number, size: number)
  : Promise<BaseResponse<ToolboxPageData<ShortCodeStatsVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  return httpUtil.get<ToolboxPageData<ShortCodeStatsVo>>(`web/auth/toolbox/short-codes/${id}/stats`, params)
}

/**
 * owner 解析（私有码唯一取值通道，校验归属）。
 * 注意：后端 resolve 会计入一次点击统计 —— 只用于「私有码打开」，禁止用于详情页/列表刷新。
 */
export async function resolveShortCode(code: string): Promise<BaseResponse<ShortCodeVo>> {
  const params: Record<string, Object> = {}
  params['code'] = code
  return httpUtil.get<ShortCodeVo>('web/auth/toolbox/short-codes/resolve', params)
}

/**
 * 契约核对（issue #28 验收：路径/参数与安卓 ToolboxApi.kt 一致；冲突时以后端实现为准）：
 * | 接口 | 安卓 ToolboxApi.kt | 后端 ToolboxShortCodeController | 本域 |
 * |---|---|---|---|
 * | POST  | web/auth/toolbox/short-codes | @PostMapping 同路径 | ✓ |
 * | GET   | 同路径 ?page&size&contentType | @GetMapping + SearchShortCodeFrom | ✓ |
 * | PUT   | /{id} body{target?,expireAt?} | @PutMapping("/{id}") + UpdateShortCodeFrom | ✓ |
 * | DELETE| /{id} → data null | @DeleteMapping("/{id}") → Result.ok(null) | ✓ |
 * | stats | /{id}/stats ?page&size | @GetMapping("/{id}/stats") + BaseSearchFrom | ✓ |
 * | resolve | /resolve ?code | @GetMapping("/resolve") @RequestParam("code") | ✓ |
 * 无冲突；contentType 查询参数安卓有默认值但鸿蒙 M1 不使用，保持请求体/查询最小集。
 */
