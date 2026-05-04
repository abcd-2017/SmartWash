package com.smartwash.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.smartwash.R

private val ErrorLight = com.smartwash.ui.theme.ErrorLight

@Composable
fun PhoneNumberInput(
    phone: String,
    isPhoneError: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = phone,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        isError = isPhoneError,
        supportingText = if (isPhoneError) {
            { Text(stringResource(R.string.invalid_phone), color = ErrorLight) }
        } else null,
        leadingIcon = {
            Icon(
                Icons.Rounded.Phone,
                contentDescription = null,
                tint = if (isPhoneError) ErrorLight
                else contentColor.copy(alpha = 0.6f)
            )
        },
        singleLine = true,
        placeholder = {
            Text(stringResource(R.string.phone_number), color = contentColor.copy(alpha = 0.4f))
        },
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
