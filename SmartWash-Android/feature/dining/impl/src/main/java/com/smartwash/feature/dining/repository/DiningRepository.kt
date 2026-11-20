package com.smartwash.feature.dining.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.dining.api.DiningServiceApi
import com.smartwash.feature.dining.api.model.DiningOrderActiveVo
import com.smartwash.feature.dining.api.model.AddDiningOrderDishRequest
import com.smartwash.feature.dining.api.model.AddDiningRecipeRequest
import com.smartwash.feature.dining.api.model.CreateDiningOrderRequest
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DietaryPreferenceVo
import com.smartwash.feature.dining.api.model.DiningOrderBriefVo
import com.smartwash.feature.dining.api.model.DiningOrderCreatedVo
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderFinishVo
import com.smartwash.feature.dining.api.model.DiningOrderListVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.JoinDiningOrderRequest
import com.smartwash.feature.dining.api.model.SubmitDiningOrderRequest
import com.smartwash.feature.dining.logic.DiningOrderBusinessCode
import com.smartwash.feature.dining.logic.DiningVersionConflictException
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 吃啥菜谱仓库：菜谱网络链路的唯一出口（ViewModel 不直接持有 API，MVVM 硬规则）。
 *
 * data 为 null 统一按失败处理上抛 [NetworkException]（对齐 coupon/points/notice 仓库模式）——
 * code=200 信封但 data 缺失是异常响应，让上层能区分「无数据」与「请求失败」。
 * 业务失败（如分类不存在、封面校验不通过）由 ResponseInterceptor 转译为 NetworkException 携带
 * 后端 message 上抛，页面按具体文案提示。
 */
