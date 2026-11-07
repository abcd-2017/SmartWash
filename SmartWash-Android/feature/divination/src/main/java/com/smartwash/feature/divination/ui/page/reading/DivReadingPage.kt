package com.smartwash.feature.divination.ui.page.reading

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.smartwash.feature.divination.data.DivChartCodec
import com.smartwash.feature.divination.data.DivMarkdownParser
import com.smartwash.feature.divination.data.DivReadingSection
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.common.ui.components.WaveIcon
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivGoldButton
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.buildDivRichText
import com.smartwash.feature.divination.ui.components.divSerif
import com.smartwash.feature.divination.DivRoute
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import kotlinx.coroutines.delay

/** 章节卡逐项淡入的间隔 — ≤60ms（规范 7） */
private const val SECTION_STAGGER_MS = 60L

/** 章节卡逐项淡入的单卡时长 */
private const val SECTION_FADE_MS = 120

/** 章节卡总入场时长上限（规范 7：总时长 ≤400ms） */
private const val SECTION_STAGGER_CAP_MS = 240L

/**
 * 解读页（画板「解读 · 玄墨 / 宣纸」双板口径）：页头右侧卦印 + 解读章节卡（发丝金线）+
 * 「一句话断语」高亮卡 + 「结论与行动」同款卡 + 单个 14% 金底主按钮 + 免责声明常驻。
 *
 * issue #78 真连口径：正文为 LLM SSE 流式 Markdown —— Loading 态按 `## ` 前缀切分逐段上屏
 * （已完成段先渲染成卡、尾部生成中段带水波指示）；done 后全量切分，四段固定标题映射
 * （一句话断语→断语卡 / 其余→章节卡 / 结论与建议→行动卡 / `>` 引用→免责条）。
 */
