package com.smartwash.feature.divination.ui.page.reading

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.divination.R
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.divination.data.DivChartBundle
import com.smartwash.feature.divination.data.DivChartCodec
import com.smartwash.feature.divination.data.DivLlmRepository
import com.smartwash.feature.divination.data.DivMarkdownParser
import com.smartwash.feature.divination.data.DivReading
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

data class DivReadingUiState(
    val record: DivRecordEntity? = null,
    val bundle: DivChartBundle? = null,
    val reading: DivReading? = null,
    val readingState: RequestState = RequestState.Idle,
    /** SSE delta 累积原文（流式逐段上屏；done 后切分为 reading） */
    val streamText: String = "",
    val loadingRecord: Boolean = true,
    /** 应验反馈提交中 */
    val feedbackSubmitting: Boolean = false,
    /** 反馈已提交（成功后隐藏评分入口） */
    val feedbackSubmitted: Boolean = false,
)

/**
 * 解读页 VM（issue #78 真连）：从 Room 读原盘 → 调 [DivLlmRepository.interpret]（SSE 流式，
 * 内部先确保服务端建档）。delta 增量累积进 streamText 供页面逐段渲染，done 后
 * [DivMarkdownParser.parse] 切分为 [DivReading]（四段映射章节卡/断语卡/总结卡）；
 * error 帧映射 UI 错误态（403 拦截 / 429 限额 / 503 降级 / 404 记录不存在）。
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
            if (record == null) {
                AppLogger.warn(TAG, "解读页卦历记录不存在 recordId=$recordId，页面按空态兜底")
            }
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
        _uiState.value = _uiState.value.copy(readingState = RequestState.Loading, streamText = "")
        try {
            llmRepository.interpret(record) { event -> onStreamEvent(event) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error(TAG, "DivReadingViewModel.interpret: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                readingState = RequestState.Error(
                    messageResId = R.string.div_reading_error,
                    message = e.message,
                ),
            )
        }
    }

    private fun onStreamEvent(event: DivStreamEvent) {
        when (event) {
            is DivStreamEvent.Delta -> {
                _uiState.value = _uiState.value.copy(
                    streamText = _uiState.value.streamText + event.text,
                )
            }

            is DivStreamEvent.Done -> {
                val reading = DivMarkdownParser.parse(_uiState.value.streamText)
                _uiState.value = _uiState.value.copy(reading = reading, readingState = RequestState.Success)
                // done 后 Room 已回写 interpretationId（含建档时的 serverRecordId），
                // 刷新 record 快照供应验反馈使用（onStreamEvent 在回调线程，读库走协程）
                viewModelScope.launch {
                    val freshRecord = runCatching { recordRepository.getById(recordId) }.getOrNull()
                    if (freshRecord != null) {
                        _uiState.value = _uiState.value.copy(record = freshRecord)
                    }
                }
                AppLogger.info(TAG, "解读生成成功 recordId=$recordId interpretationId=${event.interpretationId}")
            }

            is DivStreamEvent.Failure -> {
                if (event.reason == DivStreamEvent.Failure.Reason.UNAUTHORIZED) {
                    // 401：SseClient 已清会话并通知 UI 跳登录，本页不再叠加错误文案
                    return
                }
                AppLogger.warn(TAG, "解读失败 recordId=$recordId reason=${event.reason} msg=${event.message}")
                _uiState.value = _uiState.value.copy(
                    readingState = RequestState.Error(
                        messageResId = event.messageResId,
                        message = event.message,
                    ),
                )
            }
        }
    }

    /** 应验反馈提交（POST /records/{id}/feedback，rating 1-5；需已完成解读拿到 interpretationId） */
    fun submitFeedback(rating: Int) {
        val record = _uiState.value.record ?: return
        if (_uiState.value.feedbackSubmitting || _uiState.value.feedbackSubmitted) return
        if (record.serverRecordId <= 0 || record.interpretationId <= 0) {
            AppLogger.warn(TAG, "反馈缺依赖：serverRecordId=${record.serverRecordId} interpretationId=${record.interpretationId}")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(feedbackSubmitting = true)
            val result = llmRepository.feedback(
                serverRecordId = record.serverRecordId,
                interpretationId = record.interpretationId,
                rating = rating,
                outcome = null,
                outcomeNote = null,
            )
            _uiState.value = if (result.isSuccess) {
                AppLogger.info(TAG, "反馈已提交 recordId=$recordId rating=$rating")
                _uiState.value.copy(feedbackSubmitting = false, feedbackSubmitted = true)
            } else {
                AppLogger.warn(TAG, "反馈提交失败 recordId=$recordId: ${result.exceptionOrNull()?.message}")
                _uiState.value.copy(feedbackSubmitting = false)
            }
        }
    }

    private companion object {
        const val TAG = "DivReadingViewModel"
    }
}
