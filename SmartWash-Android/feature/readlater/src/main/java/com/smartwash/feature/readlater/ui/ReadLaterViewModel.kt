package com.smartwash.feature.readlater.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.pagingFlow
import com.smartwash.feature.readlater.ReadLaterConstant
import com.smartwash.feature.readlater.ReadLaterFormat
import com.smartwash.feature.readlater.network.ReadLaterVo
import com.smartwash.feature.readlater.paging.ReadLaterPagingSource
import com.smartwash.feature.readlater.repository.ReadLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

/**
 * 稍后读列表 ViewModel：收藏输入条 + 未读/已读 Tab + 搜索/标签筛选全部合入分页触发流
 * （pagingFlow 统一 debounce 300ms + flatMapLatest + cachedIn，搜索防抖由该层承担）。
 * 条目级动作（生成摘要/标记已读↔未读/转短链/删除）为一次性任务：在途条目记入
 * [busyIds]（卡片 loading 态），结果经 [events] 一次性事件回页面（Toast/复制）。
 * 表单类主链路（收藏提交）按 RequestState 规范走 loading 态（[adding]）。
 */
@HiltViewModel
class ReadLaterViewModel @Inject constructor(
    private val repository: ReadLaterRepository,
) : ViewModel() {

    /** 收藏输入条草稿：URL 输入 + 标签输入框 + 已提交标签 chips */
    data class Editor(
        val url: String = "",
        val tagInput: String = "",
        val tags: List<String> = emptyList(),
    )

    private val _editor = MutableStateFlow(Editor())
    val editor: StateFlow<Editor> = _editor.asStateFlow()

    /** 未读/已读 Tab（status 过滤值，默认未读） */
    private val _status = MutableStateFlow(ReadLaterConstant.STATUS_UNREAD)
    val status: StateFlow<Int> = _status.asStateFlow()

    /** 搜索关键词（防抖在 pagingFlow 层） */
    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    /** 标签筛选：null = 全部 */
    private val _tagFilter = MutableStateFlow<String?>(null)
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()

    /** 收藏提交在途（收藏按钮 loading/禁用） */
    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    /** 条目级动作在途 id 集合（卡片内 loading 态：摘要生成中…） */
    private val _busyIds = MutableStateFlow<Set<Long>>(emptySet())
    val busyIds: StateFlow<Set<Long>> = _busyIds.asStateFlow()

    /** 一次性事件（Toast/复制剪贴板），页面 LaunchedEffect 消费 */
    private val _events = MutableSharedFlow<ReadLaterEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ReadLaterEvent> = _events.asSharedFlow()

    private val refreshTick = MutableStateFlow("")

    /**
     * 分页流：status|keyword|tag|tick 合入触发串。keyword/tag 经 URL 编码后再拼接，
     * 避免输入内容本身含分隔符 | 破坏解析（encode/decode 对称可逆）。
     */
    val pagingFlow = pagingFlow(
        combine(_status, _keyword, _tagFilter, refreshTick) { status, keyword, tag, tick ->
            listOf(
                status.toString(),
                URLEncoder.encode(keyword, Charsets.UTF_8.name()),
                URLEncoder.encode(tag.orEmpty(), Charsets.UTF_8.name()),
                tick,
            ).joinToString(SEPARATOR)
        }
    ) { trigger ->
        val parts = trigger.split(SEPARATOR)
        ReadLaterPagingSource(
            repository = repository,
            status = parts.getOrNull(0)?.toIntOrNull(),
            keyword = parts.getOrNull(1)?.decodePart()?.takeIf { it.isNotBlank() },
            tag = parts.getOrNull(2)?.decodePart()?.takeIf { it.isNotBlank() },
        )
    }

    // ---------- 收藏输入条 ----------

    fun updateUrl(value: String) {
        _editor.update { it.copy(url = value) }
    }

    fun updateTagInput(value: String) {
        _editor.update { it.copy(tagInput = value) }
    }

    /** 提交标签 chip：去重 + 数量/长度上限（端上软约束）；空输入/重复/超限时只清输入框 */
    fun commitTag() {
        val tag = _editor.value.tagInput.trim()
        if (tag.isEmpty()) return
        _editor.update { draft ->
            if (draft.tags.size >= ReadLaterConstant.TAG_MAX_COUNT || tag in draft.tags) {
                draft.copy(tagInput = "")
            } else {
                draft.copy(tagInput = "", tags = draft.tags + tag.take(ReadLaterConstant.TAG_MAX_LENGTH))
            }
        }
    }

    fun removeTag(tag: String) {
        _editor.update { it.copy(tags = it.tags - tag) }
    }

    /** 收藏：https 简单校验 → POST（标题由后端抓取）→ 成功清空输入条并刷新列表 */
    fun submit() {
        if (_adding.value) return
        val url = _editor.value.url.trim()
        if (!ReadLaterFormat.isValidHttpsUrl(url)) {
            viewModelScope.launch { _events.emit(ReadLaterEvent.InvalidUrl) }
            return
        }
        viewModelScope.launch {
            _adding.value = true
            try {
                repository.add(url, _editor.value.tags)
                _editor.value = Editor()
                refresh()
                _events.emit(ReadLaterEvent.AddSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "submit: ${e.message}", e)
                _events.emit(ReadLaterEvent.Failed(e.message))
            } finally {
                _adding.value = false
            }
        }
    }

    // ---------- 筛选 ----------

    fun setStatus(status: Int) {
        if (_status.value != status) _status.value = status
    }

    fun updateKeyword(value: String) {
        if (_keyword.value != value) _keyword.value = value
    }

    /** 标签筛选（null = 全部；切换筛选后原筛选不在新结果里时由页面归位「全部」） */
    fun selectTag(tag: String?) {
        if (_tagFilter.value != tag) _tagFilter.value = tag
    }

    /** 外部信号驱动刷新（收藏/条目动作/下拉手势共用） */
    fun refresh() {
        refreshTick.value = "t-${System.nanoTime()}"
    }

    // ---------- 条目级动作 ----------

    /**
     * 生成 AI 摘要（后端抓正文调 LLM，可能较慢）：条目 loading 态 + 失败 toast 后端文案。
     * 成功后刷新分页流，摘要随列表回填。
     */
    fun generateDigest(vo: ReadLaterVo) {
        if (vo.id in _busyIds.value) return
        viewModelScope.launch {
            _busyIds.update { it + vo.id }
            try {
                repository.generateDigest(vo.id)
                refresh()
                _events.emit(ReadLaterEvent.DigestSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "generateDigest: ${e.message}", e)
                _events.emit(ReadLaterEvent.Failed(e.message))
            } finally {
                _busyIds.update { it - vo.id }
            }
        }
    }

    /** 标记已读↔未读（PUT status）：成功后刷新让卡片在两个 Tab 间归位，无额外交互 */
    fun toggleStatus(vo: ReadLaterVo) {
        val next = if (vo.status == ReadLaterConstant.STATUS_READ) {
            ReadLaterConstant.STATUS_UNREAD
        } else {
            ReadLaterConstant.STATUS_READ
        }
        viewModelScope.launch {
            try {
                repository.update(id = vo.id, status = next)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "toggleStatus: ${e.message}", e)
                _events.emit(ReadLaterEvent.Failed(e.message))
            }
        }
    }

    /** 转短链：成功事件携带完整短链地址，由页面复制到剪贴板并提示 */
    fun shorten(vo: ReadLaterVo) {
        if (vo.id in _busyIds.value) return
        viewModelScope.launch {
            _busyIds.update { it + vo.id }
            try {
                _events.emit(ReadLaterEvent.ShortenSuccess(repository.shorten(vo.id)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "shorten: ${e.message}", e)
                _events.emit(ReadLaterEvent.Failed(e.message))
            } finally {
                _busyIds.update { it - vo.id }
            }
        }
    }

    /** 删除收藏：成功后刷新（卡片从列表移除即反馈），失败 toast */
    fun delete(vo: ReadLaterVo) {
        if (vo.id in _busyIds.value) return
        viewModelScope.launch {
            _busyIds.update { it + vo.id }
            try {
                repository.delete(vo.id)
                refresh()
                _events.emit(ReadLaterEvent.DeleteSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "delete: ${e.message}", e)
                _events.emit(ReadLaterEvent.Failed(e.message))
            } finally {
                _busyIds.update { it - vo.id }
            }
        }
    }

    private fun String.decodePart(): String =
        runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)

    private companion object {
        const val TAG = "ReadLaterViewModel"
        const val SEPARATOR = "|"
    }
}

/** 一次性事件：Failed.message 为后端文案（null 时页面取兜底资源） */
sealed interface ReadLaterEvent {

    /** URL 未通过 https 简单校验 */
    data object InvalidUrl : ReadLaterEvent

    /** 收藏成功（输入条已被 VM 清空） */
    data object AddSuccess : ReadLaterEvent

    /** 摘要已生成（列表刷新后回填展示） */
    data object DigestSuccess : ReadLaterEvent

    /** 转短链成功：shortUrl 为完整短链地址，页面复制到剪贴板 */
    data class ShortenSuccess(val shortUrl: String) : ReadLaterEvent

    /** 删除成功 */
    data object DeleteSuccess : ReadLaterEvent

    /** 操作失败：message 取后端文案（ResponseInterceptor 透传），null 时页面兜底 */
    data class Failed(val message: String?) : ReadLaterEvent
}
