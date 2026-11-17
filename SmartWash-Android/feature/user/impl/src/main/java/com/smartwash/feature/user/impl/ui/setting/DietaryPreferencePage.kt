package com.smartwash.feature.user.impl.ui.setting

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
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
import com.smartwash.feature.user.impl.R

/** 饮食偏好内容区三态：首载骨架 / 首载失败 / 内容 */
private enum class PreferenceContentState { Skeleton, Error, Content }

/**
 * 饮食偏好页（契约 §三.2）：忌口 7 标签多选 + 每日热量/钠目标 stepper；进入时 GET 回填，
 * 保存走 PUT。数据经 [com.smartwash.feature.dining.api.DiningApi] 跨域访问（user-impl → dining-api）。
 *
 * 视觉：设置页分组卡唯一画法（GroupCard）+ 48dp 热区自绘 chip/stepper（规范 §7 禁 M3 交互组件）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietaryPreferencePage(
    navController: NavController,
    viewModel: DietaryPreferenceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val loadState by viewModel.loadState.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val allergens by viewModel.allergens.collectAsState()
    val calorieTarget by viewModel.calorieTarget.collectAsState()
    val sodiumTarget by viewModel.sodiumTarget.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    // 保存结果反馈（Toast 收进 effect——组合期禁副作用）；成功后返回设置页
    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is RequestState.Success -> {
                viewModel.consumeSaveState()
                Toast.makeText(context, R.string.dietary_preference_save_success, Toast.LENGTH_SHORT).show()
                navController.navigateUp()
            }

            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.consumeSaveState()
            }

            else -> Unit
        }
    }

    val contentState = when {
        loadState is RequestState.Loading -> PreferenceContentState.Skeleton
        loadState is RequestState.Error -> PreferenceContentState.Error
        else -> PreferenceContentState.Content
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background),
    ) {
        PageHeader(
            title = stringResource(R.string.dietary_preference_title),
            onBack = { navController.navigateUp() },
        )

        Crossfade(
            targetState = contentState,
            animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
            label = "dietaryPreferenceContent",
        ) { state ->
            when (state) {
                PreferenceContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                PreferenceContentState.Error -> EmptyState(
                    icon = Icons.Default.Restaurant,
                    message = (loadState as? RequestState.Error)?.getMessage(context)
                        ?: stringResource(R.string.dietary_preference_load_failed),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding),
                    action = {
                        AppButton(
                            small = true,
                            text = stringResource(R.string.dietary_preference_retry),
                            onClick = { viewModel.load() },
                            variant = AppButtonVariant.SECONDARY,
                        )
                    },
                )

                PreferenceContentState.Content -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppDimens.pagePadding),
                ) {
                    GroupCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.dietary_preference_allergens_label),
                            style = AppTextStyles.SectionTitle,
                            color = AppColors.colorScheme.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                        Text(
                            text = stringResource(R.string.dietary_preference_allergens_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
                        ) {
                            DietaryPreferenceViewModel.ALLERGENS.forEach { code ->
                                AllergenChip(
                                    code = code,
                                    selected = code in allergens,
                                    onClick = { viewModel.toggleAllergen(code) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppDimens.cardSpacing))
                    GroupCard(modifier = Modifier.fillMaxWidth()) {
                        TargetStepperRow(
                            label = stringResource(R.string.dietary_preference_calorie_label),
                            value = calorieTarget,
                            unit = stringResource(R.string.dietary_unit_kcal),
                            increaseLabel = stringResource(R.string.dietary_preference_calorie_increase),
                            decreaseLabel = stringResource(R.string.dietary_preference_calorie_decrease),
                            onDecrease = { viewModel.onCalorieTargetChange(calorieTarget - 50) },
                            onIncrease = { viewModel.onCalorieTargetChange(calorieTarget + 50) },
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                        TargetStepperRow(
                            label = stringResource(R.string.dietary_preference_sodium_label),
                            value = sodiumTarget,
                            unit = stringResource(R.string.dietary_unit_milligram),
                            increaseLabel = stringResource(R.string.dietary_preference_sodium_increase),
                            decreaseLabel = stringResource(R.string.dietary_preference_sodium_decrease),
                            onDecrease = { viewModel.onSodiumTargetChange(sodiumTarget - 50) },
                            onIncrease = { viewModel.onSodiumTargetChange(sodiumTarget + 50) },
                        )
                    }

                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                    AppButton(
                        text = stringResource(R.string.dietary_preference_save),
                        onClick = viewModel::save,
                        loading = saveState is RequestState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxl))
                }
            }
        }
    }
}

/** 忌口 chip：48dp 最小热区；选中 primarySoft 底 + primaryDark 字，未选描边（规范 §7 自绘） */
@Composable
private fun AllergenChip(
    code: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(
                if (selected) AppColors.colorScheme.primarySoft
                else AppColors.colorScheme.surface,
            )
            .then(
                if (!selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = AppColors.colorScheme.outline,
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                    )
                } else {
                    Modifier
                },
            )
            .pressable(onClick = onClick, debounce = false)
            .padding(horizontal = AppDimens.spaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = allergenChipLabel(code),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppColors.colorScheme.primaryDark
            else AppColors.colorScheme.textSecondary,
        )
    }
}

/** 目标 stepper 行：标签 + 单位值 + 48dp 步进按钮（- / +，步长 50） */
@Composable
private fun TargetStepperRow(
    label: String,
    value: Int,
    unit: String,
    increaseLabel: String,
    decreaseLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f),
        )
        PreferenceStepButton(isPlus = false, label = decreaseLabel, onClick = onDecrease)
        Text(
            text = "$value",
            style = AppTextStyles.AmountMedium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.padding(horizontal = AppDimens.spaceSm),
        )
        PreferenceStepButton(isPlus = true, label = increaseLabel, onClick = onIncrease)
        Spacer(modifier = Modifier.width(AppDimens.spaceXs))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.colorScheme.textSecondary,
        )
    }
}

/** 步进按钮：48dp 热区 + 20dp Canvas 圆帽线段（- / +） */
@Composable
private fun PreferenceStepButton(
    isPlus: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val glyphColor = AppColors.colorScheme.textSecondary
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .pressable(onClick = onClick, debounce = false),
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

/** 忌口 chip 展示名（文案在 user-impl strings.xml；白名单外原样回显） */
@Composable
private fun allergenChipLabel(code: String): String = stringResource(
    when (code) {
        "SEAFOOD" -> R.string.dietary_allergen_seafood
        "NUT" -> R.string.dietary_allergen_nut
        "GLUTEN" -> R.string.dietary_allergen_gluten
        "DAIRY" -> R.string.dietary_allergen_dairy
        "EGG" -> R.string.dietary_allergen_egg
        "SPICY" -> R.string.dietary_allergen_spicy
        "SOY" -> R.string.dietary_allergen_soy
        else -> R.string.dietary_allergen_unknown
    },
)
