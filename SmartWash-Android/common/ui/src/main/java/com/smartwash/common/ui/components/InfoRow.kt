package com.smartwash.common.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors

/**
 * 卡片内「标签 — 值」信息行 —— **二级信息组件**（与设置页 SettingRow 一级组件区分）。
 *
 * 分级口径（2026-11-09 用户拍板）：
 * - 一级 = SettingRow（设置页行）：14sp/500 + 64dp 高，可点击导航；
 * - 二级 = 本组件（卡内信息行）：12sp caption + 40dp 高，纯信息展示不可点。
 *
 * 行高默认 40dp（二级紧凑档，行间 0；12sp 字号在 40dp 行内上下留白约 12dp，比例协调）。
 * 标签与值分列两端；值侧 [value] 用弹性占位把值推到行尾，标签过长时值不会被挤出可视区
 * （旧实现固定 Spacer(24dp) 在长标签下会与值重叠）。
 *
 * @param rowHeight 行高，默认 40dp（二级紧凑档）
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: TextStyle = MaterialTheme.typography.bodySmall,
    rowHeight: Dp = 40.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = valueStyle,
            color = valueColor
        )
    }
}
