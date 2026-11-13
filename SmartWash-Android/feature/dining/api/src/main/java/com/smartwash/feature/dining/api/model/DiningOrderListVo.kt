package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 我的订单列表条目 VO（GET /web/auth/dining/orders/my 载荷，与后端 dining/vo/DiningOrderListVo
 * 对应）：我发起的 + 我参与的（创建者即首个成员），按创建时间倒序。
 *
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null，消费方经 safe 系兜底；
 * 时间字段为后端 ISO 字符串原文（对齐洗衣订单 OrderVo.createdAt 先例，端上不反序列化为
 * LocalDateTime，展示层格式化）。
 */
@Keep
data class DiningOrderListVo(
    /** 订单 ID（列表 key / 分享视图路由参数） */
    val id: Long? = null,
    /** 订单标题 */
    val title: String? = null,
    /** 订单状态：ONGOING / FINISHED（白名单外脏数据由映射层保守降级） */
    val status: String? = null,
    /** 是否我发起的 */
    val isCreator: Boolean? = null,
    /** 发起者用户 ID */
    val creatorUserId: Long? = null,
    /** 创建时间（ISO 字符串原文，如 2026-11-13T20:30:00） */
    val createdAt: String? = null,
    /** 结束时间（进行中为 null） */
    val finishedAt: String? = null,
) {
    val safeId: Long get() = id ?: 0L
    val safeTitle: String get() = title.orEmpty()
    val safeStatus: String get() = status.orEmpty()
    val safeIsCreator: Boolean get() = isCreator == true
    val safeCreatedAt: String get() = createdAt.orEmpty()
    val safeFinishedAt: String get() = finishedAt.orEmpty()
}
