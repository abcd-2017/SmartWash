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

    /**
     * 本端品牌名（变体差异）：full=`SmartWash`、lite=`TodayEat`。
     *
     * common:network 的 RequestInterceptor 注入后拼接 User-Agent（`$appName/$version ...`）。
     * 硬编码在 common 层会让 lite 变体的请求头暴露旧品牌，故由壳层按 flavor 注入
     * （与 resValue `app_name` 同源，但 UA 用英文品牌名，与界面文案「吃啥」不同）。
     */
    @Provides
    @Named("appName")
    fun provideAppName(): String = BuildConfig.APP_NAME

    /**
     * 本端 OTA 分发渠道（变体差异）：full=`full`、lite=`lite`。
     *
     * 后端 `GET /web/app/version` 与 `GET /web/app/download` 按 channel 分组下发产物
     * （lite 走 `android-lite/` 对象前缀），两个渠道是独立 App，必须各取各的清单，
     * 否则会互推错包。由 `:feature:update:core` 的 `AppUpdateRepository` 注入后透传。
     */
    @Provides
    @Named("appChannel")
    fun provideAppChannel(): String = BuildConfig.APP_CHANNEL
}
