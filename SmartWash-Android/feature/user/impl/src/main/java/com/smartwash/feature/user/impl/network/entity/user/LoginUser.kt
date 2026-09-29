package com.smartwash.feature.user.impl.network.entity.user

import androidx.annotation.Keep

@Keep
data class LoginUser(val phoneNumber: String, val password: String)
