import axios, {
  AxiosError,
  AxiosInstance,
  AxiosResponse,
  HttpStatusCode,
  InternalAxiosRequestConfig
} from '@ohos/axios'
import { checkStatus } from '../utils/CheckStatus'
import { promptAction } from '@kit.ArkUI'
import { logger } from '../utils/Logger'
import { getValue, setValue } from '../utils/StorageUtil'
import {
  buildAuthorizationHeader,
  BUSINESS_SUCCESS_CODE,
  isAuthEndpoint,
  isBusinessFailureCode,
  isUnauthorizedCode,
  isPublicRequest,
  resolveToken,
  RETRY_MARKER_HEADER,
  RETRY_MARKER_VALUE,
  shouldInjectToken
} from './RequestRules'
import { CommonConstants } from '../constant/CommonConstants'
import { BaseResponse } from './BaseResponse'
import BuildProfile from 'entry/BuildProfile'
import { resString } from '../utils/ResourceString'

/**
 * 服务端地址配置（差异分析 01 §3.3 环境红线修复）：
 * - BASE_URL 由 hvigor 构建期经 entry/build-profile.json5 的
 *   buildOption(Set).arkOptions.buildProfileFields 注入，代码零硬编码（对齐安卓 BuildConfig 机制）；
 * - debug：演示服务器兜底（明文 HTTP，仅限内网联调，禁止随生产发布）；
 * - release：默认空串（未注入即不可用），发布前必须在构建配置注入生产 HTTPS 域名，
 *   环境切换步骤见 entry/build-profile.json5 内注释。
 */
const BASE_URL: string = BuildProfile.BASE_URL

if (BASE_URL === "") {
  logger.error("axios", "BASE_URL 未注入",
    resString('axios_1') +
    resString('axios_2'))
}

// 请求超时（毫秒）：与 Android 端 30s 基准对齐（评审 #9 / 跨端 #3），原 100s 过长
const REQUEST_TIMEOUT: number = 30000

// 401 防抖时间窗（毫秒）：并发请求同时 401 时，窗口期内只执行一次跳转/清理
const UNAUTHORIZED_DEBOUNCE_MS: number = 1500

// 401 统一文案（业务码 401 / HTTP 401 / 续签失败三处合流，避免文案漂移）
function UNAUTHORIZED_MESSAGE(): string { return resString('axios_3') }

// 错误类 toast 去重窗口（毫秒）：轮询等高频请求持续失败时，相同文案不重复弹（评审 #4）
const ERROR_TOAST_DEDUPE_MS: number = 10000

// 上次 401 处理时间戳（防抖）
let lastUnauthorizedAt: number = 0
// 上次错误 toast 的时间与文案（去重）
let lastErrorToastAt: number = 0
let lastErrorToastMsg: string = ""

/**
 * 带去重窗口的错误 toast：相同文案在窗口期内只弹一次。
 * 供超时/网络错误等高频失败场景使用，避免轮询期间每 5s 弹一次。
 */
function showErrorToast(message: string): void {
  const now = Date.now()
  if (message === lastErrorToastMsg && now - lastErrorToastAt < ERROR_TOAST_DEDUPE_MS) {
    return
  }
  lastErrorToastAt = now
  lastErrorToastMsg = message
  promptAction.showToast({ message: message })
}

/**
 * 清空本地 token 与内存登录态（401/退出登录统一语义，CLAUDE.md 硬规则）。
 *
 * 会话态 isLoggedIn 是**唯一**的登录根页切换信号：Index 监听它，把 Login 作为
 * Navigation 根内容渲染并清空页面栈 —— Home 随之销毁，其 5s 订单轮询停止。
 * 不再使用 pushPathByName/replacePathByName(LOGIN)：那种写法会把 Login 压到
 * 仍然活着的 Home 之上，导致侧滑能回首页、重登后停在旧 tab、轮询持续 401、
 * 反复 replace 掉用户正在输入的登录页（输入法被关 + "请先登录" toast）。
 */
