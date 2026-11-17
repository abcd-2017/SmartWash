package com.smartwash.feature.dining.ui.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.toRecipeItem
import com.smartwash.feature.dining.ui.DiningCalorieBadge

/** 详情内容区三态：首载骨架 / 失败 / 内容 */
private enum class DetailContentState { Skeleton, Error, Content }

/**
 * 菜谱详情页（04 号票）：封面 + 菜名/分类 + 食材名列表 + 做法步骤（按换行分段展示）。
 * 数据为一次性详情请求；页面纯渲染，业务在 ViewModel。
 */
@Composable
fun DiningRecipeDetailPage(
    navController: NavHostController,
    recipeId: Long,
    viewModel: DiningRecipeDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val detailState by viewModel.detailState.collectAsState()
    val recipe by viewModel.recipe.collectAsState()
    val favorited by viewModel.favorited.collectAsState()
    val favoriting by viewModel.favoriting.collectAsState()

    LaunchedEffect(recipeId) { viewModel.load(recipeId) }

    val contentState = when {
        detailState is RequestState.Loading && recipe == null -> DetailContentState.Skeleton
        detailState is RequestState.Error && recipe == null -> DetailContentState.Error
        else -> DetailContentState.Content
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.dining_recipe_detail_title),
            onBack = { navController.navigateUp() },
            actions = {
                // 收藏入口（TD22/P2-6）：此前仅列表卡片可收藏，详情页无入口；spring 弹跳反馈（D11）。
                // 自绘 pressable 心形（规范 §7 禁 M3 交互组件/ripple）
                if (recipe != null) {
                    val favLabel = stringResource(
                        if (favorited) R.string.dining_recipe_unfavorite else R.string.dining_recipe_favorite,
                    )
                    val favScale by animateFloatAsState(
                        targetValue = if (favorited) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "favoriteScale",
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = favLabel }
                            .pressable(onClick = { viewModel.toggleFavorite() }, debounce = false),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (favorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (favorited) AppColors.colorScheme.dangerInk
                            else AppColors.colorScheme.textSecondary,
                            modifier = Modifier
                                .size(26.dp)
                                .scale(favScale),
                        )
                    }
                }
            },
        )

        Crossfade(
            targetState = contentState,
            animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
            label = "diningDetailContent",
        ) { state ->
            when (state) {
                DetailContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                DetailContentState.Error -> EmptyState(
                    // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                    icon = Icons.Default.Restaurant,
                    message = (detailState as? RequestState.Error)?.getMessage(context)
                        ?: stringResource(R.string.dining_recipe_detail_load_failed),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding),
                    action = {
                        AppButton(
                            small = true,
                            text = stringResource(R.string.dining_recipe_action_retry),
                            onClick = { viewModel.load(recipeId) },
                            variant = AppButtonVariant.SECONDARY,
                        )
                    },
                )

                DetailContentState.Content -> recipe?.let { vo ->
                    DetailContent(vo = vo.toRecipeItem(), steps = vo.safeSteps)
                }
            }
        }
    }
}

/** 详情内容（封面锚点 + 信息卡）：食材 chips + 步骤分段；纯渲染，safe 值来自映射层 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    vo: com.smartwash.feature.dining.logic.DiningRecipeItem,
    steps: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding),
    ) {
        // 封面 16:9（视觉锚点）；未上传渲染 surfaceVariant 占位底
        SmartWashAsyncImage(
            url = vo.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(AppDimens.radiusLg)),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))

        Text(
            text = vo.name,
            style = AppTextStyles.CardTitle,
            color = AppColors.colorScheme.textPrimary,
        )
        if (vo.categoryName.isNotBlank()) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            Text(
                text = vo.categoryName,
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.colorScheme.textSecondary,
            )
        }
        // 份热量徽标行（TD23/P2-5）：列表卡片有 kcal 详情页反而没有——信息回补（缺失渲染「热量未知」）
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiningCalorieBadge(calories = vo.calories)
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        GroupCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.dining_recipe_detail_ingredients_section),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            if (vo.ingredients.isEmpty()) {
                Text(
                    text = stringResource(R.string.dining_recipe_detail_ingredients_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
            } else {
                // 食材名 chips：FlowRow 换行流式排布
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
                ) {
                    vo.ingredients.forEach { ingredient ->
                        Text(
                            text = ingredient,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppDimens.radiusFull))
                                .background(AppColors.colorScheme.surfaceVariant)
                                .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceXxs),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

        // 步骤分组卡（TD24/P1-3/P2-1/P2-2）：渲染层清洗源数据——剥离 markdown「**」加粗
        // 标记与原文自带编号（与渲染序号双重显示）；「-」开头行识别为子注解（缩进浅色不编号）
        GroupCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.dining_recipe_detail_steps_section),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            val stepLines = parseStepLines(steps)
            if (stepLines.isEmpty()) {
                Text(
                    text = stringResource(R.string.dining_recipe_detail_steps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
            } else {
                var mainStepNo = 0
                stepLines.forEachIndexed { index, line ->
                    when (line) {
                        is StepLine.Main -> {
                            mainStepNo++
                            Row {
                                Text(
                                    // 步骤序号开 tabular numbers（规范 §2.2 硬规则）
                                    text = "$mainStepNo.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    color = AppColors.colorScheme.primaryDark,
                                )
                                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppColors.colorScheme.textPrimary,
                                )
                            }
                        }

                        is StepLine.Sub -> Row(modifier = Modifier.padding(start = AppDimens.spaceLg)) {
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textSecondary,
                            )
                        }
                    }
                    if (index != stepLines.lastIndex) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    }
                }
            }
        }
        // 底部避让手势条/三键导航（不硬编码高度）+ 末卡呼吸空间
        Spacer(
            modifier = Modifier.height(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    AppDimens.spaceLg,
            ),
        )
    }
}

/** 步骤行模型：主步骤（自动编号）与子注解（「-」开头原文行，缩进浅色不编号） */
internal sealed interface StepLine {
    data class Main(val text: String) : StepLine
    data class Sub(val text: String) : StepLine
}

/** 原文自带编号前缀（「1.」「2、」「3)」——HowToCook/CookLikeHOC 步骤文本常见），渲染层剥离避免双重编号 */
private val STEP_NO_PREFIX = Regex("^\\d+[.、)）]\\s*")

/**
 * 步骤文本解析（TD24）：按行拆分 → 全局剥离 markdown「**」标记（HowToCook 原文加粗语法
 * 直接入库导致星号泄露渲染）→ 「-」开头行归子注解、其余行剥原文编号归主步骤。
 * 纯函数无 UI 依赖，供详情页渲染与单元测试共用。
 */
internal fun parseStepLines(raw: String): List<StepLine> = raw
    .split('\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { line ->
        val cleaned = line.replace("**", "").trim()
        when {
            cleaned.startsWith("-") || cleaned.startsWith("–") || cleaned.startsWith("—") ->
                StepLine.Sub(cleaned.trimStart('-', '–', '—', ' ').trim())

            else -> StepLine.Main(cleaned.replace(STEP_NO_PREFIX, "").trim())
        }
    }

