package com.smartwash.feature.toolbox.ui.create

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import com.smartwash.feature.toolbox.ui.ToolboxDatePickerDialog
import com.smartwash.feature.toolbox.ui.ToolboxSelectableChip
import com.smartwash.feature.toolbox.ui.ToolboxExpiryPresetRow
import com.smartwash.feature.toolbox.ui.notifyShortCodeChanged
import java.time.format.DateTimeFormatter

/**
 * 新建短链页：目标链接（必填 http/https）+ 自定义短码（可选 4-16 位）+
 * 可见性（默认公开）+ 有效期预设（永久/7天/30天/自定义日期）。
 * 校验错误仅在提交时展示（规范 §3.7 基线⑤）；成功后回列表并触发刷新。
 */
@Composable
fun ShortCodeCreatePage(
    navController: NavHostController,
    viewModel: ShortCodeCreateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsState()
    val submitState by viewModel.submitState.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    // 提交结果：成功 → 提示 + 置刷新信号 + 返回列表；失败 → 表单下方内联错误（13sp error 色）
    LaunchedEffect(submitState) {
        when (submitState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.toolbox_toast_create_success), Toast.LENGTH_SHORT).show()
                navController.notifyShortCodeChanged()
                navController.popBackStack()
                viewModel.resetSubmitState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 底部避让导航条（audit-17 #3）：三键导航下提交按钮完整可见可点；
            // navigationBarsPadding 先消费 navBars，imePadding 只补剩余——
            // 组合即 max(ime, navBars) 语义，键盘弹出时不与导航条高度双重叠加
            .navigationBarsPadding()
            .imePadding()
    ) {
        PageHeader(
            title = stringResource(R.string.toolbox_create_title),
            onBack = { navController.navigateUp() },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.pagePadding)
                .padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceLg)
                .verticalScroll(rememberScrollState())
        ) {
            GroupCard {
                // ---------- 目标链接 ----------
                FormLabel(stringResource(R.string.toolbox_target_label))
                OutlinedTextField(
                    value = form.target,
                    onValueChange = viewModel::updateTarget,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.toolbox_target_hint),
                            // 规范页样张实测：占位符 body 档 14/400 + textTertiary
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    // URL 输入补 Uri 键盘（「/」「.com」便捷键，audit-17 #11）
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    // 规范 §3 radius.input = 12（14 为非收敛档）
                    shape = RoundedCornerShape(AppDimens.radiusMd),
                    colors = tokenTextFieldColors(),
                )
                Text(
                    text = stringResource(R.string.toolbox_target_helper),
                    style = MaterialTheme.typography.labelSmall,
                    // helper 为用户必读说明文字：textTertiary(≈2.3:1) 破 §10 红线，收敛 textSecondary（audit-17 #1）
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(top = AppDimens.spaceXxs),
                )

                Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                // ---------- 自定义短码（可选） ----------
                FormLabel(stringResource(R.string.toolbox_custom_code_label))
                OutlinedTextField(
                    value = form.customCode,
                    onValueChange = viewModel::updateCustomCode,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.toolbox_custom_code_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textTertiary,
                            // 对齐目标链接框：字体放大 130% 时 hint 不折行、不破 48dp 固定高（audit-17 #10）
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(AppDimens.radiusMd),
                    colors = tokenTextFieldColors(),
                )

                Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                // ---------- 可见性 ----------
                FormLabel(stringResource(R.string.toolbox_visibility_label))
                Row {
                    ToolboxSelectableChip(
                        text = stringResource(R.string.toolbox_visibility_public_value),
                        selected = form.isPublic,
                        onClick = { viewModel.updateVisibility(true) },
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    ToolboxSelectableChip(
                        text = stringResource(R.string.toolbox_visibility_private_value),
                        selected = !form.isPublic,
                        onClick = { viewModel.updateVisibility(false) },
                    )
                }
                Text(
                    text = stringResource(
                        if (form.isPublic) R.string.toolbox_visibility_public_desc
                        else R.string.toolbox_visibility_private_desc
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    // 可见性说明是用户必读语义：与 helper 同口径收敛 textSecondary（audit-17 #2）
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(top = AppDimens.spaceXxs),
                )

                Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                // ---------- 有效期预设 ----------
                FormLabel(stringResource(R.string.toolbox_expiry_label))
                ToolboxExpiryPresetRow(
                    selected = form.preset,
                    onSelect = viewModel::updatePreset,
                )
                if (form.preset == ShortCodeRules.ExpiryPreset.CUSTOM) {
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    AppButton(
                        text = form.customDate?.let { ShortCodeRules.formatDate(it) }
                            ?: stringResource(R.string.toolbox_expiry_custom_pick),
                        onClick = { showDatePicker = true },
                        variant = AppButtonVariant.SECONDARY,
                    )
                }
            }

            // ---------- 提交错误（仅提交后展示，13sp error 色，规范 §3.7） ----------
            val errorMessage = (submitState as? RequestState.Error)?.getMessage(context)
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.error,
                    modifier = Modifier.padding(top = AppDimens.spaceSm),
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceLg))
            AppButton(
                text = stringResource(R.string.toolbox_submit_create),
                onClick = viewModel::submit,
                loading = submitState is RequestState.Loading,
            )
        }
    }

    if (showDatePicker) {
        ToolboxDatePickerDialog(
            initialDate = form.customDate,
            onConfirm = {
                viewModel.updateCustomDate(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** 表单字段标签（12sp/500，labelMedium 档；13sp 夹档已被 v0.4 字阶覆写作废，labelMedium 自带 500 不再冗余覆盖） */
@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.colorScheme.textSecondary,
        modifier = Modifier.padding(bottom = AppDimens.spaceXs),
    )
}

/** 输入框令牌配色（对齐 UpdateUserInfoPage 先例：r12 + surfaceVariant 底 + outline 描边） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    errorBorderColor = AppColors.colorScheme.error,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)
