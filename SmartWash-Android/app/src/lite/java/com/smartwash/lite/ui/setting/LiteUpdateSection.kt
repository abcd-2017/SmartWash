package com.smartwash.lite.ui.setting

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkManager
import com.smartwash.R
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.ButtonSpinner
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.update.service.ApkInstaller
import com.smartwash.feature.update.service.InstallRequestResult

/**
 * lite 设置页「检查更新」区块（flavor 源集专属）：行 + 更新流程弹窗。
 *
 * 与 full 的差异：无「自动更新」开关、无启动静默检查、无事件总线——一切由用户点击触发。
 * 行内反馈检查中（副标题原位换 [ButtonSpinner]）；发现新版本弹说明窗（版本 + 更新内容）；
 * 下载中弹进度窗（可取消/稍后）；完成弹「安装包已就位」确认窗；失败弹错误窗（可重试）。
 *
 * 视觉全部取自清氧令牌（[AppColors] / [AppDimens]）；行高 64dp 与 lite 设置页其他行同口径。
 */
@Composable
fun LiteUpdateSection(
    workManager: WorkManager,
    updateViewModel: LiteUpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = currentView()
    val state by updateViewModel.state.collectAsState()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }

    // 状态驱动的弹窗切换（副作用出组合期，禁止组合期直接弹）
    LaunchedEffect(state) {
        when (state) {
            is LiteUpdateState.UpdateAvailable -> showUpdateDialog = true
            is LiteUpdateState.Downloading -> showDownloadDialog = true
            is LiteUpdateState.Downloaded -> {
                showDownloadDialog = false
                showInstallDialog = true
            }
            is LiteUpdateState.Error -> {
                showDownloadDialog = false
                showErrorDialog = true
            }
            else -> {}
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(
                onClick = {
                    view.performHaptic(HapticEffect.SELECTION)
                    updateViewModel.checkForUpdate(workManager)
                },
            )
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(
            icon = Icons.Filled.Refresh,
            size = 32.dp,
            iconSize = 20.dp,
            containerColor = AppColors.colorScheme.iconContainerGreen,
            iconTint = AppColors.colorScheme.iconForegroundGreen,
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.lite_setting_check_update),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 检查中：副标题原位换 spinner（对齐 full 的 N-08-02 检查反馈）
            if (state is LiteUpdateState.Checking) {
                ButtonSpinner(
                    size = 16.dp,
                    strokeWidth = 2.dp,
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.lite_setting_check_update_desc,
                        updateViewModel.currentVersionName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AppColors.colorScheme.textTertiary
        )
    }

    // 已是最新：轻提示（不落弹窗，避免打断）
    if (state is LiteUpdateState.LatestVersion) {
        LaunchedEffect(state) {
            Toast.makeText(
                context,
                context.getString(R.string.already_latest),
                Toast.LENGTH_SHORT,
            ).show()
            updateViewModel.reset()
        }
    }

    if (showUpdateDialog) {
        val version = (state as? LiteUpdateState.UpdateAvailable)?.version
        // 双按钮确认窗：仅「立即更新」点击才启动下载（原 AppInfoDialog 单按钮 +
        // LaunchedEffect(version) 弹窗出现即下载，用户未确认就开始下载）
        AppConfirmDialog(
            title = stringResource(R.string.new_version_available),
            message = version?.let {
                stringResource(R.string.update_version_format, it.versionName, it.releaseDate) +
                    (if (it.changelog.isNotBlank()) "\n\n" + it.changelog else "")
            }.orEmpty(),
            confirmText = stringResource(R.string.update_now),
            cancelText = stringResource(R.string.update_later),
            onConfirm = {
                showUpdateDialog = false
                updateViewModel.startDownload(workManager)
            },
            onDismiss = {
                showUpdateDialog = false
                updateViewModel.reset()
            },
        )
    }

    if (showDownloadDialog) {
        val downloading = state as? LiteUpdateState.Downloading
        AppConfirmDialog(
            title = stringResource(R.string.downloading_update),
            message = downloading?.let {
                if (it.totalBytes > 0) {
                    stringResource(
                        R.string.download_progress_format,
                        formatFileSize(it.downloadedBytes),
                        formatFileSize(it.totalBytes),
                    ) + " · ${it.progress}%"
                } else {
                    formatFileSize(it.downloadedBytes)
                }
            }.orEmpty(),
            confirmText = stringResource(R.string.cancel_download),
            cancelText = stringResource(R.string.update_later),
            onConfirm = {
                showDownloadDialog = false
                updateViewModel.cancelDownload(workManager)
            },
            // 稍后更新：lite 无后台下载语义，同样撤销任务（原实现仅收弹窗，任务继续跑，
            // 进度监听把 Downloading 状态推回 → 弹窗几秒后复现）
            onDismiss = {
                showDownloadDialog = false
                updateViewModel.cancelDownload(workManager)
            },
        )
    }

    if (showInstallDialog) {
        val file = (state as? LiteUpdateState.Downloaded)?.file
        AppConfirmDialog(
            title = stringResource(R.string.download_complete),
            message = stringResource(R.string.download_complete_message),
            confirmText = stringResource(R.string.install_now),
            cancelText = stringResource(R.string.install_later),
            onConfirm = {
                showInstallDialog = false
                file?.let { apkFile ->
                    view.performHaptic(HapticEffect.MEDIUM)
                    // 无安装权限时 ApkInstaller 跳系统设置页引导；返回后仍可重点安装
                    ApkInstaller.requestInstall(context, apkFile)
                    updateViewModel.onInstallStarted()
                }
                updateViewModel.reset()
            },
            onDismiss = {
                showInstallDialog = false
                updateViewModel.reset()
            },
        )
    }

    if (showErrorDialog) {
        val message = (state as? LiteUpdateState.Error)?.message.orEmpty()
        AppConfirmDialog(
            title = stringResource(R.string.download_failed),
            message = message,
            confirmText = stringResource(R.string.retry),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                showErrorDialog = false
                updateViewModel.startDownload(workManager)
            },
            onDismiss = {
                showErrorDialog = false
                updateViewModel.reset()
            },
        )
    }
}

/** 字节数格式化（B/KB/MB，与 ApkDownloadWorker 同口径） */
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    return when {
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
