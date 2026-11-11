package com.smartwash.feature.mood.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.components.dialogEnterMotion
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.mood.MoodMonth
import com.smartwash.feature.mood.R
import com.smartwash.feature.mood.network.MoodCalendarDayVo
import com.smartwash.feature.mood.ui.MoodErrorState
import com.smartwash.feature.mood.ui.MoodMonthSwitcher
import com.smartwash.feature.mood.ui.moodLevelColor
import com.smartwash.feature.mood.ui.moodLevelLabel
import com.smartwash.feature.mood.ui.moodSnapshotHex
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 月历热图页：Compose Canvas 7×N 网格按 dayKey（yyyy-MM-dd）查 Map 着色（star_book 模式），
 * 无记录灰格、未来格更浅；历史色按契约优先用 color 快照（非法值兜底词典基准 hex）。
 * 点有记录的格子弹出当日详情（word——契约月历不含 note）；‹ › 切月。
 */
@Composable
fun MoodCalendarPage(
    navController: NavHostController,
    viewModel: MoodCalendarViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 点按选中的当日记录（弹窗单一事实来源，null = 关闭）
    var selectedDay by remember { mutableStateOf<MoodCalendarDayVo?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.mood_calendar_title),
            onBack = { navController.navigateUp() },
        )
        when (val state = uiState.loadState) {
            RequestState.Loading -> SkeletonList(rows = 4)
            is RequestState.Error -> MoodErrorState(
                message = state.getMessage(context),
                onRetry = { viewModel.retry() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.pagePadding),
            )
            else -> MoodCalendarContent(
                uiState = uiState,
                onPrev = { viewModel.shiftMonth(-1) },
                onNext = { viewModel.shiftMonth(1) },
                onDayClick = { selectedDay = it },
            )
        }
    }

    selectedDay?.let { day ->
        MoodDayDetailDialog(
            day = day,
            onDismiss = { selectedDay = null },
        )
    }
}

/** 加载成功的内容体：月切换条 + 周表头 + Canvas 网格 + 统计行 */
@Composable
private fun MoodCalendarContent(
    uiState: MoodCalendarViewModel.UiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (MoodCalendarDayVo) -> Unit,
) {
    val ym = MoodMonth.parse(uiState.ym) ?: YearMonth.now()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
    ) {
        MoodMonthSwitcher(
            monthText = stringResource(R.string.mood_month_format, ym.year, ym.monthValue),
            onPrev = onPrev,
            onNext = onNext,
        )

        GroupCard {
            // ---------- 周表头（周一起始） ----------
            MoodWeekHeader()
            MoodCalendarGrid(
                ym = ym,
                records = uiState.records,
                today = LocalDate.now(),
                onDayClick = onDayClick,
            )
        }

        // ---------- 统计与图例说明 ----------
        Column {
            Text(
                text = stringResource(R.string.mood_calendar_recorded_days_format, uiState.records.size),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
            )
            Text(
                text = stringResource(R.string.mood_calendar_day_empty_hint),
                // 功能性图例说明（审查报告 #2②）：textTertiary 仅限禁用/装饰（§10 红线）
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(top = AppDimens.spaceXxs),
            )
        }
    }
}

