package com.smartwash.ui.page.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wallet
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartwash.R
import com.smartwash.common.ui.components.DrumMark
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor

/**
 * AI 工具工作台 — 状态工作流 + 来源引用 + 人工确认
 * 当前为 UI 占位版本，实际 AI 接入后续迭代
 */
@Composable
fun AiWorkPage(
    onBack: () -> Unit = {},
) {
    var inputText by remember { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            // 设计规范 §4：键盘弹出时输入框保持可见（imePadding）。此前无 ime 处理，
            // 键盘弹出时系统为露出底部输入框整窗上移（pan）——页头标题滚出视口、
            // LazyColumn 内容可滚入状态栏与时钟重叠；输入条随键盘上移后系统不再 pan，
            // 布局顶部（页头/滚动边界）保持稳定，内容不再顶入状态栏
            .imePadding()
    ) {
        PageHeader(
            title = stringResource(R.string.ai_workbench_title),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            // 区块间距 32（ui-spec §2.3，sectionSpacing 令牌；原 spaceMd 16 偏小）
            verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
        ) {
            // 快捷任务
            item(key = "quick_tasks") {
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Text(
                    text = stringResource(R.string.ai_quick_tasks),
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.colorScheme.textPrimary,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Column(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
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
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
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
            item(key = "recent_work") {
                Text(
                    text = stringResource(R.string.ai_recent_work),
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.colorScheme.textPrimary,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                GroupCard(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                    // 画板实测滚筒顶距卡顶 32——列表卡用水平内边距画法，纵向由内容自控，
                    // 否则 GroupCard 纵向 20 + 此处 32 = 52 与画板不符
                    contentPadding = PaddingValues(horizontal = AppDimens.cardPadding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = AppDimens.spaceMd),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 滚筒圆空态插画 — 公共 DrumMark 母题（弱文本色，深浅主题各自可读）
                        DrumMark(
                            size = 60.dp,
                            tint = AppColors.colorScheme.textTertiary
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                        Text(
                            text = stringResource(R.string.ai_empty_recent),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                        // 设计稿「开始提问」：radiusButton 14（画板实测非胶囊）
                        // [P08] 2026-11-05 画板复裁：按钮 84×52（原高 48 差 4）——显式 52dp 高、内容居中
                        Surface(
                            shape = RoundedCornerShape(AppDimens.buttonRadius),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .height(52.dp)
                                .pressable(onClick = { focusRequester.requestFocus() }, debounce = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.ai_start_asking),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // 底部输入框（固定）——设计稿：白底条高 80（顶线 1px + 上下 16 + 输入框 48）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // 避让系统导航条（MainActivity edge-to-edge，规范 §4 固定 CTA 栏 WindowInsets 透传）；
                // 与外层 imePadding 叠加语义正确：键盘弹出时 ime 高度覆盖导航条，二者取大不双重让位
                .navigationBarsPadding()
                .softCardShadow(),
            color = AppColors.colorScheme.surface,
            // 固定输入条是浮层（§2.4 豁免档），阴影按规范 §2.4 卡片档自绘（softCardShadow）
        ) {
            Column {
                // 顶部 1px 分隔线（画板「顶线」outline）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.colorScheme.outline)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 聚焦反馈：外框颜色 primary ↔ outline 过渡（reduced motion 自动降级 180ms）
                    val inputBorderColor = selectedColor(
                        if (inputFocused) AppColors.colorScheme.primary else AppColors.colorScheme.outline
                    )
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            // 占位符：textSecondary + 与正文同字号 16sp（ui-spec §3.7 表单输入唯一标准，
                            // 工作台搜索框先例；textTertiary 1.9:1 触 DESIGN-SPEC §10 可读文本红线）
                            Text(
                                stringResource(R.string.ai_input_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColors.colorScheme.textSecondary
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { inputFocused = it.isFocused }
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = inputBorderColor,
                                // 输入框圆角 14（ui-spec §2.3 radiusButton 档含输入框，inputRadius 令牌，
                                // 工作台搜索框先例；radiusMd 12 是图标容器/嵌套块档的误用）
                                shape = RoundedCornerShape(AppDimens.inputRadius)
                            ),
                        shape = RoundedCornerShape(AppDimens.inputRadius),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = AppColors.colorScheme.surfaceVariant,
                            unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    // 发送 — 自绘圆钮：Material IconButton 的 state layer 会在渐变圆上浮出
                    // 不贴合的涟漪（规范 §7 禁 ripple），改用 pressable（scale 0.97）
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pressable(onClick = { /* TODO: 接入 AI */ })
                            .clip(CircleShape)
                            // 画板实测发送钮为纯色 primary（非渐变）
                            .background(AppColors.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = stringResource(R.string.ai_send),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
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
        modifier = modifier.pressable(onClick = { /* TODO */ }).softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.cardPadding, vertical = AppDimens.spaceMd),
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
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
