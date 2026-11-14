package com.smartwash.feature.review.ui.queue

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.review.R
import com.smartwash.feature.review.ReviewRoute
import com.smartwash.feature.review.logic.ReviewFormat
import com.smartwash.feature.review.network.ReviewCardVo
import com.smartwash.feature.review.ui.DueTypeChip
import com.smartwash.feature.review.ui.ReviewTagChip
import com.smartwash.feature.review.ui.navigateToReviewStudy
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 今日队列页：顶部「今日 N 张」计数条（数据大字锚点 + 到期/新卡分解 + 开始复习 CTA）+
 * 队列列表（卡面预览、所属资料、到期类型 Learning 分钟级/Review 天级角标）。
 * 数据变更（资料页建卡/复习评分回传信号）经 savedStateHandle 驱动重拉（CHANGED_KEY）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueuePage(
    navController: NavHostController,
    viewModel: ReviewQueueViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // 资料页建资料/建卡、复习页评分成功后置位的刷新信号（消费后复位并重拉队列）
    val changedState = navController.currentBackStackEntry?.savedStateHandle
        ?.getStateFlow(ReviewRoute.CHANGED_KEY, false)
        ?.collectAsState()
    LaunchedEffect(changedState?.value) {
        if (changedState?.value == true) {
            viewModel.load()
            navController.currentBackStackEntry?.savedStateHandle
                ?.set(ReviewRoute.CHANGED_KEY, false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.review_queue_title),
                onBack = { navController.navigateUp() },
                actions = {
                    // 资料页入口：队列非空时也要能管理资料/建卡（评审 P1：原仅空态可达，
                    // 有到期卡的日子资料管理整体断路）。文字按钮热区 ≥48dp（§3.3）
                    Box(
                        modifier = Modifier
                            .pressable(onClick = { navController.navigate(ReviewRoute.Materials) })
                            .heightIn(min = 48.dp)
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.review_queue_action_materials),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.colorScheme.textGreen,
                        )
                    }
                },
            )

            PullToRefreshBox(
                isRefreshing = state.loadState is RequestState.Loading && state.queue != null,
                onRefresh = { viewModel.load() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val loadState = state.loadState) {
                    // 首刷加载：骨架屏占位（形状对齐 QueueCard，禁止转圈，规范 §3.6）
                    RequestState.Loading, RequestState.Idle -> {
                        QueueSkeleton(modifier = Modifier.fillMaxSize())
                    }
                    // 首刷失败：说明 + 重试按钮
                    is RequestState.Error -> {
                        EmptyState(
                            icon = Icons.Rounded.School,
                            message = loadState.getMessage(context),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AppDimens.pagePadding),
                            action = {
                                // 本屏唯一主行动：48dp 胶囊主按钮（DESIGN-SPEC §3 触控 ≥48dp / §4 单一主行动，矩阵 #9）
                                AppButton(
                                    text = stringResource(R.string.review_action_retry),
                                    onClick = { viewModel.load() },
                                )
                            },
                        )
                    }
                    RequestState.Success -> {
                        val queue = state.queue
                        if (queue == null || queue.cards.isEmpty()) {
                            // 空态：滚筒圆母题 + 引导 + 行动按钮（跳资料页建卡）
                            EmptyState(
                                icon = Icons.Rounded.School,
                                message = stringResource(R.string.review_queue_empty_message),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = AppDimens.pagePadding),
                                action = {
                                    // 本屏唯一主行动：48dp 胶囊主按钮（DESIGN-SPEC §3 触控 ≥48dp / §4 单一主行动，矩阵 #9）
                                    AppButton(
                                        text = stringResource(R.string.review_queue_empty_action),
                                        onClick = { navController.navigate(ReviewRoute.Materials) },
                                    )
                                },
                            )
                        } else {
                            QueueContent(
                                queueCards = queue.cards,
                                dueCount = queue.dueCount,
                                newCount = queue.newCount,
                                materialTitles = state.materialTitles,
                                onStartStudy = { navController.navigateToReviewStudy() },
                                onCardClick = { card -> navController.navigateToReviewStudy(card.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueContent(
    queueCards: List<ReviewCardVo>,
    dueCount: Int,
    newCount: Int,
    materialTitles: Map<Long, String>,
    onStartStudy: () -> Unit,
    onCardClick: (ReviewCardVo) -> Unit,
) {
    // 页面级时钟（分钟粒度，对齐分钟边界滴答）：页面停留时「N 分钟后到期」随时间推进不失真，
    // 全部卡片同一 now 基准（审查矩阵 #8；数据变更仍经下拉刷新/savedStateHandle 重拉）
    val now by produceState(initialValue = LocalDateTime.now(), key1 = Unit) {
        while (isActive) {
            val current = LocalDateTime.now()
            val nextMinute = current.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(Duration.between(current, nextMinute).toMillis() + 1)
            value = LocalDateTime.now()
        }
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
        contentPadding = PaddingValues(
            horizontal = AppDimens.pagePadding,
            vertical = AppDimens.spaceXs,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        // 计数条 —— 每屏唯一锚点：数据大字（36sp display + tnum，数字变化不跳动）
        item(key = "queue_summary") {
            GroupCard {
                Text(
                    text = stringResource(R.string.review_queue_count_format, queueCards.size),
                    style = AppTextStyles.DataLarge,
                    color = AppColors.colorScheme.textPrimary,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = stringResource(R.string.review_queue_breakdown_format, dueCount, newCount),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textSecondary,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                AppButton(
                    text = stringResource(R.string.review_queue_action_start),
                    onClick = onStartStudy,
                )
            }
        }
        items(queueCards, key = { it.id }) { card ->
            QueueCard(
                card = card,
                materialTitle = materialTitles[card.materialId],
                now = now,
                onClick = { onCardClick(card) },
            )
        }
    }
}

/**
 * 队列条目卡（规范 §3.1 唯一卡片画法由 AppCard 承担）：
 * 问题预览（body 14/500 两行）+ 所属资料 chip + 到期类型角标 + 右侧到期时间。
 */
@Composable
private fun QueueCard(
    card: ReviewCardVo,
    materialTitle: String?,
    now: LocalDateTime,
    onClick: () -> Unit,
) {
    // 新卡不显示到期：语义由「新卡」角标承担（原实现未短路，出现「新卡 + 7 小时后到期」冗余）
    val dueText = if (card.isNewCard) ReviewFormat.DueText.Unknown else ReviewFormat.dueTextOf(card.dueAt, now)
    AppCard(modifier = Modifier.pressable(onClick = onClick)) {
        // 卡内容包卡内边距 20dp：AppCard 无默认内边距，遗漏会让文本贴 16dp 圆角切字
        // （规范 §3.1 卡内边距 / 审查矩阵 #1，与 ReviewStudyPage 卡面画法一致）
        Row(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.question,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.colorScheme.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs)) {
                    ReviewTagChip(
                        text = materialTitle
                            ?: stringResource(R.string.review_material_chip_none),
                        containerColor = AppColors.colorScheme.surfaceVariant,
                        contentColor = AppColors.colorScheme.textSecondary,
                    )
                    DueTypeChip(card = card)
                }
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Text(
                text = dueDisplayText(dueText),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                // 到期时间是队列核心决策信息（非占位/装饰文本）：textTertiary 浅 2.25 / 深 4.25:1
                // 双模式均破 4.5:1 红线，改 textSecondary（审查矩阵 #2）
                color = AppColors.colorScheme.textSecondary,
            )
        }
    }
}

/** 到期量级 → 文案（新卡不显示到期，语义由「新卡」角标承担） */
@Composable
private fun dueDisplayText(dueText: ReviewFormat.DueText): String = when (dueText) {
    is ReviewFormat.DueText.DueNow -> stringResource(R.string.review_due_now)
    is ReviewFormat.DueText.InMinutes ->
        stringResource(R.string.review_due_in_minutes, dueText.minutes)
    is ReviewFormat.DueText.InHours ->
        stringResource(R.string.review_due_in_hours, dueText.hours)
    is ReviewFormat.DueText.InDays ->
        stringResource(R.string.review_due_in_days, dueText.days)
    ReviewFormat.DueText.Unknown -> ""
}

/**
 * 队列页首刷骨架 — 形状对齐真实 QueueCard：两行问题文本块 + chips 胶囊行 + 右侧到期时间小块
 * （SkeletonList 的「40dp 左圆块 + 两行文本」是带图标容器的行结构，QueueCard 为无图标的全宽卡，
 * 骨架形状须与真实内容一致 — 规范 §3.6 / 审查矩阵 #7）。
 * 微光扫过与 reduced motion 静态灰降级由 SkeletonBlock（rememberSkeletonBrush）承担。
 */
@Composable
private fun QueueSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(
            horizontal = AppDimens.pagePadding,
            vertical = AppDimens.spaceXs,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
    ) {
        repeat(5) { QueueCardSkeletonRow() }
    }
}

/**
 * 单条队列卡骨架行 — 结构复刻 QueueCard（卡内边距 20dp + 左侧文本列 + 右侧到期小块）。
 * 块高复用 AppDimens 既有档位对齐真实文本度量：titleSmall 行高 20sp → spaceLg(20dp)、
 * labelSmall chip 高 16sp 行高 + 8dp 纵向内边距 → spaceXl(24dp)、到期小字行高 16sp → spaceMd(16dp)，
 * 不新增任何硬编码尺寸。
 */
@Composable
private fun QueueCardSkeletonRow() {
    AppCard {
        Row(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(AppDimens.spaceLg),
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(AppDimens.spaceLg),
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.26f)
                            .height(AppDimens.spaceXl),
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.2f)
                            .height(AppDimens.spaceXl),
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                    )
                }
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.14f)
                    .height(AppDimens.spaceMd),
            )
        }
    }
}
