package com.smartwash.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smartwash.database.dao.CouponVoDao
import com.smartwash.database.dao.LaundryItemDao
import com.smartwash.database.dao.SchoolNameDao
import com.smartwash.database.entity.CouponVoEntity
import com.smartwash.database.entity.LaundryItemEntity
import com.smartwash.database.entity.SchoolNameEntity
import com.smartwash.divination.data.DivRecordDao
import com.smartwash.divination.data.DivRecordEntity

@Database(
    entities = [
        LaundryItemEntity::class,
        SchoolNameEntity::class,
        CouponVoEntity::class,
        DivRecordEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun laundryItemDao(): LaundryItemDao
    abstract fun schoolNameDao(): SchoolNameDao
    abstract fun couponVoDao(): CouponVoDao

    // 观象台卦历（占卜模块 v2 新增表）
    abstract fun divRecordDao(): DivRecordDao
}
