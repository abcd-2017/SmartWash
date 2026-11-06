package com.smartwash.feature.subscription.ui.edit

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.SubscriptionCycleType
import com.smartwash.feature.subscription.logic.SubscriptionRules
import com.smartwash.feature.subscription.ui.SubscriptionDatePickerDialog
import com.smartwash.feature.subscription.ui.SubscriptionSelectableChip
import com.smartwash.feature.subscription.ui.SubscriptionToggle
import com.smartwash.feature.subscription.ui.copyToClipboard
import com.smartwash.feature.subscription.ui.notifySubscriptionChanged

/**
 * 订阅添加/编辑表单页（同页双模式：路由 id ≤ 0 = 新建，编辑模式拉详情回填）。
 * 字段：名称/金额/月年切换/间隔期数/下次续费日选择器/试用截止（可选）/自动续费开关/
 * 提醒提前天数/取消网址（一键打开浏览器·复制）+ 取消步骤多行备忘 + 备注。
 * 校验错误仅提交后展示（规范 §3.7 基线⑤）；成功后回上一页并置变更信号。
 */
@Composable
fun SubscriptionEditPage(
    navController: NavHostController,
    viewModel: SubscriptionEditViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val submitState by viewModel.submitState.collectAsState()

    var showRenewDatePicker by remember { mutableStateOf(false) }
    var showTrialDatePicker by remember { mutableStateOf(false) }

    // 提交结果：成功 → 提示 + 置变更信号 + 返回；失败 → 表单下方内联错误（13sp error 色）
    LaunchedEffect(submitState) {
        when (submitState) {
            is RequestState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(
                        if (viewModel.isEditMode) R.string.subscription_toast_save_success
                        else R.string.subscription_toast_create_success
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                navController.notifySubscriptionChanged()
                navController.popBackStack()
                viewModel.resetSubmitState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            .imePadding()
    ) {
        PageHeader(
            title = stringResource(
                if (viewModel.isEditMode) R.string.subscription_edit_title_edit
                else R.string.subscription_edit_title_new
            ),
            onBack = { navController.navigateUp() },
        )
        when {
            // 编辑模式回填加载：骨架屏（规范 §3.6）
            viewModel.isEditMode && loadState is RequestState.Loading -> {
                SkeletonList(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = AppDimens.spaceLg),
                    rows = 6,
                )
            }
            // 编辑模式加载失败：说明 + 重试（规范 §3.6 错误态）
            viewModel.isEditMode && loadState is RequestState.Error -> {
                EmptyState(
                    icon = Icons.Rounded.Autorenew,
                    message = stringResource(R.string.subscription_error_load_detail),
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        AppButton(
                            text = stringResource(R.string.subscription_detail_retry),
                            onClick = viewModel::load,
                        )
                    },
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppDimens.pagePadding)
                        .padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceLg)
                ) {
                    GroupCard {
                        // ---------- 名称 ----------
                        FormLabel(stringResource(R.string.subscription_form_name_label))
                        OutlinedTextField(
                            value = form.name,
                            onValueChange = viewModel::updateName,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.subscription_form_name_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.colorScheme.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(AppDimens.inputRadius),
                            colors = tokenTextFieldColors(),
                        )

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 每期金额 ----------
                        FormLabel(stringResource(R.string.subscription_form_amount_label))
                        OutlinedTextField(
                            value = form.amount,
                            onValueChange = viewModel::updateAmount,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.subscription_form_amount_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.colorScheme.textSecondary,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(AppDimens.inputRadius),
                            colors = tokenTextFieldColors(),
                        )

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 计费周期（月/年切换） ----------
                        FormLabel(stringResource(R.string.subscription_form_cycle_label))
                        Row {
                            SubscriptionSelectableChip(
                                text = stringResource(R.string.subscription_form_cycle_month),
                                selected = form.cycleType == SubscriptionCycleType.MONTH,
                                onClick = { viewModel.updateCycleType(SubscriptionCycleType.MONTH) },
                            )
                            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                            SubscriptionSelectableChip(
                                text = stringResource(R.string.subscription_form_cycle_year),
                                selected = form.cycleType == SubscriptionCycleType.YEAR,
                                onClick = { viewModel.updateCycleType(SubscriptionCycleType.YEAR) },
                            )
                        }

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 间隔期数 ----------
                        FormLabel(stringResource(R.string.subscription_form_interval_label))
                        OutlinedTextField(
                            value = form.interval,
                            onValueChange = viewModel::updateInterval,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(AppDimens.inputRadius),
                            colors = tokenTextFieldColors(),
                        )
                        Text(
                            text = stringResource(R.string.subscription_form_interval_helper),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textTertiary,
                            modifier = Modifier.padding(top = AppDimens.spaceXs),
                        )

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 下次续费日 ----------
                        FormLabel(stringResource(R.string.subscription_form_next_renew_label))
                        AppButton(
                            text = form.nextRenewDate?.let(SubscriptionRules::formatDate)
                                ?: stringResource(R.string.subscription_form_pick_date),
                            onClick = { showRenewDatePicker = true },
                            variant = AppButtonVariant.SECONDARY,
                        )

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 试用截止（可选，可清除） ----------
                        FormLabel(stringResource(R.string.subscription_form_trial_label))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppButton(
                                text = form.trialEndDate?.let(SubscriptionRules::formatDate)
                                    ?: stringResource(R.string.subscription_form_trial_none),
                                onClick = { showTrialDatePicker = true },
                                variant = AppButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f),
                            )
                            if (form.trialEndDate != null) {
                                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                                // 44dp 最小热区（规范 §2.3），文字居中
                                Box(
                                    modifier = Modifier
                                        .pressable(onClick = { viewModel.updateTrialEndDate(null) })
                                        .heightIn(min = 44.dp)
                                        .padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.spaceXs),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.subscription_form_trial_clear),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.colorScheme.textGreen,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 自动续费 ----------
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.subscription_form_auto_renew_label),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.colorScheme.textPrimary,
                                )
                                Text(
                                    text = stringResource(R.string.subscription_form_auto_renew_helper),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.colorScheme.textTertiary,
                                )
                            }
                            SubscriptionToggle(
                                checked = form.autoRenew,
                                onCheckedChange = viewModel::updateAutoRenew,
                            )
                        }

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 提醒提前天数 ----------
                        FormLabel(stringResource(R.string.subscription_form_remind_label))
                        OutlinedTextField(
                            value = form.remindDays,
                            onValueChange = viewModel::updateRemindDays,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(AppDimens.inputRadius),
                            colors = tokenTextFieldColors(),
                        )
                        Text(
                            text = stringResource(R.string.subscription_form_remind_helper),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textTertiary,
                            modifier = Modifier.padding(top = AppDimens.spaceXs),
                        )

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 取消网址（可一键打开浏览器/复制） ----------
                        FormLabel(stringResource(R.string.subscription_form_cancel_url_label))
                        OutlinedTextField(
                            value = form.cancelUrl,
                            onValueChange = viewModel::updateCancelUrl,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.subscription_form_cancel_url_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.colorScheme.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(AppDimens.inputRadius),
                            colors = tokenTextFieldColors(),
                        )
                        Text(
                            text = stringResource(R.string.subscription_form_cancel_url_helper),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textTertiary,
                            modifier = Modifier.padding(top = AppDimens.spaceXs),
                        )
                        if (form.cancelUrl.isNotBlank()) {
                            Row(modifier = Modifier.padding(top = AppDimens.spaceXs)) {
                                InlineTextAction(
                                    text = stringResource(R.string.subscription_action_open_browser),
                                    onClick = { openInBrowser(context, form.cancelUrl.trim()) },
                                )
                                Spacer(modifier = Modifier.width(AppDimens.spaceMd))
                                InlineTextAction(
                                    text = stringResource(R.string.subscription_action_copy),
                                    onClick = {
                                        copyToClipboard(context, form.cancelUrl.trim())
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.subscription_copy_success),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 取消步骤多行备忘 ----------
                        FormLabel(stringResource(R.string.subscription_form_cancel_steps_label))
                        OutlinedTextField(
                            value = form.cancelSteps,
                            onValueChange = viewModel::updateCancelSteps,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.subscription_form_cancel_steps_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.colorScheme.textSecondary,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimens.inputRadius),
                            colors = tokenTextFieldColors(),
                        )

                        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                        // ---------- 备注 ----------
                        FormLabel(stringResource(R.string.subscription_form_note_label))
                        OutlinedTextField(
                            value = form.note,
                            onValueChange = viewModel::updateNote,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.subscription_form_note_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.colorScheme.textSecondary,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimens.inputRadius),
                            colors = tokenTextFieldColors(),
                        )
                    }

                    // ---------- 提交错误（仅提交后展示，规范 §3.7） ----------
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
                        text = stringResource(
                            if (viewModel.isEditMode) R.string.subscription_submit_save
                            else R.string.subscription_submit_create
                        ),
                        onClick = viewModel::submit,
                        loading = submitState is RequestState.Loading,
                    )
                }
            }
        }
    }

    if (showRenewDatePicker) {
        SubscriptionDatePickerDialog(
            initialDate = form.nextRenewDate,
            onConfirm = {
                viewModel.updateNextRenewDate(it)
                showRenewDatePicker = false
            },
            onDismiss = { showRenewDatePicker = false },
        )
    }
    if (showTrialDatePicker) {
        SubscriptionDatePickerDialog(
            initialDate = form.trialEndDate,
            onConfirm = {
                viewModel.updateTrialEndDate(it)
                showTrialDatePicker = false
            },
            onDismiss = { showTrialDatePicker = false },
        )
    }
}

/** 表单字段标签（13sp 次文本层级，模式对齐 ShortCodeCreatePage.FormLabel） */
@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = AppColors.colorScheme.textSecondary,
        modifier = Modifier.padding(bottom = AppDimens.spaceXs),
    )
}

/** 行内文字动作（13→12sp captionStrong 收敛档，主色） */
@Composable
private fun InlineTextAction(text: String, onClick: () -> Unit) {
    // 44dp 最小热区（规范 §2.3），文字居中
    Box(
        modifier = Modifier
            .pressable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(vertical = AppDimens.spaceXxs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.colorScheme.textGreen,
        )
    }
}

/** 输入框令牌配色（对齐 ShortCodeCreatePage 先例：r12 + surfaceVariant 底 + outline 描边） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    errorBorderColor = AppColors.colorScheme.error,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)

/** 浏览器打开（App 内不拦截跳转；无浏览器兜底提示） */
internal fun openInBrowser(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.subscription_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
