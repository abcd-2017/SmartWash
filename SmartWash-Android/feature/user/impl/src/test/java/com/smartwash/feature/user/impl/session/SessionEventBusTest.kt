package com.smartwash.feature.user.impl.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * SessionEventBus JVM 单测（Robolectric 供 Context——去重抑制路径会经 AppLogger 触发 android.util.Log）。
 *
 * 锚点契约：网络层事件（NeedLogin/Unauthorized）走同一去重窗口（1500ms），并发多请求
 * 同时失败只通知一次；用户操作事件（LoggedIn/LoggedOut）直接广播不走去重；
 * 缓冲 extraBufferCapacity=16 + DROP_OLDEST（慢订阅者不反压发射方）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionEventBusTest {

    /** 后台订阅事件流，onSubscription 回调确认注册完成（SharedFlow 无 replay：先订阅后发射才有事件） */
    private fun subscribe(bus: SessionEventBus): Pair<CoroutineScope, CopyOnWriteArrayList<SessionEvent>> {
        val received = CopyOnWriteArrayList<SessionEvent>()
        val subscribed = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            bus.events
                .onSubscription { subscribed.countDown() }
                .collect { received += it }
        }
        check(subscribed.await(5, TimeUnit.SECONDS)) { "订阅应在 5s 内注册" }
        return scope to received
    }

    /** 等待收集到指定数量的事件（事件从缓冲到收集器是异步分发） */
    private fun awaitSize(received: List<SessionEvent>, expected: Int) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && received.size < expected) Thread.sleep(10)
        assertEquals("事件收集数量不符", expected, received.size)
    }

    @Test
    fun `网络层同类事件并发触发 去重窗口内只通知一次`() {
        val bus = SessionEventBus()
        val (scope, received) = subscribe(bus)
        try {
            bus.notifyNeedLogin()
            bus.notifyNeedLogin()
            bus.notifyNeedLogin()
            awaitSize(received, 1)
            // 窗口内连续 3 次触发只投递首个
            Thread.sleep(100)
            assertEquals(listOf<SessionEvent>(SessionEvent.NeedLogin), received)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `NeedLogin 与 Unauthorized 共享同一去重窗口`() {
        val bus = SessionEventBus()
        val (scope, received) = subscribe(bus)
        try {
            bus.notifyNeedLogin()
            bus.notifyUnauthorized() // 同属网络层路径，落入同一 1500ms 窗口被抑制
            awaitSize(received, 1)
            Thread.sleep(100)
            assertEquals(listOf<SessionEvent>(SessionEvent.NeedLogin), received)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `用户操作事件不走去重 逐条广播`() {
        val bus = SessionEventBus()
        val (scope, received) = subscribe(bus)
        try {
            bus.notifyLoggedIn()
            bus.notifyLoggedOut()
            bus.notifyLoggedIn()
            awaitSize(received, 3)
            assertEquals(
                listOf(
                    SessionEvent.LoggedIn,
                    SessionEvent.LoggedOut,
                    SessionEvent.LoggedIn,
                ),
                received,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `去重窗口过后网络层事件可再次通知`() {
        val bus = SessionEventBus()
        val (scope, received) = subscribe(bus)
        try {
            bus.notifyNeedLogin()
            awaitSize(received, 1)
            // 越过去重窗口（>1500ms 实际耗时）
            Thread.sleep(1600)
            bus.notifyUnauthorized()
            awaitSize(received, 2)
            assertEquals(listOf<SessionEvent>(SessionEvent.NeedLogin, SessionEvent.Unauthorized), received)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `实现 SessionEventNotifier 契约（拦截器调用路径）`() {
        val bus = SessionEventBus()
        val notifier: com.smartwash.common.network.SessionEventNotifier = bus
        val (scope, received) = subscribe(bus)
        try {
            notifier.notifyUnauthorized()
            awaitSize(received, 1)
            assertEquals(SessionEvent.Unauthorized, received[0])
        } finally {
            scope.cancel()
        }
    }
}
