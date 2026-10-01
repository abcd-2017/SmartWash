package com.smartwash.common.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
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
 * Modifier.pressable(onClick = onClick, scaleFactor = 0.95f)
 * Modifier.pressable(onClick = onClick, alphaFactor = 0.92f)
 * ```
 *
 * 保留原有 pressScale/pressAlpha 供观象台等特殊场景使用。
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
            indication = LocalIndication.current,
            onClick = onClick
        )
}

/**
 * 按下反馈 Modifier — Apple Design 原则：按下瞬间给予视觉反馈（缩放）
 *
 * 需要把同一个 [MutableInteractionSource] 同时传给本 Modifier 和对应的 clickable。
 * 推荐使用 [pressable] 扩展，内部已处理好 InteractionSource 共用。
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
    this.graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
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
        animationSpec = snappySpring(),
        label = "pressAlpha"
    )
    this.graphicsLayer { alpha = animatedAlpha }
}
