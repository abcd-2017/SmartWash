package com.smartwash.feature.divination.data

import com.google.gson.Gson
import com.smartwash.common.network.sse.SseClient
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.divination.BuildConfig
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.core.DivCategory
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.database.DivRecordDao
import com.smartwash.feature.divination.database.DivRecordEntity
import com.smartwash.feature.divination.network.DivCreateRecordRequest
import com.smartwash.feature.divination.network.DivFeedbackRequest
import com.smartwash.feature.divination.network.DivinationApi
import kotlinx.coroutines.delay
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** 解读结果（UI 层模型）。真连四段映射 + Mock 文本共用；disclaimer 为服务端免责条（无则 null） */
data class DivReading(
    val summary: String,
    val sections: List<DivReadingSection>,
    val action: String,
    val disclaimer: String? = null,
)

data class DivReadingSection(
    val title: String,
    val content: String,
)

/** 追问单轮 */
data class DivFollowUpTurn(
    val role: String,   // "user" / "assistant"
    val content: String,
)

/** SSE 流事件（delta 增量 / done 完成 / error 失败），解读与追问共用 */
sealed class DivStreamEvent {

    /** 增量文本（累积上屏） */
    data class Delta(val text: String) : DivStreamEvent()

    /** 完成：interpretationId 落库供追问/反馈（Mock 无服务端 id，恒 0） */
    data class Done(val interpretationId: Long, val cached: Boolean) : DivStreamEvent()

    /** 失败：reason 决定兜底文案资源，message 取服务端文案（可能为 null，UI 侧 resId 兜底） */
    data class Failure(
        val reason: Reason,
        val message: String?,
        val messageResId: Int,
    ) : DivStreamEvent() {
        enum class Reason {
            /** 404 卦例不存在（含建档失败查无此卦） */
            RECORD_NOT_FOUND,

            /** 403 高风险问题分流拦截 */
            REFUSED,

            /** 429 当日解读限额 */
            RATE_LIMITED,

            /** 503 供应商降级 / 服务暂不可用 */
            SERVICE_UNAVAILABLE,

            /** 建档（POST /records）失败 */
            CREATE_RECORD_FAILED,

            /** 连接/读流网络异常 */
            NETWORK,

            /** HTTP 非 2xx 非 401 */
            HTTP_ERROR,

            /** 401：会话已清并已通知跳登录，UI 无需再展示错误 */
            UNAUTHORIZED,

            /** 其他服务端错误（error 帧未知码 / 500） */
            SERVER_ERROR,
        }
    }
}

/** 建档失败（业务信封失败或网络异常）——由 interpret/followUp 转为 Failure(CREATE_RECORD_FAILED) */
private class DivCreateRecordException(cause: String?) : Exception(cause)

/**
 * 观象台解读仓库（issue #78 真连实现）。
 *
 * 通道分工：
 * - JSON 信封接口（建档/今日一签/卦历/反馈/元信息）→ [DivinationApi]（Retrofit 主通道，401/业务
 *   失败由 ResponseInterceptor 统一转译）；
 * - interpret / followup SSE 流 → [SseClient]（event 名 delta/done/error，401 由 SseClient 复用
 *   ResponseInterceptor 同款语义：清 token + 通知跳登录）。
 *
 * Mock 开关：BuildConfig.DIV_LLM_MOCK_ENABLED（divination build.gradle buildConfigField，
 * 默认 false = 真连；后端不可用时翻 true 回退内置 Mock 文本，Mock 文本与真连前逐字一致）。
 *
 * 建档时机：求解读/追问时若本地记录无服务端 recordId（serverRecordId==0），先 POST /records
 * 建档并回写 Room，再发起 SSE（失败即 Failure(CREATE_RECORD_FAILED)，UI 呈明确错误态）。
 */
