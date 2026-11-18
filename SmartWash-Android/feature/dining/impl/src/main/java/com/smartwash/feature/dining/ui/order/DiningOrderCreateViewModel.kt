package com.smartwash.feature.dining.ui.order

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.model.AddDiningOrderDishRequest
import com.smartwash.feature.dining.api.model.CreateDiningOrderRequest
import com.smartwash.feature.dining.api.model.DietaryPreferenceVo
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.api.model.PublishDiningOrderRequest
import com.smartwash.feature.dining.logic.DiningAllergenGuard
import com.smartwash.feature.dining.logic.DiningDraftMenuEditor
import com.smartwash.feature.dining.logic.DiningJoinUrlBuilder
import com.smartwash.feature.dining.logic.DiningNutritionCalculator
import com.smartwash.feature.dining.logic.DiningOrderFormValidator
import com.smartwash.feature.dining.logic.DiningOrderFormValidator.DiningOrderFormError
import com.smartwash.feature.dining.logic.DiningVersionConflictException
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
 * 创建订单页 ViewModel：标题 + 左分类栏按分类勾选公共库菜品（契约 §5.2 美团式双栏，
 * 复用 T4 菜谱读取面——分类目录与上架菜谱分页直连域内 [DiningRepository]，页面均在
 * dining 域内无需跨域 DiningApi 扩展面）。
 *
 * 菜品按需分页策略（性能硬约束，保留服务端分页 + 懒加载）：首载只取第 1 页（每页 20），
 * 触底 [loadMore] 续拉；左栏选中分类 → 服务端按分类过滤分页（[selectCategory] 换分类即
 * 重置分页）；加载更多失败不封底、footer 内联重试。搜索关键词走服务端模糊查询，变更即
 * 重置分页（page=1）重拉。首载失败给整页错误态 + 重试（菜品是本页主流程，不静默降级）。
 * 已选集合（[selectedRecipeIds]）跨分页/跨分类持久持有，左栏徽标只统计本地已选数。
 * 复用历史菜单模式例外：只读回显源单勾选态需按全量菜谱渲染，仍走全量拉取（见
 * [loadAllRecipesFull]），不分页、无分类栏。
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

    /** 草稿编辑模式订单 ID（路由参数；>0 时编辑既有 DRAFT，跳过惰性创建） */
    private val editOrderId: Long? =
        savedStateHandle.get<Long>(ARG_ORDER_ID)?.takeIf { it > 0L }

    /** 是否复用历史菜单模式（源单菜单只读，提交走 fromOrderId） */
    val isReuseMode: Boolean get() = reuseFromOrderId != null

    /** 是否草稿编辑模式（加载既有 DRAFT，加菜/移除走 dishes 接口、CTA 定稿） */
    val isDraftEditMode: Boolean get() = editOrderId != null

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

    /**
     * 左栏选中分类（null=全部，契约 §5.2）：服务端按分类过滤分页（换分类即重置分页，
     * 保留懒加载）；已选集合跨分类持久持有（[selectedRecipeIds] 不随分类切换清空）。
     */
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    private var currentPage = 1

    /** 菜谱拉取串行锁：首载/搜索重置与加载更多共用，防并发响应交错覆盖 */
    private val fetchMutex = Mutex()

    /** 关键词搜索防抖任务（新输入取消上一次） */
    private var keywordJob: Job? = null

    /** 已勾选菜谱 ID 集合（无序集合；提交时按分组顺序展开保序提交） */
    private val _selectedRecipeIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRecipeIds = _selectedRecipeIds.asStateFlow()

    /**
     * 已加载菜谱的分类注册表（recipeId → 分类 ID，null=未分类）：跨分类切换/分页累计，
     * 供左栏「该分类已选数」徽标统计（契约 §5.2：徽标只统计本地已选数，无网络成本）。
     */
    private val _recipeCategories = MutableStateFlow<Map<Long, Long?>>(emptyMap())
    val recipeCategories = _recipeCategories.asStateFlow()

    /**
     * 已勾选菜谱的 VO 快照（recipeId → VO，随勾选/加载回填维护）：营养超标警示条的数据源，
     * 分类切换导致分页列表重置后仍可对全部已选菜做营养聚合（不丢已选菜的口径）。
     */
    private val _selectedRecipes = MutableStateFlow<Map<Long, DiningRecipeVo>>(emptyMap())
    val selectedRecipes = _selectedRecipes.asStateFlow()

    /**
     * 用户忌口标签（契约 §三.1 忌口警示数据源之一）：首载拉取偏好后回填，失败静默置空
     * （无忌口则不渲染警示，不误报）。
     */
    private val _userAllergens = MutableStateFlow<Set<String>>(emptySet())
    val userAllergens = _userAllergens.asStateFlow()

    /**
     * 已选菜 allergens 缓存（recipeId → 详情接口返回的过敏原码）：列表 VO 不带 allergens，
     * 选中时按需拉详情回填，跨分页/换分类保留（[DiningAllergenGuard] 的输入）。
     */
    private val _allergenById = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val allergenById = _allergenById.asStateFlow()

    /** 详情拉取任务（去重：同一道菜并发选中只发一次请求） */
    private val allergenLoadJobs = mutableMapOf<Long, Job>()

    /** 每日热量目标（默认 2000；偏好拉取后回填，份量建议参考值 = 目标 ÷ 3） */
    private val _calorieTarget = MutableStateFlow(DietaryPreferenceVo.DEFAULT_CALORIE_TARGET)
    val calorieTarget = _calorieTarget.asStateFlow()

    /** 发起页人数（1-20，本地态不落库；默认 1） */
    private val _peopleCount = MutableStateFlow(DiningNutritionCalculator.PEOPLE_MIN)
    val peopleCount = _peopleCount.asStateFlow()

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

    // ==================== 草稿协同（DRAFT 惰性创建 + 菜单写操作） ====================

    /** 草稿菜单编辑状态机（纯 Kotlin）：身份/服务端真相/本地意图与 version 乐观锁 */
    private val draftMenu = DiningDraftMenuEditor()

    /** 草稿写操作串行锁：创建 + 加菜/移除 + publish 共用，保证 version 基准始终最新 */
    private val draftWriteMutex = Mutex()

    /** 邀请链接一次性事件（页面复制到剪贴板后 consume；null=无待处理） */
    private val _inviteLink = MutableStateFlow<String?>(null)
    val inviteLink = _inviteLink.asStateFlow()

    /** 草稿写操作一次性提示（页面 Toast 后 consume；null=无待提示） */
    private val _draftMessage = MutableStateFlow<String?>(null)
    val draftMessage = _draftMessage.asStateFlow()

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
                loadPreferenceSafe()
                val editId = editOrderId
                val reuseId = reuseFromOrderId
                when {
                    // 草稿编辑模式：常规分页选菜列表 + 既有草稿详情（标题/菜单/version/凭证）
                    editId != null -> {
                        fetchFirstPage()
                        applyDraftDetail(editId)
                    }
                    reuseId != null -> {
                        loadAllRecipesFull()
                        applyReusedMenu(reuseId)
                    }
                    else -> fetchFirstPage()
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
     * 切换左栏分类（服务端按分类过滤 + 重置分页，契约 §5.2；防重：加载中忽略）。
     * 已选集合不清空——跨分类勾选由 [selectedRecipeIds] 持久持有；复用模式菜单只读忽略。
     */
    fun selectCategory(categoryId: Long?) {
        if (reuseFromOrderId != null) return
        if (_selectedCategoryId.value == categoryId) return
        if (_loadState.value is RequestState.Loading) return
        _selectedCategoryId.value = categoryId
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                fetchFirstPage()
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "selectCategory: ${e.message}", e)
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
     * 草稿编辑模式：加载既有 DRAFT，回填标题/菜单/version/加入凭证。
     * 菜单即本地意图（首载无未决写），已加载列表内的菜回填 VO 快照供营养聚合。
     */
    private suspend fun applyDraftDetail(orderId: Long) {
        val detail = diningRepository.getOrderDetail(orderId)
        draftMenu.markCreated(orderId = detail.safeId, joinToken = detail.safeJoinToken)
        val menu = detail.safeMenu.map { it.safeRecipeId }.filter { it > 0L }.toSet()
        draftMenu.onInitialSnapshot(detail.safeVersion, menu)
        _title.value = detail.safeTitle
        _selectedRecipeIds.value = menu
        _selectedRecipes.value = _recipes.value.filter { it.safeId in menu }.associateBy { it.safeId }
    }

    /**
     * 分类目录拉取：分类是分组命名依据，失败随主流程上抛（进入整页错误态）。
     */
    private suspend fun loadCategoriesSafe() {
        _categories.value = diningRepository.getCategories()
    }

    /**
     * 饮食偏好拉取（契约 §三.1/§2.5）：忌口 + 热量目标。失败静默降级（忌口空集、目标默认），
     * 不拖垮点单主流程。
     */
    private suspend fun loadPreferenceSafe() {
        try {
            val preference = diningRepository.getDietaryPreference()
            _userAllergens.value = DiningAllergenGuard.normalizeAllergens(preference.safeAllergens).toSet()
            _calorieTarget.value = preference.calorieTarget
                ?.takeIf { it > 0 }
                ?: DietaryPreferenceVo.DEFAULT_CALORIE_TARGET
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "饮食偏好拉取失败（降级无忌口/默认目标）：${e.message}")
        }
    }

    /**
     * 按需拉取选中菜的详情以缓存 allergens（契约 §三.1：列表不带 allergens，选中时按需拉详情）。
     * 同一道菜并发选中只发一次；失败按「无过敏原」处理（不误报）。
     */
    private fun ensureAllergens(recipeId: Long) {
        if (recipeId <= 0L) return
        if (_allergenById.value.containsKey(recipeId)) return
        if (allergenLoadJobs[recipeId]?.isActive == true) return
        allergenLoadJobs[recipeId] = viewModelScope.launch {
            try {
                val detail = diningRepository.getRecipeDetail(recipeId)
                _allergenById.value = _allergenById.value + (recipeId to detail.safeAllergens)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "选中菜过敏原拉取失败（按无过敏原处理）：${e.message}")
            } finally {
                allergenLoadJobs.remove(recipeId)
            }
        }
    }

    /** 人数 stepper（1-20，本地态；越界收敛） */
    fun onPeopleChange(value: Int) {
        _peopleCount.value = DiningNutritionCalculator.coercePeopleCount(value)
    }

    /** 首页拉取（关键词 + 分类 + 分页基准；互斥串行，成功后才推进页码） */
    private suspend fun fetchFirstPage() = fetchMutex.withLock {
        val page = diningRepository.getRecipes(
            1, PAGE_SIZE, effectiveCategoryId(), keyword = currentKeyword(),
        )
        currentPage = 1
        _recipes.value = page.records
        _hasMore.value = _recipes.value.size < page.total
        _loadMoreState.value = RequestState.Idle
        recordCatalog(page.records)
    }

    /**
     * 生效分类筛选（契约 §5.2）：关键词搜索态为全库检索（分类暂不参与，UI 同步隐藏分类栏）；
     * 关键词清空后恢复分类过滤。选中分类保留在本地态，不因搜索被清除。
     */
    private fun effectiveCategoryId(): Long? =
        if (_keyword.value.trim().isNotBlank()) null else _selectedCategoryId.value

    /** 回填分类注册表与已选菜谱快照（分页/换分类后徽标与营养聚合不丢已选项；categoryId 0 归并为 null=未分类） */
    private fun recordCatalog(records: List<DiningRecipeVo>) {
        _recipeCategories.value = _recipeCategories.value +
            records.associate { it.safeId to it.categoryId?.takeIf { categoryId -> categoryId > 0L } }
        val selected = _selectedRecipeIds.value
        if (selected.isNotEmpty()) {
            _selectedRecipes.value = _selectedRecipes.value +
                records.filter { it.safeId in selected }.associateBy { it.safeId }
        }
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
        val page = diningRepository.getRecipes(
            nextPage, PAGE_SIZE, effectiveCategoryId(), keyword = currentKeyword(),
        )
        currentPage = nextPage
        _recipes.value = _recipes.value + page.records
        _hasMore.value = _recipes.value.size < page.total
        recordCatalog(page.records)
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
        recordCatalog(collected)
    }

    fun onTitleChange(value: String) {
        _title.value = value
        clearFormError()
    }

    /**
     * 勾选/取消一道菜（上限校验在提交时统一做，勾选过程不打断；复用模式菜单只读忽略）。
     * 勾选时从已加载列表回填 VO 快照（营养警示数据源），取消时移除。
     *
     * 草稿协同：常规模式即「草稿菜单」——本地意图即时更新，异步经 dishes 接口落库：
     * 未落库时先惰性创建（空草稿也可），之后加菜/移除走 dishes（version 乐观锁，409 刷新重试）。
     */
    fun toggleRecipe(recipeId: Long) {
        if (isReuseMode) return
        if (recipeId <= 0L) return
        val willSelect = recipeId !in _selectedRecipeIds.value
        val vo = _recipes.value.firstOrNull { it.safeId == recipeId }
        if (willSelect) {
            if (vo != null) {
                ensureAllergens(recipeId)
                _selectedRecipes.value = _selectedRecipes.value + (recipeId to vo)
            }
        } else {
            _selectedRecipes.value = _selectedRecipes.value - recipeId
        }
        draftMenu.onToggle(recipeId)
        _selectedRecipeIds.value = draftMenu.localMenu
        ensureDraftAndFlush()
    }

    /** 清空已选（购物车栏「清空」，二次确认由页面承担；复用模式菜单只读忽略） */
    fun clearSelections() {
        if (isReuseMode) return
        draftMenu.onClear()
        _selectedRecipeIds.value = draftMenu.localMenu
        _selectedRecipes.value = emptyMap()
        ensureDraftAndFlush()
    }

    /**
     * 发起点单（端上校验先行；Loading 期间防重）。成功由页面消费后进分享视图。
     *
     * 草稿协同：
     * - 常规/草稿编辑模式：校验标题必填 + 菜单非空 → 确保草稿已落库并冲刷待写菜单 →
     *   publish（version 乐观锁；409 时刷新草稿并重放一次）；
     * - 复用模式：标题必填 → create(fromOrderId) 复制菜单 → 取 version → publish。
     */
    fun submit() {
        if (_submitState.value is RequestState.Loading) return
        val reuseFromOrderId = this.reuseFromOrderId
        val normalizedTitle: String
        val formError: DiningOrderFormError?
        if (reuseFromOrderId != null) {
            val reuseValidation = DiningOrderFormValidator.validateReuse(_title.value)
            if (!reuseValidation.valid) {
                _formError.value = reuseValidation.error
                return
            }
            normalizedTitle = reuseValidation.normalized
            formError = null
        } else {
            val validation = DiningOrderFormValidator.validate(
                title = _title.value,
                recipeIds = orderedSelectedRecipeIds(),
            )
            if (!validation.valid) {
                _formError.value = validation.error
                return
            }
            normalizedTitle = validation.normalizedTitle
            formError = null
        }
        _formError.value = formError
        _submitState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val created = if (reuseFromOrderId != null) {
                    publishReusedOrder(reuseFromOrderId, normalizedTitle)
                } else {
                    publishDraft(normalizedTitle)
                }
                AppLogger.info(TAG, "发起点单成功 orderId=${created.orderId} 标题=$normalizedTitle")
                createdResult = created
                _submitState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "submit: ${e.message}", e)
                _submitState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 常规/草稿编辑模式：确保草稿落库 + 冲刷待写菜单后 publish（409 刷新重放一次） */
    private suspend fun publishDraft(normalizedTitle: String): CreatedOrder {
        draftWriteMutex.withLock {
            if (!draftMenu.isCreated) {
                createDraft()
                // create 已携带当前菜单，冲刷后本地即服务端真相
                flushDraftWrites()
            } else {
                flushDraftWrites()
            }
        }
        return try {
            diningRepository.publishOrder(
                draftMenu.orderId,
                PublishDiningOrderRequest(title = normalizedTitle, version = draftMenu.serverVersion),
            )
            CreatedOrder(draftMenu.orderId, normalizedTitle, draftMenu.joinToken)
        } catch (e: DiningVersionConflictException) {
            AppLogger.info(TAG, "publish 版本冲突，刷新草稿后重放 orderId=${draftMenu.orderId}")
            if (!refreshDraftMenu()) throw e
            // 服务端菜单可能被并发清空：publish 前二次校验非空（端上友好提示）
            if (draftMenu.serverMenu.isEmpty()) {
                throw NetworkException(
                    application.getString(R.string.dining_order_error_menu_empty),
                    R.string.dining_order_error_menu_empty,
                )
            }
            diningRepository.publishOrder(
                draftMenu.orderId,
                PublishDiningOrderRequest(title = normalizedTitle, version = draftMenu.serverVersion),
            )
            CreatedOrder(draftMenu.orderId, normalizedTitle, draftMenu.joinToken)
        }
    }

    /** 复用模式：create(fromOrderId) 复制历史菜单 → 取服务端 version → publish 定稿 */
    private suspend fun publishReusedOrder(fromOrderId: Long, normalizedTitle: String): CreatedOrder {
        val created = diningRepository.createOrder(
            CreateDiningOrderRequest(title = normalizedTitle, recipeIds = null, fromOrderId = fromOrderId),
        )
        val detail = diningRepository.getOrderDetail(created.safeOrderId)
        diningRepository.publishOrder(
            created.safeOrderId,
            PublishDiningOrderRequest(title = normalizedTitle, version = detail.safeVersion),
        )
        return CreatedOrder(created.safeOrderId, normalizedTitle, created.safeJoinToken)
    }

    /** 惰性创建草稿：携带当前标题与本地菜单（空也可），落库后服务端真相即本地菜单 */
    private suspend fun createDraft() {
        val menu = draftMenu.localMenu
        val created = diningRepository.createOrder(
            CreateDiningOrderRequest(
                title = _title.value.trim(),
                recipeIds = menu.toList(),
                fromOrderId = null,
            ),
        )
        draftMenu.markCreated(orderId = created.safeOrderId, joinToken = created.safeJoinToken)
        // create 已携带创建瞬间的菜单（version=0）；API 在途期间的新切换保留在本地意图里，
        // 由 onServerSnapshot 收敛后继续冲刷（避免覆盖并发本地编辑）
        draftMenu.onServerSnapshot(version = 0L, menu = menu)
        AppLogger.info(TAG, "惰性创建草稿 orderId=${created.safeOrderId} 菜品数=${menu.size}")
    }

    /**
     * 触发草稿写冲刷（防重：无待写且未落库不创建；串行锁内执行）。
     * 未落库且有待写 → 先惰性创建；已落库 → 循环冲刷差分直到本地意图与服务端一致。
     */
    private fun ensureDraftAndFlush() {
        viewModelScope.launch {
            draftWriteMutex.withLock {
                try {
                    if (!draftMenu.isCreated) {
                        if (draftMenu.pendingOp() == null) return@withLock
                        createDraft()
                    }
                    flushDraftWrites()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DiningVersionConflictException) {
                    // flush 内部已做一次刷新重放；仍冲突则交下一次操作/轮询自愈
                    AppLogger.warn(TAG, "草稿写版本冲突未收敛: ${e.message}")
                    _draftMessage.value = e.message
                } catch (e: NetworkException) {
                    AppLogger.error(TAG, "草稿写失败: ${e.message}", e)
                    _draftMessage.value = e.message
                } catch (e: Exception) {
                    AppLogger.error(TAG, "草稿写未知失败", e)
                    _draftMessage.value = application.getString(R.string.dining_order_create_write_failed)
                }
            }
        }
    }

    /** 冲刷草稿菜单差分（调用方持串行锁）：409 刷新后重放，直至本地意图与服务端一致 */
    private suspend fun flushDraftWrites() {
        while (true) {
            val op = draftMenu.pendingOp() ?: return
            try {
                if (op.add) {
                    diningRepository.addOrderDish(
                        draftMenu.orderId,
                        AddDiningOrderDishRequest(recipeId = op.recipeId, version = draftMenu.serverVersion),
                    )
                } else {
                    diningRepository.removeOrderDish(draftMenu.orderId, op.recipeId, draftMenu.serverVersion)
                }
                draftMenu.onWriteSuccess(op)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DiningVersionConflictException) {
                AppLogger.info(TAG, "草稿写版本冲突，刷新后重放 recipeId=${op.recipeId}")
                if (!refreshDraftMenu()) return
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "草稿写失败 recipeId=${op.recipeId}: ${e.message}", e)
                draftMenu.onWriteFailure(op)
                _draftMessage.value = e.message
                return
            } catch (e: Exception) {
                AppLogger.error(TAG, "草稿写未知失败 recipeId=${op.recipeId}", e)
                draftMenu.onWriteFailure(op)
                _draftMessage.value = application.getString(R.string.dining_order_create_write_failed)
                return
            }
        }
    }

    /** 拉取草稿最新详情刷新服务端真相（保留本地意图，差分在 [flushDraftWrites] 重放） */
    private suspend fun refreshDraftMenu(): Boolean {
        if (draftMenu.orderId <= 0L) return false
        return try {
            val detail = diningRepository.getOrderDetail(draftMenu.orderId)
            draftMenu.onServerSnapshot(detail.safeVersion, detail.safeMenu.map { it.safeRecipeId }.toSet())
            syncSelectedRecipesFromMenu()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "刷新草稿失败 orderId=${draftMenu.orderId}: ${e.message}")
            false
        }
    }

    /** 按草稿菜单同步已选 VO 快照（服务端新增的菜按需拉详情补营养数据） */
    private fun syncSelectedRecipesFromMenu() {
        val menu = draftMenu.serverMenu
        _selectedRecipeIds.value = draftMenu.localMenu
        val known = _recipes.value.filter { it.safeId in menu }.associateBy { it.safeId }
        _selectedRecipes.value = known
        menu.filterNot { it in known }.forEach { ensureAllergens(it) }
    }

    /**
     * 邀请：惰性创建草稿（空也可）并冲刷待写菜单，随后下发加入深链供页面复制。
     * 无加入凭证（非发起者详情不下发）时提示不可邀请。
     */
    fun requestInviteLink() {
        viewModelScope.launch {
            try {
                draftWriteMutex.withLock {
                    if (!draftMenu.isCreated) {
                        createDraft()
                        flushDraftWrites()
                    }
                }
                val link = DiningJoinUrlBuilder.buildOrderDeepLink(draftMenu.orderId, draftMenu.joinToken)
                if (link == null) {
                    _draftMessage.value = application.getString(R.string.dining_order_invite_unavailable)
                } else {
                    _inviteLink.value = link
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "邀请链接准备失败: ${e.message}", e)
                _draftMessage.value = e.message
            } catch (e: Exception) {
                AppLogger.error(TAG, "邀请链接准备未知失败", e)
                _draftMessage.value = application.getString(R.string.dining_order_create_write_failed)
            }
        }
    }

    /** 消费邀请链接一次性事件（页面复制后调用） */
    fun consumeInviteLink() {
        _inviteLink.value = null
    }

    /** 消费草稿写提示（页面 Toast 后调用） */
    fun consumeDraftMessage() {
        _draftMessage.value = null
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
        const val ARG_ORDER_ID = "orderId"
    }
}
