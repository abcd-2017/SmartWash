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
import { JSON } from '@kit.ArkTS'
import { getValue } from '../utils/StoreageUtil'
import { CommonConstants } from '../constant/CommonConstants'
import { pathStack } from '../utils/PathStackUtil'

export const instance: AxiosInstance = axios.create({
  baseURL: "http://8.148.70.81:9000",
  timeout: 100000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8',
  }
})

//请求前拦截器
instance.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  let tokenCache = await getValue(CommonConstants.USER_TOKEN, "") as string
  if (config.url?.startsWith("/web")) {
    if (tokenCache != null && tokenCache != "") {
      config.headers['Authorization'] = `Bearer ${tokenCache}`
    } else {
      //跳转到登录页
      pathStack.pushPathByName("Login", null)
    }
  }
  return config
}, (error: AxiosError) => {
  return Promise.reject(error)
})

//请求后拦截器
instance.interceptors.response.use((response: AxiosResponse) => {
  //登录失效
  logger.debug("debug", JSON.stringify(response.data))
  if (response.data.code == HttpStatusCode.Unauthorized) {
    promptAction.showToast({ message: "登录失效，请重新登录！" })
    logger.warn("warn", JSON.stringify(response.data))
    //跳转到登录页
    pathStack.pushPathByName("Login", null)
  } else if (response.status != HttpStatusCode.Ok) {

  }

  return response.data
}, (error: AxiosError) => {
  if (error.message.indexOf("timeout") !== -1) {
    promptAction.showToast({ message: "请求超时！请您稍后重试" });
  }
  if (error.message.indexOf("Network Error") !== -1) {
    promptAction.showToast({ message: "网络错误！请您稍后重试" });
  }
  let status = error.response?.status
  if (status) {
    checkStatus(status)
  }
  logger.error("error", JSON.stringify(error))
  return Promise.reject(error)
})
