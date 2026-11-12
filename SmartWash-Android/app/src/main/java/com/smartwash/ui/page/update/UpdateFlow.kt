package com.smartwash.ui.page.update

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalView
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
import com.smartwash.common.ui.components.ButtonSpinner
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.performHaptic
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
private const val TAG = "UpdateFlow"

/**
 * 更新流程（T8.1 自 MainActivity 抽出，职责仍归壳层）：事件收集 → 下载调度 →
 * 弹窗展示。启动静默检查已移至 UpdateInitTask（App.onCreate 经 InitEngine 调度，
 * 非阻塞低优先级），本流程只消费其广播的「发现新版本」/「自动下载」事件。
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
    // 「自动下载」标记（2026-11-12）：下载中不弹进度窗（用户未主动点击，静默下载），
    // 完成时仍弹「安装包已就位」窗；失败时清除标记让错误弹窗正常显示
    val autoDownload by updateViewModel.autoDownload.collectAsState()

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

    // 壳层只收集事件总线广播的「发现新版本」/「自动下载」事件驱动弹窗状态；
    // 事件可能早于本订阅发出（总线 replay 兜底），重放已由 ViewModel 防重挡下
    LaunchedEffect(updateEventBus) {
        updateEventBus.events.collect { event ->
            when (event) {
                is UpdateEvent.UpdateAvailable -> updateViewModel.onUpdateAvailable(event.version)
                is UpdateEvent.AutoDownload -> {
                    // 用户已开启自动更新：不弹说明弹窗，直接取预签名地址下载。
                    // 仍走 combine(downloadUrl, isResumed) 入队，规避 Android 12+ 前台服务启动被拒
                    updateViewModel.onAutoDownload(event.version)
                    updateViewModel.startDownload(context)
                }
            }
        }
    }

    // 下载中的 WorkManager 进度监听（手动「立即更新」或自动下载由 scheduleApkDownload 入队）
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
                    val alreadyDownloaded = scheduleApkDownload(
                        context = context,
                        workManager = workManager,
                        downloadUrl = url,
                        version = version,
                        currentDownloadWorkId = currentDownloadWorkId,
                    )
                    // 本地已有完整安装包：直接进完成态弹「安装包已就位」，不再走下载
                    if (alreadyDownloaded != null) {
                        updateViewModel.onDownloadComplete(alreadyDownloaded)
                    }
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
                    onUpdateLater = { updateViewModel.reset() },
                    // 勾选「不再提醒此版本」：记录 versionCode 后关闭，启动静默检查不再弹此版本
                    onIgnoreVersion = {
                        updateViewModel.ignoreVersion(version.versionCode)
                        updateViewModel.reset()
                    },
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
            // 标记存 VM，旋转重建不丢。
            // 「自动下载」（2026-11-12）同理静默：用户没有主动点击，进度交给通知栏，
            // 完成时弹「安装包已就位」。
            if (!backgroundDownload && !autoDownload) {
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
 * 设置页「自动更新」开关行（2026-11-12）：开启后启动静默检查发现新版本即自动下载，
 * 不再弹更新说明弹窗（强制更新除外），下载完成弹「安装包已就位」。
 *
 * 交互形态对齐设置页「触感反馈」行（SettingPage）：整行点击切换 + 右侧状态文案，
 * 不引入新的开关控件形态；开启瞬间给一次确认触感（规范 §7.7，因果明确的时刻）。
 */
