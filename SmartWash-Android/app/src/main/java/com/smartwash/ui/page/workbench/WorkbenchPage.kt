package com.smartwash.ui.page.workbench

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.DivBgBottomDark
import com.smartwash.common.ui.theme.DivBgMidDark
import com.smartwash.common.ui.theme.DivBgTopDark
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.ui.theme.DivGoldDark
import com.smartwash.common.ui.theme.DivTextPrimaryDark
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.divination.DivRoute
import com.smartwash.ui.page.PageConstant
import kotlinx.coroutines.delay

/** 工具网格列数（tile3 两列卡） */
private const val GRID_COLUMNS = 2

/** 逐项入场错峰间隔（规范 §7：间隔 ≤60ms；40ms 取中段） */
private const val TOOL_STAGGER_MS = 40

/** 错峰索引上限（40ms × 8 = 320ms，留出入场时长仍在 §7 总预算 400ms 内） */
private const val STAGGER_MAX_INDEX = 8

/** 单项入场淡入时长（reduced motion 由 motionSpec 自动降级 180ms 交叉淡变档） */
private const val TOOL_ENTER_MS = 150

/**
 * 工作台 — 底栏 tab 根页（设计稿屏 15）。
 * 清氧页面里的一扇玄墨之窗：观象台特色卡 + 工具网格（占卜降为其中一个入口）。
 * 工具列表来自 [WorkbenchViewModel]：服务端入口配置下发（`/web/auth/workbench/entries`），
 * 失败/空配置回落本地兜底列表；点击统一走深链分发（DeepLinkHandler.resolve → navigate）。
 * 观象台玄墨特色卡为端上固定品牌位，不参与服务端下发。
 */
