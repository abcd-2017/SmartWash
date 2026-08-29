package com.smartwash.divination.ui.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nlf.calendar.Solar
import com.smartwash.divination.core.DivCategory
import com.smartwash.divination.core.GanZhi
import com.smartwash.divination.core.meihua.MeiHuaChart
import com.smartwash.divination.data.DivRecordEntity
import com.smartwash.divination.data.DivRecordRepository
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
 */
@HiltViewModel
class DivHomeViewModel @Inject constructor(
    private val repository: DivRecordRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DivHomeUiState())
    val uiState: StateFlow<DivHomeUiState> = _uiState.asStateFlow()

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

    /** 今日一签入库（同日去重），返回卦历记录 id 供跳转卦盘复盘 */
    fun ensureTodaySignSaved(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val (id, _) = repository.todaySign(
                question = "今日一签",
                category = DivCategory.OTHER.id,
            )
            onSaved(id)
        }
    }
}
