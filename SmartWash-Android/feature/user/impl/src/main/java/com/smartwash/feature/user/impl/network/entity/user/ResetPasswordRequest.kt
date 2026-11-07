package com.smartwash.feature.user.impl.network.entity.user

import androidx.annotation.Keep

/** 重置密码请求体（POST /auth/user/resetPassword，忘记密码链路，免鉴权） */
@Keep
data class ResetPasswordRequest(val phoneNumber: String, val code: String, val newPassword: String)
