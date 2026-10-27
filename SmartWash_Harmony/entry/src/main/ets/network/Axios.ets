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
import { CommonConstants } from '../constant/CommonConstants'
import { RouteNames } from '../constant/RouteNames'
import { pathStack } from '../utils/PathStackUtil'
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
  logger.error("BASE_URL 未注入", "release 构建必须在 entry/build-profile.json5 buildOptionSet[release]" +
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

/** 清空本地 token 与内存登录态（401/退出登录统一语义，CLAUDE.md 硬规则） */
async function clearLoginState(): Promise<void> {
  await setValue(CommonConstants.USER_TOKEN, "")
  // role 与 userLoginFlag 同属登录态，一并复位：
  // 只清 token 会残留上一位用户的角色与登录标志，下次登录前读到脏数据
  AppStorage.setOrCreate("userRole", "")
  AppStorage.setOrCreate("userLoginFlag", false)
}

/**
 * 401 统一处理：清 token → 清内存登录态 → replace 到登录页。
 * 使用 replacePathByName 避免压栈；1.5s 时间窗防抖，并发请求同时 401 不重复跳转、不重复压栈。
 * @param toastMessage 提示文案，传空串则不弹 toast（如本地无 token 的静默跳转场景）
 */
function handleUnauthorized(toastMessage: string): void {
  const now = Date.now()
  if (now - lastUnauthorizedAt < UNAUTHORIZED_DEBOUNCE_MS) {
    return
  }
  lastUnauthorizedAt = now
  if (toastMessage !== "") {
    showErrorToast(toastMessage)
  }
  clearLoginState()
  pathStack.replacePathByName(RouteNames.LOGIN, null)
}

export const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=utf-8',
  }
})

//请求前拦截器：/web 前缀接口注入 Bearer Token
instance.interceptors.request.use(async (config: InternalAxiosRequestConfig): Promise<InternalAxiosRequestConfig> => {
  const cached = await getValue(CommonConstants.USER_TOKEN, "")
  const tokenCache = typeof cached === "string" ? cached : ""
  if (config.url?.startsWith("/web")) {
    if (tokenCache !== "") {
      config.headers['Authorization'] = `Bearer ${tokenCache}`
    } else {
      // 本地无 token：复位登录态并替换到登录页（防抖避免重复压栈），本次请求直接失败
      handleUnauthorized("请先登录")
      return Promise.reject(new Error("未登录"))
    }
  }
  return config
}, (error: AxiosError) => {
  return Promise.reject(error)
})

//请求后拦截器：返回完整 AxiosResponse，由 HttpUtil 统一解包业务信封（评审 #11：不做"声明返回 AxiosResponse 实际返回 body"的类型谎言）
instance.interceptors.response.use((response: AxiosResponse): AxiosResponse => {
  // 仅输出状态码等摘要，不打印完整响应体（含手机号/余额等敏感信息，评审 #25）
  logger.debug("响应返回", `status=${response.status}`)
  // 后端统一业务信封：HTTP 200 但业务码 401 同样表示登录失效
  if (response.data?.code === HttpStatusCode.Unauthorized) {
    handleUnauthorized("登录失效，请重新登录！")
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
  logger.error("请求异常", message)
  return Promise.reject(error)
})
