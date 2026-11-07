package com.smartwash.feature.user.impl.network.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.user.impl.network.entity.div.SaveUserApiConfig
import com.smartwash.feature.user.impl.network.vo.div.PlatformModelVo
import com.smartwash.feature.user.impl.network.vo.div.UserApiConfigVo
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST

/**
 * 观象台解读模型 BYOK（用户自带 API Key）Retrofit 接口。
 *
 * 后端契约（DivinationWebController，前缀 /web/auth/div，JWT ROLE_USER）：
 * - GET  /settings/api      查询配置（仅掩码；未配置时 data=null）
 * - PUT  /settings/api      保存（服务端先真实试呼，失败回业务失败信封，data 带原因文案）
 * - DELETE /settings/api    删除配置
 * - POST /settings/api/test 连通性测试（不保存；失败不抛信封异常，data=false）
 * - GET  /models            平台启用模型目录（BYOK 输入建议，不限于目录）
 *
 * 全部走 JSON 信封（非 SSE），经 :common:network 主通道（Retrofit + ResponseInterceptor）：
 * 鉴权注入与 401 清会话跳登录由拦截器统一处理，本接口只声明端点。
 * 业务失败（code=201）时 ResponseInterceptor 抛 NetworkException，message 已按
 * 「data 字符串优先、message 兜底」解析为用户可读文案。
 */
interface DivSettingApi {

    /** 查询用户 BYOK 配置（data=null 表示未配置，解读走平台默认模型） */
    @RequireAuthorization
    @GET("/web/auth/div/settings/api")
    suspend fun getUserApiConfig(): ApiResult<UserApiConfigVo>

    /** 保存/更换 BYOK（服务端试呼通过才入库；失败信封 data 带具体原因） */
    @RequireAuthorization
    @PUT("/web/auth/div/settings/api")
    suspend fun saveApiConfig(@Body body: SaveUserApiConfig): ApiResult<String>

    /** 删除 BYOK 配置（删除后解读回落平台默认模型） */
    @RequireAuthorization
    @DELETE("/web/auth/div/settings/api")
    suspend fun deleteApiConfig(): ApiResult<String>

    /** 连通性测试（不保存）：成功/失败均回 200 信封，以 data==true 判定通过 */
    @RequireAuthorization
    @POST("/web/auth/div/settings/api/test")
    suspend fun testApiConfig(@Body body: SaveUserApiConfig): ApiResult<Boolean>

    /** 平台启用模型目录（表单「模型名称」输入建议） */
    @RequireAuthorization
    @GET("/web/auth/div/models")
    suspend fun getPlatformModels(): ApiResult<List<PlatformModelVo>>
}
