package com.smartwash.network.vo.user

import com.smartwash.network.vo.school.SchoolVo

data class UserInfoVo(
    val phoneNumber: String,
    val studentId: String,
    val campusCard: String,
    val balance: Float,
    val schoolVo: SchoolVo,
)
