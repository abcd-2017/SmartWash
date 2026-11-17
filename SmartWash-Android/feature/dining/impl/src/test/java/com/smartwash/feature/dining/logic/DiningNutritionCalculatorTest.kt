package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningRecipeVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 营养计算内核 JVM 单测（锚点）：份热量口径（契约 §二：calories_total ?:
 * calories_per_100g × 250g ÷ 100）、聚合（已知/未知热量分账）、人均（人数 1-20 边界）、
 * 一餐超标判定（日参考 ÷ 3）。断言值由口径独立推算，不复制实现。
 */
class DiningNutritionCalculatorTest {

    /** 口径锚点：250g/份份量假设与人数区间（契约 §二/§5.4，四端一致） */
    @Test
    fun `份量常量与人数区间锚点`() {
        assertEquals(250.0, DiningNutritionCalculator.PORTION_GRAMS, 0.0)
        assertEquals(1, DiningNutritionCalculator.PEOPLE_MIN)
        assertEquals(20, DiningNutritionCalculator.PEOPLE_MAX)
    }

    @Test
    fun `份热量 - 整份口径优先于每100g口径`() {
        val recipe = recipe(caloriesTotal = 300.0, caloriesPer100g = 200.0)
        assertEquals(300.0, DiningNutritionCalculator.dishNutrition(recipe).calories!!, 1e-9)
        assertEquals(
            300.0,
            DiningNutritionCalculator.servingCalories(caloriesTotal = 300.0, caloriesPer100g = 200.0)!!,
            1e-9,
        )
    }

    @Test
    fun `份热量 - 仅每100g口径按 250g 折算`() {
        // 200 kcal/100g × 250g ÷ 100 = 500 kcal/份
        assertEquals(
            500.0,
            DiningNutritionCalculator.servingCalories(caloriesTotal = null, caloriesPer100g = 200.0)!!,
            1e-9,
        )
        val recipe = recipe(caloriesTotal = null, caloriesPer100g = 88.0)
        assertEquals(220.0, DiningNutritionCalculator.dishNutrition(recipe).calories!!, 1e-9)
    }

    @Test
    fun `份热量 - 双缺失为热量未知`() {
        assertNull(DiningNutritionCalculator.servingCalories(null, null))
        assertNull(DiningNutritionCalculator.dishNutrition(recipe()).calories)
    }

    @Test
    fun `每100g营养按份折算`() {
        // 8g/100g 蛋白质 × 250 ÷ 100 = 20g/份；钠 800mg/100g → 2000mg/份
        val dish = DiningNutritionCalculator.dishNutrition(
            recipe(proteinG = 8.0, fatG = 4.0, carbsG = 20.0, sodiumMg = 800.0),
        )
        assertEquals(20.0, dish.proteinG!!, 1e-9)
        assertEquals(10.0, dish.fatG!!, 1e-9)
        assertEquals(50.0, dish.carbsG!!, 1e-9)
        assertEquals(2000.0, dish.sodiumMg!!, 1e-9)
        assertNull(DiningNutritionCalculator.dishNutrition(recipe(proteinG = null)).proteinG)
    }

    @Test
    fun `聚合 - 正常全已知`() {
        val report = DiningNutritionCalculator.summarize(
            listOf(
                DiningNutritionCalculator.DishNutrition(
                    calories = 300.0, proteinG = 8.0, fatG = 6.0, carbsG = 30.0, sodiumMg = 300.0,
                ),
                DiningNutritionCalculator.DishNutrition(
                    calories = 300.0, proteinG = 5.0, fatG = 4.0, carbsG = 20.0, sodiumMg = 150.0,
                ),
            ),
        )
        assertEquals(600.0, report.totalCalories!!, 1e-9)
        assertEquals(2, report.calorieKnownCount)
        assertEquals(0, report.calorieUnknownCount)
        val protein = report.nutrients
            .first { it.nutrient == DiningNutritionCalculator.Nutrient.PROTEIN }
        assertEquals(13.0, protein.value!!, 1e-9)
        val sodium = report.nutrients
            .first { it.nutrient == DiningNutritionCalculator.Nutrient.SODIUM }
        assertEquals(450.0, sodium.value!!, 1e-9)
        // 全项低于一餐参考（600<666.7 kcal / 13<20g / 10<20g / 50<100g / 450<666.7mg）→ 无超标
        assertTrue(report.exceededItems.isEmpty())
    }

