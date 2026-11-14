package com.smartwash.feature.toolbox.ui.delivery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.smartwash.common.ui.components.ButtonSpinner
import com.smartwash.common.ui.components.InfoRow
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.components.dialogEnterMotion
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressTween
import com.smartwash.common.utils.pressable
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.DeliveryRules
import com.smartwash.feature.toolbox.network.DeliveryItemVo
import com.smartwash.feature.toolbox.ui.delivery.DeliverySessionViewModel.PreviewState

/**
 * 条目预览/取走弹窗：文本=正文预览 +「复制并取走」（复制即 take+焚毁）；
 * 图片=取走确认 → 下载预览 →「保存到相册」；文件=取走确认 →「取走并保存」到系统下载目录。
 * 视觉对齐 AppConfirmDialog：radiusLg + surface + floatLayer 阴影 + 统一入场动效
 * （common:ui 的 dialogEnterMotion 公开 helper，模块内不再复制等价实现）。
 */
@Composable
internal fun DeliveryPreviewDialog(
    state: PreviewState,
    actionLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirmTakeImage: () -> Unit,
    onConfirmTakeFile: () -> Unit,
    onRetryContent: () -> Unit,
    onCopyText: () -> Unit,
    onSaveImage: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.spaceXl, vertical = AppDimens.spaceLg)
                ) {
                    PreviewHeader(state.item)
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    when (state) {
                        is PreviewState.TextLoading ->
                            SkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                            )

                        is PreviewState.TextReady -> TextReadyBody(state)

                        is PreviewState.ConfirmImage -> ConfirmBody(
                            item = state.item,
                            icon = Icons.Rounded.Image,
                            hint = stringResource(R.string.toolbox_delivery_confirm_image_message),
                        )

                        is PreviewState.ConfirmFile -> ConfirmBody(
                            item = state.item,
                            icon = Icons.Rounded.InsertDriveFile,
                            hint = stringResource(R.string.toolbox_delivery_confirm_file_message),
                        )

                        is PreviewState.ImageReady -> ImageReadyBody(state)

                        is PreviewState.ContentFailed -> ContentFailedBody()
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                PreviewActionRow(
                    state = state,
                    actionLoading = actionLoading,
                    onDismiss = onDismiss,
                    onConfirmTakeImage = onConfirmTakeImage,
                    onConfirmTakeFile = onConfirmTakeFile,
                    onRetryContent = onRetryContent,
                    onCopyText = onCopyText,
                    onSaveImage = onSaveImage,
                )
            }
        }
    }
}

@Composable
private fun PreviewHeader(item: DeliveryItemVo) {
    Text(
        text = item.title ?: stringResource(kindNameRes(DeliveryRules.ItemKind.fromCode(item.kind))),
        style = AppTextStyles.CardTitle,
        color = AppColors.colorScheme.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
    Text(
        text = stringResource(kindNameRes(DeliveryRules.ItemKind.fromCode(item.kind))),
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.colorScheme.textTertiary,
    )
}

/** 文本正文：surfaceVariant 嵌套块 + 最大高滚动（规范 §2.3 卡内嵌套块 radiusMd） */
@Composable
private fun TextReadyBody(state: PreviewState.TextReady) {
    if (state.text == null) {
        // 已焚毁（对端先取走或读取前已认领）：内容不可再读
        BurntPlaceholder(text = stringResource(R.string.toolbox_delivery_preview_text_burnt))
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(AppColors.colorScheme.surfaceVariant)
            .padding(AppDimens.spaceMd)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = state.text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textPrimary,
        )
    }
}

