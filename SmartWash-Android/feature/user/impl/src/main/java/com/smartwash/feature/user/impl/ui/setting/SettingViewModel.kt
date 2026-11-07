package com.smartwash.feature.user.impl.ui.setting

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.HapticSettings
import com.smartwash.common.utils.SharePreferenceUtils
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.network.vo.div.PlatformModelVo
import com.smartwash.feature.user.impl.network.vo.div.UserApiConfigVo
import com.smartwash.feature.user.impl.repository.DivSettingRepository
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** BYOK 表单字段（字段级校验错误的定位键） */
enum class ApiConfigField { BASE_URL, MODEL, API_KEY }

/** BYOK 表单校验错误（仅在提交时展示；输入即清除，规范 §3.7「输入中不报错」） */
data class ApiConfigFieldError(
    val field: ApiConfigField,
    @StringRes val messageRes: Int,
)

/**
 * 设置页 ViewModel（T5.3）：承载登出的会话操作。
 *
 * 原实现由宿主 MainActivity 向 SettingPage 传 SessionManager/SessionEventBus 参数——
 * app 壳因此被迫触碰用户域实现类型。现依赖改为模块内 Hilt 自取，页面签名不再
 * 暴露 impl 类型，宿主只传导航与插槽。
 *
 * 2026-11-01 设置页接真实数据：新增触感反馈开关状态（内存态直读 [HapticSettings]
 * 保证热路径即时生效 + DataStore 异步落盘；启动初值由 HapticSettingsInitTask 预热）。
 *
 * 2026-11-07 观象台 BYOK（issue #79）：新增「解读模型」配置的查/存/删/试呼状态。
 * 配置与明文密钥端上零持久化（只存服务端，列表仅回掩码），表单输入为内存态。
 */