    @Test
    fun `聚合值不因超标而改变 - 超标只影响 exceededItems`() {
        val report = DiningNutritionCalculator.summarize(
            listOf(
                DiningNutritionCalculator.DishNutrition(
                    calories = 500.0, proteinG = 20.0, fatG = 10.0, carbsG = 50.0, sodiumMg = 800.0,
                ),
                DiningNutritionCalculator.DishNutrition(
                    calories = 300.0, proteinG = 10.0, fatG = 5.0, carbsG = 25.0, sodiumMg = 200.0,
                ),
            ),
        )
        assertEquals(800.0, report.totalCalories!!, 1e-9)
        val exceeded = report.exceededItems.map { it.nutrient }
        // 热量 800>666.7、蛋白质 30>20、钠 1000>666.7；脂肪 15<20、碳水 75<100
        assertTrue(DiningNutritionCalculator.Nutrient.CALORIES in exceeded)
        assertTrue(DiningNutritionCalculator.Nutrient.PROTEIN in exceeded)
        assertTrue(DiningNutritionCalculator.Nutrient.SODIUM in exceeded)
        assertFalse(DiningNutritionCalculator.Nutrient.FAT in exceeded)
        assertFalse(DiningNutritionCalculator.Nutrient.CARBS in exceeded)
    }

    @Test
    fun `聚合 - 部分缺失热量单独计数不参与总和`() {
        val report = DiningNutritionCalculator.summarize(
            listOf(
                DiningNutritionCalculator.DishNutrition(500.0, null, null, null, null),
                DiningNutritionCalculator.DishNutrition(null, 20.0, null, null, null),
                DiningNutritionCalculator.DishNutrition(null, null, null, null, null),
            ),
        )
        assertEquals(500.0, report.totalCalories!!, 1e-9)
        assertEquals(1, report.calorieKnownCount)
        assertEquals(2, report.calorieUnknownCount)
        // 蛋白质仅一菜已知 → 照常聚合；热量未知菜的营养字段不阻断其他项
        val protein = report.nutrients
            .first { it.nutrient == DiningNutritionCalculator.Nutrient.PROTEIN }
        assertEquals(20.0, protein.value!!, 1e-9)
    }

    @Test
    fun `聚合 - 全未知为 null 不误报 0`() {
        val report = DiningNutritionCalculator.summarize(
            listOf(DiningNutritionCalculator.DishNutrition(null, null, null, null, null)),
        )
        assertNull(report.totalCalories)
        assertEquals(0, report.calorieKnownCount)
        assertEquals(1, report.calorieUnknownCount)
        assertTrue(report.nutrients.all { it.value == null })
        assertTrue(report.exceededItems.isEmpty())
    }

    @Test
    fun `聚合 - 空列表返回空报告`() {
        val report = DiningNutritionCalculator.summarize(emptyList())
        assertNull(report.totalCalories)
        assertEquals(0, report.calorieKnownCount)
        assertEquals(0, report.calorieUnknownCount)
        assertTrue(report.exceededItems.isEmpty())
    }

    /** 人数边界：0→1、1/20 原样、25→20、负数→1（越界钳制，防御异常输入） */
    @Test
    fun `人数收敛到 1-20 区间`() {
        assertEquals(1, DiningNutritionCalculator.coercePeopleCount(0))
        assertEquals(1, DiningNutritionCalculator.coercePeopleCount(1))
        assertEquals(20, DiningNutritionCalculator.coercePeopleCount(20))
        assertEquals(20, DiningNutritionCalculator.coercePeopleCount(25))
        assertEquals(1, DiningNutritionCalculator.coercePeopleCount(-3))
    }

