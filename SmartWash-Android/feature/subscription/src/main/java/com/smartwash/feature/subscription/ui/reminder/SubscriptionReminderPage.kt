package com.smartwash.feature.subscription.ui.reminder

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.StatusDot
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.SubscriptionReminderType
import com.smartwash.feature.subscription.network.ReminderVo
import com.smartwash.feature.subscription.ui.SubscriptionSelectableChip
import com.smartwash.feature.subscription.ui.SubscriptionTagChip
import com.smartwash.feature.subscription.ui.notifySubscriptionChanged

/**
 * 续费提醒列表页：未读/全部筛选（切换重拉），条目（订阅名 + 类型 renew/trial_end +
 * 应提醒日），点击标记已读（幂等；未读筛选下条目本地移除）。
 * 标记成功后置变更信号，订阅列表页红点随返回刷新。
 */
@Composable
fun SubscriptionReminderPage(
    navController: NavHostController,
    viewModel: SubscriptionReminderViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val unreadOnly by viewModel.unreadOnly.collectAsState()
    val items by viewModel.items.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val markReadState by viewModel.markReadState.collectAsState()

    // 标记已读结果：失败提示（成功不 toast，本地收敛即反馈）；成功 → 通知列表页红点刷新
    LaunchedEffect(markReadState) {
        when (val state = markReadState) {
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.resetMarkReadState()
            }
            is RequestState.Success -> {
                navController.notifySubscriptionChanged()
                viewModel.resetMarkReadState()
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
                title = stringResource(R.string.subscription_reminder_title),
                onBack = { navController.navigateUp() },
            )

            // 未读/全部筛选（切换即重拉）
            Row(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
            ) {
                SubscriptionSelectableChip(
                    text = stringResource(R.string.subscription_reminder_filter_unread),
                    selected = unreadOnly,
                    onClick = { viewModel.setFilter(true) },
                )
                SubscriptionSelectableChip(
                    text = stringResource(R.string.subscription_reminder_filter_all),
                    selected = !unreadOnly,
                    onClick = { viewModel.setFilter(false) },
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))

            when {
                listState is RequestState.Loading && items.isEmpty() -> {
                    SkeletonList(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = AppDimens.spaceSm),
                        rows = 5,
                    )
                }
                listState is RequestState.Error && items.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Rounded.Notifications,
                        message = (listState as RequestState.Error).getMessage(context),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            AppButton(
                                small = true,
                                text = stringResource(R.string.subscription_action_retry),
                                onClick = viewModel::load,
                            )
                        },
                    )
                }
                items.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Rounded.Notifications,
                        message = stringResource(
                            if (unreadOnly) R.string.subscription_reminder_empty_unread
                            else R.string.subscription_reminder_empty_all
                        ),
                        modifier = Modifier.fillMaxSize(),
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
                        items(items, key = { it.reminderId }) { reminder ->
                            ReminderRow(
                                reminder = reminder,
                                onClick = {
                                    if (reminder.isRead == 0) viewModel.markRead(reminder.reminderId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 提醒条目卡：类型图标盒（renew 橙·通知 / trial_end 粉·截止）+ 订阅名 +
 * 应提醒日；行尾类型 chip + 未读红点/已读标注，已读整行弱化。
 */
@Composable
private fun ReminderRow(
    reminder: ReminderVo,
    onClick: () -> Unit,
) {
    val type = SubscriptionReminderType.fromRawOrDefault(reminder.remindType)
    val unread = reminder.isRead == 0
    // 统一走 GroupCard（规范 §3.1 唯一卡片画法：softCardShadow + 1dp 描边 + 20dp 卡内边距）
    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            // 已读条目不可点（无动作可执行），不挂点击
            .then(if (unread) Modifier.pressable(onClick = onClick, scaleFactor = 0.97f) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBox(
                icon = when (type) {
                    SubscriptionReminderType.RENEW -> Icons.Rounded.Notifications
                    SubscriptionReminderType.TRIAL_END -> Icons.Rounded.HourglassTop
                },
                size = 36.dp,
                iconSize = 20.dp,
                containerColor = when (type) {
                    SubscriptionReminderType.RENEW -> AppColors.colorScheme.iconContainerOrange
                    SubscriptionReminderType.TRIAL_END -> AppColors.colorScheme.iconContainerPink
                },
                iconTint = when (type) {
                    SubscriptionReminderType.RENEW -> AppColors.colorScheme.iconForegroundOrange
                    SubscriptionReminderType.TRIAL_END -> AppColors.colorScheme.iconForegroundPink
                },
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.subscriptionName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (unread) AppColors.colorScheme.textPrimary else AppColors.colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                Text(
                    text = stringResource(R.string.subscription_reminder_date_format, reminder.remindDate.orEmpty()),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            Column(horizontalAlignment = Alignment.End) {
                SubscriptionTagChip(
                    text = stringResource(
                        if (type == SubscriptionReminderType.RENEW) R.string.subscription_reminder_type_renew
                        else R.string.subscription_reminder_type_trial_end
                    ),
                    containerColor = when (type) {
                        SubscriptionReminderType.RENEW -> AppColors.colorScheme.warningContainer
                        SubscriptionReminderType.TRIAL_END -> AppColors.colorScheme.iconContainerPink
                    },
                    contentColor = when (type) {
                        SubscriptionReminderType.RENEW -> AppColors.colorScheme.ongoingInk
                        SubscriptionReminderType.TRIAL_END -> AppColors.colorScheme.dangerInk
                    },
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                if (unread) {
                    StatusDot(color = AppColors.colorScheme.danger)
                } else {
                    Text(
                        text = stringResource(R.string.subscription_reminder_read_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textTertiary,
                    )
                }
            }
        }
    }
}
