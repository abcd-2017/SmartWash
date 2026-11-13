package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.logic.DiningOrderFormValidator.DiningOrderFormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 创建订单表单校验 JVM 单测（票内验收：标题必填 / 至少选一道菜）。
 *
 * 规则与后端 from/CreateDiningOrderFrom 的 Bean Validation + service 二选一裁决一致
 * （端上先拦给友好提示，后端校验是最终兜底）：标题 trim 后非空 ≤64 字符；本单菜单
 * recipeIds 至少一道、上限 100 道。fromOrderId 复用链路（13 号票）走 [DiningOrderFormValidator.validateReuse]，
 * 只校验标题——菜单由后端整份复制。
 */
class DiningOrderFormValidatorTest {

    // ==================== 标题 ====================

    @Test
    fun `标题合法返回 trim 值`() {
        val result = DiningOrderFormValidator.validateTitle("  周五晚火锅  ")
        assertTrue(result.valid)
        assertNull(result.error)
        assertEquals("周五晚火锅", result.normalized)
    }

    @Test
    fun `空白标题报必填`() {
        val result = DiningOrderFormValidator.validateTitle("   ")
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.TITLE_REQUIRED, result.error)
    }

    @Test
    fun `空标题报必填`() {
        val result = DiningOrderFormValidator.validateTitle("")
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.TITLE_REQUIRED, result.error)
    }

    @Test
    fun `标题恰好 64 字符合法`() {
        val result = DiningOrderFormValidator.validateTitle("周".repeat(64))
        assertTrue(result.valid)
        assertEquals(64, result.normalized.length)
    }

    @Test
    fun `标题超长报错且不返回值`() {
        val result = DiningOrderFormValidator.validateTitle("周".repeat(65))
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.TITLE_TOO_LONG, result.error)
        assertEquals("", result.normalized)
    }

    // ==================== 本单菜单 ====================

    @Test
    fun `空菜单报至少选一道菜`() {
        val result = DiningOrderFormValidator.validateMenu(emptyList())
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.MENU_EMPTY, result.error)
    }

    @Test
    fun `单菜菜单合法`() {
        val result = DiningOrderFormValidator.validateMenu(listOf(7L))
        assertTrue(result.valid)
        assertNull(result.error)
        assertEquals(listOf(7L), result.normalized)
    }

    @Test
    fun `菜单恰好 100 道合法`() {
        val ids = (1L..100L).toList()
        val result = DiningOrderFormValidator.validateMenu(ids)
        assertTrue(result.valid)
        assertEquals(ids, result.normalized)
    }

    @Test
    fun `菜单超 100 道报错`() {
        val result = DiningOrderFormValidator.validateMenu((1L..101L).toList())
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.MENU_TOO_MANY, result.error)
    }

    // ==================== 组合校验 ====================

    @Test
    fun `组合校验合法返回归一化表单`() {
        val result = DiningOrderFormValidator.validate(
            title = " 家庭聚餐 ",
            recipeIds = listOf(3L, 1L, 2L),
        )
        assertTrue(result.valid)
        assertEquals("家庭聚餐", result.normalizedTitle)
        assertEquals(listOf(3L, 1L, 2L), result.normalizedRecipeIds)
        assertNull(result.error)
    }

    @Test
    fun `组合校验标题先拦`() {
        val result = DiningOrderFormValidator.validate(title = "", recipeIds = listOf(3L))
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.TITLE_REQUIRED, result.error)
    }

    @Test
    fun `组合校验菜单后拦`() {
        val result = DiningOrderFormValidator.validate(title = "家庭聚餐", recipeIds = emptyList())
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.MENU_EMPTY, result.error)
    }

    // ==================== 复用历史菜单（fromOrderId） ====================

    @Test
    fun `复用模式只校验标题且返回 trim 值`() {
        val result = DiningOrderFormValidator.validateReuse("  再来一单  ")
        assertTrue(result.valid)
        assertNull(result.error)
        assertEquals("再来一单", result.normalized)
    }

    @Test
    fun `复用模式空白标题报必填`() {
        val result = DiningOrderFormValidator.validateReuse("   ")
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.TITLE_REQUIRED, result.error)
    }

    @Test
    fun `复用模式标题超长报错`() {
        val result = DiningOrderFormValidator.validateReuse("周".repeat(65))
        assertFalse(result.valid)
        assertEquals(DiningOrderFormError.TITLE_TOO_LONG, result.error)
    }
}
