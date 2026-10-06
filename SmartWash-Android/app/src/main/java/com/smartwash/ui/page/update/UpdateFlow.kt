package com.smartwash.ui.page.update

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smartwash.R
import com.smartwash.common.ui.components.SettingRow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.feature.update.event.UpdateEvent
import com.smartwash.feature.update.event.UpdateEventBus
import com.smartwash.feature.update.model.AppVersionVo
import com.smartwash.feature.update.service.ApkDownloadWorker
import com.smartwash.feature.update.service.ApkInstaller
import com.smartwash.feature.update.ui.UpdateState
import com.smartwash.feature.update.ui.UpdateViewModel
import java.io.File
import java.util.UUID

/**
 * 更新流程（T8.1 自 MainActivity 抽出，职责仍归壳层）：事件收集 → 下载调度 →
 * 弹窗展示。启动静默检查已移至 UpdateInitTask（App.onCreate 经 InitEngine 调度，
 * 非阻塞低优先级），本流程只消费其广播的「发现新版本」事件。
 */
@Composable
internal fun UpdateFlow(
    context: Context,
    workManager: WorkManager,
    updateViewModel: UpdateViewModel,
    updateEventBus: UpdateEventBus,
) {
    val updateState by updateViewModel.state.collectAsState()

    // 通知权限 launcher：用户点击「立即更新」时，若无权限则弹出系统授权请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 授权通过：进入准备下载状态，开始获取预签名地址
            updateViewModel.preparingDownload()
            updateViewModel.startDownload(context)
        }
        // 未授权则什么都不做，弹窗保持 UpdateAvailable 状态（用户可再次点击）
    }

    // 检查通知权限是否已授予（Android 13+ 需要运行时权限）
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    // 壳层只收集事件总线广播的「发现新版本」事件驱动弹窗状态；
    // 事件可能早于本订阅发出（总线 replay 兜底），重放已由 ViewModel 防重挡下
    LaunchedEffect(updateEventBus) {
        updateEventBus.events.collect { event ->
            when (event) {
                is UpdateEvent.UpdateAvailable -> updateViewModel.onUpdateAvailable(event.version)
            }
        }
    }

    // 下载中的 WorkManager 进度监听（用户点「立即更新」后由 enqueueApkDownload 入队）
    // 声明必须早于下方 LaunchedEffect(updateViewModel) 的首次使用
    val currentDownloadWorkId = remember { mutableStateOf<String?>(null) }

    // 监听预签名下载地址：获取成功后调度 Worker 开始下载
    LaunchedEffect(updateViewModel) {
        updateViewModel.downloadUrl.collect { url ->
            if (url != null) {
                val version = updateViewModel.getLatestVersion() ?: return@collect
                enqueueApkDownload(context, workManager, url, version, currentDownloadWorkId)
                updateViewModel.consumeDownloadUrl()
            }
        }
    }

    LaunchedEffect(currentDownloadWorkId.value) {
        val workId = currentDownloadWorkId.value ?: return@LaunchedEffect
        val uuid = try { UUID.fromString(workId) } catch (_: Exception) { return@LaunchedEffect }
        workManager.getWorkInfoByIdFlow(uuid).collect { workInfo ->
            when (workInfo?.state) {
                WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getInt(ApkDownloadWorker.KEY_PROGRESS, 0)
                    updateViewModel.onDownloadProgress(progress)
                }
                WorkInfo.State.SUCCEEDED -> {
                    val apkPath = workInfo.outputData.getString(ApkDownloadWorker.KEY_APK_PATH)
                    if (apkPath != null) {
                        updateViewModel.onDownloadComplete(File(apkPath))
                    } else {
                        updateViewModel.onDownloadFailed(context.getString(R.string.download_failed))
                    }
                    currentDownloadWorkId.value = null
                }
                WorkInfo.State.FAILED -> {
                    val error = workInfo.outputData.getString(ApkDownloadWorker.KEY_ERROR)
                    updateViewModel.onDownloadFailed(error ?: context.getString(R.string.download_failed))
                    currentDownloadWorkId.value = null
                }
                WorkInfo.State.CANCELLED -> {
                    updateViewModel.reset()
                    currentDownloadWorkId.value = null
                }
                else -> {}
            }
        }
    }

    // 更新弹窗 UI — 根据状态展示对应弹窗
    val currentState = updateState
    when (currentState) {
        is UpdateState.UpdateAvailable -> {
            val version = currentState.version
            if (version.forceUpdate) {
                ForceUpdateRequiredDialog(
                    // 连点防抖：阻断重复拉起权限弹窗/重复调度下载
                    onUpdateNow = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
                        handleUpdateClick(
                            context = context,
                            hasPermission = hasNotificationPermission,
                            launcher = notificationPermissionLauncher,
                            onGranted = {
                                updateViewModel.preparingDownload()
                                updateViewModel.startDownload(context)
                            }
                        )
                    }
                )
            } else {
                UpdateAvailableDialog(
                    version = version,
                    onUpdateNow = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
                        handleUpdateClick(
                            context = context,
                            hasPermission = hasNotificationPermission,
                            launcher = notificationPermissionLauncher,
                            onGranted = {
                                updateViewModel.preparingDownload()
                                updateViewModel.startDownload(context)
                            }
                        )
                    },
                    onUpdateLater = { updateViewModel.reset() }
                )
            }
        }
        is UpdateState.Preparing -> {
            // 准备下载中：展示加载指示器，不关闭弹窗
            DownloadPreparingDialog()
        }
        is UpdateState.Downloading -> {
            DownloadProgressDialog(
                progress = currentState.progress,
                downloadedBytes = 0L,
                totalBytes = (updateViewModel.getLatestVersion()?.fileSize ?: 0L),
                onCancel = {
                    currentDownloadWorkId.value?.let { id ->
                        try { workManager.cancelWorkById(UUID.fromString(id)) } catch (_: Exception) {}
                    }
                    updateViewModel.reset()
                },
            )
        }
        is UpdateState.Downloaded -> {
            DownloadCompleteDialog(
                // 连点防抖：阻断双击拉起两个系统安装 Intent 与删包竞态
                onInstallNow = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
                    handleApkInstall(context, updateViewModel, currentState.file)
                },
                onInstallLater = { updateViewModel.reset() },
            )
        }
        else -> { /* Idle / Checking / LatestVersion / Error / Installing 不弹窗 */ }
    }
}

