package com.smartwash.common.network.interceptor

import android.util.Log
import com.smartwash.common.network.R
import com.smartwash.common.network.SessionEventNotifier
import com.smartwash.common.network.TokenProvider
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.network.exception.NetworkException
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class RequestInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val sessionEventNotifier: SessionEventNotifier,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val tag = request.tag(retrofit2.Invocation::class.java)
        val method = tag?.method()
        val annotated = method?.annotations?.any { it is RequireAuthorization }

        if (annotated == true) {
            // 同步读进程内 token 缓存，不再 runBlocking 读 DataStore
            val token = tokenProvider.currentToken().orEmpty()

            if (token.isBlank()) {
                Log.w(TAG, "Request: ${request.method} ${request.url} — token 为空，拦截请求")
                sessionEventNotifier.notifyNeedLogin()
                throw NetworkException("未登录，请先登录", R.string.error_login_expired)
            }

            Log.d(TAG, "Request: ${request.method} ${request.url} — 携带 token")
            val modifiedRequest = request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            return chain.proceed(modifiedRequest)
        }

        Log.d(TAG, "Request: ${request.method} ${request.url}")
        return chain.proceed(request)
    }

    private companion object {
        /** 日志 TAG（原 app 内 AppConstant.APP_NAME，迁移后模块内自定义常量） */
        const val TAG = "SmartWash"
    }
}
