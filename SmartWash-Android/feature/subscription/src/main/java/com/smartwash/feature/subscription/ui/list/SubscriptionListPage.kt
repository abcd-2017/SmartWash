package com.smartwash.feature.subscription.ui.list

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.StatusDot
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.SubscriptionCycleType
import com.smartwash.feature.subscription.SubscriptionRoute
import com.smartwash.feature.subscription.SubscriptionStatus
import com.smartwash.feature.subscription.logic.SubscriptionRules
import com.smartwash.feature.subscription.network.SubscriptionVo
import com.smartwash.feature.subscription.ui.SubscriptionSelectableChip
import com.smartwash.feature.subscription.ui.SubscriptionTagChip
import com.smartwash.feature.subscription.ui.cycleDescription
import com.smartwash.feature.subscription.ui.navigateToSubscriptionReminders
import com.smartwash.feature.subscription.ui.renewRelativeColor
import com.smartwash.feature.subscription.ui.renewRelativeText
import com.smartwash.feature.subscription.ui.notifySubscriptionChanged
import com.smartwash.feature.subscription.ui.navigateToSubscriptionEdit
import com.smartwash.feature.subscription.ui.navigateToSubscriptionDetail
import com.smartwash.feature.subscription.ui.statusText

/**
 * 订阅列表页：Paging 3 分页（pagingFlow/PageData 模式）+ 状态筛选 chips +
 * PullToRefreshBox 下拉刷新 + 骨架屏/空态/错误提示（规范 §3.6）。
 * 条目卡：名称/每期金额/周期描述 + 续费周期进度环 + 试用倒计时角标 + 未读提醒红点；
 * canceled/finished 灰态（去色 + 状态 chip）。新建/提醒入口在页头（宿主入口后续一行接入）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionListPage(
    navController: NavHostController,
    viewModel: SubscriptionListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()
    val selectedFilter by viewModel.statusFilter.collectAsState()

    // 刷新会话门控（2026-11-06 闪空态回归）：下拉刷新 / 变更重建 / 切换筛选都会先把列表清空为 0 项，
    // 在「清空后、新加载尚未开始」的帧里 refresh=NotLoading && itemCount=0 会命中空态分支闪一帧。
    // 会话期间一律走骨架；只有会话结束（有数据，或本轮加载已完成）才允许真实空态。
    // 用 rememberSaveable：旋转/进程重建后 everLoaded 不丢，避免已加载完的 0 项页卡在骨架。
    var refreshSession by rememberSaveable { mutableStateOf(false) }
    var loadStarted by rememberSaveable { mutableStateOf(false) }
    var everLoaded by rememberSaveable { mutableStateOf(false) }

    // 新建/编辑/删除/状态流转/标记已读成功回列表的刷新信号（发起页经 savedStateHandle 置位）
    val changedState = navController.currentBackStackEntry?.savedStateHandle
        ?.getStateFlow(SubscriptionRoute.CHANGED_KEY, false)
        ?.collectAsState()
    LaunchedEffect(changedState?.value) {
        if (changedState?.value == true) {
            refreshSession = true
            viewModel.refresh()
            navController.currentBackStackEntry?.savedStateHandle?.set(SubscriptionRoute.CHANGED_KEY, false)
        }
    }

    // 首刷/下拉刷新失败提示（append 失败由 footer 重试入口承载）
    val refreshState = pagingItems.loadState.refresh
    LaunchedEffect(refreshState) {
        when (refreshState) {
            is LoadState.Loading -> {
                loadStarted = true
                everLoaded = true
            }
            is LoadState.NotLoading -> {
                // 会话结束：有数据，或本轮加载已完成（空结果也放行空态）
                if (pagingItems.itemCount > 0 || loadStarted) {
                    refreshSession = false
                    loadStarted = false
                }
            }
            is LoadState.Error -> {
                refreshSession = false
                loadStarted = false
            }
        }
        if (refreshState is LoadState.Error) {
            Toast.makeText(context, context.getString(R.string.subscription_error_load), Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.subscription_title),
                onBack = { navController.navigateUp() },
                actions = {
                    // 页头文字动作 — font.title 18/24/600（≥18sp 绿字豁免保留 primaryDark），热区 48 高（同 ToolboxListPage）
                    Box(
                        modifier = Modifier
                            .pressable(onClick = { navController.navigateToSubscriptionReminders() })
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm)
                    ) {
                        Text(
                            text = stringResource(R.string.subscription_action_reminders),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.colorScheme.primaryDark,
                        )
                    }
                    // 相邻触控目标间距 ≥8dp（DESIGN-SPEC §3）：两个 pressable 热区边界原先彼此贴合
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    Box(
                        modifier = Modifier
                            .pressable(onClick = { navController.navigateToSubscriptionEdit(-1L) })
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm)
                    ) {
                        Text(
                            text = stringResource(R.string.subscription_action_new),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.colorScheme.primaryDark,
                        )
                    }
                },
            )

            // 状态筛选 chips（全部/进行中/已暂停/已取消/已结束）
            StatusFilterRow(
                selected = selectedFilter,
                // 切换筛选会重建 Pager（同属刷新清空窗口），同样进入会话门控
                onSelect = {
                    if (selectedFilter != it) refreshSession = true
                    viewModel.setStatus(it)
                },
                modifier = Modifier.padding(bottom = AppDimens.spaceXs),
            )

            PullToRefreshBox(
                // 会话期间保持指示器；错误/数据到达即停（见会话门控注释）
                isRefreshing = refreshSession || (refreshState is LoadState.Loading && pagingItems.itemCount > 0),
                onRefresh = {
                    refreshSession = true
                    pagingItems.refresh()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    // 首刷失败：说明 + 重试按钮（规范 §3.6 错误态）。
                    // 错误分支前置：会话门控不得掩盖真实错误（否则刷新失败时先闪一帧骨架）
                    pagingItems.itemCount == 0 && refreshState is LoadState.Error -> {
                        EmptyState(
                            icon = Icons.Rounded.Autorenew,
                            message = stringResource(R.string.subscription_error_load),
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                AppButton(
                                    small = true,
                                    text = stringResource(R.string.subscription_action_retry),
                                    onClick = { pagingItems.retry() },
                                )
                            },
                        )
                    }
                    // 首刷加载 / 刷新会话清空帧 / 从未完成过加载：骨架屏占位（禁止转圈）
                    pagingItems.itemCount == 0 &&
                        (refreshState is LoadState.Loading || refreshSession || !everLoaded) -> {
                        SkeletonList(modifier = Modifier.fillMaxSize(), rows = 5)
                    }
                    // 空态：滚筒圆母题 + 引导 + 行动按钮。
                    // 走到这里必为 session=false && everLoaded=true（会话门控保证），语义=真实空结果
                    pagingItems.itemCount == 0 && refreshState is LoadState.NotLoading -> {
                        EmptyState(
                            icon = Icons.Rounded.Autorenew,
                            // 筛选态空结果用独立文案，避免「还没有订阅」误导用户以为数据被清空（评审 P1）
                            message = stringResource(
                                if (selectedFilter == null) R.string.subscription_empty_message
                                else R.string.subscription_empty_filter_message
                            ),
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                AppButton(
                                    small = true,
                                    text = stringResource(R.string.subscription_empty_action),
                                    onClick = { navController.navigateToSubscriptionEdit(-1L) },
                                )
                            },
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
                            contentPadding = PaddingValues(
                                horizontal = AppDimens.pagePadding,
                                vertical = AppDimens.spaceXs,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = pagingItems.itemCount,
                                key = pagingItems.itemKey { it.subscriptionId },
                            ) { index ->
                                pagingItems[index]?.let { vo ->
                                    SubscriptionCard(
                                        vo = vo,
                                        onClick = { navController.navigateToSubscriptionDetail(vo.subscriptionId) },
                                    )
                                }
                            }
                            // 追加页脚：加载中骨架 / 失败重试（文字按钮）
                            when (pagingItems.loadState.append) {
                                is LoadState.Loading -> {
                                    item(key = "append_loading") { AppendLoadingRow() }
                                }
                                is LoadState.Error -> {
                                    item(key = "append_error") {
                                        // 48dp 最小热区（DESIGN-SPEC §3 触控目标 ≥48×48dp，错误态唯一出路），重试文字居中
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pressable(onClick = { pagingItems.retry() })
                                                .heightIn(min = 48.dp)
                                                .padding(vertical = AppDimens.spaceSm),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.subscription_error_load),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = AppColors.colorScheme.dangerInk,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 状态筛选行（水平滚动，防窄屏溢出） */
