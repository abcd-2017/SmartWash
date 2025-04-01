package com.smartwash.network.vo.order

import com.smartwash.network.vo.laundry.LaundryItem
import com.smartwash.network.vo.locker.LockerVo
import com.smartwash.network.vo.school.SchoolVo
import com.smartwash.network.vo.user.UserInfoVo

data class OrderInfo(
    val orderId: Long,
    val userVo: UserInfoVo,
    val schoolsVo: SchoolVo,
    val lockersVo: LockerVo,
    val orderNo: String,
    val laundryPackageVo: LaundryItem,
    val totalPrice: Float,
    val status: String,
    val pickupCode: String
)
