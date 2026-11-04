package com.smartwash.feature.order.impl.repository

import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
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
 *
 * 日志留痕（阶段五 A1）：订单流转写操作（下单/取消/寄件/取件）的成败在本层统一
 * info/warn——消费方分布在 laundry:impl 与 app 壳，本层是唯一收口；读操作
 * data 为 null 的空态降级打 warn（带业务键，与调用方 catch 的 error 形成层次而非重复）。
 * 外部调用失败（网络异常/业务码 500）由 :common:network ResponseInterceptor 统一留痕，
 * 本层不重复 catch。
 */
@Singleton
class OrderRepository @Inject constructor(
    private val orderServiceApi: OrderServiceApi,
) {
    suspend fun reservationLaundry(itemsId: Long, totalPrice: Float): Long {
        val orderId = orderServiceApi.reservationLaundry(ReservationLaundry(itemsId, totalPrice)).data
        if (orderId == null) {
            AppLogger.warn(TAG, "reservationLaundry: 后端未返回新订单 id，按预约失败降级 itemsId=$itemsId")
            return -1
        }
        AppLogger.info(TAG, "reservationLaundry: 预约下单成功 orderId=$orderId itemsId=$itemsId")
        return orderId
    }

    suspend fun getOrderInfo(orderId: Long): OrderInfo {
        val result = orderServiceApi.getOrderInfo(orderId)
        if (result.data == null) {
            AppLogger.warn(TAG, "getOrderInfo: 订单数据为空，按失败降级 orderId=$orderId")
        }
        return result.data?.toModel()
            ?: throw NetworkException("订单信息为空", R.string.error_network_fail)
    }

    suspend fun getOrderGroup(size: Int = 10): Map<String, OrderGroup> {
        // data 为 null 按失败处理，让 UI 能区分"无数据"与"请求失败"
        val result = orderServiceApi.getOrderGroup(size)
        if (result.data == null) {
            AppLogger.warn(TAG, "getOrderGroup: 订单分组数据为空，按失败降级 size=$size")
        }
        return result.data?.mapValues { (_, group) -> group.toModel() }
            ?: throw NetworkException("订单分组数据为空", R.string.error_network_fail)
    }

    suspend fun getOrderList(status: String, page: Int, size: Int = 10): List<OrderInfo> {
        val result = orderServiceApi.getOrderList(status, page, size)
        if (result.data == null) {
            AppLogger.warn(TAG, "getOrderList: 订单列表数据为空，按失败降级 status=$status page=$page")
        }
        return result.data?.map { it.toModel() }
            ?: throw NetworkException("订单列表数据为空", R.string.error_network_fail)
    }

    suspend fun getOrderItemCount(
        pendingPaymentStatus: String,
        processingStatus: String,
        pendingPickupStatus: String,
        shippedStatus: String,
    ): OrderItemCount {
        val result = orderServiceApi.getOrderItemCount(
            pendingPaymentStatus,
            processingStatus,
            pendingPickupStatus,
            shippedStatus,
        )
        if (result.data == null) {
            AppLogger.warn(TAG, "getOrderItemCount: 订单数量统计数据为空，按失败降级")
        }
        return result.data?.toModel() ?: throw NetworkException("订单数量统计为空", R.string.error_network_fail)
    }

    suspend fun getWashingOrder(): List<OrderBrief> {
        val result = orderServiceApi.getWashingOrder()
        if (result.data == null) {
            AppLogger.warn(TAG, "getWashingOrder: 进行中订单数据为空，按失败降级")
        }
        return result.data?.map { it.toModel() }
            ?: throw NetworkException("进行中订单数据为空", R.string.error_network_fail)
    }

    suspend fun cancelOrder(orderId: Long): Boolean {
        val accepted = orderServiceApi.cancelOrder(orderId).data == true
        if (accepted) {
            AppLogger.info(TAG, "cancelOrder: 订单取消成功 orderId=$orderId")
        } else {
            // code=200 但 data 非 true：业务拒绝或幂等命中（订单已是取消态），UI 侧仍按成功反馈
            AppLogger.warn(TAG, "cancelOrder: 后端未确认取消（业务拒绝/幂等命中）orderId=$orderId")
        }
        return accepted
    }

    suspend fun shippingOrder(orderId: Long, pickupCode: String): Boolean {
        // pickupCode 为取件码（敏感信息），只作为请求参数，不入日志
        val accepted = orderServiceApi.shippingOrder(OrderNextStatus(orderId, pickupCode)).data == true
        if (accepted) {
            AppLogger.info(TAG, "shippingOrder: 订单寄件流转成功 orderId=$orderId")
        } else {
            AppLogger.warn(TAG, "shippingOrder: 后端未确认寄件（业务拒绝/幂等命中）orderId=$orderId")
        }
        return accepted
    }

    suspend fun pickupOrder(orderId: Long, pickupCode: String): Boolean {
        // pickupCode 为取件码（敏感信息），只作为请求参数，不入日志
        val accepted = orderServiceApi.pickupOrder(OrderNextStatus(orderId, pickupCode)).data == true
        if (accepted) {
            AppLogger.info(TAG, "pickupOrder: 订单取件完成 orderId=$orderId")
        } else {
            AppLogger.warn(TAG, "pickupOrder: 后端未确认取件（业务拒绝/幂等命中）orderId=$orderId")
        }
        return accepted
    }

    suspend fun calculationOrder(orderId: Long, userCouponId: Long): OrderInfo {
        val result = orderServiceApi.calculationOrder(orderId, userCouponId)
        if (result.data == null) {
            AppLogger.warn(TAG, "calculationOrder: 优惠券试算数据为空，按失败降级 orderId=$orderId userCouponId=$userCouponId")
        }
        return result.data?.toModel()
            ?: throw NetworkException("订单计算结果为空", R.string.error_network_fail)
    }

    private fun OrderGroupVo.toModel() = OrderGroup(
        items = items.map { it.toModel() },
        hasMore = hasMore,
        total = total,
    )

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "OrderRepository"
    }
}
