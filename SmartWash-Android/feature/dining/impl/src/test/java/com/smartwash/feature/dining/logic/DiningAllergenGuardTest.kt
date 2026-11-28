package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 忌口警示内核 JVM 单测（锚点，契约 §三.1）：选中菜 allergens ∩ 用户忌口 → 命中项。
 * 覆盖空忌口、交集、大小写/空白归一化、同菜多命中与去重、无命中。
 */
class DiningAllergenGuardTest {

    private fun dish(id: Long, name: String, allergens: Set<String>) =
        DiningAllergenGuard.SelectedDish(recipeId = id, recipeName = name, allergens = allergens)

    @Test
    fun `用户未设忌口返回空命中`() {
        val hits = DiningAllergenGuard.hits(
            listOf(dish(1L, "宫保鸡丁", setOf("NUT", "SOY"))),
            emptySet(),
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `交集命中产出菜名与忌口码`() {
        val hits = DiningAllergenGuard.hits(
            listOf(dish(1L, "宫保鸡丁", setOf("NUT", "SOY"))),
            setOf("NUT"),
        )
        assertEquals(1, hits.size)
        assertEquals(DiningAllergenGuard.Hit(1L, "宫保鸡丁", "NUT"), hits[0])
    }

    @Test
    fun `同菜多忌口逐个产出且保选中菜顺序`() {
        val hits = DiningAllergenGuard.hits(
            listOf(
                dish(1L, "宫保鸡丁", setOf("NUT", "SOY")),
                dish(2L, "麻婆豆腐", setOf("SOY", "SPICY")),
            ),
            setOf("SOY", "SPICY", "NUT"),
        )
        assertEquals(
            listOf(
                DiningAllergenGuard.Hit(1L, "宫保鸡丁", "NUT"),
                DiningAllergenGuard.Hit(1L, "宫保鸡丁", "SOY"),
                DiningAllergenGuard.Hit(2L, "麻婆豆腐", "SOY"),
                DiningAllergenGuard.Hit(2L, "麻婆豆腐", "SPICY"),
            ),
            hits,
        )
    }

    @Test
    fun `大小写与空白漂移仍命中`() {
        val hits = DiningAllergenGuard.hits(
            listOf(dish(1L, "凉拌花生", setOf(" nut "))),
            setOf("NUT"),
        )
        assertEquals(1, hits.size)
        assertEquals("NUT", hits[0].allergen)
    }

    @Test
    fun `同菜重复码去重且不误报未选忌口`() {
        val hits = DiningAllergenGuard.hits(
            listOf(dish(1L, "花生糊", setOf("NUT", "NUT", "DAIRY"))),
            setOf("NUT"),
        )
        assertEquals(1, hits.size)
        assertEquals("NUT", hits[0].allergen)
    }

    @Test
    fun `无交集返回空命中`() {
        val hits = DiningAllergenGuard.hits(
            listOf(dish(1L, "白灼菜心", setOf("SEAFOOD"))),
            setOf("NUT", "EGG"),
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `归一化工具 - 空白剔除与大写`() {
        assertEquals(listOf("NUT", "SOY"), DiningAllergenGuard.normalizeAllergens(listOf(" nut ", "", "soy")))
        assertEquals(null, DiningAllergenGuard.normalizeAllergen("  "))
    }

    @Test
    fun `成员忌口聚合 - 跨成员去重保序`() {
        val members = listOf(
            DiningOrderDetailVo.OrderMember(allergens = listOf("SEAFOOD", "SPICY")),
            DiningOrderDetailVo.OrderMember(allergens = listOf("SPICY", "NUT")),
            DiningOrderDetailVo.OrderMember(allergens = emptyList()),
            DiningOrderDetailVo.OrderMember(allergens = null),
        )
        assertEquals(
            listOf("SEAFOOD", "SPICY", "NUT"),
            DiningAllergenGuard.summarizeMemberAllergens(members),
        )
    }
}
