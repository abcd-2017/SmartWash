package com.smartwash.feature.toolbox.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.InfoRow
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.StatusDot
import com.smartwash.common.utils.generateQrCodeBitmap
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.ToolboxConstant
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import com.smartwash.feature.toolbox.network.ShortCodeStatsVo
import com.smartwash.feature.toolbox.ui.ToolboxDatePickerDialog
import com.smartwash.feature.toolbox.ui.ToolboxExpiryPresetRow
import com.smartwash.feature.toolbox.ui.ToolboxTagChip
import com.smartwash.feature.toolbox.ui.notifyShortCodeChanged
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 短链详情页：基础信息（路由携带）+ shortUrl 二维码（:common:utils generateQrCodeBitmap）+
 * 复制/浏览器打开（公开码直开 shortUrl，私有码经 resolve 取 target——计一次点击属预期）+
 * 续期（PUT expireAt）/ 删除（DELETE）+ 访问明细分页（/stats）。
 */
@Composable
fun ShortCodeDetailPage(
    navController: NavHostController,
    viewModel: ShortCodeDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val vo = viewModel.shortCode
    val updateState by viewModel.updateState.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()
    val resolveState by viewModel.resolveState.collectAsState()
    val resolvedTarget by viewModel.resolvedTarget.collectAsState()
    val statsItems = viewModel.statsPagingFlow.collectAsLazyPagingItems()

    var showRenewDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenewDatePicker by remember { mutableStateOf(false) }
    var renewPreset by remember { mutableStateOf(ShortCodeRules.ExpiryPreset.DAYS_7) }
    var renewDate by remember { mutableStateOf<LocalDate?>(null) }

    val isPublic = ShortCodeRules.isPublicVisibility(vo.isPublic)

    // 续期结果：成功 → 提示 + 刷新信号 + 返回列表（列表重建后展示新过期态）
    LaunchedEffect(updateState) {
        val state = updateState
        when (state) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.toolbox_toast_renew_success), Toast.LENGTH_SHORT).show()
                navController.notifyShortCodeChanged()
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
        val state = deleteState
        when (state) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.toolbox_toast_delete_success), Toast.LENGTH_SHORT).show()
                navController.notifyShortCodeChanged()
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

    // 私有码解析：成功取 target 开浏览器（点击统计 +1 属预期），失败提示
    LaunchedEffect(resolveState) {
        when (resolveState) {
            is RequestState.Success -> {
                val target = resolvedTarget
                if (target.isNullOrBlank()) {
                    Toast.makeText(context, context.getString(R.string.toolbox_resolve_failed), Toast.LENGTH_SHORT).show()
                } else {
                    openInBrowser(context, target)
                }
                viewModel.resetResolveState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, context.getString(R.string.toolbox_resolve_failed), Toast.LENGTH_SHORT).show()
                viewModel.resetResolveState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.toolbox_detail_title),
            onBack = { navController.navigateUp() },
            actions = {
                // 危险操作文字按钮（规范 §3.3 文字按钮 / §2.1 dangerInk 小字变体）
                Box(
                    modifier = Modifier
                        .pressable(onClick = { showDeleteDialog = true })
                        .padding(horizontal = AppDimens.spaceSm, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.toolbox_action_delete),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.colorScheme.dangerInk,
                    )
                }
            },
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = AppDimens.pagePadding,
                vertical = AppDimens.spaceSm,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ---------- 基础信息 ----------
            item(key = "info") {
                val now = remember { LocalDateTime.now() }
                val expiryState = ShortCodeRules.expiryStateOf(vo.expireAt, now)
                GroupCard {
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_code),
                        value = vo.code,
                        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    )
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_type),
                        value = stringResource(R.string.toolbox_type_short_link),
                    )
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_visibility),
                        value = stringResource(
                            if (isPublic) R.string.toolbox_visibility_public_value
                            else R.string.toolbox_visibility_private_value
                        ),
                        valueColor = if (isPublic) AppColors.colorScheme.success else AppColors.colorScheme.ongoingInk,
                    )
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_clicks),
                        value = stringResource(R.string.toolbox_clicks_format, vo.clickCount),
                        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        valueColor = AppColors.colorScheme.primary,
                    )
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_created),
                        value = ShortCodeRules.formatDisplayDateTime(vo.createdAt)
                            .ifBlank { stringResource(R.string.toolbox_value_none) },
                    )
                    val (expiryText, expiryColor) = when (expiryState) {
                        is ShortCodeRules.ExpiryState.Permanent ->
                            stringResource(R.string.toolbox_expiry_permanent) to AppColors.colorScheme.success
                        is ShortCodeRules.ExpiryState.Expired ->
                            stringResource(R.string.toolbox_expiry_expired) to AppColors.colorScheme.dangerInk
                        is ShortCodeRules.ExpiryState.Active ->
                            stringResource(
                                R.string.toolbox_expiry_until_format,
                                ShortCodeRules.formatDisplayDateTime(vo.expireAt),
                            ) to AppColors.colorScheme.textSecondary
                    }
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_expiry),
                        value = expiryText,
                        valueColor = expiryColor,
                    )
                    if (!vo.target.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                        Text(
                            text = stringResource(R.string.toolbox_label_target),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colorScheme.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                        Text(
                            text = vo.target,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.colorScheme.textPrimary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .pressable(onClick = { showRenewDialog = true })
                            .padding(vertical = AppDimens.spaceXxs)
                    ) {
                        Text(
                            text = stringResource(R.string.toolbox_action_renew),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.colorScheme.primary,
                        )
                    }
                }
            }

            // ---------- 二维码与动作 ----------
            item(key = "qr") {
                QrCard(
                    shortUrl = vo.shortUrl,
                    isPublic = isPublic,
                    onCopy = {
                        vo.shortUrl?.let { url ->
                            copyToClipboard(context, url)
                            Toast.makeText(context, context.getString(R.string.toolbox_copy_success), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpen = {
                        if (isPublic) {
                            openInBrowser(context, vo.shortUrl.orEmpty())
                        } else {
                            viewModel.resolveTarget()
                        }
                    },
                )
            }

            // ---------- 访问明细 ----------
            item(key = "stats_header") {
                Text(
                    text = stringResource(R.string.toolbox_stats_title),
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.colorScheme.textPrimary,
                )
            }
            item(key = "stats") {
                val statsRefresh = statsItems.loadState.refresh
                GroupCard {
                    when {
                        statsItems.itemCount == 0 && statsRefresh is LoadState.Loading -> {
                            SkeletonList(rows = 3)
                        }
                        // 首刷失败：说明 + 重试（规范 §3.6 错误态）
                        statsItems.itemCount == 0 && statsRefresh is LoadState.Error -> {
                            Text(
                                text = stringResource(R.string.toolbox_stats_error_load),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.colorScheme.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppDimens.spaceXs),
                            )
                            Text(
                                text = stringResource(R.string.toolbox_action_retry),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable(onClick = { statsItems.retry() })
                                    .padding(vertical = AppDimens.spaceXs),
                            )
                        }
                        statsItems.itemCount == 0 && statsRefresh is LoadState.NotLoading -> {
                            Text(
                                text = stringResource(R.string.toolbox_stats_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.colorScheme.textTertiary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppDimens.spaceMd),
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> {
                            statsItems.itemCount.let { count ->
                                repeat(count) { index ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = AppColors.colorScheme.hairline,
                                        )
                                    }
                                    statsItems[index]?.let { StatRow(it) }
                                }
                            }
                            if (statsItems.loadState.append is LoadState.Loading) {
                                SkeletonBlock(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                )
                            }
                            if (statsRefresh is LoadState.Error && statsItems.itemCount > 0) {
                                Text(
                                    text = stringResource(R.string.toolbox_stats_error_load),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.colorScheme.dangerInk,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AppDimens.spaceXs),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 续期弹窗 ----------
    if (showRenewDialog) {
        RenewDialog(
            preset = renewPreset,
            customDate = renewDate,
            loading = updateState is RequestState.Loading,
            onPresetChange = { renewPreset = it },
            onPickDate = { showRenewDatePicker = true },
            onConfirm = {
                if (updateState !is RequestState.Loading) {
                    viewModel.renewExpire(renewPreset, renewDate)
                }
            },
            onDismiss = {
                showRenewDialog = false
                renewDate = null
                renewPreset = ShortCodeRules.ExpiryPreset.DAYS_7
            },
        )
    }

    if (showRenewDatePicker) {
        ToolboxDatePickerDialog(
            initialDate = renewDate,
            onConfirm = {
                renewDate = it
                showRenewDatePicker = false
            },
            onDismiss = { showRenewDatePicker = false },
        )
    }

    // ---------- 删除确认 ----------
    if (showDeleteDialog) {
        com.smartwash.common.ui.components.AppConfirmDialog(
            title = stringResource(R.string.toolbox_confirm_delete_title),
            message = stringResource(R.string.toolbox_confirm_delete_message, vo.code),
            isDanger = true,
            confirmText = stringResource(R.string.toolbox_action_delete),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/** 二维码卡：shortUrl 生成（默认线程池，组合期副作用收敛在 produceState）+ 复制/打开 */
@Composable
private fun QrCard(
    shortUrl: String?,
    isPublic: Boolean,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = shortUrl) {
        value = withContext(Dispatchers.Default) {
            shortUrl?.takeIf { it.isNotBlank() }?.let { generateQrCodeBitmap(it, 512) }
        }
    }
    GroupCard {
        Text(
            text = stringResource(R.string.toolbox_qr_section_title),
            style = AppTextStyles.SectionTitle,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.padding(bottom = AppDimens.spaceMd),
        )
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
                contentDescription = stringResource(R.string.toolbox_qr_section_title),
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
            text = shortUrl.orEmpty(),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = AppColors.colorScheme.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.spaceMd),
        )
        if (!isPublic) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Text(
                text = stringResource(R.string.toolbox_private_open_hint),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.ongoingInk,
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
                text = stringResource(R.string.toolbox_action_open),
                onClick = onOpen,
                variant = AppButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 访问明细行：时间 + IP 摘要，次行 UA / Referer（均脱敏弱化展示） */
@Composable
private fun StatRow(stats: ShortCodeStatsVo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = AppColors.colorScheme.water)
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            Text(
                text = ShortCodeRules.formatDisplayDateTime(stats.createdAt),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = AppColors.colorScheme.textPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            ToolboxTagChip(
                text = stats.ipHash.orEmpty().ifEmpty { "—" },
                containerColor = AppColors.colorScheme.surfaceVariant,
                contentColor = AppColors.colorScheme.textTertiary,
            )
        }
        if (!stats.userAgent.isNullOrBlank()) {
            Text(
                text = stats.userAgent,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = AppDimens.spaceXxs + 8.dp),
            )
        }
        if (!stats.referer.isNullOrBlank()) {
            Text(
                text = stats.referer,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = AppDimens.spaceXxs + 8.dp),
            )
        }
    }
}

/** 续期弹窗：预设（7 天/30 天/自定义）+ 自定义日期入口 + 确认/取消 */
@Composable
private fun RenewDialog(
    preset: ShortCodeRules.ExpiryPreset,
    customDate: LocalDate?,
    loading: Boolean,
    onPresetChange: (ShortCodeRules.ExpiryPreset) -> Unit,
    onPickDate: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
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
                    Text(
                        text = stringResource(R.string.toolbox_renew_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    ToolboxExpiryPresetRow(
                        selected = preset,
                        onSelect = onPresetChange,
                        includePermanent = false,
                    )
                    if (preset == ShortCodeRules.ExpiryPreset.CUSTOM) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                        AppButton(
                            text = customDate?.let { ShortCodeRules.formatDate(it) }
                                ?: stringResource(R.string.toolbox_expiry_custom_pick),
                            onClick = onPickDate,
                            variant = AppButtonVariant.SECONDARY,
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                Row(modifier = Modifier.fillMaxWidth()) {
                    DialogAction(
                        text = stringResource(com.smartwash.common.ui.R.string.cancel),
                        color = AppColors.colorScheme.textSecondary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(48.dp)
                            .background(AppColors.colorScheme.divider)
                    )
                    DialogAction(
                        text = stringResource(com.smartwash.common.ui.R.string.confirm),
                        color = AppColors.colorScheme.primary,
                        onClick = onConfirm,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 弹窗底部动作区（透明度按压反馈，模式对齐 AppComponents.DialogAction） */
@Composable
private fun DialogAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .pressable(onClick = onClick, alphaFactor = 0.5f, debounce = false)
            .then(if (!enabled) Modifier.background(Color.Transparent) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color.copy(alpha = if (enabled) 1f else 0.5f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 浏览器打开（App 内不拦截跳转；无浏览器兜底提示） */
private fun openInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.toolbox_open_failed), Toast.LENGTH_SHORT).show()
    }
}

/** 复制到剪贴板 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("shortUrl", text))
}
