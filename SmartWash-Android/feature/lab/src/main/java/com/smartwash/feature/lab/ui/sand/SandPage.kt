package com.smartwash.feature.lab.ui.sand

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColorScheme
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.lab.R

/** 目标格距（dp）：按屏宽自适应约 100 列；实际列数受 [MIN_COLS]/[MAX_COLS] 约束 */
private val SAND_CELL_TARGET = 4.5.dp

/** 网格列数边界（下限仅防极端窄屏；常规手机约 76 列） */
private const val MIN_COLS = 20
private const val MAX_COLS = 200

/** 网格行数边界（行 0 为顶；下限贴近画布高宽比，避免拉伸失真） */
private const val MIN_ROWS = 8
private const val MAX_ROWS = 400

/** 模拟节拍（ms）：约 30 tick/秒，沙落一整屏约 5 秒 */
private const val SIM_TICK_MS = 33L

/** 调色板每行元素数（9 元素 → 5 + 4 两行） */
private const val PALETTE_COLUMNS = 5

/**
 * 落沙实验室页：全屏 Canvas 沙盒（ByteArray 网格元胞自动机，内核 [SandSimulator] 纯 Kotlin）
 * + 底部调色板（9 元素）+ 清除 / 暂停播放。
 *
 * 渲染：内核 [SandSimulator.render] 输出 IntArray ARGB → [Bitmap.setPixels] →
 * Compose Canvas [androidx.compose.ui.graphics.drawscope.DrawScope.drawImage] 放大铺满
 * （禁止逐 cell drawRect）。笔刷为圆形概率笔刷（单指拖动涂抹）。
 *
 * reduced motion：默认暂停（静帧）+ 用户主动「播放」；正常态默认播放。
 * 触觉：仅在落下笔刷 / 切换元素 / 清除时发，禁止每帧触发。
 */
@Composable
fun SandPage(
    navController: NavHostController,
    viewModel: SandViewModel = hiltViewModel(),
) {
    val sim = viewModel.simulator
    val selected by viewModel.selectedElement.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val view = currentView()
    val scheme = AppColors.colorScheme
    val density = LocalDensity.current

    // reduced motion 默认暂停（静帧），用户主动播放；正常态默认播放
    var playing by remember { mutableStateOf(!reduceMotion) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) playing = false
    }

    // 画布尺寸 / 网格规格 / 位图（网格变化即重建位图）
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var gridCols by remember { mutableStateOf(0) }
    var gridRows by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // 帧序号：绘制块读取它以建立重绘订阅（暂停时不变=静帧；涂抹后 +1 触发重绘）
    var frameTick by remember { mutableLongStateOf(0L) }

    // 调色板 ARGB 按元素 id 索引（EMPTY 透明，画布底色透出）
    val paletteArgb = remember(scheme) {
        IntArray(SandElement.entries.size) { id ->
            val element = SandElement.fromId(id)
            if (element == SandElement.EMPTY) Color.Transparent.toArgb()
            else element.colorOf(scheme).toArgb()
        }
    }

    /** 把当前网格写入位图并触发重绘（帧循环与涂抹共用） */
    fun renderFrame() {
        val bmp = bitmap ?: return
        if (gridCols <= 0 || gridRows <= 0) return
        bmp.setPixels(sim.render(paletteArgb), 0, gridCols, 0, 0, gridCols, gridRows)
        frameTick++
    }

    // 画布尺寸 → 计算网格规格并重建位图（旋转/分屏会重铺空沙盒）
    val cellTargetPx = with(density) { SAND_CELL_TARGET.toPx() }
    LaunchedEffect(canvasSize, cellTargetPx) {
        val size = canvasSize
        if (size.width <= 0 || size.height <= 0 || cellTargetPx <= 0f) return@LaunchedEffect
        val cols = (size.width / cellTargetPx).toInt().coerceIn(MIN_COLS, MAX_COLS)
        if (cols <= 0) return@LaunchedEffect
        val cellPx = size.width.toFloat() / cols
        val rows = (size.height / cellPx).toInt().coerceIn(MIN_ROWS, MAX_ROWS)
        if (rows <= 0) return@LaunchedEffect
        gridCols = cols
        gridRows = rows
        sim.resize(cols, rows)
        val bmp = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        bitmap = bmp
        imageBitmap = bmp.asImageBitmap()
        renderFrame()
    }

    // 帧驱动：固定节拍 tick + 写位图；暂停即取消循环（静帧，页面退出不空转）
    LaunchedEffect(playing, paletteArgb) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        var accumulator = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dtMs = ((now - last) / 1_000_000L).coerceIn(0L, 100L)
                    accumulator += dtMs
                    while (accumulator >= SIM_TICK_MS) {
                        sim.tick()
                        accumulator -= SIM_TICK_MS
                    }
                    renderFrame()
                }
                last = now
            }
        }
    }

    // 手势闭包经 rememberUpdatedState 读最新值（避免以频繁变化的选中态作 pointerInput key）
    val currentSelected by rememberUpdatedState(selected)
    val currentGrid by rememberUpdatedState(gridCols to gridRows)
    val currentCanvasSize by rememberUpdatedState(canvasSize)

    /** 把指针位置换算为网格坐标并涂抹（圆形概率笔刷） */
    fun paintAt(position: Offset) {
        val (cols, rows) = currentGrid
        if (cols <= 0 || rows <= 0) return
        val cellPx = currentCanvasSize.width.toFloat() / cols
        if (cellPx <= 0f) return
        val col = (position.x / cellPx).toInt().coerceIn(0, cols - 1)
        val row = (position.y / cellPx).toInt().coerceIn(0, rows - 1)
        sim.paint(col, row, currentSelected)
        renderFrame()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.lab_sand_title),
            onBack = { navController.navigateUp() },
        )
        Text(
            text = stringResource(R.string.lab_sand_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.textTertiary,
            modifier = Modifier
                .padding(horizontal = AppDimens.pagePadding)
                .padding(top = AppDimens.spaceXs),
        )

        // 沙盒：weight(1f) 占据页头/调色板之外的剩余高度
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = AppDimens.spaceMd)
                .padding(top = AppDimens.spaceSm)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(AppDimens.radiusLg))
                    .background(scheme.surfaceVariant)
                    .border(1.dp, scheme.outline, RoundedCornerShape(AppDimens.radiusLg))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                paintAt(offset)
                                view.performHaptic(HapticEffect.LIGHT)
                            },
                            onDrag = { change, _ -> paintAt(change.position) },
                        )
                    }
            ) {
                frameTick // 读取状态建立每帧重绘订阅
                val img = imageBitmap ?: return@Canvas
                drawImage(
                    image = img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(gridCols, gridRows),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.None,
                )
            }
        }

        // 调色板（卡片唯一画法：白 surface + 1dp outline + level1 阴影 + radiusLg + cardPadding）
        SandPalette(
            selected = selected,
            onSelect = { element ->
                viewModel.selectElement(element)
                view.performHaptic(HapticEffect.SELECTION)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding)
                .padding(top = AppDimens.spaceMd)
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.lab_sand_current_format,
                    stringResource(selected.labelRes()),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.textSecondary,
                modifier = Modifier.weight(1f),
            )
            SandTextButton(
                text = stringResource(R.string.lab_sand_clear),
                onClick = {
                    viewModel.clear()
                    renderFrame()
                    view.performHaptic(HapticEffect.MEDIUM)
                },
            )
            Spacer(modifier = Modifier.size(AppDimens.spaceSm))
            SandTextButton(
                text = stringResource(
                    if (playing) R.string.lab_sand_pause else R.string.lab_sand_play
                ),
                onClick = {
                    playing = !playing
                    view.performHaptic(HapticEffect.SELECTION)
                },
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
    }
}

