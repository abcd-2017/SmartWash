package com.smartwash.feature.toolbox.ui.detail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.DialogAction
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.InfoRow
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.components.dialogEnterMotion
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.StatusDot
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
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
import com.smartwash.feature.toolbox.ui.copyToClipboard
import com.smartwash.feature.toolbox.ui.notifyShortCodeChanged
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 续期弹窗展开/收起退出时长 — 动效语言 v0.4：退出 ≈ 进入的 70%（由 vocabulary 派生） */
private val RENEW_EXIT_MS = DURATION_MICRO_MS * 7 / 10

/**
 * 短链详情页：基础信息（路由携带）+ shortUrl 二维码（:common:utils generateQrCodeBitmap）+
 * 复制/浏览器打开（公开码直开 shortUrl，私有码经 resolve 取 target——计一次点击属预期）+
 * 续期（PUT expireAt）/ 删除（DELETE）+ 访问明细分页（/stats）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ShortCodeDetailPage(
    navController: NavHostController,
    // 一镜到底：共享元素 scope（2026-11-01）
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
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

    // 访问明细加载会话门控（2026-11-06 闪空态回归，同款防御）：本页 statsTrigger 恒空、
    // 无刷新/重建路径，故会话退化为「首轮加载」——仍加 everLoaded 门控，
    // 保证「NotLoading + 0 项」只有在真的完成过加载后才判空态，首帧不闪空态。
    var statsLoadStarted by rememberSaveable { mutableStateOf(false) }
    var statsEverLoaded by rememberSaveable { mutableStateOf(false) }
    val statsRefreshState = statsItems.loadState.refresh
    LaunchedEffect(statsRefreshState) {
        when (statsRefreshState) {
            is LoadState.Loading -> {
                statsLoadStarted = true
                statsEverLoaded = true
            }
            is LoadState.NotLoading -> {
                if (statsItems.itemCount > 0 || statsLoadStarted) statsLoadStarted = false
            }
            is LoadState.Error -> statsLoadStarted = false
        }
    }

    val isPublic = ShortCodeRules.isPublicVisibility(vo.isPublic)
    // 私密文本（contentType=7）：状态与分享链接按类型分支（方案 §4.7）
    val isSecret = ShortCodeRules.CONTENT_TYPE_SECRET_TEXT == vo.contentType
    val secretState = ShortCodeRules.secretStateOf(vo.secretState)
    // 终态（已焚毁/已过期）禁止续期（决策 9），页面隐藏入口、后端终审
    val secretTerminal = isSecret && (secretState == ShortCodeRules.SecretState.Burned ||
        secretState == ShortCodeRules.SecretState.Expired)

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

    // 底部避让手势条（与列表页已修口径同式）：本页是无底栏独立路由，
    // 末张明细卡不得贴/压手势条区域
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.toolbox_detail_title),
            onBack = { navController.navigateUp() },
            actions = {
                // 危险操作文字按钮 — 与列表页页头动作同规格：font.title 18/24/600，
                // 色 dangerInk（#A8435A，规范 error 唯一角色），热区 48 高
                Box(
                    modifier = Modifier
                        .pressable(onClick = { showDeleteDialog = true })
                        .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm)
                ) {
                    Text(
                        text = stringResource(R.string.toolbox_action_delete),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.colorScheme.dangerInk,
                    )
                }
            },
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = AppDimens.pagePadding,
                top = AppDimens.spaceSm,
                end = AppDimens.pagePadding,
                bottom = AppDimens.spaceSm + navBarsBottom,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ---------- 基础信息 ----------
            item(key = "info") {
                val now = remember { LocalDateTime.now() }
                val expiryState = ShortCodeRules.expiryStateOf(vo.expireAt, now)
                // 一镜到底：与列表卡共享边界（key 与列表侧一致）
                val infoCardModifier = if (animatedVisibilityScope != null && sharedTransitionScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "shortcode-card-${vo.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                } else {
                    Modifier
                }
                GroupCard(modifier = infoCardModifier) {
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_code),
                        value = vo.code,
                        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    )
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_type),
                        value = stringResource(
                            if (isSecret) R.string.toolbox_create_mode_secret
                            else R.string.toolbox_type_short_link
                        ),
                    )
                    if (!isSecret) {
                        InfoRow(
                            label = stringResource(R.string.toolbox_label_visibility),
                            value = stringResource(
                                if (isPublic) R.string.toolbox_visibility_public_value
                                else R.string.toolbox_visibility_private_value
                            ),
                            // 12sp 小字语义色走深档（对比度红线 §10）：公开=textGreen(#177A4C ≈5.1:1)、
                            // 私有=onWarningContainer(#92400E)；success(3.49:1)/ongoingInk(2.76:1) 作小字不达标
                            valueColor = if (isPublic) AppColors.colorScheme.textGreen else AppColors.colorScheme.onWarningContainer,
                        )
                    }
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_clicks),
                        value = stringResource(R.string.toolbox_clicks_format, vo.clickCount),
                        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        valueColor = AppColors.colorScheme.textGreen,
                    )
                    InfoRow(
                        label = stringResource(R.string.toolbox_label_created),
                        value = ShortCodeRules.formatDisplayDateTime(vo.createdAt)
                            .ifBlank { stringResource(R.string.toolbox_value_none) },
                        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    )
                    val (expiryText, expiryColor) = when (expiryState) {
                        is ShortCodeRules.ExpiryState.Permanent ->
                            // 「永久」12sp 小字绿用 textGreen 深档（success #2D9B6A 3.49:1 不达标，§10；
                            // 域内 2026-11-06 既有口径，同页点击数行已用对）
                            stringResource(R.string.toolbox_expiry_permanent) to AppColors.colorScheme.textGreen
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
                        valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        valueColor = expiryColor,
                    )
                    if (isSecret) {
                        val (stateText, stateColor) = when (secretState) {
                            ShortCodeRules.SecretState.Active ->
                                stringResource(R.string.toolbox_secret_state_active) to AppColors.colorScheme.textGreen
                            ShortCodeRules.SecretState.Burned ->
                                stringResource(R.string.toolbox_secret_state_burned) to AppColors.colorScheme.dangerInk
                            ShortCodeRules.SecretState.Expired ->
                                stringResource(R.string.toolbox_secret_state_expired) to AppColors.colorScheme.onWarningContainer
                            ShortCodeRules.SecretState.Unknown ->
                                stringResource(R.string.toolbox_secret_state_unknown) to AppColors.colorScheme.textSecondary
                        }
                        InfoRow(
                            label = stringResource(R.string.toolbox_secret_label_state),
                            value = stateText,
                            valueColor = stateColor,
                        )
                        if (secretState == ShortCodeRules.SecretState.Burned && vo.burnedAt != null) {
                            InfoRow(
                                label = stringResource(R.string.toolbox_secret_label_burned_at),
                                value = ShortCodeRules.formatDisplayDateTime(vo.burnedAt)
                                    .ifBlank { stringResource(R.string.toolbox_value_none) },
                                valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                        Text(
                            text = stringResource(R.string.toolbox_secret_privacy_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textSecondary,
                            modifier = Modifier.padding(top = AppDimens.spaceXxs),
                        )
                    }
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
                    // 续期入口：私密文本终态（焚毁/过期）隐藏（决策 9）
                    if (!secretTerminal) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                // 热区外扩至 48dp（触控红线 ≥48，对齐列表页已修口径）：
                                // 原 vertical 4×2 + labelMedium 行高 16 仅 24dp；视觉字号与位置不变
                                .defaultMinSize(minHeight = 48.dp)
                                .pressable(onClick = { showRenewDialog = true })
                                .padding(vertical = AppDimens.spaceXxs),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 行内文字动作 — 13sp 非规范档收敛为 captionStrong 12/500（规范 §2 迁移表 13→12）
                            Text(
                                text = stringResource(R.string.toolbox_action_renew),
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.colorScheme.textGreen,
                            )
                        }
                    }
                }
            }

            // ---------- 二维码与动作 ----------
            item(key = "qr") {
                if (isSecret) {
                    QrCard(
                        url = vo.secretUrl,
                        sectionTitle = stringResource(R.string.toolbox_secret_link_label),
                        hint = stringResource(R.string.toolbox_secret_link_hint),
                        unavailableText = stringResource(R.string.toolbox_secret_link_unavailable),
                        // 链接不可用（未配置 Web 域名）或秘密已终态（焚毁/过期，链接已失效）时
                        // 禁用复制/打开——避免按钮可点却静默无效；终态语义已由状态行表达
                        actionsEnabled = !secretTerminal && !vo.secretUrl.isNullOrBlank(),
                        onCopy = {
                            vo.secretUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                copyToClipboard(context, url)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toolbox_secret_copy_success),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onOpen = { openInBrowser(context, vo.secretUrl.orEmpty()) },
                    )
                } else {
                    QrCard(
                        url = vo.shortUrl,
                        sectionTitle = stringResource(R.string.toolbox_qr_section_title),
                        hint = if (!isPublic) {
                            stringResource(R.string.toolbox_private_open_hint)
                        } else {
                            null
                        },
                        unavailableText = null,
                        actionsEnabled = !vo.shortUrl.isNullOrBlank(),
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
                GroupCard {
                    when {
                        // 首刷失败：说明 + 重试（规范 §3.6 错误态）。
                        // 错误分支前置：门控不得掩盖真实错误（否则失败时先闪一帧骨架）
                        statsItems.itemCount == 0 && statsRefreshState is LoadState.Error -> {
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
                                color = AppColors.colorScheme.textGreen,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable(onClick = { statsItems.retry() })
                                    // 热区 48dp（触控红线）：16×2 + 行高 16，与列表页同位置已修口径一致
                                    //（原 8×2 = 32 不达标）
                                    .padding(vertical = AppDimens.spaceMd),
                            )
                        }
                        // 首刷 / 从未完成过加载：骨架而非空态（见会话门控注释）
                        statsItems.itemCount == 0 &&
                            (statsRefreshState is LoadState.Loading || !statsEverLoaded) -> {
                            SkeletonList(rows = 3)
                        }
                        // 走到这里必为 everLoaded=true（会话门控保证），语义=真实空结果
                        statsItems.itemCount == 0 &&
                            statsRefreshState is LoadState.NotLoading &&
                            statsEverLoaded -> {
                            Text(
                                text = stringResource(R.string.toolbox_stats_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                // 空态属可读状态正文：textTertiary ≈2.3:1 不达标（DESIGN-SPEC §10），
                                // 与同页骨架/错误分支一致降档改 textSecondary
                                color = AppColors.colorScheme.textSecondary,
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
                            // 已有数据时补拉失败：整行可点重试（48dp 热区，与首刷失败分支及列表页
                            // append 分支行为对齐——规范 §3.6 错误态须给重试入口），文案含解决指引
                            if (statsRefreshState is LoadState.Error && statsItems.itemCount > 0) {
                                Text(
                                    text = stringResource(R.string.toolbox_stats_error_retry),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.colorScheme.dangerInk,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressable(onClick = { statsItems.retry() })
                                        .padding(vertical = AppDimens.spaceMd),
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

/** 二维码卡：url 生成（默认线程池，组合期副作用收敛在 produceState）+ 复制/打开。
 *  私密文本传 secretUrl（链接不可用时降级提示文案，不再骨架空转）；
 *  [actionsEnabled] 为 false（链接不可用/终态）时按钮禁用、「查看行为」提示一并隐藏 */
