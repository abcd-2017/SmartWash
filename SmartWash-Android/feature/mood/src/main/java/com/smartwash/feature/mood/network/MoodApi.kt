package com.smartwash.feature.mood.network

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * 情绪气象站接口 —— 契约见 worktree docs/mood-api.md（后端已交付，commit c87a5cb）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；鉴权/错误转译拦截器经 NetworkModule 自动生效。
 *
 * 业务语义：成功 code=200；参数非法等业务失败 code=201（HTTP 仍 200），
 * ResponseInterceptor 已统一转译为 NetworkException（后端 message 保留在 exception.message）。
 * 所有数据以当前登录用户为主体（服务端从 JWT 上下文取 user_id，端上无需传）。
 */
interface MoodApi {

    /**
     * 记录/覆盖今日情绪：upsert 语义（同一天重复提交即覆盖，服务端唯一索引原子裁决，
     * 可放心重复点击），返回保存后的当日记录。
     */
    @PUT("web/auth/mood/today")
    @RequireAuthorization
    suspend fun saveToday(@Body body: SaveMoodRequest): ApiResult<MoodRecordVo>

    /** 月历热图数据：ym 格式 yyyy-MM；只返回当月有记录的日期（升序），空月返回空数组 */
    @GET("web/auth/mood/calendar")
    @RequireAuthorization
    suspend fun monthCalendar(@Query("ym") ym: String): ApiResult<List<MoodCalendarDayVo>>

    /** 月度趋势统计：ym 格式 yyyy-MM；空月 recordedDays=0、averageLevel=null、distribution=[] */
    @GET("web/auth/mood/stats")
    @RequireAuthorization
    suspend fun monthStats(@Query("ym") ym: String): ApiResult<MoodStatsVo>
}

/** 请求体：color 为提交时刻的颜色快照（#RRGGBB），word/note 可选（空白按 null 存） */
@Keep
data class SaveMoodRequest(
    /** 1..5，越界后端返回 code=201 */
    val level: Int,
    val color: String,
    val word: String? = null,
    val note: String? = null,
)

/** 保存后的当日记录（PUT /today 响应） */
@Keep
data class MoodRecordVo(
    val date: String = "",
    val level: Int = 0,
    val color: String = "",
    val word: String? = null,
    val note: String? = null,
    val updatedAt: String? = null,
)

/** 月历单日记录（GET /calendar 响应项；注意契约不含 note，当日备注不回看） */
@Keep
data class MoodCalendarDayVo(
    /** yyyy-MM-dd */
    val date: String = "",
    val level: Int = 0,
    val color: String = "",
    val word: String? = null,
)

/** 月度趋势统计（GET /stats 响应） */
@Keep
data class MoodStatsVo(
    val ym: String = "",
    val recordedDays: Long = 0L,
    /** 等级均值保留 2 位；无记录时为 null */
    val averageLevel: Double? = null,
    /** 按等级升序，只含出现过的等级（缺的等级端上补 0） */
    val distribution: List<MoodLevelCountVo> = emptyList(),
)

/** 单个等级的月度计数（GROUP BY mood_level） */
@Keep
data class MoodLevelCountVo(
    val level: Int = 0,
    val recordCount: Long = 0L,
)
