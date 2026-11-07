/**
 * 观象台 BYOK（用户自带模型 Key）设置接口封装 —— #81。
 *
 * 后端契约（SmartWash/src/main/java/com/smartwash/divination/controller/DivinationWebController.java，
 * 前缀 /web/auth/div，需 ROLE_USER；对齐安卓 #79 消费的同一组接口）：
 * - GET    /web/auth/div/settings/api      → Result<UserApiConfigVo>（仅掩码，无明文/密文；未配置时 data=null）
 * - PUT    /web/auth/div/settings/api      body {baseUrl*, model*, apiKey*} → Result<String>（保存前服务端真实试呼）
 * - DELETE /web/auth/div/settings/api      → Result<String>
 * - POST   /web/auth/div/settings/api/test body 同 PUT → Result<Boolean>（试呼，不保存）
 * - GET    /web/auth/div/models            → Result<List<ModelVo>>（平台启用模型目录，BYOK 输入建议用，不限于该目录）
 *
 * 通道：走既有 httpUtil（JSON 信封，非 SSE）。/web/auth 前缀不在 PublicPaths 白名单内，
 * Axios 请求拦截器自动注入 Bearer token，401 语义由 Axios 响应拦截器统一处理（清 token →
 * 清内存登录态 → 切登录根页），本层不重复处理。
 *
 * 敏感红线：API Key 明文只在请求体内出现一次，本文件与调用方均不得将其写入日志或本地存储
 * （key 仅在服务端 AES-GCM 加密落库，端上不持久化）。
 */

import { BaseResponse } from '../../../network/BaseResponse'
import { httpUtil } from '../../../network/HttpUtil'
import { logger } from '../../../utils/Logger'

/** 用户 BYOK 配置 VO（对齐后端 UserApiConfigVo / 安卓 UserApiConfigVo，字段名逐一勿改）。 */
export interface DivUserApiConfigVo {
  /** 配置记录 id（未配置时整包为 null） */
  id?: number
  /** 平台预设供应商显示名（如「平台预设」；自定义接入点时为空） */
  providerName?: string | null
  /** 自定义接入点（https） */
  customBaseUrl?: string | null
  /** 自定义模型名 */
  customModel?: string | null
  /** 掩码（形如 sk-****abc4），仅用于回显 */
  apiKeyMask?: string | null
  /** 试呼验证标记：1 已验证 0 未验证 */
  verified?: number
  /** 配置启用标记：1 启用 0 停用 */
  enabled?: number
  /**
   * 平台级 BYOK 开关（1 开启 0 关闭；后端 additive 追加字段，旧响应缺省）。
   * 端上语义：仅 === 0 视为平台已关闭（入口置灰、不可进编辑层）；
   * 1 / undefined / null（旧响应兼容）均视为开启，行为不变。
   */
  byokEnabled?: number
}

/** 平台模型目录项 VO（对齐后端 ModelVo，字段名逐一勿改）。 */
export interface DivModelVo {
  id?: number
  /** 目录展示名 */
  name?: string
  /** 供应商标识（openai_compat 等） */
  provider?: string
  /** 模型名（填入 BYOK 表单的值） */
  modelId?: string
  /** 优先级，越小越靠前 */
  priority?: number
  /** 当前用户是否正在使用该模型 */
  active?: boolean
}

/** 保存 / 试呼请求体（对齐后端 SaveUserApiFrom，三字段均必填，由服务端 @NotBlank 校验）。 */
export interface SaveDivUserApiForm {
  /** 接入点：必须 https 且非内网地址（服务端 SSRF 校验） */
  baseUrl: string
  /** 模型名 */
  model: string
  /** API Key（明文仅出现在本请求体，禁止落日志/落盘） */
  apiKey: string
}

const SETTINGS_API_URL: string = '/web/auth/div/settings/api'
const SETTINGS_API_TEST_URL: string = '/web/auth/div/settings/api/test'
const MODELS_URL: string = '/web/auth/div/models'

/**
 * 查询当前用户 BYOK 配置（仅掩码）。未配置时 data 为 null。
 * 日志只记动作，不记 baseUrl / model / key（敏感红线）。
 */
export async function getDivUserApiConfig(): Promise<BaseResponse<DivUserApiConfigVo>> {
  return httpUtil.get<DivUserApiConfigVo>(SETTINGS_API_URL)
}

/**
 * 保存 / 更换 BYOK（服务端保存前真实 1-token 试呼，失败即业务失败信封，
 * 具体文案在信封 message / data 中，由 HttpUtil.decode 统一转译）。
 */
export async function saveDivUserApiConfig(form: SaveDivUserApiForm): Promise<BaseResponse<string>> {
  logger.debug('divSettingsApi', '保存 BYOK 配置请求已发出')
  return httpUtil.put<string>(SETTINGS_API_URL, form)
}

/** 删除 BYOK，删除后解读回落平台默认模型。 */
export async function deleteDivUserApiConfig(): Promise<BaseResponse<string>> {
  logger.debug('divSettingsApi', '删除 BYOK 配置请求已发出')
  return httpUtil.delete<string>(SETTINGS_API_URL)
}

/**
 * 连通性试呼（不保存）。code=200 且 data===true 才是连通成功；
 * data===false 表示试呼未通过（key/接入点/模型任一不匹配），文案由页面给出。
 */
export async function testDivUserApiConfig(form: SaveDivUserApiForm): Promise<BaseResponse<boolean>> {
  logger.debug('divSettingsApi', 'BYOK 试呼请求已发出')
  return httpUtil.post<boolean>(SETTINGS_API_TEST_URL, form)
}

/**
 * 平台启用模型目录（BYOK 模型名输入建议；BYOK 不限于该目录，拉取失败仅降级为无建议）。
 */
export async function getDivModels(): Promise<BaseResponse<Array<DivModelVo>>> {
  return httpUtil.get<Array<DivModelVo>>(MODELS_URL)
}