export async function clearLoginState(): Promise<void> {
  // 先置会话态，保证 UI 立即切回登录根页（不等待 preferences 落盘）
  AppStorage.setOrCreate("userRole", "")
  AppStorage.setOrCreate("userLoginFlag", false)
  AppStorage.setOrCreate("isLoggedIn", false)
  await setValue(CommonConstants.USER_TOKEN, "")
  // 双 token 语义（对齐 ChangePassword 手动登出）：refreshToken 一并清除，
  // 防止残留 refreshToken 在后续会话被误用于续签（refreshToken 一次性，残留即重放风险）
  await setValue(CommonConstants.USER_REFRESH_TOKEN, "")
}

/** 登录成功置会话态（Index 据此把 Home 作为根内容渲染） */
export function markLoggedIn(): void {
  AppStorage.setOrCreate("isLoggedIn", true)
}

/**
 * 401 防抖窗口认领（导出供 DivSseClient 共享同一时间戳）：窗口期内首个调用方
 * 记录时间戳并返回 true，窗口内的后续调用返回 false —— SSE 通道与 axios 通道
 * 同刻 401 时只执行一次「清登录态 + toast」，避免双 toast / 双清理。
 * @returns true = 本次调用负责执行 401 收尾；false = 窗口内已有调用方处理
 */
export function tryClaimUnauthorizedDebounce(): boolean {
  const now = Date.now()
  if (now - lastUnauthorizedAt < UNAUTHORIZED_DEBOUNCE_MS) {
    return false
  }
  lastUnauthorizedAt = now
  return true
}

/**
 * 401 统一处理：清 token → 清内存登录态 → 切回登录根页（经 isLoggedIn 会话态，不再压栈）。
 * 1.5s 时间窗防抖（tryClaimUnauthorizedDebounce，SSE 通道共享同一时间戳）；
 * 已处于未登录态时静默返回，避免轮询/并发请求重复弹 toast、重复跳页。
 * @param toastMessage 提示文案，传空串则不弹 toast（如本地无 token 的静默跳转场景）
 */
function handleUnauthorized(toastMessage: string): void {
  // 已确认未登录：静默拒绝。否则 Home 里仍在跑的轮询会持续触发本函数，
  // 在用户输入手机号时反复弹 toast / 替换登录页（问题 2）
  if (AppStorage.get<boolean>("isLoggedIn") === false) {
    return
  }
  if (!tryClaimUnauthorizedDebounce()) {
    return
  }
  if (toastMessage !== "") {
    showErrorToast(toastMessage)
  }
  clearLoginState()
}

// ========== 双 token 静默续签（JWT 双 token 消费侧） ==========

/**
 * 后端契约（LoginController#refresh，已定稿）：
 * - POST /auth/refresh（公开接口，/auth/** 在 PublicPaths 白名单内），body { refreshToken }；
 * - 成功：Result<{ token, refreshToken }>（无 role）；任何失败：HTTP 200 + 业务码 401 信封。
 *
 * 机制：
 * - 单飞：并发多个 401 共享同一个 in-flight 刷新 Promise，只发一次 /auth/refresh
 *   （refreshToken 一次性轮换，并发重放会被后端黑名单 401）；新 token 在 Promise
 *   兑现前落盘，保证等待同一单飞的请求重试时必然读到新 token；
 * - 重试：刷新成功后把原请求重发一次，重试结果直接返回调用方，业务无感知；
 * - 防递归：刷新走无拦截器的裸 Axios 实例（等价 PublicPaths 公开通道），刷新响应的
 *   401 信封就地转译为失败，不经过 instance 响应拦截器；重发请求携带标记头，
 *   再次 401 不再续签，直接走 handleUnauthorized；
 * - DivSseClient 兼容：续签失败与原 401 路径合流，仍经 handleUnauthorized 的
 *   1.5s 防抖窗（tryClaimUnauthorizedDebounce），SSE 通道共享时间戳语义不变。
 */

/** 刷新端点（公开接口） */
const REFRESH_ENDPOINT: string = "/auth/refresh"

/** 刷新请求体（后端 RefreshTokenFrom） */
interface RefreshTokenRequest {
  refreshToken: string
}

