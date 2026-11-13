package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享购物车重放差分 JVM 单测（锚点）：只测「服务端我的选菜 → 本地意图」的差分契约，
 * 断言最小操作集，不涉及并发/协程。
 */
class DiningCartReplayTest {

    @Test
    fun `服务端缺失时补勾`() {
        val ops = replayOperations(emptyMap(), DiningCartState(mapOf(1L to "")))
        assertEquals(listOf(DiningCartReplayOp.Add(1L, "")), ops)
    }

    @Test
    fun `服务端多出时取消`() {
        val ops = replayOperations(mapOf(1L to ""), DiningCartState())
        assertEquals(listOf(DiningCartReplayOp.Remove(1L)), ops)
    }

    @Test
    fun `备注不一致时改备注`() {
        val ops = replayOperations(mapOf(1L to "旧"), DiningCartState(mapOf(1L to "新")))
        assertEquals(listOf(DiningCartReplayOp.UpdateNote(1L, "新")), ops)
    }

    @Test
    fun `备注清空按改备注处理`() {
        val ops = replayOperations(mapOf(1L to "不要辣"), DiningCartState(mapOf(1L to "")))
        assertEquals(listOf(DiningCartReplayOp.UpdateNote(1L, "")), ops)
    }

    @Test
    fun `两边一致时无操作`() {
        val ops = replayOperations(mapOf(1L to "不要辣"), DiningCartState(mapOf(1L to "不要辣")))
        assertTrue(ops.isEmpty())
    }

    @Test
    fun `混合差分先补勾改备注后取消`() {
        val server = mapOf(1L to "旧", 3L to "")
        val intent = DiningCartState(mapOf(1L to "新", 2L to ""))
        val ops = replayOperations(server, intent)
        assertEquals(
            listOf(
                DiningCartReplayOp.UpdateNote(1L, "新"),
                DiningCartReplayOp.Add(2L, ""),
                DiningCartReplayOp.Remove(3L),
            ),
            ops,
        )
    }
}
