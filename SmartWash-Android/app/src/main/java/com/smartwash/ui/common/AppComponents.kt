package com.smartwash.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.smartwash.ui.theme.GlassTextDisabled
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartwash.R
import com.smartwash.ui.theme.AppColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.AppElevation
import com.smartwash.ui.theme.Primary
import com.smartwash.ui.theme.PrimaryDark
import com.smartwash.ui.theme.TextSecondary
import com.smartwash.utils.HapticEffect
import com.smartwash.utils.currentView
import com.smartwash.utils.performHaptic
import com.smartwash.utils.pressAlpha
import com.smartwash.utils.pressScale

// ========== 页面头部 ==========

@Composable
fun PageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = AppDimens.pagePadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

// ========== 统一卡片 ==========

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.cardRadius)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                    .pressAlpha(interactionSource, 0.92f)
                else Modifier
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}

// ========== 统一主按钮 ==========

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    // 按压缩放反馈：把同一个 InteractionSource 接入 Button 与 pressScale
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interactionSource, 0.98f),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(AppDimens.buttonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = AppColors.colorScheme.primary.copy(alpha = 0.5f),
            disabledContentColor = GlassTextDisabled
        )
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

// ========== 设置/账户行 ==========

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                    .pressScale(interactionSource, 0.98f)
                else Modifier
            )
            .height(56.dp)
            .padding(horizontal = AppDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.smartwash.ui.theme.IconBox(icon = icon, size = 36.dp, iconSize = 18.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
        }
        trailing()
    }
}

// ========== 空状态 ==========

@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300))
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 图标 — 浅色圆形背景
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(AppColors.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = AppColors.colorScheme.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.colorScheme.textSecondary
            )
        }
    }
}

// ========== 加载状态 ==========

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300))
        ) {
            CircularProgressIndicator(
                color = AppColors.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// ========== 标签栏 ==========

@Composable
fun AppTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = currentView()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding),
    ) {
        tabs.forEachIndexed { index, title ->
            Column(
                modifier = Modifier
                    .clickable {
                        view.performHaptic(HapticEffect.SELECTION)
                        onTabSelected(index)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = if (selectedIndex == index)
                        MaterialTheme.typography.titleMedium
                    else
                        MaterialTheme.typography.bodyMedium,
                    color = if (selectedIndex == index)
                        MaterialTheme.colorScheme.primary
                    else
                        AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (selectedIndex == index) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(AppColors.colorScheme.primary)
                    )
                } else {
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

// ========== 统一弹窗组件 ==========

/**
 * 确认/取消操作弹窗
 * 用于：取消订单、确认支付、解绑校园卡、退出登录等
 */
@Composable
fun AppConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    confirmText: String = stringResource(R.string.confirm),
    cancelText: String = stringResource(R.string.cancel),
    isDanger: Boolean = false,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AppDimens.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.level4,
            tonalElevation = 6.dp
        ) {
            Column {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            text = cancelText,
                            color = AppColors.colorScheme.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(48.dp)
                            .background(AppColors.colorScheme.divider)
                    )
                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            text = confirmText,
                            color = if (isDanger) AppColors.colorScheme.error else AppColors.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单按钮提示弹窗
 * 用于：套餐未选择提示、更换校区提示等
 */
@Composable
fun AppInfoDialog(
    message: String,
    onDismiss: () -> Unit,
    title: String? = null,
    buttonText: String = stringResource(R.string.confirm),
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AppDimens.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.level4,
            tonalElevation = 6.dp
        ) {
            Column {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = buttonText,
                        color = AppColors.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 带输入框的弹窗
 * 用于：绑定校园卡
 */
@Composable
fun AppInputDialog(
    title: String,
    inputLabel: String,
    inputValue: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AppDimens.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.level4,
            tonalElevation = 6.dp
        ) {
            Column {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = onValueChange,
                        label = { Text(inputLabel) },
                        supportingText = {
                            if (isError && errorMessage != null) {
                                Text(errorMessage)
                            }
                        },
                        singleLine = true,
                        isError = isError,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDimens.inputRadius),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.colorScheme.primary,
                            unfocusedBorderColor = AppColors.colorScheme.outline,
                            errorBorderColor = AppColors.colorScheme.error
                        )
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = AppColors.colorScheme.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(48.dp)
                            .background(AppColors.colorScheme.divider)
                    )
                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.confirm),
                            color = AppColors.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
