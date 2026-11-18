package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 草稿加菜请求体（POST /web/auth/dining/orders/{id}/dishes，与后端
 * from/AddDiningOrderDishFrom 字段对应）。
 *
 * 后端契约：仅 DRAFT 态、全部成员可加；已存在按幂等成功返回（不重复落库、不递增 version）；
 * [version] 为客户端已知版本，做乐观锁（与服务端不一致返回业务码 409，端上刷新后重试）。
 */
@Keep
data class AddDiningOrderDishRequest(
    /** 要加入本单菜单的菜谱 ID（须存在且上架） */
    val recipeId: Long,
    /** 客户端已知的订单 version（乐观并发闸门） */
    val version: Long,
)
