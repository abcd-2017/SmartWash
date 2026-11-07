package com.smartwash.common.network.sse

import com.smartwash.common.network.SessionEventNotifier
import com.smartwash.common.network.TokenProvider
import com.smartwash.common.utils.AppLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * SSE 流式通道通用封装（issue #78 观象台解读真连）。
 *
 * 与主通道（Retrofit + Request/ResponseInterceptor）的关系：
 * - 主通道拦截器依赖 retrofit2.Invocation tag 判定 @RequireAuthorization（SSE 请求没有该
 *   tag），且 ResponseInterceptor 会 peek 响应体当 JSON 信封解析（对 text/event-stream 流
 *   无意义且有阻塞风险）——SSE 走本封装自建的 OkHttpClient，自行注入 Bearer 并处理 401；
 * - 401 语义与 [com.smartwash.common.network.interceptor.ResponseInterceptor] 保持一致：
 *   清 token（幂等）→ [SessionEventNotifier.notifyUnauthorized]（事件去重、只跳一次登录页）；
 * - baseUrl 复用主网关（@Named("baseUrl") 由 app 侧注入，禁止硬编码）；
 * - 请求体由调用方给定（如 interpret 的纯文本问题），Content-Type 透传调用方选择。
 *
 * 仅做传输层封装：事件名/data 原样回调给业务侧解析，本类无任何业务语义。
 *
 * okhttp-sse 用法说明：EventSources.createFactory(client).newEventSource(request, listener)
 * 内部自行发起请求并读流——非 2xx 响应不走 onOpen，直接进 onFailure(t=null, response!=null)；
 * 服务端 complete 走 onClosed；连接失败/读流中断走 onFailure(t!=null)。禁止外层再套
 * Call.enqueue，否则同一请求会发出两次。
 */
@Singleton
class SseClient @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val sessionEventNotifier: SessionEventNotifier,
    @Named("baseUrl") private val baseUrl: String,
) {

    /** SSE 一次连接的终态 */
    sealed class Outcome {
        /** 流正常结束（服务端 complete，事件已在 onEvent 全部回调） */
        data object Completed : Outcome()

        /** HTTP 401：会话已清 + 已通知跳登录（业务侧无需再弹错误文案） */
        data object Unauthorized : Outcome()

        /** HTTP 非 2xx 非 401 */
        data class HttpError(val code: Int) : Outcome()

        /** 连接/读流异常（含超时） */
        data class NetworkError(val message: String?) : Outcome()
    }

    /** SSE 专用 client：无业务拦截器；读超时须覆盖服务端 SSE 最长推流时长（后端 SseEmitter 120s） */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * POST 发起 SSE 流并挂起至流结束。
     *
     * @param path        相对路径（如 "web/auth/div/records/1/interpret"，拼在 baseUrl 后）
     * @param body        请求体原文（如解读问题纯文本；空串则发空 body）
     * @param contentType 请求体 Content-Type（须含 charset，如 "text/plain; charset=utf-8"）
     * @param onEvent     事件回调（event 名 + data 原文；同帧多行 data 由 okhttp-sse 以 \n 合并返回）
     */
    suspend fun post(
        path: String,
        body: String,
        contentType: String,
        onEvent: (name: String, data: String) -> Unit,
    ): Outcome {
        val url = buildUrl(path)
        val mediaType = contentType.toMediaType()
        val requestBody = if (body.isEmpty()) {
            // 空 body：仍带 Content-Type，服务端 @RequestBody(required=false) 才能安全落 null
            ByteArray(0).toRequestBody(mediaType)
        } else {
            body.toRequestBody(mediaType)
        }
        val token = tokenProvider.currentToken()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(requestBody)
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return suspendCancellableCoroutine { continuation ->
            var sourceRef: EventSource? = null
            // 协程取消（解读页返回/追问页退出）→ 断流中止服务端推流
            continuation.invokeOnCancellation { sourceRef?.cancel() }

            sourceRef = EventSources.createFactory(client).newEventSource(
                requestBuilder.build(),
                object : EventSourceListener() {
                    override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                        if (continuation.isCancelled) return
                        onEvent(type.orEmpty(), data)
                    }

                    override fun onClosed(es: EventSource) {
                        if (continuation.isCancelled) return
                        continuation.resume(Outcome.Completed)
                    }

                    override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                        if (continuation.isCancelled) return
                        when {
                            response != null && response.code == 401 -> {
                                AppLogger.warn(TAG, "SSE: $url — HTTP 401 登录失效")
                                handleSessionExpired()
                                continuation.resume(Outcome.Unauthorized)
                            }
                            response != null -> {
                                AppLogger.warn(TAG, "SSE: $url — HTTP ${response.code}")
                                continuation.resume(Outcome.HttpError(response.code))
                            }
                            else -> {
                                AppLogger.warn(TAG, "SSE: $url — 连接失败: ${t?.message}")
                                continuation.resume(Outcome.NetworkError(t?.message))
                            }
                        }
                    }
                },
            )
        }
    }

    /** 401 语义与 ResponseInterceptor.handleSessionExpired 完全一致（清 token 幂等 + 事件去重） */
    private fun handleSessionExpired() {
        tokenProvider.clearToken()
        sessionEventNotifier.notifyUnauthorized()
    }

    /** baseUrl（末尾带 /，与 Retrofit 同约定）+ 相对路径拼接 */
    private fun buildUrl(path: String): String {
        val base = baseUrl.ifEmpty { "/" }
        return if (base.endsWith("/")) base + path else "$base/$path"
    }

    private companion object {
        const val TAG = "SseClient"
    }
}
