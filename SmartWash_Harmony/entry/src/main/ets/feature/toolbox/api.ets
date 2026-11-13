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
import { resString } from '../../utils/ResourceString'

/** 列表/明细分页每页条数（对齐 ToolboxConstant.PAGE_SIZE；后端 BaseSearchFrom 上限 100） */
export const TOOLBOX_PAGE_SIZE: number = 10

/** 可见性：1公开 2私有（对齐 ToolboxConstant.VISIBILITY_*，后端 is_public） */
export const VISIBILITY_PUBLIC: number = 1
export const VISIBILITY_PRIVATE: number = 2

/** 内容类型：M1 仅短链（1短链 2活码 3交付箱 4图床 5时间胶囊 6设备投递 7私密文本 8物品标签） */
export const CONTENT_TYPE_SHORT_LINK: number = 1
/** 内容类型：7 私密文本（阅后即焚，一次性揭示即焚毁） */
export const CONTENT_TYPE_SECRET_TEXT: number = 7
/** 内容类型：8 物品标签（一物一码） */
export const CONTENT_TYPE_ASSET_TAG: number = 8

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
  /** 1短链 2活码 3交付箱 4图床 5时间胶囊 6设备投递 7私密文本 8物品标签 */
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
  /** 私密文本状态（仅 contentType=7）：ACTIVE/BURNED/EXPIRED（后端 SecretState，未知值兜底） */
  secretState?: string | null
  /** 焚毁时刻（仅 contentType=7，未焚毁为 null），ISO LocalDateTime */
  burnedAt?: string | null
  /** 私密文本分享链接：{toolbox.web-base-url}/s/{code}；未配置时为空串（降级仅展示短码） */
  secretUrl?: string | null
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

/** 创建短码请求体（对齐 AddShortCodeFrom；类型1 target 必填 expireAt 不传 = 永久；类型7 content 必填 expireAt 必填） */
export interface AddShortCodeForm {
  /** 内容类型：1 短链（缺省） 7 私密文本；不传由后端按 1 处理 */
  contentType?: number
  /** 类型1：目标长链（http/https）；类型7 不传 */
  target?: string
  /** 类型7：私密文本正文（≤2000 字符，服务端加密落库） */
  content?: string
  /** 类型7：可选访问口令（4-32 位，服务端 BCrypt 落库） */
  password?: string
  customCode?: string
  expireAt?: string
  isPublic: number
}

/** 更新短码请求体（对齐 UpdateShortCodeFrom；字段不传即不修改） */
export interface UpdateShortCodeForm {
  target?: string
  expireAt?: string
}

// ==================== 物品标签（一物一码 M2） ====================

/** 物品标签详情页路由入参（对齐安卓 AssetTagDetailViewModel 的 9 个 query 参数） */
export class AssetTagDetailParam {
  id: number = 0
  code: string = ''
  itemName: string = ''
  itemDesc: string = ''
  status: number = 0
  hasContact: boolean = false
  tagUrl: string = ''
  scanCount: number = 0
  lastScannedAt: string = ''
}

/** 创建物品标签请求体（对齐 CreateAssetTagFrom） */
export interface CreateAssetTagForm {
  itemName: string
  itemDesc?: string
  contact?: string
  customCode?: string
}

/** 更新物品标签请求体（对齐 UpdateAssetTagFrom；字段不传即不修改） */
export interface UpdateAssetTagForm {
  itemName?: string
  itemDesc?: string
  /** 空串 = 清空联系方式；不传 = 不修改 */
  contact?: string
  /** 0 正常 / 1 寻物中 / 2 已寻回；不传 = 不修改 */
  status?: number
}

/**
 * 物品标签视图对象（对齐后端 AssetTagVo / 安卓 AssetTagVo）。
 * 联系方式只回「是否已留」（hasContact），明文/密文均不回显。
 */
