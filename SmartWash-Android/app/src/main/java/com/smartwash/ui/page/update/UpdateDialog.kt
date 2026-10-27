package com.smartwash.ui.page.update

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartwash.R
import com.smartwash.feature.update.model.AppVersionVo
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
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
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pressable(onClick = onUpdateNow)
                            .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.buttonRadius)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.update_now),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressable(onClick = onUpdateLater)
                                .border(1.dp, AppColors.colorScheme.outline, RoundedCornerShape(AppDimens.buttonRadius)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.update_later),
                                color = AppColors.colorScheme.textSecondary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressable(onClick = onUpdateNow)
                                .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.buttonRadius)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.update_now),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
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
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pressable(onClick = onUpdateNow)
                        .background(AppColors.colorScheme.error, RoundedCornerShape(AppDimens.buttonRadius)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.update_now),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * 准备下载弹窗 — 获取预签名地址阶段展示加载指示器，不允许关闭
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
                PreparingSpinner(
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
 * 下载进度弹窗
 */
@Composable
fun DownloadProgressDialog(
    progress: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit,
) {
    // 下载进度每 1% 硬跳 → 弹簧平滑到目标值（进度本身仍由 WorkManager 真实进度驱动）；
    // reduced motion 经 motionSpec 降级为 180ms 短过渡，不引入位移/缩放
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
                    progress = { animatedProgress },
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
                    // 百分比宽度随位数变化 → tabular numbers 等宽，消除数字抖动
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pressable(onClick = onCancel)
                        .border(1.dp, AppColors.colorScheme.outline, RoundedCornerShape(AppDimens.buttonRadius)),
                    contentAlignment = Alignment.Center,
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
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressable(onClick = onInstallLater),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.install_later),
                            color = AppColors.colorScheme.textSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressable(onClick = onInstallNow),
                        contentAlignment = Alignment.Center,
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
 * 下载失败弹窗 — 展示失败原因并提供重试入口（下载链路错误的唯一 App 内反馈，
 * 避免进度弹窗静默消失让用户不知发生了什么）
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
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.download_failed_icon),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.download_failed),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = AppColors.colorScheme.textSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressable(onClick = onRetry),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.retry),
                            color = AppColors.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 更新弹窗统一入场动效 — 与公共 `AppConfirmDialog` 同规格：scale 0.92 → 1 + alpha 0 → 1
 * 一次性入场；reduced motion 由 [motionSpec] 自动降级为 180ms 纯淡入（缩放归零）。
 *
 * 公共 helper 是 common:ui 模块内部私有实现，壳层无法直接复用，故在本目录复制等价实现。
 * 退出动画受 Dialog 窗口机制限制（窗口由系统直接移除，拿不到退出帧），本仓库一律不做。
 */
private fun Modifier.dialogEnterMotion(): Modifier = composed {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = motionSpec(defaultSpring()),
        label = "updateDialogEnter"
    )
    val reduceMotion = LocalReduceMotion.current
    this.graphicsLayer {
        alpha = progress
        val scale = if (reduceMotion) 1f else 0.92f + 0.08f * progress
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 准备下载指示器 — 规范 §8：reduced motion 取消无限旋转，退化为**静态 270° 圆环**
 * （保留"在忙"可读性）；正常态保留旋转。
 */
@Composable
private fun PreparingSpinner(
    size: Dp,
    strokeWidth: Dp,
    color: Color,
) {
    if (LocalReduceMotion.current) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    } else {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
        )
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
