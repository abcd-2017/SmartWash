package com.smartwash.common.network.interceptor

import android.os.Build
import com.smartwash.common.network.R
import com.smartwash.common.network.SessionEventNotifier
import com.smartwash.common.network.TokenProvider
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.network.util.NetworkTypeResolver
import com.smartwash.common.utils.AppLogger
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Named

class RequestInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val sessionEventNotifier: SessionEventNotifier,
    private val networkTypeResolver: NetworkTypeResolver,
    /** 宿主 app 的 versionName（app 变体经 @Named 提供，common:network 不可见 app BuildConfig） */
    @Named("appVersionName") private val appVersionName: String,
    /**
     * 宿主 app 的品牌名（变体差异：full=SmartWash / lite=TodayEat，app 经 @Named 提供）。
     * 拼进 User-Agent，避免 common 层硬编码品牌导致 lite 变体请求头暴露旧品牌。
     */
    @Named("appName") private val appName: String,
    /**
     * 宿主 app 的项目渠道（变体差异：full=full / lite=lite，app 经 @Named 提供）。
     *
     * 与 [PLATFORM] 正交：X-Platform 表达「端别」（android/ios/harmonyos/web，后端 ADR-0011
     * 封闭合法集），本头表达「同一端上的哪个 App 变体」，后端落 login_log.app_channel，
     * 供运营区分 SmartWash 与吃啥的登录来源。缺省不应硬编码——library 层臆断会让 lite
     * 请求落错渠道。
     */
    @Named("appChannel") private val appChannel: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 手机号脱敏：验证码接口路径含手机号，URL 入日志前统一打码
        val url = AppLogger.maskPhone(request.url.toString())

        val tag = request.tag(retrofit2.Invocation::class.java)
        val method = tag?.method()
        val annotated = method?.annotations?.any { it is RequireAuthorization }

        // 平台/项目标识头：全变体统一注入（与后端/Web 口径一致），供网关区分端别、项目与版本。
        // X-Platform 是端别（android），X-App-Channel 是同端上的 App 变体（full/lite），两者正交。
        val builder = request.newBuilder()
            .header("X-Platform", PLATFORM)
            .header("X-App-Channel", appChannel)
            .header("X-App-Version", appVersionName)
            .header("X-Network-Type", networkTypeResolver.current().wireName)
            .header("User-Agent", buildUserAgent(appName, appVersionName))

        if (annotated == true) {
            // 同步读进程内 token 缓存，不再 runBlocking 读 DataStore
            val token = tokenProvider.currentToken().orEmpty()

            if (token.isBlank()) {
                AppLogger.warn(TAG, "Request: ${request.method} $url — token 为空，拦截请求")
                sessionEventNotifier.notifyNeedLogin()
                throw NetworkException("未登录，请先登录", R.string.error_login_expired)
            }

            AppLogger.debug(TAG, "Request: ${request.method} $url — 携带 token")
            builder.header("Authorization", "Bearer $token")
            return chain.proceed(builder.build())
        }

        AppLogger.debug(TAG, "Request: ${request.method} $url")
        return chain.proceed(builder.build())
    }

    /**  UA 含品牌 / App 版本 / OS 版本 / 机型（覆盖 OkHttp 默认 okhttp/x.y.z） */
    private fun buildUserAgent(appName: String, version: String): String {
        val manufacturer = Build.MANUFACTURER.trim().trimStart()
        val model = Build.MODEL.trim()
        val device = when {
            manufacturer.isEmpty() -> model
            model.isEmpty() -> manufacturer
            model.startsWith(manufacturer) -> model
            else -> "$manufacturer $model"
        }
        val os = Build.VERSION.RELEASE ?: "unknown"
        return "$appName/$version (Android $os; $device)"
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "RequestInterceptor"

        /** 端别标识（Android 用户端，后端按此区分 Web/鸿蒙） */
        const val PLATFORM = "android"
    }
}