/** 周表头（一 二 三 四 五 六 日，周一起始与网格 offset 对齐） */
@Composable
private fun MoodWeekHeader() {
    val weekLabels = listOf(
        R.string.mood_week_mon,
        R.string.mood_week_tue,
        R.string.mood_week_wed,
        R.string.mood_week_thu,
        R.string.mood_week_fri,
        R.string.mood_week_sat,
        R.string.mood_week_sun,
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        weekLabels.forEach { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.labelMedium,
                // 功能性列标签（审查报告 #2③）：textTertiary 仅限禁用/装饰（§10 红线）
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(modifier = Modifier.height(AppDimens.spaceXs))
}

/**
 * 单元格高（网格高度 = 行数 × 本值；宽度由屏宽 7 等分）。
 * 48dp（审查报告 #4③）：44dp 跌破 DESIGN-SPEC §3 触控红线，打卡主路径格高提档。
 */
private val CELL_HEIGHT = 48.dp

/**
 * Canvas 7×N 热图网格：每格圆角方块着色 + 日期数字，今日描边；
 * 有记录格带静态细描边（可点 affordance，审查报告 #5）与按下高亮描边；
 * 手势按点击坐标反算格子（行/列 → 日），命中记录才回调（无记录灰格不可点）。
 * 网格级读屏描述挂 Canvas（审查报告 #7）。
 */
@Composable
private fun MoodCalendarGrid(
    ym: YearMonth,
    records: Map<String, MoodCalendarDayVo>,
    today: LocalDate,
    onDayClick: (MoodCalendarDayVo) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    // 绘制用色一次性取 Token（draw 作用域里不能调 @Composable）
    val emptyColor = AppColors.colorScheme.surfaceVariant
    val futureColor = AppColors.colorScheme.groupBackground
    val todayStroke = AppColors.colorScheme.textPrimary
    val dayText = AppColors.colorScheme.textSecondary
    // 未来格数字（审查报告 #9）：textTertiary on groupBackground 浅色仅 1.86:1，改 textSecondary
    val futureText = AppColors.colorScheme.textSecondary
    val cellInk = AppColors.colorScheme.textPrimary
    // 彩色格上的"白字"实际取 surface（浅色=白、深色=深卡底色），保证深浅主题都有对比
    val cellOnColor = MaterialTheme.colorScheme.surface
    // 可点击格静态 affordance 描边（审查报告 #5）：装饰性用途，§10 允许 textTertiary 用于装饰
    val clickableStroke = AppColors.colorScheme.textTertiary

    // 按下的有记录格（0-based 日索引，null = 无按下）；draw 阶段读快照状态自动触发重绘
    var pressedIndex by remember { mutableStateOf<Int?>(null) }

    // 读屏语义（审查报告 #7）：Canvas 手势网格对读屏不可达，先给网格级描述兜底
    val gridDesc = stringResource(R.string.mood_calendar_grid_desc_format, ym.monthValue, records.size)

    val offset = ym.atDay(1).dayOfWeek.value - 1 // 周一 = 0
    val daysInMonth = ym.lengthOfMonth()
    val rows = (offset + daysInMonth + 6) / 7

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val cellWidthPx = with(density) { (maxWidth / 7).toPx() }
        val cellHeightPx = with(density) { CELL_HEIGHT.toPx() }

        Canvas(
            modifier = Modifier
                .size(width = maxWidth, height = CELL_HEIGHT * rows)
                .semantics { contentDescription = gridDesc }
                .pointerInput(ym, records) {
                    detectTapGestures(
                        // 按下即高亮命中格（审查报告 #5：月历格此前无任何按压反馈）。
                        // 静态状态描边非动画，无缩放/位移动画需要 reduced motion 降级（§8）；
                        // finally 复位兜底 pointerInput 重启取消协程的残留高亮
                        onPress = { pos ->
                            dayIndexAt(pos, cellWidthPx, cellHeightPx, offset, daysInMonth)?.let { dayIndex ->
                                if (records.containsKey(ym.atDay(dayIndex + 1).toString())) {
                                    pressedIndex = dayIndex
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        pressedIndex = null
                                    }
                                }
                            }
                        },
                        onTap = { pos ->
                            dayIndexAt(pos, cellWidthPx, cellHeightPx, offset, daysInMonth)?.let { dayIndex ->
                                records[ym.atDay(dayIndex + 1).toString()]?.let(onDayClick)
                            }
                        },
                    )
                },
        ) {
            val gap = AppDimens.spaceXxs.toPx()
            val boxSize = Size(cellWidthPx - gap * 2, cellHeightPx - gap * 2)
            // 热图格圆角取 radiusSmall（规范 §2.3 六档内）
            val corner = CornerRadius(AppDimens.radiusSmall.toPx())
            for (day in 1..daysInMonth) {
                val index = offset + day - 1
                val col = index % 7
                val row = index / 7
                val topLeft = Offset(col * cellWidthPx + gap, row * cellHeightPx + gap)
                val date = ym.atDay(day)
                val record = records[date.toString()]
                val fill = when {
                    record != null -> parseSnapshotColorInDraw(record.color, record.level)
                    date.isAfter(today) -> futureColor
                    else -> emptyColor
                }
                drawRoundRect(color = fill, topLeft = topLeft, size = boxSize, cornerRadius = corner)
                if (record != null) {
                    // 可点击格静态视觉差异（审查报告 #5）：细描边区分「可点开详情」与无记录灰格
                    drawRoundRect(
                        color = clickableStroke,
                        topLeft = topLeft,
                        size = boxSize,
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    if (index == pressedIndex) {
                        // 按下高亮描边（真实按压状态触发，非伪造）
                        drawRoundRect(
                            color = todayStroke,
                            topLeft = topLeft,
                            size = boxSize,
                            cornerRadius = corner,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
                if (date == today) {
                    drawRoundRect(
                        color = todayStroke,
                        topLeft = topLeft,
                        size = boxSize,
                        cornerRadius = corner,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
                // 日期数字（审查报告 #1）：弃用 luminance>0.4f 单阈值——单阈值在「亮底配墨字」
                // 与「暗底配白字」间顾此失彼（浅色 3/5 色级、深色 2/5 色级跌破 4.5:1），改对
                // 墨字与卡底色两候选按 WCAG 实算对比度取高者，双主题全色级达标
                val numberColor = when {
                    record != null -> if (contrastRatio(cellInk, fill) >= contrastRatio(cellOnColor, fill)) {
                        cellInk
                    } else {
                        cellOnColor
                    }
                    date.isAfter(today) -> futureText
                    else -> dayText
                }
                val layout = textMeasurer.measure(
                    text = day.toString(),
                    style = TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (date == today) FontWeight.SemiBold else FontWeight.Normal,
                        color = numberColor,
                        // 数字槽位开 tnum（规范 §2.2：日期数字必须 tabular numbers）
                        fontFeatureSettings = "tnum",
                    ),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = topLeft.x + (boxSize.width - layout.size.width) / 2f,
                        y = topLeft.y + (boxSize.height - layout.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

/** 点击坐标 → 日索引（0-based within month）；越界/空白格返回 null（手势命中判定统一入口） */
private fun dayIndexAt(
    pos: Offset,
    cellWidthPx: Float,
    cellHeightPx: Float,
    offset: Int,
    daysInMonth: Int,
): Int? {
    val col = (pos.x / cellWidthPx).toInt().coerceIn(0, 6)
    val row = (pos.y / cellHeightPx).toInt()
    val dayIndex = row * 7 + col - offset
    return dayIndex.takeIf { it in 0 until daysInMonth }
}

/**
 * WCAG 对比度（审查报告 #1 的实算口径）：正文判色以 ≥4.5:1 为红线，
 * 对两个候选色（墨字 textPrimary / 卡底色 surface）取对比度高者。
 */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * draw 作用域内无法调 @Composable 的 parseSnapshotColor：这里内联同逻辑
 * （快照 hex 解析失败兜底词典浅色基准 hex）。
 */
private fun parseSnapshotColorInDraw(hex: String, level: Int): Color {
    val effective = hex.trim().ifBlank { moodSnapshotHex(level) }
    return runCatching { Color(android.graphics.Color.parseColor(effective)) }
        .getOrDefault(Color(android.graphics.Color.parseColor(moodSnapshotHex(level))))
}

/** 当日详情弹窗：日期 + 心情色点与等级名 + 当日 word（契约月历不含 note，不展示备注） */
@Composable
private fun MoodDayDetailDialog(
    day: MoodCalendarDayVo,
    onDismiss: () -> Unit,
) {
    val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = AppDimens.spaceXl,
                    vertical = AppDimens.spaceLg,
                ),
            ) {
                Text(
                    text = date?.format(
                        DateTimeFormatter.ofPattern(
                            stringResource(R.string.mood_day_date_pattern),
                            Locale.CHINESE,
                        )
                    ) ?: day.date,
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.colorScheme.textPrimary,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(moodLevelColor(day.level)),
                    )
                    Text(
                        text = stringResource(
                            R.string.mood_day_dialog_level_format,
                            moodLevelLabel(day.level),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.padding(start = AppDimens.spaceXs),
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = day.word?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.mood_day_dialog_word_empty),
                    style = MaterialTheme.typography.titleMedium,
                    // 空词占位（审查报告 #10）：18sp 大字号无装饰豁免，textTertiary 改 textSecondary
                    color = if (day.word.isNullOrBlank()) {
                        AppColors.colorScheme.textSecondary
                    } else {
                        AppColors.colorScheme.textPrimary
                    },
                )
            }
        }
    }
}
