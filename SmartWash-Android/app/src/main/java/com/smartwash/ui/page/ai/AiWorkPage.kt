package com.smartwash.ui.page.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.R
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
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
                    style = AppTextStyles.SectionTitle,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Column(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickTaskCard(
                            icon = Icons.Default.LocalLaundryService,
                            label = stringResource(R.string.ai_task_choose_package),
                            gradient = Brush.linearGradient(
                                listOf(Color(0xFF2D9B6A), Color(0xFF1E8C5C))
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        QuickTaskCard(
                            icon = Icons.Default.Inventory,
                            label = stringResource(R.string.ai_task_check_order),
                            gradient = Brush.linearGradient(
                                listOf(Color(0xFF2D9B6A), Color(0xFF1E8C5C))
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickTaskCard(
                            icon = Icons.Default.LocalOffer,
                            label = stringResource(R.string.ai_task_use_coupon),
                            gradient = Brush.linearGradient(
                                listOf(Color(0xFF2D9B6A), Color(0xFF1E8C5C))
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        QuickTaskCard(
                            icon = Icons.Default.Wallet,
                            label = stringResource(R.string.ai_task_recharge),
                            gradient = Brush.linearGradient(
                                listOf(Color(0xFF2D9B6A), Color(0xFF1E8C5C))
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 最近工作
            item {
                Text(
                    text = stringResource(R.string.ai_recent_work),
                    style = AppTextStyles.SectionTitle,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                GroupCard(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 滚筒圆空态插画
                        Box(
                            modifier = Modifier.size(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                drawCircle(
                                    color = Color(0xFFD6DAD4),
                                    radius = size.minDimension / 2 - 4.dp.toPx(),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4.dp.toPx())
                                )
                                drawCircle(
                                    color = Color(0xFFE3E7E2),
                                    radius = size.minDimension / 4,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                        Text(
                            text = stringResource(R.string.ai_empty_recent),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = AppColors.colorScheme.textTertiary
                            )
                        )
                    }
                }
            }
        }

        // 底部输入框（固定）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 0.5.dp,
                        color = AppColors.colorScheme.outline,
                        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding, vertical = 12.dp),
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
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = 1.dp,
                                color = AppColors.colorScheme.outline,
                                shape = RoundedCornerShape(23.dp)
                            ),
                        shape = RoundedCornerShape(23.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = AppColors.colorScheme.surfaceVariant,
                            unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = { /* TODO: 接入 AI */ },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2D9B6A), Color(0xFF1E8C5C))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = stringResource(R.string.ai_send),
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTaskCard(
    icon: ImageVector,
    label: String,
    gradient: Brush,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(58.dp)
            .pressable(onClick = { /* TODO */ }),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
    }
}
