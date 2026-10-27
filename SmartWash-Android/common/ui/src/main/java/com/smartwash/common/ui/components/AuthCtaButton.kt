package com.smartwash.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.pressable

/**
 * 认证页（玻璃卡）主 CTA 按钮 — 玻璃页特有形态，自绘实现无 state layer（规范 §7）：
 * 主题 primary 底 / onPrimary 文字（浅色 #2D9B6A·白 / 深色 #5BC894·墨绿，对齐设计稿登录/注册画板
 * 浅深两板 CTA 取值），48dp 高 / radiusFull 胶囊 / 文字 titleMedium（18/24/600）。
 * 防抖由调用方经 rememberDebouncedClick 注入（认证页动作用 800ms 档），本组件不叠加。
 * enabled=false 时整体降为 38% 透明度（规范 §3.1 禁用态：内容 38%，占位不塌陷）。
 */
@Composable
fun AuthCtaButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val active = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (active) Modifier.pressable(onClick = onClick, debounce = false) else Modifier)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 1f else 0.38f),
                shape = RoundedCornerShape(AppDimens.radiusFull)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
