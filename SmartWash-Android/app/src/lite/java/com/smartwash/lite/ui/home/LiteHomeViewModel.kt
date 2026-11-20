package com.smartwash.lite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.dining.api.DiningApi
import com.smartwash.feature.dining.api.model.DiningOrderActiveVo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * lite 首页 ViewModel：只服务「发起点单」主 CTA 的唯一性收敛。
 *
 * 拉取本人进行中的 DRAFT（[com.smartwash.feature.dining.api.DiningApi.getActiveOrder]）：
 * 有则 CTA 进该单编辑态，无则空白创建页——与全量版列表 FAB 同一口径，避免反复新建草稿。
 */
@HiltViewModel
class LiteHomeViewModel @Inject constructor(
    private val diningApi: DiningApi,
) : ViewModel() {

    private val _activeOrder = MutableStateFlow<DiningOrderActiveVo?>(null)
    val activeOrder = _activeOrder.asStateFlow()

    /** 首载/回前台刷新（失败静默置空，最坏退回新建，后端 40901 兜底提示） */
    fun load() {
        viewModelScope.launch {
            _activeOrder.value = runCatching { diningApi.getActiveOrder() }.getOrNull()
        }
    }
}
