package com.smartwash.feature.notice.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.notice.api.model.NoticeVo
import com.smartwash.feature.notice.repository.NoticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 收件箱分页大小（后端 BaseSearchFrom 默认 10、上限 100；公告量级小取 20 平衡首屏与请求数） */
private const val PAGE_SIZE = 20

/**
 * 收件箱页 ViewModel。
 *
 * 状态划分（请求状态全程 [RequestState]）：
 * - [listState]：首页列表（骨架/错误/内容），下拉刷新复用；
 * - [loadMoreState]：加载更多独立态，失败不影响已加载列表；
 * - [readAllState]：全部已读动作态，Loading 期间按钮禁点（防重第一层，
 *   后端幂等为最终兜底）；
 * - [unreadCount]：未读数独立拉取，失败仅留痕（角标/计数隐藏，不拖垮列表）。
 *
 * 已读操作乐观更新：标记已读立即置灰 + 未读减一，失败回滚并静默重拉未读数；
 * 全部已读成功后整列表置已读 + 未读清零。已读条目重复点击不再请求（后端幂等）。
 */
@HiltViewModel
class NoticeInboxViewModel @Inject constructor(
    private val noticeRepository: NoticeRepository,
) : ViewModel() {

    private val _listState = MutableStateFlow<RequestState>(RequestState.Idle)
    val listState = _listState.asStateFlow()

    private val _notices = MutableStateFlow<List<NoticeVo>>(emptyList())
    val notices = _notices.asStateFlow()

    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount = _unreadCount.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private val _loadMoreState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadMoreState = _loadMoreState.asStateFlow()

    private val _readAllState = MutableStateFlow<RequestState>(RequestState.Idle)
    val readAllState = _readAllState.asStateFlow()

    private var currentPage = 1
    private var total = 0L

    /** 首载/重试/下拉刷新（防重：加载中直接忽略） */
    fun load() {
        if (_listState.value is RequestState.Loading) return
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                val page = noticeRepository.getInbox(1, PAGE_SIZE)
                currentPage = 1
                total = page.total
                _notices.value = page.records
                _hasMore.value = page.records.size < page.total
                _listState.value = RequestState.Success
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "NoticeInboxViewModel.load: ${e.message}", e)
                _listState.value = RequestState.Error(e.resId, e.message)
            }
        }
        loadUnreadCount()
    }

    /** 未读数独立拉取（失败仅留痕，不影响列表主流程） */
    fun loadUnreadCount() {
        viewModelScope.launch {
            try {
                _unreadCount.value = noticeRepository.getUnreadCount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "未读数拉取失败（计数隐藏）：${e.message}")
            }
        }
    }

    /** 加载更多（防重：加载中/无更多直接忽略） */
    fun loadMore() {
        if (_loadMoreState.value is RequestState.Loading || !_hasMore.value) return
        viewModelScope.launch {
            _loadMoreState.value = RequestState.Loading
            try {
                val nextPage = currentPage + 1
                val page = noticeRepository.getInbox(nextPage, PAGE_SIZE)
                currentPage = nextPage
                total = page.total
                _notices.value = _notices.value + page.records
                _hasMore.value = _notices.value.size < total
                _loadMoreState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "NoticeInboxViewModel.loadMore: ${e.message}", e)
                _loadMoreState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /**
     * 标记已读（乐观更新：立即置已读 + 未读减一；失败回滚并静默重拉未读数）。
     * 已读条目重复点击不再请求（后端幂等，端上省一次请求）。
     */
    fun markRead(id: Long) {
        if (id <= 0) return
        val index = _notices.value.indexOfFirst { it.safeId == id }
        if (index < 0) return
        val target = _notices.value[index]
        if (!target.isUnread) return
        _notices.value = _notices.value.toMutableList().apply { set(index, target.copy(isRead = true)) }
        _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)
        viewModelScope.launch {
            try {
                noticeRepository.markRead(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.warn(TAG, "markRead 失败，回滚乐观更新：${e.message}")
                val currentIndex = _notices.value.indexOfFirst { it.safeId == id }
                if (currentIndex >= 0) {
                    _notices.value = _notices.value.toMutableList().apply { set(currentIndex, target) }
                }
                loadUnreadCount()
            }
        }
    }

    /** 全部已读（防重：加载中直接忽略；成功后整列表置已读 + 未读清零） */
    fun readAll() {
        if (_readAllState.value is RequestState.Loading) return
        viewModelScope.launch {
            _readAllState.value = RequestState.Loading
            try {
                noticeRepository.readAll()
                _notices.value = _notices.value.map { it.copy(isRead = true) }
                _unreadCount.value = 0
                _readAllState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "NoticeInboxViewModel.readAll: ${e.message}", e)
                _readAllState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 页面消费完全部已读反馈后复位（Toast 触发在页面 LaunchedEffect，副作用不落渲染分支） */
    fun resetReadAllState() {
        _readAllState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "NoticeInboxViewModel"
    }
}
