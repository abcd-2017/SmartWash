package com.smartwash.network.api

import androidx.annotation.Keep
import com.smartwash.common.utils.model.ApiResult
import retrofit2.http.GET

/**
 * 服务端公开配置接口（App 启动拉取，替代编译期注入）。
 * 后端契约：WebConfigController#config，路径 /web/config（公开只读，SecurityConfig 已放行）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址。
 */
interface ServerConfigApi {

    /** 服务端公开配置：MinIO 对外端点 + 短链对外域名 */
    @GET("web/config")
    suspend fun getConfig(): ApiResult<ServerConfigVo>
}

/**
 * 服务端公开配置 VO（对齐后端 ServerConfigVo，字段名逐一对齐勿改名）。
 */
@Keep
data class ServerConfigVo(
    /** MinIO 对外可访问端点（externalEndpoint 优先，缺失回退 endpoint） */
    val minioEndpoint: String? = null,
    /** 短链对外跳转域名（后端拼 shortUrl 用；端上仅展示，不拼接） */
    val shortLinkBaseUrl: String? = null,
)
