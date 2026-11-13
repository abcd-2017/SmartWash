package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.logic.DiningRecipeFormValidator.DiningRecipeFormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新增菜谱表单校验 JVM 单测（纯 Kotlin，无 Android 依赖）。
 * 断言与后端 from/AddDiningRecipeFrom 的 Bean Validation 契约对齐：
 * 菜名必填 ≤64 / 食材逐项 trim 非空 ≤32 且 ≤100 项 / 步骤可空 ≤20000。
 */
class DiningRecipeFormValidatorTest {

    // ==================== 菜名 ====================

    @Test
    fun `菜名合法返回 trim 后文本`() {
        val result = DiningRecipeFormValidator.validateName("  番茄炒蛋  ")
        assertTrue(result.valid)
        assertEquals("番茄炒蛋", result.normalized)
        assertNull(result.error)
    }

    @Test
    fun `菜名空白拒绝为必填错误`() {
        listOf("", "   ", "\n\t").forEach { raw ->
            val result = DiningRecipeFormValidator.validateName(raw)
            assertFalse("空白菜名必须拒绝: \"$raw\"", result.valid)
            assertEquals(DiningRecipeFormError.NAME_REQUIRED, result.error)
        }
    }

    @Test
    fun `菜名 64 字符恰好在限内，65 字符拒绝为超长`() {
        val boundary = "菜".repeat(64)
        assertTrue(DiningRecipeFormValidator.validateName(boundary).valid)

        val over = "菜".repeat(65)
        val result = DiningRecipeFormValidator.validateName(over)
        assertFalse(result.valid)
        assertEquals(DiningRecipeFormError.NAME_TOO_LONG, result.error)
    }

    // ==================== 食材 ====================

    @Test
    fun `食材列表合法返回逐项 trim 后列表`() {
        val result = DiningRecipeFormValidator.validateIngredients(listOf(" 西红柿 ", "鸡蛋", " 葱"))
        assertTrue(result.valid)
        assertEquals(listOf("西红柿", "鸡蛋", "葱"), result.normalized)
        assertNull(result.error)
    }

    @Test
    fun `食材空列表合法（可留空提交）`() {
        val result = DiningRecipeFormValidator.validateIngredients(emptyList())
        assertTrue(result.valid)
        assertTrue(result.normalized.isEmpty())
        assertNull(result.error)
    }

    @Test
    fun `空白食材项拒绝为空错误`() {
        val result = DiningRecipeFormValidator.validateIngredients(listOf("西红柿", "   "))
        assertFalse(result.valid)
        assertEquals(DiningRecipeFormError.INGREDIENT_EMPTY, result.error)
    }

    @Test
    fun `食材名 32 字符恰好在限内，33 字符拒绝为超长`() {
        assertTrue(DiningRecipeFormValidator.validateIngredients(listOf("食".repeat(32))).valid)

        val result = DiningRecipeFormValidator.validateIngredients(listOf("食".repeat(33)))
        assertFalse(result.valid)
        assertEquals(DiningRecipeFormError.INGREDIENT_TOO_LONG, result.error)
    }

    @Test
    fun `食材超过 100 项拒绝为超量（校验先于逐项短路）`() {
        val tooMany = (1..101).map { "食材$it" }
        val result = DiningRecipeFormValidator.validateIngredients(tooMany)
        assertFalse(result.valid)
        assertEquals(DiningRecipeFormError.INGREDIENT_TOO_MANY, result.error)
    }

    @Test
    fun `食材恰 100 项合法`() {
        val boundary = (1..100).map { "食材$it" }
        assertTrue(DiningRecipeFormValidator.validateIngredients(boundary).valid)
    }

    // ==================== 步骤 ====================

    @Test
    fun `步骤空白按未填写放行并归一为 null`() {
        listOf(null, "", "   \n ").forEach { raw ->
            val result = DiningRecipeFormValidator.validateSteps(raw)
            assertTrue("空白步骤必须放行: $raw", result.valid)
            assertNull(result.normalized)
            assertNull(result.error)
        }
    }

    @Test
    fun `步骤合法保留原始换行分段`() {
        val result = DiningRecipeFormValidator.validateSteps("热锅下油\n倒入蛋液\n翻炒出锅")
        assertTrue(result.valid)
        assertEquals("热锅下油\n倒入蛋液\n翻炒出锅", result.normalized)
    }

    @Test
    fun `步骤 20000 字符恰好在限内，20001 字符拒绝为超长`() {
        assertTrue(DiningRecipeFormValidator.validateSteps("步".repeat(20000)).valid)

        val result = DiningRecipeFormValidator.validateSteps("步".repeat(20001))
        assertFalse(result.valid)
        assertEquals(DiningRecipeFormError.STEPS_TOO_LONG, result.error)
    }
}
