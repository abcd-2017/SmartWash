package com.smartwash.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AuthCtaText
import com.smartwash.common.utils.pressable

/**
 * 认证页（玻璃卡）白色 CTA 按钮 — 玻璃页特有形态，自绘实现无 state layer（规范 §7）：
 * 白底 52dp 高 / 14dp 圆角 / 品牌深绿文字 15sp·600·letterSpacing 1sp。
 * 防抖由调用方经 rememberDebouncedClick 注入（认证页动作用 800ms 档），本组件不叠加。
 */
@Composable
fun AuthCtaButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (!loading) Modifier.pressable(onClick = onClick, debounce = false) else Modifier)
            .background(color = Color.White, shape = RoundedCornerShape(AppDimens.buttonRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AuthCtaText,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = AuthCtaText,
            )
        }
    }
}
