package com.smartwash.feature.dining.ui.order

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import com.smartwash.feature.dining.logic.DiningOrderFormValidator.DiningOrderFormError

/** 创建页内容区三态：首载骨架 / 首载失败 / 内容（菜品列表可空态） */
private enum class CreateContentState { Skeleton, Error, Content }

/**
 * 创建订单页（07 号票）：标题输入 + 按分类分组勾选公共库菜品（分组选菜面；美团式左右联动
 * 选菜为点单页后续票）+ 底部固定 CTA。提交成功进订单分享视图（弹掉创建页层级：分享返回
 * 直回订单列表）。
 *
 * 复用历史菜单模式（13 号票）：路由携带 fromOrderId 进入——菜品区只读回显源单菜单并提示
 * 「将复用历史订单「x」的 N 道菜」，提交带 fromOrderId 由后端整份复制（见 ViewModel）。
 *
 * 输入控件（规范 §3.7/§7）：OutlinedTextField + tokenTextFieldColors（T4 新增菜谱页先例），
 * 正文 16sp；菜品行勾选自绘（pressable + Check 图标，禁 M3 Checkbox/ripple）。
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
    val loadState by viewModel.loadState.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val formError by viewModel.formError.collectAsState()
    val sourceTitle by viewModel.sourceTitle.collectAsState()
    val sourceMenuCount by viewModel.sourceMenuCount.collectAsState()

    // 首载（栈上重组不重放；失败重试显式触发）
    LaunchedEffect(Unit) { viewModel.load() }

    val contentState = when {
        recipes.isEmpty() && loadState is RequestState.Loading -> CreateContentState.Skeleton
        recipes.isEmpty() && loadState is RequestState.Idle -> CreateContentState.Skeleton
        recipes.isEmpty() && loadState is RequestState.Error -> CreateContentState.Error
        else -> CreateContentState.Content
    }

    // 分组派生（组合期纯计算）：按分类目录排序分组，未分类/孤儿分类归「未分类」组收尾
    val uncategorizedLabel = stringResource(R.string.dining_order_create_menu_uncategorized)
    val groups = remember(categories, recipes, uncategorizedLabel) {
        groupRecipesByCategory(recipes, categories, uncategorizedLabel)
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
    ) {
        PageHeader(
            title = stringResource(R.string.dining_order_create_title),
            onBack = { navController.navigateUp() },
        )

        Box(modifier = Modifier.weight(1f)) {
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
                                modifier = Modifier.padding(bottom = AppDimens.spaceXs, start = 4.dp),
                            )
                            OutlinedTextField(
                                value = title,
                                onValueChange = viewModel::onTitleChange,
                                placeholder = { Text(stringResource(R.string.dining_order_create_title_hint)) },
                                isError = formError == DiningOrderFormError.TITLE_REQUIRED ||
                                    formError == DiningOrderFormError.TITLE_TOO_LONG,
                                supportingText = {
                                    if (formError == DiningOrderFormError.TITLE_REQUIRED ||
                                        formError == DiningOrderFormError.TITLE_TOO_LONG
                                    ) {
                                        Text(stringResource(viewModel.errorRes(formError!!)))
                                    }
                                },
                                // §3.7：输入正文禁用 14sp 及以下
                                textStyle = MaterialTheme.typography.bodyLarge,
                                singleLine = true,
                                shape = RoundedCornerShape(AppDimens.radiusMd),
                                colors = tokenTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // 复用模式提示（13 号票）：菜单按源单整份复制、只读回显，不可修改
                        if (viewModel.isReuseMode) {
                            Text(
                                text = stringResource(
                                    R.string.dining_order_create_reuse_hint_format,
                                    sourceTitle,
                                    sourceMenuCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textSecondary,
                                modifier = Modifier.padding(
                                    start = AppDimens.pagePadding + 4.dp,
                                    end = AppDimens.pagePadding,
                                    bottom = AppDimens.spaceXs,
                                ),
                            )
                        }

                        // 勾选计数（数量开 tabular numbers，规范 §2.2 硬规则）
                        Text(
                            text = stringResource(
                                R.string.dining_order_create_selected_count_format,
                                selectedIds.size,
                            ),
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.colorScheme.textPrimary,
                            modifier = Modifier.padding(
                                top = AppDimens.spaceLg,
                                bottom = AppDimens.spaceXs,
                                start = AppDimens.pagePadding + 4.dp,
                            ),
                        )
                        // 菜单就近错误提示
                        if (formError == DiningOrderFormError.MENU_EMPTY ||
                            formError == DiningOrderFormError.MENU_TOO_MANY
                        ) {
                            Text(
                                text = stringResource(viewModel.errorRes(formError!!)),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.error,
                                modifier = Modifier.padding(
                                    start = AppDimens.pagePadding + 4.dp,
                                    bottom = AppDimens.spaceXs,
                                ),
                            )
                        }

                        if (groups.isEmpty()) {
                            EmptyState(
                                icon = Icons.Default.Restaurant,
                                message = stringResource(R.string.dining_recipe_list_empty),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = AppDimens.pagePadding),
                            )
                        } else {
                            RecipeGroupList(
                                groups = groups,
                                selectedIds = selectedIds,
                                onToggle = viewModel::toggleRecipe,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        // 底部固定 CTA（菜品多时滚动到底不可达由固定条解决；避让手势条）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.colorScheme.background)
                .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceSm),
        ) {
            AppButton(
                text = stringResource(R.string.dining_order_create_submit),
                onClick = viewModel::submit,
                loading = submitState is RequestState.Loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(
            modifier = Modifier.height(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        )
    }
}

/** 分组菜品列表：每组区块标题（卡外）+ 菜品选择行；group key 用分类 ID、行 key 用菜谱 ID */
@Composable
private fun RecipeGroupList(
    groups: List<DiningMenuGroup>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
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
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceXs, start = 4.dp),
                )
            }
            items(group.recipes, key = { it.safeId }) { recipe ->
                RecipeSelectRow(
                    recipe = recipe,
                    selected = recipe.safeId in selectedIds,
                    onToggle = { onToggle(recipe.safeId) },
                )
            }
        }
    }
}

/** 菜品选择行：勾选框（自绘圆角方块 + Check）+ 菜名 + 食材数角标；整行可点切换 */
@Composable
private fun RecipeSelectRow(
    recipe: DiningRecipeVo,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onToggle, debounce = false)
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
                    color = AppColors.colorScheme.textTertiary,
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
                // 行内已有菜名语义，勾选图形为装饰性（无障碍由整行 pressable 承担）
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
 * 菜谱按分类目录分组（组合期纯计算）：
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

/** 输入框令牌色（T4 新增菜谱页同款：r12 + outline 描边 + surfaceVariant 底） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    errorBorderColor = AppColors.colorScheme.error,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)
