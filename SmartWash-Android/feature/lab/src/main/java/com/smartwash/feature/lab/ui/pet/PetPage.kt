package com.smartwash.feature.lab.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColorScheme
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** 精灵画布尺寸（dp）——本屏唯一的视觉锚点 */
private val PET_CANVAS_SIZE = 220.dp

/** 状态机 tick 周期（ms）：驱动限时状态回 idle、idle 超时入睡 */
private const val PET_TICK_MS = 1_000L

/**
 * 泡泡精灵页：一只住在洗衣机里的像素泡泡精灵。
 *
 * 结构：标准信息卡（白 surface + 1px outline + level1 阴影 + radiusLg + cardPadding，
 * 规范 §3.1 唯一画法）内嵌 surfaceVariant 浅底水池 + 220dp Canvas 精灵（视觉锚点）；
 * 卡下「喂食 / 玩耍」双按钮 + 一句「点它抚摸」引导。
 *
 * 互动：点精灵 = 抚摸（HAPPY + 计数）、喂食（EATING）、玩耍（PLAYING）；
 * 累计互动次数达阈值进化（小泡泡 → 泡沫精灵 → 彩虹泡泡），计数经 DataStore 持久化。
 * 放置回报只出一句文案（按距上次见面时长分档），不伪造数值成长。
 *
 * reduced motion：默认静帧；仅用户主动「玩耍」给一次轻微弹跳；进化直接切终态（规范 §8）。
 * 组合期无副作用：tick 与动画均在 LaunchedEffect 内，触觉在点击回调里发。
 */
