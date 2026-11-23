package com.smartwash.feature.update.event

import com.smartwash.feature.update.api.model.AppVersionVo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 更新事件总线 JVM 单测（纯 JVM，无 Android 依赖）。
 *
 * 锚点契约：
 * - 「发现新版本」与「自动下载」两类事件载荷类型分流（壳层按此分流弹窗形态）；
 * - replay = 1：启动检查（InitEngine 非阻塞）可能早于 MainActivity 订阅——无重放则
 *   事件被丢弃、本次启动不弹更新；重放后的重复打扰由壳层「已关闭版本」防重挡下；
 * - 缓冲 extraBufferCapacity = 16 + DROP_OLDEST：慢订阅者不反压发射方。
 */
class UpdateEventBusTest {

    private fun version(code: Int) = AppVersionVo(
        versionCode = code,
        versionName = "1.$code.0",
        apkUrl = "https://cdn.example.com/app-$code.apk",
        forceUpdate = false,
    )

    /** 后台订阅并等待注册完成（onSubscription 回调确认） */
    private fun subscribe(bus: UpdateEventBus): Pair<CoroutineScope, CopyOnWriteArrayList<UpdateEvent>> {
        val received = CopyOnWriteArrayList<UpdateEvent>()
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

    private fun awaitSize(received: List<UpdateEvent>, expected: Int) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && received.size < expected) Thread.sleep(10)
        assertEquals("事件收集数量不符", expected, received.size)
    }

    // ==================== 事件类型分流 ====================

    @Test
    fun `发现新版本广播 UpdateAvailable 事件且载荷透传`() {
        val bus = UpdateEventBus()
        val (scope, received) = subscribe(bus)
        try {
            bus.notifyUpdateAvailable(version(1008))
            awaitSize(received, 1)
            val event = received[0] as UpdateEvent.UpdateAvailable
            assertEquals(1008, event.version.versionCode)
            assertEquals("1.1008.0", event.version.versionName)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `自动下载广播 AutoDownload 事件且载荷透传`() {
        val bus = UpdateEventBus()
        val (scope, received) = subscribe(bus)
        try {
            bus.notifyAutoDownload(version(1009))
            awaitSize(received, 1)
            val event = received[0] as UpdateEvent.AutoDownload
            assertEquals(1009, event.version.versionCode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `两类事件顺序投递不混淆`() {
        val bus = UpdateEventBus()
        val (scope, received) = subscribe(bus)
        try {
            bus.notifyUpdateAvailable(version(1))
            bus.notifyAutoDownload(version(2))
            awaitSize(received, 2)
            assertTrue(received[0] is UpdateEvent.UpdateAvailable)
            assertTrue(received[1] is UpdateEvent.AutoDownload)
        } finally {
            scope.cancel()
        }
    }

    // ==================== replay = 1 契约 ====================

    @Test
    fun `先发后订阅 迟到订阅者收到最近一次事件（启动检查早于页面订阅不丢事件）`() {
        val bus = UpdateEventBus()
        bus.notifyUpdateAvailable(version(1008)) // 订阅前发射：InitEngine 启动检查时序
        val (scope, received) = subscribe(bus)
        try {
            awaitSize(received, 1) // replay=1：迟到订阅立即重放最近事件
            assertEquals(1008, (received[0] as UpdateEvent.UpdateAvailable).version.versionCode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `连续两次发射 迟到订阅者只重放最近一次`() {
        val bus = UpdateEventBus()
        bus.notifyUpdateAvailable(version(1007))
        bus.notifyUpdateAvailable(version(1008))
        val (scope, received) = subscribe(bus)
        try {
            awaitSize(received, 1)
            assertEquals(1008, (received[0] as UpdateEvent.UpdateAvailable).version.versionCode)
        } finally {
            scope.cancel()
        }
    }

    // ==================== DROP_OLDEST 缓冲契约 ====================

    @Test
    fun `无订阅者高频发射 缓冲溢出丢最旧且重放保留最新`() {
        val bus = UpdateEventBus()
        // 无订阅者连续发射 20 条（> replay 1 + buffer 16）：DROP_OLDEST 丢弃最旧，不阻塞不崩溃
        (1..20).forEach { bus.notifyUpdateAvailable(version(it)) }
        val (scope, received) = subscribe(bus)
        try {
            awaitSize(received, 1)
            assertEquals(20, (received[0] as UpdateEvent.UpdateAvailable).version.versionCode)
        } finally {
            scope.cancel()
        }
    }
}
