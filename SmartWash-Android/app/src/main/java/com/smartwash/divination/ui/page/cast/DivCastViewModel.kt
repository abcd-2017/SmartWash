package com.smartwash.divination.ui.page.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nlf.calendar.Solar
import com.smartwash.divination.core.CoinTosser
import com.smartwash.divination.core.DivCategory
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.core.YaoValue
import com.smartwash.divination.core.liuyao.LiuYaoChart
import com.smartwash.divination.data.DivChartBundle
import com.smartwash.divination.data.DivRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DivCastUiState(
    val lines: List<Int> = emptyList(),   // 已定六爻（自初爻累积）
    val tossing: Boolean = false,         // 动画播放中（禁重掷：爻值确定性序列不可被改写）
    val saving: Boolean = false,
)

/**
 * 摇卦页 VM：SecureRandom 三币×六掷，逐爻累积；六掷完成装卦入库（记录起卦时刻）。
 */
@HiltViewModel
class DivCastViewModel @Inject constructor(
    private val repository: DivRecordRepository,
) : ViewModel() {

    private val tosser = CoinTosser()

    private val _uiState = MutableStateFlow(DivCastUiState())
    val uiState: StateFlow<DivCastUiState> = _uiState.asStateFlow()

    /** 取下一次摇卦结果（随机源 SecureRandom；结果先于动画确定，动画只呈现不改变） */
    fun nextToss(): YaoValue = tosser.tossLine()

    /** 动画落定后提交本爻 */
    fun commitToss(yao: YaoValue) {
        val current = _uiState.value
        if (current.lines.size >= 6) return
        _uiState.value = current.copy(lines = current.lines + yao.code, tossing = false)
    }

    fun setTossing(active: Boolean) {
        _uiState.value = _uiState.value.copy(tossing = active)
    }

    fun isComplete(): Boolean = _uiState.value.lines.size == 6

    /** 六掷完成：以当前时刻起四柱装卦入库（存入卦历），返回记录 id */
    fun completeChart(question: String, category: DivCategory, onSaved: (Long) -> Unit) {
        val lines = _uiState.value.lines
        if (lines.size != 6 || _uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true)
            val castAt = System.currentTimeMillis()
            val chart = LiuYaoChart.compile(lines, Solar.fromDate(Date(castAt)))
            val id = repository.save(
                method = DivMethod.LIU_YAO,
                category = category.id,
                question = question,
                lines = lines,
                castAt = castAt,
                bundle = DivChartBundle(DivMethod.LIU_YAO.id, liuyao = chart),
            )
            _uiState.value = _uiState.value.copy(saving = false)
            onSaved(id)
        }
    }
}
