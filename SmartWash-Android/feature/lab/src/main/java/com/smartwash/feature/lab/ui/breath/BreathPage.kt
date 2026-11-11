package com.smartwash.feature.lab.ui.breath

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppPillTabRow
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.feature.lab.R

/** 呼吸圆环画布边长 */
private val BREATH_RING_SIZE = 240.dp

/** 呼吸圆基准半径占画布比例（放大 1.4 倍后仍不出轨道环外） */
private const val BASE_RADIUS_RATIO = 0.32f

/** 吸气满相放大倍数（相位目标的单一事实，页面内图形语义常量） */
private const val SCALE_INHALED = 1.4f

/**
 * reduced motion 呼吸圆固定尺寸倍数（规范 §8：圆环静止在中等尺寸，
 * 图形完整可读，仅文字与倒计时推进、触觉保留）。
 */
private const val SCALE_REDUCE_MOTION = 0.75f

/**
 * 圆心文字底衬的平台占比（径向渐变 0~该值内为纯色平台，之后渐隐至透明）。
 * 相位文字为 2 字（半宽 ≈19dp）+ 倒计时数字（半高 ≈28dp），主笔画最远角 ≈29dp，
 * 恰落在平台内；2 字相位的最远角 ≈34dp（88% 处）仍有约半强度底衬。
 */
private const val TEXT_PLATE_SOLID_RATIO = 0.75f

/**
 * 呼吸引导页：模式选择（4-7-8 睡眠 / Box 4-4-4-4 专注，胶囊 chips）→
 * 全屏引导：中心 Canvas 圆环随相位放大/保持/缩小 + 相位文字 +
 * 倒计时数字（tnum）+ 相位切换轻触觉；可暂停/停止。
 * 相位状态机在 [BreathViewModel]（协程 tick 驱动）。
 */
