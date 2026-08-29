package com.smartwash.divination.ui.page.cast

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.divination.core.YaoValue
import com.smartwash.divination.ui.DivinationScreen
import com.smartwash.divination.ui.components.CoinTossRow
import com.smartwash.divination.ui.components.DivGoldButton
import com.smartwash.divination.ui.components.MeridianNode
import com.smartwash.divination.ui.components.divSerif
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.DivColors
import com.smartwash.utils.HapticEffect
import com.smartwash.ui.common.AppConfirmDialog
import com.smartwash.utils.currentView
import com.smartwash.utils.performHaptic

/**
 * 摇卦页（原型 Cast）：三币 + 子午线六节点。
 * 时间轴（方案 7.3）：按下即缩放 → 三币起跳翻转 ~420ms → 落定【同帧】触觉 →
 * 爻落子午线节点 → 第 6 掷完成按钮变为「成卦 · 进入卦盘」。
 * 掷完前返回需二次确认（禁重摇）；动画播放期间掷爻按钮禁用。
 */
@Composable
fun DivCastPage(
    navController: NavHostController,
    question: String,
    categoryId: String,
    viewModel: DivCastViewModel = hiltViewModel(),
) {
    val c = DivColors.current
    val view = currentView()
    val state by viewModel.uiState.collectAsState()
    val reduceMotion = com.smartwash.divination.ui.divReduceMotion()

    // 本掷状态
    var pendingYao by remember { mutableStateOf<YaoValue?>(null) }
    var tossId by remember { mutableStateOf(0L) }
    var faces by remember { mutableStateOf(listOf(true, true, true)) }
    var showQuitDialog by remember { mutableStateOf(false) }

    val tossCount = state.lines.size + (if (pendingYao != null) 1 else 0)

    BackHandler(enabled = true) {
        if (tossCount in 1..5 || state.tossing) {
            showQuitDialog = true
        } else {
            navController.popBackStack()
        }
    }

    DivinationScreen {
        Text(
            text = stringResource(R.string.div_cast_title),
            style = divSerif(15.sp, FontWeight.Medium, 4.sp),
            color = c.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
        Text(
            text = stringResource(R.string.div_cast_progress, tossCount.coerceAtMost(6)),
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            color = c.gold.copy(alpha = 0.75f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
        )

        // 三币
        CoinTossRow(
            faces = faces,
            tossId = tossId,
            onSettled = {
                // 币落定【同帧】触觉；动爻（老阴/老阳）更强反馈
                val yao = pendingYao
                if (yao != null) {
                    view.performHaptic(if (yao.isMoving) HapticEffect.SUCCESS else HapticEffect.MEDIUM)
                    viewModel.commitToss(yao)
                    pendingYao = null
                }
            },
        )

        Spacer(Modifier.height(6.dp))

        // 子午线：六节点，自上爻(顶)到初爻(底)
        val positions = listOf("上爻", "五爻", "四爻", "三爻", "二爻", "初爻")
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        ) {
            // 中线（鎏金渐隐）
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .height(300.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(c.goldHair, c.goldLine, c.goldHair)
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
            ) {
                positions.forEachIndexed { displayIndex, posLabel ->
                    // displayIndex 0=上爻(index5) … 5=初爻(index0)
                    val lineIndex = 5 - displayIndex
                    val committed = state.lines.getOrNull(lineIndex)
                    val isPending = pendingYao != null && lineIndex == state.lines.size
                    val yaoCode = committed ?: (if (isPending) pendingYao?.code else null)
                    MeridianNode(
                        positionLabel = posLabel,
                        symbol = yaoCode?.let {
                            if (it == 1 || it == 3) "▅▅▅▅▅▅" else "▅▅  ▅▅"
                        },
                        nameLabel = yaoCode?.let {
                            when (it) {
                                1 -> "少阳"
                                2 -> "少阴"
                                3 -> "老阳 · 动"
                                else -> "老阴 · 动"
                            }
                        },
                        isMoving = yaoCode != null && yaoCode > 2,
                        done = yaoCode != null,
                    )
                }
            }
        }

        // 掷爻 / 成卦
        DivGoldButton(
            text = if (viewModel.isComplete()) {
                stringResource(R.string.div_cast_done)
            } else {
                stringResource(R.string.div_cast_toss)
            },
            enabled = !state.tossing && !state.saving && pendingYao == null,
            modifier = Modifier
                .padding(horizontal = 76.dp, vertical = 14.dp)
                .fillMaxWidth(),
            onClick = {
                if (viewModel.isComplete()) {
                    view.performHaptic(HapticEffect.SUCCESS)
                    viewModel.completeChart(question, com.smartwash.divination.core.DivCategory.fromId(categoryId)) { id ->
                        navController.navigate("${PageConstant.DivChart.text}/$id?animate=true") {
                            popUpTo(PageConstant.DivHome.text)
                        }
                    }
                } else {
                    // t=0：三币同步起跳（预生成结果，动画只呈现）
                    val yao = viewModel.nextToss()
                    pendingYao = yao
                    faces = List(3) { coinFace(yao, it) }
                    viewModel.setTossing(true)
                    tossId++
                    if (reduceMotion) {
                        // reduced-motion：直接定格结果面，仅保留落定触觉
                        view.performHaptic(if (yao.isMoving) HapticEffect.SUCCESS else HapticEffect.MEDIUM)
                        viewModel.commitToss(yao)
                        pendingYao = null
                    }
                }
            },
        )

        if (showQuitDialog) {
            AppConfirmDialog(
                message = stringResource(R.string.div_cast_quit_body),
                title = stringResource(R.string.div_cast_quit_title),
                onConfirm = {
                    showQuitDialog = false
                    navController.popBackStack()
                },
                onDismiss = { showQuitDialog = false },
                isDanger = true,
            )
        }
    }
}

/** 由爻值定三币结果面：字记 3、背记 2，和 6/7/8/9 = 老阴/少阳/少阴/老阳；face=true 为字 */
private fun coinFace(yao: YaoValue, coinIndex: Int): Boolean {
    val ziCount = when (yao) {
        YaoValue.LAO_YANG -> 3  // 3+3+3 = 9 三字
        YaoValue.SHAO_YIN -> 2  // 3+3+2 = 8 二字一背
        YaoValue.SHAO_YANG -> 1 // 3+2+2 = 7 一字两背
        YaoValue.LAO_YIN -> 0   // 2+2+2 = 6 三背
    }
    return coinIndex < ziCount
}
