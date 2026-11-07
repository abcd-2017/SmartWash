package com.smartwash.common.network

import com.smartwash.common.network.interceptor.RequestInterceptor
import com.smartwash.common.network.interceptor.ResponseInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 网络基础设施供给（T2.3 自 app 的 RetrofitClient 迁入，业务 API 实例与 Room 供给留在 app）：
 * - OkHttp：超时 + 鉴权/错误转译拦截器 + debug 日志
 * - Retrofit：baseUrl 经 @Named("baseUrl") 由 app 侧注入（app 的 BuildConfig.BASE_URL），本模块零环境配置感知
 */
@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        requestInterceptor: RequestInterceptor,
        responseInterceptor: ResponseInterceptor,
    ): OkHttpClient {
        // 不配置 HTTP 缓存：后端未返回缓存头，缓存恒不命中反而占用磁盘
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(requestInterceptor)
            .addInterceptor(responseInterceptor)

        if (BuildConfig.DEBUG) {
            // 日志脱敏（2026-11-07 BYOK，issue #79）：BODY 级日志会把 PUT/POST 请求体原样
            // 打进 logcat，解读模型 BYOK 的 apiKey 明文因此泄露。仅经构造函数注入 Logger
            // 包装输出通道做字段级正则脱敏（见 [maskApiKeyForLog]），BODY 级别、拦截链
            // （鉴权/401/信封解析）与输出目标（Android logcat INFO 级 "OKHttp" tag，
            // 等价默认 logger 行为）零改动。4.12.0 的 logger 为 private final，无 setter。
            val loggingInterceptor = HttpLoggingInterceptor(
                HttpLoggingInterceptor.Logger { message ->
                    android.util.Log.println(
                        android.util.Log.INFO, "OKHttp", maskApiKeyForLog(message)
                    )
                }
            )
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    /**
     * 专用于文件下载的 OkHttpClient（不配置 ResponseInterceptor，避免二进制响应体被当作 JSON 解析）
     */
    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, @Named("baseUrl") baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

/**
 * 请求/响应日志中 apiKey 字段值脱敏（2026-11-07 BYOK，issue #79）：
 * 形如 `"apiKey":"…"`（含大小写、空格变体）的 JSON 字段值替换为 `"apiKey":"***"`。
 *
 * 仅作用于 debug 日志输出通道（HttpLoggingInterceptor logger 包装），不触碰请求本身；
 * internal 供 JVM 单测锚点（同 ResponseInterceptor.resolveBusinessErrorMessage 先例）。
 * 注：OkHttp 超长日志（>4KB）会分段进 logger，跨段字段理论上可能漏匹配——BYOK 请求体
 * 远小于分段阈值，实际路径可靠；其余接口不涉及密钥字段。
 */
internal val API_KEY_LOG_PATTERN: Regex =
    Regex("\"\\s*api_?key\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE)

internal fun maskApiKeyForLog(message: String): String =
    message.replace(API_KEY_LOG_PATTERN, "\"apiKey\":\"***\"")
