package com.smartwash.common.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 优惠券缓存行（网络 CouponVo 的本地快照；↔VO 映射见 app 侧 EntityMappers） */
@Keep
@Entity(tableName = "coupon_vos")
data class CouponVoEntity(
    @PrimaryKey val couponId: Long,
    val title: String,
    val description: String,
    val discount: Float,
    val threshold: Float,
    val startTime: String,
    val endTime: String,
    val validDays: Int,
    val isNewUser: Boolean,
    val status: String,
)
