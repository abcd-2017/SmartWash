package com.smartwash.utils

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalView

/**
 * 触觉反馈工具 — Apple Design 多模态反馈原则
 */

enum class HapticEffect {
    LIGHT,      // 轻触 — 一般交互
    MEDIUM,     // 中触 — 确认操作
    HEAVY,      // 重触 — 重要操作/成功
    SUCCESS,    // 成功
    ERROR,      // 错误
    SELECTION   // 选择/Tab切换
}

@Composable
@ReadOnlyComposable
fun currentView(): View = LocalView.current

fun View.performHaptic(effect: HapticEffect) {
    when (effect) {
        HapticEffect.LIGHT -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        HapticEffect.MEDIUM -> performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        HapticEffect.HEAVY -> performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        HapticEffect.SUCCESS -> performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        HapticEffect.ERROR -> performHapticFeedback(HapticFeedbackConstants.REJECT)
        HapticEffect.SELECTION -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
