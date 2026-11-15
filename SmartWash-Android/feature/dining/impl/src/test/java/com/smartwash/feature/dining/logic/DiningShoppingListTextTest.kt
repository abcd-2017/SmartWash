package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 购买清单文本导出 JVM 单测（锚点）：标题透传、勾选态标记、按菜选菜人与备注、空清单降级。
 */
class DiningShoppingListTextTest {

    private val result = DiningShoppingListResult(
        ingredients = listOf("西红柿", "鸡蛋", "葱"),
        perRecipe = listOf(
            DiningRecipeShopping(
                recipeName = "番茄炒蛋",
                selectors = listOf(
                    DiningSelectorShopping(label = "139****0001", note = "不要辣", isMe = true),
                    DiningSelectorShopping(label = "138****0002", note = "", isMe = false),
                ),
            ),
            DiningRecipeShopping(
                recipeName = "凉拌西红柿",
                selectors = listOf(DiningSelectorShopping(label = "139****0001", note = "少糖", isMe = true)),
            ),
        ),
    )

    @Test
    fun `含标题与勾选态的全文`() {
        val text = buildShoppingListText("周五晚火锅", result, checked = setOf("鸡蛋"))

        val expected = """
            吃啥 · 购买清单
            订单：周五晚火锅

            【按食材买菜】
            [ ] 西红柿
            [x] 鸡蛋
            [ ] 葱

            【按菜备菜】
            - 番茄炒蛋：139****0001（不要辣）、138****0002
            - 凉拌西红柿：139****0001（少糖）
        """.trimIndent()
        assertEquals(expected, text)
    }

    @Test
    fun `标题空白时省略订单行`() {
        val text = buildShoppingListText("  ", result, emptySet())

        assertFalse(text.contains("订单："))
        assertTrue(text.startsWith("吃啥 · 购买清单"))
    }

    @Test
    fun `空清单降级为无`() {
        val text = buildShoppingListText("家宴", DiningShoppingListResult(emptyList(), emptyList()), emptySet())

        assertTrue(text.contains("【按食材买菜】\n（无）"))
        assertTrue(text.contains("【按菜备菜】\n（无）"))
    }

    @Test
    fun `无选菜人的菜只出菜名`() {
        val text = buildShoppingListText(
            "家宴",
            DiningShoppingListResult(
                ingredients = emptyList(),
                perRecipe = listOf(DiningRecipeShopping("白米饭", emptyList())),
            ),
            emptySet(),
        )

        assertTrue(text.contains("- 白米饭"))
        assertFalse(text.contains("白米饭："))
    }
}
