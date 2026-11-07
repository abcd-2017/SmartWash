package com.smartwash.feature.divination.network

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
 * 观象台用户端接口（issue #78 对齐后端 DivinationWebController，前缀 web/auth/div，JWT ROLE_USER）。
 *
 * 全部走 JSON 信封 {code,message,data} 主通道（Retrofit + :common:network 拦截器）：
 * 鉴权注入与 401 清会话跳登录由拦截器统一处理，本接口只声明端点。
 * 业务失败（code=201）时 ResponseInterceptor 抛 NetworkException，message 已按
 * 「data 字符串优先、message 兜底」解析为用户可读文案。
 *
 * 注意：interpret / followup 是 SSE 流式接口（event: delta/done/error），不走 Retrofit，
 * 由 :common:network 的 SseClient 封装承接（见 data/DivLlmRepository）——本接口不声明。
 */
interface DivinationApi {

    /** 创建卦例（服务端 core 重算权威盘面并与 clientChart 比对）→ 服务端 recordId */
    @POST("web/auth/div/records")
    @RequireAuthorization
    suspend fun createRecord(@Body body: DivCreateRecordRequest): ApiResult<Long>

    /** 卦历分页（method/dateFrom/dateTo 可空 + page/size，参照后端 SearchRecordFrom） */
    @GET("web/auth/div/records")
    @RequireAuthorization
    suspend fun searchRecords(
        @Query("method") method: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 10,
    ): ApiResult<PageData<DivRecordVo>>

    /** 卦例详情（含最近一次解读；失败信封「卦例不存在」→ NetworkException） */
    @GET("web/auth/div/records/{id}")
    @RequireAuthorization
    suspend fun getRecordDetail(@Path("id") id: Long): ApiResult<DivRecordDetailVo>

    /** 今日一签（服务端幂等：method=meihua & source=today，同日复用） */
    @GET("web/auth/div/today")
    @RequireAuthorization
    suspend fun getTodayRecord(): ApiResult<DivRecordDetailVo>

    /** 反馈与应验回填（对解读评分 1-5） */
    @POST("web/auth/div/records/{id}/feedback")
    @RequireAuthorization
    suspend fun feedback(@Path("id") id: Long, @Body body: DivFeedbackRequest): ApiResult<String>

    /** 四术元信息（code/name/description/disclaimer） */
    @GET("web/auth/div/methods")
    @RequireAuthorization
    suspend fun getMethods(): ApiResult<List<DivMethodVo>>

    /** 可选模型目录（平台启用模型，BYOK 输入建议） */
    @GET("web/auth/div/models")
    @RequireAuthorization
    suspend fun getModels(): ApiResult<List<DivModelVo>>
}

/** ---------------- 请求体 ---------------- */

/**
 * 创建卦例请求（后端 CreateRecordFrom）。
 *
 * lines 编码按后端引擎语义传：
 * - liuyao：三币和编码 6=老阴 7=少阳 8=少阴 9=老阳（端上 1/2/3/4 编码须转换，见 DivLlmRepository）；
 * - meihua：[上卦先天数, 下卦先天数, 动爻]（后端 lines.size>=3 走数字卦重算，与端上时间/报数卦同数同盘）；
 * - qimen/liuren：null（纯时间起局，服务端按 castAt+tzOffset 重算）。
 */
@Keep
data class DivCreateRecordRequest(
    val method: String,
    val category: String?,
    val question: String,
    val castMethod: String,
    val castAt: Long,
    val tzOffset: Int = 480,
    val lines: List<Int>? = null,
    val clientChart: String? = null,
)

/** 反馈 + 应验回填请求（后端 FeedbackFrom：rating 1-5，outcome 0未回填/1应验/2未验/3难说） */
@Keep
data class DivFeedbackRequest(
    val interpretationId: Long,
    val rating: Int? = null,
    val outcome: Int? = null,
    val outcomeNote: String? = null,
)

/** ---------------- 返回 VO（字段对齐后端 vo/ 包；时间字段以字符串透传，端上不解析） ---------------- */

/** 卦例（分页元素，后端 RecordVo；castAt/createdAt 为 ISO LocalDateTime 字符串） */
@Keep
data class DivRecordVo(
    val id: Long,
    val method: String? = null,
    val category: String? = null,
    val question: String? = null,
    val castMethod: String? = null,
    val castAt: String? = null,
    val tzOffset: Int? = null,
    val source: String? = null,
    val chartVerified: Int? = null,
    val latestInterpretation: String? = null,
    val createdAt: String? = null,
)

/** 解读记录（后端 InterpretationVo；contentMd 为 Markdown 全文） */
@Keep
data class DivInterpretationVo(
    val id: Long,
    val recordId: Long? = null,
    val kind: String? = null,
    val question: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val keySource: String? = null,
    val contentMd: String? = null,
    val tokensIn: Int? = null,
    val tokensOut: Int? = null,
    val latencyMs: Int? = null,
    val cacheHit: Int? = null,
    val auditStatus: Int? = null,
    val createdAt: String? = null,
)

/** 卦例详情（后端 RecordDetailVo：含 lines/clientChart/serverChart 与最近一次解读） */
@Keep
data class DivRecordDetailVo(
    val id: Long,
    val method: String? = null,
    val category: String? = null,
    val question: String? = null,
    val castMethod: String? = null,
    val castAt: String? = null,
    val tzOffset: Int? = null,
    val source: String? = null,
    val lines: String? = null,
    val clientChart: String? = null,
    val serverChart: String? = null,
    val chartVerified: Int? = null,
    val latestInterpretation: DivInterpretationVo? = null,
    val createdAt: String? = null,
)

/** 术数元信息（后端 MethodVo） */
@Keep
data class DivMethodVo(
    val code: String,
    val name: String? = null,
    val description: String? = null,
    val disclaimer: String? = null,
)

/** 平台模型目录项（后端 ModelVo） */
@Keep
data class DivModelVo(
    val id: Long,
    val name: String? = null,
    val provider: String? = null,
    val modelId: String? = null,
    val priority: Int? = null,
    val active: Boolean? = null,
)
