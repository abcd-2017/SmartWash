package com.smartwash.feature.payment.impl.network.vo.recharge

import androidx.annotation.Keep

@Keep
data class RechargeRecordVo(
    val recordId: Long,
    val amount: Float,
    val rechargeType: String,
    val rechargeTime: String
)