@Composable
fun WorkbenchPage(
    navController: NavHostController,
    workbenchViewModel: WorkbenchViewModel = hiltViewModel(),
) {
    val tools by workbenchViewModel.tools.collectAsState()
    val entriesState by workbenchViewModel.entriesState.collectAsState()
    val context = LocalContext.current

    // 深链解析回流导航：resolve 在 VM 协程内完成，本收集端只承担导航副作用（规范：副作用出组合期）
    LaunchedEffect(Unit) {
        workbenchViewModel.navigateAction.collect { action ->
            workbenchViewModel.navigate(navController, action)
        }
    }

    // 两列网格行（entryKey 稳定，服务端下发顺序即后端 sort 序）
    val toolRows = remember(tools) { tools.chunked(GRID_COLUMNS) }
    val showSkeleton = entriesState is RequestState.Loading && tools.isEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        item(key = "wb_title") {
            // 标题区（顶部与其他 tab 页统一取状态栏下 16；副标题底→特色卡 20 为设计稿实测）
            Column(
                modifier = Modifier.padding(
                    start = AppDimens.pagePadding,
                    end = AppDimens.pagePadding,
                    top = 16.dp,
                    bottom = 20.dp
                )
            ) {
                Text(
                    text = stringResource(R.string.workbench_title),
                    style = AppTextStyles.RootTitle,
                    color = AppColors.colorScheme.textPrimary
                )
                // 设计稿实测：大标题底→副标题 4
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.workbench_subtitle),
                    // 画板副标题 12/400（caption 400 档），非 captionStrong 500
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
        }

        item(key = "wb_divination") {
            // 观象台特色卡 — 玄墨渐变窗（端上固定品牌位，不随服务端下发）；上距取区块令牌
            DivinationEntryCard(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                onClick = { navController.navigate(DivRoute.Home.text) }
            )
        }

        item(key = "wb_tools_header") {
            // 全部工具（设计稿实测：特色卡底→标题 32、标题底→网格 12；字距 0.3 与观象台卡标题同款）
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.workbench_all_tools),
                style = AppTextStyles.SectionTitle.copy(letterSpacing = 0.3.sp),
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        }

        if (showSkeleton) {
            // 加载骨架（规范 §3.6：骨架屏替代转圈，形状与真实网格一致；行距同真实网格 cardSpacing）
            item(key = "wb_tools_skeleton") {
                Column(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    repeat(2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                        ) {
                            repeat(GRID_COLUMNS) {
                                SkeletonBlock(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(148.dp),
                                    shape = RoundedCornerShape(AppDimens.radiusLg)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 工具网格 — 两列 tile3（白卡唯一画法 + 44dp 语义图标容器 + 角标胶囊）。
            // 行 key 由 entryKey 拼接（稳定；行数前缀防不同切分的拼接碰撞），
            // 服务端配置变更时按 key 精准复用与重排。
            // 行间距：行内 bottom padding = cardSpacing（footer top 相应 24→8，视觉总距不变 24）
            itemsIndexed(
                toolRows,
                key = { _, rowTools ->
                    "wb_tool_${rowTools.size}_" + rowTools.joinToString("-") { it.entryKey }
                }
            ) { rowIndex, rowTools ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppDimens.pagePadding,
                            end = AppDimens.pagePadding,
                            bottom = AppDimens.cardSpacing
                        ),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    rowTools.forEachIndexed { colIndex, tool ->
                        StaggerIn(
                            index = rowIndex * GRID_COLUMNS + colIndex,
                            modifier = Modifier.weight(1f)
                        ) {
                            ToolTile(
                                tool = tool,
                                onClick = {
                                    if (tool.target.isBlank()) {
                                        // 兜底占位项：功能建设中（不走深链链路）
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.workbench_coming_soon),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        // 已下发/已注册 target：resolve 后经 navigateAction 回流导航
                                        workbenchViewModel.onToolClick(tool)
                                    }
                                }
                            )
                        }
                    }
                    // 接口返回奇数个工具时末行补位，保持两列网格
                    if (rowTools.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item(key = "wb_footer") {
            // 页脚弱提示（设计稿 margin 26/10 → 24/8；top 由 24 改 8 是因网格行自带
            // bottom cardSpacing，16+8=24 与原稿总距一致）
            Text(
                text = stringResource(R.string.workbench_footer),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppDimens.pagePadding,
                        end = AppDimens.pagePadding,
                        top = 8.dp,
                        bottom = 8.dp
                    )
            )
        }
    }
}

/**
 * 逐项错峰入场：淡入 + 轻位移（规范 §7 列表逐项入场节奏）；
 * reduced motion（规范 §8）：取消错峰延迟与位移，纯 180ms 淡入。
 */
@Composable
private fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            shown = true
        } else {
            delay((index.coerceAtMost(STAGGER_MAX_INDEX) * TOOL_STAGGER_MS).toLong())
            shown = true
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(animationSpec = motionSpec(tween(TOOL_ENTER_MS))) +
            slideInVertically(
                animationSpec = motionSpec(defaultSpring()),
                initialOffsetY = { if (reduceMotion) 0 else it / 6 }
            ),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * 观象台玄墨特色卡（设计稿 darkcard3）：清氧页面里开一扇通往玄墨世界的窗。
 * **端上固定品牌位**：不参与服务端工作台入口下发，样式与跳转（DivRoute.Home）端上写死。
 * 深色自带分层不加投影；皮肤取 DivColors 玄墨暗色令牌，浅深模式同款（设计稿两板对此卡同款）。
 * 渐变对角 135° 三段 #201D18→#1A1917@55%→#151412 = 规范 §1.5 divination.ink（画板实测）。
 */
@Composable
private fun DivinationEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val div = DivColors.current
    // 画板实测圆角 16（= radiusLg 卡片唯一档，非 hero 20）
    val shape = RoundedCornerShape(AppDimens.radiusLg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .background(
                // 画板渐变三段色标 0 / 0.55 / 1（#201D18→#1A1917@55%→#151412）
                brush = Brush.linearGradient(
                    0f to DivBgTopDark,
                    0.55f to DivBgMidDark,
                    1f to DivBgBottomDark,
                ),
                shape = shape
            )
            // 卡描边两板均为鎏金 #C9A961 32%（不随清氧主题换色，玄墨皮肤固定金线）
            .border(1.dp, DivGoldDark.copy(alpha = 0.32f), shape)
            .padding(AppDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
    ) {
        DivinationCompass(modifier = Modifier.size(64.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.workbench_divination_name),
                style = AppTextStyles.SectionTitle.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                ),
                color = DivTextPrimaryDark
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            // 设计稿两板副标题同为宣纸主文本 #EFE9DA（弱层级靠字号 12，字重 400）
            Text(
                text = stringResource(R.string.workbench_divination_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = DivTextPrimaryDark
            )
        }
        // 32dp 圆形描边箭头：浅 #8F7233 / 深 #C9A961 均 32% = div.gold 主题感知（画板实测），
        // 内 chevron_right 20dp 鎏金
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.dp, div.gold.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = div.gold,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 罗盘圆 — 玄墨金线几何母题（规范 §5：滚筒圆同一几何的观象台变体），
 * 画布 64dp 基准按设计稿 66px 罗盘 SVG 等比换算（外环 26 / 虚线中环 18 / 内环 9）。
 */
@Composable
private fun DivinationCompass(modifier: Modifier = Modifier) {
    val gold = DivColors.current.gold
    val goldHi = DivColors.current.goldHi
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val hairStroke = 1.5.dp.toPx()
        // 外环
        drawCircle(
            color = gold.copy(alpha = 0.45f),
            radius = 26.dp.toPx(),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // 虚线中环（设计 dasharray 3 5）
        drawCircle(
            color = gold.copy(alpha = 0.26f),
            radius = 18.dp.toPx(),
            style = Stroke(
                width = hairStroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(3.dp.toPx(), 5.dp.toPx())
                )
            )
        )
        // 内环
        drawCircle(
            color = gold.copy(alpha = 0.5f),
            radius = 9.dp.toPx(),
            style = Stroke(width = hairStroke, cap = StrokeCap.Round)
        )
        // 四向刻度（12/6/3/9 点位，自外缘向内 5）
        listOf(
            0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f
        ).forEach { (dx, dy) ->
            drawLine(
                color = gold,
                start = center + Offset(dx * 24.dp.toPx(), dy * 24.dp.toPx()),
                end = center + Offset(dx * 19.dp.toPx(), dy * 19.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        // 水波线（穿过环心的观象台波纹）
        val wave = Path().apply {
            moveTo(center.x - 7.dp.toPx(), center.y)
            quadraticBezierTo(
                center.x - 4.dp.toPx(), center.y - 3.dp.toPx(),
                center.x - 1.dp.toPx(), center.y
            )
            quadraticBezierTo(
                center.x + 2.dp.toPx(), center.y + 3.dp.toPx(),
                center.x + 5.dp.toPx(), center.y
            )
        }
        drawPath(
            path = wave,
            color = goldHi,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        // 中心点
        drawCircle(color = gold, radius = 2.5.dp.toPx(), center = center)
    }
}

/**
 * 工具卡（设计稿 tile3）：白卡 1px 描边 + level1 阴影 + 44dp 语义图标容器（icon.md 24）
 * + 14sp 标题 + 12sp 描述；右上角 badge 角标（清氧小胶囊：radiusFull、主色浅底/主色文字）。
 * icon/color 由 WorkbenchTint 白名单映射（未知值兜底），触控整卡 ≥48dp、按压反馈走 pressable
 * （scale 0.97 + indication = null，禁止 Material ripple）。
 */
@Composable
private fun ToolTile(
    tool: WorkbenchTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val (containerColor, iconTint) = when (tool.tint) {
        WorkbenchTint.GREEN -> scheme.iconContainerGreen to scheme.iconForegroundGreen
        WorkbenchTint.BLUE -> scheme.iconContainerBlue to scheme.iconForegroundBlue
        WorkbenchTint.ORANGE -> scheme.iconContainerOrange to scheme.iconForegroundOrange
        WorkbenchTint.PINK -> scheme.iconContainerPink to scheme.iconForegroundPink
        WorkbenchTint.TEAL -> scheme.iconContainerTeal to scheme.iconForegroundTeal
        WorkbenchTint.PURPLE -> scheme.iconContainerPurple to scheme.iconForegroundPurple
    }
    Surface(
        modifier = modifier.pressable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, scheme.outline)
    ) {
        Box {
            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                IconBox(
                    icon = tool.icon,
                    containerColor = containerColor,
                    iconTint = iconTint,
                    iconSize = 24.dp
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.textPrimary
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.textSecondary
                )
            }
            // badge 角标（服务端下发文案）：清氧小胶囊 — radiusFull + 主色浅底/主色文字（caption 档）
            if (!tool.badge.isNullOrBlank()) {
                Text(
                    text = tool.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = AppDimens.spaceSm, end = AppDimens.spaceSm)
                        .background(scheme.primaryLight, RoundedCornerShape(AppDimens.radiusFull))
                        .padding(horizontal = AppDimens.spaceSm, vertical = 2.dp)
                )
            }
        }
    }
}
