package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 创建点单订单请求体（POST /web/auth/dining/orders/create，与后端 from/CreateDiningOrderFrom
 * 字段对应）。
 *
 * 草稿协同点单：本接口创建的是 **DRAFT 草稿**（不再直接 ONGOING）——title/recipeIds 均可空，
 * 端上惰性创建（首次加菜或邀请时才调），标题在 publish 时才校验必填、菜单在 publish 时才
 * 校验非空。菜单来源二选一：显式 [recipeIds]（≤100 道，后端校验上架存在性并去重保序）或
 * [fromOrderId] 复用历史订单本单菜单（当前用户须为该单成员，13 号票「再来一单」入口）；
 * 二者同时携带后端拒绝。返回 orderId + joinToken（草稿期即可邀请）。
 */
@Keep
data class CreateDiningOrderRequest(
    /** 订单标题（如 周五晚火锅；草稿期可空，publish 时必填） */
    val title: String,
    /** 本单菜单菜谱 ID 集合（草稿期可空，后续由成员加菜/移除） */
    val recipeIds: List<Long>? = null,
    /** 要复用本单菜单的历史订单 ID，null=显式 recipeIds 链路 */
    val fromOrderId: Long? = null,
)
