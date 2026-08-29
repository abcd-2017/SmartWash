package com.smartwash.divination.ui.page.reading

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.R
import com.smartwash.divination.core.DivCategory
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.data.DivChartBundle
import com.smartwash.divination.data.DivChartCodec
import com.smartwash.divination.data.DivLlmRepository
import com.smartwash.divination.data.DivReading
import com.smartwash.divination.data.DivRecordEntity
import com.smartwash.divination.data.DivRecordRepository
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DivReadingUiState(
    val record: DivRecordEntity? = null,
    val bundle: DivChartBundle? = null,
    val reading: DivReading? = null,
    val readingState: RequestState = RequestState.Idle,
    val loadingRecord: Boolean = true,
)

/**
 * 解读页 VM：从 Room 读原盘原时刻 → 调 [DivLlmRepository.interpret]（本期 Mock）获取解读。
 * 后端就绪后只换 Repository 实现，VM 不变。
 */
@HiltViewModel
class DivReadingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordRepository: DivRecordRepository,
    private val llmRepository: DivLlmRepository,
) : ViewModel() {

    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: -1L

    private val _uiState = MutableStateFlow(DivReadingUiState())
    val uiState: StateFlow<DivReadingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val record = recordRepository.getById(recordId)
            val bundle = record?.let { DivChartCodec.decode(it.chartJson) }
            _uiState.value = _uiState.value.copy(
                record = record,
                bundle = bundle,
                loadingRecord = false,
            )
            if (record != null) {
                interpret(record, bundle)
            }
        }
    }

    private suspend fun interpret(record: DivRecordEntity, bundle: DivChartBundle?) {
        _uiState.value = _uiState.value.copy(readingState = RequestState.Loading)
        val method = runCatching { DivMethod.fromId(record.method) }.getOrDefault(DivMethod.LIU_YAO)
        val category = DivCategory.fromId(record.category)
        val result = runCatching {
            llmRepository.interpret(
                method = method,
                category = category,
                question = record.question,
                chartLine = bundle?.let { DivChartCodec.encode(it) } ?: record.chartJson,
            )
        }
        _uiState.value = if (result.isSuccess) {
            _uiState.value.copy(reading = result.getOrNull(), readingState = RequestState.Success)
        } else {
            _uiState.value.copy(
                readingState = RequestState.Error(
                    messageResId = R.string.div_reading_error,
                    message = result.exceptionOrNull()?.message,
                ),
            )
        }
    }
}
