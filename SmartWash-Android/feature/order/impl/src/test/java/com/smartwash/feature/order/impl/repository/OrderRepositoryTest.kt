package com.smartwash.feature.order.impl.repository

import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.order.impl.network.api.OrderServiceApi
import com.smartwash.feature.order.impl.network.entity.order.OrderNextStatus
import com.smartwash.feature.order.impl.network.entity.order.ReservationLaundry
import com.smartwash.feature.order.impl.network.vo.order.OrderGroupVo
import com.smartwash.feature.order.impl.network.vo.order.OrderInfoVo
import com.smartwash.feature.order.impl.network.vo.order.OrderItemCountVo
import com.smartwash.feature.order.impl.network.vo.order.OrderVo
import com.smartwash.feature.order.impl.network.vo.review.ReviewSubmitRequest
import com.smartwash.feature.order.impl.network.vo.review.ReviewVo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 订单域数据仓库 JVM 单测（手写桩实现 OrderServiceApi，无 Mockito）。
 *
 * 锚点契约（资金链路语义，行为零改动回归锚）：
 * - 读操作 data 为 null 按失败处理抛 NetworkException，让 UI 区分「无数据」与「请求失败」；
 * - 预约下单 data 缺失降级返回 -1（不抛，调用方按预约失败处理）；
 * - 取消/寄件/取件返回后端确认布尔（code=200 但 data 非 true = 业务拒绝/幂等命中，如实透传）；
 * - 评价提交 content 空白收敛为 null（后端 AddReviewFrom content 可空）；
 * - 评价列表 data 缺失按空页处理（不抛）。
 */
class OrderRepositoryTest {

    /** OrderServiceApi 手写桩：各端点可编程返回值 + 请求体捕获 */
    private class FakeOrderServiceApi : OrderServiceApi {
        var reservationResult: ApiResult<Long> = ApiResult(200, "成功", null)
        var orderInfoResult: ApiResult<OrderInfoVo> = ApiResult(200, "成功", null)
        var orderGroupResult: ApiResult<Map<String, OrderGroupVo>> = ApiResult(200, "成功", null)
        var orderListResult: ApiResult<List<OrderInfoVo>> = ApiResult(200, "成功", null)
        var itemCountResult: ApiResult<OrderItemCountVo> = ApiResult(200, "成功", null)
        var washingResult: ApiResult<List<OrderVo>> = ApiResult(200, "成功", null)
        var boolResult: ApiResult<Boolean> = ApiResult(200, "成功", null)
        var calculationResult: ApiResult<OrderInfoVo> = ApiResult(200, "成功", null)
        var addReviewResult: ApiResult<Boolean> = ApiResult(200, "成功", null)
        var reviewsResult: ApiResult<PageData<ReviewVo>> = ApiResult(200, "成功", null)

        val reservations = mutableListOf<ReservationLaundry>()
        val shippingRequests = mutableListOf<OrderNextStatus>()
        val pickupRequests = mutableListOf<OrderNextStatus>()
        val cancelIds = mutableListOf<Long>()
        val reviewRequests = mutableListOf<ReviewSubmitRequest>()
        var reviewListCalls = 0
            private set

        override suspend fun reservationLaundry(reservationLaundry: ReservationLaundry): ApiResult<Long> {
            reservations += reservationLaundry
            return reservationResult
        }

        override suspend fun getOrderInfo(orderId: Long): ApiResult<OrderInfoVo> = orderInfoResult

        override suspend fun getOrderList(status: String, page: Int?, size: Int?): ApiResult<List<OrderInfoVo>> =
            orderListResult

        override suspend fun getOrderGroup(size: Int?): ApiResult<Map<String, OrderGroupVo>> = orderGroupResult

        override suspend fun getOrderItemCount(
            pendingPaymentStatus: String,
            processingStatus: String,
            pendingPickupStatus: String,
            shippedStatus: String,
        ): ApiResult<OrderItemCountVo> = itemCountResult

        override suspend fun shippingOrder(orderNextStatus: OrderNextStatus): ApiResult<Boolean> {
            shippingRequests += orderNextStatus
            return boolResult
        }

        override suspend fun pickupOrder(orderNextStatus: OrderNextStatus): ApiResult<Boolean> {
            pickupRequests += orderNextStatus
            return boolResult
        }

