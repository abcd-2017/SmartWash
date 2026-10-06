package com.smartwash.common.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按压反馈便捷扩展 — 内部统一创建 InteractionSource 并同时驱动 pressScale + clickable。
 *
 * 使用方式：
 * ```
 * Modifier.pressable(onClick = onClick)
 * Modifier.pressable(onClick = onClick, alphaFactor = 0.92f)
 * ```
 *
 * 规格：规范 7.2「按压即反馈」——按下瞬间 scale 0.97 + [PRESS_DURATION_MS]，
 * 所有可点元素强制生效。reduced motion 下缩放降级为透明度（见 [pressScale]）。
 *
 * 注意：indication 固定为 null——本设计系统的按压反馈只由 scale/alpha 表达，
 * 不使用 Material ripple（涟漪的颜色与形状不受令牌控制，按下时会浮出
 * 与容器不贴合的胶囊/矩形色块）。
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    scaleFactor: Float = 0.97f,
    alphaFactor: Float = 1.0f,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .pressScale(interactionSource, scaleFactor)
        .then(if (alphaFactor != 1f) Modifier.pressAlpha(interactionSource, alphaFactor) else Modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * 按下反馈 Modifier — 按下瞬间给予视觉反馈（缩放）。
 *
 * 需要把同一个 [MutableInteractionSource] 同时传给本 Modifier 和对应的 clickable。
 * 推荐使用 [pressable] 扩展，内部已处理好 InteractionSource 共用。
 *
 * reduced motion（规范 7.8 明令取消缩放）时降级为极轻的透明度变化——
 * 降级不等于没有反馈，只是换一种不引起前庭不适的表达。
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    scaleFactor: Float = 0.97f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    if (LocalReduceMotion.current) {
        val animatedAlpha by animateFloatAsState(
            targetValue = if (isPressed) 0.86f else 1f,
            animationSpec = pressTween(),
            label = "pressScaleReduced"
        )
        this.graphicsLayer { alpha = animatedAlpha }
    } else {
        val animatedScale by animateFloatAsState(
            targetValue = if (isPressed) scaleFactor else 1f,
            animationSpec = pressTween(),
            label = "pressScale"
        )
        this.graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
    }
}

/**
 * 按下透明度反馈 — 适用于卡片等大面积元素
 *
 * 需要把同一个 [MutableInteractionSource] 同时传给本 Modifier 和对应的 clickable。
 * 推荐使用 [pressable] 扩展，内部已处理好 InteractionSource 共用。
 */
fun Modifier.pressAlpha(
    interactionSource: MutableInteractionSource,
    alphaFactor: Float = 0.92f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isPressed) alphaFactor else 1f,
        animationSpec = pressTween(),
        label = "pressAlpha"
    )
    this.graphicsLayer { alpha = animatedAlpha }
}
