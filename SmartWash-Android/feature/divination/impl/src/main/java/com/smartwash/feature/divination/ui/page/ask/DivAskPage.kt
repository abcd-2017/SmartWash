package com.smartwash.feature.divination.ui.page.ask

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.core.DivCategory
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.ui.DivinationScreen
import com.smartwash.feature.divination.ui.components.DivCard
import com.smartwash.feature.divination.ui.components.DivCategoryChip
import com.smartwash.feature.divination.ui.components.DivFooterNote
import com.smartwash.feature.divination.ui.components.DivGoldButton
import com.smartwash.feature.divination.ui.components.DivPageHeader
import com.smartwash.feature.divination.ui.components.DivRadioRow
import com.smartwash.feature.divination.ui.components.DivSectionTitle
import com.smartwash.feature.divination.ui.components.DivSeal
import com.smartwash.feature.divination.ui.components.divSerif
import com.smartwash.feature.divination.api.DivRoute
import com.smartwash.common.ui.components.dialogEnterMotion
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.ui.theme.DivinationColorScheme
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable

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
    val state by viewModel.uiState.collectAsState()
    // 弹窗开关与内部输入状态均可持久化（audit-22 #13）：旋转/进程重建后弹窗不消失、
    // 已选爻值与已报数字不丢（对照 DivCastPage 掷爻状态的 rememberSaveable 做法）
    var showLinesDialog by rememberSaveable { mutableStateOf(false) }
    var showNumbersDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(methodId) { viewModel.initMethod(methodId) }

    val navigateChart: (Long) -> Unit = { id ->
        navController.navigate(DivRoute.Chart(id, animate = true)) {
            // 起卦交互链收敛：卦盘返回直接回观象台首页
            popUpTo<DivRoute.Home>()
        }
    }

    // 问事表单本体（供高风险分流页与表单之间的 Crossfade 复用）
    // 颜色经参数传入：DivColors.current 必须在 DivinationScreen 之内读取，
    // 在包根外读会拿到默认玄墨（浅色系统下问题输入框文字与提示不可见）
    val askForm: @Composable (DivinationColorScheme) -> Unit = { c ->
        // 问题卡
        DivCard(Modifier.padding(horizontal = 20.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.div_ask_hint),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = state.question,
                    onValueChange = viewModel::onQuestionChange,
                    placeholder = {
                        // 占位符字号与正文一致（规范 §3.7）
                        Text(stringResource(R.string.div_question_example), fontSize = 16.sp, color = c.textSecondary)
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
                    // 输入正文 16sp（规范 §3.7 输入控件标准，禁用 14sp 及以下；audit-22 #3，
                    // 与同页 NumberField 的 16sp 口径对齐），衬线保留，行高随 bodyLarge 16/24
                    textStyle = divSerif(16.sp, FontWeight.Normal, 0.sp).copy(lineHeight = 24.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 所问领域（画板：卡到标题 16，标题自带 8 下距）
        Spacer(Modifier.height(8.dp))
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
                // 玄墨画板页边距统一 20（问卦/卦历卡均在 x=20），非 16
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

        // 起卦方式（画板：chips 到标题 8，由标题自带内距承担）
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
                        MethodRowDivider()
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
                        MethodRowDivider()
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

        // 主行动（画板：方式卡到按钮 24，按钮到脚注 16 = Spacer 8 + 脚注自带 paddingTop 8）
        Spacer(Modifier.height(24.dp))
        DivGoldButton(
            // 提交中禁用 + loading 指示（规范 §3.1 异步提交；audit-22 #4，
            // 防止梅花/奇门/六壬提交期间重复入库）
            text = if (state.saving) {
                stringResource(R.string.div_ask_casting)
            } else {
                stringResource(R.string.div_ask_go)
            },
            enabled = state.question.isNotBlank() && !state.saving,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            onClick = {
                when (state.method) {
                    DivMethod.LIU_YAO -> when (state.liuYaoMode) {
                        // 摇卦与手动爻值同源传参：问词 + 领域随路由带入，
                        // 成卦案卷问词、卦盘用神口径、解读上下文才完整（缺省会存空问词/恒「其他」）
                        LiuYaoCastMode.SHAKE -> navController.navigate(
                            DivRoute.Cast(
                                question = state.question.trim(),
                                category = state.category.id,
                            )
                        )
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

        Spacer(Modifier.height(8.dp))
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

    DivinationScreen {
        val c = DivColors.current
        DivPageHeader(
            title = stringResource(R.string.div_ask_title),
            onBack = { navController.popBackStack() },
            tail = stringResource(divMethodTailRes(state.method)),
        )

        // 高风险分流页与问事表单之间的整页替换：Crossfade 过渡，避免硬切
        Crossfade(
            targetState = state.safetyBlocked,
            animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
            label = "safetyBlocked",
        ) { blocked ->
            if (blocked) {
                SafetyBlockingPage(
                    onBack = { navController.popBackStack() },
                    onClear = { viewModel.onQuestionChange("") },
                )
            } else {
                // askForm 内含多个兄弟节点（问题卡/章节标题/领域 chips/方式卡/按钮/脚注），
                // 必须落在 Column 作用域才能纵向排布：Crossfade 的内容是 Box，
                // 直接发射会让全部兄弟叠在同一原点，整页表单挤成一团（布局错乱）
                Column(Modifier.fillMaxWidth()) { askForm(c) }
            }
        }
    }
}

/** 四术尾注（audit-22 #7：用户可见文案一律 strings.xml） */
private fun divMethodTailRes(method: DivMethod): Int = when (method) {
    DivMethod.LIU_YAO -> R.string.div_method_tail_liuyao
    DivMethod.MEI_HUA -> R.string.div_method_tail_meihua
    DivMethod.QI_MEN -> R.string.div_method_tail_qimen
    DivMethod.LIU_REN -> R.string.div_method_tail_liuren
}

/** 方式卡行分隔（画板：1px 12% 金发丝线，左右缩进 16） */
@Composable
private fun MethodRowDivider() {
    val c = DivColors.current
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(c.hair),
    )
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
        DivSeal(stringResource(R.string.div_safety_seal), size = 44.dp, fontSize = 22.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.div_safety_title),
            style = divSerif(18.sp, FontWeight.SemiBold, 3.sp),
            color = c.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.div_safety_body),
            fontSize = 16.sp,
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
    // 爻位名与选项文案迁 strings.xml（audit-22 #7）
    val positionNames = listOf(
        stringResource(R.string.div_yao_position_1),
        stringResource(R.string.div_yao_position_2),
        stringResource(R.string.div_yao_position_3),
        stringResource(R.string.div_yao_position_4),
        stringResource(R.string.div_yao_position_5),
        stringResource(R.string.div_yao_position_6),
    )
    val options = listOf(
        1 to stringResource(R.string.div_manual_option_yang),
        2 to stringResource(R.string.div_manual_option_yin),
        3 to stringResource(R.string.div_manual_option_lao_yang),
        4 to stringResource(R.string.div_manual_option_lao_yin),
    )
    // 已选爻值可持久化（audit-22 #13）：旋转/进程重建不丢
    var lines by rememberSaveable { mutableStateOf(listOf(1, 1, 1, 1, 1, 1)) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        // audit-22 #1：选项热区提至 48dp 后每行内容 ≈ 爻位名 32 + 4×48 + 间距 24 = 248dp，
        // 平台默认 Dialog 宽（≈280，含弹窗内边距 40）必溢出——自控窗口宽（水平 16 边距），
        // 选项行内 weight 均分，320dp 窄屏每选项仍精确 ≥48dp
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 与公共弹窗同一条入场动效（scale 0.92→1 + alpha，reduced motion 降级纯淡入）
        DivCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .dialogEnterMotion(),
            background = c.surface2,
        ) {
            // 48dp 选项 × 6 行总高约 +144dp，超出弹窗可视高度：内容区纵向滚动（audit-22 #1）
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                        // 爻位名两字 12sp 宽 32 足够；320 窄屏下为右侧 4×48dp 选项让宽
                        Text(name, fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.width(32.dp))
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            options.forEach { (code, label) ->
                                val selected = lines[index] == code
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        // 触控热区 ≥48dp（规范 §2.3 / DESIGN-SPEC §3 红线；audit-22 #1：
                                        // 原 10sp + 8/4 padding 仅 ≈21dp，六爻手动装卦核心输入件误触率高）
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) c.gold14 else Color.Transparent)
                                        .border(
                                            0.5.dp,
                                            if (selected) c.goldLine else c.hair,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .pressable(
                                            onClick = {
                                                lines = lines.toMutableList().also { it[index] = code }
                                            },
                                            debounce = false,
                                        )
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // 选中字色用文字级 goldText（audit-22 #2：原图形级 c.gold 压 15% 金底
                                    // 宣纸侧 3.26:1 不达标；goldText 宣纸墨 12.2:1 / 玄墨金 5.26:1 双达标，
                                    // 选中底维持 14% 金底不动——不复刻已裁定的「实底金+墨字 3.75:1」组合）；
                                    // 字号 10→12sp 为 v0.4 字阶最小档（无 10sp 档）
                                    Text(label, fontSize = 12.sp, color = if (selected) c.goldText else c.textSecondary)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                DivGoldButton(
                    text = stringResource(R.string.div_manual_confirm),
                    modifier = Modifier.fillMaxWidth(),
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
    // 已报数字可持久化（audit-22 #13）：旋转/进程重建不丢
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    val valid = first.toIntOrNull() in 1..999 && second.toIntOrNull() in 1..999

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        // 与公共弹窗同一条入场动效（scale 0.92→1 + alpha，reduced motion 降级纯淡入）
        DivCard(modifier = Modifier.dialogEnterMotion(), background = c.surface2) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.div_numbers_title),
                    style = divSerif(15.sp, FontWeight.SemiBold, 3.sp),
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.div_numbers_hint), fontSize = 12.sp, color = c.textTertiary)
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
        // 报数输入走数字键盘（校验逻辑仍由 onValueChange 过滤 + 提交前 1..999 区间拦截）
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
