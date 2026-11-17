package com.smartwash.feature.user.impl.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.api.DiningApi
import com.smartwash.feature.dining.api.model.DietaryPreferenceVo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 饮食偏好页 ViewModel（契约 §2.1/§三.2）：进入时 GET 回填，保存走 PUT。
 *
 * 忌口为 7 标签多选（SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY）；热量/钠目标用 stepper
 * （1200-4000 / 1000-4000），后端 null 字段=默认值语义，端上 GET 到的 null 兜底为
 * 2000/2000 展示，保存提交具体值（与后端默认等价）。
 *
 * 状态：[loadState]（首载/回填，失败整页错误态可重试）+ [saveState]（保存动作态，Loading 防重）；
 * [allergens]/[calorieTarget]/[sodiumTarget] 为可编辑本地态，单向驱动页面。
 */
@HiltViewModel
class DietaryPreferenceViewModel @Inject constructor(
    private val diningApi: DiningApi,
) : ViewModel() {

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _saveState = MutableStateFlow<RequestState>(RequestState.Idle)
    val saveState = _saveState.asStateFlow()

    /** 已选忌口标签（空集=无忌口） */
    private val _allergens = MutableStateFlow<Set<String>>(emptySet())
    val allergens = _allergens.asStateFlow()

    /** 每日热量目标（kcal，1200-4000；默认 2000） */
    private val _calorieTarget = MutableStateFlow(DietaryPreferenceVo.DEFAULT_CALORIE_TARGET)
    val calorieTarget = _calorieTarget.asStateFlow()

    /** 每日钠目标（mg，1000-4000；默认 2000） */
    private val _sodiumTarget = MutableStateFlow(DietaryPreferenceVo.DEFAULT_SODIUM_TARGET)
    val sodiumTarget = _sodiumTarget.asStateFlow()

    /** 首载/重试（防重：加载中忽略） */
    fun load() {
        if (_loadState.value is RequestState.Loading) return
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                val preference = diningApi.getDietaryPreference()
                _allergens.value = preference.safeAllergens.toSet()
                _calorieTarget.value = preference.calorieTarget
                    ?.takeIf { it > 0 }
                    ?: DietaryPreferenceVo.DEFAULT_CALORIE_TARGET
                _sodiumTarget.value = preference.sodiumTarget
                    ?.takeIf { it > 0 }
                    ?: DietaryPreferenceVo.DEFAULT_SODIUM_TARGET
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DietaryPreferenceViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 切换忌口标签（多选；白名单外忽略） */
    fun toggleAllergen(code: String) {
        if (code !in ALLERGENS) return
        _allergens.value = _allergens.value.toMutableSet().apply { if (!add(code)) remove(code) }
    }

    /** 热量目标 stepper（越界钳制到 1200-4000） */
    fun onCalorieTargetChange(value: Int) {
        _calorieTarget.value = value.coerceIn(
            DietaryPreferenceVo.CALORIE_MIN,
            DietaryPreferenceVo.CALORIE_MAX,
        )
    }

    /** 钠目标 stepper（越界钳制到 1000-4000） */
    fun onSodiumTargetChange(value: Int) {
        _sodiumTarget.value = value.coerceIn(
            DietaryPreferenceVo.SODIUM_MIN,
            DietaryPreferenceVo.SODIUM_MAX,
        )
    }

    /** 保存（PUT；Loading 期间防重）。成功由页面消费后提示并返回 */
    fun save() {
        if (_saveState.value is RequestState.Loading) return
        _saveState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                diningApi.saveDietaryPreference(
                    DietaryPreferenceVo(
                        allergens = _allergens.value.toList(),
                        calorieTarget = _calorieTarget.value,
                        sodiumTarget = _sodiumTarget.value,
                    ),
                )
                _saveState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DietaryPreferenceViewModel.save: ${e.message}", e)
                _saveState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 消费保存结果（页面 Toast/返回后调用，避免重放） */
    fun consumeSaveState() {
        _saveState.value = RequestState.Idle
    }

    companion object {
        const val TAG = "DietaryPreferenceViewModel"

        /** 忌口标签白名单（与设置页 7 个 chip、后端 Pattern 同源） */
        val ALLERGENS = listOf("SEAFOOD", "NUT", "GLUTEN", "DAIRY", "EGG", "SOY", "SPICY")
    }
}
