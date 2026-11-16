package com.smartwash.common.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens

/**
 * 清氧设计系统唯一输入控件（规范 §3.7「登录/注册/一切表单的唯一标准」）。
 *
 * 全项目输入框统一走本组件：形状 [AppDimens.inputRadius]、`surfaceVariant` 底、
 * `outline` 描边、聚焦 `primary`、错误 `error`、正文 16sp。
 * 业务层禁止直接使用 material3 `OutlinedTextField`（规范 §7）。
 *
 * @param placeholder 占位文案；传 null 则不显示（字段标签由页面在输入框上方自绘）
 * @param label Material 浮动标签（仅 BYOK 等需要内置标签的场景使用，普通表单用页面 FormLabel）
 * @param leadingIcon 前缀图标，固定 16dp、[AppColors.colorScheme.textSecondary]
 * @param trailingIcon 尾缀槽位（密码可见性 / 清空等自绘按钮，§7 禁 M3 交互组件）
 * @param supportingText 错误 / 辅助文案槽位，由调用方控制展示时机（规范 §3.7：错误仅在提交后展示）
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    leadingIcon: ImageVector? = null,
    leadingIconContentDescription: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
) {
    val colors = AppColors.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        // §3.7：输入正文 16sp / Normal —— 输入是高频读写场景，禁用 14sp 及以下
        textStyle = textStyle,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        isError = isError,
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        },
        label = label?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        },
        leadingIcon = leadingIcon?.let {
            {
                // §3.7 前缀图标 16dp + 透明度 .5
                Icon(
                    imageVector = it,
                    contentDescription = leadingIconContentDescription,
                    modifier = Modifier.size(16.dp),
                    tint = colors.textSecondary,
                )
            }
        },
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        shape = RoundedCornerShape(AppDimens.inputRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            errorBorderColor = colors.error,
            focusedContainerColor = colors.surfaceVariant,
            unfocusedContainerColor = colors.surfaceVariant,
            errorContainerColor = colors.surfaceVariant,
            disabledContainerColor = colors.surfaceVariant,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            errorTextColor = colors.textPrimary,
            disabledTextColor = colors.textSecondary,
            cursorColor = if (isError) colors.error else colors.primary,
            errorCursorColor = colors.error,
            focusedPlaceholderColor = colors.textSecondary,
            unfocusedPlaceholderColor = colors.textSecondary,
            errorPlaceholderColor = colors.textSecondary,
            disabledPlaceholderColor = colors.textTertiary,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.textSecondary,
            errorLabelColor = colors.error,
            disabledLabelColor = colors.textTertiary,
            focusedLeadingIconColor = colors.textSecondary,
            unfocusedLeadingIconColor = colors.textSecondary,
            errorLeadingIconColor = colors.error,
        ),
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    )
}
