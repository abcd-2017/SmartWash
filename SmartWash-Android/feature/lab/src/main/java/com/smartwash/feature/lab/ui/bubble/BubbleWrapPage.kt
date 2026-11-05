package com.smartwash.feature.lab.ui.bubble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColorScheme
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.feature.lab.R
import com.smartwash.feature.lab.util.PopSoundPlayer

/**
 * 目标格距：屏宽自适应约 8 列（375dp 常见屏宽 → 7 列，宽屏/平板列数随之增多；
 * 窄屏 coerce 下限 6 列兜底可点性）。
 */
private val CELL_TARGET = 52.dp

/** 列数自适应边界（coerce 防止极端屏宽下泡泡过小/过大） */
private const val MIN_COLS = 6
private const val MAX_COLS = 12

/** 行数自适应边界（行高按 0.92 倍格距略密于横向） */
private const val MIN_ROWS = 8
private const val MAX_ROWS = 24
private const val ROW_ASPECT = 0.92f

/** 泡泡半径占格内基准半径的比例（留缝形成网格感） */
private const val FILL_RATIO = 0.78f

/** 命中容差（相对半径）：手指触点略偏出圆缘仍算戳中 */
private const val HIT_TOLERANCE = 1.15f

/** 高光偏移（相对半径）：径向渐变圆心左上偏移制造凸起感 */
private const val HIGHLIGHT_SHIFT = 0.35f

/**
 * 泡泡纸页：Canvas 全屏泡泡网格，点击戳破（缩放+淡出短动效 + 三档触觉 +
 * 程序化 pop 短音），全部戳完出「再铺一张」重置。
 * 泡泡阵列/计数在 [BubbleWrapViewModel]（会话内即弃）；戳破动画帧经
 * withFrameNanos 驱动，reduced motion 下直接消失态（触觉/声音保留）。
 */
