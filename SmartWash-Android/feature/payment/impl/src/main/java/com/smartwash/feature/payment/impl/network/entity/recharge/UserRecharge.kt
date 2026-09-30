package com.smartwash.feature.payment.impl.network.entity.recharge

import androidx.annotation.Keep

@Keep
data class UserRecharge(val amount: Float, val rechargeType: String)