@Composable
private fun StatusFilterRow(
    selected: SubscriptionStatus?,
    onSelect: (SubscriptionStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        null to stringResource(R.string.subscription_filter_all),
        SubscriptionStatus.ACTIVE to stringResource(R.string.subscription_filter_active),
        SubscriptionStatus.PAUSED to stringResource(R.string.subscription_filter_paused),
        SubscriptionStatus.CANCELED to stringResource(R.string.subscription_filter_canceled),
        SubscriptionStatus.FINISHED to stringResource(R.string.subscription_filter_finished),
    )
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = AppDimens.pagePadding),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
    ) {
        items(options, key = { it.second }) { (status, label) ->
            SubscriptionSelectableChip(
                text = label,
                selected = selected == status,
                onClick = { onSelect(status) },
            )
        }
    }
}

@Composable
private fun AppendLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceSm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 骨架块而非转圈（规范 §3.6）
        com.smartwash.common.ui.components.SkeletonBlock(
            modifier = Modifier
                .width(120.dp)
                .height(14.dp)
        )
    }
}

/**
 * 订阅条目卡（规范 §3.1 唯一卡片画法：GroupCard r16 · surface 底 + 1dp 描边 + softCardShadow）：
 * 图标盒 36（状态语义六色浅底）+ 名称（含未读红点）+ 周期描述 + 续费进度环（44dp）+
 * 金额（tnum 金额中字）+ 试用倒计时角标 + 续费日相对描述 + 状态 chip；终态整卡去灰。
 */