        override suspend fun getWashingOrder(): ApiResult<List<OrderVo>> = washingResult

        override suspend fun cancelOrder(orderId: Long): ApiResult<Boolean> {
            cancelIds += orderId
            return boolResult
        }

        override suspend fun calculationOrder(orderId: Long, userCouponId: Long): ApiResult<OrderInfoVo> =
            calculationResult

        override suspend fun addReview(review: ReviewSubmitRequest): ApiResult<Boolean> {
            reviewRequests += review
            return addReviewResult
        }

        override suspend fun getOrderReviews(orderId: Long, page: Int, size: Int): ApiResult<PageData<ReviewVo>> {
            reviewListCalls++
            return reviewsResult
        }
    }

    private fun infoVo(orderId: Long = 1L, status: String = "3") = OrderInfoVo(
        orderId = orderId, orderNo = "NO-$orderId", status = status, pickupCode = "112233",
        userVo = null, schoolsVo = null, lockersVo = null, laundryPackageVo = null,
        totalPrice = 15f, payPrice = 15f, createdAt = "c", updatedAt = "u",
    )

    // ==================== 预约下单 ====================

    @Test
    fun `预约下单透传请求体并返回新订单号`() = runTest {
        val api = FakeOrderServiceApi().apply { reservationResult = ApiResult(200, "成功", 66L) }
        val orderId = OrderRepository(api).reservationLaundry(itemsId = 9L, totalPrice = 15f)
        assertEquals(66L, orderId)
        assertEquals(listOf(ReservationLaundry(itemsId = 9L, totalPrice = 15f)), api.reservations)
    }

    @Test
    fun `预约下单响应缺失降级返回 -1 不抛`() = runTest {
        // 契约：后端未返回新订单 id 按预约失败降级（调用方据此走失败反馈，不视为异常）
        val api = FakeOrderServiceApi()
        assertEquals(-1L, OrderRepository(api).reservationLaundry(itemsId = 9L, totalPrice = 15f))
    }

    // ==================== 读操作：data 缺失按失败上抛 ====================

    @Test
    fun `订单详情 data 缺失抛 NetworkException`() = runTest {
        val api = FakeOrderServiceApi()
        val ex = assertThrows(NetworkException::class.java) {
            kotlinx.coroutines.runBlocking { OrderRepository(api).getOrderInfo(orderId = 1L) }
        }
        assertEquals("订单信息为空", ex.message)
    }

    @Test
    fun `订单分组映射为领域模型且 data 缺失抛 NetworkException`() = runTest {
        val api = FakeOrderServiceApi().apply {
            orderGroupResult = ApiResult(200, "成功", mapOf("0" to OrderGroupVo(listOf(infoVo()), hasMore = false, total = 1)))
        }
        val groups = OrderRepository(api).getOrderGroup()
        assertEquals(1, groups.size)
        assertEquals(1, groups["0"]?.items?.size)
        assertEquals("NO-1", groups["0"]?.items?.first()?.orderNo)
        assertEquals(false, groups["0"]?.hasMore)

        val emptyApi = FakeOrderServiceApi()
        val ex = assertThrows(NetworkException::class.java) {
            kotlinx.coroutines.runBlocking { OrderRepository(emptyApi).getOrderGroup() }
        }
        assertEquals("订单分组数据为空", ex.message)
    }

    @Test
    fun `订单列表 data 缺失抛 NetworkException`() = runTest {
        val api = FakeOrderServiceApi().apply {
            orderListResult = ApiResult(200, "成功", listOf(infoVo(1L), infoVo(2L)))
        }
        val list = OrderRepository(api).getOrderList(status = "0", page = 1)
        assertEquals(listOf(1L, 2L), list.map { it.orderId })

        val emptyApi = FakeOrderServiceApi()
        assertThrows(NetworkException::class.java) {
            kotlinx.coroutines.runBlocking { OrderRepository(emptyApi).getOrderList(status = "0", page = 1) }
        }
    }

