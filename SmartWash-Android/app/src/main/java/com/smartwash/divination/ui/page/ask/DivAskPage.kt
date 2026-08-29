package com.smartwash.divination.ui.page.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.divination.core.DivCategory
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.ui.DivinationScreen
import com.smartwash.divination.ui.components.DivCard
import com.smartwash.divination.ui.components.DivCategoryChip
import com.smartwash.divination.ui.components.DivFooterNote
import com.smartwash.divination.ui.components.DivGoldButton
import com.smartwash.divination.ui.components.DivPageHeader
import com.smartwash.divination.ui.components.DivRadioRow
import com.smartwash.divination.ui.components.DivSectionTitle
import com.smartwash.divination.ui.components.DivSeal
import com.smartwash.divination.ui.components.divSerif
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.DivColors

/**
 * 问事页（原型 Ask）：问题输入 + 领域 chips（自动预选）+ 起卦方式单选。
 * 高风险问题（医疗/生死/胎儿性别/失踪）整页替换为分流提示，不排盘。
 */
@Composable
fun DivAskPage(
    navController: NavHostController,
    methodId: String,
    viewModel: DivAskViewModel = hiltViewModel(),
) {
    val c = DivColors.current
    val state by viewModel.uiState.collectAsState()
    var showLinesDialog by remember { mutableStateOf(false) }
    var showNumbersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(methodId) { viewModel.initMethod(methodId) }

    val navigateChart: (Long) -> Unit = { id ->
        navController.navigate("${PageConstant.DivChart.text}/$id?animate=true") {
            // 起卦交互链收敛：卦盘返回直接回观象台首页
            popUpTo(PageConstant.DivHome.text)
        }
    }

    DivinationScreen {
        DivPageHeader(
            title = stringResource(R.string.div_ask_title),
            onBack = { navController.popBackStack() },
            tail = divMethodTail(state.method),
        )

        if (state.safetyBlocked) {
            SafetyBlockingPage(
                onBack = { navController.popBackStack() },
                onClear = { viewModel.onQuestionChange("") },
            )
            return@DivinationScreen
        }

        // 问题卡
        DivCard(Modifier.padding(horizontal = 20.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(stringResource(R.string.div_ask_hint), fontSize = 11.sp, color = c.textTertiary)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = state.question,
                    onValueChange = viewModel::onQuestionChange,
                    placeholder = {
                        Text(stringResource(R.string.div_question_example), fontSize = 14.sp, color = c.textTertiary)
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
                    minLines = 2,
                    textStyle = divSerif(14.sp, FontWeight.Normal, 0.sp).copy(lineHeight = 24.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 所问领域
        DivSectionTitle(
            title = stringResource(R.string.div_ask_category_label),
            tail = if (state.categoryAuto) {
                stringResource(R.string.div_ask_category_auto)
            } else {
                stringResource(R.string.div_ask_category_manual)
            },
        )
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                DivCategory.CAREER to R.string.div_category_career,
                DivCategory.WEALTH to R.string.div_category_wealth,
                DivCategory.LOVE to R.string.div_category_love,
                DivCategory.STUDY to R.string.div_category_study,
                DivCategory.TRAVEL to R.string.div_category_travel,
                DivCategory.OTHER to R.string.div_category_other,
            ).forEach { (category, nameRes) ->
                DivCategoryChip(
                    text = stringResource(nameRes),
                    selected = state.category == category,
                    onClick = { viewModel.onCategorySelect(category) },
                )
            }
        }

        // 起卦方式
        DivSectionTitle(title = stringResource(R.string.div_ask_cast_method_label))
        DivCard(Modifier.padding(horizontal = 20.dp)) {
            Column {
                when (state.method) {
                    DivMethod.LIU_YAO -> {
                        DivRadioRow(
                            title = stringResource(R.string.div_cast_method_shake),
                            subtitle = stringResource(R.string.div_cast_method_shake_sub),
                            selected = state.liuYaoMode == LiuYaoCastMode.SHAKE,
                            onClick = { viewModel.onLiuYaoMode(LiuYaoCastMode.SHAKE) },
                        )
                        DivRadioRow(
                            title = stringResource(R.string.div_cast_method_lines),
                            subtitle = stringResource(R.string.div_cast_method_lines_sub),
                            selected = state.liuYaoMode == LiuYaoCastMode.LINES,
                            onClick = { viewModel.onLiuYaoMode(LiuYaoCastMode.LINES) },
                        )
                    }

                    DivMethod.MEI_HUA -> {
                        DivRadioRow(
                            title = stringResource(R.string.div_cast_method_time),
                            subtitle = stringResource(R.string.div_cast_method_time_sub),
                            selected = state.meiHuaMode == MeiHuaCastMode.TIME,
                            onClick = { viewModel.onMeiHuaMode(MeiHuaCastMode.TIME) },
                        )
                        DivRadioRow(
                            title = stringResource(R.string.div_cast_method_number),
                            subtitle = stringResource(R.string.div_cast_method_number_sub),
                            selected = state.meiHuaMode == MeiHuaCastMode.NUMBERS,
                            onClick = { viewModel.onMeiHuaMode(MeiHuaCastMode.NUMBERS) },
                        )
                    }

                    DivMethod.QI_MEN -> DivRadioRow(
                        title = stringResource(R.string.div_cast_method_now_ju),
                        subtitle = stringResource(R.string.div_cast_method_now_ju_sub),
                        selected = true,
                        onClick = {},
                    )

                    DivMethod.LIU_REN -> DivRadioRow(
                        title = stringResource(R.string.div_cast_method_now_ke),
                        subtitle = stringResource(R.string.div_cast_method_now_ke_sub),
                        selected = true,
                        onClick = {},
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        DivGoldButton(
            text = stringResource(R.string.div_ask_go),
            enabled = state.question.isNotBlank(),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            onClick = {
                when (state.method) {
                    DivMethod.LIU_YAO -> when (state.liuYaoMode) {
                        LiuYaoCastMode.SHAKE -> navController.navigate(PageConstant.DivCast.text)
                        LiuYaoCastMode.LINES -> showLinesDialog = true
                        LiuYaoCastMode.MANUAL -> showLinesDialog = true
                    }

                    DivMethod.MEI_HUA -> when (state.meiHuaMode) {
                        MeiHuaCastMode.TIME -> viewModel.castMeiHuaTime(navigateChart)
                        MeiHuaCastMode.NUMBERS -> showNumbersDialog = true
                    }

                    DivMethod.QI_MEN -> viewModel.castQiMen(navigateChart)
                    DivMethod.LIU_REN -> viewModel.castLiuRen(navigateChart)
                }
            },
        )

        DivFooterNote(text = stringResource(R.string.div_ask_safety_hint))

        // 手动输入爻值弹窗（单拆重交，自初爻到上爻）
        if (showLinesDialog) {
            ManualLinesDialog(
                onConfirm = { lines ->
                    showLinesDialog = false
                    viewModel.castManualLines(lines, navigateChart)
                },
                onDismiss = { showLinesDialog = false },
            )
        }
        // 梅花报数弹窗
        if (showNumbersDialog) {
            NumbersDialog(
                onConfirm = { a, b ->
                    showNumbersDialog = false
                    viewModel.castMeiHuaNumbers(a, b, navigateChart)
                },
                onDismiss = { showNumbersDialog = false },
            )
        }
    }
}

private fun divMethodTail(method: DivMethod): String = when (method) {
    DivMethod.LIU_YAO -> "六爻 · 摇卦"
    DivMethod.MEI_HUA -> "梅花 · 心易"
    DivMethod.QI_MEN -> "奇门 · 起局"
    DivMethod.LIU_REN -> "六壬 · 起课"
}

/** 高风险分流页：整页替换，不排盘，引导现实求助 */
@Composable
private fun SafetyBlockingPage(onBack: () -> Unit, onClear: () -> Unit) {
    val c = DivColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        DivSeal(stringResource(R.string.div_safety_seal), size = 56.dp, fontSize = 26.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.div_safety_title),
            style = divSerif(18.sp, FontWeight.SemiBold, 3.sp),
            color = c.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.div_safety_body),
            fontSize = 13.sp,
            lineHeight = 24.sp,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(28.dp))
        DivGoldButton(
            text = stringResource(R.string.div_safety_back),
            onClick = {
                onClear()
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 手动输入六爻弹窗：逐爻选 单(阳)/拆(阴)/重(老阳动)/交(老阴动)，自初爻到上爻 */
@Composable
private fun ManualLinesDialog(onConfirm: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    val c = DivColors.current
    val positionNames = listOf("初爻", "二爻", "三爻", "四爻", "五爻", "上爻")
    val options = remember {
        listOf(
            1 to "单 · 阳",
            2 to "拆 · 阴",
            3 to "重 ○ 动",
            4 to "交 × 动",
        )
    }
    var lines by remember { mutableStateOf(listOf(1, 1, 1, 1, 1, 1)) }
    val noIndication = remember { MutableInteractionSource() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DivCard(background = c.surface2) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.div_manual_title),
                    style = divSerif(15.sp, FontWeight.SemiBold, 3.sp),
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(12.dp))
                positionNames.forEachIndexed { index, name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.width(40.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            options.forEach { (code, label) ->
                                val selected = lines[index] == code
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) c.gold14 else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (selected) c.goldLine else c.hair,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable(
                                            interactionSource = noIndication,
                                            indication = null,
                                        ) {
                                            lines = lines.toMutableList().also { it[index] = code }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(label, fontSize = 10.sp, color = if (selected) c.gold else c.textSecondary)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                DivGoldButton(
                    text = stringResource(R.string.div_manual_confirm),
                    modifier = Modifier.fillMaxWidth(),
                    height = 42.dp,
                    fontSize = 13.sp,
                    onClick = { onConfirm(lines) },
                )
            }
        }
    }
}

/** 梅花报数弹窗：随口报两数 */
@Composable
private fun NumbersDialog(onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val c = DivColors.current
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val valid = first.toIntOrNull() in 1..999 && second.toIntOrNull() in 1..999

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DivCard(background = c.surface2) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.div_numbers_title),
                    style = divSerif(15.sp, FontWeight.SemiBold, 3.sp),
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.div_numbers_hint), fontSize = 11.sp, color = c.textTertiary)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(first, { first = it }, Modifier.weight(1f))
                    NumberField(second, { second = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                DivGoldButton(
                    text = stringResource(R.string.div_manual_confirm),
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                    height = 42.dp,
                    fontSize = 13.sp,
                    onClick = { onConfirm(first.toInt(), second.toInt()) },
                )
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = DivColors.current
    TextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(3)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = c.goldLine,
            unfocusedIndicatorColor = c.hair,
            cursorColor = c.gold,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
        ),
        textStyle = divSerif(16.sp, FontWeight.SemiBold, 0.sp),
        singleLine = true,
        modifier = modifier,
    )
}
