package com.smartwash.feature.update.init

import com.smartwash.common.init.InitTask
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.update.event.UpdateEventBus
import com.smartwash.feature.update.repository.AppUpdateRepository
import com.smartwash.feature.update.settings.UpdateSettingsStore
import kotlinx.coroutines.CancellationException

/**
 * 启动静默版本检查任务（InitTask 首个业务接入）。
 *
 * 经 di/UpdateInitModule 以 @IntoSet 注册进 InitTaskRegistry，App.onCreate 由
 * InitEngine 调度：非阻塞（launch 后不拖慢启动返回）、低优先级（900，晚于其他
 * 就绪任务出队），网络检查直接挂起（默认超时 10s）。
 *
 * 发现新版本后的分流（2026-11-12 用户设置「自动更新」）：
 * 1. 命中用户「不再提醒」的版本（且非强制更新）→ 跳过，不广播；
 * 2. 强制更新 → 广播 UpdateEvent.UpdateAvailable（必须让用户看到停服说明，不自动下载）；
 * 3. 已开启自动更新 → 广播 UpdateEvent.AutoDownload，壳层直接下载；
 * 4. 其余 → 广播 UpdateEvent.UpdateAvailable，壳层弹更新说明弹窗。
 */
class UpdateInitTask(
    private val repository: AppUpdateRepository,
    private val eventBus: UpdateEventBus,
    private val settings: UpdateSettingsStore,
) : InitTask() {

    override val taskId: String = "updateCheck"
    override val priority: Int = 900
    override val blocking: Boolean = false

    override suspend fun execute() {
        AppLogger.debug(TAG, "启动静默版本检查（非阻塞，priority=$priority）")

        // 设置载入（幂等）：失败按默认值继续——不自动更新、不忽略任何版本
        runCatching { settings.load() }
            .onFailure { AppLogger.error(TAG, "更新设置载入失败，按默认值继续", it) }

        try {
            val version = repository.getLatestVersion()
            if (!repository.shouldUpdate(version.versionCode)) {
                AppLogger.debug(TAG, "已是最新版本（本地 versionCode=${repository.getCurrentVersionCode()}），跳过更新提示")
                return
            }

            // 「不再提醒」只压制非强制版本：强制更新必须让用户看到
            if (!version.forceUpdate && settings.isVersionIgnored(version.versionCode)) {
                AppLogger.debug(
                    TAG,
                    "版本 versionCode=${version.versionCode} 已被用户设为不再提醒，跳过更新提示",
                )
                return
            }

            AppLogger.info(
                TAG,
                "发现新版本：versionName=${version.versionName} versionCode=${version.versionCode}" +
                    "（本地 versionCode=${repository.getCurrentVersionCode()}）forceUpdate=${version.forceUpdate}" +
                    " autoUpdate=${settings.autoUpdateEnabled}",
            )

            when {
                version.forceUpdate -> {
                    // 强制更新：不自动下载，必须让用户看到说明并主动确认
                    eventBus.notifyUpdateAvailable(version)
                }
                settings.autoUpdateEnabled -> {
                    // 用户已授权自动更新：直接后台下载，完成后弹「安装包已就位」
                    eventBus.notifyAutoDownload(version)
                }
                else -> eventBus.notifyUpdateAvailable(version)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 静默检查：失败不打扰用户（原 checkForUpdate(silent=true) 语义），仅记日志；
            // 属外部调用失败（版本检查网络失败），按级别约定记 error
            AppLogger.error(TAG, "启动静默版本检查失败（本地 versionCode=${repository.getCurrentVersionCode()}）", e)
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "UpdateInitTask"
    }
}
