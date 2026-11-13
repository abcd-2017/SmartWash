package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderBriefVo
import com.smartwash.feature.dining.api.model.DiningOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 加入前 brief VO → 确认弹窗模型映射 JVM 单测：safe 兜底 + 状态保守降级
 * （Gson 全可空容错契约的消费端锚点，缺字段/类型漂移时映射不抛、不产生垃圾展示值）。
 */
class DiningBriefMapperTest {

    @Test
    fun `完整 brief Vo 映射保留全部字段`() {
        val ui = DiningOrderBriefVo(
            orderId = 42L,
            title = "周五晚火锅",
            status = "ONGOING",
            creatorName = "139****9000",
            recipeCount = 6L,
            joined = false,
        ).toBriefUi()

        assertEquals(42L, ui.orderId)
        assertEquals("周五晚火锅", ui.title)
        assertEquals(DiningOrderStatus.ONGOING, ui.status)
        assertEquals("139****9000", ui.creatorName)
        assertEquals(6, ui.recipeCount)
        assertFalse(ui.joined)
    }

    @Test
    fun `全空 brief Vo 走 safe 兜底且状态保守降级为已结束`() {
        val ui = DiningOrderBriefVo().toBriefUi()

        assertEquals(0L, ui.orderId)
        assertEquals("", ui.title)
        assertEquals("白名单外/缺失状态按不可写降级（已结束不引导加入）", DiningOrderStatus.FINISHED, ui.status)
        assertEquals("", ui.creatorName)
        assertEquals(0, ui.recipeCount)
        assertFalse(ui.joined)
    }

    @Test
    fun `joined 为 true 时已加入标记透传（端上据此跳过确认弹窗）`() {
        val ui = DiningOrderBriefVo(orderId = 8L, title = "夜宵", status = "ONGOING", joined = true).toBriefUi()
        assertEquals(8L, ui.orderId)
        assertEquals(DiningOrderStatus.ONGOING, ui.status)
        assertEquals(true, ui.joined)
    }

    @Test
    fun `状态白名单外脏数据保守降级为已结束`() {
        val ui = DiningOrderBriefVo(status = "CANCELLED").toBriefUi()
        assertEquals(DiningOrderStatus.FINISHED, ui.status)
    }
}
