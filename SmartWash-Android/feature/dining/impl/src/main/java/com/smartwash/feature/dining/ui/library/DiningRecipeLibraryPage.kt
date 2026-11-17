package com.smartwash.feature.dining.ui.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppTextField
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.dining.ui.DiningCalorieBadge
import com.smartwash.feature.dining.logic.DiningCategoryChip
import com.smartwash.feature.dining.logic.DiningRecipeItem

/** 左分类栏宽度（对齐点单页 RAIL_WIDTH 先例：分类名短词为主，84dp 兼顾两行折行） */
private val RAIL_WIDTH = 84.dp

/** 右栏封面卡固定宽高比（防 CLS：双列封面卡统一 4:3） */
private const val COVER_ASPECT = 4f / 3f

/** 菜谱库内容区三态：首载骨架 / 首载失败 / 内容（含空态） */
private enum class LibraryContentState { Skeleton, Error, Content }

/**
 * 菜谱库页（04 号票，契约 §5.1 美团式双栏重构）：左分类栏 + 右双列封面卡网格，右上角「新增」。
 *
 * 性能硬约束（用户明确要求）：右栏保留服务端分页 + 懒加载——左栏选中分类 → 服务端按分类
 * 分页查询（换分类即重置分页），触底自动加载更多；禁止一次拉全量本地过滤。左栏不做数量徽标
 * （避免全量 count 查询）。关键词搜索切换为全库结果列表（服务端分页链路沿用）。
 *
 * 布局（规范 §1 每屏一个锚点）：视觉锚点为菜谱封面卡；分类栏安静灰阶，选中态 primarySoft
 * 浅绿容器（规范：品牌色出现在选中态底）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiningRecipeLibraryPage(
    navController: NavHostController,
    viewModel: DiningRecipeLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val listState by viewModel.listState.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadMoreState by viewModel.loadMoreState.collectAsState()
    val chips by viewModel.chips.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val keyword by viewModel.keyword.collectAsState()
    val searchByIngredient by viewModel.searchByIngredient.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    // 搜索态切换为全库结果列表（契约 §5.1）：分类栏隐藏，结果单列铺满
    val isSearching = keyword.isNotBlank()

    val contentState = when {
        recipes.isEmpty() && listState is RequestState.Loading -> LibraryContentState.Skeleton
        recipes.isEmpty() && listState is RequestState.Idle -> LibraryContentState.Skeleton
        recipes.isEmpty() && listState is RequestState.Error -> LibraryContentState.Error
        else -> LibraryContentState.Content
    }

    // 空态文案：搜索无果 / 分类无果 / 全库空（引导新增）
    val emptyMessage = when {
        isSearching -> stringResource(
            R.string.dining_recipe_list_empty_search, keyword.trim(),
        )
        selectedCategoryId != null -> stringResource(R.string.dining_recipe_list_empty_filtered)
        else -> stringResource(R.string.dining_recipe_list_empty)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            // 规范 §4：搜索框键盘弹出时整页上收，输入框保持可见（与 RechargePage 同口径）
            .imePadding()
    ) {
        PageHeader(
            title = stringResource(R.string.dining_recipe_library_title),
            onBack = { navController.navigateUp() },
            actions = {
                AppButton(
                    text = stringResource(R.string.dining_recipe_action_create),
                    onClick = { navController.navigate(DiningRoute.RecipeCreate) },
                    variant = AppButtonVariant.TEXT,
                )
            },
        )

        // 关键词搜索（菜名/食材模糊，服务端分页；防抖 300ms 重载；搜索态切全库列表）
        RecipeSearchField(
            value = keyword,
            onValueChange = viewModel::onKeywordChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXs),
        )

        // 搜索模式（契约 §2.4 最小方案）：菜名走 keyword，食材走 ingredient 反查；
        // 二选一避免同时传导致 AND 过严。模式切换即按当前关键词重置分页重拉。
        SearchModeRow(
            searchByIngredient = searchByIngredient,
            onSelect = viewModel::onSearchModeChange,
            modifier = Modifier.padding(
                start = AppDimens.pagePadding,
                end = AppDimens.pagePadding,
                bottom = AppDimens.spaceXs,
            ),
        )

        Crossfade(
            targetState = contentState,
            animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
            label = "diningLibraryContent",
        ) { state ->
            when (state) {
                LibraryContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                LibraryContentState.Error -> EmptyState(
                    // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                    icon = Icons.Default.Restaurant,
                    message = (listState as? RequestState.Error)?.getMessage(context)
                        ?: stringResource(R.string.dining_recipe_load_failed),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding),
                    action = {
                        AppButton(
                            small = true,
                            text = stringResource(R.string.dining_recipe_action_retry),
                            onClick = { viewModel.load() },
                            variant = AppButtonVariant.SECONDARY,
                        )
                    },
                )

                LibraryContentState.Content -> PullToRefreshBox(
                    isRefreshing = listState is RequestState.Loading,
                    onRefresh = { viewModel.load() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 空态也放进刷新容器：空列表/搜索无果同样支持下拉刷新（与有数据态交互一致）
                    if (recipes.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Restaurant,
                            message = emptyMessage,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AppDimens.pagePadding),
                        )
                    } else if (isSearching) {
                        SearchResultList(
                            recipes = recipes,
                            hasMore = hasMore,
                            loadMoreState = loadMoreState,
                            onLoadMore = { viewModel.loadMore() },
                            onRecipeClick = { recipe ->
                                if (recipe.id > 0) {
                                    navController.navigate(DiningRoute.RecipeDetail(recipe.id))
                                }
                            },
                        )
                    } else {
                        // 分类浏览态：左栏 + 双列封面卡网格（数据随分类/分页服务端拉取）
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (chips.isNotEmpty()) {
                                CategoryRail(
                                    chips = chips,
                                    selectedCategoryId = selectedCategoryId,
                                    onSelect = viewModel::selectCategory,
                                    modifier = Modifier.width(RAIL_WIDTH),
                                )
                            }
                            RecipeGrid(
                                recipes = recipes,
                                hasMore = hasMore,
                                loadMoreState = loadMoreState,
                                modifier = Modifier.weight(1f),
                                onLoadMore = { viewModel.loadMore() },
                                onRecipeClick = { recipe ->
                                    if (recipe.id > 0) {
                                        navController.navigate(DiningRoute.RecipeDetail(recipe.id))
                                    }
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 左分类栏（契约 §5.1）：分类名纵向列表，选中 primarySoft 底 + primaryDark 字；点击 → 服务端
 * 按分类重置分页。不做数量徽标（避免全量 count 查询，契约性能硬约束）。
 */
