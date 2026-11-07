package com.smartwash.feature.divination.ui.page.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.network.DivMonthCountVo
import com.smartwash.feature.divination.network.DivNameCountVo
import com.smartwash.feature.divination.network.DivStatsVo
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivGoldButton
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.DivSectionTitle
import com.smartwash.feature.divination.ui.components.DivSeal
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.model.RequestState
import kotlin.math.roundToInt

/**
 * 盘感页（占卜闭环三）：卦例统计复盘。
 * 数字卡（总卦数/已反馈/应验率）+ 三张图表全部 Canvas 自绘（清氧规范：禁图表库，
 * 术数/类别为横向条形，月度趋势为纵向条形），零新增依赖。
 */
@Composable
fun DivStatsPage(
    navController: NavHostController,
    viewModel: DivStatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    val state by viewModel.state.collectAsState()

    DivinationScreen {
        // 颜色必须在 DivinationScreen 之内读取（默认玄墨，包根外读会让浅色系统下文案不可见）
        val c = DivColors.current
        DivPageHeader(
            title = stringResource(R.string.div_loop_stats_title),
            onBack = { navController.popBackStack() },
            alignStart = true,
        )

        when (val s = state) {
            RequestState.Idle, RequestState.Loading -> StatsHint(stringResource(R.string.div_loop_stats_loading))
            is RequestState.Error -> Column(Modifier.fillMaxWidth()) {
                StatsHint(s.getMessage(LocalContext.current))
                Spacer(Modifier.height(16.dp))
                DivGoldButton(
                    text = stringResource(R.string.div_loop_stats_retry),
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .fillMaxWidth(),
                    onClick = viewModel::load,
                )
            }
            RequestState.Success -> {
                val data = stats
                if (data == null) {
                    StatsHint(stringResource(R.string.div_loop_stats_empty))
                } else {
                    // 顶部数字卡（画板：三卡并排，卡内数字衬线 22 / 标签 12）
                    Row(
                        Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatsNumberCard(stringResource(R.string.div_loop_stats_total), data.totalRecords, Modifier.weight(1f))
                        StatsNumberCard(stringResource(R.string.div_loop_stats_feedback), data.feedbackTotal, Modifier.weight(1f))
                        StatsNumberCard(
                            stringResource(R.string.div_loop_stats_verify),
                            // 契约 verifyRate 为比例 0-1；>1 视为后端已给百分数，防御性兼容
                            ((if (data.verifyRate > 1.0) data.verifyRate else data.verifyRate * 100).roundToInt()),
                            Modifier.weight(1f),
                            suffix = "%",
                        )
                    }

                    // 术数分布（横向条形：名称 + 条 + 数量）
                    Spacer(Modifier.height(8.dp))
                    DivSectionTitle(title = stringResource(R.string.div_loop_stats_method_title))
                    DivCard(Modifier.padding(horizontal = 20.dp)) {
                        BarChart(
                            rows = data.methodCounts,
                            emptyText = stringResource(R.string.div_loop_stats_empty_chart),
                            modifier = Modifier.padding(14.dp),
                        )
                    }

                    // 月度趋势（纵向条形：单 Canvas 画多柱 + 底部月标签行）
                    Spacer(Modifier.height(8.dp))
                    DivSectionTitle(title = stringResource(R.string.div_loop_stats_month_title))
                    DivCard(Modifier.padding(horizontal = 20.dp)) {
                        MonthTrendChart(
                            trend = data.monthTrend,
                            emptyText = stringResource(R.string.div_loop_stats_empty_chart),
                            modifier = Modifier.padding(14.dp),
                        )
                    }

                    // 所问领域分布（横向条形）
                    Spacer(Modifier.height(8.dp))
                    DivSectionTitle(title = stringResource(R.string.div_loop_stats_category_title))
                    DivCard(Modifier.padding(horizontal = 20.dp)) {
                        BarChart(
                            rows = data.categoryCounts,
                            emptyText = stringResource(R.string.div_loop_stats_empty_chart),
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        }

        DivFooterNote(text = stringResource(R.string.div_loop_stats_note))
    }
}

/** 状态提示（加载 / 错误 / 空态共用容器） */
@Composable
private fun StatsHint(text: String) {
    val c = DivColors.current
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DivSeal("盘", size = 44.dp, fontSize = 22.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}

/** 数字卡：大数字（衬线）+ 标签；suffix 承载百分号 */
@Composable
private fun StatsNumberCard(label: String, value: Int, modifier: Modifier = Modifier, suffix: String = "") {
    val c = DivColors.current
    DivCard(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value.toString(),
                    style = com.smartwash.feature.divination.ui.components.divSerif(22.sp, FontWeight.Bold, 0.sp),
                    color = c.textPrimary,
                )
                if (suffix.isNotEmpty()) {
                    Text(text = suffix, fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.padding(start = 2.dp))
                }
            }
            Text(
                text = label,
                fontSize = 12.sp,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 横向条形图（Canvas 自绘）：名称 + 金条（gold14 轨道 + gold 实条）+ 数量 */
@Composable
private fun BarChart(rows: List<DivNameCountVo>, emptyText: String, modifier: Modifier = Modifier) {
    val c = DivColors.current
    if (rows.isEmpty()) {
        Text(
            text = emptyText,
            fontSize = 12.sp,
            color = c.textTertiary,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        return
    }
    val maxCount = remember(rows) { rows.maxOf { it.count }.coerceAtLeast(1) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            val fraction = (row.count.toFloat() / maxCount).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name.orEmpty(),
                    fontSize = 12.sp,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(52.dp),
                )
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(12.dp),
                ) {
                    // 轨道满宽；实条按占比绘制（0 占比不画，避免 0 宽圆角闪烁）
                    drawRoundRect(
                        color = c.gold14,
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                    if (fraction > 0f) {
                        drawRoundRect(
                            color = c.gold,
                            topLeft = Offset.Zero,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                        )
                    }
                }
                Text(
                    text = row.count.toString(),
                    fontSize = 12.sp,
                    color = c.textPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(34.dp),
                )
            }
        }
    }
}

/** 纵向月度趋势（Canvas 自绘多柱）：柱高按占比，底部标签行与柱一一对应 */
@Composable
private fun MonthTrendChart(trend: List<DivMonthCountVo>, emptyText: String, modifier: Modifier = Modifier) {
    val c = DivColors.current
    if (trend.isEmpty()) {
        Text(
            text = emptyText,
            fontSize = 12.sp,
            color = c.textTertiary,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        return
    }
    val maxCount = remember(trend) { trend.maxOf { it.count }.coerceAtLeast(1) }
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val slot = size.width / trend.size
            val barWidth = slot * 0.42f
            trend.forEachIndexed { index, item ->
                if (item.count <= 0) return@forEachIndexed
                val barHeight = size.height * (item.count.toFloat() / maxCount)
                drawRoundRect(
                    color = c.gold,
                    topLeft = Offset(
                        x = slot * index + (slot - barWidth) / 2f,
                        y = size.height - barHeight,
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            trend.forEach { item ->
                Text(
                    text = shortMonth(item.month),
                    fontSize = 10.sp,
                    color = c.textTertiary,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 月标签（"2026-11" → "11月"；非标准格式透传前 7 字符兜底） */
private fun shortMonth(month: String?): String {
    val raw = month ?: return ""
    return if (raw.length >= 7 && raw[4] == '-') "${raw.substring(5, 7)}月" else raw.take(7)
}
