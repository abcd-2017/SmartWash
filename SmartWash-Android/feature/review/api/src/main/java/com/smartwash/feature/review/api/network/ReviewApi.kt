package com.smartwash.feature.review.api.network

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 记忆复习接口 —— 契约见 docs/review-api.md（后端 ReviewWebController，前缀 /web/auth/review）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；鉴权/错误转译拦截器经 NetworkModule 自动生效。
 *
 * 调度约定：FSRS-6 全部在后端 FsrsScheduler 纯函数内重算，客户端只传 (cardId, rating 1..4)，
 * 不参与任何调度计算；服务端以自身时钟为复习时刻。
 */
interface ReviewApi {

    /** 创建资料：title 必填（≤100 字），rawText 可空（≤100k） */
    @POST("web/auth/review/materials")
    @RequireAuthorization
    suspend fun createMaterial(@Body body: AddMaterialRequest): ApiResult<Long>

    /** 资料分页（按创建时间倒序） */
    @GET("web/auth/review/materials")
    @RequireAuthorization
    suspend fun pageMaterials(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<MaterialVo>>

    /** 建卡（手动问答卡）：初始 state=Learning, step=0, due=now */
    @POST("web/auth/review/materials/{id}/cards")
    @RequireAuthorization
    suspend fun addCard(
        @Path("id") materialId: Long,
        @Body body: AddCardRequest,
    ): ApiResult<Long>

    /** 卡片分页（按资料筛选） */
    @GET("web/auth/review/cards")
    @RequireAuthorization
    suspend fun pageCards(
        @Query("materialId") materialId: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<ReviewCardVo>>

    /** 今日队列：到期卡在前（state,due 升序，上限 50）+ 新卡注入队尾（每次最多带出 20 张） */
    @GET("web/auth/review/queue")
    @RequireAuthorization
    suspend fun getQueue(): ApiResult<ReviewQueueVo>

    /** 复习评分：rating 1..4（1=Again 2=Hard 3=Good 4=Easy），durationMs 可空；返回更新后的卡片 */
    @POST("web/auth/review/answer")
    @RequireAuthorization
    suspend fun answer(@Body body: AnswerRequest): ApiResult<ReviewCardVo>

    /** 复习统计：近 30 天每日复习数（缺日补零）+ 各评分次数分布 */
    @GET("web/auth/review/stats")
    @RequireAuthorization
    suspend fun stats(): ApiResult<ReviewStatsVo>
}

@Keep
data class AddMaterialRequest(
    val title: String,
    /** 粘贴的原文，可空 */
    val rawText: String? = null,
)

@Keep
data class AddCardRequest(
    val question: String,
    val answer: String,
)

@Keep
data class AnswerRequest(
    val cardId: Long,
    /** 1=Again 2=Hard 3=Good 4=Easy */
    val rating: Int,
    /** 作答耗时毫秒，可空 */
    val durationMs: Int? = null,
)

@Keep
data class MaterialVo(
    val id: Long = 0L,
    val title: String = "",
    /** 原文（列表返回摘要） */
    val rawText: String? = null,
    /** ISO LocalDateTime（yyyy-MM-dd'T'HH:mm:ss） */
    val createdAt: String? = null,
)

@Keep
data class ReviewCardVo(
    val id: Long = 0L,
    val materialId: Long? = null,
    val question: String = "",
    val answer: String = "",
    /** 1=Learning 2=Review 3=Relearning（py-fsrs State） */
    val state: Int = 1,
    /** learning/relearning 步序号，Review 态为 null */
    val step: Int? = null,
    /** 稳定度 S（天），新卡为 null */
    val stability: Double? = null,
    /** 难度 D（1-10），新卡为 null */
    val difficulty: Double? = null,
    /** 下次到期时刻（ISO LocalDateTime） */
    val dueAt: String? = null,
    /** NULL=从未复习的新卡 */
    val lastReviewAt: String? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val createdAt: String? = null,
) {
    /** 从未复习的新卡（新卡注入队尾、显示「新卡」角标的唯一判据，对齐后端 last_review_at IS NULL） */
    val isNewCard: Boolean get() = lastReviewAt == null
}

@Keep
data class ReviewQueueVo(
    /** 到期卡张数 */
    val dueCount: Int = 0,
    /** 注入的新卡张数 */
    val newCount: Int = 0,
    /** 合并后的队列（到期卡 + 新卡） */
    val cards: List<ReviewCardVo> = emptyList(),
)

@Keep
data class ReviewStatsVo(
    /** 近 30 天每日复习数（缺日补零） */
    val daily: List<DailyCount> = emptyList(),
    /** 各评分次数分布（恒 4 项） */
    val ratingShare: List<RatingShare> = emptyList(),
) {
    @Keep
    data class DailyCount(
        /** 统计日（ISO LocalDate，yyyy-MM-dd） */
        val date: String? = null,
        /** 当日复习次数 */
        val count: Long = 0L,
    )

    @Keep
    data class RatingShare(
        /** 1=Again 2=Hard 3=Good 4=Easy */
        val rating: Int = 0,
        /** 该评分次数 */
        val count: Long = 0L,
    )
}
