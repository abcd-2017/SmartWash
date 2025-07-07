import { instance } from './Axios'
import { BaseResponse } from './BaseResponse'

class HttpUtil {
  async get<T>(url: string, params?: Object): Promise<BaseResponse<T>> {
    return instance.get<T, BaseResponse<T>>(url, { params })
  }

  async post<T>(url: string, data?: object): Promise<BaseResponse<T>> {
    return instance.post<T, BaseResponse<T>>(url, data)
  }
}

export const httpUtil = new HttpUtil()