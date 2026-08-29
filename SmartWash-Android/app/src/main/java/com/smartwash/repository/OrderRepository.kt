package com.smartwash.repository

import com.smartwash.R
import com.smartwash.network.api.OrderApi
import com.smartwash.network.entity.order.OrderItemCountFrom
import com.smartwash.network.entity.order.OrderNextStatus
import com.smartwash.network.entity.order.ReservationLaundry
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.network.vo.order.OrderGroupVo
import com.smartwash.network.vo.order.OrderItemCountVo
import com.smartwash.network.vo.order.OrderVo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    val orderApi: OrderApi,
) {
    suspend fun reservationLaundry(reservation: ReservationLaundry): Long {
        return orderApi.reservationLaundry(reservation).data ?: -1
    }

    suspend fun getOrderInfo(orderId: Long): OrderInfo {
        return orderApi.getOrderInfo(orderId).data
            ?: throw NetworkException("订单信息为空", R.string.error_network_fail)
    }

    suspend fun getOrderGroup(size: Int = 10): Map<String, OrderGroupVo> {
        // data 为 null 按失败处理，让 UI 能区分"无数据"与"请求失败"
        return orderApi.getOrderGroup(size).data
            ?: throw NetworkException("订单分组数据为空", R.string.error_network_fail)
    }

    suspend fun getOrderList(status: String, page: Int, size: Int = 10): List<OrderInfo> {
        return orderApi.getOrderList(status, page, size).data
            ?: throw NetworkException("订单列表数据为空", R.string.error_network_fail)
    }

    suspend fun getOrderItemCount(from: OrderItemCountFrom): OrderItemCountVo {
        return orderApi.getOrderItemCount(
            from.pendingPaymentStatus,
            from.processingStatus,
            from.pendingPickupStatus,
            from.shippedStatus,
        ).data ?: throw NetworkException("订单数量统计为空", R.string.error_network_fail)
    }

    suspend fun getWashingOrder(): List<OrderVo> {
        return orderApi.getWashingOrder().data
            ?: throw NetworkException("进行中订单数据为空", R.string.error_network_fail)
    }

    suspend fun cancelOrder(orderId: Long): Boolean {
        return orderApi.cancelOrder(orderId).data == true
    }

    suspend fun shippingOrder(orderNextStatus: OrderNextStatus): Boolean {
        return orderApi.shippingOrder(orderNextStatus).data == true
    }

    suspend fun pickupOrder(orderNextStatus: OrderNextStatus): Boolean {
        return orderApi.pickupOrder(orderNextStatus).data == true
    }

    suspend fun calculationOrder(orderId: Long, userCouponId: Long): OrderInfo {
        return orderApi.calculationOrder(orderId, userCouponId).data
            ?: throw NetworkException("订单计算结果为空", R.string.error_network_fail)
    }
}
