package com.smartwash.feature.update.settings

import com.smartwash.common.utils.SharePreferenceUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新链路用户设置的持久化封装（DataStore，经 common:utils 的 [SharePreferenceUtils]）。
 *
 * 存两项：
 * - [autoUpdateEnabled]：设置页「自动更新」开关。开启后启动静默检查发现新版本即自动下载，
 *   不再弹更新说明弹窗（强制更新除外，见 UpdateInitTask 分流）。
 * - [ignoredVersionCode]：用户在更新弹窗勾选「不再提醒此版本」后记录的 versionCode。
 *   命中该版本（≤）时启动静默检查不再弹提示，直到服务端出现更高版本；
 *   手动「检查更新」不受影响（用户主动触发时应如实告知）。
 *
 * 内存态镜像供启动任务（UpdateInitTask）同步读取路径免挂起访问；写入由调用方
 * （UpdateViewModel）先改内存态让 UI 即时生效，再调本类的 suspend 方法落盘，
 * 落盘失败回滚内存态——与 SettingViewModel.setHapticEnabled 同口径。
 */
@Singleton
class UpdateSettingsStore @Inject constructor(
    private val prefs: SharePreferenceUtils,
) {
    /** 自动更新开关内存态（默认关闭：用户明确要求「开启以后」才自动下载） */
    @Volatile
    var autoUpdateEnabled: Boolean = false

    /** 已忽略的 versionCode 内存态（0 = 未忽略任何版本） */
    @Volatile
    var ignoredVersionCode: Int = 0

    /** 是否已完成首次载入（未载入时内存态为默认值） */
    @Volatile
    var loaded: Boolean = false
        private set

    /** 载入持久化值（幂等；启动任务与 ViewModel 初始化各调一次） */
    suspend fun load() {
        autoUpdateEnabled = prefs.getData(KEY_AUTO_UPDATE, false)
        ignoredVersionCode = prefs.getData(KEY_IGNORED_VERSION, 0)
        loaded = true
    }

    /**
     * 该版本是否已被用户「不再提醒」。
     * 只忽略不高于已记录值的版本：服务端后续推送更高版本时提示恢复。
     */
    fun isVersionIgnored(versionCode: Int): Boolean =
        ignoredVersionCode > 0 && versionCode <= ignoredVersionCode

    /** 落盘自动更新开关（内存态由调用方先行更新，失败时调用方回滚） */
    suspend fun persistAutoUpdateEnabled(enabled: Boolean) {
        prefs.saveData(KEY_AUTO_UPDATE, enabled)
    }

    /** 落盘已忽略版本（单调递增由调用方 UpdateViewModel.ignoreVersion 保证） */
    suspend fun persistIgnoredVersion(versionCode: Int) {
        prefs.saveData(KEY_IGNORED_VERSION, versionCode)
    }

    private companion object {
        const val KEY_AUTO_UPDATE = "update_auto_update_enabled"
        const val KEY_IGNORED_VERSION = "update_ignored_version_code"
    }
}