/** 刷新响应载荷（后端 TokenPairVo：一次性轮换后的新双 token，无 role） */
interface TokenPairVo {
  token: string
  refreshToken: string
}

/**
 * 续签专用通道：无任何拦截器的裸 Axios 实例（等价 PublicPaths 公开通道）。
 * 不复用 instance —— 刷新响应的 401 信封必须在此就地转译为失败，不经过
 * instance 的响应拦截器，从结构上杜绝「刷新失败 → 再次 401 → 再次刷新/登出」递归。
 */
const refreshInstance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=utf-8',
  }
})

/** 单飞 in-flight 刷新 Promise：null = 无进行中的刷新；兑现值 true = 刷新并落盘成功 */
let refreshInFlight: Promise<boolean> | null = null

/**
 * 单飞刷新入口：并发 401 复用同一 Promise；完成（含落盘）后窗口重开。
 */
function refreshTokens(): Promise<boolean> {
  if (refreshInFlight !== null) {
    return refreshInFlight
  }
  const inFlight = doRefreshTokens().finally(() => {
    refreshInFlight = null
  })
  refreshInFlight = inFlight
  return inFlight
}

/**
 * 执行一次 /auth/refresh 并落盘新双 token（落盘先于 Promise 兑现）。
 * 任何失败（无 refreshToken / 业务码非 200 / 存储与传输层异常）一律返回 false、
 * 永不 reject —— 单飞 Promise 的兑现值就是唯一事实源，调用方无需另行兜底。
 */
async function doRefreshTokens(): Promise<boolean> {
  try {
    const cached = await getValue(CommonConstants.USER_REFRESH_TOKEN, "")
    const refreshToken = resolveToken(cached)
    if (refreshToken === "") {
      // 无 refreshToken：未登录语义，交由调用方直接走 handleUnauthorized 收尾
      return false
    }
    const body: RefreshTokenRequest = { refreshToken: refreshToken }
    const response: AxiosResponse<BaseResponse<TokenPairVo>> =
      await refreshInstance.post<TokenPairVo, AxiosResponse<BaseResponse<TokenPairVo>>>(
        REFRESH_ENDPOINT, body)
    // 刷新端点任何失败都是业务码 401 信封（HTTP 200 携带），非 200 一律按失败处理
    if (response.data.code !== BUSINESS_SUCCESS_CODE) {
      logger.warn("axios", "token 续签失败（业务码非 200）")
      return false
    }
    const token = response.data.data?.token ?? ""
    const newRefreshToken = response.data.data?.refreshToken ?? ""
    if (token === "" || newRefreshToken === "") {
      logger.warn("axios", "token 续签响应缺失双 token 字段")
      return false
    }
    // 刷新期间可能已被登出（SSE 通道 401 防抖收尾 / 手动退出）：丢弃结果，
    // 避免登出后又把新 token 写回本地，造成「已退出仍带有效凭据」的幽灵会话
    if (AppStorage.get<boolean>("isLoggedIn") !== true) {
      return false
    }
    await setValue(CommonConstants.USER_TOKEN, token)
    await setValue(CommonConstants.USER_REFRESH_TOKEN, newRefreshToken)
    // 低频关键动作成功：info 级（token 本身不落日志，敏感红线）
    logger.info("axios", "token 续签成功")
    return true
  } catch (err) {
    // 网络/超时等传输层失败同样按续签失败处理（复用 401 收尾，不额外弹 toast 避免双弹）
    logger.error("axios", "token 续签请求异常", err instanceof Error ? err.message : "")
    return false
  }
}

export const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=utf-8',
  }
})

// ========== 401 静默续签重试（业务码 401 与 HTTP 401 两通道共用） ==========

/**
 * 该 401 请求是否具备静默续签资格：
 * - 会话态必须仍为已登录（未登录时 handleUnauthorized 自带静默语义，直接收尾不续签）；
 * - 已重发过的请求不再续签（重发标记头判定，防「新 token 仍被拒 → 无限刷新」循环）；
 * - /auth/** 认证端点自身（登录/注册/验证码/刷新/登出）的 401 不续签——登录失败不得触发续签；
 * - 仅业务认证接口（/web 前缀、需注入 token 的请求）才续签（公开只读接口不注入 token，无续签语义）。
 */
