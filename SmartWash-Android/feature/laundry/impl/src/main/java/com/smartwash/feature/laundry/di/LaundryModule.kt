package com.smartwash.feature.laundry.di

import com.smartwash.feature.laundry.api.SchoolSearchSource
import com.smartwash.feature.laundry.network.api.LaundryItemsApi
import com.smartwash.feature.laundry.network.api.SchoolApi
import com.smartwash.feature.laundry.repository.SchoolSearchSourceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

/**
 * 洗衣域服务契约绑定（T7.1）：
 * - SchoolSearchSource → SchoolSearchSourceImpl：跨模块消费方（user-impl 资料编辑页）
 *   经接口访问学校搜索，替换原 app 壳 UserImplSeamModule 的过渡实现（已删）；
 * - LaundryItemsApi/SchoolApi 实例随洗衣+学校域迁入（自 app/di 的 NetworkModule 拆出，
 *   模式同 order-impl 的 OrderServiceApiModule / payment-impl 的 PaymentApiModule）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LaundryModule {

    @Binds
    abstract fun bindSchoolSearchSource(impl: SchoolSearchSourceImpl): SchoolSearchSource
}

/** 洗衣域网络供给 */
@Module
@InstallIn(SingletonComponent::class)
object LaundryApiModule {

    @Provides
    fun provideLaundryItemsApi(retrofit: Retrofit): LaundryItemsApi =
        retrofit.create(LaundryItemsApi::class.java)

    @Provides
    fun provideSchoolApi(retrofit: Retrofit): SchoolApi =
        retrofit.create(SchoolApi::class.java)
}
