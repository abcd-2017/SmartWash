package com.smartwash.divination.data

import com.nlf.calendar.Solar
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.core.meihua.MeiHuaChart
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 卦历仓库 —— 观象台排盘全部离线，数据走「内存即时计算 → Room」，
 * 复盘/追问一律读库中原盘原时刻，不重算。
 */
@Singleton
class DivRecordRepository @Inject constructor(
    private val dao: DivRecordDao,
) {

    fun observeAll(): Flow<List<DivRecordEntity>> = dao.observeAll()

    fun observeByMethod(method: DivMethod): Flow<List<DivRecordEntity>> = dao.observeByMethod(method.id)

    fun observeById(id: Long): Flow<DivRecordEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): DivRecordEntity? = dao.getById(id)

    suspend fun recent(limit: Int = 3): List<DivRecordEntity> = dao.recent(limit)

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
        val id = existing?.id ?: save(
            method = DivMethod.MEI_HUA,
            category = category,
            question = question,
            lines = chart.lines,
            castAt = System.currentTimeMillis(),
            bundle = DivChartBundle(method = DivMethod.MEI_HUA.id, meihua = chart),
        )
        return id to chart
    }
}
