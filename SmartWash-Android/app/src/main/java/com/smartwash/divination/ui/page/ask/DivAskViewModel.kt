package com.smartwash.divination.ui.page.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nlf.calendar.Solar
import com.smartwash.divination.core.DivCategory
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.core.GanZhi
import com.smartwash.divination.core.liuren.LiuRenChart
import com.smartwash.divination.core.meihua.MeiHuaChart
import com.smartwash.divination.core.qimen.QiMenChart
import com.smartwash.divination.data.DivChartBundle
import com.smartwash.divination.data.DivRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 六爻起卦方式 */
enum class LiuYaoCastMode { SHAKE, MANUAL, LINES }

/** 梅花起卦方式 */
enum class MeiHuaCastMode { TIME, NUMBERS }

data class DivAskUiState(
    val method: DivMethod = DivMethod.LIU_YAO,
    val question: String = "",
    val category: DivCategory = DivCategory.OTHER,
    val categoryAuto: Boolean = true,
    val liuYaoMode: LiuYaoCastMode = LiuYaoCastMode.SHAKE,
    val meiHuaMode: MeiHuaCastMode = MeiHuaCastMode.TIME,
    val safetyBlocked: Boolean = false,
    val saving: Boolean = false,
)

/**
 * 问事页 VM：领域自动预选（关键词）、高风险问题前置分流（整页替换，不排盘）、
 * 梅花/奇门/六壬直接排盘入库（六爻摇卦走 DivCastViewModel）。
 */
@HiltViewModel
class DivAskViewModel @Inject constructor(
    private val repository: DivRecordRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DivAskUiState())
    val uiState: StateFlow<DivAskUiState> = _uiState.asStateFlow()

    fun initMethod(methodId: String) {
        val method = runCatching { DivMethod.fromId(methodId) }.getOrDefault(DivMethod.LIU_YAO)
        _uiState.value = DivAskUiState(method = method)
    }

    fun onQuestionChange(text: String) {
        val blocked = isHighRisk(text)
        val auto = classifyCategory(text)
        _uiState.value = _uiState.value.copy(
            question = text,
            safetyBlocked = blocked,
            category = if (_uiState.value.categoryAuto) auto else _uiState.value.category,
        )
    }

    fun onCategorySelect(category: DivCategory) {
        _uiState.value = _uiState.value.copy(category = category, categoryAuto = false)
    }

    fun onLiuYaoMode(mode: LiuYaoCastMode) {
        _uiState.value = _uiState.value.copy(liuYaoMode = mode)
    }

    fun onMeiHuaMode(mode: MeiHuaCastMode) {
        _uiState.value = _uiState.value.copy(meiHuaMode = mode)
    }

    /** 梅花时间卦：按当前时刻确定性起卦入库 */
    fun castMeiHuaTime(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val chart = MeiHuaChart.timeChart(Solar.fromDate(Date()))
            saved(chart.lines, DivChartBundle(DivMethod.MEI_HUA.id, meihua = chart), onSaved)
        }
    }

    /** 梅花报数卦 */
    fun castMeiHuaNumbers(a: Int, b: Int, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val chart = MeiHuaChart.numbersChart(a, b)
            saved(chart.lines, DivChartBundle(DivMethod.MEI_HUA.id, meihua = chart), onSaved)
        }
    }

    /** 奇门起局 */
    fun castQiMen(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val chart = QiMenChart.compile(Solar.fromDate(Date()))
            saved(emptyList(), DivChartBundle(DivMethod.QI_MEN.id, qimen = chart), onSaved)
        }
    }

    /** 大六壬起课 */
    fun castLiuRen(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val solar = Solar.fromDate(Date())
            val lunar = solar.lunar
            val (jiang, jiangName) = LiuRenChart.yueJiang(solar)
            val chart = LiuRenChart.compile(
                dayGanZhi = GanZhi.parse(lunar.dayInGanZhi),
                hourGanZhi = GanZhi.parse(lunar.timeInGanZhi),
                yueJiang = jiang,
                yueJiangName = jiangName,
            )
            saved(emptyList(), DivChartBundle(DivMethod.LIU_REN.id, liuren = chart), onSaved)
        }
    }

    /** 手动输入爻值直接装卦（六爻「直接输入爻值」路径） */
    fun castManualLines(lines: List<Int>, onSaved: (Long) -> Unit) {
        if (lines.size != 6 || lines.any { it !in 1..4 }) return
        viewModelScope.launch {
            val solar = Solar.fromDate(Date())
            val chart = com.smartwash.divination.core.liuyao.LiuYaoChart.compile(lines, solar)
            saved(lines, DivChartBundle(DivMethod.LIU_YAO.id, liuyao = chart), onSaved)
        }
    }

    private suspend fun saved(lines: List<Int>, bundle: DivChartBundle, onSaved: (Long) -> Unit) {
        val state = _uiState.value
        _uiState.value = state.copy(saving = true)
        val id = repository.save(
            method = DivMethod.fromId(bundle.method),
            category = state.category.id,
            question = state.question.trim(),
            lines = lines,
            castAt = System.currentTimeMillis(),
            bundle = bundle,
        )
        _uiState.value = state.copy(saving = false)
        onSaved(id)
    }

    companion object {
        /** 高风险问题：医疗/生死/胎儿性别/失踪 —— 直接分流现实求助，不排盘不解读 */
        private val HIGH_RISK_KEYWORDS = listOf(
            "病", "癌", "瘤", "医", "药", "手术", "诊断", "抑郁", "怀孕", "妊",
            "死", "自杀", "轻生", "寿命", "阳寿",
            "胎儿", "性别", "男孩", "女孩",
            "失踪", "走失", "寻人",
        )

        private val CATEGORY_KEYWORDS = mapOf(
            DivCategory.CAREER to listOf("工作", "事业", "offer", "面试", "升职", "跳槽", "岗位", "实习", "转正"),
            DivCategory.WEALTH to listOf("财", "钱", "收入", "工资", "回款", "生意", "投资", "欠", "借款"),
            DivCategory.LOVE to listOf("感情", "恋", "婚", "表白", "对象", "心动", "分手", "复合"),
            DivCategory.STUDY to listOf("学", "考试", "考研", "绩点", "论文", "毕业", "保研", "四六级", "考证"),
            DivCategory.TRAVEL to listOf("出行", "旅行", "搬家", "出差", "车票", "远行", "返校"),
        )

        fun isHighRisk(question: String): Boolean =
            HIGH_RISK_KEYWORDS.any { question.contains(it) }

        fun classifyCategory(question: String): DivCategory =
            CATEGORY_KEYWORDS.entries.firstOrNull { (_, words) -> words.any { question.contains(it, ignoreCase = true) } }?.key
                ?: DivCategory.OTHER
    }
}
