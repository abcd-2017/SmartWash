package com.smartwash.feature.lab.ui.soundscape

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.SPIN_PERIOD_MS
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pingPongInfinite
import com.smartwash.common.utils.pressable
import com.smartwash.feature.lab.R
import kotlin.math.PI
import kotlin.math.sin

// ==================== 混音台分组（顺序即展示序） ====================

/** 底噪组：白 / 粉 / 棕噪音 */
private val NOISE_CHANNELS = listOf(
    SoundChannel.WHITE_NOISE,
    SoundChannel.PINK_NOISE,
    SoundChannel.BROWN_NOISE,
)

/** 自然组：雨 / 风 / 海浪 / 篝火 */
private val NATURE_CHANNELS = listOf(
    SoundChannel.RAIN,
    SoundChannel.WIND,
    SoundChannel.OCEAN,
    SoundChannel.CAMPFIRE,
)

/** 室内组：翻书 / 键盘 / 远处交谈 */
private val INDOOR_CHANNELS = listOf(
    SoundChannel.PAGE_FLIP,
    SoundChannel.KEYBOARD,
    SoundChannel.MURMUR,
)

/** 声道的视觉映射（图标 + 六色浅底），在组合层一次性解析 */
private data class ChannelVisuals(
    val channel: SoundChannel,
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val iconTint: Color,
)

/**
 * 声景混音器页：hero 控制卡（播放 / 停止 + 可视化条形）+ 5 个预设场景 +
 * 10 路声道混音台（开关 + 独立音量）+ 睡眠定时（15 / 30 / 60 分钟）。
 *
 * 进入页面不自动播放，由用户主动开启；离开页面经 onCleared 释放引擎。
 * reduced motion：默认停止 + 用户主动播放；hero 条形动画退化为静止条，
 * 开关滑块经 [motionSpec] 降级为 180ms 颜色过渡，触觉保留。
 */
@Composable
fun SoundscapePage(
    navController: NavHostController,
    viewModel: SoundscapeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val view = currentView()

    // 回调记住：避免重组时新建 lambda 触发无谓重组；触觉在因果明确的时刻发
    val togglePlay = remember {
        {
            view.performHaptic(HapticEffect.MEDIUM)
            viewModel.togglePlay()
        }
    }
    val toggleChannel = remember {
        { channel: SoundChannel ->
            view.performHaptic(HapticEffect.LIGHT)
            viewModel.toggleChannel(channel)
        }
    }
    val changeVolume = remember {
        { channel: SoundChannel, volume: Int ->
            viewModel.setVolume(channel, volume)
        }
    }
    val applyPreset = remember {
        { preset: SoundscapePreset ->
            view.performHaptic(HapticEffect.SELECTION)
            viewModel.applyPreset(preset)
        }
    }
    val selectTimer = remember {
        { minutes: Int ->
            view.performHaptic(HapticEffect.SELECTION)
            viewModel.setTimer(minutes)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background),
    ) {
        PageHeader(
            title = stringResource(R.string.lab_soundscape_title),
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
            HeroCard(
                playing = state.playing,
                activePreset = state.activePreset,
                reduceMotion = reduceMotion,
                onTogglePlay = togglePlay,
            )
            PresetSection(
                activePreset = state.activePreset,
                onApply = applyPreset,
            )
            MixerSection(
                channels = state.channels,
                onToggle = toggleChannel,
                onVolumeChange = changeVolume,
            )
            TimerCard(
                playing = state.playing,
                timerMinutes = state.timerMinutes,
                remainingMs = state.timerRemainingMs,
                onSelect = selectTimer,
            )
        }
    }
}

// ==================== hero 控制卡 ====================

/**
 * hero 卡（每页唯一视觉锚点）：品牌渐变底 + 白字 + 白胶囊 CTA。
 * 播放中右侧条形动画（reduced motion 静止）；状态文字与预设名随真实状态变化。
 */
