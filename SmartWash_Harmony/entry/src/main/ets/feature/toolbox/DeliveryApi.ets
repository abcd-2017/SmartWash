// 设备投递域契约 — VO / 表单 / 常量 / 规则 / 文案 / 接口封装
//
// 依据根目录 docs/delivery-api.md 与后端 ToolboxDeliveryController.java；
// 路径与参数逐一对齐安卓 feature/toolbox/network/DeliveryApi.kt
// （owner 侧，前缀 /web/auth/toolbox/delivery，需 ROLE_USER）。
// 对端（电脑浏览器）走公开段 /web/delivery/**（ToolboxDeliveryPublicController），本端不消费。
//
// 错误码语义（信封 code，非 HTTP 状态码，对齐 DeliveryBusinessCode）：
//   201 通用失败 / 403 密码错误 / 404 会话或条目失效 / 409 状态冲突 / 413 负载超限。
//   该组业务码不得触发全局 401 登出（Axios 拦截器只特判 401）。
//
// 按 feature/README.md 约定：本文件只放声明层（VO interface + 常量 + 规则 + 文案 + 接口封装），
// UI 组件与页面逻辑放 ui/delivery/ 各页面文件。

import { BaseResponse } from '../../network/BaseResponse'
import { httpUtil } from '../../network/HttpUtil'
import { FormData, Type } from '@ohos/axios'
import { resString } from '../../utils/ResourceString'

// ==================== 常量 ====================

/** 有效期预设（分钟），对齐安卓 DeliveryCreateViewModel.DURATION_PRESETS 与契约 1-120、缺省 30 */
export const DELIVERY_DURATION_PRESETS: number[] = [10, 30, 60, 120]
export const DELIVERY_MIN_DURATION_MINUTES: number = 1
export const DELIVERY_MAX_DURATION_MINUTES: number = 120
export const DELIVERY_DEFAULT_DURATION_MINUTES: number = 30

/** 对端进入密码范围（契约：可选，4-64 位） */
export const DELIVERY_MIN_PASSWORD_LENGTH: number = 4
export const DELIVERY_MAX_PASSWORD_LENGTH: number = 64

/** 文本条目上限（契约：10 万字符） */
export const DELIVERY_MAX_TEXT_LENGTH: number = 100000

/** 单文件上限（契约 20MB；端侧前置校验与后端 413 终审一致） */
export const DELIVERY_MAX_FILE_BYTES: number = 20 * 1024 * 1024

/** 轮询基准间隔（契约 2-3s，取中值；对齐安卓 POLL_INTERVAL_MS） */
export const DELIVERY_POLL_INTERVAL_MS: number = 2500
/** 连续失败指数退避封顶（对齐安卓 MAX_POLL_INTERVAL_MS） */
export const DELIVERY_MAX_POLL_INTERVAL_MS: number = 10000

// ==================== 业务码 ====================

export const DELIVERY_BIZ_FAIL: number = 201
export const DELIVERY_BIZ_WRONG_PASSWORD: number = 403
export const DELIVERY_BIZ_GONE: number = 404
export const DELIVERY_BIZ_CONFLICT: number = 409
export const DELIVERY_BIZ_TOO_LARGE: number = 413

// ==================== 枚举 ====================

/** 发布方向：1 手机→电脑（owner 发布）、2 电脑→手机（对端发布）；未知码防御为对端发布 */
export enum DeliveryDirection {
  PHONE_TO_COMPUTER = 1,
  COMPUTER_TO_PHONE = 2
}

/** 条目类型：1 文本、2 图片、3 文件；未知码防御为文件（信息量最全的兜底展示） */
export enum DeliveryItemKind {
  TEXT = 1,
  IMAGE = 2,
  FILE = 3
}

// ==================== VO / 表单 ====================

/** 投递会话视图对象（会话元信息不含 password_hash，仅回是否已设密） */
export interface DeliverySessionVo {
  /** 投递码（7 位） */
  code?: string
  /** ISO LocalDateTime（yyyy-MM-dd'T'HH:mm:ss），过期/关闭后读路径一律 404 */
  expireAt?: string | null
  /** 累计进入次数（对端凭码进入时原子自增） */
  clickCount?: number
  /** 是否已设置进入密码 */
  hasPassword?: boolean
  /** 条目总数（含已焚毁占位） */
  itemCount?: number
  /** 未取走条目数 */
  pendingCount?: number
  /** 对端 H5 页地址（{public-base-url}/d/{code}），二维码内容即该 URL */
  enterUrl?: string | null
  createdAt?: string | null
}

