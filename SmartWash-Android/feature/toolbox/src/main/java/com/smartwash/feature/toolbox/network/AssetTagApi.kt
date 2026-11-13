package com.smartwash.feature.toolbox.network

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
 * 物品标签（一物一码 M2）接口 —— 契约对齐后端 ToolboxAssetTagController
 * （/web/auth/toolbox/asset-tags，需 ROLE_USER）与鸿蒙 feature/toolbox/api.ets。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit。
 */
interface AssetTagApi {

    /** 创建标签：主表 type=8 短码 + 明细同事务；联系方式可选（服务端加密落库） */
    @POST("web/auth/toolbox/asset-tags")
    @RequireAuthorization
    suspend fun createAssetTag(@Body body: CreateAssetTagRequest): ApiResult<AssetTagVo>

    /** 我的标签分页（按创建时间倒序，含扫码统计） */
    @GET("web/auth/toolbox/asset-tags")
    @RequireAuthorization
    suspend fun pageAssetTags(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<AssetTagVo>>

    /** 更新标签（信息/联系方式/寻物状态；状态裁定在后端） */
    @PUT("web/auth/toolbox/asset-tags/{id}")
    @RequireAuthorization
    suspend fun updateAssetTag(
        @Path("id") id: Long,
        @Body body: UpdateAssetTagRequest,
    ): ApiResult<AssetTagVo>

    /** 删除标签（明细 + 访问明细 + 主表级联） */
    @DELETE("web/auth/toolbox/asset-tags/{id}")
    @RequireAuthorization
    suspend fun deleteAssetTag(@Path("id") id: Long): ApiResult<Unit>
}

@Keep
data class CreateAssetTagRequest(
    /** 物品名称（必填 ≤64 字符） */
    val itemName: String,
    /** 物品描述（可选 ≤256 字符） */
    val itemDesc: String? = null,
    /** 联系方式（可选 ≤64 字符；服务端加密落库） */
    val contact: String? = null,
    /** 自定义别名（可选 4-16 位），不传随机生成 */
    val customCode: String? = null,
)

@Keep
data class UpdateAssetTagRequest(
    val itemName: String? = null,
    val itemDesc: String? = null,
    /** 空串 = 清空联系方式；null = 不修改 */
    val contact: String? = null,
    /** 0 正常 / 1 寻物中 / 2 已寻回；null = 不修改 */
    val status: Int? = null,
)

@Keep
data class AssetTagVo(
    val id: Long = 0L,
    val code: String = "",
    val itemName: String? = null,
    val itemDesc: String? = null,
    /** 0 正常 / 1 寻物中 / 2 已寻回 */
    val status: Int = 0,
    /** 是否已留联系方式（明文永不回显） */
    val hasContact: Boolean = false,
    /** 分享链接：{toolbox.web-base-url}/tag/{code}；未配置时为空串（降级仅展示短码） */
    val tagUrl: String? = null,
    /** 累计扫码次数（失主线索） */
    val scanCount: Long = 0L,
    /** 最近被扫码时间，ISO LocalDateTime */
    val lastScannedAt: String? = null,
    val createdAt: String? = null,
)
