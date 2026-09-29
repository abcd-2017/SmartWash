package com.smartwash.feature.divination.di

import android.content.Context
import androidx.room.Room
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
        ).build()
    }

    @Provides
    fun provideDivRecordDao(database: DivinationDatabase): DivRecordDao =
        database.divRecordDao()

    @Provides
    @Singleton
    fun provideDivinationApi(retrofit: Retrofit): DivinationApi = retrofit.create(DivinationApi::class.java)
}
