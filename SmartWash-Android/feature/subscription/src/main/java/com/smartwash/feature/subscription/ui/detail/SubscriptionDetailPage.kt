package com.smartwash.feature.subscription.ui.detail

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LocalCoverArt
import com.smartwash.common.ui.components.InfoRow
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.StatusDot
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.SubscriptionConstant
import com.smartwash.feature.subscription.SubscriptionRoute
import com.smartwash.feature.subscription.SubscriptionStatus
import com.smartwash.feature.subscription.logic.SubscriptionRules
import com.smartwash.feature.subscription.network.SubscriptionVo
import com.smartwash.feature.subscription.network.UsageLogVo
import com.smartwash.feature.subscription.ui.SubscriptionTagChip
import com.smartwash.feature.subscription.ui.copyToClipboard
import com.smartwash.feature.subscription.ui.navigateToSubscriptionEdit
import com.smartwash.feature.subscription.ui.notifySubscriptionChanged
import com.smartwash.feature.subscription.ui.renewRelativeColor
import com.smartwash.feature.subscription.ui.renewRelativeText
import com.smartwash.feature.subscription.ui.statusChipContainer
import com.smartwash.feature.subscription.ui.statusChipContent
import com.smartwash.feature.subscription.ui.statusText
import java.time.LocalDate

/** 折叠卡展开/收起退出时长 — 动效语言 v0.4：退出 ≈ 进入的 70%（由 vocabulary 派生） */
private val COLLAPSE_EXIT_MS = DURATION_MICRO_MS * 7 / 10

/**
 * 订阅详情页：续费信息卡（金额/周期/续费日/试用/自动续费/状态）+ 状态操作
 * （active↔pause、cancel/finish，按当前状态显隐；终态只读）+ 近 90 天使用统计 +
 * 使用打卡按钮与打卡列表（可撤销）+ 取消路径折叠卡（网址可打开/复制）。
 * 详情数据实时拉取；编辑经表单页（返回经 CHANGED_KEY 触发刷新并转发列表）。
 */
