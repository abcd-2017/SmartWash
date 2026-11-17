package com.smartwash.feature.dining.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 菜谱详情页 ViewModel：按 id 拉取上架菜谱全量信息（食材数组 + 步骤全文）。
 * 单请求无分页；下架/不存在由后端统一业务失败转译，页面整页错误态 + 重试。
 */
@HiltViewModel
class DiningRecipeDetailViewModel @Inject constructor(
    private val diningRepository: DiningRepository,
) : ViewModel() {

    private val _detailState = MutableStateFlow<RequestState>(RequestState.Idle)
    val detailState = _detailState.asStateFlow()

    private val _recipe = MutableStateFlow<DiningRecipeVo?>(null)
    val recipe = _recipe.asStateFlow()

    /** 收藏态（详情返回填充；页内乐观切换 + 服务端失败回滚） */
    private val _favorited = MutableStateFlow(false)
    val favorited = _favorited.asStateFlow()

    /** 收藏切换进行中（防连击重复请求） */
    private val _favoriting = MutableStateFlow(false)
    val favoriting = _favoriting.asStateFlow()

    /** 首载/重试（防重：加载中直接忽略） */
    fun load(recipeId: Long) {
        if (recipeId <= 0 || _detailState.value is RequestState.Loading) return
        viewModelScope.launch {
            _detailState.value = RequestState.Loading
            try {
                val vo = diningRepository.getRecipeDetail(recipeId)
                _recipe.value = vo
                _favorited.value = vo.favorited == true
                _detailState.value = RequestState.Success
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningRecipeDetailViewModel.load: ${e.message}", e)
                _detailState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 收藏切换（TD22/P2-6）：乐观更新本地态，服务端失败回滚——详情页收藏入口此前缺失 */
    fun toggleFavorite() {
        val id = _recipe.value?.safeId ?: return
        if (_favoriting.value) return
        _favoriting.value = true
        val target = !_favorited.value
        _favorited.value = target
        viewModelScope.launch {
            try {
                val ok = if (target) {
                    diningRepository.favoriteRecipe(id)
                } else {
                    diningRepository.unfavoriteRecipe(id)
                }
                if (!ok) _favorited.value = !target
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "toggleFavorite: ${e.message}", e)
                _favorited.value = !target
            } finally {
                _favoriting.value = false
            }
        }
    }

    private companion object {
        const val TAG = "DiningRecipeDetailViewModel"
    }
}
