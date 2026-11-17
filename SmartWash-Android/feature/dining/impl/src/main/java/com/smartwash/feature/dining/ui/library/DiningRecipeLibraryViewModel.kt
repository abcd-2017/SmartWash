package com.smartwash.feature.dining.ui.library

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningCategoryChip
import com.smartwash.feature.dining.logic.DiningRecipeItem
import com.smartwash.feature.dining.logic.toCategoryChips
import com.smartwash.feature.dining.logic.toRecipeItem
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

/** 菜谱库分页大小（后端 BaseSearchFrom 默认 10、上限 100；菜谱卡片大图取 12 平衡首屏与流量） */
private const val PAGE_SIZE = 12

/** 关键词搜索防抖时长（对齐 pagingFlow 先例：输入停顿后再打请求） */
private const val KEYWORD_DEBOUNCE_MS = 300L

/**
 * 状态划分（请求状态全程 [RequestState]）：
 * - [listState]：上架菜谱首页列表（骨架/错误/内容），切分类与下拉刷新复用；
 * - [loadMoreState]：加载更多独立态，失败不影响已加载列表；
 * - [chips]：左分类栏数据（头部固定「全部」伪分类），加载失败仅留痕隐藏分类栏——
 *   分类是辅助筛选，不拖垮菜谱列表主流程（对齐 notice 未读数失败语义）；
 * - [selectedCategoryId]：当前筛选分类（null=全部），切换即服务端按分类重置分页（契约 §5.1
 *   性能硬约束：保留服务端分页 + 懒加载，禁止全量拉取本地过滤）；
 * - [keyword]：菜名模糊搜索，服务端分页查询；搜索态切换为全库检索（分类过滤暂不参与），
 *   关键词清空后恢复分类过滤。
 */
@HiltViewModel
class DiningRecipeLibraryViewModel @Inject constructor(
    private val diningRepository: DiningRepository,
    private val application: Application,
) : ViewModel() {

    private val _listState = MutableStateFlow<RequestState>(RequestState.Idle)
    val listState = _listState.asStateFlow()

    private val _recipes = MutableStateFlow<List<DiningRecipeItem>>(emptyList())
    val recipes = _recipes.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private val _loadMoreState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadMoreState = _loadMoreState.asStateFlow()

    private val _chips = MutableStateFlow<List<DiningCategoryChip>>(emptyList())
    val chips = _chips.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    /** 关键词（菜名模糊，空串=全库；与分类筛选叠加） */
    private val _keyword = MutableStateFlow("")
    val keyword = _keyword.asStateFlow()

    private var currentPage = 1

    /** 首页拉取串行锁：首载/切分类/关键词搜索共用，防并发响应交错覆盖 */
    private val fetchMutex = Mutex()

    /** 关键词搜索防抖任务（新输入取消上一次） */
    private var keywordJob: Job? = null

    /** 首载/重试/下拉刷新（防重：加载中直接忽略；分类条与列表并行独立拉取） */
    fun load() {
        if (_listState.value is RequestState.Loading) return
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                fetchFirstPage()
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningRecipeLibraryViewModel.load: ${e.message}", e)
                _listState.value = RequestState.Error(e.resId, e.message)
            }
        }
        loadCategories()
    }

    /** 关键词输入：防抖 300ms 重载首页（空串=全库；分类叠加） */
    fun onKeywordChange(value: String) {
        _keyword.value = value
        keywordJob?.cancel()
        keywordJob = viewModelScope.launch {
            delay(KEYWORD_DEBOUNCE_MS)
            _listState.value = RequestState.Loading
            try {
                fetchFirstPage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "onKeywordChange: ${e.message}", e)
                _listState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /**
     * 生效分类筛选（契约 §5.1）：关键词搜索态切换为全库检索（分类暂不参与，UI 同步隐藏
     * 分类栏）；关键词清空后恢复分类过滤。选中分类始终保留在本地态，不因搜索被清除。
     */
    private fun effectiveCategoryId(): Long? =
        if (_keyword.value.trim().isNotBlank()) null else _selectedCategoryId.value

    /** 首页拉取（分类 + 关键词 + 分页基准；互斥串行，成功后才推进页码） */
    private suspend fun fetchFirstPage() = fetchMutex.withLock {
        val page = diningRepository.getRecipes(
            1, PAGE_SIZE, effectiveCategoryId(), _keyword.value.trim(),
        )
        currentPage = 1
        _recipes.value = page.records.map { it.toRecipeItem() }
        _hasMore.value = _recipes.value.size < page.total
        _listState.value = RequestState.Success
    }

    /** 分类条独立拉取（失败仅留痕，筛选条隐藏，不拖垮列表主流程） */
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                _chips.value = diningRepository.getCategories()
                    .toCategoryChips(application.getString(R.string.dining_category_all))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "分类条拉取失败（隐藏筛选条）：${e.message}")
            }
        }
    }

    /** 切换分类筛选（切到当前分类不重复请求；防重：加载中忽略） */
    fun selectCategory(categoryId: Long?) {
        if (_selectedCategoryId.value == categoryId) return
        if (_listState.value is RequestState.Loading) return
        _selectedCategoryId.value = categoryId
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                fetchFirstPage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "selectCategory: ${e.message}", e)
                _listState.value = RequestState.Error(e.resId, e.message)
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
            val page = diningRepository.getRecipes(
                nextPage, PAGE_SIZE, effectiveCategoryId(), _keyword.value.trim(),
            )
                currentPage = nextPage
                _recipes.value = _recipes.value + page.records.map { it.toRecipeItem() }
                _hasMore.value = _recipes.value.size < page.total
                _loadMoreState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "loadMore: ${e.message}", e)
                _loadMoreState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    private companion object {
        const val TAG = "DiningRecipeLibraryViewModel"
    }
}
