package com.smartwash.feature.dining.di

import com.smartwash.feature.dining.api.DiningRoute
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 家庭点单域深链贡献者 JVM 单测（锚点，对齐 GameDeepLinkModuleTest 先例）：
 * dining host 映射订单列表路由（域首页，07 号票落位后由菜谱库占位重指向至此），
 * 忽略 path 段与 query；未登记 host 不在贡献表。
 *
 * 重指向锚定（07 号票）：spec 规划域首页为订单列表页——04 号票曾占位指向菜谱库，
 * 订单列表落位后 host "dining" 正式指向 [DiningRoute.OrderList]（工作台入口同批上线）。
 * 本测试为该取舍的防漂移锚点，再变更须同步 DiningDeepLinkModule 注释与 impl AGENTS.md。
 */
class DiningDeepLinkModuleTest {

    /** 被测贡献者（object 方法直调，Hilt 注解不影响 JVM 实例化） */
    private val contributor = DiningDeepLinkModule.provideDiningDeepLinkContributor()

    private val factory = contributor.routes().getValue("dining")

    @Test
    fun `dining host 映射订单列表路由且忽略参数`() {
        assertEquals(DiningRoute.OrderList, factory.route(emptyList(), emptyMap()))
        assertEquals(DiningRoute.OrderList, factory.route(listOf("unexpected"), emptyMap()))
        assertEquals(DiningRoute.OrderList, factory.route(emptyList(), mapOf("x" to "1")))
    }

    @Test
    fun `dining order 路径解析加入入口并携带 token`() {
        assertEquals(
            DiningRoute.OrderJoin(42L, "abc"),
            factory.route(listOf("order", "42"), mapOf("t" to "abc")),
        )
    }

    @Test
    fun `dining order 路径缺 token 仍落加入入口由页面提示无效凭证`() {
        assertEquals(DiningRoute.OrderJoin(42L, ""), factory.route(listOf("order", "42"), emptyMap()))
    }

    @Test
    fun `dining order 路径非法回退订单列表`() {
        assertEquals(DiningRoute.OrderList, factory.route(listOf("order"), emptyMap()))
        assertEquals(DiningRoute.OrderList, factory.route(listOf("order", "abc"), mapOf("t" to "x")))
        assertEquals(DiningRoute.OrderList, factory.route(listOf("order", "0"), mapOf("t" to "x")))
        assertEquals(DiningRoute.OrderList, factory.route(listOf("order", "42", "extra"), mapOf("t" to "x")))
        assertEquals(DiningRoute.OrderList, factory.route(listOf("orders", "42"), mapOf("t" to "x")))
    }

    @Test
    fun `贡献表仅登记 dining`() {
        assertEquals(setOf("dining"), contributor.routes().keys)
    }

    @Test
    fun `未登记 host 不在贡献表`() {
        assertNull(contributor.routes()["dining-recipes"])
        assertNull(contributor.routes()["dining-orders"])
        assertNull(contributor.routes()["messages"])
        assertNull(contributor.routes()["game-center"])
        assertFalse(contributor.routes().containsKey("web"))
    }

    @Test
    fun `路由串锚点`() {
        assertEquals("DiningOrderList", serializer<DiningRoute.OrderList>().descriptor.serialName)
        assertEquals("DiningOrderCreate", serializer<DiningRoute.OrderCreate>().descriptor.serialName)
        assertEquals(
            "DiningOrderDetail/{orderId}",
            serializer<DiningRoute.OrderDetail>().descriptor.serialName,
        )
        assertEquals(
            "DiningOrderShare/{orderId}",
            serializer<DiningRoute.OrderShare>().descriptor.serialName,
        )
        assertEquals(
            "DiningOrderJoin/{orderId}/{token}",
            serializer<DiningRoute.OrderJoin>().descriptor.serialName,
        )
        assertEquals("DiningRecipeLibrary", serializer<DiningRoute.RecipeLibrary>().descriptor.serialName)
        assertEquals("DiningRecipeCreate", serializer<DiningRoute.RecipeCreate>().descriptor.serialName)
    }
}
