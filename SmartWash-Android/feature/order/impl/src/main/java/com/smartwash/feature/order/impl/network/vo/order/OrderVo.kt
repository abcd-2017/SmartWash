package com.smartwash.feature.order.impl.network.vo.order

import androidx.annotation.Keep
import com.smartwash.feature.order.api.model.OrderBrief

/** 进行中订单响应 VO（原 app 的 network/vo/order/OrderVo，T6.1 迁入），经 [toModel] 映射 OrderBrief */
@Keep
data class OrderVo(
    val orderId: Long,
    val userId: Long = 0,
    val schoolId: Long = 0,
    val lockerId: Long = 0,
    val orderNo: String,
    val laundryItemsId: Long = 0,
    val totalPrice: Float = 0f,
    val payPrice: Float = 0f,
    val status: String,
    val pickupCode: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** 网络 VO → 对外领域模型（order-api 的 OrderBrief） */
internal fun OrderVo.toModel() = OrderBrief(
    orderId = orderId,
    userId = userId,
    schoolId = schoolId,
    lockerId = lockerId,
    orderNo = orderNo,
    laundryItemsId = laundryItemsId,
    totalPrice = totalPrice,
    payPrice = payPrice,
    status = status,
    pickupCode = pickupCode,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
