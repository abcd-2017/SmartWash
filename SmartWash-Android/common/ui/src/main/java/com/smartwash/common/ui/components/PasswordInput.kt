package com.smartwash.common.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.smartwash.common.ui.R

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
    TextField(
        value = password,
        onValueChange = { newValue ->
            // 超过16位直接截断，不显示报错
            val filtered = if (newValue.length > 16) newValue.take(16) else newValue
            onValueChange(filtered)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.fillMaxWidth(),
        isError = isPasswordError,
        supportingText = if (isPasswordError) {
            { Text(stringResource(R.string.invalid_password), color = ErrorLight) }
        } else null,
        leadingIcon = {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (isPasswordError) ErrorLight
                else contentColor.copy(alpha = 0.6f)
            )
        },
        trailingIcon = {
            IconButton(onClick = showVisibility) {
                Icon(
                    imageVector = if (showPassword) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f)
                )
            }
        },
        singleLine = true,
        placeholder = {
            Text(stringResource(R.string.password), color = contentColor.copy(alpha = 0.4f))
        },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedTextColor = contentColor,
            unfocusedTextColor = contentColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            cursorColor = contentColor,
            errorCursorColor = ErrorLight,
            errorLeadingIconColor = ErrorLight,
            errorSupportingTextColor = ErrorLight,
        )
    )
}
