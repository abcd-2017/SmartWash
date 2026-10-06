package com.smartwash.common.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import com.smartwash.common.ui.theme.GlassTextDisabled
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.R
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppTextStyles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.Primary
import com.smartwash.common.ui.theme.PrimaryDark
import com.smartwash.common.ui.theme.TextSecondary
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.pressScale

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
            style = AppTextStyles.PageHeader,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

// ========== 容器组件 ==========

// 规范 §2.4 卡片阴影的 Compose 等价实现。CSS「0 2px 12px rgba(20,40,30,.05)」
// 是模糊扩散模型；Compose 的 elevation 阴影是海拔投影模型且不可控模糊半径——
// 用「3dp 海拔 + 低透明绿灰投影色」近似大扩散柔光（ambient/spot 颜色需 API 28+，minSdk 30）。
// 1dp 描边在无阴影衬托时会读成生硬灰圈，故减半为 0.5dp 退居辅助防溢出。
private val CardShadowAmbient = Color(0x0D14281E)  // 5% 绿黑
private val CardShadowSpot = Color(0x1A14281E)     // 10% 绿黑

/**
 * 分组容器 — 规范 §3.1 标准卡片画法（白底 + 0.5dp 描边 + 柔和投影）
 * 用于：设置页分组、订单详情信息组、内容分区
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.radiusLg)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = CardShadowAmbient,
                spotColor = CardShadowSpot,
                clip = false,
            )
            .border(0.5.dp, AppColors.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface, shape),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
            content = content
        )
    }
}

/**
 * 列表行 — 无 Surface，Row + 底部分隔线
 * 用于：服务套餐列表、订单列表、设置项
 */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    headline: @Composable () -> Unit,
    supporting: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.pressable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(AppDimens.spaceMd))
        }
        Column(modifier = Modifier.weight(1f)) {
            headline()
            if (supporting != null) {
                Spacer(modifier = Modifier.height(2.dp))
                supporting()
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(AppDimens.spaceMd))
            trailing()
        }
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = AppColors.colorScheme.divider,
        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
    )
}