export interface AssetTagVo {
  id?: number
  code?: string
  itemName?: string | null
  itemDesc?: string | null
  /** 0 正常 / 1 寻物中 / 2 已寻回 */
  status?: number
  hasContact?: boolean
  /** 分享链接：{toolbox.web-base-url}/tag/{code}；未配置时为空串 */
  tagUrl?: string | null
  /** 累计扫码次数（失主线索） */
  scanCount?: number
  /** 最近被扫码时间，ISO LocalDateTime */
  lastScannedAt?: string | null
  createdAt?: string | null
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
  secretState: string = ''
  burnedAt: string = ''
  secretUrl: string = ''
}

/**
 * 工具箱短链文案表 — 键名逐一对齐安卓 feature/toolbox/src/main/res/values/strings.xml 的 toolbox_* 资源。
 * 便于跨端文案核对。
 */
export class ToolboxText {
  static get toolbox_title(): string { return resString('toolbox_title') }
  static get toolbox_create_title(): string { return resString('toolbox_create_title') }
  static get toolbox_detail_title(): string { return resString('toolbox_detail_title') }
  static get toolbox_action_new(): string { return resString('toolbox_action_new') }
  static get toolbox_empty_message(): string { return resString('toolbox_empty_message') }
  static get toolbox_empty_action(): string { return resString('toolbox_create_title') }
  static get toolbox_error_load(): string { return resString('toolbox_error_load') }
  // 网络异常/401 拒绝等传输层失败（原硬编码在列表页 catch 分支，收敛入表便于跨端文案核对）
  static get toolbox_error_network(): string { return resString('toolbox_text_toolbox_error_network') }
  static get toolbox_action_retry(): string { return resString('toolbox_action_retry') }
  static get toolbox_type_short_link(): string { return resString('toolbox_type_short_link') }
  static get toolbox_visibility_public_value(): string { return resString('toolbox_visibility_public_value') }
  static get toolbox_visibility_private_value(): string { return resString('toolbox_visibility_private_value') }
  static get toolbox_expiry_permanent(): string { return resString('toolbox_expiry_permanent') }
  static get toolbox_expiry_expired(): string { return resString('toolbox_expiry_expired') }
  static get toolbox_expiry_until_format(): string { return resString('toolbox_text_toolbox_expiry_until_format') }
  static get toolbox_clicks_unit(): string { return resString('toolbox_text_toolbox_clicks_unit') }
  static readonly toolbox_value_none: string = "—"
  static get toolbox_target_label(): string { return resString('toolbox_target_label') }
  static readonly toolbox_target_hint: string = "https://example.com/very-long-link"
  static get toolbox_target_helper(): string { return resString('toolbox_target_helper') }
  static get toolbox_custom_code_label(): string { return resString('toolbox_custom_code_label') }
  static get toolbox_custom_code_hint(): string { return resString('toolbox_custom_code_hint') }
  static get toolbox_visibility_label(): string { return resString('toolbox_visibility_label') }
  static get toolbox_visibility_public_desc(): string { return resString('toolbox_visibility_public_desc') }
  static get toolbox_visibility_private_desc(): string { return resString('toolbox_visibility_private_desc') }
  static get toolbox_expiry_label(): string { return resString('toolbox_expiry_label') }
  static get toolbox_expiry_preset_permanent(): string { return resString('toolbox_expiry_preset_permanent') }
  static get toolbox_expiry_preset_7d(): string { return resString('toolbox_expiry_preset_7d') }
  static get toolbox_expiry_preset_30d(): string { return resString('toolbox_expiry_preset_30d') }
  static get toolbox_expiry_preset_custom(): string { return resString('toolbox_expiry_preset_custom') }
  static get toolbox_expiry_custom_pick(): string { return resString('toolbox_expiry_custom_pick') }
  // 日期弹窗动作（对齐安卓 toolbox_date_confirm / toolbox_date_cancel，原先硬编码在弹窗内）
  static get toolbox_date_confirm(): string { return resString('toolbox_date_confirm') }
  static get toolbox_date_cancel(): string { return resString('toolbox_date_cancel') }
  static get toolbox_submit_create(): string { return resString('toolbox_submit_create') }
  static get toolbox_toast_create_success(): string { return resString('toolbox_toast_create_success') }
  static get toolbox_error_invalid_target(): string { return resString('toolbox_error_invalid_target') }
  static get toolbox_error_invalid_custom_code(): string { return resString('toolbox_error_invalid_custom_code') }
  static get toolbox_error_custom_date_required(): string { return resString('toolbox_error_custom_date_required') }
  static get toolbox_error_submit(): string { return resString('toolbox_error_submit') }
  static get toolbox_label_code(): string { return resString('toolbox_label_code') }
  static get toolbox_label_type(): string { return resString('toolbox_label_type') }
  static get toolbox_label_visibility(): string { return resString('toolbox_visibility_label') }
  static get toolbox_label_clicks(): string { return resString('toolbox_label_clicks') }
  static get toolbox_label_created(): string { return resString('toolbox_label_created') }
  static get toolbox_label_expiry(): string { return resString('toolbox_expiry_label') }
  static get toolbox_label_target(): string { return resString('toolbox_target_label') }
  static get toolbox_action_renew(): string { return resString('toolbox_action_renew') }
  static get toolbox_renew_title(): string { return resString('toolbox_action_renew') }
  static get toolbox_action_delete(): string { return resString('toolbox_action_delete') }
  static get toolbox_confirm_delete_title(): string { return resString('toolbox_confirm_delete_title') }
  static get toolbox_qr_section_title(): string { return resString('toolbox_qr_section_title') }
  static get toolbox_private_open_hint(): string { return resString('toolbox_private_open_hint') }
  static get toolbox_action_copy(): string { return resString('toolbox_text_toolbox_action_copy') }
  static get toolbox_action_open(): string { return resString('toolbox_action_open') }
  static get toolbox_copy_success(): string { return resString('toolbox_copy_success') }
  static get toolbox_copy_failed(): string { return resString('toolbox_text_toolbox_copy_failed') }
  static get toolbox_open_failed(): string { return resString('toolbox_open_failed') }
  static get toolbox_resolve_failed(): string { return resString('toolbox_resolve_failed') }
  static get toolbox_toast_renew_success(): string { return resString('toolbox_toast_renew_success') }
  static get toolbox_toast_delete_success(): string { return resString('toolbox_toast_delete_success') }
  static get toolbox_qr_loading(): string { return resString('toolbox_text_toolbox_qr_loading') }
  static get toolbox_qr_failed(): string { return resString('toolbox_text_toolbox_qr_failed') }
  static get toolbox_stats_title(): string { return resString('toolbox_stats_title') }
  static get toolbox_stats_empty(): string { return resString('toolbox_stats_empty') }
  static get toolbox_stats_error_load(): string { return resString('toolbox_stats_error_load') }
  /** 已有数据补拉失败 footer（含解决指引，对齐安卓 toolbox_stats_error_retry） */
  static get toolbox_stats_error_retry(): string { return resString('toolbox_stats_error_retry') }
  // ==================== 私密文本（阅后即焚） ====================
  static get toolbox_common_off(): string { return resString('toolbox_common_off') }
  static get toolbox_common_on(): string { return resString('toolbox_common_on') }
  static get toolbox_create_mode_short_link(): string { return resString('toolbox_create_mode_short_link') }
  static get toolbox_create_mode_secret(): string { return resString('toolbox_create_mode_secret') }
  static get toolbox_secret_mode_helper(): string { return resString('toolbox_secret_mode_helper') }
  static get toolbox_secret_content_label(): string { return resString('toolbox_secret_content_label') }
  static get toolbox_secret_content_hint(): string { return resString('toolbox_secret_content_hint') }
  static get toolbox_secret_content_counter_format(): string { return resString('toolbox_secret_content_counter_format') }
  static get toolbox_secret_password_label(): string { return resString('toolbox_secret_password_label') }
  static get toolbox_secret_password_hint(): string { return resString('toolbox_secret_password_hint') }
  static get toolbox_secret_password_desc(): string { return resString('toolbox_secret_password_desc') }
  static get toolbox_secret_expiry_label(): string { return resString('toolbox_secret_expiry_label') }
  static get toolbox_secret_expiry_preset_1h(): string { return resString('toolbox_secret_expiry_preset_1h') }
  static get toolbox_secret_expiry_preset_1d(): string { return resString('toolbox_secret_expiry_preset_1d') }
  static get toolbox_secret_expiry_preset_7d(): string { return resString('toolbox_secret_expiry_preset_7d') }
  static get toolbox_error_secret_content(): string { return resString('toolbox_error_secret_content') }
  static get toolbox_error_secret_password(): string { return resString('toolbox_error_secret_password') }
  static get toolbox_secret_label_state(): string { return resString('toolbox_secret_label_state') }
  static get toolbox_secret_state_active(): string { return resString('toolbox_secret_state_active') }
  static get toolbox_secret_state_burned(): string { return resString('toolbox_secret_state_burned') }
  static get toolbox_secret_state_expired(): string { return resString('toolbox_secret_state_expired') }
  static get toolbox_secret_state_unknown(): string { return resString('toolbox_secret_state_unknown') }
  static get toolbox_secret_label_burned_at(): string { return resString('toolbox_secret_label_burned_at') }
  static get toolbox_secret_link_label(): string { return resString('toolbox_secret_link_label') }
  static get toolbox_secret_link_hint(): string { return resString('toolbox_secret_link_hint') }
  static get toolbox_secret_link_unavailable(): string { return resString('toolbox_secret_link_unavailable') }
  static get toolbox_secret_privacy_note(): string { return resString('toolbox_secret_privacy_note') }
  static get toolbox_secret_copy_success(): string { return resString('toolbox_secret_copy_success') }
  // ==================== 物品标签（一物一码 M2） ====================
  static get asset_tag_title(): string { return resString('asset_tag_title') }
  static get asset_tag_create_title(): string { return resString('asset_tag_create_title') }
  static get asset_tag_detail_title(): string { return resString('asset_tag_detail_title') }
  static get asset_tag_action_new(): string { return resString('asset_tag_action_new') }
  static get asset_tag_empty_message(): string { return resString('asset_tag_empty_message') }
  static get asset_tag_item_name_label(): string { return resString('asset_tag_item_name_label') }
  static get asset_tag_item_name_hint(): string { return resString('asset_tag_item_name_hint') }
  static get asset_tag_item_desc_label(): string { return resString('asset_tag_item_desc_label') }
  static get asset_tag_item_desc_hint(): string { return resString('asset_tag_item_desc_hint') }
  static get asset_tag_contact_label(): string { return resString('asset_tag_contact_label') }
  static get asset_tag_contact_hint(): string { return resString('asset_tag_contact_hint') }
  static get asset_tag_contact_desc(): string { return resString('asset_tag_contact_desc') }
  static get asset_tag_custom_code_label(): string { return resString('asset_tag_custom_code_label') }
  static get asset_tag_submit_create(): string { return resString('asset_tag_submit_create') }
  static get asset_tag_toast_create_success(): string { return resString('asset_tag_toast_create_success') }
  static get asset_tag_error_item_name(): string { return resString('asset_tag_error_item_name') }
  static get asset_tag_error_contact(): string { return resString('asset_tag_error_contact') }
  static get asset_tag_error_submit(): string { return resString('asset_tag_error_submit') }
  static get asset_tag_status_normal(): string { return resString('asset_tag_status_normal') }
  static get asset_tag_status_lost(): string { return resString('asset_tag_status_lost') }
  static get asset_tag_status_found(): string { return resString('asset_tag_status_found') }
  static get asset_tag_label_status(): string { return resString('asset_tag_label_status') }
  static get asset_tag_label_scans(): string { return resString('asset_tag_label_scans') }
  static get asset_tag_scans_format(): string { return resString('asset_tag_scans_format') }
  static get asset_tag_label_last_scanned(): string { return resString('asset_tag_label_last_scanned') }
  static get asset_tag_label_code(): string { return resString('asset_tag_label_code') }
  static get asset_tag_link_label(): string { return resString('asset_tag_link_label') }
  static get asset_tag_link_hint(): string { return resString('asset_tag_link_hint') }
  static get asset_tag_link_unavailable(): string { return resString('asset_tag_link_unavailable') }
  static get asset_tag_copy_success(): string { return resString('asset_tag_copy_success') }
  static get asset_tag_copy_failed(): string { return resString('asset_tag_copy_failed') }
  static get asset_tag_action_set_lost(): string { return resString('asset_tag_action_set_lost') }
  static get asset_tag_action_set_found(): string { return resString('asset_tag_action_set_found') }
  static get asset_tag_action_set_normal(): string { return resString('asset_tag_action_set_normal') }
  static get asset_tag_toast_status_success(): string { return resString('asset_tag_toast_status_success') }
  static get asset_tag_action_delete(): string { return resString('asset_tag_action_delete') }
  static get asset_tag_confirm_delete_title(): string { return resString('asset_tag_confirm_delete_title') }
  static get asset_tag_confirm_delete_message(): string { return resString('asset_tag_confirm_delete_message') }
  static get asset_tag_toast_delete_success(): string { return resString('asset_tag_toast_delete_success') }
  static get asset_tag_privacy_note(): string { return resString('asset_tag_privacy_note') }
}

