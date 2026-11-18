package com.smartwash.common.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec

/**
 * 列表项进出场 / 位移动效统一入口（动效语言 §7：state-transition 平滑、stagger 节奏）。
 *
 * 背景：此前仅占卜卦历用了 `animateItem`，其余列表的分页 / 刷新 / 筛选项均为 0ms 突变。
 * 收敛成一行调用，避免每处各写一遍 animateItem + reduced motion 分支。
 *
 * reduced motion 时直接落位不动画（规范 §8：取消位移，保留状态可读性）。
 *
 * 用法（LazyColumn/LazyRow 的 item 内，item lambda 以 LazyItemScope 为接收者）：
 * ```
 * // 无其它修饰符时
 * modifier = animatedListItem()
 * // 已有 modifier 链时追加到末尾（animateItem 是 LazyItemScope 的成员扩展，不能直接链式）
 * modifier = Modifier.fillMaxWidth().then(animatedListItem())
 * ```
 */
@Composable
fun LazyItemScope.animatedListItem(): Modifier =
    if (LocalReduceMotion.current) {
        Modifier
    } else {
        Modifier.animateItem(placementSpec = motionSpec(defaultSpring()))
    }

/**
 * 网格项版本，用于 LazyVerticalGrid / LazyHorizontalGrid（如菜谱库、游戏中心）。
 * 语义与 [animatedListItem] 一致。
 */
@Composable
fun LazyGridItemScope.animatedGridItem(): Modifier =
    if (LocalReduceMotion.current) {
        Modifier
    } else {
        Modifier.animateItem(placementSpec = motionSpec(defaultSpring()))
    }
