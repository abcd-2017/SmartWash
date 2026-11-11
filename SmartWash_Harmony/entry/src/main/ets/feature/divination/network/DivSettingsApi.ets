/**
 * 观象台解读模型设置接口封装 —— #81，观星台模型配置链路重构后契约。
 *
 * 后端契约（SmartWash/src/main/java/com/smartwash/divination/controller/DivinationWebController.java，
 * 前缀 /web/auth/div，需 ROLE_USER；对齐安卓同域消费的同一组接口）：
 * - GET    /web/auth/div/settings/api      → Result<UserApiConfigVo|null>（仅掩码，无明文/密文；
 *                                            未配置时 data=null）
 * - PUT    /web/auth/div/settings/api      双模式二选一（apiKey 均必填，服务端试呼通过才落库，
 *                                            AES-GCM 加密，apiKey 只写不读）：
 *                                            目录模式 body {modelConfigId*, apiKey*}
 *                                            （接入点/模型名服务端从目录取，用户不输 url）
 *                                            自定义模式 body {baseUrl*, model*, apiKey*}
 * - DELETE /web/auth/div/settings/api      → Result<String>（删除后需重新配置模型才能 AI 解读，
 *                                            平台零兜底、无平台默认模型）
 * - POST   /web/auth/div/settings/api/test 请求体同 PUT（双模式）→ Result<Boolean>（试呼，不保存）
 * - GET    /web/auth/div/models            → Result<List<DivVendorGroupVo>>（平台启用模型目录，
 *                                            树形一次返回整棵目录：vendor 一级 + models 二级，
 *                                            目录模式分步选择数据源：第一步选厂商、第二步选模型）
 *
 * 观星台零兜底口径：用户未配置模型时解读一律 SSE error 帧 code=428，无平台默认模型；
 * 平台总开关关闭（divUseEnabled=0）时解读 SSE error 帧 code=403（message 透传「暂未开放」）。
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

/** 用户解读模型配置 VO（对齐后端 UserApiConfigVo，字段名逐一勿改）。 */
export interface DivUserApiConfigVo {
  /** 配置记录 id（未配置时整包为 null） */
  id?: number
  /** 目录模式：模型目录条目 id（div_model_config）；自定义模式为 null */
  modelConfigId?: number | null
  /** 目录模式：厂商名（一级分组显示名）；自定义模式为 null */
  vendor?: string | null
  /** 目录模式 = 目录显示名；自定义模式 = customModel */
  modelName?: string | null
  /** 自定义接入点（https）；目录模式为 null */
  customBaseUrl?: string | null
  /** 自定义模型名；目录模式为 null */
  customModel?: string | null
  /** 掩码（形如 sk-****abc4），仅用于回显 */
  apiKeyMask?: string | null
  /** 试呼验证标记：1 已验证 0 未验证 */
  verified?: number
  /** 配置启用标记：1 启用 0 停用 */
  enabled?: number
  /**
   * 观星台 AI 解读总开关（1 开启 0 关闭；原 byokEnabled 字段已更名，语义同步变为
   * 「AI 解读总开关」而非「自带模型开关」）。
   * 端上语义：仅 === 0 视为平台已关闭（入口置灰、解读入口拦截）；
   * 1 / undefined / null（旧响应兼容）均视为开启，行为不变。
   */
  divUseEnabled?: number
}

/**
 * 平台模型目录项 VO（对齐后端 DivModelVo，字段名逐一勿改）。
 * 厂商 / 供应商标识 / 接入点已上移到 DivVendorGroupVo，本 VO 只描述单个模型条目。
 */
export interface DivModelVo {
  id?: number
  /** 目录展示名 */
  name?: string
  /** 模型名（目录条目实际接入的模型，服务端取用） */
  modelId?: string
  /** 优先级，越小越靠前 */
  priority?: number
  /** 当前用户是否正在使用该模型 */
  active?: boolean
}

/**
 * 平台模型目录厂商分组 VO（对齐后端 DivVendorGroupVo，字段名逐一勿改）。
 * 目录模式分步选择数据源：vendor 一级 + 该厂商 models 二级（整棵树一次返回，第二步本地过滤）。
 */
export interface DivVendorGroupVo {
  /** 厂商名（一级分组显示名） */
  vendor?: string
  /** 供应商标识（openai_compat 等） */
  provider?: string
  /** OpenAI 兼容接入点（目录模式由服务端按条目取用，端上仅作厂商次要信息展示） */
  baseUrl?: string
  /** 优先级，越小越靠前 */
  priority?: number
  /** 该厂商下的模型条目（二级） */
  models: Array<DivModelVo>
}

/**
 * 保存 / 试呼请求体（对齐后端 SaveUserApiFrom，双模式二选一；apiKey 均必填）。
 * 目录模式只传 modelConfigId + apiKey；自定义模式只传 baseUrl + model + apiKey。
 */
export interface SaveDivUserApiForm {
  /** 目录模式：平台模型目录条目 id（与 baseUrl/model 互斥） */
  modelConfigId?: number
  /** 自定义模式：接入点，必须 https 且非内网地址（服务端 SSRF 校验） */
  baseUrl?: string
  /** 自定义模式：模型名 */
  model?: string
  /** API Key（两种模式均必填；明文仅出现在本请求体，禁止落日志/落盘） */
  apiKey: string
}

const SETTINGS_API_URL: string = '/web/auth/div/settings/api'
const SETTINGS_API_TEST_URL: string = '/web/auth/div/settings/api/test'
const MODELS_URL: string = '/web/auth/div/models'

/**
 * 查询当前用户解读模型配置（仅掩码）。未配置时 data 为 null。
 * 日志只记动作，不记 baseUrl / model / key（敏感红线）。
 */
export async function getDivUserApiConfig(): Promise<BaseResponse<DivUserApiConfigVo>> {
  return httpUtil.get<DivUserApiConfigVo>(SETTINGS_API_URL)
}

/**
 * 保存 / 更换解读模型配置（服务端保存前真实 1-token 试呼，失败即业务失败信封，
 * 具体文案在信封 message / data 中，由 HttpUtil.decode 统一转译）。
 */
export async function saveDivUserApiConfig(form: SaveDivUserApiForm): Promise<BaseResponse<string>> {
  logger.debug('divSettingsApi', '保存解读模型配置请求已发出')
  return httpUtil.put<string>(SETTINGS_API_URL, form)
}

/** 删除解读模型配置（平台零兜底：删除后需重新配置模型才能使用 AI 解读）。 */
export async function deleteDivUserApiConfig(): Promise<BaseResponse<string>> {
  logger.debug('divSettingsApi', '删除解读模型配置请求已发出')
  return httpUtil.delete<string>(SETTINGS_API_URL)
}

/**
 * 连通性试呼（不保存，请求体同 PUT 双模式）。code=200 且 data===true 才是连通成功；
 * data===false 表示试呼未通过（key/接入点/模型任一不匹配），文案由页面给出。
 */
export async function testDivUserApiConfig(form: SaveDivUserApiForm): Promise<BaseResponse<boolean>> {
  logger.debug('divSettingsApi', '解读模型试呼请求已发出')
  return httpUtil.post<boolean>(SETTINGS_API_TEST_URL, form)
}

/**
 * 平台启用模型目录（树形整包返回：厂商分组一级 + 该厂商模型条目二级；
 * 目录模式分步选择数据源，第二步在树内本地过滤，无二次请求；自定义模式仅作模型名建议）。
 */
export async function getDivModels(): Promise<BaseResponse<Array<DivVendorGroupVo>>> {
  return httpUtil.get<Array<DivVendorGroupVo>>(MODELS_URL)
}
