package com.smartwash.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
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
 * 横滚可达性（回归 2026-11-06 N-05-01）：tab 数多时（订单页 5 个）窄屏 360dp 下
 * 尾部 tab 会被右缘截断，且带初始 tab 进页（如「我的订单(已取消)」）时选中项在视口外。
 * 选中变化时自动将选中胶囊滚到视口内（尽量居中）；reduced motion 直接落位不动画。
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
    val scrollState = rememberScrollState()
    // 视口宽（horizontalScroll 层的 size）与每个 tab 在**内容坐标系**中的起点/宽度：
    // positionInWindow 随滚动平移，与内容原点（Row 内容层）同源相减即得固定偏移
    var viewportWidth by remember { mutableIntStateOf(0) }
    var contentOriginX by remember { mutableIntStateOf(0) }
    val tabStarts = remember { mutableStateListOf<Int>() }
    val tabWidths = remember { mutableStateListOf<Int>() }
    // reduced motion 判定必须在组合期取（LaunchedEffect 是非 composable 上下文）
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(selectedIndex, tabs.size) {
        if (tabStarts.size < tabs.size || viewportWidth <= 0) return@LaunchedEffect
        val tabStart = tabStarts.getOrNull(selectedIndex) ?: return@LaunchedEffect
        val tabWidth = tabWidths.getOrNull(selectedIndex) ?: return@LaunchedEffect
        // 目标：选中胶囊尽量居中；clamp 到滚动范围（ScrollState 会再兜底一次）
        val target = (tabStart - (viewportWidth - tabWidth) / 2).coerceIn(0, scrollState.maxValue)
        if (reduceMotion) {
            scrollState.scrollTo(target)
        } else {
            scrollState.animateScrollTo(target)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { viewportWidth = it.width }
            .horizontalScroll(scrollState)
            .onGloballyPositioned { contentOriginX = it.positionInWindow().x.toInt() }
            .padding(horizontal = AppDimens.pagePadding),
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            // 设计稿「优惠券」画板实测：选中 = primary 实底 + 白字，未选中 = surface 白底
            // + 1px outline 描边 + textSecondary（与订单列表筛选胶囊同语言）
            // P09 深色板裁定：选中胶囊深色 = primary 深绿 #2D9B6A 底 + 白字（规范 §6.5
            // 「primary 深色 #2D9B6A 选中底/描边不变」；DarkPrimary #5BC894 是主 CTA 按钮深色底，
            // 与选中态需保持层次差）。仅本组件内固定取值，不改全局 DarkPrimary 令牌（消费点过多）
            val isDark = isSystemInDarkTheme()
            val containerColor = selectedColor(
                when {
                    !selected -> AppColors.colorScheme.surface
                    isDark -> Color(0xFF2D9B6A)
                    else -> AppColors.colorScheme.primary
                }
            )
            val labelColor = selectedColor(
                if (selected && isDark) Color.White
                else if (selected) MaterialTheme.colorScheme.onPrimary
                else AppColors.colorScheme.textSecondary
            )
            Box(
                modifier = Modifier
                    // Tab 快速连切是合法操作，关闭防抖；pressable 内置 indication = null（禁 ripple）
                    // 选中态语义（a11y）：TalkBack 播报「已选中」
                    .semantics { this.selected = selected }
                    // 记录该 tab 在内容坐标系中的起点/宽度（窗口 x - 内容原点 x，滚动时两者同平移）
                    .onGloballyPositioned { coordinates ->
                        val start = (coordinates.positionInWindow().x - contentOriginX).toInt()
                        val width = coordinates.size.width
                        if (index < tabStarts.size) {
                            tabStarts[index] = start
                            tabWidths[index] = width
                        } else {
                            tabStarts.add(start)
                            tabWidths.add(width)
                        }
                    }
                    .padding(end = AppDimens.spaceXs)
                    // 选中胶囊 — 规范 §3.x 分段控件为胶囊形，取 radiusFull（999），
                    // 非按钮档 buttonRadius(14)
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(containerColor)
                    .then(
                        if (!selected) Modifier.border(
                            1.dp,
                            AppColors.colorScheme.outline,
                            RoundedCornerShape(AppDimens.radiusFull)
                        )
                        else Modifier
                    )
                    .pressable(
                        onClick = {
                            view.performHaptic(HapticEffect.SELECTION)
                            onSelect(index)
                        },
                        debounce = false,
                    )
                    // 胶囊视觉高 34（画板 .seg3：labelMedium 行盒 16 + 上下 9）；
                    // 垂直 9 → 14 把触控热区抬到 44dp（规范 §2.3，胶囊视觉高度不变——
                    // padding 在 clip/background 之后，只扩热区不改视觉）
                    .padding(horizontal = AppDimens.spaceMd, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    // 画板实测 12/500（captionStrong 档），两态同字重不做字重动画
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                )
            }
        }
    }
}
