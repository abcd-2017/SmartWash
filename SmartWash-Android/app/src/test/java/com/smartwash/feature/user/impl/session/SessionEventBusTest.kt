package com.smartwash.feature.user.impl.session

import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * [SessionEventBus] JVM 单测（锚点）：并发 401 去重窗口（1.5s）内多请求同时失败只通知一次，
 * 窗口过期后可再次通知；主动 [SessionEvent.LoggedIn] / [SessionEvent.LoggedOut] 不受去重窗口约束。
 *
 * 时间语义：去重窗口用 [System.nanoTime] 真实时间，故本用例用 [runBlocking] + [delay]，
 * 不用 runTest 虚拟时间。
 */
class SessionEventBusTest {

    @Test
    fun `并发未授权通知在去重窗口内只发一次`() = runBlocking {
        val bus = SessionEventBus()
        val received = Collections.synchronizedList(mutableListOf<SessionEvent>())
        val collector = launch { bus.events.collect { received.add(it) } }
        // 等待订阅生效（SharedFlow replay=0，无订阅者时事件不落缓存）
        delay(SUBSCRIBE_SETTLE_MS)

        val threads = (1..THREAD_COUNT).map {
            thread { repeat(CALLS_PER_THREAD) { bus.notifyUnauthorized() } }
        }
        threads.forEach { it.join() }
        delay(EMIT_SETTLE_MS)

        assertEquals("并发 401 在去重窗口内应只发一次", 1, received.size)
        assertEquals(SessionEvent.Unauthorized, received.single())
        collector.cancel()
    }

    @Test
    fun `并发未登录通知同样受去重窗口约束`() = runBlocking {
        val bus = SessionEventBus()
        val received = Collections.synchronizedList(mutableListOf<SessionEvent>())
        val collector = launch { bus.events.collect { received.add(it) } }
        delay(SUBSCRIBE_SETTLE_MS)

        val threads = (1..THREAD_COUNT).map {
            thread { repeat(CALLS_PER_THREAD) { bus.notifyNeedLogin() } }
        }
        threads.forEach { it.join() }
        delay(EMIT_SETTLE_MS)

        assertEquals(1, received.size)
        assertEquals(SessionEvent.NeedLogin, received.single())
        collector.cancel()
    }

    @Test
    fun `主动登录与登出不受去重窗口约束`() = runBlocking {
        val bus = SessionEventBus()
        val received = Collections.synchronizedList(mutableListOf<SessionEvent>())
        val collector = launch { bus.events.collect { received.add(it) } }
        delay(SUBSCRIBE_SETTLE_MS)

        bus.notifyLoggedIn()
        bus.notifyLoggedOut()
        bus.notifyLoggedIn()
        delay(EMIT_SETTLE_MS)

        assertEquals(
            listOf(SessionEvent.LoggedIn, SessionEvent.LoggedOut, SessionEvent.LoggedIn),
            received
        )
        collector.cancel()
    }

    @Test
    fun `去重窗口过期后可再次通知`() = runBlocking {
        val bus = SessionEventBus()
        val received = Collections.synchronizedList(mutableListOf<SessionEvent>())
        val collector = launch { bus.events.collect { received.add(it) } }
        delay(SUBSCRIBE_SETTLE_MS)

        bus.notifyUnauthorized()
        delay(DEDUP_INTERVAL_MS + 200)
        bus.notifyUnauthorized()
        delay(EMIT_SETTLE_MS)

        assertEquals(2, received.size)
        assertTrue(received.all { it == SessionEvent.Unauthorized })
        collector.cancel()
    }

    private companion object {
        const val THREAD_COUNT = 8
        const val CALLS_PER_THREAD = 16
        const val SUBSCRIBE_SETTLE_MS = 100L
        const val EMIT_SETTLE_MS = 100L
        const val DEDUP_INTERVAL_MS = 1_500L
    }
}