/** 投递条目视图对象（列表轮询用）。已焚毁条目保留占位，不回传内容本身 */
export interface DeliveryItemVo {
  id?: number
  /** 1手机→电脑 2电脑→手机 */
  direction?: number
  /** 1文本 2图片 3文件 */
  kind?: number
  /** 文件名或文本摘要 */
  title?: string | null
  /** 0未取走 1已取走（焚毁占位） */
  taken?: number
  takenAt?: string | null
  /** 文件字节数（kind=2/3） */
  objectSize?: number | null
  mimeType?: string | null
  sha256?: string | null
  /** 是否仍有内容可读（焚毁后 false） */
  hasContent?: boolean
  createdAt?: string | null
}

/** 投递会话详情：会话元信息 + 全量条目（含已焚毁占位，按创建时间正序） */
export interface DeliverySessionDetailVo {
  session?: DeliverySessionVo | null
  items?: Array<DeliveryItemVo>
}

/** 取走条目响应（文本=认领+焚毁一步完成，正文随响应返回；文件=仅原子认领） */
export interface DeliveryItemTakenVo {
  id?: number
  kind?: number
  title?: string | null
  /** 文本条目正文（认领后焚毁，仅本次响应返回一次） */
  textContent?: string | null
  /** 文件条目内容下载路径（owner 侧固定走 owner content 端点，不消费本字段） */
  contentUrl?: string | null
}

/** 文本条目内容（未取走返回正文；已焚毁 textContent=null） */
export interface DeliveryTextContentVo {
  id?: number
  kind?: number
  textContent?: string | null
}

/** 创建投递会话请求体（对齐 CreateDeliverySessionFrom；durationMinutes 不传 = 后端缺省 30） */
export interface AddDeliverySessionForm {
  durationMinutes?: number
  password?: string
}

/** 发布文本条目请求体（对齐 PublishDeliveryTextFrom；title 不传由后端截取正文前 30 字） */
export interface AddDeliveryTextForm {
  textContent: string
  title?: string
}

// ==================== 纯规则（零 UI 依赖，对齐安卓 DeliveryRules） ====================

export function isValidPassword(password: string): boolean {
  return password.length >= DELIVERY_MIN_PASSWORD_LENGTH
    && password.length <= DELIVERY_MAX_PASSWORD_LENGTH
}

export function isValidDuration(minutes: number): boolean {
  return minutes >= DELIVERY_MIN_DURATION_MINUTES && minutes <= DELIVERY_MAX_DURATION_MINUTES
}

export function directionFromCode(code: number): DeliveryDirection {
  return code === DeliveryDirection.PHONE_TO_COMPUTER
    ? DeliveryDirection.PHONE_TO_COMPUTER : DeliveryDirection.COMPUTER_TO_PHONE
}

export function kindFromCode(code: number): DeliveryItemKind {
  if (code === DeliveryItemKind.TEXT) {
    return DeliveryItemKind.TEXT
  }
  if (code === DeliveryItemKind.IMAGE) {
    return DeliveryItemKind.IMAGE
  }
  return DeliveryItemKind.FILE
}

/**
 * 剩余秒数 → 「mm:ss」（≥1 小时为「H:MM:SS」）；倒计时/投递码同场景开 tnum，
 * 数字变化不左右跳（规范 §2.2 硬规则）。
 */
export function formatRemaining(totalSeconds: number): string {
  const safe = totalSeconds > 0 ? totalSeconds : 0
  const hours = Math.floor(safe / 3600)
  const minutes = Math.floor((safe % 3600) / 60)
  const seconds = safe % 60
  if (hours > 0) {
    return `${hours}:${pad2(minutes)}:${pad2(seconds)}`
  }
  return `${pad2(minutes)}:${pad2(seconds)}`
}

function pad2(value: number): string {
  return value < 10 ? `0${value}` : `${value}`
}

/** 文件大小展示：B / KB / MB（一位小数），与条目 objectSize、单文件上限提示共用 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes}B`
  }
  const kb = bytes / 1024
  if (kb < 1024) {
    return `${kb.toFixed(1)}KB`
  }
  const mb = kb / 1024
  return `${mb.toFixed(1)}MB`
}

/**
 * ISO LocalDateTime 解析为毫秒时间戳（不可解析返回 null）。
 * 后端两种分隔符实测并存（列表 T / 详情空格），此处统一兼容；按本地时区朴素处理
 * （与安卓 DeliveryRules.parseOrNull 同口径，不做时区补偿）。
 */
