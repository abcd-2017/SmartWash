package com.smartwash.feature.toolbox.ui.assettag

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.generateQrCodeBitmap
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.api.ToolboxRoute
import com.smartwash.feature.toolbox.logic.AssetTagRules
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import com.smartwash.feature.toolbox.ui.ToolboxTagChip
import com.smartwash.feature.toolbox.ui.copyToClipboard
import com.smartwash.feature.toolbox.ui.notifyAssetTagChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 物品标签详情页：信息卡 + 状态流转（按当前状态给出唯一动作）+ 标签链接二维码 +
 * 复制/分享标签图（物品名 + 二维码合成，可保存/打印张贴）+ 删除。
 */
@Composable
fun AssetTagDetailPage(
    navController: NavHostController,
    viewModel: AssetTagDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val vo = viewModel.assetTag
    val updateState by viewModel.updateState.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }

    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.asset_tag_toast_status_success), Toast.LENGTH_SHORT).show()
                navController.notifyAssetTagChanged()
                navController.popBackStack()
                viewModel.resetUpdateState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateState()
            }
            else -> {}
        }
    }

    LaunchedEffect(deleteState) {
        when (val state = deleteState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.asset_tag_toast_delete_success), Toast.LENGTH_SHORT).show()
                navController.notifyAssetTagChanged()
                navController.popBackStack()
                viewModel.resetDeleteState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.resetDeleteState()
            }
            else -> {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.asset_tag_detail_title),
            onBack = { navController.navigateUp() },
            actions = {
                Box(
                    modifier = Modifier
                        .pressable(onClick = { showDeleteDialog = true })
                        .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm)
                ) {
                    Text(
                        text = stringResource(R.string.asset_tag_action_delete),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.colorScheme.dangerInk,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.pagePadding)
                .padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
        ) {
            // ---------- 信息卡 ----------
            GroupCard {
                InfoRows(vo = vo)
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = stringResource(R.string.asset_tag_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary,
                )
            }

            // ---------- 状态流转卡（按当前状态给出唯一动作，裁定在后端） ----------
            GroupCard {
                val actionText = when (vo.status) {
                    AssetTagRules.STATUS_NORMAL -> stringResource(R.string.asset_tag_action_set_lost)
                    AssetTagRules.STATUS_LOST -> stringResource(R.string.asset_tag_action_set_found)
                    else -> stringResource(R.string.asset_tag_action_set_normal)
                }
                val targetStatus = when (vo.status) {
                    AssetTagRules.STATUS_NORMAL -> AssetTagRules.STATUS_LOST
                    AssetTagRules.STATUS_LOST -> AssetTagRules.STATUS_FOUND
                    else -> AssetTagRules.STATUS_NORMAL
                }
                Text(
                    text = stringResource(R.string.asset_tag_label_status),
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.colorScheme.textPrimary,
                    modifier = Modifier.padding(bottom = AppDimens.spaceMd),
                )
                AppButton(
                    text = actionText,
                    onClick = { viewModel.changeStatus(targetStatus) },
                    loading = updateState is RequestState.Loading,
                )
            }

            // ---------- 标签链接与分享 ----------
            TagLinkCard(
                vo = vo,
                onCopy = {
                    if (vo.tagUrl?.isNotBlank() == true) {
                        copyToClipboard(context, vo.tagUrl.orEmpty())
                        Toast.makeText(context, context.getString(R.string.asset_tag_copy_success), Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    if (vo.tagUrl?.isNotBlank() == true) {
                        shareLabelImage(context, vo.itemName.orEmpty(), vo.tagUrl.orEmpty(), vo.code)
                    }
                },
            )
        }
    }

    if (showDeleteDialog) {
        AppConfirmDialog(
            title = stringResource(R.string.asset_tag_confirm_delete_title),
            message = stringResource(R.string.asset_tag_confirm_delete_message, vo.itemName.orEmpty()),
            isDanger = true,
            confirmText = stringResource(R.string.asset_tag_action_delete),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun InfoRows(vo: com.smartwash.feature.toolbox.api.network.AssetTagVo) {
    val (container, content) = when (vo.status) {
        AssetTagRules.STATUS_LOST ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AssetTagRules.STATUS_FOUND ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else ->
            AppColors.colorScheme.surfaceVariant to AppColors.colorScheme.textSecondary
    }
    val statusLabel = when (vo.status) {
        AssetTagRules.STATUS_LOST -> stringResource(R.string.asset_tag_status_lost)
        AssetTagRules.STATUS_FOUND -> stringResource(R.string.asset_tag_status_found)
        else -> stringResource(R.string.asset_tag_status_normal)
    }
    com.smartwash.common.ui.components.InfoRow(
        label = stringResource(R.string.asset_tag_item_name_label),
        value = vo.itemName.orEmpty(),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceXs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.asset_tag_label_status),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textSecondary,
        )
        ToolboxTagChip(text = statusLabel, containerColor = container, contentColor = content)
    }
    com.smartwash.common.ui.components.InfoRow(
        label = stringResource(R.string.asset_tag_label_code),
        value = vo.code,
        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
    )
    com.smartwash.common.ui.components.InfoRow(
        label = stringResource(R.string.asset_tag_label_scans),
        value = stringResource(R.string.asset_tag_scans_format, vo.scanCount),
        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        valueColor = AppColors.colorScheme.textGreen,
    )
    com.smartwash.common.ui.components.InfoRow(
        label = stringResource(R.string.asset_tag_label_last_scanned),
        value = ShortCodeRules.formatDisplayDateTime(vo.lastScannedAt)
            .ifBlank { stringResource(R.string.toolbox_value_none) },
        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
    )
    if (!vo.itemDesc.isNullOrBlank()) {
        com.smartwash.common.ui.components.InfoRow(
            label = stringResource(R.string.asset_tag_item_desc_label),
            value = vo.itemDesc.orEmpty(),
        )
    }
}

/** 标签链接卡：二维码（tagUrl）+ 链接文本 + 复制/分享标签图；链接不可用降级提示 */
@Composable
private fun TagLinkCard(
    vo: com.smartwash.feature.toolbox.api.network.AssetTagVo,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = vo.tagUrl) {
        value = withContext(Dispatchers.Default) {
            vo.tagUrl?.takeIf { it.isNotBlank() }?.let { generateQrCodeBitmap(it, 512) }
        }
    }
    GroupCard {
        Text(
            text = stringResource(R.string.asset_tag_link_label),
            style = AppTextStyles.SectionTitle,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.padding(bottom = AppDimens.spaceMd),
        )
        if (vo.tagUrl.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.asset_tag_link_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.spaceMd),
            )
        } else {
            val bitmap = qrBitmap
            if (bitmap == null) {
                SkeletonBlock(
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(AppDimens.radiusMd),
                )
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.asset_tag_link_label),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(AppDimens.radiusMd))
                        .background(Color.White),
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                text = vo.tagUrl.orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.spaceMd),
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Text(
                text = stringResource(R.string.asset_tag_link_hint),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.onWarningContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
            AppButton(
                text = stringResource(R.string.toolbox_action_copy),
                onClick = onCopy,
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = stringResource(R.string.asset_tag_action_share_label),
                onClick = onShare,
                variant = AppButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f),
            )
        }
    }
    HorizontalDivider(thickness = 0.dp, color = Color.Transparent)
}

/** 分享标签图：物品名 + 二维码合成（默认线程池），经系统分享面板分发（可保存/打印张贴） */
private fun shareLabelImage(context: Context, itemName: String, url: String, code: String) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        val bitmap = withContext(Dispatchers.Default) {
            buildAssetTagLabelBitmap(itemName, url, code)
        }
        shareBitmap(context, bitmap, context.getString(R.string.asset_tag_share_chooser))
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap, chooserTitle: String) {
    runCatching {
        val cachePath = java.io.File(context.cacheDir, "asset_tag_labels").apply { mkdirs() }
        val file = java.io.File(cachePath, "tag_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.toolbox_open_failed), Toast.LENGTH_SHORT).show()
    }
}