@Composable
fun DivReadingPage(
    navController: NavHostController,
    recordId: Long,
    viewModel: DivReadingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    DivinationScreen {
        // 颜色必须在 DivinationScreen 之内读取（默认玄墨，包根外读会让加载/空态文案在浅色系统下不可见）
        val c = DivColors.current
        DivPageHeader(
            title = stringResource(R.string.div_reading_title),
            onBack = { navController.popBackStack() },
            // 画板：页头右侧固定卦印（右缘 20、30 方印 r8、平涂深朱、印文 14/700）
            sealTrailing = state.record?.let { readingSealText(it) },
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
                    fontSize = 12.sp,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                val record = state.record ?: return@DivinationScreen
                val reading = state.reading

                // 解读正文（loading/流式 / error / success）：三态互相替换走 Crossfade，避免硬切
                Crossfade(
                    targetState = state.readingState,
                    animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
                    label = "readingState",
                ) { readingState ->
                    when (readingState) {
                        is RequestState.Loading -> StreamingReadingContent(state.streamText)

                        is RequestState.Error -> {
                            // error 帧 message 优先（服务端分流/限额/降级文案），无则用兜底资源
                            val context = LocalContext.current
                            DivCard(Modifier.padding(horizontal = 20.dp)) {
                                Column(
                                    Modifier.padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = readingState.getMessage(context),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = c.textTertiary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }

                        else -> if (reading != null) {
                            // 注意：Crossfade 的内容容器是 BoxScope（CrossfadeScope），此前章节卡/
                            // 「总结与行动」卡/主按钮三件直接作为其子组件按 TopStart 叠放
                            // （P-1701：「继 续 追 问」按钮压在「总结与行动」卡片标题上），
                            // 收拢进单个 Column 恢复流式排列（画板：卡宽 320、页边距 20、卡间 12）
                            Column(Modifier.padding(horizontal = 20.dp)) {
                                // 一句话断语（真连四段之首；Mock 亦有）
                                if (reading.summary.isNotBlank()) {
                                    ReadingSummaryCard(reading.summary)
                                }

                                reading.sections.forEachIndexed { index, section ->
                                    ReadingSectionCard(section, index)
                                }

                                // 「总结与行动」卡（画板三卡同款：surface 底 + 12% 金发丝线）；
                                // 真连「## 结论与建议」段映射至此，无该段则不渲染
                                if (reading.action.isNotBlank()) {
                                    ActionCard(reading.action)
                                }

                                // 主行动（2026-11-05 解读画板：「继 续 追 问」320×52 r14，宣纸实底金 #8F7233 +
                                // 墨字 / 玄墨 14% 金底金字 —— soft 主题感知口径；取 DivGoldButton 默认
                                // （52/r14/15sp）不再单侧覆写。画板另有「分 享」次位按钮，端上暂无分享实现）
                                DivGoldButton(
                                    text = stringResource(R.string.div_reading_followup),
                                    soft = true,
                                    modifier = Modifier
                                        .padding(top = 20.dp)
                                        .fillMaxWidth(),
                                    onClick = {
                                        navController.navigate(DivRoute.FollowUp(recordId))
                                    },
                                )

                                // 应验反馈（issue #78：POST /records/{id}/feedback，rating 1-5；
                                // 需解读 done 拿到服务端 interpretationId 后出现）
                                FeedbackRow(
                                    visible = (state.record?.interpretationId ?: 0L) > 0,
                                    submitting = state.feedbackSubmitting,
                                    submitted = state.feedbackSubmitted,
                                    onRating = viewModel::submitFeedback,
                                )
                            }
                        }
                    }
                }

                // 免责条：服务端 `> 免责声明` 引用行优先，否则常驻本地文案
                DivFooterNote(
                    text = reading?.disclaimer
                        ?: stringResource(R.string.div_disclaimer_reading),
                )
            }
        }
    }
}

/** 应验反馈行（画板章节卡同款容器）：标题 + 5 档鎏金圆点评分（1-5），提交后呈现已提交态 */
@Composable
private fun FeedbackRow(
    visible: Boolean,
    submitting: Boolean,
    submitted: Boolean,
    onRating: (Int) -> Unit,
) {
    if (!visible) return
    val c = DivColors.current
    DivCard(Modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.div_feedback_title),
                style = divSerif(14.sp, FontWeight.SemiBold, 4.sp, lineHeight = 20.sp),
                color = c.goldText,
            )
            Spacer(Modifier.height(10.dp))
            if (submitted) {
                Text(
                    text = stringResource(R.string.div_feedback_done),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = c.textSecondary,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (1..5).forEach { rating ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c.gold14)
                                .border(BorderStroke(1.dp, c.hair), CircleShape)
                                .then(if (submitting) Modifier else Modifier.pressable(onClick = { onRating(rating) })),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = rating.toString(),
                                fontSize = 12.sp,
                                color = c.goldText,
                            )
                        }
                    }
                }
                if (submitting) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.div_feedback_submitting),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = c.textTertiary,
                    )
                }
            }
        }
    }
}

/** 页头右侧卦印文字（2026-11-05 卦盘/解读画板口径：取本卦名末字，泽火革→「革」、水雷屯→「屯」；与卦盘 [divSealText] 同规则） */
@Composable
private fun readingSealText(record: com.smartwash.feature.divination.database.DivRecordEntity): String {
    val method = DivMethod.fromId(record.method)
    val hexagramName = remember(record.chartJson) {
        DivChartCodec.decode(record.chartJson)?.let { bundle ->
            when {
                bundle.liuyao != null -> bundle.liuyao.name
                bundle.meihua != null -> bundle.meihua.benName
                else -> null
            }
        }
    }
    return when (method) {
        DivMethod.LIU_YAO -> hexagramName?.takeLast(1) ?: "六"
        DivMethod.MEI_HUA -> hexagramName?.takeLast(1) ?: "梅"
        DivMethod.QI_MEN -> "奇"
        DivMethod.LIU_REN -> "壬"
    }
}

