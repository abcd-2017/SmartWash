package com.smartwash.divination.network

import androidx.annotation.Keep
import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.ApiResult
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 观象台解读接口 —— 本阶段只定义不实连：
 * LLM 解读走后端网关（SmartWash/docs/占卜模块后端与数据库架构设计.md），
 * 当前 UI 由 DivLlmRepository 返回内置 Mock 文本；后端就绪后切到本接口实现。
 * endpoint 经 buildConfigField DIVINATION_BASE_URL 注入，禁止硬编码。
 */
interface DivinationApi {

    @POST("web/auth/divination/interpret")
    @RequireAuthorization
    suspend fun interpret(@Body body: DivInterpretRequest): ApiResult<DivReadingVo>

    @POST("web/auth/divination/followup")
    @RequireAuthorization
    suspend fun followUp(@Body body: DivFollowUpRequest): ApiResult<DivFollowUpVo>
}

@Keep
data class DivInterpretRequest(
    val recordId: Long,
    val method: String,
    val category: String,
    val question: String,
    val castAt: Long,
    val chartJson: String,
    val stream: Boolean = false,
)

@Keep
data class DivFollowUpRequest(
    val recordId: Long,
    val question: String,
    val history: List<DivFollowUpTurn>,
)

@Keep
data class DivFollowUpTurn(
    val role: String,   // user / assistant
    val content: String,
)

@Keep
data class DivReadingVo(
    val summary: String,
    val sections: List<DivReadingSection>,
    val action: String,
    val disclaimer: String,
)

@Keep
data class DivReadingSection(
    val title: String,
    val content: String,
)

@Keep
data class DivFollowUpVo(
    val reply: String,
)
