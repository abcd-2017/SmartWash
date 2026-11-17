package com.smartwash.feature.dining.ui.order

import android.widget.Toast

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppTextField
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
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
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.ui.DiningNutritionWarningBar
import com.smartwash.feature.dining.logic.DiningCategoryChip
import com.smartwash.feature.dining.logic.DiningNutritionCalculator
import com.smartwash.feature.dining.logic.DiningOrderFormValidator.DiningOrderFormError

/** 左分类栏宽度（对齐点单页 RAIL_WIDTH 先例：分类名短词为主，84dp 兼顾两行折行） */
private val RAIL_WIDTH = 84.dp

/** 菜品行卡封面缩略尺寸（对齐点单页 DISH_COVER_SIZE 先例） */
private val DISH_COVER_SIZE = 56.dp

/** 菜品行卡最小行高（规范 §3.4 列表行卡 64dp 口径） */
private val DISH_ROW_MIN_HEIGHT = 64.dp

/** 创建页内容区三态：首载骨架 / 首载失败 / 内容（菜品列表可空态） */
private enum class CreateContentState { Skeleton, Error, Content }

/**
 * 创建订单页（07 号票，契约 §5.2 美团式双栏重构）：标题输入 + 左分类栏（每分类已选数徽标）+
 * 右菜品行卡列表（服务端分页 + 懒加载保留）+ 底部固定购物车栏（已选 N 道 · 清空[确认] ·
 * 主 CTA 发起点单）+ 营养超标警示条（仅超标时出现，非阻断）。提交成功进订单分享视图
 * （弹掉创建页层级：分享返回直回订单列表）。
 *
 * 复用历史菜单模式（13 号票）：路由携带 fromOrderId 进入——无分类栏与购物车交互，菜品区
 * 只读回显源单菜单并提示「将复用历史订单「x」的 N 道菜」，提交带 fromOrderId 由后端整份复制。
 *
 * 输入控件（规范 §3.7/§7）：AppTextField（common:ui 统一输入组件，正文 16sp）；
 * 菜品行勾选自绘（pressable + Check 图标，禁 M3 Checkbox/ripple）。
 */
