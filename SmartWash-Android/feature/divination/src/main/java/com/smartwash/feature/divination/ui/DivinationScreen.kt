package com.smartwash.feature.divination.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.smartwash.common.ui.theme.DarkDivinationColors
import com.smartwash.common.ui.theme.LightDivinationColors
import com.smartwash.common.ui.theme.LocalDivinationColors

/**
 * 观象台页面根：进入模块即切换宣纸/玄墨子主题（CompositionLocal 覆盖），
 * 退出回清氧；背景为原型口径的径向收束渐变。
 */
@Composable
fun DivinationScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkDivinationColors else LightDivinationColors
    // 规范 §1.5：玄墨背景中段停靠 55%、宣纸 50%
    val midStop = if (scheme.isDark) 0.55f else 0.50f
    CompositionLocalProvider(LocalDivinationColors provides scheme) {
        Box(
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to scheme.bgColors[0],
                            midStop to scheme.bgColors[1],
                            1f to scheme.bgColors[2],
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .then(
                        if (scrollable) {
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                        } else {
                            Modifier.navigationBarsPadding()
                        }
                    ),
                content = content,
            )
        }
    }
}
