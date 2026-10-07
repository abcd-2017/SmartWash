package com.smartwash.feature.toolbox.network

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.toolbox.ToolboxConstant
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 工具箱短链接口 —— 契约见根目录 docs/短链内核实现方案.md §6.1（后端已交付）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；鉴权/错误转译拦截器经 NetworkModule 自动生效。
 *
 * 注意：resolve 会计入一次点击统计，客户端只在「私有码打开」时使用，
 * 禁止用于详情页刷新（详情字段由列表页路由携带）。
 */
interface ToolboxApi {

    /** 创建短码：target 必填（http/https），expireAt 不传 = 永久 */
    @POST("web/auth/toolbox/short-codes")
    @RequireAuthorization
    suspend fun createShortCode(@Body body: AddShortCodeRequest): ApiResult<ShortCodeVo>

    /** 我的短码分页（按创建时间倒序），contentType 筛选暂不使用（M1 仅短链类型） */
    @GET("web/auth/toolbox/short-codes")
    @RequireAuthorization
    suspend fun pageShortCodes(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("contentType") contentType: Int? = null,
    ): ApiResult<PageData<ShortCodeVo>>

    /** 更新短码：target/expireAt 不传即不修改（续期走这里，活码改向同） */
    @PUT("web/auth/toolbox/short-codes/{id}")
    @RequireAuthorization
    suspend fun updateShortCode(
        @Path("id") id: Long,
        @Body body: UpdateShortCodeRequest,
    ): ApiResult<ShortCodeVo>

    /** 删除短码（物理删除并清跳转缓存；后端 data 返回 null，成功以 code=200 为准） */
    @DELETE("web/auth/toolbox/short-codes/{id}")
    @RequireAuthorization
    suspend fun deleteShortCode(@Path("id") id: Long): ApiResult<Unit>

    /** 访问明细分页（IP 仅存摘要） */
    @GET("web/auth/toolbox/short-codes/{id}/stats")
    @RequireAuthorization
    suspend fun pageShortCodeStats(
        @Path("id") id: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<ShortCodeStatsVo>>

    /** owner 解析（私有码唯一取值通道，校验归属；计入一次点击统计） */
    @GET("web/auth/toolbox/short-codes/resolve")
    @RequireAuthorization
    suspend fun resolveShortCode(@Query("code") code: String): ApiResult<ShortCodeVo>
}

@Keep
data class AddShortCodeRequest(
    val target: String,
    val customCode: String? = null,
    /** ISO LocalDateTime（yyyy-MM-dd'T'HH:mm:ss），null = 永久 */
    val expireAt: String? = null,
    /** 1公开（默认） 2私有 */
    val isPublic: Int = ToolboxConstant.VISIBILITY_PUBLIC,
)

@Keep
data class UpdateShortCodeRequest(
    val target: String? = null,
    val expireAt: String? = null,
)

@Keep
data class ShortCodeVo(
    val id: Long = 0L,
    val code: String = "",
    /** 1短链 2活码 3交付箱 4图床 5时间胶囊 */
    val contentType: Int = 1,
    val target: String? = null,
    /** 1公开 2私有 */
    val isPublic: Int = 1,
    val unlockAt: String? = null,
    val expireAt: String? = null,
    val maxVisits: Long? = null,
    val clickCount: Long = 0L,
    /** 完整跳转 URL：{toolbox.public-base-url}/web/t/{code}，二维码由客户端生成 */
    val shortUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Keep
data class ShortCodeStatsVo(
    val id: Long = 0L,
    val code: String = "",
    /** IP 摘要前 8 位，不存明文 */
    val ipHash: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val createdAt: String? = null,
)
