package com.smartwash.feature.subscription.api.network

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
 * 订阅逃生舱接口 —— 契约见根目录 docs/subscription-api.md（后端已交付，commit 5bdd62b）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；全部挂 web/auth/subscription 路径前缀（ROLE_USER），鉴权/错误转译拦截器自动生效。
 *
 * 状态机（§一）：active ↔ paused（pause/resume），→ canceled/finished 终态只读；
 * 业务失败统一 code=500 由 ResponseInterceptor 转译为 NetworkException（后端文案透出）。
 */
interface SubscriptionApi {

    /** 创建订阅（初始 active） */
    @POST("web/auth/subscription")
    @RequireAuthorization
    suspend fun createSubscription(@Body body: SaveSubscriptionRequest): ApiResult<SubscriptionVo>

    /** 编辑（全字段可选，null = 不改；终态拒绝） */
    @PUT("web/auth/subscription/{id}")
    @RequireAuthorization
    suspend fun updateSubscription(
        @Path("id") id: Long,
        @Body body: UpdateSubscriptionRequest,
    ): ApiResult<SubscriptionVo>

    /** 物理删除（级联清打卡流水 + 提醒；成功以 code=200 为准） */
    @DELETE("web/auth/subscription/{id}")
    @RequireAuthorization
    suspend fun deleteSubscription(@Path("id") id: Long): ApiResult<Unit>

    /** 分页列表（按 nextRenewDate 升序；status/keyword 可选筛） */
    @GET("web/auth/subscription")
    @RequireAuthorization
    suspend fun pageSubscriptions(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("status") status: String? = null,
        @Query("keyword") keyword: String? = null,
    ): ApiResult<PageData<SubscriptionVo>>

    /** 详情（含 daysUntilRenew/trialDaysLeft/unreadReminderCount 派生字段） */
    @GET("web/auth/subscription/{id}")
    @RequireAuthorization
    suspend fun getSubscription(@Path("id") id: Long): ApiResult<SubscriptionVo>

    /** 暂停（active → paused；非法流转后端拒绝） */
    @POST("web/auth/subscription/{id}/pause")
    @RequireAuthorization
    suspend fun pauseSubscription(@Path("id") id: Long): ApiResult<Unit>

    /** 恢复（paused → active） */
    @POST("web/auth/subscription/{id}/resume")
    @RequireAuthorization
    suspend fun resumeSubscription(@Path("id") id: Long): ApiResult<Unit>

    /** 取消（active/paused → canceled 终态，保留数据） */
    @POST("web/auth/subscription/{id}/cancel")
    @RequireAuthorization
    suspend fun cancelSubscription(@Path("id") id: Long): ApiResult<Unit>

    /** 结束（active/paused → finished 终态，语义为试用结束不再续用） */
    @POST("web/auth/subscription/{id}/finish")
    @RequireAuthorization
    suspend fun finishSubscription(@Path("id") id: Long): ApiResult<Unit>

    /** 使用打卡（usageDate 不传默认今天，禁未来；每订阅每天一条，重复拒绝） */
    @POST("web/auth/subscription/{id}/usages")
    @RequireAuthorization
    suspend fun checkInUsage(@Path("id") id: Long, @Body body: AddUsageRequest): ApiResult<Unit>

