package com.smartwash.common.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.IconBox

@Composable
fun InfoSection(
    title: String,
    icon: ImageVector?,
    iconContainerColor: Color? = null,
    iconTint: Color? = null,
    content: @Composable () -> Unit,
) {
    // 卡片规格对齐同页组画板实测：radiusLg 16 + 1px outline 描边 + 无阴影
    // （规范 §3：卡片 elevation 0 用描边代替；由订单详情页对齐任务统一，取件/寄件页共用本组件）
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding)
        ) {
            // 订单详情屏 6 实测：图标块底 → 首行 4（配合页内行距 34+14 得 48 行距节奏）；图标 20 = icon.sm 档
            // 寄件板（寄件 · 浅色/深色）操作指引卡无图标：icon 传 null 时只渲染标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                if (icon != null) {
                    IconBox(
                        icon = icon,
                        size = 36.dp,
                        iconSize = 20.dp,
                        // null 时保持 IconBox 默认（primaryLight 容器 / primary 前景）
                        containerColor = iconContainerColor ?: AppColors.colorScheme.primaryLight,
                        iconTint = iconTint ?: AppColors.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.colorScheme.textPrimary
                )
            }
            content()
        }
    }
}
