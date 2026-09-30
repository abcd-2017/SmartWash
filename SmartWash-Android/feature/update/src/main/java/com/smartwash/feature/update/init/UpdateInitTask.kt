package com.smartwash.feature.update.init

import android.util.Log
import com.smartwash.core.init.InitTask
import com.smartwash.feature.update.event.UpdateEventBus
import com.smartwash.feature.update.repository.AppUpdateRepository
import kotlinx.coroutines.CancellationException

/**
 * 启动静默版本检查任务（InitTask 首个业务接入）。
 *
 * 经 di/UpdateInitModule 以 @IntoSet 注册进 InitTaskRegistry，App.onCreate 由
 * InitEngine 调度：非阻塞（launch 后不拖慢启动返回）、低优先级（900，晚于其他
 * 就绪任务出队），网络检查直接挂起（默认超时 10s）。
 *
 * 发现新版本经 [UpdateEventBus] 广播，壳层收集事件驱动更新弹窗——
 * 替代原 MainActivity LaunchedEffect 延迟 1500ms 的检查时机。
 */
class UpdateInitTask(
    private val repository: AppUpdateRepository,
    private val eventBus: UpdateEventBus,
) : InitTask() {

    override val taskId: String = "updateCheck"
    override val priority: Int = 900
    override val blocking: Boolean = false

    override suspend fun execute() {
        try {
            val version = repository.getLatestVersion()
            if (repository.shouldUpdate(version.versionCode)) {
                eventBus.notifyUpdateAvailable(version)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 静默检查：失败不打扰用户（原 checkForUpdate(silent=true) 语义），仅记日志
            Log.w(TAG, "Silent update check failed on startup", e)
        }
    }

    private companion object {
        const val TAG = "UpdateInitTask"
    }
}
