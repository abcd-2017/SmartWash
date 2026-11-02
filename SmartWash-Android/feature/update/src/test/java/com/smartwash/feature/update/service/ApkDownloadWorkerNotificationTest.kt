package com.smartwash.feature.update.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 下载完成通知契约：下载期间用户可能已划掉 App，完成通知的「点击安装」
 * 是那时唯一的安装入口，contentIntent 缺失即点击无反应。
 *
 * 2026-11-02 补两条不变量（用户反馈「点后台下载后通知栏没动静」的回归锚点）：
 * 完成/失败通知必须使用**独立于进度通知的 ID**——WorkManager 在 Worker 结束时经
 * `SystemForegroundDispatcher.onExecuted` → `cancelNotification(foregroundInfo.notificationId)`
 * 撤掉前台通知，若完成通知与进度通知同 ID，会被连带清除，「点击安装」入口随之消失。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkDownloadWorkerNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        FileProviderCacheResetter.reset()
    }

    @Test
    fun `完成通知必须携带安装 contentIntent`() {
        val apkFile = File(context.cacheDir, ApkDownloadWorker.APK_FILE_NAME).apply {
            writeBytes(byteArrayOf(0x50, 0x4B))
        }

        val notification = ApkDownloadWorker.buildCompleteNotification(context, "1.2.3", apkFile)

        assertNotNull("完成通知缺少 contentIntent，App 被划掉后点击通知无任何反应", notification.contentIntent)
    }

    @Test
    fun `完成通知 ID 必须独立于进度通知 ID`() {
        assertNotEquals(
            "完成通知与进度通知同 ID，Worker 结束回收前台通知时「点击安装」入口会被连带清除",
            ApkDownloadWorker.NOTIFICATION_ID,
            ApkDownloadWorker.NOTIFICATION_ID_COMPLETE,
        )
    }

    @Test
    fun `失败通知 ID 必须独立于进度通知 ID`() {
        assertNotEquals(
            "失败通知与进度通知同 ID，失败原因会随前台通知一起被回收，用户无从知晓",
            ApkDownloadWorker.NOTIFICATION_ID,
            ApkDownloadWorker.NOTIFICATION_ID_FAILED,
        )
    }

    @Test
    fun `完成与失败通知 ID 互不相同`() {
        assertNotEquals(
            "完成与失败复用同一 ID 会互相覆盖，先失败后完成（或反之）只剩一条",
            ApkDownloadWorker.NOTIFICATION_ID_COMPLETE,
            ApkDownloadWorker.NOTIFICATION_ID_FAILED,
        )
    }

    @Test
    fun `通知 ID 非零且为正`() {
        // 0 是 NotificationManager 的非法 ID（WorkManager 的 handleNotify 以 0 为缺省值兜底）
        listOf(
            ApkDownloadWorker.NOTIFICATION_ID,
            ApkDownloadWorker.NOTIFICATION_ID_COMPLETE,
            ApkDownloadWorker.NOTIFICATION_ID_FAILED,
        ).forEach { id -> assertTrue("通知 ID 必须为正整数，got $id", id > 0) }
    }
}
