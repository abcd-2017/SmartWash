package com.smartwash.feature.dining.sync

import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.logic.DiningPollScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轮询实现的订单变更订阅通道（首实现）。
 *
 * 收集期间：立即拉一次 → 成功按基准间隔（约 3s）轮询 → 连续失败指数退避（封顶 15s）。
 * 带 knownVersion 的轻量协议：无变化时发射 unchanged=true 的轻量响应（不推进 knownVersion），
 * 订阅方据此收敛新鲜度/执行挂起回滚，不重建页面。
 * 取消收集即停止（无后台引用，防泄露）；可见性由订阅方（VM 生命周期状态机）启停收集表达。
 */
@Singleton
class PollingDiningOrderSyncChannel @Inject constructor(
    private val source: DiningOrderDetailSource,
) : DiningOrderSyncChannel {

    override fun subscribe(orderId: Long, initialVersion: Long?): Flow<DiningOrderDetailVo> = flow {
        if (orderId <= 0L) return@flow
        val scheduler = DiningPollScheduler()
        var knownVersion = initialVersion
        while (currentCoroutineContext().isActive) {
            try {
                val detail = source.fetch(orderId, knownVersion)
                scheduler.onSuccess()
                if (!detail.safeUnchanged) knownVersion = detail.safeVersion
                emit(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scheduler.onFailure()
                // 轮询是高频路径，失败只 debug（对齐 Delivery 轮询退避日志级别）
                AppLogger.debug(TAG, "订单详情轮询失败 orderId=$orderId: ${e.message}")
            }
            delay(scheduler.nextDelayMs())
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningOrderSyncChannel"
    }
}
