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
 * 卡片内「标签 — 值」信息行 — DESIGN-SPEC v0.4 §5 列表项规格（行高 ≥48）。
 *
 * 行高默认 56dp（实现自定默认值；规范仅要求 ≥48，信息密度高的页面可传参收紧）。
 * 标签与值分列两端；值侧 [value] 用弹性占位把值推到行尾，标签过长时值不会被挤出可视区
 * （旧实现固定 Spacer(24dp) 在长标签下会与值重叠）。
 *
 * 注：旧注释曾引用「§3.4 卡片内信息行高 56dp」，经核对 DESIGN-SPEC v0.4 无 §3.4 条款，
 * 该引用不成立，已于 2026-11-05 修正。
 *
 * @param rowHeight 行高，默认 56dp（规范下限 ≥48，见 §5）
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: TextStyle = MaterialTheme.typography.bodySmall,
    rowHeight: Dp = 56.dp,
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
