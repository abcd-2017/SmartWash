package com.smartwash.feature.divination.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.spinInfinite

/** 罗盘缓转周期 — 140s/圈（原型口径：极缓，仅作背景呼吸感） */
private const val COMPASS_PERIOD_MS = 140_000

/**
 * 观象台罗盘：十二支环缓转（140s/圈）+ 中宫「卜」（画板口径：外环 50% 金实线 + 12% 虚环 + 12sp 支环文）。
 * reduced-motion 时停转（静态盘仍完整可读），且不创建无限动画——
 * 否则 `rememberInfiniteTransition` 会每帧推进重组，降级形同虚设。
 */
@Composable
fun CompassDial(modifier: Modifier = Modifier, dialText: String = "卜") {
    val c = DivColors.current
    val rotation = if (LocalReduceMotion.current) {
        remember { Animatable(0f) }.value
    } else {
        val transition = rememberInfiniteTransition(label = "compass")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = spinInfinite(durationMs = COMPASS_PERIOD_MS),
            label = "compassRotation",
        ).value
    }
    val textMeasurer = rememberTextMeasurer()
    val zhiLabels = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        rotate(degrees = rotation, pivot = center) {
            // 外环：50% 鎏金实线
            drawCircle(
                color = c.gold.copy(alpha = 0.5f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            // 内虚线环：12% 金发丝
            drawCircle(
                color = c.hair,
                radius = radius * 0.85f,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f)),
                ),
            )
            // 十二支环文（画板：r=0.92、12sp、金/墨 70%）
            zhiLabels.forEachIndexed { index, label ->
                val angle = Math.toRadians((index * 30.0) - 90.0)
                val pos = center + Offset(
                    (radius * 0.92f * kotlin.math.cos(angle)).toFloat(),
                    (radius * 0.92f * kotlin.math.sin(angle)).toFloat(),
                )
                val measured = textMeasurer.measure(
                    label,
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 12.sp,
                        color = c.goldText.copy(alpha = 0.7f),
                    ),
                )
                drawText(measured, topLeft = pos - measured.size.center.toOffset())
            }
        }
        // 中宫「卜」（不随罗盘旋转）
        val dial = textMeasurer.measure(
            dialText,
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = c.goldText,
            ),
        )
        drawText(dial, topLeft = center - dial.size.center.toOffset())
    }
}

/** DrawScope 辅助：由中心与角度取环上坐标 */
private fun ringOffset(center: Offset, radius: Float, angleDeg: Double): Offset =
    center + Offset(
        (radius * kotlin.math.cos(Math.toRadians(angleDeg - 90.0))).toFloat(),
        (radius * kotlin.math.sin(Math.toRadians(angleDeg - 90.0))).toFloat(),
    )
