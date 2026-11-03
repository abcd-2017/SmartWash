package com.smartwash.feature.review.di

import com.smartwash.feature.review.network.ReviewApi
import com.smartwash.feature.review.repository.ReviewRepository
import com.smartwash.feature.review.repository.ReviewRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 记忆复习 Hilt 供给：ReviewApi 复用 :common:network 注入的 Retrofit
 * （baseUrl 走主网关 BuildConfig.BASE_URL，鉴权/错误转译拦截器自动生效），
 * Repository 以接口绑定（JVM 单测可替换 Fake）。接线方式照 ToolboxModule。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReviewApiModule {

    @Provides
    @Singleton
    fun provideReviewApi(retrofit: Retrofit): ReviewApi =
        retrofit.create(ReviewApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewRepositoryModule {

    @Binds
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository
}
