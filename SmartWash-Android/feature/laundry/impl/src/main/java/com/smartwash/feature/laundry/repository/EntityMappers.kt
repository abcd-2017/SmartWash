package com.smartwash.feature.laundry.repository

import com.smartwash.common.database.entity.LaundryItemEntity
import com.smartwash.common.database.entity.SchoolNameEntity
import com.smartwash.feature.laundry.network.vo.LaundryItem
import com.smartwash.feature.laundry.network.vo.SchoolName

/**
 * 缓存 Entity ↔ 网络 VO 映射（T7.1 随洗衣+学校域自 app 的 repository/EntityMappers 迁入；
 * common:database 不依赖业务 VO，转换随消费方留本模块。CouponVo 映射仍留 app 壳，
 * T7.2 随 coupon 域迁走）。
 */

fun LaundryItemEntity.toVo() = LaundryItem(itemId, itemName, basePrice, description)

fun LaundryItem.toEntity() = LaundryItemEntity(
    itemId = itemId,
    itemName = itemName,
    basePrice = basePrice,
    description = description,
)

fun SchoolNameEntity.toVo() = SchoolName(schoolId, schoolName)

fun SchoolName.toEntity() = SchoolNameEntity(
    schoolId = schoolId,
    schoolName = schoolName,
)
