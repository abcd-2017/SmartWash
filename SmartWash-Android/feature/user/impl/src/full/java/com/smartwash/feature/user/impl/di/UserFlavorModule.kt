package com.smartwash.feature.user.impl.di

import com.smartwash.feature.user.impl.network.api.DivSettingApi
import com.smartwash.feature.user.impl.network.api.TransactionApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * full 变体用户域网络供给（flavor 源集专属，仅 full classpath 存在）。
 *
 * full 专属页面的 Retrofit 接口供给：
 * - [DivSettingApi]：观象台 BYOK（设置页「解读模型」入口，issue #79）；
 * - [TransactionApi]：钱包流水分页（钱包流水页）。
 *
 * 两者都是 full 专属页面的依赖，故接口与 @Provides 一并放本模块 src/full 源集
 * （放进 src/main 会让 lite classpath 引用 full 专属类型）。
 */
@Module
@InstallIn(SingletonComponent::class)
object UserFlavorModule {

    /** 观象台 BYOK 接口（设置页「解读模型」入口，issue #79） */
    @Provides
    @Singleton
    fun provideDivSettingApi(retrofit: Retrofit): DivSettingApi =
        retrofit.create(DivSettingApi::class.java)

    /** 钱包流水分页接口（钱包流水页） */
    @Provides
    @Singleton
    fun provideTransactionApi(retrofit: Retrofit): TransactionApi =
        retrofit.create(TransactionApi::class.java)
}
