package com.smartwash.feature.dining.sync

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import kotlinx.coroutines.flow.Flow

/**
 * 订单变更订阅通道（传输层可替换抽象，spec「同步机制（可替换传输层）」）。
 *
 * 语义：订阅某订单的变更——定期/按需获取最新详情，并以流的形式通知订阅者。
 * 首个实现为轮询（[PollingDiningOrderSyncChannel]）；后续可换 SSE/WebSocket 而不动业务代码
 * （ViewModel 只依赖本接口，不感知轮询细节）。
 *
 * 约定：
 * - 返回冷 Flow：每次收集独立拉取；取消收集即停止订阅（传输层据此释放资源，防泄露）；
 * - [initialVersion] 为已知版本（null=首次全量；非 null=带 knownVersion 的轻量协议）；
 * - 每次服务端响应都发射：详情变更时发射全量快照（[DiningOrderDetailVo.safeUnchanged]=false）；
 *   无变化时发射轻量响应（safeUnchanged=true，详情字段为 null）——订阅方据此只推进新鲜度/
 *   执行挂起回滚，不重建页面，避免无变化时也要解析全量载荷。
 */
interface DiningOrderSyncChannel {
    fun subscribe(orderId: Long, initialVersion: Long? = null): Flow<DiningOrderDetailVo>
}

/**
 * 订单详情读取窄契约（通道的数据源接缝）：只暴露通道所需的最小读取面，
 * 便于单测以伪实现驱动轮询调度，也避免通道直接依赖整个仓库。
 */
fun interface DiningOrderDetailSource {
    /** 拉取订单详情（[knownVersion] 为 null=首次全量；非 null=带 knownVersion 的轻量协议） */
    suspend fun fetch(orderId: Long, knownVersion: Long?): DiningOrderDetailVo
}
