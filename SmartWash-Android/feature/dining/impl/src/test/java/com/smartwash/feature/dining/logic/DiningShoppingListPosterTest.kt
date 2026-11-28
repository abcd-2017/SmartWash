package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 购买清单海报行装配 JVM 单测（锚点）：v2 聚合行带数量单位与已买态，老快照回落名并集。
 * 位图绘制部分依赖 Android Graphics，不在此断言。
 */
class DiningShoppingListPosterTest {

    @Test
    fun `v2 聚合行转海报行 - 带数量单位与已买态`() {
        val result = DiningShoppingListResult(
            ingredients = listOf("番茄"),
            perRecipe = emptyList(),
            ingredientTotals = listOf(
                DiningIngredientTotalUi(name = "番茄", totalValue = 575.0, unit = "g", dishCount = 2),
                DiningIngredientTotalUi(name = "盐", totalValue = null, unit = "", dishCount = 1),
            ),
            memberCount = 2,
        )

        val lines = DiningShoppingListPoster.buildLines(result, checked = setOf("盐"))

        assertEquals(
            listOf(
                DiningShoppingListPoster.Line("番茄", "575g", checked = false),
                DiningShoppingListPoster.Line("盐", amount = null, checked = true),
            ),
            lines,
        )
    }

    @Test
    fun `老快照无聚合行 - 回落名并集`() {
        val result = DiningShoppingListResult(
            ingredients = listOf("番茄", "鸡蛋"),
            perRecipe = emptyList(),
            ingredientTotals = null,
        )

        val lines = DiningShoppingListPoster.buildLines(result, checked = setOf("鸡蛋"))

        assertEquals(
            listOf(
                DiningShoppingListPoster.Line("番茄", amount = null, checked = false),
                DiningShoppingListPoster.Line("鸡蛋", amount = null, checked = true),
            ),
            lines,
        )
    }
}