@Composable
private fun CategoryRail(
    chips: List<DiningCategoryChip>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.surfaceVariant),
    ) {
        items(chips, key = { it.id ?: -1L }) { chip ->
            val selected = chip.id == selectedCategoryId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 分类项最小 48dp 热区（规范 §3.2），且让选中底铺满整行高
                    .heightIn(min = 48.dp)
                    .background(if (selected) AppColors.colorScheme.primarySoft else Color.Transparent)
                    .pressable(onClick = { onSelect(chip.id) }, debounce = false)
                    .padding(vertical = AppDimens.spaceSm, horizontal = AppDimens.spaceXxs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chip.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) AppColors.colorScheme.primaryDark
                    else AppColors.colorScheme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 右栏双列封面卡网格（契约 §5.1）：item 给 key、封面固定宽高比防 CLS；触底自动加载更多
 * （末两项可见即触发），footer 三态（加载点 / 失败重试 / 加载更多），失败不封底可重试。
 */
@Composable
internal fun RecipeGrid(
    recipes: List<DiningRecipeItem>,
    hasMore: Boolean,
    loadMoreState: RequestState,
    onLoadMore: () -> Unit,
    onRecipeClick: (DiningRecipeItem) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    // 触底探测（对齐订单列表先例）：末两项可见即触发下一页；加载中不重入，失败保留 footer 重试
    val shouldLoadMore by remember {
        derivedStateOf { gridState.reachedBottom() }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && loadMoreState !is RequestState.Loading) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.spaceMd,
            top = AppDimens.spaceXs,
            end = AppDimens.pagePadding,
            // 末卡避让手势条/三键导航
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                AppDimens.spaceXl,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
    ) {
        items(recipes, key = { it.id }) { recipe ->
            RecipeCoverCard(
                recipe = recipe,
                onClick = { onRecipeClick(recipe) },
                onToggleFavorite = { onToggleFavorite(recipe.id) },
            )
        }
        if (hasMore) {
            item(key = "load_more", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.spaceMd),
                    contentAlignment = Alignment.Center,
                ) {
                    when (loadMoreState) {
                        // 触底已自动触发，加载点即进行中反馈
                        is RequestState.Loading -> LoadingDots()
                        is RequestState.Error -> AppButton(
                            text = stringResource(R.string.dining_recipe_action_retry),
                            onClick = onLoadMore,
                            variant = AppButtonVariant.SECONDARY,
                            small = true,
                        )
                        else -> AppButton(
                            text = stringResource(R.string.dining_load_more),
                            onClick = onLoadMore,
                            variant = AppButtonVariant.TEXT,
                        )
                    }
                }
            }
        }
    }
}

