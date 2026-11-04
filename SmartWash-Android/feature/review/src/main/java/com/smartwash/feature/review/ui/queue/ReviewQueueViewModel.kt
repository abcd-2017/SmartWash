package com.smartwash.feature.review.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.review.R
import com.smartwash.feature.review.ReviewConstant
import com.smartwash.feature.review.network.ReviewCardVo
import com.smartwash.feature.review.network.ReviewQueueVo
import com.smartwash.feature.review.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 今日队列 ViewModel：拉取 GET /queue（到期卡在前 + 新卡队尾），
 * 并按队列内 materialId 补齐「所属资料」标题映射（materials 分页翻页直至覆盖，
 * 防御性上限 [ReviewConstant.MATERIAL_TITLE_MAX_PAGES] 页）。
 */
@HiltViewModel
class ReviewQueueViewModel @Inject constructor(
    private val repository: ReviewRepository,
) : ViewModel() {

    data class UiState(
        val loadState: RequestState = RequestState.Idle,
        val queue: ReviewQueueVo? = null,
        val materialTitles: Map<Long, String> = emptyMap(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loadState = RequestState.Loading) }
            try {
                val queue = repository.getQueue()
                val titles = loadMaterialTitles(queue.cards.mapNotNull { it.materialId }.toSet())
                _state.value = UiState(
                    loadState = RequestState.Success,
                    queue = queue,
                    materialTitles = titles,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "ReviewQueueViewModel.load: ${e.message}", e)
                _state.update { it.copy(loadState = RequestState.Error(e.resId, e.message)) }
            } catch (e: Exception) {
                AppLogger.error(TAG, "ReviewQueueViewModel.load: ${e.message}", e)
                _state.update { it.copy(loadState = RequestState.Error(R.string.review_error_load)) }
            }
        }
    }

    /** 队列卡片按 materialId 补齐资料标题（分页翻页直至覆盖全部 id 或翻页到顶） */
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
        if (titles.size < ids.size) {
            AppLogger.warn(TAG, "loadMaterialTitles: 资料标题翻页结束仍未完全覆盖 ids=${ids.size} covered=${titles.size}")
        }
        return titles
    }

    /** 队列条目（复习入口按卡片定位） */
    fun cardAt(index: Int): ReviewCardVo? = _state.value.queue?.cards?.getOrNull(index)

    private companion object {
        const val TAG = "ReviewQueueViewModel"
    }
}
