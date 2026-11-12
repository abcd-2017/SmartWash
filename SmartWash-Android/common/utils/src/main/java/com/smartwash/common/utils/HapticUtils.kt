package com.smartwash.common.utils

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalView

/**
 * 触觉反馈工具 — 多模态反馈原则（规范 7.7）。
 *
 * 触觉遵循 causality：只在因果明确的时刻发（见规范 7.7 时刻表），
 * 列表滚动/翻页一律无声无震。
 */

enum class HapticEffect {
    LIGHT,        // 轻触 — 一般交互、币落定、解读完成
    MEDIUM,       // 中触 — 确认操作
    HEAVY,        // 重触 — 重要操作
    SUCCESS,      // 成功模式 — 盘面完整、支付成功
    ERROR,        // 错误
    SELECTION,    // 选择/Tab切换
    DOUBLE_TICK;  // 双连击 — 老阴/老阳揭示（动爻值得更强的身体反馈）
}

/** 双连击两下之间的间隔（毫秒）——够快才成"连击"，够慢才分辨得出两下 */
private const val DOUBLE_TICK_GAP_MS = 70L

/**
 * 触感反馈全局开关（权限管理页「触感反馈」行持久化，默认开启）。
 *
 * 内存态供 [View.performHaptic] 同步热路径读取——点击时刻无法挂起读 DataStore，
 * 持久化初值由 user:impl 的 HapticSettingsInitTask 启动异步加载，
 * 权限管理页切换时同步改内存 + 落盘（NonCancellable，避免页面销毁取消写入）。
 */
object HapticSettings {
    /** DataStore 存储键（非用户可见常量，归本对象持有） */
    const val STORAGE_KEY = "haptic_feedback_enabled"

    @Volatile
    var enabled: Boolean = true
}

@Composable
@ReadOnlyComposable
fun currentView(): View = LocalView.current

fun View.performHaptic(effect: HapticEffect) {
    // 全局开关关闭时静默返回（设置页「触感反馈」= 关）
    if (!HapticSettings.enabled) return
    when (effect) {
        HapticEffect.LIGHT -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        HapticEffect.MEDIUM -> performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        HapticEffect.HEAVY -> performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        HapticEffect.SUCCESS -> performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        HapticEffect.ERROR -> performHapticFeedback(HapticFeedbackConstants.REJECT)
        HapticEffect.SELECTION -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        HapticEffect.DOUBLE_TICK -> {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            postDelayed(
                { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
                DOUBLE_TICK_GAP_MS
            )
        }
    }
}