/**
 * 处理「立即更新」按钮点击：检查通知权限，已授权则直接开始，未授权则请求。
 */
private fun handleUpdateClick(
    context: Context,
    hasPermission: Boolean,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
    onGranted: () -> Unit,
) {
    if (hasPermission) {
        onGranted()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        // Android 12 及以下无需运行时权限
        onGranted()
    }
}

/**
 * 设置页「检查更新」行（经 SettingPage 的 checkUpdateContent 插槽注入，T5.2 模式；
 * feature:update 归壳层聚合，user-impl 不依赖它）：手动检查的 Toast 反馈与检查入口
 * 同生命周期（挂载于设置页组合内），启动静默检查（UpdateInitTask）不会误弹 Toast。
 */
@Composable
internal fun CheckUpdateSettingRow(updateViewModel: UpdateViewModel) {
    val context = LocalContext.current
    val manualCheckState by updateViewModel.state.collectAsState()
    LaunchedEffect(manualCheckState) {
        when (manualCheckState) {
            is UpdateState.LatestVersion -> {
                Toast.makeText(context, context.getString(R.string.already_latest), Toast.LENGTH_SHORT).show()
                updateViewModel.reset()
            }
            is UpdateState.Error -> {
                val msg = (manualCheckState as UpdateState.Error).message
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                updateViewModel.reset()
            }
            else -> {}
        }
    }
    SettingRow(
        icon = Icons.Default.SystemUpdate,
        title = stringResource(R.string.check_update),
        subtitle = stringResource(R.string.check_update_desc, updateViewModel.getCurrentVersionName()),
        trailing = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.height(16.dp),
                tint = AppColors.colorScheme.textSecondary
            )
        },
        onClick = { updateViewModel.checkForUpdate(silent = false) }
    )
}

/**
 * 入队 APK 下载 Worker，并将 workId 写入 state 触发 LaunchedEffect 监听进度
 * @param downloadUrl 预签名下载地址（从后端 /web/app/download 获取）
 * @param version 版本信息（用于 fileSize、sha256 校验与 workName 唯一标识）
 */
private fun enqueueApkDownload(
    context: Context,
    workManager: WorkManager,
    downloadUrl: String,
    version: AppVersionVo,
    currentDownloadWorkId: androidx.compose.runtime.MutableState<String?>,
) {
    val inputData = workDataOf(
        ApkDownloadWorker.KEY_APK_URL to downloadUrl,
        ApkDownloadWorker.KEY_SHA256 to version.sha256,
        "versionName" to version.versionName,
    )

    val downloadRequest = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
        .setInputData(inputData)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    workManager.enqueueUniqueWork(
        "apk_download_${version.versionCode}",
        ExistingWorkPolicy.REPLACE,
        downloadRequest
    )

    // 记录 workId，触发 LaunchedEffect 收集进度
    currentDownloadWorkId.value = downloadRequest.id.toString()
}

/**
 * 处理 APK 安装：检查权限后调起系统安装器
 */
private fun handleApkInstall(
    context: Context,
    updateViewModel: UpdateViewModel,
    apkFile: File,
) {
    if (ApkInstaller.canInstallApk(context)) {
        updateViewModel.onInstallStarted()
        ApkInstaller.installViaIntent(context, apkFile)
        ApkInstaller.deleteLocalApk(context)
        updateViewModel.reset()
    } else {
        // 无安装权限，跳转系统设置
        ApkInstaller.openInstallPermissionSettings(context)
    }
}
