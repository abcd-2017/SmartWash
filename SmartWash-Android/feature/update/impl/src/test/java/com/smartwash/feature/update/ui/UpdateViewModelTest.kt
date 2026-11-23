package com.smartwash.feature.update.ui

import androidx.test.core.app.ApplicationProvider
import com.smartwash.common.utils.SharePreferenceUtils
import com.smartwash.feature.update.api.model.AppVersionVo
import com.smartwash.feature.update.api.network.AppUpdateApi
import com.smartwash.feature.update.repository.AppUpdateRepository
import com.smartwash.feature.update.settings.UpdateSettingsStore
import com.smartwash.common.utils.model.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 更新状态机 JVM 单测（Robolectric 供 Context 构造 AppUpdateRepository/UpdateSettingsStore；
 * Main 调度器注入测试调度器）。
 *
 * 锚点契约（app 壳层更新弹窗分流依据）：
 * - onUpdateAvailable / onAutoDownload 事件入口分别落到 UpdateAvailable / Preparing 状态，
 *   自动下载不弹说明弹窗（_autoDownload = true）；
 * - 事件重放防打扰：reset() 记录已关闭版本，同版本事件再次到达被忽略；
 * - 下载流程中（Downloading/Downloaded/Installing）到达的版本事件不打断进度；
 * - 下载回调三态：进度 → Downloading；完成 → Downloaded（后台/自动标记复位）；失败 → Error(Download)。
 *
 * 注意：setAutoUpdateEnabled/ignoreVersion 涉及 DataStore 落盘（进程级单例路径与
 * Robolectric 每用例临时目录错位），本类不覆盖落盘路径——落盘语义由
 * :feature:update:core 的 UpdateSettingsStore 消费方在真机链路回归。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** AppUpdateApi 手写桩：预签名地址可编程 + 调用计数 */
    private class FakeAppUpdateApi : AppUpdateApi {
        var presignedUrl: String? = "https://minio.example.com/android/app-1008.apk?sig=abc"
        var presignedCalls = 0
            private set

        override suspend fun getLatestVersion(channel: String): ApiResult<AppVersionVo> =
            ApiResult(200, "成功", null)

        override suspend fun getPresignedDownloadUrl(channel: String): ApiResult<String> {
            presignedCalls++
            return ApiResult(200, "成功", presignedUrl)
        }
    }

    private fun version(code: Int, force: Boolean = false) = AppVersionVo(
        versionCode = code,
        versionName = "1.$code.0",
        apkUrl = "https://cdn.example.com/app-$code.apk",
        forceUpdate = force,
    )

    private lateinit var api: FakeAppUpdateApi

    private fun newViewModel(): UpdateViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = UpdateSettingsStore(SharePreferenceUtils(context))
        val repository = AppUpdateRepository(api, channel = "full", context = context)
        return UpdateViewModel(repository, settings)
    }

    @Before
    fun setUp() {
        api = FakeAppUpdateApi()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== 事件入口 ====================

    @Test
    fun `版本事件转 UpdateAvailable 状态且自动下载标记为假`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.onUpdateAvailable(version(1008))
        val state = vm.state.value as UpdateState.UpdateAvailable
        assertEquals(1008, state.version.versionCode)
        assertFalse(vm.autoDownload.value) // 说明弹窗路径：清掉残留自动下载标记
        assertEquals(version(1008), vm.getLatestVersion()) // 缓存供下载使用
    }

    @Test
    fun `自动下载事件转 Preparing 且自动下载标记为真`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.onAutoDownload(version(1008))
        assertTrue(vm.state.value is UpdateState.Preparing)
        assertTrue(vm.autoDownload.value) // 下载中不弹进度窗（静默下载走系统通知）
    }

    @Test
    fun `已关闭版本的事件重放被忽略`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.onUpdateAvailable(version(1008))
        vm.reset() // 用户关闭弹窗：记录 dismissedVersionCode
        assertEquals(UpdateState.Idle, vm.state.value)

        vm.onUpdateAvailable(version(1008)) // replay 重放同版本 → 忽略，不再打扰
        assertEquals(UpdateState.Idle, vm.state.value)

        vm.onUpdateAvailable(version(1009)) // 更高版本 → 正常弹出
        assertTrue(vm.state.value is UpdateState.UpdateAvailable)
    }

    @Test
    fun `自动下载事件命中已关闭版本同样忽略`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.onUpdateAvailable(version(1008))
        vm.reset()

        vm.onAutoDownload(version(1008)) // 重放同版本不再自动下载
        assertEquals(UpdateState.Idle, vm.state.value)
        assertFalse(vm.autoDownload.value)
    }

    @Test
    fun `下载流程中到达的版本事件不打断进度`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.onUpdateAvailable(version(1008))
        vm.onDownloadProgress(40, 400L, 1000L) // 进入下载中
        assertTrue(vm.state.value is UpdateState.Downloading)

        vm.onUpdateAvailable(version(1008)) // 重放事件在下载中到达 → 忽略
        assertTrue(vm.state.value is UpdateState.Downloading)
        vm.onUpdateAvailable(version(1009)) // 下载中任何版本事件都不打断进度
        assertTrue(vm.state.value is UpdateState.Downloading)
    }

    // ==================== 下载流程 ====================

    @Test
    fun `下载进度完成失败三态流转`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.onUpdateAvailable(version(1008))

        vm.onDownloadProgress(progress = 40, downloadedBytes = 400L, totalBytes = 1000L)
        val downloading = vm.state.value as UpdateState.Downloading
        assertEquals(40, downloading.progress)
        assertEquals(400L, downloading.downloadedBytes)
        assertEquals(1000L, downloading.totalBytes)

        // 后台下载中转后台：完成时复位标记让完成窗回弹
        vm.minimizeDownloadDialog()
        assertTrue(vm.backgroundDownload.value)
        vm.onDownloadComplete(java.io.File("/tmp/app.apk"))
        val downloaded = vm.state.value as UpdateState.Downloaded
        assertEquals("/tmp/app.apk", downloaded.file.path)
        assertFalse(vm.backgroundDownload.value)
        assertFalse(vm.autoDownload.value)

        // 失败：转 Error(Download 来源，含重试入口)
        vm.onDownloadFailed("磁盘空间不足")
        val error = vm.state.value as UpdateState.Error
        assertEquals("磁盘空间不足", error.message)
        assertEquals(ErrorSource.Download, error.source)
        assertFalse(vm.backgroundDownload.value)
    }

    @Test
    fun `安装中状态与状态重置`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.onUpdateAvailable(version(1008))
        vm.onInstallStarted()
        assertTrue(vm.state.value is UpdateState.Installing)

        vm.reset()
        assertEquals(UpdateState.Idle, vm.state.value)
        assertNull(vm.downloadUrl.value)
        assertFalse(vm.backgroundDownload.value)
        assertFalse(vm.autoDownload.value)
    }

    @Test
    fun `startDownload 获取预签名地址并暴露给 UI 层`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.onUpdateAvailable(version(1008))
        vm.startDownload(ApplicationProvider.getApplicationContext())
        advanceUntilIdle()

        assertEquals("https://minio.example.com/android/app-1008.apk?sig=abc", vm.downloadUrl.value)
        assertEquals(1, api.presignedCalls)

        vm.consumeDownloadUrl() // 消费防重复调度 Worker
        assertNull(vm.downloadUrl.value)
    }

    @Test
    fun `未持有最新版本时 startDownload 直接忽略`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // 推进 init 块的设置载入协程
        vm.startDownload(ApplicationProvider.getApplicationContext()) // 未先完成版本检查
        advanceUntilIdle()
        assertNull(vm.downloadUrl.value)
        assertEquals(0, api.presignedCalls)
        // 时序异常不打扰用户（状态仍为 Idle）
        assertEquals(UpdateState.Idle, vm.state.value)
    }

    // ==================== 手动检查状态机 ====================

    @Test
    fun `手动检查 有新版本转 UpdateAvailable 无新版本转 LatestVersion`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = UpdateSettingsStore(SharePreferenceUtils(context))
        // 本地 versionCode 以仓库读取值为准（Robolectric 默认 manifest 形态无关测试结论）
        val current = AppUpdateRepository(api, "full", context).getCurrentVersionCode()

        val higherApi = object : AppUpdateApi by api {
            override suspend fun getLatestVersion(channel: String): ApiResult<AppVersionVo> =
                ApiResult(200, "成功", version(current + 10))
        }
        val vm = UpdateViewModel(AppUpdateRepository(higherApi, "full", context), settings)
        vm.checkForUpdate(silent = false)
        advanceUntilIdle()
        assertTrue(vm.state.value is UpdateState.UpdateAvailable)

        val sameApi = object : AppUpdateApi by api {
            override suspend fun getLatestVersion(channel: String): ApiResult<AppVersionVo> =
                ApiResult(200, "成功", version(current)) // 远程与本地同版 → 已是最新
        }
        val vm2 = UpdateViewModel(AppUpdateRepository(sameApi, "full", context), settings)
        vm2.checkForUpdate(silent = false)
        advanceUntilIdle()
        assertTrue(vm2.state.value is UpdateState.LatestVersion)
    }

    @Test
    fun `静默检查失败回 Idle 不打断用户`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = UpdateSettingsStore(SharePreferenceUtils(context))
        val failingApi = object : AppUpdateApi by api {
            override suspend fun getLatestVersion(channel: String): ApiResult<AppVersionVo> =
                throw com.smartwash.common.network.exception.NetworkException("网络开小差了")
        }
        val vm = UpdateViewModel(AppUpdateRepository(failingApi, "full", context), settings)
        vm.checkForUpdate(silent = true)
        advanceUntilIdle()
        assertEquals(UpdateState.Idle, vm.state.value) // 静默检查失败回到 Idle，不打断用户

        vm.checkForUpdate(silent = false) // 手动检查失败 → Error 态（设置页 Toast 反馈）
        advanceUntilIdle()
        val error = vm.state.value as UpdateState.Error
        assertEquals("网络开小差了", error.message)
        assertEquals(ErrorSource.Check, error.source)
    }
}
