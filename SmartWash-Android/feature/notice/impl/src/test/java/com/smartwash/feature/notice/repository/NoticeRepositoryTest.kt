package com.smartwash.feature.notice.repository

import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.notice.api.model.NoticeVo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 站内信仓库 JVM 单测（手写桩实现 NoticeServiceApi，无 Mockito）。
 *
 * 锚点契约：data 为 null 统一按失败上抛 NetworkException，让上层区分「无数据」与
 * 「请求失败」；唯一例外是未读数——次要点缀数据，data 缺失按 0 处理（角标隐藏），
 * 不让徽章请求失败影响我的页主流程。
 */
class NoticeRepositoryTest {

    /** NoticeServiceApi 手写桩：可编程返回值 + 调用计数 */
    private class FakeNoticeServiceApi(
        var inboxResult: ApiResult<PageData<NoticeVo>> = ApiResult(200, "成功", null),
        var unreadCountResult: ApiResult<Long> = ApiResult(200, "成功", null),
    ) : com.smartwash.feature.notice.api.NoticeServiceApi {

        var inboxCalls = 0
            private set
        var unreadCalls = 0
            private set
        val markReadIds = mutableListOf<Long>()
        var readAllCalls = 0
            private set

        override suspend fun inbox(page: Int, size: Int): ApiResult<PageData<NoticeVo>> {
            inboxCalls++
            return inboxResult
        }

        override suspend fun unreadCount(): ApiResult<Long> {
            unreadCalls++
            return unreadCountResult
        }

        override suspend fun markRead(id: Long): ApiResult<Unit> {
            markReadIds += id
            return ApiResult(200, "成功", null)
        }

        override suspend fun readAll(): ApiResult<Int> {
            readAllCalls++
            return ApiResult(200, "成功", 3)
        }
    }

    private fun pageData(vararg ids: Long) = PageData(
        records = ids.map { NoticeVo(id = it, title = "公告 $it", type = "SYSTEM") },
        total = ids.size.toLong(),
        size = 10,
        current = 1,
    )

    @Test
    fun `收件箱分页透传 PageData`() = runTest {
        val api = FakeNoticeServiceApi(inboxResult = ApiResult(200, "成功", pageData(1L, 2L, 3L)))
        val page = NoticeRepository(api).getInbox(page = 1, size = 10)
        assertEquals(listOf(1L, 2L, 3L), page.records.map { it.safeId })
        assertEquals(3L, page.total)
        assertEquals(1, api.inboxCalls)
    }

    @Test
    fun `收件箱 data 缺失按失败上抛 NetworkException`() = runTest {
        val api = FakeNoticeServiceApi(inboxResult = ApiResult(200, "成功", null))
        val ex = assertThrows(NetworkException::class.java) {
            kotlinx.coroutines.runBlocking { NoticeRepository(api).getInbox(page = 2, size = 10) }
        }
        assertEquals("收件箱数据为空", ex.message)
    }

    @Test
    fun `未读数透传`() = runTest {
        val api = FakeNoticeServiceApi(unreadCountResult = ApiResult(200, "成功", 7L))
        assertEquals(7L, NoticeRepository(api).getUnreadCount())
    }

    @Test
    fun `未读数 data 缺失按 0 降级 不上抛`() = runTest {
        // 契约：未读数是点缀数据，失败只隐藏角标，不让我的页进错误态
        val api = FakeNoticeServiceApi(unreadCountResult = ApiResult(200, "成功", null))
        assertEquals(0L, NoticeRepository(api).getUnreadCount())
        assertEquals(1, api.unreadCalls)
    }

    @Test
    fun `标记已读与全部已读正常调用（data 恒 null 不读 data）`() = runTest {
        val api = FakeNoticeServiceApi()
        val repo = NoticeRepository(api)
        repo.markRead(id = 42L)
        repo.readAll()
        assertEquals(listOf(42L), api.markReadIds)
        assertEquals(1, api.readAllCalls)
    }
}