/** 物品标签寻物状态 → 展示文案（0 正常 / 1 寻物中 / 2 已寻回；未知兜底） */
export function toolboxAssetTagStatusText(status: number | null | undefined): string {
  if (status === 1) {
    return ToolboxText.asset_tag_status_lost
  }
  if (status === 2) {
    return ToolboxText.asset_tag_status_found
  }
  return ToolboxText.asset_tag_status_normal
}

/** 物品标签扫码次数展示（对齐安卓 asset_tag_scans_format "%1$d 次扫码"） */
export function assetTagScansFormat(scanCount: number): string {
  return ToolboxText.asset_tag_scans_format.replace('%1$s', scanCount.toString())
}

/** 私密文本状态 → 展示文案（对齐安卓 secretStateText 映射，未知值兜底） */
export function toolboxSecretStateText(state: string | null | undefined): string {
  if (state === 'ACTIVE') {
    return ToolboxText.toolbox_secret_state_active
  }
  if (state === 'BURNED') {
    return ToolboxText.toolbox_secret_state_burned
  }
  if (state === 'EXPIRED') {
    return ToolboxText.toolbox_secret_state_expired
  }
  return ToolboxText.toolbox_secret_state_unknown
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
  // 私密文本：content/password 按 contentType=7 分支；target 仅类型1 携带（后端按类型校验必填面）
  if (form.contentType === CONTENT_TYPE_SECRET_TEXT) {
    body['contentType'] = form.contentType
    if (form.content !== undefined) {
      body['content'] = form.content
    }
    if (form.password !== undefined && form.password.length > 0) {
      body['password'] = form.password
    }
  } else if (form.target !== undefined) {
    body['target'] = form.target
  }
  if (form.customCode !== undefined && form.customCode.length > 0) {
    body['customCode'] = form.customCode
  }
  if (form.expireAt !== undefined && form.expireAt.length > 0) {
    body['expireAt'] = form.expireAt
  }
  body['isPublic'] = form.isPublic
  return httpUtil.post<ShortCodeVo>('/web/auth/toolbox/short-codes', body)
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
  return httpUtil.get<ToolboxPageData<ShortCodeVo>>('/web/auth/toolbox/short-codes', params)
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
  return httpUtil.put<ShortCodeVo>(`/web/auth/toolbox/short-codes/${id}`, body)
}

