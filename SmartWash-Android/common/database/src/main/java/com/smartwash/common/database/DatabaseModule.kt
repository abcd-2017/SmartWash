package com.smartwash.common.database

import android.content.Context
import androidx.room.Room
import com.smartwash.common.database.dao.CouponVoDao
import com.smartwash.common.database.dao.LaundryItemDao
import com.smartwash.common.database.dao.SchoolNameDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room 供给（T2.4 自 app/di/AppDatabaseModule 迁入）：AppDatabase 与三张缓存表 DAO。
 */
@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartwash_db",
        )
            // 三表均为网络数据的本地缓存（Cache-first，进入页面即回填），schema 变更时
            // 允许破坏性重建；v2→v3 拆出 div_records 亦走此路径（卦历改由 divination
            // 独立库承接，旧库存量不迁移——demo 可接受）。
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideLaundryItemDao(database: AppDatabase): LaundryItemDao {
        return database.laundryItemDao()
    }

    @Provides
    fun provideSchoolNameDao(database: AppDatabase): SchoolNameDao {
        return database.schoolNameDao()
    }

    @Provides
    fun provideCouponVoDao(database: AppDatabase): CouponVoDao {
        return database.couponVoDao()
    }
}
