package com.smartwash.common.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartwash.common.utils.LocalReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 水滴动效图标 — 规范 §4.5 附。
 *
 * 整层缩放 1 → 1.07 → 1，2400ms ease-in-out 无限。
 * 缩放圆心定在 (12,14)。仅当 active 时播放；reduced motion 时静止。
 *
 * 触发：充值页/余额不足提示出现时。
 */
@Composable
fun DropIcon(
    active: Boolean,
    tint: Color,
    size: Dp = 24.dp,
) {
    val reduce = LocalReduceMotion.current
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(active, reduce) {
        if (active && !reduce) {
            scaleAnim.animateTo(
                1.07f,
                infiniteRepeatable(tween(2400, easing = EaseInOut))
            )
        } else {
            scaleAnim.snapTo(1f)
        }
    }

    val path = remember {
        Path().apply {
            moveTo(12f, 3f)
            cubicTo(12f, 3f, 5f, 9.4f, 5f, 14f)
            arcTo(
                rect = Rect(5f, 7f, 19f, 21f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            cubicTo(19f, 9.4f, 12f, 3f, 12f, 3f)
            close()
        }
    }

    Canvas(Modifier.size(size)) {
        val scaleFactor = size.toPx() / 24f
        val stroke = 2f * scaleFactor
        val pivot = Offset(12f * scaleFactor, 14f * scaleFactor)

        scale(scaleAnim.value, scaleAnim.value, pivot) {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * 水波动效图标 — 规范 §4.5 附。
 *
 * 整层横向平移 ±2，1600ms ease-in-out 无限。
 * 两条线同相位。仅当 isLoading 时播放；reduced motion 时静止。
 *
 * 触发：RequestState.Loading（调用方传入 state is RequestState.Loading）。
 */
@Composable
fun WaveIcon(
    isLoading: Boolean,
    tint: Color,
    size: Dp = 24.dp,
) {
    val reduce = LocalReduceMotion.current
    val offset = remember { Animatable(0f) }

    LaunchedEffect(isLoading, reduce) {
        if (isLoading && !reduce) {
            offset.animateTo(
                2f,
                infiniteRepeatable(tween(1600, easing = EaseInOut))
            )
        } else {
            offset.snapTo(0f)
        }
    }

    val path1 = remember {
        Path().apply {
            moveTo(3f, 8f)
            cubicTo(4.5f, 5f, 6f, 5f, 6f, 8f)
            cubicTo(7.5f, 11f, 9f, 11f, 9f, 8f)
            cubicTo(10.5f, 5f, 12f, 5f, 12f, 8f)
        }
    }

    val path2 = remember {
        Path().apply {
            moveTo(3f, 16f)
            cubicTo(4.5f, 13f, 6f, 13f, 6f, 16f)
            cubicTo(7.5f, 19f, 9f, 19f, 9f, 16f)
            cubicTo(10.5f, 13f, 12f, 13f, 12f, 16f)
            cubicTo(13.5f, 19f, 15f, 19f, 15f, 16f)
            cubicTo(16.5f, 13f, 18f, 13f, 18f, 16f)
        }
    }

    Canvas(Modifier.size(size)) {
        val scaleFactor = size.toPx() / 24f
        val stroke = 2f * scaleFactor

        translate(left = offset.value * scaleFactor) {
            drawPath(
                path = path1,
                color = tint,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = path2,
                color = tint,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * 泡沫动效图标 — 规范 §4.5 附。
 *
 * 前两圆上浮 0 → -2.6 → 0，2400ms ease-in-out 无限。
 * 第二个圆延迟 700ms。仅当 active 时播放；reduced motion 时静止。
 *
 * 触发：订单状态 == 清洗中（调用方传入 state == OrderStatus.WASHING）。
 */
@Composable
fun FoamIcon(
    active: Boolean,
    tint: Color,
    size: Dp = 24.dp,
) {
    val reduce = LocalReduceMotion.current
    val offset1 = remember { Animatable(0f) }
    val offset2 = remember { Animatable(0f) }

    LaunchedEffect(active, reduce) {
        if (active && !reduce) {
            coroutineScope {
                launch {
                    offset1.animateTo(
                        -2.6f,
                        infiniteRepeatable(tween(2400, easing = EaseInOut))
                    )
                }
                launch {
                    delay(700)
                    offset2.animateTo(
                        -2.6f,
                        infiniteRepeatable(tween(2400, easing = EaseInOut))
                    )
                }
            }
        } else {
            offset1.snapTo(0f)
            offset2.snapTo(0f)
        }
    }

    val circle1 = remember {
        Path().apply {
            addOval(Rect(3.2f, 8.8f, 13.8f, 19.4f))
        }
    }

    val circle2 = remember {
        Path().apply {
            addOval(Rect(13f, 5.7f, 20.2f, 12.9f))
        }
    }

    val circle3 = remember {
        Path().apply {
            addOval(Rect(13.2f, 12.6f, 18f, 17.4f))
        }
    }

    Canvas(Modifier.size(size)) {
        val scaleFactor = size.toPx() / 24f
        val stroke = 2f * scaleFactor

        translate(top = offset1.value * scaleFactor) {
            drawPath(
                path = circle1,
                color = tint,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        translate(top = offset2.value * scaleFactor) {
            drawPath(
                path = circle2,
                color = tint,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        drawPath(
            path = circle3,
            color = tint,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