/**
 * 元素调色板：9 元素两行（5 + 4）色块，选中态加粗品牌色描边。
 * 色块为颜色选择器（非功能图标），44dp 圆形 + 1dp 描边，整块 pressable。
 */
@Composable
private fun SandPalette(
    selected: SandElement,
    onSelect: (SandElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val elements = SandElement.entries
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softCardShadow()
            .background(scheme.surface, RoundedCornerShape(AppDimens.radiusLg))
            .border(1.dp, scheme.outline, RoundedCornerShape(AppDimens.radiusLg))
            .padding(AppDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
    ) {
        PaletteRow(elements.take(PALETTE_COLUMNS), selected, onSelect)
        PaletteRow(elements.drop(PALETTE_COLUMNS), selected, onSelect)
    }
}

/** 单行色块（等距排列） */
@Composable
private fun PaletteRow(
    items: List<SandElement>,
    selected: SandElement,
    onSelect: (SandElement) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { element ->
            PaletteSwatch(
                element = element,
                selected = element == selected,
                onClick = { onSelect(element) },
            )
        }
    }
}

/** 单个色块：44dp 圆形，选中态 3dp 品牌色描边，a11y 以元素名作 contentDescription */
@Composable
private fun PaletteSwatch(
    element: SandElement,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = AppColors.colorScheme
    val label = stringResource(element.labelRes())
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(element.swatchColorOf(scheme))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outline,
                shape = CircleShape,
            )
            .pressable(onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/** 小按钮（文字胶囊）：44dp 最小触控高度 + 12dp 圆角，pressable 无 ripple */
@Composable
private fun SandTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(scheme.surfaceVariant)
            .pressable(onClick = onClick)
            .padding(horizontal = AppDimens.spaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.textPrimary,
        )
    }
}

// ---------- 元素 → 文案 / 令牌色映射 ----------

/** 元素名文案资源（用户可见文案一律进 strings_sand.xml） */
private fun SandElement.labelRes(): Int = when (this) {
    SandElement.EMPTY -> R.string.lab_sand_element_empty
    SandElement.SAND -> R.string.lab_sand_element_sand
    SandElement.WATER -> R.string.lab_sand_element_water
    SandElement.DETERGENT -> R.string.lab_sand_element_detergent
    SandElement.FOAM -> R.string.lab_sand_element_foam
    SandElement.STAIN -> R.string.lab_sand_element_stain
    SandElement.FABRIC -> R.string.lab_sand_element_fabric
    SandElement.FIRE -> R.string.lab_sand_element_fire
    SandElement.STEAM -> R.string.lab_sand_element_steam
}

/** 沙盒渲染色（全部取清氧令牌，禁止写死色值；EMPTY 由页面特殊处理为透明） */
private fun SandElement.colorOf(scheme: AppColorScheme): Color = when (this) {
    SandElement.EMPTY -> scheme.surfaceVariant
    SandElement.SAND -> scheme.warning
    SandElement.WATER -> scheme.water
    SandElement.DETERGENT -> scheme.iconContainerBlue
    SandElement.FOAM -> scheme.surface
    SandElement.STAIN -> scheme.iconForegroundPink
    SandElement.FABRIC -> scheme.primary
    SandElement.FIRE -> scheme.error
    SandElement.STEAM -> scheme.textTertiary
}

/** 调色板色块色（EMPTY 用表面次级色，避免与卡片底混淆） */
private fun SandElement.swatchColorOf(scheme: AppColorScheme): Color = when (this) {
    SandElement.EMPTY -> scheme.surfaceVariant
    else -> colorOf(scheme)
}
