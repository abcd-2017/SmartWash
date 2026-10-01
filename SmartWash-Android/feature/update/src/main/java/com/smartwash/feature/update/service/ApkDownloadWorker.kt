package com.smartwash.feature.update.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * APK 后台下载 Worker
 * 通过 WorkManager 调度，下载更新包到 cacheDir，每 5% 回调进度。
 * 前台服务通知：Android 12+ 通过 setForegroundAsync 提升为前台服务，
 * 在通知栏展示下载进度，防止下载被系统杀死。
 */
@HiltWorker
class ApkDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val APK_FILE_NAME = "smartwash_update.apk"
        const val KEY_APK_URL = "apk_url"
        const val KEY_SHA256 = "sha256"
        const val KEY_PROGRESS = "progress"
        const val KEY_APK_PATH = "apk_path"
        const val KEY_ERROR = "error"

        /** 进度回调间隔（每 5% 一次） */
        private const val PROGRESS_INTERVAL = 5

        // 通知渠道 ID（与应用同名，避免重复创建）
        private const val CHANNEL_ID = "smartwash_update_download"
        private const val CHANNEL_NAME = "应用更新下载"

        // 通知 ID（固定，保证进度更新是同一通知）
        private const val NOTIFICATION_ID = 0x1001
    }

    override suspend fun doWork(): Result {
        val apkUrl = inputData.getString(KEY_APK_URL)
            ?: return Result.failure(workDataOf(KEY_ERROR to "APK 下载地址为空"))
        val expectedSha256 = inputData.getString(KEY_SHA256)?.takeIf { it.isNotBlank() }
        val versionName = inputData.getString("versionName") ?: ""

        val apkFile = File(applicationContext.cacheDir, APK_FILE_NAME)

        try {
            // 清理旧文件
            if (apkFile.exists()) apkFile.delete()

            // 创建通知渠道 + 启动前台服务（Android 12+ 必须调用 setForegroundAsync）
            createNotificationChannel()
            setForeground(ForegroundInfo(NOTIFICATION_ID, buildProgressNotification(0, 0L, 0L)))

            val request = Request.Builder().url(apkUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorMsg = "下载失败: HTTP ${response.code}"
                setForeground(ForegroundInfo(NOTIFICATION_ID, buildFailedNotification(errorMsg)))
                return Result.failure(workDataOf(KEY_ERROR to errorMsg))
            }

            val body = response.body ?: run {
                val errorMsg = "下载失败: 空响应体"
                setForeground(ForegroundInfo(NOTIFICATION_ID, buildFailedNotification(errorMsg)))
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
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                                // 更新通知栏进度
                                setForeground(ForegroundInfo(
                                    NOTIFICATION_ID,
                                    buildProgressNotification(progress, downloadedBytes, totalBytes)
                                ))
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
                    setForeground(ForegroundInfo(NOTIFICATION_ID, buildFailedNotification(errorMsg)))
                    return Result.failure(workDataOf(KEY_ERROR to errorMsg))
                }
            }

            // 下载完成：展示完成通知（点击可触发安装，由 UpdateFlow 监听 SUCCEEDED 状态处理）
            setForeground(ForegroundInfo(
                NOTIFICATION_ID,
                buildCompleteNotification(versionName)
            ))

            // 下载完成，返回文件路径
            return Result.success(
                workDataOf(
                    KEY_APK_PATH to apkFile.absolutePath,
                    KEY_PROGRESS to 100
                )
            )
        } catch (e: CancellationException) {
            // 取消时删除残件并向上传播
            if (apkFile.exists()) apkFile.delete()
            throw e
        } catch (e: Exception) {
            // 失败时删除残件
            if (apkFile.exists()) apkFile.delete()
            val errorMsg = e.message ?: "下载失败"
            setForeground(ForegroundInfo(NOTIFICATION_ID, buildFailedNotification(errorMsg)))
            return Result.failure(workDataOf(KEY_ERROR to errorMsg))
        }
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需；已存在则幂等）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // 低重要性：不发出声音，只在通知栏显示
            ).apply {
                description = "显示 APK 更新包的下载进度"
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
            "${formatFileSize(downloaded)} / ${formatFileSize(total)}"
        } else {
            formatFileSize(downloaded)
        }

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 SmartWash 更新")
            .setContentText(contentText)
            .setOngoing(true) // 进行中，用户不可滑动删除
            .setSilent(true) // 静默通知，不发出声音
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress.coerceIn(0, 100), total <= 0) // total 未知时显示不确定进度条
            .setOnlyAlertOnce(true) // 仅首次弹出提醒，后续更新不打扰
            .build()
    }

    /**
     * 构建下载完成通知
     */
    private fun buildCompleteNotification(versionName: String): Notification {
        val title = "更新包下载完成"
        val text = if (versionName.isNotBlank()) {
            "点击安装 SmartWash $versionName"
        } else {
            "点击安装 SmartWash 更新"
        }

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true) // 点击后自动消失
            .setSilent(false) // 完成时允许提示音
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * 构建下载失败通知
     */
    private fun buildFailedNotification(error: String): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("下载失败")
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
