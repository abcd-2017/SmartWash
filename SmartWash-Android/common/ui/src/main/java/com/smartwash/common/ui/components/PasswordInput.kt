package com.smartwash.common.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.R
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
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
    // 可见字段标签（设计稿画板「t:密码标签」12/500，§7 表单必须有可见标签；null 保持旧行为）
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
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isPasswordError) ErrorLight
                else contentColor.copy(alpha = 0.5f)
            )
            // 前缀与文字间距 spaceSm(12dp)（§3.7）
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
                // §3.7：输入正文 16sp / Normal —— 输入是高频读写场景，禁用 14sp 及以下
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp, lineHeight = 24.sp, color = contentColor
                ),
                cursorBrush = SolidColor(if (isPasswordError) ErrorLight else contentColor),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                decorationBox = { innerTextField ->
                    Box {
                        if (password.isEmpty()) {
                            // §3.7：占位符字号与正文一致
                            Text(
                                text = stringResource(R.string.password_hint),
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            // 尾缀可见性切换：图标化 + 48dp 方形热区（§3.7 定稿，禁止文字替代）。
            // 热区 48dp 大于行高 20dp，垂直方向对称溢出行边界补足触控红线，不参与布局占位。
            // 纯视觉切换，关闭连击防抖——快速连点切换是合法操作
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressable(onClick = showVisibility, debounce = false),
                contentAlignment = Alignment.Center
            ) {
                // 可见性图标 0ms 硬切 → 交叉淡变（180ms 档，reduced motion 下同样成立）
                Crossfade(
                    targetState = showPassword,
                    animationSpec = tween(CROSSFADE_ALT_MS),
                    label = "passwordVisibilityIcon"
                ) { visible ->
                    Icon(
                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.hide_password else R.string.show_password
                        ),
                        modifier = Modifier.size(20.dp),
                        tint = contentColor.copy(alpha = 0.92f)
                    )
                }
            }
        }
        // 错误文案 0ms 硬切 → 展开/收起 + 淡入淡出（规范 7.8）
        AnimatedVisibility(
            visible = isPasswordError,
            enter = fadeIn(enterTween(DURATION_MICRO_MS)) +
                expandVertically(enterTween(DURATION_MICRO_MS)),
            exit = fadeOut(exitTween(140)) +
                shrinkVertically(exitTween(140)),
        ) {
            // 错误文案沿用既有 supportingText 内容（原 TextField supportingText 平替）
            Text(
                text = stringResource(R.string.invalid_password),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = ErrorLight,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
