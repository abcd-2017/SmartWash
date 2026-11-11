package com.smartwash.feature.divination.ui.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nlf.calendar.Solar
import com.smartwash.feature.divination.core.DivCategory
import com.smartwash.feature.divination.core.GanZhi
import com.smartwash.feature.divination.core.meihua.MeiHuaChart
import com.smartwash.feature.divination.database.DivRecordEntity
import com.smartwash.feature.divination.data.DivRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DivTodaySignUi(
    val hexagramName: String,
    val subLine: String,
)

data class DivHomeUiState(
    val tianXiang: String = "",
    val xunKong: String = "",
    val todaySign: DivTodaySignUi? = null,
)

/**
 * 卜问首页 VM：干支天象头（当日四柱）、今日一签（梅花时间卦按当天日期确定性计算）、案卷近三条。
 *
 * 今日一签（issue #78）：签面仍由本地确定性时间卦呈现（同日同签，与服务端算法同源）；
 * 点击入库时先本地幂等建档，再经 [DivRecordRepository.linkTodaySign] 调 GET /today
 * （服务端同日幂等）取服务端卦例 id 回写关联（serverRecordId，服务端优先）——后续求解读
 * 直接复用服务端卦例，避免重复建档；服务端不可达时静默回退纯本地（解读时再由
 * DivLlmRepository.ensureServerRecord 兜底建档）。
 */
@HiltViewModel
class DivHomeViewModel @Inject constructor(
    private val repository: DivRecordRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DivHomeUiState())
    val uiState: StateFlow<DivHomeUiState> = _uiState.asStateFlow()

    private val _signSaving = MutableStateFlow(false)

    /** 今日一签保存中（audit-11 #6）：点击→导航前链路（本地建档 + GET /today 服务端关联）的
     *  轻量 loading 反馈，true 期间页面禁用重复点击；业务链路不变（建档 → 关联 → onSaved 导航） */
    val signSaving: StateFlow<Boolean> = _signSaving.asStateFlow()

    /** 案卷：Room 卦历近三条 */
    val recentRecords: StateFlow<List<DivRecordEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val solar = Solar.fromDate(Date())
            val lunar = solar.lunar
            val siZhu = lunar.getBaZi().map { GanZhi.parse(it) }
            _uiState.value = _uiState.value.copy(
                tianXiang = siZhu.joinToString(" · ") { it.label },
                xunKong = lunar.dayXunKong,
            )

            val chart = MeiHuaChart.timeChart(solar)
            _uiState.value = _uiState.value.copy(
                todaySign = DivTodaySignUi(
                    hexagramName = chart.benName,
                    subLine = "${chart.ti.label}${chart.ti.wuXing.label}体 · ${chart.yong.label}${chart.yong.wuXing.label}用 · ${chart.tiYongRelation.label}",
                ),
            )
        }
    }

    /** 今日一签入库（本地同日去重 + 服务端 /today 幂等关联），返回卦历记录 id 供跳转卦盘复盘；
     *  保存中（audit-11 #6）忽略重复点击，链路结束（含异常）后复位 loading 态 */
    fun ensureTodaySignSaved(onSaved: (Long) -> Unit) {
        if (_signSaving.value) return
        _signSaving.value = true
        viewModelScope.launch {
            try {
                val (id, _) = repository.todaySign(
                    question = "今日一签",
                    category = DivCategory.OTHER.id,
                )
                repository.linkTodaySign(id)
                onSaved(id)
            } finally {
                _signSaving.value = false
            }
        }
    }
}
