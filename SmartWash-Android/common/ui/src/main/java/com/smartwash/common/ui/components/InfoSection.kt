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
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.IconBox

@Composable
fun InfoSection(
    title: String,
    icon: ImageVector?,
    iconContainerColor: Color? = null,
    iconTint: Color? = null,
    content: @Composable () -> Unit,
) {
    // 卡片规格 v0.4 §3.1 / §2.4：radiusLg 16 + 1px outline 描边 + level1 阴影
    // （订单详情页与取件/寄件页共用本组件，描边与阴影在此一处收敛）
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding)
        ) {
            // 图标块底 → 首行 4dp 贴合（InfoRow 行高已按 §3.4 收敛为 56dp，行距节奏由行高承担）；
            // 图标块 36/20 = 规范 §3.4 的 icon.sm 档
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
