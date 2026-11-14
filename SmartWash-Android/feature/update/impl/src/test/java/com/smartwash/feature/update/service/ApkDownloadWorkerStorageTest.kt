package com.smartwash.feature.update.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * APK 落盘目录契约回归（2026-11-15 用户反馈「更新下载失败」）。
 *
 * 症状：`FileNotFoundException: /data/user/0/com.smartwash/files/update/smartwash_update_1008.apk:
 * open failed: ENOENT (No such file or directory)`。
 *
 * 根因：[ApkDownloadWorker.doWork] 直接用 `FileOutputStream(apkFile)` 打开
 * `filesDir/update/` 下的文件，而生产代码从未创建该目录——首次安装、或系统清理后必现。
 * 本用例在 JVM 侧锚定「apkFile 路径的父目录必须存在，否则落盘即 ENOENT」这条不变量。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkDownloadWorkerStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        ApkDownloadWorker.apkDir(context).deleteRecursively()
    }

    @Test
    fun `APK 目录不存在时 apkFile 的父目录应被创建`() {
        val dir = ApkDownloadWorker.apkDir(context)
        dir.deleteRecursively()
        assertFalse("前置：目录不存在，用于复现首次下载场景", dir.exists())

        val apkFile = ApkDownloadWorker.apkFile(context, 1008)

        assertTrue(
            "apkFile 父目录必须存在，否则首次下载 FileOutputStream 抛 ENOENT（用户症状）",
            apkFile.parentFile?.exists() == true,
        )
    }

    @Test
    fun `目录缺失时落盘 APK 不抛 ENOENT`() {
        val dir = ApkDownloadWorker.apkDir(context)
        dir.deleteRecursively()
        val apkFile = ApkDownloadWorker.apkFile(context, 1008)

        // 与 doWork 相同路径：FileOutputStream(apkFile)；修复前抛 FileNotFoundException
        apkFile.outputStream().use { it.write(byteArrayOf(0x50, 0x4B)) }

        assertTrue("APK 应成功落盘且非空", apkFile.exists() && apkFile.length() > 0)
    }
}