@Composable
fun PetPage(
    navController: NavHostController,
    viewModel: PetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val view = currentView()

    // 时间推进：页面退出即取消（不空转）
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.tick()
            delay(PET_TICK_MS)
        }
    }

    val onInteract: (PetAction) -> Unit = { action ->
        when (val result = viewModel.interact(action)) {
            is PetInteractResult.Accepted -> view.performHaptic(
                when {
                    result.evolved -> HapticEffect.SUCCESS
                    action == PetAction.PET -> HapticEffect.LIGHT
                    else -> HapticEffect.SELECTION
                },
            )
            // 同动作窗口内重复触发：无触觉、无动画、不计数
            PetInteractResult.Debounced -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background),
    ) {
        PageHeader(
            title = stringResource(R.string.lab_pet_title),
            onBack = { navController.navigateUp() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // 顶部留白：verticalScroll 会裁到视口边界，卡片外阴影需要容身空间
                .padding(top = AppDimens.spaceSm)
                .padding(horizontal = AppDimens.pagePadding)
                .padding(bottom = AppDimens.sectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
        ) {
            PetCard(
                snapshot = state.snapshot,
                absenceBucket = state.absenceBucket,
                evolveGeneration = state.evolveGeneration,
                reduceMotion = reduceMotion,
                onPet = { onInteract(PetAction.PET) },
            )
            PetActions(
                onFeed = { onInteract(PetAction.FEED) },
                onPlay = { onInteract(PetAction.PLAY) },
            )
            Text(
                text = stringResource(R.string.lab_pet_pet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 精灵信息卡（规范 §3.1 唯一画法）：阶段名（CardTitle）+ 放置回报（body）+
 * 水池浅底里的精灵画布（锚点）+ 累计互动次数（tnum 槽位）。
 */
@Composable
private fun PetCard(
    snapshot: PetSnapshot,
    absenceBucket: AbsenceBucket,
    evolveGeneration: Int,
    reduceMotion: Boolean,
    onPet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stageLabel(snapshot.stage),
                style = AppTextStyles.CardTitle,
                color = scheme.textPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            Text(
                text = absenceLabel(absenceBucket),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.textSecondary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimens.radiusMd))
                    .background(scheme.surfaceVariant)
                    .padding(vertical = AppDimens.spaceMd),
                contentAlignment = Alignment.Center,
            ) {
                PetCanvas(
                    snapshot = snapshot,
                    evolveGeneration = evolveGeneration,
                    reduceMotion = reduceMotion,
                    onPet = onPet,
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Text(
                text = stringResource(R.string.lab_pet_interactions_format, snapshot.interactionCount),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.textTertiary,
            )
        }
    }
}

/** 喂食 / 玩耍双按钮（48dp 胶囊，规范 §3.3；按压反馈由 AppButton 内置 pressable 承担） */
@Composable
private fun PetActions(
    onFeed: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
    ) {
        AppButton(
            text = stringResource(R.string.lab_pet_feed),
            onClick = onFeed,
            variant = AppButtonVariant.SECONDARY,
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = stringResource(R.string.lab_pet_play),
            onClick = onPlay,
            variant = AppButtonVariant.PRIMARY,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 精灵画布：Canvas 绘制圆形泡泡身体 + 高光 + 表情 + 阶段小部件。
 * 互动反应（happy 挤压拉伸 / eating 咀嚼 / playing 弹跳）走 [defaultSpring]（规范 §7：
 * 位移/缩放一律弹簧）；进化走 [evolveGeneration] 驱动的一次性放大回弹。
 * reduced motion：reaction 静帧，仅「玩耍」保留一次轻弹跳；进化直接切终态。
 */
@Composable
private fun PetCanvas(
    snapshot: PetSnapshot,
    evolveGeneration: Int,
    reduceMotion: Boolean,
    onPet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val reaction = remember { Animatable(0f) }
    val evolveScale = remember { Animatable(1f) }
    val reactionSpec = motionSpec<Float>(defaultSpring())
    val description = stringResource(
        R.string.lab_pet_canvas_description,
        stageLabel(snapshot.stage),
        stateLabel(snapshot.state),
    )

    LaunchedEffect(snapshot.state, snapshot.interactionCount, reduceMotion) {
        if (reduceMotion) {
            // 默认静帧；仅用户主动「玩耍」才动（一次轻微弹跳）
            if (snapshot.state == PetState.PLAYING) {
                reaction.snapTo(0f)
                reaction.animateTo(1f, reactionSpec)
                reaction.animateTo(0f, reactionSpec)
            } else {
                reaction.snapTo(0f)
            }
            return@LaunchedEffect
        }
        when (snapshot.state) {
            PetState.IDLE, PetState.SLEEPING -> reaction.snapTo(0f)
            else -> {
                reaction.snapTo(0f)
                reaction.animateTo(1f, reactionSpec)
                reaction.animateTo(0f, reactionSpec)
            }
        }
    }

    LaunchedEffect(evolveGeneration, reduceMotion) {
        if (evolveGeneration <= 0) return@LaunchedEffect
        if (reduceMotion) {
            // 进化动画在 reduceMotion 下直接切终态
            evolveScale.snapTo(1f)
            return@LaunchedEffect
        }
        evolveScale.snapTo(1f)
        evolveScale.animateTo(1.28f, reactionSpec)
        evolveScale.animateTo(1f, reactionSpec)
    }

    Canvas(
        modifier = modifier
            .size(PET_CANVAS_SIZE)
            .pressable(onClick = onPet)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        drawPet(scheme, snapshot, reaction.value, evolveScale.value, reduceMotion)
    }
}

/**
 * 精灵绘制：水面圆底盘 + 阶段小部件 + 泡泡身体（径向渐变）+ 高光 + 表情。
 * 色值全部引用清氧令牌（surface/waterSoft/water/primary/ongoing/danger/iconForegroundPurple）。
 */
private fun DrawScope.drawPet(
    scheme: AppColorScheme,
    snapshot: PetSnapshot,
    reaction: Float,
    evolveScale: Float,
    reduceMotion: Boolean,
) {
    val stage = snapshot.stage
    val state = snapshot.state
    val baseRadius = min(size.width, size.height) / 2f * 0.62f
    val radius = baseRadius * stage.scaleFactor()

    // 反应动画（reaction 0→1→0）：happy 挤压拉伸、eating 咀嚼、playing 弹跳
    val pulse = sin(reaction * PI).toFloat()
    var scaleX = 1f
    var scaleY = 1f
    var transY = 0f
    when {
        state == PetState.HAPPY -> {
            scaleX = 1f + 0.12f * pulse
            scaleY = 1f + 0.12f * pulse
        }
        state == PetState.EATING -> {
            scaleX = 1f + 0.10f * pulse
            scaleY = 1f - 0.12f * pulse
        }
        state == PetState.PLAYING -> {
            // reduced motion 下仅保留用户主动「玩耍」的一次轻弹跳
            transY = -(if (reduceMotion) 10f else 28f) * pulse
        }
    }

    // 水面圆底盘（托住精灵，强化「住在洗衣机里」的意象）
    drawCircle(
        color = scheme.waterSoft.copy(alpha = 0.45f),
        radius = size.minDimension / 2f * 0.94f,
        center = center,
    )

    withTransform({
        translate(left = center.x, top = center.y + transY)
        scale(scaleX * evolveScale, scaleY * evolveScale, pivot = Offset.Zero)
    }) {
        drawStageAccessory(scheme, stage, radius)

        // 泡泡身体：径向渐变（高光偏左上，边缘偏 water 深色）
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    scheme.surface,
                    scheme.waterSoft,
                    scheme.water.copy(alpha = 0.55f),
                ),
                center = Offset(-radius * 0.3f, -radius * 0.3f),
                radius = radius * 1.6f,
            ),
            radius = radius,
            center = Offset.Zero,
        )
        drawCircle(
            color = scheme.water.copy(alpha = 0.35f),
            radius = radius,
            center = Offset.Zero,
            style = Stroke(width = 1.dp.toPx()),
        )
        // 高光
        drawCircle(
            color = scheme.surface.copy(alpha = 0.8f),
            radius = radius * 0.16f,
            center = Offset(-radius * 0.34f, -radius * 0.34f),
        )
        drawFace(scheme, state, radius)
    }
}

/**
 * 阶段小部件（小泡泡无装饰；泡沫精灵加三颗泡沫；彩虹泡泡加五色环）：
 * 进化不只是换名，也是可见的外形成长。
 */
private fun DrawScope.drawStageAccessory(scheme: AppColorScheme, stage: PetStage, radius: Float) {
    when (stage) {
        PetStage.BUBBLE -> Unit
        PetStage.FOAM_SPIRIT -> {
            val stroke = 1.dp.toPx()
            listOf(
                Offset(-radius * 1.22f, -radius * 0.55f) to radius * 0.22f,
                Offset(radius * 1.12f, -radius * 0.78f) to radius * 0.16f,
                Offset(radius * 1.32f, radius * 0.32f) to radius * 0.26f,
            ).forEach { (foamCenter, foamRadius) ->
                drawCircle(color = scheme.waterSoft, radius = foamRadius, center = foamCenter)
                drawCircle(
                    color = scheme.water.copy(alpha = 0.5f),
                    radius = foamRadius,
                    center = foamCenter,
                    style = Stroke(width = stroke),
                )
            }
        }
        PetStage.RAINBOW_BUBBLE -> {
            val ringColors = listOf(
                scheme.danger,
                scheme.ongoing,
                scheme.primary,
                scheme.water,
                scheme.iconForegroundPurple,
            )
            val ringRadius = radius * 1.18f
            val sweep = 360f / ringColors.size
            ringColors.forEachIndexed { index, color ->
                drawArc(
                    color = color,
                    startAngle = -90f + index * sweep,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(-ringRadius, -ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = radius * 0.14f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** 表情：眼睛 + 嘴随状态切换（idle 点眼微笑 / happy·playing ^眼大笑 / eating 张口 / sleeping 闭眼 + Z） */
private fun DrawScope.drawFace(scheme: AppColorScheme, state: PetState, radius: Float) {
    val ink = scheme.textPrimary
    val eyeY = -radius * 0.12f
    val eyeDX = radius * 0.32f
    val eyeR = radius * 0.075f
    val faceStroke = Stroke(width = radius * 0.05f, cap = StrokeCap.Round)

    when (state) {
        PetState.HAPPY, PetState.PLAYING -> {
            listOf(-eyeDX, eyeDX).forEach { cx ->
                val path = Path().apply {
                    moveTo(cx - eyeR * 1.4f, eyeY + eyeR)
                    lineTo(cx, eyeY - eyeR * 1.2f)
                    lineTo(cx + eyeR * 1.4f, eyeY + eyeR)
                }
                drawPath(path, color = ink, style = faceStroke)
            }
        }
        PetState.SLEEPING -> {
            listOf(-eyeDX, eyeDX).forEach { cx ->
                drawArc(
                    color = ink,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - eyeR * 1.6f, eyeY - eyeR),
                    size = Size(eyeR * 3.2f, eyeR * 2.4f),
                    style = faceStroke,
                )
            }
        }
        PetState.IDLE, PetState.EATING -> {
            listOf(-eyeDX, eyeDX).forEach { cx ->
                drawCircle(color = ink, radius = eyeR, center = Offset(cx, eyeY))
            }
        }
    }

    val mouthY = radius * 0.22f
    when (state) {
        PetState.EATING -> {
            drawCircle(color = ink, radius = radius * 0.13f, center = Offset(0f, mouthY))
        }
        PetState.HAPPY, PetState.PLAYING -> {
            drawArc(
                color = ink,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(-radius * 0.28f, mouthY - radius * 0.22f),
                size = Size(radius * 0.56f, radius * 0.44f),
                style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round),
            )
        }
        PetState.SLEEPING -> {
            drawArc(
                color = ink,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(-radius * 0.14f, mouthY - radius * 0.1f),
                size = Size(radius * 0.28f, radius * 0.2f),
                style = Stroke(width = radius * 0.05f, cap = StrokeCap.Round),
            )
            drawSleepZ(scheme, radius)
        }
        PetState.IDLE -> {
            drawArc(
                color = ink,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(-radius * 0.2f, mouthY - radius * 0.14f),
                size = Size(radius * 0.4f, radius * 0.28f),
                style = Stroke(width = radius * 0.05f, cap = StrokeCap.Round),
            )
        }
    }
}

/** 睡眠小「Z」（textTertiary，避免与表情抢视线） */
private fun DrawScope.drawSleepZ(scheme: AppColorScheme, radius: Float) {
    val zx = radius * 0.5f
    val zy = -radius * 0.72f
    val s = radius * 0.16f
    val path = Path().apply {
        moveTo(zx, zy)
        lineTo(zx + s, zy)
        lineTo(zx, zy + s)
        lineTo(zx + s, zy + s)
    }
    drawPath(
        path,
        color = scheme.textTertiary,
        style = Stroke(width = radius * 0.045f, cap = StrokeCap.Round),
    )
}

/** 阶段体型系数（进化可见：小泡泡最小、彩虹泡泡最大） */
private fun PetStage.scaleFactor(): Float = when (this) {
    PetStage.BUBBLE -> 0.82f
    PetStage.FOAM_SPIRIT -> 0.95f
    PetStage.RAINBOW_BUBBLE -> 1.08f
}

@Composable
private fun stageLabel(stage: PetStage): String = when (stage) {
    PetStage.BUBBLE -> stringResource(R.string.lab_pet_stage_bubble)
    PetStage.FOAM_SPIRIT -> stringResource(R.string.lab_pet_stage_foam)
    PetStage.RAINBOW_BUBBLE -> stringResource(R.string.lab_pet_stage_rainbow)
}

@Composable
private fun stateLabel(state: PetState): String = when (state) {
    PetState.IDLE -> stringResource(R.string.lab_pet_state_idle)
    PetState.HAPPY -> stringResource(R.string.lab_pet_state_happy)
    PetState.EATING -> stringResource(R.string.lab_pet_state_eating)
    PetState.PLAYING -> stringResource(R.string.lab_pet_state_playing)
    PetState.SLEEPING -> stringResource(R.string.lab_pet_state_sleeping)
}

@Composable
private fun absenceLabel(bucket: AbsenceBucket): String = when (bucket) {
    AbsenceBucket.FIRST_MEET -> stringResource(R.string.lab_pet_absence_first)
    AbsenceBucket.JUST_NOW -> stringResource(R.string.lab_pet_absence_just_now)
    AbsenceBucket.SHORT -> stringResource(R.string.lab_pet_absence_short)
    AbsenceBucket.LONG -> stringResource(R.string.lab_pet_absence_long)
    AbsenceBucket.VERY_LONG -> stringResource(R.string.lab_pet_absence_very_long)
}
