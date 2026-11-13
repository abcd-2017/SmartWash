package com.smartwash.feature.toolbox.ui.assettag

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.ui.notifyAssetTagChanged

/**
 * 新建物品标签页：物品名（必填）+ 描述/联系方式（可选）+ 自定义标签码（可选）。
 * 联系方式隐私语义在 helper 说明；成功后回列表并触发刷新。
 */
@Composable
fun AssetTagCreatePage(
    navController: NavHostController,
    viewModel: AssetTagCreateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsState()
    val submitState by viewModel.submitState.collectAsState()

    LaunchedEffect(submitState) {
        when (submitState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.asset_tag_toast_create_success), Toast.LENGTH_SHORT).show()
                navController.notifyAssetTagChanged()
                navController.popBackStack()
                viewModel.resetSubmitState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        PageHeader(
            title = stringResource(R.string.asset_tag_create_title),
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
                // ---------- 物品名称 ----------
                FormLabel(stringResource(R.string.asset_tag_item_name_label))
                OutlinedTextField(
                    value = form.itemName,
                    onValueChange = viewModel::updateItemName,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.asset_tag_item_name_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textTertiary,
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

                // ---------- 物品描述（可选） ----------
                FormLabel(stringResource(R.string.asset_tag_item_desc_label))
                OutlinedTextField(
                    value = form.itemDesc,
                    onValueChange = viewModel::updateItemDesc,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.asset_tag_item_desc_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textTertiary,
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

                // ---------- 联系方式（可选） ----------
                FormLabel(stringResource(R.string.asset_tag_contact_label))
                OutlinedTextField(
                    value = form.contact,
                    onValueChange = viewModel::updateContact,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.asset_tag_contact_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textTertiary,
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
                Text(
                    text = stringResource(R.string.asset_tag_contact_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(top = AppDimens.spaceXxs),
                )

                Spacer(modifier = Modifier.height(AppDimens.spaceLg))

                // ---------- 自定义标签码（可选） ----------
                FormLabel(stringResource(R.string.asset_tag_custom_code_label))
                OutlinedTextField(
                    value = form.customCode,
                    onValueChange = viewModel::updateCustomCode,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.toolbox_custom_code_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textTertiary,
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
            }

            // ---------- 提交错误 ----------
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
                text = stringResource(R.string.asset_tag_submit_create),
                onClick = viewModel::submit,
                loading = submitState is RequestState.Loading,
            )
        }
    }
}

/** 表单字段标签（labelMedium 档，对齐短链创建页 FormLabel） */
@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.colorScheme.textSecondary,
        modifier = Modifier.padding(bottom = AppDimens.spaceXs),
    )
}

/** 输入框令牌配色（对齐短链创建页 tokenTextFieldColors） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    errorBorderColor = AppColors.colorScheme.error,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)
