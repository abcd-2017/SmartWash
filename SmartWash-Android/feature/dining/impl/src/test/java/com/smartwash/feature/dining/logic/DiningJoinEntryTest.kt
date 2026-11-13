package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 站内信 dining 深链加入入口决策 JVM 单测（12 号票）：
 * 成员直达 / 非成员确认加入 / 已结束不引导 / brief 缺失四分支，
 * 以及「成员判重优先于状态」的顺序锚点（已结束单的成员仍应直达详情）。
 */
class DiningJoinEntryTest {

    @Test
    fun `brief 缺失走不可用分支`() {
        assertEquals(DiningJoinEntryDecision.BRIEF_UNAVAILABLE, DiningJoinEntry.decide(null))
    }

    @Test
    fun `已是成员直达详情`() {
        assertEquals(
            DiningJoinEntryDecision.ENTER_DETAIL,
            DiningJoinEntry.decide(brief(joined = true, status = DiningOrderStatus.ONGOING)),
        )
    }

    @Test
    fun `非成员且进行中展示确认加入`() {
        assertEquals(
            DiningJoinEntryDecision.CONFIRM_JOIN,
            DiningJoinEntry.decide(brief(joined = false, status = DiningOrderStatus.ONGOING)),
        )
    }

    @Test
    fun `非成员且已结束不引导加入`() {
        assertEquals(
            DiningJoinEntryDecision.ORDER_FINISHED,
            DiningJoinEntry.decide(brief(joined = false, status = DiningOrderStatus.FINISHED)),
        )
    }

    @Test
    fun `成员判重优先于状态（已结束单的成员仍直达详情）`() {
        assertEquals(
            DiningJoinEntryDecision.ENTER_DETAIL,
            DiningJoinEntry.decide(brief(joined = true, status = DiningOrderStatus.FINISHED)),
        )
    }

    private fun brief(joined: Boolean, status: DiningOrderStatus) = DiningOrderBriefUi(
        orderId = 42L,
        title = "周五晚火锅",
        status = status,
        creatorName = "139****9000",
        recipeCount = 3,
        joined = joined,
    )
}
