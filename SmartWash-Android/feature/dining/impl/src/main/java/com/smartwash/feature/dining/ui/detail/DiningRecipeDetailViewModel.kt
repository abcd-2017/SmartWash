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

    /** 首载/重试（防重：加载中直接忽略） */
    fun load(recipeId: Long) {
        if (recipeId <= 0 || _detailState.value is RequestState.Loading) return
        viewModelScope.launch {
            _detailState.value = RequestState.Loading
            try {
                _recipe.value = diningRepository.getRecipeDetail(recipeId)
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

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningRecipeDetailViewModel"
    }
}
