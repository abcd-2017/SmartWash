package com.smartwash.feature.lab.ui.fish

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.lab.R

/** 鱼形尺寸范围（dp，随机档 4~8） */
private const val FISH_SIZE_MIN_DP = 4f
private const val FISH_SIZE_RANGE_DP = 4f

/** 单帧模拟预算（ms）：超时视为低端机负载重，自动降 tick 率 */
private const val FRAME_BUDGET_MS = 8L

/** 降 tick 率的退避步长与封顶（ms）：连续超预算指数退避，恢复正常帧即恢复 */
private const val FRAME_BACKOFF_STEP_MS = 16L
private const val FRAME_BACKOFF_MAX_MS = 48L

/** 首次进入顶部提示停留时长（ms）——产品规格数字（展示时长，非动画时长） */
private const val HINT_STAY_MS = 3_000L

/**
 * 指尖鱼群页：清氧浅色"水池"（waterSoft 底）+ 约 180 条小鱼 boids 模拟
 * （三规则 + 均匀网格邻居查询，内核 [BoidsSimulator] 纯 Kotlin）。
 * 交互：单指按住=吸引、长按(>400ms)=驱散、双击=径向冲击波炸开再聚拢；
 * 首次进入顶部提示 3s 淡出。tick 由 withFrameNanos 驱动、帧预算超时自动降率；
 * reduced motion：默认静帧 + 用户主动「播放/暂停」开关（触觉保留）。
 */
@Composable
fun FishPage(
    navController: NavHostController,
    viewModel: FishViewModel = hiltViewModel(),
) {
    val sim = viewModel.simulator
    val reduceMotion = LocalReduceMotion.current
    val view = currentView()

    // reduced motion 下由用户主动播放/暂停（静帧为默认）；正常态恒播放
    var manualPlaying by remember { mutableStateOf(false) }
    val playing = if (reduceMotion) manualPlaying else true

    // 画布尺寸注入内核（含首次与旋转重铺）
    var canvasSize by remember { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(canvasSize) {
        val size = canvasSize ?: return@LaunchedEffect
        sim.resize(size.width.toFloat(), size.height.toFloat())
    }

    // 帧驱动：tick + 帧序号（绘制块读 frameTick 建立重绘订阅；暂停即停止重绘=静帧）
    var frameTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var lastFrame = 0L
        var backoff = 0L
        while (true) {
            var overBudget = false
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val dtMs = ((now - lastFrame) / 1_000_000L).coerceIn(1L, 50L)
                    val t0 = System.nanoTime()
                    sim.tick(dtMs)
                    overBudget = System.nanoTime() - t0 > FRAME_BUDGET_MS * 1_000_000L
                }
                lastFrame = now
            }
            frameTick++
            backoff = if (overBudget) {
                (backoff + FRAME_BACKOFF_STEP_MS).coerceAtMost(FRAME_BACKOFF_MAX_MS)
            } else {
                0L
            }
            if (backoff > 0L) delay(backoff)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.lab_fish_title),
            onBack = { navController.navigateUp() },
        )
        // weight(1f) 占据 PageHeader 之外的剩余高度（fillMaxSize 会以整屏约束测量，
        // 叠加 PageHeader 后底部溢出——水池底部与 reduced motion 播放开关被推出屏幕）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = AppDimens.pagePadding),
        ) {
            FishPool(
                sim = sim,
                frameTick = frameTick,
                onPulse = { x, y ->
                    sim.pulse(x, y)
                    view.performHaptic(HapticEffect.MEDIUM)
                },
                onLongPress = { x, y ->
                    sim.engageRepel(x, y)
                    view.performHaptic(HapticEffect.HEAVY)
                },
                onPress = { x, y ->
                    sim.pointerDown(x, y)
                    view.performHaptic(HapticEffect.LIGHT)
                },
                onRelease = { sim.pointerUp() },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it },
            )
            FirstRunHint(reduceMotion = reduceMotion, modifier = Modifier.align(Alignment.TopCenter))
            // reduced motion：用户主动触发的播放/暂停（触觉保留）
            if (reduceMotion) {
                PlayToggleButton(
                    playing = playing,
                    onToggle = {
                        manualPlaying = !manualPlaying
                        view.performHaptic(HapticEffect.SELECTION)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = AppDimens.sectionSpacing),
                )
            }
        }
    }
}

