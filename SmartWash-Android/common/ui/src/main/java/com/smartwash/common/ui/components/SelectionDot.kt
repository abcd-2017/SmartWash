package com.smartwash.common.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.selectedColor

/**
 * 公共选中圆点 — 单选/步骤态的标记（规范 §7 的"选中态颜色"统一模式）。
 *
 * 结构：20dp 外环（描边色随选中态弹簧过渡）+ 内圆（scale 0 → 1 弹簧）。
 * reduced motion 由 [motionSpec] 自动降级为 180ms 纯颜色过渡（缩放仍平滑但无回弹）。
 *
 * 无 ripple、无 clickable：按压反馈由调用方负责（本组件只做状态表达）。
 * 全组件无魔法值——时长/曲线/spring 参数一律走 [com.smartwash.common.utils] vocabulary。
 */
@Composable
fun SelectionDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
    dotSize: Dp = 20.dp,
) {
    val ringColor = selectedColor(
        if (selected) AppColors.colorScheme.primary else AppColors.colorScheme.outline
    )
    val innerScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motionSpec(defaultSpring()),
        label = "dotScale"
    )

    Box(
        modifier = modifier.size(dotSize),
        contentAlignment = Alignment.Center,
    ) {
        // 外环
        Box(
            modifier = Modifier
                .size(dotSize)
                .border(1.dp, ringColor, CircleShape)
        )
        // 内圆：选中时从圆心长出（scale 0 → 1）
        Box(
            modifier = Modifier
                .size(dotSize / 2f)
                .graphicsLayer {
                    scaleX = innerScale
                    scaleY = innerScale
                }
                .clip(CircleShape)
                .background(AppColors.colorScheme.primary)
        )
    }
}
