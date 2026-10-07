package com.smartwash.ui.page.workbench

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.DivBgMidDark
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.ui.theme.DivSurface2Dark
import com.smartwash.common.ui.theme.DivSurfaceDark
import com.smartwash.common.ui.theme.DivTextPrimaryDark
import com.smartwash.common.ui.theme.DivTextSecondaryDark
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.feature.divination.DivRoute
import com.smartwash.common.utils.pressable
import com.smartwash.ui.page.PageConstant

/**
 * 工作台 — 底栏 tab 根页（设计稿屏 15）。
 * 清氧页面里的一扇玄墨之窗：观象台特色卡 + 工具网格（占卜降为其中一个入口）。
 * 工具列表来自 [WorkbenchViewModel]（当前假数据，后端工作台接口就绪后替换数据源）。
 */
@Composable
fun WorkbenchPage(
    navController: NavHostController,
    workbenchViewModel: WorkbenchViewModel = hiltViewModel(),
) {
    val tools by workbenchViewModel.tools.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        item {
            // 标题区（设计稿顶部 16）
            Column(
                modifier = Modifier.padding(
                    horizontal = AppDimens.pagePadding,
                    vertical = 16.dp
                )
            ) {
                Text(
                    text = stringResource(R.string.workbench_title),
                    style = AppTextStyles.RootTitle,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = stringResource(R.string.workbench_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
        }

        item {
            // 观象台特色卡 — 玄墨渐变窗（上距取区块令牌）
            DivinationEntryCard(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                onClick = { navController.navigate(DivRoute.Home.text) }
            )
        }

        item {
            // 全部工具（设计稿 margin 32/12）
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.workbench_all_tools),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        }

        item {
            // 工具网格 — 2 列 tile3（白卡唯一画法 + 44dp 语义图标容器）
            Column(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
            ) {
                tools.chunked(2).forEach { rowTools ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                    ) {
                        rowTools.forEach { tool ->
                            ToolTile(
                                tool = tool,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (tool.action) {
                                        WorkbenchAction.DIVINATION_HOME ->
                                            navController.navigate(DivRoute.Home.text)
                                        WorkbenchAction.DIV_HISTORY ->
                                            navController.navigate(DivRoute.History.text)
                                        WorkbenchAction.AI_ASSISTANT ->
                                            navController.navigate(PageConstant.AiWork.text)
                                        WorkbenchAction.COMING_SOON -> Toast.makeText(
                                            context,
                                            context.getString(R.string.workbench_coming_soon),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                        // 接口返回奇数个工具时末行补位，保持两列网格
                        if (rowTools.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            // 页脚弱提示（设计稿 margin 26/10 → 24/8）
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
                        top = 24.dp,
                        bottom = 8.dp
                    )
            )
        }
    }
}

/**
 * 观象台玄墨特色卡（设计稿 darkcard3）：清氧页面里开一扇通往玄墨世界的窗。
 * 深色自带分层不加投影；皮肤取 DivColors 玄墨暗色令牌，浅深模式同款（设计稿深色副本对此卡无覆盖）。
 */
@Composable
private fun DivinationEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val div = DivColors.current
    val shape = RoundedCornerShape(AppDimens.radiusXl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .background(
                // 设计稿渐变 140deg #26241F→#1E1D1B 55%→#191713；末端无令牌，取最近玄墨底 DivBgMidDark
                brush = Brush.linearGradient(
                    listOf(DivSurface2Dark, DivSurfaceDark, DivBgMidDark)
                ),
                shape = shape
            )
            .border(1.dp, div.goldLine, shape)
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
                    letterSpacing = 1.5.sp
                ),
                color = DivTextPrimaryDark
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Text(
                text = stringResource(R.string.workbench_divination_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = DivTextSecondaryDark
            )
        }
        // 30dp 圆形描边箭头（设计稿 30px + rgba(gold,.4) → 金线令牌 30%）
        Box(
            modifier = Modifier
                .size(30.dp)
                .border(1.dp, div.goldLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = div.gold,
                modifier = Modifier.size(14.dp)
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

/** 工具卡（设计稿 tile3）：白卡 + 44dp 语义图标容器 + 14sp 标题 + 12sp 描述 */
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
        WorkbenchTint.TEAL -> scheme.iconContainerTeal to scheme.iconForegroundTeal
        WorkbenchTint.PURPLE -> scheme.iconContainerPurple to scheme.iconForegroundPurple
    }
    Surface(
        modifier = modifier.pressable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        shadowElevation = AppElevation.level1
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            IconBox(
                icon = tool.icon,
                containerColor = containerColor,
                iconTint = iconTint
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                text = stringResource(tool.titleRes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.textPrimary
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            Text(
                text = stringResource(tool.descriptionRes),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.textSecondary
            )
        }
    }
}
