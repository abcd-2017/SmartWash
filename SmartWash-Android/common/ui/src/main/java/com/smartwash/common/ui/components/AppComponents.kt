package com.smartwash.common.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.smartwash.common.ui.theme.GlassTextDisabled
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick

// ========== 页面头部 ==========

@Composable
fun PageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    // 返回键内置连击防抖：PageHeader 是多数页面唯一返回入口，连点双 pop 会弹空导航栈（黑屏）
    val debouncedBack = rememberDebouncedClick(enabled = onBack != null) { onBack?.invoke() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = AppDimens.pagePadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            // §7 禁 Material 交互组件（IconButton 自带 state layer 涟漪）：改自绘按压热区，
            // pressable 内置 indication = null + scale 0.97 按压反馈
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pressable(onClick = debouncedBack),
                contentAlignment = Alignment.Center,
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

// DESIGN-SPEC v0.4 §2.4 卡片规格的 Compose 等价实现：规范「CSS 阴影 ↔ Compose elevation 映射表」
// 裁定卡片 = `AppElevation.level1`（1dp），与页面侧 `Surface(shadowElevation = level1)` 同源。
// 描边恢复设计稿的 1dp（0.5dp 是上一版「无阴影裸白卡」时期的退让，阴影补齐后不再需要）。

/**
 * 分组容器 — DESIGN-SPEC v0.4 §3.1 唯一卡片画法（纯白 surface 底 + 1dp 描边 + level1 阴影）
 * 用于：设置页分组、订单详情信息组、内容分区
 *
 * 阴影走 §2.4「CSS 阴影 ↔ Compose elevation 映射表」的 `AppElevation.level1`，
 * 与页面侧 `Surface(shadowElevation = level1)` 同一条路径 —— 全应用卡片阴影只有一个来源。
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
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
            // 阴影与 GroupCard / 页面侧卡片同一条路径：§2.4 映射表 → AppElevation.level1
            .shadow(
                elevation = AppElevation.level1,
                shape = shape,
                clip = false,
            )
            .border(1.dp, AppColors.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface, shape),
    ) {
        Column(content = content)
    }
}

// ========== 统一按钮（自绘，规范 §3.3 三态） ==========

/** AppButton 形态 — 主（品牌渐变底）/ 次（浅灰底）/ 文字（无底无框） */
enum class AppButtonVariant { PRIMARY, SECONDARY, TEXT }

/**
 * 按钮 loading 指示器 — 规范 §7.8：reduced motion 时取消旋转，退化为**静态 270° 圆环**
 * （保留"在忙"的可读性，取消前庭不适的无限旋转）。
 * 全应用共用（按钮内嵌、头像上传、准备中等局部忙态），避免各模块自绘 spinner。
 */
@Composable
fun ButtonSpinner(
    size: Dp,
    strokeWidth: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (LocalReduceMotion.current) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    } else {
        CircularProgressIndicator(
            modifier = modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
        )
    }
}

/**
 * 弹窗入场动效 — 规范 §7.8：scale 0.92 → 1 + alpha 0 → 1 的**一次性**入场，
 * 组合时启动；reduced motion 由 [motionSpec] 自动降级为 180ms 纯淡入（缩放归零）。
 *
 * 关闭动画受 `Dialog` 窗口机制限制（窗口由系统直接移除，拿不到退出帧），
 * 本仓库一律不做弹窗退出动画——见各 Dialog 调用处。
 *
 * 公开给壳层更新弹窗与观象台私有弹窗复用：全应用自绘弹窗共用同一条入场动效，
 * 禁止在模块内再复制等价实现。
 */
