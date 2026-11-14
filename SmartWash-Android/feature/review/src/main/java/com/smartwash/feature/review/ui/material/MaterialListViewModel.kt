package com.smartwash.feature.review.ui.material

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.review.R
import com.smartwash.feature.review.ReviewConstant
import com.smartwash.feature.review.network.MaterialVo
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
 * 资料页 ViewModel：资料分页（首页 + 加载更多）+ 展开查看资料的卡片列表（首页 + 加载更多）+
 * 粘贴文本建资料（内联表单）+ 添加问答卡（弹窗表单）。
 * 契约 v1 未提供改卡/删卡端点（见 docs/review-api.md），故卡片仅支持新增与查看；
 * 任一写入成功后页面侧向队列页发数据变更信号（新卡进入明日/今日队列）。
 */
@HiltViewModel
class MaterialListViewModel @Inject constructor(
    private val repository: ReviewRepository,
) : ViewModel() {

    data class UiState(
        // ---------- 资料列表 ----------
        val loadState: RequestState = RequestState.Idle,
        val materials: List<MaterialVo> = emptyList(),
        val total: Long = 0L,
        val loadMoreState: RequestState = RequestState.Idle,
        // ---------- 展开的卡片区 ----------
        val expandedMaterialId: Long? = null,
        val cards: List<ReviewCardVo> = emptyList(),
        val cardsState: RequestState = RequestState.Idle,
        val cardsTotal: Long = 0L,
        val cardsHasMore: Boolean = false,
        // ---------- 建资料内联表单 ----------
        val createFormVisible: Boolean = false,
        val formTitle: String = "",
        val formRawText: String = "",
        val createState: RequestState = RequestState.Idle,
        // ---------- 建卡弹窗 ----------
        val cardDialogMaterialId: Long? = null,
        val dialogQuestion: String = "",
        val dialogAnswer: String = "",
        val addCardState: RequestState = RequestState.Idle,
    ) {
        /** 资料是否还有下一页 */
        val materialsHasMore: Boolean get() = materials.size.toLong() < total
        /** 当前展开资料的标题（弹窗副标题展示） */
        val expandedMaterial: MaterialVo?
            get() = materials.firstOrNull { it.id == expandedMaterialId }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    // ---------- 资料列表 ----------

    fun load() {
        // 已有列表的重拉（建资料成功后刷新）：保留旧列表静默刷新，避免整页骨架闪切（审查矩阵 #6）；
        // 静默刷新失败同样保留旧列表（建资料已成功并 toast，重拉失败不推翻页面）
        val isRefresh = _state.value.materials.isNotEmpty()
        viewModelScope.launch {
            if (!isRefresh) {
                _state.update { it.copy(loadState = RequestState.Loading) }
            }
            try {
                val data = repository.pageMaterials(page = 1, size = ReviewConstant.PAGE_SIZE)
                _state.update {
                    it.copy(loadState = RequestState.Success, materials = data.records, total = data.total)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "MaterialListViewModel.load: ${e.message}", e)
                if (!isRefresh) {
                    _state.update { it.copy(loadState = RequestState.Error(e.resId, e.message)) }
                }
            } catch (e: Exception) {
                AppLogger.error(TAG, "MaterialListViewModel.load: ${e.message}", e)
                if (!isRefresh) {
                    _state.update { it.copy(loadState = RequestState.Error(R.string.review_error_load)) }
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadMoreState is RequestState.Loading || !s.materialsHasMore) return
        val nextPage = s.materials.size / ReviewConstant.PAGE_SIZE + 1
        viewModelScope.launch {
            _state.update { it.copy(loadMoreState = RequestState.Loading) }
            try {
                val data = repository.pageMaterials(page = nextPage, size = ReviewConstant.PAGE_SIZE)
                _state.update {
                    it.copy(
                        materials = it.materials + data.records,
                        total = data.total,
                        loadMoreState = RequestState.Idle,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "MaterialListViewModel.loadMore: ${e.message}", e)
                _state.update { it.copy(loadMoreState = RequestState.Error(e.resId, e.message)) }
            } catch (e: Exception) {
                AppLogger.error(TAG, "MaterialListViewModel.loadMore: ${e.message}", e)
                _state.update { it.copy(loadMoreState = RequestState.Error(R.string.review_error_load_more)) }
            }
        }
    }

    // ---------- 展开卡片列表 ----------

    /** 展开/收起某份资料的卡片列表（展开即拉卡片首页） */
    fun toggleExpand(materialId: Long) {
        if (_state.value.expandedMaterialId == materialId) {
            _state.update {
                it.copy(
                    expandedMaterialId = null,
                    cards = emptyList(),
                    cardsState = RequestState.Idle,
                    cardsTotal = 0L,
                    cardsHasMore = false,
                )
            }
        } else {
            _state.update {
                it.copy(
                    expandedMaterialId = materialId,
                    cards = emptyList(),
                    cardsState = RequestState.Loading,
                    cardsTotal = 0L,
                    cardsHasMore = false,
                )
            }
            loadCardsPage(materialId = materialId, page = 1, replace = true)
        }
    }

    fun loadMoreCards() {
        val s = _state.value
        val materialId = s.expandedMaterialId ?: return
        if (s.cardsState is RequestState.Loading || !s.cardsHasMore) return
        val nextPage = s.cards.size / ReviewConstant.PAGE_SIZE + 1
        loadCardsPage(materialId = materialId, page = nextPage, replace = false)
    }

    /** 展开区加载失败后的重试（重拉当前展开资料的卡片首页） */
    fun retryCards() {
        val materialId = _state.value.expandedMaterialId ?: return
        _state.update { it.copy(cardsState = RequestState.Loading) }
        loadCardsPage(materialId = materialId, page = 1, replace = true)
    }

    private fun loadCardsPage(materialId: Long, page: Int, replace: Boolean) {
        viewModelScope.launch {
            try {
                val data = repository.pageCards(
                    materialId = materialId,
                    page = page,
                    size = ReviewConstant.PAGE_SIZE,
                )
                _state.update {
                    if (it.expandedMaterialId != materialId) {
                        // 用户已切换展开对象，丢弃过期响应
                        it
                    } else {
                        it.copy(
                            cards = if (replace) data.records else it.cards + data.records,
                            cardsState = RequestState.Success,
                            cardsTotal = data.total,
                            cardsHasMore = page.toLong() * ReviewConstant.PAGE_SIZE < data.total,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "MaterialListViewModel.loadCardsPage: ${e.message}", e)
                _state.update {
                    if (it.expandedMaterialId == materialId) {
                        it.copy(cardsState = RequestState.Error(e.resId, e.message))
                    } else {
                        it
                    }
                }
            } catch (e: Exception) {
                AppLogger.error(TAG, "MaterialListViewModel.loadCardsPage: ${e.message}", e)
                _state.update {
                    if (it.expandedMaterialId == materialId) {
                        it.copy(cardsState = RequestState.Error(R.string.review_error_load))
                    } else {
                        it
                    }
                }
            }
        }
    }

    // ---------- 建资料（内联表单） ----------

    fun toggleCreateForm() {
        _state.update {
            it.copy(
                createFormVisible = !it.createFormVisible,
                createState = RequestState.Idle,
            )
        }
    }

    fun updateFormTitle(value: String) {
        _state.update { it.copy(formTitle = value) }
    }

    fun updateFormRawText(value: String) {
        _state.update { it.copy(formRawText = value) }
    }

    /** 提交建资料：客户端前置校验（对齐后端 AddMaterialFrom 上限），成功后清空表单并刷新列表 */
    fun submitCreateMaterial() {
        val s = _state.value
        if (s.createState is RequestState.Loading) return
        val title = s.formTitle.trim()
        val rawText = s.formRawText.trim()
        when {
            title.isEmpty() -> {
                _state.update { it.copy(createState = RequestState.Error(R.string.review_error_title_required)) }
                return
            }
            title.length > ReviewConstant.TITLE_MAX -> {
                _state.update { it.copy(createState = RequestState.Error(R.string.review_error_title_too_long)) }
                return
            }
            rawText.length > ReviewConstant.RAW_TEXT_MAX -> {
                _state.update { it.copy(createState = RequestState.Error(R.string.review_error_raw_too_long)) }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(createState = RequestState.Loading) }
            try {
                val materialId = repository.createMaterial(title = title, rawText = rawText.ifEmpty { null })
                AppLogger.info(TAG, "复习资料创建成功 materialId=$materialId rawText=${rawText != null}")
                _state.update {
                    it.copy(
                        createState = RequestState.Success,
                        createFormVisible = false,
                        formTitle = "",
                        formRawText = "",
                    )
                }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "MaterialListViewModel.submitCreateMaterial: ${e.message}", e)
                _state.update { it.copy(createState = RequestState.Error(e.resId, e.message)) }
            } catch (e: Exception) {
                AppLogger.error(TAG, "MaterialListViewModel.submitCreateMaterial: ${e.message}", e)
                _state.update { it.copy(createState = RequestState.Error(R.string.review_error_load)) }
            }
        }
    }

    /** 页面 toast 消费创建成功后复位 */
    fun consumeCreateState() {
        _state.update {
            if (it.createState is RequestState.Success || it.createState is RequestState.Error) {
                it.copy(createState = RequestState.Idle)
            } else {
                it
            }
        }
    }

    // ---------- 建卡弹窗 ----------

    fun showAddCardDialog(materialId: Long) {
        _state.update {
            it.copy(
                cardDialogMaterialId = materialId,
                dialogQuestion = "",
                dialogAnswer = "",
                addCardState = RequestState.Idle,
            )
        }
    }

    fun dismissAddCardDialog() {
        _state.update {
            it.copy(
                cardDialogMaterialId = null,
                dialogQuestion = "",
                dialogAnswer = "",
                addCardState = RequestState.Idle,
            )
        }
    }

    fun updateDialogQuestion(value: String) {
        _state.update { it.copy(dialogQuestion = value) }
    }

    fun updateDialogAnswer(value: String) {
        _state.update { it.copy(dialogAnswer = value) }
    }

    /** 提交建卡：前置校验（对齐后端 AddCardFrom 上限），成功后关弹窗并刷新展开资料的卡片列表 */
    fun submitAddCard() {
        val s = _state.value
        val materialId = s.cardDialogMaterialId ?: return
        if (s.addCardState is RequestState.Loading) return
        val question = s.dialogQuestion.trim()
        val answer = s.dialogAnswer.trim()
        when {
            question.isEmpty() -> {
                _state.update { it.copy(addCardState = RequestState.Error(R.string.review_error_question_required)) }
                return
            }
            question.length > ReviewConstant.QUESTION_MAX -> {
                _state.update { it.copy(addCardState = RequestState.Error(R.string.review_error_question_too_long)) }
                return
            }
            answer.isEmpty() -> {
                _state.update { it.copy(addCardState = RequestState.Error(R.string.review_error_answer_required)) }
                return
            }
            answer.length > ReviewConstant.ANSWER_MAX -> {
                _state.update { it.copy(addCardState = RequestState.Error(R.string.review_error_answer_too_long)) }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(addCardState = RequestState.Loading) }
            try {
                val cardId = repository.addCard(materialId = materialId, question = question, answer = answer)
                AppLogger.info(TAG, "问答卡创建成功 cardId=$cardId materialId=$materialId")
                _state.update {
                    it.copy(
                        addCardState = RequestState.Success,
                        cardDialogMaterialId = null,
                        dialogQuestion = "",
                        dialogAnswer = "",
                    )
                }
                // 该资料正展开则刷新其卡片首页；无论展开与否新卡都会进入队列（页面侧发变更信号）
                if (_state.value.expandedMaterialId == materialId) {
                    loadCardsPage(materialId = materialId, page = 1, replace = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "MaterialListViewModel.submitAddCard: ${e.message}", e)
                _state.update { it.copy(addCardState = RequestState.Error(e.resId, e.message)) }
            } catch (e: Exception) {
                AppLogger.error(TAG, "MaterialListViewModel.submitAddCard: ${e.message}", e)
                _state.update { it.copy(addCardState = RequestState.Error(R.string.review_error_load)) }
            }
        }
    }

    /** 页面 toast 消费建卡成功后复位（错误由弹窗内联展示，不经此复位） */
    fun consumeAddCardSuccess() {
        _state.update {
            if (it.addCardState is RequestState.Success) {
                it.copy(addCardState = RequestState.Idle)
            } else {
                it
            }
        }
    }

    private companion object {
        const val TAG = "MaterialListViewModel"
    }
}
