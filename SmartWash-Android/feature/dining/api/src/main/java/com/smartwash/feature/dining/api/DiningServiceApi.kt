package com.smartwash.feature.dining.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.dining.api.model.DiningOrderActiveVo
import com.smartwash.feature.dining.api.model.AddDiningOrderDishRequest
import com.smartwash.feature.dining.api.model.AddDiningRecipeRequest
import com.smartwash.feature.dining.api.model.CreateDiningOrderRequest
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DietaryPreferenceVo
import com.smartwash.feature.dining.api.model.DiningOrderCreatedVo
import com.smartwash.feature.dining.api.model.DiningOrderBriefVo
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderFinishVo
import com.smartwash.feature.dining.api.model.DiningOrderListVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.JoinDiningOrderRequest
import com.smartwash.feature.dining.api.model.SubmitDiningOrderRequest
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
 * 订单接口消费面（07 号票发起链路）：创建 / 我的订单（分页）/ 详情（首次全量拉取）；
 * 选菜增改删（09 号票点单页本地勾选最小接入，version 冲突重放为 10 号票）；
 * brief / join / 重置凭证为扫码加入链路后续票消费。
 */
interface DiningServiceApi {

    /** 全量菜谱分类（sort 升序，供菜谱库分类筛选条） */
    @GET("/web/auth/dining/categories/all")
    @RequireAuthorization
    suspend fun categories(): ApiResult<List<DiningCategoryVo>>

    /**
     * 上架菜谱分页（categoryId 精确 / keyword 菜名模糊 / ingredient 配料名模糊反查，null=不过滤，
     * 多参数 AND；仅返回上架，新菜谱在前；列表按当前用户批量填充 favorited）。
     */
    @GET("/web/auth/dining/recipes/all")
    @RequireAuthorization
    suspend fun recipes(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("categoryId") categoryId: Long? = null,
        @Query("keyword") keyword: String? = null,
        @Query("ingredient") ingredient: String? = null,
    ): ApiResult<PageData<DiningRecipeVo>>