    @Test
    fun `订单计数与进行中订单 data 缺失抛 NetworkException`() = runTest {
        val countApi = FakeOrderServiceApi().apply {
            itemCountResult = ApiResult(200, "成功", OrderItemCountVo(2, 3, 1, 4))
        }
        val count = OrderRepository(countApi).getOrderItemCount("0", "3", "6", "5")
        assertEquals(2, count.pendingPaymentCount)
        assertEquals(3, count.processingCount)
        assertEquals(1, count.pendingPickupCount)
        assertEquals(4, count.shippedCount)

        assertThrows(NetworkException::class.java) {
            kotlinx.coroutines.runBlocking { OrderRepository(FakeOrderServiceApi()).getOrderItemCount("0", "3", "6", "5") }
        }
        assertThrows(NetworkException::class.java) {
            kotlinx.coroutines.runBlocking { OrderRepository(FakeOrderServiceApi()).getWashingOrder() }
        }
    }

    @Test
    fun `进行中订单 pickupCode 缺失收敛为空串`() = runTest {
        val api = FakeOrderServiceApi().apply {
            washingResult = ApiResult(200, "成功", listOf(OrderVo(orderId = 1L, orderNo = "NO-1", status = "3", pickupCode = null)))
        }
        val list = OrderRepository(api).getWashingOrder()
        assertEquals("", list.single().pickupCode)
    }

    // ==================== 订单流转（资金链路语义） ====================

    @Test
    fun `取消订单透传后端确认布尔`() = runTest {
        val accepted = FakeOrderServiceApi().apply { boolResult = ApiResult(200, "成功", true) }
        assertEquals(true, OrderRepository(accepted).cancelOrder(orderId = 7L))
        assertEquals(listOf(7L), accepted.cancelIds)

        // code=200 但 data 非 true：业务拒绝或幂等命中，如实透传 false
        val rejected = FakeOrderServiceApi().apply { boolResult = ApiResult(200, "成功", false) }
        assertEquals(false, OrderRepository(rejected).cancelOrder(orderId = 7L))

        val missing = FakeOrderServiceApi()
        assertEquals(false, OrderRepository(missing).cancelOrder(orderId = 7L))
    }

    @Test
    fun `寄件与取件透传订单号与取件码请求体`() = runTest {
        val api = FakeOrderServiceApi().apply { boolResult = ApiResult(200, "成功", true) }
        val repo = OrderRepository(api)
        assertTrue(repo.shippingOrder(orderId = 7L, pickupCode = "112233"))
        assertTrue(repo.pickupOrder(orderId = 7L, pickupCode = "112233"))
        assertEquals(listOf(OrderNextStatus(orderId = 7L, pickupCode = "112233")), api.shippingRequests)
        assertEquals(listOf(OrderNextStatus(orderId = 7L, pickupCode = "112233")), api.pickupRequests)
    }

    // ==================== 优惠券试算与评价 ====================

    @Test
    fun `优惠券试算映射订单模型 data 缺失抛 NetworkException`() = runTest {
        val api = FakeOrderServiceApi().apply { calculationResult = ApiResult(200, "成功", infoVo()) }
        val model = OrderRepository(api).calculationOrder(orderId = 1L, userCouponId = 5L)
        assertEquals(1L, model.orderId)

        assertThrows(NetworkException::class.java) {
            kotlinx.coroutines.runBlocking { OrderRepository(FakeOrderServiceApi()).calculationOrder(1L, 5L) }
        }
    }

    @Test
    fun `评价提交 content 空白收敛为 null`() = runTest {
        val api = FakeOrderServiceApi().apply { addReviewResult = ApiResult(200, "成功", true) }
        assertTrue(OrderRepository(api).submitReview(orderId = 1L, rating = 4, content = "   "))
        assertNull(api.reviewRequests.single().content) // 空白 → null（后端 content 可空契约）

        val api2 = FakeOrderServiceApi().apply { addReviewResult = ApiResult(200, "成功", true) }
        OrderRepository(api2).submitReview(orderId = 1L, rating = 3, content = "干净")
        assertEquals("干净", api2.reviewRequests.single().content)
    }

    @Test
    fun `评价列表 data 缺失按空列表降级 不抛`() = runTest {
        val api = FakeOrderServiceApi()
        val list = OrderRepository(api).getOrderReviews(orderId = 1L, page = 1, size = 10)
        assertTrue(list.isEmpty())
        assertEquals(1, api.reviewListCalls)
    }
}
