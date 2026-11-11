package com.smartwash.feature.user.impl.network.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.user.impl.network.entity.div.SaveUserApiConfig
import com.smartwash.feature.user.impl.network.vo.div.PlatformVendorGroupVo
import com.smartwash.feature.user.impl.network.vo.div.UserApiConfigVo
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST

/**
 * 观象台解读模型 BYOK（用户自带 API Key）Retrofit 接口。
 *
 * 后端契约（DivinationWebController，前缀 /web/auth/div，JWT ROLE_USER，2026-11-11 重构版）：
 * - GET  /settings/api      查询配置（仅掩码；未配置时 data=null，解读统一 428 无平台兜底）
 * - PUT  /settings/api      保存（双模式二选一；服务端先真实试呼，失败回业务失败信封，data 带原因文案）
 * - DELETE /settings/api    删除配置（删除后用户需重新配置才能解读）
 * - POST /settings/api/test 连通性测试（不保存；失败不抛信封异常，data=false）
 * - GET  /models            平台启用模型目录（目录模式厂商 → 模型目录树，一次返回）
 *
 * 全部走 JSON 信封（非 SSE），经 :common:network 主通道（Retrofit + ResponseInterceptor）：
 * 鉴权注入与 401 清会话跳登录由拦截器统一处理，本接口只声明端点。
 * 业务失败（code=201）时 ResponseInterceptor 抛 NetworkException，message 已按
 * 「data 字符串优先、message 兜底」解析为用户可读文案。
 */
interface DivSettingApi {

    /** 查询用户解读模型配置（data=null 表示未配置；平台零兜底，未配置解读统一 428） */
    @RequireAuthorization
    @GET("/web/auth/div/settings/api")
    suspend fun getUserApiConfig(): ApiResult<UserApiConfigVo>

    /** 保存/更换配置（双模式二选一，服务端试呼通过才入库；失败信封 data 带具体原因） */
    @RequireAuthorization
    @PUT("/web/auth/div/settings/api")
    suspend fun saveApiConfig(@Body body: SaveUserApiConfig): ApiResult<String>

    /** 删除配置（删除后用户需重新配置模型才能解读） */
    @RequireAuthorization
    @DELETE("/web/auth/div/settings/api")
    suspend fun deleteApiConfig(): ApiResult<String>

    /** 连通性测试（不保存）：成功/失败均回 200 信封，以 data==true 判定通过 */
    @RequireAuthorization
    @POST("/web/auth/div/settings/api/test")
    suspend fun testApiConfig(@Body body: SaveUserApiConfig): ApiResult<Boolean>

    /** 平台启用模型目录树（目录模式第一步选厂商、第二步选该厂商的模型） */
    @RequireAuthorization
    @GET("/web/auth/div/models")
    suspend fun getPlatformModels(): ApiResult<List<PlatformVendorGroupVo>>
}
