package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 加入前预览 brief VO（GET /web/auth/dining/orders/brief/{token} 载荷，与后端
 * dining/vo/DiningOrderBriefVo 对应）。
 *
 * 扫码解析出 join_token 后先拉本接口，用于「是否加入点餐环节：{订单标题}」确认弹窗：
 * joined 可提前判重避免无效弹窗，status 可提前提示订单已结束。
 *
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null，消费方经 safe 系兜底。
 */
@Keep
data class DiningOrderBriefVo(
    /** 订单 ID（确认加入后调 join 接口用） */
    val orderId: Long? = null,
    /** 订单标题 */
    val title: String? = null,
    /** 订单状态：ONGOING / FINISHED */
    val status: String? = null,
    /** 发起者标识（脱敏手机号，如 139****9000） */
    val creatorName: String? = null,
    /** 本单菜单菜品数 */
    val recipeCount: Long? = null,
    /** 当前用户是否已在该单中（已加入时端上可直接进详情页） */
    val joined: Boolean? = null,
) {
    val safeOrderId: Long get() = orderId ?: 0L
    val safeTitle: String get() = title.orEmpty()
    val safeStatus: String get() = status.orEmpty()
    val safeCreatorName: String get() = creatorName.orEmpty()
    val safeRecipeCount: Int get() = recipeCount?.toInt() ?: 0
    val safeJoined: Boolean get() = joined == true
}