    /** 我的收藏分页（按收藏时间倒序，字段同上架列表，favorited 恒 true） */
    @GET("/web/auth/dining/recipes/favorites")
    @RequireAuthorization
    suspend fun favoriteRecipes(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<DiningRecipeVo>>

    /** 收藏菜谱（幂等：已收藏返回当前态 true；菜谱不存在业务失败） */
    @POST("/web/auth/dining/recipes/{id}/favorite")
    @RequireAuthorization
    suspend fun favoriteRecipe(@Path("id") id: Long): ApiResult<Boolean>

    /** 取消收藏（幂等：未收藏时为无操作，返回当前态 false） */
    @DELETE("/web/auth/dining/recipes/{id}/favorite")
    @RequireAuthorization
    suspend fun unfavoriteRecipe(@Path("id") id: Long): ApiResult<Boolean>

    /**
     * 菜谱详情（仅上架可见，下架/不存在返回同一业务失败）；
     * 带 allergens（该菜全部配料的过敏原去重集合）与 favorited（当前用户是否收藏）。
     */
    @GET("/web/auth/dining/recipes/{id}")
    @RequireAuthorization
    suspend fun recipeDetail(@Path("id") id: Long): ApiResult<DiningRecipeVo>

    /** 获取饮食偏好（忌口 + 每日目标；字段 null=默认值语义，端上兜底 2000/2000） */
    @GET("/web/auth/dining/dietary-preference")
    @RequireAuthorization
    suspend fun dietaryPreference(): ApiResult<DietaryPreferenceVo>

    /** 保存饮食偏好（整行 upsert；任一字段 null=恢复默认，allergens null=清空忌口） */
    @PUT("/web/auth/dining/dietary-preference")
    @RequireAuthorization
    suspend fun saveDietaryPreference(@Body request: DietaryPreferenceVo): ApiResult<String>

    /** 新增菜谱（source=USER 直上架，无审核流；categoryId 传 0/null=未分类） */
    @POST("/web/auth/dining/recipes/add")
    @RequireAuthorization
    suspend fun addRecipe(@retrofit2.http.Body request: AddDiningRecipeRequest): ApiResult<String>

    /** 上传菜谱封面（multipart 图片，后端校验 JPG/PNG/WebP + 魔数 + ≤5MB，返回 dining/covers/ 对象键） */
    @Multipart
    @POST("/web/auth/dining/recipes/cover")
    @RequireAuthorization
    suspend fun uploadCover(@Part file: MultipartBody.Part): ApiResult<String>

    /**
     * 创建点单草稿（title/recipeIds 均可空，或 fromOrderId 复用历史菜单；返回 orderId + joinToken）。
     * 草稿协同点单：本接口创建 DRAFT（标题/菜单可空），草稿期即可邀请；publish 定稿后才 ONGOING。
     */
    @POST("/web/auth/dining/orders/create")
    @RequireAuthorization
    suspend fun createOrder(@retrofit2.http.Body request: CreateDiningOrderRequest): ApiResult<DiningOrderCreatedVo>

    /**
     * 提交点单（DRAFT→FINISHED；仅发起者；标题必填 + 菜单非空；version 乐观锁，冲突返回业务码 409）。
     * 提交即结束：聚合本单菜单生成购买清单快照写库，status→FINISHED 并通知全体成员。
     * [request] 携带草稿页编辑后的标题与客户端已知 version；返回购买清单快照（复用 Finish VO）。
     */
    @POST("/web/auth/dining/orders/{id}/submit")
    @RequireAuthorization
    suspend fun submitOrder(
        @Path("id") id: Long,
        @Body request: SubmitDiningOrderRequest,
    ): ApiResult<DiningOrderFinishVo>

    /**
     * 草稿加菜（recipeId；仅 DRAFT 态、全部成员可加；幂等；version 乐观锁，冲突返回 409）。
     */
    @POST("/web/auth/dining/orders/{id}/dishes")
    @RequireAuthorization
    suspend fun addOrderDish(
        @Path("id") id: Long,
        @Body request: AddDiningOrderDishRequest,
    ): ApiResult<Void>

    /**
     * 草稿移除菜（仅 DRAFT 态、全部成员可移除任意菜；幂等；version 乐观锁，冲突返回 409）。
     * [version] 为客户端已知订单版本（query 参数，与后端 @RequestParam 契约一致）。
     */
    @DELETE("/web/auth/dining/orders/{id}/dishes/{recipeId}")
    @RequireAuthorization
    suspend fun removeOrderDish(
        @Path("id") id: Long,
        @Path("recipeId") recipeId: Long,
        @Query("version") version: Long,
    ): ApiResult<Void>

    /**
     * 放弃草稿（cancel，仅发起者 + 仅 DRAFT）：订单状态置 CANCELLED 并写 cancelled_at，
     * 不再物理删除——参与者仍可在「我的订单」看到该单（灰态「已取消」）；已提交/已取消拒绝。
     */
    @DELETE("/web/auth/dining/orders/{id}")
    @RequireAuthorization
    suspend fun cancelOrder(@Path("id") id: Long): ApiResult<Void>

    /** 退出点单（仅参与者非发起者 + 仅 DRAFT + 仅成员；幂等：不在单中直接成功） */
    @POST("/web/auth/dining/orders/{id}/leave")
    @RequireAuthorization
    suspend fun leaveOrder(@Path("id") id: Long): ApiResult<Void>

    /** 我的进行中点单（本人发起的 DRAFT，无则 data=null）：列表 FAB / lite 主 CTA 收敛依据 */
    @GET("/web/auth/dining/orders/active")
    @RequireAuthorization
    suspend fun getActiveOrder(): ApiResult<DiningOrderActiveVo>

    /** 我的订单分页（我发起的 + 我参与的，按创建时间倒序，带 isCreator 标记；page 从 1 起） */
    @GET("/web/auth/dining/orders/my")
    @RequireAuthorization
    suspend fun myOrders(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<DiningOrderListVo>>

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

    /** 加入订单（校验 token 匹配 + 订单 DRAFT + 未重复加入；三类失败均有明确业务提示） */
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
}