/**
 * 删除短码（物理删除并清空跳转缓存）。
 * 后端 Result.ok(null)：data 字段为 null（或省略），成功一律以 code === 200 判定，勿判 data 非空。
 */
export async function deleteShortCode(id: number): Promise<BaseResponse<string>> {
  return httpUtil.delete<string>(`/web/auth/toolbox/short-codes/${id}`)
}

/** 访问明细分页（IP 仅存摘要） */
export async function pageShortCodeStats(id: number, page: number, size: number)
  : Promise<BaseResponse<ToolboxPageData<ShortCodeStatsVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  return httpUtil.get<ToolboxPageData<ShortCodeStatsVo>>(`/web/auth/toolbox/short-codes/${id}/stats`, params)
}

/**
 * owner 解析（私有码唯一取值通道，校验归属）。
 * 注意：后端 resolve 会计入一次点击统计 —— 只用于「私有码打开」，禁止用于详情页/列表刷新。
 */
export async function resolveShortCode(code: string): Promise<BaseResponse<ShortCodeVo>> {
  const params: Record<string, Object> = {}
  params['code'] = code
  return httpUtil.get<ShortCodeVo>('/web/auth/toolbox/short-codes/resolve', params)
}

/**
 * 契约核对（issue #28 验收：路径/参数与安卓 ToolboxApi.kt 一致；冲突时以后端实现为准）：
 * | 接口 | 安卓 ToolboxApi.kt | 后端 ToolboxShortCodeController | 本域 |
 * |---|---|---|---|
 * | POST  | /web/auth/toolbox/short-codes | @PostMapping 同路径 | ✓ |
 * | GET   | 同路径 ?page&size&contentType | @GetMapping + SearchShortCodeFrom | ✓ |
 * | PUT   | /{id} body{target?,expireAt?} | @PutMapping("/{id}") + UpdateShortCodeFrom | ✓ |
 * | DELETE| /{id} → data null | @DeleteMapping("/{id}") → Result.ok(null) | ✓ |
 * | stats | /{id}/stats ?page&size | @GetMapping("/{id}/stats") + BaseSearchFrom | ✓ |
 * | resolve | /resolve ?code | @GetMapping("/resolve") @RequestParam("code") | ✓ |
 * 无冲突；contentType 查询参数安卓有默认值但鸿蒙 M1 不使用，保持请求体/查询最小集。
 */

