package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点单页本地选菜状态类 JVM 单测（锚点）：切换、备注、由详情选菜记录重建。
 * 只断言状态行为（选中集合 + 备注），不涉及 Android/Compose。
 */
class DiningCartStateTest {

    @Test
    fun `初始为空`() {
        val state = DiningCartState()
        assertEquals(0, state.count)
        assertFalse(state.isSelected(1L))
        assertEquals("", state.noteOf(1L))
    }

    @Test
    fun `未选切换为选中且备注为空`() {
        val state = DiningCartState().toggle(1L)
        assertTrue(state.isSelected(1L))
        assertEquals(1, state.count)
        assertEquals("", state.noteOf(1L))
    }

    @Test
    fun `已选切换为取消且移除备注`() {
        val state = DiningCartState().toggle(1L).withNote(1L, "不要辣")
        assertTrue(state.isSelected(1L))
        val cancelled = state.toggle(1L)
        assertFalse(cancelled.isSelected(1L))
        assertEquals(0, cancelled.count)
        assertEquals("", cancelled.noteOf(1L))
    }

    @Test
    fun `连续两次切换回到原状`() {
        val state = DiningCartState().toggle(1L).toggle(1L)
        assertEquals(DiningCartState(), state)
    }

    @Test
    fun `改备注仅作用于已选菜`() {
        val state = DiningCartState().toggle(1L).withNote(1L, "  多放葱  ")
        assertEquals("多放葱", state.noteOf(1L))
        // 未选菜写备注被忽略（备注不能脱离勾选存在）
        val unchanged = state.withNote(2L, "不要辣")
        assertEquals(state, unchanged)
    }

    @Test
    fun `备注清空保留勾选`() {
        val state = DiningCartState().toggle(1L).withNote(1L, "不要辣").withNote(1L, "   ")
        assertTrue(state.isSelected(1L))
        assertEquals("", state.noteOf(1L))
    }

    @Test
    fun `由详情记录重建只认当前用户`() {
        val items = listOf(
            DiningOrderDetailVo.OrderItem(recipeId = 1L, userId = 9L, note = "不要辣"),
            DiningOrderDetailVo.OrderItem(recipeId = 2L, userId = 10L, note = "多放葱"),
            DiningOrderDetailVo.OrderItem(recipeId = 3L, userId = 9L, note = null),
        )
        val state = DiningCartState.fromItems(items, myUserId = 9L)
        assertEquals(2, state.count)
        assertTrue(state.isSelected(1L))
        assertFalse(state.isSelected(2L))
        assertTrue(state.isSelected(3L))
        assertEquals("不要辣", state.noteOf(1L))
        assertEquals("", state.noteOf(3L))
    }

    @Test
    fun `未取到当前用户标识时不误判他人选择`() {
        val items = listOf(DiningOrderDetailVo.OrderItem(recipeId = 1L, userId = 9L, note = "不要辣"))
        val state = DiningCartState.fromItems(items, myUserId = null)
        assertEquals(0, state.count)
    }
}
