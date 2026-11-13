package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 创建点单订单请求体（POST /web/auth/dining/orders/create，与后端 from/CreateDiningOrderFrom
 * 字段对应）。
 *
 * 后端契约：title 必填（≤64 字符）；菜单来源二选一——显式 [recipeIds]（≤100 道，后端校验
 * 上架存在性并去重保序）或 [fromOrderId] 复用历史订单本单菜单（当前用户须为该单成员，
 * 13 号票「再来一单」入口）；二者同时携带后端拒绝，均为空后端报「本单菜单不能为空」。
 * 端上校验先行：显式链路校验标题必填 + 至少选一道菜；复用链路只校验标题（菜单由后端复制）。
 */
@Keep
data class CreateDiningOrderRequest(
    /** 订单标题（如 周五晚火锅） */
    val title: String,
    /** 本单菜单菜谱 ID 集合（创建后参与者的选菜范围） */
    val recipeIds: List<Long>? = null,
    /** 要复用本单菜单的历史订单 ID，null=显式 recipeIds 链路 */
    val fromOrderId: Long? = null,
)
