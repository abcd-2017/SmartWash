package com.smartwash.feature.dining.ui.order

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.model.CreateDiningOrderRequest
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.logic.DiningOrderFormValidator
import com.smartwash.feature.dining.logic.DiningOrderFormValidator.DiningOrderFormError
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** 选菜列表分页大小（后端 BaseSearchFrom 默认 10、上限 100；取 20 平衡首屏与请求数） */
private const val PAGE_SIZE = 20

/** 复用模式全量回显每页大小（只读展示源单勾选态，需按全量菜谱渲染，取上限 100 减少请求数） */
private const val FULL_LOAD_PAGE_SIZE = 100

/** 关键词搜索防抖时长（对齐菜谱库先例：输入停顿后再打请求） */
private const val KEYWORD_DEBOUNCE_MS = 300L

/**
 * 创建订单页 ViewModel：标题 + 按分类分组勾选公共库菜品（复用 T4 菜谱读取面——分类目录与
 * 上架菜谱分页直连域内 [DiningRepository]，页面均在 dining 域内无需跨域 DiningApi 扩展面）。
 *
 * 菜品按需分页策略：首载只取第 1 页（每页 20），触底 [loadMore] 续拉；加载更多失败不封底、
 * footer 内联重试。搜索关键词走服务端模糊查询，变更即重置分页（page=1）重拉。
 * 首载失败给整页错误态 + 重试（菜品是本页主流程，不静默降级）。
 * 复用历史菜单模式例外：只读回显源单勾选态需按全量菜谱渲染，仍走全量拉取（见
 * [loadAllRecipesFull]），不分页。
 *
 * 复用历史菜单模式（13 号票，路由参数 fromOrderId）：进入时拉取源单详情，把源单本单菜单
 * 预勾选并只读回显（[selectedRecipeIds] 锁定，[toggleRecipe] 忽略），提交携带 fromOrderId
 * 由后端整份复制菜单（见 [DiningOrderFormValidator.validateReuse]）。
 *
 * 状态划分：[loadState]（分类+菜谱首载）/ [submitState]（提交动作态，Loading 防重）独立；
 * [formError] 为端上校验首错（提交时设置，随输入清除）——规则锚点见 [DiningOrderFormValidator]
 * JVM 单测（标题必填 / 至少选一道菜 / 复用模式只校验标题）。
 */
