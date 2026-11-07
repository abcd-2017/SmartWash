package com.smartwash.feature.user.impl.network.vo.user

import androidx.annotation.Keep

/**
 * 刷新令牌响应数据（对应后端 vo/TokenPairVo：{token, refreshToken}，无 role——
 * 角色语义以登录响应为准，刷新不重查角色）。
 *
 * refreshToken 一次性轮换：每次刷新成功后旧 refreshToken 立即失效，
 * 端上须以新双 token 覆盖会话存储（见 SessionManager.updateTokens）。
 */
@Keep
data class TokenPairVo(
    val token: String? = null,
    val refreshToken: String? = null,
)
