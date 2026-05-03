package com.smartwash.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun PhoneNumberInput(
    phone: String,
    isPhoneError: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = phone,
        onValueChange = onValueChange,
        label = { Text("手机号") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Phone
        ),
        isError = isPhoneError,
        supportingText = if (isPhoneError) {
            { Text("请输入正确的手机号") }
        } else null,
        leadingIcon = {
            Icon(
                Icons.Rounded.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        shape = MaterialTheme.shapes.small,
        singleLine = true,
    )
}
