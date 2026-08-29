package com.smartwash.network.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络层 → UI 的会话事件（替代原 App.globalRequestBefore/AfterCallback 静态 lateinit 回调）。
 *
 * 请求可能发生在 Activity setContent 之前，静态回调此时未赋值会直接崩溃；
 * 改为 Hilt 注入的事件流后，早期请求的事件只会在无订阅者时被丢弃，不再崩溃。
 */
sealed class SessionEvent {
    /** 请求被拦截：未登录（token 为空），需引导用户去登录 */
    data object NeedLogin : SessionEvent()

    /** 登录失效：HTTP 401 / 业务码 401，需重新登录 */
    data object Unauthorized : SessionEvent()
}

@Singleton
class SessionEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<SessionEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** 同类事件去重窗口：并发多请求同时失败时只通知一次，避免堆叠多个登录页 */
    private val lastEmitElapsedMs = AtomicLong(0L)

    fun notifyNeedLogin() = emit(SessionEvent.NeedLogin)

    fun notifyUnauthorized() = emit(SessionEvent.Unauthorized)

    private fun emit(event: SessionEvent) {
        val now = System.nanoTime() / 1_000_000L
        val last = lastEmitElapsedMs.get()
        if (now - last < DEDUP_INTERVAL_MS) return
        if (!lastEmitElapsedMs.compareAndSet(last, now)) return
        _events.tryEmit(event)
    }

    private companion object {
        const val DEDUP_INTERVAL_MS = 1500L
    }
}