    /** 打卡分页（按使用日期倒序） */
    @GET("web/auth/subscription/{id}/usages")
    @RequireAuthorization
    suspend fun pageUsages(
        @Path("id") id: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<UsageLogVo>>

    /** 撤销打卡（物理删除） */
    @DELETE("web/auth/subscription/{id}/usages/{usageId}")
    @RequireAuthorization
    suspend fun deleteUsage(@Path("id") id: Long, @Path("usageId") usageId: Long): ApiResult<Unit>

    /** 提醒列表（unread=true 只看未读；remindDate 倒序，上限 200 条） */
    @GET("web/auth/subscription/reminders")
    @RequireAuthorization
    suspend fun listReminders(@Query("unread") unread: Boolean? = null): ApiResult<List<ReminderVo>>

    /** 标记已读（幂等，重复标记成功） */
    @POST("web/auth/subscription/reminders/{id}/read")
    @RequireAuthorization
    suspend fun markReminderRead(@Path("id") reminderId: Long): ApiResult<Unit>

    /** 统计（月度应收总额 + 各订阅近 90 天使用次数/单次折算成本） */
    @GET("web/auth/subscription/stats")
    @RequireAuthorization
    suspend fun getStats(): ApiResult<SubscriptionStatsVo>
}

// ==================== 请求体 ====================

/**
 * 创建请求。cycleType ∈ month|year（原值直传）；autoRenew 0|1；
 * 可选字段 null = 不设置（trialEndDate/cancelUrl/cancelSteps/note）。
 */
@Keep
data class SaveSubscriptionRequest(
    val name: String,
    val amount: Double,
    val cycleType: String,
    val cycleInterval: Int,
    val nextRenewDate: String,
    val trialEndDate: String? = null,
    val autoRenew: Int,
    val remindDaysBefore: Int,
    val cancelUrl: String? = null,
    val cancelSteps: String? = null,
    val note: String? = null,
)

/**
 * 编辑请求（全字段可选，null = 不改）。客户端始终提交完整表单快照；
 * 可选项被用户清空时只能传 null（= 不改），见方案偏离点说明。
 */
@Keep
data class UpdateSubscriptionRequest(
    val name: String? = null,
    val amount: Double? = null,
    val cycleType: String? = null,
    val cycleInterval: Int? = null,
    val nextRenewDate: String? = null,
    val trialEndDate: String? = null,
    val autoRenew: Int? = null,
    val remindDaysBefore: Int? = null,
    val cancelUrl: String? = null,
    val cancelSteps: String? = null,
    val note: String? = null,
)

/** 打卡请求（body 契约可空：usageDate null = 默认今天；Gson 默认省略 null 字段，序列化为 {}） */
@Keep
data class AddUsageRequest(
    val usageDate: String? = null,
)

// ==================== 响应 VO ====================

/**
 * 订阅 VO（列表项/详情）。原值字段（status/cycleType/autoRenew）由 UI 层经
 * SubscriptionStatus/SubscriptionCycleType 枚举转换，未知值防御回落。
 */
@Keep
data class SubscriptionVo(
    val subscriptionId: Long = 0L,
    val userId: Long = 0L,
    val name: String = "",
    val amount: Double = 0.0,
    val cycleType: String = "month",
    val cycleInterval: Int = 1,
    val nextRenewDate: String? = null,
    /** 首次锚点日（保留字段，v1 不入表单） */
    val firstAnchorDate: String? = null,
    val trialEndDate: String? = null,
    val autoRenew: Int = 1,
    val status: String = "active",
    val cancelUrl: String? = null,
    val cancelSteps: String? = null,
    val remindDaysBefore: Int = 0,
    val note: String? = null,
    /** 距下次续费天数（负数 = 已过期待推进；后端派生） */
    val daysUntilRenew: Int? = null,
    /** 试用剩余天数（无试用为 null；后端派生） */
    val trialDaysLeft: Int? = null,
    /** 未读提醒数（后端派生） */
    val unreadReminderCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** 使用打卡流水 VO（每订阅每天一条） */
@Keep
data class UsageLogVo(
    val usageId: Long = 0L,
    val subscriptionId: Long = 0L,
    val usageDate: String? = null,
    val note: String? = null,
    val createdAt: String? = null,
)

/** 提醒 VO（remindType ∈ renew|trial_end；isRead 0|1） */
@Keep
data class ReminderVo(
    val reminderId: Long = 0L,
    val subscriptionId: Long = 0L,
    val subscriptionName: String = "",
    val remindType: String = "renew",
    /** 被提醒的目标日期（非扫描日），幂等键之一 */
    val remindDate: String? = null,
    val isRead: Int = 0,
    val createdAt: String? = null,
)

/** 统计 VO（口径见契约 §三：monthlyNormalized HALF_UP 2 位；perUseCost 无使用为 null） */
@Keep
data class SubscriptionStatsVo(
    val monthlyPayable: Double = 0.0,
    val activeCount: Int = 0,
    val items: List<SubscriptionStatsItemVo> = emptyList(),
)

@Keep
data class SubscriptionStatsItemVo(
    val subscriptionId: Long = 0L,
    val name: String = "",
    val cycleType: String = "month",
    val cycleInterval: Int = 1,
    val amount: Double = 0.0,
    val monthlyNormalized: Double = 0.0,
    val usageCount90d: Int = 0,
    val perUseCost: Double? = null,
)
