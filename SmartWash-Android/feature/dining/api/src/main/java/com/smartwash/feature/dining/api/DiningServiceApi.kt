package com.smartwash.feature.dining.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.dining.api.model.AddDiningRecipeRequest
import com.smartwash.feature.dining.api.model.CreateDiningOrderRequest
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DiningOrderCreatedVo
import com.smartwash.feature.dining.api.model.AddDiningOrderItemRequest
import com.smartwash.feature.dining.api.model.DiningOrderBriefVo
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderFinishVo
import com.smartwash.feature.dining.api.model.DiningOrderListVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.JoinDiningOrderRequest
import com.smartwash.feature.dining.api.model.UpdateDiningOrderItemRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 吃啥域网络接口（路径/参数/响应结构以后端 WebDiningRecipeController /
 * WebDiningCategoryController / WebDiningOrderController 为契约源，接口需 ROLE_USER——
 * /web/auth 路径由 SecurityConfig 通配，@RequireAuthorization 注入 Bearer）。
 *
 * 放置说明（对齐 notice/points 先例）：Retrofit 接口与 Gson 载荷一起放 api 模块——
 * 域内 impl 直接消费，跨模块消费方（后续订单域）经 [DiningApi] 领域契约访问，
 * 不直连本接口；依赖方向 api → common:network 合法（铁律 1）。
 *
 * 订单接口消费面（07 号票发起链路）：创建 / 我的订单 / 详情（首次全量拉取）；
 * 选菜增改删（09 号票点单页本地勾选最小接入，version 冲突重放为 10 号票）；
 * brief / join / 重置凭证为扫码加入链路后续票消费。
 */
interface DiningServiceApi {

    /** 全量菜谱分类（sort 升序，供菜谱库分类筛选条） */
    @GET("/web/auth/dining/categories/all")
    @RequireAuthorization
    suspend fun categories(): ApiResult<List<DiningCategoryVo>>

    /** 上架菜谱分页（categoryId 精确 / keyword 菜名模糊，null=不过滤；仅返回上架，新菜谱在前） */
    @GET("/web/auth/dining/recipes/all")
    @RequireAuthorization
    suspend fun recipes(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("categoryId") categoryId: Long? = null,
        @Query("keyword") keyword: String? = null,
    ): ApiResult<PageData<DiningRecipeVo>>

    /** 菜谱详情（仅上架可见，下架/不存在返回同一业务失败） */
    @GET("/web/auth/dining/recipes/{id}")
    @RequireAuthorization
    suspend fun recipeDetail(@Path("id") id: Long): ApiResult<DiningRecipeVo>

    /** 新增菜谱（source=USER 直上架，无审核流；categoryId 传 0/null=未分类） */
    @POST("/web/auth/dining/recipes/add")
    @RequireAuthorization
    suspend fun addRecipe(@retrofit2.http.Body request: AddDiningRecipeRequest): ApiResult<String>

    /** 上传菜谱封面（multipart 图片，后端校验 JPG/PNG/WebP + 魔数 + ≤5MB，返回 dining/covers/ 对象键） */
    @Multipart
    @POST("/web/auth/dining/recipes/cover")
    @RequireAuthorization
    suspend fun uploadCover(@Part file: MultipartBody.Part): ApiResult<String>

    /** 创建点单订单（title + recipeIds，或 fromOrderId 复用历史菜单；返回 orderId + joinToken 供拼二维码） */
    @POST("/web/auth/dining/orders/create")
    @RequireAuthorization
    suspend fun createOrder(@retrofit2.http.Body request: CreateDiningOrderRequest): ApiResult<DiningOrderCreatedVo>

    /** 我的订单列表（我发起的 + 我参与的，按创建时间倒序，带 isCreator 标记） */
    @GET("/web/auth/dining/orders/my")
    @RequireAuthorization
    suspend fun myOrders(): ApiResult<List<DiningOrderListVo>>

    /** 订单详情（轮询主接口；[knownVersion] 与服务端一致返回 unchanged=true 轻量响应，null=首次全量拉取） */
    @GET("/web/auth/dining/orders/{id}")
    @RequireAuthorization
    suspend fun orderDetail(
        @Path("id") id: Long,
        @Query("knownVersion") knownVersion: Long? = null,
    ): ApiResult<DiningOrderDetailVo>

    /** 加入前预览 brief（凭二维码 token 换标题/发起者/菜品数/是否已在单中，用于确认弹窗；token 无效/订单结束由后端业务失败上抛） */
    @GET("/web/auth/dining/orders/brief/{token}")
    @RequireAuthorization
    suspend fun orderBrief(@Path("token") token: String): ApiResult<DiningOrderBriefVo>

    /** 加入订单（校验 token 匹配 + 订单 ONGOING + 未重复加入；三类失败均有明确业务提示） */
    @POST("/web/auth/dining/orders/{id}/join")
    @RequireAuthorization
    suspend fun joinOrder(
        @Path("id") id: Long,
        @Body request: JoinDiningOrderRequest,
    ): ApiResult<Void>

    /** 重置加入凭证（仅发起者；旧二维码全部作废，返回新 joinToken） */
    @POST("/web/auth/dining/orders/{id}/join-token/reset")
    @RequireAuthorization
    suspend fun resetJoinToken(@Path("id") id: Long): ApiResult<String>

    /**
     * 结束点单（仅发起者且仅 ONGOING；聚合所有选菜生成购买清单快照写库，status→FINISHED）。
     * 重复调用幂等返回库内原快照；非发起者/状态异常返回后端业务失败上抛。
     */
    @POST("/web/auth/dining/orders/{id}/finish")
    @RequireAuthorization
    suspend fun finishOrder(@Path("id") id: Long): ApiResult<DiningOrderFinishVo>

    /** 勾选菜品（共享购物车；菜须在本单菜单内；一人一菜一条，重复勾选拒绝；version 冲突返回业务码 409） */
    @POST("/web/auth/dining/orders/{orderId}/items")
    @RequireAuthorization
    suspend fun addOrderItem(
        @Path("orderId") orderId: Long,
        @Body request: AddDiningOrderItemRequest,
    ): ApiResult<Void>

    /** 修改选菜备注（仅本人选菜记录；note 不传即清空；version 冲突返回业务码 409） */
    @PUT("/web/auth/dining/orders/{orderId}/items")
    @RequireAuthorization
    suspend fun updateOrderItemNote(
        @Path("orderId") orderId: Long,
        @Body request: UpdateDiningOrderItemRequest,
    ): ApiResult<Void>

    /** 取消选菜（删除本人该菜的选菜记录；version 冲突返回业务码 409） */
    @DELETE("/web/auth/dining/orders/{orderId}/items/{recipeId}")
    @RequireAuthorization
    suspend fun removeOrderItem(
        @Path("orderId") orderId: Long,
        @Path("recipeId") recipeId: Long,
        @Query("version") version: Long,
    ): ApiResult<Void>
}
