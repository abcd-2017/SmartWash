package com.smartwash.feature.order.impl.ui.order

import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.order.api.model.ShowOrderStatus
import com.smartwash.feature.order.impl.network.api.OrderServiceApi
import com.smartwash.feature.order.impl.network.entity.order.OrderNextStatus
import com.smartwash.feature.order.impl.network.entity.order.ReservationLaundry
import com.smartwash.feature.order.impl.network.vo.order.OrderGroupVo
import com.smartwash.feature.order.impl.network.vo.order.OrderInfoVo
import com.smartwash.feature.order.impl.network.vo.order.OrderItemCountVo
import com.smartwash.feature.order.impl.network.vo.order.OrderVo
import com.smartwash.feature.order.impl.network.vo.review.ReviewSubmitRequest
import com.smartwash.feature.order.impl.network.vo.review.ReviewVo
import com.smartwash.feature.order.impl.repository.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 订单页 ViewModel 状态机 JVM 单测（真实 OrderRepository + 手写桩 OrderServiceApi，
 * Main 调度器注入测试调度器，虚拟时间推进）。
 *
 * 锚点契约（2026-11-06 N-05-02 单一 UiState 回归）：
 * - loadAllOrders 一次发射覆盖全部 tab：summary 缺席的 tab 补空列表 + hasMore=false；
 * - loadMore 追加下一页并按满页判定 hasMore（不足一页即无后续）；
 * - loadMore 防重入：hasMore=false 或 loadingMore=true 时不发请求。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** OrderServiceApi 手写桩：仅订单分组与列表可编程 */
    private class FakeOrderApi(
        private val group: Map<String, OrderGroupVo>,
        private val pagesByStatus: MutableMap<Pair<String, Int>, List<OrderInfoVo>> = mutableMapOf(),
    ) : OrderServiceApi {
        val listRequests = mutableListOf<Pair<String, Int>>() // (status, page)

        override suspend fun reservationLaundry(reservationLaundry: ReservationLaundry): ApiResult<Long> =
            ApiResult(200, "成功", null)

        override suspend fun getOrderInfo(orderId: Long): ApiResult<OrderInfoVo> =
            ApiResult(200, "成功", null)

        override suspend fun getOrderList(status: String, page: Int?, size: Int?): ApiResult<List<OrderInfoVo>> {
            listRequests += (status to (page ?: 1))
            return ApiResult(200, "成功", pagesByStatus[status to (page ?: 1)] ?: emptyList())
        }

        override suspend fun getOrderGroup(size: Int?): ApiResult<Map<String, OrderGroupVo>> =
            ApiResult(200, "成功", group)

        override suspend fun getOrderItemCount(
            pendingPaymentStatus: String,
            processingStatus: String,
            pendingPickupStatus: String,
            shippedStatus: String,
        ): ApiResult<OrderItemCountVo> = ApiResult(200, "成功", null)

        override suspend fun shippingOrder(orderNextStatus: OrderNextStatus): ApiResult<Boolean> =
            ApiResult(200, "成功", null)

        override suspend fun pickupOrder(orderNextStatus: OrderNextStatus): ApiResult<Boolean> =
            ApiResult(200, "成功", null)

        override suspend fun getWashingOrder(): ApiResult<List<OrderVo>> = ApiResult(200, "成功", null)

        override suspend fun cancelOrder(orderId: Long): ApiResult<Boolean> = ApiResult(200, "成功", null)

        override suspend fun calculationOrder(orderId: Long, userCouponId: Long): ApiResult<OrderInfoVo> =
            ApiResult(200, "成功", null)

        override suspend fun addReview(review: ReviewSubmitRequest): ApiResult<Boolean> =
            ApiResult(200, "成功", null)

        override suspend fun getOrderReviews(orderId: Long, page: Int, size: Int): ApiResult<PageData<ReviewVo>> =
            ApiResult(200, "成功", null)
    }

    private fun infoVo(orderId: Long, status: String = "3") = OrderInfoVo(
        orderId = orderId, orderNo = "NO-$orderId", status = status, pickupCode = null,
        userVo = null, schoolsVo = null, lockersVo = null, laundryPackageVo = null,
        totalPrice = null, payPrice = null, createdAt = null, updatedAt = null,
    )

    /** 构造 summary：status → (items, hasMore) */
    private fun groupOf(status: String, count: Int, hasMore: Boolean) =
        status to OrderGroupVo(
            items = (1..count).map { infoVo(orderId = status.toLong() * 1000 + it, status = status) },
            hasMore = hasMore,
            total = count,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `首刷一次发射覆盖全部 tab 缺席 tab 补空列表`() = runTest {
        val api = FakeOrderApi(group = mapOf(groupOf("0", count = 2, hasMore = true)))
        val viewModel = OrderViewModel(OrderRepository(api))
        runCurrent()

        assertEquals(RequestState.Success, viewModel.loadState.value)
        val state = viewModel.uiState.value
        // summary 只回了待支付 tab，其余 4 个 tab 补空列表
        assertEquals(2, state.orders["0"]?.size)
        ShowOrderStatus.entries
            .filter { it.status != "0" }
            .forEach { tab ->
                assertTrue("tab ${tab.status} 应补空列表", state.orders[tab.status].isNullOrEmpty())
                assertEquals("tab ${tab.status} 无后续页", false, state.hasMore[tab.status])
            }
        // 有数据的 tab hasMore 透传
        assertEquals(true, state.hasMore["0"])
        // 单一 UiState：orders 与 hasMore 键集一致（无发射间错位）
        assertEquals(state.orders.keys, state.hasMore.keys)
    }

    @Test
    fun `summary 请求失败进入 Error 态并透传信息`() = runTest {
        val failingApi = object : OrderServiceApi by FakeOrderApi(group = emptyMap()) {
            override suspend fun getOrderGroup(size: Int?): ApiResult<Map<String, OrderGroupVo>> =
                throw NetworkException("网络开小差了")
        }
        val viewModel = OrderViewModel(OrderRepository(failingApi))
        runCurrent()

        val error = viewModel.loadState.value as RequestState.Error
        assertEquals("网络开小差了", error.message)
        // 失败不发射数据
        assertTrue(viewModel.uiState.value.orders.isEmpty())
    }

    @Test
    fun `loadMore 追加下一页且不足一页判定无后续`() = runTest {
        val api = FakeOrderApi(
            group = mapOf(groupOf("6", count = 10, hasMore = true)),
            pagesByStatus = mutableMapOf(
                // 第二页只有 3 条（不足一页）→ hasMore 置 false
                "6" to 2 to (1L..3L).map { infoVo(orderId = 6000L + it, status = "6") },
            ),
        )
        val viewModel = OrderViewModel(OrderRepository(api))
        runCurrent()
        assertEquals(true, viewModel.uiState.value.hasMore["6"])

        viewModel.loadMore("6")
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(13, state.orders["6"]?.size) // 10 + 3
        assertEquals(false, state.hasMore["6"])
        assertEquals(listOf("6" to 2), api.listRequests)
        // loadingMore 复位
        assertEquals(false, state.loadingMore["6"])
    }

    @Test
    fun `loadMore 防重入 hasMore 为假时不发请求`() = runTest {
        val api = FakeOrderApi(group = mapOf(groupOf("0", count = 1, hasMore = false)))
        val viewModel = OrderViewModel(OrderRepository(api))
        runCurrent()

        viewModel.loadMore("0") // hasMore=false：直接 return，不发请求
        runCurrent()
        assertTrue(api.listRequests.isEmpty())
    }

    @Test
    fun `loadMore 防重入 进行中重复触发只发一次请求`() = runTest {
        // 第二页故意挂起较久（由测试调度器手动推进）：并发触发两次同页只发出一次请求
        val api = FakeOrderApi(
            group = mapOf(groupOf("6", count = 10, hasMore = true)),
            pagesByStatus = mutableMapOf(
                "6" to 2 to (1L..3L).map { infoVo(orderId = 6000L + it, status = "6") },
            ),
        )
        val viewModel = OrderViewModel(OrderRepository(api))
        runCurrent()

        viewModel.loadMore("6") // 置 loadingMore=true（同步），launch 排队
        viewModel.loadMore("6") // 看到进行中标记 → 被拦截
        runCurrent()            // 推进第一次的请求
        assertEquals(1, api.listRequests.size)
        assertEquals(13, viewModel.uiState.value.orders["6"]?.size)
    }
}
