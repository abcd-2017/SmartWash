package com.smartwash.common.utils

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 动效工具 — 全应用唯一的动效 vocabulary。
 *
 * 规格来源：`docs/占卜模块UI设计方案.md` 第 7.1 节「弹簧参数表」，与
 * `docs/design/占卜模块视觉原型.html` 头部注释的声明一致：
 * 默认 spring(1.0, 350) / 惯性 spring(0.8, 350) / 按压缩放 0.97·100ms / 降级 180ms。
 *
 * 页面里不要再写魔法数值——改这里等于全应用换手感。
 */

/** 用户是否开启了「减少动态效果」（ANIMATOR_DURATION_SCALE == 0） */
fun isReduceMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * reduced-motion 状态在 Compose 树内共享，避免每个 composable 各自读 Context + Settings。
 * 由壳层 `MainActivity` 在根节点 provides，叶子节点经 [motionSpec] 消费。
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

// ========== 弹簧参数表（规范 7.1） ==========

/**
 * SpringDefault — 卡片入场、选中态、所有非手势位移动画。
 * 临界阻尼无回弹，从当前值动画，天然可中断。
 */
fun <T> defaultSpring(): SpringSpec<T> = spring(
    dampingRatio = 1f,
    stiffness = 350f,
)

/** SpringMomentum — 仅用于带惯性的动作：掷币落定、爻坠落、外环甩动 */
fun <T> momentumSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.8f,
    stiffness = 350f,
)

/** SpringHeavy — 大质量物体（大六壬外环等），比 [momentumSpring] 更慢更沉 */
fun <T> heavySpring(): SpringSpec<T> = spring(
    dampingRatio = 0.8f,
    stiffness = 200f,
)

// ========== 时长规格（规范 7.1 / 7.8） ==========

/** 按压反馈时长 — 配合 scale 0.97，按下瞬间必须无延迟感知 */
const val PRESS_DURATION_MS = 100

/** reduced motion 降级时长 — CrossfadeAlt */
const val CROSSFADE_ALT_MS = 180

// ========== 时长层级（动效语言 v0.4：退出时长 ≈ 进入的 60–70%） ==========

/** 小组件级 — 选中态、tint、微反馈时长上限 */
const val DURATION_MICRO_MS = 200

/** 卡片与展开级 — 内容块/卡片过渡 */
const val DURATION_CARD_MS = 300

/** 页面与弹层转场级 */
const val DURATION_SCREEN_MS = 400

/** 进入减速 tween — 动效语言：进入减速（LinearOutSlowInEasing） */
fun <T> enterTween(durationMillis: Int): TweenSpec<T> =
    tween(durationMillis, easing = LinearOutSlowInEasing)

/** 退出加速 tween — 动效语言：退出加速（FastOutLinearInEasing），时长取进入的 60–70% */
fun <T> exitTween(durationMillis: Int): TweenSpec<T> =
    tween(durationMillis, easing = FastOutLinearInEasing)

/**
 * 往返循环规格 — 修 MotionIcons 锯齿循环的根：`infiniteRepeatable` 必须带
 * [RepeatMode.Reverse]，否则 1 → 1.07 后瞬间跳回 1（视觉锯齿）。
 * 供所有"去而复返"的无限动效复用（水滴呼吸、泡沫上浮等）。
 */
fun <T> pingPongInfinite(durationMs: Int): InfiniteRepeatableSpec<T> =
    infiniteRepeatable(
        animation = tween(durationMs, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
    )

/**
 * 选中态颜色 — 全应用统一的"选中 / 未选中"颜色过渡（200ms 档弹簧，
 * reduced motion 时经 [motionSpec] 自动降级为 180ms 纯颜色过渡）。
 *
 * 收敛全应用"选中态颜色"模式：调用侧只给目标色，不再各写一遍 animateColorAsState。
 */
@Composable
fun selectedColor(target: Color): Color =
    animateColorAsState(
        targetValue = target,
        animationSpec = motionSpec(defaultSpring()),
        label = "selectedColor",
    ).value

/** TweenPress — 按压反馈规格（注意是 tween 而非 spring：按压要"立刻"而不是"弹一下"） */
fun <T> pressTween(): TweenSpec<T> = tween(
    durationMillis = PRESS_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * 动效规格选择器 — reduced motion 时把任意规格降级为 CrossfadeAlt（规范 7.8：
 * 取消无限旋转/回弹/位移/缩放，但保留文字、颜色、图标与状态可读性）。
 *
 * 用法：`val scale by animateFloatAsState(target, motionSpec(defaultSpring()), label = "x")`
 */
@Composable
fun <T> motionSpec(full: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) tween(CROSSFADE_ALT_MS) else full
