import { instance } from './Axios'
import { BaseResponse } from './BaseResponse'

class HttpUtil {
  async get<T>(url: string, params?: Object): Promise<BaseResponse<T>> {
    return instance.get<T, BaseResponse<T>>(url, { params })
  }

  async post<T>(url: string, data?: object): Promise<BaseResponse<T>> {
    return instance.post<T, BaseResponse<T>>(url, data)
  }

  async delete<T>(url: string): Promise<BaseResponse<T>> {
    return instance.delete<T, BaseResponse<T>>(url)
  }

  // 上传文件（multipart/form-data）
  async upload<T>(url: string, formData: FormData): Promise<BaseResponse<T>> {
    return instance.post<T, BaseResponse<T>>(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  }
}

export const httpUtil = new HttpUtil()