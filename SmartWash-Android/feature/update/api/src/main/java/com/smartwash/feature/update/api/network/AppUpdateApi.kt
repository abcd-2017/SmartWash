package com.smartwash.feature.update.api.network

import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.update.api.model.AppVersionVo
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 应用更新接口（公开接口，无需登录）
 *
 * 两个端点均按 [channel] 分发：full 与 lite 是独立 App（applicationId 不同），
 * 版本清单与 APK 产物按渠道分组下发，避免互推错包。channel 由 repository 经
 * `@Named("appChannel")` 注入后透传（library 模块不可见 app 的 BuildConfig）。
 */
interface AppUpdateApi {
    /**
     * @param channel 分发渠道（full / lite），由调用方注入
     */
    @GET("/web/app/version")
    suspend fun getLatestVersion(@Query("channel") channel: String): ApiResult<AppVersionVo>

    /**
     * 获取 APK 预签名下载地址（从 MinIO 获取临时下载链接）
     *
     * @param channel 分发渠道（full / lite），决定对象前缀（full=android/，lite=android-lite/）
     */
    @GET("/web/app/download")
    suspend fun getPresignedDownloadUrl(@Query("channel") channel: String): ApiResult<String>
}
