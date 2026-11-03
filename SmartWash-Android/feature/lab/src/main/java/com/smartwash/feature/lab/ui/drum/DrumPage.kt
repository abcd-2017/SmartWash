package com.smartwash.feature.lab.ui.drum

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.lab.R

/** 步进格宽（dp）：16 步在窄屏横向滚动，保证可点性 */
private val CELL_WIDTH = 24.dp

/** 步进格行高（dp） */
private val ROW_HEIGHT = 40.dp

/** 声部标签列宽（dp） */
private val CELL_LABEL_WIDTH = 44.dp

/** 播放头落点格的轻微放大倍数（仅正常动效，reduced motion 关闭） */
private const val CURRENT_CELL_SCALE = 1.06f

/**
 * 滚筒节拍机页：BPM 锚点（36sp 数据大字）+ 播放/停止 + 4 节奏型 + 6 预设 + 8×16 步进网格。
 *
 * reduced motion：默认停止、用户主动播放（与 FishPage 降级一致）；播放头落点格的放大反馈
 * 关闭，仅保留颜色态与触觉（降级不是没有反馈）。
 */
@Composable
fun DrumPage(
    navController: NavHostController,
    viewModel: DrumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val view = currentView()
    val scheme = AppColors.colorScheme

    // 回调记住：播放头 currentStep 每 25ms 变一次，稳定引用避免控制卡/预设区无谓重组
    val setBpm = remember { viewModel::setBpm }
    val toggleStep = remember { viewModel::toggleStep }
    val togglePlay = remember {
        {
            view.performHaptic(HapticEffect.MEDIUM)
            viewModel.togglePlay()
        }
    }
    val selectPattern = remember {
        { index: Int ->
            view.performHaptic(HapticEffect.SELECTION)
            viewModel.selectPattern(index)
        }
    }
    val applyPreset = remember {
        { preset: DrumPreset ->
            view.performHaptic(HapticEffect.SELECTION)
            viewModel.applyPreset(preset)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        PageHeader(
            title = stringResource(R.string.lab_drum_title),
            onBack = { navController.navigateUp() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.pagePadding)
                .padding(bottom = AppDimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
        ) {
            ControlCard(
                bpm = state.bpm,
                playing = state.playing,
                activePattern = state.activePattern,
                onBpmChange = setBpm,
                onTogglePlay = togglePlay,
                onSelectPattern = selectPattern,
            )
            PresetSection(onApply = applyPreset)
            StepGridCard(
                pattern = state.patterns[state.activePattern],
                currentStep = currentStep,
                reduceMotion = reduceMotion,
                onToggle = toggleStep,
            )
        }
    }
}

// ==================== 控制卡：BPM + 播放 + 节奏型 ====================

@Composable
private fun ControlCard(
    bpm: Int,
    playing: Boolean,
    activePattern: Int,
    onBpmChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onSelectPattern: (Int) -> Unit,
) {
    val scheme = AppColors.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = stringResource(R.string.lab_drum_bpm_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.textSecondary,
                    )
                    Text(
                        text = bpm.toString(),
                        style = AppTextStyles.DataLarge,
                        color = scheme.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AppButton(
                    text = stringResource(
                        if (playing) R.string.lab_drum_stop else R.string.lab_drum_play,
                    ),
                    onClick = onTogglePlay,
                    variant = if (playing) {
                        AppButtonVariant.SECONDARY
                    } else {
                        AppButtonVariant.PRIMARY
                    },
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            BpmSlider(bpm = bpm, onBpmChange = onBpmChange)
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Text(
                text = stringResource(R.string.lab_drum_pattern_label),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.textSecondary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            PatternSelector(selected = activePattern, onSelect = onSelectPattern)
        }
    }
}

/**
 * BPM 滑杆（自绘，无 Material ripple）：Canvas 轨道 + 拖拽/点击定位。
 * 触摸定位（手势）不动画，跟随手指；reduced motion 无影响（本身无动画）。
 */
@Composable
private fun BpmSlider(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val minBpm = DrumEngine.MIN_BPM.toFloat()
    val maxBpm = DrumEngine.MAX_BPM.toFloat()
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val fraction = (((dragValue ?: bpm.toFloat()) - minBpm) / (maxBpm - minBpm)).coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onBpmChange((minBpm + f * (maxBpm - minBpm)).toInt())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragValue = minBpm +
                            (offset.x / size.width).coerceIn(0f, 1f) * (maxBpm - minBpm)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragValue = minBpm +
                            (change.position.x / size.width).coerceIn(0f, 1f) * (maxBpm - minBpm)
                        change.consume()
                    },
                    onDragEnd = {
                        dragValue?.let { onBpmChange(it.toInt()) }
                        dragValue = null
                    },
                    onDragCancel = { dragValue = null },
                )
            },
    ) {
        val trackHeight = 4.dp.toPx()
        val trackY = size.height / 2f
        val cornerRadius = CornerRadius(trackHeight / 2f)
        drawRoundRect(
            color = scheme.surfaceVariant,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            color = scheme.primary,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(size.width * fraction, trackHeight),
            cornerRadius = cornerRadius,
        )
        val thumbX = size.width * fraction
        val thumbRadius = 10.dp.toPx()
        drawCircle(color = scheme.surface, radius = thumbRadius, center = Offset(thumbX, trackY))
        drawCircle(
            color = scheme.primary,
            radius = thumbRadius,
            center = Offset(thumbX, trackY),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** 4 节奏型选择器（A/B/C/D）：选中 = primary 实底白字，未选中 = 白底描边 */
@Composable
private fun PatternSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val view = currentView()
    val scheme = AppColors.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
    ) {
        patternLabels().forEachIndexed { index, res ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(if (isSelected) scheme.primary else scheme.surface)
                    .then(
                        if (!isSelected) {
                            Modifier.border(
                                1.dp,
                                scheme.outline,
                                RoundedCornerShape(AppDimens.radiusFull),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .pressable(
                        onClick = {
                            view.performHaptic(HapticEffect.SELECTION)
                            onSelect(index)
                        },
                        debounce = false,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) scheme.onPrimary else scheme.textSecondary,
                )
            }
        }
    }
}

private fun patternLabels(): List<Int> = listOf(
    R.string.lab_drum_pattern_a,
    R.string.lab_drum_pattern_b,
    R.string.lab_drum_pattern_c,
    R.string.lab_drum_pattern_d,
)

// ==================== 预设节奏 ====================

@Composable
private fun PresetSection(onApply: (DrumPreset) -> Unit) {
    val view = currentView()
    val scheme = AppColors.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.lab_drum_presets_label),
            style = AppTextStyles.SectionTitle,
            color = scheme.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
        ) {
            DrumPreset.entries.forEach { preset ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        .background(scheme.surfaceVariant)
                        .border(1.dp, scheme.outline, RoundedCornerShape(AppDimens.radiusFull))
                        .pressable(
                            onClick = {
                                view.performHaptic(HapticEffect.SELECTION)
                                onApply(preset)
                            },
                        )
                        .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
                ) {
                    Text(
                        text = presetLabel(preset),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.textPrimary,
                    )
                }
            }
        }
    }
}

