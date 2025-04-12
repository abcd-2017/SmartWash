package com.smartwash.network.vo.locker

import androidx.annotation.Keep

@Keep
data class LockerVo(
    val lockerId: Long,
    val schoolId: Long,
    val lockerNumber: Int,
    val status: Long
)