@Composable
private fun HeroCard(
    playing: Boolean,
    activePreset: SoundscapePreset?,
    reduceMotion: Boolean,
    onTogglePlay: () -> Unit,
) {
    val scheme = AppColors.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.radiusXl))
            .background(
                Brush.linearGradient(
                    colors = listOf(scheme.heroGradientStart, scheme.heroGradientEnd),
                ),
            )
            .padding(AppDimens.cardPadding),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (playing) R.string.lab_soundscape_playing
                            else R.string.lab_soundscape_stopped,
                        ),
                        style = AppTextStyles.Headline,
                        color = scheme.onPrimary,
                    )
                    if (activePreset != null) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                        Text(
                            text = presetLabel(activePreset),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(AppDimens.spaceMd))
                SoundscapeBars(
                    playing = playing,
                    reduceMotion = reduceMotion,
                    barColor = scheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            // 白胶囊 CTA（hero 上反相：白底 + 品牌深绿字），48dp 高
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(scheme.onPrimary)
                    .pressable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (playing) R.string.lab_soundscape_stop
                        else R.string.lab_soundscape_play,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primaryDark,
                )
            }
        }
    }
}

/**
 * 声景可视化条形（5 根）：播放中按正弦相位起伏；
 * reduced motion 或停止时退化为静止条（规范 §8：取消无限动画，保留状态可读性）。
 */
@Composable
private fun SoundscapeBars(
    playing: Boolean,
    reduceMotion: Boolean,
    barColor: Color,
) {
    val phase = rememberBarPhase(animate = playing && !reduceMotion)
    Canvas(modifier = Modifier.size(width = 56.dp, height = 40.dp)) {
        val count = 5
        val barWidth = size.width / (count * 2f - 1f)
        val gap = barWidth
        for (i in 0 until count) {
            val center = i.toFloat() / (count - 1).coerceAtLeast(1)
            val wave = sin((center * 1.6f + phase * 2f) * PI.toFloat()).toFloat() * 0.5f + 0.5f
            val barHeight = size.height * (0.25f + 0.75f * wave)
            val left = i * (barWidth + gap)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * 条形相位：仅在 [animate] 为真时启动无限往返动画；
 * 否则不进入动画组合（reduced motion / 停止时零开销静止）。
 */
@Composable
private fun rememberBarPhase(animate: Boolean): Float {
    if (!animate) return 0f
    val transition = rememberInfiniteTransition(label = "soundscapeBars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = pingPongInfinite(SPIN_PERIOD_MS),
        label = "soundscapeBarPhase",
    )
    return phase
}

// ==================== 预设场景 ====================

/** 预设场景横向胶囊 chips：选中 = primary 实底白字，未选中 = surfaceVariant + 描边 */
@Composable
private fun PresetSection(
    activePreset: SoundscapePreset?,
    onApply: (SoundscapePreset) -> Unit,
) {
    val scheme = AppColors.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.lab_soundscape_presets_label),
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
            SoundscapePreset.entries.forEach { preset ->
                val selected = preset == activePreset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        .background(
                            if (selected) scheme.primary else scheme.surfaceVariant,
                        )
                        .then(
                            if (selected) {
                                Modifier
                            } else {
                                Modifier.border(
                                    1.dp,
                                    scheme.outline,
                                    RoundedCornerShape(AppDimens.radiusFull),
                                )
                            },
                        )
                        .pressable(onClick = { onApply(preset) })
                        .padding(
                            horizontal = AppDimens.spaceMd,
                            vertical = AppDimens.spaceXs,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = presetLabel(preset),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) scheme.onPrimary else scheme.textPrimary,
                    )
                }
            }
        }
    }
}

// ==================== 混音台 ====================

/** 混音台：底噪 / 自然 / 室内三张分组卡（卡片唯一画法，组内发丝线分隔） */
@Composable
private fun MixerSection(
    channels: List<SoundChannelUiState>,
    onToggle: (SoundChannel) -> Unit,
    onVolumeChange: (SoundChannel, Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
    ) {
        MixerGroup(
            title = stringResource(R.string.lab_soundscape_group_noise),
            channels = channels,
            groupChannels = NOISE_CHANNELS,
            onToggle = onToggle,
            onVolumeChange = onVolumeChange,
        )
        MixerGroup(
            title = stringResource(R.string.lab_soundscape_group_nature),
            channels = channels,
            groupChannels = NATURE_CHANNELS,
            onToggle = onToggle,
            onVolumeChange = onVolumeChange,
        )
        MixerGroup(
            title = stringResource(R.string.lab_soundscape_group_indoor),
            channels = channels,
            groupChannels = INDOOR_CHANNELS,
            onToggle = onToggle,
            onVolumeChange = onVolumeChange,
        )
    }
}

