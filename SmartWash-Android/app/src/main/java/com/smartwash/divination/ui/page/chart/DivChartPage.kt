package com.smartwash.divination.ui.page.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.core.liuyao.LiuYaoChart
import com.smartwash.divination.ui.DivinationScreen
import com.smartwash.divination.ui.components.DivCard
import com.smartwash.divination.ui.components.DivFooterNote
import com.smartwash.divination.ui.components.DivGoldButton
import com.smartwash.divination.ui.components.DivMiniCard
import com.smartwash.divination.ui.components.DivPageHeader
import com.smartwash.divination.ui.components.LiurenCourses
import com.smartwash.divination.ui.components.LiurenPan
import com.smartwash.divination.ui.components.LiuYaoPan
import com.smartwash.divination.ui.components.MeiHuaTripleCards
import com.smartwash.divination.ui.components.QimenGrid
import com.smartwash.divination.ui.components.divSerif
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.DivColors

/**
 * 卦盘页（原型 ChartLiuyao/ChartMeihua/ChartQimen/ChartLiuren）：
 * 头部统一印章卦名 + 干支/口径声明，底部统一「求解读」「存入卦历」。
 * 盘面数据一律来自 Room 原盘原时刻，不因当前日期重算。
 */
@Composable
fun DivChartPage(
    navController: NavHostController,
    recordId: Long,
    animateEntry: Boolean,
    viewModel: DivChartViewModel = hiltViewModel(),
) {
    val c = DivColors.current
    val state by viewModel.uiState.collectAsState()
    val record = state.record
    val bundle = state.bundle

    DivinationScreen {
        if (state.loading) {
            Spacer(Modifier.height(120.dp))
            Text(
                text = stringResource(R.string.div_chart_loading),
                fontSize = 12.sp,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@DivinationScreen
        }
        if (record == null || bundle == null) {
            DivPageHeader(title = stringResource(R.string.div_chart_title), onBack = { navController.popBackStack() })
            Text(
                text = stringResource(R.string.div_chart_missing),
                fontSize = 13.sp,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
            )
            return@DivinationScreen
        }

        val method = DivMethod.fromId(record.method)
        val sealText = divSealText(method, bundle.liuyao?.name, bundle.meihua?.benName)

        DivPageHeader(
            title = divTitleText(method, bundle),
            onBack = { navController.popBackStack() },
            seal = sealText,
        )

        // 统一干支/口径声明行
        Text(
            text = divDeclarationText(method, bundle),
            style = divSerif(11.sp, FontWeight.Normal, 2.sp),
            color = c.gold.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
        // 奇门口径徽标
        if (method == DivMethod.QI_MEN) {
            bundle.qimen?.let { qimen ->
                Text(
                    text = qimen.juDeclaration { n -> cnJuNumber(n) },
                    style = divSerif(12.sp, FontWeight.Medium, 3.sp),
                    color = c.gold,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .align(Alignment.CenterHorizontally),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (method) {
            DivMethod.LIU_YAO -> bundle.liuyao?.let { chart ->
                LiuYaoPan(chart = chart, facts = state.liuYaoFacts, animateEntry = animateEntry, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DivMiniCard(label = stringResource(R.string.div_liuyao_yongshen), modifier = Modifier.weight(1f)) {
                        val facts = state.liuYaoFacts
                        Text(
                            text = facts?.yongShenLabel ?: "—",
                            style = divSerif(12.5.sp, FontWeight.Medium, 1.sp),
                            color = c.gold,
                        )
                        facts?.yongShenNote?.let {
                            Text(text = it, fontSize = 10.sp, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                        facts?.let { f ->
                            val pos = f.yongShenPosition
                            if (pos != null) {
                                Text(
                                    text = "${f.strength[pos]} · ${listOf("初", "二", "三", "四", "五", "上")[pos - 1]}爻",
                                    fontSize = 10.sp,
                                    color = c.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    DivMiniCard(label = stringResource(R.string.div_liuyao_facts), modifier = Modifier.weight(1f)) {
                        val facts = state.liuYaoFacts
                        Text(
                            text = buildString {
                                if (facts != null) {
                                    if (facts.xunKongYao.isNotEmpty()) append("旬空${facts.xunKongYao.joinToString("、") { "${it}爻" }}\n")
                                    if (facts.yuePo.isNotEmpty()) append("月破${facts.yuePo.joinToString("、") { "${it}爻" }}\n")
                                    if (facts.riChong.isNotEmpty()) append("日冲${facts.riChong.joinToString("、") { "${it}爻" }}\n")
                                    facts.huiTou.forEach { (pos, rel) ->
                                        append("${pos}爻$rel\n")
                                    }
                                    if (isEmpty()) append("未见动变冲空之象")
                                }
                            },
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            color = c.textPrimary,
                        )
                    }
                }
            }

            DivMethod.MEI_HUA -> bundle.meihua?.let { chart ->
                MeiHuaTripleCards(chart = chart, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                DivCard(Modifier.padding(horizontal = 20.dp), borderColor = c.goldHair) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            stringResource(R.string.div_meihua_tiyong),
                            fontSize = 9.5.sp,
                            letterSpacing = 2.sp,
                            color = c.textTertiary,
                        )
                        Text(
                            text = stringResource(
                                R.string.div_meihua_tiyong_line,
                                chart.movingYao,
                                chart.lower.label, chart.lower.wuXing.label,
                                chart.upper.label, chart.upper.wuXing.label,
                                chart.tiYongRelation.label, chart.tiYongRelation.phrase,
                            ),
                            fontSize = 11.sp,
                            lineHeight = 19.sp,
                            color = c.textPrimary,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }

            DivMethod.QI_MEN -> bundle.qimen?.let { chart ->
                QimenGrid(chart = chart, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DivMiniCard(label = stringResource(R.string.div_qimen_yongshen), modifier = Modifier.weight(1f)) {
                        Text(
                            text = "日干${chart.siZhu.day.gan.label} · 时干${chart.siZhu.hour.gan.label}",
                            style = divSerif(12.sp, FontWeight.Medium, 0.sp),
                            color = c.gold,
                        )
                        Text(
                            text = "值符${chart.zhiFuXing}落${gongName(chart.zhiFuLuoGong)} · 值使${chart.zhiShiMen}落${gongName(chart.zhiShiGong)}",
                            fontSize = 10.sp,
                            lineHeight = 16.sp,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    DivMiniCard(label = stringResource(R.string.div_qimen_facts), modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildString {
                                append("空亡${chart.kongWangZhi.joinToString("")}落${chart.kongWangGong.joinToString("、") { gongName(it) }}\n")
                                append("驿马${chart.maZhi}在${gongName(chart.maGong)}")
                            },
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            color = c.textPrimary,
                        )
                    }
                }
            }

            DivMethod.LIU_REN -> bundle.liuren?.let { chart ->
                LiurenPan(chart = chart, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                LiurenCourses(chart = chart, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = chart.unsupportedNote
                        ?: stringResource(R.string.div_liuren_keti, chart.keTi ?: "", chart.jiuZongMen ?: ""),
                    fontSize = 11.sp,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DivGoldButton(
                text = stringResource(R.string.div_chart_interpret),
                modifier = Modifier.weight(1f),
                onClick = {
                    navController.navigate("${PageConstant.DivReading.text}/$recordId")
                },
            )
            DivGoldButton(
                text = stringResource(R.string.div_chart_saved),
                enabled = false,
                modifier = Modifier.weight(1f),
                onClick = {},
            )
        }

        DivFooterNote(text = stringResource(R.string.div_disclaimer_footer))
    }
}

private fun gongName(gong: Int): String = when (gong) {
    1 -> "坎一"; 2 -> "坤二"; 3 -> "震三"; 4 -> "巽四"; 5 -> "中五"; 6 -> "乾六"; 7 -> "兑七"; 8 -> "艮八"; 9 -> "离九"
    else -> "$gong 宫"
}

private fun cnJuNumber(n: Int): String = when (n) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "七"; 8 -> "八"; 9 -> "九"
    else -> n.toString()
}

private fun divSealText(method: DivMethod, liuYaoName: String?, meiHuaName: String?): String = when (method) {
    DivMethod.LIU_YAO -> liuYaoName?.take(1) ?: "卦"
    DivMethod.MEI_HUA -> meiHuaName?.takeLast(1) ?: "梅"
    DivMethod.QI_MEN -> "奇"
    DivMethod.LIU_REN -> "壬"
}

private fun divTitleText(method: DivMethod, bundle: com.smartwash.divination.data.DivChartBundle): String = when (method) {
    DivMethod.LIU_YAO -> bundle.liuyao?.let { ch ->
        ch.bianName?.let { bian -> "${ch.name} 之 $bian" } ?: ch.name
    } ?: "六爻"

    DivMethod.MEI_HUA -> bundle.meihua?.let { "${it.benName} · ${it.movingYao}爻动" } ?: "梅花"
    DivMethod.QI_MEN -> "奇门遁甲"
    DivMethod.LIU_REN -> "大六壬"
}

private fun divDeclarationText(method: DivMethod, bundle: com.smartwash.divination.data.DivChartBundle): String = when (method) {
    DivMethod.LIU_YAO -> bundle.liuyao?.let { ch ->
        "${ch.siZhu.labels().joinToString(" ")} · 旬空 ${ch.xunKong} · ${ch.gongDeclaration()}"
    } ?: ""

    DivMethod.MEI_HUA -> bundle.meihua?.derivation ?: ""

    DivMethod.QI_MEN -> bundle.qimen?.let { ch ->
        "${ch.siZhu.labels().joinToString(" ")} · ${ch.jieQiName}后 · 旬首${ch.xunShouYi}"
    } ?: ""

    DivMethod.LIU_REN -> bundle.liuren?.let { ch ->
        "${ch.dayGanZhi}日 ${ch.hourGanZhi}时 · 月将 ${ch.yueJiang}（${ch.yueJiangName}）· ${if (ch.isDay) "昼占" else "夜占"}贵${ch.guiRen}"
    } ?: ""
}
