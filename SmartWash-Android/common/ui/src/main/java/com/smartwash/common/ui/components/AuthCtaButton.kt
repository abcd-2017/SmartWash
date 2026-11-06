package com.smartwash.common.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.pressable

/**
 * 认证页（玻璃卡）主 CTA 按钮 — 玻璃页特有形态，自绘实现无 state layer（规范 §7）：
 * [P01/P02] 2026-11-05 画板复裁：浅色板 = 白胶囊底 + 文字绿深档（textGreen #177A4C，
 * 2026-11-06 评审 R17/R22：原 primaryDark #1E8C5C on 白仅 4.23:1 不足 AA，改 textGreen ≈5.1:1）；
 * 深色板维持 primary 亮绿底 + onPrimary 墨绿字，
 * 48dp 高 / radiusFull 胶囊 / 文字 15sp·600（规范 §3.3 主按钮；U-116：v0.4 已废字距）。
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
    // 浅深两板 CTA 形态不同（设计稿登录/注册画板复裁）：浅色白底绿字 / 深色绿底墨字
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) MaterialTheme.colorScheme.primary else Color.White
    val contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary
    else AppColors.colorScheme.textGreen
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (active) Modifier.pressable(onClick = onClick, debounce = false) else Modifier)
            .background(
                color = containerColor.copy(alpha = if (active) 1f else 0.38f),
                shape = RoundedCornerShape(AppDimens.radiusFull)
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 文字 ↔ spinner 硬切 → 交叉淡变；spinner 复用 AppButton 的 reduced motion 降级（静态 270° 圆环）
        Crossfade(
            targetState = loading,
            animationSpec = tween(CROSSFADE_ALT_MS),
            label = "authCtaContent"
        ) { isLoading ->
            if (isLoading) {
                ButtonSpinner(
                    size = 22.dp,
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Text(
                    text = text,
                    // §3.3 主按钮文字 15sp/600（v0.4 覆写全应用统一口径）；
                    // U-116：v0.4 字阶已废字距，删除 letterSpacing = 1.sp
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = contentColor,
                )
            }
        }
    }
}
