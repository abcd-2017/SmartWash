package com.smartwash.feature.game.ui.center

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor
import com.smartwash.feature.game.GameCategory
import com.smartwash.feature.game.GameTint
import com.smartwash.feature.game.R
import com.smartwash.feature.game.model.GameItem
import com.smartwash.feature.game.ui.navigateToGameWebView

/** 网格列数（对齐工作台两列卡，方案 §六） */
private const val GRID_COLUMNS = 2

/** 网格卡片间距（方案 §六 明确 8dp：touch-spacing 网格密排，取间距令牌 spaceXs） */
private val GRID_SPACING = AppDimens.spaceXs

/**
 * 骨架卡高度（与真实卡构成对齐：卡内边距 20 + 图标块 44 + 间距 12 + 标题行 20 +
 * 间距 4 + 描述两行 32 + 卡内边距 20）
 */
private val SKELETON_CARD_HEIGHT = 152.dp

/**
 * 游戏中心页（方案 §六）：PageHeader + 分类筛选 chips（本地过滤）+ 两列网格卡片。
 * 列表数据来自 [GameCenterViewModel]（`GET /web/auth/game/list` 全量 <100 不分页），
 * 三态：骨架 shimmer / EmptyState / 错误重试（规范 §3.6）。
 * 卡片点击 → 既有壳层 WebView 路由（后端下发 url 即最终可玩地址，端上零拼装）。
 */
@Composable
fun GameCenterPage(
    navController: NavHostController,
    viewModel: GameCenterViewModel = hiltViewModel(),
) {
    val games by viewModel.filteredGames.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.game_center_title),
            onBack = { navController.navigateUp() },
        )

        // 分类筛选 chips（横向滚动不换行；数据无关常驻，骨架/错误态同样可见）
        GameCategoryChipsRow(
            selected = selectedCategory,
            onSelect = viewModel::selectCategory,
            modifier = Modifier.padding(top = AppDimens.spaceXs, bottom = AppDimens.spaceSm),
        )

        when {
            // 首刷失败：说明发生了什么 + 重试（规范 §3.6 错误态）
            listState is RequestState.Error -> {
                EmptyState(
                    // EmptyState 的 icon 为占位参数不渲染（组件只出滚筒圆母题）
                    icon = Icons.Rounded.SportsEsports,
                    message = stringResource(R.string.game_error_load),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding),
                    action = {
                        AppButton(
                            text = stringResource(R.string.game_action_retry),
                            onClick = viewModel::loadGames,
                        )
                    },
                )
            }
            // 首刷加载：骨架屏占位（禁止转圈），形状与真实网格一致
            games.isEmpty() && listState !is RequestState.Success -> {
                GameGridSkeleton(modifier = Modifier.fillMaxSize())
            }
            // 空态：全量空（后端未上传/未上架）与筛选无命中分别给文案
            games.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.SportsEsports,
                    message = if (selectedCategory == null) {
                        stringResource(R.string.game_empty_message)
                    } else {
                        stringResource(R.string.game_empty_category)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding),
                )
            }
            // 游戏网格（两列，行 key 由 gameKey 拼接，服务端 sort 序）
            else -> GameGrid(
                games = games,
                onGameClick = { game -> navController.navigateToGameWebView(game) },
            )
        }
    }
}

/**
 * 分类筛选 chips 行（全部 + 四分类，OTHER 不设 chip 仅在「全部」下展示）：
 * 横向滚动不换行压缩；选中态 = 品牌底白字（onPrimary 令牌，深色提亮），
 * 未选中 = 白底描边；`semantics { selected }` 提供选中态语义（a11y）。
 */
@Composable
private fun GameCategoryChipsRow(
    selected: GameCategory?,
    onSelect: (GameCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
    ) {
        GameCategoryChip(
            label = stringResource(R.string.game_category_all),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        GameCategory.CHIP_ORDER.forEach { category ->
            GameCategoryChip(
                label = categoryChipLabel(category),
                selected = selected == category,
                onClick = { onSelect(category) },
            )
        }
    }
}

/** 分类 chip 文案（全部之外的固定四类映射；OTHER 无 chip 无文案） */
@Composable
private fun categoryChipLabel(category: GameCategory): String = when (category) {
    GameCategory.PUZZLE -> stringResource(R.string.game_category_puzzle)
    GameCategory.ARCADE -> stringResource(R.string.game_category_arcade)
    GameCategory.WORD -> stringResource(R.string.game_category_word)
    GameCategory.BOARD -> stringResource(R.string.game_category_board)
    GameCategory.OTHER -> ""
}

/**
 * 单个分类 chip（语言对齐工具箱 ToolboxSelectableChip：pressable 自绘、无 Material
 * ripple/state layer，规范 §7）：选中切换经 [selectedColor] 200ms 档过渡，
 * reduced motion 自动降级。
 */
@Composable
private fun GameCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = AppColors.colorScheme
    // 选中底色 / 文字色 / 描边色统一走公共 selectedColor（reduced motion 自动降级瞬切）
    val containerColor = selectedColor(
        if (selected) scheme.primary else MaterialTheme.colorScheme.surface
    )
    val contentColor = selectedColor(
        if (selected) scheme.onPrimary else scheme.textSecondary
    )
    // 描边常驻 1dp、颜色在 outline ↔ 透明间过渡：选中切换不让描边整条消失/出现
    val borderColor = selectedColor(
        if (selected) Color.Transparent else scheme.outline
    )
    Surface(
        modifier = Modifier
            // 选中态语义（a11y）：TalkBack 播报「已选中」
            .semantics { this.selected = selected }
            .pressable(onClick = onClick, debounce = false),
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            // 字重统一 SemiBold：字重随选中切换会让文字宽度跳变（对齐工具箱 chip）
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = AppDimens.spaceMd, vertical = 6.dp),
        )
    }
}