@Composable
private fun SubscriptionCard(
    vo: SubscriptionVo,
    onClick: () -> Unit,
) {
    val status = SubscriptionStatus.fromRawOrDefault(vo.status)
    val cycleType = SubscriptionCycleType.fromRaw(vo.cycleType)
    val terminal = status.isTerminal

    // 统一走 GroupCard（规范 §3.1 唯一卡片画法：softCardShadow + 1dp 描边 + 20dp 卡内边距）
    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, scaleFactor = 0.97f),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(
                    icon = statusIcon(status),
                    size = 36.dp,
                    iconSize = 20.dp,
                    containerColor = statusContainerColor(status),
                    iconTint = statusIconColor(status),
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = vo.name,
                            style = AppTextStyles.CardTitle,
                            // 终态弱化止步 textSecondary（on 白卡 4.9:1）：装饰档 textTertiary 禁作可读正文（DESIGN-SPEC §10）
                            color = if (terminal) AppColors.colorScheme.textSecondary else AppColors.colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // 未读提醒红点（规范 §3.5 状态色点：异常/提醒 = danger）。
                        // 补语义文案（DESIGN-SPEC §10 颜色不是唯一信息载体）：
                        // 读屏朗读「N 条未读提醒」，消费此前零引用的 subscription_unread_reminders_format
                        if (vo.unreadReminderCount > 0) {
                            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                            val unreadText = stringResource(
                                R.string.subscription_unread_reminders_format,
                                vo.unreadReminderCount,
                            )
                            StatusDot(
                                color = AppColors.colorScheme.danger,
                                modifier = Modifier.semantics { contentDescription = unreadText },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    Text(
                        text = cycleDescription(cycleType, vo.cycleInterval),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                RenewProgressRing(vo = vo, status = status)
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.subscription_amount_per_period, SubscriptionRules.formatAmount(vo.amount)),
                    style = AppTextStyles.AmountMedium,
                    color = if (terminal) AppColors.colorScheme.textSecondary else AppColors.colorScheme.primaryDark,
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                TrialBadge(vo = vo)
                Spacer(modifier = Modifier.weight(1f))
                if (status != SubscriptionStatus.ACTIVE) {
                    SubscriptionTagChip(
                        text = statusText(status),
                        containerColor = statusChipContainer(status),
                        contentColor = statusChipContent(status),
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = renewRelativeText(vo),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = renewRelativeColor(vo),
                )
            }
        }
    }
}

/** 试用倒计时角标：≤3 天转粉（临近截止），其余橙（待办语义）；无试用不渲染 */
@Composable
private fun TrialBadge(vo: SubscriptionVo) {
    val daysLeft = vo.trialDaysLeft ?: return
    if (daysLeft < 0) return
    val urgent = daysLeft <= 3
    val text = if (daysLeft == 0) {
        stringResource(R.string.subscription_trial_last_day)
    } else {
        stringResource(R.string.subscription_trial_left_format, daysLeft)
    }
    SubscriptionTagChip(
        text = text,
        containerColor = if (urgent) AppColors.colorScheme.iconContainerPink else AppColors.colorScheme.warningContainer,
        // 非紧急分支换 onWarningContainer（容器/内容成对令牌，on warningContainer 6.47:1）：
        // ongoingInk v0.4 与 ongoing 同值 #D98A26，压橙浅底仅 2.52:1，不达正文红线（§10）
        contentColor = if (urgent) AppColors.colorScheme.dangerInk else AppColors.colorScheme.onWarningContainer,
        // 天数为动态数字，开 tnum 防跳动（规范 §2.2）
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
    )
}

/** 续费相对描述与颜色改由 SubscriptionUiCommon 共享（列表卡/详情信息行同源） */

/**
 * 续费周期进度环（44dp · 4dp 描边）：进度 = 已消耗天数 / 当期总天数（真实派生数据驱动，
 * 弹簧过渡 + reduced motion 自动降级）。逾期转 danger，临近（≤3 天）转 ongoing，
 * 暂停弱化为 textTertiary，终态与不可解析数据不渲染。环心为距续费天数（tnum）。
 * 环线与环心文字拆色：环线走图形口径，环心 12sp 数字走文字级达标令牌（§10 ≥4.5:1）。
 */
