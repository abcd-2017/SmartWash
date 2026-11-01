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
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.selectedColor

/**
 * 公共单选标记 — 全应用统一口径（2026-11-01 用户拍板「单选框全局统一样式」）。
 *
 * 对齐设计稿 `.pm3 .ck3`：20dp 圆形、1.5dp 描边（原型 1.6px）、选中态描边转 primary 且内圆填充
 * （原型 radial-gradient 42% ≈ 8dp）。此前充值页 24dp / 支付页 28dp / 默认 20dp 三种尺寸并存
 * （同一控件三种长相），现统一 20dp。
 *
 * 结构：外环（描边色随选中态弹簧过渡）+ 内圆（scale 0 → 1 弹簧长出）。
 * reduced motion 由 [motionSpec] 自动降级为 180ms 纯颜色过渡（缩放仍平滑但无回弹）。
 *
 * 无 ripple、无 clickable：按压反馈由调用方负责（本组件只做状态表达）。
 * 全组件无魔法值——时长/曲线/spring 参数一律走 [com.smartwash.common.utils] vocabulary。
 */
@Composable
fun SelectionDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
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
        modifier = modifier.size(DotSize),
        contentAlignment = Alignment.Center,
    ) {
        // 外环（设计稿 .ck3：20dp 圆 + 1.5dp 描边）
        Box(
            modifier = Modifier
                .size(DotSize)
                .border(DotBorder, ringColor, CircleShape)
        )
        // 内圆：选中时从圆心长出（scale 0 → 1），直径约为外环 42%（原型 radial-gradient 42%）
        Box(
            modifier = Modifier
                .size(DotInnerSize)
                .graphicsLayer {
                    scaleX = innerScale
                    scaleY = innerScale
                }
                .clip(CircleShape)
                .background(AppColors.colorScheme.primary)
        )
    }
}

/** 单选标记外径（设计稿 .ck3 = 20px） */
private val DotSize = 20.dp

/** 单选标记描边宽（原型 1.6px，取规范 1.5dp 档） */
private val DotBorder = 1.5.dp

/** 单选标记内圆直径（原型 radial-gradient 42% ≈ 8.4dp） */
private val DotInnerSize = 8.dp
