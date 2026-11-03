package com.smartwash.feature.toolbox.network

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * 设备投递接口（owner 侧）—— 契约见根目录 docs/delivery-api.md（v1 后端定稿）。
 * Android 为会话主人（owner），对端（电脑浏览器）走公开的 web/delivery 路由段，本端不消费。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；鉴权/错误转译拦截器经 NetworkModule 自动生效。
 *
 * 错误码语义（信封 code，非 HTTP 状态码）：403 密码错误 / 404 会话条目失效 / 409 状态冲突 /
 * 413 负载超限——该组业务码**不得触发全局 401 登出**（拦截器只特判 401/201，本组原样穿过，
 * 由 [com.smartwash.feature.toolbox.repository.DeliveryRepositoryImpl] 统一转译）。
 */
interface DeliveryApi {

    /** 创建投递会话：durationMinutes 1-120（缺省 30），password 可选 4-64 位（服务端 BCrypt 存储，不回传） */
    @POST("web/auth/toolbox/delivery/sessions")
    @RequireAuthorization
    suspend fun createSession(@Body body: AddDeliverySessionRequest): ApiResult<DeliverySessionVo>

    /** 会话详情 + 全量条目（owner 视角；条目按 createdAt 正序，含已焚毁占位行，轮询消费） */
    @GET("web/auth/toolbox/delivery/sessions/{code}")
    @RequireAuthorization
    suspend fun fetchSessionDetail(@Path("code") code: String): ApiResult<DeliverySessionDetailVo>

    /** 关闭会话：expire_at 前置为当前时刻，读路径立即失效（data=null，成功以 code=200 为准） */
    @POST("web/auth/toolbox/delivery/sessions/{code}/close")
    @RequireAuthorization
    suspend fun closeSession(@Path("code") code: String): ApiResult<Unit>

    /** 发布文本条目（正文上限 10 万字符；title 缺省由后端截取正文前 30 字） */
    @POST("web/auth/toolbox/delivery/sessions/{code}/items")
    @RequireAuthorization
    suspend fun publishText(
        @Path("code") code: String,
        @Body body: AddDeliveryTextRequest,
    ): ApiResult<DeliveryItemVo>

    /** 发布文件/图片条目（multipart；kind 2 图片 / 3 文件；单文件 ≤20MB） */
    @Multipart
    @POST("web/auth/toolbox/delivery/sessions/{code}/items/file")
    @RequireAuthorization
    suspend fun publishFile(
        @Path("code") code: String,
        @Part("kind") kind: RequestBody,
        @Part file: MultipartBody.Part,
    ): ApiResult<DeliveryItemVo>

    /** 取走条目：文本=认领+焚毁一步完成（textContent 随响应返回，此后不可再读）；文件=仅原子认领 */
    @POST("web/auth/toolbox/delivery/sessions/{code}/items/{itemId}/take")
    @RequireAuthorization
    suspend fun takeItem(
        @Path("code") code: String,
        @Path("itemId") itemId: Long,
    ): ApiResult<DeliveryTakeVo>

    /** 文本条目内容：未取走返回正文；已焚毁 textContent=null（响应信封 JSON） */
    @GET("web/auth/toolbox/delivery/sessions/{code}/items/{itemId}/content")
    @RequireAuthorization
    suspend fun fetchTextContent(
        @Path("code") code: String,
        @Path("itemId") itemId: Long,
    ): ApiResult<DeliveryTextContentVo>
}

@Keep
data class AddDeliverySessionRequest(
    /** 有效期分钟数，null = 走后端缺省 30 */
    val durationMinutes: Int? = null,
    /** 对端进入密码，null/缺省 = 无密码 */
    val password: String? = null,
)

@Keep
data class DeliverySessionVo(
    /** 7 位投递码，码即对端凭证 */
    val code: String = "",
    /** ISO LocalDateTime（yyyy-MM-dd'T'HH:mm:ss），过期/关闭后读路径一律 404 */
    val expireAt: String? = null,
    /** 对端累计进入次数（会话级无痕计数，仅此一项） */
    val clickCount: Long = 0L,
    val hasPassword: Boolean = false,
    val itemCount: Int = 0,
    /** 未取走条目数 */
    val pendingCount: Int = 0,
    /** 电脑浏览器打开的 H5 页地址（{TOOLBOX_PUBLIC_BASE_URL}/d/{code}），二维码内容即该 URL */
    val enterUrl: String? = null,
    val createdAt: String? = null,
)

@Keep
data class DeliverySessionDetailVo(
    val session: DeliverySessionVo? = null,
    val items: List<DeliveryItemVo> = emptyList(),
)

@Keep
data class AddDeliveryTextRequest(
    val textContent: String,
    /** 可选标题，缺省后端截取正文前 30 字 */
    val title: String? = null,
)

@Keep
data class DeliveryItemVo(
    val id: Long = 0L,
    /** 1 手机→电脑（owner 发布）、2 电脑→手机（对端发布），语义见 DeliveryRules.Direction */
    val direction: Int = 1,
    /** 1 文本、2 图片、3 文件，语义见 DeliveryRules.ItemKind */
    val kind: Int = 1,
    val title: String? = null,
    /** 0 未取走、1 已取走（焚毁后保留占位行） */
    val taken: Int = 0,
    val takenAt: String? = null,
    /** 文件/图片条目的字节数；文本为 null */
    val objectSize: Long? = null,
    val mimeType: String? = null,
    val sha256: String? = null,
    /** 文本=正文是否仍在；文件=是否未认领（已焚毁条目不回传内容本身） */
    val hasContent: Boolean = false,
    val createdAt: String? = null,
)

@Keep
data class DeliveryTakeVo(
    val id: Long = 0L,
    val kind: Int = 1,
    val title: String? = null,
    /** 文本条目：认领+焚毁一步完成，正文随响应返回；文件条目为 null */
    val textContent: String? = null,
    /** 文件条目认领后的下载路径（对端公开 URL；owner 侧固定走 owner content 端点，不消费本字段） */
    val contentUrl: String? = null,
)

@Keep
data class DeliveryTextContentVo(
    val id: Long = 0L,
    val kind: Int = 1,
    /** 已焚毁时为 null */
    val textContent: String? = null,
)
