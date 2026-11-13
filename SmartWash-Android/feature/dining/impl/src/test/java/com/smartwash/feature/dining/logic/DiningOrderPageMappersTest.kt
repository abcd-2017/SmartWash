package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点单页详情映射 JVM 单测（锚点）：分类分段、我的选菜识别（脱敏手机号比对）、
 * 选菜人标识、发起者判定与 joinToken 可见性。
 */
class DiningOrderPageMappersTest {

    // ==================== 手机号脱敏 / 当前用户识别 ====================

    @Test
    fun `11 位手机号按 3+4+4 掩中间`() {
        assertEquals("139****0001", maskDiningPhone("13900000001"))
    }

    @Test
    fun `非 11 位保留前 3 后 2`() {
        // 10 位：前 3 + **** + 后 2
        assertEquals("138****23", maskDiningPhone("1380000123"))
        // 5 位及以下原样
        assertEquals("12345", maskDiningPhone("12345"))
    }

    @Test
    fun `空手机号脱敏为空串`() {
        assertEquals("", maskDiningPhone(null))
        assertEquals("", maskDiningPhone("   "))
    }

    @Test
    fun `按脱敏手机号定位当前用户`() {
        val members = listOf(
            DiningOrderDetailVo.OrderMember(userId = 9L, isCreator = true, phoneMasked = "139****0001"),
            DiningOrderDetailVo.OrderMember(userId = 10L, isCreator = false, phoneMasked = "138****0002"),
        )
        assertEquals(9L, resolveMyUserId(members, "13900000001"))
        assertEquals(10L, resolveMyUserId(members, "13800000002"))
        assertNull(resolveMyUserId(members, "13700000003"))
    }

    @Test
    fun `手机号缺失或成员脱敏为空时不误判`() {
        val members = listOf(
            DiningOrderDetailVo.OrderMember(userId = 9L, isCreator = true, phoneMasked = ""),
        )
        assertNull(resolveMyUserId(members, null))
        assertNull(resolveMyUserId(members, "13900000001"))
    }

    // ==================== 详情 → 点单页模型 ====================

    @Test
    fun `按菜单首现顺序分段并解析分类名`() {
        val vo = detail(
            menu = listOf(
                menu(10L, "番茄炒蛋", categoryId = 2L),
                menu(11L, "凉拌黄瓜", categoryId = 1L),
                menu(12L, "紫菜蛋花汤", categoryId = 2L),
            ),
        )
        val page = vo.toOrderPageDetail(
            myUserId = 9L,
            categoryNames = mapOf(1L to "家常菜", 2L to "汤羹"),
            coverUrls = emptyMap(),
            uncategorizedLabel = "未分类",
        )
        assertEquals(listOf(2L, 1L), page.sections.map { it.categoryId })
        assertEquals(listOf("汤羹", "家常菜"), page.sections.map { it.categoryName })
        assertEquals(listOf("番茄炒蛋", "紫菜蛋花汤"), page.sections[0].dishes.map { it.name })
        assertEquals(listOf("凉拌黄瓜"), page.sections[1].dishes.map { it.name })
    }

    @Test
    fun `未分类与分类目录缺失分别兜底`() {
        val vo = detail(
            menu = listOf(
                menu(10L, "白米饭", categoryId = null),
                menu(11L, "编排外菜", categoryId = 0L),
                menu(12L, "神秘菜", categoryId = 99L),
            ),
        )
        val page = vo.toOrderPageDetail(
            myUserId = 9L,
            categoryNames = emptyMap(),
            coverUrls = emptyMap(),
            uncategorizedLabel = "未分类",
        )
        // null 与 0 归并同一段（首现 null 命名「未分类」）
        assertEquals(2, page.sections.size)
        assertEquals("未分类", page.sections[0].categoryName)
        assertEquals(2, page.sections[0].dishes.size)
        assertEquals("分类 99", page.sections[1].categoryName)
    }

