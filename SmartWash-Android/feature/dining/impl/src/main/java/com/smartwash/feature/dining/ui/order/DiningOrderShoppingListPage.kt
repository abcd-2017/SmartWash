package com.smartwash.feature.dining.ui.order

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.components.animatedListItem
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.dining.logic.DiningNutritionCalculator
import com.smartwash.feature.dining.logic.DiningRecipeShopping
import com.smartwash.feature.dining.logic.DiningShoppingListPoster
import com.smartwash.feature.dining.logic.DiningShoppingListResult
import com.smartwash.feature.dining.logic.buildShoppingListText
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val calorieTarget by viewModel.calorieTarget.collectAsState()

    // 双视图切换（纯 UI 态；不参与网络请求）——rememberSaveable：跳菜谱详情再返回时
    // NavHost 已重组本页，普通 remember 必丢（返回即弹回「按食材买菜」，D28 修）
    var tab by rememberSaveable { mutableStateOf(TAB_INGREDIENTS) }

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

    // 分享海报：清单按食材行绘制成竖版图片，经系统分享面板分发
    val scope = rememberCoroutineScope()
    val untitledFallback = stringResource(R.string.dining_order_title_placeholder)
    val posterSubtitle = stringResource(
        R.string.dining_shopping_list_poster_subtitle,
        result?.memberCount ?: 0,
        result?.ingredients?.size ?: 0,
    )
    val posterFooter = stringResource(R.string.dining_shopping_list_poster_footer)
    val posterChooser = stringResource(R.string.dining_shopping_list_share_poster_chooser)
    val posterFail = stringResource(R.string.dining_shopping_list_poster_fail)
    val sharePoster: () -> Unit = {
        val current = result
        if (current != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.Default) {
                    DiningShoppingListPoster.build(
                        title = orderTitle.ifBlank { untitledFallback },
                        subtitle = posterSubtitle,
                        lines = DiningShoppingListPoster.buildLines(current, checked),
                        footer = posterFooter,
                    )
                }
                shareBitmap(context, bitmap, posterChooser, posterFail)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.surface),
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
                AppButton(
                    text = stringResource(R.string.dining_shopping_list_share_poster),
                    onClick = sharePoster,
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
                calorieTarget = calorieTarget,
                onToggleChecked = viewModel::toggleChecked,
                onTabSelected = { tab = it },
                onRecipeClick = { recipeId ->
                    // 按菜备菜行跳菜谱详情看做法（老快照无 recipeId 时行内守卫不跳）
                    navController.navigate(DiningRoute.RecipeDetail(recipeId))
                },
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

/** 内容区：热量卡（仅 v2 快照有数据时出现）+ Tab 栏固定，视图切换走 Crossfade（卡片入场令牌，reduced motion 由 motionSpec 降级） */
@Composable
private fun ShoppingListContent(
    tab: Int,
    result: DiningShoppingListResult,
    checked: Set<String>,
    calorieTarget: Int,
    onToggleChecked: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
    onRecipeClick: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 热量卡（契约 §5.4）：总热量 tnum 大字 + 人数 stepper（1-20 本地态）+ 人均 + 今日剩余 + 未知提示；
        // 老快照无 v2 字段（totalCalories 与 unknownCount 皆 null）时整卡隐藏
        if (result.totalCalories != null || result.calorieUnknownDishCount != null) {
            CalorieCard(result = result, calorieTarget = calorieTarget)
        }
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
                    result = result,
                    checked = checked,
                    onToggle = onToggleChecked,
                )
            } else {
                PerRecipeView(perRecipe = result.perRecipe, onRecipeClick = onRecipeClick)
            }
        }
    }
}

/**
 * 热量卡（契约 §5.4）：总热量大字（AppTextStyles.DataLarge 36sp tnum，本页唯一视觉锚点）+
 * 人数 stepper（- N +，1-20，默认参与者人数，本地状态不落库）+ 人均热量（总/人数本地实时算）
 * + 「N 道菜热量未知，未计入」提示行。
 */
