package com.smartwash.network.interceptor

import android.util.Log
import com.google.gson.Gson
import com.smartwash.App
import com.smartwash.R
import com.smartwash.network.entity.ResponseData
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.utils.HttpStatusCode
import com.smartwash.utils.SharePreferenceUtils
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        try {
            val response = chain.proceed(request)
            if (!response.isSuccessful) {
                Log.w(AppConstant.APP_NAME, "Response: ${request.method} ${request.url} — HTTP ${response.code}")
                throw NetworkException("请求失败", R.string.error_network_fail)
            }

            val bodyString = response.peekBody(Long.MAX_VALUE).string()
            val responseData = Gson().fromJson(bodyString, ResponseData::class.java)

            when (responseData.code) {
                HttpStatusCode.Unauthorized.code -> {
                    Log.w(AppConstant.APP_NAME, "Response: ${request.method} ${request.url} — 登录失效(401)")
                    SharePreferenceUtils.saveDataBlocking(AppConstant.TOKEN, "")
                    App.globalRequestAfterCallback()
                    throw NetworkException("登录失效，请重新登录", R.string.error_login_expired)
                }
                HttpStatusCode.Fail.code -> {
                    Log.w(AppConstant.APP_NAME, "Response: ${request.method} ${request.url} — 业务错误: ${responseData.message}")
                    throw NetworkException(responseData.message, R.string.http_fail)
                }
            }
            return response
        } catch (e: NetworkException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Log.w(AppConstant.APP_NAME, "Response: ${request.method} ${request.url} — 请求超时", e)
            throw NetworkException("请求超时，请检查网络", R.string.error_timeout)
        } catch (e: IOException) {
            Log.w(AppConstant.APP_NAME, "Response: ${request.method} ${request.url} — 网络连接失败", e)
            throw NetworkException("网络连接失败，请检查网络", R.string.error_connection)
        } catch (e: Exception) {
            Log.e(AppConstant.APP_NAME, "Response: ${request.method} ${request.url} — 未知错误", e)
            throw NetworkException("未知错误", R.string.error_unknown)
        }
    }
}
