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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SmartWashAsyncImage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningRecipeFormValidator.DiningRecipeFormError

/**
 * 新增菜谱页（04 号票）：菜名 / 分类 / 食材名列表（chips 增删）/ 步骤 / 封面（PhotoPicker
 * 选图 → Multipart 上传，头像上传先例模式）。保存成功 Toast 后返回菜谱库（列表经
 * SmartWashAsyncImage 按后端直链回显封面）。
 *
 * 输入控件（规范 §3.7/§7）：OutlinedTextField + 令牌色（对齐 subscription/toolbox 先例的
 * tokenTextFieldColors），正文 16sp；分类用 chips 而非下拉（量级小 + 触达少一跳）。
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
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.dining_recipe_create_title),
            onBack = { navController.navigateUp() },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.pagePadding),
        ) {
            // 封面（可选）：16:9 选图区，选中后本地 Uri 预览
            SectionLabel(stringResource(R.string.dining_create_cover_label))
            GroupCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(AppDimens.cardPadding),
            ) {
                CoverPicker(
                    coverUri = coverUri?.toString(),
                    isUploading = coverUploadState is RequestState.Loading,
                    onPick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

            // 菜名
            SectionLabel(stringResource(R.string.dining_create_name_label))
            GroupCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = viewModel::onNameChange,
                    placeholder = { Text(stringResource(R.string.dining_create_name_hint)) },
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
                    // §3.7：输入正文禁用 14sp 及以下
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    shape = RoundedCornerShape(AppDimens.radiusMd),
                    colors = tokenTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

            // 分类（chips 多选一；未分类 = 不选）
            SectionLabel(stringResource(R.string.dining_create_category_label))
            GroupCard(modifier = Modifier.fillMaxWidth()) {
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
            }
            Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

            // 食材（输入 + 添加 → chips 可移除）
            SectionLabel(stringResource(R.string.dining_create_ingredients_label))
            GroupCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ingredientInput,
                        onValueChange = { ingredientInput = it },
                        placeholder = { Text(stringResource(R.string.dining_create_ingredients_hint)) },
                        // §3.7：输入正文 16sp
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        shape = RoundedCornerShape(AppDimens.radiusMd),
                        colors = tokenTextFieldColors(),
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
                // 食材校验错误就近提示
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
            }
            Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

            // 步骤（多行）
            SectionLabel(stringResource(R.string.dining_create_steps_label))
            GroupCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = steps,
                    onValueChange = viewModel::onStepsChange,
                    placeholder = { Text(stringResource(R.string.dining_create_steps_hint)) },
                    isError = formError == DiningRecipeFormError.STEPS_TOO_LONG,
                    supportingText = {
                        if (formError == DiningRecipeFormError.STEPS_TOO_LONG) {
                            Text(stringResource(R.string.dining_form_error_steps_too_long))
                        }
                    },
                    // §3.7：输入正文 16sp
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 4,
                    shape = RoundedCornerShape(AppDimens.radiusMd),
                    colors = tokenTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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

/** 区块标题（卡外标签行，18sp title 档，规范 §2.2） */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = AppColors.colorScheme.textPrimary,
        modifier = Modifier.padding(bottom = AppDimens.spaceXs, start = 4.dp),
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
                text = if (isUploading) {
                    stringResource(R.string.dining_create_cover_pick)
                } else {
                    stringResource(R.string.dining_create_cover_replace)
                },
                onClick = onPick,
                variant = AppButtonVariant.SECONDARY,
            )
        }
    }
}

/** 分类 chip（多选一；选中 primarySoft 浅绿底 + primaryDeep 文字，未选中灰阶） */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val (container, content) = if (selected) {
        AppColors.colorScheme.primarySoft to AppColors.colorScheme.primaryDark
    } else {
        AppColors.colorScheme.surfaceVariant to AppColors.colorScheme.textSecondary
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(container)
            .pressable(onClick = onClick)
            .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
    )
}

/** 食材 chip（右上角移除；可访问性文案走 semantics） */
@Composable
private fun IngredientChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(AppColors.colorScheme.surfaceVariant)
            .padding(start = AppDimens.spaceSm, end = AppDimens.spaceXs, top = 4.dp, bottom = 4.dp),
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
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = removeLabel,
            tint = AppColors.colorScheme.textSecondary,
            modifier = Modifier
                .width(20.dp)
                .pressable(onClick = onRemove),
        )
    }
}

/** 输入框令牌色（对齐 subscription/toolbox 先例的 tokenTextFieldColors：r12 + outline 描边） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    errorBorderColor = AppColors.colorScheme.error,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)
