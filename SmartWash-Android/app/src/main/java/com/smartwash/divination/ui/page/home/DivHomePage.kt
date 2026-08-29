package com.smartwash.divination.ui.page.home

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.ui.components.CompassDial
import com.smartwash.divination.ui.components.DivCard
import com.smartwash.divination.ui.components.DivFooterNote
import com.smartwash.divination.ui.components.DivSectionTitle
import com.smartwash.divination.ui.components.DivSeal
import com.smartwash.divination.ui.components.TianXiangHeader
import com.smartwash.divination.ui.components.divSerif
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.DivColors
import com.smartwash.utils.HapticEffect
import com.smartwash.utils.currentView
import com.smartwash.utils.performHaptic
import com.smartwash.utils.pressScale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 观象台首页（原型 DivHome）：干支天象头 + 缓转罗盘 + 四术玉牌（竖排名）+ 今日一签 + 案卷。
 */
@Composable
fun DivHomePage(
    navController: NavHostController,
    viewModel: DivHomeViewModel = hiltViewModel(),
) {
    val c = DivColors.current
    val uiState by viewModel.uiState.collectAsState()
    val records by viewModel.recentRecords.collectAsState()
    val view = currentView()

    Column {
        TianXiangHeader(
            text = uiState.tianXiang.ifEmpty { "—" },
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.div_title),
                style = divSerif(26.sp, FontWeight.SemiBold, 4.sp),
                color = c.textPrimary,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = stringResource(R.string.div_home_xunkong_format, uiState.xunKong.ifEmpty { "—" }),
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = c.textTertiary,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        // 罗盘
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompassDial(modifier = Modifier.size(112.dp))
        }

        // 四术玉牌
        val methods = listOf(
            Triple(DivMethod.LIU_YAO, R.string.div_method_liuyao_name, R.string.div_method_liuyao_desc),
            Triple(DivMethod.MEI_HUA, R.string.div_method_meihua_name, R.string.div_method_meihua_desc),
            Triple(DivMethod.QI_MEN, R.string.div_method_qimen_name, R.string.div_method_qimen_desc),
            Triple(DivMethod.LIU_REN, R.string.div_method_liuren_name, R.string.div_method_liuren_desc),
        )
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            methods.chunked(2).forEach { rowMethods ->
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    rowMethods.forEach { (method, nameRes, descRes) ->
                        JadeTile(
                            name = stringResource(nameRes),
                            desc = stringResource(descRes),
                            tag = if (method == DivMethod.LIU_YAO) stringResource(R.string.div_method_liuyao_tag) else null,
                            onClick = {
                                view.performHaptic(HapticEffect.LIGHT)
                                navController.navigate("${PageConstant.DivAsk.text}?method=${method.id}")
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowMethods.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // 今日一签
        val sign = uiState.todaySign
        if (sign != null) {
            DivCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                borderColor = c.goldHair,
                onClick = { viewModel.ensureTodaySignSaved { id -> navController.navigate("${PageConstant.DivChart.text}/$id") } },
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DivSeal(stringResource(R.string.div_sign_seal), size = 44.dp, fontSize = 22.sp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.div_sign_card_title, sign.hexagramName),
                            style = divSerif(18.sp, FontWeight.SemiBold, 4.sp),
                            color = c.textPrimary,
                        )
                        Text(
                            text = sign.subLine + " —— " + stringResource(R.string.div_sign_card_source),
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    Text("›", color = c.gold.copy(alpha = 0.6f), fontSize = 16.sp)
                }
            }
        }

        // 案卷
        DivSectionTitle(
            title = stringResource(R.string.div_home_ledger),
            tail = stringResource(R.string.div_home_ledger_more),
            onTailClick = { navController.navigate(PageConstant.DivHistory.text) },
        )
        DivCard(Modifier.padding(horizontal = 20.dp), borderColor = c.goldHair) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                if (records.isEmpty()) {
                    Text(
                        text = stringResource(R.string.div_history_empty),
                        fontSize = 12.sp,
                        color = c.textTertiary,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    records.take(3).forEachIndexed { index, record ->
                        LedgerRow(record)
                        if (index != 2 && index != records.take(3).lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(c.goldHair)
                            )
                        }
                    }
                }
            }
        }

        DivFooterNote(text = stringResource(R.string.div_disclaimer_footer))
    }
}

/** 案卷行：日期 + 术数色点 + 问题摘要 + 卦名（衬线金） */
@Composable
private fun LedgerRow(record: com.smartwash.divination.data.DivRecordEntity) {
    val c = DivColors.current
    val dateText = remember(record.castAt) {
        SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(record.castAt))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateText, fontSize = 11.sp, color = c.textTertiary, modifier = Modifier.width(44.dp))
        Box(
            Modifier
                .size(7.dp)
                .background(if (record.method == DivMethod.LIU_YAO.id) c.gold else c.jade, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = record.question,
            fontSize = 13.sp,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = DivMethod.fromId(record.method).name,
            style = divSerif(12.sp, FontWeight.Medium, 2.sp),
            color = c.gold.copy(alpha = 0.75f),
        )
    }
}

/** 四术玉牌：竖排术名 + 一句话定位 + 推荐标 */
@Composable
private fun JadeTile(
    name: String,
    desc: String,
    tag: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(148.dp)
            .pressScale(interactionSource, 0.97f)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(desc, fontSize = 11.5.sp, lineHeight = 18.sp, color = c.textSecondary)
        }
        // 竖排术名：writing-mode 语义用逐字换行实现
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
        ) {
            name.forEach { ch ->
                Text(
                    text = ch.toString(),
                    style = divSerif(19.sp, FontWeight.SemiBold, 7.sp),
                    color = c.textPrimary,
                )
            }
        }
        if (tag != null) {
            Text(
                text = tag,
                fontSize = 10.5.sp,
                letterSpacing = 2.sp,
                color = c.gold.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }
    }
}
