package com.smartwash.feature.user.impl.ui.setting

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.network.entity.div.SaveUserApiConfig
import com.smartwash.feature.user.impl.network.vo.div.PlatformVendorGroupVo
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
enum class ApiConfigField { MODEL_CONFIG, BASE_URL, MODEL, API_KEY }

/** 解读模型配置模式（2026-11-11 双模式契约）：目录模式点选条目回传 id，自定义模式自输接入点 */
enum class ApiConfigMode {
    /** 目录模式（默认）：GET /models 返回厂商→模型目录树，双下拉联动选择，服务端取接入点/模型名 */
    CATALOG,

    /** 自定义模式：自输 baseUrl + 模型名 + apiKey */
    CUSTOM,
}

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
 * 2026-11-12 触感反馈开关迁至权限管理页（PermissionManagementViewModel）：
 * 设置页不再承载该开关，原「同步内存 + viewModelScope 异步落盘」写法存在
 * 离开页面即丢落盘、初值补读覆盖用户切换两处缺陷，随迁移一并修复。
 *
 * 2026-11-07 观象台 BYOK（issue #79）：新增「解读模型」配置的查/存/删/试呼状态。
 * 配置与明文密钥端上零持久化（只存服务端，列表仅回掩码），表单输入为内存态。
 */
@HiltViewModel
class SettingViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
    private val divSettingRepository: DivSettingRepository,
) : ViewModel() {

    // ==================== 观象台 BYOK（解读模型，issue #79） ====================

    /** 用户解读模型配置（null = 未配置；平台零兜底，未配置解读统一 428） */
    private val _apiConfig = MutableStateFlow<UserApiConfigVo?>(null)
    val apiConfig = _apiConfig.asStateFlow()

    /** 配置拉取状态（页面打开时加载/失败反馈；设置页行内展示不依赖它，失败可重试） */
    private val _apiConfigState = MutableStateFlow<RequestState>(RequestState.Idle)
    val apiConfigState = _apiConfigState.asStateFlow()

    /** 平台模型目录树（目录模式分步选择：厂商→模型；拉取失败静默降级为空，不阻塞表单） */
    private val _platformModels = MutableStateFlow<List<PlatformVendorGroupVo>>(emptyList())
    val platformModels = _platformModels.asStateFlow()

    /** 连通性测试状态（POST test；成功/失败均在配置页内即时反馈） */
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
        // 首进即拉解读模型配置（2026-11-07 双轴审查 B1）：原先仅在配置页打开/保存/删除后刷新，
        // 已配置用户首进看不到掩码回显、divUseEnabled=0 的行内置灰也不生效
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

    // -------------------- BYOK：查询与建议目录 --------------------

    /**
     * 拉取最新 BYOK 配置（配置页打开与保存/删除成功后调用；设置页回到前台亦调用以刷新行内回显）。
     * 失败置 Error 供配置页展示重试；行内展示保持最近一次成功数据（无则未配置态）。
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

    /** 拉取平台模型目录树（目录模式分步选择；失败静默为空——目录属可选增强，可切自定义模式） */
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

    /** 清空全部字段级校验错误（切换模式时两模式字段集不同，旧模式的报错不再适用） */
    fun clearFieldErrors() {
        _apiFieldError.value = null
    }

    /**
     * 表单校验（双模式，2026-11-11 契约）：apiKey 两模式均必填；目录模式须选中目录条目
     * （modelConfigId），自定义模式三字段均必填且 baseUrl 须 https 前缀（与服务端 SSRF
     * 校验同口径，端上先拦一次给即时文案）。错误置 [apiFieldError]，通过返回 true。
     */
    private fun validate(
        mode: ApiConfigMode,
        modelConfigId: Long?,
        baseUrl: String,
        model: String,
        apiKey: String,
    ): Boolean {
        val error = when (mode) {
            ApiConfigMode.CATALOG -> when {
                modelConfigId == null ->
                    ApiConfigFieldError(ApiConfigField.MODEL_CONFIG, R.string.api_config_error_model_config_empty)
                apiKey.isBlank() ->
                    ApiConfigFieldError(ApiConfigField.API_KEY, R.string.api_config_error_key_empty)
                else -> null
            }
            ApiConfigMode.CUSTOM -> when {
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
        }
        _apiFieldError.value = error
        return error == null
    }

    /** 按模式组装请求体（双模式二选一；apiKey 明文仅存在于本次请求体内存态） */
    private fun buildRequest(
        mode: ApiConfigMode,
        modelConfigId: Long?,
        baseUrl: String,
        model: String,
        apiKey: String,
    ): SaveUserApiConfig = if (mode == ApiConfigMode.CATALOG) {
        SaveUserApiConfig(modelConfigId = modelConfigId, apiKey = apiKey)
    } else {
        SaveUserApiConfig(baseUrl = baseUrl.trim(), model = model.trim(), apiKey = apiKey)
    }

    /**
     * 连通性测试（POST test，不保存）：后端试呼失败也回 200 信封（data=false），
     * 需端上自行转失败文案；网络异常经 NetworkException 带可读 message。
     */
    fun testApiConnection(
        mode: ApiConfigMode,
        modelConfigId: Long?,
        baseUrl: String,
        model: String,
        apiKey: String,
    ) {
        if (!validate(mode, modelConfigId, baseUrl, model, apiKey)) return
        _apiTestState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val passed = divSettingRepository.testApiConfig(
                    buildRequest(mode, modelConfigId, baseUrl, model, apiKey),
                )
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
     * 保存配置（PUT，双模式二选一）：服务端先真实试呼，失败信封 data 带原因文案
     * （ResponseInterceptor 已解析进 NetworkException.message）。成功后刷新配置供
     * 行内掩码/徽标更新。
     */
    fun saveApiConfig(
        mode: ApiConfigMode,
        modelConfigId: Long?,
        baseUrl: String,
        model: String,
        apiKey: String,
    ) {
        if (!validate(mode, modelConfigId, baseUrl, model, apiKey)) return
        _apiSaveState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                divSettingRepository.saveApiConfig(
                    buildRequest(mode, modelConfigId, baseUrl, model, apiKey),
                )
                // 成功日志不含任何密钥信息（掩码由服务端生成下发）
                AppLogger.info(TAG, "解读模型配置保存成功 mode=$mode")
                _apiSaveState.value = RequestState.Success
                refreshApiConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _apiSaveState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.warn(TAG, "解读模型配置保存失败", e)
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

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "SettingViewModel"
    }
}
