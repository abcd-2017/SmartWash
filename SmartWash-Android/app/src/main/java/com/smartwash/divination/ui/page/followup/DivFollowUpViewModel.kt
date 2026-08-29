package com.smartwash.divination.ui.page.followup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.R
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.data.DivChartBundle
import com.smartwash.divination.data.DivChartCodec
import com.smartwash.divination.data.DivFollowUpTurn
import com.smartwash.divination.data.DivLlmRepository
import com.smartwash.divination.data.DivRecordEntity
import com.smartwash.divination.data.DivRecordRepository
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DivFollowUpUiState(
    val record: DivRecordEntity? = null,
    val bundle: DivChartBundle? = null,
    val history: List<DivFollowUpTurn> = emptyList(),
    val followUpState: RequestState = RequestState.Idle,
    val loadingRecord: Boolean = true,
)

/**
 * 追问页 VM：从 Room 读原盘原时刻（沿用原 castAt 与原盘面，不重算）→ 调 [DivLlmRepository.followUp]。
 * 后端就绪后只换 Repository 实现，VM 不变。
 */
@HiltViewModel
class DivFollowUpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordRepository: DivRecordRepository,
    private val llmRepository: DivLlmRepository,
) : ViewModel() {

    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: -1L

    private val _uiState = MutableStateFlow(DivFollowUpUiState())
    val uiState: StateFlow<DivFollowUpUiState> = _uiState.asStateFlow()

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
        }
    }

    /** 发送追问：追加 user 轮 → 调 followUp → 追加 assistant 轮 */
    fun send(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _uiState.value.followUpState is RequestState.Loading) return
        viewModelScope.launch {
            val record = _uiState.value.record ?: return@launch
            val method = runCatching { DivMethod.fromId(record.method) }.getOrDefault(DivMethod.LIU_YAO)

            val userTurn = DivFollowUpTurn(role = "user", content = q)
            val newHistory = _uiState.value.history + userTurn
            _uiState.value = _uiState.value.copy(
                history = newHistory,
                followUpState = RequestState.Loading,
            )

            val result = runCatching {
                llmRepository.followUp(
                    method = method,
                    question = q,
                    history = newHistory,
                )
            }
            if (result.isSuccess) {
                val reply = DivFollowUpTurn(role = "assistant", content = result.getOrThrow())
                _uiState.value = _uiState.value.copy(
                    history = newHistory + reply,
                    followUpState = RequestState.Success,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    followUpState = RequestState.Error(
                        messageResId = R.string.div_reading_error,
                        message = result.exceptionOrNull()?.message,
                    ),
                )
            }
        }
    }
}
