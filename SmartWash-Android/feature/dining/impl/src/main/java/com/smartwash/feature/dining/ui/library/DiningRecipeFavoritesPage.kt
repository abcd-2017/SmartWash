package com.smartwash.feature.dining.ui.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.DiningRoute

/** 收藏列表内容区三态：首载骨架 / 首载失败 / 内容（含空态） */
private enum class FavoritesContentState { Skeleton, Error, Content }

/**
 * 我的收藏页（契约 §三.4）：复用菜谱库双列封面卡网格（[RecipeGrid]），数据走 favorites
 * 分页链路（favorited 恒 true）。入口在订单列表页分区。空态引导去菜谱库收藏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiningRecipeFavoritesPage(
    navController: NavHostController,
    viewModel: DiningRecipeFavoritesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val listState by viewModel.listState.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadMoreState by viewModel.loadMoreState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    // 返回本页（详情收藏切换后）静默同步收藏态（失败保留旧列表）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load(silent = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contentState = when {
        recipes.isEmpty() && listState is RequestState.Loading -> FavoritesContentState.Skeleton
        recipes.isEmpty() && listState is RequestState.Idle -> FavoritesContentState.Skeleton
        recipes.isEmpty() && listState is RequestState.Error -> FavoritesContentState.Error
        else -> FavoritesContentState.Content
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.surface),
    ) {
        PageHeader(
            title = stringResource(R.string.dining_recipe_favorites_title),
            onBack = { navController.navigateUp() },
        )

        Crossfade(
            targetState = contentState,
            animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
            label = "diningRecipeFavoritesContent",
        ) { state ->
            when (state) {
                FavoritesContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                FavoritesContentState.Error -> EmptyState(
                    icon = Icons.Default.Restaurant,
                    message = (listState as? RequestState.Error)?.getMessage(context)
                        ?: stringResource(R.string.dining_recipe_favorites_load_failed),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding),
                )

                FavoritesContentState.Content -> PullToRefreshBox(
                    isRefreshing = listState is RequestState.Loading,
                    onRefresh = { viewModel.load() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (recipes.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Restaurant,
                            message = stringResource(R.string.dining_recipe_favorites_empty),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AppDimens.pagePadding),
                        )
                    } else {
                        RecipeGrid(
                            recipes = recipes,
                            hasMore = hasMore,
                            loadMoreState = loadMoreState,
                            onLoadMore = { viewModel.loadMore() },
                            onRecipeClick = { recipe ->
                                if (recipe.id > 0) {
                                    navController.navigate(DiningRoute.RecipeDetail(recipe.id))
                                }
                            },
                            onToggleFavorite = viewModel::unfavorite,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
