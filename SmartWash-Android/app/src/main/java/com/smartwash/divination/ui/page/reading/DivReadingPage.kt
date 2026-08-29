package com.smartwash.divination.ui.page.reading

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.smartwash.divination.ui.DivinationScreen
import com.smartwash.divination.ui.components.DivCard
import com.smartwash.divination.ui.components.DivFooterNote
import com.smartwash.divination.ui.components.DivGoldButton
import com.smartwash.divination.ui.components.DivPageHeader
import com.smartwash.divination.ui.components.DivSeal
import com.smartwash.divination.ui.components.DivSectionTitle
import com.smartwash.divination.ui.components.divSerif
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.DivColors
import com.smartwash.utils.RequestState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 解读页（原型 Reading）：盘面摘要 + 解读正文（本期 Mock 文本）+ 金边"总结与行动"卡 +
 * 落款印 + 免责声明常驻 + 「继续追问」入口。
 */
@Composable
fun DivReadingPage(
    navController: NavHostController,
    recordId: Long,
    viewModel: DivReadingViewModel = hiltViewModel(),
) {
    val c = DivColors.current
    val state by viewModel.uiState.collectAsState()

    DivinationScreen {
        DivPageHeader(
            title = stringResource(R.string.div_reading_title),
            onBack = { navController.popBackStack() },
        )

        when {
            state.loadingRecord -> {
                Spacer(Modifier.height(120.dp))
                Text(
                    text = stringResource(R.string.div_reading_loading),
                    fontSize = 12.sp,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.record == null -> {
                Spacer(Modifier.height(80.dp))
                Text(
                    text = stringResource(R.string.div_chart_missing),
                    fontSize = 13.sp,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                val record = state.record ?: return@DivinationScreen
                val method = DivMethod.fromId(record.method)
                val reading = state.reading

                // 盘面摘要
                ReadingSummaryCard(record, method)

                // 解读正文（loading / error / success）
                when (state.readingState) {
                    is RequestState.Loading -> {
                        DivCard(Modifier.padding(horizontal = 20.dp)) {
                            Column(
                                Modifier.padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.div_reading_loading),
                                    fontSize = 12.sp,
                                    color = c.textTertiary,
                                )
                            }
                        }
                    }

                    is RequestState.Error -> {
                        DivCard(Modifier.padding(horizontal = 20.dp)) {
                            Column(
                                Modifier.padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.div_reading_error),
                                    fontSize = 12.sp,
                                    color = c.textTertiary,
                                )
                            }
                        }
                    }

                    else -> if (reading != null) {
                        // 解读章节
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            reading.sections.forEach { section ->
                                ReadingSectionCard(section)
                            }
                        }

                        // 金边"总结与行动"卡
                        ActionCard(reading.action)

                        // 落款印 + 免责声明
                        EndSeal()
                    }
                }

                // 操作按钮
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DivGoldButton(
                        text = stringResource(R.string.div_reading_followup),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            navController.navigate("${PageConstant.DivFollowUp.text}/$recordId")
                        },
                    )
                    DivGoldButton(
                        text = stringResource(R.string.div_reading_share),
                        enabled = false,
                        modifier = Modifier.weight(1f),
                        onClick = {},
                    )
                }

                DivFooterNote(text = stringResource(R.string.div_disclaimer_reading))
            }
        }
    }
}

/** 盘面摘要卡：印章 + 卦名 + 起卦时刻 */
@Composable
private fun ReadingSummaryCard(record: com.smartwash.divination.data.DivRecordEntity, method: DivMethod) {
    val c = DivColors.current
    val bundle = remember(record.chartJson) { com.smartwash.divination.data.DivChartCodec.decode(record.chartJson) }
    val chartName = bundle?.let {
        when {
            it.liuyao != null -> it.liuyao.name + (it.liuyao.bianName?.let { b -> " 之 $b" } ?: "")
            it.meihua != null -> it.meihua.benName
            it.qimen != null -> "奇门遁甲"
            it.liuren != null -> "大六壬"
            else -> null
        }
    } ?: methodLabel(method)
    val sealText = when (method) {
        DivMethod.LIU_YAO -> chartName.take(1)
        DivMethod.MEI_HUA -> "梅"
        DivMethod.QI_MEN -> "奇"
        DivMethod.LIU_REN -> "壬"
    }
    val timeText = remember(record.castAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.castAt))
    }

    DivCard(Modifier.padding(horizontal = 20.dp), borderColor = c.goldLine) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DivSeal(sealText, size = 30.dp, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = chartName,
                style = divSerif(15.sp, FontWeight.SemiBold, 3.sp),
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$timeText · 卜",
                fontSize = 10.sp,
                color = c.textTertiary,
            )
        }
    }
}

/** 解读章节卡 */
@Composable
private fun ReadingSectionCard(section: com.smartwash.divination.data.DivReadingSection) {
    val c = DivColors.current
    DivCard(
        Modifier.padding(vertical = 6.dp),
        borderColor = c.goldHair,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = section.title,
                style = divSerif(13.5.sp, FontWeight.SemiBold, 3.sp),
                color = c.gold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = section.content,
                fontSize = 12.5.sp,
                lineHeight = 21.sp,
                color = c.textPrimary,
            )
        }
    }
}

/** 金边"总结与行动"卡：金底 + 鎏金描边 */
@Composable
private fun ActionCard(action: String) {
    val c = DivColors.current
    DivCard(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        background = c.goldSoft,
        borderColor = c.goldLine,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.div_reading_action),
                style = divSerif(13.sp, FontWeight.SemiBold, 3.sp),
                color = c.gold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = action,
                fontSize = 12.5.sp,
                lineHeight = 21.sp,
                color = c.textPrimary,
            )
        }
    }
}

/** 落款印 + 免责声明 */
@Composable
private fun EndSeal() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DivSeal(
            stringResource(R.string.div_reading_endseal),
            size = 36.dp,
            fontSize = 18.sp,
        )
    }
}

private fun methodLabel(method: DivMethod): String = when (method) {
    DivMethod.LIU_YAO -> "六爻"
    DivMethod.MEI_HUA -> "梅花"
    DivMethod.QI_MEN -> "奇门"
    DivMethod.LIU_REN -> "六壬"
}
