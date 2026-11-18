package com.smartwash.feature.order.impl.network.vo.review

import androidx.annotation.Keep
import com.smartwash.feature.order.api.model.OrderReview

/** 评价提交请求体（对齐后端 AddReviewFrom；content 可空） */
@Keep
data class ReviewSubmitRequest(
    val orderId: Long,
    val rating: Int,
    val content: String?,
)

/** 评价响应 VO（对齐后端 ReviewVo），经 [toModel] 映射 OrderReview */
@Keep
data class ReviewVo(
    val reviewId: Long = 0,
    val orderId: Long = 0,
    val userId: Long = 0,
    val userPhone: String? = null,
    val rating: Int = 0,
    val content: String? = null,
    val createdAt: String? = null,
) {
    fun toModel(): OrderReview = OrderReview(
        reviewId = reviewId,
        orderId = orderId,
        userId = userId,
        userPhone = userPhone.orEmpty(),
        rating = rating,
        content = content.orEmpty(),
        createdAt = createdAt.orEmpty(),
    )
}
