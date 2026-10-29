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
 * 卡片内「标签 — 值」信息行 — DESIGN-SPEC v0.4 §3.4 信息行规格。
 *
 * 行高 56dp（规范 §3.4 卡片内信息行高），标签与值分列两端。
 * 值侧 [value] 用弹性占位把值推到行尾，标签过长时值不会被挤出可视区
 * （旧实现固定 Spacer(24dp) 在长标签下会与值重叠）。
 *
 * @param rowHeight 行高，默认 56dp（§3.4）
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
