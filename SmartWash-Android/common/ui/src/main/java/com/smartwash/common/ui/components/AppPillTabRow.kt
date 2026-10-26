package com.smartwash.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor

/**
 * 胶囊分段选择器 — 公共 tab 行（规范 §3.x 分段控件）。
 *
 * 与 [AppTabBar] 的分工：本组件是"胶囊分段"形态（选中项有底色胶囊），
 * AppTabBar 是"下划线指示条"形态。页面按设计稿二选一。
 *
 * 动效：选中底色 / 文字色走 [selectedColor]（200ms 档弹簧，reduced motion 自动降级
 * 180ms 纯颜色过渡）；字重统一为 SemiBold——字重渐变会让文字宽度在动画期间持续变化，
 * 在可横滚 Row 里表现为兄弟节点连续位移，观感差于一次跳变，故不做字重动画。
 * 不引入背景滑块动画（颜色过渡已足够表达选中位移）。
 *
 * 触觉：选中项 `SELECTION`（因果明确的时刻，规范 §7.7）；
 * 按压反馈由 [pressable] 提供（indication = null，禁 Material ripple）。
 */
@Composable
fun AppPillTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = currentView()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding),
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            val containerColor = selectedColor(
                if (selected) AppColors.colorScheme.primaryLight else Color.Transparent
            )
            val labelColor = selectedColor(
                if (selected) AppColors.colorScheme.primary
                else AppColors.colorScheme.textSecondary
            )
            Box(
                modifier = Modifier
                    // Tab 快速连切是合法操作，关闭防抖；pressable 内置 indication = null（禁 ripple）
                    .padding(end = AppDimens.spaceXs)
                    .clip(RoundedCornerShape(AppDimens.buttonRadius))
                    .background(containerColor)
                    .pressable(
                        onClick = {
                            view.performHaptic(HapticEffect.SELECTION)
                            onSelect(index)
                        },
                        debounce = false,
                    )
                    .padding(horizontal = AppDimens.spaceMd, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                )
            }
        }
    }
}