@Composable
private fun CalorieCard(
    result: DiningShoppingListResult,
    calorieTarget: Int,
    modifier: Modifier = Modifier,
) {
    // 人数本地态：默认快照参与者人数（0 兜底收敛到 1）；不落库，离开页面即重置
    var people by remember(result.memberCount) {
        mutableStateOf(DiningNutritionCalculator.coercePeopleCount(result.memberCount))
    }
    val perCapita = DiningNutritionCalculator.perCapita(result.totalCalories, people)
    val unknownCount = result.calorieUnknownDishCount ?: 0

    GroupCard(modifier = modifier.padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceSm)) {
        Text(
            text = stringResource(R.string.dining_shopping_list_calorie_card_title),
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.colorScheme.textSecondary,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        Row(verticalAlignment = Alignment.Bottom) {
            // TD27/P2-8：全部菜热量未知时聚合 totalCalories=0.0——渲染「≈0」误导（仿佛确认了
            // 零热量）；未知菜数 == 已点菜总数（按菜备菜视图仅含被点过的菜）时改显「热量未知」
            val allCaloriesUnknown = result.calorieUnknownDishCount != null &&
                result.perRecipe.isNotEmpty() &&
                result.calorieUnknownDishCount == result.perRecipe.size
            val formattedTotal = if (allCaloriesUnknown) {
                null
            } else {
                DiningNutritionCalculator.formatAmount(result.totalCalories)
            }
            Text(
                text = if (formattedTotal != null) {
                    stringResource(R.string.dining_shopping_list_total_calories_value_format, formattedTotal)
                } else {
                    stringResource(R.string.dining_shopping_list_total_calories_unknown)
                },
                style = AppTextStyles.DataLarge,
                color = AppColors.colorScheme.textPrimary,
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
            Text(
                text = stringResource(R.string.dining_unit_kcal),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.colorScheme.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        // 人数 stepper（- N +）：按钮 48dp 触控热区，图形为 Canvas 圆帽线段（无新增 Icons.* 使用）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.dining_shopping_list_people_label),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.weight(1f),
            )
            PeopleStepButton(
                isPlus = false,
                enabled = people > DiningNutritionCalculator.PEOPLE_MIN,
                onClick = { people = DiningNutritionCalculator.coercePeopleCount(people - 1) },
            )
            Text(
                text = people.toString(),
                style = AppTextStyles.AmountMedium,
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.padding(horizontal = AppDimens.spaceSm),
            )
            PeopleStepButton(
                isPlus = true,
                enabled = people < DiningNutritionCalculator.PEOPLE_MAX,
                onClick = { people = DiningNutritionCalculator.coercePeopleCount(people + 1) },
            )
        }
        if (perCapita != null) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            val formattedPerCapita = DiningNutritionCalculator.formatAmount(perCapita)
            Text(
                text = stringResource(
                    R.string.dining_shopping_list_per_capita_format,
                    formattedPerCapita.orEmpty(),
                ),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textPrimary,
            )
        }
        // 今日剩余（契约 §三.3）：目标 - 本单总热量；负值（本单超目标）用危险色
        val total = result.totalCalories
        if (total != null) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            val remaining = calorieTarget - total
            val remainingText = DiningNutritionCalculator.formatAmount(remaining).orEmpty()
            Text(
                text = stringResource(R.string.dining_shopping_list_remaining_calories_format, remainingText),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = if (remaining < 0) AppColors.colorScheme.dangerInk
                else AppColors.colorScheme.textPrimary,
            )
        }
        if (unknownCount > 0) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            Text(
                text = stringResource(R.string.dining_shopping_list_calorie_unknown_hint_format, unknownCount),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.ongoingInk,
            )
        }
    }
}

