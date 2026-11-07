package com.smartwash.feature.user.impl.network.entity.user

import androidx.annotation.Keep

/**
 * 刷新令牌请求体（对应后端 from/auth/RefreshTokenFrom：{"refreshToken": "..."}）。
 * 仅由 TokenRefresherImpl 经续签专用裸 OkHttp 客户端消费（不走 Retrofit 主通道）。
 */
@Keep
data class RefreshTokenRequest(
    val refreshToken: String,
)
