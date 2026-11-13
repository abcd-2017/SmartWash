package com.smartwash.feature.dining.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.dining.api.DiningServiceApi
import com.smartwash.feature.dining.api.model.AddDiningOrderItemRequest
import com.smartwash.feature.dining.api.model.AddDiningRecipeRequest
import com.smartwash.feature.dining.api.model.CreateDiningOrderRequest
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DiningOrderBriefVo
import com.smartwash.feature.dining.api.model.DiningOrderCreatedVo
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderFinishVo
import com.smartwash.feature.dining.api.model.DiningOrderListVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.JoinDiningOrderRequest
import com.smartwash.feature.dining.api.model.UpdateDiningOrderItemRequest
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

    /** 上架菜谱分页（page 从 1 起，size 上限 100，见后端 BaseSearchFrom） */
    suspend fun getRecipes(
        page: Int,
        size: Int,
        categoryId: Long? = null,
        keyword: String? = null,
    ): PageData<DiningRecipeVo> =
        diningServiceApi.recipes(page, size, categoryId, keyword).data
            ?: run {
                AppLogger.warn(TAG, "getRecipes 响应 data 为空，按失败处理上抛（page=$page, categoryId=$categoryId）")
                throw NetworkException("菜谱数据为空", R.string.error_network_fail)
            }

    /** 菜谱详情（仅上架可见；下架/不存在由后端业务失败转译上抛） */
    suspend fun getRecipeDetail(id: Long): DiningRecipeVo =
        diningServiceApi.recipeDetail(id).data
            ?: run {
                AppLogger.warn(TAG, "getRecipeDetail 响应 data 为空，按失败处理上抛（id=$id）")
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

    /** 创建点单订单（返回 orderId + joinToken 供拼二维码；token 是端上唯一凭证来源，data 缺失按失败上抛） */
    suspend fun createOrder(request: CreateDiningOrderRequest): DiningOrderCreatedVo =
        diningServiceApi.createOrder(request).data
            ?: run {
                AppLogger.warn(TAG, "createOrder 响应 data 为空，按失败上抛")
                throw NetworkException("创建订单结果为空", R.string.error_network_fail)
            }

    /** 我的订单列表（我发起的 + 我参与的，创建时间倒序；data 缺失按失败上抛） */
    suspend fun getMyOrders(): List<DiningOrderListVo> =
        diningServiceApi.myOrders().data
            ?: run {
                AppLogger.warn(TAG, "getMyOrders 响应 data 为空，按失败上抛")
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
     * 结束点单（仅发起者且仅 ONGOING；重复调用幂等返回库内原快照）。
     * 非发起者/状态异常由后端业务失败转译上抛；data 缺失按失败处理。
     */
    suspend fun finishOrder(orderId: Long): DiningOrderFinishVo =
        diningServiceApi.finishOrder(orderId).data
            ?: run {
                AppLogger.warn(TAG, "finishOrder 响应 data 为空，按失败上抛（orderId=$orderId）")
                throw NetworkException("结束点单结果为空", R.string.error_network_fail)
            }

    /**
     * 勾选菜品（共享购物车；version 为客户端已知版本）。
     * 信封 409（版本冲突）转译为 [DiningVersionConflictException]，其余非 200 转译为
     * NetworkException；data 恒为 null，成功即返回。
     */
    suspend fun addOrderItem(orderId: Long, request: AddDiningOrderItemRequest) {
        diningServiceApi.addOrderItem(orderId, request).ensureDiningSuccess()
    }

    /** 修改选菜备注（note 传 null 即清空；version 冲突语义同 [addOrderItem]） */
    suspend fun updateOrderItemNote(orderId: Long, request: UpdateDiningOrderItemRequest) {
        diningServiceApi.updateOrderItemNote(orderId, request).ensureDiningSuccess()
    }

    /** 取消选菜（version 冲突语义同 [addOrderItem]） */
    suspend fun removeOrderItem(orderId: Long, recipeId: Long, version: Long) {
        diningServiceApi.removeOrderItem(orderId, recipeId, version).ensureDiningSuccess()
    }

    /**
     * 选菜写接口信封校验：200 放行；409 抛 [DiningVersionConflictException]（调用方拉取最新后重放）；
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
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningRepository"
    }
}
