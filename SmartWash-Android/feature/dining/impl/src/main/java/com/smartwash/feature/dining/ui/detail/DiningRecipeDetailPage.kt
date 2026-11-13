package com.smartwash.feature.dining.ui.detail

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.toRecipeItem

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

        // 菜名 + 分类行
        Text(
            text = vo.name,
            style = MaterialTheme.typography.titleLarge,
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
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        // 食材分组卡
        GroupCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.dining_recipe_detail_ingredients_section),
                style = MaterialTheme.typography.titleSmall,
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
                                .padding(horizontal = AppDimens.spaceSm, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

        // 步骤分组卡（按换行分段编号；空步骤给引导文案）
        GroupCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.dining_recipe_detail_steps_section),
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.colorScheme.textPrimary,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            val segments = steps.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (segments.isEmpty()) {
                Text(
                    text = stringResource(R.string.dining_recipe_detail_steps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
            } else {
                segments.forEachIndexed { index, segment ->
                    Row {
                        Text(
                            // 步骤序号开 tabular numbers（规范 §2.2 硬规则）
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.colorScheme.primaryDark,
                        )
                        Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                        Text(
                            text = segment,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textPrimary,
                        )
                    }
                    if (index != segments.lastIndex) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    }
                }
            }
        }
        // 底部留白避让手势条
        Box(modifier = Modifier.height(32.dp))
    }
}
