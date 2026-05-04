package com.smartwash.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ========== 清氧设计系统 — 主题感知颜色 ==========

@Immutable
data class AppColorScheme(
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val outline: Color,
    val error: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val iconTint: Color,
)

val LightAppColorScheme = AppColorScheme(
    primary = Primary,
    primaryLight = PrimaryLight,
    primaryDark = PrimaryDark,
    background = Background,
    surface = Surface,
    onBackground = OnBackground,
    onSurface = OnSurface,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textTertiary = TextTertiary,
    divider = Divider,
    outline = Outline,
    error = Error,
    warning = Warning,
    warningContainer = WarningContainer,
    onWarningContainer = OnWarningContainer,
    success = Success,
    iconTint = TextSecondary,
)

val DarkAppColorScheme = AppColorScheme(
    primary = DarkPrimary,
    primaryLight = DarkPrimaryLight,
    primaryDark = DarkPrimaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    divider = DarkDivider,
    outline = DarkOutline,
    error = DarkError,
    warning = DarkWarning,
    warningContainer = DarkWarningContainer,
    onWarningContainer = DarkOnWarningContainer,
    success = DarkSuccess,
    iconTint = DarkTextSecondary,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColorScheme }

object AppColors {
    val colorScheme: AppColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current
}

// ========== 清氧设计系统 — 设计 Token ==========

object AppDimens {
    val pagePadding = 20.dp
    val cardPadding = 16.dp
    val cardSpacing = 12.dp
    val sectionSpacing = 24.dp
    val cardRadius = 20.dp
    val smallCardRadius = 16.dp
    val buttonRadius = 14.dp
    val inputRadius = 14.dp
    val iconContainerRadius = 12.dp
    val bottomBarHeight = 56.dp
}

// ========== 可复用组件 ==========

@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = AppDimens.pagePadding)
    )
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@Composable
fun IconBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    containerColor: Color = AppColors.colorScheme.primaryLight,
    iconTint: Color = AppColors.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )
    }
}

@Composable
fun CircleIconBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color = AppColors.colorScheme.primaryLight,
    iconTint: Color = AppColors.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )
    }
}

@Composable
fun ThinDivider(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.cardPadding)
            .height(0.5.dp)
            .background(AppColors.colorScheme.divider)
    )
}

@Composable
fun StatusDot(
    color: Color = Success,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}
