package com.smartwash.common.network.interceptor

import com.smartwash.common.network.R
import com.smartwash.common.network.SessionEventNotifier
import com.smartwash.common.network.TokenProvider
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class RequestInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val sessionEventNotifier: SessionEventNotifier,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 手机号脱敏：验证码接口路径含手机号，URL 入日志前统一打码
        val url = AppLogger.maskPhone(request.url.toString())

        val tag = request.tag(retrofit2.Invocation::class.java)
        val method = tag?.method()
        val annotated = method?.annotations?.any { it is RequireAuthorization }

        if (annotated == true) {
            // 同步读进程内 token 缓存，不再 runBlocking 读 DataStore
            val token = tokenProvider.currentToken().orEmpty()

            if (token.isBlank()) {
                AppLogger.warn(TAG, "Request: ${request.method} $url — token 为空，拦截请求")
                sessionEventNotifier.notifyNeedLogin()
                throw NetworkException("未登录，请先登录", R.string.error_login_expired)
            }

            AppLogger.debug(TAG, "Request: ${request.method} $url — 携带 token")
            val modifiedRequest = request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            return chain.proceed(modifiedRequest)
        }

        AppLogger.debug(TAG, "Request: ${request.method} $url")
        return chain.proceed(request)
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "RequestInterceptor"
    }
}
