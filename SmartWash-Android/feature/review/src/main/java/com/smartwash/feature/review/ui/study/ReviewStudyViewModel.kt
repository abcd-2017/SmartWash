package com.smartwash.feature.review.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.review.R
import com.smartwash.feature.review.ReviewConstant
import com.smartwash.feature.review.ReviewRoute
import com.smartwash.feature.review.model.ReviewRating
import com.smartwash.feature.review.network.ReviewCardVo
import com.smartwash.feature.review.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 复习 ViewModel：进入时拉取 GET /queue（可选 startCardId 把指定卡轮转到队首），
 * 评分走 POST /answer（rating 1..4 + durationMs），成功后本地移除该卡并自动落到下一张；
 * 队列清空即完成态。FSRS 调度全部在后端，本 VM 不做任何间隔计算。
 */
@HiltViewModel
class ReviewStudyViewModel @Inject constructor(
    private val repository: ReviewRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 路由入参：起始卡片 id（-1 = 从队首开始） */
    private val startCardId: Long =
        savedStateHandle.get<Long>(ReviewRoute.ARG_START_CARD_ID) ?: -1L

    data class StudyState(
        val loadState: RequestState = RequestState.Idle,
        /** 剩余队列（评分成功即移除，cards.firstOrNull() 为当前卡） */
        val cards: List<ReviewCardVo> = emptyList(),
        /** 本轮总张数（进度分母） */
        val total: Int = 0,
        /** 是否已翻面（正面问题 → 背面答案 + 评分按钮） */
        val flipped: Boolean = false,
        /** 评分提交状态（Loading 整排禁用；Error 由页面 toast 后消费） */
        val answerState: RequestState = RequestState.Idle,
        /** 本轮评分成功次数（页面据此向队列页发数据变更信号） */
        val successCount: Int = 0,
        val materialTitles: Map<Long, String> = emptyMap(),
    ) {
        val currentCard: ReviewCardVo? get() = cards.firstOrNull()
        val doneCount: Int get() = (total - cards.size).coerceAtLeast(0)
    }

    private val _state = MutableStateFlow(StudyState())
    val state = _state.asStateFlow()

    /** 当前卡展示时刻——评分时折算 durationMs（契约 AnswerFrom.durationMs，可空） */
    private var cardShownAtMs = 0L

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loadState = RequestState.Loading,
                    flipped = false,
                    answerState = RequestState.Idle,
                )
            }
            try {
                val queue = repository.getQueue()
                val ordered = if (startCardId > 0) {
                    val target = queue.cards.firstOrNull { it.id == startCardId }
                    if (target != null) {
                        listOf(target) + queue.cards.filterNot { it.id == startCardId }
                    } else {
                        queue.cards
                    }
                } else {
                    queue.cards
                }
                val titles = loadMaterialTitles(ordered.mapNotNull { it.materialId }.toSet())
                _state.update {
                    it.copy(
                        loadState = RequestState.Success,
                        cards = ordered,
                        total = ordered.size,
                        materialTitles = titles,
                    )
                }
                cardShownAtMs = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _state.update { it.copy(loadState = RequestState.Error(e.resId, e.message)) }
            } catch (e: Exception) {
                _state.update { it.copy(loadState = RequestState.Error(R.string.review_error_load)) }
            }
        }
    }

    private suspend fun loadMaterialTitles(ids: Set<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val titles = mutableMapOf<Long, String>()
        for (page in 1..ReviewConstant.MATERIAL_TITLE_MAX_PAGES) {
            val data = repository.pageMaterials(page = page, size = ReviewConstant.PAGE_SIZE)
            data.records.forEach { material ->
                if (material.id in ids) titles[material.id] = material.title
            }
            if (titles.size >= ids.size) break
            if (page.toLong() * ReviewConstant.PAGE_SIZE >= data.total) break
            if (data.records.isEmpty()) break
        }
        return titles
    }

    /** 翻面（提交中禁止切换，避免答案/评分态错位） */
    fun flip() {
        if (_state.value.answerState is RequestState.Loading) return
        _state.update { it.copy(flipped = !it.flipped) }
    }

    /** 评分提交：成功后本地移除该卡、复位翻面，自动落到下一张（真实状态驱动，无伪造动画） */
    fun rate(rating: ReviewRating) {
        val current = _state.value.currentCard ?: return
        if (_state.value.answerState is RequestState.Loading) return
        val durationMs = (System.currentTimeMillis() - cardShownAtMs)
            .toInt().coerceIn(0, Int.MAX_VALUE)
        viewModelScope.launch {
            _state.update { it.copy(answerState = RequestState.Loading) }
            try {
                repository.answer(cardId = current.id, rating = rating, durationMs = durationMs)
                _state.update {
                    it.copy(
                        cards = it.cards.filterNot { card -> card.id == current.id },
                        flipped = false,
                        answerState = RequestState.Idle,
                        successCount = it.successCount + 1,
                    )
                }
                cardShownAtMs = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _state.update { it.copy(answerState = RequestState.Error(e.resId, e.message)) }
            } catch (e: Exception) {
                _state.update { it.copy(answerState = RequestState.Error(R.string.review_error_answer)) }
            }
        }
    }

    /** 页面 toast 消费评分错误后复位（卡片保留，可重试） */
    fun consumeAnswerError() {
        _state.update {
            if (it.answerState is RequestState.Error) it.copy(answerState = RequestState.Idle) else it
        }
    }
}
