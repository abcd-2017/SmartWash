package com.smartwash.common.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartwash.common.utils.LocalReduceMotion

/**
 * 滚筒圆 — 规范 §5 签名单品。
 *
 * 同心圆环 + 一条穿过它的水波线。默认静止，只在真实状态变化时动。
 *
 * @param size 组件尺寸
 * @param progress 进度 0..1（用于订单进度场景），null 表示静止
 * @param tint 颜色
 * @param animate 是否播放旋转动画（用于加载场景）
 */
@Composable
fun DrumMark(
    size: Dp,
    progress: Float? = null,
    tint: Color,
    animate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(animate, reduceMotion) {
        if (animate && !reduceMotion) {
            rotation.animateTo(
                360f,
                infiniteRepeatable(
                    animation = tween(3200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            rotation.snapTo(0f)
        }
    }

    Canvas(modifier.size(size)) {
        val scaleFactor = this.size.minDimension / 24f
        val strokeWidth = 2f * scaleFactor
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // 外圈（不转）
        drawCircle(
            color = tint,
            radius = 9f * scaleFactor,
            center = center,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 进度环（如果有 progress）
        if (progress != null) {
            val clampedProgress = progress.coerceIn(0f, 1f)
            if (clampedProgress > 0f) {
                val path = Path().apply {
                    val r = 9f * scaleFactor
                    val startAngle = -90f
                    val sweepAngle = 360f * clampedProgress
                    val rect = androidx.compose.ui.geometry.Rect(
                        center.x - r, center.y - r,
                        center.x + r, center.y + r
                    )
                    arcTo(rect, startAngle, sweepAngle, false)
                }
                drawPath(
                    path = path,
                    color = tint,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // 提升筋 ×3（只转这组）
        rotate(rotation.value, pivot = center) {
            repeat(3) { i ->
                rotate(i * 120f, pivot = center) {
                    drawLine(
                        color = tint,
                        start = Offset(center.x, center.y - 8.8f * scaleFactor),
                        end = Offset(center.x, center.y - 5.8f * scaleFactor),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 水波线（穿过圆环）
        val wavePath = Path().apply {
            val y1 = center.y - 2f * scaleFactor
            val y2 = center.y + 2f * scaleFactor
            moveTo(center.x - 7f * scaleFactor, y1)
            cubicTo(
                center.x - 3.5f * scaleFactor, y1 - 3f * scaleFactor,
                center.x - 1.5f * scaleFactor, y1 - 3f * scaleFactor,
                center.x, y1
            )
            cubicTo(
                center.x + 1.5f * scaleFactor, y1 + 3f * scaleFactor,
                center.x + 3.5f * scaleFactor, y1 + 3f * scaleFactor,
                center.x + 7f * scaleFactor, y1
            )
            moveTo(center.x - 7f * scaleFactor, y2)
            cubicTo(
                center.x - 3.5f * scaleFactor, y2 - 3f * scaleFactor,
                center.x - 1.5f * scaleFactor, y2 - 3f * scaleFactor,
                center.x, y2
            )
            cubicTo(
                center.x + 1.5f * scaleFactor, y2 + 3f * scaleFactor,
                center.x + 3.5f * scaleFactor, y2 + 3f * scaleFactor,
                center.x + 7f * scaleFactor, y2
            )
        }
        drawPath(
            path = wavePath,
            color = tint,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
