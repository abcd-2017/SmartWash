package com.smartwash.common.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 共享元素转场 scope 的 CompositionLocal —— 替代原先由壳层经 `toolboxGraph` 参数层层下传。
 *
 * 壳层 `MainActivity` 在 `SharedTransitionLayout` 内 `CompositionLocalProvider` 提供
 * （`LocalSharedTransitionScope provides this`），消费方（当前为工具箱短链列表/详情页）
 * 在组合期 `LocalSharedTransitionScope.current` 读取；未处于共享转场容器内时为 null，
 * 消费方按可空处理（null 时不挂 sharedElement，退化为普通页面转场）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
