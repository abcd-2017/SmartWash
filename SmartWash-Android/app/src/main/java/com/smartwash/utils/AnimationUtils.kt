package com.smartwash.utils

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 动画工具 — 提供统一的弹簧规格，并尊重用户的减少动态效果设置
 */

/** 检测用户是否开启了减少动态效果（ANIMATOR_DURATION_SCALE == 0） */
fun isReduceMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f
    } catch (_: Exception) {
        false
    }
}

/** 默认弹簧 — 临界阻尼，无回弹（Apple 推荐默认值 damping 1.0, response 0.3-0.4） */
fun <T> defaultSpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,  // 1.0
    stiffness = Spring.StiffnessMediumLow        // ≈ response 0.35
)

/** 动量弹簧 — 欠阻尼，轻微回弹（Apple 推荐用于手势释放 damping ~0.8, response 0.3-0.4） */
fun <T> momentumSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMedium           // ≈ response 0.4
)

/** 快速弹簧 — 用于按钮等小元素（damping 1.0, response 0.2） */
fun <T> snappySpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)
