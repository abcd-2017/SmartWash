package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderListVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.DiningOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 菜谱/点单订单 VO → UI 模型映射 JVM 单测：safe 兜底与分类 chips 组装（Gson 全可空容错
 * 契约的消费端锚点——缺字段/类型漂移时映射不抛、不产生垃圾展示值）。
 */
class DiningMappersTest {

    // ==================== DiningRecipeVo → DiningRecipeItem ====================

    @Test
    fun `完整 Vo 映射保留全部字段`() {
        val vo = DiningRecipeVo(
            id = 7L,
            name = "番茄炒蛋",
            categoryName = "家常菜",
            coverUrl = "http://minio.local:9000/bucket/dining/covers/a.png",
            ingredients = listOf("西红柿", "鸡蛋"),
            steps = "热锅下油",
            source = "USER",
            status = 1,
        )
        val item = vo.toRecipeItem()
        assertEquals(7L, item.id)
        assertEquals("番茄炒蛋", item.name)
        assertEquals("家常菜", item.categoryName)
        assertEquals("http://minio.local:9000/bucket/dining/covers/a.png", item.coverUrl)
        assertEquals(listOf("西红柿", "鸡蛋"), item.ingredients)
        assertEquals(2, item.ingredientCount)
    }

    @Test
    fun `全空 Vo 映射走 safe 兜底不抛出`() {
        val item = DiningRecipeVo().toRecipeItem()
        assertEquals(0L, item.id)
        assertEquals("", item.name)
        assertEquals("", item.categoryName)
        assertEquals("", item.coverUrl)
        assertTrue(item.ingredients.isEmpty())
        assertEquals(0, item.ingredientCount)
    }

    @Test
    fun `部分缺失字段逐项兜底（缺分类名与封面）`() {
        val item = DiningRecipeVo(id = 3L, name = "凉拌黄瓜", ingredients = null).toRecipeItem()
        assertEquals(3L, item.id)
        assertEquals("凉拌黄瓜", item.name)
        assertEquals("未分类（后端 categoryName=null）兜底空串，列表隐藏角标", "", item.categoryName)
        assertEquals("未上传封面兜底空串，列表渲染占位底", "", item.coverUrl)
        assertTrue(item.ingredients.isEmpty())
    }

    // ==================== 分类列表 → 筛选 chips ====================

    @Test
    fun `分类列表映射为 chips 且头部固定全部伪分类`() {
        val categories = listOf(
            DiningCategoryVo(id = 2L, name = "汤羹", sort = 1),
            DiningCategoryVo(id = 1L, name = "家常菜", sort = 2),
        )
        val chips = categories.toCategoryChips(allLabel = "全部")
        assertEquals(3, chips.size)
        assertEquals("头部必须是「全部」伪分类（id=null 不带筛选参数）", DiningCategoryChip(id = null, name = "全部"), chips[0])
        assertEquals(DiningCategoryChip(id = 2L, name = "汤羹"), chips[1])
        assertEquals(DiningCategoryChip(id = 1L, name = "家常菜"), chips[2])
    }

    @Test
    fun `空分类列表仍保留全部伪分类（降级为无筛选可用）`() {
        val chips = emptyList<DiningCategoryVo>().toCategoryChips(allLabel = "全部")
        assertEquals(listOf(DiningCategoryChip(id = null, name = "全部")), chips)
    }

    @Test
    fun `分类 id 缺失按 0 兜底（后端异常响应不抛出，chip 不崩溃）`() {
        val chips = listOf(DiningCategoryVo(id = null, name = "幽灵分类")).toCategoryChips(allLabel = "全部")
        assertEquals(0L, chips[1].id)
        assertEquals("幽灵分类", chips[1].name)
    }

    // ==================== 点单订单列表映射 ====================

    @Test
    fun `订单列表 Vo 完整映射保留全部字段`() {
        val item = DiningOrderListVo(
            id = 11L,
            title = "周五晚火锅",
            status = "ONGOING",
            isCreator = true,
            creatorUserId = 9L,
            createdAt = "2026-11-13T20:30:00",
        ).toOrderListItem()
        assertEquals(11L, item.id)
        assertEquals("周五晚火锅", item.title)
        assertEquals(DiningOrderStatus.ONGOING, item.status)
        assertTrue(item.isCreator)
        assertEquals("2026-11-13 20:30", item.displayTime)
    }

    @Test
    fun `订单列表 Vo 全空走 safe 兜底且状态保守降级为已结束`() {
        val item = DiningOrderListVo().toOrderListItem()
        assertEquals(0L, item.id)
        assertEquals("", item.title)
        assertEquals("白名单外/缺失状态按不可写降级（不引导用户对未知态下单）", DiningOrderStatus.FINISHED, item.status)
        assertFalse(item.isCreator)
        assertEquals("", item.displayTime)
    }

    @Test
    fun `订单状态白名单外脏数据保守降级为已结束`() {
        val item = DiningOrderListVo(status = "CANCELLED").toOrderListItem()
        assertEquals(DiningOrderStatus.FINISHED, item.status)
    }

    @Test
    fun `订单时间 ISO 串格式化为年月日时分`() {
        assertEquals(
            "2025-01-05 09:07",
            formatDiningTime("2025-01-05T09:07:33"),
        )
    }

    @Test
    fun `订单时间带毫秒与零时区尾巴也能解析`() {
        assertEquals("2026-03-01 12:00", formatDiningTime("2026-03-01T12:00:00.123"))
        assertEquals("2026-03-01 12:00", formatDiningTime("2026-03-01T12:00:00Z"))
    }

    @Test
    fun `订单时间畸形原文原样返回不抛出`() {
        assertEquals("昨天", formatDiningTime("昨天"))
        assertEquals("", formatDiningTime(null))
        assertEquals("", formatDiningTime(""))
    }

    // ==================== 点单订单详情（分享视图）映射 ====================

    @Test
    fun `订单详情 Vo 映射出分享视图模型`() {
        val vo = DiningOrderDetailVo(
            id = 11L,
            version = 5L,
            unchanged = false,
            title = "周末大餐",
            status = "ONGOING",
            menu = listOf(
                DiningOrderDetailVo.MenuRecipe(recipeId = 1L, name = "番茄炒蛋", categoryId = 2L),
                DiningOrderDetailVo.MenuRecipe(recipeId = 2L, name = "凉拌黄瓜", categoryId = null),
            ),
            members = listOf(
                DiningOrderDetailVo.OrderMember(userId = 9L, isCreator = true, phoneMasked = "139****9000"),
                DiningOrderDetailVo.OrderMember(userId = 10L, isCreator = false, phoneMasked = "138****8000"),
            ),
        )
        val detail = vo.toOrderShareDetail()
        assertEquals(11L, detail.id)
        assertEquals("周末大餐", detail.title)
        assertEquals(DiningOrderStatus.ONGOING, detail.status)
        assertEquals(2, detail.menuCount)
        assertEquals(2, detail.memberCount)
    }

    @Test
    fun `订单详情 Vo 轻量响应与全空不崩溃且菜单数兜底为 0`() {
        val unchanged = DiningOrderDetailVo(id = 1L, version = 3L, unchanged = true).toOrderShareDetail()
        assertEquals(DiningOrderStatus.FINISHED, unchanged.status)
        assertEquals(0, unchanged.menuCount)

        val empty = DiningOrderDetailVo().toOrderShareDetail()
        assertEquals(0L, empty.id)
        assertEquals("", empty.title)
        assertEquals(DiningOrderStatus.FINISHED, empty.status)
        assertEquals(0, empty.menuCount)
        assertEquals(0, empty.memberCount)
    }
}