/** 触底判定（LazyGridState 与 LazyListState 的 layoutInfo 同形，抽扩展共用语义） */
private fun LazyGridState.reachedBottom(): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
    return info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 2
}

/**
 * 封面卡（GroupCard 唯一画法 + pressable 整卡按压）：封面 4:3 固定宽高比防 CLS；
 * 封面缺失回落「分类浅底 + 菜名首字」占位（不依赖网络，契约 §5.1）。
 * 菜名 + 热量徽标（≈N kcal/份；缺失渲染「热量未知」次级档）。
 */
@Composable
internal fun RecipeCoverCard(
    recipe: DiningRecipeItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick),
        contentPadding = PaddingValues(0.dp),
    ) {
        // 封面区：封面图/占位底 + 右上角收藏按钮（48dp 热区；内层 pressable 消费点击不进卡片）。
        // 分类占位图（dining/covers/category_*.png）视为无封面（TD18）：实拍缺失回落首字占位
        Box {
            if (recipe.coverUrl.isNotBlank() && !recipe.coverUrl.contains("dining/covers/category_")) {
                SmartWashAsyncImage(
                    url = recipe.coverUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(COVER_ASPECT),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(COVER_ASPECT)
                        .background(AppColors.colorScheme.iconContainerGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = recipe.name.firstOrNull()?.toString() ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppColors.colorScheme.primaryDark,
                    )
                }
            }
            RecipeFavoriteButton(
                favorited = recipe.favorited,
                recipeName = recipe.name,
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Column(
            modifier = Modifier.padding(
                horizontal = AppDimens.spaceSm,
                vertical = AppDimens.spaceSm,
            ),
        ) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textPrimary,
                // 两行上限（TD16）：大字体档单行 7 字即截断，网格卡有纵向余量
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            DiningCalorieBadge(calories = recipe.calories)
        }
    }
}

/**
 * 封面卡右上角收藏按钮（契约 §三.4）：48dp 热区，已收藏=实心红心、未收藏=描边灰心；
 * 圆形半透明白底保证在封面图上可读；内层 pressable 消费点击（不触发整卡跳转）。
 */