@Composable
fun BreathPage(
    navController: NavHostController,
    viewModel: BreathViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val mode = state.mode ?: BreathMode.SLEEP

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.lab_breath_title),
            onBack = { navController.navigateUp() },
        )
        if (!state.running) {
            ModeSelector(
                selectedMode = mode,
                onSelect = viewModel::selectMode,
                onStart = { viewModel.start(mode) },
                // 占满 PageHeader 之外剩余高度（与 GuideStage 同口径）：
                // wrap 高度下组件内 Spacer(weight) 分得 0 高，开始按钮会紧贴节奏提示而非落底
                modifier = Modifier.weight(1f),
            )
        } else {
            GuideStage(
                state = state,
                onPauseResume = { if (state.paused) viewModel.resume() else viewModel.pause() },
                onStop = viewModel::stop,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/**
 * 模式选择态：两个胶囊 chip（[AppPillTabRow]）+ 一句节奏提示 + 开始按钮。
 * [modifier] 由调用处传 weight(1f)：内部 Spacer(weight) 依赖父级给出确定高度才能推底。
 */
@Composable
private fun ModeSelector(
    selectedMode: BreathMode,
    onSelect: (BreathMode) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppDimens.spaceSm),
    ) {
        AppPillTabRow(
            tabs = listOf(
                stringResource(R.string.lab_breath_mode_sleep),
                stringResource(R.string.lab_breath_mode_box),
            ),
            selectedIndex = selectedMode.ordinal,
            onSelect = { index -> onSelect(BreathMode.entries[index]) },
        )
        Text(
            text = when (selectedMode) {
                BreathMode.SLEEP -> stringResource(R.string.lab_breath_mode_sleep_hint)
                BreathMode.BOX -> stringResource(R.string.lab_breath_mode_box_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textSecondary,
            modifier = Modifier
                .padding(horizontal = AppDimens.pagePadding)
                .padding(top = AppDimens.spaceXs),
        )
        Spacer(modifier = Modifier.weight(1f))
        AppButton(
            text = stringResource(R.string.lab_breath_start),
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXl))
    }
}

/**
 * 引导态：中心圆环 Canvas（水色令牌，吸气放大/屏息保持/呼气缩小）+
 * 相位文字与倒计时数字 + 完成轮次 + 暂停/继续与停止控制。
 */
@Composable
private fun GuideStage(
    state: BreathUiState,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = currentView()
    val reduceMotion = LocalReduceMotion.current
    val phase = state.phase
    val phaseType = phase?.type

    // 相位切换轻触觉（开始首个相位同样给一次——吸气起始的因果反馈；reduced motion 保留）
    LaunchedEffect(state.phaseIndex, state.running) {
        if (state.running) view.performHaptic(HapticEffect.LIGHT)
    }

    // 呼吸圆缩放：相位目标经 Animatable 插值，时长取 VM 剩余量（暂停冻结/恢复续播天然成立）
    val scale = remember { Animatable(1f) }
    LaunchedEffect(phaseType, state.phaseIndex, state.paused, state.running, reduceMotion) {
        if (reduceMotion) {
            // 规范 §8 降级：圆环静止在中等尺寸，仅文字/倒计时/触觉推进
            scale.snapTo(SCALE_REDUCE_MOTION)
            return@LaunchedEffect
        }
        if (!state.running || state.paused) return@LaunchedEffect
        when (phaseType) {
            BreathPhaseType.INHALE -> scale.animateTo(
                SCALE_INHALED,
                tween(state.remainingMs.toInt(), easing = LinearEasing),
            )
            BreathPhaseType.EXHALE -> scale.animateTo(
                1f,
                tween(state.remainingMs.toInt(), easing = LinearEasing),
            )
            // 屏息/尾保持：停在当前值
            else -> Unit
        }
    }

    // 令牌在组合层读取（Canvas 绘制块非 @Composable 上下文）
    val scheme = AppColors.colorScheme

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(BREATH_RING_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            // 圆环 Canvas（水色令牌系：轨道 waterSoft，呼吸圆 water→waterSoft 径向渐变）
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseR = size.minDimension / 2f * BASE_RADIUS_RATIO
                drawCircle(
                    color = scheme.waterSoft,
                    radius = baseR * SCALE_INHALED + 4.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(scheme.water, scheme.waterSoft),
                        center = center,
                        radius = baseR * SCALE_INHALED,
                    ),
                    radius = baseR * scale.value,
                )
                // 圆心文字底衬（水色晕）：呼吸圆中心为纯 water 色，深色板上
                // textPrimary（#E6E9E6）对 water（#5FAEE0）仅 1.99:1，倒计时/相位文字不可读。
                // 渐变 water↔waterSoft 亮度跨约 15 倍，任何单一文字色都无法在全区间达标，
                // 故以 waterSoft 中心晕把文字区背景固定到可控色上（双板通用，无需主题分支）：
                // 深板圆心 textPrimary 对 waterSoft(#1C2F3B) ≈11.3:1、渐隐缘 ≈6.5:1；
                // 浅板对 waterSoft(#DCEFF7) ≈14.4:1、渐隐缘 ≥5.8:1，两板均达标。
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to scheme.waterSoft,
                        TEXT_PLATE_SOLID_RATIO to scheme.waterSoft,
                        1f to scheme.waterSoft.copy(alpha = 0f),
                        center = center,
                        radius = baseR,
                    ),
                    radius = baseR,
                )
            }
            // 圆心：相位文字 + 倒计时数字（数字槽 tnum）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = phase?.type?.let { phaseLabel(it) }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colorScheme.textPrimary,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                Text(
                    text = (((state.remainingMs + 999) / 1000).coerceAtLeast(1)).toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        // 完成轮次（数量槽位 tnum）
        Text(
            text = stringResource(R.string.lab_breath_cycles_format, state.cycles),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = AppColors.colorScheme.textSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        // 主控「暂停/继续」占满内容宽（weight 内填充），低频「停止」保留紧凑独立热区：
        // 次按钮变体内置 fillMaxWidth，不加重会把 TEXT 变体「停止」挤出屏（文字不可见、热区被覆盖）；
        // 停止用 48dp 最小热区（TEXT 变体默认 min 44dp，此处抬到触控热区下限，视觉尺寸不变）。
        // 行内补页面左右边距，与 ModeSelector 的开始按钮同口径（原实现整行出血贴边）。
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(
                text = stringResource(
                    if (state.paused) R.string.lab_breath_resume else R.string.lab_breath_pause
                ),
                onClick = onPauseResume,
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(AppDimens.spaceMd))
            AppButton(
                text = stringResource(R.string.lab_breath_stop),
                onClick = onStop,
                variant = AppButtonVariant.TEXT,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceXl))
    }
}

/** 相位文案映射（用户可见文案经 strings.xml） */
@Composable
private fun phaseLabel(type: BreathPhaseType): String = when (type) {
    BreathPhaseType.INHALE -> stringResource(R.string.lab_breath_phase_inhale)
    BreathPhaseType.HOLD -> stringResource(R.string.lab_breath_phase_hold)
    BreathPhaseType.EXHALE -> stringResource(R.string.lab_breath_phase_exhale)
    BreathPhaseType.HOLD_TAIL -> stringResource(R.string.lab_breath_phase_keep)
}