/** 单分组卡：组标题 + 声道行（图标容器 + 名称 + 开关 + 音量滑杆） */
@Composable
private fun MixerGroup(
    title: String,
    channels: List<SoundChannelUiState>,
    groupChannels: List<SoundChannel>,
    onToggle: (SoundChannel) -> Unit,
    onVolumeChange: (SoundChannel, Int) -> Unit,
) {
    val scheme = AppColors.colorScheme
    // 视觉映射在组合层一次性解析（避免逐行重复读 stringResource / 令牌）
    val visuals = groupChannels.map { channel ->
        ChannelVisuals(
            channel = channel,
            label = channelLabel(channel),
            icon = channelIcon(channel),
            containerColor = channelContainerColor(channel),
            iconTint = channelIconTint(channel),
        )
    }
    GroupCard {
        Text(
            text = title,
            style = AppTextStyles.CardTitle,
            color = scheme.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        visuals.forEachIndexed { index, visual ->
            val ui = channels.firstOrNull { it.channel == visual.channel }
                ?: return@forEachIndexed
            if (index > 0) {
                // 0.5dp 发丝线只在同一张卡片内部（规范 §3.4）
                HorizontalDivider(thickness = 0.5.dp, color = scheme.divider)
            }
            ChannelRow(
                name = visual.label,
                icon = visual.icon,
                containerColor = visual.containerColor,
                iconTint = visual.iconTint,
                enabled = ui.enabled,
                volume = ui.volume,
                onToggle = { onToggle(visual.channel) },
                onVolumeChange = { onVolumeChange(visual.channel, it) },
            )
        }
    }
}

/**
 * 单路声道行：32dp 图标容器（v0.4 小容器档 32/12/20）+ 名称 14/500 +
 * 自绘开关；开启后展开独立音量滑杆（0-100）。
 */
@Composable
private fun ChannelRow(
    name: String,
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    enabled: Boolean,
    volume: Int,
    onToggle: () -> Unit,
    onVolumeChange: (Int) -> Unit,
) {
    val scheme = AppColors.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusMd))
                    .background(containerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint,
                )
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.textPrimary,
                modifier = Modifier.weight(1f),
            )
            SoundToggle(checked = enabled, onCheckedChange = onToggle, label = name)
        }
        if (enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VolumeSlider(
                    volume = volume,
                    onVolumeChange = onVolumeChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                Text(
                    text = volume.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = scheme.textSecondary,
                )
            }
        }
    }
}

/**
 * 自绘开关（48×28dp 胶囊）：拇指位置用 Animatable 弹簧过渡；
 * reduced motion 经 [motionSpec] 降级为 180ms 过渡，按压反馈由 pressable 承担。
 */
@Composable
private fun SoundToggle(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    label: String,
) {
    val scheme = AppColors.colorScheme
    val onText = stringResource(R.string.lab_soundscape_on)
    val offText = stringResource(R.string.lab_soundscape_off)
    val position = remember { Animatable(if (checked) 1f else 0f) }
    // 动效规格在组合层解析（LaunchedEffect 块是非组合的 suspend 上下文）
    val toggleSpec = motionSpec(defaultSpring<Float>())
    LaunchedEffect(checked) {
        position.animateTo(if (checked) 1f else 0f, toggleSpec)
    }
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(if (checked) scheme.primary else scheme.surfaceVariant)
            .border(
                1.dp,
                if (checked) scheme.primary else scheme.outline,
                RoundedCornerShape(AppDimens.radiusFull),
            )
            .pressable(onClick = onCheckedChange)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) onText else offText
                role = Role.Switch
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .offset(x = 20.dp * position.value)
                .size(20.dp)
                .clip(CircleShape)
                .background(scheme.surface)
                .border(1.dp, scheme.outline, CircleShape),
        )
    }
}

