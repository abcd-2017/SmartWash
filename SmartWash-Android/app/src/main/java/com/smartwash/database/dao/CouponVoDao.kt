package com.smartwash.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartwash.database.entity.CouponVoEntity

@Dao
interface CouponVoDao {
    @Query("SELECT * FROM coupon_vos")
    suspend fun getAll(): List<CouponVoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CouponVoEntity>)

    @Query("DELETE FROM coupon_vos")
    suspend fun deleteAll()
}
