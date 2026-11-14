package com.smartwash.feature.game.ui.center

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.LocalCoverArt
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.AppPillTabRow
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.game.GameCategory
import com.smartwash.feature.game.GameTint
import com.smartwash.feature.game.R
import com.smartwash.feature.game.model.GameItem
import com.smartwash.feature.game.ui.navigateToGameWebView

/** 网格列数（对齐工作台两列卡，方案 §六） */
private const val GRID_COLUMNS = 2

/** 封面宽高比（设计稿封面 800×450 横版；服务端上传与本地兜底同一比例） */
private const val GAME_CARD_ASPECT_RATIO = 16f / 9f

/** 网格卡片间距（cardSpacing 16dp：§2.3 卡片间距档，同时满足 SoftShadow「相邻卡片间距 ≥ 12dp」防阴影叠画） */
private val GRID_SPACING = AppDimens.cardSpacing

/**
 * 游戏中心页（方案 §六）：PageHeader + 分类筛选 chips（本地过滤）+ 两列网格卡片。
 * 列表数据来自 [GameCenterViewModel]（`GET /web/auth/game/list` 全量 <100 不分页），
 * 三态：骨架 shimmer / EmptyState / 错误重试（规范 §3.6）。
 * 卡片点击 → 既有壳层 WebView 路由（后端下发 url 即最终可玩地址，端上零拼装）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameCenterPage(
    navController: NavHostController,
    viewModel: GameCenterViewModel = hiltViewModel(),
) {
    val games by viewModel.filteredGames.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val context = LocalContext.current

    // 网格滚动状态：分类切换后回顶。LazyColumn 按行 key 换代后仍保留旧偏移，
    // 顶部会残留上一分类被裁切的残卡（ui-check P1：切筛选必现）
    val gridListState = rememberLazyListState()
    LaunchedEffect(selectedCategory) {
        gridListState.scrollToItem(0)
    }

    PullToRefreshBox(
        // 首载骨架已在表达加载态，指示器仅在已有内容时的下拉刷新出现
        isRefreshing = listState is RequestState.Loading && games.isNotEmpty(),
        onRefresh = viewModel::loadGames,
        modifier = Modifier.fillMaxSize(),
    ) {
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
                    // 文案消费状态携带的 resId（单一事实源：VM 按错误类型区分文案时页面自动跟随）
                    val error = listState as RequestState.Error
                    EmptyState(
                        // EmptyState 的 icon 为占位参数不渲染（组件只出滚筒圆母题）；Filled 变体对齐 GameConstant
                        icon = Icons.Default.Apps,
                        message = stringResource(error.messageResId),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AppDimens.pagePadding),
                        action = {
                            AppButton(
                                small = true,
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
                        // 占位参数不渲染；Filled 变体对齐 GameConstant 游戏卡图标组
                        icon = Icons.Default.Apps,
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
                    listState = gridListState,
                    onGameClick = { game ->
                        if (game.url.isBlank()) {
                            // 数据残缺防御（后端契约保证非空，正常配置不会走到）：可见反馈替代静默
                            Toast.makeText(
                                context,
                                context.getString(R.string.game_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            navController.navigateToGameWebView(game)
                        }
                    },
                )
            }
        }
    }
}

/**
 * 分类筛选 chips 行（全部 + 四分类，OTHER 不设 chip 仅在「全部」下展示）：
 * 2026-11-01 缺陷 9 统一——自绘 chips 迁移到公共 [AppPillTabRow]（设计稿 .seg3 胶囊形态，
 * 34dp 高 / 16dp 横内边距 / 12sp·500），选中态语义与触觉由公共组件承担。
 */
