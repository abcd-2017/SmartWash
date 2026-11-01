package com.smartwash.init

import android.util.Log
import com.smartwash.config.ServerConfig
import com.smartwash.network.api.ServerConfigApi
import com.smartwash.common.init.InitTask

/**
 * 服务端公开配置预热启动任务。
 *
 * 拉 `GET /web/config` 把 MinIO 对外端点 / 短链对外域名写入 [ServerConfig]，
 * 供 WebView 深色主题桥白名单等运行期读取——替代原编译期注入（BuildConfig.MINIO_ENDPOINT），
 * MinIO 域名变更不再需要重新发版。
 *
 * 经 di/ServerConfigModule 以 @IntoSet 注册进 InitTaskRegistry，App.onCreate 由 InitEngine 调度：
 * 非阻塞（不拖慢启动返回）、中优先级（300，晚于会话预热 10、早于静默更新检查 900）。
 *
 * 失败静默：配置缺失时 WebView 白名单只含 API host，属可接受降级（游戏页由用户操作打开，
 * 此时配置早已就绪），仅记日志不打扰用户。
 */
class ServerConfigInitTask(
    private val api: ServerConfigApi,
) : InitTask() {

    override val taskId: String = "serverConfig"

    override val priority: Int = 300

    override val blocking: Boolean = false

    override suspend fun execute() {
        try {
            val config = api.getConfig().data
            if (config != null) {
                config.minioEndpoint?.takeIf { it.isNotBlank() }?.let { ServerConfig.minioEndpoint = it }
                config.shortLinkBaseUrl?.takeIf { it.isNotBlank() }?.let { ServerConfig.shortLinkBaseUrl = it }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Server config prefetch failed (degrade: WebView whitelist uses API host only)", e)
        }
    }

    private companion object {
        const val TAG = "ServerConfigInitTask"
    }
}
