package com.smartwash.lite.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.update.api.model.AppVersionVo
import com.smartwash.feature.update.repository.AppUpdateRepository
import com.smartwash.feature.update.service.ApkDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * lite 手动检查更新状态（对齐 full 的 UpdateState 子集，无「自动更新」与事件总线）。
 *
 * 状态机：Idle → Checking →（LatestVersion | UpdateAvailable）→ Preparing →
 * Downloading → Downloaded → Installing；任一步失败 → Error。
 */
sealed interface LiteUpdateState {
    data object Idle : LiteUpdateState
    data object Checking : LiteUpdateState
    data object LatestVersion : LiteUpdateState
    data class UpdateAvailable(val version: AppVersionVo) : LiteUpdateState
    data object Preparing : LiteUpdateState
    data class Downloading(
        val progress: Int,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : LiteUpdateState
    data class Downloaded(val file: File) : LiteUpdateState
    data object Installing : LiteUpdateState
    data class Error(val message: String) : LiteUpdateState
}

/**
 * lite 变体「检查更新」ViewModel（flavor 源集专属）。
 *
 * 复用 `:feature:update:core` 的 [AppUpdateRepository]（版本比较 + 预签名地址）与
 * [ApkDownloadWorker]（下载链路），**不依赖 :feature:update:impl**（无启动静默检查、
 * 无 UpdateEventBus/UpdateViewModel）——lite 只在设置页手动触发。
 *
 * 与 full 的差异：
 * - 无「自动更新」开关（lite 不注册 UpdateInitTask，无静默检查）；
 * - 无「不再提醒此版本」（手动检查如实提示）；
 * - 无「后台下载」最小化（下载进度直接体现在设置页行内，完成后弹安装确认）。
 */
@HiltViewModel
class LiteUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<LiteUpdateState>(LiteUpdateState.Idle)
    val state: StateFlow<LiteUpdateState> = _state.asStateFlow()

    /** 当前版本名（设置页副标题与检查失败提示用） */
    val currentVersionName: String = repository.getCurrentVersionName()

    /** 防重复检查（手动点击连点守卫） */
    private var checking = false

    /** 已入队的下载任务 id（进度监听用；下载中不可重复入队） */
    private var downloadWorkId: String? = null

    /** 缓存本次检查到的版本（下载调度用） */
    private var latestVersion: AppVersionVo? = null

    /**
     * 手动检查更新：拉最新版本 → 有新版进 [LiteUpdateState.UpdateAvailable]，
     * 否则 [LiteUpdateState.LatestVersion]。失败进 [LiteUpdateState.Error]（页面 Toast）。
     */
    fun checkForUpdate(workManager: WorkManager) {
        if (checking) return
        checking = true
        _state.value = LiteUpdateState.Checking
        viewModelScope.launch {
            try {
                val version = repository.getLatestVersion()
                latestVersion = version
                _state.value = if (repository.shouldUpdate(version.versionCode)) {
                    LiteUpdateState.UpdateAvailable(version)
                } else {
                    LiteUpdateState.LatestVersion
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.warn(TAG, "lite 检查更新失败: ${e.message}")
                _state.value = LiteUpdateState.Error(e.message ?: "检查更新失败")
            } catch (e: Exception) {
                AppLogger.warn(TAG, "lite 检查更新失败", e)
                _state.value = LiteUpdateState.Error("检查更新失败")
            } finally {
                checking = false
            }
        }
    }

    /**
     * 开始下载：先取预签名地址，再入队 [ApkDownloadWorker] 并监听进度。
     * 本地已有完整安装包时直接进 [LiteUpdateState.Downloaded]（不重复下载）。
     */
    fun startDownload(workManager: WorkManager) {
        val version = latestVersion ?: run {
            AppLogger.warn(TAG, "startDownload 在未持有最新版本信息时被调用，忽略")
            return
        }
        if (_state.value is LiteUpdateState.Downloading) return
        _state.value = LiteUpdateState.Preparing
        viewModelScope.launch {
            try {
                val apkFile = ApkDownloadWorker.apkFile(context, version.versionCode)
                if (isApkUsable(apkFile, version)) {
                    AppLogger.info(TAG, "命中本地已下载的安装包，跳过重复下载：${apkFile.absolutePath}")
                    _state.value = LiteUpdateState.Downloaded(apkFile)
                    return@launch
                }
                val url = repository.getPresignedDownloadUrl()
                enqueueDownload(workManager, url, version)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "获取 APK 预签名下载地址失败", e)
                _state.value = LiteUpdateState.Error(e.message ?: "获取下载地址失败")
            } catch (e: Exception) {
                AppLogger.error(TAG, "获取 APK 预签名下载地址失败", e)
                _state.value = LiteUpdateState.Error("获取下载地址失败")
            }
        }
    }

