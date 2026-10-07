package com.smartwash.feature.toolbox.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.ToolboxConstant
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import com.smartwash.feature.toolbox.network.AddShortCodeRequest
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
 * 新建短链 ViewModel：表单为单向数据流（Page 调 onXxxChange，收集 [form] 渲染）；
 * 提交状态走 [RequestState]，别名占用等业务错误保留后端 message 由页面展示。
 */
@HiltViewModel
class ShortCodeCreateViewModel @Inject constructor(
    private val repository: ToolboxRepository,
) : ViewModel() {

    /** 表单状态（isPublic 默认公开；过期预设默认永久，对齐后端默认值） */
    data class FormState(
        val target: String = "",
        val customCode: String = "",
        val isPublic: Boolean = true,
        val preset: ShortCodeRules.ExpiryPreset = ShortCodeRules.ExpiryPreset.PERMANENT,
        val customDate: LocalDate? = null,
    )

    private val _form = MutableStateFlow(FormState())
    val form = _form.asStateFlow()

    private val _submitState = MutableStateFlow<RequestState>(RequestState.Idle)
    val submitState = _submitState.asStateFlow()

    fun updateTarget(value: String) {
        _form.value = _form.value.copy(target = value)
    }

    fun updateCustomCode(value: String) {
        _form.value = _form.value.copy(customCode = value)
    }

    fun updateVisibility(isPublic: Boolean) {
        _form.value = _form.value.copy(isPublic = isPublic)
    }

    fun updatePreset(preset: ShortCodeRules.ExpiryPreset) {
        _form.value = _form.value.copy(preset = preset)
    }

    fun updateCustomDate(date: LocalDate?) {
        _form.value = _form.value.copy(customDate = date)
    }

    /**
     * 提交创建。客户端前置校验失败不触网（错误由页面按 resId 展示）；
     * now 参数注入便于单测固定过期计算结果。
     */
    fun submit(now: LocalDateTime = LocalDateTime.now()) {
        val f = _form.value
        if (!ShortCodeRules.isValidTarget(f.target)) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_invalid_target)
            return
        }
        if (f.customCode.isNotBlank() && !ShortCodeRules.isValidCustomCode(f.customCode.trim())) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_invalid_custom_code)
            return
        }
        if (f.preset == ShortCodeRules.ExpiryPreset.CUSTOM && f.customDate == null) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_custom_date_required)
            return
        }
        viewModelScope.launch {
            _submitState.value = RequestState.Loading
            try {
                repository.createShortCode(
                    AddShortCodeRequest(
                        target = f.target.trim(),
                        customCode = f.customCode.trim().ifEmpty { null },
                        expireAt = ShortCodeRules.expireAtFor(f.preset, f.customDate, now),
                        isPublic = if (f.isPublic) {
                            ToolboxConstant.VISIBILITY_PUBLIC
                        } else {
                            ToolboxConstant.VISIBILITY_PRIVATE
                        },
                    )
                )
                _submitState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _submitState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                _submitState.value = RequestState.Error(R.string.toolbox_error_submit)
            }
        }
    }

    fun resetSubmitState() {
        _submitState.value = RequestState.Idle
    }
}