@Singleton
class DiningRepository @Inject constructor(
    private val diningServiceApi: DiningServiceApi,
) {

    /** 全量分类目录（sort 升序；data 缺失按失败上抛，分类条是筛选主流程不可静默降级） */
    suspend fun getCategories(): List<DiningCategoryVo> =
        diningServiceApi.categories().data
            ?: run {
                AppLogger.warn(TAG, "getCategories 响应 data 为空，按失败处理上抛")
                throw NetworkException("分类数据为空", R.string.error_network_fail)
            }

    /** 上架菜谱分页（page 从 1 起，size 上限 100；keyword/ingredient 互斥语义由调用方决定，可 AND） */
    suspend fun getRecipes(
        page: Int,
        size: Int,
        categoryId: Long? = null,
        keyword: String? = null,
        ingredient: String? = null,
    ): PageData<DiningRecipeVo> =
        diningServiceApi.recipes(page, size, categoryId, keyword, ingredient).data
            ?: run {
                AppLogger.warn(TAG, "getRecipes 响应 data 为空，按失败上抛（page=$page, categoryId=$categoryId）")
                throw NetworkException("菜谱数据为空", R.string.error_network_fail)
            }

    /** 我的收藏分页（按收藏时间倒序；data 缺失按失败上抛） */
    suspend fun getFavoriteRecipes(page: Int, size: Int): PageData<DiningRecipeVo> =
        diningServiceApi.favoriteRecipes(page, size).data
            ?: run {
                AppLogger.warn(TAG, "getFavoriteRecipes 响应 data 为空，按失败上抛（page=$page）")
                throw NetworkException("收藏列表为空", R.string.error_network_fail)
            }

    /** 收藏/取消收藏（幂等，返回操作后的当前态；data 缺失按失败上抛） */
    suspend fun favoriteRecipe(id: Long): Boolean =
        diningServiceApi.favoriteRecipe(id).data
            ?: run {
                AppLogger.warn(TAG, "favoriteRecipe 响应 data 为空，按失败上抛（id=$id）")
                throw NetworkException("收藏结果为空", R.string.error_network_fail)
            }

    /** 取消收藏（幂等，返回操作后的当前态；data 缺失按失败上抛） */
    suspend fun unfavoriteRecipe(id: Long): Boolean =
        diningServiceApi.unfavoriteRecipe(id).data
            ?: run {
                AppLogger.warn(TAG, "unfavoriteRecipe 响应 data 为空，按失败上抛（id=$id）")
                throw NetworkException("取消收藏结果为空", R.string.error_network_fail)
            }

    /** 获取饮食偏好（字段 null=默认值语义；data 缺失按失败上抛） */
    suspend fun getDietaryPreference(): DietaryPreferenceVo =
        diningServiceApi.dietaryPreference().data
            ?: run {
                AppLogger.warn(TAG, "getDietaryPreference 响应 data 为空，按失败上抛")
                throw NetworkException("饮食偏好为空", R.string.error_network_fail)
            }

    /** 保存饮食偏好（任一字段 null=恢复默认；成功 data 为提示文案，缺失不视为失败） */
    suspend fun saveDietaryPreference(preference: DietaryPreferenceVo) {
        diningServiceApi.saveDietaryPreference(preference)
    }

    /** 菜谱详情（仅上架可见；下架/不存在由后端业务失败转译上抛） */
    suspend fun getRecipeDetail(id: Long): DiningRecipeVo =
        diningServiceApi.recipeDetail(id).data
            ?: run {
                AppLogger.warn(TAG, "getRecipeDetail 响应 data 为空，按失败上抛（id=$id）")
                throw NetworkException("菜谱详情为空", R.string.error_network_fail)
            }

    /** 新增菜谱（source=USER 直上架；成功返回后端提示文案，data 缺失不视为失败） */
    suspend fun addRecipe(request: AddDiningRecipeRequest) {
        diningServiceApi.addRecipe(request)
    }

    /** 上传封面（后端校验 JPG/PNG/WebP + 魔数 + ≤5MB，返回 dining/covers/ 对象键；data 缺失按失败上抛） */
    suspend fun uploadCover(file: MultipartBody.Part): String =
        diningServiceApi.uploadCover(file).data
            ?: run {
                AppLogger.warn(TAG, "uploadCover 响应 data 为空，按失败上抛")
                throw NetworkException("封面上传结果为空", R.string.error_network_fail)
            }

    /**
     * 创建点单草稿（返回 orderId + joinToken；草稿期即可邀请，data 缺失按失败上抛）。
     * title/recipeIds 均可空——端上惰性创建，标题/菜单在草稿期随时补充。
     */
    suspend fun createOrder(request: CreateDiningOrderRequest): DiningOrderCreatedVo =
        diningServiceApi.createOrder(request).data
            ?: run {
                AppLogger.warn(TAG, "createOrder 响应 data 为空，按失败上抛")
                throw NetworkException("创建订单结果为空", R.string.error_network_fail)
            }

    /**
     * 提交点单（DRAFT→FINISHED；仅发起者；标题必填 + 菜单非空；version 乐观锁）。
     * 提交即结束：后端聚合本单菜单生成购买清单快照并通知全体成员。
     * 信封 409 转译为 [DiningVersionConflictException]（端上刷新后重试），其余非 200 转译为
     * NetworkException；data 缺失按失败上抛。
     */
    suspend fun submitOrder(orderId: Long, request: SubmitDiningOrderRequest): DiningOrderFinishVo =
        diningServiceApi.submitOrder(orderId, request).data
            ?: run {
                AppLogger.warn(TAG, "submitOrder 响应 data 为空，按失败上抛（orderId=$orderId）")
                throw NetworkException("提交结果为空", R.string.error_network_fail)
            }

    /**
     * 草稿加菜（仅 DRAFT 态、全部成员；幂等；version 乐观锁）。
     * 409 转译为 [DiningVersionConflictException]，其余非 200 转译为 NetworkException。
     */
    suspend fun addOrderDish(orderId: Long, request: AddDiningOrderDishRequest) {
        diningServiceApi.addOrderDish(orderId, request).ensureDiningSuccess()
    }

    /**
     * 草稿移除菜（仅 DRAFT 态、全部成员可移除任意菜；幂等；version 乐观锁）。
     * 409 转译为 [DiningVersionConflictException]，其余非 200 转译为 NetworkException。
     */
    suspend fun removeOrderDish(orderId: Long, recipeId: Long, version: Long) {
        diningServiceApi.removeOrderDish(orderId, recipeId, version).ensureDiningSuccess()
    }

    /**
     * 放弃草稿（cancel，仅发起者 + 仅 DRAFT）：订单状态置 CANCELLED，不再物理删单——
     * 参与者仍可在「我的订单」看到该单（灰态「已取消」）。成功 data 为 null 不视为失败。
     */
    suspend fun cancelOrder(orderId: Long) {
        diningServiceApi.cancelOrder(orderId).ensureDiningSuccess()
    }

    /** 退出点单（仅参与者非发起者 + 仅 DRAFT；幂等：不在单中直接成功） */
    suspend fun leaveOrder(orderId: Long) {
        diningServiceApi.leaveOrder(orderId).ensureDiningSuccess()
    }

    /**
     * 我的进行中点单（本人发起的 DRAFT；无则 null——合法语义，不按失败上抛）。
     * 列表 FAB / lite 主 CTA 据此收敛：有则进该单编辑态，无则空白创建页。
     */
    suspend fun getActiveOrder(): DiningOrderActiveVo? = diningServiceApi.getActiveOrder().data

    /** 我的订单分页（我发起的 + 我参与的，创建时间倒序；data 缺失按失败上抛） */
    suspend fun getMyOrders(page: Int, size: Int): PageData<DiningOrderListVo> =
        diningServiceApi.myOrders(page, size).data
            ?: run {
                AppLogger.warn(TAG, "getMyOrders 响应 data 为空，按失败上抛（page=$page）")
                throw NetworkException("订单列表为空", R.string.error_network_fail)
            }

    /** 订单详情（轮询主接口；knownVersion 与服务端一致返回轻量响应，null=首次全量拉取） */
    suspend fun getOrderDetail(orderId: Long, knownVersion: Long? = null): DiningOrderDetailVo =
        diningServiceApi.orderDetail(orderId, knownVersion).data
            ?: run {
                AppLogger.warn(TAG, "getOrderDetail 响应 data 为空，按失败上抛（orderId=$orderId）")
                throw NetworkException("订单详情为空", R.string.error_network_fail)
            }

    /**
     * 加入前预览 brief（凭二维码 token 换订单标题/发起者/菜品数/是否已在单中）。
     * token 无效/订单不存在由后端业务失败转译上抛；data 缺失按失败处理。
     */
    suspend fun getOrderBrief(token: String): DiningOrderBriefVo =
        diningServiceApi.orderBrief(token).data
            ?: run {
                AppLogger.warn(TAG, "getOrderBrief 响应 data 为空，按失败上抛")
                throw NetworkException("订单信息为空", R.string.error_network_fail)
            }

    /** 加入订单（token/状态/重复加入三类失败由后端业务失败转译上抛；成功 data 为 null 不视为失败） */
    suspend fun joinOrder(orderId: Long, joinToken: String) {
        diningServiceApi.joinOrder(orderId, JoinDiningOrderRequest(joinToken))
    }

    /** 重置加入凭证（仅发起者；旧 token 全部作废，返回新 token；data 缺失按失败上抛） */
    suspend fun resetJoinToken(orderId: Long): String =
        diningServiceApi.resetJoinToken(orderId).data
            ?: run {
                AppLogger.warn(TAG, "resetJoinToken 响应 data 为空，按失败上抛（orderId=$orderId）")
                throw NetworkException("重置凭证结果为空", R.string.error_network_fail)
            }

    /**
     * 菜单写接口信封校验：200 放行；409 抛 [DiningVersionConflictException]（调用方拉取最新后重放）；
     * 其余非 200 抛 NetworkException。201 通用失败已由 ResponseInterceptor 转译，正常不会到这里。
     */
    private fun ApiResult<*>.ensureDiningSuccess() {
        when (code) {
            HttpStatusCode.Success.code -> Unit
            DiningOrderBusinessCode.VERSION_CONFLICT ->
                throw DiningVersionConflictException(message)
            else -> throw NetworkException(message, R.string.error_network_fail)
        }
    }

    private companion object {
        const val TAG = "DiningRepository"
    }
}
