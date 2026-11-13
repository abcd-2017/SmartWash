package com.smartwash.feature.dining.ui.order

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppTabBar
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningRecipeShopping
import com.smartwash.feature.dining.logic.DiningShoppingListResult

/** 双视图 Tab 索引（0=按食材买菜，1=按菜备菜） */
private const val TAB_INGREDIENTS = 0
private const val TAB_PER_RECIPE = 1

/**
 * 购买清单结果页（11 号票）：渲染结束点单写入的 shopping_list 快照，
 * 「按食材买菜 / 按菜备菜」双视图切换（spec 用户故事 19/20）。
 *
 * 数据来自详情接口（快照 + 参与者），纯映射在 [com.smartwash.feature.dining.logic]
 * 的 DiningShoppingListMappers（JVM 单测锚点）。列表均经 LazyColumn 渲染并给 key。
 */
@Composable
fun DiningOrderShoppingListPage(
    navController: NavHostController,
    viewModel: DiningOrderShoppingListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val loadState by viewModel.loadState.collectAsState()
    val result by viewModel.result.collectAsState()

    // 双视图切换（纯 UI 态；不参与网络请求）
    var tab by remember { mutableStateOf(TAB_INGREDIENTS) }

    // 首载（返回本页经 LaunchedEffect(Unit) 不重放；失败重试显式触发）
    LaunchedEffect(Unit) { viewModel.load() }

    // 失败反馈（Toast 收进 effect——组合期禁副作用）
    LaunchedEffect(loadState) {
        val state = loadState
        if (state is RequestState.Error) {
            Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background),
    ) {
        PageHeader(
            title = stringResource(R.string.dining_shopping_list_title),
            onBack = { navController.navigateUp() },
        )

        val current = result
        when {
            current != null -> ShoppingListContent(
                tab = tab,
                result = current,
                onTabSelected = { tab = it },
            )

            loadState is RequestState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())

            else -> EmptyState(
                // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                icon = Icons.Default.Restaurant,
                message = (loadState as? RequestState.Error)?.getMessage(context)
                    ?: stringResource(R.string.dining_shopping_list_load_failed),
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
        }
    }
}

/** 内容区：Tab 栏固定，视图切换走 Crossfade（卡片入场令牌，reduced motion 由 motionSpec 降级） */
@Composable
private fun ShoppingListContent(
    tab: Int,
    result: DiningShoppingListResult,
    onTabSelected: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTabBar(
            tabs = listOf(
                stringResource(R.string.dining_shopping_list_tab_ingredients),
                stringResource(R.string.dining_shopping_list_tab_per_recipe),
            ),
            selectedIndex = tab,
            onTabSelected = onTabSelected,
        )
        Crossfade(
            targetState = tab,
            animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
            label = "diningShoppingListTab",
        ) { index ->
            if (index == TAB_INGREDIENTS) {
                IngredientsView(ingredients = result.ingredients)
            } else {
                PerRecipeView(perRecipe = result.perRecipe)
            }
        }
    }
}

/** 按食材买菜视图：食材名并集清单（保序，带序号） */
@Composable
private fun IngredientsView(ingredients: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AppDimens.pagePadding,
            vertical = AppDimens.spaceSm,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
    ) {
        item(key = "ingredients_header") {
            Text(
                text = stringResource(R.string.dining_shopping_list_ingredients_section),
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.colorScheme.textSecondary,
            )
        }
        item(key = "ingredients_card") {
            GroupCard(modifier = Modifier.fillMaxWidth()) {
                if (ingredients.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dining_shopping_list_empty_ingredients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textTertiary,
                    )
                } else {
                    ingredients.forEachIndexed { index, name ->
                        IngredientRow(index = index + 1, name = name)
                    }
                }
            }
        }
    }
}

/** 食材行：序号（tnum 对齐）+ 食材名 */
@Composable
private fun IngredientRow(index: Int, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = AppColors.colorScheme.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.colorScheme.textPrimary,
        )
    }
}

/** 按菜备菜视图：每道菜一张卡（菜名 + 选菜人 + 备注） */
@Composable
private fun PerRecipeView(perRecipe: List<DiningRecipeShopping>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AppDimens.pagePadding,
            vertical = AppDimens.spaceSm,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
    ) {
        item(key = "per_recipe_header") {
            Text(
                text = stringResource(R.string.dining_shopping_list_per_recipe_section),
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.colorScheme.textSecondary,
            )
        }
        if (perRecipe.isEmpty()) {
            item(key = "per_recipe_empty") {
                GroupCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.dining_shopping_list_empty_per_recipe),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textTertiary,
                    )
                }
            }
        } else {
            itemsIndexed(perRecipe, key = { index, _ -> index }) { _, recipe ->
                RecipeCard(recipe = recipe)
            }
        }
    }
}

/** 按菜卡片：菜名 + 选菜人（脱敏手机号）+ 备注 */
@Composable
private fun RecipeCard(recipe: DiningRecipeShopping) {
    GroupCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = recipe.recipeName,
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.colorScheme.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        recipe.selectors.forEach { selector ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selector.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
                if (selector.note.isNotBlank()) {
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    Text(
                        text = stringResource(
                            R.string.dining_shopping_list_selector_note_format,
                            selector.note,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textTertiary,
                    )
                }
            }
        }
    }
}
