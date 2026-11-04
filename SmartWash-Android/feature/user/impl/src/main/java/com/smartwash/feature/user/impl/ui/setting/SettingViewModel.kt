package com.smartwash.feature.user.impl.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.HapticSettings
import com.smartwash.common.utils.SharePreferenceUtils
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel（T5.3）：承载登出的会话操作。
 *
 * 原实现由宿主 MainActivity 向 SettingPage 传 SessionManager/SessionEventBus 参数——
 * app 壳因此被迫触碰用户域实现类型。现依赖改为模块内 Hilt 自取，页面签名不再
 * 暴露 impl 类型，宿主只传导航与插槽。
 *
 * 2026-11-01 设置页接真实数据：新增触感反馈开关状态（内存态直读 [HapticSettings]
 * 保证热路径即时生效 + DataStore 异步落盘；启动初值由 HapticSettingsInitTask 预热）。
 */
@HiltViewModel
class SettingViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
    private val prefs: SharePreferenceUtils,
) : ViewModel() {

    private val _hapticEnabled = MutableStateFlow(HapticSettings.enabled)
    val hapticEnabled = _hapticEnabled.asStateFlow()

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

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "SettingViewModel"
    }
}
