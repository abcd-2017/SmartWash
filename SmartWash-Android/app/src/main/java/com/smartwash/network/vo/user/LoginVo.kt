package com.smartwash.network.vo.user

import androidx.annotation.Keep

/**
 * 登录响应数据（对应后端 vo/LoginVo：{token, role}）
 *
 * 注意：仅 /auth/user/login 返回该对象；/auth/user/register 仍直接返回 token 字符串，
 * 两条路径的解析不得互相污染。
 */
@Keep
data class LoginVo(
    val token: String? = null,
    val role: String? = null,
)
