package com.smartwash.feature.toolbox.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.DeliveryRules
import com.smartwash.feature.toolbox.network.DeliveryException
import com.smartwash.feature.toolbox.network.DeliverySessionVo
import com.smartwash.feature.toolbox.repository.DeliveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设备投递发起 ViewModel：表单单向数据流（有效期预设 + 可选进入密码）；
 * 创建成功后 [session] 非 null，页面切换为「投递码 + 二维码 + 倒计时 + 手动关闭」会话态；
 * 关闭成功（读路径立即失效）回表单态，可直接重新发起。
 */
@HiltViewModel
class DeliveryCreateViewModel @Inject constructor(
    private val repository: DeliveryRepository,
) : ViewModel() {

    companion object {
        /** 有效期预设（分钟），对齐契约 1-120、缺省 30 */
        val DURATION_PRESETS = listOf(10, 30, 60, 120)

        private const val TAG = "DeliveryCreateViewModel"
    }

    /** 表单状态（默认 30 分钟 + 无密码，对齐后端缺省） */
    data class FormState(
        val durationMinutes: Int = DeliveryRules.DEFAULT_DURATION_MINUTES,
        val password: String = "",
    )

    private val _form = MutableStateFlow(FormState())
    val form = _form.asStateFlow()

    private val _createState = MutableStateFlow<RequestState>(RequestState.Idle)
    val createState = _createState.asStateFlow()

    /** 进入密码端侧校验错误（audit-07 P1-4：挂到密码字段本身，与网络类错误分流） */
    private val _passwordError = MutableStateFlow(false)
    val passwordError = _passwordError.asStateFlow()

    /** 已创建会话（非 null 即页面切换为会话信息态） */
    private val _session = MutableStateFlow<DeliverySessionVo?>(null)
    val session = _session.asStateFlow()

    /** 手动关闭提交状态 */
    private val _closeState = MutableStateFlow<RequestState>(RequestState.Idle)
    val closeState = _closeState.asStateFlow()

    fun updateDuration(minutes: Int) {
        if (DeliveryRules.isValidDuration(minutes)) {
            _form.value = _form.value.copy(durationMinutes = minutes)
        }
    }

    fun updatePassword(value: String) {
        // 超上限直接截断不报错（与 PasswordInput 既有惯例一致），提交时校验下限
        _form.value = _form.value.copy(password = value.take(DeliveryRules.MAX_PASSWORD_LENGTH))
        // 输入即清除字段错误（规范 §3.7：输入中不报错）
        _passwordError.value = false
    }

    /** 创建投递会话；可选密码 4-64 位端侧前置校验（后端 BCrypt 存储、不回传） */
    fun createSession() {
        if (_createState.value is RequestState.Loading) return
        val f = _form.value
        val password = f.password.trim()
        // 密码校验错误走 passwordError 显示在字段正下方；createState 只承载网络/业务错误
        _passwordError.value = false
        if (password.isNotEmpty() && !DeliveryRules.isValidPassword(password)) {
            _passwordError.value = true
            return
        }
        viewModelScope.launch {
            _createState.value = RequestState.Loading
            try {
                val vo = repository.createSession(
                    durationMinutes = f.durationMinutes,
                    password = password.ifEmpty { null },
                )
                _session.value = vo
                _createState.value = RequestState.Success
                AppLogger.info(TAG, "投递会话创建成功 code=${vo.code} duration=${f.durationMinutes}min 带密码=${password.isNotEmpty()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DeliveryCreateViewModel.createSession: ${e.message}", e)
                _createState.value = RequestState.Error(e.resId, e.message)
            } catch (e: DeliveryException) {
                AppLogger.warn(TAG, "DeliveryCreateViewModel.createSession: 业务拒绝 businessCode=${e.businessCode}")
                _createState.value = RequestState.Error(R.string.toolbox_delivery_error_create, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "DeliveryCreateViewModel.createSession: ${e.message}", e)
                _createState.value = RequestState.Error(R.string.toolbox_delivery_error_create)
            }
        }
    }

    /** 手动关闭会话（expire_at 前置，投递码立即失效）；成功回表单态可重新发起 */
    fun closeSession() {
        val code = _session.value?.code ?: return
        if (_closeState.value is RequestState.Loading) return
        viewModelScope.launch {
            _closeState.value = RequestState.Loading
            try {
                repository.closeSession(code)
                _closeState.value = RequestState.Success
                AppLogger.info(TAG, "投递会话手动关闭成功 code=$code")
                resetToForm()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DeliveryCreateViewModel.closeSession: ${e.message}", e)
                _closeState.value = RequestState.Error(e.resId, e.message)
            } catch (e: DeliveryException) {
                AppLogger.warn(TAG, "DeliveryCreateViewModel.closeSession: 业务拒绝 businessCode=${e.businessCode}")
                _closeState.value = RequestState.Error(R.string.toolbox_delivery_error_close, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "DeliveryCreateViewModel.closeSession: ${e.message}", e)
                _closeState.value = RequestState.Error(R.string.toolbox_delivery_error_close)
            }
        }
    }

    fun resetToForm() {
        _session.value = null
        _form.value = FormState()
        _createState.value = RequestState.Idle
        _closeState.value = RequestState.Idle
        _passwordError.value = false
    }

    fun resetCloseState() {
        _closeState.value = RequestState.Idle
    }
}
