package com.smartwash.feature.divination.ui.page.followup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.divination.R
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.divination.data.DivChartBundle
import com.smartwash.feature.divination.data.DivChartCodec
import com.smartwash.feature.divination.data.DivFollowUpTurn
import com.smartwash.feature.divination.data.DivLlmRepository
import com.smartwash.feature.divination.data.DivStreamEvent
import com.smartwash.feature.divination.database.DivRecordEntity
import com.smartwash.feature.divination.data.DivRecordRepository
import com.smartwash.common.utils.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DivFollowUpUiState(
    val record: DivRecordEntity? = null,
    val bundle: DivChartBundle? = null,
    val history: List<DivFollowUpTurn> = emptyList(),
    /** 当前流式回复（delta 增量累积；null=无进行中回复，空串=已发起尚无增量） */
    val streamingReply: String? = null,
    val followUpState: RequestState = RequestState.Idle,
    val loadingRecord: Boolean = true,
)

/**
 * 追问页 VM（issue #78 真连）：followUp 走与解读同一 SSE 通道，历史上下文由服务端拼接
 * （客户端只发当前问题）。delta 增量累积进 streamingReply 流式上屏，done 后并入 history；
 * error 帧映射 UI 错误态（用户轮保留，可重发）。必要时先确保服务端建档（Repository 内兜底）。
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
            if (record == null) {
                AppLogger.warn(TAG, "追问页卦历记录不存在 recordId=$recordId，页面按空态兜底")
            }
            val bundle = record?.let { DivChartCodec.decode(it.chartJson) }
            _uiState.value = _uiState.value.copy(
                record = record,
                bundle = bundle,
                loadingRecord = false,
            )
        }
    }

    /** 发送追问：追加 user 轮 → SSE 流式回复（delta 累积 / done 并入 history / error 呈错误态） */
    fun send(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _uiState.value.followUpState is RequestState.Loading) return
        viewModelScope.launch {
            val record = _uiState.value.record ?: return@launch

            val userTurn = DivFollowUpTurn(role = "user", content = q)
            val newHistory = _uiState.value.history + userTurn
            _uiState.value = _uiState.value.copy(
                history = newHistory,
                streamingReply = "",
                followUpState = RequestState.Loading,
            )

            try {
                llmRepository.followUp(record, q, newHistory) { event -> onStreamEvent(event) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "DivFollowUpViewModel.send: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    streamingReply = null,
                    followUpState = RequestState.Error(
                        messageResId = R.string.div_reading_error,
                        message = e.message,
                    ),
                )
            }
        }
    }

    private fun onStreamEvent(event: DivStreamEvent) {
        when (event) {
            is DivStreamEvent.Delta -> {
                _uiState.value = _uiState.value.copy(
                    streamingReply = (_uiState.value.streamingReply ?: "") + event.text,
                )
            }

            is DivStreamEvent.Done -> {
                val reply = _uiState.value.streamingReply.orEmpty()
                _uiState.value = _uiState.value.copy(
                    history = _uiState.value.history + DivFollowUpTurn(role = "assistant", content = reply),
                    streamingReply = null,
                    followUpState = RequestState.Success,
                )
                AppLogger.info(TAG, "追问回复成功 recordId=$recordId interpretationId=${event.interpretationId}")
            }

            is DivStreamEvent.Failure -> {
                if (event.reason == DivStreamEvent.Failure.Reason.UNAUTHORIZED) {
                    // 401：SseClient 已清会话并通知 UI 跳登录，本页不再叠加错误文案
                    return
                }
                AppLogger.warn(TAG, "追问失败 recordId=$recordId reason=${event.reason} msg=${event.message}")
                _uiState.value = _uiState.value.copy(
                    streamingReply = null,
                    followUpState = RequestState.Error(
                        messageResId = event.messageResId,
                        message = event.message,
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "DivFollowUpViewModel"
    }
}
