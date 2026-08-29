import { instance } from './Axios'
import { BaseResponse } from './BaseResponse'
import { AxiosResponse } from '@ohos/axios'

/**
 * GET/POST/DELETE/上传 统一封装（单例）。
 * 响应拦截器返回完整 AxiosResponse，此处解包 body 得到业务信封 BaseResponse<T>，
 * 使泛型声明与实际返回一致，消除"声明返回 AxiosResponse 实际返回 body"的类型谎言（评审 #11）。
 */
class HttpUtil {
  async get<T>(url: string, params?: Object): Promise<BaseResponse<T>> {
    const response = await instance.get<T, AxiosResponse<BaseResponse<T>>>(url, { params })
    return response.data
  }

  async post<T>(url: string, data?: object): Promise<BaseResponse<T>> {
    const response = await instance.post<T, AxiosResponse<BaseResponse<T>>>(url, data)
    return response.data
  }

  async delete<T>(url: string): Promise<BaseResponse<T>> {
    const response = await instance.delete<T, AxiosResponse<BaseResponse<T>>>(url)
    return response.data
  }

  // 上传文件（multipart/form-data）
  async upload<T>(url: string, formData: FormData): Promise<BaseResponse<T>> {
    const response = await instance.post<T, AxiosResponse<BaseResponse<T>>>(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    return response.data
  }
}

export const httpUtil = new HttpUtil()