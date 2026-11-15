package com.smartwash.di

import com.smartwash.deeplink.DeepLinkRepository
import com.smartwash.deeplink.DeepLinkRepositoryImpl
import com.smartwash.network.api.DeepLinkApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 深链路由字典 Hilt 供给（两变体共用，故留 main 源集）。
 *
 * - [DeepLinkApi]：复用 :common:network 注入的 Retrofit（baseUrl 走主网关 BuildConfig.BASE_URL，
 *   错误转译拦截器自动生效；公开接口不消费 @RequireAuthorization）；
 * - [DeepLinkRepository] 绑定：[DeepLinkRepositoryImpl] 用该 Api 做「未知 host 查后端字典」
 *   的兜底。lite 同样注入本仓库（MainActivity 的深链分发与扫码页共用
 *   [com.smartwash.deeplink.DeepLinkHandler]），字典拉取失败静默返回空列表，不影响本地解析，
 *   故两变体都保留。
 */
@Module
@InstallIn(SingletonComponent::class)
object DeepLinkApiModule {

    @Provides
    @Singleton
    fun provideDeepLinkApi(retrofit: Retrofit): DeepLinkApi =
        retrofit.create(DeepLinkApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeepLinkRepositoryModule {

    @Binds
    abstract fun bindDeepLinkRepository(impl: DeepLinkRepositoryImpl): DeepLinkRepository
}
