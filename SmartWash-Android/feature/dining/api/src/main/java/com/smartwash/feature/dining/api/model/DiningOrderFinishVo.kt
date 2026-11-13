package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 结束点单响应 VO（POST /web/auth/dining/orders/{id}/finish 载荷，
 * 与后端 dining/vo/DiningOrderFinishVo 对应）。
 *
 * 首次结束返回新写入的购买清单快照；重复调用幂等返回库内原快照（端上据此进结果页，
 * 不重复触发聚合）。shoppingList 为 JSON 文本，结构化解析见
 * [DiningShoppingListSnapshot] 与 impl 的 DiningShoppingListMappers。
 */
@Keep
data class DiningOrderFinishVo(
    /** 订单 ID */
    val id: Long? = null,
    /** 订单标题 */
    val title: String? = null,
    /** 订单状态（结束成功或幂等命中均为 FINISHED） */
    val status: String? = null,
    /** 结束时间（ISO 字符串原文，进行中为 null） */
    val finishedAt: String? = null,
    /** 购买清单快照 JSON 文本（食材名并集去重 + 按菜视图） */
    val shoppingList: String? = null,
) {
    val safeId: Long get() = id ?: 0L
    val safeTitle: String get() = title.orEmpty()
    val safeStatus: String get() = status.orEmpty()
    val safeFinishedAt: String get() = finishedAt.orEmpty()
    val safeShoppingList: String get() = shoppingList.orEmpty()
}
