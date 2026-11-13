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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 菜谱库分页大小（后端 BaseSearchFrom 默认 10、上限 100；菜谱卡片大图取 12 平衡首屏与流量） */
private const val PAGE_SIZE = 12

/**
 * 菜谱库页 ViewModel。
 *
 * 状态划分（请求状态全程 [RequestState]）：
 * - [listState]：上架菜谱首页列表（骨架/错误/内容），切分类与下拉刷新复用；
 * - [loadMoreState]：加载更多独立态，失败不影响已加载列表；
 * - [chips]：分类筛选条（头部固定「全部」伪分类），加载失败仅留痕隐藏筛选条——
 *   分类是辅助筛选，不拖垮菜谱列表主流程（对齐 notice 未读数失败语义）；
 * - [selectedCategoryId]：当前筛选分类（null=全部），切换即重载首页列表。
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

    private var currentPage = 1

    /** 首载/重试/下拉刷新（防重：加载中直接忽略；分类条与列表并行独立拉取） */
    fun load() {
        if (_listState.value is RequestState.Loading) return
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                val page = diningRepository.getRecipes(1, PAGE_SIZE, _selectedCategoryId.value)
                currentPage = 1
                _recipes.value = page.records.map { it.toRecipeItem() }
                _hasMore.value = _recipes.value.size < page.total
                _listState.value = RequestState.Success
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
                val page = diningRepository.getRecipes(1, PAGE_SIZE, categoryId)
                currentPage = 1
                _recipes.value = page.records.map { it.toRecipeItem() }
                _hasMore.value = _recipes.value.size < page.total
                _listState.value = RequestState.Success
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
                val page = diningRepository.getRecipes(nextPage, PAGE_SIZE, _selectedCategoryId.value)
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
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningRecipeLibraryViewModel"
    }
}
