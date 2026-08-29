package com.smartwash.network.interceptor

import android.util.Log
import com.smartwash.R
import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.session.SessionEventBus
import com.smartwash.network.session.SessionManager
import com.smartwash.utils.AppConstant
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class RequestInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val tag = request.tag(retrofit2.Invocation::class.java)
        val method = tag?.method()
        val annotated = method?.annotations?.any { it is RequireAuthorization }

        if (annotated == true) {
            // 同步读进程内 token 缓存，不再 runBlocking 读 DataStore
            val token = sessionManager.currentToken()

            if (token.isBlank()) {
                Log.w(AppConstant.APP_NAME, "Request: ${request.method} ${request.url} — token 为空，拦截请求")
                sessionEventBus.notifyNeedLogin()
                throw NetworkException("未登录，请先登录", R.string.error_login_expired)
            }

            Log.d(AppConstant.APP_NAME, "Request: ${request.method} ${request.url} — 携带 token")
            val modifiedRequest = request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            return chain.proceed(modifiedRequest)
        }

        Log.d(AppConstant.APP_NAME, "Request: ${request.method} ${request.url}")
        return chain.proceed(request)
    }
}
