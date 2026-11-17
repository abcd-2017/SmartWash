package com.smartwash.feature.dining.ui.order

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningRecipeShopping
import com.smartwash.feature.dining.logic.DiningShoppingListResult
import com.smartwash.feature.dining.logic.buildShoppingListText

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
    val clipboardManager = LocalClipboardManager.current
    val loadState by viewModel.loadState.collectAsState()
    val result by viewModel.result.collectAsState()
    val orderTitle by viewModel.orderTitle.collectAsState()
    val checked by viewModel.checked.collectAsState()

    // 双视图切换（纯 UI 态；不参与网络请求）
    var tab by remember { mutableStateOf(TAB_INGREDIENTS) }

    LaunchedEffect(Unit) { viewModel.load() }

    // 失败反馈（Toast 收进 effect——组合期禁副作用）
    LaunchedEffect(loadState) {
        val state = loadState
        if (state is RequestState.Error) {
            Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
        }
    }

    // 复制清单：勾选态 + 按菜视图拼成文本，发给家人/自己对着买
    val copyList: () -> Unit = {
        val current = result
        if (current != null) {
            clipboardManager.setText(
                AnnotatedString(buildShoppingListText(orderTitle, current, checked)),
            )
            Toast.makeText(
                context,
                R.string.dining_shopping_list_copy_done,
                Toast.LENGTH_SHORT,
            ).show()
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
            actions = {
                AppButton(
                    text = stringResource(R.string.dining_shopping_list_copy),
                    onClick = copyList,
                    variant = AppButtonVariant.TEXT,
                )
            },
        )

        val current = result
        when {
            current != null -> ShoppingListContent(
                tab = tab,
                result = current,
                checked = checked,
                onToggleChecked = viewModel::toggleChecked,
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
    checked: Set<String>,
    onToggleChecked: (String) -> Unit,
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
                IngredientsView(
                    ingredients = result.ingredients,
                    checked = checked,
                    onToggle = onToggleChecked,
                )
            } else {
                PerRecipeView(perRecipe = result.perRecipe)
            }
        }
    }
}

/**
 * 按食材买菜视图：食材名并集清单（保序），行首自绘勾选框标记「已买」。
 * 勾选为本地态（按订单持久化），便于买菜时对照勾掉；仅影响展示与导出文本。
 */
@Composable
private fun IngredientsView(
    ingredients: List<String>,
    checked: Set<String>,
    onToggle: (String) -> Unit,
) {
    val checkedCount = ingredients.count { it in checked }
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
                text = stringResource(
                    R.string.dining_shopping_list_ingredients_progress_format,
                    checkedCount,
                    ingredients.size,
                ),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textSecondary,
            )
        }
        item(key = "ingredients_card") {
            GroupCard(modifier = Modifier.fillMaxWidth()) {
                if (ingredients.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dining_shopping_list_empty_ingredients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary,
                    )
                } else {
                    ingredients.forEachIndexed { index, name ->
                        IngredientRow(
                            name = name,
                            checked = name in checked,
                            onToggle = { onToggle(name) },
                        )
                        // 卡内行间 0.5dp 发丝线（规范 §3.1：仅同一容器内部）
                        if (index != ingredients.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = AppColors.colorScheme.divider,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 食材行：自绘勾选框 + 食材名（整行可点切换已买；已买降级为次要文字） */
@Composable
private fun IngredientRow(
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // 列表行最小 48dp 热区（规范 §3.2）
            .heightIn(min = 48.dp)
            .pressable(onClick = onToggle, debounce = false)
            .semantics { contentDescription = name }
            .padding(vertical = AppDimens.spaceXs),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .then(
                    if (checked) {
                        Modifier.background(
                            color = AppColors.colorScheme.primary,
                            shape = RoundedCornerShape(AppDimens.radiusSmall),
                        )
                    } else {
                        Modifier
                            .background(
                                color = AppColors.colorScheme.surface,
                                shape = RoundedCornerShape(AppDimens.radiusSmall),
                            )
                            .border(
                                width = 1.dp,
                                color = AppColors.colorScheme.outline,
                                shape = RoundedCornerShape(AppDimens.radiusSmall),
                            )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) AppColors.colorScheme.textSecondary
            else AppColors.colorScheme.textPrimary,
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
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textSecondary,
            )
        }
        if (perRecipe.isEmpty()) {
            item(key = "per_recipe_empty") {
                GroupCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.dining_shopping_list_empty_per_recipe),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
            }
        } else {
            // key 用内容组合而非索引：列表重排时避免错误复用
            itemsIndexed(perRecipe, key = { index, item -> "${item.recipeName}_$index" }) { _, recipe ->
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
            style = AppTextStyles.CardTitle,
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
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
            }
        }
    }
}
