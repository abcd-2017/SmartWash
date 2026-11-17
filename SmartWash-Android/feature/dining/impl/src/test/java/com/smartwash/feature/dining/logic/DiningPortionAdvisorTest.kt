package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 份量建议内核 JVM 单测（锚点，契约 §2.5）：人均 = 总热量 ÷ 人数，参考 = 目标 ÷ 3，
 * 阈值 0.9-1.1 → 够/偏多/偏少。断言值由口径独立推算，不复制实现。
 */
class DiningPortionAdvisorTest {

    @Test
    fun `总热量未知返回 null`() {
        assertNull(DiningPortionAdvisor.advice(totalCalories = null, peopleCount = 2, calorieTargetPerDay = 2000))
    }

    @Test
    fun `常量锚点 - 默认目标与阈值`() {
        assertEquals(2000, DiningPortionAdvisor.DEFAULT_CALORIE_TARGET)
        assertEquals(0.9, DiningPortionAdvisor.LOWER_RATIO, 0.0)
        assertEquals(1.1, DiningPortionAdvisor.UPPER_RATIO, 0.0)
    }

    @Test
    fun `人均落在参考零点九到一点一倍区间判够`() {
        // 参考 = 2000/3 ≈ 666.67；人均 600 在 [600, 733.33] 内
        val enough = DiningPortionAdvisor.advice(totalCalories = 600.0, peopleCount = 1, calorieTargetPerDay = 2000)
        assertEquals(DiningPortionAdvisor.Verdict.ENOUGH, enough?.verdict)
        assertEquals(600.0, enough!!.perCapita, 1e-9)
        assertEquals(2000.0 / 3.0, enough.reference, 1e-9)
    }

    @Test
    fun `边界 - 上限一点一倍判够`() {
        // 人均 = 参考 × 1.1（666.666... × 1.1 = 733.333...）
        val reference = 2000.0 / 3.0
        val atUpper = DiningPortionAdvisor.advice(
            totalCalories = reference * 1.1,
            peopleCount = 1,
            calorieTargetPerDay = 2000,
        )
        assertEquals(DiningPortionAdvisor.Verdict.ENOUGH, atUpper?.verdict)
    }

    @Test
    fun `边界 - 下限零点九倍判够`() {
        val reference = 2000.0 / 3.0
        val atLower = DiningPortionAdvisor.advice(
            totalCalories = reference * 0.9,
            peopleCount = 1,
            calorieTargetPerDay = 2000,
        )
        assertEquals(DiningPortionAdvisor.Verdict.ENOUGH, atLower?.verdict)
    }

    @Test
    fun `人均高于上限判偏多`() {
        val reference = 2000.0 / 3.0
        val more = DiningPortionAdvisor.advice(
            totalCalories = reference * 1.2,
            peopleCount = 1,
            calorieTargetPerDay = 2000,
        )
        assertEquals(DiningPortionAdvisor.Verdict.MORE, more?.verdict)
    }

    @Test
    fun `人均低于下限判偏少`() {
        val reference = 2000.0 / 3.0
        val less = DiningPortionAdvisor.advice(
            totalCalories = reference * 0.8,
            peopleCount = 1,
            calorieTargetPerDay = 2000,
        )
        assertEquals(DiningPortionAdvisor.Verdict.LESS, less?.verdict)
    }

    @Test
    fun `人数收敛 - 越界钳制到 1-20`() {
        val low = DiningPortionAdvisor.advice(totalCalories = 600.0, peopleCount = 0, calorieTargetPerDay = 2000)
        assertEquals(1, low?.peopleCount)
        val high = DiningPortionAdvisor.advice(totalCalories = 600.0, peopleCount = 99, calorieTargetPerDay = 2000)
        assertEquals(20, high?.peopleCount)
    }

    @Test
    fun `人均随人数变化 - 3 人吃 1800 kcal 人均 600`() {
        val advice = DiningPortionAdvisor.advice(totalCalories = 1800.0, peopleCount = 3, calorieTargetPerDay = 2000)
        assertEquals(3, advice?.peopleCount)
        assertEquals(600.0, advice!!.perCapita, 1e-9)
        assertEquals(DiningPortionAdvisor.Verdict.ENOUGH, advice.verdict)
    }

    @Test
    fun `目标缺省用默认 2000 - 自定义目标按目标除三`() {
        val defaultTarget = DiningPortionAdvisor.advice(totalCalories = 600.0, peopleCount = 1, calorieTargetPerDay = 0)
        assertEquals(2000.0 / 3.0, defaultTarget!!.reference, 1e-9)

        // 目标 3000 → 参考 1000；人均 900 在 [900, 1100] 内判够
        val custom = DiningPortionAdvisor.advice(totalCalories = 900.0, peopleCount = 1, calorieTargetPerDay = 3000)
        assertEquals(1000.0, custom!!.reference, 1e-9)
        assertEquals(DiningPortionAdvisor.Verdict.ENOUGH, custom.verdict)
    }
}
