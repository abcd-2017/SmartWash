package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 加入点单订单请求体（POST /web/auth/dining/orders/{id}/join，与后端
 * dining/from/JoinDiningOrderFrom 字段对应）。
 *
 * joinToken 来自二维码内容（`{origin}/dining/join/{orderId}?t={join_token}`），
 * 服务端校验 token 匹配 + 订单 ONGOING + 未重复加入。
 */
@Keep
data class JoinDiningOrderRequest(
    /** 加入凭证（与订单当前 join_token 一致才放行；发起者重置后旧 token 全部作废） */
    val joinToken: String,
)
