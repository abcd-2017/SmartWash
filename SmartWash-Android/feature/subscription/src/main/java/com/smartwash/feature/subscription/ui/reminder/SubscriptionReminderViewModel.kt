package com.smartwash.feature.subscription.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.network.ReminderVo
import com.smartwash.feature.subscription.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 续费提醒 ViewModel：单次全量拉取（接口非分页，上限 200 条）+
 * 未读/全部筛选（切换即重拉）；标记已读幂等——未读模式本地移除、全部模式本地置已读，
 * 避免整表重拉闪烁；同时由页面置变更信号让列表页红点刷新。
 */
@HiltViewModel
class SubscriptionReminderViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    private val _unreadOnly = MutableStateFlow(true)
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    private val _items = MutableStateFlow<List<ReminderVo>>(emptyList())
    val items: StateFlow<List<ReminderVo>> = _items.asStateFlow()

    private val _listState = MutableStateFlow<RequestState>(RequestState.Idle)
    val listState: StateFlow<RequestState> = _listState.asStateFlow()

    /** 标记已读提交状态（失败页面 toast，不打断列表） */
    private val _markReadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val markReadState: StateFlow<RequestState> = _markReadState.asStateFlow()

    init {
        load()
    }

    fun setFilter(unreadOnly: Boolean) {
        if (_unreadOnly.value == unreadOnly) return
        _unreadOnly.value = unreadOnly
        load()
    }

    fun load() {
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                _items.value = repository.listReminders(_unreadOnly.value)
                _listState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _listState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                _listState.value = RequestState.Error(R.string.subscription_reminder_error_load)
            }
        }
    }

    /** 点击条目标记已读（幂等）；本地先行收敛，服务端为准 */
    fun markRead(reminderId: Long) {
        viewModelScope.launch {
            _markReadState.value = RequestState.Loading
            try {
                repository.markReminderRead(reminderId)
                _items.value = if (_unreadOnly.value) {
                    _items.value.filterNot { it.reminderId == reminderId }
                } else {
                    _items.value.map { if (it.reminderId == reminderId) it.copy(isRead = 1) else it }
                }
                _markReadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _markReadState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                _markReadState.value = RequestState.Error(R.string.subscription_error_submit)
            }
        }
    }

    fun resetMarkReadState() {
        _markReadState.value = RequestState.Idle
    }
}
