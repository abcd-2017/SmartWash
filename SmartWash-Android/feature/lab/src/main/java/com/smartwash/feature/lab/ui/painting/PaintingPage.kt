package com.smartwash.feature.lab.ui.painting

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppInputDialog
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColorScheme
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.feature.lab.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 保存位图尺寸（3:4 竖幅，与屏幕画作同比例，避免二次拉伸） */
private const val SAVE_WIDTH = 1080
private const val SAVE_HEIGHT = 1440

/** 保存结果（页面据此在 LaunchedEffect 里弹 Toast，避免点击回调里直接弹） */
private enum class SaveResult { SUCCESS, FAILURE }

/**
 * 今日一画页：程序化水墨山水（内核 [PaintingGenerator] 纯 Kotlin，种子确定性可复现）。
 *
 * 版式：PageHeader + 3:4 画作 hero（每屏唯一视觉锚点，宣纸底 + 墨色 + 朱砂/青绿点缀）
 * + 信息卡（种子 / 日期 + 换一张 / 保存 / 输入种子）。
 *
 * 交互：换一张 = 新随机种子（LIGHT 触觉）；保存 = 离屏渲染 Bitmap 写 MediaStore
 * （API 29+ 应用私有贡献，无需权限；minSdk 30 使低版本分支不可达，见 [saveBitmapToGallery]）。
 * 动效：生成过程直接出图、无渐进动画，无新增动画即无 reduced motion 缺口；
 * 按压反馈由 [com.smartwash.common.utils.pressable]（AppButton 内置）承担，其自带降级。
 */