function isRefreshRetryable(config: InternalAxiosRequestConfig): boolean {
  if (AppStorage.get<boolean>("isLoggedIn") !== true) {
    return false
  }
  if (config.headers.get(RETRY_MARKER_HEADER) === RETRY_MARKER_VALUE) {
    return false
  }
  if (isAuthEndpoint(config.url)) {
    return false
  }
  return shouldInjectToken(config.url)
}

/**
 * 重发前还原请求体：dispatchRequest 已把 config.data 原地序列化（对象 → JSON 字符串），
 * 不还原直接重发会被 transformRequest 二次 stringify（双重编码）。
 * FormData / ArrayBuffer 等非字符串载荷 transformRequest 原样透传，无需处理。
 * @returns false = 字符串请求体无法反序列化（不可重放），调用方放弃重试
 */
function restoreReplayableBody(config: InternalAxiosRequestConfig): boolean {
  // 显式标注：axios 的 data 声明为 any，直接 const 推断触发 arkts-no-any-unknown
  const data: Object | null = config.data
  if (typeof data === "string") {
    try {
      config.data = JSON.parse(data)
      return true
    } catch (err) {
      return false
    }
  }
  return true
}

/**
 * 单飞续签 + 原请求重发一次（带标记头）。重发请求完整走 instance 拦截器链，
 * 请求拦截器自动注入落盘后的新 token，url/method/params/headers 原样重放。
 * @returns 重发响应（成功/失败语义与普通请求一致，直接交回调用方）；
 *          null = 刷新失败或请求体不可重放（调用方按既有 401 语义收尾）
 */
async function refreshAndRetry(config: InternalAxiosRequestConfig): Promise<AxiosResponse | null> {
  const refreshed = await refreshTokens()
  if (!refreshed) {
    return null
  }
  if (!restoreReplayableBody(config)) {
    logger.warn("axios", "请求体不可重放，放弃续签重试")
    return null
  }
  config.headers.set(RETRY_MARKER_HEADER, RETRY_MARKER_VALUE)
  // 显式泛型：request 默认泛型含 any，触发 arkts-no-any-unknown（对齐 HttpUtil 的调用惯例）
  return await instance.request<Object, AxiosResponse>(config)
}

/**
 * 业务码 401（HTTP 200 信封）统一入口：可续签则重发并返回重试结果（业务无感知）；
 * 否则按原语义收尾（handleUnauthorized 防抖 + 返回 401 信封响应，HttpUtil.decode 照常转译）。
 */
async function handleUnauthorizedResponse(response: AxiosResponse): Promise<AxiosResponse> {
  if (isRefreshRetryable(response.config)) {
    const retried: AxiosResponse | null = await refreshAndRetry(response.config)
    if (retried !== null) {
      return retried
    }
  }
  handleUnauthorized(UNAUTHORIZED_MESSAGE())
  return response
}

/**
 * HTTP 401 统一入口：与业务码 401 行为一致（可续签则重发）；不可续签时按原语义收尾
 * （handleUnauthorized 防抖 + 以原 error reject，保持既有失败传播路径）。
 */
async function handleUnauthorizedError(error: AxiosError): Promise<AxiosResponse> {
  // 显式标注：AxiosError.config 泛型默认 any，直接 const 推断触发 arkts-no-any-unknown
  const config: InternalAxiosRequestConfig | undefined = error.config
  if (config !== undefined && isRefreshRetryable(config)) {
    const retried: AxiosResponse | null = await refreshAndRetry(config)
    if (retried !== null) {
      return retried
    }
  }
  handleUnauthorized(UNAUTHORIZED_MESSAGE())
  // 仅记录错误摘要，不序列化完整 error（可能携带请求体/手机号等敏感信息，评审 #25）
  logger.error("axios", "请求异常", error.message ?? "")
  return Promise.reject(error)
}

