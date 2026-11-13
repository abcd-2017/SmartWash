package com.smartwash.feature.divination.ui.page.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.core.liuyao.LiuYaoChart
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.feature.user.api.UserRoute
import kotlinx.coroutines.launch
import com.smartwash.common.ui.components.WaveIcon
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivGoldButton
import com.smartwash.feature.divination.ui.components.DivMiniCard
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.LiurenCourses
import com.smartwash.feature.divination.ui.components.LiurenPan
import com.smartwash.feature.divination.ui.components.LiuYaoPan
import com.smartwash.feature.divination.ui.components.MeiHuaTripleCards
import com.smartwash.feature.divination.ui.components.QimenGrid
import com.smartwash.feature.divination.ui.components.divSerif
import com.smartwash.feature.divination.DivRoute
import com.smartwash.common.ui.theme.DivColors

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
    val state by viewModel.uiState.collectAsState()
    val record = state.record
    val bundle = state.bundle

    DivinationScreen {
        // 颜色必须在 DivinationScreen 之内读取（默认玄墨，包根外读会让声明行/事实卡在浅色系统下不可见）
        val c = DivColors.current
        // audit-23 #1：页头提到 loading/missing/主内容三态之外——加载与缺失期间也保留返回出口
        // （此前 loading 分支整页无返回按钮，仅靠系统手势兜底）；盘面数据就绪前标题/卦印回落
        // 「卦盘」纯文字，与解读页「页头常驻 + 下方三态」同构
        val headerMethod = record?.let { DivMethod.fromId(it.method) }
        DivPageHeader(
            title = if (headerMethod != null && bundle != null) {
                divTitleText(headerMethod, bundle)
            } else {
                stringResource(R.string.div_chart_title)
            },
            onBack = { navController.popBackStack() },
            seal = if (headerMethod != null && bundle != null) {
                divSealText(headerMethod, bundle.liuyao?.name, bundle.meihua?.benName)
            } else {
                null
            },
            // 画板（卦盘双板一致）：平涂深朱印 radius 8 印文 14/700；长卦名标题字距 2
            flatSeal = true,
            titleSpacing = 2.sp,
        )
        if (state.loading) {
            Spacer(Modifier.height(120.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 水波指示（§4.5 附：观象台金色；自带 reduced motion 静止分支）
                WaveIcon(isLoading = true, tint = c.gold, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.div_chart_loading),
                    fontSize = 12.sp,
                    color = c.textTertiary,
                )
            }
            return@DivinationScreen
        }
        if (record == null || bundle == null) {
            Text(
                text = stringResource(R.string.div_chart_missing),
                fontSize = 12.sp,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
            )
            return@DivinationScreen
        }

        val method = DivMethod.fromId(record.method)

        // 统一干支/口径声明行（画板：12/16 字距 2 居中，玄墨金 75% / 宣纸墨 75%）
        Text(
            text = divDeclarationText(method, bundle),
            style = divSerif(12.sp, FontWeight.Normal, 2.sp, lineHeight = 16.sp),
            color = c.goldText.copy(alpha = 0.75f),
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
                    color = c.goldText,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .align(Alignment.CenterHorizontally),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (method) {
            DivMethod.LIU_YAO -> bundle.liuyao?.let { chart ->
                // 画板：盘区无卡片直接铺底，轨道左 28 / 右 20 由盘组件自持
                LiuYaoPan(chart = chart, facts = state.liuYaoFacts, animateEntry = animateEntry)
                // 画板：末行到事实卡间距 24
                Spacer(Modifier.height(24.dp))
                Row(
                    // 画板实测：双迷你卡页边距 20/20、卡距 8（用神卡 20..176、规则卡 184..340）
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DivMiniCard(label = stringResource(R.string.div_liuyao_yongshen), modifier = Modifier.weight(1f)) {
                        val facts = state.liuYaoFacts
                        Text(
                            text = facts?.yongShenLabel ?: "—",
                            style = divSerif(12.sp, FontWeight.Medium, 1.sp, lineHeight = 16.sp),
                            color = c.goldText,
                        )
                        facts?.yongShenNote?.let {
                            Text(
                                text = it,
                                // audit-23 #3：迷你卡注释正文升 14/20（v0.4 §2.2 正文下限，caption 12 仅辅助）
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = c.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        facts?.let { f ->
                            val pos = f.yongShenPosition
                            if (pos != null) {
                                Text(
                                    text = "${f.strength[pos]} · ${listOf("初", "二", "三", "四", "五", "上")[pos - 1]}爻",
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
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
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = c.textPrimary,
                        )
                    }
                }
            }

            DivMethod.MEI_HUA -> bundle.meihua?.let { chart ->
                MeiHuaTripleCards(chart = chart, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                DivCard(Modifier.padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            stringResource(R.string.div_meihua_tiyong),
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            color = c.textSecondary,
                        )
                        Text(
                            text = stringResource(
                                R.string.div_meihua_tiyong_line,
                                chart.movingYao,
                                chart.lower.label, chart.lower.wuXing.label,
                                chart.upper.label, chart.upper.wuXing.label,
                                chart.tiYongRelation.label, chart.tiYongRelation.phrase,
                            ),
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            color = c.textPrimary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            DivMethod.QI_MEN -> bundle.qimen?.let { chart ->
                QimenGrid(chart = chart, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                Row(
                    // 画板实测：双迷你卡页边距 20/20、卡距 8（用神卡 20..176、规则卡 184..340）
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DivMiniCard(label = stringResource(R.string.div_qimen_yongshen), modifier = Modifier.weight(1f)) {
                        Text(
                            text = "日干${chart.siZhu.day.gan.label} · 时干${chart.siZhu.hour.gan.label}",
                            style = divSerif(12.sp, FontWeight.Medium, 0.sp, lineHeight = 16.sp),
                            color = c.goldText,
                        )
                        Text(
                            text = "值符${chart.zhiFuXing}落${gongName(chart.zhiFuLuoGong)} · 值使${chart.zhiShiMen}落${gongName(chart.zhiShiGong)}",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
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
                            // audit-23 #3：迷你卡事实正文升 14/20
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
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
                    fontSize = 12.sp,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        // 画板（2026-11-05 卦盘）：底部双按钮各 156×52 r14（x20/x184、卡距 8），
        // 「解 读」鎏金实底、「已 保 存」14% 金禁用态；取 DivGoldButton 默认（52/r14/15sp）不再单侧覆写
        // 「解 读」入口拦截（2026-11-11 平台零兜底）：先查用户模型配置——未配置 Toast 引导并
        // 跳设置页直达「解读模型」弹层；平台开关关闭仅提示；已配置/查询失败正常进入解读页
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var gateChecking by remember { mutableStateOf(false) }
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DivGoldButton(
                text = stringResource(R.string.div_chart_interpret),
                modifier = Modifier.weight(1f),
                onClick = {
                    // 防重入：拦截查询在途时忽略后续点击（VM 侧 AtomicBoolean 双保险）
                    if (gateChecking) return@DivGoldButton
                    gateChecking = true
                    scope.launch {
                        when (val gate = viewModel.resolveInterpretGate()) {
                            InterpretGate.PROCEED ->
                                navController.navigate(DivRoute.Reading(recordId))
                            InterpretGate.NEED_CONFIG -> {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.div_interpret_need_config),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                navController.navigate(UserRoute.ApiConfig)
                            }
                            InterpretGate.PLATFORM_DISABLED ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.div_interpret_platform_disabled),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            // 上一次查询仍在途（防重入），忽略
                            null -> Unit
                        }
                        gateChecking = false
                    }
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

/** 页头卦印字（2026-11-05 卦盘画板口径：取本卦名末字，泽火革→「革」、水雷屯→「屯」；梅花已同口径） */
private fun divSealText(method: DivMethod, liuYaoName: String?, meiHuaName: String?): String = when (method) {
    DivMethod.LIU_YAO -> liuYaoName?.takeLast(1) ?: "六"
    DivMethod.MEI_HUA -> meiHuaName?.takeLast(1) ?: "梅"
    DivMethod.QI_MEN -> "奇"
    DivMethod.LIU_REN -> "壬"
}

private fun divTitleText(method: DivMethod, bundle: com.smartwash.feature.divination.data.DivChartBundle): String = when (method) {
    DivMethod.LIU_YAO -> bundle.liuyao?.let { ch ->
        ch.bianName?.let { bian -> "${ch.name} 之 $bian" } ?: ch.name
    } ?: "六爻"

    DivMethod.MEI_HUA -> bundle.meihua?.let { "${it.benName} · ${it.movingYao}爻动" } ?: "梅花"
    DivMethod.QI_MEN -> "奇门遁甲"
    DivMethod.LIU_REN -> "大六壬"
}

private fun divDeclarationText(method: DivMethod, bundle: com.smartwash.feature.divination.data.DivChartBundle): String = when (method) {
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
