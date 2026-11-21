package com.smartwash.feature.order.impl.network.vo.order

import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.OrderReview
import com.smartwash.feature.order.impl.network.vo.review.ReviewVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 订单网络 VO → 领域模型映射 JVM 单测（Gson 空值容错契约锚点）。
 *
 * 背景回归：后端对未绑校园卡/学号的用户返回 null、列表接口不返回 userVo、终态订单清空
 * pickupCode、LEFT JOIN 缺柜机——Gson 经 Unsafe 绕过 Kotlin 构造器反序列化，非空声明
 * 只是 Kotlin 侧谎言，直接透传会在映射阶段抛 NPE（2026-11-06 下单后闪退 / 启动崩溃）。
 * toModel 统一落安全默认值（空串 / 0 / id 落 -1），UI 消费侧无需判空。
 */
class OrderVoMapperTest {

    // ==================== OrderInfoVo.toModel：全字段缺失容错 ====================

    private fun emptyInfoVo() = OrderInfoVo(
        orderId = 100L,
        userVo = null,
        schoolsVo = null,
        lockersVo = null,
        orderNo = null,
        laundryPackageVo = null,
        totalPrice = null,
        payPrice = null,
        status = null,
        pickupCode = null,
        createdAt = null,
        updatedAt = null,
    )

    @Test
    fun `全字段缺失的订单详情映射落安全默认值`() {
        val model = emptyInfoVo().toModel()
        assertEquals(100L, model.orderId)
        // 用户快照缺失：字符串落空串、余额落 0、id 落 -1
        assertEquals("", model.orderNo)
        assertEquals("", model.userVo.phoneNumber)
        assertEquals("", model.userVo.studentId)
        assertEquals("", model.userVo.campusCard)
        assertEquals(0f, model.userVo.balance)
        assertNull(model.userVo.avatar)
        // 学校 / 柜机 / 套餐快照缺失：id 落 -1、文本落空串
        assertEquals(-1L, model.schoolsVo.schoolId)
        assertEquals("", model.schoolsVo.schoolName)
        assertEquals(-1L, model.lockersVo.lockerId)
        assertEquals("", model.lockersVo.status)
        assertEquals(-1L, model.laundryPackageVo.itemId)
        assertEquals("", model.laundryPackageVo.itemName)
        // 金额 / 状态 / 时间
        assertEquals(0f, model.totalPrice)
        assertEquals(0f, model.payPrice)
        assertEquals("", model.status)
        assertEquals("", model.pickupCode) // 终态订单取件码被清空 → 空串
        assertEquals("", model.createdAt)
        assertEquals("", model.updatedAt)
    }

    @Test
    fun `全量快照逐字段映射`() {
        val vo = OrderInfoVo(
            orderId = 7L,
            userVo = OrderInfoVo.UserVo(
                phoneNumber = "13800000000",
                studentId = "20260001",
                campusCard = "CARD-1",
                balance = 55.5f,
                avatar = "https://cdn.example.com/a.png",
            ),
            schoolsVo = OrderInfoVo.School(1L, "示范大学", "北京市海淀区", 12),
            lockersVo = OrderInfoVo.Locker(2L, 1L, 3, "1"),
            orderNo = "ORD-20261121-0007",
            laundryPackageVo = OrderInfoVo.LaundryPackage(9L, "标准洗", 15f, "含烘干"),
            totalPrice = 15f,
            payPrice = 13f,
            status = "3",
            pickupCode = "886644",
            createdAt = "2026-11-21T08:00:00",
            updatedAt = "2026-11-21T09:00:00",
        )
        val model = vo.toModel()
        assertEquals("13800000000", model.userVo.phoneNumber)
        assertEquals("20260001", model.userVo.studentId)
        assertEquals("CARD-1", model.userVo.campusCard)
        assertEquals(55.5f, model.userVo.balance)
        assertEquals("https://cdn.example.com/a.png", model.userVo.avatar)
        assertEquals(1L, model.schoolsVo.schoolId)
        assertEquals("示范大学", model.schoolsVo.schoolName)
        assertEquals(12, model.schoolsVo.lockerCount)
        assertEquals(2L, model.lockersVo.lockerId)
        assertEquals(3, model.lockersVo.lockerNumber)
        assertEquals("ORD-20261121-0007", model.orderNo)
        assertEquals(9L, model.laundryPackageVo.itemId)
        assertEquals("标准洗", model.laundryPackageVo.itemName)
        assertEquals(15f, model.totalPrice)
        assertEquals(13f, model.payPrice)
        assertEquals("3", model.status)
        assertEquals("886644", model.pickupCode)
        assertEquals("2026-11-21T08:00:00", model.createdAt)
    }