/**
 * 游戏网格（两列，行切分语言对齐工作台 LazyColumn + chunked）：行 key 由 gameKey
 * 拼接（稳定；行数前缀防不同切分的拼接碰撞），奇数末行补位保持两列。
 * 间距 8dp（GRID_SPACING = spaceXs，方案 §六 touch-spacing）。
 */
@Composable
private fun GameGrid(
    games: List<GameItem>,
    onGameClick: (GameItem) -> Unit,
) {
    val rows = remember(games) { games.chunked(GRID_COLUMNS) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = AppDimens.spaceXs,
            bottom = AppDimens.spaceXl,
        ),
    ) {
        itemsIndexed(
            rows,
            key = { _, rowGames ->
                "game_row_${rowGames.size}_" + rowGames.joinToString("-") { it.gameKey }
            },
        ) { _, rowGames ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppDimens.pagePadding,
                        end = AppDimens.pagePadding,
                        bottom = GRID_SPACING,
                    ),
                horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            ) {
                rowGames.forEach { game ->
                    GameCard(
                        game = game,
                        onClick = { onGameClick(game) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 接口返回奇数个游戏时末行补位，保持两列网格
                if (rowGames.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 游戏卡（卡片唯一画法：白 surface + 1dp outline 描边 + level1 阴影，规范 §3.1）：
 * 44dp 图标色块（色组 Token 六色浅底 + 对应前景色；圆角 16dp 为方案 §六 tile 语言
 * 明确规定，较 §3.2 图标容器 12dp 档放宽）+ 标题 + 描述两行截断 + badge 文字角标
 * （文字传达，非纯色）。整卡 pressable（scale 0.97 / 80-150ms 档，transform-only，
 * reduced motion 降级透明度）；a11y：contentDescription = title + description。
 */
@Composable
private fun GameCard(
    game: GameItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = AppColors.colorScheme
    val (containerColor, iconTint) = when (game.tint) {
        GameTint.GREEN -> scheme.iconContainerGreen to scheme.iconForegroundGreen
        GameTint.BLUE -> scheme.iconContainerBlue to scheme.iconForegroundBlue
        GameTint.ORANGE -> scheme.iconContainerOrange to scheme.iconForegroundOrange
        GameTint.PINK -> scheme.iconContainerPink to scheme.iconForegroundPink
        GameTint.TEAL -> scheme.iconContainerTeal to scheme.iconForegroundTeal
        GameTint.PURPLE -> scheme.iconContainerPurple to scheme.iconForegroundPurple
    }
    Surface(
        modifier = modifier
            .semantics {
                contentDescription =
                    if (game.description.isBlank()) game.title
                    else "${game.title}，${game.description}"
            }
            .pressable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Box {
            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                // 图标色块：44dp 容器（v0.4 §3.2 主容器档）+ 24dp 图标，圆角取 16dp（方案 §六）
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusLg))
                        .background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = game.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint,
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                Text(
                    text = game.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // badge 角标（服务端下发文案）：清氧小胶囊 — radiusFull + 主色浅底/主色文字
            // （文字角标非纯色传达；语言对齐工作台 ToolTile）
            if (!game.badge.isNullOrBlank()) {
                Text(
                    text = game.badge,
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

/**
 * 加载骨架（规范 §3.6：骨架屏替代转圈，形状与真实网格一致；行距同真实网格 8dp，
 * 微光扫过经 SkeletonBlock，reduced motion 自动退化为静态浅灰块）。
 */
@Composable
private fun GameGridSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
    ) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            ) {
                repeat(GRID_COLUMNS) {
                    SkeletonBlock(
                        modifier = Modifier
                            .weight(1f)
                            .height(SKELETON_CARD_HEIGHT),
                        shape = RoundedCornerShape(AppDimens.radiusLg),
                    )
                }
            }
        }
    }
}