/**
 * 音量滑杆（自绘，无 Material ripple）：Canvas 轨道 + 拖拽/点击定位。
 * 手势跟随手指，本身无动画；reduced motion 无影响。
 */
@Composable
private fun VolumeSlider(
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val volumeLabel = stringResource(R.string.lab_soundscape_volume_format, volume)
    var dragValue by remember { mutableStateOf<Int?>(null) }
    val value = dragValue ?: volume
    val fraction = value / 100f
    Canvas(
        modifier = modifier
            .semantics { contentDescription = volumeLabel }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onVolumeChange((f * 100).toInt())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragValue = ((offset.x / size.width).coerceIn(0f, 1f) * 100).toInt()
                    },
                    onHorizontalDrag = { change, _ ->
                        dragValue = ((change.position.x / size.width).coerceIn(0f, 1f) * 100).toInt()
                        change.consume()
                    },
                    onDragEnd = {
                        dragValue?.let { onVolumeChange(it) }
                        dragValue = null
                    },
                    onDragCancel = { dragValue = null },
                )
            },
    ) {
        val trackHeight = 4.dp.toPx()
        val trackY = size.height / 2f
        drawRoundRect(
            color = scheme.surfaceVariant,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )
        drawRoundRect(
            color = scheme.primary,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(size.width * fraction, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )
        val thumbX = size.width * fraction
        val thumbRadius = 8.dp.toPx()
        drawCircle(color = scheme.surface, radius = thumbRadius, center = Offset(thumbX, trackY))
        drawCircle(
            color = scheme.primary,
            radius = thumbRadius,
            center = Offset(thumbX, trackY),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

// ==================== 睡眠定时 ====================

/**
 * 睡眠定时卡：关闭 / 15 / 30 / 60 分钟四档；仅播放中可设置。
 * 选中档显示剩余时间（tnum，每秒刷新）；到点由 VM 先淡出再停止。
 */
@Composable
private fun TimerCard(
    playing: Boolean,
    timerMinutes: Int,
    remainingMs: Long,
    onSelect: (Int) -> Unit,
) {
    val scheme = AppColors.colorScheme
    GroupCard {
        Text(
            text = stringResource(R.string.lab_soundscape_timer_label),
            style = AppTextStyles.CardTitle,
            color = scheme.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
        ) {
            SoundscapeViewModel.TIMER_OPTIONS.forEach { minutes ->
                val selected = minutes == timerMinutes
                val enabled = playing || minutes == 0
                val shape = RoundedCornerShape(AppDimens.radiusMd)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(shape)
                        .background(
                            if (selected) scheme.primarySoft else scheme.surfaceVariant,
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, scheme.primary, shape)
                            else Modifier,
                        )
                        .then(
                            if (enabled) Modifier.pressable(onClick = { onSelect(minutes) })
                            else Modifier,
                        )
                        .semantics {
                            if (!enabled) disabled()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (minutes == 0) {
                            stringResource(R.string.lab_soundscape_timer_off)
                        } else {
                            stringResource(
                                R.string.lab_soundscape_timer_minutes_format,
                                minutes,
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            selected -> scheme.onPrimarySoft
                            enabled -> scheme.textSecondary
                            else -> scheme.textTertiary
                        },
                    )
                }
            }
        }
        if (timerMinutes > 0) {
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
            Text(
                text = stringResource(
                    R.string.lab_soundscape_timer_remaining_format,
                    (totalSeconds / 60L).toInt(),
                    (totalSeconds % 60L).toInt(),
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.textSecondary,
            )
        } else if (!playing) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Text(
                text = stringResource(R.string.lab_soundscape_timer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.textTertiary,
            )
        }
    }
}

// ==================== 文案 / 图标 / 令牌映射 ====================

@Composable
private fun presetLabel(preset: SoundscapePreset): String = stringResource(
    when (preset) {
        SoundscapePreset.STUDY -> R.string.lab_soundscape_preset_study
        SoundscapePreset.SLEEP -> R.string.lab_soundscape_preset_sleep
        SoundscapePreset.DORM_NIGHT -> R.string.lab_soundscape_preset_dorm
        SoundscapePreset.CAMPFIRE -> R.string.lab_soundscape_preset_campfire
        SoundscapePreset.OCEAN -> R.string.lab_soundscape_preset_ocean
    },
)

@Composable
private fun channelLabel(channel: SoundChannel): String = stringResource(
    when (channel) {
        SoundChannel.WHITE_NOISE -> R.string.lab_soundscape_channel_white
        SoundChannel.PINK_NOISE -> R.string.lab_soundscape_channel_pink
        SoundChannel.BROWN_NOISE -> R.string.lab_soundscape_channel_brown
        SoundChannel.RAIN -> R.string.lab_soundscape_channel_rain
        SoundChannel.WIND -> R.string.lab_soundscape_channel_wind
        SoundChannel.OCEAN -> R.string.lab_soundscape_channel_ocean
        SoundChannel.CAMPFIRE -> R.string.lab_soundscape_channel_campfire
        SoundChannel.PAGE_FLIP -> R.string.lab_soundscape_channel_page
        SoundChannel.KEYBOARD -> R.string.lab_soundscape_channel_keyboard
        SoundChannel.MURMUR -> R.string.lab_soundscape_channel_murmur
    },
)

@Composable
private fun channelIcon(channel: SoundChannel): ImageVector = when (channel) {
    SoundChannel.WHITE_NOISE -> Icons.Rounded.GraphicEq
    SoundChannel.PINK_NOISE -> Icons.Rounded.Tune
    SoundChannel.BROWN_NOISE -> Icons.Rounded.NightsStay
    SoundChannel.RAIN -> Icons.Rounded.WaterDrop
    SoundChannel.WIND -> Icons.Rounded.Air
    SoundChannel.OCEAN -> Icons.Rounded.Waves
    SoundChannel.CAMPFIRE -> Icons.Rounded.LocalFireDepartment
    SoundChannel.PAGE_FLIP -> Icons.AutoMirrored.Rounded.MenuBook
    SoundChannel.KEYBOARD -> Icons.Rounded.Keyboard
    SoundChannel.MURMUR -> Icons.Rounded.Groups
}

@Composable
private fun channelContainerColor(channel: SoundChannel): Color {
    val scheme = AppColors.colorScheme
    return when (channel) {
        SoundChannel.WHITE_NOISE -> scheme.iconContainerBlue
        SoundChannel.PINK_NOISE -> scheme.iconContainerPurple
        SoundChannel.BROWN_NOISE -> scheme.iconContainerOrange
        SoundChannel.RAIN -> scheme.iconContainerBlue
        SoundChannel.WIND -> scheme.iconContainerTeal
        SoundChannel.OCEAN -> scheme.iconContainerBlue
        SoundChannel.CAMPFIRE -> scheme.iconContainerOrange
        SoundChannel.PAGE_FLIP -> scheme.iconContainerPurple
        SoundChannel.KEYBOARD -> scheme.iconContainerTeal
        SoundChannel.MURMUR -> scheme.iconContainerPink
    }
}

@Composable
private fun channelIconTint(channel: SoundChannel): Color {
    val scheme = AppColors.colorScheme
    return when (channel) {
        SoundChannel.WHITE_NOISE -> scheme.iconForegroundBlue
        SoundChannel.PINK_NOISE -> scheme.iconForegroundPurple
        SoundChannel.BROWN_NOISE -> scheme.iconForegroundOrange
        SoundChannel.RAIN -> scheme.iconForegroundBlue
        SoundChannel.WIND -> scheme.iconForegroundTeal
        SoundChannel.OCEAN -> scheme.iconForegroundBlue
        SoundChannel.CAMPFIRE -> scheme.iconForegroundOrange
        SoundChannel.PAGE_FLIP -> scheme.iconForegroundPurple
        SoundChannel.KEYBOARD -> scheme.iconForegroundTeal
        SoundChannel.MURMUR -> scheme.iconForegroundPink
    }
}
