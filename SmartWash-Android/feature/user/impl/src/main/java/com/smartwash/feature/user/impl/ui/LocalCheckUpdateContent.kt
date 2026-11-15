package com.smartwash.feature.user.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 设置页「检查更新」区块插槽的 CompositionLocal —— 替代原先由壳层经 `userGraph`
 * 参数层层下传。
 *
 * 热更新链路归 `:feature:update`（app 壳聚合，无 api 模块），user-impl 不依赖它，
 * 故跨域 UI 以插槽形式由壳层注入：MainActivity 在 NavHost 外层
 * `CompositionLocalProvider(LocalCheckUpdateContent provides { UpdateSettingRows(vm) })`，
 * [userGraph] 在组合期读取。未注入（如 lite flavor 裁掉更新域）时为 null，
 * 设置页不展示更新区块。
 */
val LocalCheckUpdateContent: ProvidableCompositionLocal<(@Composable () -> Unit)?> =
    staticCompositionLocalOf { null }
