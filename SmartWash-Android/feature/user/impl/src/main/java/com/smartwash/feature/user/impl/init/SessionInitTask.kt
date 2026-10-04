package com.smartwash.feature.user.impl.init

import android.util.Log
import com.smartwash.common.init.InitTask
import com.smartwash.feature.user.impl.session.SessionManager
import kotlinx.coroutines.CancellationException

/**
 * 会话预热任务（T5.2 自 App.onCreate 的 warmUp() 直调迁入，InitEngine 统一调度）。
 *
 * blocking=true + priority=10（高优先级，先于 UpdateInitTask 的 900 出队）：
 * 把 DataStore 中的 token/role 读入 SessionManager 内存缓存，返回即代表后续
 * 拦截器同步读 currentToken() 可命中——启动早期的鉴权请求不再依赖预热竞态。
 *
 * 与原 warmUp()（fire-and-forget）的差异：execute 挂起读 [SessionManager.getToken]，
 * 阻塞语义真实成立（不是"启动了预热"而是"预热完成"）。
 * 失败降级：DataStore 读取异常仅记日志不短路启动链（token 视为空 → 请求未带鉴权 →
 * 服务端 401 → 走既有重新登录流程，行为可恢复）。
 */
class SessionInitTask(
    private val sessionManager: SessionManager,
) : InitTask() {

    override val taskId: String = "sessionWarmUp"
    override val priority: Int = 10
    override val blocking: Boolean = true

    override suspend fun execute() {
        try {
            // 挂起读 token/role：命中内存缓存即返回，未命中读 DataStore 并回填
            sessionManager.getToken()
            sessionManager.getRole()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Session warm-up failed, token treated as absent", e)
        }
    }

    private companion object {
        const val TAG = "SessionInitTask"
    }
}
