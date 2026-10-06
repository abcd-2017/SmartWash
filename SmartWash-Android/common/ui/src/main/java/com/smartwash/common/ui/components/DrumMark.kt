package com.smartwash.common.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 滚筒圆 — 规范 §5 母题：同心圆环 + 一条穿过它的水波线。
 *
 * 几何基准 64（设计稿屏 3 hero SVG），全部尺寸相对 [size] 等比缩放：
 * - 外环 track：r = 28/64，描边宽 2.5/64，颜色 [trackTint]
 * - 进度弧：同半径同宽，起始 -90°、扫角 360° × progress，颜色 [tint]（progress 为 null 或 <= 0 不绘制）
 * - 内环：r = 17/64，描边宽 2/64，颜色 [innerRingTint]（null 不绘制）
 * - 单条水波：描边宽 2.4/64，颜色 [tint]，对照设计稿
 *   `M23 32c3-3.5 6 2 9-.5s6 1.5 9-1`（中心 32,32，相对中心自 -9 至 +9 两段 cubic 波浪）
 *
 * 组件本身不带动画：进度环由真实状态传入，空态传 [progress] = null 即为静止滚筒。
 *
 * @param size 组件尺寸
 * @param progress 进度 0..1（订单进度场景），null 表示静止空态
 * @param tint 主色（进度弧与水波）
 * @param trackTint 外环轨道色，默认同 [tint]
 * @param innerRingTint 内环色，null 表示不绘制内环
 */
@Composable
fun DrumMark(
    size: Dp,
    progress: Float? = null,
    tint: Color,
    trackTint: Color = tint,
    innerRingTint: Color? = null,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val scaleFactor = this.size.minDimension / 64f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // 外环 track
        drawCircle(
            color = trackTint,
            radius = 28f * scaleFactor,
            center = center,
            style = Stroke(width = 2.5f * scaleFactor, cap = StrokeCap.Round)
        )

        // 进度弧（起始 -90°，扫描 360° × progress）
        if (progress != null) {
            val clamped = progress.coerceIn(0f, 1f)
            if (clamped > 0f) {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(center.x - 28f * scaleFactor, center.y - 28f * scaleFactor),
                    size = Size(56f * scaleFactor, 56f * scaleFactor),
                    style = Stroke(width = 2.5f * scaleFactor, cap = StrokeCap.Round)
                )
            }
        }

        // 内环
        if (innerRingTint != null) {
            drawCircle(
                color = innerRingTint,
                radius = 17f * scaleFactor,
                center = center,
                style = Stroke(width = 2f * scaleFactor, cap = StrokeCap.Round)
            )
        }

        // 单条水波线（穿过圆环）— 设计稿 M23 32c3-3.5 6 2 9-.5s6 1.5 9-1，中心 32,32
        val wavePath = Path().apply {
            moveTo(center.x - 9f * scaleFactor, center.y)
            cubicTo(
                center.x - 6f * scaleFactor, center.y - 3.5f * scaleFactor,
                center.x - 3f * scaleFactor, center.y + 2f * scaleFactor,
                center.x, center.y - 0.5f * scaleFactor
            )
            // s 指令：第一控制点为上一段第二控制点关于当前点的反射 → (3, -3)
            cubicTo(
                center.x + 3f * scaleFactor, center.y - 3f * scaleFactor,
                center.x + 6f * scaleFactor, center.y + 1f * scaleFactor,
                center.x + 9f * scaleFactor, center.y - 1.5f * scaleFactor
            )
        }
        drawPath(
            path = wavePath,
            color = tint,
            style = Stroke(width = 2.4f * scaleFactor, cap = StrokeCap.Round)
        )
    }
}
