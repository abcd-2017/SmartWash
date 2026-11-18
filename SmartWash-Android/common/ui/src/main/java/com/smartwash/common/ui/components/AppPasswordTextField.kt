package com.smartwash.common.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.R
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.pressable

/**
 * 密码输入框（[AppTextField] + 尾缀可见性切换）。
 *
 * §3.7：尾缀操作一律图标化（Material `Visibility / VisibilityOff`，禁止文字替代），
 * 热区 ≥44dp（此处 48dp 自绘，§7 禁 M3 IconButton 自带 state layer）。
 *
 * @param maxLength 最大位数，超出直接截断、不报错（沿用原 PasswordInput 行为）；
 *   传 null 则不限制长度（如投递进入密码 4-64 位由业务侧规则校验的场景）
 */
@Composable
fun AppPasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    maxLength: Int? = 16,
    variant: AppFieldVariant = AppFieldVariant.STANDARD,
    interactionSource: MutableInteractionSource? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val colors = AppColors.colorScheme

    AppTextField(
        value = value,
        onValueChange = { newValue ->
            onValueChange(
                if (maxLength != null && newValue.length > maxLength) newValue.take(maxLength) else newValue
            )
        },
        modifier = modifier,
        placeholder = placeholder,
        isError = isError,
        supportingText = supportingText,
        enabled = enabled,
        variant = variant,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        interactionSource = interactionSource,
        trailingIcon = {
            // 48dp 方形热区；纯视觉切换，关闭连击防抖（快速连点切换是合法操作）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressable(onClick = { visible = !visible }, debounce = false),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = visible,
                    animationSpec = tween(CROSSFADE_ALT_MS),
                    label = "passwordVisibilityIcon",
                ) { isVisible ->
                    Icon(
                        imageVector = if (isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (isVisible) R.string.hide_password else R.string.show_password
                        ),
                        modifier = Modifier.size(20.dp),
                        // 玻璃皮肤（认证页）图标走白 .8；常规档维持 textSecondary
                        tint = if (variant == AppFieldVariant.GLASS) {
                            Color.White.copy(alpha = 0.8f)
                        } else {
                            colors.textSecondary
                        },
                    )
                }
            }
        },
    )
}
