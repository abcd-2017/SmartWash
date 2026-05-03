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

class ResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful) {
                throw NetworkException("请求失败")
            }

            val bodyString = response.peekBody(Long.MAX_VALUE).string()
            val responseData = Gson().fromJson(bodyString, ResponseData::class.java)

            when (responseData.code) {
                HttpStatusCode.Unauthorized.code -> {
                    SharePreferenceUtils.saveDataBlocking(AppConstant.TOKEN, "")
                    App.globalRequestAfterCallback()
                    throw NetworkException("登录失效，请重新登录")
                }
                HttpStatusCode.Fail.code -> {
                    throw NetworkException(responseData.message)
                }
            }
            return response
        } catch (e: NetworkException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw NetworkException("请求超时，请检查网络")
        } catch (e: IOException) {
            throw NetworkException("网络连接失败，请检查网络")
        } catch (e: Exception) {
            Log.e(AppConstant.APP_NAME, "未知错误", e)
            throw NetworkException("未知错误")
        }
    }
}