/** 人数步进按钮：48dp 触控热区 + 20dp Canvas 圆帽线段（- / +）；越界禁用降为 textTertiary */
@Composable
private fun PeopleStepButton(
    isPlus: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val glyphColor = if (enabled) AppColors.colorScheme.textSecondary
    else AppColors.colorScheme.textTertiary
    val label = if (isPlus) {
        stringResource(R.string.dining_shopping_list_people_increase)
    } else {
        stringResource(R.string.dining_shopping_list_people_decrease)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .then(if (enabled) Modifier.pressable(onClick = onClick, debounce = false) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val arm = 7.dp.toPx()
            drawLine(
                color = glyphColor,
                start = Offset(center.x - arm, center.y),
                end = Offset(center.x + arm, center.y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            if (isPlus) {
                drawLine(
                    color = glyphColor,
                    start = Offset(center.x, center.y - arm),
                    end = Offset(center.x, center.y + arm),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 按食材买菜视图：v2 快照优先渲染 [DiningShoppingListResult.ingredientTotals] 聚合行
 * （名称 总量单位 · N 道菜，无数量配料仅列名），老快照回落食材名并集逐名渲染。
 * 行首自绘勾选框标记「已买」（勾选为本地态，按订单持久化，仅影响展示与导出文本）。
 */
@Composable
private fun IngredientsView(
    result: DiningShoppingListResult,
    checked: Set<String>,
    onToggle: (String) -> Unit,
) {
    // 行模型统一：v2 聚合行（名 + 数量单位 · N 道菜）与老快照名行共用一套渲染
    data class IngredientRowUi(val name: String, val amountText: String?)

    val rows: List<IngredientRowUi> = result.ingredientTotals?.map { total ->
        val amountText = if (total.totalValue == null) {
            null
        } else {
            val value = DiningNutritionCalculator.formatAmount(total.totalValue).orEmpty()
            val amount = stringResource(
                R.string.dining_shopping_list_ingredient_amount_format,
                value,
                total.unit,
            )
            if (total.dishCount > 0) {
                amount + " · " + stringResource(
                    R.string.dining_shopping_list_ingredient_dishes_format,
                    total.dishCount,
                )
            } else {
                amount
            }
        }
        IngredientRowUi(name = total.name, amountText = amountText)
    } ?: result.ingredients.map { IngredientRowUi(name = it, amountText = null) }

    val checkedCount = rows.count { it.name in checked }
    // 平铺清单：左右 24dp 页边距由容器统一提供，行间 0.5dp 发丝线
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AppDimens.pagePadding,
            vertical = AppDimens.spaceSm,
        ),
    ) {
        item(key = "ingredients_header") {
            Text(
                text = stringResource(
                    R.string.dining_shopping_list_ingredients_progress_format,
                    checkedCount,
                    rows.size,
                ),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(vertical = AppDimens.spaceXs),
            )
        }
        if (rows.isEmpty()) {
            item(key = "ingredients_empty") {
                Text(
                    text = stringResource(R.string.dining_shopping_list_empty_ingredients),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        } else {
            itemsIndexed(rows, key = { _, row -> row.name }) { index, row ->
                IngredientRow(
                    name = row.name,
                    amountText = row.amountText,
                    checked = row.name in checked,
                    onToggle = { onToggle(row.name) },
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = AppColors.colorScheme.divider,
                    )
                }
            }
        }
    }
}

/** 食材行：自绘勾选框 + 食材名 + 右侧聚合数量（整行可点切换已买；已买降级为次要文字） */
@Composable
private fun IngredientRow(
    name: String,
    amountText: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // 列表行最小 48dp 热区（规范 §3.2）；纵 spaceSm（左右页边距由列表容器提供）
            .heightIn(min = 48.dp)
            .pressable(onClick = onToggle, debounce = false)
            .semantics { contentDescription = name }
            .padding(vertical = AppDimens.spaceSm),
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
            // 已买加中折线（清单语义：划掉=已完成，仅变灰可读性弱）
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (amountText != null) {
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            Text(
                // 聚合数量（数量数据开 tnum，规范 §2.2 硬规则）
                text = amountText,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
            )
        }
    }
}

/** 按菜备菜视图：每道菜一行（平铺：菜名 + 选菜人 + 备注），行间 0.5dp 发丝线；行可点跳菜谱详情 */
@Composable
private fun PerRecipeView(
    perRecipe: List<DiningRecipeShopping>,
    onRecipeClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AppDimens.pagePadding,
            vertical = AppDimens.spaceSm,
        ),
    ) {
        item(key = "per_recipe_header") {
            Text(
                text = stringResource(R.string.dining_shopping_list_per_recipe_section),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(vertical = AppDimens.spaceXs),
            )
        }
        if (perRecipe.isEmpty()) {
            item(key = "per_recipe_empty") {
                Text(
                    text = stringResource(R.string.dining_shopping_list_empty_per_recipe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        } else {
            // key 用内容组合而非索引：列表重排时避免错误复用
            itemsIndexed(perRecipe, key = { index, item -> "${item.recipeName}_$index" }) { index, recipe ->
                RecipeCard(
                    recipe = recipe,
                    onClick = if (recipe.recipeId > 0L) {
                        { onRecipeClick(recipe.recipeId) }
                    } else {
                        null
                    },
                    modifier = Modifier.then(animatedListItem()),
                )
                if (index != perRecipe.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = AppColors.colorScheme.divider,
                    )
                }
            }
        }
    }
}

/** 按菜行（平铺清单）：56dp 封面缩略 + 菜名/选菜人（脱敏手机号）/备注；行间 0.5dp 发丝线；有 recipeId 整行可点进详情 */
@Composable
private fun RecipeCard(
    recipe: DiningRecipeShopping,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .pressable(onClick = onClick, debounce = false)
                        .semantics { contentDescription = recipe.recipeName }
                } else {
                    Modifier
                },
            )
            .padding(vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeShoppingThumb(coverUrl = recipe.coverUrl, name = recipe.recipeName)
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Column {
            Text(
                text = recipe.recipeName,
                style = AppTextStyles.CardTitle,
                color = AppColors.colorScheme.textPrimary,
            )
            if (recipe.selectors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppDimens.space2xs))
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
    }
}

/** 按菜行 56dp 封面缩略（与点单页同款口径）：真封面走网络图，内置伪键/缺失回落品牌占位图（D24） */
@Composable
private fun RecipeShoppingThumb(coverUrl: String, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(AppColors.colorScheme.iconContainerGreen),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl.isNotBlank() && !coverUrl.contains("dining/covers/category_")) {
            SmartWashAsyncImage(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.dining_recipe_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }
    }
}

/**
 * 海报落盘 + 系统分享：复用 :feature:update 的 FileProvider 授权目录（cacheDir/asset_tag_labels），
 * 与工具箱标签图同先例；失败静默 Toast，不打断清单浏览。
 */
private fun shareBitmap(context: Context, bitmap: Bitmap, chooserTitle: String, failMessage: String) {
    runCatching {
        val cacheDir = java.io.File(context.cacheDir, "asset_tag_labels").apply { mkdirs() }
        val file = java.io.File(cacheDir, "shopping_list_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }.onFailure {
        Toast.makeText(context, failMessage, Toast.LENGTH_SHORT).show()
    }
}
