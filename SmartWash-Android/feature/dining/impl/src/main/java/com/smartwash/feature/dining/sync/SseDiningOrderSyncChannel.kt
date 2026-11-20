package com.smartwash.feature.dining.sync

import com.smartwash.common.network.sse.SseClient
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSE 实现的订单变更订阅通道（本期起为主传输，轮询为降级）。
 *
 * 语义与 [PollingDiningOrderSyncChannel] 对齐（[DiningOrderSyncChannel]）：
 * - 订阅即先拉一次详情（首帧与轮询同构）；
 * - 之后监听服务端 `changed` 信号，收到即按已知 version 回拉详情（复用差分/脱敏契约）；
 * - SSE 连续失败 [MAX_SSE_FAILURES] 次 → 委托轮询通道继续同步，保证可用性（降级不静默）；
 * - 服务端正常收尾（超时 complete）→ 自动重连；取消收集即断流（channelFlow 生产者协程随之取消）。
 *
 * 设计取舍：服务端只推「变更信号」（orderId），不回推全量详情——端上仍走既有 detail 协议，
 * 无需第二套解析，也避免 join_token 等字段经广播扩散。
 */
@Singleton
class SseDiningOrderSyncChannel @Inject constructor(
    private val source: DiningOrderDetailSource,
    private val sseClient: SseClient,
    private val pollFallback: PollingDiningOrderSyncChannel,
) : DiningOrderSyncChannel {

    override fun subscribe(orderId: Long, initialVersion: Long?): Flow<DiningOrderDetailVo> = channelFlow {
        if (orderId <= 0L) return@channelFlow
        var knownVersion = initialVersion

        // 首次立即拉取（订阅起点与轮询一致；失败不阻塞，等待变更信号再拉）
        runCatching { source.fetch(orderId, knownVersion) }
            .onSuccess { detail ->
                if (!detail.safeUnchanged) knownVersion = detail.safeVersion
                trySend(detail)
            }

        val signals = Channel<Long>(Channel.CONFLATED)
        var sseUnavailable = false
        val sseJob = launch {
            var consecutiveFailures = 0
            while (isActive) {
                val outcome = sseClient.get("$STREAM_PATH$orderId/stream") { name, _ ->
                    if (name == EVENT_CHANGED) signals.trySend(orderId)
                }
                when (outcome) {
                    // 服务端正常收尾（含 emitter 超时）：稍候重连，不计入失败
                    is SseClient.Outcome.Completed -> {
                        AppLogger.debug(TAG, "订单 SSE 收尾，准备重连 orderId=$orderId")
                        delay(RECONNECT_DELAY_MS)
                    }
                    else -> {
                        consecutiveFailures++
                        AppLogger.debug(TAG, "订单 SSE 连接异常 orderId=$orderId outcome=$outcome")
                        if (consecutiveFailures >= MAX_SSE_FAILURES) {
                            sseUnavailable = true
                            break
                        }
                        delay(backoffDelayMs(consecutiveFailures))
                    }
                }
            }
            signals.close()
        }

        for (id in signals) {
            runCatching { source.fetch(id, knownVersion) }
                .onSuccess { detail ->
                    if (!detail.safeUnchanged) knownVersion = detail.safeVersion
                    trySend(detail)
                }
        }
        sseJob.cancel()

        if (sseUnavailable) {
            AppLogger.info(TAG, "订单 SSE 持续不可用，降级轮询同步 orderId=$orderId")
            pollFallback.subscribe(orderId, knownVersion).collect { trySend(it) }
        }
    }

    private companion object {
        const val TAG = "DiningOrderSyncChannel"
        const val STREAM_PATH = "web/auth/dining/orders/"
        const val EVENT_CHANGED = "changed"

        /** SSE 连续失败达到该次数即降级轮询 */
        const val MAX_SSE_FAILURES = 3

        /** 服务端正常收尾后的重连间隔（防风暴） */
        const val RECONNECT_DELAY_MS = 1_000L

        /** 指数退避（2s 起，封顶 15s） */
        fun backoffDelayMs(failures: Int): Long = (2_000L * failures).coerceAtMost(15_000L)
    }
}
