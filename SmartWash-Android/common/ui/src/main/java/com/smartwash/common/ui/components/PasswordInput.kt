package com.smartwash.common.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.R
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.pressable

private val ErrorLight = com.smartwash.common.ui.theme.ErrorLight

@Composable
fun PasswordInput(
    password: String,
    isPasswordError: Boolean,
    showPassword: Boolean,
    showVisibility: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 紧凑输入行：总高 48dp、垂直居中（设计稿输入行 48px，D7/D8 对齐）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isPasswordError) ErrorLight
                else contentColor.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            BasicTextField(
                value = password,
                onValueChange = { newValue ->
                    // 超过16位直接截断，不显示报错
                    val filtered = if (newValue.length > 16) newValue.take(16) else newValue
                    onValueChange(filtered)
                },
                modifier = modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = contentColor),
                cursorBrush = SolidColor(if (isPasswordError) ErrorLight else contentColor),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                decorationBox = { innerTextField ->
                    Box {
                        if (password.isEmpty()) {
                            Text(
                                text = stringResource(R.string.password),
                                fontSize = 16.sp,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            // 尾缀可见性切换：图标化 + 48dp 方形热区（§3.7 定稿，禁止文字替代）。
            // 纯视觉切换，关闭连击防抖——快速连点切换是合法操作
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .pressable(onClick = showVisibility, debounce = false),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(
                        if (showPassword) R.string.hide_password else R.string.show_password
                    ),
                    modifier = Modifier.size(18.dp),
                    tint = contentColor.copy(alpha = 0.92f)
                )
            }
        }
        if (isPasswordError) {
            // 错误文案沿用既有 supportingText 内容（原 TextField supportingText 平替）
            Text(
                text = stringResource(R.string.invalid_password),
                fontSize = 13.sp,
                color = ErrorLight,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