@Composable
private fun RecipeFavoriteButton(
    favorited: Boolean,
    recipeName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (favorited) {
        stringResource(R.string.dining_recipe_action_favorite_remove, recipeName)
    } else {
        stringResource(R.string.dining_recipe_action_favorite_add, recipeName)
    }
    Box(
        modifier = modifier
            .padding(AppDimens.spaceXs)
            .size(48.dp)
            .clip(CircleShape)
            .background(AppColors.colorScheme.surface.copy(alpha = 0.88f))
            .pressable(onClick = onClick, debounce = false)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (favorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = null,
            tint = if (favorited) AppColors.colorScheme.danger else AppColors.colorScheme.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 搜索结果列表（契约 §5.1「搜索时切换为全库结果列表」）：单列行卡 + 触底自动加载更多 */
@Composable
private fun SearchResultList(
    recipes: List<DiningRecipeItem>,
    hasMore: Boolean,
    loadMoreState: RequestState,
    onLoadMore: () -> Unit,
    onRecipeClick: (DiningRecipeItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && loadMoreState !is RequestState.Loading) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.pagePadding,
            top = AppDimens.spaceXs,
            end = AppDimens.pagePadding,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                AppDimens.spaceXl,
        ),
    ) {
        itemsIndexed(recipes, key = { _, recipe -> recipe.id }) { _, recipe ->
            SearchResultRow(
                recipe = recipe,
                onClick = { onRecipeClick(recipe) },
                modifier = Modifier.padding(bottom = AppDimens.cardSpacing),
            )
        }
        if (hasMore) {
            item(key = "load_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.spaceMd),
                    contentAlignment = Alignment.Center,
                ) {
                    when (loadMoreState) {
                        is RequestState.Loading -> LoadingDots()
                        is RequestState.Error -> AppButton(
                            text = stringResource(R.string.dining_recipe_action_retry),
                            onClick = onLoadMore,
                            variant = AppButtonVariant.SECONDARY,
                            small = true,
                        )
                        else -> AppButton(
                            text = stringResource(R.string.dining_load_more),
                            onClick = onLoadMore,
                            variant = AppButtonVariant.TEXT,
                        )
                    }
                }
            }
        }
    }
}

/** 搜索结果行卡：56dp 封面缩略（缺失回落首字占位）+ 菜名 + 热量徽标 */
@Composable
private fun SearchResultRow(
    recipe: DiningRecipeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick),
        contentPadding = PaddingValues(AppDimens.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecipeThumb(coverUrl = recipe.coverUrl, name = recipe.name)
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                DiningCalorieBadge(calories = recipe.calories)
            }
        }
    }
}

/** 56dp 封面缩略（发起点单页同款）：有封面走网络图，分类占位键/缺失回落浅绿底 + 菜名首字（TD18） */
@Composable
private fun RecipeThumb(coverUrl: String, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(AppColors.colorScheme.iconContainerGreen),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl.isNotBlank() && !coverUrl.contains("dining/covers/category_")) {
            SmartWashAsyncImage(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.firstOrNull()?.toString() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.primaryDark,
            )
        }
    }
}

/**
 * 菜谱搜索输入框（§3.7 输入控件标准）：前缀 16dp 搜索图标（组件统一 textSecondary），
 * 有内容时尾缀 48dp 自绘清空热区（禁 M3 IconButton 涟漪），正文 16sp。
 */
@Composable
private fun RecipeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(R.string.dining_recipe_search_hint),
        leadingIcon = Icons.Rounded.Search,
        trailingIcon = {
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pressable(onClick = { onValueChange("") }, debounce = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.dining_action_clear),
                        modifier = Modifier.size(20.dp),
                        tint = AppColors.colorScheme.textSecondary,
                    )
                }
            }
        },
        singleLine = true,
        modifier = modifier,
    )
}

/**
 * 搜索模式切换行（契约 §2.4 食材反查最小方案）：「菜名」走 keyword、「食材」走 ingredient，
 * 二选一避免 AND 过严；选中态 primarySoft 容器 + primaryDark 字（与分类栏选中态同令牌）。
 */
@Composable
private fun SearchModeRow(
    searchByIngredient: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)) {
        SearchModeChip(
            text = stringResource(R.string.dining_recipe_search_mode_name),
            selected = !searchByIngredient,
            onClick = { onSelect(false) },
        )
        SearchModeChip(
            text = stringResource(R.string.dining_recipe_search_mode_ingredient),
            selected = searchByIngredient,
            onClick = { onSelect(true) },
        )
    }
}

/** 搜索模式 chip：48dp 最小热区，选中 primarySoft 底 + primaryDark 字，未选描边（规范 §7 自绘） */
@Composable
private fun SearchModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(
                if (selected) AppColors.colorScheme.primarySoft
                else AppColors.colorScheme.surface,
            )
            .then(
                if (!selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = AppColors.colorScheme.outline,
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                    )
                } else {
                    Modifier
                },
            )
            .pressable(onClick = onClick, debounce = false)
            .padding(horizontal = AppDimens.spaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppColors.colorScheme.primaryDark
            else AppColors.colorScheme.textSecondary,
        )
    }
}
