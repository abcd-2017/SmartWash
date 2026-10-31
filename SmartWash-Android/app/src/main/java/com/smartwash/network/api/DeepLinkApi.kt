package com.smartwash.network.api

import androidx.annotation.Keep
import com.smartwash.common.utils.model.ApiResult
import retrofit2.http.GET

/**
 * 深链路由字典接口（公开接口，无需鉴权——不加 @RequireAuthorization）。
 * 后端契约：WebDeeplinkRouteController#routes，路径前缀 /web/deeplink（SecurityConfig permitAll）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；错误转译拦截器自动生效（非 200 业务码抛 NetworkException）。
 */
interface DeepLinkApi {

    /** deeplink 路由字典：仅启用中的路由，按 id 升序 */
    @GET("web/deeplink/routes")
    suspend fun getRoutes(): ApiResult<List<DeeplinkRouteVo>>
}

/** 字典枚举值（对齐后端 AppDeeplinkRoute） */
object DeeplinkRouteDict {

    /** enabled：1 启用 0 停用 */
    const val ENABLED = 1

    /** platform：1 Android */
    const val PLATFORM_ANDROID = 1

    /** platform：2 HarmonyOS */
    const val PLATFORM_HARMONY = 2

    /** platform：3 双端 */
    const val PLATFORM_BOTH = 3
}

@Keep
data class DeeplinkRouteVo(
    val id: Long = 0L,

    /** smartwash:// 的 host，如 login */
    val host: String = "",

    /** 页面名称（管理端展示） */
    val name: String = "",

    /** 1 Android 2 HarmonyOS 3 双端 */
    val platform: Int = 0,

    /** 1 需要登录 0 不需要 */
    val requireLogin: Int = 0,

    /** 说明（如 web/t 的 scheme 用法） */
    val description: String = "",

    /** 1 启用 0 停用 */
    val enabled: Int = 0,
)