    @Test
    fun `识别我的选菜与全部选菜人标识`() {
        val vo = detail(
            menu = listOf(menu(10L, "番茄炒蛋", 1L)),
            items = listOf(
                DiningOrderDetailVo.OrderItem(recipeId = 10L, userId = 9L, note = "不要辣"),
                DiningOrderDetailVo.OrderItem(recipeId = 10L, userId = 10L, note = null),
            ),
            members = listOf(
                DiningOrderDetailVo.OrderMember(userId = 9L, isCreator = true, phoneMasked = "139****0001"),
                DiningOrderDetailVo.OrderMember(userId = 10L, isCreator = false, phoneMasked = "138****0002"),
            ),
            creatorUserId = 9L,
        )
        val page = vo.toOrderPageDetail(9L, mapOf(1L to "家常菜"), mapOf(10L to "http://cover"), "未分类")
        val dish = page.sections.single().dishes.single()
        assertEquals("http://cover", dish.coverUrl)
        assertTrue(dish.selectedByMe)
        assertEquals("不要辣", dish.myNote)
        assertEquals(2, dish.selectors.size)
        assertTrue(dish.selectors[0].isMe)
        assertFalse(dish.selectors[1].isMe)
        assertEquals("138****0002", dish.selectors[1].label)
        assertTrue(page.isCreator)
        assertTrue(page.isOngoing)
        assertEquals(DiningOrderStatus.ONGOING, page.status)
    }

    @Test
    fun `当前用户未选时不高亮且备注为空`() {
        val vo = detail(
            menu = listOf(menu(10L, "番茄炒蛋", 1L)),
            items = listOf(DiningOrderDetailVo.OrderItem(recipeId = 10L, userId = 10L, note = "不要辣")),
        )
        val page = vo.toOrderPageDetail(9L, emptyMap(), emptyMap(), "未分类")
        val dish = page.sections.single().dishes.single()
        assertFalse(dish.selectedByMe)
        assertEquals("", dish.myNote)
        assertEquals(1, dish.selectors.size)
    }

    @Test
    fun `发起者可见 joinToken 且状态白名单外保守降级`() {
        val creator = detail(creatorUserId = 9L).copy(status = "ONGOING", joinToken = "tok-abc")
        val creatorPage = creator.toOrderPageDetail(9L, emptyMap(), emptyMap(), "未分类")
        assertEquals("tok-abc", creatorPage.joinToken)

        val participant = detail(creatorUserId = 8L).copy(joinToken = null)
        val participantPage = participant.toOrderPageDetail(9L, emptyMap(), emptyMap(), "未分类")
        assertEquals("", participantPage.joinToken)
        assertFalse(participantPage.isCreator)

        val unknown = detail().copy(status = "CANCELLED")
        val unknownPage = unknown.toOrderPageDetail(9L, emptyMap(), emptyMap(), "未分类")
        assertEquals(DiningOrderStatus.FINISHED, unknownPage.status)
        assertFalse(unknownPage.isOngoing)
    }

    @Test
    fun `空菜单映射为空分段且不崩溃`() {
        val page = DiningOrderDetailVo(id = 1L, title = "空单").toOrderPageDetail(
            9L, emptyMap(), emptyMap(), "未分类",
        )
        assertTrue(page.sections.isEmpty())
        assertTrue(page.members.isEmpty())
        assertFalse(page.isOngoing)
    }

    @Test
    fun `FINISHED 状态进入只读态且携带购买清单快照`() {
        val vo = detail().copy(
            status = "FINISHED",
            shoppingList = """{"ingredientNames":["西红柿","鸡蛋","葱"]}""",
        )
        val page = vo.toOrderPageDetail(9L, emptyMap(), emptyMap(), "未分类")

        assertEquals(DiningOrderStatus.FINISHED, page.status)
        assertFalse("FINISHED 必须只读（隐藏勾选/备注/结束入口）", page.isOngoing)
        assertEquals("""{"ingredientNames":["西红柿","鸡蛋","葱"]}""", page.shoppingList)
    }

    // ==================== 辅助 ====================

    private fun menu(recipeId: Long, name: String, categoryId: Long?) =
        DiningOrderDetailVo.MenuRecipe(recipeId = recipeId, name = name, categoryId = categoryId)

    private fun detail(
        menu: List<DiningOrderDetailVo.MenuRecipe> = emptyList(),
        items: List<DiningOrderDetailVo.OrderItem> = emptyList(),
        members: List<DiningOrderDetailVo.OrderMember> = emptyList(),
        creatorUserId: Long? = 9L,
    ) = DiningOrderDetailVo(
        id = 1L,
        version = 3L,
        unchanged = false,
        title = "周五晚火锅",
        status = "ONGOING",
        creatorUserId = creatorUserId,
        menu = menu,
        items = items,
        members = members,
    )
}
