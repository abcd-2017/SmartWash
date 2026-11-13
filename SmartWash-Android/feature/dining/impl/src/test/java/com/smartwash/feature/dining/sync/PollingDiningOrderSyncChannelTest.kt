package com.smartwash.feature.dining.sync

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * 轮询通道 JVM 单测（锚点）：以伪数据源驱动，验证 knownVersion 推进、unchanged 轻量不发射、
 * 连续失败退避后恢复。用测试调度器的虚拟时间，不依赖真实时钟。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PollingDiningOrderSyncChannelTest {

    @Test
    fun `全量发射并推进 knownVersion，unchanged 轻量发射`() = runTest {
        val source = FakeSource(
            listOf(
                full(version = 1L),
                unchanged(version = 1L),
                full(version = 2L),
            ),
        )
        val channel = PollingDiningOrderSyncChannel(source)
        val received = mutableListOf<Pair<Long, Boolean>>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            channel.subscribe(orderId = 1L, initialVersion = 0L).collect {
                received += it.safeVersion to it.safeUnchanged
            }
        }

        // 首次收集立即全量
        assertEquals(listOf(1L to false), received)
        assertEquals(listOf<Long?>(0L), source.requestedVersions)

        advanceTimeBy(3_000L)
        runCurrent()
        // 第二次 unchanged：轻量发射，knownVersion 不推进
        assertEquals(listOf(1L to false, 1L to true), received)
        assertEquals(listOf<Long?>(0L, 1L), source.requestedVersions)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(listOf(1L to false, 1L to true, 2L to false), received)
        assertEquals(listOf<Long?>(0L, 1L, 1L), source.requestedVersions)

        job.cancel()
    }

    @Test
    fun `连续失败退避后恢复`() = runTest {
        val source = ThrowThenDetailSource(failures = 2, detail = full(version = 1L))
        val channel = PollingDiningOrderSyncChannel(source)
        val received = mutableListOf<Long>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            channel.subscribe(orderId = 1L, initialVersion = 0L).collect { received += it.safeVersion }
        }

        // 第 1 次失败（t=0），退避 3s
        advanceTimeBy(3_000L)
        runCurrent()
        // 第 2 次失败（t=3000），退避 6s
        assertEquals(0, source.successes)
        advanceTimeBy(6_000L)
        runCurrent()
        // 第 3 次成功（t=9000）
        assertEquals(listOf(1L), received)
        assertEquals(9_000L, testScheduler.currentTime)

        job.cancel()
    }

    @Test
    fun `非法订单不发请求且立即完成`() = runTest {
        val source = FakeSource(emptyList())
        PollingDiningOrderSyncChannel(source).subscribe(orderId = 0L, initialVersion = null)
            .collect { }
        assertEquals(0, source.calls)
    }

    // ==================== 辅助 ====================

    private class FakeSource(private val responses: List<DiningOrderDetailVo>) : DiningOrderDetailSource {
        var calls = 0
            private set
        var successes = 0
            private set
        val requestedVersions = mutableListOf<Long?>()

        override suspend fun fetch(orderId: Long, knownVersion: Long?): DiningOrderDetailVo {
            requestedVersions += knownVersion
            val response = responses.getOrNull(calls++) ?: responses.last()
            successes += 1
            return response
        }
    }

    private class ThrowThenDetailSource(
        private val failures: Int,
        private val detail: DiningOrderDetailVo,
    ) : DiningOrderDetailSource {
        private var calls = 0
        var successes = 0
            private set

        override suspend fun fetch(orderId: Long, knownVersion: Long?): DiningOrderDetailVo {
            if (calls++ < failures) throw IOException("网络异常")
            successes += 1
            return detail
        }
    }

    private fun full(version: Long) = DiningOrderDetailVo(id = 1L, version = version, unchanged = false)

    private fun unchanged(version: Long) = DiningOrderDetailVo(id = 1L, version = version, unchanged = true)
}
