package com.smartwash.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按下反馈 Modifier — Apple Design 原则：按下瞬间给予视觉反馈（缩放）
 *
 * 使用方式：把同一个 [MutableInteractionSource] 同时传给本 Modifier 和对应的
 * clickable/Button，按压缩放才会生效：
 * ```
 * val interactionSource = remember { MutableInteractionSource() }
 * Modifier
 *     .pressScale(interactionSource, 0.98f)
 *     .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { }
 * ```
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    scaleFactor: Float = 0.97f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) scaleFactor else 1f,
        animationSpec = snappySpring(),
        label = "pressScale"
    )
    this
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
}

/**
 * 按下透明度反馈 — 适用于卡片等大面积元素
 * 同样需要接入外部 [MutableInteractionSource]（与 clickable 共用同一实例）
 */
fun Modifier.pressAlpha(
    interactionSource: MutableInteractionSource,
    alphaFactor: Float = 0.92f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isPressed) alphaFactor else 1f,
        animationSpec = snappySpring(),
        label = "pressAlpha"
    )
    this
        .graphicsLayer { alpha = animatedAlpha }
}
