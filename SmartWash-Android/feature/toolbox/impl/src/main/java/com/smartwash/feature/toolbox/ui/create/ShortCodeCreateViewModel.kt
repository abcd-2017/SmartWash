package com.smartwash.feature.toolbox.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.api.ToolboxConstant
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import com.smartwash.feature.toolbox.api.network.AddShortCodeRequest
import com.smartwash.feature.toolbox.api.network.ShortCodeVo
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
 * 新建短码 ViewModel：表单为单向数据流（Page 调 onXxxChange，收集 [form] 渲染）；
 * 提交状态走 [RequestState]，别名占用等业务错误保留后端 message 由页面展示。
 * 创建模式二选一：短链（target）/ 私密文本（content，阅后即焚，后端加密落库）。
 */
@HiltViewModel
class ShortCodeCreateViewModel @Inject constructor(
    private val repository: ToolboxRepository,
) : ViewModel() {

    /** 创建模式（页首 chip 切换，切换即整组表单语义切换） */
    enum class CreateMode { SHORT_LINK, SECRET }

    /** 表单状态（isPublic 默认公开；短链过期预设默认永久、私密文本默认 1 天） */
    data class FormState(
        val mode: CreateMode = CreateMode.SHORT_LINK,
        val target: String = "",
        val customCode: String = "",
        val isPublic: Boolean = true,
        val preset: ShortCodeRules.ExpiryPreset = ShortCodeRules.ExpiryPreset.PERMANENT,
        val customDate: LocalDate? = null,
        // ---------- 私密文本 ----------
        val content: String = "",
        val passwordEnabled: Boolean = false,
        val password: String = "",
        val secretPreset: ShortCodeRules.SecretExpiryPreset = ShortCodeRules.SecretExpiryPreset.DAYS_1,
    )

    private val _form = MutableStateFlow(FormState())
    val form = _form.asStateFlow()

    private val _submitState = MutableStateFlow<RequestState>(RequestState.Idle)
    val submitState = _submitState.asStateFlow()

    /** 最近一次创建成功的短码 VO（私密文本创建成功直达详情页用，仅该分支写入/消费） */
    private val _lastCreated = MutableStateFlow<ShortCodeVo?>(null)
    val lastCreated = _lastCreated.asStateFlow()

    fun updateMode(mode: CreateMode) {
        _form.value = _form.value.copy(mode = mode)
    }

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

    fun updateContent(value: String) {
        // 硬上限 2000：输入法粘贴超长时截断，与 helper 计数一致
        _form.value = _form.value.copy(
            content = if (value.length > ShortCodeRules.SECRET_MAX_CHARS) {
                value.take(ShortCodeRules.SECRET_MAX_CHARS)
            } else {
                value
            },
        )
    }

    fun updatePasswordEnabled(enabled: Boolean) {
        _form.value = _form.value.copy(passwordEnabled = enabled)
    }

    fun updatePassword(value: String) {
        _form.value = _form.value.copy(
            password = if (value.length > ShortCodeRules.SECRET_PASSWORD_MAX) {
                value.take(ShortCodeRules.SECRET_PASSWORD_MAX)
            } else {
                value
            },
        )
    }

    fun updateSecretPreset(preset: ShortCodeRules.SecretExpiryPreset) {
        _form.value = _form.value.copy(secretPreset = preset)
    }

    /**
     * 提交创建。客户端前置校验失败不触网（错误由页面按 resId 展示）；
     * now 参数注入便于单测固定过期计算结果。
     */
    fun submit(now: LocalDateTime = LocalDateTime.now()) {
        val f = _form.value
        if (f.customCode.isNotBlank() && !ShortCodeRules.isValidCustomCode(f.customCode.trim())) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_invalid_custom_code)
            return
        }
        when (f.mode) {
            CreateMode.SHORT_LINK -> submitShortLink(f, now)
            CreateMode.SECRET -> submitSecret(f, now)
        }
    }

    private fun submitShortLink(f: FormState, now: LocalDateTime) {
        if (!ShortCodeRules.isValidTarget(f.target)) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_invalid_target)
            return
        }
        if (f.preset == ShortCodeRules.ExpiryPreset.CUSTOM && f.customDate == null) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_custom_date_required)
            return
        }
        // 日期选择器已限今日起选，提交前再拦一次（状态残留/深链带进过去日期时在此报错）
        if (f.preset == ShortCodeRules.ExpiryPreset.CUSTOM && f.customDate != null &&
            ShortCodeRules.isCustomExpiryInPast(f.customDate, now)
        ) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_expiry_in_past)
            return
        }
        viewModelScope.launch {
            _submitState.value = RequestState.Loading
            try {
                repository.createShortCode(
                    AddShortCodeRequest(
                        contentType = null,
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
                AppLogger.info(TAG, "短链创建成功 customCode=${f.customCode.trim().ifEmpty { "auto" }} 公开=${f.isPublic}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "ShortCodeCreateViewModel.submit: ${e.message}", e)
                _submitState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "ShortCodeCreateViewModel.submit: ${e.message}", e)
                _submitState.value = RequestState.Error(R.string.toolbox_error_submit)
            }
        }
    }

    private fun submitSecret(f: FormState, now: LocalDateTime) {
        if (!ShortCodeRules.isValidSecretContent(f.content)) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_secret_content)
            return
        }
        if (f.passwordEnabled && !ShortCodeRules.isValidSecretPassword(f.password)) {
            _submitState.value = RequestState.Error(R.string.toolbox_error_secret_password)
            return
        }
        viewModelScope.launch {
            _submitState.value = RequestState.Loading
            try {
                val vo = repository.createShortCode(
                    AddShortCodeRequest(
                        contentType = ShortCodeRules.CONTENT_TYPE_SECRET_TEXT,
                        content = f.content,
                        // 口令明文只经 HTTPS 请求体上行，服务端 BCrypt 落库；成功后立即清出表单状态
                        password = if (f.passwordEnabled) f.password else null,
                        customCode = f.customCode.trim().ifEmpty { null },
                        expireAt = ShortCodeRules.secretExpireAtFor(f.secretPreset, now),
                    )
                )
                // 记录创建结果供页面直达详情（分享链接是核心交付物，少一步打断）
                _lastCreated.value = vo
                _submitState.value = RequestState.Success
                AppLogger.info(TAG, "私密文本创建成功 customCode=${f.customCode.trim().ifEmpty { "auto" }} 带口令=${f.passwordEnabled}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "ShortCodeCreateViewModel.submit: ${e.message}", e)
                _submitState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "ShortCodeCreateViewModel.submit: ${e.message}", e)
                _submitState.value = RequestState.Error(R.string.toolbox_error_submit)
            }
        }
    }

    fun resetSubmitState() {
        _submitState.value = RequestState.Idle
    }

    private companion object {
        const val TAG = "ShortCodeCreateViewModel"
    }
}
