package com.smartwash.common.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.R

private val ErrorLight = com.smartwash.common.ui.theme.ErrorLight

@Composable
fun PhoneNumberInput(
    phone: String,
    isPhoneError: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
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
                Icons.Rounded.Phone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isPhoneError) ErrorLight
                else contentColor.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = phone,
                onValueChange = { newValue ->
                    // 只允许输入数字，过滤非数字字符
                    val filtered = newValue.filter { it.isDigit() }
                    onValueChange(filtered)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = contentColor),
                cursorBrush = SolidColor(if (isPhoneError) ErrorLight else contentColor),
                decorationBox = { innerTextField ->
                    Box {
                        if (phone.isEmpty()) {
                            Text(
                                text = stringResource(R.string.phone_number),
                                fontSize = 14.sp,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        if (isPhoneError) {
            // 错误文案沿用既有 supportingText 内容（原 TextField supportingText 平替）
            Text(
                text = stringResource(R.string.invalid_phone),
                fontSize = 13.sp,
                color = ErrorLight,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
