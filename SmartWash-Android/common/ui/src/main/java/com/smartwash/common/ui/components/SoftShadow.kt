package com.smartwash.common.ui.components

import com.smartwash.common.ui.components.softCardShadow

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 规范 §2.4 卡片阴影色：CSS `rgba(20,40,30,0.05)`（5% 不透明度 + 20,40,30 绿调）。
 */
val CardShadowColor = Color(0x0D14281E)

/**
 * 柔和卡片阴影 — 规范 §2.4 卡片档 `0 2px 12px rgba(20,40,30,.05)` 的 Compose 自绘等价。
 *
 * **为什么不用 elevation**：Compose 的 elevation 阴影是系统固定的硬边几何形。真机像素采样实测
 * `AppElevation.level1 = 1dp` 时：模糊仅延伸 1.5–2dp（CSS 是 12dp，差 6–8 倍）、峰值 Δ28（约 12% 黑）
 * 比 1dp 描边（Δ14）还深、偏移全压在底边 —— 视觉上就是「白卡 + 浅描边 + 外面再套一条更深的细线」，
 * 即一条"深色粗边"而非柔和投影。规范映射表「CSS 12px ↔ elevation 1dp」实测不成立。
 *
 * 实现：垂直渐变（顶部透明 → 底部实色，承担 offset-y 与向下模糊）叠加水平渐变（左右边缘衰减），
 * 圆角随卡片。峰值 alpha ≈ 5%，边缘自然淡出。
 *
 * ⚠️ **阴影绘制在卡片边界之外**：父容器与滚动容器不得裁剪，否则阴影被裁成硬边。
 * 容器级 horizontal padding 必须改为 `contentPadding`（见 2026-11-01 阴影裁剪修复）。
 *
 * @param cornerRadius 卡片圆角（与 Surface 的 shape 保持一致，默认 16dp）
 * @param color 阴影色（默认规范 §2.4 卡片档）
 * @param offsetY 向下偏移（CSS 的 2px）
 * @param blur 模糊半径（CSS 的 12px，用于水平衰减范围）
 */
fun Modifier.softCardShadow(
    cornerRadius: Dp = 16.dp,
    color: Color = CardShadowColor,
    offsetY: Dp = 2.dp,
    blur: Dp = 12.dp,
): Modifier = drawBehind {
    val r = cornerRadius.toPx()
    val oy = offsetY.toPx()
    val bodyHeight = (size.height - oy).coerceAtLeast(0f)
    val bodyTop = Offset(0f, oy)

    // 1) 垂直渐变：顶部透明 → 底部实色（offset-y 的向下偏移 + 底部最实）
    drawRoundRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = 0f),
                0.35f to color.copy(alpha = color.alpha * 0.55f),
                1f to color,
            )
        ),
        topLeft = bodyTop,
        size = Size(size.width, bodyHeight),
        cornerRadius = CornerRadius(r, r),
    )
    // 2) 水平渐变：左右边缘衰减（CSS 模糊在水平方向同样淡出）
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = 0f),
                0.18f to color.copy(alpha = color.alpha * 0.6f),
                0.82f to color.copy(alpha = color.alpha * 0.6f),
                1f to color.copy(alpha = 0f),
            )
        ),
        topLeft = bodyTop,
        size = Size(size.width, bodyHeight),
        cornerRadius = CornerRadius(r, r),
    )
}
