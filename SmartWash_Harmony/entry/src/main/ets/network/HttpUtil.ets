import { instance } from './Axios'
import { BaseResponse } from './BaseResponse'
import { AxiosResponse, FormData } from '@ohos/axios'

/**
 * 业务信封状态码（对齐 Android common/utils/model/HttpStatusCode.kt）。
 * 业务语义看信封 code，HTTP 状态码只反映传输层。
 */
export class BizCode {
  static readonly SUCCESS: number = 200
  static readonly FAIL: number = 201
  static readonly UNAUTHORIZED: number = 401
  static readonly NOT_FOUND: number = 404
  static readonly ERROR: number = 500
}

// 业务失败兜底文案：后端 message 缺失/空串时使用（对齐安卓 error_network_fail 文案）
const DEFAULT_FAIL_MESSAGE: string = "请求失败"
// 信封异常兜底文案：body 非信封结构（code 缺省）时使用（对齐安卓 error_unknown 文案）
const DEFAULT_UNKNOWN_MESSAGE: string = "未知错误"

/**
 * 信封解码兜底（差异分析 01 §3.3：code 缺省视为失败，与安卓 ApiResult code/message 非空对齐）：
 * - code 缺省（body 非信封/网关异常页等）：统一按业务失败处理，code=ERROR、data 清空；
 * - 业务失败（201/404/500 等）且 message 缺失：补默认文案，页面 toast 不再弹 "undefined"；
 * - 401 已由 Axios 响应拦截器统一处理（清 token → replace 登录页），此处不改写。
 * 统一转译后，页面只需判断 code === 200，失败分支的 message 一定可用。
 */
function decode<T>(raw: BaseResponse<T>): BaseResponse<T> {
  if (raw.code === undefined || raw.code === null) {
    const unknown: BaseResponse<T> = {
      data: null,
      code: BizCode.ERROR,
      message: raw.message !== undefined && raw.message !== "" ? raw.message : DEFAULT_UNKNOWN_MESSAGE
    }
    return unknown
  }
  if (raw.code !== BizCode.SUCCESS &&
    (raw.message === undefined || raw.message === "")) {
    const failed: BaseResponse<T> = {
      data: raw.data,
      code: raw.code,
      message: DEFAULT_FAIL_MESSAGE
    }
    return failed
  }
  return raw
}

/**
 * GET/POST/DELETE/上传 统一封装（单例）。
 * 响应拦截器返回完整 AxiosResponse，此处解包 body 得到业务信封 BaseResponse<T>，
 * 经 decode 统一转译业务失败（含 201 Fail）后返回，
 * 使泛型声明与实际返回一致，消除"声明返回 AxiosResponse 实际返回 body"的类型谎言（评审 #11）。
 *
 * ArkTS 约束：
 * - response 需显式标注类型（await 后的推断类型含 any，触发 arkts-no-any-unknown 编译错误）；
 * - FormData 使用 @ohos/axios 导出的 FormData（ArkTS 无浏览器 DOM 全局 FormData）。
 */
class HttpUtil {
  async get<T>(url: string, params?: Object): Promise<BaseResponse<T>> {
    const response: AxiosResponse<BaseResponse<T>> =
      await instance.get<T, AxiosResponse<BaseResponse<T>>>(url, { params })
    return decode<T>(response.data)
  }

  async post<T>(url: string, data?: object): Promise<BaseResponse<T>> {
    const response: AxiosResponse<BaseResponse<T>> =
      await instance.post<T, AxiosResponse<BaseResponse<T>>>(url, data)
    return decode<T>(response.data)
  }

  // PUT：短链续期/改向（/web/auth/toolbox/short-codes/{id}）使用，与 post 同构
  async put<T>(url: string, data?: object): Promise<BaseResponse<T>> {
    const response: AxiosResponse<BaseResponse<T>> =
      await instance.put<T, AxiosResponse<BaseResponse<T>>>(url, data)
    return decode<T>(response.data)
  }

  async delete<T>(url: string): Promise<BaseResponse<T>> {
    const response: AxiosResponse<BaseResponse<T>> =
      await instance.delete<T, AxiosResponse<BaseResponse<T>>>(url)
    return decode<T>(response.data)
  }

  // 上传文件（multipart/form-data）
  async upload<T>(url: string, formData: FormData): Promise<BaseResponse<T>> {
    const response: AxiosResponse<BaseResponse<T>> =
      await instance.post<T, AxiosResponse<BaseResponse<T>>>(url, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
    return decode<T>(response.data)
  }
}

export const httpUtil = new HttpUtil()