    @Test
    fun `人均 = 总热量除以人数`() {
        assertEquals(610.0, DiningNutritionCalculator.perCapita(1830.0, 3)!!, 1e-9)
        assertEquals(1830.0, DiningNutritionCalculator.perCapita(1830.0, 1)!!, 1e-9)
        assertEquals(91.5, DiningNutritionCalculator.perCapita(1830.0, 20)!!, 1e-9)
        assertNull(DiningNutritionCalculator.perCapita(null, 3))
    }

    @Test
    fun `超标判定 - 热量超一餐参考`() {
        // 一餐参考 666.7 kcal（日参考 2000 ÷ 3）：900 > 666.7 超标；600 未超
        val over = DiningNutritionCalculator.summarize(
            listOf(DiningNutritionCalculator.DishNutrition(900.0, null, null, null, null)),
        ).nutrients.first { it.nutrient == DiningNutritionCalculator.Nutrient.CALORIES }
        assertTrue(over.exceeded)
        assertEquals(666.7, over.reference, 1e-9)

        val under = DiningNutritionCalculator.summarize(
            listOf(DiningNutritionCalculator.DishNutrition(600.0, null, null, null, null)),
        ).nutrients.first { it.nutrient == DiningNutritionCalculator.Nutrient.CALORIES }
        assertFalse(under.exceeded)
    }

    @Test
    fun `超标判定 - 各营养项分别命中`() {
        // 逐项构造「当前值 > 一餐参考」：蛋白质 20g、脂肪 20g、碳水 100g、钠 666.7mg
        val report = DiningNutritionCalculator.summarize(
            listOf(
                DiningNutritionCalculator.DishNutrition(
                    calories = null,
                    proteinG = 21.0,
                    fatG = 20.1,
                    carbsG = 100.1,
                    sodiumMg = 666.8,
                ),
            ),
        )
        val exceeded = report.exceededItems.map { it.nutrient }
        assertTrue(DiningNutritionCalculator.Nutrient.PROTEIN in exceeded)
        assertTrue(DiningNutritionCalculator.Nutrient.FAT in exceeded)
        assertTrue(DiningNutritionCalculator.Nutrient.CARBS in exceeded)
        assertTrue(DiningNutritionCalculator.Nutrient.SODIUM in exceeded)
        // 热量未知 → 不误报
        assertFalse(DiningNutritionCalculator.Nutrient.CALORIES in exceeded)
    }

    @Test
    fun `超标判定 - 恰好等于参考值不算超标`() {
        val report = DiningNutritionCalculator.summarize(
            listOf(DiningNutritionCalculator.DishNutrition(null, 20.0, 20.0, 100.0, 666.7)),
        )
        assertTrue(report.exceededItems.none { it.nutrient == DiningNutritionCalculator.Nutrient.FAT })
        assertTrue(report.exceededItems.none { it.nutrient == DiningNutritionCalculator.Nutrient.PROTEIN })
        assertTrue(report.exceededItems.none { it.nutrient == DiningNutritionCalculator.Nutrient.CARBS })
    }

    @Test
    fun `数量格式化 - 整数去尾零与一位小数`() {
        assertEquals("1830", DiningNutritionCalculator.formatAmount(1830.0))
        assertEquals("666.7", DiningNutritionCalculator.formatAmount(666.66))
        assertEquals("0.5", DiningNutritionCalculator.formatAmount(0.5))
        assertEquals("1130", DiningNutritionCalculator.formatAmount(1130.0000001))
        assertNull(DiningNutritionCalculator.formatAmount(null))
    }

    private fun recipe(
        caloriesTotal: Double? = null,
        caloriesPer100g: Double? = null,
        proteinG: Double? = null,
        fatG: Double? = null,
        carbsG: Double? = null,
        sodiumMg: Double? = null,
    ) = DiningRecipeVo(
        id = 1L,
        name = "番茄炒蛋",
        caloriesTotal = caloriesTotal,
        caloriesPer100g = caloriesPer100g,
        proteinG = proteinG,
        fatG = fatG,
        carbsG = carbsG,
        sodiumMg = sodiumMg,
    )
}
