package com.smartwash.common.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 规范 §2.4 卡片阴影色：CSS `rgba(20,40,30,0.05)`（5% 不透明度 + 20,40,30 绿调）。
 */
val CardShadowColor = Color(0x0D14281E)

/**
 * 柔和卡片阴影 — 规范 §2.4 卡片档 `0 2px 12px rgba(20,40,30,.05)` 的 Compose 等价实现。
 *
 * **实现方式**：对卡面同形圆角矩形（向下平移 [offsetY]）挂 [BlurMaskFilter] 做一次真高斯模糊，
 * 经 `nativeCanvas` 绘制（Compose 的 DrawScope 未暴露 Paint 版 drawRoundRect，框架 Canvas 稳定可用）。
 * CSS box-shadow 的可见边缘峰值是 50%（模糊后的阶跃边缘），即本实现卡边外峰值 ≈ 2.5%，
 * 与鸿蒙端 ArkUI `.shadow(radius=12, color=rgba(20,40,30,0.05), offsetY=2)` 渲染一致。
 * Paint/BlurMaskFilter 在 [drawWithCache] 中按尺寸缓存，每帧只做一次模糊绘制。
 *
 * **为什么不用 elevation**：Compose 的 elevation 阴影是系统固定的硬边几何形。真机像素采样实测
 * `AppElevation.level1 = 1dp` 时：模糊仅延伸 1.5–2dp（CSS 是 12dp，差 6–8 倍）、峰值 Δ28（约 12% 黑）
 * 比 1dp 描边（Δ14）还深、偏移全压在底边 —— 视觉上就是「白卡 + 浅描边 + 外面再套一条更深的细线」。
 * 规范映射表「CSS 12px ↔ elevation 1dp」实测不成立。
 *
 * **为什么不用多层矩形叠绘**：旧实现按 `alpha = 5%·(1-t)²` 由外向内叠 10 层圆角矩形，但各层
 * 合成是 `1-∏(1-α)` 的叠加关系——卡边外合成峰值实测 17.8%（设计 2.5% 的 7 倍），且层间台阶
 * 达 4.3%/1.2dp，视觉上就是一圈深色晕边 + 可见色带，即「阴影与边框过渡生硬」的来源。
 *
 * ⚠️ **必须画在卡片边界之外可见的位置**：本修饰符画在内容层之下、越出自身边界。调用顺序要求：
 * 写在 `.clip(shape)` **之前**（`clip` 只裁自己图层内的内容，先画的阴影不受影响），
 * 写在 `.background(...)` 之前（卡面只覆盖卡内区，外圈阴影因此可见）。M3 `Surface(shape=...)`
 * 的内部裁剪在调用方修饰符之后，天然满足该顺序。
 *
 * **父容器空间要求**：阴影越出卡片 12dp，父容器需留出容身空间——列表用
 * `LazyColumn(contentPadding = ...)` 而非容器级 padding，`verticalScroll` 在
 * `padding(horizontal)` 之前（scroll 会裁到视口边界）。相邻卡片间距应 ≥ 12dp，否则阴影互相叠画。
 *
 * **背景要求**：卡面必须不透明（surface / 渐变实色），半透明卡面会透出阴影内半圈。
 * hero、深色卡按规范 §2.4 豁免阴影，不应调用本修饰符。
 *
 * @param cornerRadius 卡片圆角（与卡面 shape 保持一致，默认 16dp = radiusLg）
 * @param color 阴影色（默认规范 §2.4 卡片档，自带 5% alpha；浮层档可传 10% 等价色）
 * @param offsetY 向下偏移（CSS 的 2px）
 * @param blur 模糊半径（CSS blur 口径，框架内部 σ≈blur/2）
 */
fun Modifier.softCardShadow(
    cornerRadius: Dp = 16.dp,
    color: Color = CardShadowColor,
    offsetY: Dp = 2.dp,
    blur: Dp = 12.dp,
): Modifier = drawWithCache {
    val blurPx = blur.toPx()
    val oy = offsetY.toPx()
    if (blurPx > 0f && size.width > 0f && size.height > 0f) {
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        val radius = cornerRadius.toPx()
        onDrawBehind {
            // 阴影形 = 卡面矩形整体下移 offsetY（CSS 0 2px 口径），内半圈被不透明卡面盖住
            drawContext.canvas.nativeCanvas.drawRoundRect(
                0f, oy, size.width, oy + size.height, radius, radius, shadowPaint,
            )
        }
    } else {
        onDrawBehind { }
    }
}