// ========== 统一卡片（规范 §3.1 唯一画法） ==========

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.radiusLg)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.pressable(onClick = onClick, scaleFactor = 0.97f)
                else Modifier
            )
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = CardShadowAmbient,
                spotColor = CardShadowSpot,
                clip = false,
            )
            .border(0.5.dp, AppColors.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface, shape),
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
    val interactionSource = remember { MutableInteractionSource() }
    // btn3-p 规格：135° primary→primaryDark 渐变底；禁用/加载态渐变降透明度（disabledContainerColor
    // 为 Transparent，禁用视觉完全由 brush 承担，避免双层叠加变淡）
    val buttonBrush = if (enabled && !loading) {
        Brush.linearGradient(
            colors = listOf(AppColors.colorScheme.primary, AppColors.colorScheme.primaryDark),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                AppColors.colorScheme.primary.copy(alpha = 0.5f),
                AppColors.colorScheme.primaryDark.copy(alpha = 0.5f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = buttonBrush,
                shape = RoundedCornerShape(AppDimens.buttonRadius)
            )
            .height(52.dp)
            .pressScale(interactionSource, 0.97f),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(AppDimens.buttonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
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
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.pressable(onClick = onClick, scaleFactor = 0.97f)
                else Modifier
            )
            .height(56.dp)
            .padding(horizontal = AppDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.smartwash.common.ui.theme.IconBox(icon = icon, size = 36.dp, iconSize = 18.dp)
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
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
    action: @Composable (() -> Unit)? = null,
    useDrumMark: Boolean = false,
) {
    // 空态是"状态到达"的时刻，给一次入场；reduced motion 时位移归零只留淡入（规范 7.8）
    val reduceMotion = LocalReduceMotion.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(animationSpec = motionSpec(defaultSpring())) +
            slideInVertically(
                animationSpec = motionSpec(defaultSpring()),
                initialOffsetY = { if (reduceMotion) 0 else it / 5 }
            ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 图标 — 规范 §3.6：滚筒圆母题（DrumMark）静止态
            if (useDrumMark) {
                DrumMark(
                    size = 64.dp,
                    tint = AppColors.colorScheme.textTertiary
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = AppColors.colorScheme.textTertiary
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.colorScheme.textSecondary
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                action()
            }
        }
    }
}

// ========== 加载状态（规范 §3.6：骨架屏，禁止转圈） ==========

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
) {
    SkeletonList(modifier = modifier, rows = 4)
}

// ========== 骨架屏 ==========

/** 微光扫过一遍的行程（px）——足够覆盖一屏内任意宽度的骨架块 */
private const val SHIMMER_TRAVEL = 1200f

/**
 * 骨架屏微光画笔 — 用 Compose 原生线性渐变实现，不引第三方 shimmer 库。
 * reduced motion 时退化为静态浅灰（规范 7.8：取消无限循环，但保留可读性）。
 */
@Composable
fun rememberSkeletonBrush(): Brush {
    val base = AppColors.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    if (LocalReduceMotion.current) return SolidColor(base)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val sweep by transition.animateFloat(
        initialValue = -SHIMMER_TRAVEL,
        targetValue = SHIMMER_TRAVEL,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(sweep, 0f),
        end = Offset(sweep + SHIMMER_TRAVEL / 2f, 0f)
    )
}

/** 单个骨架块 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppDimens.radiusSm),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(rememberSkeletonBrush())
    )
}

/**
 * 列表骨架 — 列表型页面（订单/券/收支记录）的加载占位，替代居中转圈：
 * 转圈只说明"在忙"，骨架说明"马上出现什么形状"。
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.pagePadding,
                vertical = AppDimens.spaceSm
            ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(AppDimens.radiusMd)
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(14.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp)
                    )
                }
            }
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
            val selected = selectedIndex == index
            // 选中态过渡走全局弹簧（规范 7.1），reduced motion 时降级为 CrossfadeAlt
            val indicatorWidth by animateDpAsState(
                targetValue = if (selected) 20.dp else 0.dp,
                animationSpec = motionSpec(defaultSpring()),
                label = "tabIndicatorWidth"
            )
            val labelColor by animateColorAsState(
                targetValue = if (selected) AppColors.colorScheme.primary
                else AppColors.colorScheme.textSecondary,
                animationSpec = motionSpec(defaultSpring()),
                label = "tabLabelColor"
            )
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
                    // 两态同为 14sp，只差字重 — 避免选中瞬间字号跳变
                    text = title,
                    style = if (selected)
                        MaterialTheme.typography.labelLarge
                    else
                        MaterialTheme.typography.bodyMedium,
                    color = labelColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 指示条常驻、宽度 0→20dp 生长，切换不再硬跳
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(AppColors.colorScheme.primary)
                )
            }
        }
    }
}

// ========== 统一弹窗组件 ==========

/**
 * 确认/取消操作弹窗
 * 用于：取消订单、确认支付、解绑校园卡、退出登录等
 */
/**
 * 弹窗底部分区按钮——左右贴合弹窗边缘，按压反馈为整区透明度变化（iOS 惯例）。
 * 不使用 Material 按钮：其胶囊形 state layer 会浮在弹窗白底上，与贴边的
 * 矩形分区不重叠。
 */
@Composable
private fun DialogAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .pressable(onClick = onClick, alphaFactor = 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

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
                    DialogAction(
                        text = cancelText,
                        color = AppColors.colorScheme.textSecondary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(48.dp)
                            .background(AppColors.colorScheme.divider)
                    )
                    DialogAction(
                        text = confirmText,
                        color = if (isDanger) AppColors.colorScheme.error else AppColors.colorScheme.primary,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
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
                DialogAction(
                    text = buttonText,
                    color = AppColors.colorScheme.primary,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    DialogAction(
                        text = stringResource(R.string.cancel),
                        color = AppColors.colorScheme.textSecondary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(48.dp)
                            .background(AppColors.colorScheme.divider)
                    )
                    DialogAction(
                        text = stringResource(R.string.confirm),
                        color = AppColors.colorScheme.primary,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
