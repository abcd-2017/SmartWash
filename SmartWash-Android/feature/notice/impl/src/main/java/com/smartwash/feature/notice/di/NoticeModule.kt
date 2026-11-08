package com.smartwash.feature.notice.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.notice.api.NoticeApi
import com.smartwash.feature.notice.api.NoticeRoute
import com.smartwash.feature.notice.api.NoticeServiceApi
import com.smartwash.feature.notice.repository.NoticeApiImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 站内信域服务契约绑定（模式同 coupon 的 CouponModule）：
 * - [NoticeApi] → [NoticeApiImpl]：跨模块消费方（user-impl 我的页未读角标）经接口访问；
 * - [NoticeServiceApi] 实例由本域提供（Retrofit 实例由 common:network 的 NetworkModule 提供）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoticeModule {

    @Binds
    @Singleton
    abstract fun bindNoticeApi(impl: NoticeApiImpl): NoticeApi
}

/** 站内信域网络供给 */
@Module
@InstallIn(SingletonComponent::class)
object NoticeApiModule {

    @Provides
    @Singleton
    fun provideNoticeServiceApi(retrofit: Retrofit): NoticeServiceApi =
        retrofit.create(NoticeServiceApi::class.java)
}

/**
 * 站内信域深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - messages → NoticeRoute.Inbox
 *
 * 登录守卫：收件箱消费 /web/auth 鉴权接口，host 不进壳层 PUBLIC_DEEP_LINK_HOSTS
 * 白名单（未登录由壳层拦截落登录页，对齐受保护 host 默认语义）。
 */
@Module
@InstallIn(SingletonComponent::class)
object NoticeDeepLinkModule {

    @Provides
    @IntoSet
    fun provideNoticeDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "messages" to DeepLinkRouteFactory { _, _ -> NoticeRoute.Inbox },
        )
    }
}