@Composable
fun DiningOrderCreatePage(
    navController: NavHostController,
    viewModel: DiningOrderCreateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val title by viewModel.title.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val selectedIds by viewModel.selectedRecipeIds.collectAsState()
    val selectedRecipes by viewModel.selectedRecipes.collectAsState()
    val recipeCategories by viewModel.recipeCategories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val formError by viewModel.formError.collectAsState()
    val sourceTitle by viewModel.sourceTitle.collectAsState()
    val sourceMenuCount by viewModel.sourceMenuCount.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadMoreState by viewModel.loadMoreState.collectAsState()
    val keyword by viewModel.keyword.collectAsState()

    // 首载（栈上重组不重放；失败重试显式触发）
    LaunchedEffect(Unit) { viewModel.load() }

    // 清空已选确认弹窗（破坏性操作二次确认）
    var showClearConfirm by remember { mutableStateOf(false) }

    val contentState = when {
        recipes.isEmpty() && loadState is RequestState.Loading -> CreateContentState.Skeleton
        recipes.isEmpty() && loadState is RequestState.Idle -> CreateContentState.Skeleton
        recipes.isEmpty() && loadState is RequestState.Error -> CreateContentState.Error
        else -> CreateContentState.Content
    }

    // 分类已选数徽标（契约 §5.2：只统计本地已选数，无网络成本；全部=总数）
    val selectedCounts = remember(recipeCategories, selectedIds) {
        val counts = mutableMapOf<Long?, Int>()
        selectedIds.forEach { id ->
            val category = recipeCategories[id]
            counts[category] = (counts[category] ?: 0) + 1
        }
        counts
    }

    // 已选菜营养聚合（组合期纯计算，数据源为已选 VO 快照——跨分类切换不丢口径）
    val nutritionReport = remember(selectedRecipes, selectedIds) {
        DiningNutritionCalculator.summarize(
            selectedIds.mapNotNull { selectedRecipes[it] }
                .map { DiningNutritionCalculator.dishNutrition(it) },
        )
    }

    // 提交成功：进分享视图（携带创建响应的 orderId + joinToken——token 的唯一来源）。
    // popUpTo 弹掉创建页：分享页返回直接回订单列表，避免返回栈残留「创建中」状态
    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is RequestState.Success -> {
                val created = viewModel.consumeCreatedResult()
                viewModel.resetSubmitState()
                if (created == null) {
                    // token 缺失/orderId 非法视为异常创建结果（无法分享，提示后留在本页可重试）
                    Toast.makeText(
                        context,
                        context.getString(R.string.dining_order_create_result_invalid),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    navController.navigate(
                        DiningRoute.OrderShare(
                            orderId = created.orderId,
                            title = created.title,
                            joinToken = created.joinToken,
                        )
                    ) {
                        popUpTo<DiningRoute.OrderCreate> { inclusive = true }
                    }
                }
            }

            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.resetSubmitState()
            }

            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            // 规范 §4：标题/搜索输入框键盘弹出时整页上收，底部固定 CTA 随根布局抬升，
            // 不被软键盘遮挡（与 RechargePage 同口径）
            .imePadding()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            // PageHeader 置于滚动区外（固定头部：标题输入 + 分类栏为页面骨架，不随列表滚动）
            Column(modifier = Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.dining_order_create_title),
                    onBack = { navController.navigateUp() },
                )

                Crossfade(
                    targetState = contentState,
                    animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
                    label = "diningOrderCreateContent",
                ) { state ->
                    when (state) {
                        CreateContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                        CreateContentState.Error -> EmptyState(
                            // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                            icon = Icons.Default.Restaurant,
                            message = (loadState as? RequestState.Error)?.getMessage(context)
                                ?: stringResource(R.string.dining_order_create_load_failed),
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

                        CreateContentState.Content -> Column(modifier = Modifier.fillMaxSize()) {
                            // 标题（表单首字段，就近错误提示）
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppDimens.pagePadding),
                            ) {
                                Text(
                                    text = stringResource(R.string.dining_order_create_title_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AppColors.colorScheme.textPrimary,
                                    modifier = Modifier.padding(
                                        bottom = AppDimens.spaceXs,
                                        start = AppDimens.spaceXxs,
                                    ),
                                )
                                AppTextField(
                                    value = title,
                                    onValueChange = viewModel::onTitleChange,
                                    placeholder = stringResource(R.string.dining_order_create_title_hint),
                                    isError = formError == DiningOrderFormError.TITLE_REQUIRED ||
                                        formError == DiningOrderFormError.TITLE_TOO_LONG,
                                    supportingText = {
                                        if (formError == DiningOrderFormError.TITLE_REQUIRED ||
                                            formError == DiningOrderFormError.TITLE_TOO_LONG
                                        ) {
                                            Text(stringResource(viewModel.errorRes(formError!!)))
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // 菜品搜索（服务端模糊查询，防抖 300ms 重置分页重拉；搜索态切全库列表，
                            // 分类栏隐藏；复用模式菜单只读，不给搜索）
                            if (!viewModel.isReuseMode) {
                                DishSearchField(
                                    value = keyword,
                                    onValueChange = viewModel::onKeywordChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = AppDimens.pagePadding,
                                            end = AppDimens.pagePadding,
                                            top = AppDimens.spaceSm,
                                        ),
                                )
                            } else {
                                // 复用模式提示（13 号票）：菜单按源单整份复制、只读回显，不可修改
                                Text(
                                    text = stringResource(
                                        R.string.dining_order_create_reuse_hint_format,
                                        sourceTitle,
                                        sourceMenuCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary,
                                    modifier = Modifier.padding(
                                        start = AppDimens.pagePadding + AppDimens.spaceXxs,
                                        end = AppDimens.pagePadding,
                                        top = AppDimens.spaceSm,
                                    ),
                                )
                            }

                            when {
                                // 复用模式：保留分组只读回显（源单勾选态展示，无分类栏交互）
                                viewModel.isReuseMode -> ReuseGroupList(
                                    groups = groupRecipesByCategory(
                                        recipes,
                                        categories,
                                        stringResource(R.string.dining_order_create_menu_uncategorized),
                                    ),
                                    selectedIds = selectedIds,
                                    modifier = Modifier.weight(1f),
                                )

                                recipes.isEmpty() -> EmptyState(
                                    icon = Icons.Default.Restaurant,
                                    // 搜索无果与全库空区分：前者引导换词，后者引导新增
                                    message = if (keyword.isNotBlank()) {
                                        stringResource(
                                            R.string.dining_recipe_list_empty_search,
                                            keyword.trim(),
                                        )
                                    } else {
                                        stringResource(R.string.dining_recipe_list_empty)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = AppDimens.pagePadding),
                                )

                                else -> {
                                    // 搜索态切换为全库结果列表（契约 §5.2）：分类栏隐藏，选中分类保留
                                    val isSearching = keyword.isNotBlank()
                                    Row(modifier = Modifier.weight(1f)) {
                                        if (categories.isNotEmpty() && !isSearching) {
                                            val allLabel = stringResource(R.string.dining_category_all)
                                            val railChips = remember(categories, allLabel) {
                                                listOf(DiningCategoryChip(id = null, name = allLabel)) +
                                                    categories.map { DiningCategoryChip(it.safeId, it.safeName) }
                                            }
                                            CreateCategoryRail(
                                                chips = railChips,
                                                selectedCategoryId = selectedCategoryId,
                                                selectedCounts = selectedCounts,
                                                onSelect = viewModel::selectCategory,
                                                modifier = Modifier.width(RAIL_WIDTH),
                                            )
                                        }
                                        DishList(
                                            recipes = recipes,
                                            selectedIds = selectedIds,
                                            hasMore = hasMore,
                                            loadMoreState = loadMoreState,
                                            onLoadMore = viewModel::loadMore,
                                            onToggle = viewModel::toggleRecipe,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部固定购物车栏（契约 §5.2）：营养警示条（仅超标时出现）+ 表单菜单错误 + 已选数/
        // 清空/主 CTA；navigationBarsPadding 整体抬升，背景延伸至屏底避免按钮被手势区遮挡
        CreateBottomBar(
            selectedCount = selectedIds.size,
            nutritionReport = nutritionReport,
            menuError = formError.takeIf {
                it == DiningOrderFormError.MENU_EMPTY || it == DiningOrderFormError.MENU_TOO_MANY
            },
            menuErrorRes = { viewModel.errorRes(it) },
            isReuseMode = viewModel.isReuseMode,
            submitLoading = submitState is RequestState.Loading,
            onSubmit = viewModel::submit,
            onRequestClear = { showClearConfirm = true },
        )
    }

    if (showClearConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.dining_order_create_clear_confirm_title),
            message = stringResource(R.string.dining_order_create_clear_confirm_message),
            confirmText = stringResource(R.string.dining_order_create_clear),
            isDanger = true,
            onConfirm = {
                showClearConfirm = false
                viewModel.clearSelections()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

/**
 * 左分类栏（契约 §5.2）：分类名 + 已选数徽标（橙色小徽标，仅本地已选统计）；选中态
 * primarySoft 容器。点击 → 服务端按分类过滤重置分页（已选集合跨分类持久持有）。
 */
@Composable
private fun CreateCategoryRail(
    chips: List<DiningCategoryChip>,
    selectedCategoryId: Long?,
    selectedCounts: Map<Long?, Int>,
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
            val count = selectedCounts[chip.id] ?: 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 分类项最小 48dp 热区（规范 §3.2），且让选中底铺满整行高
                    .heightIn(min = 52.dp)
                    .background(if (selected) AppColors.colorScheme.primarySoft else Color.Transparent)
                    .pressable(onClick = { onSelect(chip.id) }, debounce = false)
                    .padding(horizontal = AppDimens.spaceXxs, vertical = AppDimens.spaceXs),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = chip.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) AppColors.colorScheme.primaryDark
                        else AppColors.colorScheme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.height(AppDimens.space2xs))
                        // 已选数徽标（橙色小徽标）：iconContainerOrange 底 + ongoingInk 字（双令牌，
                        // 对比度达标），数量开 tnum
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.colorScheme.ongoingInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppDimens.radiusFull))
                                .background(AppColors.colorScheme.iconContainerOrange)
                                .padding(horizontal = AppDimens.spaceXxs, vertical = AppDimens.space2xs),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 菜品行卡列表（契约 §5.2 右栏）：单列行卡（封面缩略 + 菜名 + 食材数·热量 + 右侧 48dp 加选
 * 按钮），触底自动加载更多（服务端分页保留），footer 三态失败不封底。
 */
@Composable
private fun DishList(
    recipes: List<DiningRecipeVo>,
    selectedIds: Set<Long>,
    hasMore: Boolean,
    loadMoreState: RequestState,
    onLoadMore: () -> Unit,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 触底探测（对齐订单列表先例）：末两项可见即触发下一页；加载中不重入，失败保留重试入口
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.spaceMd,
            top = AppDimens.spaceXs,
            end = AppDimens.spaceMd,
            bottom = AppDimens.spaceLg,
        ),
    ) {
        items(recipes, key = { it.safeId }) { recipe ->
            DishRow(
                recipe = recipe,
                selected = recipe.safeId in selectedIds,
                onToggle = { onToggle(recipe.safeId) },
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
                        // 触底已自动触发，加载点即进行中反馈
                        is RequestState.Loading -> LoadingDots()
                        is RequestState.Error -> AppButton(
                            text = stringResource(R.string.dining_order_load_more_failed),
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

/**
 * 菜品行卡（契约 §5.2）：GroupCard 唯一画法 + 整卡 pressable 切换勾选；56dp 封面缩略
 * （缺失回落浅绿底 + 菜名首字）+ 菜名（已选 primaryDark 强调）+ 食材数·热量副行 + 右侧
 * 48dp 加选按钮（已选态 primary 实底 + 白色对勾）。
 */
@Composable
private fun DishRow(
    recipe: DiningRecipeVo,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nutrition = DiningNutritionCalculator.dishNutrition(recipe)
    val formattedCalories = DiningNutritionCalculator.formatAmount(nutrition.calories)
    val calorieText = if (formattedCalories != null) {
        stringResource(R.string.dining_nutrition_calorie_badge_format, formattedCalories)
    } else {
        stringResource(R.string.dining_nutrition_calorie_unknown)
    }
    val ingredientText = if (recipe.safeIngredients.isNotEmpty()) {
        stringResource(
            R.string.dining_order_create_recipe_ingredients_format,
            recipe.safeIngredients.size,
        )
    } else {
        null
    }
    val subLine = listOfNotNull(ingredientText, calorieText).joinToString(" · ")

    GroupCard(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onToggle, debounce = false),
        contentPadding = PaddingValues(horizontal = AppDimens.cardPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DISH_ROW_MIN_HEIGHT),
        ) {
            DishThumb(coverUrl = recipe.safeCoverUrl, name = recipe.safeName)
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.safeName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) AppColors.colorScheme.primaryDark
                    else AppColors.colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subLine.isNotBlank()) {
                    Text(
                        text = subLine,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            AddDishButton(
                selected = selected,
                dishName = recipe.safeName,
                onClick = onToggle,
            )
        }
    }
}

/** 56dp 封面缩略（菜谱库搜索行同款）：有封面走网络图，缺失回落浅绿底 + 菜名首字 */
@Composable
private fun DishThumb(coverUrl: String, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(DISH_COVER_SIZE)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(AppColors.colorScheme.iconContainerGreen),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl.isNotBlank()) {
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
 * 加选按钮（契约 §5.2「右侧加号按钮，已选态变对勾」）：48dp 触控热区，视觉 32dp 圆——
 * 未选 outline 描边空心圆 + Canvas 白底加号（primary 线），已选 primary 实底 + 白色对勾。
 * 无障碍语义由外层 semantics contentDescription 承担（图形为装饰）。
 */
@Composable
private fun AddDishButton(
    selected: Boolean,
    dishName: String,
    onClick: () -> Unit,
) {
    val actionLabel = if (selected) {
        stringResource(R.string.dining_order_create_recipe_deselect, dishName)
    } else {
        stringResource(R.string.dining_order_create_recipe_select, dishName)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = actionLabel }
            .pressable(onClick = onClick, debounce = false),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (selected) {
                        Modifier.background(
                            color = AppColors.colorScheme.primary,
                            shape = RoundedCornerShape(AppDimens.radiusFull),
                        )
                    } else {
                        Modifier.border(
                            width = 1.dp,
                            color = AppColors.colorScheme.outline,
                            shape = RoundedCornerShape(AppDimens.radiusFull),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                // 加号：两条圆帽线段（对齐订单列表 FAB 自绘先例，不新增 Icons.* 使用）；
                // 令牌色在组合期取好再进 draw 块（Canvas 内不能调 composable）
                val plusColor = AppColors.colorScheme.primary
                Canvas(modifier = Modifier.size(14.dp)) {
                    val stroke = 1.8.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val arm = 5.dp.toPx()
                    drawLine(
                        color = plusColor,
                        start = Offset(center.x - arm, center.y),
                        end = Offset(center.x + arm, center.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = plusColor,
                        start = Offset(center.x, center.y - arm),
                        end = Offset(center.x, center.y + arm),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * 底部固定购物车栏（契约 §5.2）：营养超标警示条（仅超标时出现，非阻断，估算口径）+
 * 菜单表单错误（就近提示）+ 已选数/清空（确认）/主 CTA「发起点单」。浮层与滚动内容之间
 * 允许 0.5dp 分隔线（规范 §2.4 豁免条款）。
 */
@Composable
private fun CreateBottomBar(
    selectedCount: Int,
    nutritionReport: DiningNutritionCalculator.Report,
    menuError: DiningOrderFormError?,
    menuErrorRes: (DiningOrderFormError) -> Int,
    isReuseMode: Boolean,
    submitLoading: Boolean,
    onSubmit: () -> Unit,
    onRequestClear: () -> Unit,
) {
    Column(
        // 固定浮层（规范 §2.4 豁免：浮层与滚动内容之间可用 0.5dp 发丝线）：
        // surface 白底承载购物车栏语义，顶部发丝线与滚动区切分
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.colorScheme.divider),
        )
        Column(modifier = Modifier.navigationBarsPadding()) {
            if (nutritionReport.exceededItems.isNotEmpty()) {
                DiningNutritionWarningBar(items = nutritionReport.exceededItems)
            }
            if (menuError != null) {
                Text(
                    text = stringResource(menuErrorRes(menuError)),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.error,
                    modifier = Modifier.padding(
                        horizontal = AppDimens.pagePadding,
                        vertical = AppDimens.spaceXxs,
                    ),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceSm),
            ) {
                Text(
                    // 已选计数（数量数据档 AmountMedium + tnum，规范 §2.2 硬规则）
                    text = stringResource(R.string.dining_order_create_selected_count_format, selectedCount),
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.textPrimary,
                )
                if (!isReuseMode && selectedCount > 0) {
                    Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                    AppButton(
                        text = stringResource(R.string.dining_order_create_clear),
                        onClick = onRequestClear,
                        variant = AppButtonVariant.TEXT,
                        contentColor = AppColors.colorScheme.dangerInk,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AppButton(
                    text = stringResource(R.string.dining_order_create_submit),
                    onClick = onSubmit,
                    loading = submitLoading,
                )
            }
        }
    }
}

/**
 * 复用历史菜单模式的只读分组列表（13 号票保留）：每组区块标题（卡外）+ 菜品选择行
 * （只读勾选态回显）；group key 用分类 ID、行 key 用菜谱 ID。
 */
@Composable
private fun ReuseGroupList(
    groups: List<DiningMenuGroup>,
    selectedIds: Set<Long>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = AppDimens.pagePadding,
            end = AppDimens.pagePadding,
            bottom = AppDimens.spaceLg,
        ),
    ) {
        groups.forEach { group ->
            item(key = "group_${group.categoryId ?: 0L}") {
                Text(
                    text = group.categoryName,
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(
                        top = AppDimens.spaceSm,
                        bottom = AppDimens.spaceXs,
                        start = AppDimens.spaceXxs,
                    ),
                )
            }
            items(group.recipes, key = { it.safeId }) { recipe ->
                ReuseRecipeRow(recipe = recipe, selected = recipe.safeId in selectedIds)
            }
        }
    }
}

/** 复用模式只读行：勾选框（自绘）+ 菜名 + 食材数角标；整行不可点（菜单锁定） */
@Composable
private fun ReuseRecipeRow(recipe: DiningRecipeVo, selected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DISH_ROW_MIN_HEIGHT)
            .padding(vertical = AppDimens.spaceSm),
    ) {
        RecipeCheckbox(selected = selected)
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipe.safeName,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (recipe.safeIngredients.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.dining_order_create_recipe_ingredients_format,
                        recipe.safeIngredients.size,
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        }
    }
}

/** 自绘勾选框（规范 §7 禁 M3 Checkbox/ripple）：未选描边空心方块，选中 primary 实底 + 白色对勾 */
@Composable
private fun RecipeCheckbox(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .then(
                if (selected) {
                    Modifier.background(
                        color = AppColors.colorScheme.primary,
                        shape = RoundedCornerShape(AppDimens.radiusSmall),
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = AppColors.colorScheme.outline,
                        shape = RoundedCornerShape(AppDimens.radiusSmall),
                    )
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                // 行内已有菜名语义，勾选图形为装饰性（无障碍由整行承担）
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 菜品分组模型：[categoryId] 为 null 表示未分类组（收尾渲染） */
private data class DiningMenuGroup(
    val categoryId: Long?,
    val categoryName: String,
    val recipes: List<DiningRecipeVo>,
)

/**
 * 菜谱按分类目录分组（复用模式只读回显用，组合期纯计算）：
 * - 有 categoryId 且命中目录 → 按目录序分组（分类目录后端已按 sort/id 升序返回，保序）；
 * - 未分类（categoryId null/0）或不存在的分类 → 归并「未分类」组收尾。
 * [uncategorizedLabel] 由调用方传 strings.xml 文案——纯计算不持有文案（toCategoryChips 先例）。
 */
private fun groupRecipesByCategory(
    recipes: List<DiningRecipeVo>,
    categories: List<DiningCategoryVo>,
    uncategorizedLabel: String,
): List<DiningMenuGroup> {
    if (recipes.isEmpty()) return emptyList()
    val categoryIds = categories.map { it.safeId }.toSet()
    val grouped = recipes.groupBy { recipe ->
        val categoryId = recipe.categoryId
        if (categoryId != null && categoryId > 0 && categoryId in categoryIds) categoryId else null
    }
    val namedGroups = categories.mapNotNull { category ->
        grouped[category.safeId]?.let { recipesInCategory ->
            DiningMenuGroup(category.safeId, category.safeName, recipesInCategory)
        }
    }
    val uncategorized = grouped[null].orEmpty()
    return if (uncategorized.isEmpty()) {
        namedGroups
    } else {
        namedGroups + DiningMenuGroup(null, uncategorizedLabel, uncategorized)
    }
}

/** 菜品搜索输入框（§3.7：前缀 16dp 搜索图标组件统一着色 + 48dp 自绘清空热区） */
@Composable
private fun DishSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(R.string.dining_order_create_search_hint),
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
