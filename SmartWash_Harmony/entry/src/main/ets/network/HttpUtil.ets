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
 * 业务失败（code≠200）的用户可见文案取值策略（对齐安卓 ResponseInterceptor.resolveBusinessErrorMessage，
 * 修复 5125729f）：后端业务失败时信封 message 恒为「失败」，具体语义（如「系统异常，请稍后再试」）
 * 放在 data 字符串里——仅用 message 会把所有业务失败展示成裸「失败」。
 * 取值顺序 = 有具体语义的 data 字符串优先、message 兜底；data 只在**非空字符串**时采用
 * （业务失败时 data 也可能是对象/空串/布尔，直接 toString 会产出「{...}」「null」这类垃圾文案）。
 */
function resolveBusinessErrorMessage(message: string | null | undefined, data: Object | null): string {
  if (typeof data === 'string' && data.trim().length > 0) {
    return data
  }
  return message ?? ""
}

/**
 * 信封解码兜底（差异分析 01 §3.3：code 缺省视为失败，与安卓 ApiResult code/message 非空对齐）：
 * - code 缺省（body 非信封/网关异常页等）：统一按业务失败处理，code=ERROR、data 清空；
 * - 业务失败（201/404/500 等）：优先采用 data 中的具体文案，其次 message，均缺失才补默认文案，
 *   页面 toast 不再弹 "undefined" / 裸「失败」；
 * - 401 已由 Axios 响应拦截器统一处理（清 token → replace 登录页），此处不改写。
 * 统一转译后，页面只需判断 code === 200，失败分支的 message 一定可用。
 *
 * 注：message 的「缺失」含 undefined / null / 空串三态——后端 Jackson 可显式下发 null，
 * 仅判 undefined 会把 null 透传到 toast（单测 HttpUtil.test.ets 覆盖该边界）。
 */
export function decode<T>(raw: BaseResponse<T>): BaseResponse<T> {
  if (raw.code === undefined || raw.code === null) {
    const unknown: BaseResponse<T> = {
      data: null,
      code: BizCode.ERROR,
      message: isBlankMessage(raw.message) ? DEFAULT_UNKNOWN_MESSAGE : raw.message
    }
    return unknown
  }
  if (raw.code !== BizCode.SUCCESS) {
    // as 仅作泛型 → Object 的类型桥接（同 PathStackUtil 对 SDK 返回值的处理），
    // data 具体类型由下方 typeof 运行时校验，不做盲转
    const resolved = resolveBusinessErrorMessage(raw.message, raw.data as Object | null)
    const failed: BaseResponse<T> = {
      data: raw.data,
      code: raw.code,
      message: resolved.length > 0 ? resolved : DEFAULT_FAIL_MESSAGE
    }
    return failed
  }
  return raw
}

/** message 是否为缺失态（undefined / null / 空串） */
function isBlankMessage(message: string | null | undefined): boolean {
  return message === undefined || message === null || message === ""
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
