package com.smartwash.feature.user.impl.di

import com.smartwash.common.network.SessionEventNotifier
import com.smartwash.common.network.TokenProvider
import com.smartwash.common.init.InitTask
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.impl.UserApiImpl
import com.smartwash.feature.user.impl.init.SessionInitTask
import com.smartwash.feature.user.impl.network.api.UserAccountApi
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit

/**
 * 用户域服务契约绑定（T5.2）：
 * - UserApi → UserApiImpl：跨模块消费方（order/payment/laundry 与 app 壳）经接口访问用户域；
 * - TokenProvider / SessionEventNotifier → SessionManager / SessionEventBus：
 *   common:network 拦截器的跨模块契约绑定，自 app/di 的 SessionNetworkBindingModule 随迁
 *   （实现类已在本模块，绑定跟着实现走）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserImplModule {

    @Binds
    abstract fun bindUserApi(impl: UserApiImpl): UserApi

    @Binds
    abstract fun bindTokenProvider(impl: SessionManager): TokenProvider

    @Binds
    abstract fun bindSessionEventNotifier(impl: SessionEventBus): SessionEventNotifier
}

/**
 * 用户域网络供给（T5.2 自 app/di/NetworkModule 拆出）：UserAccountApi 实例随用户域迁入，
 * 其余业务 API（order/payment/laundry/coupon/school）仍留 app，待各自 feature 阶段迁移。
 */
@Module
@InstallIn(SingletonComponent::class)
object UserAccountApiModule {

    @Provides
    fun provideUserAccountApi(retrofit: Retrofit): UserAccountApi =
        retrofit.create(UserAccountApi::class.java)
}

/**
 * 启动任务注册（模式同 feature:update 的 UpdateInitModule）：
 * SessionInitTask 经 @IntoSet 汇入 InitTaskRegistry，App.onCreate 由 InitEngine 调度。
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionInitModule {

    @Provides
    @IntoSet
    fun provideSessionInitTask(sessionManager: SessionManager): InitTask =
        SessionInitTask(sessionManager)
}
