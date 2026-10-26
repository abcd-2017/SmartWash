package com.smartwash.feature.update.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * 安装点击序列回归测试。
 *
 * 系统安装器经 FileProvider content URI 读取 APK 发生在 startActivity 返回之后，
 * 安装发起的调用序列里任何同步删除文件，都会让安装器读到空文件（「解析软件包时出现问题」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkInstallerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        Shadows.shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
    }

    @After
    fun tearDown() {
        FileProviderCacheResetter.reset()
    }

    private fun createCachedApk(): File =
        File(context.cacheDir, ApkDownloadWorker.APK_FILE_NAME).apply {
            writeBytes(byteArrayOf(0x50, 0x4B))
        }

    private fun nextStartedActivity(): Intent? =
        Shadows.shadowOf(context.applicationContext as Application).nextStartedActivity

    @Test
    fun `有权限时安装发起后 APK 必须保留供安装器异步读取`() {
        val apkFile = createCachedApk()

        val result = ApkInstaller.requestInstall(context, apkFile)

        assertEquals(InstallRequestResult.Installed, result)

        // 安装 Intent 确已发出：证明走的是安装分支，下方文件断言不是因分支未执行而假绿
        val intent = nextStartedActivity()
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("application/vnd.android.package-archive", intent?.type)

        assertTrue("安装发起后 APK 被同步删除，系统安装器将无法读取", apkFile.exists())
    }

    @Test
    fun `无权限时跳安装权限设置页且 APK 保留`() {
        Shadows.shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        val apkFile = createCachedApk()

        val result = ApkInstaller.requestInstall(context, apkFile)

        assertEquals(InstallRequestResult.NeedPermission, result)
        assertTrue(apkFile.exists())

        val intent = nextStartedActivity()
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent?.action)
    }
}
