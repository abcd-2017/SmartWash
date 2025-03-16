package com.smartwash.network.interceptor

import android.util.Log
import com.smartwash.App
import com.smartwash.utils.AppConstant
import com.smartwash.utils.HttpStatusCode
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject

class ResponseInterceptor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        //2.根据请求结果判断用户token是否过期
        val response = chain.proceed(chain.request())
        Log.d(AppConstant.APP_NAME, "intercept: $response")

        // 解析服务器返回的 JSON
        val responseBody = response.peekBody(Long.MAX_VALUE).string()
        try {
            val json = JSONObject(responseBody)
            val code =
                json.optInt("code", 200) // 假设你的 API 返回 { "code": 401, "message": "Unauthorized" }

            if (code == HttpStatusCode.Unauthorized.code) {
                App.globalRequestAfterCallback()  // 触发 UI 逻辑（比如跳转登录页）
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return response
    }
}