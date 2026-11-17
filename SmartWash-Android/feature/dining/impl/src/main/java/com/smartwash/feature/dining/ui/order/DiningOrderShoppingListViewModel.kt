package com.smartwash.feature.dining.ui.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.SharePreferenceUtils
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.model.DietaryPreferenceVo
import com.smartwash.feature.dining.logic.DiningShoppingListResult
import com.smartwash.feature.dining.logic.parseDiningShoppingList
import com.smartwash.feature.dining.logic.resolveMyUserId
import com.smartwash.feature.dining.logic.toShoppingListResult
import com.smartwash.feature.dining.repository.DiningRepository
import com.smartwash.feature.user.api.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 购买清单结果页 ViewModel（11 号票）：详情接口拉取 shopping_list 快照与参与者，
 * 解析为双视图模型（[parseDiningShoppingList] + [toShoppingListResult] 纯函数）。
 *
 * 入口有两处：结束点单成功后进入；FINISHED 只读态点单页经「查看购买清单」进入。
 * 快照缺失/畸形一律降级为空清单（结果页展示空态文案，不崩溃）。
 */
@HiltViewModel
class DiningOrderShoppingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diningRepository: DiningRepository,
    private val userApi: UserApi,
    private val prefs: SharePreferenceUtils,
) : ViewModel() {

    /** 路由参数：订单 ID（0=非法，不发起请求） */
    val orderId: Long = savedStateHandle.get<Long>(ARG_ORDER_ID) ?: 0L

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _result = MutableStateFlow<DiningShoppingListResult?>(null)
    val result = _result.asStateFlow()

    /** 订单标题（导出文本用；缺失空串） */
    private val _orderTitle = MutableStateFlow("")
    val orderTitle = _orderTitle.asStateFlow()

    /**
     * 用户每日热量目标（默认 2000；偏好拉取后回填）。热量卡「今日剩余 = 目标 - 本单总热量」
     * 的数据源（契约 §三.3）；失败静默用默认值，不阻断清单主流程。
     */
    private val _calorieTarget = MutableStateFlow(DietaryPreferenceVo.DEFAULT_CALORIE_TARGET)
    val calorieTarget = _calorieTarget.asStateFlow()

    /**
     * 已买食材（本地态，按订单持久化）：买菜时勾选，重启仍在。
     * 只影响展示与导出文本，不回写服务端（清单是快照）。
     */
    private val _checked = MutableStateFlow<Set<String>>(emptySet())
    val checked = _checked.asStateFlow()

    /** 首载/重试（防重：加载中忽略） */
    fun load() {
        if (_loadState.value is RequestState.Loading) return
        if (orderId <= 0L) {
            _loadState.value = RequestState.Error(R.string.dining_shopping_list_load_failed)
            return
        }
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                val detail = diningRepository.getOrderDetail(orderId)
                _orderTitle.value = detail.safeTitle
                val phone = userApi.getUserInfo()?.phoneNumber
                val myUserId = resolveMyUserId(detail.safeMembers, phone)
                _result.value = parseDiningShoppingList(detail.safeShoppingList)
                    ?.toShoppingListResult(detail.safeMembers, myUserId)
                    ?: DiningShoppingListResult(
                        ingredients = emptyList(),
                        perRecipe = emptyList(),
                        ingredientTotals = null,
                        totalCalories = null,
                        calorieUnknownDishCount = null,
                        memberCount = detail.safeMembers.size,
                    )
                _checked.value = readChecked()
                loadCalorieTargetSafe()
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderShoppingListViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /**
     * 热量目标拉取（契约 §三.3）：失败静默用默认 2000，不阻断清单主流程。
     */
    private suspend fun loadCalorieTargetSafe() {
        try {
            val preference = diningRepository.getDietaryPreference()
            _calorieTarget.value = preference.calorieTarget
                ?.takeIf { it > 0 }
                ?: DietaryPreferenceVo.DEFAULT_CALORIE_TARGET
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "热量目标拉取失败（降级默认 2000）：${e.message}")
        }
    }

    /** 切换「已买」并落盘（写失败仅不落盘，不阻断勾选交互） */
    fun toggleChecked(name: String) {
        val next = _checked.value.toMutableSet().apply { if (!add(name)) remove(name) }
        _checked.value = next
        viewModelScope.launch {
            runCatching { prefs.saveData(checkedKey(), GSON.toJson(next)) }
                .onFailure { AppLogger.error(TAG, "购买清单已买态保存失败：${it.message}", it) }
        }
    }

    /** 读取本单已买食材（缺失/畸形一律空集，不崩溃） */
    private suspend fun readChecked(): Set<String> {
        if (orderId <= 0L) return emptySet()
        val raw = runCatching { prefs.getData(checkedKey(), "") }.getOrNull()
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching { GSON.fromJson(raw, Array<String>::class.java)?.toSet() }
            .getOrNull()
            ?: emptySet()
    }

    private fun checkedKey(): String = CHECKED_KEY_PREFIX + orderId

    private companion object {
        const val TAG = "DiningOrderShoppingListViewModel"

        /** 类型安全路由参数键（DiningRoute.ShoppingList 属性名即键名） */
        const val ARG_ORDER_ID = "orderId"

        /** 已买食材本地存储键前缀（按订单隔离，后缀为 orderId） */
        const val CHECKED_KEY_PREFIX = "dining_shopping_checked_"

        /** 清单快照/已买态 JSON 序列化（与 DiningShoppingListMappers 同口径） */
        val GSON = Gson()
    }
}
