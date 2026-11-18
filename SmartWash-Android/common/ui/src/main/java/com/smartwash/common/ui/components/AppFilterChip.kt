package com.smartwash.common.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor

/**
 * 胶囊筛选/选择 chip 的视觉变体。
 *
 * - [SOLID]：选中 = primary 实底 + onPrimary 白字；未选中 = surface 底 + outline 描边 + textSecondary
 *   （对齐原 `SubscriptionSelectableChip` / `ToolboxSelectableChip` / `AppPillTabRow` / Drum 节奏型）
 * - [SOFT]：选中 = primarySoft 浅底 + onPrimarySoft 深字；未选中同上
 *   （对齐原 dining `SearchModeChip` / `AllergenChip`）
 */
enum class AppChipVariant { SOLID, SOFT }

/**
 * 全库统一的胶囊筛选 chip —— 规范 §3.x 分段/筛选控件。
 *
 * 收敛此前 6+ 套各自实现的 chip（选中底 primary / primarySoft 两套语言、热区 36/40/44/48 四种写法、
 * 字重是否随选中变化、动画覆盖不均、无障碍缺失）。统一口径：
 * - 形状 [AppDimens.radiusFull]，外层 [heightIn] 48dp 最小热区（ui-ux-pro-max 触控目标 ≥48dp）；
 * - 描边常驻 1dp：选中与底色同色（视觉无边界、尺寸不跳），未选中 outline；
 * - 字重恒定 SemiBold（字重随选中插值会让文字宽度连续变化，兄弟 chip 连续位移，观感更差）；
 * - 底色 / 文字 / 描边统一走 [selectedColor]（弹簧，reduced motion 自动降级 180ms）；
 * - 无障碍 [selected] 语义，供 TalkBack 播报「已选中」。
 *
 * 用法：
 * ```
 * AppFilterChip(text = "全部", selected = selected, onClick = { ... })
 * AppFilterChip(text = "微辣", selected = selected, onClick = { ... }, variant = AppChipVariant.SOFT)
 * ```
 */
@Composable
fun AppFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppChipVariant = AppChipVariant.SOLID,
    enabled: Boolean = true,
    tnum: Boolean = false,
    hapticOnSelect: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val scheme = AppColors.colorScheme
    val containerColor = selectedColor(
        when {
            !selected -> scheme.surface
            variant == AppChipVariant.SOLID -> scheme.primary
            else -> scheme.primarySoft
        }
    )
    val contentColor = selectedColor(
        when {
            !selected -> scheme.textSecondary
            variant == AppChipVariant.SOLID -> scheme.onPrimary
            else -> scheme.onPrimarySoft
        }
    )
    // 描边常驻：选中与底色同色（无边界），未选中 outline
    val borderColor = selectedColor(
        when {
            !selected -> scheme.outline
            variant == AppChipVariant.SOLID -> scheme.primary
            else -> scheme.primarySoft
        }
    )
    val view = currentView()
    Box(
        modifier = modifier
            .pressable(
                onClick = {
                    if (!enabled) return@pressable
                    if (hapticOnSelect && !selected) view.performHaptic(HapticEffect.SELECTION)
                    onClick()
                },
                debounce = false,
            )
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.radiusFull),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Text(
                text = text,
                style = if (tnum) textStyle.copy(fontFeatureSettings = "tnum") else textStyle,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                // 控件档 TD36：可见胶囊高 32dp = caption 行高 16dp + 上下各 8dp
                modifier = Modifier.padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
            )
        }
    }
}

/**
 * 可横滚的 chip 行 —— 替代各页自绘的 `Row + horizontalScroll + spacedBy`。
 *
 * 用于数量不定、可能超宽的筛选 chip 组（工具箱有效期、配送时长、订阅状态筛选等）。
 * 固定数量的 chip 组直接用普通 `Row` 即可，不必包本组件。
 */
@Composable
fun AppChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = AppDimens.pagePadding),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(AppDimens.spaceSm),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
