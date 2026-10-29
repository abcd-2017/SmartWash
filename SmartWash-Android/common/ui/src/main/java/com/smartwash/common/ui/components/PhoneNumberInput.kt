package com.smartwash.common.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.R
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween

private val ErrorLight = com.smartwash.common.ui.theme.ErrorLight

@Composable
fun PhoneNumberInput(
    phone: String,
    isPhoneError: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    // 可见字段标签（设计稿画板「t:手机号标签」12/500，§7 表单必须有可见标签；null 保持旧行为）
    label: String? = null,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.92f)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        // 输入行：高 48dp（§3.7 行高 48–50dp），文字与图标垂直居中
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 前缀图标 16dp + opacity .5（§3.7）
            Icon(
                Icons.Rounded.Phone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isPhoneError) ErrorLight
                else contentColor.copy(alpha = 0.5f)
            )
            // 前缀与文字间距 spaceSm(12dp)（§3.7）
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
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
                // §3.7：输入正文 16sp / Normal —— 输入是高频读写场景，禁用 14sp 及以下
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp, lineHeight = 24.sp, color = contentColor
                ),
                cursorBrush = SolidColor(if (isPhoneError) ErrorLight else contentColor),
                decorationBox = { innerTextField ->
                    Box {
                        if (phone.isEmpty()) {
                            // §3.7：占位符字号与正文一致
                            Text(
                                text = stringResource(R.string.phone_hint),
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        // 错误文案 0ms 硬切 → 展开/收起 + 淡入淡出（规范 7.8）
        AnimatedVisibility(
            visible = isPhoneError,
            enter = fadeIn(enterTween(DURATION_MICRO_MS)) +
                expandVertically(enterTween(DURATION_MICRO_MS)),
            exit = fadeOut(exitTween(140)) +
                shrinkVertically(exitTween(140)),
        ) {
            // 错误文案沿用既有 supportingText 内容（原 TextField supportingText 平替）
            Text(
                text = stringResource(R.string.invalid_phone),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = ErrorLight,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