@Composable
private fun RenewProgressRing(vo: SubscriptionVo, status: SubscriptionStatus) {
    if (status.isTerminal) return
    val cycleType = SubscriptionCycleType.fromRaw(vo.cycleType)
    // remember key 覆盖 renewProgressFraction 全部四个数据入参（+id 隔离条目）：
    // 改计费周期而续费日恰好不变时进度也要重算
    val fraction = remember(
        vo.subscriptionId,
        vo.cycleType,
        vo.cycleInterval,
        vo.nextRenewDate,
        vo.daysUntilRenew,
    ) {
        SubscriptionRules.renewProgressFraction(cycleType, vo.cycleInterval, vo.nextRenewDate, vo.daysUntilRenew)
    } ?: return
    val daysLeft = vo.daysUntilRenew ?: return

    val ringColor = when {
        status == SubscriptionStatus.PAUSED -> AppColors.colorScheme.textTertiary
        daysLeft < 0 -> AppColors.colorScheme.danger
        daysLeft <= 3 -> AppColors.colorScheme.ongoing
        else -> AppColors.colorScheme.primary
    }
    // 环心是 12sp 可读数字（§10 正文 ≥4.5:1），与环线（图形 3:1 口径）拆档：
    // 环线保持 ringColor 不变，文字用文字级达标令牌（按状态同色相映射）
    val centerTextColor = when {
        status == SubscriptionStatus.PAUSED -> AppColors.colorScheme.textSecondary
        daysLeft < 0 -> AppColors.colorScheme.dangerInk
        daysLeft <= 3 -> AppColors.colorScheme.onWarningContainer
        else -> AppColors.colorScheme.textGreen
    }
    // 进度由真实数据驱动的一次性过渡（规范 §7 位移动画一律弹簧；reduced motion 降级 180ms）
    val progress by animateFloatAsState(
        targetValue = fraction,
        animationSpec = motionSpec(defaultSpring()),
        label = "renewProgress",
    )
    val trackColor = AppColors.colorScheme.outline
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            // 轨迹圆与弧线同心：半径按描边内缩，避免外半描边被画布裁切
            drawCircle(
                color = trackColor,
                radius = (this.size.minDimension - stroke) / 2f,
                center = this.center,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = stringResource(R.string.subscription_renew_days_compact, kotlin.math.abs(daysLeft)),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = centerTextColor,
            maxLines = 1,
        )
    }
}

// ---------- 状态语义映射（图标盒六色浅底 + chip 配色，规范 §2.1/§3.2） ----------

private fun statusIcon(status: SubscriptionStatus) = when (status) {
    SubscriptionStatus.ACTIVE -> Icons.Rounded.Autorenew
    SubscriptionStatus.PAUSED -> Icons.Rounded.Pause
    SubscriptionStatus.CANCELED -> Icons.Rounded.LinkOff
    SubscriptionStatus.FINISHED -> Icons.Rounded.CheckCircle
}

@Composable
private fun statusContainerColor(status: SubscriptionStatus): Color = when (status) {
    SubscriptionStatus.ACTIVE -> AppColors.colorScheme.iconContainerTeal
    SubscriptionStatus.PAUSED -> AppColors.colorScheme.iconContainerOrange
    SubscriptionStatus.CANCELED -> AppColors.colorScheme.iconContainerPink
    SubscriptionStatus.FINISHED -> AppColors.colorScheme.iconContainerGreen
}

@Composable
private fun statusIconColor(status: SubscriptionStatus): Color = when (status) {
    SubscriptionStatus.ACTIVE -> AppColors.colorScheme.iconForegroundTeal
    SubscriptionStatus.PAUSED -> AppColors.colorScheme.iconForegroundOrange
    SubscriptionStatus.CANCELED -> AppColors.colorScheme.iconForegroundPink
    SubscriptionStatus.FINISHED -> AppColors.colorScheme.iconForegroundGreen
}

@Composable
private fun statusChipContainer(status: SubscriptionStatus): Color = when (status) {
    SubscriptionStatus.PAUSED -> AppColors.colorScheme.warningContainer
    SubscriptionStatus.CANCELED -> AppColors.colorScheme.iconContainerPink
    else -> AppColors.colorScheme.surfaceVariant
}

@Composable
private fun statusChipContent(status: SubscriptionStatus): Color = when (status) {
    // PAUSED：onWarningContainer 与 warningContainer 成对（6.47:1）；ongoingInk 压橙浅底仅 2.52:1（§10 红线）
    SubscriptionStatus.PAUSED -> AppColors.colorScheme.onWarningContainer
    SubscriptionStatus.CANCELED -> AppColors.colorScheme.dangerInk
    // FINISHED（else）：textSecondary on surfaceVariant 4.51:1；textTertiary 1.98:1 禁作可读标签（DESIGN-SPEC §10）
    else -> AppColors.colorScheme.textSecondary
}
