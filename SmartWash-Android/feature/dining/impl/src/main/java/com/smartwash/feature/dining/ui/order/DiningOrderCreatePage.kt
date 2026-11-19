package com.smartwash.feature.dining.ui.order

import android.widget.Toast

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppTextField
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.components.animatedListItem
import com.smartwash.common.ui.components.dialogEnterMotion
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
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
import com.smartwash.feature.dining.ui.DiningAllergenWarningBar
import com.smartwash.feature.dining.ui.DiningNutritionWarningBar
import com.smartwash.feature.dining.logic.DiningAllergenGuard
import com.smartwash.feature.dining.logic.DiningCategoryChip
import com.smartwash.feature.dining.logic.DiningNutritionCalculator
import com.smartwash.feature.dining.logic.DiningOrderFormValidator.DiningOrderFormError
import com.smartwash.feature.dining.logic.DiningPortionAdvisor

/** 左分类栏宽度（对齐点单页 RAIL_WIDTH 先例：分类名短词为主，84dp 兼顾两行折行） */
private val RAIL_WIDTH = 84.dp

/** 营养三小项数值格式化（份口径；整数值去掉小数尾巴，与徽标口径一致） */
private fun macroAmount(value: Double): String =
    DiningNutritionCalculator.formatAmount(value).orEmpty()

/** 复用模式只读行最小行高（对齐行内勾选框 + 两行文字的视觉节奏） */
private val DISH_ROW_MIN_HEIGHT = 72.dp

/** 菜品行方图边长（1:1，固定尺寸——不随文字行数变化，用户定案 2026-11-18） */