/**
 * 水池画布：waterSoft 浅底 + 鱼形三角（primary/water 令牌色系，4~8dp 随机档，
 * 朝向速度方向）。手势：双击冲击波 / 长按驱散 / 按住吸引（触觉在回调里发）。
 */
@Composable
private fun FishPool(
    sim: BoidsSimulator,
    frameTick: Long,
    onPulse: (Float, Float) -> Unit,
    onLongPress: (Float, Float) -> Unit,
    onPress: (Float, Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 单位鱼形（头朝 +x，长 2.4 / 宽 1.2，绘制时按鱼尺寸缩放）预构一次
    val unitFish = remember {
        Path().apply {
            moveTo(1.2f, 0f)
            lineTo(-1.2f, -0.6f)
            lineTo(-1.2f, 0.6f)
            close()
        }
    }
    // 令牌色组在组合层读取（Canvas 绘制块非 @Composable 上下文）
    val scheme = AppColors.colorScheme
    val fishColors = remember(scheme) {
        listOf(scheme.primary, scheme.water, scheme.primaryDark)
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimens.radiusLg))
            .background(scheme.waterSoft)
            // 1dp outline 描边与沙盘池（SandPage）同画法：waterSoft on background
            // 边界对比仅 ≈1.15:1，无描边时浅色下池界发虚
            .border(1.dp, scheme.outline, RoundedCornerShape(AppDimens.radiusLg))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset -> onPulse(offset.x, offset.y) },
                    onLongPress = { offset -> onLongPress(offset.x, offset.y) },
                    onPress = { offset ->
                        onPress(offset.x, offset.y)
                        try {
                            awaitRelease()
                        } finally {
                            onRelease()
                        }
                    },
                )
            }
    ) {
        frameTick // 读取状态建立每帧重绘订阅（tick 循环暂停后无重绘=静帧）
        sim.boids.forEach { boid ->
            val sizePx = (FISH_SIZE_MIN_DP + boid.sizeFactor * FISH_SIZE_RANGE_DP).dp.toPx()
            withTransform({
                translate(boid.x, boid.y)
                rotate(BoidsSimulator.headingDegrees(boid), pivot = Offset.Zero)
                scale(sizePx, sizePx, pivot = Offset.Zero)
            }) {
                drawPath(unitFish, color = fishColors[boid.colorIndex % fishColors.size])
            }
        }
    }
}

/**
 * 首次进入顶部提示（三种手势）：正常态停留 3s 后淡出（退出加速档）；
 * reduced motion 常显（规范 §8：取消淡出动画、保留文字可读性）。
 */
@Composable
private fun FirstRunHint(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            delay(HINT_STAY_MS)
            visible = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceMotion) tween(CROSSFADE_ALT_MS) else exitTween(DURATION_CARD_MS),
        label = "fishHintAlpha",
    )
    if (alpha > 0f) {
        Text(
            text = stringResource(R.string.lab_fish_hint),
            style = MaterialTheme.typography.labelMedium,
            // 白卡胶囊上的文字用「文字绿」深档 textGreen（R17 裁定，on 白 5.1:1；
            // onPrimarySoft 语义是 primarySoft 浅绿容器配对字色，与此处 surface 白底错位；
            // 色板无 onPrimaryContainer 令牌，编译核实）
            color = AppColors.colorScheme.textGreen,
            modifier = modifier
                .alpha(alpha)
                .background(AppColors.colorScheme.surface, RoundedCornerShape(AppDimens.radiusFull))
                .border(1.dp, AppColors.colorScheme.outline, RoundedCornerShape(AppDimens.radiusFull))
                .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
        )
    }
}

/** reduced motion 播放/暂停开关（12dp 圆角 + pressable 无 ripple；视觉胶囊不变，热区抬至 48dp 下限） */
@Composable
private fun PlayToggleButton(
    playing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    Row(
        modifier = modifier
            // 仅 padding(spaceXs) + 20dp 图标实际高度 ≈36dp，低于 48dp 触控下限，sizeIn 兜底
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(AppDimens.radiusMd))
            .pressable(onClick = onToggle)
            .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = scheme.primary,
        )
        Text(
            text = stringResource(if (playing) R.string.lab_fish_pause else R.string.lab_fish_play),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.textPrimary,
        )
    }
}
