import axios, {
  Axios,
  AxiosError,
  AxiosInstance,
  AxiosResponse,
  HttpStatusCode,
  InternalAxiosRequestConfig
} from '@ohos/axios'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { checkStatus } from '../utils/checkStatus'
import { promptAction } from '@kit.ArkUI'
import { logger } from '../utils/Logger'
import { JSON } from '@kit.ArkTS'
import { BaseResponse } from './BaseResponse'
import { getValue } from '../utils/StoreageUtil'
import { CommonConstants } from '../constant/CommonConstants'

export const instance: AxiosInstance = axios.create({
  baseURL: "http://192.168.5.24:8080",
  timeout: 100000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8',
  }
})

//请求前拦截器
instance.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  let tokenCache = await getValue(CommonConstants.USER_TOKEN, "") as string
  if (config.url?.startsWith("/web")) {
    if (tokenCache) {
      config.headers['Authorization'] = `Bearer ${tokenCache}`
    } else {
      //跳转到登录页

    }
  }
  return config
}, (error: AxiosError) => {
  return Promise.reject(error)
})

//请求后拦截器
instance.interceptors.response.use((response: AxiosResponse) => {
  //登录失效
  if (response.data.code == HttpStatusCode.Unauthorized) {
    promptAction.showToast({ message: "登录失效，请重新登录！" })
    logger.warn("warn", JSON.stringify(response.data))
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
