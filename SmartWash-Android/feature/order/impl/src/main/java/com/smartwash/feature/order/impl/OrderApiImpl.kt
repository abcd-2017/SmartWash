package com.smartwash.feature.order.impl

import androidx.paging.PagingSource
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderBrief
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.OrderItemCount
import com.smartwash.feature.order.impl.network.api.OrderServiceApi
import com.smartwash.feature.order.impl.paging.OrderPagingSource
import com.smartwash.feature.order.impl.repository.OrderRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OrderApi] 实现（T6.1，api/impl 服务化样板的 impl 侧门面，模式同 user-impl 的 UserApiImpl）。
 *
 * 纯委托 [OrderRepository]（业务语义与错误处理全部在 Repository 层，本类零逻辑）；
 * 分页数据源直连 [OrderServiceApi] 构造（保持原 OrderPagingSource"data 为 null 按空页"
 * 的静默结束语义）。消费 user-api 经接口——T6.1 调研结论：订单域现状无 isLogin/
 * getUserInfo 调用点，暂无 UserApi 注入需求（后续有需求时经构造注入，不得触碰 SessionManager）。
 */
@Singleton
class OrderApiImpl @Inject constructor(
    private val orderRepository: OrderRepository,
    private val orderServiceApi: OrderServiceApi,
) : OrderApi {

    override suspend fun reservationLaundry(itemsId: Long, totalPrice: Float): Long =
        orderRepository.reservationLaundry(itemsId, totalPrice)

    override suspend fun getOrderInfo(orderId: Long): OrderInfo =
        orderRepository.getOrderInfo(orderId)

    override suspend fun calculationOrder(orderId: Long, userCouponId: Long): OrderInfo =
        orderRepository.calculationOrder(orderId, userCouponId)

    override suspend fun getWashingOrder(): List<OrderBrief> =
        orderRepository.getWashingOrder()

    override suspend fun getOrderItemCount(
        pendingPaymentStatus: String,
        processingStatus: String,
        pendingPickupStatus: String,
        shippedStatus: String,
    ): OrderItemCount = orderRepository.getOrderItemCount(
        pendingPaymentStatus,
        processingStatus,
        pendingPickupStatus,
        shippedStatus,
    )

    override suspend fun shippingOrder(orderId: Long, pickupCode: String): Boolean =
        orderRepository.shippingOrder(orderId, pickupCode)

    override suspend fun pickupOrder(orderId: Long, pickupCode: String): Boolean =
        orderRepository.pickupOrder(orderId, pickupCode)

    override fun orderPagingSource(status: String): PagingSource<Int, OrderInfo> =
        OrderPagingSource(orderServiceApi, status)
}
