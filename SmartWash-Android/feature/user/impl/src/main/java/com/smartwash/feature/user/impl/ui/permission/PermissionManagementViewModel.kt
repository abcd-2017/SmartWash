package com.smartwash.feature.user.impl.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.HapticSettings
import com.smartwash.common.utils.SharePreferenceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 权限管理页 ViewModel：承载「触感反馈」开关（2026-11-12 自设置页迁入）。
 *
 * 为什么从设置页迁来：本页已是「用户可随时开关的权限与设置」的归集处
 * （通知权限 / 安装未知应用），触感开关与两者同性质，放一起内聚。
 *
 * 迁移时一并修复了原设置页实现的两处缺陷（用户反馈「震动管理不生效」）：
 * 1. **落盘被页面销毁取消**：原写在 `viewModelScope` 里 fire-and-forget，
 *    用户切换后一切走设置页，VM 即清空、写盘协程被取消，开关静默回滚、重启丢失。
 *    现包在 [NonCancellable] 中，VM 清空后仍把写入做完。
 * 2. **初值补读覆盖用户切换**：原 init 无条件用 DataStore 值覆写内存态，
 *    DataStore 首读是异步的，用户在初读完成前切换会被旧值盖回。
 *    现以 [userToggled] 守卫：用户动过之后初值补读不再生效。
 */
@HiltViewModel
class PermissionManagementViewModel @Inject constructor(
    private val prefs: SharePreferenceUtils,
) : ViewModel() {

    private val _hapticEnabled = MutableStateFlow(HapticSettings.enabled)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    /** 用户是否已手动切换过：为真后初值补读不再覆盖内存态 */
    private var userToggled = false

    init {
        // 初值补读：覆盖启动预热任务（HapticSettingsInitTask）尚未跑完就进本页的窗口
        viewModelScope.launch {
            runCatching { prefs.getData(HapticSettings.STORAGE_KEY, true) }
                .onSuccess {
                    if (!userToggled) {
                        HapticSettings.enabled = it
                        _hapticEnabled.value = it
                    }
                }
                // 降级：读取失败保持默认值（开启）
                .onFailure { AppLogger.warn(TAG, "触感反馈开关初值读取失败，使用默认值", it) }
        }
    }

    /** 切换触感反馈：内存态即时生效（热路径），落盘用 [NonCancellable] 保证不被页面销毁取消 */
    fun setHapticEnabled(enabled: Boolean) {
        userToggled = true
        HapticSettings.enabled = enabled
        _hapticEnabled.value = enabled
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching { prefs.saveData(HapticSettings.STORAGE_KEY, enabled) }
                    .onFailure {
                        // 降级：落盘失败回滚内存态（本次会话内生效，重启后丢失）
                        AppLogger.warn(TAG, "触感反馈开关落盘失败，已回滚内存态", it)
                        HapticSettings.enabled = !enabled
                        _hapticEnabled.value = !enabled
                    }
            }
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "PermissionManagementVM"
    }
}
