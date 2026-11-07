package com.smartwash.feature.divination.ui.page.nudge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.DivRoute
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.network.DivNudgeVo
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivGoldButton
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.DivSeal
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 待回访页（占卜闭环二）：status=0 待回访卦例回访簿。
 * 卡片两动作：去反馈 → 既有解读页（反馈区在解读页，跳 Reading 路由）；
 * 忽略 → POST ignore 成功后乐观移除该条。
 */
@Composable
fun DivNudgePage(
    navController: NavHostController,
    viewModel: DivNudgeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val state by viewModel.state.collectAsState()
    val ignoring by viewModel.ignoring.collectAsState()

    val listState = rememberLazyListState()
    // 触底预取：末尾 2 条进入可视区即续拉下一页（loadMore 内有 loading/total 双防护）
    val shouldLoadMore by remember(items) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            items.isNotEmpty() && lastVisible >= items.lastIndex - 2
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    DivinationScreen(scrollable = false) {
        // 颜色必须在 DivinationScreen 之内读取（默认玄墨，包根外读会让浅色系统下文案不可见）
        val c = DivColors.current
        DivPageHeader(
            title = stringResource(R.string.div_loop_nudge_title),
            onBack = { navController.popBackStack() },
            alignStart = true,
        )

        when (val s = state) {
            RequestState.Idle, RequestState.Loading -> NudgeHint(stringResource(R.string.div_loop_nudge_loading))
            is RequestState.Error -> Column(Modifier.fillMaxWidth()) {
                NudgeHint(s.getMessage(LocalContext.current))
                Spacer(Modifier.height(16.dp))
                DivGoldButton(
                    text = stringResource(R.string.div_loop_stats_retry),
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .fillMaxWidth(),
                    onClick = viewModel::reload,
                )
            }
            RequestState.Success -> {
                if (items.isEmpty()) {
                    NudgeHint(stringResource(R.string.div_loop_nudge_empty))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        items(items, key = { it.recordId }) { vo ->
                            NudgeCard(
                                vo = vo,
                                ignoring = vo.recordId in ignoring,
                                onGoFeedback = { navController.navigate(DivRoute.Reading(vo.recordId)) },
                                onIgnore = { viewModel.ignore(vo.recordId) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        DivFooterNote(text = stringResource(R.string.div_loop_nudge_note))
    }
}

/** 状态提示（加载 / 错误 / 空态共用容器） */
@Composable
private fun NudgeHint(text: String) {
    val c = DivColors.current
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DivSeal("访", size = 44.dp, fontSize = 22.sp)
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

@Composable
private fun NudgeCard(
    vo: DivNudgeVo,
    ignoring: Boolean,
    onGoFeedback: () -> Unit,
    onIgnore: () -> Unit,
) {
    val c = DivColors.current
    // 时间解析按条目缓存，列表滚动不重复计算
    val castText = remember(vo.castAt) { formatIsoTime(vo.castAt) }
    val nudgeText = remember(vo.nudgeDate) { formatIsoDate(vo.nudgeDate) }
    DivCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DivSeal(methodSeal(vo.method), size = 32.dp, fontSize = 14.sp, cornerRadius = 8.dp, flat = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = vo.question.orEmpty().ifEmpty { "—" },
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(
                            stringResource(R.string.div_loop_nudge_cast_prefix, castText),
                            stringResource(R.string.div_loop_nudge_date, nudgeText),
                        ).joinToString(" · "),
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NudgeActionButton(
                    text = stringResource(R.string.div_loop_go_feedback),
                    enabled = true,
                    onClick = onGoFeedback,
                )
                Spacer(Modifier.width(8.dp))
                NudgeActionButton(
                    text = if (ignoring) {
                        stringResource(R.string.div_loop_ignoring)
                    } else {
                        stringResource(R.string.div_loop_ignore)
                    },
                    enabled = !ignoring,
                    onClick = onIgnore,
                )
            }
        }
    }
}

/** 回访簿卡片小按钮：胶囊实底金（宣纸）/ 14% 金底（玄墨），金字墨字随主题感知 */
@Composable
private fun NudgeActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = DivColors.current
    val view = currentView()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (c.isDark) c.gold14 else c.gold)
            .border(1.dp, c.goldLine, RoundedCornerShape(999.dp))
            .pressable(
                onClick = {
                    if (enabled) {
                        view.performHaptic(HapticEffect.LIGHT)
                        onClick()
                    }
                },
            )
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, fontSize = 12.sp, color = c.goldText, maxLines = 1)
    }
}

/** 术数印字（六/梅/奇/壬；未知术数兜底「卦」，避免 fromId 对陌生值抛异常） */
private fun methodSeal(methodId: String?): String = when (
    methodId?.let { id -> DivMethod.entries.firstOrNull { it.id == id } }
) {
    DivMethod.LIU_YAO -> "六"
    DivMethod.MEI_HUA -> "梅"
    DivMethod.QI_MEN -> "奇"
    DivMethod.LIU_REN -> "壬"
    else -> "卦"
}

/** ISO 时间短格式（MM-dd HH:mm；解析失败回原串截前 16 位，兜底空） */
private fun formatIsoTime(iso: String?): String {
    val raw = iso ?: return ""
    return runCatching {
        LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
    }.getOrDefault(raw.take(16))
}

/** ISO 日期短格式（MM-dd；解析失败回原串截前 10 位，兜底空） */
private fun formatIsoDate(iso: String?): String {
    val raw = iso ?: return ""
    return runCatching {
        LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault()))
    }.getOrDefault(raw.take(10))
}
