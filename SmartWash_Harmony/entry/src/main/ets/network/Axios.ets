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
  isBusinessFailureCode,
  isUnauthorizedCode,
  isPublicRequest,
  resolveToken,
  shouldInjectToken
} from './RequestRules'
import { CommonConstants } from '../constant/CommonConstants'
import BuildProfile from 'entry/BuildProfile'

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
    "release 构建必须在 entry/build-profile.json5 buildOptionSet[release]" +
    ".arkOptions.buildProfileFields 注入生产 HTTPS 域名后重新出包")
}

// 请求超时（毫秒）：与 Android 端 30s 基准对齐（评审 #9 / 跨端 #3），原 100s 过长
const REQUEST_TIMEOUT: number = 30000

// 401 防抖时间窗（毫秒）：并发请求同时 401 时，窗口期内只执行一次跳转/清理
const UNAUTHORIZED_DEBOUNCE_MS: number = 1500

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
}

/** 登录成功置会话态（Index 据此把 Home 作为根内容渲染） */
export function markLoggedIn(): void {
  AppStorage.setOrCreate("isLoggedIn", true)
}

/**
 * 401 统一处理：清 token → 清内存登录态 → 切回登录根页（经 isLoggedIn 会话态，不再压栈）。
 * 1.5s 时间窗防抖；已处于未登录态时静默返回，避免轮询/并发请求重复弹 toast、重复跳页。
 * @param toastMessage 提示文案，传空串则不弹 toast（如本地无 token 的静默跳转场景）
 */
function handleUnauthorized(toastMessage: string): void {
  // 已确认未登录：静默拒绝。否则 Home 里仍在跑的轮询会持续触发本函数，
  // 在用户输入手机号时反复弹 toast / 替换登录页（问题 2）
  if (AppStorage.get<boolean>("isLoggedIn") === false) {
    return
  }
  const now = Date.now()
  if (now - lastUnauthorizedAt < UNAUTHORIZED_DEBOUNCE_MS) {
    return
  }
  lastUnauthorizedAt = now
  if (toastMessage !== "") {
    showErrorToast(toastMessage)
  }
  clearLoginState()
}

export const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=utf-8',
  }
})

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
        handleUnauthorized("请先登录")
      }
      return Promise.reject(new Error("未登录"))
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
instance.interceptors.response.use((response: AxiosResponse): AxiosResponse => {
  // 仅输出状态码等摘要，不打印完整响应体（含手机号/余额等敏感信息，评审 #25）
  logger.debug("axios", `响应返回 status=${response.status}`)
  // 后端统一业务信封：HTTP 200 但业务码 401 同样表示登录失效
  if (isUnauthorizedCode(response.data?.code)) {
    handleUnauthorized("登录失效，请重新登录！")
  } else if (isBusinessFailureCode(response.data?.code)) {
    // HTTP 200 但业务码非 200/401（如 201/404/500）：此前静默放行，此处补 warn 留痕
    //（401 已由 handleUnauthorized 统一处理，不再重复记录）。
    // 仅输出 url + 业务码 + message 摘要，不打印完整响应体（敏感红线，评审 #25）
    logger.warn("axios", `业务失败响应 url=${response.config.url ?? ""} ` +
      `code=${response.data?.code} message=${response.data?.message ?? ""}`)
  }
  return response
}, (error: AxiosError) => {
  const message = error.message ?? ""
  if (message.indexOf("timeout") !== -1) {
    showErrorToast("请求超时！请您稍后重试")
  }
  if (message.indexOf("Network Error") !== -1) {
    showErrorToast("网络错误！请您稍后重试")
  }
  const status = error.response?.status
  if (status !== undefined) {
    if (status === HttpStatusCode.Unauthorized) {
      // HTTP 层 401 与业务码 401 同语义：清 token → 复位登录态 → replace 到登录页
      handleUnauthorized("登录失效，请重新登录！")
    } else {
      checkStatus(status)
    }
  }
  // 仅记录错误摘要，不序列化完整 error（可能携带请求体/手机号等敏感信息，评审 #25）
  logger.error("axios", "请求异常", message)
  return Promise.reject(error)
})