@Singleton
class DivLlmRepository @Inject constructor(
    private val api: DivinationApi,
    private val sseClient: SseClient,
    private val dao: DivRecordDao,
) {

    private val gson = Gson()

    /** 模拟解读延迟（让 loading 态可感知） */
    private val mockDelayMs = 600L

    /** interpret 请求体（后端 @RequestBody String：StringHttpMessageConverter 原样收文，须显式 utf-8） */
    private val sseContentType = "text/plain; charset=utf-8"

    /**
     * 解读（流式）：ensure 建档 → SSE delta 增量回调 → done 回写 interpretationId。
     * 首次解读问题用卦例自身问题（record.question；空则发空 body 由服务端回退卦例问题）。
     */
    suspend fun interpret(record: DivRecordEntity, onEvent: (DivStreamEvent) -> Unit) {
        if (BuildConfig.DIV_LLM_MOCK_ENABLED) {
            emitMock(record, onEvent)
            return
        }
        val serverId = ensureServerRecord(record, onEvent) ?: return
        val body = record.question.trim()
        stream("web/auth/div/records/$serverId/interpret", body, record.id, onEvent)
    }

    /**
     * 追问（流式）：同卦同通道；历史上下文由服务端按 div_interpretation 拼接，客户端只发当前问题。
     * @param history 本轮已发生的对话（仅 Mock 分支用于轮次文案；真连忽略，服务端拼历史）
     */
    suspend fun followUp(
        record: DivRecordEntity,
        question: String,
        history: List<DivFollowUpTurn>,
        onEvent: (DivStreamEvent) -> Unit,
    ) {
        if (BuildConfig.DIV_LLM_MOCK_ENABLED) {
            emitMockFollowUp(record, question, history, onEvent)
            return
        }
        val serverId = ensureServerRecord(record, onEvent) ?: return
        stream("web/auth/div/records/$serverId/followup", question.trim(), record.id, onEvent)
    }

    /** 反馈与应验回填（服务端 FeedbackFrom） */
    suspend fun feedback(
        serverRecordId: Long,
        interpretationId: Long,
        rating: Int?,
        outcome: Int?,
        outcomeNote: String?,
    ): Result<String> {
        return runCatching {
            val result = api.feedback(
                serverRecordId,
                DivFeedbackRequest(
                    interpretationId = interpretationId,
                    rating = rating,
                    outcome = outcome,
                    outcomeNote = outcomeNote,
                ),
            )
            if (result.code != 200) error(result.message)
            result.data.orEmpty()
        }
    }

    // ---- SSE 流（真连） ----

    private suspend fun stream(path: String, body: String, localId: Long, onEvent: (DivStreamEvent) -> Unit) {
        var doneReceived = false
        var failureSent = false
        val outcome = sseClient.post(path, body, sseContentType) { name, data ->
            when (name) {
                EVENT_DELTA -> {
                    val frame = runCatching { gson.fromJson(data, DeltaFrame::class.java) }.getOrNull()
                    if (frame?.text != null) onEvent(DivStreamEvent.Delta(frame.text))
                }
                EVENT_DONE -> {
                    val frame = runCatching { gson.fromJson(data, DoneFrame::class.java) }.getOrNull()
                    val interpretationId = frame?.interpretationId ?: 0L
                    if (interpretationId > 0) {
                        // done 落库供追问（服务端拼历史）与反馈
                        runCatching { dao.updateInterpretationId(localId, interpretationId) }
                            .onFailure { AppLogger.warn(TAG, "interpretationId 回写失败 localId=$localId: ${it.message}") }
                    }
                    doneReceived = true
                    onEvent(DivStreamEvent.Done(interpretationId, frame?.cached ?: false))
                }
                EVENT_ERROR -> {
                    val frame = runCatching { gson.fromJson(data, ErrorFrame::class.java) }.getOrNull()
                    val code = frame?.code ?: 500
                    // error 帧后服务端 emitter.complete() 是正常关闭——置位防终态重复补发，
                    // 避免 403/429/503 语义被终态兜底文案覆盖
                    failureSent = true
                    onEvent(DivStreamEvent.Failure(reasonOf(code), frame?.message, resOf(code)))
                }
            }
        }
        // 流终态兜底：正常 complete 若未收到 done 帧按网络失败处理；401 已由 SseClient 通知跳登录
        when (outcome) {
            is SseClient.Outcome.Completed -> if (!doneReceived && !failureSent) {
                AppLogger.warn(TAG, "SSE 流关闭但未收到 done 帧 path=$path")
                onEvent(
                    DivStreamEvent.Failure(
                        DivStreamEvent.Failure.Reason.NETWORK,
                        null,
                        R.string.div_error_network,
                    ),
                )
            }
            is SseClient.Outcome.Unauthorized -> if (!failureSent) {
                onEvent(DivStreamEvent.Failure(DivStreamEvent.Failure.Reason.UNAUTHORIZED, null, R.string.div_error_unauthorized))
            }
            is SseClient.Outcome.HttpError -> if (!failureSent) {
                onEvent(
                    DivStreamEvent.Failure(
                        reasonOf(outcome.code),
                        null,
                        resOf(outcome.code),
                    ),
                )
            }
            is SseClient.Outcome.NetworkError -> if (!failureSent) {
                onEvent(
                    DivStreamEvent.Failure(
                        DivStreamEvent.Failure.Reason.NETWORK,
                        outcome.message,
                        R.string.div_error_network,
                    ),
                )
            }
        }
    }

    /** reason 映射（与后端 SseBridge/LlmErrorMapping 的错误码契约对齐） */
    private fun reasonOf(code: Int): DivStreamEvent.Failure.Reason = when (code) {
        404 -> DivStreamEvent.Failure.Reason.RECORD_NOT_FOUND
        403 -> DivStreamEvent.Failure.Reason.REFUSED
        429 -> DivStreamEvent.Failure.Reason.RATE_LIMITED
        503 -> DivStreamEvent.Failure.Reason.SERVICE_UNAVAILABLE
        else -> DivStreamEvent.Failure.Reason.SERVER_ERROR
    }

    /** 兜底文案（服务端 error 帧 message 有值时 UI 优先用 message） */
    private fun resOf(code: Int): Int = when (code) {
        404 -> R.string.div_error_record_missing
        403 -> R.string.div_error_refused
        429 -> R.string.div_error_rate_limited
        503 -> R.string.div_error_service_unavailable
        else -> R.string.div_reading_error
    }

    // ---- 建档（POST /records → 回写 Room） ----

    /**
     * 确保服务端卦例存在并返回 serverRecordId；失败发 Failure 并返回 null（中断解读）。
     * lines/castMethod 映射见 [buildCreateRequest]（后端 core 重算须拿到与本地一致的原始输入）。
     */
    private suspend fun ensureServerRecord(
        record: DivRecordEntity,
        onEvent: (DivStreamEvent) -> Unit,
    ): Long? {
        if (record.serverRecordId > 0) return record.serverRecordId
        val request = runCatching { buildCreateRequest(record) }
        val result = runCatching { api.createRecord(request.getOrThrow()) }
        val serverId = result.map { it.data ?: 0L }.getOrNull()
        if (serverId == null || serverId <= 0L) {
            val cause = result.exceptionOrNull()
            AppLogger.warn(TAG, "建档失败 localId=${record.id}: ${cause?.message ?: result.getOrNull()?.code}")
            onEvent(
                DivStreamEvent.Failure(
                    DivStreamEvent.Failure.Reason.CREATE_RECORD_FAILED,
                    cause?.message,
                    R.string.div_error_create_record,
                ),
            )
            return null
        }
        runCatching { dao.updateServerRecordId(record.id, serverId) }
        AppLogger.info(TAG, "建档成功 localId=${record.id} serverRecordId=$serverId")
        return serverId
    }

    /**
     * 建档请求映射（后端 CreateRecordFrom，服务端 core 按原始输入重算权威盘面）：
     * - liuyao：端上爻值 1/2/3/4 → 三币和编码 7/8/9/6（后端 LiuYaoEngine 契约 6=老阴 7=少阳 8=少阴 9=老阳）；
     * - meihua：lines=[上卦先天数, 下卦先天数, 动爻]（后端 size>=3 走数字卦路径，时间/报数卦同数同盘）；
     * - qimen/liuren：lines=null（纯时间起局，服务端按 castAt+tzOffset 重算）；
     * clientChart 传本地排盘 JSON（DivChartCodec 序列化结果），不一致仅置 chartVerified=0 不阻断。
     */
    private fun buildCreateRequest(record: DivRecordEntity): DivCreateRecordRequest {
        val bundle = DivChartCodec.decode(record.chartJson)
        val method = DivMethod.fromId(record.method)
        val lines: List<Int>?
        val castMethod: String
        when (method) {
            DivMethod.LIU_YAO -> {
                castMethod = "auto"
                lines = parseLocalLines(record.lines).map { yaoCodeToTossSum(it) }.takeIf { it.size == 6 }
            }
            DivMethod.MEI_HUA -> {
                castMethod = "number"
                val meihua = bundle?.meihua
                lines = meihua?.let { listOf(it.upper.number, it.lower.number, it.movingYao) }
            }
            DivMethod.QI_MEN, DivMethod.LIU_REN -> {
                castMethod = "time"
                lines = null
            }
        }
        return DivCreateRecordRequest(
            method = record.method,
            category = record.category.ifBlank { "general" },
            // 后端 @NotBlank：本地历史脏数据（空问题）兜底占位，避免建档 400
            question = record.question.ifBlank { "未记所问" },
            castMethod = castMethod,
            castAt = record.castAt,
            // 记录起卦时刻的设备时区偏移（分钟），与服务端 LocalDateTime 还原口径一致
            tzOffset = TimeZone.getDefault().getOffset(record.castAt) / 60_000,
            lines = lines,
            clientChart = record.chartJson.ifBlank { null },
        )
    }

    /** 本地 lines JSON（[1,2,3,4,...]）解析；异常/空返回空列表 */
    private fun parseLocalLines(json: String): List<Int> = runCatching {
        gson.fromJson(json, Array<Int>::class.java).toList()
    }.getOrDefault(emptyList())

    /** 端上爻值编码 → 三币求和编码：1少阳→7 2少阴→8 3老阳→9 4老阴→6（越界原样传，服务端校验） */
    private fun yaoCodeToTossSum(code: Int): Int = when (code) {
        1 -> 7
        2 -> 8
        3 -> 9
        4 -> 6
        else -> code
    }

    // ---- Mock（BuildConfig.DIV_LLM_MOCK_ENABLED=true 时回退；文案与真连前逐字一致） ----

    private suspend fun emitMock(record: DivRecordEntity, onEvent: (DivStreamEvent) -> Unit) {
        delay(mockDelayMs)
        val method = runCatching { DivMethod.fromId(record.method) }.getOrDefault(DivMethod.LIU_YAO)
        val category = DivCategory.fromId(record.category)
        val reading = buildMockReading(method, category, record.question, record.chartJson)
        // 旧三字段模型 → markdown 四段，走与真连一致的解析渲染管线（UI 行为等价）
        onEvent(DivStreamEvent.Delta(toMockMarkdown(reading)))
        onEvent(DivStreamEvent.Done(0L, false))
    }

    private suspend fun emitMockFollowUp(
        record: DivRecordEntity,
        question: String,
        history: List<DivFollowUpTurn>,
        onEvent: (DivStreamEvent) -> Unit,
    ) {
        delay(mockDelayMs)
        val method = runCatching { DivMethod.fromId(record.method) }.getOrDefault(DivMethod.LIU_YAO)
        onEvent(DivStreamEvent.Delta(buildMockFollowUp(method, question, history)))
        onEvent(DivStreamEvent.Done(0L, false))
    }

    /** mock 解读文本 → markdown（一句话断语/盘面要点/分项解读/结论与建议 四段） */
    private fun toMockMarkdown(reading: DivReading): String = buildString {
        append("## 一句话断语\n\n").append(reading.summary).append("\n\n")
        reading.sections.forEach { section ->
            append("## ").append(section.title).append("\n\n").append(section.content).append("\n\n")
        }
        append("## 结论与建议\n\n").append(reading.action)
    }

    // ---- Mock 文本生成（原样保留） ----

    private fun buildMockReading(
        method: DivMethod,
        category: DivCategory,
        question: String,
        chartLine: String,
    ): DivReading {
        val categoryLabel = categoryLabel(category)
        val q = question.ifEmpty { "所问之事" }

        val summary = when (method) {
            DivMethod.LIU_YAO -> "本卦六爻排成，世应分明；所问「$q」落在${categoryLabel}，用神可取，动象已见。"
            DivMethod.MEI_HUA -> "梅花以时起卦，体用已分；所问「$q」属${categoryLabel}，卦象所示，吉凶可见端倪。"
            DivMethod.QI_MEN -> "奇门起局完成，九宫排定；所问「$q」在${categoryLabel}之域，方位时令皆有端倪。"
            DivMethod.LIU_REN -> "六壬课成三传，人事占验最精；所问「$q」系${categoryLabel}，四课三传已示机微。"
        }

        val sections = when (method) {
            DivMethod.LIU_YAO -> listOf(
                DivReadingSection(
                    "直接判断",
                    "所问${categoryLabel}之事，倾向有利，但成于出空之后，眼下仍需蓄力等待，非立见分晓之象。",
                ),
                DivReadingSection(
                    "用神与世应",
                    "取用神对照世爻，世持官鬼，官星旺相，主此求于你为正路；应爻相生，外部环境尚可。",
                ),
                DivReadingSection(
                    "关键动变",
                    "动爻发动，化出回头生——先行者先耗而后散，动爻落定之期，正是转机。",
                ),
                DivReadingSection(
                    "应期",
                    "逢值逢合，关注旬末与出空前后两周窗口，届时事态明朗。",
                ),
            )

            DivMethod.MEI_HUA -> listOf(
                DivReadingSection(
                    "直接判断",
                    "体用生克已分，用克体则事稍阻，体生用则耗心力；所问「$q」需以静待时，勿急进。",
                ),
                DivReadingSection(
                    "本互变卦",
                    "本卦示现状，互卦示过程，变卦之象为归结——三卦合参，脉络自明。",
                ),
                DivReadingSection(
                    "体用生克",
                    "体卦静而用卦动，体用相克之处即事之关隘；调其失衡，方得其用。",
                ),
                DivReadingSection(
                    "应期",
                    "卦数合时，关注动爻对应之期，近则数日，远则数周。",
                ),
            )

            DivMethod.QI_MEN -> listOf(
                DivReadingSection(
                    "直接判断",
                    "局象已成，值符值使落宫既定；所问${categoryLabel}之事，择时择方皆有法度。",
                ),
                DivReadingSection(
                    "用神宫位",
                    "用神落宫得令则吉，失令则宜静待；宫位旺衰为断事根本。",
                ),
                DivReadingSection(
                    "空亡驿马",
                    "空亡落宫事宜缓，驿马动处利出行——趋避有道，不拘一时。",
                ),
                DivReadingSection(
                    "应期",
                    "值使落宫之数应期，近则当日，远则合数之期。",
                ),
            )

            DivMethod.LIU_REN -> listOf(
                DivReadingSection(
                    "直接判断",
                    "四课三传已定，课体九宗门可辨；所问${categoryLabel}，机微尽在初传之中。",
                ),
                DivReadingSection(
                    "四课辨析",
                    "日干支上神为四课，阴阳相配之处即事之发端；四课无克则审三传。",
                ),
                DivReadingSection(
                    "三传走势",
                    "初传为事之中，中传为事之转，末传为事之归——三传连珠，脉络自现。",
                ),
                DivReadingSection(
                    "应期",
                    "初传落支应期，逢合逢冲为验，关注传中之支对应时日。",
                ),
            )
        }

        val action = when (method) {
            DivMethod.LIU_YAO -> "此问偏吉，成事在出空之期。近两周集中打磨准备，窗口一到即刻行动。"
            DivMethod.MEI_HUA -> "以静待时为上，勿强求近利。关注动爻应期，届时顺势而为。"
            DivMethod.QI_MEN -> "择时择方，趋吉避凶。用神得令则动，失令则守，不拘于一时一地。"
            DivMethod.LIU_REN -> "人事占验，机在三传。初传落定后顺势而为，勿逆课体而行。"
        }

        return DivReading(summary = summary, sections = sections, action = action)
    }

    private fun buildMockFollowUp(
        method: DivMethod,
        question: String,
        history: List<DivFollowUpTurn>,
    ): String {
        val q = question.ifEmpty { "追问" }
        val turnCount = history.count { it.role == "user" }
        return when {
            turnCount <= 1 -> "仍以原盘为准：所问「$q」，用神得令则可行，旬空未出则宜缓。建议先观后动，待出空窗口再做定夺。"
            else -> "综合本卦与前两轮追问，「$q」之机象渐明——体用不争则事可成，三传不逆则行无碍。时机到时，当断则断。"
        }
    }

    private fun categoryLabel(category: DivCategory): String = when (category) {
        DivCategory.CAREER -> "事业"
        DivCategory.WEALTH -> "财运"
        DivCategory.LOVE -> "感情"
        DivCategory.STUDY -> "学业"
        DivCategory.TRAVEL -> "出行"
        DivCategory.OTHER -> "综合"
    }

    // ---- SSE 事件帧（与后端 SseBridge 契约一致） ----

    private data class DeltaFrame(val text: String? = null)

    private data class DoneFrame(val interpretationId: Long = 0L, val cached: Boolean = false)

    private data class ErrorFrame(val code: Int = 500, val message: String? = null)

    private companion object {
        const val TAG = "DivLlmRepository"

        const val EVENT_DELTA = "delta"
        const val EVENT_DONE = "done"
        const val EVENT_ERROR = "error"
    }
}
