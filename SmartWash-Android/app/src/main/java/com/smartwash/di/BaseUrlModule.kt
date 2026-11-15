package com.smartwash.di

import com.smartwash.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/** 环境地址与变体信息供给（壳层提供，common:network 消费）。 */
@Module
@InstallIn(SingletonComponent::class)
object BaseUrlModule {
    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.BASE_URL

    /**
     * 宿主 versionName：common:network 的 RequestInterceptor 注入后写入 X-App-Version
     * 与 User-Agent（library 模块不可见 app 的 BuildConfig，故由壳层提供）。
     */
    @Provides
    @Named("appVersionName")
    fun provideAppVersionName(): String = BuildConfig.VERSION_NAME

    /**
     * 本端深链 scheme（变体差异）：full=`smartwash`、lite=`todayeat`。
     *
     * 与 Manifest 的 `${deepLinkScheme}` 占位符同源——两处都读 [BuildConfig.DEEP_LINK_SCHEME]，
     * 由 app/build.gradle 按 flavor 写入（full/lite 各自的 manifestPlaceholders 之外再落一份
     * BuildConfig，供 DeepLinkParser/DeepLinkHandler 运行期判定本端 scheme）。
     * 非本端 scheme 的深链一律 Unknown，不接受任意自定义 scheme。
     */
    @Provides
    @Named("deepLinkScheme")
    fun provideDeepLinkScheme(): String = BuildConfig.DEEP_LINK_SCHEME
}