    @Test
    fun `快照对象存在但内部字段缺失 仍落安全默认值`() {
        // 后端对未绑学校的用户返回 schoolVo 空对象（字段全 null）
        val vo = emptyInfoVo().copy(
            schoolsVo = OrderInfoVo.School(null, null, null, null),
            userVo = OrderInfoVo.UserVo(null, null, null, null, null),
        )
        val model = vo.toModel()
        assertEquals(-1L, model.schoolsVo.schoolId)
        assertEquals("", model.userVo.phoneNumber)
        assertEquals(0f, model.userVo.balance)
    }

    // ==================== OrderVo.toModel：pickupCode null 收敛 ====================

    @Test
    fun `进行中订单 pickupCode 为 null 收敛为空串`() {
        // 清洗中订单尚未分配取件码（后端 pickup_code 为 NULL），映射不得抛 NPE
        val vo = OrderVo(orderId = 1L, orderNo = "NO-1", status = "3", pickupCode = null)
        val model = vo.toModel()
        assertEquals("", model.pickupCode)
        assertEquals("NO-1", model.orderNo)
        assertEquals("3", model.status)
    }

    @Test
    fun `进行中订单全字段映射含默认值`() {
        val vo = OrderVo(
            orderId = 5L, userId = 11L, schoolId = 2L, lockerId = 8L,
            orderNo = "NO-5", laundryItemsId = 9L,
            totalPrice = 15f, payPrice = 12f,
            status = "6", pickupCode = "112233",
            createdAt = "c", updatedAt = "u",
        )
        val model = vo.toModel()
        assertEquals(5L, model.orderId)
        assertEquals(11L, model.userId)
        assertEquals(2L, model.schoolId)
        assertEquals(8L, model.lockerId)
        assertEquals(9L, model.laundryItemsId)
        assertEquals(15f, model.totalPrice)
        assertEquals(12f, model.payPrice)
        assertEquals("112233", model.pickupCode)
        assertEquals("c", model.createdAt)
    }

    // ==================== OrderItemCountVo.toModel ====================

    @Test
    fun `各状态订单计数逐字段映射`() {
        val model = OrderItemCountVo(
            pendingPaymentCount = 2,
            processingCount = 3,
            pendingPickupCount = 1,
            shippedCount = 4,
        ).toModel()
        assertEquals(2, model.pendingPaymentCount)
        assertEquals(3, model.processingCount)
        assertEquals(1, model.pendingPickupCount)
        assertEquals(4, model.shippedCount)
    }

    // ==================== ReviewVo.toModel ====================

    @Test
    fun `评价响应可空字段收敛为空串`() {
        val model = ReviewVo(reviewId = 9L, orderId = 1L, rating = 3).toModel()
        assertEquals(9L, model.reviewId)
        assertEquals(1L, model.orderId)
        assertEquals(3, model.rating)
        assertEquals("", model.userPhone)
        assertEquals("", model.content)
        assertEquals("", model.createdAt)
    }

    @Test
    fun `评价响应全字段映射`() {
        val model = ReviewVo(
            reviewId = 10L, orderId = 2L, userId = 3L,
            userPhone = "13800000000", rating = 4,
            content = "洗得很干净", createdAt = "2026-11-21T10:00:00",
        ).toModel()
        assertEquals("13800000000", model.userPhone)
        assertEquals(4, model.rating)
        assertEquals("洗得很干净", model.content)
        assertEquals("2026-11-21T10:00:00", model.createdAt)
    }

    @Test
    fun `评价缺省字段映射落安全默认值`() {
        val model: OrderReview = ReviewVo(rating = 1).toModel()
        assertEquals(0L, model.reviewId) // 缺省 reviewId 落 0
        assertEquals(0L, model.orderId)
        assertEquals(0L, model.userId)
        assertEquals(1, model.rating)
    }
}