@Composable
fun PaintingPage(
    navController: NavHostController,
    viewModel: PaintingViewModel = hiltViewModel(),
) {
    val scheme = AppColors.colorScheme
    val view = currentView()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val seed by viewModel.seed.collectAsState()
    val scene by viewModel.scene.collectAsState()
    val showSeedDialog by viewModel.showSeedDialog.collectAsState()
    val seedInput by viewModel.seedInput.collectAsState()
    val seedInputError by viewModel.seedInputError.collectAsState()

    val palette = remember(scheme) { paintingPaletteOf(scheme) }
    val isToday = seed == viewModel.todaySeed
    val paintingDesc = stringResource(R.string.lab_painting_image_desc)
    val saveSuccessText = stringResource(R.string.lab_painting_save_success)
    val saveFailedText = stringResource(R.string.lab_painting_save_failed)

    var saveResult by remember { mutableStateOf<SaveResult?>(null) }
    LaunchedEffect(saveResult) {
        when (saveResult) {
            SaveResult.SUCCESS -> {
                Toast.makeText(context, saveSuccessText, Toast.LENGTH_SHORT).show()
                view.performHaptic(HapticEffect.SUCCESS)
                saveResult = null
            }
            SaveResult.FAILURE -> {
                Toast.makeText(context, saveFailedText, Toast.LENGTH_SHORT).show()
                view.performHaptic(HapticEffect.ERROR)
                saveResult = null
            }
            null -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(
            title = stringResource(R.string.lab_painting_title),
            onBack = { navController.navigateUp() },
        )

        // 画作 hero：宣纸底 + 程序化水墨，3:4 竖幅
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding)
                .padding(top = AppDimens.spaceXs)
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(AppDimens.radiusXl))
                .background(palette.paper)
                .semantics { contentDescription = paintingDesc },
        ) {
            drawPainting(scene, palette)
        }

        GroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding)
                // 区块间距 32（画作 hero → 信息卡），卡内 20 由 GroupCard 默认 contentPadding 承担
                .padding(top = AppDimens.spaceXxl, bottom = AppDimens.spaceLg),
        ) {
            Text(
                text = stringResource(R.string.lab_painting_seed_format, seed.value),
                style = AppTextStyles.AmountMedium, // 18sp + tnum，数字槽位不跳动
                color = scheme.textPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            Text(
                text = if (isToday) {
                    stringResource(
                        R.string.lab_painting_today_format,
                        viewModel.todayDate.year,
                        viewModel.todayDate.monthValue,
                        viewModel.todayDate.dayOfMonth,
                    )
                } else {
                    stringResource(R.string.lab_painting_random_label)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.textSecondary,
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
                AppButton(
                    text = stringResource(R.string.lab_painting_random),
                    onClick = {
                        view.performHaptic(HapticEffect.LIGHT)
                        viewModel.randomize()
                    },
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = stringResource(R.string.lab_painting_save),
                    onClick = {
                        view.performHaptic(HapticEffect.MEDIUM)
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                val bitmap = renderPaintingToBitmap(scene, palette, SAVE_WIDTH, SAVE_HEIGHT)
                                saveBitmapToGallery(context, bitmap)
                            }
                            saveResult = if (ok) SaveResult.SUCCESS else SaveResult.FAILURE
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.SECONDARY,
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButton(
                    text = stringResource(R.string.lab_painting_input_seed),
                    onClick = {
                        view.performHaptic(HapticEffect.SELECTION)
                        viewModel.openSeedDialog()
                    },
                    variant = AppButtonVariant.TEXT,
                )
                if (!isToday) {
                    AppButton(
                        text = stringResource(R.string.lab_painting_back_today),
                        onClick = {
                            view.performHaptic(HapticEffect.SELECTION)
                            viewModel.backToToday()
                        },
                        variant = AppButtonVariant.TEXT,
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Text(
                text = stringResource(R.string.lab_painting_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.textTertiary,
            )
        }
    }

    if (showSeedDialog) {
        AppInputDialog(
            title = stringResource(R.string.lab_painting_input_seed),
            inputLabel = stringResource(R.string.lab_painting_seed_label),
            inputValue = seedInput,
            onValueChange = viewModel::updateSeedInput,
            onConfirm = { viewModel.confirmSeedInput() },
            onDismiss = { viewModel.dismissSeedDialog() },
            isError = seedInputError,
            errorMessage = stringResource(R.string.lab_painting_seed_invalid),
            keyboardType = KeyboardType.Number,
        )
    }
}

// ---------- 调色板 ----------

/**
 * 画作调色板：全部取清氧令牌，禁止写死色值（深浅主题各自成立）。
 * 墨色角色 → 令牌映射在深浅两套主题下自动反转（淡墨=弱文本 / 中浓墨=主文本）。
 */
private data class PaintingPalette(
    val paper: Color,
    val inkPale: Color,
    val inkLight: Color,
    val inkMid: Color,
    val inkDark: Color,
    val cinnabar: Color,
    val verdigris: Color,
)

private fun paintingPaletteOf(scheme: AppColorScheme): PaintingPalette = PaintingPalette(
    paper = scheme.surfaceVariant,
    inkPale = scheme.textTertiary,
    inkLight = scheme.textSecondary,
    inkMid = scheme.textPrimary,
    inkDark = scheme.textPrimary,
    cinnabar = scheme.danger,
    verdigris = scheme.primary,
)

private fun PaintingPalette.colorOf(role: InkRole): Color = when (role) {
    InkRole.INK_PALE -> inkPale
    InkRole.INK_LIGHT -> inkLight
    InkRole.INK_MID -> inkMid
    InkRole.INK_DARK -> inkDark
    InkRole.CINNABAR -> cinnabar
    InkRole.VERDIGRIS -> verdigris
}

// ---------- 渲染（屏幕 Canvas 与保存 Bitmap 共用同一绘制逻辑） ----------

/** 分层绘制：纸底 → 红日/飞鸟 → 远山 → 云雾 → 近山 → 水面 → 树 → 屋 → 舟 → 朱印 */
private fun DrawScope.drawPainting(scene: PaintingScene, palette: PaintingPalette) {
    val w = size.width
    val h = size.height
    drawRect(palette.paper, size = size)

    scene.sun?.let { drawSun(it, palette, w, h) }
    scene.birds.forEach { drawBird(it, palette, w, h) }

    scene.mountainsFar.forEach { drawMountain(it, palette, w, h) }
    scene.mistBands.forEach { drawMistBand(it, palette, w, h) }
    scene.mountainsNear.forEach { drawMountain(it, palette, w, h) }

    scene.waterLines.forEach { drawWaterLine(it, palette, w, h) }
    scene.trees.forEach { drawTree(it, palette, w, h) }
    scene.houses.forEach { drawHouse(it, palette, w, h) }
    scene.boats.forEach { drawBoat(it, palette, w, h) }
    drawSeal(scene.seal, palette, w, h)
}

/** 山体：山脊折线平滑后填充至山脚，再以略深墨线勾脊 */
private fun DrawScope.drawMountain(layer: MountainLayer, palette: PaintingPalette, w: Float, h: Float) {
    val pts = layer.ridge
    if (pts.isEmpty()) return
    val ink = palette.colorOf(layer.role)

    val fill = Path().apply {
        smoothThrough(pts, w, h)
        lineTo(pts.last().x * w, layer.baseY * h)
        lineTo(pts.first().x * w, layer.baseY * h)
        close()
    }
    drawPath(fill, ink.copy(alpha = layer.alpha))

    val ridge = Path().apply { smoothThrough(pts, w, h) }
    drawPath(
        ridge,
        color = ink.copy(alpha = (layer.alpha + 0.20f).coerceAtMost(0.90f)),
        style = Stroke(width = (w * 0.003f).coerceAtLeast(1f), cap = StrokeCap.Round),
    )
}

/** 路径平滑：以相邻两点中点为终点、当前点为控制点的二次贝塞尔串联 */
private fun Path.smoothThrough(pts: List<Pt>, w: Float, h: Float) {
    moveTo(pts.first().x * w, pts.first().y * h)
    for (i in 1 until pts.size - 1) {
        val p = pts[i]
        val next = pts[i + 1]
        quadraticBezierTo(
            p.x * w,
            p.y * h,
            (p.x + next.x) * 0.5f * w,
            (p.y + next.y) * 0.5f * h,
        )
    }
    lineTo(pts.last().x * w, pts.last().y * h)
}

/** 云雾留白带：宣纸色胶囊横带，半透明叠在远山上形成空间纵深 */
private fun DrawScope.drawMistBand(band: MistBand, palette: PaintingPalette, w: Float, h: Float) {
    val bandHeight = band.height * h
    drawRoundRect(
        color = palette.paper.copy(alpha = band.alpha),
        topLeft = Offset(band.x * w, band.y * h),
        size = Size(band.width * w, bandHeight),
        cornerRadius = CornerRadius(bandHeight / 2f),
    )
}

/** 水面淡墨横线 */
private fun DrawScope.drawWaterLine(line: WaterLine, palette: PaintingPalette, w: Float, h: Float) {
    drawLine(
        color = palette.colorOf(line.role).copy(alpha = line.alpha),
        start = Offset(line.x * w, line.y * h),
        end = Offset((line.x + line.length) * w, line.y * h),
        strokeWidth = (line.width * w).coerceAtLeast(1f),
        cap = StrokeCap.Round,
    )
}

/** 树：松 = 主干 + 四层外挑枝；点叶 = 主干 + 青绿点簇 */
private fun DrawScope.drawTree(tree: Tree, palette: PaintingPalette, w: Float, h: Float) {
    val baseX = tree.x * w
    val baseY = tree.y * h
    val topX = baseX + tree.lean * w
    val topY = baseY - tree.height * h
    val ink = palette.colorOf(tree.role).copy(alpha = tree.alpha)

    if (tree.style == TreeStyle.PINE) {
        drawLine(ink, Offset(baseX, baseY), Offset(topX, topY), strokeWidth = w * 0.004f, cap = StrokeCap.Round)
        val layers = 4
        for (i in 1..layers) {
            val t = i.toFloat() / (layers + 1)
            val cx = baseX + (topX - baseX) * t
            val cy = baseY + (topY - baseY) * t
            val half = w * 0.022f * (1f - t * 0.55f)
            drawLine(ink, Offset(cx, cy), Offset(cx - half, cy - half * 0.45f), strokeWidth = w * 0.0028f, cap = StrokeCap.Round)
            drawLine(ink, Offset(cx, cy), Offset(cx + half, cy - half * 0.45f), strokeWidth = w * 0.0028f, cap = StrokeCap.Round)
        }
    } else {
        drawLine(ink, Offset(baseX, baseY), Offset(topX, topY), strokeWidth = w * 0.0035f, cap = StrokeCap.Round)
        val foliage = palette.verdigris.copy(alpha = tree.alpha)
        tree.foliage.forEach { dot ->
            drawCircle(foliage, radius = w * 0.012f, center = Offset(dot.x * w, dot.y * h))
        }
    }
}

/** 屋舍：宣纸留白屋身 + 墨线轮廓 + 挑檐墨顶 + 门缝 */
private fun DrawScope.drawHouse(house: House, palette: PaintingPalette, w: Float, h: Float) {
    val left = (house.x - house.width / 2f) * w
    val right = (house.x + house.width / 2f) * w
    val bottom = house.y * h
    val bodyTop = bottom - house.height * h
    val roofTop = bodyTop - house.roofHeight * h
    val cx = house.x * w

    val body = Path().apply {
        moveTo(left, bottom)
        lineTo(left, bodyTop)
        lineTo(right, bodyTop)
        lineTo(right, bottom)
        close()
    }
    drawPath(body, palette.paper)
    drawPath(body, palette.inkDark, style = Stroke(width = w * 0.0028f))

    val overhang = house.width * w * 0.18f
    val roof = Path().apply {
        moveTo(left - overhang, bodyTop)
        lineTo(cx, roofTop)
        lineTo(right + overhang, bodyTop)
        close()
    }
    drawPath(roof, palette.inkMid.copy(alpha = 0.88f))

    drawLine(
        palette.inkDark,
        Offset(cx, bottom),
        Offset(cx, bottom - house.height * h * 0.75f),
        strokeWidth = w * 0.0025f,
        cap = StrokeCap.Round,
    )
}

/** 小舟：墨色船身 + 桅杆 + 朱砂帆 */
private fun DrawScope.drawBoat(boat: Boat, palette: PaintingPalette, w: Float, h: Float) {
    val cx = boat.x * w
    val cy = boat.y * h
    val halfW = boat.width * w / 2f
    val hullDepth = boat.width * h * 0.10f

    val hull = Path().apply {
        moveTo(cx - halfW, cy)
        quadraticBezierTo(cx, cy + hullDepth, cx + halfW, cy)
        lineTo(cx + halfW * 0.7f, cy - hullDepth * 0.7f)
        lineTo(cx - halfW * 0.7f, cy - hullDepth * 0.7f)
        close()
    }
    drawPath(hull, palette.colorOf(boat.role).copy(alpha = 0.90f))

    val mastTop = cy - boat.sailHeight * h
    drawLine(
        palette.inkDark,
        Offset(cx, cy - hullDepth * 0.7f),
        Offset(cx, mastTop),
        strokeWidth = w * 0.0025f,
        cap = StrokeCap.Round,
    )
    val sail = Path().apply {
        moveTo(cx, mastTop)
        lineTo(cx + boat.width * w * 0.30f, mastTop + boat.sailHeight * h * 0.70f)
        lineTo(cx, mastTop + boat.sailHeight * h * 0.70f)
        close()
    }
    drawPath(sail, palette.cinnabar.copy(alpha = 0.70f))
}

/** 飞鸟：极简「人」形两笔 */
private fun DrawScope.drawBird(bird: Bird, palette: PaintingPalette, w: Float, h: Float) {
    val cx = bird.x * w
    val cy = bird.y * h
    val s = bird.size * w
    val ink = palette.colorOf(bird.role)
    val stroke = (w * 0.002f).coerceAtLeast(1f)
    drawLine(ink, Offset(cx - s, cy), Offset(cx, cy - s * 0.7f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(ink, Offset(cx, cy - s * 0.7f), Offset(cx + s, cy), strokeWidth = stroke, cap = StrokeCap.Round)
}

/** 红日：朱砂圆（画面唯一暖色锚点，70% 概率出现） */
private fun DrawScope.drawSun(sun: Sun, palette: PaintingPalette, w: Float, h: Float) {
    drawCircle(
        color = palette.colorOf(sun.role).copy(alpha = sun.alpha),
        radius = sun.radius * w,
        center = Offset(sun.x * w, sun.y * h),
    )
}

/** 朱印：朱砂圆角方 + 朱文「山」字（宣纸色三竖一横） */
private fun DrawScope.drawSeal(seal: Seal, palette: PaintingPalette, w: Float, h: Float) {
    val side = seal.size * w
    val topLeft = Offset(seal.x * w - side / 2f, seal.y * h - side / 2f)
    drawRoundRect(
        color = palette.colorOf(seal.role).copy(alpha = 0.85f),
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = CornerRadius(side * 0.16f),
    )

    val ink = palette.paper
    val stroke = (side * 0.11f).coerceAtLeast(1f)
    val left = topLeft.x + side * 0.22f
    val right = topLeft.x + side * 0.78f
    val midX = topLeft.x + side * 0.5f
    val top = topLeft.y + side * 0.20f
    val bottom = topLeft.y + side * 0.80f
    drawLine(ink, Offset(left, top + side * 0.18f), Offset(left, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(ink, Offset(midX, top), Offset(midX, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(ink, Offset(right, top + side * 0.18f), Offset(right, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(ink, Offset(left, bottom), Offset(right, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
}

// ---------- 离屏渲染与保存 ----------

/**
 * 离屏渲染：把同一套 [drawPainting] 绘制到 [ImageBitmap]，再转 Android [Bitmap]。
 * 屏幕 Canvas 与保存位图共用同一绘制逻辑，保证「所见即所得」。
 */
private fun renderPaintingToBitmap(
    scene: PaintingScene,
    palette: PaintingPalette,
    width: Int,
    height: Int,
): Bitmap {
    val imageBitmap = ImageBitmap(width, height)
    val canvas = ComposeCanvas(imageBitmap)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        drawPainting(scene, palette)
    }
    return imageBitmap.asAndroidBitmap()
}

/**
 * 保存到系统相册。
 *
 * API 分支与权限：minSdk 30（> Q），只走 API 29+ 的 MediaStore 应用私有贡献路径
 * （`RELATIVE_PATH = Pictures/SmartWash` + `IS_PENDING`），**无需任何存储权限**；
 * 低版本（< Q）需 WRITE_EXTERNAL_STORAGE 且本模块不改宿主 manifest，故显式返回失败。
 */
private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

    val resolver = context.contentResolver
    val displayName = "smartwash_painting_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/SmartWash")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: run {
        AppLogger.error(TAG, "saveBitmapToGallery: 相册插入记录失败，画作保存中止")
        return false
    }
    return try {
        val stream = resolver.openOutputStream(uri) ?: run {
            AppLogger.error(TAG, "saveBitmapToGallery: 相册输出流打开失败，画作保存中止")
            return false
        }
        val compressed = stream.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (!compressed) {
            AppLogger.error(TAG, "saveBitmapToGallery: PNG 编码失败，画作保存中止")
            return false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        AppLogger.info(TAG, "画作保存相册成功 displayName=$displayName")
        true
    } catch (e: Exception) {
        // 失败清掉半成品，避免相册出现空文件
        AppLogger.error(TAG, "saveBitmapToGallery: 画作写入失败，半成品已清理", e)
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}

private const val TAG = "PaintingPage"
