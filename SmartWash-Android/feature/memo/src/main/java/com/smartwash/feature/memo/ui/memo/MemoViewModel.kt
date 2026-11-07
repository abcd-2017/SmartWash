package com.smartwash.feature.memo.ui.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pagingFlow
import com.smartwash.feature.memo.MemoConstant
import com.smartwash.feature.memo.R
import com.smartwash.feature.memo.network.MemoTagCountVo
import com.smartwash.feature.memo.network.MemoVo
import com.smartwash.feature.memo.paging.MemoPagingSource
import com.smartwash.feature.memo.repository.MemoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 速记手账 ViewModel：
 * - 列表分页流走 :common:utils 的 [pagingFlow]（debounce + flatMapLatest + cachedIn），
 *   搜索词/标签筛选/刷新 tick 合入触发流，任一变化即换 Pager（搜索即实时过滤）；
 * - 顶部输入区状态收敛在 [Composer]（新建/编辑双模式，编辑回填 editingId/editingPinned）；
 * - 新建保存走 POST，更新/置顶走 PUT（内容与标签原样透传），删除走 DELETE；
 *   成功后重建分页流并刷新标签聚合（新增标签/删除标签后计数与筛选行保持一致）。
 */
@HiltViewModel
class MemoViewModel @Inject constructor(
    private val repository: MemoRepository,
) : ViewModel() {

    /**
     * 顶部随手记输入区状态：content 单一事实来源在 VM（页面 TextField 直读直写）。
     * 编辑模式 = editingId 非空，保存走 PUT 并保持 [editingPinned]（置顶状态不受编辑影响）。
     */
    data class Composer(
        val content: String = "",
        val tagDraft: String = "",
        val selectedTags: List<String> = emptyList(),
        val editingId: Long? = null,
        val editingPinned: Boolean = false,
    ) {
        val isEditing: Boolean get() = editingId != null
    }

    private val _composer = MutableStateFlow(Composer())
    val composer: StateFlow<Composer> = _composer.asStateFlow()

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    /** 标签筛选（null = 全部） */
    private val _tagFilter = MutableStateFlow<String?>(null)
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()

    /** 标签聚合（筛选行 + 输入区常用标签共用） */
    private val _tags = MutableStateFlow<List<MemoTagCountVo>>(emptyList())
    val tags: StateFlow<List<MemoTagCountVo>> = _tags.asStateFlow()

    /** 保存（新建/更新）请求状态 */
    private val _submitState = MutableStateFlow<RequestState>(RequestState.Idle)
    val submitState: StateFlow<RequestState> = _submitState.asStateFlow()

    /** 卡片操作（置顶/删除）请求状态 */
    private val _actionState = MutableStateFlow<RequestState>(RequestState.Idle)
    val actionState: StateFlow<RequestState> = _actionState.asStateFlow()

    /** 卡片操作成功提示（string res，页面 Toast 后消费复位） */
    private val _actionToast = MutableStateFlow<Int?>(null)
    val actionToast: StateFlow<Int?> = _actionToast.asStateFlow()

    private val refreshTick = MutableStateFlow("")

    /**
     * 分页触发流：keyword / tagFilter / refreshTick 编码进触发串（\u0001 分隔，
     * 关键词可能含任意字符，不可用可打印字符做分隔），由 sourceFactory 解码还原。
     */
    val pagingFlow = pagingFlow(
        combine(_keyword, _tagFilter, refreshTick) { keyword, tag, tick ->
            "$keyword\u0001${tag.orEmpty()}\u0001$tick"
        },
        pageSize = MemoConstant.PAGE_SIZE,
    ) { trigger ->
        val parts = trigger.split('\u0001')
        MemoPagingSource(
            repository = repository,
            keyword = parts.getOrNull(0),
            tag = parts.getOrNull(1),
        )
    }

    // ---------- 标签聚合 ----------

    /** 拉取标签聚合（进入页面 + 增删改后调用；失败静默保留旧值，不阻塞列表主链路） */
    fun loadTags() {
        viewModelScope.launch {
            runCatching { repository.listTags() }
                .onSuccess { _tags.value = it }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    AppLogger.warn(TAG, "loadTags: 标签聚合拉取失败：${e.message}")
                }
        }
    }

    // ---------- 随手记输入区 ----------

    fun updateContent(value: String) {
        _composer.value = _composer.value.copy(content = value.take(MemoConstant.CONTENT_MAX_LENGTH))
    }

    fun updateTagDraft(value: String) {
        _composer.value = _composer.value.copy(tagDraft = value)
    }

    /** 提交草稿标签：trim、去重、去空；已存在时仅清空草稿（静默去重，不打断输入） */
    fun addTagFromDraft() {
        val current = _composer.value
        val tag = current.tagDraft.trim()
        if (tag.isEmpty()) return
        _composer.value = if (tag in current.selectedTags) {
            current.copy(tagDraft = "")
        } else {
            current.copy(selectedTags = current.selectedTags + tag, tagDraft = "")
        }
    }

    /** 常用标签 chip 点按：未选 → 加入，已选 → 移除（切换语义） */
    fun toggleTag(tag: String) {
        val current = _composer.value
        _composer.value = if (tag in current.selectedTags) {
            current.copy(selectedTags = current.selectedTags - tag)
        } else {
            current.copy(selectedTags = current.selectedTags + tag)
        }
    }

    fun removeTag(tag: String) {
        _composer.value = _composer.value.copy(selectedTags = _composer.value.selectedTags - tag)
    }

    /** 卡片「编辑」：内容/标签回填顶部输入区，保存按钮切换为更新 */
    fun startEdit(vo: MemoVo) {
        _composer.value = Composer(
            content = vo.content,
            selectedTags = vo.tags,
            editingId = vo.id,
            editingPinned = vo.pinned,
        )
    }

    fun cancelEdit() {
        _composer.value = Composer()
    }

    /** 保存（新建 POST / 编辑 PUT）：空内容拦截为 Error 态由页面 Toast */
    fun save() {
        val current = _composer.value
        if (current.content.isBlank()) {
            _submitState.value = RequestState.Error(R.string.memo_error_empty)
            return
        }
        if (_submitState.value is RequestState.Loading) return
        // 编辑态的 id 在协程外先取定（launch 的 lambda 内禁止非局部 return）
        val editingId = current.editingId
        viewModelScope.launch {
            _submitState.value = RequestState.Loading
            _submitState.value = try {
                if (editingId != null) {
                    repository.updateMemo(
                        id = editingId,
                        content = current.content.trim(),
                        tags = current.selectedTags,
                        pinned = current.editingPinned,
                    )
                } else {
                    repository.createMemo(content = current.content.trim(), tags = current.selectedTags)
                }
                _composer.value = Composer()
                refresh()
                loadTags()
                RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "save: 保存记录失败：${e.message}", e)
                toErrorState(e, R.string.memo_error_save)
            }
        }
    }

    // ---------- 卡片操作 ----------

    /** 置顶/取消置顶：PUT 全量透传当前内容与标签，仅翻转 pinned */
    fun togglePin(vo: MemoVo) {
        if (_actionState.value is RequestState.Loading) return
        viewModelScope.launch {
            _actionState.value = RequestState.Loading
            try {
                repository.updateMemo(id = vo.id, content = vo.content, tags = vo.tags, pinned = !vo.pinned)
                _actionToast.value = if (vo.pinned) R.string.memo_toast_unpinned else R.string.memo_toast_pinned
                refresh()
                _actionState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "togglePin: 置顶切换失败（id=${vo.id}）：${e.message}", e)
                _actionState.value = toErrorState(e, R.string.memo_error_action)
            }
        }
    }

    /** 删除（后端逻辑删除）：成功即重建分页流移出列表，并刷新标签聚合 */
    fun delete(vo: MemoVo) {
        if (_actionState.value is RequestState.Loading) return
        viewModelScope.launch {
            _actionState.value = RequestState.Loading
            try {
                repository.deleteMemo(id = vo.id)
                _actionToast.value = R.string.memo_toast_deleted
                refresh()
                loadTags()
                _actionState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "delete: 删除记录失败（id=${vo.id}）：${e.message}", e)
                _actionState.value = toErrorState(e, R.string.memo_error_action)
            }
        }
    }

    // ---------- 搜索 / 筛选 / 刷新 ----------

    fun setKeyword(value: String) {
        _keyword.value = value
    }

    fun setTagFilter(tag: String?) {
        if (_tagFilter.value != tag) _tagFilter.value = tag
    }

    /** 外部信号驱动刷新（保存/置顶/删除后与下拉手势共用） */
    fun refresh() {
        refreshTick.value = "t-${System.nanoTime()}"
    }

    fun consumeSubmit() {
        _submitState.value = RequestState.Idle
    }

    fun consumeAction() {
        _actionState.value = RequestState.Idle
    }

    fun consumeActionToast() {
        _actionToast.value = null
    }

    /** 异常 → RequestState.Error：业务失败取拦截器转译的 resId + message，其余回落兜底文案 */
    private fun toErrorState(e: Exception, fallbackRes: Int): RequestState.Error {
        val networkException = e as? NetworkException
        return RequestState.Error(
            messageResId = networkException?.resId?.takeIf { it != 0 } ?: fallbackRes,
            message = networkException?.message ?: e.message,
        )
    }

    private companion object {
        const val TAG = "MemoViewModel"
    }
}