// ==================== 物品标签接口封装（/web/auth/toolbox/asset-tags） ====================

/** 创建标签：主表 type=8 短码 + 明细同事务；联系方式可选（服务端加密落库） */
export async function createAssetTag(form: CreateAssetTagForm): Promise<BaseResponse<AssetTagVo>> {
  const body: Record<string, Object> = {}
  body['itemName'] = form.itemName
  if (form.itemDesc !== undefined && form.itemDesc.length > 0) {
    body['itemDesc'] = form.itemDesc
  }
  if (form.contact !== undefined && form.contact.length > 0) {
    body['contact'] = form.contact
  }
  if (form.customCode !== undefined && form.customCode.length > 0) {
    body['customCode'] = form.customCode
  }
  return httpUtil.post<AssetTagVo>('/web/auth/toolbox/asset-tags', body)
}

/** 我的标签分页（按创建时间倒序，含扫码统计） */
export async function pageAssetTags(page: number, size: number)
  : Promise<BaseResponse<ToolboxPageData<AssetTagVo>>> {
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  return httpUtil.get<ToolboxPageData<AssetTagVo>>('/web/auth/toolbox/asset-tags', params)
}

/** 更新标签（信息/联系方式/寻物状态；状态裁定在后端） */
export async function updateAssetTag(id: number, form: UpdateAssetTagForm)
  : Promise<BaseResponse<AssetTagVo>> {
  const body: Record<string, Object> = {}
  if (form.itemName !== undefined) {
    body['itemName'] = form.itemName
  }
  if (form.itemDesc !== undefined) {
    body['itemDesc'] = form.itemDesc
  }
  if (form.contact !== undefined) {
    body['contact'] = form.contact
  }
  if (form.status !== undefined) {
    body['status'] = form.status
  }
  return httpUtil.put<AssetTagVo>(`/web/auth/toolbox/asset-tags/${id}`, body)
}

/** 删除标签（明细 + 访问明细 + 主表级联） */
export async function deleteAssetTag(id: number): Promise<BaseResponse<string>> {
  return httpUtil.delete<string>(`/web/auth/toolbox/asset-tags/${id}`)
}
