package com.smartwash.common.ui.components

import com.smartwash.common.ui.components.softCardShadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 规范 §2.4 卡片阴影色：CSS `rgba(20,40,30,0.05)`（5% 不透明度 + 20,40,30 绿调）。
 */
val CardShadowColor = Color(0x0D14281E)

/** 阴影衰减层数：层数越多越柔，10 层在 5% 峰值下无可感知色带 */
private const val SHADOW_STEPS = 10

/**
 * 柔和卡片阴影 — 规范 §2.4 卡片档 `0 2px 12px rgba(20,40,30,.05)` 的 Compose 自绘等价。
 *
 * **为什么不用 elevation**：Compose 的 elevation 阴影是系统固定的硬边几何形。真机像素采样实测
 * `AppElevation.level1 = 1dp` 时：模糊仅延伸 1.5–2dp（CSS 是 12dp，差 6–8 倍）、峰值 Δ28（约 12% 黑）
 * 比 1dp 描边（Δ14）还深、偏移全压在底边 —— 视觉上就是「白卡 + 浅描边 + 外面再套一条更深的细线」，
 * 即一条"深色粗边"而非柔和投影。规范映射表「CSS 12px ↔ elevation 1dp」实测不成立。
 *
 * ⚠️ **必须画在卡片边界之外**：CSS box-shadow 是外投影。本实现把圆角矩形自卡边向外扩 [blur]，
 * 由外向内叠绘 [SHADOW_STEPS] 层（外层最淡、内层最实，alpha 二次衰减），叠加出柔和过渡。
 *
 * **调用方顺序要求**：必须写在 `.clip(shape)` **之前**。`clip` 是 `graphicsLayer(clip = true)`，
 * 只裁卡片自己的图层，画在外层的阴影不受影响；若写在 `clip` 之后会被裁成硬边。
 * 同时它必须写在 `background` 之前 —— 卡面只覆盖卡片内区，外圈阴影因此可见。
 * （反例：画在边界之内会被自己的 background 整块盖住，阴影完全不可见。）
 *
 * @param cornerRadius 卡片圆角（与 Surface 的 shape 保持一致，默认 16dp）
 * @param color 阴影色（默认规范 §2.4 卡片档）
 * @param offsetY 向下偏移（CSS 的 2px）
 * @param blur 模糊半径（CSS 的 12px，即阴影自卡边向外扩散的距离）
 */
fun Modifier.softCardShadow(
    cornerRadius: Dp = 16.dp,
    color: Color = CardShadowColor,
    offsetY: Dp = 2.dp,
    blur: Dp = 12.dp,
): Modifier = drawBehind {
    val r = cornerRadius.toPx()
    val oy = offsetY.toPx()
    val pad = blur.toPx()
    if (pad <= 0f || size.width <= 0f || size.height <= 0f) return@drawBehind

    // 由外向内：t=1 最外（扩散 blur、alpha→0），t=0 贴近卡边（alpha 最大）
    for (i in SHADOW_STEPS downTo 0) {
        val t = i.toFloat() / SHADOW_STEPS
        val expand = pad * t
        val alpha = color.alpha * (1f - t) * (1f - t)
        drawRoundRect(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            topLeft = Offset(-expand, oy * t - expand),
            size = Size(size.width + expand * 2f, size.height + expand * 2f),
            cornerRadius = CornerRadius(r + expand, r + expand),
        )
    }
}
