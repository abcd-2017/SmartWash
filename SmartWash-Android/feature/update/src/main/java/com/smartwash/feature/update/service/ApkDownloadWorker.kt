package com.smartwash.feature.update.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.update.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import javax.inject.Named

/**
 * APK 后台下载 Worker
 * 通过 WorkManager 调度，下载更新包到 cacheDir，每 5% 回调进度。
 * 前台服务通知：经 setForeground 提升为前台服务在通知栏展示下载进度，防止下载被系统杀死；
 * targetSdk 34+ 前台服务必须带类型启动，类型统一由 buildForegroundInfo 注入
 * （清单侧 SystemForegroundService 的 foregroundServiceType=dataSync 声明见 :feature:update Manifest）。
 *
 * ## 为什么进度/完成/失败通知要经 [NotificationManagerCompat] 再发一次（2026-11-02）
 *
 * `setForeground` 的通知与前台服务生命周期绑定，两条路径会让通知「没有动静」：
 *
 * 1. **前台服务启动被系统拒绝**：WorkManager 的 `SystemForegroundService.Api31Impl.startForeground`
 *    捕获 `ForegroundServiceStartNotAllowedException` 后**只打日志不抛出**（App 不在前台态时启动
 *    前台服务会被 Android 12+ 拒绝）——此时 Worker 照常下载、应用内进度弹窗照常，但通知栏
 *    一条都没有。App 侧 [com.smartwash.ui.page.update.UpdateFlow] 已把「入队下载」收敛到
 *    Activity RESUMED 之后，从源头规避；这里再发一次独立通知做兜底。
 * 2. **完成通知被回收**：Worker 结束时 WorkManager 经 `SystemForegroundDispatcher.onExecuted`
 *    → `cancelNotification(foregroundInfo.notificationId)` 撤掉前台通知，因此「下载完成、
 *    点击安装」这条**唯一安装入口**（用户已划掉 App 时）必须用**独立通知 ID** 发出，
 *    否则跟着前台通知一起消失。
 *
 * 进度通知与前台通知同 ID（内容一致，后发覆盖先发布，无重复）；完成/失败用独立 ID 常驻。
 */
