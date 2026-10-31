package com.smartwash.feature.game.di

import com.smartwash.feature.game.network.GameApi
import com.smartwash.feature.game.repository.GameRepository
import com.smartwash.feature.game.repository.GameRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 游戏中心 Hilt 供给：GameApi 复用 :common:network 注入的 Retrofit
 * （baseUrl 走主网关 BuildConfig.BASE_URL，鉴权/错误转译拦截器自动生效），
 * Repository 以接口绑定（JVM 单测可替换 Fake）。
 */
@Module
@InstallIn(SingletonComponent::class)
object GameApiModule {

    @Provides
    @Singleton
    fun provideGameApi(retrofit: Retrofit): GameApi =
        retrofit.create(GameApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GameRepositoryModule {

    @Binds
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository
}