/** 菜品行底部容器高度：热量贴上沿、⊕ 贴下沿，比按钮触控盒(48dp)稍高（用户定案） */
private val DISH_ROW_FOOTER_HEIGHT = 56.dp
private val DISH_THUMB_SIZE = 64.dp


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
    val favoritingIds by viewModel.favoritingIds.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadMoreState by viewModel.loadMoreState.collectAsState()
    val keyword by viewModel.keyword.collectAsState()
    val peopleCount by viewModel.peopleCount.collectAsState()
    val calorieTarget by viewModel.calorieTarget.collectAsState()
    val userAllergens by viewModel.userAllergens.collectAsState()
    val allergenById by viewModel.allergenById.collectAsState()
    val inviteTarget by viewModel.inviteTarget.collectAsState()
    val draftMessage by viewModel.draftMessage.collectAsState()

    // 首载（栈上重组不重放；失败重试显式触发）
    LaunchedEffect(Unit) { viewModel.load() }

    // 邀请：跳「邀请加入」（与分享同一页，二维码 + 复制链接），不再只复制到剪贴板
    LaunchedEffect(inviteTarget) {
        val target = inviteTarget ?: return@LaunchedEffect
        viewModel.consumeInviteLink()
        navController.navigate(
            DiningRoute.OrderShare(
                orderId = target.orderId,
                title = target.title,
                joinToken = target.joinToken,
            )
        )
    }

    // 草稿写操作失败提示（409 未收敛/网络失败；组合期禁副作用）
    LaunchedEffect(draftMessage) {
        draftMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeDraftMessage()
        }
    }

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

    // 已选菜列表（底栏购物车弹窗数据源；按已选集合顺序展开 VO 快照）
    val selectedDishes = remember(selectedIds, selectedRecipes) {
        selectedIds.mapNotNull { selectedRecipes[it] }
    }

    // 已选菜营养聚合（组合期纯计算，数据源为已选 VO 快照——跨分类切换不丢口径）
    val nutritionReport = remember(selectedRecipes, selectedIds) {
        DiningNutritionCalculator.summarize(
            selectedIds.mapNotNull { selectedRecipes[it] }
                .map { DiningNutritionCalculator.dishNutrition(it) },
        )
    }

    // 忌口命中（契约 §三.1）：选中菜 allergens（详情按需缓存）∩ 用户忌口；组合期纯计算
    val allergenHits = remember(selectedRecipes, selectedIds, allergenById, userAllergens) {
        val dishes = selectedIds.mapNotNull { id ->
            selectedRecipes[id]?.let { vo ->
                DiningAllergenGuard.SelectedDish(
                    recipeId = vo.safeId,
                    recipeName = vo.safeName,
                    allergens = allergenById[id]?.toSet().orEmpty(),
                )
            }
        }
        DiningAllergenGuard.hits(dishes, userAllergens)
    }

    // 份量建议（契约 §2.5）：人均 = 本单总热量 ÷ 人数，参考 = 用户目标 ÷ 3（默认 2000÷3）
    val portionAdvice = remember(nutritionReport.totalCalories, peopleCount, calorieTarget) {
        DiningPortionAdvisor.advice(nutritionReport.totalCalories, peopleCount, calorieTarget)
    }

    // 提交成功（提交即结束）：进购买清单结果页。popUpTo 弹掉创建页：结果页返回直接回订单列表，
    // 避免返回栈残留「编辑中」状态
    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is RequestState.Success -> {
                val submitted = viewModel.consumeSubmittedResult()
                viewModel.resetSubmitState()
                if (submitted == null) {
                    // orderId 非法视为异常提交结果（无法进结果页，提示后留在本页可重试）
                    Toast.makeText(
                        context,
                        context.getString(R.string.dining_order_create_result_invalid),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    navController.navigate(DiningRoute.ShoppingList(orderId = submitted.orderId)) {
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
            // 列表区整体白底（用户定案 2026-11-18「整体白」）；底栏同为 surface，靠发丝线切分
            .background(AppColors.colorScheme.surface)
            // 键盘避让决策（TD15）：本页是「表单头 + 长列表 + 固定 CTA」混合页，不再整页上收
            // （原 RechargePage 表单页口径套用导致键盘弹出列表被压缩、CTA 弹起跳变）。
            // 标题/搜索框位于页面上半部不会被键盘遮挡；键盘弹出时列表下半部被遮可接受——
            // 输入场景下列表本就无需交互，与美团点单页行为一致。
    ) {
        Box(modifier = Modifier.weight(1f)) {
            // PageHeader 置于滚动区外（固定头部：标题输入 + 分类栏为页面骨架，不随列表滚动）
            Column(modifier = Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.dining_order_create_title),
                    onBack = { navController.navigateUp() },
                    actions = {
                        // 草稿协同：邀请入口常显（复用模式无草稿语义，隐藏）——无 orderId 时先惰性创建，
                        // 复制加入深链到剪贴板（见 inviteLink 一次性事件）
                        if (!viewModel.isReuseMode) {
                            AppButton(
                                text = stringResource(R.string.dining_order_create_invite),
                                onClick = viewModel::requestInviteLink,
                                variant = AppButtonVariant.TEXT,
                            )
                        }
                    },
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
                            // 标题行：点击标题/铅笔 → 弹窗编辑（用户定案 2026-11-18）。空标题也可先行
                            // 选菜/邀请；必填校验在「发起点单」时触发，未填时标题下方出红字反馈
                            var showTitleDialog by remember { mutableStateOf(false) }
                            val titleError = formError == DiningOrderFormError.TITLE_REQUIRED ||
                                formError == DiningOrderFormError.TITLE_TOO_LONG
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXxs),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title.ifBlank {
                                            stringResource(R.string.dining_order_create_title_hint)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (title.isBlank()) AppColors.colorScheme.textTertiary
                                        else AppColors.colorScheme.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .pressable(onClick = { showTitleDialog = true }, debounce = false),
                                    )
                                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.dining_order_create_title_edit),
                                        tint = AppColors.colorScheme.textSecondary,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .pressable(onClick = { showTitleDialog = true }, debounce = false)
                                            .padding(2.dp),
                                    )
                                }
                                // 空标题点「发起点单」被拦时的红字反馈（标题下方，就近提示）
                                if (titleError && formError != null) {
                                    Text(
                                        text = stringResource(viewModel.errorRes(formError!!)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.colorScheme.error,
                                        modifier = Modifier.padding(
                                            start = AppDimens.spaceXxs,
                                            top = AppDimens.space2xs,
                                        ),
                                    )
                                }
                            }
                            if (showTitleDialog) {
                                TitleEditDialog(
                                    value = title,
                                    onValueChange = viewModel::onTitleChange,
                                    isError = titleError,
                                    errorText = if (titleError && formError != null) {
                                        stringResource(viewModel.errorRes(formError!!))
                                    } else {
                                        null
                                    },
                                    onDismiss = { showTitleDialog = false },
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
                                        // 上下间距对称（原只有 top）：修复「输入框底 padding=0 与列表相贴」（用户复核 2026-11-18）
                                        .padding(
                                            start = AppDimens.pagePadding,
                                            end = AppDimens.pagePadding,
                                            top = AppDimens.spaceXs,
                                            bottom = AppDimens.spaceSm,
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
                                            favoritingIds = favoritingIds,
                                            hasMore = hasMore,
                                            loadMoreState = loadMoreState,
                                            onLoadMore = viewModel::loadMore,
                                            onToggle = viewModel::toggleRecipe,
                                            onViewRecipe = { recipeId ->
                                                navController.navigate(DiningRoute.RecipeDetail(recipeId))
                                            },
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            selectedCategoryId = selectedCategoryId,
                                            // 搜索态全宽列表：行内水平 0 + 外层 24dp 页面边距
                                            //（对齐菜谱库搜索行，避免贴屏幕边）；分类态贴 rail 保持 6/12
                                            rowPadding = if (isSearching) {
                                                PaddingValues(vertical = 4.dp)
                                            } else {
                                                PaddingValues(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                                            },
                                            // 列表区白底（用户定案「整体白」）；分类栏保持浅灰
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(AppColors.colorScheme.surface)
                                                .then(
                                                    if (isSearching) {
                                                        Modifier.padding(horizontal = AppDimens.pagePadding)
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
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
            selectedDishes = selectedDishes,
            nutritionReport = nutritionReport,
            allergenHits = allergenHits,
            peopleCount = peopleCount,
            onPeopleChange = viewModel::onPeopleChange,
            menuError = formError.takeIf {
                it == DiningOrderFormError.MENU_EMPTY || it == DiningOrderFormError.MENU_TOO_MANY
            },
            menuErrorRes = { viewModel.errorRes(it) },
            isReuseMode = viewModel.isReuseMode,
            submitLoading = submitState is RequestState.Loading,
            onSubmit = viewModel::submit,
            onRequestClear = { showClearConfirm = true },
            onRemoveDish = { recipeId -> viewModel.toggleRecipe(recipeId) },
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
            // rail 灰底（surfaceVariant）+ 选中白块——用户原话：「选中=列表同色（白），未选中反转色（灰）」
            .background(AppColors.colorScheme.surfaceVariant),
    ) {
        items(chips, key = { it.id ?: -1L }) { chip ->
            val selected = chip.id == selectedCategoryId
            val count = selectedCounts[chip.id] ?: 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 分类项最小 48dp 热区（规范 §3.2），且让选中底铺满整行高；
                    // 选中=白块（surface，与列表卡同色）——外卖式「选中与列表同色」
                    .heightIn(min = 52.dp)
                    // 选中=白块 surface + primaryDark 字（与列表卡同色，用户原话）
                    .background(if (selected) AppColors.colorScheme.surface else Color.Transparent)
                    .pressable(onClick = { onSelect(chip.id) }, debounce = false)
                    .padding(horizontal = AppDimens.spaceXxs, vertical = AppDimens.spaceXs),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = chip.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) AppColors.colorScheme.onPrimarySoft
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
    favoritingIds: Set<Long>,
    hasMore: Boolean,
    loadMoreState: RequestState,
    onLoadMore: () -> Unit,
    onToggle: (Long) -> Unit,
    onViewRecipe: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    selectedCategoryId: Long?,
    rowPadding: PaddingValues = PaddingValues(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 分类切换回顶（D32）：列表滚动位置不随分类重置会停在上类目的偏移处
    LaunchedEffect(selectedCategoryId) {
        listState.scrollToItem(0)
    }

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
        // 平铺列表：无卡片、无左右缩进（项内自持 4dp），仅底部避让；行间隔由项内 4dp 提供
        contentPadding = PaddingValues(bottom = AppDimens.spaceLg),
    ) {
        items(recipes, key = { it.safeId }) { recipe ->
            DishRow(
                recipe = recipe,
                selected = recipe.safeId in selectedIds,
                onToggle = { onToggle(recipe.safeId) },
                onViewRecipe = { onViewRecipe(recipe.safeId) },
                onToggleFavorite = { onToggleFavorite(recipe.safeId) },
                rowPadding = rowPadding,
                modifier = Modifier.then(animatedListItem()),
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
 * 菜品行（用户复核 2026-11-18 定案 · 平铺外卖式）：无卡片容器，白底平铺、行间仅 4dp 留白无线。
 * 整行点击 = 菜谱详情；只有 ⊕ 按钮 = 加菜/取消（用户定案）。内容行 = 固定尺寸封面
 * + 菜名单行 + 食材两行；底行 = 热量（左）+ 加选按钮（右）。
 */
@Composable
private fun DishRow(
    recipe: DiningRecipeVo,
    selected: Boolean,
    onToggle: () -> Unit,
    onViewRecipe: () -> Unit,
    onToggleFavorite: () -> Unit,
    rowPadding: PaddingValues = PaddingValues(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    modifier: Modifier = Modifier,
) {
    val ingredients = recipe.safeIngredients
    // 份热量（食材下的第三行，用户定案 2026-11-18：加内容填满图高，热量口径与其他页一致）
    val servingCalories = DiningNutritionCalculator.servingCalories(
        recipe.caloriesTotal,
        recipe.caloriesPer100g,
    )
    val formattedCalories = DiningNutritionCalculator.formatAmount(servingCalories)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onViewRecipe, debounce = false)
            // 行内水平边距由调用方给：分类态贴 rail（左 6/右 12），搜索态水平 0
            // （整列表外层再套 24dp 页面边距，避免贴屏幕边）
            .padding(rowPadding),
    ) {
        Row(
            // 顶对齐（用户定案 2026-11-18：文字与图片中线重合是错的——标题顶边贴图顶）
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DishThumb(
                coverUrl = recipe.safeCoverUrl,
                name = recipe.safeName,
                favorited = recipe.favorited == true,
                onToggleFavorite = onToggleFavorite,
                // 固定尺寸方图（1:1）：不随文字行数变化——带热量 4 行的行图比 3 行大一号
                // 是 bug，图片尺寸恒定（用户定案 2026-11-18）
                modifier = Modifier.size(DISH_THUMB_SIZE),
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            Column(
                modifier = Modifier
                    .weight(1f)
                    // 文字列右距 = 按钮右距(12) + 4 = 16dp，确保文字不越过添加按钮显示区
                    .padding(end = 4.dp),
            ) {
                Text(
                    text = recipe.safeName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) AppColors.colorScheme.primaryDark
                    else AppColors.colorScheme.textPrimary,
                    // 单行省略（用户复核 2026-11-18）：折行断词难看，对齐外卖单行卡
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ingredients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AppDimens.space2xs))
                    // 食材固定占两行（用户定案 2026-11-18 选 3）：不足也留白，行高统一
                    Text(
                        text = ingredients.joinToString("、"),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textSecondary,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        // 底部容器（用户定案 2026-11-18）：热量在左上、⊕ 在右下，父容器比按钮高一点——
        // 两者不在同一行，热量贴上沿、按钮贴下沿
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DISH_ROW_FOOTER_HEIGHT),
        ) {
            if (formattedCalories != null) {
                Text(
                    text = stringResource(
                        R.string.dining_nutrition_calorie_badge_format,
                        formattedCalories,
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = DISH_THUMB_SIZE + AppDimens.spaceXs),
                )
            }
            AddDishButton(
                selected = selected,
                dishName = recipe.safeName,
                onClick = onToggle,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * 封面缩略（高度由调用方约束，行内与右侧文字块同高）：有封面走网络图，缺失回落浅绿底 +
 * 菜名首字。[onToggleFavorite] 非空时图片左上角叠收藏心（D22 新增收藏入口）：40dp 热区
 * 压图左上，视觉 28dp 半透明白圆底 + 16dp 心形保证任意封面/占位底上可读；内层 pressable
 * 消费点击不触发行跳详情。购物车缩略不传该参数即无收藏层。
 */
@Composable
private fun DishThumb(
    coverUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    favorited: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    // 分类占位图（dining/covers/category_*.png）视为无封面（TD18）：缩小后语义不清且
    // 红白色跳出清氧绿系，回落浅绿底 + 菜名首字更干净；转换器新数据已不再生成占位键
    val hasRealCover = coverUrl.isNotBlank() && !coverUrl.contains("dining/covers/category_")
    Box(
        modifier = modifier
            // 56dp 小图用最小档 10dp（radiusSmall）：12dp 占边长 21% 显得过圆
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(AppColors.colorScheme.iconContainerGreen),
        contentAlignment = Alignment.Center,
    ) {
        if (hasRealCover) {
            SmartWashAsyncImage(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 无封面占位：品牌碗筷线稿（D24，替代首字——首字在小图上可读性差且无品牌感）
            Image(
                painter = painterResource(R.drawable.dining_recipe_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            )
        }
        if (onToggleFavorite != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // 48dp 触控热区（与菜谱库卡按钮同档规范，ui-ux-pro-max：Android ≥48dp）；
                    // 防连击由 VM favoritingIds 守卫
                    .size(48.dp)
                    .pressable(onClick = onToggleFavorite, debounce = false),
                // 图标贴热区左上（图片角），热区其余为纯触控区——图标居中会让心形落在图片中心
                contentAlignment = Alignment.TopStart,
            ) {
                // 未收藏=深绿描边心（浅底可读；与菜谱库同色阶）/ 已收藏=品牌红实心心
                Icon(
                    imageVector = if (favorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (favorited) AppColors.colorScheme.danger else AppColors.colorScheme.primaryDark,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

/**
 * 加选按钮（契约 §5.2「右侧加号按钮，已选态变对勾」）：视觉 24dp 圆 + 四周 12dp padding
 * 外扩出 48dp 触控热区（用户指定做法：外层 Box 挂点击、padding 负责扩区）——
 * 未选 outline 描边空心圆 + Canvas 加号（primary 线），已选 primary 实底 + 白色对勾。
 * 无障碍语义由外层 semantics contentDescription 承担（图形为装饰）。
 */
@Composable
private fun AddDishButton(
    selected: Boolean,
    dishName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = if (selected) {
        stringResource(R.string.dining_order_create_recipe_deselect, dishName)
    } else {
        stringResource(R.string.dining_order_create_recipe_select, dishName)
    }
    Box(
        modifier = modifier
            .semantics { contentDescription = actionLabel }
            .pressable(onClick = onClick, debounce = false)
            // 点击区外扩：视觉 24dp + 四周 12dp padding = 48dp 触控热区
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 状态变换 spring 弹跳（TD19/D11）：勾选瞬间放大过冲回落（MediumBouncy 过冲特性），
        // 取消时回弹 1f；无障碍「移除动效」场景 spring 值瞬时到达，无持续动画
        val scale by animateFloatAsState(
            targetValue = if (selected) 1.12f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "addDishScale",
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
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
                    modifier = Modifier.size(14.dp),
                )
            } else {
                // 加号：两条圆帽线段（对齐订单列表 FAB 自绘先例，不新增 Icons.* 使用）；
                // 令牌色在组合期取好再进 draw 块（Canvas 内不能调 composable）
                val plusColor = AppColors.colorScheme.primary
                Canvas(modifier = Modifier.size(12.dp)) {
                    val stroke = 1.6.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val arm = 4.5.dp.toPx()
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
 * 标题编辑弹窗（用户定案 2026-11-18）：点击标题行唤起，内嵌 [AppTextField] + 取消/确定。
 * 输入即回写 VM（[onValueChange]）——标题校验错误随输入即时清除；确定/取消均只关弹窗。
 */
@Composable
private fun TitleEditDialog(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    text = stringResource(R.string.dining_order_create_title_label),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                AppTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = stringResource(R.string.dining_order_create_title_hint),
                    isError = isError,
                    supportingText = { if (errorText != null) Text(errorText) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppButton(
                        text = stringResource(com.smartwash.common.ui.R.string.cancel),
                        onClick = onDismiss,
                        variant = AppButtonVariant.TEXT,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    AppButton(
                        text = stringResource(R.string.dining_order_create_title_confirm),
                        onClick = onDismiss,
                        variant = AppButtonVariant.SECONDARY,
                        small = true,
                    )
                }
            }
        }
    }
}

/**
 * 底部固定栏（用户定案 2026-11-18「外卖式购物车」）：警示条（营养/忌口，仅出现时）+ 表单错误 +
 * 单行「已选 N 道 · M 人」（点击开购物车弹窗）+ 右 CTA「提交订单」。
 * 已选明细 / 移除 / 人数 stepper 全部收进弹窗；浮层与滚动内容间允许 0.5dp 发丝线（§2.4 豁免）。
 */
@Composable
private fun CreateBottomBar(
    selectedDishes: List<DiningRecipeVo>,
    nutritionReport: DiningNutritionCalculator.Report,
    allergenHits: List<DiningAllergenGuard.Hit>,
    peopleCount: Int,
    onPeopleChange: (Int) -> Unit,
    menuError: DiningOrderFormError?,
    menuErrorRes: (DiningOrderFormError) -> Int,
    isReuseMode: Boolean,
    submitLoading: Boolean,
    onSubmit: () -> Unit,
    onRequestClear: () -> Unit,
    onRemoveDish: (Long) -> Unit,
) {
    var showCart by remember { mutableStateOf(false) }
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
            if (allergenHits.isNotEmpty()) {
                DiningAllergenWarningBar(hits = allergenHits)
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
            // 单行：左「已选 · 人数」信息（点击开购物车弹窗）+ 右 CTA
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .pressable(onClick = { showCart = true }, debounce = false)
                        .padding(vertical = AppDimens.space2xs),
                ) {
                    Text(
                        text = stringResource(
                            R.string.dining_order_create_selected_count_format,
                            selectedDishes.size,
                        ),
                        style = AppTextStyles.AmountMedium,
                        color = AppColors.colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 与「· N 人」共享基线：18sp/12sp 混排时小字不再悬空
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (!isReuseMode) {
                        Text(
                            text = stringResource(
                                R.string.dining_order_create_people_count_format,
                                peopleCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.dining_order_create_cart),
                        tint = AppColors.colorScheme.textTertiary,
                        modifier = Modifier
                            .padding(start = AppDimens.space2xs)
                            .size(18.dp),
                    )
                }
                AppButton(
                    text = stringResource(R.string.dining_order_create_submit),
                    onClick = onSubmit,
                    loading = submitLoading,
                    // small=按内容宽度（非 small 默认 fillMaxWidth 会挤掉左侧信息行）
                    small = true,
                )
            }
        }
    }
    if (showCart) {
        CartSheet(
            dishes = selectedDishes,
            peopleCount = peopleCount,
            onPeopleChange = onPeopleChange,
            onRemoveDish = onRemoveDish,
            onClear = onRequestClear,
            onDismiss = { showCart = false },
        )
    }
}

/**
 * 购物车弹窗（用户定案 2026-11-18）：已选菜明细（缩略图 + 菜名 + 移除）+ 人数 stepper + 清空。
 * 用 M3 ModalBottomSheet（对齐 PaymentPage 先例：surface 容器 + 自绘拖拽条 + 顶部圆角）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CartSheet(
    dishes: List<DiningRecipeVo>,
    peopleCount: Int,
    onPeopleChange: (Int) -> Unit,
    onRemoveDish: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = AppDimens.radiusXl, topEnd = AppDimens.radiusXl),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = AppDimens.spaceSm)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusFull))
                    .background(AppColors.colorScheme.outline),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            // 底部不再额外加 vertical spaceSm：底部留白只由末尾 spacer 的手势条高度承担
            contentPadding = PaddingValues(
                start = AppDimens.pagePadding,
                end = AppDimens.pagePadding,
                top = AppDimens.spaceSm,
                bottom = 0.dp,
            ),
        ) {
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.dining_order_create_cart),
                        style = MaterialTheme.typography.headlineSmall,
                        color = AppColors.colorScheme.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (dishes.isNotEmpty()) {
                        AppButton(
                            text = stringResource(R.string.dining_order_create_clear_selected),
                            onClick = onClear,
                            variant = AppButtonVariant.TEXT,
                            contentColor = AppColors.colorScheme.dangerInk,
                            small = true,
                        )
                    }
                }
            }
            if (dishes.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.Restaurant,
                        message = stringResource(R.string.dining_order_create_cart_empty),
                    )
                }
            } else {
                items(dishes, key = { it.safeId }) { dish ->
                    CartDishRow(dish = dish, onRemove = { onRemoveDish(dish.safeId) })
                }
            }
            item(key = "people") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppDimens.spaceSm),
                ) {
                    Text(
                        text = stringResource(R.string.dining_portion_people_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    PortionStepButton(
                        isPlus = false,
                        enabled = peopleCount > DiningNutritionCalculator.PEOPLE_MIN,
                        onClick = { onPeopleChange(peopleCount - 1) },
                    )
                    Text(
                        text = peopleCount.toString(),
                        style = AppTextStyles.AmountMedium,
                        color = AppColors.colorScheme.textPrimary,
                        modifier = Modifier.padding(horizontal = AppDimens.spaceXs),
                    )
                    PortionStepButton(
                        isPlus = true,
                        enabled = peopleCount < DiningNutritionCalculator.PEOPLE_MAX,
                        onClick = { onPeopleChange(peopleCount + 1) },
                    )
                }
            }
            // 底部不再自加 spacer：ModalBottomSheet 的 contentWindowInsets 已含系统栏内边距，
            // 叠加会让「人数」离屏底过远（用户复核 2026-11-18）
        }
    }
}

/** 购物车行：缩略图 + 菜名 + 移除（文字动作，dangerInk） */
@Composable
private fun CartDishRow(dish: DiningRecipeVo, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceXs),
    ) {
        DishThumb(
            coverUrl = dish.safeCoverUrl,
            name = dish.safeName,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Text(
            text = dish.safeName,
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = stringResource(R.string.dining_order_create_cart_remove),
            onClick = onRemove,
            variant = AppButtonVariant.TEXT,
            contentColor = AppColors.colorScheme.dangerInk,
            small = true,
        )
    }
}

/**
 * 份量建议行（契约 §2.5/§三.5）：人数 stepper（1-20，本地态）+ 建议文案
 * （人均 vs 目标÷3 → 够/偏多/偏少）。总热量未知（advice=null）时只展示人数，不渲染建议。
 */
@Composable
private fun PortionAdviceRow(
    advice: DiningPortionAdvisor.Advice?,
    peopleCount: Int,
    onPeopleChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.dining_portion_people_label),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.weight(1f),
            )
            PortionStepButton(
                isPlus = false,
                enabled = peopleCount > DiningNutritionCalculator.PEOPLE_MIN,
                onClick = { onPeopleChange(peopleCount - 1) },
            )
            Text(
                text = peopleCount.toString(),
                style = AppTextStyles.AmountMedium,
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.padding(horizontal = AppDimens.spaceSm),
            )
            PortionStepButton(
                isPlus = true,
                enabled = peopleCount < DiningNutritionCalculator.PEOPLE_MAX,
                onClick = { onPeopleChange(peopleCount + 1) },
            )
        }
        if (advice != null) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            val perCapita = DiningNutritionCalculator.formatAmount(advice.perCapita).orEmpty()
            val text = when (advice.verdict) {
                DiningPortionAdvisor.Verdict.ENOUGH -> stringResource(
                    R.string.dining_portion_advice_enough_format, advice.peopleCount, perCapita,
                )
                DiningPortionAdvisor.Verdict.MORE -> stringResource(
                    R.string.dining_portion_advice_more_format, advice.peopleCount, perCapita,
                )
                DiningPortionAdvisor.Verdict.LESS -> stringResource(
                    R.string.dining_portion_advice_less_format, advice.peopleCount, perCapita,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = if (advice.verdict == DiningPortionAdvisor.Verdict.ENOUGH) {
                    AppColors.colorScheme.textGreen
                } else {
                    AppColors.colorScheme.textSecondary
                },
            )
        }
    }
}

/** 人数步进按钮：48dp 触控热区 + 20dp Canvas 圆帽线段（- / +）；越界禁用降为 textTertiary */
@Composable
private fun PortionStepButton(
    isPlus: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val glyphColor = if (enabled) AppColors.colorScheme.textSecondary
    else AppColors.colorScheme.textTertiary
    val label = if (isPlus) {
        stringResource(R.string.dining_portion_people_increase)
    } else {
        stringResource(R.string.dining_portion_people_decrease)
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
                ReuseRecipeRow(
                    recipe = recipe,
                    selected = recipe.safeId in selectedIds,
                    modifier = Modifier.then(animatedListItem()),
                )
            }
        }
    }
}

/** 复用模式只读行：勾选框（自绘）+ 菜名 + 食材数角标；整行不可点（菜单锁定） */
@Composable
private fun ReuseRecipeRow(
    recipe: DiningRecipeVo,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
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
        // L4 compact：头部堆叠场景收窄到 44dp，头部区（标题+搜索）占比 ≤25% 屏高
        compact = true,
        modifier = modifier,
    )
}