@HiltWorker
class ApkDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    @Named("download") private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val APK_FILE_NAME = "smartwash_update.apk"
        const val KEY_APK_URL = "apk_url"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERSION_NAME = "versionName"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_APK_PATH = "apk_path"
        const val KEY_ERROR = "error"

        /** 进度回调间隔（每 5% 一次） */
        private const val PROGRESS_INTERVAL = 5

        // 通知渠道 ID（与应用同名，避免重复创建）
        private const val CHANNEL_ID = "smartwash_update_download"

        /** 进度通知 ID：与前台服务通知同 ID（内容一致，后发覆盖），保证任一条路径发出都可见 */
        internal const val NOTIFICATION_ID = 0x1001

        /**
         * 完成/失败通知使用**独立 ID**：WorkManager 在 Worker 结束时按
         * `ForegroundInfo.notificationId` 撤掉前台通知，同 ID 的完成通知会被一并回收
         * （见类注释第 2 条）。独立 ID 才能让「点击安装」常驻。
         * internal（非 private）供回归测试锚定「完成/失败 ID ≠ 进度 ID」这条不变量。
         */
        internal const val NOTIFICATION_ID_COMPLETE = 0x1002
        internal const val NOTIFICATION_ID_FAILED = 0x1003

        // 前台服务类型（minSdk 30，常量可直接引用；须与 Manifest 声明一致）
        private const val FGS_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        private const val TAG = "ApkDownloadWorker"

        /**
         * 构建下载完成通知（companion 暴露供回归测试直接构建）。
         * 必须携带指向系统安装器的 contentIntent：下载期间用户可能已划掉 App，
         * 完成通知的「点击安装」是那时唯一的安装入口。
         */
        fun buildCompleteNotification(context: Context, versionName: String, apkFile: File): Notification {
            val title = context.getString(R.string.download_notification_complete)
            val text = if (versionName.isNotBlank()) {
                context.getString(R.string.download_notification_complete_tap, versionName)
            } else {
                context.getString(R.string.download_notification_complete)
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(ApkInstaller.getFileUri(context, apkFile), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                installIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true) // 点击后自动消失
                .setSilent(false) // 完成时允许提示音
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        }
    }

    /**
     * WorkManager 在系统恢复/提升前台（如 expedited 调度、进程死亡重投递）时，
     * 经本方法获取前台信息；未覆写时基类抛 IllegalStateException。
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(buildProgressNotification(0, 0L, 0L))

    override suspend fun doWork(): Result {
        val apkUrl = inputData.getString(KEY_APK_URL) ?: run {
            // 任务入参契约被破坏（调度侧未写入下载地址）：放弃并告警
            AppLogger.warn(TAG, "Worker 输入缺少 APK 下载地址（$KEY_APK_URL），放弃任务")
            return Result.failure(workDataOf(KEY_ERROR to "APK 下载地址为空"))
        }
        val expectedSha256 = inputData.getString(KEY_SHA256)?.takeIf { it.isNotBlank() }
        val versionName = inputData.getString(KEY_VERSION_NAME) ?: ""

        // 下载开始（低频关键动作）：URL 为 MinIO 预签名链接，查询参数含签名凭证，剥离后仅记 host+path
        AppLogger.info(
            TAG,
            "开始下载 APK：versionName=$versionName url=${apkUrl.substringBefore('?')} attempt=$runAttemptCount",
        )
        if (runAttemptCount > 0) {
            AppLogger.warn(TAG, "APK 下载任务第 $runAttemptCount 次重跑（进程死亡后重投递），继续下载")
        }

        val apkFile = File(applicationContext.cacheDir, APK_FILE_NAME)

        try {
            // 清理旧文件
            if (apkFile.exists()) apkFile.delete()

            // 创建通知渠道 + 提升为前台服务
            createNotificationChannel()
            setProgressNotification(0, 0L, 0L)

            val request = Request.Builder().url(apkUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorMsg = "下载失败: HTTP ${response.code}"
                AppLogger.error(TAG, "APK 下载失败：HTTP ${response.code} url=${apkUrl.substringBefore('?')}")
                setForeground(buildForegroundInfo(buildFailedNotification(errorMsg)))
                notifyFailed(errorMsg)
                return Result.failure(workDataOf(KEY_ERROR to errorMsg))
            }

            val body = response.body ?: run {
                val errorMsg = "下载失败: 空响应体"
                AppLogger.error(TAG, "APK 下载失败：响应体为空 url=${apkUrl.substringBefore('?')}")
                setForeground(buildForegroundInfo(buildFailedNotification(errorMsg)))
                notifyFailed(errorMsg)
                return Result.failure(workDataOf(KEY_ERROR to errorMsg))
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            var lastReportedProgress = 0

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        yield() // 检查取消并让出线程
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                            if (progress - lastReportedProgress >= PROGRESS_INTERVAL) {
                                lastReportedProgress = progress
                                setProgress(workDataOf(
                                    KEY_PROGRESS to progress,
                                    KEY_DOWNLOADED_BYTES to downloadedBytes,
                                    KEY_TOTAL_BYTES to totalBytes,
                                ))
                                // 更新通知栏进度
                                setProgressNotification(progress, downloadedBytes, totalBytes)
                            }
                        }
                    }
                    output.flush()
                }
            }

            // 校验 SHA256（如果服务端提供了）
            if (expectedSha256 != null) {
                val actualSha256 = apkFile.sha256()
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    apkFile.delete()
                    val errorMsg = "文件校验失败，SHA256 不匹配"
                    AppLogger.error(TAG, "APK 下载失败：SHA256 校验不匹配 expected=$expectedSha256 actual=$actualSha256")
                    setForeground(buildForegroundInfo(buildFailedNotification(errorMsg)))
                    notifyFailed(errorMsg)
                    return Result.failure(workDataOf(KEY_ERROR to errorMsg))
                }
            }

            // 下载完成（低频关键动作）：文件大小与落盘路径一并记录
            AppLogger.info(
                TAG,
                "APK 下载完成：versionName=$versionName size=${formatFileSize(apkFile.length())} path=${apkFile.absolutePath}",
            )

            // 下载完成：完成通知走独立 ID（Worker 结束时被回收的是前台通知，不是它），
            // 保证用户划掉 App 后仍有「点击安装」入口
            notifyComplete(versionName, apkFile)

            // 下载完成，返回文件路径
            return Result.success(
                workDataOf(
                    KEY_APK_PATH to apkFile.absolutePath,
                    KEY_PROGRESS to 100
                )
            )
        } catch (e: CancellationException) {
            // 取消时删除残件并向上传播；取消属正常路径，仅 debug
            AppLogger.debug(TAG, "APK 下载被取消，清理残留文件")
            if (apkFile.exists()) apkFile.delete()
            throw e
        } catch (e: Exception) {
            // 失败时删除残件
            AppLogger.error(TAG, "APK 下载异常", e)
            if (apkFile.exists()) apkFile.delete()
            val errorMsg = e.message ?: "下载失败"
            setForeground(buildForegroundInfo(buildFailedNotification(errorMsg)))
            notifyFailed(errorMsg)
            return Result.failure(workDataOf(KEY_ERROR to errorMsg))
        }
    }

    /**
     * 构建携带 dataSync 类型的 ForegroundInfo。
     * targetSdk 34+ 启动前台服务必须显式携带类型，且类型须为清单
     * SystemForegroundService foregroundServiceType 声明的子集，二者缺一即抛
     * InvalidForegroundServiceTypeException；所有 setForeground 调用点必须经此构造。
     */
    private fun buildForegroundInfo(notification: Notification): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, notification, FGS_TYPE)

    /**
     * 进度通知：既走 setForeground（前台服务保活 + 通知），也经 [NotificationManagerCompat]
     * 直发一份（同 ID 同内容，覆盖式更新）。两条路径任一成功，通知栏就有进度。
     */
    private suspend fun setProgressNotification(progress: Int, downloaded: Long, total: Long) {
        val notification = buildProgressNotification(progress, downloaded, total)
        setForeground(buildForegroundInfo(notification))
        notify(NOTIFICATION_ID, notification)
    }

    /** 完成通知：独立 ID，Worker 结束回收前台通知时不被连带清除 */
    private fun notifyComplete(versionName: String, apkFile: File) {
        notify(NOTIFICATION_ID_COMPLETE, buildCompleteNotification(applicationContext, versionName, apkFile))
    }

    /** 失败通知：独立 ID，常驻供用户看到失败原因 */
    private fun notifyFailed(error: String) {
        notify(NOTIFICATION_ID_FAILED, buildFailedNotification(error))
    }

    /** 「通知不可用」降级告警去重标记：进度通知每 5% 触发一次 notify，同一轮下载仅告警一次（Worker 每次运行重建实例，标记按轮生效） */
    private var notificationUnavailableLogged = false

    /**
     * 经 [NotificationManagerCompat] 直发通知（独立于前台服务生命周期）。
     * 通知权限被拒 / 用户在系统设置关掉通知时 [NotificationManagerCompat.areNotificationsEnabled]
     * 为 false——此时静默跳过并留日志，不再叠加一条永远发不出去的通知。
     */
    private fun notify(notificationId: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) {
            if (!notificationUnavailableLogged) {
                notificationUnavailableLogged = true
                AppLogger.warn(TAG, "通知不可用（权限未授或系统设置已关闭），跳过通知 id=$notificationId（本轮仅告警一次）")
            }
            return
        }
        manager.notify(notificationId, notification)
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需；已存在则幂等）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW // 低重要性：不发出声音，只在通知栏显示
            ).apply {
                description = applicationContext.getString(R.string.download_channel_desc)
                setShowBadge(false)
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建下载进度通知
     */
    private fun buildProgressNotification(progress: Int, downloaded: Long, total: Long): Notification {
        val contentText = if (total > 0) {
            applicationContext.getString(
                R.string.download_notification_progress,
                formatFileSize(downloaded),
                formatFileSize(total),
            )
        } else {
            formatFileSize(downloaded)
        }

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.download_notification_title))
            .setContentText(contentText)
            .setOngoing(true) // 进行中，用户不可滑动删除
            .setSilent(true) // 静默通知，不发出声音
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress.coerceIn(0, 100), total <= 0) // total 未知时显示不确定进度条
            .setOnlyAlertOnce(true) // 仅首次弹出提醒，后续更新不打扰
            .build()
    }

    /**
     * 构建下载失败通知
     */
    private fun buildFailedNotification(error: String): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(applicationContext.getString(R.string.download_notification_failed))
            .setContentText(error)
            .setAutoCancel(true)
            .setSilent(false) // 失败时允许提示音
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * 将字节数格式化为可读大小（B / KB / MB）
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024
        return when {
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}

/**
 * 计算文件的 SHA256 哈希值（十六进制小写）
 */
fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    this.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
