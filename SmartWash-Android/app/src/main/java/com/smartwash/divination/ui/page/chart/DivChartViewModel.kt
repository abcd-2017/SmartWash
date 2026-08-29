package com.smartwash.divination.ui.page.chart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.divination.core.liuyao.LiuYaoChart
import com.smartwash.divination.core.liuyao.LiuYaoFacts
import com.smartwash.divination.data.DivChartBundle
import com.smartwash.divination.data.DivChartCodec
import com.smartwash.divination.data.DivRecordEntity
import com.smartwash.divination.data.DivRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DivChartUiState(
    val record: DivRecordEntity? = null,
    val bundle: DivChartBundle? = null,
    val liuYaoFacts: LiuYaoFacts? = null,
    val loading: Boolean = true,
)

/**
 * 卦盘页 VM：从 Room 卦历读原盘原时刻（复盘/追问沿用首卦 chart JSON，不重算）。
 */
@HiltViewModel
class DivChartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DivRecordRepository,
) : ViewModel() {

    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: -1L

    private val _uiState = MutableStateFlow(DivChartUiState())
    val uiState: StateFlow<DivChartUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val record = repository.getById(recordId)
            val bundle = record?.let { DivChartCodec.decode(it.chartJson) }
            val liuYao = bundle?.liuyao
            _uiState.value = DivChartUiState(
                record = record,
                bundle = bundle,
                liuYaoFacts = liuYao?.let {
                    LiuYaoFacts.of(
                        it,
                        com.smartwash.divination.core.DivCategory.fromId(record.category),
                    )
                },
                loading = false,
            )
        }
    }

    /** 爻值文本（解读/追问 packet 复用） */
    fun linesText(): String = _uiState.value.record?.lines ?: "[]"

    companion object {
        fun decode(json: String): DivChartBundle? = DivChartCodec.decode(json)
        fun extractLiuYao(bundle: DivChartBundle): LiuYaoChart? = bundle.liuyao
    }
}