// ==================== 步进网格 ====================

/** 步进网格卡：左侧声部标签固定列 + 右侧 16 步横滚区（8 行 × 16 步） */
@Composable
private fun StepGridCard(
    pattern: DrumPattern,
    currentStep: Int,
    reduceMotion: Boolean,
    onToggle: (Int, Int) -> Unit,
) {
    val scheme = AppColors.colorScheme
    val cellOn = stringResource(R.string.lab_drum_cell_on)
    val cellOff = stringResource(R.string.lab_drum_cell_off)
    // 标签只解析一次（播放头高频重组下避免 24 次 stringResource 重复读表）
    val stepNames = remember { mutableListOf<String>() }
    if (stepNames.isEmpty()) {
        for (i in 0 until DRUM_STEP_COUNT) {
            stepNames.add(stringResource(R.string.lab_drum_step_format, i + 1))
        }
    }
    val voiceNames = remember { mutableListOf<String>() }
    if (voiceNames.isEmpty()) {
        DrumVoice.entries.forEach { voiceNames.add(voiceLabel(it)) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.cardPadding),
        ) {
            // 声部标签列（固定，不随步进横滚）
            Column {
                DrumVoice.entries.forEach { voice ->
                    Box(
                        modifier = Modifier
                            .width(CELL_LABEL_WIDTH)
                            .height(ROW_HEIGHT),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = voiceNames[voice.ordinal],
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            // 步进区（横滚）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            ) {
                DrumVoice.entries.forEach { voice ->
                    Row {
                        for (step in 0 until DRUM_STEP_COUNT) {
                            StepCell(
                                voice = voice,
                                voiceName = voiceNames[voice.ordinal],
                                step = step,
                                stepName = stepNames[step],
                                active = pattern.isEnabled(voice.ordinal, step),
                                isCurrent = step == currentStep,
                                reduceMotion = reduceMotion,
                                cellOn = cellOn,
                                cellOff = cellOff,
                                onToggle = onToggle,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个步进格：状态色（触发 primary / 播放头 water / 拍位 outline / 空 surfaceVariant）+
 * 正常动效下播放头落点格轻微放大（reduced motion 关闭，见 [CURRENT_CELL_SCALE]）。
 */
@Composable
private fun StepCell(
    voice: DrumVoice,
    voiceName: String,
    step: Int,
    stepName: String,
    active: Boolean,
    isCurrent: Boolean,
    reduceMotion: Boolean,
    cellOn: String,
    cellOff: String,
    onToggle: (Int, Int) -> Unit,
) {
    val scheme = AppColors.colorScheme
    val color = when {
        active && isCurrent -> scheme.water
        active -> scheme.primary
        isCurrent -> scheme.primarySoft
        step % 4 == 0 -> scheme.outline
        else -> scheme.surfaceVariant
    }
    // 播放头落点格的放大反馈：Animatable 常驻组合，isCurrent 变化时从 1 弹到目标值；
    // reduced motion 下不播放（规范 §8：取消缩放，保留颜色态与触觉）
    val scale = remember { Animatable(1f) }
    val popSpec = motionSpec(defaultSpring<Float>())
    LaunchedEffect(isCurrent, reduceMotion) {
        if (isCurrent && !reduceMotion) {
            scale.snapTo(1f)
            scale.animateTo(CURRENT_CELL_SCALE, popSpec)
        } else {
            scale.snapTo(1f)
        }
    }
    Box(
        modifier = Modifier
            .size(width = CELL_WIDTH, height = ROW_HEIGHT)
            .padding(1.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(color)
            .pressable(
                onClick = { onToggle(voice.ordinal, step) },
                debounce = false,
            )
            .semantics {
                contentDescription = "$voiceName $stepName"
                stateDescription = if (active) cellOn else cellOff
            },
    )
}

// ==================== 文案映射 ====================

@Composable
private fun voiceLabel(voice: DrumVoice): String = stringResource(
    when (voice) {
        DrumVoice.KICK -> R.string.lab_drum_voice_kick
        DrumVoice.SNARE -> R.string.lab_drum_voice_snare
        DrumVoice.HIHAT -> R.string.lab_drum_voice_hihat
        DrumVoice.CLAP -> R.string.lab_drum_voice_clap
        DrumVoice.TOM -> R.string.lab_drum_voice_tom
        DrumVoice.COWBELL -> R.string.lab_drum_voice_cowbell
        DrumVoice.RIM -> R.string.lab_drum_voice_rim
        DrumVoice.CYMBAL -> R.string.lab_drum_voice_cymbal
    },
)

@Composable
private fun presetLabel(preset: DrumPreset): String = stringResource(
    when (preset) {
        DrumPreset.BASIC -> R.string.lab_drum_preset_basic
        DrumPreset.GROOVE -> R.string.lab_drum_preset_groove
        DrumPreset.BREAKBEAT -> R.string.lab_drum_preset_breakbeat
        DrumPreset.LATIN -> R.string.lab_drum_preset_latin
        DrumPreset.ROBOT -> R.string.lab_drum_preset_robot
        DrumPreset.LAUNDRY -> R.string.lab_drum_preset_laundry
    },
)
