package com.smartwash.common.network.interceptor

import android.util.Log
import com.google.gson.Gson
import com.smartwash.common.model.ApiResult
import com.smartwash.common.model.HttpStatusCode
import com.smartwash.common.network.R
import com.smartwash.common.network.SessionEventNotifier
import com.smartwash.common.network.TokenProvider
import com.smartwash.common.network.exception.NetworkException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

/** 响应信封解析最多 peek 1MB，避免把大响应整体读入内存翻倍 */
private const val MAX_PEEK_BYTES = 1024L * 1024L

/** Gson 实例线程安全，全局复用单例 */
private val GSON = Gson()

class ResponseInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val sessionEventNotifier: SessionEventNotifier,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        try {
            val response = chain.proceed(request)

            // HTTP 401 必须在通用 !response.isSuccessful 之前处理，否则会被拦截为"请求失败"
            if (response.code == 401) {
                Log.w(TAG, "Response: ${request.method} ${request.url} — HTTP 401 登录失效")
                handleSessionExpired()
                throw NetworkException("登录失效，请重新登录", R.string.error_login_expired)
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "Response: ${request.method} ${request.url} — HTTP ${response.code}")
                throw NetworkException("请求失败", R.string.error_network_fail)
            }

            // 空 body（204/空串）没有响应信封，直接放行，避免解析出 null 后取 .code 触发 NPE
            val bodyString = response.peekBody(MAX_PEEK_BYTES).string()
            if (bodyString.isNotBlank()) {
                val responseData = GSON.fromJson(bodyString, ApiResult::class.java)
                if (responseData != null) {
                    when (responseData.code) {
                        HttpStatusCode.Unauthorized.code -> {
                            Log.w(TAG, "Response: ${request.method} ${request.url} — 业务码 401 登录失效")
                            handleSessionExpired()
                            throw NetworkException("登录失效，请重新登录", R.string.error_login_expired)
                        }
                        HttpStatusCode.Fail.code -> {
                            Log.w(TAG, "Response: ${request.method} ${request.url} — 业务错误: ${responseData.message}")
                            throw NetworkException(responseData.message, R.string.error_network_fail)
                        }
                    }
                }
            }
            return response
        } catch (e: NetworkException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Response: ${request.method} ${request.url} — 请求超时", e)
            throw NetworkException("请求超时，请检查网络", R.string.error_timeout)
        } catch (e: IOException) {
            Log.w(TAG, "Response: ${request.method} ${request.url} — 网络连接失败", e)
            throw NetworkException("网络连接失败，请检查网络", R.string.error_connection)
        } catch (e: Exception) {
            Log.e(TAG, "Response: ${request.method} ${request.url} — 未知错误", e)
            throw NetworkException("未知错误", R.string.error_unknown)
        }
    }

    /**
     * 登录失效统一处理：清 token（幂等，去重）+ 发送会话事件（去重），
     * 避免并发多请求同时 401 时重复清 token / 堆叠多个登录页。
     */
    private fun handleSessionExpired() {
        tokenProvider.clearToken()
        sessionEventNotifier.notifyUnauthorized()
    }

    private companion object {
        /** 日志 TAG（原 app 内 AppConstant.APP_NAME，迁移后模块内自定义常量） */
        const val TAG = "SmartWash"
    }
}
