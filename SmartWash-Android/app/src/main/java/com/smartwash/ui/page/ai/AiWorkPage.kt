package com.smartwash.ui.page.ai

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartwash.R
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.pressable

/**
 * AI 工具工作台 — 状态工作流 + 来源引用 + 人工确认
 * 当前为 UI 占位版本，实际 AI 接入后续迭代
 */
@Composable
fun AiWorkPage(
    onBack: () -> Unit = {},
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.ai_workbench_title),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
        ) {
            // 快捷任务
            item {
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Text(
                    text = stringResource(R.string.ai_quick_tasks),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)
                ) {
                    QuickTaskChip(
                        icon = Icons.Default.LocalLaundryService,
                        label = stringResource(R.string.ai_task_choose_package),
                        modifier = Modifier.weight(1f)
                    )
                    QuickTaskChip(
                        icon = Icons.Default.Inventory,
                        label = stringResource(R.string.ai_task_check_order),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)
                ) {
                    QuickTaskChip(
                        icon = Icons.Default.LocalOffer,
                        label = stringResource(R.string.ai_task_use_coupon),
                        modifier = Modifier.weight(1f)
                    )
                    QuickTaskChip(
                        icon = Icons.Default.Wallet,
                        label = stringResource(R.string.ai_task_recharge),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 最近工作
            item {
                GroupCard(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                ) {
                    Text(
                        text = stringResource(R.string.ai_recent_work),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = AppDimens.spaceSm)
                    )
                    Text(
                        text = stringResource(R.string.ai_empty_recent),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textTertiary
                    )
                }
            }
        }

        // 底部输入框（固定）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.ai_input_placeholder),
                            color = AppColors.colorScheme.textTertiary
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AppDimens.radiusFull),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppColors.colorScheme.surfaceVariant,
                        unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                IconButton(
                    onClick = { /* TODO: 接入 AI */ },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AppColors.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.ai_send),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTaskChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .pressable(onClick = { /* TODO */ }),
        shape = RoundedCornerShape(AppDimens.radiusMd),
        color = AppColors.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppColors.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
