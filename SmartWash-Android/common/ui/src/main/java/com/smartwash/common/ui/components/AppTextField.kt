package com.smartwash.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.ErrorLight
import com.smartwash.common.utils.selectedColor

/**
 * 输入框皮肤（TD37）。
 *
 * - [STANDARD]：常规表单——`surfaceVariant` 实底 + `outline` 描边，用于白卡/浅色页面。
 * - [GLASS]：认证页玻璃卡——透明行 + 白字，见 §3.7 / TD37；聚焦以底部 1dp 白指示线表达。
 */
enum class AppFieldVariant { STANDARD, GLASS }

/**
 * 清氧设计系统唯一输入控件（规范 §3.7「登录/注册/一切表单的唯一标准」）。
 *
 * 全项目输入框统一走本组件：形状 [AppDimens.inputRadius]、`surfaceVariant` 底、
 * `outline` 描边、聚焦 `primary`、错误 `error`、正文 16sp。
 * 业务层禁止直接使用 material3 `OutlinedTextField`（规范 §7）。
 *
 * 皮肤见 [AppFieldVariant]：认证页（品牌渐变玻璃卡）用 [AppFieldVariant.GLASS]，
 * 其余表单用默认 [AppFieldVariant.STANDARD]。
 *
 * @param placeholder 占位文案；传 null 则不显示（字段标签由页面在输入框上方自绘）
 * @param label Material 浮动标签（仅 BYOK 等需要内置标签的场景使用，普通表单用页面 FormLabel；
 *   GLASS 皮肤为透明行、无浮动标签位，不要与 GLASS 同用）
 * @param leadingIcon 前缀图标，固定 16dp、[AppColors.colorScheme.textSecondary]（GLASS 为白 .5）
 * @param trailingIcon 尾缀槽位（密码可见性 / 清空等自绘按钮，§7 禁 M3 交互组件）
 * @param supportingText 错误 / 辅助文案槽位，由调用方控制展示时机（规范 §3.7：错误仅在提交后展示）
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: AppFieldVariant = AppFieldVariant.STANDARD,
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
    /**
     * L4 紧凑档：头部堆叠场景收窄（常规档为 M3 默认 56dp）。
     *
     * M3 OutlinedTextField 内部上下各 16dp 内容内边距，16sp 文字（行高 24）自然高 56dp——
     * 早期固定 44dp 会把文字行挤到 12dp（占位被切一半）。这里改为 52dp 并把行高收到 20sp：
     * 16(上) + 20(文字) + 16(下) = 52dp 恰好容纳；字号仍 16sp（§3.7 禁 14sp 及以下）。
     * 仅 STANDARD 生效；GLASS 行高固定 48dp（TD37）。
     */
    compact: Boolean = false,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    if (variant == AppFieldVariant.GLASS) {
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            leadingIconContentDescription = leadingIconContentDescription,
            trailingIcon = trailingIcon,
            isError = isError,
            supportingText = supportingText,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = textStyle,
            interactionSource = resolvedInteractionSource,
        )
        return
    }

    val colors = AppColors.colorScheme
    val compactStyle = if (compact) textStyle.copy(lineHeight = 20.sp) else textStyle
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = if (compact) {
            modifier.fillMaxWidth().height(52.dp)
        } else {
            modifier.fillMaxWidth()
        },
        enabled = enabled,
        readOnly = readOnly,
        // §3.7：输入正文 16sp / Normal —— 输入是高频读写场景，禁用 14sp 及以下
        textStyle = compactStyle,
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
                    style = compactStyle,
                    color = colors.textSecondary,
                )
            }
        },
        label = label?.let {
            {
                Text(
                    text = it,
                    style = compactStyle,
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
                    tint = colors.textSecondary.copy(alpha = 0.5f),
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
        interactionSource = resolvedInteractionSource,
    )
}

/**
 * 玻璃皮肤输入（TD37）—— 认证页专用：透明行 + 白字 + 48dp 行高。
 *
 * 设计稿中玻璃卡内输入行无底无边（透出品牌渐变），故不套 STANDARD 的 `surfaceVariant` 实底；
 * 聚焦以底部 1dp 白指示线表达（弹簧过渡，reduced motion 降级 180ms）。两种皮肤共用同一组槽位，
 * 业务层不得再为认证页自绘 `BasicTextField`。
 */
@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String?,
    leadingIcon: ImageVector?,
    leadingIconContentDescription: String?,
    trailingIcon: (@Composable () -> Unit)?,
    isError: Boolean,
    supportingText: (@Composable () -> Unit)?,
    enabled: Boolean,
    readOnly: Boolean,
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation,
    textStyle: TextStyle,
    interactionSource: MutableInteractionSource,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    // 聚焦底指示线：未聚焦透明 → 聚焦白 .55，selectedColor = defaultSpring，reduced 180ms
    val indicator = selectedColor(
        if (focused) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0f)
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier.height(48.dp) else Modifier.heightIn(min = 48.dp))
                .alpha(if (enabled) 1f else 0.38f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = leadingIconContentDescription,
                    modifier = Modifier.size(16.dp),
                    tint = if (isError) ErrorLight else Color.White.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                readOnly = readOnly,
                textStyle = textStyle.copy(color = Color.White),
                cursorBrush = SolidColor(if (isError) ErrorLight else Color.White),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = Color.White.copy(alpha = 0.45f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                trailingIcon()
            }
        }
        // 聚焦指示线（未聚焦 alpha 0，不占视觉；动画见 indicator）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(indicator)
        )
        supportingText?.invoke()
    }
}
