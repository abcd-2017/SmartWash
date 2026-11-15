package com.smartwash.feature.lab.ui.starmap

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.DarkBackground
import com.smartwash.common.ui.theme.DarkError
import com.smartwash.common.ui.theme.DarkGroupBackground
import com.smartwash.common.ui.theme.DarkIconContainerPurple
import com.smartwash.common.ui.theme.DarkIconForegroundPurple
import com.smartwash.common.ui.theme.DarkOutline
import com.smartwash.common.ui.theme.DarkSurface
import com.smartwash.common.ui.theme.DarkTextPrimary
import com.smartwash.common.ui.theme.DarkTextSecondary
import com.smartwash.common.ui.theme.DarkWarning
import com.smartwash.common.ui.theme.DarkWater
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pingPongInfinite
import com.smartwash.common.utils.pressable
import com.smartwash.feature.lab.R
import kotlinx.coroutines.delay
import kotlin.math.sin

/** 垂直视场角（度）：60° 接近手机主摄视野，星点密度与指向感平衡 */
private const val FOV_DEGREES = 60f

/** 点星命中半径（dp）：≥48dp 触控热区 */
private const val TAP_RADIUS_DP = 48f

/** 星点闪烁周期（ms）：正常态微光呼吸，reduced motion 常亮 */
private const val TWINKLE_PERIOD_MS = 2400

/** 顶部提示停留时长（ms），超时淡出 */
private const val HINT_STAY_MS = 4000L

/**
 * 掌上星图页：全屏 Canvas 星空（暗色沉浸式，取 `:common:ui` 暗色令牌）。
 *
 * 交互：
 * - 转动手机环顾夜空（加速度计 + 磁力计 → 旋转矩阵 → 切平面投影）；
 * - 点按星星显示名称 + 星等（取最近星，点空白清除）；
 * - 顶栏夜视模式开关（深红单色，保护暗适应）。
 *
 * 降级：无磁力计 / 加速度计（模拟器、部分设备）时切到静态星空 + 提示文案，不崩。
 *
 * reduced motion：默认静止（不自动旋转）、星点常亮（取消闪烁）、提示常显（取消淡出）。
 * 触觉：点星 LIGHT、夜视切换 SELECTION（reduced motion 下保留）。
 */
@Composable
fun StarMapPage(
    navController: NavHostController,
    viewModel: StarMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val view = currentView()
    val density = LocalDensity.current.density
    val currentDensity by rememberUpdatedState(density)

    // 传感器注册 / 注销跟随页面生命周期
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val fallbackMatrix = remember { StarMapEngine.fallbackRotationMatrix() }
    val rotationMatrix = state.rotationMatrix ?: fallbackMatrix

    // 投影：矩阵 / 画布尺寸变化即重算（纯函数，无副作用）
    val projected = remember(rotationMatrix, canvasSize, density) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) {
            emptyList()
        } else {
            StarMapEngine.projectSky(
                rotationMatrix = rotationMatrix,
                widthPx = canvasSize.width.toFloat(),
                heightPx = canvasSize.height.toFloat(),
                fovDegrees = FOV_DEGREES,
                epochMillis = System.currentTimeMillis(),
                starScale = density,
            )
        }
    }
    val currentProjected by rememberUpdatedState(projected)

    // 星点闪烁：全局呼吸因子（AnimationUtils vocabulary 的 ping-pong 无限循环）；
    // reduced motion 下直接定格 1f（常亮，且无逐帧重绘）。单星相位由名称散列错峰。
    val twinkle = remember { Animatable(1f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            twinkle.snapTo(1f)
        } else {
            twinkle.animateTo(0.82f, animationSpec = pingPongInfinite(TWINKLE_PERIOD_MS))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val star = StarMapEngine.findNearest(
                            stars = currentProjected,
                            x = offset.x,
                            y = offset.y,
                            maxDistancePx = TAP_RADIUS_DP * currentDensity,
                        )
                        if (star != null) {
                            viewModel.selectStar(star.name, star.magnitude)
                            view.performHaptic(HapticEffect.LIGHT)
                        } else {
                            viewModel.clearSelection()
                        }
                    }
                },
        ) {
            // 夜空底：暗色令牌纵向微渐变（上深下略亮）
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkGroupBackground),
                ),
                size = size,
            )

            projected.forEach { star ->
                val isSelected = star.name == state.selectedName
                val color = when {
                    state.nightVision -> DarkError
                    star.kind == StarMapEngine.Kind.SUN -> DarkWarning
                    star.kind == StarMapEngine.Kind.MOON -> DarkTextSecondary
                    star.kind == StarMapEngine.Kind.PLANET -> DarkWater
                    else -> DarkTextPrimary
                }
                val starTwinkle = if (reduceMotion) {
                    1f
                } else {
                    val phase = (star.name.hashCode() and 0xFF) / 255f * (2f * Math.PI.toFloat())
                    0.82f + 0.18f * sin(twinkle.value * 2f * Math.PI.toFloat() + phase)
                }
                val starAlpha = (star.alpha * starTwinkle).coerceIn(0f, 1f)
                val center = Offset(star.x, star.y)
                drawCircle(
                    color = color.copy(alpha = starAlpha),
                    radius = star.radiusPx,
                    center = center,
                )
                // 太阳 / 月亮加一圈细环，便于在星点中识别
                if (star.kind == StarMapEngine.Kind.SUN || star.kind == StarMapEngine.Kind.MOON) {
                    drawCircle(
                        color = color.copy(alpha = starAlpha * 0.55f),
                        radius = star.radiusPx + 3.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                // 选中态：品牌水色描边环（夜视模式跟随红色滤镜）
                if (isSelected) {
                    drawCircle(
                        color = (if (state.nightVision) DarkError else DarkWater).copy(alpha = 0.95f),
                        radius = star.radiusPx + 6.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            // 夜视模式：整体罩一层深红滤镜（保护暗适应）
            if (state.nightVision) {
                drawRect(color = DarkError.copy(alpha = 0.12f), size = size)
            }
        }

        // 顶栏 + 提示（覆盖在星空之上）
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            StarMapHeader(
                nightVision = state.nightVision,
                onBack = { navController.navigateUp() },
                onToggleNightVision = {
                    viewModel.toggleNightVision()
                    view.performHaptic(HapticEffect.SELECTION)
                },
            )
            Column(
                modifier = Modifier.padding(top = AppDimens.spaceSm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state.sensorsAvailable) {
                    true -> StarMapHint(
                        text = stringResource(R.string.lab_star_map_hint),
                        reduceMotion = reduceMotion,
                        nightVision = state.nightVision,
                    )
                    false -> StarMapHint(
                        // 降级：传感器不可用，常显提示（不淡出）
                        text = stringResource(R.string.lab_star_map_sensor_unavailable),
                        reduceMotion = true,
                        nightVision = state.nightVision,
                    )
                    null -> Unit
                }
            }
        }

        // 选中天体信息卡（卡片唯一画法的暗色等价：暗面 + 描边 + 圆角 + cardPadding）
        val selectedName = state.selectedName
        if (selectedName != null) {
            SelectedStarCard(
                name = selectedName,
                magnitude = state.selectedMagnitude,
                nightVision = state.nightVision,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // edge-to-edge 下信息卡避让系统手势区（BottomCenter 紧贴屏底最易被压住）
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.pagePadding)
                    .padding(bottom = AppDimens.spaceXl),
            )
        }
    }
}

