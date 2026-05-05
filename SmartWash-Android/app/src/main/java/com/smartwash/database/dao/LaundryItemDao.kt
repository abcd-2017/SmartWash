package com.smartwash.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartwash.database.entity.LaundryItemEntity

@Dao
interface LaundryItemDao {
    @Query("SELECT * FROM laundry_items")
    suspend fun getAll(): List<LaundryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LaundryItemEntity>)

    @Query("DELETE FROM laundry_items")
    suspend fun deleteAll()
}
