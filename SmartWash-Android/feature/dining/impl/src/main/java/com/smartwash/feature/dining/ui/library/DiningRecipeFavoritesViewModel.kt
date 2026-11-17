package com.smartwash.feature.dining.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.logic.DiningRecipeItem
import com.smartwash.feature.dining.logic.toRecipeItem
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** 收藏列表分页大小（与菜谱库一致：大图卡片取 12 平衡首屏与流量） */
private const val PAGE_SIZE = 12

/**
 * 我的收藏页 ViewModel（契约 §三.4）：复用菜谱库双栏卡片的 favorites 分页链路。
 *
 * 状态划分对齐菜谱库：[listState]（首载/刷新/错误）+ [loadMoreState]（加载更多独立态）+
 * [hasMore]。收藏/取消收藏走乐观更新：取消成功后从列表移除（失败回滚重插，保持原位置）。
 */
@HiltViewModel
class DiningRecipeFavoritesViewModel @Inject constructor(
    private val diningRepository: DiningRepository,
) : ViewModel() {

    private val _listState = MutableStateFlow<RequestState>(RequestState.Idle)
    val listState = _listState.asStateFlow()

    private val _recipes = MutableStateFlow<List<DiningRecipeItem>>(emptyList())
    val recipes = _recipes.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private val _loadMoreState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadMoreState = _loadMoreState.asStateFlow()

    private var currentPage = 1

    /** 首页拉取串行锁：首载/刷新与加载更多互斥，防响应交错覆盖 */
    private val fetchMutex = Mutex()

    /** 首载/重试/下拉刷新（防重：加载中直接忽略） */
    fun load() {
        if (_listState.value is RequestState.Loading) return
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                fetchFirstPage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningRecipeFavoritesViewModel.load: ${e.message}", e)
                _listState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 加载更多（防重：加载中/无更多/首载中直接忽略） */
    fun loadMore() {
        if (_loadMoreState.value is RequestState.Loading || !_hasMore.value) return
        if (_listState.value is RequestState.Loading) return
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

    /**
     * 取消收藏（契约 §三.4）：乐观从列表移除，失败回滚到原位置；服务端返回当前态，
     * 若仍为已收藏（并发/脏数据）则回滚，若已取消则保持移除。
     */
    fun unfavorite(recipeId: Long) {
        if (recipeId <= 0L) return
        val current = _recipes.value
        val index = current.indexOfFirst { it.id == recipeId }
        if (index < 0) return
        val removed = current[index]
        _recipes.value = current.filterNot { it.id == recipeId }
        viewModelScope.launch {
            try {
                val state = diningRepository.unfavoriteRecipe(recipeId)
                if (state) {
                    // 服务端仍为已收藏（并发/脏数据）：回滚到原位置
                    rollback(current, removed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "unfavorite: ${e.message}", e)
                rollback(current, removed)
            }
        }
    }

    /** 失败回滚：把被取消项插回原位置（以移除前列表为基准，避免与加载更多追加交错） */
    private fun rollback(previous: List<DiningRecipeItem>, removed: DiningRecipeItem) {
        if (_recipes.value.any { it.id == removed.id }) return
        val index = previous.indexOfFirst { it.id == removed.id }.coerceAtLeast(0)
        _recipes.value = _recipes.value.toMutableList().apply { add(index.coerceIn(0, size), removed) }
    }

    private suspend fun fetchFirstPage() = fetchMutex.withLock {
        val page = diningRepository.getFavoriteRecipes(1, PAGE_SIZE)
        currentPage = 1
        _recipes.value = page.records.map { it.toRecipeItem() }
        _hasMore.value = _recipes.value.size < page.total
        _listState.value = RequestState.Success
    }

    private suspend fun fetchNextPage() = fetchMutex.withLock {
        val nextPage = currentPage + 1
        val page = diningRepository.getFavoriteRecipes(nextPage, PAGE_SIZE)
        currentPage = nextPage
        _recipes.value = _recipes.value + page.records.map { it.toRecipeItem() }
        _hasMore.value = _recipes.value.size < page.total
    }

    private companion object {
        const val TAG = "DiningRecipeFavoritesViewModel"
    }
}
