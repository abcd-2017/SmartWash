package com.smartwash.repository

import com.smartwash.network.api.OrderApi
import com.smartwash.network.entity.order.OrderItemCountFrom
import com.smartwash.network.entity.order.OrderNextStatus
import com.smartwash.network.entity.order.ReservationLaundry
import com.smartwash.network.vo.order.OrderInfo
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
        return orderApi.getOrderInfo(orderId).data!!
    }

    suspend fun getOrderItemCount(from: OrderItemCountFrom): OrderItemCountVo {
        return orderApi.getOrderItemCount(
            from.pendingPaymentStatus,
            from.processingStatus,
            from.pendingPickupStatus,
            from.shippedStatus,
        ).data!!
    }

    suspend fun getWashingOrder(): List<OrderVo> {
        return orderApi.getWashingOrder().data ?: emptyList()
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
        return orderApi.calculationOrder(orderId, userCouponId).data!!
    }
}