/** 图片/文件取走确认：语义图标容器（蓝·物流/寄件语义）+ 说明 + 文件元信息 */
@Composable
private fun ConfirmBody(
    item: DeliveryItemVo,
    icon: ImageVector,
    hint: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBox(
            icon = icon,
            size = 44.dp,
            iconSize = 24.dp,
            containerColor = AppColors.colorScheme.iconContainerBlue,
            iconTint = AppColors.colorScheme.iconForegroundBlue,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    if (!displayMimeType(item).isNullOrBlank() || item.objectSize != null) {
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Column {
            displayMimeType(item)?.let {
                InfoRow(
                    label = stringResource(R.string.toolbox_delivery_file_type),
                    value = it,
                )
            }
            item.objectSize?.let {
                InfoRow(
                    label = stringResource(R.string.toolbox_delivery_file_size),
                    value = DeliveryRules.formatFileSize(it),
                    valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}

/**
 * 条目类型展示：后端发布 multipart 未携带真实 MIME 时存 octet-stream，信息量低——
 * 端侧按扩展名推断兜底（推断失败回退原值；title 已带扩展名）。
 */
private fun displayMimeType(item: DeliveryItemVo): String? {
    val raw = item.mimeType
    if (!raw.isNullOrBlank() && raw != "application/octet-stream") return raw
    val ext = item.title?.substringAfterLast('.', "")?.lowercase(java.util.Locale.US).orEmpty()
    if (ext.isBlank()) return raw
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: raw
}

/** 内容获取失败占位（认领成功但下载未完成，条目未焚毁）：明确告知可重试（P0 端侧兜底） */
@Composable
private fun ContentFailedBody() {
    Text(
        text = stringResource(R.string.toolbox_delivery_take_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.colorScheme.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceMd),
    )
}

/** 图片预览：下载完成的缓存位图（解码失败回退「可直接保存」），强调已焚毁需及时保存 */
@Composable
private fun ImageReadyBody(state: PreviewState.ImageReady) {
    val bitmap = state.bitmap
    if (bitmap == null) {
        BurntPlaceholder(text = stringResource(R.string.toolbox_delivery_preview_decode_failed))
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(AppColors.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = state.item.title
                    ?: stringResource(R.string.toolbox_delivery_kind_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
    Text(
        text = stringResource(R.string.toolbox_delivery_preview_image_hint),
        style = MaterialTheme.typography.labelSmall,
        // 12sp 可读小字橙语义走 onWarningContainer 深档（ongoingInk #D98A26 白卡 2.76:1 违反 §10，
        // audit-07 P0-1 既有口径——与 SessionStrip 倒计时/待取走角标同档，原漏改此处）
        color = AppColors.colorScheme.onWarningContainer,
    )
}

/** 焚毁/解码失败占位（surfaceVariant 嵌套块，弱文本说明） */
@Composable
private fun BurntPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(AppColors.colorScheme.surfaceVariant)
            .padding(vertical = AppDimens.spaceXl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 弹窗动作区：对齐 AppConfirmDialog 分区按钮（左侧弱化关闭、右侧主动作） */
@Composable
private fun PreviewActionRow(
    state: PreviewState,
    actionLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirmTakeImage: () -> Unit,
    onConfirmTakeFile: () -> Unit,
    onRetryContent: () -> Unit,
    onCopyText: () -> Unit,
    onSaveImage: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        when (state) {
            is PreviewState.TextLoading -> {
                PreviewDialogAction(
                    text = stringResource(com.smartwash.common.ui.R.string.cancel),
                    color = AppColors.colorScheme.textSecondary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }

            is PreviewState.TextReady -> {
                if (state.text == null) {
                    // 焚毁态「关闭」= 中性动作（与 ImageReady 的关闭同色）；
                    // textGreen 保留给主动作（复制并取走/保存），不赋予关闭
                    PreviewDialogAction(
                        text = stringResource(R.string.toolbox_delivery_action_close),
                        color = AppColors.colorScheme.textSecondary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PreviewDialogAction(
                        text = stringResource(com.smartwash.common.ui.R.string.cancel),
                        color = AppColors.colorScheme.textSecondary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ActionDivider()
                    PreviewDialogAction(
                        text = stringResource(R.string.toolbox_delivery_action_copy_take),
                        color = AppColors.colorScheme.textGreen,
                        onClick = onCopyText,
                        loading = actionLoading,
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            is PreviewState.ConfirmImage -> {
                PreviewDialogAction(
                    text = stringResource(com.smartwash.common.ui.R.string.cancel),
                    color = AppColors.colorScheme.textSecondary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                ActionDivider()
                PreviewDialogAction(
                    text = stringResource(R.string.toolbox_delivery_action_take_image),
                    color = AppColors.colorScheme.textGreen,
                    onClick = onConfirmTakeImage,
                    loading = actionLoading,
                    enabled = !actionLoading,
                    modifier = Modifier.weight(1f),
                )
            }

            is PreviewState.ConfirmFile -> {
                PreviewDialogAction(
                    text = stringResource(com.smartwash.common.ui.R.string.cancel),
                    color = AppColors.colorScheme.textSecondary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                ActionDivider()
                PreviewDialogAction(
                    text = stringResource(R.string.toolbox_delivery_action_take_file),
                    color = AppColors.colorScheme.textGreen,
                    onClick = onConfirmTakeFile,
                    loading = actionLoading,
                    enabled = !actionLoading,
                    modifier = Modifier.weight(1f),
                )
            }

            is PreviewState.ImageReady -> {
                PreviewDialogAction(
                    text = stringResource(R.string.toolbox_delivery_action_close),
                    color = AppColors.colorScheme.textSecondary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                ActionDivider()
                PreviewDialogAction(
                    text = stringResource(R.string.toolbox_delivery_action_save_gallery),
                    color = AppColors.colorScheme.textGreen,
                    onClick = onSaveImage,
                    loading = actionLoading,
                    enabled = !actionLoading,
                    modifier = Modifier.weight(1f),
                )
            }

            is PreviewState.ContentFailed -> {
                PreviewDialogAction(
                    text = stringResource(R.string.toolbox_delivery_action_close),
                    color = AppColors.colorScheme.textSecondary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                ActionDivider()
                PreviewDialogAction(
                    text = stringResource(R.string.toolbox_delivery_action_retry),
                    color = AppColors.colorScheme.textGreen,
                    onClick = onRetryContent,
                    loading = actionLoading,
                    enabled = !actionLoading,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(48.dp)
            .background(AppColors.colorScheme.divider)
    )
}

/**
 * 弹窗底部动作（模式对齐 AppComponents.DialogAction：透明度按压反馈，无 ripple/state layer）；
 * loading 时以圆环替换文字（ButtonSpinner 内置 reduced motion 静态降级）。
 */
@Composable
private fun PreviewDialogAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    // 禁用态透明度 0ms 硬切 → 按压档 tween（reduced motion 经 motionSpec 降级 180ms）
    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled || loading) 1f else 0.5f,
        animationSpec = motionSpec(pressTween()),
        label = "previewActionAlpha"
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .then(
                if (enabled || loading) {
                    Modifier.pressable(onClick = onClick, alphaFactor = 0.5f, debounce = false)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            ButtonSpinner(size = 14.dp, strokeWidth = 1.5.dp, color = color)
        } else {
            Text(
                text = text,
                color = color.copy(alpha = contentAlpha),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** kind → 名称文案映射（弹窗副标题/列表角标共用） */
internal fun kindNameRes(kind: DeliveryRules.ItemKind): Int = when (kind) {
    DeliveryRules.ItemKind.TEXT -> R.string.toolbox_delivery_kind_text
    DeliveryRules.ItemKind.IMAGE -> R.string.toolbox_delivery_kind_image
    DeliveryRules.ItemKind.FILE -> R.string.toolbox_delivery_kind_file
}
