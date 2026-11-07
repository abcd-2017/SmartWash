package com.smartwash.feature.user.impl.network.entity.user

import androidx.annotation.Keep

/** 修改密码请求体（PUT /web/auth/user/password，6-16 位校验由端上先行 + 服务端兜底） */
@Keep
data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)
