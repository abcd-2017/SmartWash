package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 点单页左右联动派生纯函数 JVM 单测（锚点）：
 * 分类段起始 index 映射、滚动位置反算高亮分类、点击分类计算滚动落点。
 * 断言值来自布局语义（段头 1 行 + 菜品行）的独立推算，不复制实现。
 */
class DiningMenuNavigatorTest {

    /** 三段：A(2 道) / B(1 道) / 未分类(1 道) → 扁平 index：A头0 A菜1,2 B头3 B菜4 未分类头5 未分类菜6 */
    private val sections = listOf(
        DiningMenuSection(categoryId = 1L, categoryName = "家常菜", dishes = listOf(
            dish(10L, "番茄炒蛋"),
            dish(11L, "凉拌黄瓜"),
        )),
        DiningMenuSection(categoryId = 2L, categoryName = "汤羹", dishes = listOf(
            dish(20L, "紫菜蛋花汤"),
        )),
        DiningMenuSection(categoryId = null, categoryName = "未分类", dishes = listOf(
            dish(30L, "白米饭"),
        )),
    )

    private fun dish(id: Long, name: String) = DiningMenuItem(
        recipeId = id,
        name = name,
        coverUrl = "",
        categoryId = null,
        selectedByMe = false,
        myNote = "",
        selectors = emptyList(),
    )

    @Test
    fun `段起始 index = 段头 1 行 + 前序菜品行`() {
        val starts = DiningMenuNavigator.sectionStartIndices(sections)
        assertEquals(0, starts[1L])
        assertEquals(3, starts[2L])
        assertEquals(5, starts[null])
    }

    @Test
    fun `空分段返回空映射`() {
        assertEquals(emptyMap<Long?, Int>(), DiningMenuNavigator.sectionStartIndices(emptyList()))
    }

    @Test
    fun `滚动到段内任意位置都高亮该段`() {
        assertEquals(1L, DiningMenuNavigator.activeCategoryAt(sections, -1))
        assertEquals(1L, DiningMenuNavigator.activeCategoryAt(sections, 0))
        assertEquals(1L, DiningMenuNavigator.activeCategoryAt(sections, 1))
        assertEquals(1L, DiningMenuNavigator.activeCategoryAt(sections, 2))
        assertEquals(2L, DiningMenuNavigator.activeCategoryAt(sections, 3))
        assertEquals(2L, DiningMenuNavigator.activeCategoryAt(sections, 4))
        assertEquals(null, DiningMenuNavigator.activeCategoryAt(sections, 5))
    }

    @Test
    fun `滚过末段后高亮末段`() {
        assertEquals(null, DiningMenuNavigator.activeCategoryAt(sections, 6))
        assertEquals(null, DiningMenuNavigator.activeCategoryAt(sections, 100))
    }

    @Test
    fun `空分段高亮返回 null`() {
        assertNull(DiningMenuNavigator.activeCategoryAt(emptyList(), 0))
    }

    @Test
    fun `点击分类得到该段段头 index`() {
        assertEquals(0, DiningMenuNavigator.scrollTargetIndex(sections, 1L))
        assertEquals(3, DiningMenuNavigator.scrollTargetIndex(sections, 2L))
        assertEquals(5, DiningMenuNavigator.scrollTargetIndex(sections, null))
    }

    @Test
    fun `点击不存在分类回退到列表顶`() {
        assertEquals(0, DiningMenuNavigator.scrollTargetIndex(sections, 999L))
        assertEquals(0, DiningMenuNavigator.scrollTargetIndex(emptyList(), 1L))
    }

    @Test
    fun `状态类转发同一套派生`() {
        val link = DiningMenuLink(sections)
        assertEquals(1L, link.activeCategoryAt(0))
        assertEquals(2L, link.activeCategoryAt(3))
        assertEquals(3, link.scrollTargetIndex(2L))
    }
}
