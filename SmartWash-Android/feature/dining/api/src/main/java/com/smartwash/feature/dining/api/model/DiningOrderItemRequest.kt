package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 勾选菜品请求体（POST /web/auth/dining/orders/{orderId}/items，与后端
 * from/AddDiningOrderItemFrom 字段对应）。
 *
 * 共享购物车乐观并发契约：[version] 为客户端已知订单版本，与服务端不一致返回业务码 409
 * （「订单已被他人更新，请刷新后重试」）。本票（09 点单页）按最小方式接入写接口——
 * 成功/失败仅记日志，不做冲突重放；[version] 由点单页当前详情持有，作为后续 10 号票
 * 重放接缝（失败时用最新详情的 version 重试）。
 */
@Keep
data class AddDiningOrderItemRequest(
    /** 要勾选的菜谱 ID（须在本单菜单范围内） */
    val recipeId: Long,
    /** 个人备注（如 不要辣），可空 */
    val note: String? = null,
    /** 客户端已知的订单 version（乐观并发闸门） */
    val version: Long,
)

/**
 * 修改选菜备注请求体（PUT /web/auth/dining/orders/{orderId}/items，与后端
 * from/UpdateDiningOrderItemFrom 字段对应）。
 *
 * 一人一菜一条：改的是本人那条；[note] 传 null/不传即清空备注。乐观并发契约同
 * [AddDiningOrderItemRequest]。
 */
@Keep
data class UpdateDiningOrderItemRequest(
    /** 菜谱 ID（本人须已勾选该菜） */
    val recipeId: Long,
    /** 新备注；null=清空备注 */
    val note: String? = null,
    /** 客户端已知的订单 version（乐观并发闸门） */
    val version: Long,
)
