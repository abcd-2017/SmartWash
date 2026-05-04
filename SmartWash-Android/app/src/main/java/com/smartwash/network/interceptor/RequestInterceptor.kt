package com.smartwash.network.interceptor

import android.util.Log
import com.smartwash.App
import com.smartwash.R
import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.utils.SharePreferenceUtils
import okhttp3.Interceptor
import okhttp3.Response

class RequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val tag = request.tag(retrofit2.Invocation::class.java)
        val method = tag?.method()
        val annotated = method?.annotations?.any { it is RequireAuthorization }

        if (annotated == true) {
            val token = SharePreferenceUtils.getDataBlocking(AppConstant.TOKEN, "")

            if (token.isBlank()) {
                Log.w(AppConstant.APP_NAME, "Request: ${request.method} ${request.url} — token 为空，拦截请求")
                App.globalRequestBeforeCallback()
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