//请求前拦截器：/web 前缀接口注入 Bearer Token（公开路径白名单除外，见 PublicPaths）
instance.interceptors.request.use(async (config: InternalAxiosRequestConfig): Promise<InternalAxiosRequestConfig> => {
  // 公开接口（后端 SecurityConfig permitAll 段，清单见 PublicPaths）：不带 token，
  // 也不因本地无 token 而拦截——未登录用户同样能拉取启动配置、洗衣项目列表等只读数据。
  // 语义对齐 Android：@RequireAuthorization 未标注的方法不注入 token。
  if (isPublicRequest(config.url)) {
    return config
  }
  const cached = await getValue(CommonConstants.USER_TOKEN, "")
  const tokenCache = resolveToken(cached)
  if (shouldInjectToken(config.url)) {
    if (tokenCache !== "") {
      config.headers['Authorization'] = buildAuthorizationHeader(tokenCache)
    } else {
      // 本地无 token：仅在「会话态仍为已登录」（token 异常丢失）时才按 401 处理；
      // 已确认未登录（退出登录 / 首次 401 之后）静默拒绝，避免轮询持续弹 toast、
      // 重复跳页替换掉用户正在输入的登录页（问题 2）
      if (AppStorage.get<boolean>("isLoggedIn") === true) {
        handleUnauthorized(resString('axios_4'))
      }
      return Promise.reject(new Error(resString('axios_5')))
    }
  }
  return config
}, (error: AxiosError) => {
  // 请求阶段拦截器失败（如 header 组装异常）：补 error 留痕后原样 reject，不改失败语义。
  // 仅记 error.message 摘要，不序列化完整 error（可能携带请求体/手机号等敏感信息，评审 #25）
  logger.error("axios", "请求拦截器异常", error.message ?? "")
  return Promise.reject(error)
})

//请求后拦截器：返回完整 AxiosResponse，由 HttpUtil 统一解包业务信封（评审 #11：不做"声明返回 AxiosResponse 实际返回 body"的类型谎言）
instance.interceptors.response.use((response: AxiosResponse): AxiosResponse | Promise<AxiosResponse> => {
  // 仅输出状态码等摘要，不打印完整响应体（含手机号/余额等敏感信息，评审 #25）
  logger.debug("axios", `响应返回 status=${response.status}`)
  // 后端统一业务信封：HTTP 200 但业务码 401 同样表示登录失效（先尝试静默续签并重发原请求）
  if (isUnauthorizedCode(response.data?.code)) {
    return handleUnauthorizedResponse(response)
  } else if (isBusinessFailureCode(response.data?.code)) {
    // HTTP 200 但业务码非 200/401（如 201/404/500）：此前静默放行，此处补 warn 留痕
    //（401 已由 handleUnauthorized 统一处理，不再重复记录）。
    // 仅输出 url + 业务码 + message 摘要，不打印完整响应体（敏感红线，评审 #25）
    logger.warn("axios", `业务失败响应 url=${response.config.url ?? ""} ` +
      `code=${response.data?.code} message=${response.data?.message ?? ""}`)
  }
  return response
}, (error: AxiosError): Promise<AxiosResponse> => {
  const message = error.message ?? ""
  if (message.indexOf("timeout") !== -1) {
    showErrorToast(resString('axios_6'))
  }
  if (message.indexOf("Network Error") !== -1) {
    showErrorToast(resString('axios_7'))
  }
  const status = error.response?.status
  if (status !== undefined) {
    if (status === HttpStatusCode.Unauthorized) {
      // HTTP 层 401 与业务码 401 同语义：先尝试静默续签并重发原请求一次；
      // 不可续签时清 token → 复位登录态 → 切登录根页（含防抖），随后按原语义 reject
      return handleUnauthorizedError(error)
    } else {
      checkStatus(status)
    }
  }
  // 仅记录错误摘要，不序列化完整 error（可能携带请求体/手机号等敏感信息，评审 #25）
  logger.error("axios", "请求异常", message)
  return Promise.reject(error)
})
