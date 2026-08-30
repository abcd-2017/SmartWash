package com.smartwash.ui.page.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartwash.R
import com.smartwash.network.vo.AppVersionVo
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.AppElevation
import java.io.File

/**
 * 更新提示弹窗 — 发现新版本时展示
 */
@Composable
fun UpdateAvailableDialog(
    version: AppVersionVo,
    onUpdateNow: () -> Unit,
    onUpdateLater: () -> Unit,
) {
    val isForce = version.forceUpdate
    Dialog(
        onDismissRequest = { if (!isForce) onUpdateLater() },
        properties = DialogProperties(
            dismissOnClickOutside = !isForce,
            dismissOnBackPress = !isForce,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.level4,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 标题行：图标 + 标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_title_icon),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.new_version_available),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 版本号 + 文件大小
                Text(
                    text = stringResource(
                        R.string.update_version_format,
                        version.versionName,
                        formatFileSize(version.fileSize)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 更新内容
                Text(
                    text = stringResource(R.string.update_content),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(AppDimens.smallCardRadius),
                    color = AppColors.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = version.changelog.ifBlank { stringResource(R.string.update_content) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier
                            .padding(12.dp)
                            .height(120.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区
                if (isForce) {
                    // 强制更新：仅「立即更新」按钮，不可取消
                    Button(
                        onClick = onUpdateNow,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(AppDimens.buttonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.update_now),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onUpdateLater,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(AppDimens.buttonRadius),
                        ) {
                            Text(
                                text = stringResource(R.string.update_later),
                                color = AppColors.colorScheme.textSecondary,
                            )
                        }
                        Button(
                            onClick = onUpdateNow,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(AppDimens.buttonRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.colorScheme.primary,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.update_now),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 强制更新提示弹窗 — 当前版本已停止服务
 */
@Composable
fun ForceUpdateRequiredDialog(
    onUpdateNow: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* 不可关闭 */ },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.level4,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.force_update_icon),
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.force_update_required),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.force_update_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onUpdateNow,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(AppDimens.buttonRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.colorScheme.error,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.update_now),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * 下载进度弹窗
 */
@Composable
fun DownloadProgressDialog(
    progress: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* 下载中不允许关闭 */ },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.level4,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.download_icon),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.downloading_update),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(20.dp))

                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = AppColors.colorScheme.primary,
                    trackColor = AppColors.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(
                        R.string.download_progress_format,
                        formatFileSize(downloadedBytes),
                        formatFileSize(totalBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
                Text(
                    text = "${progress}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(AppDimens.buttonRadius),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * 下载完成弹窗 — 询问是否立即安装
 */
@Composable
fun DownloadCompleteDialog(
    onInstallNow: () -> Unit,
    onInstallLater: () -> Unit,
) {
    Dialog(
        onDismissRequest = onInstallLater,
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.level4,
            tonalElevation = 6.dp,
        ) {
            Column {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.download_complete_icon),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.download_complete),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.download_complete_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onInstallLater,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.install_later),
                            color = AppColors.colorScheme.textSecondary,
                        )
                    }
                    TextButton(
                        onClick = onInstallNow,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.install_now),
                            color = AppColors.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 将字节数格式化为可读大小（B / KB / MB）
 */
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