@Composable
internal fun AutoUpdateSettingRow(updateViewModel: UpdateViewModel) {
    val view = LocalView.current
    val autoUpdateEnabled by updateViewModel.autoUpdateEnabled.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressable(
                onClick = {
                    val newValue = !autoUpdateEnabled
                    updateViewModel.setAutoUpdateEnabled(newValue)
                    if (newValue) view.performHaptic(HapticEffect.LIGHT)
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(
            icon = Icons.Default.DownloadDone,
            size = 32.dp,
            iconSize = 20.dp,
            containerColor = AppColors.colorScheme.iconContainerTeal,
            iconTint = AppColors.colorScheme.iconForegroundTeal,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.auto_update),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textPrimary,
            )
            Text(
                text = stringResource(R.string.auto_update_desc),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = stringResource(
                if (autoUpdateEnabled) R.string.auto_update_enabled else R.string.auto_update_disabled
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary,
        )
    }
}

/**
 * 设置页更新区块（自动更新开关 + 检查更新行），经 checkUpdateContent 插槽注入。
 * 两行之间的分隔线与设置页其他行一致（hairline 0.5dp）。
 */
@Composable
internal fun UpdateSettingRows(updateViewModel: UpdateViewModel) {
    AutoUpdateSettingRow(updateViewModel)
    HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
    CheckUpdateSettingRow(updateViewModel)
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
    // 同步：64dp 高 / 32dp 图标盒 12dp 圆角 / 行自身零水平 padding。
    // 设计稿为两行行（标题 + 当前版本副标题）：副标题 12sp textSecondary
    // （规范 v0.4「设置页等两行卡片实测 64dp」）；版本号文案复用既有 version_short（v%s）。
    //
    // 检查中反馈（回归 2026-11-06 N-08-02）：手动检查需 2-4s，原版无任何视觉反馈且可连点。
    // Checking 期间副标题原位换公共 ButtonSpinner（§7.8：reduced motion 退化为静态 270° 圆环），
    // 同时忽略点击——VM 的 isChecking 防重入兜底，UI 层不发起无效调用。
    val isChecking = manualCheckState is UpdateState.Checking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressable(onClick = { if (!isChecking) updateViewModel.checkForUpdate(silent = false) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(
            icon = Icons.Default.SystemUpdate,
            size = 32.dp,
            iconSize = 20.dp,
            containerColor = AppColors.colorScheme.iconContainerTeal,
            // 容器-前景令牌配对：青底配青前景（§3.2 六色表；原错配 green 前景）
            iconTint = AppColors.colorScheme.iconForegroundTeal
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.check_update),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textPrimary
            )
            // 检查中：版本号副标题原位换 spinner（合并设计对齐两行布局与 N-08-02 检查反馈）
            Crossfade(
                targetState = isChecking,
                label = "checkUpdateVersion",
            ) { checking ->
                if (checking) {
                    ButtonSpinner(
                        size = 16.dp,
                        strokeWidth = 2.dp,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.version_short, updateViewModel.getCurrentVersionName()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AppColors.colorScheme.textTertiary
        )
    }
}

/**
 * 调度 APK 下载：本地已有完整安装包或同版本任务在进行时直接复用，不重复下载。
 *
 * 用户反馈（2026-11-12）：「点击更新下载了一个安装包，关闭弹窗再次点击更新又重复下载了」。
 * 根因是原实现每次点击都重新取预签名地址并以 [ExistingWorkPolicy.REPLACE] 入队，
 * Worker 每次运行还会先删除同名文件。现按 versionCode 命名安装包并做两级复用：
 * 1. 本地文件存在且大小与服务端 fileSize 一致 → 直接返回该文件，调用方进「安装包已就位」态；
 * 2. 同版本唯一任务在进行中（排队/运行/阻塞）→ 复用其 workId 挂监听，不再入队。
 *
 * @param downloadUrl 预签名下载地址（从后端 /web/app/download 获取）
 * @param version 版本信息（用于 fileSize、sha256 校验、versionCode 命名与 workName 唯一标识）
 * @return 非空 = 本地已有可用安装包（调用方直接进完成态）；null = 已入队或已复用既有任务
 */
private suspend fun scheduleApkDownload(
    context: Context,
    workManager: WorkManager,
    downloadUrl: String,
    version: AppVersionVo,
    currentDownloadWorkId: androidx.compose.runtime.MutableState<String?>,
): File? = withContext(Dispatchers.IO) {
    val uniqueName = "apk_download_${version.versionCode}"
    val apkFile = ApkDownloadWorker.apkFile(context, version.versionCode)

    // 复用 1：本地已有完整安装包
    if (isApkUsable(apkFile, version)) {
        AppLogger.info(TAG, "命中本地已下载的安装包，跳过重复下载：${apkFile.absolutePath}")
        return@withContext apkFile
    }

    // 复用 2：同版本任务在进行中 → 复用其 workId 挂监听，不重复入队
    val existing = runCatching {
        workManager.getWorkInfosForUniqueWork(uniqueName).get().firstOrNull()
    }.getOrNull()

    if (existing != null) {
        val inProgress = existing.state == WorkInfo.State.ENQUEUED ||
            existing.state == WorkInfo.State.RUNNING ||
            existing.state == WorkInfo.State.BLOCKED
        if (inProgress) {
            AppLogger.info(TAG, "同版本下载任务已存在（state=${existing.state}），复用不重下")
            currentDownloadWorkId.value = existing.id.toString()
            return@withContext null
        }
        // 终态（成功但文件不可用 / 失败 / 取消）：清掉旧记录后重新下载
        AppLogger.info(TAG, "同版本下载任务已是终态（state=${existing.state}）且安装包不可用，重新下载")
        runCatching { workManager.cancelWorkById(existing.id) }
    }

    val inputData = workDataOf(
        ApkDownloadWorker.KEY_APK_URL to downloadUrl,
        ApkDownloadWorker.KEY_SHA256 to version.sha256,
        ApkDownloadWorker.KEY_VERSION_NAME to version.versionName,
        ApkDownloadWorker.KEY_VERSION_CODE to version.versionCode,
    )

    val downloadRequest = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
        .setInputData(inputData)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, downloadRequest)

    // 记录 workId，触发 LaunchedEffect 收集进度
    currentDownloadWorkId.value = downloadRequest.id.toString()
    null
}

/**
 * 本地安装包是否可复用：存在且非空，大小与服务端声明一致。
 * 服务端未给 fileSize（≤0）时无法校验，只要文件非空即视为可用。
 */
private fun isApkUsable(file: File, version: AppVersionVo): Boolean {
    if (!file.exists() || file.length() <= 0) return false
    return version.fileSize <= 0 || file.length() == version.fileSize
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