@Composable
fun SubscriptionDetailPage(
    navController: NavHostController,
    viewModel: SubscriptionDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val detail by viewModel.detail.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val statsItem by viewModel.statsItem.collectAsState()
    val statsState by viewModel.statsState.collectAsState()
    val transitionState by viewModel.transitionState.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()
    val checkInState by viewModel.checkInState.collectAsState()
    val undoState by viewModel.undoState.collectAsState()
    val usageItems = viewModel.usagePagingFlow.collectAsLazyPagingItems()

    var showCancelDialog by rememberSaveable { mutableStateOf(false) }
    var showFinishDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var pendingUndoId by rememberSaveable { mutableStateOf(-1L) }
    var cancelPathExpanded by rememberSaveable { mutableStateOf(false) }

    // 打卡列表刷新会话门控（2026-11-06 闪空态回归）：refreshUsages 重建 Pager 时先把列表清空，
    // 「清空后、新加载尚未开始」的帧里 refresh=NotLoading && itemCount=0 会命中空态分支闪一帧。
    // 会话期间走骨架；只有会话结束（有数据，或本轮加载已完成）才允许真实空态。
    var usageRefreshSession by rememberSaveable { mutableStateOf(false) }
    var usageLoadStarted by rememberSaveable { mutableStateOf(false) }
    var usageEverLoaded by rememberSaveable { mutableStateOf(false) }
    val usageRefreshState = usageItems.loadState.refresh
    LaunchedEffect(usageRefreshState) {
        when (usageRefreshState) {
            is LoadState.Loading -> {
                usageLoadStarted = true
                usageEverLoaded = true
            }
            is LoadState.NotLoading -> {
                // 会话结束：有数据，或本轮加载已完成（空结果也放行空态）
                if (usageItems.itemCount > 0 || usageLoadStarted) {
                    usageRefreshSession = false
                    usageLoadStarted = false
                }
            }
            is LoadState.Error -> {
                usageRefreshSession = false
                usageLoadStarted = false
            }
        }
    }

    /** 变更成功后的统一刷新：本页重载 + 转发列表刷新信号（返回列表时生效） */
    fun refreshSelfAndForward() {
        usageRefreshSession = true
        viewModel.refreshAll()
        navController.previousBackStackEntry?.savedStateHandle?.set(SubscriptionRoute.CHANGED_KEY, true)
    }

    // 编辑页返回的变更信号（编辑成功 → 本页刷新 + 转发列表）
    val changedState = navController.currentBackStackEntry?.savedStateHandle
        ?.getStateFlow(SubscriptionRoute.CHANGED_KEY, false)
        ?.collectAsState()
    LaunchedEffect(changedState?.value) {
        if (changedState?.value == true) {
            usageRefreshSession = true
            viewModel.refreshAll()
            navController.currentBackStackEntry?.savedStateHandle?.set(SubscriptionRoute.CHANGED_KEY, false)
            navController.previousBackStackEntry?.savedStateHandle?.set(SubscriptionRoute.CHANGED_KEY, true)
        }
    }

    LaunchedEffect(transitionState) {
        when (val state = transitionState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.subscription_toast_status_updated), Toast_LENGTH).show()
                refreshSelfAndForward()
                viewModel.resetTransitionState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast_LENGTH).show()
                viewModel.resetTransitionState()
            }
            else -> {}
        }
    }

    LaunchedEffect(deleteState) {
        when (val state = deleteState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.subscription_toast_delete_success), Toast_LENGTH).show()
                navController.notifySubscriptionChanged()
                navController.popBackStack()
                viewModel.resetDeleteState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast_LENGTH).show()
                viewModel.resetDeleteState()
            }
            else -> {}
        }
    }

    LaunchedEffect(checkInState) {
        when (val state = checkInState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.subscription_usage_check_in_done), Toast_LENGTH).show()
                refreshSelfAndForward()
                viewModel.resetCheckInState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast_LENGTH).show()
                viewModel.resetCheckInState()
            }
            else -> {}
        }
    }

    LaunchedEffect(undoState) {
        when (val state = undoState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.subscription_toast_undo_success), Toast_LENGTH).show()
                refreshSelfAndForward()
                viewModel.resetUndoState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast_LENGTH).show()
                viewModel.resetUndoState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.subscription_detail_title),
                onBack = { navController.navigateUp() },
                actions = {
                    val vo = detail
                    // 编辑仅非终态可见（终态只读）；删除恒可见（终态数据也可清）
                    if (vo != null && !SubscriptionStatus.fromRawOrDefault(vo.status).isTerminal) {
                        Box(
                            modifier = Modifier
                                .pressable(onClick = { navController.navigateToSubscriptionEdit(vo.subscriptionId) })
                                // 页头文字动作 48×48 热区（DESIGN-SPEC §3）
                                .heightIn(min = 48.dp)
                                .widthIn(min = 48.dp)
                                .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm)
                        ) {
                            Text(
                                text = stringResource(R.string.subscription_action_edit),
                                // 文字按钮 13sp/600 规格（§3.3）经 v0.4 字阶迁移落 labelMedium 12sp（audit-15 #14）
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.colorScheme.primaryDark,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .pressable(onClick = {
                                // 删除请求进行中禁用入口，防重复提交（audit-15 #12）
                                if (deleteState !is RequestState.Loading) showDeleteDialog = true
                            })
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm)
                    ) {
                        Text(
                            text = stringResource(R.string.subscription_action_delete),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.colorScheme.dangerInk,
                        )
                    }
                },
            )
            when {
                loadState is RequestState.Loading -> {
                    SkeletonList(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = AppDimens.spaceLg),
                        rows = 6,
                    )
                }
                loadState is RequestState.Error || detail == null -> {
                    EmptyState(
                        icon = Icons.Rounded.Autorenew,
                        message = (loadState as? RequestState.Error)?.getMessage(context)
                            ?: context.getString(R.string.subscription_error_load_detail),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            AppButton(
                                small = true,
                                text = stringResource(R.string.subscription_detail_retry),
                                onClick = viewModel::load,
                            )
                        },
                    )
                }
                else -> {
                    val vo = detail ?: return@Column
                    DetailContent(
                        vo = vo,
                        context = context,
                        navController = navController,
                        viewModel = viewModel,
                        statsItem = statsItem,
                        statsState = statsState,
                        transitionState = transitionState,
                        checkInState = checkInState,
                        usageItems = usageItems,
                        usageRefreshSession = usageRefreshSession,
                        usageEverLoaded = usageEverLoaded,
                        cancelPathExpanded = cancelPathExpanded,
                        onCancelPathExpandedChange = { cancelPathExpanded = it },
                        onShowCancelDialog = { showCancelDialog = true },
                        onShowFinishDialog = { showFinishDialog = true },
                        // 撤销请求进行中禁用行内入口，防重复提交（audit-15 #12）
                        onShowUndoDialog = { if (undoState !is RequestState.Loading) pendingUndoId = it },
                        onOpenUrl = { com.smartwash.feature.subscription.ui.edit.openInBrowser(context, it) },
                        onCopyUrl = {
                            copyToClipboard(context, it)
                            Toast.makeText(context, context.getString(R.string.subscription_copy_success), Toast_LENGTH).show()
                        },
                    )
                }
            }
        }
    }

    if (showCancelDialog && detail != null) {
        AppConfirmDialog(
            title = stringResource(R.string.subscription_confirm_cancel_title),
            message = stringResource(R.string.subscription_confirm_cancel_message, detail?.name.orEmpty()),
            isDanger = true,
            confirmText = stringResource(R.string.subscription_action_cancel_sub),
            onConfirm = {
                showCancelDialog = false
                viewModel.cancel()
            },
            onDismiss = { showCancelDialog = false },
        )
    }
    if (showFinishDialog && detail != null) {
        AppConfirmDialog(
            title = stringResource(R.string.subscription_confirm_finish_title),
            message = stringResource(R.string.subscription_confirm_finish_message, detail?.name.orEmpty()),
            isDanger = true,
            confirmText = stringResource(R.string.subscription_action_finish),
            onConfirm = {
                showFinishDialog = false
                viewModel.finish()
            },
            onDismiss = { showFinishDialog = false },
        )
    }
    if (showDeleteDialog && detail != null) {
        AppConfirmDialog(
            title = stringResource(R.string.subscription_confirm_delete_title),
            message = stringResource(R.string.subscription_confirm_delete_message, detail?.name.orEmpty()),
            isDanger = true,
            confirmText = stringResource(R.string.subscription_action_delete),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
    if (pendingUndoId > 0) {
        val undoDate = usageItems.itemSnapshotList
            .firstOrNull { it?.usageId == pendingUndoId }?.usageDate.orEmpty()
        AppConfirmDialog(
            title = stringResource(R.string.subscription_confirm_delete_usage_title),
            message = stringResource(R.string.subscription_confirm_delete_usage_message, undoDate),
            isDanger = true,
            confirmText = stringResource(R.string.subscription_usage_undo),
            onConfirm = {
                viewModel.undoUsage(pendingUndoId)
                pendingUndoId = -1L
            },
            onDismiss = { pendingUndoId = -1L },
        )
    }
}

/** 反馈时长统一 LENGTH_LONG（DESIGN-SPEC §8 反馈要求 Toast 3–5s；audit-15 #22） */
private const val Toast_LENGTH = android.widget.Toast.LENGTH_LONG

@Composable
private fun DetailContent(
    vo: SubscriptionVo,
    context: android.content.Context,
    navController: NavHostController,
    viewModel: SubscriptionDetailViewModel,
    statsItem: com.smartwash.feature.subscription.network.SubscriptionStatsItemVo?,
    statsState: RequestState,
    transitionState: RequestState,
    checkInState: RequestState,
    usageItems: androidx.paging.compose.LazyPagingItems<UsageLogVo>,
    usageRefreshSession: Boolean,
    usageEverLoaded: Boolean,
    cancelPathExpanded: Boolean,
    onCancelPathExpandedChange: (Boolean) -> Unit,
    onShowCancelDialog: () -> Unit,
    onShowFinishDialog: () -> Unit,
    onShowUndoDialog: (Long) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
) {
    val status = SubscriptionStatus.fromRawOrDefault(vo.status)
    val terminal = status.isTerminal
    val cycleType = com.smartwash.feature.subscription.SubscriptionCycleType.fromRaw(vo.cycleType)

    // 底部避让导航条：contentPadding.bottom 取 max(spaceSm, navigationBars insets)，
    // edge-to-edge 下手势条不再横穿「取消路径」卡（DESIGN-SPEC §4；audit-15 #9）
    val navBarBottomDp = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    // 卡间距由各卡 item 自身 bottom padding 承担（原 verticalArrangement.spacedBy 移除）：
    // 使用记录行铺平为独立 items（虚拟化）后行间距由行内 padding 控制，行间不再被卡间距撑开（audit-15 #11）
    LazyColumn(
        contentPadding = PaddingValues(
            start = AppDimens.pagePadding,
            top = AppDimens.spaceSm,
            end = AppDimens.pagePadding,
            bottom = maxOf(AppDimens.spaceSm, navBarBottomDp),
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ---------- 续费信息卡 ----------
        item(key = "info") {
            GroupCard(modifier = Modifier.padding(bottom = AppDimens.cardSpacing)) {
                // 卡顶泛订阅概念头图（本地自绘；服务端头图字段落地前先展示本地资产）
                Image(
                    painter = painterResource(LocalCoverArt.subscriptionDetailHeader),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBox(
                        icon = Icons.Rounded.Autorenew,
                        size = 44.dp,
                        iconSize = 24.dp,
                        containerColor = AppColors.colorScheme.iconContainerTeal,
                        iconTint = AppColors.colorScheme.iconForegroundTeal,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                    Text(
                        text = vo.name,
                        style = AppTextStyles.CardTitle,
                        // 终态弱化止步 textSecondary（on 白卡 4.9:1）：装饰档 textTertiary 禁作可读正文（DESIGN-SPEC §10）
                        color = if (terminal) AppColors.colorScheme.textSecondary else AppColors.colorScheme.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SubscriptionTagChip(
                        text = statusText(status),
                        // 状态标签与列表页 statusChipContainer/Content 成对令牌同源（audit-15 #1/#2）：
                        // 图标前景令牌不得拼作 12sp 文字色（iconForegroundTeal on iconContainerTeal 仅 3.1:1）
                        containerColor = statusChipContainer(status),
                        contentColor = statusChipContent(status),
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                InfoRow(
                    label = stringResource(R.string.subscription_detail_amount),
                    value = stringResource(R.string.subscription_amount_per_period, SubscriptionRules.formatAmount(vo.amount)),
                    valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    // 金额是本卡核心数据：终态弱化止步 textSecondary（与 renewRelativeColor 终态档一致，audit-15 #4）
                    valueColor = if (terminal) AppColors.colorScheme.textSecondary else AppColors.colorScheme.textGreen,
                )
                InfoRow(
                    label = stringResource(R.string.subscription_detail_cycle),
                    value = cycleDescriptionFor(cycleType, vo.cycleInterval),
                    valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                )
                InfoRow(
                    label = stringResource(R.string.subscription_detail_next_renew),
                    value = renewRelativeText(vo),
                    valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    valueColor = renewRelativeColor(vo),
                )
                InfoRow(
                    label = stringResource(R.string.subscription_detail_trial_end),
                    value = vo.trialEndDate ?: stringResource(R.string.subscription_value_none),
                    valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                )
                InfoRow(
                    label = stringResource(R.string.subscription_detail_auto_renew),
                    value = stringResource(
                        if (vo.autoRenew == SubscriptionConstant.FLAG_ON) R.string.subscription_detail_auto_renew_on
                        else R.string.subscription_detail_auto_renew_off
                    ),
                )
                InfoRow(
                    label = stringResource(R.string.subscription_detail_remind_days),
                    value = if (vo.remindDaysBefore > 0) {
                        stringResource(R.string.subscription_detail_remind_days_format, vo.remindDaysBefore)
                    } else {
                        stringResource(R.string.subscription_detail_remind_days_zero)
                    },
                    valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                )
                if (!vo.note.isNullOrBlank()) {
                    // 备注是自由长文本：InfoRow 固定 40dp 行高会溢出重叠（audit-15 #10），改自由高度多行
                    MultilineInfoRow(
                        label = stringResource(R.string.subscription_detail_note),
                        value = vo.note.orEmpty(),
                    )
                }
                if (terminal) {
                    Text(
                        text = stringResource(R.string.subscription_terminal_readonly),
                        style = MaterialTheme.typography.labelSmall,
                        // 终态只读提示是可读正文：textSecondary 达标（audit-15 #5）
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.padding(top = AppDimens.spaceXs),
                    )
                }
            }
        }

        // ---------- 状态操作（按当前状态显隐；终态只读） ----------
        if (!terminal) {
            item(key = "actions") {
                Column(
                    modifier = Modifier.padding(bottom = AppDimens.cardSpacing),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
                        AppButton(
                            text = stringResource(
                                if (status == SubscriptionStatus.ACTIVE) R.string.subscription_action_pause
                                else R.string.subscription_action_resume
                            ),
                            onClick = {
                                if (status == SubscriptionStatus.ACTIVE) viewModel.pause() else viewModel.resume()
                            },
                            loading = transitionState is RequestState.Loading,
                            variant = AppButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = stringResource(R.string.subscription_action_finish),
                            onClick = onShowFinishDialog,
                            variant = AppButtonVariant.SECONDARY,
                            enabled = transitionState !is RequestState.Loading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AppButton(
                        text = stringResource(R.string.subscription_action_cancel_sub),
                        onClick = onShowCancelDialog,
                        variant = AppButtonVariant.SECONDARY,
                        // 危险语义换色不变形（规范 §2.1 例外条款：同形态换语义色）
                        contentColor = AppColors.colorScheme.dangerInk,
                        enabled = transitionState !is RequestState.Loading,
                    )
                }
            }
        }

        // ---------- 近 90 天使用统计 ----------
        item(key = "stats") {
            GroupCard(modifier = Modifier.padding(bottom = AppDimens.cardSpacing)) {
                Text(
                    text = stringResource(R.string.subscription_stats_title),
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.colorScheme.textPrimary,
                    modifier = Modifier.padding(bottom = AppDimens.spaceXs),
                )
                when {
                    statsState is RequestState.Loading -> {
                        SkeletonList(rows = 2)
                    }
                    statsState is RequestState.Error -> {
                        Text(
                            text = statsState.getMessage(context),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textSecondary,
                        )
                        // 48×48 最小热区（DESIGN-SPEC §3），重试文字居中
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable(onClick = viewModel::loadStats)
                                .heightIn(min = 48.dp)
                                .padding(vertical = AppDimens.spaceXs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.subscription_action_retry),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.colorScheme.textGreen,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    else -> {
                        // 后端统计仅含进行中订阅（stats.items 过滤 ACTIVE）：非 ACTIVE 时兜底 0 值会与
                        // 同页打卡列表矛盾（评审 P1）。端侧改为口径说明占位，不改后端契约。
                        if (status != SubscriptionStatus.ACTIVE) {
                            Text(
                                text = stringResource(R.string.subscription_stats_inactive_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textSecondary,
                                modifier = Modifier.padding(vertical = AppDimens.spaceSm),
                            )
                        } else {
                            InfoRow(
                                label = stringResource(R.string.subscription_stats_usage_format, statsItem?.usageCount90d ?: 0),
                                value = stringResource(R.string.subscription_stats_monthly_format, SubscriptionRules.formatAmount(statsItem?.monthlyNormalized ?: 0.0)),
                                valueStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                valueColor = AppColors.colorScheme.textGreen,
                            )
                            val perUse = statsItem?.perUseCost
                            Text(
                                text = if (perUse != null) {
                                    stringResource(R.string.subscription_stats_per_use_format, SubscriptionRules.formatAmount(perUse))
                                } else {
                                    stringResource(R.string.subscription_stats_per_use_none)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                // 占位语义空值提示统一收敛 textSecondary（audit-15 #15，与 #7/#8 同口径）
                                color = if (perUse != null) AppColors.colorScheme.textPrimary else AppColors.colorScheme.textSecondary,
                                modifier = Modifier.padding(vertical = AppDimens.spaceSm),
                            )
                        }
                    }
                }
            }
        }

        // ---------- 使用打卡（标题 + 打卡按钮 + 非数据态块 + 使用记录行，全部同一卡容器） ----------
        item(key = "usage") {
            val today = remember { LocalDate.now() }
            // 当天已打卡：按钮转禁用态（后端同日唯一，重复点击必吃报错——评审 P2）
            val checkedInToday = usageItems.itemSnapshotList.any { log ->
                log != null && SubscriptionRules.parseDate(log.usageDate) == today
            }
            val usageCount = usageItems.itemCount
            val usageLogs = usageItems.itemSnapshotList.filterNotNull()
            GroupCard(modifier = Modifier.padding(bottom = AppDimens.cardSpacing)) {
                Text(
                    text = stringResource(R.string.subscription_usage_title),
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.colorScheme.textPrimary,
                    modifier = Modifier.padding(bottom = AppDimens.spaceXs),
                )
                if (!terminal) {
                    AppButton(
                        text = stringResource(
                            if (checkedInToday) R.string.subscription_usage_check_in_done
                            else R.string.subscription_usage_check_in
                        ),
                        onClick = viewModel::checkIn,
                        // 刷新会话期间同样禁用，堵住「列表清空→新数据到达」间隙的重复点击窗口
                        enabled = !checkedInToday && !usageRefreshSession,
                        loading = checkInState is RequestState.Loading,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                }
                UsageListStatusBlock(
                    usageItems = usageItems,
                    refreshSession = usageRefreshSession,
                    everLoaded = usageEverLoaded,
                )
                // 使用记录行回归「使用打卡」卡内（评审 P2：原铺平为独立 items 后行脱离卡片容器，
                // 发丝线落在卡外违反 §3.4「分隔线只在同一卡片内部」；记录量级为人工打卡，非长列表）
                if (usageCount > 0 && usageLogs.isNotEmpty()) {
                    Column {
                        usageLogs.forEachIndexed { index, log ->
                            if (index > 0) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = AppColors.colorScheme.hairline,
                                )
                            }
                            UsageRow(
                                log = log,
                                terminal = terminal,
                                onUndo = onShowUndoDialog,
                            )
                        }
                    }
                }
                if (usageItems.loadState.append is LoadState.Loading) {
                    com.smartwash.common.ui.components.SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    )
                }
            }
        }

        // ---------- 取消路径（折叠卡） ----------
        item(key = "cancel_path") {
            CancelPathCard(
                cancelUrl = vo.cancelUrl,
                cancelSteps = vo.cancelSteps,
                expanded = cancelPathExpanded,
                onExpandedChange = onCancelPathExpandedChange,
                onOpenUrl = onOpenUrl,
                onCopyUrl = onCopyUrl,
            )
        }
    }
}

/**
 * 自由高度信息行（备注等长文本变体，audit-15 #10）：InfoRow 固定 40dp 行高会溢出重叠，
 * 此处 label + 多行 value 两列布局，字号/配色与 InfoRow 一致（bodySmall caption + textSecondary 标签）
 */
@Composable
private fun MultilineInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceXxs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary,
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 打卡列表非数据态块：首刷失败重试 / 骨架 / 真实空态；数据态已铺平为 LazyColumn items（见 DetailContent，audit-15 #11） */
@Composable
private fun UsageListStatusBlock(
    usageItems: androidx.paging.compose.LazyPagingItems<UsageLogVo>,
    refreshSession: Boolean,
    everLoaded: Boolean,
) {
    val refreshState = usageItems.loadState.refresh
    when {
        // 首刷失败：错误分支前置，会话门控不得掩盖真实错误（否则刷新失败时先闪一帧骨架）
        usageItems.itemCount == 0 && refreshState is LoadState.Error -> {
            Column {
                Text(
                    text = stringResource(R.string.subscription_usage_error_load),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
                // 48×48 最小热区（DESIGN-SPEC §3），重试文字居中
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { usageItems.retry() })
                        .heightIn(min = 48.dp)
                        .padding(vertical = AppDimens.spaceXs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.subscription_action_retry),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.colorScheme.textGreen,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // 首刷 / 刷新会话清空帧 / 从未完成过加载：骨架而非空态
        usageItems.itemCount == 0 &&
            (refreshState is LoadState.Loading || refreshSession || !everLoaded) -> {
            SkeletonList(rows = 2)
        }
        // 走到这里必为 session=false && everLoaded=true（会话门控保证），语义=真实空结果
        usageItems.itemCount == 0 && refreshState is LoadState.NotLoading -> {
            // 空态文案是引导下一步的正文：textSecondary 达标（audit-15 #7）
            Text(
                text = stringResource(R.string.subscription_usage_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.spaceMd),
            )
        }
    }
}

/** 打卡行：使用日期（今天标亮）+ 撤销文字动作（终态只读不展示） */
@Composable
private fun UsageRow(
    log: UsageLogVo,
    terminal: Boolean,
    onUndo: (Long) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val usageDate = SubscriptionRules.parseDate(log.usageDate)
    val isToday = usageDate == today
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = if (isToday) AppColors.colorScheme.primary else AppColors.colorScheme.water)
        Spacer(modifier = Modifier.width(AppDimens.spaceXs))
        Text(
            text = log.usageDate.orEmpty(),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            // 字重统一 SemiBold 防宽度跳变（AppTabBar 既定口径），「今天」标亮交给颜色 + chip（audit-15 #19）
            fontWeight = FontWeight.SemiBold,
            color = if (isToday) AppColors.colorScheme.textGreen else AppColors.colorScheme.textPrimary,
        )
        if (isToday) {
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            SubscriptionTagChip(
                text = stringResource(R.string.subscription_usage_today),
                containerColor = AppColors.colorScheme.iconContainerGreen,
                // 图标前景令牌不得拼作 12sp 文字色（iconForegroundGreen on iconContainerGreen 3.3:1），
                // 小号绿字走 textGreenOnTint（Color.kt R17 裁定，audit-15 #6）
                contentColor = AppColors.colorScheme.textGreenOnTint,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!terminal) {
            // 48×48 最小热区（DESIGN-SPEC §3），文字居中
            Box(
                modifier = Modifier
                    .pressable(onClick = { onUndo(log.usageId) })
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.spaceXxs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.subscription_usage_undo),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.colorScheme.dangerInk,
                )
            }
        }
    }
}

/** 取消路径折叠卡：标题行 + AnimatedVisibility（reduced motion 降级 180ms 纯淡入淡出） */
@Composable
private fun CancelPathCard(
    cancelUrl: String?,
    cancelSteps: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .pressable(onClick = { onExpandedChange(!expanded) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.subscription_cancel_path_title),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.weight(1f),
            )
            // 展开箭头进 32dp IconBox（规范 §3.2；取消路径=待办语义取橙），整行 48dp 热区
            IconBox(
                icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                size = 32.dp,
                iconSize = 20.dp,
                containerColor = AppColors.colorScheme.iconContainerOrange,
                iconTint = AppColors.colorScheme.iconForegroundOrange,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduceMotion) {
                fadeIn(animationSpec = tween(CROSSFADE_ALT_MS))
            } else {
                fadeIn(enterTween(DURATION_MICRO_MS)) + expandVertically(enterTween(DURATION_MICRO_MS))
            },
            exit = if (reduceMotion) {
                fadeOut(animationSpec = tween(CROSSFADE_ALT_MS))
            } else {
                fadeOut(exitTween(COLLAPSE_EXIT_MS)) + shrinkVertically(exitTween(COLLAPSE_EXIT_MS))
            },
        ) {
            Column(modifier = Modifier.padding(top = AppDimens.spaceXs)) {
                if (cancelUrl.isNullOrBlank() && cancelSteps.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.subscription_cancel_path_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        // 空态文案是引导正文：textSecondary 达标（audit-15 #8）
                        color = AppColors.colorScheme.textSecondary,
                    )
                } else {
                    if (!cancelUrl.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.subscription_cancel_path_url),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colorScheme.textSecondary,
                        )
                        Text(
                            text = cancelUrl,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.colorScheme.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = AppDimens.spaceXxs),
                        )
                        Row(modifier = Modifier.padding(top = AppDimens.spaceXs)) {
                            // 48×48 最小热区（DESIGN-SPEC §3），两动作相邻间距保持 ≥8dp，文字居中
                            Box(
                                modifier = Modifier
                                    .pressable(onClick = { onOpenUrl(cancelUrl) })
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 48.dp)
                                    .padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.spaceXxs),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.subscription_action_open_browser),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.colorScheme.textGreen,
                                )
                            }
                            Spacer(modifier = Modifier.width(AppDimens.spaceMd))
                            // 48×48 最小热区（DESIGN-SPEC §3），文字居中
                            Box(
                                modifier = Modifier
                                    .pressable(onClick = { onCopyUrl(cancelUrl) })
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 48.dp)
                                    .padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.spaceXxs),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.subscription_action_copy),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.colorScheme.textGreen,
                                )
                            }
                        }
                    }
                    if (!cancelSteps.isNullOrBlank()) {
                        if (!cancelUrl.isNullOrBlank()) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = AppColors.colorScheme.hairline,
                                modifier = Modifier.padding(vertical = AppDimens.spaceSm),
                            )
                        }
                        Text(
                            text = stringResource(R.string.subscription_cancel_path_steps),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colorScheme.textSecondary,
                        )
                        Text(
                            text = cancelSteps,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textPrimary,
                            modifier = Modifier.padding(top = AppDimens.spaceXxs),
                        )
                    }
                }
            }
        }
    }
}

/** 周期描述（复用 SubscriptionUiCommon 同源实现，详情信息行消费） */
@Composable
private fun cycleDescriptionFor(
    cycleType: com.smartwash.feature.subscription.SubscriptionCycleType,
    interval: Int,
): String = com.smartwash.feature.subscription.ui.cycleDescription(cycleType, interval)
