package com.smartwash.ui.page.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val focusRequester = remember { FocusRequester() }

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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QuickTaskCard(
                            icon = Icons.Default.LocalLaundryService,
                            label = stringResource(R.string.ai_task_choose_package),
                            containerColor = AppColors.colorScheme.iconContainerGreen,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundGreen,
                            modifier = Modifier.weight(1f)
                        )
                        QuickTaskCard(
                            icon = Icons.Default.Inventory,
                            label = stringResource(R.string.ai_task_check_order),
                            containerColor = AppColors.colorScheme.iconContainerBlue,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QuickTaskCard(
                            icon = Icons.Default.LocalOffer,
                            label = stringResource(R.string.ai_task_use_coupon),
                            containerColor = AppColors.colorScheme.iconContainerOrange,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundOrange,
                            modifier = Modifier.weight(1f)
                        )
                        QuickTaskCard(
                            icon = Icons.Default.Wallet,
                            label = stringResource(R.string.ai_task_recharge),
                            containerColor = AppColors.colorScheme.iconContainerPurple,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundPurple,
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
                        // 滚筒圆空态插画（灰线色：浅色取自设计稿，深色 remap：#D6DAD4→#3A403B、#E3E7E2→#2C312D）
                        val isDarkTheme = isSystemInDarkTheme()
                        val drumOuterColor = if (isDarkTheme) Color(0xFF3A403B) else Color(0xFFD6DAD4)
                        val drumInnerColor = if (isDarkTheme) Color(0xFF2C312D) else Color(0xFFE3E7E2)
                        Box(
                            modifier = Modifier.size(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                drawCircle(
                                    color = drumOuterColor,
                                    radius = size.minDimension / 2 - 4.dp.toPx(),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4.dp.toPx())
                                )
                                drawCircle(
                                    color = drumInnerColor,
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
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.pressable(onClick = { focusRequester.requestFocus() }, debounce = false)
                        ) {
                            Text(
                                text = stringResource(R.string.ai_start_asking),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.colorScheme.primaryDark
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
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
                            .focusRequester(focusRequester)
                            .border(
                                width = 1.dp,
                                color = AppColors.colorScheme.outline,
                                shape = RoundedCornerShape(AppDimens.radiusFull)
                            ),
                        shape = RoundedCornerShape(AppDimens.radiusFull),
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
                                    listOf(
                                        AppColors.colorScheme.primary,
                                        AppColors.colorScheme.primaryDark
                                    )
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
    containerColor: Color,
    iconForegroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.pressable(onClick = { /* TODO */ }),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusMd))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconForegroundColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.colorScheme.textPrimary
                )
            )
        }
    }
}
