package com.smartwash.feature.dining.ui.library

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
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
import com.smartwash.feature.dining.logic.DiningCategoryChip
import com.smartwash.feature.dining.logic.DiningRecipeItem

/** 菜谱库内容区三态：首载骨架 / 首载失败 / 内容（含空态） */
private enum class LibraryContentState { Skeleton, Error, Content }

/**
 * 菜谱库页（04 号票）：分类筛选条 + 上架菜谱列表（封面卡片），点击进详情，右上角「新增」进新增页。
 *
 * 布局（规范 §1 每屏一个锚点）：视觉锚点为菜谱封面卡片；筛选 chips 与列表行安静灰阶，
 * 选中态用 primarySoft 浅绿容器（规范：品牌色出现在选中态底）。
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

    // 首载（返回本页经 LaunchedEffect(Unit) 不重放——栈上重组，列表保留；
    // 下拉刷新/切分类由显式动作触发）
    LaunchedEffect(Unit) { viewModel.load() }

    val contentState = when {
        recipes.isEmpty() && listState is RequestState.Loading -> LibraryContentState.Skeleton
        recipes.isEmpty() && listState is RequestState.Idle -> LibraryContentState.Skeleton
        recipes.isEmpty() && listState is RequestState.Error -> LibraryContentState.Error
        else -> LibraryContentState.Content
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
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

        // 分类筛选条（加载失败仅留痕隐藏；chips 为空不占位）
        if (chips.isNotEmpty()) {
            CategoryChipRow(
                chips = chips,
                selectedCategoryId = selectedCategoryId,
                onSelect = viewModel::selectCategory,
            )
        }

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

                LibraryContentState.Content -> RecipeList(
                    recipes = recipes,
                    hasMore = hasMore,
                    loadMoreState = loadMoreState,
                    isRefreshing = listState is RequestState.Loading,
                    filtered = selectedCategoryId != null,
                    onRefresh = { viewModel.load() },
                    onLoadMore = { viewModel.loadMore() },
                    onRecipeClick = { recipe ->
                        if (recipe.id > 0) {
                            navController.navigate(DiningRoute.RecipeDetail(recipe.id))
                        }
                    },
                )
            }
        }
    }
}

/** 分类筛选 chips（横向滚动；选中 primarySoft 浅绿底 + primary 文字，未选中 surfaceVariant 灰阶） */
@Composable
private fun CategoryChipRow(
    chips: List<DiningCategoryChip>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val selected = chip.id == selectedCategoryId
            val (container, content) = if (selected) {
                AppColors.colorScheme.primarySoft to AppColors.colorScheme.primaryDark
            } else {
                AppColors.colorScheme.surfaceVariant to AppColors.colorScheme.textSecondary
            }
            Text(
                text = chip.name,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(container)
                    .pressable(onClick = { onSelect(chip.id) })
                    .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
        }
    }
}

/** 列表区：LazyColumn keyed items + 下拉刷新 + 加载更多 footer；空态由滚筒母题承载 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeList(
    recipes: List<DiningRecipeItem>,
    hasMore: Boolean,
    loadMoreState: RequestState,
    isRefreshing: Boolean,
    filtered: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRecipeClick: (DiningRecipeItem) -> Unit,
) {
    if (recipes.isEmpty()) {
        EmptyState(
            // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
            icon = Icons.Default.Restaurant,
            // 引导式文案：全库空引导去新增；分类筛选空引导换分类
            message = if (filtered) {
                stringResource(R.string.dining_recipe_list_empty_filtered)
            } else {
                stringResource(R.string.dining_recipe_list_empty)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.pagePadding),
        )
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 列表底部避让手势条/三键导航（规范 §4）——末卡与「加载更多」footer 可完整滚出系统栏之下
        val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.pagePadding,
                top = AppDimens.spaceXs,
                end = AppDimens.pagePadding,
                bottom = navBarsBottom + AppDimens.spaceXl,
            ),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(
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
}

/**
 * 菜谱卡片（GroupCard 唯一画法 + pressable 整卡按压）：
 * 封面 16:9（SmartWashAsyncImage，未上传渲染 surfaceVariant 占位底）+ 菜名 + 分类/食材数角标。
 */
@Composable
private fun RecipeCard(
    recipe: DiningRecipeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick),
        contentPadding = PaddingValues(0.dp),
    ) {
        // 封面 16:9（锚点承载）；无封面时 SmartWashAsyncImage 自身 surfaceVariant 底即占位
        SmartWashAsyncImage(
            url = recipe.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recipe.categoryName.isNotBlank()) {
                    Text(
                        text = recipe.categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
                if (recipe.ingredientCount > 0) {
                    if (recipe.categoryName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textTertiary,
                        )
                        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                    }
                    Text(
                        text = stringResource(
                            R.string.dining_recipe_ingredients_count_format,
                            recipe.ingredientCount,
                        ),
                        // 数量角标开 tabular numbers（规范 §2.2 硬规则，避免数字变化左右跳）
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
            }
        }
    }
}
