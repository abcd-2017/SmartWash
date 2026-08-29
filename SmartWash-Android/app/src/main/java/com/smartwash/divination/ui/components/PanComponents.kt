package com.smartwash.divination.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.divination.core.liuyao.LiuYaoChart
import com.smartwash.divination.core.liuyao.LiuYaoFacts
import com.smartwash.ui.theme.DivColors

/**
 * 六爻装卦行（原型 ChartLiuyao）：六神色点 / 宣纸(墨)爻线 / 动爻○× / 六亲干支 / 世应。
 * 支持自初爻向上 draw-on 入场（阳爻自中心向两端展开、阴爻两条同展）。
 */

/** 爻线：阳爻整条、阴爻两段；progress 0..1 控制展开 */
@Composable
private fun YaoBar(
    yang: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    if (yang) {
        Box(
            modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(4.5.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(9.dp)
                    .background(
                        Brush.horizontalGradient(listOf(c.yaoTop, c.yaoBottom)),
                        RoundedCornerShape(4.5.dp),
                    )
            )
        }
    } else {
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(4.5.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(9.dp)
                            .background(
                                Brush.horizontalGradient(listOf(c.yaoTop, c.yaoBottom)),
                                RoundedCornerShape(4.5.dp),
                            )
                    )
                }
            }
        }
    }
}

/** 六爻装卦一行；[revealed] 控制本爻 draw-on 进度（1 = 完整） */
@Composable
fun LiuYaoRow(
    row: com.smartwash.divination.core.liuyao.LiuYaoLine,
    revealed: Float,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val spiritIndex = listOf("青龙", "朱雀", "勾陈", "螣蛇", "白虎", "玄武").indexOf(row.liuShen)
    val spiritColor = if (spiritIndex >= 0) c.spirits[spiritIndex] else c.textTertiary

    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 六神
        Row(Modifier.width(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(spiritColor, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = row.liuShen, fontSize = 10.5.sp, color = c.textSecondary, maxLines = 1)
        }
        // 爻象（阳爻整条 / 阴爻两段 + 动爻标记）
        Box(Modifier.weight(1f).padding(end = 10.dp)) {
            YaoBar(yang = row.yao.isYang, progress = revealed)
            if (row.yao.isMoving && revealed >= 1f) {
                Text(
                    text = if (row.yao.code == 3) "○" else "×",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    color = if (row.yao.code == 3) c.gold else c.sealHi,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(start = 4.dp),
                )
            }
        }
        // 六亲 · 干支
        Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(text = row.liuQin, style = divSerif(12.5.sp, FontWeight.Medium, 1.sp), color = c.textPrimary)
            Text(
                text = row.ganZhi + row.zhiWuXing.label,
                style = divSerif(10.5.sp, FontWeight.Normal, 0.sp),
                color = c.gold.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        // 世应徽标：世鎏金 / 应青玉
        Text(
            text = row.shiYing ?: "",
            fontSize = 9.5.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            color = if (row.shiYing == "世") c.gold else c.jade,
            modifier = Modifier.width(20.dp).padding(start = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 六爻装卦表：自上爻到初爻渲染；[animateEntry] 时按"自初爻向上 draw-on"逐条展开（间隔 60ms）。
 * 任意点按"求解读"等交互可跳过（进度直接置满）。
 */
@Composable
fun LiuYaoPan(
    chart: LiuYaoChart,
    facts: LiuYaoFacts?,
    animateEntry: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    // 自初爻(index 0)向上依次 reveal
    val progress = remember { List(6) { androidx.compose.animation.core.Animatable(if (animateEntry) 0f else 1f) } }
    var skipped by remember { mutableStateOf(!animateEntry) }

    LaunchedEffect(chart) {
        if (skipped) {
            progress.forEach { anim -> if (anim.value < 1f) anim.snapTo(1f) }
            return@LaunchedEffect
        }
        progress.forEach { anim ->
            anim.animateTo(1f, animationSpec = tween(240))
        }
    }

    DivCard(modifier.clickable { skipped = true }) {
        Column(Modifier.padding(14.dp)) {
            // 表头
            Row(Modifier.padding(bottom = 4.dp)) {
                Text("六神", fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary, modifier = Modifier.width(52.dp))
                Text("爻象", fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary, modifier = Modifier.weight(1f))
                Text("六亲 · 干支", fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary)
                Text("世应", fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary, modifier = Modifier.width(20.dp))
            }
            // 自上爻到初爻显示（rows index 5 → 0）
            (5 downTo 0).forEach { index ->
                val row = chart.rows[index]
                val revealed by progress[index].asState()
                LiuYaoRow(row = row, revealed = revealed)
            }
            // 类型脚注：六冲卦 → 之 六合 · 四爻动化子孙
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        if (chart.type.isNotEmpty()) append(chart.type).append("卦")
                        chart.bianName?.let { append(" 之 ").append(it) }
                        if (chart.movingPositions.isNotEmpty()) {
                            append(" · ").append(chart.movingPositions.joinToString("、") { "${it}爻动" })
                        }
                    },
                    fontSize = 11.sp,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = chart.bianType?.let { "变卦$it" } ?: "",
                    style = divSerif(11.sp, FontWeight.SemiBold, 1.sp),
                    color = c.textPrimary,
                )
            }
        }
    }
}

/** 事实小卡（用神 / 规则事实） */
@Composable
fun DivMiniCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = DivColors.current
    DivCard(modifier, borderColor = c.goldHair) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary)
            content()
        }
    }
}
