package com.smartwash.network.entity.user

import androidx.annotation.Keep

@Keep
data class RegisterUser(val phoneNumber: String, val password: String, val code: String)
