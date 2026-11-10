package com.smartwash.di

import com.smartwash.network.api.GameBridgeApi
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 游戏桥代理 Hilt 供给：
 * - [GameBridgeApi] 实例（复用 :common:network 注入的 Retrofit，baseUrl = BuildConfig.BASE_URL）；
 * - [GameBridgeEntryPoint] 供 WebView 容器（Composable）取用 API。
 *
 * 桥代理意义：H5 不持有 token，存档/成绩/关卡请求由原生侧带 token 发出，
 * 白名单 host 外桥不生效（fail-closed，见 GameThemeBridgePolicy）。
 */
@Module
@InstallIn(SingletonComponent::class)
object GameBridgeModule {

    @Provides
    @Singleton
    fun provideGameBridgeApi(retrofit: Retrofit): GameBridgeApi =
        retrofit.create(GameBridgeApi::class.java)
}

/**
 * 游戏桥 API 入口：Composable 经 EntryPointAccessors.fromActivity(...) 取用，
 * 避免为桥单独引入 ServiceLocator。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GameBridgeEntryPoint {
    fun gameBridgeApi(): GameBridgeApi
}
