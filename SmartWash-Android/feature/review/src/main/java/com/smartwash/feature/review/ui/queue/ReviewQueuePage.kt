package com.smartwash.feature.review.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
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
import java.time.LocalDateTime

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
            )

            PullToRefreshBox(
                isRefreshing = state.loadState is RequestState.Loading && state.queue != null,
                onRefresh = { viewModel.load() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val loadState = state.loadState) {
                    // 首刷加载：骨架屏占位（禁止转圈，规范 §3.6）
                    RequestState.Loading, RequestState.Idle -> {
                        SkeletonList(modifier = Modifier.fillMaxSize(), rows = 5)
                    }
                    // 首刷失败：说明 + 重试按钮
                    is RequestState.Error -> {
                        EmptyState(
                            icon = Icons.Rounded.School,
                            message = loadState.getMessage(context),
                            modifier = Modifier.fillMaxSize(),
                            action = {
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
                                modifier = Modifier.fillMaxSize(),
                                action = {
                                    AppButton(
                                        text = stringResource(R.string.review_queue_empty_action),
                                        onClick = { navController.navigate(ReviewRoute.Materials.text) },
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
                    style = MaterialTheme.typography.bodySmall,
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
    onClick: () -> Unit,
) {
    val now = remember { LocalDateTime.now() }
    val dueText = ReviewFormat.dueTextOf(card.dueAt, now)
    AppCard(modifier = Modifier.pressable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                color = AppColors.colorScheme.textTertiary,
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