export function parseIsoToMillis(iso: string | null): number | null {
  if (iso === null) {
    return null
  }
  const raw = iso.trim()
  if (raw.length === 0) {
    return null
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/.exec(raw)
  if (match === null) {
    return null
  }
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const hour = Number(match[4])
  const minute = Number(match[5])
  const second = Number(match[6])
  return new Date(year, month - 1, day, hour, minute, second).getTime()
}

/** 条目时间展示：「MM-dd HH:mm」（会话为分钟级短会话，省略年份） */
export function formatItemTime(iso: string | null): string {
  const millis = parseIsoToMillis(iso)
  if (millis === null) {
    return iso === null ? '' : iso
  }
  const date = new Date(millis)
  return `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

/**
 * 过期时刻 → 剩余秒数（不足 1 秒归 0；expireAt 不可解析返回 null）。
 * 每次轮询重新锚定，页面侧滴答只做本地递减。
 */
export function remainingSeconds(expireAt: string | null): number | null {
  const millis = parseIsoToMillis(expireAt)
  if (millis === null) {
    return null
  }
  const remain = Math.floor((millis - Date.now()) / 1000)
  return remain > 0 ? remain : 0
}

// ==================== 文案（逐一对齐安卓 strings.xml 的 toolbox_delivery_* 资源） ====================

export class DeliveryText {
  static get toolbox_delivery_title(): string { return resString('toolbox_delivery_title') }
  static get toolbox_delivery_qr_desc(): string { return resString('toolbox_delivery_qr_desc') }
  static get toolbox_delivery_countdown_prefix(): string { return resString('delivery_text_toolbox_delivery_countdown_prefix') }
  static get toolbox_delivery_duration_label(): string { return resString('toolbox_expiry_label') }
  /** 有效期说明带时长（对齐安卓 strings.xml「%1$d 分钟时间内有效，到期自动失效」，compare-07 #26） */
  static durationDesc(minutes: number): string {
    return `${minutes} 分钟时间内有效，到期自动失效`
  }
  static get toolbox_delivery_password_label(): string { return resString('toolbox_delivery_password_label') }
  static get toolbox_delivery_password_hint(): string { return resString('toolbox_delivery_password_hint') }
  static get toolbox_delivery_password_helper(): string { return resString('toolbox_delivery_password_helper') }
  static get toolbox_delivery_privacy_notice(): string { return resString('toolbox_delivery_privacy_notice') }
  static get toolbox_delivery_submit_create(): string { return resString('toolbox_delivery_submit_create') }
  static get toolbox_delivery_error_invalid_password(): string { return resString('toolbox_delivery_error_invalid_password') }
  static get toolbox_delivery_error_create(): string { return resString('toolbox_delivery_error_create') }
  static get toolbox_delivery_code_label(): string { return resString('toolbox_delivery_code_label') }
  static get toolbox_delivery_session_expired(): string { return resString('toolbox_delivery_session_expired') }
  static get toolbox_delivery_has_password(): string { return resString('toolbox_delivery_has_password') }
  static get toolbox_delivery_action_copy_link(): string { return resString('toolbox_delivery_action_copy_link') }
  static get toolbox_delivery_action_open_session(): string { return resString('toolbox_delivery_action_open_session') }
  static get toolbox_delivery_action_close(): string { return resString('toolbox_delivery_action_close') }
  static get toolbox_delivery_action_recreate(): string { return resString('toolbox_delivery_action_recreate') }
  static get toolbox_delivery_toast_close_success(): string { return resString('toolbox_delivery_toast_close_success') }
  static get toolbox_delivery_toast_link_copied(): string { return resString('toolbox_delivery_toast_link_copied') }
  static get toolbox_delivery_error_close(): string { return resString('toolbox_delivery_error_close') }
  static get toolbox_delivery_session_title(): string { return resString('toolbox_delivery_title') }
  static get toolbox_delivery_url_fallback(): string { return resString('toolbox_delivery_url_fallback') }
  static get toolbox_delivery_action_close_session(): string { return resString('toolbox_delivery_action_close_session') }
  static get toolbox_delivery_confirm_close_title(): string { return resString('toolbox_delivery_confirm_close_title') }
  static get toolbox_delivery_confirm_close_message(): string { return resString('toolbox_delivery_confirm_close_message') }
  static get toolbox_delivery_empty_message(): string { return resString('toolbox_delivery_empty_message') }
  static get toolbox_delivery_poll_error(): string { return resString('toolbox_delivery_poll_error') }
  static get toolbox_delivery_error_load(): string { return resString('toolbox_delivery_error_load') }
  static get toolbox_delivery_action_retry(): string { return resString('toolbox_action_retry') }
  static get toolbox_delivery_direction_out(): string { return resString('toolbox_delivery_direction_out') }
  static get toolbox_delivery_direction_in(): string { return resString('toolbox_delivery_direction_in') }
  static get toolbox_delivery_kind_text(): string { return resString('toolbox_delivery_kind_text') }
  static get toolbox_delivery_kind_image(): string { return resString('toolbox_delivery_kind_image') }
  static get toolbox_delivery_kind_file(): string { return resString('toolbox_delivery_kind_file') }
  static get toolbox_delivery_state_pending(): string { return resString('toolbox_delivery_state_pending') }
  static get toolbox_delivery_state_taken(): string { return resString('toolbox_delivery_state_taken') }
  static get toolbox_delivery_toast_code_copied(): string { return resString('toolbox_delivery_toast_code_copied') }
  static get toolbox_delivery_sending(): string { return resString('toolbox_delivery_sending') }
  static get toolbox_delivery_send_hint(): string { return resString('toolbox_delivery_send_hint') }
  static get toolbox_delivery_action_send(): string { return resString('toolbox_delivery_action_send') }
  static get toolbox_delivery_pick_image(): string { return resString('toolbox_delivery_pick_image') }
  static get toolbox_delivery_pick_file(): string { return resString('toolbox_delivery_pick_file') }
  static get toolbox_delivery_error_send(): string { return resString('toolbox_delivery_error_send') }
  static get toolbox_delivery_error_file_too_large(): string { return resString('toolbox_delivery_error_file_too_large') }
  static get toolbox_delivery_error_text_too_long(): string { return resString('toolbox_delivery_error_text_too_long') }
  static get toolbox_delivery_error_text_empty(): string { return resString('toolbox_delivery_error_text_empty') }
  static get toolbox_delivery_session_gone_toast(): string { return resString('toolbox_delivery_session_gone_toast') }
  static get toolbox_delivery_item_taken_toast(): string { return resString('toolbox_delivery_item_taken_toast') }
  static get toolbox_delivery_action_copy_take(): string { return resString('toolbox_delivery_action_copy_take') }
  static get toolbox_delivery_action_take(): string { return resString('delivery_text_toolbox_delivery_action_take') }
  static get toolbox_delivery_toast_copy_success(): string { return resString('toolbox_delivery_toast_copy_success') }
  static get toolbox_delivery_preview_error(): string { return resString('toolbox_delivery_preview_error') }
  static get toolbox_delivery_confirm_take_title(): string { return resString('delivery_text_toolbox_delivery_confirm_take_title') }
  static get toolbox_delivery_confirm_take_message(): string { return resString('delivery_text_toolbox_delivery_confirm_take_message') }
}

/** 有效期 chip 文案（10/30 分钟走「%d 分钟」，60/120 走「1 小时 / 2 小时」） */
export function durationChipText(minutes: number): string {
  if (minutes === 60) {
    return resString('toolbox_delivery_duration_60m')
  }
  if (minutes === 120) {
    return resString('toolbox_delivery_duration_120m')
  }
  return `${minutes} 分钟`
}

/** 进入次数文案（对齐安卓 toolbox_delivery_clicks_format「%1$d 次进入」） */
export function clicksFormat(clickCount: number): string {
  return `${clickCount} 次进入`
}

// ==================== 接口封装 ====================

/**
 * 创建投递会话。durationMinutes 1-120（不传 = 后端缺省 30），password 可选 4-64 位
 * （服务端 BCrypt 存储、不回传）。
 */
export async function createDeliverySession(form: AddDeliverySessionForm)
  : Promise<BaseResponse<DeliverySessionVo>> {
  const body: Record<string, Object> = {}
  if (form.durationMinutes !== undefined) {
    body['durationMinutes'] = form.durationMinutes
  }
  if (form.password !== undefined && form.password.length > 0) {
    body['password'] = form.password
  }
  return httpUtil.post<DeliverySessionVo>('/web/auth/toolbox/delivery/sessions', body)
}

/** 会话详情 + 全量条目（owner 视角；条目按 createdAt 正序，含已焚毁占位行，轮询消费） */
export async function fetchDeliverySessionDetail(code: string)
  : Promise<BaseResponse<DeliverySessionDetailVo>> {
  return httpUtil.get<DeliverySessionDetailVo>(`/web/auth/toolbox/delivery/sessions/${code}`)
}

/** 关闭会话：expire_at 前置为当前时刻，读路径立即失效（data=null，成功以 code=200 为准） */
export async function closeDeliverySession(code: string): Promise<BaseResponse<string>> {
  return httpUtil.post<string>(`/web/auth/toolbox/delivery/sessions/${code}/close`)
}

/** 发布文本条目（正文上限 10 万字符；title 缺省由后端截取正文前 30 字） */
export async function publishDeliveryText(code: string, form: AddDeliveryTextForm)
  : Promise<BaseResponse<DeliveryItemVo>> {
  const body: Record<string, Object> = {}
  body['textContent'] = form.textContent
  if (form.title !== undefined && form.title.length > 0) {
    body['title'] = form.title
  }
  return httpUtil.post<DeliveryItemVo>(`/web/auth/toolbox/delivery/sessions/${code}/items`, body)
}

/**
 * 发布文件/图片条目（multipart；kind 2 图片 / 3 文件；单文件 ≤20MB）。
 * 文件内容支持 ArrayBuffer（ArkTS 无浏览器 DOM FormData，经 option 指定文件名与 MIME）。
 */
export async function publishDeliveryFile(code: string, kind: number, fileName: string,
  mimeType: string, buffer: ArrayBuffer): Promise<BaseResponse<DeliveryItemVo>> {
  const formData: FormData = new FormData()
  formData.append('kind', `${kind}`)
  const option: Type = { filename: fileName, type: mimeType }
  formData.append('file', buffer, option)
  return httpUtil.upload<DeliveryItemVo>(`/web/auth/toolbox/delivery/sessions/${code}/items/file`, formData)
}

/**
 * 取走条目：文本=认领+焚毁一步完成（textContent 随响应返回，此后不可再读）；
 * 文件=仅原子认领。
 */
export async function takeDeliveryItem(code: string, itemId: number)
  : Promise<BaseResponse<DeliveryItemTakenVo>> {
  return httpUtil.post<DeliveryItemTakenVo>(`/web/auth/toolbox/delivery/sessions/${code}/items/${itemId}/take`)
}

/** 文本条目内容：未取走返回正文；已焚毁 textContent=null（响应信封 JSON，非破坏预览） */
export async function fetchDeliveryTextContent(code: string, itemId: number)
  : Promise<BaseResponse<DeliveryTextContentVo>> {
  return httpUtil.get<DeliveryTextContentVo>(`/web/auth/toolbox/delivery/sessions/${code}/items/${itemId}/content`)
}

/**
 * 契约核对（路径/参数与安卓 DeliveryApi.kt 一致；冲突时以后端实现为准）：
 * | 接口 | 安卓 DeliveryApi.kt | 后端 ToolboxDeliveryController | 本域 |
 * |---|---|---|---|
 * | POST  | web/auth/toolbox/delivery/sessions | @PostMapping("/sessions") | ✓ |
 * | GET   | .../sessions/{code} | @GetMapping("/sessions/{code}") | ✓ |
 * | POST  | .../sessions/{code}/close | @PostMapping("/sessions/{code}/close") | ✓ |
 * | POST  | .../sessions/{code}/items body{textContent,title?} | @PostMapping("/sessions/{code}/items") + PublishDeliveryTextFrom | ✓ |
 * | POST  | .../sessions/{code}/items/file multipart(kind,file) | @PostMapping("/sessions/{code}/items/file") @RequestParam kind/file | ✓ |
 * | POST  | .../sessions/{code}/items/{itemId}/take | @PostMapping("/sessions/{code}/items/{itemId}/take") | ✓ |
 * | GET   | .../sessions/{code}/items/{itemId}/content | @GetMapping("/sessions/{code}/items/{itemId}/content") | ✓ |
 * 无冲突。
 */
