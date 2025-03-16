package com.smartwash.network.interceptor

import com.smartwash.App
import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.utils.AppConstant
import com.smartwash.utils.SharePreferenceUtils
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 请求拦截器
 */
class RequestInterceptor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        //1.如果请求方法上带了这个注解，那么就请求头加上token这个参数
        val tag = request.tag(retrofit2.Invocation::class.java)
        //调用方法的信息
        val method = tag?.method()
        //是否带有这个标签
        val annotated = method?.annotations?.any { it is RequireAuthorization }
        var modifiedRequest = request.newBuilder()
        if (annotated != null && annotated) {
            val token = SharePreferenceUtils.getData(AppConstant.TOKEN, "")

            if (token.isBlank()) {
                App.globalRequestBeforeCallback
            }

            modifiedRequest = request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
        }
        return chain.proceed(modifiedRequest.build())
    }
}