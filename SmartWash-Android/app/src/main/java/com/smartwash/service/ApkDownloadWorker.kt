package com.smartwash.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
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
 * 通过 WorkManager 调度，下载更新包到 cacheDir，每 5% 回调进度
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
    }

    override suspend fun doWork(): Result {
        val apkUrl = inputData.getString(KEY_APK_URL)
            ?: return Result.failure(workDataOf(KEY_ERROR to "APK 下载地址为空"))
        val expectedSha256 = inputData.getString(KEY_SHA256)?.takeIf { it.isNotBlank() }

        val apkFile = File(applicationContext.cacheDir, APK_FILE_NAME)

        try {
            // 清理旧文件
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder().url(apkUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure(
                    workDataOf(KEY_ERROR to "下载失败: HTTP ${response.code}")
                )
            }

            val body = response.body ?: return Result.failure(
                workDataOf(KEY_ERROR to "下载失败: 空响应体")
            )

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
                    return Result.failure(
                        workDataOf(KEY_ERROR to "文件校验失败，SHA256 不匹配")
                    )
                }
            }

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
            return Result.failure(workDataOf(KEY_ERROR to (e.message ?: "下载失败")))
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
