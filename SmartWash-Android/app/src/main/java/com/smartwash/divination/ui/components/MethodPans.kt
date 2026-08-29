package com.smartwash.divination.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.divination.core.liuren.LiuRenChart
import com.smartwash.divination.core.meihua.MeiHuaChart
import com.smartwash.divination.core.qimen.QiMenChart
import com.smartwash.ui.theme.DivColors

/**
 * 梅花本互变三卡 / 奇门九宫格 / 大六壬式盘（原型 ChartMeihua / ChartQimen / ChartLiuren）。
 */

private val GONG_NAMES = mapOf(
    1 to "坎一", 2 to "坤二", 3 to "震三", 4 to "巽四",
    5 to "中五", 6 to "乾六", 7 to "兑七", 8 to "艮八", 9 to "离九",
)

/** 梅花三卡（本/互/变）+ 顶部色条（本=鎏金、互=弱文本、变=青玉） */
@Composable
fun MeiHuaTripleCards(chart: MeiHuaChart, modifier: Modifier = Modifier) {
    val c = DivColors.current
    data class CardData(val label: String, val name: String, val mark: String, val bar: Color, val chip: String, val chipGold: Boolean)

    val cards = listOf(
        CardData("本卦 · 体", chart.benName, chart.benMark, c.gold, "${chart.ti.label}${chart.ti.wuXing.label}体 · ${chart.yong.label}${chart.yong.wuXing.label}用", true),
        CardData("互卦 · 过程", chart.huName, chart.huMark, c.textTertiary, "${com.smartwash.divination.core.Trigram.fromBinary(chart.huMark.substring(3, 6)).label}${chart.huWuXing.label} · 用", false),
        CardData("变卦 · 结局", chart.bianName, chart.bianMark, c.jade, "${com.smartwash.divination.core.Trigram.fromBinary(chart.bianMark.substring(3, 6)).label}${chart.bianWuXing.label} · 用", false),
    )

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.forEach { card ->
            DivCard(Modifier.weight(1f), background = c.surface, borderColor = c.goldHair) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(card.bar)
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(card.label, fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    Text(card.name, style = divSerif(17.sp, FontWeight.SemiBold, 2.sp), color = c.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    // 爻象：自上爻到初爻显示
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        (5 downTo 0).forEach { index ->
                            val yang = card.mark[index] == '1'
                            if (yang) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(c.yaoTop)
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.yaoTop))
                                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.yaoTop))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (card.chipGold) {
                        Text(
                            card.chip,
                            fontSize = 10.sp,
                            color = c.gold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.goldSoft)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    } else {
                        Text(
                            card.chip,
                            fontSize = 10.sp,
                            color = c.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surface2)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 奇门九宫格：宫内五层（八神/九星/天盘干/八门+地盘干/暗干角标）+ 空亡驿马 badge */
@Composable
fun QimenGrid(chart: QiMenChart, modifier: Modifier = Modifier) {
    val c = DivColors.current
    // 九宫布局顺序：巽四 离九 坤二 / 震三 中五 兑七 / 艮八 坎一 乾六
    val layout = listOf(
        listOf(4, 9, 2),
        listOf(3, 5, 7),
        listOf(8, 1, 6),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        layout.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { gong ->
                    PalaceCell(
                        chart = chart,
                        gong = gong,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PalaceCell(chart: QiMenChart, gong: Int, modifier: Modifier = Modifier) {
    val c = DivColors.current
    val isCenter = gong == 5
    Box(
        modifier
            .height(116.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCenter) c.surface2 else c.surface)
            .background(c.goldHair, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        if (isCenter) {
            // 中宫寄坤二宫
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("二 · 五", style = divSerif(17.sp, FontWeight.SemiBold, 3.sp), color = c.gold)
                Spacer(Modifier.height(6.dp))
                Text("寄 坤 二 宫", fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary)
            }
        } else {
            Column {
                // 八神（鎏金）
                Text(chart.baShen[gong] ?: "", fontSize = 10.sp, letterSpacing = 1.sp, color = c.gold)
                Spacer(Modifier.height(4.dp))
                // 九星
                Text(chart.jiuXing[gong] ?: "", style = divSerif(13.sp, FontWeight.Medium, 1.sp), color = c.textPrimary)
                // 天盘干（鎏金大字）
                Text(chart.tianPan[gong] ?: "", style = divSerif(15.sp, FontWeight.SemiBold, 0.sp), color = c.gold)
                Spacer(Modifier.height(4.dp))
                // 八门 + 地盘干
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chart.baMen[gong] ?: "", fontSize = 10.5.sp, color = c.textSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(chart.diPan[gong] ?: "", style = divSerif(12.5.sp, FontWeight.Normal, 0.sp), color = c.textSecondary)
                }
            }
            // 暗干角标
            Text(
                text = "暗·${chart.anGan[gong] ?: ""}",
                fontSize = 9.sp,
                color = c.textTertiary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .alpha(0.75f),
            )
            // 空亡 / 驿马 badge
            val badges = buildList {
                if (gong in chart.kongWangGong) add("空亡")
                if (gong == chart.maGong) add("驿马")
            }
            if (badges.isNotEmpty()) {
                Text(
                    text = badges.joinToString("·"),
                    fontSize = 9.sp,
                    color = c.sealHi,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.hair)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        // 宫名
        Text(
            text = GONG_NAMES[gong] ?: "",
            fontSize = 9.sp,
            color = c.textTertiary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .alpha(0.85f),
        )
    }
}

/** 大六壬式盘：天地盘双环（外环天盘缓转、内环地盘固定 + 四正刻线 + 天心金点） */
@Composable
fun LiurenPan(chart: LiuRenChart, modifier: Modifier = Modifier) {
    val c = DivColors.current
    val textMeasurer = rememberTextMeasurer()
    androidx.compose.foundation.Canvas(modifier.size(228.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val rOuter = size.minDimension / 2f
        val rInner = rOuter * 0.54f

        // 外环天盘 / 内环地盘
        drawCircle(color = c.goldLine, radius = rOuter - 1.dp.toPx() / 2, center = center, style = Stroke(1.2.dp.toPx()))
        drawCircle(color = c.goldHair, radius = rOuter * 0.72f, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(color = c.goldLine, radius = rInner, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(color = c.surface2, radius = rInner - 1.dp.toPx(), center = center)

        val zhi = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
        val tianPanByDi = chart.tianPan // 地盘支 → 天盘支

        // 外环（天盘支）：子在上，顺布
        zhi.forEachIndexed { index, label ->
            // 该天盘支由哪个地盘支转来：找 tianPan 逆映射
            val diZhi = tianPanByDi.entries.firstOrNull { it.value == label }?.key ?: return@forEachIndexed
            val angle = (zhi.indexOf(diZhi) * 30.0) - 90.0
            val rad = Math.toRadians(angle)
            val pos = center + Offset(
                ((rOuter * 0.84f) * kotlin.math.cos(rad)).toFloat(),
                ((rOuter * 0.84f) * kotlin.math.sin(rad)).toFloat(),
            )
            val measured = textMeasurer.measure(
                label,
                TextStyle(fontFamily = FontFamily.Serif, fontSize = 13.sp, color = c.gold),
            )
            drawText(measured, topLeft = pos - measured.size.center.toOffset())
        }
        // 内环（地盘支，固定）
        zhi.forEachIndexed { index, label ->
            val angle = Math.toRadians((index * 30.0) - 90.0)
            val rad = angle
            val pos = center + Offset(
                ((rInner * 0.72f) * kotlin.math.cos(rad)).toFloat(),
                ((rInner * 0.72f) * kotlin.math.sin(rad)).toFloat(),
            )
            val measured = textMeasurer.measure(
                label,
                TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, color = c.textTertiary),
            )
            drawText(measured, topLeft = pos - measured.size.center.toOffset())
        }
        // 四正刻线
        listOf(0f, 90f, 180f, 270f).forEach { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val from = center + Offset(
                (rInner * kotlin.math.cos(rad)).toFloat(),
                (rInner * kotlin.math.sin(rad)).toFloat(),
            )
            val to = center + Offset(
                (rOuter * 0.72f * kotlin.math.cos(rad)).toFloat(),
                (rOuter * 0.72f * kotlin.math.sin(rad)).toFloat(),
            )
            drawLine(c.goldHair, from, to, strokeWidth = 1.dp.toPx())
        }
        // 天心
        drawCircle(color = c.gold, radius = 3.5.dp.toPx(), center = center)
    }
}

/** 四课 / 三传行卡 */
@Composable
fun LiurenCourses(chart: LiuRenChart, modifier: Modifier = Modifier) {
    val c = DivColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 四课
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            chart.courses.forEach { course ->
                DivCard(Modifier.weight(1f), borderColor = c.goldHair) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(course.label, fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary)
                        Spacer(Modifier.height(8.dp))
                        // 上神在上，下神在下
                        Text(course.upper, style = divSerif(15.sp, FontWeight.SemiBold, 0.sp), color = c.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            course.lower,
                            style = divSerif(15.sp, FontWeight.SemiBold, 0.sp),
                            color = c.gold,
                        )
                    }
                }
            }
        }
        // 三传（或暂不支持说明）
        if (chart.sanChuan != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val labels = listOf("初传", "中传", "末传")
                labels.forEachIndexed { index, label ->
                    DivCard(Modifier.weight(1f), borderColor = c.goldLine) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(label, fontSize = 9.5.sp, letterSpacing = 2.sp, color = c.textTertiary)
                            Spacer(Modifier.height(8.dp))
                            Text(chart.sanChuan[index], style = divSerif(15.sp, FontWeight.SemiBold, 0.sp), color = c.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                chart.sanChuanJiang?.getOrNull(index) ?: "",
                                fontSize = 9.5.sp,
                                letterSpacing = 1.sp,
                                color = c.gold.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}
