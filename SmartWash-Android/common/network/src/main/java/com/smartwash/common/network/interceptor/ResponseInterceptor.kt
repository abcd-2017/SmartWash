package com.smartwash.common.network.interceptor

import com.google.gson.Gson
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.common.network.R
import com.smartwash.common.network.SessionEventNotifier
import com.smartwash.common.network.TokenProvider
import com.smartwash.common.network.TokenRefresher
import com.smartwash.common.network.exception.NetworkException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

/** 响应信封解析最多 peek 1MB，避免把大响应整体读入内存翻倍 */
private const val MAX_PEEK_BYTES = 1024L * 1024L

/** Gson 实例线程安全，全局复用单例 */
private val GSON = Gson()

/**
 * 401 续签重试标记（okhttp tag，不随请求上行服务端）：
 * 原请求 401 → 续签成功 → 换新 token 重试，重试请求携带本标记；重试响应再遇 401
 * （HTTP/业务码任一通道）时不再续签，直接登录失效——新 token 对该资源同样无效时
 * 继续重试只会死循环。
 */
private class RetriedMarker

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
    private val tokenRefresher: TokenRefresher,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        interceptInternal(chain, chain.request())

    private fun interceptInternal(chain: Interceptor.Chain, request: Request): Response {
        // 手机号脱敏：验证码接口路径含手机号，URL 入日志前统一打码
        val url = AppLogger.maskPhone(request.url.toString())
        try {
            val response = chain.proceed(request)

            // HTTP 401 必须在通用 !response.isSuccessful 之前处理，否则会被拦截为"请求失败"
            if (response.code == 401) {
                AppLogger.warn(TAG, "Response: ${request.method} $url — HTTP 401 登录失效")
                return recoverFromUnauthorized(chain, request, response, url)
            }

            if (!response.isSuccessful) {
                AppLogger.warn(TAG, "Response: ${request.method} $url — HTTP ${response.code}")
                response.close()
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
                            return recoverFromUnauthorized(chain, request, response, url)
                        }
                        HttpStatusCode.Fail.code -> {
                            AppLogger.warn(TAG, "Response: ${request.method} $url — 业务错误: ${responseData.message}")
                            response.close()
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
     * 401（HTTP/业务码两通道）统一恢复入口：
     * - 请求属 /auth/ 前缀（登录/注册/验证码/刷新等公开认证接口自身失败）或该请求已重试过
     *   → 保持原语义直接登录失效（登录失败不能触发续签）；
     * - 否则经 [TokenRefresher] 单飞续签：成功 → 用新 token 重设 Authorization 头重试一次，
     *   重试响应重新走完整信封解析（重试再 401 由 [RetriedMarker] 拦截，直接登录失效）；
     *   失败 → 登录失效。
     *
     * 登录失效统一由 [handleSessionExpired] 处理（清 token 幂等 + 会话事件去重）。
     */
    private fun recoverFromUnauthorized(
        chain: Interceptor.Chain,
        request: Request,
        failed: Response,
        url: String,
    ): Response {
        val retryRequest = retryRequestAfterRefreshOrNull(request, url)
        if (retryRequest == null) {
            failed.close()
            handleSessionExpired()
            throw NetworkException("登录失效，请重新登录", R.string.error_login_expired)
        }
        AppLogger.info(TAG, "Response: $url — 续签成功，换新 token 重试一次")
        // 重试前必须关闭失败响应，否则其连接无法归还连接池（okhttp 拦截器协议要求）
        failed.close()
        return interceptInternal(chain, retryRequest)
    }

    /** 可续签重试时返回携带新 Authorization 头与已重试标记的新请求；否则返回 null（须登录失效） */
    private fun retryRequestAfterRefreshOrNull(request: Request, url: String): Request? {
        // 认证接口自身 401：凭据错误就是最终语义（密码错/验证码错/refreshToken 失效），不触发续签
        if (isAuthEndpoint(request.url)) {
            AppLogger.debug(TAG, "Response: $url — 认证接口自身 401，不触发续签")
            return null
        }
        // 已重试过仍 401：新 token 对该资源同样无效（会话被顶下线/权限吊销等），不再重试
        if (request.tag(RetriedMarker::class.java) != null) {
            AppLogger.debug(TAG, "Response: $url — 重试后仍 401，不再续签")
            return null
        }
        // 单飞续签：并发多个 401 共享一次刷新结果（实现见 user:impl 的 TokenRefresherImpl）
        if (!tokenRefresher.refreshBlocking()) {
            AppLogger.warn(TAG, "Response: $url — 刷新 token 失败，会话失效")
            return null
        }
        val newToken = tokenProvider.currentToken().orEmpty()
        if (newToken.isBlank()) {
            AppLogger.warn(TAG, "Response: $url — 刷新成功但 token 为空，会话失效")
            return null
        }
        return request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .tag(RetriedMarker::class.java, RetriedMarker())
            .build()
    }

    /**
     * 公开认证端点判定：路径以 /auth/ 开头（登录/注册/验证码/刷新/登出等）。
     * 注意受保护业务接口是 /web/auth/ 前缀（需续签重试），不能被 /auth/ 误伤；
     * 两者以完整前缀区分（BASE_URL 为 host 根路径，Retrofit 相对路径原样拼接）。
     */
    private fun isAuthEndpoint(url: HttpUrl): Boolean {
        val path = url.encodedPath
        return path.startsWith("/auth/") && !path.startsWith("/web/auth/")
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
