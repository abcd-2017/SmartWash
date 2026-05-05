package com.smartwash.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartwash.network.vo.coupon.CouponVo

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
) {
    fun toVo() = CouponVo(
        couponId, title, description, discount, threshold,
        startTime, endTime, validDays, isNewUser, status,
    )

    companion object {
        fun fromVo(vo: CouponVo) = CouponVoEntity(
            couponId = vo.couponId,
            title = vo.title,
            description = vo.description,
            discount = vo.discount,
            threshold = vo.threshold,
            startTime = vo.startTime,
            endTime = vo.endTime,
            validDays = vo.validDays,
            isNewUser = vo.isNewUser,
            status = vo.status,
        )
    }
}
