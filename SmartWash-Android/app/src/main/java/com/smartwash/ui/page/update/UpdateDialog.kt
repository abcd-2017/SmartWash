package com.smartwash.ui.page.update

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartwash.R
import com.smartwash.feature.update.model.AppVersionVo
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.ButtonSpinner
import com.smartwash.common.ui.components.DialogAction
import com.smartwash.common.ui.components.DrumMark
import com.smartwash.common.ui.components.dialogEnterMotion
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import java.io.File

/**
 * 更新提示弹窗 — 发现新版本时展示。
 *
 * 视觉锚点 = 规范 §5 滚筒圆母题（DrumMark 静止态，primary）：历史上这里用 emoji
 * （§4.4 禁止），且全项目自绘图标清单（§4.1）不含更新类图形，母题是唯一合规来源。
 * 主/次按钮统一走 [AppButton]（§3.3：52dp / 14dp 圆角 / 品牌渐变），与全应用 CTA 同源。
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
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 母题锚点（§5）：静止滚筒圆即完整图形，reduced motion 天然无需降级
                DrumMark(size = 48.dp, tint = AppColors.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                    // 卡内嵌套块圆角 radiusMd（§2.3 radiusInner 12dp；smallCardRadius 已废弃）
                    shape = RoundedCornerShape(AppDimens.radiusMd),
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
                    AppButton(
                        text = stringResource(R.string.update_now),
                        onClick = onUpdateNow,
                        variant = AppButtonVariant.PRIMARY,
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        AppButton(
                            text = stringResource(R.string.update_later),
                            onClick = onUpdateLater,
                            modifier = Modifier.weight(1f),
                            variant = AppButtonVariant.SECONDARY,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        AppButton(
                            text = stringResource(R.string.update_now),
                            onClick = onUpdateNow,
                            modifier = Modifier.weight(1f),
                            variant = AppButtonVariant.PRIMARY,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 强制更新提示弹窗 — 当前版本已停止服务。
 *
 * 母题染 danger（§2.1 语义：异常/停服）；CTA 仍是「立即更新」这一正向动作，
 * 走品牌渐变主按钮——危险语义由母题与文案承担，不把 error 角色当大面积按钮底色。
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
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DrumMark(size = 48.dp, tint = AppColors.colorScheme.danger)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.force_update_required),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.force_update_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary,
                )
                Spacer(modifier = Modifier.height(24.dp))
                AppButton(
                    text = stringResource(R.string.update_now),
                    onClick = onUpdateNow,
                    variant = AppButtonVariant.PRIMARY,
                )
            }
        }
    }
}

/**
 * 准备下载弹窗 — 获取预签名地址阶段展示加载指示器，不允许关闭。
 * spinner 用公共 [ButtonSpinner]（§7.8：reduced motion 退化为静态 270° 圆环）。
 */
@Composable
fun DownloadPreparingDialog() {
    Dialog(
        onDismissRequest = { /* 准备中不允许关闭 */ },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        Surface(
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ButtonSpinner(
                    size = 40.dp,
                    strokeWidth = 4.dp,
                    color = AppColors.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.preparing_download),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.preparing_download_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        }
    }
}

/**
 * 下载进度弹窗。
 *
 * 进度展示 = 规范 §5「环的填充度 = 真实进度」：滚筒圆进度环由 [DrumMark.progress]
 * 承载（WorkManager 真实进度驱动），替代历史上的 emoji + LinearProgressIndicator。
 * 下载进度每 1% 硬跳 → 弹簧平滑到目标值；reduced motion 经 motionSpec 降级为
 * 180ms 短过渡，不引入位移/缩放。
 */
@Composable
fun DownloadProgressDialog(
    progress: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = motionSpec(defaultSpring()),
        label = "downloadProgress"
    )
    Dialog(
        onDismissRequest = { /* 下载中不允许关闭 */ },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        Surface(
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DrumMark(
                    size = 48.dp,
                    progress = animatedProgress,
                    tint = AppColors.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.downloading_update),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(20.dp))

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
                    // 百分比宽度随位数变化 → tabular numbers 等宽，消除数字抖动
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))
                AppButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCancel,
                    variant = AppButtonVariant.SECONDARY,
                )
            }
        }
    }
}

/**
 * 下载完成弹窗 — 询问是否立即安装。
 * 底部按钮 = 公共 [DialogAction] 贴边分区（与 [com.smartwash.common.ui.components.AppConfirmDialog] 同形态）。
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
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 母题染 success（§2.1 语义：完成）
                    DrumMark(size = 48.dp, tint = AppColors.colorScheme.success)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.download_complete),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.download_complete_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                Row(modifier = Modifier.fillMaxWidth()) {
                    DialogAction(
                        text = stringResource(R.string.install_later),
                        color = AppColors.colorScheme.textSecondary,
                        onClick = onInstallLater,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(48.dp)
                            .background(AppColors.colorScheme.divider)
                    )
                    DialogAction(
                        text = stringResource(R.string.install_now),
                        color = AppColors.colorScheme.primary,
                        onClick = onInstallNow,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 下载失败弹窗 — 展示失败原因并提供重试入口（下载链路错误的唯一 App 内反馈，
 * 避免进度弹窗静默消失让用户不知发生了什么）。
 */
@Composable
fun DownloadErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
    ) {
        Surface(
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 母题染 danger（§2.1 语义：异常）
                    DrumMark(size = 48.dp, tint = AppColors.colorScheme.danger)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.download_failed),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                Row(modifier = Modifier.fillMaxWidth()) {
                    DialogAction(
                        text = stringResource(R.string.cancel),
                        color = AppColors.colorScheme.textSecondary,
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(48.dp)
                            .background(AppColors.colorScheme.divider)
                    )
                    DialogAction(
                        text = stringResource(R.string.retry),
                        color = AppColors.colorScheme.primary,
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    )
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
