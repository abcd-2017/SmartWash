package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 我的进行中点单 VO（GET /web/auth/dining/orders/active 载荷，与后端 dining/vo/DiningOrderActiveVo 对应）。
 *
 * 唯一性收敛：同一发起者同时最多一个进行中的 DRAFT。列表 FAB / lite 主 CTA 据此决定
 * 「进该单编辑态」还是「空白创建页」；create 冲突（业务码 40901）时用于展示提示标题。
 *
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null；无进行中订单时 data 整体为 null。
 */
@Keep
data class DiningOrderActiveVo(
    val orderId: Long? = null,
    val title: String? = null,
    /** 恒为 DRAFT（本查询只取进行中），保留字段便于与列表状态口径统一 */
    val status: String? = null,
) {
    val safeOrderId: Long get() = orderId ?: 0L
    val safeTitle: String get() = title.orEmpty()
}
