package com.smartwash.feature.update.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 下载完成通知契约：下载期间用户可能已划掉 App，完成通知的「点击安装」
 * 是那时唯一的安装入口，contentIntent 缺失即点击无反应。
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
}
