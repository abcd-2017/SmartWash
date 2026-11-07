package com.smartwash.feature.user.impl.session

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartwash.common.network.TokenRefresher
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.feature.user.impl.network.entity.user.RefreshTokenRequest
import com.smartwash.feature.user.impl.network.vo.user.TokenPairVo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 双 token 自动续签实现（JWT 双 token 消费侧，实现 common:network 的 [TokenRefresher] 契约）：
 * ResponseInterceptor 遇 401 时经 [refreshBlocking] 用 refreshToken 换新双 token，
 * 成功经 [SessionManager.updateTokens] 覆盖会话（role 不变），失败交由拦截器统一登出。
 *
 * 续签走自建裸 OkHttp 客户端（同 SseClient 先例，baseUrl 复用 @Named("baseUrl")）：
 * - 不挂 Request/ResponseInterceptor：refresh 自身 401（refreshToken 失效/重放）不允许
 *   再触发续签（防递归死循环），失败语义由本类解析信封后返回 false；
 * - 不与主客户端共享 Dispatcher：主客户端 async 并发上限会把 refresh 请求排在
 *   持续阻塞的 401 等待者之后，造成饿死死锁。
 *
 * 单飞设计（refreshToken 一次性轮换，重复刷新会把后续请求打成重放失败）：
 * - [inFlight] 持有进行中续签的共享凭据（CompletableDeferred），快路径直接等待共享结果；
 * - [mutex] 只保护「读取-置入」竞速（唯一胜出者创建凭据），刷新本体在锁外执行，
 *   胜出者完成后广播结果并清空凭据，等待者拿到的是同一次刷新的结果。
 *
 * 阻塞说明：本方法仅在 OkHttp 拦截器线程调用（主通道全部为 Retrofit suspend +
 * 下载/SSE 客户端不挂本模块拦截器，无主线程路径），内部 runBlocking 的挂起链路
 * （DataStore 读写）走独立 IO 线程池，不阻塞主线程、不会与刷新请求互相等待。
 */
@Singleton
class TokenRefresherImpl @Inject constructor(
    private val sessionManager: SessionManager,
    @Named("baseUrl") private val baseUrl: String,
) : TokenRefresher {

    /** 续签专用裸客户端：无任何业务拦截器（见类注释死锁/递归说明），超时对齐主客户端 */
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** /auth/refresh 响应信封 ApiResult<TokenPairVo> 的泛型类型令牌 */
    private val resultType = object : TypeToken<ApiResult<TokenPairVo>>() {}.type

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 进行中的续签凭据：非空表示有刷新正在进行，等待者经它共享结果（单飞核心） */
    @Volatile
    private var inFlight: CompletableDeferred<Boolean>? = null

    /** 互斥锁：仅保护 inFlight 的读取-置入竞速，刷新本体不持锁 */
    private val mutex = Mutex()

    override fun refreshBlocking(): Boolean = runBlocking { refresh() }

    /**
     * 单飞续签：并发多个 401 只发一次刷新请求，其余等待共享同一结果。
     * 挂起版入口（拦截器线程经 runBlocking 进入；亦便于单测直接挂起调用）。
     */
    suspend fun refresh(): Boolean {
        // 快路径：已有进行中的刷新，直接等待共享结果（不抢锁，避免把刷新排在等待者后面）
        inFlight?.let { existing -> return existing.await() }

        val created = CompletableDeferred<Boolean>()
        val winner = mutex.withLock {
            // double-check：进锁后可能已有胜出者置入凭据，败者直接共享其结果
            inFlight ?: created.also { inFlight = it }
        }
        if (winner !== created) {
            return winner.await()
        }

        // 唯一胜出者：锁外执行刷新，完成后广播结果并清空凭据
        return try {
            doRefresh().also { created.complete(it) }
        } catch (e: CancellationException) {
            // 胜出者被取消也不能让等待者悬死，广播失败结果后再传播取消
            created.complete(false)
            throw e
        } catch (e: Exception) {
            created.complete(false)
            false
        } finally {
            inFlight = null
        }
    }

    /** 换取新双 token 并写入会话；任何失败（无凭据/被拒/网络/解析）一律返回 false */
    private suspend fun doRefresh(): Boolean {
        val refreshToken = sessionManager.getRefreshToken()
        if (refreshToken.isBlank()) {
            // 未登录/已登出：不发请求，登录失效交由拦截器统一处理
            AppLogger.debug(TAG, "refreshToken 为空，跳过续签请求")
            return false
        }

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + REFRESH_PATH)
            .post(gson.toJson(RefreshTokenRequest(refreshToken)).toRequestBody(jsonMediaType))
            .build()

        return try {
            refreshClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                    AppLogger.warn(TAG, "Refresh: /auth/refresh — HTTP ${response.code}，续签失败")
                    return false
                }
                // 后端契约：成功 HTTP 200 + 信封 code=200 + data{token, refreshToken}；
                // refreshToken 失效/重放返回信封 code=401（HTTP 仍为 200）
                val result = gson.fromJson<ApiResult<TokenPairVo>>(bodyString, resultType)
                val pair = result?.data
                if (result?.code != HttpStatusCode.Success.code ||
                    pair?.token.isNullOrBlank() || pair?.refreshToken.isNullOrBlank()
                ) {
                    AppLogger.warn(TAG, "Refresh: /auth/refresh — 信封 code=${result?.code}，续签被拒")
                    return false
                }
                sessionManager.updateTokens(pair.token, pair.refreshToken)
                // 会话变更基建节点（低频；token 值绝不入日志）
                AppLogger.info(TAG, "Refresh: /auth/refresh — 续签成功，双 token 已更新")
                true
            }
        } catch (e: IOException) {
            AppLogger.warn(TAG, "Refresh: /auth/refresh — 网络连接失败，续签失败", e)
            false
        }
    }

    private companion object {
        /** 刷新端点（后端公开认证域，SecurityConfig 放行，无前缀变更感知） */
        const val REFRESH_PATH = "/auth/refresh"

        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "TokenRefresher"
    }
}
