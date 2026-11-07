package com.smartwash.feature.memo.network

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 速记手账接口 —— 后端契约（勿改形状）：
 * - POST /web/auth/memo {content(≤2000), tags[]} → Result<Long>
 * - GET /web/auth/memo/list?page&size&keyword?&tag? → Result<Page<MemoVo>>（置顶优先由后端排序）
 * - PUT /web/auth/memo/{id} {content, tags, pinned} → Result<String>
 * - DELETE /web/auth/memo/{id} → Result<String>（逻辑删除，端上删除即移出列表）
 * - GET /web/auth/memo/tags → Result<List<{tag, count}>>
 *
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；鉴权/错误转译拦截器经 NetworkModule 自动生效。
 * 业务语义：成功 code=200；业务失败 code=201 等已由 ResponseInterceptor 统一转译为
 * NetworkException（后端 message 保留在 exception.message）。所有数据以当前登录用户为主体
 * （服务端从 JWT 上下文取 user_id，端上无需传）。
 */
interface MemoApi {

    /** 新建一条随手记，返回记录 id */
    @POST("web/auth/memo")
    @RequireAuthorization
    suspend fun createMemo(@Body body: MemoSaveRequest): ApiResult<Long>

    /** 记录列表（分页）：keyword 模糊搜内容，tag 精确筛标签；置顶优先由后端排序 */
    @GET("web/auth/memo/list")
    @RequireAuthorization
    suspend fun pageMemos(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("keyword") keyword: String? = null,
        @Query("tag") tag: String? = null,
    ): ApiResult<PageData<MemoVo>>

    /** 更新记录（内容/标签/置顶全量提交；置顶切换也走本端点，内容与标签原样透传） */
    @PUT("web/auth/memo/{id}")
    @RequireAuthorization
    suspend fun updateMemo(@Path("id") id: Long, @Body body: MemoUpdateRequest): ApiResult<String>

    /** 删除记录（后端逻辑删除；端上成功即从列表移除） */
    @DELETE("web/auth/memo/{id}")
    @RequireAuthorization
    suspend fun deleteMemo(@Path("id") id: Long): ApiResult<String>

    /** 标签聚合（tag + count），驱动筛选行与输入区「常用标签」 */
    @GET("web/auth/memo/tags")
    @RequireAuthorization
    suspend fun listTags(): ApiResult<List<MemoTagCountVo>>
}

/** 新建请求体：content ≤ 2000（端上输入即截断），tags 空数组表示无标签 */
@Keep
data class MemoSaveRequest(
    val content: String,
    val tags: List<String>,
)

/** 更新请求体：三字段全量提交（置顶切换复用，content/tags 传当前值） */
@Keep
data class MemoUpdateRequest(
    val content: String,
    val tags: List<String>,
    val pinned: Boolean,
)

/** 记录条目（GET /list 分页 records 项） */
@Keep
data class MemoVo(
    val id: Long = 0L,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    // 后端 MemoVo 实际序列化键为 createdAt/updatedAt（非 createTime/updateTime），
    // Gson 按属性名映射，属性名必须与后端键一致，否则时间列解析为 null 显示空白
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** 标签聚合条目（GET /tags 响应项） */
@Keep
data class MemoTagCountVo(
    val tag: String = "",
    val count: Long = 0L,
)