/** Loading 态流式正文：无增量先出「正在推演」卡；有增量按 `## ` 前缀切段逐卡上屏 + 尾部生成中段 */
@Composable
private fun StreamingReadingContent(streamText: String) {
    if (streamText.isBlank()) {
        DivCard(Modifier.padding(horizontal = 20.dp)) {
            Column(
                Modifier.padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val c = DivColors.current
                WaveIcon(isLoading = true, tint = c.gold, size = 18.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.div_reading_loading),
                    fontSize = 12.sp,
                    color = c.textTertiary,
                )
            }
        }
        return
    }
    val streaming = remember(streamText) { DivMarkdownParser.parseStreaming(streamText) }
    val c = DivColors.current
    Column(Modifier.padding(horizontal = 20.dp)) {
        streaming.summary?.let { ReadingSummaryCard(it) }
        streaming.sections.forEachIndexed { index, section ->
            ReadingSectionCard(section, index)
        }
        streaming.action?.let { ActionCard(it) }
        streaming.pending?.let { pending ->
            DivCard(Modifier.padding(vertical = 6.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                    if (pending.title.isNotBlank()) {
                        Text(
                            text = pending.title,
                            style = divSerif(14.sp, FontWeight.SemiBold, 4.sp, lineHeight = 20.sp),
                            color = c.goldText,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WaveIcon(isLoading = true, tint = c.gold, size = 12.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = pending.content.ifBlank { stringResource(R.string.div_reading_loading) },
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = c.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

/** 「一句话断语」卡（四段之首：卡题金衬线同章节卡，正文 12sp；画板章节卡同款容器） */
@Composable
private fun ReadingSummaryCard(summary: String) {
    val c = DivColors.current
    DivCard(Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.div_reading_summary),
                style = divSerif(14.sp, FontWeight.SemiBold, 4.sp, lineHeight = 20.sp),
                color = c.goldText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildDivRichText(summary),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = c.textPrimary,
            )
        }
    }
}

/** 解读章节卡（画板：surface 底 + 1px 12% 金发丝线、r16、内边距 14、标题→正文 8；
 *  卡高画板 152/152/120 —— 设计文件两卡正文同两行而卡高不一致，按条目定档 minHeight 120，
 *  内容不足时留白沉底、超出自适应）；首屏逐项淡入（无位移），reduced motion 退化为纯淡入。
 *  正文经 buildDivRichText 支持 **加粗** 与 `- ` 列表（issue #78）。*/
@Composable
private fun ReadingSectionCard(
    section: DivReadingSection,
    index: Int,
) {
    val c = DivColors.current
    val reduceMotion = LocalReduceMotion.current
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            // 降级：纯 180ms 淡入，取消逐项延迟
            alpha.animateTo(1f, tween(CROSSFADE_ALT_MS))
        } else {
            // 间隔 ≤60ms，总时长 ≤400ms（规范 7）
            delay((index * SECTION_STAGGER_MS).coerceAtMost(SECTION_STAGGER_CAP_MS))
            alpha.animateTo(1f, tween(SECTION_FADE_MS, easing = LinearOutSlowInEasing))
        }
    }
    DivCard(
        Modifier
            .padding(vertical = 6.dp)
            .graphicsLayer { this.alpha = alpha.value }
            .defaultMinSize(minHeight = 120.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                // 无 `## ` 标题的兜底单卡：卡题用「解读」
                text = section.title.ifBlank { stringResource(R.string.div_reading_section_fallback) },
                style = divSerif(14.sp, FontWeight.SemiBold, 4.sp, lineHeight = 20.sp),
                // 画板：玄墨侧金字 / 宣纸侧墨字，主题感知金强调
                color = c.goldText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildDivRichText(section.content),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = c.textPrimary,
            )
        }
    }
}

/** 「总结与行动」卡（画板无金底卡，与章节卡同款样式；水平边距由解读正文的 Column 提供） */
@Composable
private fun ActionCard(action: String) {
    val c = DivColors.current
    DivCard(Modifier.padding(top = 6.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.div_reading_action),
                style = divSerif(14.sp, FontWeight.SemiBold, 4.sp, lineHeight = 20.sp),
                color = c.goldText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildDivRichText(action),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = c.textPrimary,
            )
        }
    }
}
