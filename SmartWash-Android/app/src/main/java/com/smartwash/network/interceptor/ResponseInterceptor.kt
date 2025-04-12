package com.smartwash.network.interceptor

import android.util.Log
import com.google.gson.Gson
import com.smartwash.App
import com.smartwash.network.entity.ResponseData
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.utils.HttpStatusCode
import com.smartwash.utils.SharePreferenceUtils
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ResponseInterceptor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var errorMsg = ""
        try {
            //2.根据请求结果判断用户token是否过期
            val response = chain.proceed(chain.request())
            Log.d(AppConstant.APP_NAME, "intercept: $response")

            // 解析服务器返回的 JSON
            val responseBody = response.peekBody(Long.MAX_VALUE).string()
            Log.d(AppConstant.APP_NAME, "intercept: $responseBody")
            val responseData = Gson().fromJson(responseBody, ResponseData::class.java)
            if (responseData.code == HttpStatusCode.Unauthorized.code) {
                SharePreferenceUtils
                App.globalRequestAfterCallback()  // 触发 UI 逻辑（比如跳转登录页）
                errorMsg = "登录失效，请重新登录"
                throw NetworkException("登录失效，请重新登录")
            } else if (responseData.code == HttpStatusCode.Fail.code) {
                errorMsg = responseData.message
                throw NetworkException(responseData.message)
            }
            return response
        } catch (e: SocketTimeoutException) {
            throw NetworkException("请求超时，请检查网络")
        } catch (e: IOException) {
            if (errorMsg.isEmpty()) {
                errorMsg = "网络连接失败，请检查网络"
            }
            throw NetworkException(errorMsg)
        } catch (e: Exception) {
            throw NetworkException("未知错误")
        }
    }
}