    /** 入队下载并挂进度监听（WorkManager 唯一任务名按 versionCode，同版本复用） */
    private fun enqueueDownload(workManager: WorkManager, downloadUrl: String, version: AppVersionVo) {
        val uniqueName = "apk_download_${version.versionCode}"
        val request = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setInputData(
                workDataOf(
                    ApkDownloadWorker.KEY_APK_URL to downloadUrl,
                    ApkDownloadWorker.KEY_SHA256 to version.sha256,
                    ApkDownloadWorker.KEY_VERSION_NAME to version.versionName,
                    ApkDownloadWorker.KEY_VERSION_CODE to version.versionCode,
                )
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        downloadWorkId = request.id.toString()
        observeProgress(workManager, request.id)
    }

    /** 监听下载进度：RUNNING → Downloading；SUCCEEDED → Downloaded；FAILED → Error */
    private fun observeProgress(workManager: WorkManager, workId: java.util.UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(ApkDownloadWorker.KEY_PROGRESS, 0)
                        val downloaded = workInfo.progress.getLong(ApkDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                        val total = workInfo.progress.getLong(ApkDownloadWorker.KEY_TOTAL_BYTES, 0L)
                        _state.value = LiteUpdateState.Downloading(progress, downloaded, total)
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val apkPath = workInfo.outputData.getString(ApkDownloadWorker.KEY_APK_PATH)
                        if (apkPath != null) {
                            _state.value = LiteUpdateState.Downloaded(File(apkPath))
                        } else {
                            _state.value = LiteUpdateState.Error("下载完成但安装包路径缺失")
                        }
                        downloadWorkId = null
                    }

                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString(ApkDownloadWorker.KEY_ERROR)
                        _state.value = LiteUpdateState.Error(error ?: "下载失败，请重试")
                        downloadWorkId = null
                    }

                    WorkInfo.State.CANCELLED -> {
                        _state.value = LiteUpdateState.Idle
                        downloadWorkId = null
                    }

                    else -> {}
                }
            }
        }
    }

    /** 安装开始（页面调 ApkInstaller 后回调，用于关掉进度态） */
    fun onInstallStarted() {
        _state.value = LiteUpdateState.Installing
    }

    /**
     * 取消下载：撤销 WorkManager 任务并复位。lite 无后台下载语义，进度弹窗的
     * 「取消下载」与「稍后更新」都走这里——只复位状态不撤任务的话，observeProgress
     * 监听仍在收 RUNNING，Downloading 状态会把进度弹窗再推出来。
     */
    fun cancelDownload(workManager: WorkManager) {
        downloadWorkId?.let { id ->
            runCatching { workManager.cancelWorkById(java.util.UUID.fromString(id)) }
                .onFailure { AppLogger.warn(TAG, "取消下载任务失败：$id", it) }
        }
        downloadWorkId = null
        reset()
    }

    /** 复位到 Idle（关闭弹窗/提示后） */
    fun reset() {
        _state.value = LiteUpdateState.Idle
    }

    /** 本地安装包是否可复用：存在且非空，大小与服务端声明一致（未给 fileSize 时只要非空） */
    private fun isApkUsable(file: File, version: AppVersionVo): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        return version.fileSize <= 0 || file.length() == version.fileSize
    }

    private companion object {
        const val TAG = "LiteUpdateViewModel"
    }
}
