package com.smartwash.feature.divination.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartwash.feature.divination.database.DivRecordDao
import com.smartwash.feature.divination.database.DivinationDatabase
import com.smartwash.feature.divination.network.DivinationApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * v1 → v2（issue #78 增列）：div_records 增 serverRecordId / interpretationId 两列，
 * 列名/类型/默认值与 DivRecordEntity 定义逐字一致（Kotlin 非空 Long → INTEGER NOT NULL，
 * 兜底 0 = 未建档 / 未解读）。卦历案卷（question/castAt/serverRecordId）是用户数据
 * 不可重建——禁止 fallbackToDestructiveMigration（曾致升级清空用户全部卦历）。
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE div_records ADD COLUMN serverRecordId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE div_records ADD COLUMN interpretationId INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 观象台 Hilt 供给：卦历独立库 DivinationDatabase（T2.4 自 AppDatabase 拆出，
 * T4.1 随模块迁入）与解读 API（复用 :common:network 提供的 Retrofit，
 * 鉴权/错误转译拦截器自动生效）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DivinationModule {

    @Provides
    @Singleton
    fun provideDivinationDatabase(@ApplicationContext context: Context): DivinationDatabase {
        return Room.databaseBuilder(
            context,
            DivinationDatabase::class.java,
            "divination_db",
        )
            // 版本升级一律走显式 migration（见 MIGRATION_1_2）：卦历是用户数据，
            // 不可用破坏性迁移清库重建
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideDivRecordDao(database: DivinationDatabase): DivRecordDao =
        database.divRecordDao()

    @Provides
    @Singleton
    fun provideDivinationApi(retrofit: Retrofit): DivinationApi = retrofit.create(DivinationApi::class.java)
}
