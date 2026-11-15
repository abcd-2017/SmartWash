package com.smartwash.feature.coupon.repository

import com.smartwash.common.database.entity.CouponVoEntity
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 优惠券缓存 Entity ↔ 网络 VO 映射 JVM 单测（纯函数，逐字段映射 + 往返一致性）。
 */
class EntityMappersTest {

    @Test
    fun `CouponVoEntity toVo 逐字段映射`() {
        val entity = CouponVoEntity(
            couponId = 1L,
            title = "新用户券",
            description = "满 10 减 2",
            discount = 2f,
            threshold = 10f,
            startTime = "2026-01-01",
            endTime = "2026-12-31",
            validDays = 30,
            isNewUser = true,
            status = "0",
        )

        val vo = entity.toVo()

        assertEquals(1L, vo.couponId)
        assertEquals("新用户券", vo.title)
        assertEquals("满 10 减 2", vo.description)
        assertEquals(2f, vo.discount, 0f)
        assertEquals(10f, vo.threshold, 0f)
        assertEquals("2026-01-01", vo.startTime)
        assertEquals("2026-12-31", vo.endTime)
        assertEquals(30, vo.validDays)
        assertEquals(true, vo.isNewUser)
        assertEquals("0", vo.status)
    }

    @Test
    fun `CouponVo toEntity 逐字段映射`() {
        val vo = CouponVo(
            couponId = 2L,
            title = "折扣券",
            description = "9 折",
            discount = 0.9f,
            threshold = 0f,
            startTime = "2026-02-01",
            endTime = "2026-02-28",
            validDays = 7,
            isNewUser = false,
            status = "1",
        )

        val entity = vo.toEntity()

        assertEquals(2L, entity.couponId)
        assertEquals("折扣券", entity.title)
        assertEquals("9 折", entity.description)
        assertEquals(0.9f, entity.discount, 0f)
        assertEquals(0f, entity.threshold, 0f)
        assertEquals("2026-02-01", entity.startTime)
        assertEquals("2026-02-28", entity.endTime)
        assertEquals(7, entity.validDays)
        assertEquals(false, entity.isNewUser)
        assertEquals("1", entity.status)
    }

    @Test
    fun `Entity 与 Vo 往返映射保持字段一致`() {
        val entity = CouponVoEntity(
            couponId = 3L,
            title = "t",
            description = "d",
            discount = 1.5f,
            threshold = 20f,
            startTime = "s",
            endTime = "e",
            validDays = 3,
            isNewUser = true,
            status = "2",
        )
        assertEquals(entity, entity.toVo().toEntity())
    }
}
