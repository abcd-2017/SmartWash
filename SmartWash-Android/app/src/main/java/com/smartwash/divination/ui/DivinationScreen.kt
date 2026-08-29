package com.smartwash.divination.ui

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
import androidx.compose.ui.platform.LocalContext
import com.smartwash.ui.theme.DarkDivinationColors
import com.smartwash.ui.theme.LightDivinationColors
import com.smartwash.ui.theme.LocalDivinationColors
import com.smartwash.utils.isReduceMotionEnabled

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
    CompositionLocalProvider(LocalDivinationColors provides scheme) {
        Box(
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to scheme.bgColors[0],
                            0.46f to scheme.bgColors[1],
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

/** 观象台各页通用：reduced-motion 判定（ANIMATOR_DURATION_SCALE=0 时动效降级） */
@Composable
fun divReduceMotion(): Boolean = isReduceMotionEnabled(LocalContext.current)
