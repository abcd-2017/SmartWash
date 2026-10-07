package com.smartwash.feature.toolbox.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pagingFlow
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import com.smartwash.feature.toolbox.network.ShortCodeVo
import com.smartwash.feature.toolbox.network.UpdateShortCodeRequest
import com.smartwash.feature.toolbox.paging.ShortCodeStatsPagingSource
import com.smartwash.feature.toolbox.repository.ToolboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 短链详情 ViewModel：基础字段由列表页路由携带（禁止经 resolve 拉取——resolve 会计点击），
 * 访问明细独立分页；私有码打开经 resolve 取 target（计一次点击属预期）；
 * 续期/删除走 PUT/DELETE，成功后由页面回列表并触发刷新。
 */
@HiltViewModel
class ShortCodeDetailViewModel @Inject constructor(
    private val repository: ToolboxRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 路由入参还原（缺省防御值与 ShortCodeVo 默认一致） */
    val shortCode = ShortCodeVo(
        id = savedStateHandle.get<Long>(ARG_ID) ?: -1L,
        code = savedStateHandle.get<String>(ARG_CODE).orEmpty(),
        contentType = savedStateHandle.get<Int>(ARG_CONTENT_TYPE) ?: 1,
        target = savedStateHandle.get<String>(ARG_TARGET)?.ifBlank { null },
        isPublic = savedStateHandle.get<Int>(ARG_IS_PUBLIC) ?: 1,
        expireAt = savedStateHandle.get<String>(ARG_EXPIRE_AT)?.ifBlank { null },
        clickCount = savedStateHandle.get<Long>(ARG_CLICK_COUNT) ?: 0L,
        shortUrl = savedStateHandle.get<String>(ARG_SHORT_URL)?.ifBlank { null },
        createdAt = savedStateHandle.get<String>(ARG_CREATED_AT)?.ifBlank { null },
    )

    /** 访问明细分页 */
    private val _statsTrigger = MutableStateFlow("")
    val statsPagingFlow = pagingFlow(_statsTrigger) {
        ShortCodeStatsPagingSource(repository, shortCode.id)
    }

    /** 续期提交状态 */
    private val _updateState = MutableStateFlow<RequestState>(RequestState.Idle)
    val updateState = _updateState.asStateFlow()

    /** 删除提交状态 */
    private val _deleteState = MutableStateFlow<RequestState>(RequestState.Idle)
    val deleteState = _deleteState.asStateFlow()

    /** 私有码解析状态与结果（Success 时页面取 [resolvedTarget] 打开浏览器） */
    private val _resolveState = MutableStateFlow<RequestState>(RequestState.Idle)
    val resolveState = _resolveState.asStateFlow()
    private val _resolvedTarget = MutableStateFlow<String?>(null)
    val resolvedTarget = _resolvedTarget.asStateFlow()

    /** 续期（预设 7 天 / 30 天 / 自定义日期；target 不传即不修改） */
    fun renewExpire(preset: ShortCodeRules.ExpiryPreset, customDate: LocalDate?, now: LocalDateTime = LocalDateTime.now()) {
        if (preset == ShortCodeRules.ExpiryPreset.PERMANENT) return
        if (preset == ShortCodeRules.ExpiryPreset.CUSTOM && customDate == null) {
            _updateState.value = RequestState.Error(R.string.toolbox_error_custom_date_required)
            return
        }
        viewModelScope.launch {
            _updateState.value = RequestState.Loading
            try {
                repository.updateShortCode(
                    shortCode.id,
                    UpdateShortCodeRequest(
                        expireAt = ShortCodeRules.expireAtFor(preset, customDate, now),
                    ),
                )
                _updateState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _updateState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                _updateState.value = RequestState.Error(R.string.toolbox_error_submit)
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            _deleteState.value = RequestState.Loading
            try {
                repository.deleteShortCode(shortCode.id)
                _deleteState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _deleteState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                _deleteState.value = RequestState.Error(R.string.toolbox_error_submit)
            }
        }
    }

    /** 私有码打开前解析（owner 通道，计入一次点击统计） */
    fun resolveTarget() {
        viewModelScope.launch {
            _resolveState.value = RequestState.Loading
            try {
                val vo = repository.resolveShortCode(shortCode.code)
                _resolvedTarget.value = vo.target
                _resolveState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _resolveState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                _resolveState.value = RequestState.Error(R.string.toolbox_error_submit)
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = RequestState.Idle
    }

    fun resetDeleteState() {
        _deleteState.value = RequestState.Idle
    }

    fun resetResolveState() {
        _resolveState.value = RequestState.Idle
        _resolvedTarget.value = null
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_CODE = "code"
        const val ARG_CONTENT_TYPE = "contentType"
        const val ARG_IS_PUBLIC = "isPublic"
        const val ARG_CLICK_COUNT = "clickCount"
        const val ARG_TARGET = "target"
        const val ARG_EXPIRE_AT = "expireAt"
        const val ARG_SHORT_URL = "shortUrl"
        const val ARG_CREATED_AT = "createdAt"
    }
}
