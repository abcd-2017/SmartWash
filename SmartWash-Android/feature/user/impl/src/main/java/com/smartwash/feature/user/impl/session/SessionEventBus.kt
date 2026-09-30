package com.smartwash.feature.user.impl.session

import com.smartwash.common.network.SessionEventNotifier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络层/用户操作 → UI 的会话事件（替代原 App.globalRequestBefore/AfterCallback 静态 lateinit 回调）。
 *
 * 请求可能发生在 Activity setContent 之前，静态回调此时未赋值会直接崩溃；
 * 改为 Hilt 注入的事件流后，早期请求的事件只会在无订阅者时被丢弃，不再崩溃。
 *
 * 实现 [SessionEventNotifier]（common:network 的跨模块契约，T2.3）：拦截器经接口发会话事件，
 * 绑定见 di/UserImplModule（T5.2 自 app/di 的 SessionNetworkBindingModule 随迁）。
 *
 * T5.2 起事件面扩展为四类：网络层触发的 [NeedLogin]/[Unauthorized]（经去重窗口，
 * 防并发多请求同时失败堆叠登录页）+ 用户操作触发的 [LoggedIn]/[LoggedOut]
 * （登录/注册成功、设置页主动登出，直接广播不走去重——用户主动操作不会并发风暴）。
 * UserApiImpl 将四类统一桥接为 user-api 的 LoginEvent 对外广播。
 */
sealed class SessionEvent {
    /** 请求被拦截：未登录（token 为空），需引导用户去登录 */
    data object NeedLogin : SessionEvent()

    /** 登录失效：HTTP 401 / 业务码 401，需重新登录 */
    data object Unauthorized : SessionEvent()

    /** 会话建立：登录/注册成功（页面在 saveToken 后主动广播；网络层不触发） */
    data object LoggedIn : SessionEvent()

    /** 主动登出：设置页 clearToken 后广播（网络层触发的会话终止走 NeedLogin/Unauthorized） */
    data object LoggedOut : SessionEvent()
}

@Singleton
class SessionEventBus @Inject constructor() : SessionEventNotifier {

    private val _events = MutableSharedFlow<SessionEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** 同类事件去重窗口：并发多请求同时失败时只通知一次，避免堆叠多个登录页 */
    private val lastEmitElapsedMs = AtomicLong(0L)

    override fun notifyNeedLogin() = emit(SessionEvent.NeedLogin)

    override fun notifyUnauthorized() = emit(SessionEvent.Unauthorized)

    /** 登录/注册成功广播（用户主动操作，不走去重窗口） */
    fun notifyLoggedIn() {
        _events.tryEmit(SessionEvent.LoggedIn)
    }

    /** 主动登出广播（用户主动操作，不走去重窗口） */
    fun notifyLoggedOut() {
        _events.tryEmit(SessionEvent.LoggedOut)
    }

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
