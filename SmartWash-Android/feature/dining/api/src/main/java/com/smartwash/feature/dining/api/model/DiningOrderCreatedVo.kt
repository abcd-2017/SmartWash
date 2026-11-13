package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 创建点单订单返回 VO（后端 dining/vo/DiningOrderCreatedVo 对应）。
 *
 * [joinToken] 是端上拼二维码内容（https://{host}/dining/join/{orderId}?t={join_token}）的
 * 唯一来源——后端详情/列表接口均不回传 token（重置接口仅发起者可换新）。创建成功即跳
 * 分享视图并携带本 token 展示二维码；离开分享视图后 token 不再可得（重置凭证入口后续票）。
 *
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null，消费方经 safe 系兜底。
 */
@Keep
data class DiningOrderCreatedVo(
    /** 新订单 ID（分享视图详情拉取 / 后续点单页路由参数） */
    val orderId: Long? = null,
    /** 加入凭证（发起者重置后旧码作废，需重新生成二维码） */
    val joinToken: String? = null,
) {
    val safeOrderId: Long get() = orderId ?: 0L
    val safeJoinToken: String get() = joinToken.orEmpty()
}