@Composable
fun BubbleWrapPage(
    navController: NavHostController,
    viewModel: BubbleWrapViewModel = hiltViewModel(),
) {
    val bubbles by viewModel.bubbles.collectAsState()
    val poppedKeys by viewModel.poppedKeys.collectAsState()
    val poppedCount by viewModel.poppedCount.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val view = currentView()

    // pop 短音合成器（页面作用域，销毁即回收；声音失败静默，触觉才是兜底反馈）
    val popSound = remember { PopSoundPlayer() }
    DisposableEffect(Unit) {
        onDispose { popSound.release() }
    }

    // 戳破淡出在途表（key → 戳破时刻，nanoTime 时钟与 withFrameNanos 同源）；重铺即清空
    val animatingPops = remember { mutableStateMapOf<String, Long>() }

    // 画布尺寸测量 → 首铺；网格规格变化（旋转/分屏等）重铺——旧 row/col 索引配新
    // 格距会溢出/变形（newSheet 清计数，语义即「换一张新的」，可接受）
    var canvasSize by remember { mutableStateOf<IntSize?>(null) }
    val density = LocalDensity.current
    val colsRows = remember(canvasSize, density) {
        canvasSize?.let { size ->
            val cellPx = with(density) { CELL_TARGET.toPx() }
            val cols = (size.width / cellPx).toInt().coerceIn(MIN_COLS, MAX_COLS)
            val rows = (size.height / (cellPx * ROW_ASPECT)).toInt().coerceIn(MIN_ROWS, MAX_ROWS)
            cols to rows
        }
    }
    LaunchedEffect(colsRows) {
        val (cols, rows) = colsRows ?: return@LaunchedEffect
        if (viewModel.totalCount == 0 || viewModel.gridSpec != cols to rows) {
            viewModel.newSheet(cols, rows)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.lab_bubble_title),
            onBack = { navController.navigateUp() },
        )
        // 戳破计数（数量槽位：tnum 保证数字变化不左右跳）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding),
        ) {
            Text(
                text = stringResource(R.string.lab_bubble_popped_format, poppedCount, bubbles.size),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // edge-to-edge 下泡泡排布区避让系统手势区：底行泡泡与「再铺一张」
                // 都落在 inset 之上，避免点中心落在手势区内
                .navigationBarsPadding(),
        ) {
            val (cols, rows) = colsRows ?: (0 to 0)
            BubbleSheet(
                bubbles = bubbles,
                poppedKeys = poppedKeys,
                cols = cols,
                rows = rows,
                reduceMotion = reduceMotion,
                animatingPops = animatingPops,
                onTapBubble = { bubble ->
                    if (viewModel.pop(bubble.key)) {
                        if (!reduceMotion) {
                            // 戳破淡出动效经在途表 + 帧时钟推进；reduced motion 直接消失态
                            animatingPops[bubble.key] = System.nanoTime()
                        }
                        // 全部戳完给完成触觉，其余按大小档三档力度。
                        // 判定读 VM StateFlow 权威值（pop 同步更新后立即可读）：重组闭包的
                        // collectAsState 快照在快速连戳时滞后一帧，最后一泡会漏发 SUCCESS
                        val allPopped =
                            viewModel.poppedCount.value >= viewModel.bubbles.value.size
                        view.performHaptic(
                            when {
                                allPopped -> HapticEffect.SUCCESS
                                bubble.tier >= 2 -> HapticEffect.HEAVY
                                bubble.tier >= 1 -> HapticEffect.MEDIUM
                                else -> HapticEffect.LIGHT
                            }
                        )
                        popSound.pop()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it },
            )
            // 全部戳完：浮出「再铺一张」（真实状态触发，无伪造入场动画）
            if (bubbles.isNotEmpty() && poppedCount >= bubbles.size) {
                AppButton(
                    text = stringResource(R.string.lab_bubble_reset),
                    onClick = {
                        colsRows?.let { (cols, rows) -> viewModel.newSheet(cols, rows) }
                        animatingPops.clear()
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
 * 泡泡网格画布：未戳=凸起感（高光偏移径向渐变，water/waterSoft 令牌系），
 * 戳破=缩放+淡出短动效（[DURATION_MICRO_MS] 档，加速退出 t²），播完即消失态。
 * 动画帧仅在在途动画存在时逐帧推进（循环退出条件=全部播完，页面退出不空转）。
 */
@Composable
private fun BubbleSheet(
    bubbles: List<Bubble>,
    poppedKeys: Set<String>,
    cols: Int,
    rows: Int,
    reduceMotion: Boolean,
    animatingPops: MutableMap<String, Long>,
    onTapBubble: (Bubble) -> Unit,
    modifier: Modifier = Modifier,
) {
    // frameNanos 状态在绘制块内读取 → 每帧触发重绘；无动画时循环退出停止重绘
    var frameNanos by remember { mutableLongStateOf(0L) }
    val hasAnimating = animatingPops.isNotEmpty()
    LaunchedEffect(hasAnimating) {
        if (!hasAnimating) return@LaunchedEffect
        while (animatingPops.isNotEmpty()) {
            withFrameNanos { frameNanos = it }
            val now = System.nanoTime()
            animatingPops.entries.removeAll {
                (now - it.value) / 1_000_000L >= DURATION_MICRO_MS
            }
        }
    }

    // 手势闭包经 rememberUpdatedState 读最新状态（避免以频繁变化的阵列作 pointerInput key）
    val currentBubbles by rememberUpdatedState(bubbles)
    val currentPoppedKeys by rememberUpdatedState(poppedKeys)
    // 令牌在组合层读取（Canvas 绘制块非 @Composable 上下文）
    val scheme = AppColors.colorScheme

    Canvas(
        modifier = modifier.pointerInput(cols, rows) {
            detectTapGestures { offset ->
                if (cols <= 0 || rows <= 0 || size.width <= 0 || size.height <= 0) {
                    return@detectTapGestures
                }
                val cellW = size.width / cols.toFloat()
                val cellH = size.height / rows.toFloat()
                val col = (offset.x / cellW).toInt().coerceIn(0, cols - 1)
                val row = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
                val key = "${row}_$col"
                if (key in currentPoppedKeys) return@detectTapGestures
                val bubble = currentBubbles.firstOrNull { it.key == key }
                    ?: return@detectTapGestures
                val baseR = minOf(cellW, cellH) / 2f * FILL_RATIO
                val center = Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
                val r = baseR * Bubble.radiusFactor(bubble.tier)
                if ((offset - center).getDistance() > r * HIT_TOLERANCE) {
                    return@detectTapGestures
                }
                onTapBubble(bubble)
            }
        }
    ) {
        if (cols <= 0 || rows <= 0) return@Canvas
        val cellW = size.width / cols.toFloat()
        val cellH = size.height / rows.toFloat()
        val baseR = minOf(cellW, cellH) / 2f * FILL_RATIO
        bubbles.forEach { bubble ->
            val center = Offset((bubble.col + 0.5f) * cellW, (bubble.row + 0.5f) * cellH)
            val r = baseR * Bubble.radiusFactor(bubble.tier)
            val poppedAt = animatingPops[bubble.key]
            when {
                poppedAt != null -> {
                    // 戳破淡出：缩放 1→1.35 + alpha 1→0，加速退出（t²）
                    val t = ((frameNanos - poppedAt) / 1_000_000f / DURATION_MICRO_MS)
                        .coerceIn(0f, 1f)
                    val eased = t * t
                    if (eased < 1f) {
                        drawBulge(scheme, center, r * (1f + 0.35f * eased), alpha = 1f - eased)
                    }
                }
                // 已播完：消失态不再绘制；未戳：凸起感泡泡
                bubble.key in poppedKeys -> Unit
                else -> drawBulge(scheme, center, r, alpha = 1f)
            }
        }
    }
}

/**
 * 凸起感泡泡：径向渐变圆心向左上偏移（高光落在左上、边缘偏 water 深色），
 * 外缘细描边增强立体；色值全部引用清氧令牌（surface/waterSoft/water）。
 */
private fun DrawScope.drawBulge(
    scheme: AppColorScheme,
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                scheme.surface,
                scheme.waterSoft,
                scheme.water.copy(alpha = 0.6f),
            ),
            center = center - Offset(radius * HIGHLIGHT_SHIFT, radius * HIGHLIGHT_SHIFT),
            radius = radius * 1.7f,
        ),
        radius = radius,
        center = center,
        alpha = alpha,
    )
    drawCircle(
        color = scheme.water.copy(alpha = 0.35f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.dp.toPx()),
        alpha = alpha,
    )
}
