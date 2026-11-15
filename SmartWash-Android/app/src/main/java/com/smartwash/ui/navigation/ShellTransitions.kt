package com.smartwash.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_SCREEN_MS
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween

/**
 * 壳层全局转场（两变体共享，故留 main 源集；full 的壳层页面图见 `app/src/full` 的 ShellGraph）。
 *
 * 动效语言 v0.4：fade 走页面转场档 400ms 减速 tween——alpha 用 `defaultSpring()`
 * 实际要 450–500ms，超出页面转场预算；scale 位移分量保留弹簧（从当前值动画，
 * 天然可中断）。reduceMotion 时取消缩放与长淡入，只留 180ms 纯淡入（规范 §8）。
 */

/** 壳层全局出场淡出时长 — 动效语言 v0.4：退出 ≈ 进入的 70%（400ms → 280ms） */
private val SHELL_EXIT_FADE_MS = DURATION_SCREEN_MS * 7 / 10

/** 壳层全局入场转场（原 MainActivity NavHost 内联动画，T8.1 抽出） */
internal fun shellEnterTransition(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        fadeIn(animationSpec = enterTween(DURATION_SCREEN_MS)) + scaleIn(
            initialScale = 0.95f,
            animationSpec = defaultSpring()
        )
    }

/** 壳层全局出场转场（pop 转场复用同值动画：fade 加速 280ms，scale 弹簧） */
internal fun shellExitTransition(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        fadeOut(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        fadeOut(animationSpec = exitTween(SHELL_EXIT_FADE_MS)) + scaleOut(
            targetScale = 0.95f,
            animationSpec = defaultSpring()
        )
    }
