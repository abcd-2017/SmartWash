package com.smartwash.feature.update.network

import com.smartwash.common.model.ApiResult
import com.smartwash.feature.update.model.AppVersionVo
import retrofit2.http.GET

/**
 * 应用更新接口（公开接口，无需登录）
 */
interface AppUpdateApi {
    /**
     * 获取最新版本信息
     */
    @GET("/web/app/version")
    suspend fun getLatestVersion(): ApiResult<AppVersionVo>

    /**
     * 获取 APK 预签名下载地址（从 MinIO 获取临时下载链接）
     */
    @GET("/web/app/download")
    suspend fun getPresignedDownloadUrl(): ApiResult<String>
}
