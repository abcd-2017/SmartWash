package com.smartwash.feature.order.api.model

import com.smartwash.feature.order.api.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 订单状态码契约 JVM 单测（T6.1 自 app utils/OrderStatus.kt 迁入后的单一事实来源锚点）。
 *
 * 契约锚点（与后端 orders 表 status 对齐）：
 * -2 已取消 / -1 已退款 / 0 待支付 / 1 待发货（清洗前）/ 2 已取件（清洗中流转起点）/
 * 3 清洗中 / 4 已烘干 / 5 配送中 / 6 待取件 / 7 已完成；
 * 未知状态码 getDescriptionResByStatus 回落「全部」文案（order_status_all）。
 */
class OrderStatusMappingTest {

    @Test
    fun `状态码字符串与枚举一一对应`() {
        assertEquals(OrderStatus.CANCELED, OrderStatus.fromStatus("-2"))
        assertEquals(OrderStatus.REFUNDED, OrderStatus.fromStatus("-1"))
        assertEquals(OrderStatus.PENDING_PAYMENT, OrderStatus.fromStatus("0"))
        assertEquals(OrderStatus.PENDING_SHIPMENT, OrderStatus.fromStatus("1"))
        assertEquals(OrderStatus.RECEIVED, OrderStatus.fromStatus("2"))
        assertEquals(OrderStatus.WASHING, OrderStatus.fromStatus("3"))
        assertEquals(OrderStatus.DRIED, OrderStatus.fromStatus("4"))
        assertEquals(OrderStatus.IN_DELIVERY, OrderStatus.fromStatus("5"))
        assertEquals(OrderStatus.READY_FOR_PICKUP, OrderStatus.fromStatus("6"))
        assertEquals(OrderStatus.COMPLETED, OrderStatus.fromStatus("7"))
    }

    @Test
    fun `未知状态码 fromStatus 返回 null 不抛`() {
        assertEquals(null, OrderStatus.fromStatus("8"))
        assertEquals(null, OrderStatus.fromStatus(""))
        assertEquals(null, OrderStatus.fromStatus("x"))
        // 数值语义相同但字符串形态不同（带符号/带空格）不匹配：后端只下发裸字符串码
        assertEquals(null, OrderStatus.fromStatus("+3"))
        assertEquals(null, OrderStatus.fromStatus(" 3"))
    }

    @Test
    fun `状态码全集十个 无重复码`() {
        assertEquals(10, OrderStatus.entries.size)
        assertEquals(10, OrderStatus.entries.map { it.status }.distinct().size)
    }

    @Test
    fun `getDescriptionResByStatus 已知状态映射专属文案`() {
        assertEquals(R.string.order_status_pending_payment, OrderStatus.getDescriptionResByStatus("0"))
        assertEquals(R.string.order_status_washing, OrderStatus.getDescriptionResByStatus("3"))
        assertEquals(R.string.order_status_ready_for_pickup, OrderStatus.getDescriptionResByStatus("6"))
        assertEquals(R.string.order_status_completed, OrderStatus.getDescriptionResByStatus("7"))
    }

    @Test
    fun `getDescriptionResByStatus 未知状态回落全部文案`() {
        assertEquals(R.string.order_status_all, OrderStatus.getDescriptionResByStatus("99"))
        assertEquals(R.string.order_status_all, OrderStatus.getDescriptionResByStatus(""))
    }

    @Test
    fun `订单页 tab 状态子集契约`() {
        // 全部 tab 用虚拟码 001（不与真实状态冲突，只作为 tab 标识）
        assertEquals("001", ShowOrderStatus.ALL_ORDER.status)
        // tab 子集 = 待支付 / 待发货 / 清洗中 / 待取件 + 全部
        assertEquals(
            listOf("001", "0", "1", "3", "6"),
            ShowOrderStatus.entries.map { it.status },
        )
        // tab 里的每个真实状态码都能在 OrderStatus 全集中找到（映射闭环）
        ShowOrderStatus.entries
            .filter { it != ShowOrderStatus.ALL_ORDER }
            .forEach { tab -> assertEquals(tab.name, OrderStatus.fromStatus(tab.status)?.name) }
    }
}
