package com.smartwash.feature.divination.data

import com.nlf.calendar.Solar
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.core.meihua.MeiHuaChart
import com.smartwash.feature.divination.database.DivRecordDao
import com.smartwash.feature.divination.database.DivRecordEntity
import com.smartwash.feature.divination.api.network.DivUserApiConfigVo
import com.smartwash.feature.divination.api.network.DivinationApi
import com.smartwash.feature.divination.api.network.DivRecordVo
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 卦历仓库 —— 观象台排盘全部离线，数据走「内存即时计算 → Room」，
 * 复盘/追问一律读库中原盘原时刻，不重算。
 *
 * 服务端关联（issue #78）：卦历对账（分页对账回填 serverRecordId）与今日一签
 * 服务端幂等关联也由本仓库承接（VM 不直连 Retrofit Api）。
 */
@Singleton
class DivRecordRepository @Inject constructor(
    private val dao: DivRecordDao,
    private val api: DivinationApi,
) {

    fun observeAll(): Flow<List<DivRecordEntity>> = dao.observeAll()

    fun observeByMethod(method: DivMethod): Flow<List<DivRecordEntity>> = dao.observeByMethod(method.id)

    fun observeById(id: Long): Flow<DivRecordEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): DivRecordEntity? = dao.getById(id)

    suspend fun recent(limit: Int = 3): List<DivRecordEntity> = dao.recent(limit)

    suspend fun findUnlinked(): List<DivRecordEntity> = dao.findUnlinked()

    /** 回写服务端卦例 id（建档 / 今日一签关联） */
    suspend fun markServerLinked(localId: Long, serverRecordId: Long) {
        if (serverRecordId > 0) dao.updateServerRecordId(localId, serverRecordId)
    }

    /** 回写最近一次解读的服务端 id（SSE done 帧） */
    suspend fun markInterpretation(localId: Long, interpretationId: Long) {
        if (interpretationId > 0) dao.updateInterpretationId(localId, interpretationId)
    }

    /**
     * 用户解读模型配置（2026-11-11 契约，解读入口拦截用）：成功时 data=null = 未配置
     * （平台零兜底，解读统一 428）。查询失败走 Result.failure——拦截属前置体验优化，
     * 不因网络抖动把用户误拦在卦盘页（调用方失败时放行，SSE 侧 428/403 帧仍有兜底文案）。
     */
    suspend fun getUserApiConfig(): Result<DivUserApiConfigVo?> =
        runCatching { api.getUserApiConfig().data }
            .onFailure { AppLogger.warn(TAG, "解读模型配置查询失败（放行走 SSE 兜底）: ${it.message}") }

    /**
     * 卦历对账（issue #78，自 DivHistoryViewModel 下沉）：静默拉服务端分页首页，把
     * 「本地未关联服务端」的记录按 method+question+castAt 对账回填 serverRecordId——
     * 后续求解读/追问直接复用服务端卦例。失败静默返回 0（卦盘/解读链路另有
     * ensureServerRecord 兜底建档），本地展示不受影响。
     *
     * @return 本次回填关联的记录数（0 = 无回填或拉取失败）
     */
    suspend fun syncServerLinks(page: Int = 1, size: Int = 50): Int {
        val result = runCatching { api.searchRecords(page = page, size = size) }
            .onFailure { AppLogger.warn(TAG, "卦历服务端同步失败（本地展示不受影响）: ${it.message}") }
            .getOrNull() ?: return 0
        val serverRecords = result.data?.records.orEmpty()
        if (result.code != 200 || serverRecords.isEmpty()) return 0
        val unlinked = findUnlinked()
        if (unlinked.isEmpty()) return 0
        var linked = 0
        unlinked.forEach { local ->
            serverRecords.firstOrNull { it.matches(local) }?.let { match ->
                markServerLinked(local.id, match.id)
                linked++
            }
        }
        if (linked > 0) {
            AppLogger.info(TAG, "卦历同步回填 serverRecordId ×$linked / 未关联 ${unlinked.size}")
        }
        return linked
    }

    /**
     * 今日一签服务端幂等关联（issue #78，自 DivHomeViewModel 下沉）：GET /today
     * （服务端同日幂等）取服务端卦例 id 回写本地记录 serverRecordId（服务端优先）。
     * 失败静默回退纯本地（解读时再由 DivLlmRepository.ensureServerRecord 兜底建档）。
     */
    suspend fun linkTodaySign(localId: Long) {
        runCatching { api.getTodayRecord() }
            .onSuccess { result ->
                val serverId = result.data?.id ?: 0L
                if (result.code == 200 && serverId > 0) {
                    markServerLinked(localId, serverId)
                    AppLogger.info(TAG, "今日一签关联服务端卦例 localId=$localId serverRecordId=$serverId")
                }
            }
            .onFailure {
                AppLogger.warn(TAG, "今日一签服务端关联失败（回退本地，解读时再兜底建档）: ${it.message}")
            }
    }

    /** 对账键：method + question + castAt（服务端 LocalDateTime 按 tzOffset 还原为 epoch millis） */
    private fun DivRecordVo.matches(local: DivRecordEntity): Boolean =
        method == local.method &&
            question == local.question &&
            castAtMillis() == local.castAt

    private fun DivRecordVo.castAtMillis(): Long? = runCatching {
        val iso = castAt ?: return null
        val offsetSeconds = (tzOffset ?: 480) * 60
        LocalDateTime.parse(iso).toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds)) * 1000L
    }.getOrNull()

    /** 保存排盘记录（摇卦成卦 / 起局 / 今日一签入库），返回记录 id */
    suspend fun save(
        method: DivMethod,
        category: String,
        question: String,
        lines: List<Int>,
        castAt: Long,
        bundle: DivChartBundle,
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            DivRecordEntity(
                method = method.id,
                category = category,
                question = question,
                lines = lines.joinToString(",", "[", "]"),
                castAt = castAt,
                chartJson = DivChartCodec.encode(bundle),
                createdAt = now,
            )
        )
    }

    /**
     * 今日一签：按当天日期起梅花时间卦（确定性，同日同签）。
     * 当天已有签则直接复用卦历记录，保证"每天一次、结果存 Room"。
     */
    suspend fun todaySign(question: String, category: String): Pair<Long, MeiHuaChart> {
        val now = Calendar.getInstance()
        val solar = Solar.fromDate(Date(now.timeInMillis))
        val chart = MeiHuaChart.timeChart(solar)

        val dayStart = now.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val existing = dao.findTodaySign(DivMethod.MEI_HUA.id, question, dayStart, dayStart + 24 * 3600_000L)
        val id = existing?.id ?: run {
            val newId = save(
                method = DivMethod.MEI_HUA,
                category = category,
                question = question,
                lines = chart.lines,
                castAt = System.currentTimeMillis(),
                bundle = DivChartBundle(method = DivMethod.MEI_HUA.id, meihua = chart),
            )
            AppLogger.info(TAG, "今日一签入库 recordId=$newId")
            newId
        }
        if (existing != null) {
            AppLogger.debug(TAG, "todaySign: 今日一签幂等命中，复用当日记录 recordId=${existing.id}")
        }
        return id to chart
    }

    private companion object {
        const val TAG = "DivRecordRepository"
    }
}
