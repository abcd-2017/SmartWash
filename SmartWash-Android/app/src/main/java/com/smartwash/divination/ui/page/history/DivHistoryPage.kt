package com.smartwash.divination.ui.page.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.ui.DivinationScreen
import com.smartwash.divination.ui.components.DivCard
import com.smartwash.divination.ui.components.DivCategoryChip
import com.smartwash.divination.ui.components.DivFooterNote
import com.smartwash.divination.ui.components.DivPageHeader
import com.smartwash.divination.ui.components.DivSeal
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.DivColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 卦历案卷（原型 History）：术数筛选 chips + 卦历卡列表；点击进只读复盘（原盘原时刻）。
 */
@Composable
fun DivHistoryPage(
    navController: NavHostController,
    viewModel: DivHistoryViewModel = hiltViewModel(),
) {
    val c = DivColors.current
    val uiState by viewModel.uiState.collectAsState()
    val records by viewModel.records.collectAsState()

    DivinationScreen(scrollable = false) {
        DivPageHeader(title = stringResource(R.string.div_history_title))

        // 术数筛选
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            DivCategoryChip(
                text = stringResource(R.string.div_filter_all),
                selected = uiState.filter == null,
                onClick = { viewModel.onFilter(null) },
            )
            DivMethod.entries.forEach { method ->
                DivCategoryChip(
                    text = methodLabel(method),
                    selected = uiState.filter == method,
                    onClick = { viewModel.onFilter(method) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (records.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DivSeal("空", size = 44.dp, fontSize = 22.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.div_history_empty),
                        fontSize = 12.sp,
                        color = c.textTertiary,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryCard(record) {
                        navController.navigate("${PageConstant.DivChart.text}/${record.id}?animate=false")
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        DivFooterNote(text = stringResource(R.string.div_history_note))
    }
}

@Composable
private fun HistoryCard(record: com.smartwash.divination.data.DivRecordEntity, onClick: () -> Unit) {
    val c = DivColors.current
    val timeText = remember(record.castAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.castAt))
    }
    DivCard(onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DivSeal(methodSeal(record.method), size = 30.dp, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.question.ifEmpty { "—" },
                    fontSize = 13.5.sp,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$timeText · ${methodLabel(DivMethod.fromId(record.method))} · ${record.summaryLine()}",
                    fontSize = 10.5.sp,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text("›", color = c.textTertiary, fontSize = 14.sp)
        }
    }
}

/** 列表摘要行：卦名/局数/课体 */
private fun com.smartwash.divination.data.DivRecordEntity.summaryLine(): String =
    com.smartwash.divination.data.DivChartCodec.decode(chartJson)?.let { bundle ->
        when {
            bundle.liuyao != null -> bundle.liuyao.name + (bundle.liuyao.bianName?.let { " 之 $it" } ?: "")
            bundle.meihua != null -> bundle.meihua.benName
            bundle.qimen != null -> "阴遁${bundle.qimen.juNumber}局"
            bundle.liuren != null -> "月将${bundle.liuren.yueJiang}"
            else -> ""
        }
    } ?: ""

private fun methodLabel(method: DivMethod): String = when (method) {
    DivMethod.LIU_YAO -> "六爻"
    DivMethod.MEI_HUA -> "梅花"
    DivMethod.QI_MEN -> "奇门"
    DivMethod.LIU_REN -> "六壬"
}

private fun methodSeal(methodId: String): String = when (DivMethod.fromId(methodId)) {
    DivMethod.LIU_YAO -> "卦"
    DivMethod.MEI_HUA -> "梅"
    DivMethod.QI_MEN -> "奇"
    DivMethod.LIU_REN -> "壬"
}
