package com.smartwash.common.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.pressable

/**
 * 验证码输入框（[AppTextField] + 尾缀「获取验证码」描边按钮）。
 *
 * §3.7：验证码发送 = 描边按钮——1dp 描边 + 12dp 圆角（`radiusMd`，规范正文写 radiusSmall(12dp)，
 * 而 `AppDimens.radiusSmall` 实为 10dp）+ 高 40dp + 横向 14dp；倒计时禁用态描边与文字降至 .55 透明度。
 *
 * @param countdownText 已格式化的倒计时文案（如「重新发送(59s)」），由调用方按语言格式化
 * @param sendEnabled 可发送态（倒计时结束且未在请求中）；禁用态不响应点击
 */
@Composable
fun AppVerificationCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    sendText: String,
    countdownText: String,
    onSendCaptcha: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    countDown: Int = 0,
    sendEnabled: Boolean = true,
) {
    val colors = AppColors.colorScheme
    val canSend = sendEnabled && countDown == 0

    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        isError = isError,
        supportingText = supportingText,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .then(
                        if (canSend) Modifier.pressable(onClick = onSendCaptcha, debounce = false)
                        else Modifier
                    )
                    .border(
                        width = 1.dp,
                        color = if (canSend) colors.outline else colors.outline.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(AppDimens.radiusMd),
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = countDown > 0,
                    animationSpec = tween(CROSSFADE_ALT_MS),
                    label = "sendCaptchaLabel",
                ) { counting ->
                    Text(
                        text = if (counting) countdownText else sendText,
                        // 12/16/500 = captionStrong 档；倒计时秒数是数量，开 tnum（规范 §2.2）
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (canSend) colors.textPrimary else colors.textPrimary.copy(alpha = 0.55f),
                    )
                }
            }
        },
    )
}
