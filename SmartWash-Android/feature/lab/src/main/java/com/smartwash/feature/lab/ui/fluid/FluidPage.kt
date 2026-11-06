package com.smartwash.feature.lab.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppPillTabRow
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColorScheme
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.performHaptic
import com.smartwash.feature.lab.R
import kotlinx.coroutines.delay

/** 首次进入顶部提示停留时长（ms）——产品规格数字（展示时长，非动画时长） */
private const val HINT_STAY_MS = 3_000L

/**
 * 指尖流体页：清氧设计系统画布 + stable fluids 模拟（[FluidRenderer]）。
 * 交互：手指划过注入速度 + 染料（划得越快涡旋越猛）；3 套配色预设（令牌色系）；
 * 清空重来重置种子。reduced motion：默认静帧（静态种子画面）+ 用户主动播放/暂停
 * （触觉保留），与 FishPage 降级模式一致。
 */
@Composable
fun FluidPage(
    navController: NavHostController,
    viewModel: FluidViewModel = hiltViewModel(),
) {
    val preset by viewModel.preset.collectAsState()
    val playing by viewModel.playing.collectAsState()
    val resetToken by viewModel.resetToken.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val scheme = AppColors.colorScheme
    val view = currentView()
    val context = LocalContext.current

    // 正常态恒播放；reduced motion 下由用户主动播放/暂停（静帧为默认）
    val effectivePlaying = if (reduceMotion) playing else true

    // 令牌色在组合层映射为渲染层可用的 RGB（渲染层不写死色值）
    val palette = remember(preset, scheme) { paletteOf(scheme, preset) }
    val isDark = isSystemInDarkTheme()
    val baseColor = remember(scheme, isDark) { colorOf(baseColorOf(isDark, scheme)) }

    // 渲染器唯一实例：经构造注入 GL 视图，页面收集 state / 注入调色板都用同一对象。
    // 若另建实例再在 apply 里写 renderer.palette，本地变量会遮蔽 view.renderer 成员，
    // 注入落到未挂载实例（GL 始终默认黑底白染料、failed 兜底永不触发）。
    val fluidRenderer = remember { FluidRenderer() }
    val renderState by fluidRenderer.state.collectAsState()
    val glView = remember {
        FluidGLSurfaceView(context, fluidRenderer).apply {
            this.renderer.palette = palette
            this.renderer.baseColor = baseColor
            onTouchDown = { view.performHaptic(HapticEffect.LIGHT) }
        }
    }

    // 生命周期：后台/离开页面即暂停 GL 线程，回前台恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 先把 GL 资源释放投递给仍在运行的 GL 线程，再暂停线程（顺序不能反，
            // 否则释放事件永远停在队列里 → GPU 资源泄漏，见 FluidGLSurfaceView.disposeResources）
            glView.disposeResources()
            glView.onPause()
        }
    }

    // 预设 / 主题变化 → 渲染层取最新调色板与底色
    LaunchedEffect(palette) { fluidRenderer.palette = palette }
    LaunchedEffect(baseColor) { fluidRenderer.baseColor = baseColor }
    // 播放意图 → 连续渲染 / 按需渲染（静帧只画一帧）
    LaunchedEffect(effectivePlaying) { glView.setPlaying(effectivePlaying) }
    // 重置信号 → 重铺静态种子（静帧态也会立即刷新一帧）
    LaunchedEffect(resetToken) {
        if (resetToken > 0) glView.resetSimulation()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.lab_fluid_title),
            onBack = { navController.navigateUp() },
        )

        // 视觉锚点：流体画布（圆角裁切，深色底由令牌映射）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    horizontal = AppDimens.pagePadding,
                    vertical = AppDimens.spaceMd,
                )
                .clip(RoundedCornerShape(AppDimens.radiusLg))
        ) {
            AndroidView(
                factory = { glView },
                modifier = Modifier.fillMaxSize(),
            )
            if (renderState.failed) {
                Text(
                    text = stringResource(R.string.lab_fluid_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.textSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            FirstRunHint(
                reduceMotion = reduceMotion,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = AppDimens.spaceMd),
            )
        }

        // 控制区：配色预设（AppPillTabRow 自带页面边距）+ 清空（reduced motion 追加播放/暂停）
        Column(modifier = Modifier.fillMaxWidth()) {
            AppPillTabRow(
                tabs = listOf(
                    stringResource(R.string.lab_fluid_preset_oxygen),
                    stringResource(R.string.lab_fluid_preset_foam),
                    stringResource(R.string.lab_fluid_preset_sand),
                ),
                selectedIndex = preset.ordinal,
                onSelect = { index -> viewModel.selectPreset(FluidPreset.entries[index]) },
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Row(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButton(
                    text = stringResource(R.string.lab_fluid_reset),
                    onClick = {
                        view.performHaptic(HapticEffect.MEDIUM)
                        viewModel.reset()
                    },
                    variant = AppButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
                if (reduceMotion) {
                    Spacer(modifier = Modifier.width(AppDimens.spaceMd))
                    AppButton(
                        text = stringResource(
                            if (playing) R.string.lab_fluid_pause else R.string.lab_fluid_play
                        ),
                        onClick = {
                            view.performHaptic(HapticEffect.SELECTION)
                            viewModel.setPlaying(!playing)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceXl))
        }
    }
}

/**
 * 首次进入顶部提示：正常态停留 3s 后淡出（退出加速档）；
 * reduced motion 常显（规范 §8：取消淡出动画、保留文字可读性）。
 */
@Composable
private fun FirstRunHint(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val scheme = AppColors.colorScheme
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
        label = "fluidHintAlpha",
    )
    if (alpha > 0f) {
        Text(
            text = stringResource(R.string.lab_fluid_hint),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.textPrimary,
            modifier = modifier
                .alpha(alpha)
                .background(scheme.surface, RoundedCornerShape(AppDimens.radiusFull))
                .border(1.dp, scheme.outline, RoundedCornerShape(AppDimens.radiusFull))
                .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
        )
    }
}

/** 令牌色 → RGB 数组（渲染层只认 0..1 通道） */
private fun colorOf(color: Color): FloatArray =
    floatArrayOf(color.red, color.green, color.blue)

/**
 * 画布底色：浅色主题用深绿 [AppColorScheme.onPrimarySoft]（#0B3625），
 * 深色主题用次深表面 [AppColorScheme.surfaceVariant]（#262A27）——
 * 两套主题都保证足够暗，染料加法叠加后才显色。
 */
private fun baseColorOf(isDark: Boolean, scheme: AppColorScheme): Color =
    if (isDark) scheme.surfaceVariant else scheme.onPrimarySoft

/** 预设 → 调色板（全部取自清氧令牌，禁止写死色值） */
private fun paletteOf(scheme: AppColorScheme, preset: FluidPreset): FluidPalette = when (preset) {
    FluidPreset.OXYGEN_WATER -> FluidPalette.of(
        colorOf(scheme.water),
        colorOf(scheme.primary),
        colorOf(scheme.waterSoft),
    )
    FluidPreset.FOAM_WHITE -> FluidPalette.of(
        colorOf(scheme.surface),
        colorOf(scheme.waterSoft),
        colorOf(scheme.primarySoft),
    )
    FluidPreset.WARM_SAND -> FluidPalette.of(
        colorOf(scheme.ongoing),
        colorOf(scheme.danger),
        colorOf(scheme.warningContainer),
    )
}
