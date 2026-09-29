package com.smartwash.feature.order.impl.repository

import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.order.api.model.OrderBrief
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.OrderItemCount
import com.smartwash.feature.order.impl.R
import com.smartwash.feature.order.impl.network.api.OrderServiceApi
import com.smartwash.feature.order.impl.network.entity.order.OrderNextStatus
import com.smartwash.feature.order.impl.network.entity.order.ReservationLaundry
import com.smartwash.feature.order.impl.network.vo.order.OrderGroupVo
import com.smartwash.feature.order.impl.network.vo.order.toModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 订单域数据仓库（原 app 的 repository/OrderRepository，T6.1 迁入）。
 *
 * 行为对齐原实现（资金链路逻辑零改动）：data 为 null 按失败处理抛
 * [NetworkException]，让 UI 能区分"无数据"与"请求失败"。
 * 变化点：方法参数由请求体实体（ReservationLaundry/OrderNextStatus/OrderItemCountFrom）
 * 改为标量——实体仅在本层构造；返回值由网络 VO 改为 order-api 领域模型（VO→模型
 * 映射集中在本层）。原 OrderItemCountFrom/OrderListFrom 实体随签名简化失去消费者，
 * 未随迁（死代码，见 T6.1 报告）。
 */
@Singleton
class OrderRepository @Inject constructor(
    private val orderServiceApi: OrderServiceApi,
) {
    suspend fun reservationLaundry(itemsId: Long, totalPrice: Float): Long {
        return orderServiceApi.reservationLaundry(ReservationLaundry(itemsId, totalPrice)).data ?: -1
    }

    suspend fun getOrderInfo(orderId: Long): OrderInfo {
        return orderServiceApi.getOrderInfo(orderId).data?.toModel()
            ?: throw NetworkException("订单信息为空", R.string.error_network_fail)
    }

    suspend fun getOrderGroup(size: Int = 10): Map<String, OrderGroup> {
        // data 为 null 按失败处理，让 UI 能区分"无数据"与"请求失败"
        return orderServiceApi.getOrderGroup(size).data?.mapValues { (_, group) -> group.toModel() }
            ?: throw NetworkException("订单分组数据为空", R.string.error_network_fail)
    }

    suspend fun getOrderList(status: String, page: Int, size: Int = 10): List<OrderInfo> {
        return orderServiceApi.getOrderList(status, page, size).data?.map { it.toModel() }
            ?: throw NetworkException("订单列表数据为空", R.string.error_network_fail)
    }

    suspend fun getOrderItemCount(
        pendingPaymentStatus: String,
        processingStatus: String,
        pendingPickupStatus: String,
        shippedStatus: String,
    ): OrderItemCount {
        return orderServiceApi.getOrderItemCount(
            pendingPaymentStatus,
            processingStatus,
            pendingPickupStatus,
            shippedStatus,
        ).data?.toModel() ?: throw NetworkException("订单数量统计为空", R.string.error_network_fail)
    }

    suspend fun getWashingOrder(): List<OrderBrief> {
        return orderServiceApi.getWashingOrder().data?.map { it.toModel() }
            ?: throw NetworkException("进行中订单数据为空", R.string.error_network_fail)
    }

    suspend fun cancelOrder(orderId: Long): Boolean {
        return orderServiceApi.cancelOrder(orderId).data == true
    }

    suspend fun shippingOrder(orderId: Long, pickupCode: String): Boolean {
        return orderServiceApi.shippingOrder(OrderNextStatus(orderId, pickupCode)).data == true
    }

    suspend fun pickupOrder(orderId: Long, pickupCode: String): Boolean {
        return orderServiceApi.pickupOrder(OrderNextStatus(orderId, pickupCode)).data == true
    }

    suspend fun calculationOrder(orderId: Long, userCouponId: Long): OrderInfo {
        return orderServiceApi.calculationOrder(orderId, userCouponId).data?.toModel()
            ?: throw NetworkException("订单计算结果为空", R.string.error_network_fail)
    }

    private fun OrderGroupVo.toModel() = OrderGroup(
        items = items.map { it.toModel() },
        hasMore = hasMore,
        total = total,
    )
}
