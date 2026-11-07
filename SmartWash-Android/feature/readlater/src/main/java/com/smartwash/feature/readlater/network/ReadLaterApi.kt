package com.smartwash.feature.readlater.network

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 稍后读（AI 摘要）接口 —— 后端契约（/web/auth/readlater，随本功能同步交付）：
 * - POST /readlater             {url(https), tags[]} → Result<Long>（标题由后端抓取）
 * - GET  /readlater/list        page&size&status?&tag?&keyword? → Result<Page<ReadLaterVo>>
 * - POST /readlater/{id}/digest → Result<String>（AI 摘要，后端抓正文调 LLM，可能较慢）
 * - PUT  /readlater/{id}        {tags?, status?} → Result<String>（字段可选，缺省不更新）
 * - DELETE /readlater/{id}      → Result<String>
 * - POST /readlater/{id}/shorten → Result<String>（返回完整短链地址）
 *
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；鉴权/错误转译拦截器经 NetworkModule 自动生效。
 * 业务语义：成功 code=200；业务失败（参数非法/抓取失败等）由 ResponseInterceptor
 * 统一转译为 NetworkException（后端 message 保留在 exception.message）。
 * 所有数据以当前登录用户为主体（服务端从 JWT 上下文取 user_id，端上无需传）。
 */
interface ReadLaterApi {

    /** 收藏链接（url 仅收 https；标题由后端抓取，端上不传） */
    @POST("web/auth/readlater")
    @RequireAuthorization
    suspend fun addReadLater(@Body body: AddReadLaterRequest): ApiResult<Long>

    /** 分页列表：status/tag/keyword 均可选（null 不传，Gson/Retrofit 自动省略） */
    @GET("web/auth/readlater/list")
    @RequireAuthorization
    suspend fun listReadLater(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("status") status: Int? = null,
        @Query("tag") tag: String? = null,
        @Query("keyword") keyword: String? = null,
    ): ApiResult<PageData<ReadLaterVo>>

    /** 生成 AI 摘要（可能较慢，端上按条目给 loading 态） */
    @POST("web/auth/readlater/{id}/digest")
    @RequireAuthorization
    suspend fun generateDigest(@Path("id") id: Long): ApiResult<String>

    /** 更新标签/已读状态（字段可选：null 不更新，Gson 默认省略 null 字段） */
    @PUT("web/auth/readlater/{id}")
    @RequireAuthorization
    suspend fun updateReadLater(@Path("id") id: Long, @Body body: UpdateReadLaterRequest): ApiResult<String>

    /** 删除收藏 */
    @DELETE("web/auth/readlater/{id}")
    @RequireAuthorization
    suspend fun deleteReadLater(@Path("id") id: Long): ApiResult<String>

    /** 转短链，返回完整短链地址（端上复制到剪贴板） */
    @POST("web/auth/readlater/{id}/shorten")
    @RequireAuthorization
    suspend fun shorten(@Path("id") id: Long): ApiResult<String>
}

/** 收藏请求体：url 仅收 https；tags 可空（不传标签） */
@Keep
data class AddReadLaterRequest(
    val url: String,
    val tags: List<String> = emptyList(),
)

/** 更新请求体：tags/status 均可选，null 字段由 Gson 省略（后端按缺省不更新） */
@Keep
data class UpdateReadLaterRequest(
    val tags: List<String>? = null,
    val status: Int? = null,
)

/** 收藏条目（list 接口分页项）。注意时间字段为 createdAt（跨端对齐 MemoVo 修复的同一约定） */
@Keep
data class ReadLaterVo(
    val id: Long = 0L,
    val url: String = "",
    val title: String = "",
    /** AI 摘要，未生成时为 null/空白 */
    val digest: String? = null,
    val tags: List<String> = emptyList(),
    /** 0=未读 1=已读（见 ReadLaterConstant.STATUS_*） */
    val status: Int = 0,
    val createdAt: String? = null,
)
