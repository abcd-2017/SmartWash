package com.smartwash.feature.subscription.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.SubscriptionConstant
import com.smartwash.feature.subscription.SubscriptionCycleType
import com.smartwash.feature.subscription.logic.SubscriptionRules
import com.smartwash.feature.subscription.network.SaveSubscriptionRequest
import com.smartwash.feature.subscription.network.SubscriptionVo
import com.smartwash.feature.subscription.network.UpdateSubscriptionRequest
import com.smartwash.feature.subscription.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 添加/编辑共用表单 ViewModel（同页双模式：路由 id ≤ 0 = 新建）：
 * 表单为单向数据流（Page 调 onXxxChange，收集 [form] 渲染）；
 * 客户端前置校验镜像后端 SaveSubscription 边界（name/amount/cycleInterval/remindDaysBefore/
 * cancelUrl http-https/各文本长度），校验失败不触网；业务错误保留后端 message 展示。
 */
@HiltViewModel
class SubscriptionEditViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 编辑目标 id（新建为 -1） */
    val editId: Long = savedStateHandle.get<Long>(ARG_ID) ?: -1L
    val isEditMode: Boolean get() = editId > 0

    /** 表单状态（金额/期数/提醒天数以文本承载，提交时统一解析校验） */
    data class FormState(
        val name: String = "",
        val amount: String = "",
        val cycleType: SubscriptionCycleType = SubscriptionCycleType.MONTH,
        val interval: String = "1",
        val nextRenewDate: LocalDate? = null,
        val trialEndDate: LocalDate? = null,
        val autoRenew: Boolean = true,
        val remindDays: String = "3",
        val cancelUrl: String = "",
        val cancelSteps: String = "",
        val note: String = "",
    )

    private val _form = MutableStateFlow(FormState(nextRenewDate = LocalDate.now()))
    val form = _form.asStateFlow()

    /** 编辑模式回填状态 */
    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _submitState = MutableStateFlow<RequestState>(RequestState.Idle)
    val submitState = _submitState.asStateFlow()

    init {
        if (isEditMode) load()
    }

    fun load() {
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                val vo = repository.getSubscription(editId)
                _form.value = vo.toFormState()
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _loadState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                _loadState.value = RequestState.Error(R.string.subscription_error_load_detail)
            }
        }
    }

    fun updateName(value: String) {
        _form.value = _form.value.copy(name = value)
    }

    fun updateAmount(value: String) {
        _form.value = _form.value.copy(amount = value)
    }

    fun updateCycleType(value: SubscriptionCycleType) {
        _form.value = _form.value.copy(cycleType = value)
    }

    fun updateInterval(value: String) {
        _form.value = _form.value.copy(interval = value)
    }

    fun updateNextRenewDate(value: LocalDate?) {
        _form.value = _form.value.copy(nextRenewDate = value)
    }

    fun updateTrialEndDate(value: LocalDate?) {
        _form.value = _form.value.copy(trialEndDate = value)
    }

    fun updateAutoRenew(value: Boolean) {
        _form.value = _form.value.copy(autoRenew = value)
    }

    fun updateRemindDays(value: String) {
        _form.value = _form.value.copy(remindDays = value)
    }

    fun updateCancelUrl(value: String) {
        _form.value = _form.value.copy(cancelUrl = value)
    }

    fun updateCancelSteps(value: String) {
        _form.value = _form.value.copy(cancelSteps = value)
    }

    fun updateNote(value: String) {
        _form.value = _form.value.copy(note = value)
    }

    /**
     * 提交（新建 → POST；编辑 → PUT）。客户端校验失败置 Error（页面内联展示，规范 §3.7）；
     * 成功由页面提示 + 置变更信号 + 返回。
     */
    fun submit() {
        val f = _form.value
        val name = f.name.trim()
        if (name.isEmpty()) {
            _submitState.value = RequestState.Error(R.string.subscription_error_name_required)
            return
        }
        if (name.length > SubscriptionConstant.NAME_MAX) {
            _submitState.value = RequestState.Error(R.string.subscription_error_name_too_long)
            return
        }
        val amount = f.amount.trim().toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _submitState.value = RequestState.Error(R.string.subscription_error_amount_invalid)
            return
        }
        val nextRenewDate = f.nextRenewDate
        if (nextRenewDate == null) {
            _submitState.value = RequestState.Error(R.string.subscription_error_next_renew_required)
            return
        }
        val interval = f.interval.trim().toIntOrNull()
        if (interval == null ||
            interval < SubscriptionConstant.CYCLE_INTERVAL_MIN ||
            interval > SubscriptionConstant.CYCLE_INTERVAL_MAX
        ) {
            _submitState.value = RequestState.Error(R.string.subscription_error_interval_invalid)
            return
        }
        val remindDays = f.remindDays.trim().toIntOrNull()
        if (remindDays == null ||
            remindDays < SubscriptionConstant.REMIND_DAYS_MIN ||
            remindDays > SubscriptionConstant.REMIND_DAYS_MAX
        ) {
            _submitState.value = RequestState.Error(R.string.subscription_error_remind_days_invalid)
            return
        }
        val cancelUrl = f.cancelUrl.trim()
        if (cancelUrl.isNotEmpty()) {
            if (cancelUrl.length > SubscriptionConstant.CANCEL_URL_MAX ||
                !SubscriptionRules.isValidCancelUrl(cancelUrl)
            ) {
                _submitState.value = RequestState.Error(R.string.subscription_error_cancel_url_invalid)
                return
            }
        }
        val cancelSteps = f.cancelSteps.trim()
        if (cancelSteps.length > SubscriptionConstant.CANCEL_STEPS_MAX) {
            _submitState.value = RequestState.Error(R.string.subscription_error_cancel_steps_too_long)
            return
        }
        val note = f.note.trim()
        if (note.length > SubscriptionConstant.NOTE_MAX) {
            _submitState.value = RequestState.Error(R.string.subscription_error_note_too_long)
            return
        }
        viewModelScope.launch {
            _submitState.value = RequestState.Loading
            try {
                if (isEditMode) {
                    // 编辑：可选项被清空时只能传 null（= 不改，契约语义），见方案偏离点说明
                    repository.updateSubscription(
                        editId,
                        UpdateSubscriptionRequest(
                            name = name,
                            amount = amount,
                            cycleType = f.cycleType.raw,
                            cycleInterval = interval,
                            nextRenewDate = SubscriptionRules.formatDate(nextRenewDate),
                            trialEndDate = f.trialEndDate?.let(SubscriptionRules::formatDate),
                            autoRenew = if (f.autoRenew) SubscriptionConstant.FLAG_ON else SubscriptionConstant.FLAG_OFF,
                            remindDaysBefore = remindDays,
                            cancelUrl = cancelUrl.ifEmpty { null },
                            cancelSteps = cancelSteps.ifEmpty { null },
                            note = note.ifEmpty { null },
                        ),
                    )
                } else {
                    repository.createSubscription(
                        SaveSubscriptionRequest(
                            name = name,
                            amount = amount,
                            cycleType = f.cycleType.raw,
                            cycleInterval = interval,
                            nextRenewDate = SubscriptionRules.formatDate(nextRenewDate),
                            trialEndDate = f.trialEndDate?.let(SubscriptionRules::formatDate),
                            autoRenew = if (f.autoRenew) SubscriptionConstant.FLAG_ON else SubscriptionConstant.FLAG_OFF,
                            remindDaysBefore = remindDays,
                            cancelUrl = cancelUrl.ifEmpty { null },
                            cancelSteps = cancelSteps.ifEmpty { null },
                            note = note.ifEmpty { null },
                        ),
                    )
                }
                _submitState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _submitState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                _submitState.value = RequestState.Error(R.string.subscription_error_submit)
            }
        }
    }

    fun resetSubmitState() {
        _submitState.value = RequestState.Idle
    }

    /** VO → 表单快照（编辑回填；日期解析失败防御为 null，由用户重选） */
    private fun SubscriptionVo.toFormState(): FormState = FormState(
        name = name,
        amount = SubscriptionRules.formatAmount(amount),
        cycleType = SubscriptionCycleType.fromRaw(cycleType),
        interval = cycleInterval.coerceIn(
            SubscriptionConstant.CYCLE_INTERVAL_MIN,
            SubscriptionConstant.CYCLE_INTERVAL_MAX,
        ).toString(),
        nextRenewDate = SubscriptionRules.parseDate(nextRenewDate) ?: LocalDate.now(),
        trialEndDate = SubscriptionRules.parseDate(trialEndDate),
        autoRenew = autoRenew == SubscriptionConstant.FLAG_ON,
        remindDays = remindDaysBefore.coerceIn(
            SubscriptionConstant.REMIND_DAYS_MIN,
            SubscriptionConstant.REMIND_DAYS_MAX,
        ).toString(),
        cancelUrl = cancelUrl.orEmpty(),
        cancelSteps = cancelSteps.orEmpty(),
        note = note.orEmpty(),
    )

    companion object {
        const val ARG_ID = "id"
    }
}
