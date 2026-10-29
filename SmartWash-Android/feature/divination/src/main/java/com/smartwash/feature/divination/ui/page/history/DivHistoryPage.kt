package com.smartwash.feature.divination.ui.page.history

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivCategoryChip
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.DivSeal
import com.smartwash.feature.divination.DivRoute
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
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
        // 画板（玄墨/宣纸双板一致）：无返回页头标题左对齐 x20，标题下 10 进筛选行
        DivPageHeader(title = stringResource(R.string.div_history_title), alignStart = true)

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
                // 画板：卦历筛选 chip 宽 52（2 字 12 号 + 左右 14），与问卦领域 chip 48 区分
                horizontalPadding = 14.dp,
            )
            DivMethod.entries.forEach { method ->
                DivCategoryChip(
                    text = methodLabel(method),
                    selected = uiState.filter == method,
                    onClick = { viewModel.onFilter(method) },
                    horizontalPadding = 14.dp,
                )
            }
        }

        // 画板：筛选行到首卡间距 20（本 Spacer 16 + 列表 contentPadding 顶 4）
        Spacer(Modifier.height(16.dp))

        // 空态与列表互相替换走 Crossfade，避免 0ms 硬切
        Crossfade(
            targetState = records.isEmpty(),
            animationSpec = tween(CROSSFADE_ALT_MS),
            label = "historyEmpty",
        ) { empty ->
            if (empty) {
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
                        HistoryCard(
                            record = record,
                            onClick = {
                                navController.navigate("${DivRoute.Chart.text}/${record.id}?animate=false")
                            },
                            // 筛选切换的位移补间：reduced motion 取消（规范 §8）
                            // （animateItemPlacement 已废弃且需 OptIn 实验 API，改用其官方替代 animateItem）
                            modifier = if (LocalReduceMotion.current) {
                                Modifier
                            } else {
                                Modifier.animateItem(placementSpec = motionSpec(defaultSpring()))
                            },
                        )
                        // 画板：卡间距 8（卡高 60）
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        DivFooterNote(text = stringResource(R.string.div_history_note))
    }
}

@Composable
private fun HistoryCard(
    record: com.smartwash.feature.divination.database.DivRecordEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val timeText = remember(record.castAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.castAt))
    }
    DivCard(modifier = modifier, onClick = onClick) {
        // 画板：卡高 60，印章距左 14；问句 14/20 偏上 12、元信息 12/14 间隔 2；› 距右 8
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DivSeal(methodSeal(record.method), size = 32.dp, fontSize = 14.sp, cornerRadius = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.question.ifEmpty { "—" },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$timeText · ${methodLabel(DivMethod.fromId(record.method))} · ${record.summaryLine()}",
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text("›", color = c.textSecondary, fontSize = 14.sp)
        }
    }
}

/** 列表摘要行：卦名/局数/课体 */
private fun com.smartwash.feature.divination.database.DivRecordEntity.summaryLine(): String =
    com.smartwash.feature.divination.data.DivChartCodec.decode(chartJson)?.let { bundle ->
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
