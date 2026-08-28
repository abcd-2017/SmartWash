package com.smartwash.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按下反馈 Modifier — Apple Design 原则：按下瞬间给予视觉反馈（缩放）
 * 使用方式：Modifier.pressScale()
 */
fun Modifier.pressScale(scaleFactor: Float = 0.97f): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
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
 */
fun Modifier.pressAlpha(alphaFactor: Float = 0.92f): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isPressed) alphaFactor else 1f,
        animationSpec = snappySpring(),
        label = "pressAlpha"
    )
    this
        .graphicsLayer { alpha = animatedAlpha }
}
