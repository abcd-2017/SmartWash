package com.smartwash.feature.mood.ui.trend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.mood.MoodMonth
import com.smartwash.feature.mood.R
import com.smartwash.feature.mood.ui.MoodDistributionBar
import com.smartwash.feature.mood.ui.MoodErrorState
import com.smartwash.feature.mood.ui.MoodMonthSwitcher
import com.smartwash.feature.mood.ui.MoodSmallAction
import com.smartwash.feature.mood.ui.moodLevelColor
import com.smartwash.feature.mood.ui.moodLevelLabel
import com.smartwash.feature.mood.ui.navigateToMoodToday
import java.util.Locale

/**
 * 月度趋势页：等级分布条形（stats.distribution，缺的等级补 0）+ 记录天数 + 月均值。
 * 数字槽位开 tnum；空月（recordedDays=0）走空态 + 「去记录」行动按钮（规范 §3.6）。
 */
@Composable
fun MoodTrendPage(
    navController: NavHostController,
    viewModel: MoodTrendViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.mood_trend_title),
            onBack = { navController.navigateUp() },
        )
        when (val state = uiState.loadState) {
            RequestState.Loading -> SkeletonList(rows = 4)
            is RequestState.Error -> MoodErrorState(
                message = state.getMessage(context),
                onRetry = { viewModel.retry() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.pagePadding),
            )
            else -> MoodTrendContent(
                uiState = uiState,
                onPrev = { viewModel.shiftMonth(-1) },
                onNext = { viewModel.shiftMonth(1) },
                onGoRecord = { navController.navigateToMoodToday() },
            )
        }
    }
}

/** 加载成功的内容体：月切换条 + 数据卡 + 分布条形卡（空月走 EmptyState） */
@Composable
private fun MoodTrendContent(
    uiState: MoodTrendViewModel.UiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onGoRecord: () -> Unit,
) {
    val stats = uiState.stats
    val isEmpty = stats == null || stats.recordedDays <= 0L
    val parsedYm = MoodMonth.parse(uiState.ym)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
    ) {
        MoodMonthSwitcher(
            monthText = stringResource(
                R.string.mood_month_format,
                parsedYm?.year ?: 0,
                parsedYm?.monthValue ?: 0,
            ),
            onPrev = onPrev,
            onNext = onNext,
        )

        if (isEmpty) {
            // 空月：滚筒圆母题 + 引导 + 行动按钮（规范 §3.6；icon 为兼容占位参数不渲染）
            EmptyState(
                icon = Icons.Rounded.Cloud,
                message = stringResource(R.string.mood_trend_empty_message),
                action = {
                    MoodSmallAction(
                        text = stringResource(R.string.mood_trend_empty_action),
                        onClick = onGoRecord,
                        containerColor = AppColors.colorScheme.primaryLight,
                        // 12sp 文字压在 primaryLight tinted 底 → textGreenOnTint（2026-11-06 评审 R17/R18）
                        contentColor = AppColors.colorScheme.textGreenOnTint,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            requireNotNull(stats)

            // ---------- 记录天数 + 月均值（月均值为数据大字锚点，tnum） ----------
            GroupCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoodStatCell(
                        label = stringResource(R.string.mood_trend_stats_days),
                        value = stats.recordedDays.toString(),
                        suffix = stringResource(R.string.mood_trend_days_unit),
                        modifier = Modifier.weight(1f),
                        // 一屏一锚点：记录天数降为 AmountMedium，把大字锚点留给月均值
                        valueStyle = AppTextStyles.AmountMedium,
                    )
                    MoodStatCell(
                        label = stringResource(R.string.mood_trend_stats_average),
                        value = stats.averageLevel?.let { String.format(Locale.CHINESE, "%.2f", it) }
                            ?: stringResource(R.string.mood_trend_value_none),
                        suffix = null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ---------- 等级分布条形（1..5 全量补 0，缺级显 0） ----------
            GroupCard {
                Text(
                    text = stringResource(R.string.mood_trend_distribution_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textPrimary,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                val maxCount = stats.distribution.maxOfOrNull { it.recordCount } ?: 0L
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
                    stats.distribution.forEach { item ->
                        MoodDistributionBar(
                            label = moodLevelLabel(item.level),
                            color = moodLevelColor(item.level),
                            count = item.recordCount,
                            fraction = if (maxCount > 0L) {
                                item.recordCount.toFloat() / maxCount.toFloat()
                            } else {
                                0f
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 数据格：标签（caption）+ 数字 + 可选单位。
 * valueStyle 默认 AppTextStyles.Headline 28sp（一屏一锚点，仅一个格子用大字）；
 * 次要数字传 AppTextStyles.AmountMedium 18sp，避免同屏双锚点（规范 §2.2 一屏一锚点）。
 */
@Composable
private fun MoodStatCell(
    label: String,
    value: String,
    suffix: String?,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = AppTextStyles.Headline,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = valueStyle.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textPrimary,
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    // 裸值 2/3dp 收敛为 spaceXxs（与数字间距统一）
                    modifier = Modifier.padding(start = AppDimens.spaceXxs, bottom = AppDimens.spaceXxs),
                )
            }
        }
    }
}
