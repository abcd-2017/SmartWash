package com.smartwash.feature.divination.ui.page.reading

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.common.ui.components.WaveIcon
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivGoldButton
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.divSerif
import com.smartwash.feature.divination.DivRoute
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.model.RequestState
import kotlinx.coroutines.delay

/** 章节卡逐项淡入的间隔 — ≤60ms（规范 7） */
private const val SECTION_STAGGER_MS = 60L

/** 章节卡逐项淡入的单卡时长 */
private const val SECTION_FADE_MS = 120

/** 章节卡总入场时长上限（规范 7：总时长 ≤400ms） */
private const val SECTION_STAGGER_CAP_MS = 240L

/**
 * 解读页（画板「解读 · 玄墨 / 宣纸」双板口径）：页头右侧卦印 + 解读章节卡（发丝金线）+
 * 「总结与行动」同款卡 + 单个 14% 金底主按钮 + 免责声明常驻。
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

                // 解读正文（loading / error / success）：三态互相替换走 Crossfade，避免硬切
                Crossfade(
                    targetState = state.readingState,
                    animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
                    label = "readingState",
                ) { readingState ->
                    when (readingState) {
                        is RequestState.Loading -> {
                            DivCard(Modifier.padding(horizontal = 20.dp)) {
                                Column(
                                    Modifier.padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    WaveIcon(isLoading = true, tint = c.gold, size = 18.dp)
                                    Spacer(Modifier.height(8.dp))
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
                            // 解读章节（画板：卡宽 320、页边距 20、卡间 12）
                            Column(Modifier.padding(horizontal = 20.dp)) {
                                reading.sections.forEachIndexed { index, section ->
                                    ReadingSectionCard(section, index)
                                }
                            }

                            // 「总结与行动」卡（画板三卡同款：surface 底 + 12% 金发丝线）
                            ActionCard(reading.action)

                            // 主行动（画板：单个全宽 320×52、r14、14% 金底 + 金字 14/600）
                            DivGoldButton(
                                text = stringResource(R.string.div_reading_followup),
                                soft = true,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp)
                                    .fillMaxWidth(),
                                onClick = {
                                    navController.navigate("${DivRoute.FollowUp.text}/$recordId")
                                },
                            )
                        }
                    }
                }

                DivFooterNote(text = stringResource(R.string.div_disclaimer_reading))
            }
        }
    }
}

/** 页头右侧卦印文字（画板「革」口径）：卦名首字，非六爻取法类首字 */
@Composable
private fun readingSealText(record: com.smartwash.feature.divination.database.DivRecordEntity): String {
    val method = DivMethod.fromId(record.method)
    val chartName = remember(record.chartJson) {
        com.smartwash.feature.divination.data.DivChartCodec.decode(record.chartJson)?.let { bundle ->
            when {
                bundle.liuyao != null -> bundle.liuyao.name
                bundle.meihua != null -> bundle.meihua.benName
                bundle.qimen != null -> "奇门遁甲"
                bundle.liuren != null -> "大六壬"
                else -> null
            }
        }
    } ?: methodLabel(method)
    return when (method) {
        DivMethod.LIU_YAO -> chartName.take(1)
        DivMethod.MEI_HUA -> "梅"
        DivMethod.QI_MEN -> "奇"
        DivMethod.LIU_REN -> "壬"
    }
}

/** 解读章节卡（画板：surface 底 + 1px 12% 金发丝线、r16、内边距 14、标题→正文 8）；
 *  首屏逐项淡入（无位移），reduced motion 退化为纯淡入 */
@Composable
private fun ReadingSectionCard(
    section: com.smartwash.feature.divination.data.DivReadingSection,
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
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = section.title,
                style = divSerif(14.sp, FontWeight.SemiBold, 4.sp, lineHeight = 20.sp),
                // 画板：玄墨侧金字 / 宣纸侧墨字，主题感知金强调
                color = c.goldText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = section.content,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = c.textPrimary,
            )
        }
    }
}

/** 「总结与行动」卡（画板无金底卡，与章节卡同款样式） */
@Composable
private fun ActionCard(action: String) {
    val c = DivColors.current
    DivCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.div_reading_action),
                style = divSerif(14.sp, FontWeight.SemiBold, 4.sp, lineHeight = 20.sp),
                color = c.goldText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = action,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = c.textPrimary,
            )
        }
    }
}

private fun methodLabel(method: DivMethod): String = when (method) {
    DivMethod.LIU_YAO -> "六爻"
    DivMethod.MEI_HUA -> "梅花"
    DivMethod.QI_MEN -> "奇门"
    DivMethod.LIU_REN -> "六壬"
}
