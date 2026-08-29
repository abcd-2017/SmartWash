package com.smartwash.divination.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import com.smartwash.ui.theme.DivColors
import com.smartwash.divination.ui.divReduceMotion

/**
 * 观象台罗盘：十二支环缓转（140s/圈）+ 四正金点 + 中宫「卜」。
 * reduced-motion 时停转（静态盘仍完整可读）。
 */
@Composable
fun CompassDial(modifier: Modifier = Modifier, dialText: String = "卜") {
    val c = DivColors.current
    val reduceMotion = divReduceMotion()
    val transition = rememberInfiniteTransition(label = "compass")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 140_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "compassRotation",
    )
    val textMeasurer = rememberTextMeasurer()
    val zhiLabels = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val spin = if (reduceMotion) 0f else rotation

        rotate(degrees = spin, pivot = center) {
            // 外环：鎏金渐变
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(c.gold.copy(alpha = 0.8f), c.gold.copy(alpha = 0.25f)),
                ),
                radius = radius * 0.93f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            // 内虚线环
            drawCircle(
                color = c.goldHair,
                radius = radius * 0.78f,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f)),
                ),
            )
            // 十二支环文
            zhiLabels.forEachIndexed { index, label ->
                val angle = Math.toRadians((index * 30.0) - 90.0)
                val pos = center + Offset(
                    (radius * 0.855f * kotlin.math.cos(angle)).toFloat(),
                    (radius * 0.855f * kotlin.math.sin(angle)).toFloat(),
                )
                val measured = textMeasurer.measure(
                    label,
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 8.5.sp,
                        color = c.gold.copy(alpha = 0.75f),
                    ),
                )
                drawText(measured, topLeft = pos - measured.size.center.toOffset())
            }
            // 四正刻线
            listOf(0f, 90f, 180f, 270f).forEach { deg ->
                rotate(degrees = deg, pivot = center) {
                    drawLine(
                        brush = Brush.verticalGradient(listOf(Color.Transparent, c.goldLine, Color.Transparent)),
                        start = Offset(center.x, center.y - radius * 0.78f),
                        end = Offset(center.x, center.y - radius * 0.70f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        // 中环（不随罗盘旋转）
        drawCircle(color = c.goldLine, radius = radius * 0.5f, center = center, style = Stroke(1.dp.toPx()))
        // 中宫「卜」
        val dial = textMeasurer.measure(
            dialText,
            TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, color = c.textPrimary.copy(alpha = 0.92f)),
        )
        drawText(dial, topLeft = center - dial.size.center.toOffset())
        // 天心金点
        drawCircle(color = c.gold, radius = 3.dp.toPx(), center = center)
    }
}

/** DrawScope 辅助：由中心与角度取环上坐标 */
private fun ringOffset(center: Offset, radius: Float, angleDeg: Double): Offset =
    center + Offset(
        (radius * kotlin.math.cos(Math.toRadians(angleDeg - 90.0))).toFloat(),
        (radius * kotlin.math.sin(Math.toRadians(angleDeg - 90.0))).toFloat(),
    )