/**
 * 顶栏（暗色沉浸式自定义页头，布局与 `PageHeader` 一致：48dp 返回热区 + 22sp 标题）：
 * 返回箭头 + 标题 + 夜视模式开关。
 */
@Composable
private fun StarMapHeader(
    nightVision: Boolean,
    onBack: () -> Unit,
    onToggleNightVision: () -> Unit,
) {
    val contentColor = if (nightVision) DarkError else DarkTextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.72f))
            .statusBarsPadding()
            .padding(
                start = 12.dp,
                end = AppDimens.pagePadding,
                top = 2.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.lab_star_map_back),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
        }
        Text(
            text = stringResource(R.string.lab_star_map_title),
            style = AppTextStyles.PageHeader,
            color = contentColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        NightVisionToggle(
            nightVision = nightVision,
            onToggle = onToggleNightVision,
        )
    }
}

/**
 * 夜视模式开关：36dp 图标容器（v0.4 §3.2 次容器档：36 / 12 / 20）+ 紫浅底，
 * pressable 无 ripple；开启时图标变红（深红滤镜状态指示）。
 */
@Composable
private fun NightVisionToggle(
    nightVision: Boolean,
    onToggle: () -> Unit,
) {
    val description = stringResource(
        if (nightVision) R.string.lab_star_map_night_vision_on
        else R.string.lab_star_map_night_vision_off
    )
    Box(
        modifier = Modifier
            // pressable 前置 + 视觉容器外 padding 外扩触控热区：36dp 次容器档视觉不变，
            // 命中区 36+16=52（§3.2 此类开关须保证 48dp 热区）
            .pressable(onClick = onToggle)
            .padding(AppDimens.spaceXs)
            .size(36.dp)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(DarkIconContainerPurple)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.NightsStay,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (nightVision) DarkError else DarkIconForegroundPurple,
        )
    }
}

/**
 * 顶部提示胶囊：正常态停留 [HINT_STAY_MS] 后淡出（退出加速档）；
 * reduced motion 常显（规范 §8：取消淡出动画、保留文字可读性）。
 */
@Composable
private fun StarMapHint(
    text: String,
    reduceMotion: Boolean,
    nightVision: Boolean = false,
) {
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
        label = "starMapHintAlpha",
    )
    if (alpha > 0f) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (nightVision) DarkError else DarkTextSecondary,
            modifier = Modifier
                .alpha(alpha)
                .background(DarkSurface.copy(alpha = 0.85f), RoundedCornerShape(AppDimens.radiusFull))
                .border(1.dp, DarkOutline, RoundedCornerShape(AppDimens.radiusFull))
                .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
        )
    }
}

/**
 * 选中天体信息卡：44dp 图标容器（紫浅底，观象台语义）+ 名称（CardTitle）+
 * 星等（caption + tnum）。暗色卡片等价画法（暗面 + 描边 + radiusLg + cardPadding）。
 */
@Composable
private fun SelectedStarCard(
    name: String,
    magnitude: Float,
    nightVision: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleColor = if (nightVision) DarkError else DarkTextPrimary
    val subColor = if (nightVision) DarkError.copy(alpha = 0.8f) else DarkTextSecondary
    val iconTint = if (nightVision) DarkError else DarkIconForegroundPurple
    Row(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(AppDimens.radiusLg))
            .border(1.dp, DarkOutline, RoundedCornerShape(AppDimens.radiusLg))
            .padding(AppDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                .background(DarkIconContainerPurple),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
        }
        Column {
            Text(
                text = name,
                style = AppTextStyles.CardTitle,
                color = titleColor,
            )
            Text(
                text = stringResource(R.string.lab_star_map_magnitude_format, magnitude),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = subColor,
            )
        }
    }
}
