package com.smartwash.ui.page.update

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.feature.update.event.UpdateEvent
import com.smartwash.feature.update.event.UpdateEventBus
import com.smartwash.feature.update.model.AppVersionVo
import com.smartwash.feature.update.service.ApkDownloadWorker
import com.smartwash.feature.update.service.ApkInstaller
import com.smartwash.feature.update.service.InstallRequestResult
import com.smartwash.feature.update.ui.ErrorSource
import com.smartwash.feature.update.ui.UpdateState
import com.smartwash.feature.update.ui.UpdateViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

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
    // 「后台下载」标记：Downloaded/Error 等其余状态由 VM 复位，弹窗自然回弹
    val backgroundDownload by updateViewModel.backgroundDownload.collectAsState()

    // 通知权限 launcher：用户点击「立即更新」时，若无权限则弹出系统授权请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 授权通过：进入准备下载状态，开始获取预签名地址
            updateViewModel.preparingDownload()
            updateViewModel.startDownload(context)
        } else {
            // 未授权不再静默无反应（旧行为：什么都不做，用户只见弹窗不动，不知发生了什么）——
            // 更新照常开始（应用内进度弹窗承载反馈），仅明确告知通知栏不显示进度
            Toast.makeText(
                context,
                context.getString(R.string.update_notification_denied),
                Toast.LENGTH_LONG
            ).show()
            updateViewModel.preparingDownload()
            updateViewModel.startDownload(context)
        }
    }

    // 前台态标记（2026-11-02）：下载入队收敛到 Activity RESUMED 之后。
    // WorkManager 的 SystemForegroundService 在 App 不处于前台态时启动前台服务会被
    // Android 12+ 拒绝（ForegroundServiceStartNotAllowedException），而 WorkManager 只记日志
    // 不抛出——典型触发点是「权限授权回调里入队下载」：授权页刚关闭、本页尚未恢复 RESUMED。
    // 此时下载照跑、应用内进度弹窗照显，但通知栏一条都没有（用户反馈的「后台下载没动静」）。
    val lifecycleOwner = LocalLifecycleOwner.current
    val isResumed = remember {
        MutableStateFlow(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed.value = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // 监听预签名下载地址：获取成功后调度 Worker 开始下载。
    // 与前台态标记 combine：仅在本页 RESUMED 时入队（见 isResumed 注释）；
    // 未恢复时先持有 URL 不消费，恢复后自动补发，避免下载被降级为后台进程遭系统回收。
    LaunchedEffect(updateViewModel) {
        combine(updateViewModel.downloadUrl, isResumed) { url, resumed -> url to resumed }
            .collect { (url, resumed) ->
                if (url != null && resumed) {
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
                    val downloaded = workInfo.progress.getLong(ApkDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                    val total = workInfo.progress.getLong(ApkDownloadWorker.KEY_TOTAL_BYTES, 0L)
                    updateViewModel.onDownloadProgress(progress, downloaded, total)
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
            // 「后台下载」（2026-11-01 用户反馈）：用户选择后台后弹窗隐藏但 WorkManager 下载
            // 继续（本组合的进度收集不中断），完成/失败时状态机自动回弹对应弹窗；
            // 标记存 VM，旋转重建不丢
            if (!backgroundDownload) {
                DownloadProgressDialog(
                    progress = currentState.progress,
                    downloadedBytes = currentState.downloadedBytes,
                    totalBytes = currentState.totalBytes.takeIf { it > 0 }
                        ?: (updateViewModel.getLatestVersion()?.fileSize ?: 0L),
                    onCancel = {
                        currentDownloadWorkId.value?.let { id ->
                            try { workManager.cancelWorkById(UUID.fromString(id)) } catch (_: Exception) {}
                        }
                        updateViewModel.reset()
                    },
                    onBackgroundDownload = updateViewModel::minimizeDownloadDialog,
                )
            }
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
        is UpdateState.Error -> {
            // 下载失败必须给用户明确反馈与重试入口；检查失败的 Toast 由设置页 CheckUpdateSettingRow 负责
            if (currentState.source == ErrorSource.Download) {
                DownloadErrorDialog(
                    message = currentState.message,
                    onRetry = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
                        updateViewModel.preparingDownload()
                        updateViewModel.startDownload(context)
                    },
                    onClose = { updateViewModel.reset() },
                )
            }
        }
        else -> { /* Idle / Checking / LatestVersion / Installing 不弹窗 */ }
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
                // 下载类错误由壳层 UpdateFlow 弹窗反馈，此处只消费手动检查失败，避免双响应
                if ((manualCheckState as UpdateState.Error).source == ErrorSource.Check) {
                    val msg = (manualCheckState as UpdateState.Error).message
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    updateViewModel.reset()
                }
            }
            else -> {}
        }
    }
    // 行样式对齐 SettingPage 私有 SettingRow 口径（2026-11-01 缺陷 5a）：此前用 common:ui 的
    // SettingRow（56dp 高 + 自带 20dp 水平 padding），放进设置页「自带 20dp 内边距」的分组卡后
    // 内容缩进 40dp、行高 56 与相邻 64 行错位。此处改为同口径行——与 SettingPage 私有行保持
    // 同步：64dp 高 / 32dp 图标盒 12dp 圆角 / bodyMedium 标题 / 行自身零水平 padding；
    // 版本号收进行尾（对齐「关于」行尾注形态，双行副标题与 64dp 行高压不下）。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressable(onClick = { updateViewModel.checkForUpdate(silent = false) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(
            icon = Icons.Default.SystemUpdate,
            size = 32.dp,
            iconSize = 20.dp,
            containerColor = AppColors.colorScheme.iconContainerTeal,
            iconTint = AppColors.colorScheme.iconForegroundGreen
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.check_update),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.version_short, updateViewModel.getCurrentVersionName()),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = AppColors.colorScheme.textSecondary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AppColors.colorScheme.textTertiary
        )
    }
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
        ApkDownloadWorker.KEY_VERSION_NAME to version.versionName,
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
 * 处理 APK 安装：安装序列（权限检查 → 拉起安装器/跳设置页）在 ApkInstaller.requestInstall，
 * 此处只做 ViewModel 状态联动
 */
private fun handleApkInstall(
    context: Context,
    updateViewModel: UpdateViewModel,
    apkFile: File,
) {
    when (ApkInstaller.requestInstall(context, apkFile)) {
        InstallRequestResult.Installed -> {
            updateViewModel.onInstallStarted()
            updateViewModel.reset()
        }
        InstallRequestResult.NeedPermission -> {
            // 已跳转系统「允许安装未知应用」设置页；弹窗保持 Downloaded 状态，授权返回后可重点安装
        }
    }
}
