package com.smartwash.feature.user.impl.network.vo.user

import androidx.annotation.Keep

/**
 * 登录/注册响应数据（对应后端 vo/LoginVo：{token, refreshToken, role}）。
 *
 * 双 token 契约变更：[refreshToken] 为续签预留凭据，端上仅存不用（续签链路后续统一接入）；
 * 注册接口响应已从 Result<String>（裸 token 字符串）升级为 Result<LoginVo>（注册即自动登录），
 * 登录/注册两条路径的解析结构一致，均为本对象。
 */
@Keep
data class LoginVo(
    val token: String? = null,
    val refreshToken: String? = null,
    val role: String? = null,
)
