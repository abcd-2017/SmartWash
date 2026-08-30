package com.smartwash.network.api

import com.smartwash.network.entity.ApiResult
import com.smartwash.network.vo.AppVersionVo
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
}