@Composable
private fun QrCard(
    url: String?,
    sectionTitle: String,
    hint: String?,
    unavailableText: String?,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    actionsEnabled: Boolean = true,
) {
    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.Default) {
            url?.takeIf { it.isNotBlank() }?.let { generateQrCodeBitmap(it, 512) }
        }
    }
    GroupCard {
        Text(
            text = sectionTitle,
            style = AppTextStyles.SectionTitle,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.padding(bottom = AppDimens.spaceMd),
        )
        val bitmap = qrBitmap
        if (url.isNullOrBlank() && unavailableText != null) {
            // 分享链接不可用（web-base-url 未配置）：降级文案而非骨架空转
            Text(
                text = unavailableText,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.spaceMd),
            )
        } else if (bitmap == null) {
            SkeletonBlock(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(AppDimens.radiusMd),
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = sectionTitle,
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
            text = url.orEmpty(),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = AppColors.colorScheme.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.spaceMd),
        )
        // 「查看行为」提示只在链接真实可用时展示——不可用降级/终态下与降级文案、状态行并存
        // 会自相矛盾（链接已 410 还承诺「打开即可查看」）
        if (hint != null && actionsEnabled) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                // 12sp 可读小字橙语义走 onWarningContainer 深档（ongoingInk #D98A26 仅 2.76:1，§10 红线）
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
                enabled = actionsEnabled,
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = stringResource(R.string.toolbox_action_open),
                onClick = onOpen,
                enabled = actionsEnabled,
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
                text = stats.ipHash.orEmpty().ifEmpty { stringResource(R.string.toolbox_value_none) },
                containerColor = AppColors.colorScheme.surfaceVariant,
                // chip 内 IP 哈希是用户可读数据值：textTertiary on surfaceVariant(#EFF1EE) ≈2.0:1
                // 不达标（§10 红线），改 textSecondary
                contentColor = AppColors.colorScheme.textSecondary,
            )
        }
        if (!stats.userAgent.isNullOrBlank()) {
            Text(
                text = stats.userAgent,
                style = MaterialTheme.typography.labelSmall,
                // UA 是访问记录数据值而非装饰角标：textTertiary ≈2.3:1 不达标（§10），改 textSecondary
                color = AppColors.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = AppDimens.space2xs, start = AppDimens.spaceSm),
            )
        }
        if (!stats.referer.isNullOrBlank()) {
            Text(
                text = stats.referer,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = AppDimens.space2xs, start = AppDimens.spaceSm),
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
            // 统一弹窗入场动效（公共 helper：AppComponents.dialogEnterMotion，与 AppConfirmDialog 同规格）
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = AppColors.colorScheme.surface,
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
                        color = AppColors.colorScheme.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    ToolboxExpiryPresetRow(
                        selected = preset,
                        onSelect = onPresetChange,
                        includePermanent = false,
                    )
                    val reduceMotion = LocalReduceMotion.current
                    AnimatedVisibility(
                        visible = preset == ShortCodeRules.ExpiryPreset.CUSTOM,
                        // reduced motion：只留 180ms 淡入淡出，展开/收起（尺寸动画）归零
                        enter = if (reduceMotion) {
                            fadeIn(animationSpec = tween(CROSSFADE_ALT_MS))
                        } else {
                            fadeIn(enterTween(DURATION_MICRO_MS)) +
                                expandVertically(enterTween(DURATION_MICRO_MS))
                        },
                        exit = if (reduceMotion) {
                            fadeOut(animationSpec = tween(CROSSFADE_ALT_MS))
                        } else {
                            fadeOut(exitTween(RENEW_EXIT_MS)) +
                                shrinkVertically(exitTween(RENEW_EXIT_MS))
                        },
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                            AppButton(
                                text = customDate?.let { ShortCodeRules.formatDate(it) }
                                    ?: stringResource(R.string.toolbox_expiry_custom_pick),
                                onClick = onPickDate,
                                variant = AppButtonVariant.SECONDARY,
                            )
                        }
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
                        color = AppColors.colorScheme.textGreen,
                        onClick = onConfirm,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
