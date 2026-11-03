package com.smartwash.feature.mood.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.feature.mood.MoodConstant
import com.smartwash.feature.mood.R

/**
 * 心情等级词典 —— 端上五色块的单一事实来源（服务端只存 level + color 快照）。
 *
 * 颜色一律映射到清氧设计系统既有 Token（禁止自创色值，规范 §2.1）：
 *   1 暴雨 = danger（红粉，最差） · 2 阴 = textTertiary（灰） · 3 多云 = water（蓝） ·
 *   4 晴 = ongoing（暖阳橙） · 5 大晴 = primary（品牌绿，最好）。
 *
 * [snapshotHex] 是提交给后端的 color 快照（#RRGGBB，浅色主题令牌基准值）：
 * 契约约定「color 为提交时快照，后续端上调整词典颜色不会改写历史」，
 * 用固定值保证同一心情的快照稳定；界面着色仍走 [color] 的主题感知 Token。
 */
enum class MoodLevel(
    val value: Int,
    @StringRes val labelRes: Int,
    val snapshotHex: String,
) {
    RAINSTORM(1, R.string.mood_level_rainstorm, "#A8435A"),
    OVERCAST(2, R.string.mood_level_overcast, "#A9AEA9"),
    CLOUDY(3, R.string.mood_level_cloudy, "#4A9FD4"),
    SUNNY(4, R.string.mood_level_sunny, "#D98A26"),
    SHINING(5, R.string.mood_level_shining, "#2D9B6A"),
    ;

    companion object {

        /** 按 level 取词典；越界（后端理论上 CHECK 拦截）返回 null，调用方自行兜底 */
        fun fromValue(value: Int): MoodLevel? = entries.find { it.value == value }
    }
}

/**
 * 心情等级 → 主题感知颜色（浅/深色自动适配，月历历史格除外——历史格按契约优先用快照色）。
 * 未映射的 level（理论上不会出现）兜底回品牌绿。
 */
@Composable
fun moodLevelColor(level: Int): Color {
    val colors = AppColors.colorScheme
    return when (level) {
        1 -> colors.danger
        2 -> colors.textTertiary
        3 -> colors.water
        4 -> colors.ongoing
        else -> colors.primary
    }
}

/** 心情等级 → 心情名文案（暴雨/阴/多云/晴/大晴）；未映射 level 兜底「—」 */
@Composable
fun moodLevelLabel(level: Int): String =
    stringResource(MoodLevel.fromValue(level)?.labelRes ?: R.string.mood_trend_value_none)

/**
 * 解析后端返回的 color 快照（#RRGGBB / #RRGGBBAA）；非法/空值兜底回词典主题色。
 * 月历热图历史格按契约「历史色以此为准」优先消费本函数。
 */
@Composable
fun parseSnapshotColor(hex: String?, level: Int): Color {
    if (hex.isNullOrBlank()) return moodLevelColor(level)
    return runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }
        .getOrDefault(moodLevelColor(level))
}

/**
 * 提交给后端的 color 快照（#RRGGBB，词典浅色基准值，见 [MoodLevel.snapshotHex]）。
 * 非 Composable：供 ViewModel 保存时取值；未映射 level 兜底品牌绿基准。
 */
fun moodSnapshotHex(level: Int): String =
    MoodLevel.fromValue(level)?.snapshotHex ?: MoodLevel.SHINING.snapshotHex
