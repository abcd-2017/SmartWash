package com.smartwash.ui.page.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.feature.order.api.model.OrderStatus

/**
 * 滚筒动效图标 — 规范 §4.5 附。
 *
 * 外圈静止，提升筋 ×3 旋转。仅当订单状态 == 清洗中 时转动；
 * reduced motion 时静止在当前角度（盘面完整可读，不丢信息）。
 *
 * 红线：订单没在「清洗中」不许动 — 动画由真实状态触发，不伪造。
 */
@Composable
fun DrumIcon(state: OrderStatus, tint: Color, drumSize: Dp = 26.dp) {
    val reduce = LocalReduceMotion.current
    val angle = remember { Animatable(0f) }
    LaunchedEffect(state, reduce) {
        if (state == OrderStatus.WASHING && !reduce) {
            angle.animateTo(360f, infiniteRepeatable(tween(3200, easing = LinearEasing)))
        } else {
            angle.stop()
        }
    }
    Canvas(Modifier.size(drumSize)) {
        // 24 单位视口 → 画布像素
        val scale = drumSize.toPx() / 24f
        val stroke = 2f * scale
        // 外圈（静止，不参与旋转）
        drawCircle(
            color = tint,
            radius = 9f * scale,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // 提升筋 ×3（只转这组）
        rotate(degrees = angle.value, pivot = center) {
            repeat(3) { i ->
                rotate(degrees = i * 120f) {
                    drawLine(
                        color = tint,
                        start = Offset(center.x, center.y - 8.8f * scale),
                        end = Offset(center.x, center.y - 5.8f * scale),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
