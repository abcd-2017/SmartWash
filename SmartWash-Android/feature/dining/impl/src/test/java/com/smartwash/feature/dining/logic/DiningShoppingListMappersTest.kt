package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 购买清单结果页映射 JVM 单测（锚点）：快照 JSON 解析（Gson 容错）、
 * 食材并集视图与按菜视图映射、选菜人标识兜底、空/畸形数据降级。
 */
class DiningShoppingListMappersTest {

    @Test
    fun `解析快照并映射食材与按菜双视图`() {
        val raw = """
            {"ingredientNames":["西红柿","鸡蛋","葱","糖"],
             "perRecipe":[{"recipeName":"番茄炒蛋","selectors":[{"userId":9,"note":"不要辣"},{"userId":10,"note":null}]},
                          {"recipeName":"凉拌西红柿","selectors":[{"userId":9,"note":"少糖"}]}]}
        """.trimIndent()

        val snapshot = parseDiningShoppingList(raw)
        assertNotNull(snapshot)
        val result = snapshot!!.toShoppingListResult(
            members = listOf(
                DiningOrderDetailVo.OrderMember(userId = 9L, isCreator = true, phoneMasked = "139****0001"),
                DiningOrderDetailVo.OrderMember(userId = 10L, isCreator = false, phoneMasked = "138****0002"),
            ),
            myUserId = 9L,
        )

        // 按食材：并集保序原样透出
        assertEquals(listOf("西红柿", "鸡蛋", "葱", "糖"), result.ingredients)
        // 按菜：菜名 + 选菜人脱敏标识 + 备注
        assertEquals(2, result.perRecipe.size)
        assertEquals("番茄炒蛋", result.perRecipe[0].recipeName)
        assertEquals(2, result.perRecipe[0].selectors.size)
        assertEquals("139****0001", result.perRecipe[0].selectors[0].label)
        assertEquals("不要辣", result.perRecipe[0].selectors[0].note)
        assertTrue(result.perRecipe[0].selectors[0].isMe)
        assertEquals("138****0002", result.perRecipe[0].selectors[1].label)
        assertFalse(result.perRecipe[0].selectors[1].isMe)
    }

    @Test
    fun `选菜人不在参与者中时按用户 ID 兜底`() {
        val snapshot = parseDiningShoppingList(
            """{"ingredientNames":["西红柿"],"perRecipe":[{"recipeName":"番茄炒蛋","selectors":[{"userId":99,"note":null}]}]}""",
        )

        val result = snapshot!!.toShoppingListResult(emptyList(), null)

        assertEquals("用户 99", result.perRecipe[0].selectors[0].label)
        assertFalse(result.perRecipe[0].selectors[0].isMe)
    }

    @Test
    fun `空快照映射为空清单`() {
        val result = parseDiningShoppingList("""{"ingredientNames":[],"perRecipe":[]}""")!!
            .toShoppingListResult(emptyList(), null)

        assertTrue(result.ingredients.isEmpty())
        assertTrue(result.perRecipe.isEmpty())
    }

    @Test
    fun `缺失字段按空清单兜底不崩溃`() {
        val result = parseDiningShoppingList("""{}""")!!.toShoppingListResult(emptyList(), null)

        assertTrue(result.ingredients.isEmpty())
        assertTrue(result.perRecipe.isEmpty())
    }

    @Test
    fun `null 空白与畸形 JSON 解析为 null`() {
        assertNull(parseDiningShoppingList(null))
        assertNull(parseDiningShoppingList(""))
        assertNull(parseDiningShoppingList("   "))
        assertNull(parseDiningShoppingList("{not-json"))
    }
}
