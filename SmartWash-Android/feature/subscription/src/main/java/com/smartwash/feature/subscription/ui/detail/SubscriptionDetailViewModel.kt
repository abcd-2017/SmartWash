package com.smartwash.feature.subscription.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pagingFlow
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.network.AddUsageRequest
import com.smartwash.feature.subscription.network.SubscriptionStatsItemVo
import com.smartwash.feature.subscription.network.SubscriptionVo
import com.smartwash.feature.subscription.paging.SubscriptionUsagePagingSource
import com.smartwash.feature.subscription.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 订阅详情 ViewModel：详情实时拉取（GET /{id}，保证状态与派生字段新鲜）+
 * 打卡分页（pagingFlow）+ 近 90 天统计（stats.items 按 id 取本订阅项）+
 * 状态机动作（pause/resume/cancel/finish）/删除/打卡/撤销。
 * 任一变更成功后由页面调 [refreshAll] 同步刷新详情、打卡流与统计。
 */
@HiltViewModel
class SubscriptionDetailViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val subscriptionId: Long = savedStateHandle.get<Long>(ARG_ID) ?: -1L

    private val _detail = MutableStateFlow<SubscriptionVo?>(null)
    val detail = _detail.asStateFlow()

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    /** 打卡分页流（撤销/打卡后由 [refreshUsages] 重建 Pager） */
    private val usageTick = MutableStateFlow("")
    val usagePagingFlow = pagingFlow(usageTick) { _ ->
        SubscriptionUsagePagingSource(repository, subscriptionId)
    }

    private val _statsItem = MutableStateFlow<SubscriptionStatsItemVo?>(null)
    val statsItem = _statsItem.asStateFlow()

    private val _statsState = MutableStateFlow<RequestState>(RequestState.Idle)
    val statsState = _statsState.asStateFlow()

    /** 状态机动作（pause/resume/cancel/finish）提交状态 */
    private val _transitionState = MutableStateFlow<RequestState>(RequestState.Idle)
    val transitionState = _transitionState.asStateFlow()

    /** 删除提交状态（成功后页面返回列表） */
    private val _deleteState = MutableStateFlow<RequestState>(RequestState.Idle)
    val deleteState = _deleteState.asStateFlow()

    /** 打卡提交状态（同日重复打卡后端拒绝，文案透出） */
    private val _checkInState = MutableStateFlow<RequestState>(RequestState.Idle)
    val checkInState = _checkInState.asStateFlow()

    /** 撤销打卡提交状态 */
    private val _undoState = MutableStateFlow<RequestState>(RequestState.Idle)
    val undoState = _undoState.asStateFlow()

    init {
        load()
    }

    /** 详情加载（成功后连带拉取统计） */
    fun load() {
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                _detail.value = repository.getSubscription(subscriptionId)
                _loadState.value = RequestState.Success
                loadStats()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(R.string.subscription_error_load_detail)
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            _statsState.value = RequestState.Loading
            try {
                val stats = repository.getStats()
                _statsItem.value = stats.items.firstOrNull { it.subscriptionId == subscriptionId }
                _statsState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.loadStats: ${e.message}", e)
                _statsState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.loadStats: ${e.message}", e)
                _statsState.value = RequestState.Error(R.string.subscription_stats_error)
            }
        }
    }

    /** 状态机动作统一走 [transition]（成功后页面刷新全部数据） */
    fun pause() = transition { repository.pauseSubscription(subscriptionId) }

    fun resume() = transition { repository.resumeSubscription(subscriptionId) }

    fun cancel() = transition { repository.cancelSubscription(subscriptionId) }

    fun finish() = transition { repository.finishSubscription(subscriptionId) }

    fun delete() {
        viewModelScope.launch {
            _deleteState.value = RequestState.Loading
            try {
                repository.deleteSubscription(subscriptionId)
                _deleteState.value = RequestState.Success
                AppLogger.info(TAG, "订阅删除成功 id=$subscriptionId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.delete: ${e.message}", e)
                _deleteState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.delete: ${e.message}", e)
                _deleteState.value = RequestState.Error(R.string.subscription_error_submit)
            }
        }
    }

    /** 使用打卡（body 可空契约：不传 usageDate = 今天，后端终审同日唯一） */
    fun checkIn() {
        viewModelScope.launch {
            _checkInState.value = RequestState.Loading
            try {
                repository.checkInUsage(subscriptionId, AddUsageRequest())
                _checkInState.value = RequestState.Success
                AppLogger.info(TAG, "订阅打卡成功 id=$subscriptionId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.checkIn: ${e.message}", e)
                _checkInState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.checkIn: ${e.message}", e)
                _checkInState.value = RequestState.Error(R.string.subscription_error_submit)
            }
        }
    }

    /** 撤销打卡（物理删除） */
    fun undoUsage(usageId: Long) {
        viewModelScope.launch {
            _undoState.value = RequestState.Loading
            try {
                repository.deleteUsage(subscriptionId, usageId)
                _undoState.value = RequestState.Success
                AppLogger.info(TAG, "订阅打卡撤销成功 id=$subscriptionId usageId=$usageId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.undoUsage: ${e.message}", e)
                _undoState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.undoUsage: ${e.message}", e)
                _undoState.value = RequestState.Error(R.string.subscription_error_submit)
            }
        }
    }

    /** 变更成功后的统一刷新：详情 + 统计 + 打卡分页 */
    fun refreshAll() {
        load()
        refreshUsages()
    }

    fun refreshUsages() {
        usageTick.value = "t-${System.nanoTime()}"
    }

    fun resetTransitionState() {
        _transitionState.value = RequestState.Idle
    }

    fun resetDeleteState() {
        _deleteState.value = RequestState.Idle
    }

    fun resetCheckInState() {
        _checkInState.value = RequestState.Idle
    }

    fun resetUndoState() {
        _undoState.value = RequestState.Idle
    }

    private fun transition(block: suspend () -> Unit) {
        viewModelScope.launch {
            _transitionState.value = RequestState.Loading
            try {
                block()
                _transitionState.value = RequestState.Success
                AppLogger.info(TAG, "订阅状态流转成功 id=$subscriptionId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.transition: ${e.message}", e)
                _transitionState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "SubscriptionDetailViewModel.transition: ${e.message}", e)
                _transitionState.value = RequestState.Error(R.string.subscription_error_submit)
            }
        }
    }

    companion object {
        const val ARG_ID = "id"

        private const val TAG = "SubscriptionDetailViewModel"
    }
}
