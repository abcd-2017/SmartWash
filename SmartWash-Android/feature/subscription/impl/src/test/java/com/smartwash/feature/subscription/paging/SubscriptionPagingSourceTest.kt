package com.smartwash.feature.subscription.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.subscription.api.network.SubscriptionVo
import com.smartwash.feature.subscription.repository.SubscriptionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 订阅列表分页源 JVM 单测（手写桩实现 SubscriptionRepository）。
 *
 * 锚点契约：CancellationException 先 rethrow（Compose 硬规则：取消不作错误），其余异常
 * 转 PagingSource.LoadResult.Error；空页结束分页（nextKey = null），非空页继续（即使不满页——本源
 * 不以「条数不足一页」短路，与 OrderPagingSource 的满页判定策略不同）。
 */
class SubscriptionPagingSourceTest {

    /** SubscriptionRepository 手写桩：仅 pageSubscriptions 有行为，其余端点不可达 */
    private class FakeSubscriptionRepository(
        private val pageData: PageData<SubscriptionVo>? = null,
        private val error: Exception? = null,
    ) : SubscriptionRepository {

        var calls = 0
            private set
        val requestedPages = mutableListOf<Int>()
        val requestedSizes = mutableListOf<Int>()
        val requestedStatuses = mutableListOf<String?>()

        override suspend fun pageSubscriptions(page: Int, size: Int, status: String?): PageData<SubscriptionVo> {
            calls++
            requestedPages += page
            requestedSizes += size
            requestedStatuses += status
            error?.let { throw it }
            return pageData ?: PageData(records = emptyList(), total = 0, size = size, current = page)
        }

        override suspend fun createSubscription(request: com.smartwash.feature.subscription.api.network.SaveSubscriptionRequest): SubscriptionVo =
            throw UnsupportedOperationException()

        override suspend fun updateSubscription(id: Long, request: com.smartwash.feature.subscription.api.network.UpdateSubscriptionRequest): SubscriptionVo =
            throw UnsupportedOperationException()

        override suspend fun deleteSubscription(id: Long) = throw UnsupportedOperationException()

        override suspend fun getSubscription(id: Long): SubscriptionVo = throw UnsupportedOperationException()

        override suspend fun pauseSubscription(id: Long) = throw UnsupportedOperationException()

        override suspend fun resumeSubscription(id: Long) = throw UnsupportedOperationException()

        override suspend fun cancelSubscription(id: Long) = throw UnsupportedOperationException()

        override suspend fun finishSubscription(id: Long) = throw UnsupportedOperationException()

        override suspend fun checkInUsage(id: Long, request: com.smartwash.feature.subscription.api.network.AddUsageRequest) =
            throw UnsupportedOperationException()

        override suspend fun pageUsages(id: Long, page: Int, size: Int): PageData<com.smartwash.feature.subscription.api.network.UsageLogVo> =
            throw UnsupportedOperationException()

        override suspend fun deleteUsage(id: Long, usageId: Long) = throw UnsupportedOperationException()

        override suspend fun listReminders(unreadOnly: Boolean?): List<com.smartwash.feature.subscription.api.network.ReminderVo> =
            throw UnsupportedOperationException()

        override suspend fun markReminderRead(reminderId: Long) = throw UnsupportedOperationException()

        override suspend fun getStats(): com.smartwash.feature.subscription.api.network.SubscriptionStatsVo =
            throw UnsupportedOperationException()
    }

    private fun vo(id: Long) = SubscriptionVo(subscriptionId = id, name = "订阅 $id")

    private suspend fun load(source: PagingSource<Int, SubscriptionVo>, key: Int?): PagingSource.LoadResult<Int, SubscriptionVo> =
        source.load(PagingSource.LoadParams.Refresh(key = key, loadSize = 10, placeholdersEnabled = false))

    private fun state(anchor: Int?) = PagingState<Int, SubscriptionVo>(
        pages = emptyList(), anchorPosition = anchor, config = PagingConfig(10),
        leadingPlaceholderCount = 0,
    )

    @Test
    fun `首页加载 满页数据 nextKey 推进`() = runTest {
        val records = (1L..10L).map(::vo)
        val repo = FakeSubscriptionRepository(PageData(records, total = 25, size = 10, current = 1))
        val source = SubscriptionPagingSource(repo, status = null)

        val result = load(source, key = null) as PagingSource.LoadResult.Page

        assertEquals(records, result.data)
        assertEquals(null, result.prevKey) // 首页 prevKey 为 null
        assertEquals(2, result.nextKey)
        assertEquals(listOf(1), repo.requestedPages)
        assertEquals(listOf(10), repo.requestedSizes) // 固定 PAGE_SIZE = 10
        assertEquals(listOf<String?>(null), repo.requestedStatuses)
    }

    @Test
    fun `第二页 prevKey 回指 上一页`() = runTest {
        val records = (11L..20L).map(::vo)
        val repo = FakeSubscriptionRepository(PageData(records, total = 25, size = 10, current = 2))
        val source = SubscriptionPagingSource(repo, status = "active")

        val result = load(source, key = 2) as PagingSource.LoadResult.Page

        assertEquals(1, result.prevKey)
        assertEquals(3, result.nextKey)
        assertEquals(listOf(2), repo.requestedPages)
        assertEquals(listOf<String?>("active"), repo.requestedStatuses) // 状态筛选透传
    }

    @Test
    fun `空页结束分页 nextKey 为 null`() = runTest {
        val repo = FakeSubscriptionRepository(PageData(emptyList(), total = 0, size = 10, current = 3))
        val source = SubscriptionPagingSource(repo, status = null)

        val result = load(source, key = 3) as PagingSource.LoadResult.Page

        assertTrue(result.data.isEmpty())
        assertEquals(2, result.prevKey)
        assertEquals(null, result.nextKey)
    }

    @Test
    fun `不满页仍继续分页（本源不短路）`() = runTest {
        // 与 OrderPagingSource「条数不足一页即结束」策略不同：只要非空就给 nextKey
        val records = (1L..3L).map(::vo)
        val repo = FakeSubscriptionRepository(PageData(records, total = 3, size = 10, current = 1))
        val source = SubscriptionPagingSource(repo, status = null)

        val result = load(source, key = null) as PagingSource.LoadResult.Page

        assertEquals(3, result.data.size)
        assertEquals(2, result.nextKey)
    }

    @Test
    fun `加载异常转 LoadResult Error`() = runTest {
        val repo = FakeSubscriptionRepository(error = IOException("网络异常"))
        val source = SubscriptionPagingSource(repo, status = null)

        val result = load(source, key = 1)

        assertTrue(result is PagingSource.LoadResult.Error)
        assertEquals("网络异常", (result as PagingSource.LoadResult.Error).throwable.message)
    }

    @Test
    fun `取消异常直接 rethrow 不转 Error`() = runTest {
        val repo = FakeSubscriptionRepository(error = CancellationException("页面销毁"))
        val source = SubscriptionPagingSource(repo, status = null)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { load(source, key = 1) }
        }
    }

    @Test
    fun `getRefreshKey 返回锚点位置`() {
        val repo = FakeSubscriptionRepository()
        val source = SubscriptionPagingSource(repo, status = null)
        // 本源 refresh key = anchorPosition 原值
        assertEquals(35, source.getRefreshKey(state(anchor = 35)))
        assertEquals(null, source.getRefreshKey(state(anchor = null)))
    }
}
