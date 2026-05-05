package com.smartwash.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartwash.network.vo.laundry.LaundryItem

@Keep
@Entity(tableName = "laundry_items")
data class LaundryItemEntity(
    @PrimaryKey val itemId: Long,
    val itemName: String,
    val basePrice: Float,
    val description: String,
) {
    fun toVo() = LaundryItem(itemId, itemName, basePrice, description)

    companion object {
        fun fromVo(vo: LaundryItem) = LaundryItemEntity(
            itemId = vo.itemId,
            itemName = vo.itemName,
            basePrice = vo.basePrice,
            description = vo.description,
        )
    }
}
