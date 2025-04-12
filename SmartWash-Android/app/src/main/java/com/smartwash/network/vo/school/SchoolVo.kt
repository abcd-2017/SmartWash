package com.smartwash.network.vo.school

import androidx.annotation.Keep

@Keep
data class SchoolVo(
    val schoolId: Long,
    val schoolName: String,
    val location: String,
    val lockerCount: Int
)
