package com.smartwash.common.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 洗衣项目缓存行（网络 LaundryItem 的本地快照；↔VO 映射见 app 侧 EntityMappers） */
@Keep
@Entity(tableName = "laundry_items")
data class LaundryItemEntity(
    @PrimaryKey val itemId: Long,
    val itemName: String,
    val basePrice: Float,
    val description: String,
)
