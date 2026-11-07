package com.smartwash.feature.divination.ui.page.search

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.DivRoute
import com.smartwash.feature.divination.core.DivCategory
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.network.DivRecordSearchVo
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.DivSeal
import com.smartwash.feature.divination.ui.components.divSerif
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.model.RequestState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 卦例搜索页（占卜闭环一）：关键词防抖搜案卷问题与解读全文，
 * 结果卡点击跳既有卦盘复盘（DivRoute.Chart，原盘原时刻）。
 */
@Composable
fun DivSearchPage(
    navController: NavHostController,
    viewModel: DivSearchViewModel = hiltViewModel(),
) {
    val keyword by viewModel.keyword.collectAsState()
    val results by viewModel.results.collectAsState()
    val state by viewModel.state.collectAsState()

    DivinationScreen(scrollable = false) {
        // 颜色必须在 DivinationScreen 之内读取（默认玄墨，包根外读会让浅色系统下文案不可见）
        val c = DivColors.current
        DivPageHeader(
            title = stringResource(R.string.div_loop_search_title),
            onBack = { navController.popBackStack() },
            alignStart = true,
        )

        // 搜索框（问卦问题卡同款容器：DivCard + 透明 TextField，清氧无新增图标）
        DivCard(Modifier.padding(horizontal = 20.dp)) {
            TextField(
                value = keyword,
                onValueChange = viewModel::onKeywordChange,
                placeholder = {
                    Text(stringResource(R.string.div_loop_search_hint), fontSize = 14.sp, color = c.textSecondary)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = c.gold,
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = divSerif(14.sp, FontWeight.Normal, 0.sp).copy(lineHeight = 20.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            RequestState.Idle -> SearchHint(stringResource(R.string.div_loop_search_idle))
            RequestState.Loading -> SearchHint(stringResource(R.string.div_loop_search_loading))
            is RequestState.Error -> {
                val message = s.getMessage(LocalContext.current)
                SearchHint(message)
            }
            RequestState.Success -> {
                if (results.isEmpty()) {
                    SearchHint(stringResource(R.string.div_loop_search_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        items(results, key = { it.recordId }) { vo ->
                            SearchResultCard(
                                vo = vo,
                                onClick = { navController.navigate(DivRoute.Chart(vo.recordId, animate = false)) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        DivFooterNote(text = stringResource(R.string.div_loop_search_note))
    }
}

/** 状态提示（引导 / 加载 / 错误 / 空态共用容器） */
@Composable
private fun SearchHint(text: String) {
    val c = DivColors.current
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DivSeal("检", size = 44.dp, fontSize = 22.sp)
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
private fun SearchResultCard(vo: DivRecordSearchVo, onClick: () -> Unit) {
    val c = DivColors.current
    // castAt 解析按条目缓存，列表滚动不重复计算；类别名是组合期映射（@Composable，不可入 remember 块）
    val timeText = remember(vo.castAt) { formatIsoTime(vo.castAt) }
    val categoryText = categoryLabel(vo.category)
    DivCard(onClick = onClick) {
        // 画板：卦历卡同款 14/8/12 内距，印章 32 + 问句 14/20 + 预览两行 12/16
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
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
                    text = listOf(categoryText, timeText).filter { it.isNotEmpty() }.joinToString(" · "),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (!vo.contentPreview.isNullOrEmpty()) {
                    Text(
                        text = vo.contentPreview,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = c.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Text("›", color = c.textSecondary, fontSize = 14.sp)
        }
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

/** 类别显示名（标准领域走资源中文；非标准值透传后端原文） */
@Composable
private fun categoryLabel(categoryId: String?): String {
    val resId = when (categoryId) {
        DivCategory.CAREER.id -> R.string.div_category_career
        DivCategory.WEALTH.id -> R.string.div_category_wealth
        DivCategory.LOVE.id -> R.string.div_category_love
        DivCategory.STUDY.id -> R.string.div_category_study
        DivCategory.TRAVEL.id -> R.string.div_category_travel
        DivCategory.OTHER.id -> R.string.div_category_other
        else -> return categoryId.orEmpty()
    }
    return stringResource(resId)
}

/** ISO 时间短格式（MM-dd HH:mm；解析失败回原串截前 16 位，兜底空） */
private fun formatIsoTime(iso: String?): String {
    val raw = iso ?: return ""
    return runCatching {
        LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
    }.getOrDefault(raw.take(16))
}
