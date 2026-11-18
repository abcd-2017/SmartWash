@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.smartwash.feature.dining.ui.create

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppChipVariant
import com.smartwash.common.ui.components.AppFilterChip
import com.smartwash.common.ui.components.AppTextField
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningRecipeFormValidator.DiningRecipeFormError

/**
 * 新增菜谱页（04 号票）：菜名 / 分类 / 食材名列表（chips 增删）/ 步骤 / 封面（PhotoPicker
 * 选图 → Multipart 上传，头像上传先例模式）。保存成功 Toast 后返回菜谱库（列表经
 * SmartWashAsyncImage 按后端直链回显封面）。
 *
 * 输入控件（规范 §3.7/§7）：AppTextField（common:ui 统一输入组件，正文 16sp）；
 * 分类用 chips 而非下拉（量级小 + 触达少一跳）。
 */
@Composable
fun DiningRecipeCreatePage(
    navController: NavHostController,
    viewModel: DiningRecipeCreateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val name by viewModel.name.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val ingredients by viewModel.ingredients.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val coverUri by viewModel.coverUri.collectAsState()
    val coverUploadState by viewModel.coverUploadState.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val formError by viewModel.formError.collectAsState()

    var ingredientInput by remember { mutableStateOf("") }

    // PhotoPicker（头像上传先例）：选图即上传，成功后以本地 Uri 预览
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.uploadCover(it) }
    }

    LaunchedEffect(Unit) { viewModel.loadCategories() }

    // 保存成功：Toast + 返回菜谱库（返回栈弹到菜谱库，列表 LaunchedEffect 不重放——
    // 由用户下拉刷新或切分类看到新菜；直上架语义下新菜谱立即可见）
    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is RequestState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.dining_create_submit_success),
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.resetSubmitState()
                navController.popBackStack()
            }

            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.resetSubmitState()
            }

            else -> {}
        }
    }

    // 封面上传失败反馈（独立于提交态）
    LaunchedEffect(coverUploadState) {
        val state = coverUploadState
        if (state is RequestState.Error) {
            Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
            viewModel.resetCoverUploadState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.surface)
            // 规范 §4：键盘弹出时整页上收，步骤/食材等靠下输入框可经纵向滚动露出，
            // 不被软键盘遮挡（与 RechargePage 同口径）
            .imePadding()
    ) {
        PageHeader(
            title = stringResource(R.string.dining_recipe_create_title),
            onBack = { navController.navigateUp() },
        )

        // 平铺表单（对齐点单页标准）：SectionLabel + 控件直接陈列，无 GroupCard 包裹——
        // 输入框/选图区自带灰底容器，再套白卡成「卡中卡」，页面臃肿且层级虚高
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.pagePadding),
        ) {
            // 封面（可选）：16:9 选图区，选中后本地 Uri 预览
            SectionLabel(stringResource(R.string.dining_create_cover_label))
            CoverPicker(
                coverUri = coverUri?.toString(),
                isUploading = coverUploadState is RequestState.Loading,
                onPick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            SectionLabel(stringResource(R.string.dining_create_name_label))
            AppTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
                placeholder = stringResource(R.string.dining_create_name_hint),
                isError = formError == DiningRecipeFormError.NAME_REQUIRED ||
                    formError == DiningRecipeFormError.NAME_TOO_LONG,
                supportingText = {
                    formError?.let { error ->
                        if (error == DiningRecipeFormError.NAME_REQUIRED ||
                            error == DiningRecipeFormError.NAME_TOO_LONG
                        ) {
                            Text(stringResource(viewModel.errorRes(error)))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            // 分类（chips 多选一；未分类 = 不选）
            SectionLabel(stringResource(R.string.dining_create_category_label))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
            ) {
                CategoryChip(
                    label = stringResource(R.string.dining_create_category_uncategorized),
                    selected = selectedCategoryId == null,
                    onClick = { viewModel.selectCategory(null) },
                )
                categories.forEach { category ->
                    CategoryChip(
                        label = category.safeName,
                        selected = selectedCategoryId == category.safeId,
                        onClick = { viewModel.selectCategory(category.safeId) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            // 食材（输入 + 添加 → chips 可移除）
            SectionLabel(stringResource(R.string.dining_create_ingredients_label))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = ingredientInput,
                    onValueChange = { ingredientInput = it },
                    placeholder = stringResource(R.string.dining_create_ingredients_hint),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                AppButton(
                    small = true,
                    text = stringResource(R.string.dining_create_ingredient_add),
                    onClick = {
                        viewModel.addIngredient(ingredientInput)
                        ingredientInput = ""
                    },
                    // 空输入禁用（VM 对空串直接 return，避免可点无反馈）
                    enabled = ingredientInput.isNotBlank(),
                    variant = AppButtonVariant.SECONDARY,
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            if (ingredients.isEmpty()) {
                Text(
                    text = stringResource(R.string.dining_create_ingredients_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
                ) {
                    ingredients.forEachIndexed { index, ingredient ->
                        IngredientChip(
                            label = ingredient,
                            onRemove = { viewModel.removeIngredient(index) },
                        )
                    }
                }
            }
            if (formError == DiningRecipeFormError.INGREDIENT_EMPTY ||
                formError == DiningRecipeFormError.INGREDIENT_TOO_LONG ||
                formError == DiningRecipeFormError.INGREDIENT_TOO_MANY
            ) {
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = stringResource(viewModel.errorRes(formError!!)),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            SectionLabel(stringResource(R.string.dining_create_steps_label))
            AppTextField(
                value = steps,
                onValueChange = viewModel::onStepsChange,
                placeholder = stringResource(R.string.dining_create_steps_hint),
                isError = formError == DiningRecipeFormError.STEPS_TOO_LONG,
                supportingText = {
                    if (formError == DiningRecipeFormError.STEPS_TOO_LONG) {
                        Text(stringResource(R.string.dining_form_error_steps_too_long))
                    }
                },
                singleLine = false,
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // 保存（底部 CTA；避让手势条）
            Spacer(modifier = Modifier.height(AppDimens.spaceLg))
            AppButton(
                text = stringResource(R.string.dining_create_submit),
                onClick = viewModel::submit,
                loading = submitState is RequestState.Loading,
            )
            Spacer(
                modifier = Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + AppDimens.spaceLg
                )
            )
        }
    }
}

/** 区块标题（平铺标签行，18sp Title 档 = AppTextStyles.SectionTitle，规范 §2.2） */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = AppTextStyles.SectionTitle,
        color = AppColors.colorScheme.textPrimary,
        modifier = Modifier.padding(bottom = AppDimens.spaceXs),
    )
}

/** 封面选图区：未选 = 虚位按钮；已选 = 本地 Uri 预览 + 「更换」 */
@Composable
private fun CoverPicker(
    coverUri: String?,
    isUploading: Boolean,
    onPick: () -> Unit,
) {
    Column {
        if (coverUri == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(AppDimens.radiusMd))
                    .background(AppColors.colorScheme.surfaceVariant)
                    .pressable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.dining_create_cover_pick),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        } else {
            // 已选封面本地预览（提交成功回列表后经 SmartWashAsyncImage 按直链回显）
            SmartWashAsyncImage(
                url = coverUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(AppDimens.radiusMd)),
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            AppButton(
                small = true,
                // 已选封面场景固定「更换」；上传中走 loading 并禁用，避免重复选传
                text = stringResource(R.string.dining_create_cover_replace),
                onClick = onPick,
                loading = isUploading,
                enabled = !isUploading,
                variant = AppButtonVariant.SECONDARY,
            )
        }
    }
}

/**
 * 分类 chip（多选一）—— 统一走公共 [AppFilterChip]（SOFT）：
 * 选中 = primarySoft 浅底 + onPrimarySoft 深字，未选中 = surface 底 + 1dp outline 描边 + textSecondary
 * （原未选中 surfaceVariant 灰阶统一收敛到 surface + outline 口径）；48dp 最小热区 / 常驻描边 /
 * 恒定 SemiBold / selected 语义由公共组件内置。
 */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AppFilterChip(
        text = label,
        selected = selected,
        onClick = onClick,
        variant = AppChipVariant.SOFT,
        // 该页分类 chip 原用 labelLarge（14sp），保留原字号，避免画板观感漂移
        textStyle = MaterialTheme.typography.labelLarge,
    )
}

/** 食材 chip（右上角移除；可访问性文案走 semantics） */
@Composable
private fun IngredientChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(AppColors.colorScheme.surfaceVariant)
            .padding(start = AppDimens.spaceSm, end = AppDimens.spaceXs, top = AppDimens.spaceXxs, bottom = AppDimens.spaceXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
        val removeLabel = stringResource(R.string.dining_create_ingredient_remove, label)
        // 48dp 自绘热区（规范 §3.2：图标强制 48dp 热区），20dp 图形居中
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = removeLabel,
                tint = AppColors.colorScheme.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