@HiltViewModel
class SettingViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
    private val prefs: SharePreferenceUtils,
    private val divSettingRepository: DivSettingRepository,
) : ViewModel() {

    private val _hapticEnabled = MutableStateFlow(HapticSettings.enabled)
    val hapticEnabled = _hapticEnabled.asStateFlow()

    // ==================== 观象台 BYOK（解读模型，issue #79） ====================

    /** 用户 BYOK 配置（null = 未配置，解读走平台默认模型） */
    private val _apiConfig = MutableStateFlow<UserApiConfigVo?>(null)
    val apiConfig = _apiConfig.asStateFlow()

    /** 配置拉取状态（弹层打开时加载/失败反馈；行内展示不依赖它，失败可重试） */
    private val _apiConfigState = MutableStateFlow<RequestState>(RequestState.Idle)
    val apiConfigState = _apiConfigState.asStateFlow()

    /** 平台模型目录（「模型名称」输入建议；拉取失败静默降级为空，不阻塞表单） */
    private val _platformModels = MutableStateFlow<List<PlatformModelVo>>(emptyList())
    val platformModels = _platformModels.asStateFlow()

    /** 连通性测试状态（POST test；成功/失败均在弹层内即时反馈） */
    private val _apiTestState = MutableStateFlow<RequestState>(RequestState.Idle)
    val apiTestState = _apiTestState.asStateFlow()

    /** 保存状态（PUT；服务端试呼失败时 Error.message 为后端原因文案） */
    private val _apiSaveState = MutableStateFlow<RequestState>(RequestState.Idle)
    val apiSaveState = _apiSaveState.asStateFlow()

    /** 删除状态（DELETE） */
    private val _apiDeleteState = MutableStateFlow<RequestState>(RequestState.Idle)
    val apiDeleteState = _apiDeleteState.asStateFlow()

    /** 字段级校验错误（提交时展示，输入即清除） */
    private val _apiFieldError = MutableStateFlow<ApiConfigFieldError?>(null)
    val apiFieldError = _apiFieldError.asStateFlow()

    init {
        // 初值补读：覆盖启动预热任务（HapticSettingsInitTask）尚未跑完就进设置页的窗口
        viewModelScope.launch {
            runCatching { prefs.getData(HapticSettings.STORAGE_KEY, true) }
                .onSuccess {
                    HapticSettings.enabled = it
                    _hapticEnabled.value = it
                }
                // 降级：读取失败保持默认值（开启）
                .onFailure { AppLogger.warn(TAG, "触感反馈开关初值读取失败，使用默认值", it) }
        }
        // 首进即拉 BYOK 配置（2026-11-07 双轴审查 B1）：原先仅在弹层打开/保存/删除后刷新，
        // 已配置用户首进看不到掩码回显、byokEnabled=0 的行内置灰也不生效
        refreshApiConfig()
    }

    /**
     * 登出：同步清内存缓存并异步清 DataStore（幂等，不 runBlocking 阻塞主线程），
     * 随后广播主动登出（UserApiImpl 桥接为 LoginEvent.LoggedOut(LOGOUT)，
     * 并使用户信息缓存失效）。
     */
    fun logout() {
        // 低频关键动作（主动登出；会话清除的基建侧记录见 SessionManager）
        AppLogger.info(TAG, "用户主动登出")
        sessionManager.clearToken()
        sessionEventBus.notifyLoggedOut()
    }

    /** 切换触感反馈：先改内存态（热路径即时生效），再异步落盘（失败回滚内存态） */
    fun setHapticEnabled(enabled: Boolean) {
        HapticSettings.enabled = enabled
        _hapticEnabled.value = enabled
        viewModelScope.launch {
            runCatching { prefs.saveData(HapticSettings.STORAGE_KEY, enabled) }
                .onFailure {
                    // 降级：落盘失败回滚内存态（本次会话内生效，重启后丢失）
                    AppLogger.warn(TAG, "触感反馈开关落盘失败，已回滚内存态", it)
                    HapticSettings.enabled = !enabled; _hapticEnabled.value = !enabled
                }
        }
    }

    // -------------------- BYOK：查询与建议目录 --------------------

    /**
     * 拉取最新 BYOK 配置（弹层打开与保存/删除成功后调用）。
     * 失败置 Error 供弹层展示重试；行内展示保持最近一次成功数据（无则未配置态）。
     */
    fun refreshApiConfig() {
        viewModelScope.launch {
            _apiConfigState.value = RequestState.Loading
            try {
                _apiConfig.value = divSettingRepository.getUserApiConfig()
                _apiConfigState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.warn(TAG, "BYOK 配置拉取失败: ${e.message}")
                _apiConfigState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.warn(TAG, "BYOK 配置拉取失败", e)
                _apiConfigState.value = RequestState.Error(R.string.error_network_fail)
            }
        }
    }

    /** 拉取平台模型目录（输入建议；失败静默为空——建议属可选增强，不阻塞表单） */
    fun loadPlatformModels() {
        viewModelScope.launch {
            try {
                _platformModels.value = divSettingRepository.getPlatformModels()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "平台模型目录拉取失败，输入建议降级为空: ${e.message}")
            }
        }
    }

    // -------------------- BYOK：校验与提交 --------------------

    /** 输入变化时清除对应字段的校验错误（§3.7：仅提交时报错，输入中不报错） */
    fun clearFieldError(field: ApiConfigField) {
        if (_apiFieldError.value?.field == field) _apiFieldError.value = null
    }

    /**
     * 表单校验：三字段服务端均必填，baseUrl 须 https 前缀（与服务端 SSRF 校验同口径，
     * 端上先拦一次给即时文案）。错误置 [apiFieldError]，通过返回 true。
     */
    private fun validate(baseUrl: String, model: String, apiKey: String): Boolean {
        val error = when {
            baseUrl.isBlank() ->
                ApiConfigFieldError(ApiConfigField.BASE_URL, R.string.api_config_error_base_url_empty)
            !baseUrl.trim().startsWith("https://") ->
                ApiConfigFieldError(ApiConfigField.BASE_URL, R.string.api_config_error_base_url_https)
            model.isBlank() ->
                ApiConfigFieldError(ApiConfigField.MODEL, R.string.api_config_error_model_empty)
            apiKey.isBlank() ->
                ApiConfigFieldError(ApiConfigField.API_KEY, R.string.api_config_error_key_empty)
            else -> null
        }
        _apiFieldError.value = error
        return error == null
    }

    /**
     * 连通性测试（POST test，不保存）：后端试呼失败也回 200 信封（data=false），
     * 需端上自行转失败文案；网络异常经 NetworkException 带可读 message。
     */
    fun testApiConnection(baseUrl: String, model: String, apiKey: String) {
        if (!validate(baseUrl, model, apiKey)) return
        _apiTestState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val passed = divSettingRepository.testApiConfig(baseUrl.trim(), model.trim(), apiKey)
                _apiTestState.value =
                    if (passed) RequestState.Success
                    else RequestState.Error(R.string.api_config_test_fail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _apiTestState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.warn(TAG, "连通性测试请求失败: ${e.message}")
                _apiTestState.value = RequestState.Error(R.string.error_network_fail)
            }
        }
    }

    /**
     * 保存配置（PUT）：服务端先真实试呼，失败信封 data 带原因文案（ResponseInterceptor
     * 已解析进 NetworkException.message）。成功后刷新配置供行内掩码/徽标更新。
     */
    fun saveApiConfig(baseUrl: String, model: String, apiKey: String) {
        if (!validate(baseUrl, model, apiKey)) return
        _apiSaveState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                divSettingRepository.saveApiConfig(baseUrl.trim(), model.trim(), apiKey)
                // 成功日志不含任何密钥信息（掩码由服务端生成下发）
                AppLogger.info(TAG, "解读模型 BYOK 配置保存成功")
                _apiSaveState.value = RequestState.Success
                refreshApiConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _apiSaveState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.warn(TAG, "解读模型 BYOK 保存失败", e)
                _apiSaveState.value = RequestState.Error(R.string.error_network_fail)
            }
        }
    }

    /** 删除配置（DELETE）：成功后刷新，入口行回到「未配置」态 */
    fun deleteApiConfig() {
        _apiDeleteState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                divSettingRepository.deleteApiConfig()
                AppLogger.info(TAG, "解读模型 BYOK 配置已删除")
                _apiDeleteState.value = RequestState.Success
                refreshApiConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _apiDeleteState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.warn(TAG, "解读模型 BYOK 删除失败", e)
                _apiDeleteState.value = RequestState.Error(R.string.error_network_fail)
            }
        }
    }

    /** 弹层关闭时重置瞬态（测试/保存/删除/校验/加载态），避免下次打开残留旧反馈 */
    fun resetApiEditState() {
        _apiTestState.value = RequestState.Idle
        _apiSaveState.value = RequestState.Idle
        _apiDeleteState.value = RequestState.Idle
        _apiConfigState.value = RequestState.Idle
        _apiFieldError.value = null
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "SettingViewModel"
    }
}
