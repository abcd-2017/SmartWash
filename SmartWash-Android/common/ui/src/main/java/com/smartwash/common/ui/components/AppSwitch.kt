package com.smartwash.common.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor

/**
 * 全库唯一开关（自绘）—— 规范 §3.2 开关形态，替代此前散落的自绘/伪开关。
 *
 * 为什么自绘而不用 Material3 `Switch`：M3 Switch 自带 state layer（涟漪），
 * 与规范 §7「禁 Material 交互组件 state layer」冲突；且既有订阅开关已是自绘，
 * 本组件把它的口径（未选中轨道取 textSecondary 以满足 ≥3:1 非文本对比）收敛为全库标准。
 *
 * 规格：
 * - 轨道 44×24dp 胶囊、thumb 20dp，位移 2dp ↔ 22dp 走 [defaultSpring]（规范 §7 位移用弹簧）；
 *   44×24 与原订阅开关同尺寸，视觉高度贴近 16sp 正文行高（24dp），不压过左侧文字；
 * - 轨道：选中 = primary 实底；未选中 = surfaceVariant 轻底 + 1dp outline 描边（轮廓由描边承担，
 *   比灰实底更轻，深色下同样清晰）；
 * - 外层 48dp 最小热区（ui-ux-pro-max：触控目标 ≥48dp），视觉尺寸不变；
 * - 选中态色走 [selectedColor]，reduced motion 经 [motionSpec] 自动降级 180ms；
 * - 无障碍：`Role.Switch` + `stateDescription`（开/关），可选 [contentDescription] 作为标签。
 *
 * 用法：
 * ```
 * AppSwitch(checked = on, onCheckedChange = { on = it }, onText = "已开启", offText = "已关闭")
 * ```
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onText: String? = null,
    offText: String? = null,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = motionSpec(defaultSpring()),
        label = "appSwitchThumb",
    )
    // 选中 = primary 实底；未选中 = surfaceVariant 轻底 + 1dp outline 描边（轮廓由描边承担）。
    // 颜色/描边过渡统一走 selectedColor（弹簧），reduced motion 自动降级 180ms。
    val trackColor = selectedColor(
        if (checked) AppColors.colorScheme.primary else AppColors.colorScheme.surfaceVariant
    )
    val borderColor = selectedColor(
        if (checked) AppColors.colorScheme.primary else AppColors.colorScheme.outline
    )
    Box(
        modifier = modifier
            // pressable 前置 + 外扩热区：视觉胶囊 48×28 不变，命中区抬到 48×48
            .pressable(onClick = { if (enabled) onCheckedChange(!checked) }, debounce = false)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                role = Role.Switch
                if (contentDescription != null) this.contentDescription = contentDescription
                val state = if (checked) onText else offText
                if (state != null) stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(RoundedCornerShape(AppDimens.radiusFull))
                .background(trackColor)
                .border(1.dp, borderColor, RoundedCornerShape(AppDimens.radiusFull)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = thumbOffset)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(AppColors.colorScheme.surface),
            )
        }
    }
}

/**
 * 设置页「开关行」—— 图标盒（可选）+ 标题/副标题 + 右侧 [AppSwitch]，整行点击切换。
 *
 * 用于把「用文字状态冒充开关」的设置项（自动更新、触感反馈等）升级为真实开关语义：
 * 整行可点、右侧开关有 `Role.Switch` + 开/关状态播报。
 *
 * @param rowHeight 行高，默认 64dp；带副标题且需更高行时可传 72dp
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconTint: Color? = null,
    description: String? = null,
    rowHeight: Dp = 64.dp,
    enabled: Boolean = true,
    onText: String? = null,
    offText: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .pressable(onClick = { if (enabled) onCheckedChange(!checked) }, debounce = false),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBox(
                icon = icon,
                size = 32.dp,
                iconSize = 20.dp,
                containerColor = iconContainerColor ?: AppColors.colorScheme.iconContainerTeal,
                iconTint = iconTint ?: AppColors.colorScheme.iconForegroundTeal,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textPrimary,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            onText = onText,
            offText = offText,
        )
    }
}
