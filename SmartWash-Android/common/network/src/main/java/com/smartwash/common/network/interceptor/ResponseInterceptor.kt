package com.smartwash.common.network.interceptor

import com.google.gson.Gson
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.HttpStatusCode
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

/**
 * 业务错误（code≠200）的用户可见文案取值策略：
 *
 * 后端业务失败时信封 message 恒为「失败」，具体语义（如「系统异常，请稍后再试」）
 * 放在 data 字符串里——仅用 message 会把所有业务失败展示成裸「失败」。
 * 取值顺序 = 有具体语义的 data 字符串优先、message 兜底；data 只在**非空字符串**时
 * 采用（业务失败时 data 也可能是对象/空串/布尔，直接 toString 会产出「{...}」「null」
 * 这类垃圾文案，必须丢弃）。
 *
 * internal 供 JVM 单测锚点（Regression 2026-11-06 A1）。
 */
internal fun resolveBusinessErrorMessage(message: String?, data: Any?): String =
    (data as? String)?.takeIf { it.isNotBlank() } ?: message.orEmpty()

class ResponseInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val sessionEventNotifier: SessionEventNotifier,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 手机号脱敏：验证码接口路径含手机号，URL 入日志前统一打码
        val url = AppLogger.maskPhone(request.url.toString())
        try {
            val response = chain.proceed(request)

            // HTTP 401 必须在通用 !response.isSuccessful 之前处理，否则会被拦截为"请求失败"
            if (response.code == 401) {
                AppLogger.warn(TAG, "Response: ${request.method} $url — HTTP 401 登录失效")
                handleSessionExpired()
                throw NetworkException("登录失效，请重新登录", R.string.error_login_expired)
            }

            if (!response.isSuccessful) {
                AppLogger.warn(TAG, "Response: ${request.method} $url — HTTP ${response.code}")
                throw NetworkException("请求失败", R.string.error_network_fail)
            }

            // 空 body（204/空串）没有响应信封，直接放行，避免解析出 null 后取 .code 触发 NPE
            val bodyString = response.peekBody(MAX_PEEK_BYTES).string()
            if (bodyString.isNotBlank()) {
                val responseData = GSON.fromJson(bodyString, ApiResult::class.java)
                if (responseData != null) {
                    when (responseData.code) {
                        HttpStatusCode.Unauthorized.code -> {
                            AppLogger.warn(TAG, "Response: ${request.method} $url — 业务码 401 登录失效")
                            handleSessionExpired()
                            throw NetworkException("登录失效，请重新登录", R.string.error_login_expired)
                        }
                        HttpStatusCode.Fail.code -> {
                            AppLogger.warn(TAG, "Response: ${request.method} $url — 业务错误: ${responseData.message}")
                            throw NetworkException(
                                resolveBusinessErrorMessage(responseData.message, responseData.data),
                                R.string.error_network_fail,
                            )
                        }
                    }
                }
            }
            return response
        } catch (e: NetworkException) {
            throw e
        } catch (e: SocketTimeoutException) {
            AppLogger.warn(TAG, "Response: ${request.method} $url — 请求超时", e)
            throw NetworkException("请求超时，请检查网络", R.string.error_timeout)
        } catch (e: IOException) {
            AppLogger.warn(TAG, "Response: ${request.method} $url — 网络连接失败", e)
            throw NetworkException("网络连接失败，请检查网络", R.string.error_connection)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Response: ${request.method} $url — 未知错误", e)
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
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "ResponseInterceptor"
    }
}
