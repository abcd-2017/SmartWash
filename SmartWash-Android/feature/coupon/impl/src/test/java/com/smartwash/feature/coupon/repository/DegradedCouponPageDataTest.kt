package com.smartwash.feature.coupon.repository

import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A5（回归 2026-11-06）券缓存降级归并锚点单测：
 * 网络失败时三组「有啥显啥」——有缓存回退缓存（CACHE），无缓存标 FAILED（UI 出错误小态而非「暂无」空态）。
 */
class DegradedCouponPageDataTest {

    private fun coupon(id: Long) = CouponVo(
        couponId = id,
        title = "满100减10",
        description = "测试券",
        discount = 10f,
        threshold = 100f,
        startTime = "2026-01-01 00:00:00",
        endTime = "2026-12-31 23:59:59",
        validDays = 30,
        isNewUser = false,
        status = "ACTIVE",
    )

    @Test
    fun `非空可用券缓存降级为 CACHE`() {
        val data = buildDegradedCouponPageData(listOf(coupon(1), coupon(2)))
        assertEquals(CouponSource.CACHE, data.available.source)
        assertEquals(2, data.available.items.size)
    }

    @Test
    fun `已领与历史无本地缓存表 降级时标 FAILED`() {
        val data = buildDegradedCouponPageData(listOf(coupon(1)))
        assertEquals(CouponSource.FAILED, data.claimed.source)
        assertEquals(CouponSource.FAILED, data.historical.source)
        assertTrue(data.claimed.items.isEmpty())
        assertTrue(data.historical.items.isEmpty())
    }

    @Test
    fun `可用券缓存也为空时该组标 FAILED 而非空态`() {
        val data = buildDegradedCouponPageData(emptyList())
        assertEquals(CouponSource.FAILED, data.available.source)
        assertTrue(data.available.items.isEmpty())
    }
}
