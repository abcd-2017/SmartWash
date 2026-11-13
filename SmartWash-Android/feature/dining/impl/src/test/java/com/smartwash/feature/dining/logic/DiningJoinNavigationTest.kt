package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 扫码加入后导航归一 JVM 单测：点单页路由已注册 → 解析出带订单 ID 的路由；
 * 未注册（09 号票未落地）→ 返回 null 由调用方回退订单列表。
 * 同时锚定不与洗护订单详情（OrderDetail/{orderId}）混淆。
 */
class DiningJoinNavigationTest {

    @Test
    fun `点单页路由已注册时解析出导航串`() {
        val routes = listOf("DiningOrderList", "DiningOrderDetail/{orderId}")
        assertEquals("DiningOrderDetail/42", DiningJoinNavigation.resolveOrderDetailRoute(routes, 42L))
    }

    @Test
    fun `点单页路由参数名不同也能归一`() {
        val routes = listOf("DiningOrderDetailPage/{id}")
        assertEquals("DiningOrderDetailPage/7", DiningJoinNavigation.resolveOrderDetailRoute(routes, 7L))
    }

    @Test
    fun `未注册点单页时返回 null（回退订单列表）`() {
        val routes = listOf("DiningOrderList", "DiningOrderCreate", "OrderDetail/{orderId}")
        assertNull(DiningJoinNavigation.resolveOrderDetailRoute(routes, 1L))
    }

    @Test
    fun `不把洗护订单详情误判为点单页`() {
        val routes = listOf("OrderDetail/{orderId}", "PickupDelivery/{orderId}/{pickupType}")
        assertNull(DiningJoinNavigation.resolveOrderDetailRoute(routes, 1L))
    }

    @Test
    fun `查询参数形态的点单页路由也能归一`() {
        val routes = listOf("DiningOrderDetail?orderId={orderId}")
        assertEquals("DiningOrderDetail?orderId=9", DiningJoinNavigation.resolveOrderDetailRoute(routes, 9L))
    }
}
