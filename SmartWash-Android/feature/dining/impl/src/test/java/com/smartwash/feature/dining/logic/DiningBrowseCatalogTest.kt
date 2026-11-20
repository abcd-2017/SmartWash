package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningRecipeVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浏览态快照还原 JVM 单测（锚点）：创建订单页搜索态的「双状态」契约——
 * 进入搜索前存浏览快照，退出搜索零网络还原，收藏态以搜索期间的最新值合并。
 */
class DiningBrowseCatalogTest {

    private fun recipe(id: Long, favorited: Boolean? = null) = DiningRecipeVo(id = id, favorited = favorited)

    @Test
    fun `无收藏变更时快照逐项原样还原`() {
        val catalog = DiningBrowseCatalog(
            recipes = listOf(recipe(1, false), recipe(2, true), recipe(3)),
            page = 2,
            hasMore = true,
        )

        val restored = catalog.restoreRecipes(latestStates = emptyMap())

        assertEquals(catalog.recipes, restored)
        assertEquals(2, catalog.page)
        assertTrue(catalog.hasMore)
    }

    @Test
    fun `搜索期间收藏翻转按最新值合并进浏览快照`() {
        val catalog = DiningBrowseCatalog(
            recipes = listOf(recipe(1, false), recipe(2, true)),
            page = 1,
            hasMore = false,
        )
        // 搜索期间：菜 1 被收藏（false→true）、菜 2 被取消收藏（true→false）
        val latest = mapOf(
            1L to recipe(1, true),
            2L to recipe(2, false),
        )

        val restored = catalog.restoreRecipes(latest)

        assertTrue(restored.first { it.safeId == 1L }.favorited == true)
        assertFalse(restored.first { it.safeId == 2L }.favorited == true)
    }

    @Test
    fun `合并只覆盖收藏字段其余字段保持快照值`() {
        val snapshotVo = DiningRecipeVo(
            id = 7L,
            name = "浏览态名称",
            favorited = false,
            ingredients = listOf("番茄"),
        )
        val catalog = DiningBrowseCatalog(recipes = listOf(snapshotVo), page = 1, hasMore = false)
        // 搜索结果里的同 id VO 是服务端另一份快照（字段可能同值），仅 favorited 允许被带过
        val latest = mapOf(7L to DiningRecipeVo(id = 7L, name = "搜索态名称", favorited = true, ingredients = emptyList()))

        val restored = catalog.restoreRecipes(latest)

        val merged = restored.single()
        assertTrue(merged.favorited == true)
        assertEquals("浏览态名称", merged.name)
        assertEquals(listOf("番茄"), merged.ingredients)
    }

    @Test
    fun `搜索结果未覆盖的菜保持快照收藏态`() {
        val catalog = DiningBrowseCatalog(
            recipes = listOf(recipe(1, false), recipe(2, true)),
            page = 1,
            hasMore = false,
        )
        // 搜索结果只包含菜 1；菜 2 不可见、收藏态不可能变化，保持快照值 true
        val latest = mapOf(1L to recipe(1, true))

        val restored = catalog.restoreRecipes(latest)

        assertTrue(restored.first { it.safeId == 1L }.favorited == true)
        assertTrue(restored.first { it.safeId == 2L }.favorited == true)
    }

    @Test
    fun `还原保持快照顺序不变`() {
        val catalog = DiningBrowseCatalog(
            recipes = listOf(recipe(5), recipe(3), recipe(9)),
            page = 1,
            hasMore = false,
        )

        val restored = catalog.restoreRecipes(emptyMap())

        assertEquals(listOf(5L, 3L, 9L), restored.map { it.safeId })
    }
}