@Composable
private fun GameCategoryChipsRow(
    selected: GameCategory?,
    onSelect: (GameCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = buildList {
        add(stringResource(R.string.game_category_all))
        GameCategory.CHIP_ORDER.forEach { add(categoryChipLabel(it)) }
    }
    AppPillTabRow(
        tabs = tabs,
        selectedIndex = if (selected == null) 0 else GameCategory.CHIP_ORDER.indexOf(selected) + 1,
        onSelect = { index -> onSelect(if (index == 0) null else GameCategory.CHIP_ORDER[index - 1]) },
        modifier = modifier,
    )
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
 * 游戏网格（两列，行切分语言对齐工作台 LazyColumn + chunked）：行 key 由 gameKey
 * 拼接（稳定；行数前缀防不同切分的拼接碰撞），奇数末行补位保持两列。
 * 间距 16dp（GRID_SPACING = cardSpacing，§2.3 卡片间距 + SoftShadow ≥12dp 不叠画）。
 */
@Composable
private fun GameGrid(
    games: List<GameItem>,
    listState: LazyListState,
    onGameClick: (GameItem) -> Unit,
) {
    val rows = remember(games) { games.chunked(GRID_COLUMNS) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            // 顶部 ≥12dp：首行卡片阴影越出卡边 12dp，LazyColumn 会裁切边界外内容，需留容身空间
            top = AppDimens.spaceSm,
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
 * 游戏卡（卡片唯一画法：白 surface + 1dp outline 描边 + level1 阴影，规范 §3.1）。
 * 两种构图按有无封面分流：
 * - 有封面（服务端 MinIO 直链优先，缺失回退本地自绘兜底）：封面与文字分区 — 16:9 封面
 *   独立占卡顶（随卡片圆角裁切），文字区白底置于封面下方（标题 + 两行描述），文字永不遮图
 *   （2026-11-07 用户反馈调整：原「底部渐变遮罩压白字」构图遮挡封面细节，弃用）；
 *   badge 白胶囊叠封面右上（surface 底 + textPrimary 字 + 1dp outline 描边），任意封面底色下可读；
 *   服务端封面加载失败经 Coil error 态回退本地自绘封面（无本地资产退占位灰）。
 * - 无封面：原回退卡 — IconBox 图标容器（六色浅底 + 对应前景色，44/12/24 v0.4 §3.2 主容器档）
 *   + 标题 + 描述两行截断。
 * 整卡 pressable（scale 0.97 / 80-150ms 档，transform-only，reduced motion 降级透明度）；
 * a11y：contentDescription = title + description，封面为装饰图不出语义。
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
            .pressable(onClick = onClick).softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        val localCover = LocalCoverArt.gameCover(game.gameKey)
        // 服务端封面优先（管理端上传 MinIO），缺失回退本地自绘兜底封面
        val hasServerCover = !game.coverUrl.isNullOrBlank()
        val hasCover = hasServerCover || localCover != null
        if (hasCover) {
            Column {
                // 封面区：16:9 满宽，随卡片圆角裁顶角；badge 白胶囊叠右上角
                Box {
                    if (hasServerCover) {
                        SmartWashAsyncImage(
                            url = game.coverUrl.orEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(GAME_CARD_ASPECT_RATIO),
                            contentScale = ContentScale.Crop,
                            // 加载失败兜底（Coil error 态）：回退本地自绘封面，「服务端优先、本地兜底」闭环；
                            // 无本地资产时退 surfaceVariant 占位色，不再呈现永久死灰块
                            error = localCover?.let { painterResource(it) }
                                ?: ColorPainter(scheme.surfaceVariant),
                        )
                    } else {
                        localCover?.let { coverRes ->
                            Image(
                                painter = painterResource(coverRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(GAME_CARD_ASPECT_RATIO),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    if (!game.badge.isNullOrBlank()) {
                        Text(
                            text = game.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.textPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(AppDimens.spaceSm)
                                .background(scheme.surface, RoundedCornerShape(AppDimens.radiusFull))
                                // 1dp outline 描边与卡片描边同语言：封面接近白色时胶囊轮廓不消失
                                .border(
                                    BorderStroke(1.dp, scheme.outline),
                                    RoundedCornerShape(AppDimens.radiusFull),
                                )
                                .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.space2xs)
                        )
                    }
                }
                // 文字区：封面下方独立白底，文字不遮图；内边距对齐 §2.3 卡片内边距
                // （cardPadding 20dp，与无封面卡同一令牌语言，消除同页 20/12 两套并存）
                Column(
                    modifier = Modifier.padding(AppDimens.cardPadding)
                ) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (game.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                        Text(
                            text = game.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                // 图标容器：IconBox 默认档 = 44dp 容器 / 12dp 圆角（iconContainerRadius）/ 24dp 图标
                // （v0.4 §3.2 图标容器·主；原手搓 Box 误用 radiusLg 16dp，收敛到组件默认档）
                IconBox(
                    icon = game.icon,
                    containerColor = containerColor,
                    iconTint = iconTint,
                )
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
                // badge 角标（无封面构图）：主色浅底/深绿小字胶囊（语言对齐工作台 ToolTile）。
                // 文字色取 textGreenOnTint（§10 对比度红线：primary 品牌绿在 primaryLight 浅底
                // 小字号下仅 ≈3.3:1 不达 AA，textGreenOnTint ≈6.4:1 达标，深浅两主题均已定档）
                if (!game.badge.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    Text(
                        text = game.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.textGreenOnTint,
                        modifier = Modifier
                            .background(scheme.primaryLight, RoundedCornerShape(AppDimens.radiusFull))
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.space2xs)
                    )
                }
            }
        }
    }
}

/**
 * 加载骨架（规范 §3.6：骨架屏替代转圈，形状与真实内容一致；行距同真实网格 16dp（GRID_SPACING），
 * 微光扫过经 SkeletonBlock，reduced motion 自动退化为静态浅灰块）。
 * 骨架与封面主导真实卡同构：16:9 封面块（顶角圆角）+ 下方标题/描述文字条块。
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
                    Column(modifier = Modifier.weight(1f)) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(GAME_CARD_ASPECT_RATIO),
                            shape = RoundedCornerShape(
                                topStart = AppDimens.radiusLg,
                                topEnd = AppDimens.radiusLg,
                            ),
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                // 对齐真实标题行高（bodyMedium 14sp → 20sp 行高，spaceLg 档）
                                .height(AppDimens.spaceLg),
                            shape = RoundedCornerShape(AppDimens.radiusSmall),
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                // 对齐真实描述行高（labelSmall 12sp → 16sp 行高，spaceMd 档）
                                .height(AppDimens.spaceMd),
                            shape = RoundedCornerShape(AppDimens.radiusSmall),
                        )
                    }
                }
            }
        }
    }
}
