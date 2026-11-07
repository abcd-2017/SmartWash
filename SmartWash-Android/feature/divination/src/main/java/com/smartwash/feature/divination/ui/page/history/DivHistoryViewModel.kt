package com.smartwash.feature.divination.ui.page.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.database.DivRecordEntity
import com.smartwash.feature.divination.data.DivRecordRepository
import com.smartwash.feature.divination.network.DivinationApi
import com.smartwash.feature.divination.network.DivRecordVo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DivHistoryUiState(
    val filter: DivMethod? = null,
)

/**
 * 卦历案卷 VM：Room 卦历 + 术数筛选 chips。
 *
 * 服务端同步（issue #78，侵入最小口径）：列表仍以本地 Room 为单一数据源（卦盘复盘依赖
 * 本地 chartJson 原盘），进页静默拉 GET /records 分页首页，把「本地未关联服务端」的记录按
 * method+question+castAt 对账回填 serverRecordId——后续求解读/追问直接复用服务端卦例；
 * 失败静默（卦盘/解读链路另有 ensureServerRecord 兜底建档），列表内容不受影响。
 */
@HiltViewModel
class DivHistoryViewModel @Inject constructor(
    private val repository: DivRecordRepository,
    private val api: DivinationApi,
) : ViewModel() {

    private val _filter = MutableStateFlow<DivMethod?>(null)
    val uiState: StateFlow<DivHistoryUiState> = _filter.map { DivHistoryUiState(filter = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DivHistoryUiState())

    val records: StateFlow<List<DivRecordEntity>> =
        combine(repository.observeAll(), _filter) { all, filter ->
            if (filter == null) all else all.filter { it.method == filter.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { syncServerRecords() }
    }

    fun onFilter(method: DivMethod?) {
        _filter.value = method
    }

    /** 拉服务端分页首页（size=50 覆盖近期卦例），对未关联本地记录回填 serverRecordId */
    private suspend fun syncServerRecords() {
        runCatching { api.searchRecords(page = 1, size = 50) }
            .onSuccess { result ->
                val serverRecords = result.data?.records.orEmpty()
                if (result.code != 200 || serverRecords.isEmpty()) return
                val unlinked = repository.findUnlinked()
                if (unlinked.isEmpty()) return
                var linked = 0
                unlinked.forEach { local ->
                    val match = serverRecords.firstOrNull { it.matches(local) }
                    if (match != null) {
                        repository.markServerLinked(local.id, match.id)
                        linked++
                    }
                }
                if (linked > 0) {
                    AppLogger.info(TAG, "卦历同步回填 serverRecordId ×$linked / 未关联 ${unlinked.size}")
                }
            }
            .onFailure { AppLogger.warn(TAG, "卦历服务端同步失败（本地展示不受影响）: ${it.message}") }
    }

    /** 对账键：method + question + castAt（服务端 LocalDateTime 按 tzOffset 还原为 epoch millis） */
    private fun DivRecordVo.matches(local: DivRecordEntity): Boolean =
        method == local.method &&
            question == local.question &&
            castAtMillis() == local.castAt

    private fun DivRecordVo.castAtMillis(): Long? = runCatching {
        val iso = castAt ?: return null
        val offsetSeconds = (tzOffset ?: 480) * 60
        LocalDateTime.parse(iso).toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds)) * 1000L
    }.getOrNull()

    private companion object {
        const val TAG = "DivHistoryViewModel"
    }
}
