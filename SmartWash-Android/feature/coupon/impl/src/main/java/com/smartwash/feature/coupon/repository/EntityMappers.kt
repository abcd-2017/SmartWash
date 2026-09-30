package com.smartwash.feature.coupon.repository

import com.smartwash.common.database.entity.CouponVoEntity
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo

/**
 * 缓存 Entity ↔ 网络 VO 映射（T7.2 随优惠券域自 app 的 repository/EntityMappers 迁入；
 * common:database 不依赖业务 VO，转换随消费方留本模块，模式同 laundry 的 EntityMappers）。
 */

fun CouponVoEntity.toVo() = CouponVo(
    couponId, title, description, discount, threshold,
    startTime, endTime, validDays, isNewUser, status,
)

fun CouponVo.toEntity() = CouponVoEntity(
    couponId = couponId,
    title = title,
    description = description,
    discount = discount,
    threshold = threshold,
    startTime = startTime,
    endTime = endTime,
    validDays = validDays,
    isNewUser = isNewUser,
    status = status,
)