@HiltViewModel
class DiningOrderCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diningRepository: DiningRepository,
    private val application: Application,
) : ViewModel() {

    /** 复用模式源订单 ID（路由参数；>0 才生效，与后端 fromOrderId 契约同源） */
    private val reuseFromOrderId: Long? =
        savedStateHandle.get<Long>(ARG_FROM_ORDER_ID)?.takeIf { it > 0L }

    /** 是否复用历史菜单模式（源单菜单只读，提交走 fromOrderId） */
    val isReuseMode: Boolean get() = reuseFromOrderId != null

    /** 复用模式源单标题（入口顺带下发优先，详情拉取后兜底自取） */
    private val _sourceTitle = MutableStateFlow(savedStateHandle.get<String>(ARG_SOURCE_TITLE).orEmpty())
    val sourceTitle = _sourceTitle.asStateFlow()

    /** 复用模式源单菜单菜数（详情拉取后回填，供提示文案展示） */
    private val _sourceMenuCount = MutableStateFlow(0)
    val sourceMenuCount = _sourceMenuCount.asStateFlow()

    /** 点单标题（输入经 onTitleChange 回写，单向下发） */
    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    /** 分类目录（分组渲染 + 未分类组命名；失败仅在菜谱首载主流程里合并报错） */
    private val _categories = MutableStateFlow<List<DiningCategoryVo>>(emptyList())
    val categories = _categories.asStateFlow()

    /** 上架菜谱已加载部分（新菜谱在前，保序分组渲染；按需分页追加） */
    private val _recipes = MutableStateFlow<List<DiningRecipeVo>>(emptyList())
    val recipes = _recipes.asStateFlow()

    /** 是否还有下一页（累计已加载条数与 total 比较） */
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    /** 加载更多独立态（失败不封底，footer 内联重试） */
    private val _loadMoreState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadMoreState = _loadMoreState.asStateFlow()

    /** 搜索关键词（菜名模糊，空串=全库；服务端分页查询，变更即重置分页） */
    private val _keyword = MutableStateFlow("")
    val keyword = _keyword.asStateFlow()

    private var currentPage = 1

    /** 菜谱拉取串行锁：首载/搜索重置与加载更多共用，防并发响应交错覆盖 */
    private val fetchMutex = Mutex()

    /** 关键词搜索防抖任务（新输入取消上一次） */
    private var keywordJob: Job? = null

    /** 已勾选菜谱 ID 集合（无序集合；提交时按分组顺序展开保序提交） */
    private val _selectedRecipeIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRecipeIds = _selectedRecipeIds.asStateFlow()

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _submitState = MutableStateFlow<RequestState>(RequestState.Idle)
    val submitState = _submitState.asStateFlow()

    /**
     * 创建成功结果（orderId + joinToken + 归一化标题）：token 是端上唯一凭证来源，
     * 一次性交付给页面跳分享视图（consumeCreatedResult 取走即清，避免成功态重放携带旧值）。
     */
    class CreatedOrder(val orderId: Long, val title: String, val joinToken: String)

    private var createdResult: CreatedOrder? = null

    /** 端上校验首错（提交时设置；对应字段输入变化即清除） */
    private val _formError = MutableStateFlow<DiningOrderFormError?>(null)
    val formError = _formError.asStateFlow()

    /** 表单错误 → 文案 res（UI 层映射，纯逻辑枚举不持有文案） */
    fun errorRes(error: DiningOrderFormError): Int = when (error) {
        DiningOrderFormError.TITLE_REQUIRED -> R.string.dining_order_error_title_required
        DiningOrderFormError.TITLE_TOO_LONG -> R.string.dining_order_error_title_too_long
        DiningOrderFormError.MENU_EMPTY -> R.string.dining_order_error_menu_empty
        DiningOrderFormError.MENU_TOO_MANY -> R.string.dining_order_error_menu_too_many
    }

    /** 首载：分类目录 + 菜谱第 1 页（任一失败进错误态——分类名缺失会让分组残缺）。
     * 常规模式按需分页（[loadMore] 触底续拉）；复用模式需按全量菜谱回显源单勾选态，
     * 仍走全量拉取（见 [loadAllRecipesFull]），并追加拉取源单详情预勾选其菜单
     * （源单须为当前用户成员，否则后端拒绝） */
    fun load() {
        if (_loadState.value is RequestState.Loading) return
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                loadCategoriesSafe()
                val reuseId = reuseFromOrderId
                if (reuseId != null) {
                    loadAllRecipesFull()
                    applyReusedMenu(reuseId)
                } else {
                    fetchFirstPage()
                }
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderCreateViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 关键词输入：防抖 300ms 重置分页重拉首页（空串=全库；结果集变化即 page=1 重取）。
     * 复用模式不渲染搜索框，此处兜底忽略，避免只读回显被分页数据替换 */
    fun onKeywordChange(value: String) {
        _keyword.value = value
        if (reuseFromOrderId != null) return
        keywordJob?.cancel()
        keywordJob = viewModelScope.launch {
            delay(KEYWORD_DEBOUNCE_MS)
            _loadState.value = RequestState.Loading
            try {
                fetchFirstPage()
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "onKeywordChange: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /**
     * 复用模式：拉取源单详情，标题兜底回填 + 菜单预勾选（只读回显，提交不传 recipeIds）。
     * 源单不存在/非成员/已无上架菜由后端错误经 NetworkException 上抛，整页进错误态。
     */
    private suspend fun applyReusedMenu(fromOrderId: Long) {
        val detail = diningRepository.getOrderDetail(fromOrderId)
        _sourceTitle.value = detail.safeTitle.ifBlank { _sourceTitle.value }
        _sourceMenuCount.value = detail.safeMenu.size
        _selectedRecipeIds.value = detail.safeMenu.map { it.safeRecipeId }.filter { it > 0L }.toSet()
    }

    /**
     * 分类目录拉取：分类是分组命名依据，失败随主流程上抛（进入整页错误态）。
     */
    private suspend fun loadCategoriesSafe() {
        _categories.value = diningRepository.getCategories()
    }

    /** 首页拉取（关键词 + 分页基准；互斥串行，成功后才推进页码） */
    private suspend fun fetchFirstPage() = fetchMutex.withLock {
        val page = diningRepository.getRecipes(1, PAGE_SIZE, keyword = currentKeyword())
        currentPage = 1
        _recipes.value = page.records
        _hasMore.value = _recipes.value.size < page.total
        _loadMoreState.value = RequestState.Idle
    }

    /** 加载更多（防重：加载中/无更多/首载搜索中直接忽略；失败不封底可重试） */
    fun loadMore() {
        if (_loadMoreState.value is RequestState.Loading || !_hasMore.value) return
        if (_loadState.value is RequestState.Loading) return
        viewModelScope.launch {
            _loadMoreState.value = RequestState.Loading
            try {
                fetchNextPage()
                _loadMoreState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "loadMore: ${e.message}", e)
                _loadMoreState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 追加下一页（互斥串行；页码与 total 以服务端当页响应为准） */
    private suspend fun fetchNextPage() = fetchMutex.withLock {
        val nextPage = currentPage + 1
        val page = diningRepository.getRecipes(nextPage, PAGE_SIZE, keyword = currentKeyword())
        currentPage = nextPage
        _recipes.value = _recipes.value + page.records
        _hasMore.value = _recipes.value.size < page.total
    }

    /** 当前搜索关键词（空白按不过滤处理，对齐后端 null 语义） */
    private fun currentKeyword(): String? = _keyword.value.trim().ifBlank { null }

    /**
     * 复用模式只读回显：源单勾选态需按全量菜谱渲染，仍循环取满（每页 100，上限
     * [MAX_PAGE_COUNT] 防御后端 total 异常导致的死循环）；常规选菜走 [fetchFirstPage]/[loadMore]。
     */
    private suspend fun loadAllRecipesFull() {
        val collected = mutableListOf<DiningRecipeVo>()
        var page = 1
        while (page <= MAX_PAGE_COUNT) {
            val pageData = diningRepository.getRecipes(page, FULL_LOAD_PAGE_SIZE)
            collected += pageData.records
            if (pageData.records.size < FULL_LOAD_PAGE_SIZE || collected.size >= pageData.total) break
            page++
        }
        _recipes.value = collected
        _hasMore.value = false
        _loadMoreState.value = RequestState.Idle
    }

    fun onTitleChange(value: String) {
        _title.value = value
        clearFormError()
    }

    /** 勾选/取消一道菜（上限校验在提交时统一做，勾选过程不打断；复用模式菜单只读忽略） */
    fun toggleRecipe(recipeId: Long) {
        if (isReuseMode) return
        if (recipeId <= 0L) return
        _selectedRecipeIds.value = _selectedRecipeIds.value.toMutableSet().apply {
            if (!add(recipeId)) remove(recipeId)
        }
    }

    /** 提交（端上校验先行；Loading 期间防重）。成功由页面消费后进分享视图 */
    fun submit() {
        if (_submitState.value is RequestState.Loading) return
        // 复用模式只校验标题（菜单由后端按 fromOrderId 整份复制）；常规模式校验标题 + 菜单
        val reuseFromOrderId = this.reuseFromOrderId
        val validation: Boolean
        val normalizedTitle: String
        val formError: DiningOrderFormError?
        val recipeIds: List<Long>?
        if (reuseFromOrderId != null) {
            val reuseValidation = DiningOrderFormValidator.validateReuse(_title.value)
            validation = reuseValidation.valid
            normalizedTitle = reuseValidation.normalized
            formError = reuseValidation.error
            recipeIds = null
        } else {
            val normalValidation = DiningOrderFormValidator.validate(
                title = _title.value,
                recipeIds = orderedSelectedRecipeIds(),
            )
            validation = normalValidation.valid
            normalizedTitle = normalValidation.normalizedTitle
            formError = normalValidation.error
            recipeIds = normalValidation.normalizedRecipeIds
        }
        if (!validation) {
            _formError.value = formError
            return
        }
        _formError.value = null
        _submitState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val created = diningRepository.createOrder(
                    CreateDiningOrderRequest(
                        title = normalizedTitle,
                        recipeIds = recipeIds,
                        fromOrderId = reuseFromOrderId,
                    )
                )
                AppLogger.info(TAG, "点单创建成功 orderId=${created.safeOrderId} 菜品数=${recipeIds?.size ?: _sourceMenuCount.value}")
                createdResult = CreatedOrder(
                    orderId = created.safeOrderId,
                    title = normalizedTitle,
                    joinToken = created.safeJoinToken,
                )
                _submitState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "submit: ${e.message}", e)
                _submitState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 一次性取走创建成功结果（取后即清——成功态重放不携带旧值）；orderId 非法视为异常结果 */
    fun consumeCreatedResult(): CreatedOrder? {
        val result = createdResult
        createdResult = null
        return result?.takeIf { it.orderId > 0L && it.joinToken.isNotBlank() }
    }

    /**
     * 已选菜谱按分组顺序展开（保序提交：后端去重保序写入本单菜单，分组勾选顺序即菜单顺序）。
     */
    fun orderedSelectedRecipeIds(): List<Long> {
        val selected = _selectedRecipeIds.value
        if (selected.isEmpty()) return emptyList()
        val ordered = mutableListOf<Long>()
        _recipes.value.forEach { recipe ->
            val id = recipe.safeId
            if (id in selected && id > 0L && id !in ordered) ordered += id
        }
        // 已加载列表外的选中防御：不在已加载菜谱里的选中 ID 追加在尾（不丢用户选择）
        selected.filterNot { it in ordered }.sorted().let { ordered += it }
        return ordered
    }

    /** 对应字段输入变化即清除校验错误（就近提示，不打扰其他字段） */
    fun clearFormError() {
        _formError.value = null
    }

    fun resetSubmitState() {
        _submitState.value = RequestState.Idle
    }

    private companion object {
        const val TAG = "DiningOrderCreateViewModel"

        /** 复用模式全量拉取循环防御上限（100×100=1 万道菜，远超现实量级；触发即数据异常） */
        const val MAX_PAGE_COUNT = 100

        /** 类型安全路由参数键（DiningRoute.OrderCreate 属性名即键名） */
        const val ARG_FROM_ORDER_ID = "fromOrderId"
        const val ARG_SOURCE_TITLE = "sourceTitle"
    }
}