fun Modifier.dialogEnterMotion(): Modifier = composed {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = motionSpec(defaultSpring()),
        label = "dialogEnter"
    )
    val reduceMotion = LocalReduceMotion.current
    this.graphicsLayer {
        alpha = progress
        val scale = if (reduceMotion) 1f else 0.92f + 0.08f * progress
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 统一按钮 — 自绘实现，不使用 Material Button（其 state layer 颜色与形状不受
 * 令牌控制，规范 §7 禁止）。业务层一律经本组件消费，禁止直接使用 M3 交互组件。
 *
 * 规格（§3.3）：主 = 52dp 高 / 14dp 圆角 / 品牌渐变底 / 白字 15sp·600·ls 1sp；
 * 次 = 同尺寸 surfaceVariant 底 + 主文本色；文字 = 主色 13sp·600 无底无框。
 * 按压反馈 scale 0.97（pressable 内置 indication=null）。
 *
 * [contentColor] 仅用于**同形态换语义色**的少数场景（如「退出登录」次按钮配 dangerInk，
 * §2.1 例外条款）：非 null 时替换文字与 spinner 色，容器形态与尺寸一律不变。
 * 默认 null 保持三态既有配色不变。
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: AppButtonVariant = AppButtonVariant.PRIMARY,
    contentColor: Color? = null,
) {
    // 内置连击防抖：loading 禁用依赖重组存在一帧间隙，防抖补住同帧/极短连点的双提交
    val debouncedOnClick = rememberDebouncedClick { onClick() }
    val active = enabled && !loading
    val shape = RoundedCornerShape(AppDimens.buttonRadius)

    if (variant == AppButtonVariant.TEXT) {
        // 文字色 0ms 硬切 → 弹簧过渡；reduced motion 自动降级 180ms（规范 7.8）
        val textColor by animateColorAsState(
            targetValue = AppColors.colorScheme.primary.copy(alpha = if (active) 1f else 0.5f),
            animationSpec = motionSpec(defaultSpring()),
            label = "appButtonText"
        )
        Box(
            modifier = modifier
                .then(if (active) Modifier.pressable(onClick = debouncedOnClick) else Modifier)
                .heightIn(min = 44.dp)
                // 最小宽度占位：loading 的 14dp spinner 不会把按钮压窄，消除宽度收缩抖动
                .widthIn(min = 44.dp)
                .padding(horizontal = AppDimens.spaceSm),
            contentAlignment = Alignment.Center,
        ) {
            // 文字 ↔ spinner 硬切 → 交叉淡变（规范 7.8 的 CrossfadeAlt 档）
            Crossfade(
                targetState = loading,
                animationSpec = tween(CROSSFADE_ALT_MS),
                label = "appButtonTextContent"
            ) { isLoading ->
                if (isLoading) {
                    ButtonSpinner(
                        size = 14.dp,
                        strokeWidth = 1.5.dp,
                        color = contentColor ?: AppColors.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor ?: textColor,
                    )
                }
            }
        }
        return
    }

    // 主/次按钮共用容器：高 52dp；主 = 135° primary→primaryDark 渐变，
    // 次 = surfaceVariant（§3.3 的 #F5F6F3 即浅色 surfaceVariant 令牌）
    val isPrimary = variant == AppButtonVariant.PRIMARY

    // 渐变 / surfaceVariant / contentColor 三处颜色 0ms 硬切 → 弹簧过渡。
    // 目标值里保留"禁用降透明度"，动画只负责把跳变抹平，不改变最终视觉。
    val gradientStart by animateColorAsState(
        targetValue = AppColors.colorScheme.primary.copy(alpha = if (active) 1f else 0.5f),
        animationSpec = motionSpec(defaultSpring()),
        label = "appButtonGradientStart"
    )
    val gradientEnd by animateColorAsState(
        targetValue = AppColors.colorScheme.primaryDark.copy(alpha = if (active) 1f else 0.5f),
        animationSpec = motionSpec(defaultSpring()),
        label = "appButtonGradientEnd"
    )
    val secondaryContainer by animateColorAsState(
        targetValue = AppColors.colorScheme.surfaceVariant.copy(alpha = if (active) 1f else 0.5f),
        animationSpec = motionSpec(defaultSpring()),
        label = "appButtonSecondaryContainer"
    )
    // contentColor 为语义色覆盖（如 dangerInk），只做颜色值替换，不参与禁用降透明
    val baseContentColor = contentColor ?: if (isPrimary) {
        // onPrimary 令牌（浅色白 / 深色深绿 #17322B）：深色模式下品牌绿提亮为 #5BC894，
        // 白字对比度不足，必须随主题切换
        if (active) AppColors.colorScheme.onPrimary else GlassTextDisabled
    } else {
        AppColors.colorScheme.textPrimary.copy(alpha = if (active) 1f else 0.5f)
    }
    val animatedContentColor by animateColorAsState(
        targetValue = baseContentColor,
        animationSpec = motionSpec(defaultSpring()),
        label = "appButtonContentColor"
    )

    val containerModifier = if (isPrimary) {
        // 禁用/加载态渐变降透明度（视觉完全由 brush 承担，避免双层叠加变淡）
        val buttonBrush = Brush.linearGradient(
            colors = listOf(gradientStart, gradientEnd),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
        Modifier.background(buttonBrush, shape)
    } else {
        Modifier.background(secondaryContainer, shape)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (active) Modifier.pressable(onClick = debouncedOnClick) else Modifier)
            .height(52.dp)
            .then(containerModifier),
        contentAlignment = Alignment.Center,
    ) {
        // 文字 ↔ spinner 硬切 → 交叉淡变（规范 7.8 的 CrossfadeAlt 档）
        Crossfade(
            targetState = loading,
            animationSpec = tween(CROSSFADE_ALT_MS),
            label = "appButtonContent"
        ) { isLoading ->
            if (isLoading) {
                ButtonSpinner(
                    size = 22.dp,
                    strokeWidth = 2.dp,
                    color = animatedContentColor,
                )
            } else {
                Text(
                    text = text,
                    // v0.4 字阶：按钮文字 = title 档 18sp / SemiBold（与设计稿 CTA 标签一致，
                    // 原 15sp 属 2026-10-06 版的正文档，v0.4 已取消该档）
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = animatedContentColor,
                )
            }
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
        com.smartwash.common.ui.theme.IconBox(icon = icon, size = 36.dp, iconSize = 20.dp)
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

/**
 * 空状态 — 规范 §3.6：滚筒圆母题（[DrumMark]）静止态 + 一句引导文案 + 可选行动按钮。
 *
 * 规范 §3.2 禁止「用一个灰色 Material 图标了事」，故本组件**只渲染滚筒圆母题**，
 * 不再提供回退到裸 Material 图标的分支。[icon] 保留为占位参数：既有 12 处调用点已传
 * Material core 图标（不引 icons-extended），删掉参数会波及 common/ui 之外的 9 个模块文件。
 *
 * @param icon 不渲染，仅为兼容既有调用点的占位参数
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
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
            // 图标 — 规范 §3.6：滚筒圆母题（DrumMark）静止态；48dp + primary（规范 §3.6 母题色）
            DrumMark(
                size = 48.dp,
                tint = AppColors.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            // 引导文案 — 空态主文案档 bodyLarge(16sp)（字阶 §2，非 caption）
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
    shape: Shape = RoundedCornerShape(AppDimens.radiusMd),
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

// ========== 分页尾部静态加载点 ==========

/**
 * 静态三点灰 — reduced motion 降级占位（§8：取消无限旋转，但保留"还在加载"的可读性）。
 * 非动效组件：本身不含任何动画，仅在 [LocalReduceMotion] 为真时替代 CircularProgressIndicator。
 * 分页 footer 共用（订单/收支记录等），禁止页面内再复制等价实现。
 */
@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(AppColors.colorScheme.outline)
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
                    // Tab 快速连切是合法操作，关闭防抖；pressable 内置 indication=null（禁 ripple）
                    .pressable(
                        onClick = {
                            view.performHaptic(HapticEffect.SELECTION)
                            onTabSelected(index)
                        },
                        debounce = false,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    // 两态同为 14sp，只差字重 — 避免选中瞬间字号跳变。
                    //
                    // 字重硬切的修法取舍（计划二选一，此处选「统一字重」）：
                    // 用 animateValueAsState + FontWeight TwoWayConverter 平滑字重，会让
                    // 文字宽度在整段动画里持续变化，在可横滚 Row 里表现为兄弟节点连续位移，
                    // 观感比一次跳变更差（本仓库无法做真机观感回归，取不会变差的方案）。
                    // 选中态由已动画的颜色 + 指示条承担，字重统一为 Medium。
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
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
 *
 * 公开给壳层更新弹窗复用（下载完成/失败弹窗同款分区按钮），禁止再内联复制。
 */
@Composable
fun DialogAction(
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
            // 统一弹窗入场：scale 0.92→1 + alpha 0→1 一次性；reduced motion 降级 180ms 纯淡入。
            // 退出动画受 Dialog 窗口机制限制（窗口由系统直接移除），本仓库不做。
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer
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
                        // 危险确认 — 规范 §2.1：危险语义一律 dangerInk（浅 #A8435A / 暗 #E795A5），
                        // 不复用 error 角色色（error 留给表单校验红）
                        text = confirmText,
                        color = if (isDanger) AppColors.colorScheme.dangerInk else AppColors.colorScheme.primary,
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
            // 统一弹窗入场：scale 0.92→1 + alpha 0→1 一次性；reduced motion 降级 180ms 纯淡入。
            // 退出动画受 Dialog 窗口机制限制（窗口由系统直接移除），本仓库不做。
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer
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
            // 统一弹窗入场：scale 0.92→1 + alpha 0→1 一次性；reduced motion 降级 180ms 纯淡入。
            // 退出动画受 Dialog 窗口机制限制（窗口由系统直接移除），本仓库不做。
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer
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
                            // 错误行 0ms 硬切 → 展开/收起 + 淡入淡出（规范 7.8）
                            AnimatedVisibility(
                                visible = isError && errorMessage != null,
                                enter = fadeIn(enterTween(DURATION_MICRO_MS)) +
                                    expandVertically(enterTween(DURATION_MICRO_MS)),
                                exit = fadeOut(exitTween(140)) +
                                    shrinkVertically(exitTween(140)),
                            ) {
                                Text(text = errorMessage.orEmpty())
                            }
                        },
                        // §3.7：输入正文禁用 14sp 及以下，覆盖 M3 默认
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
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
