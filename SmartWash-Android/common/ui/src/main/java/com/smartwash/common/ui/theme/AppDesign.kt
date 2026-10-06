package com.smartwash.common.ui.theme

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
    val surfaceVariant: Color,
    val groupBackground: Color,
    val hairline: Color,
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
    val water: Color,
    val waterSoft: Color,
    val iconContainerGreen: Color,
    val iconContainerBlue: Color,
    val iconContainerOrange: Color,
    val iconContainerPink: Color,
    val iconContainerTeal: Color,
    val iconContainerPurple: Color,
    val ongoing: Color,
    val ongoingInk: Color,
    val danger: Color,
    val dangerInk: Color,
    val iconForegroundGreen: Color,
    val iconForegroundBlue: Color,
    val iconForegroundOrange: Color,
    val iconForegroundPink: Color,
    val iconForegroundTeal: Color,
    val iconForegroundPurple: Color,
)

val LightAppColorScheme = AppColorScheme(
    primary = Primary,
    primaryLight = PrimaryLight,
    primaryDark = PrimaryDark,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    groupBackground = GroupBackground,
    hairline = Hairline,
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
    water = Water,
    waterSoft = WaterSoft,
    iconContainerGreen = IconContainerGreen,
    iconContainerBlue = IconContainerBlue,
    iconContainerOrange = IconContainerOrange,
    iconContainerPink = IconContainerPink,
    iconContainerTeal = IconContainerTeal,
    iconContainerPurple = IconContainerPurple,
    ongoing = Ongoing,
    ongoingInk = OngoingInk,
    danger = Danger,
    dangerInk = DangerInk,
    iconForegroundGreen = IconForegroundGreen,
    iconForegroundBlue = IconForegroundBlue,
    iconForegroundOrange = IconForegroundOrange,
    iconForegroundPink = IconForegroundPink,
    iconForegroundTeal = IconForegroundTeal,
    iconForegroundPurple = IconForegroundPurple,
)

val DarkAppColorScheme = AppColorScheme(
    primary = DarkPrimary,
    primaryLight = DarkPrimaryLight,
    primaryDark = DarkPrimaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    groupBackground = DarkGroupBackground,
    hairline = DarkHairline,
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
    water = DarkWater,
    waterSoft = DarkWaterSoft,
    iconContainerGreen = DarkIconContainerGreen,
    iconContainerBlue = DarkIconContainerBlue,
    iconContainerOrange = DarkIconContainerOrange,
    iconContainerPink = DarkIconContainerPink,
    iconContainerTeal = DarkIconContainerTeal,
    iconContainerPurple = DarkIconContainerPurple,
    ongoing = DarkOngoing,
    ongoingInk = DarkOngoingInk,
    danger = DarkDanger,
    dangerInk = DarkDangerInk,
    iconForegroundGreen = DarkIconForegroundGreen,
    iconForegroundBlue = DarkIconForegroundBlue,
    iconForegroundOrange = DarkIconForegroundOrange,
    iconForegroundPink = DarkIconForegroundPink,
    iconForegroundTeal = DarkIconForegroundTeal,
    iconForegroundPurple = DarkIconForegroundPurple,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColorScheme }

object AppColors {
    val colorScheme: AppColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current
}

// ========== 清氧设计系统 — 设计 Token ==========
// 容器层级：Canvas(暖白底) → Group(浅灰分组) → ListRow(分隔线) → HeroCard(主焦点) → InlineAction(行内)

object AppDimens {
    // 页面边距（规范 §2.3）
    val pagePadding = 24.dp
    // 旧卡片 token（保留向后兼容，新代码避免使用）
    @Deprecated("使用 radiusMd / radiusLg 替代", ReplaceWith("AppDimens.radiusLg"))
    val cardRadius = 20.dp
    @Deprecated("使用 radiusMd 替代", ReplaceWith("AppDimens.radiusMd"))
    val smallCardRadius = 16.dp
    val cardPadding = 20.dp
    val cardSpacing = 16.dp
    val sectionSpacing = 32.dp
    // 新圆角 token（规范 §2.3）
    val radiusXs = 4.dp     // 标签、小角标
    val radiusSm = 8.dp     // 输入框、小按钮
    val radiusSmall = 10.dp // 小图标容器、徽标
    val radiusMd = 12.dp    // 分组背景、图标容器、内嵌套块
    val radiusLg = 16.dp    // 卡片（唯一值）
    val radiusXl = 20.dp    // hero、大容器
    val radiusFull = 999.dp // 头像、胶囊
    // 按钮 / 输入
    val buttonRadius = 14.dp
    val inputRadius = 14.dp
    val iconContainerRadius = 12.dp
    val bottomBarHeight = 68.dp
    // 间距 token（8pt 网格）
    val spaceXxs = 4.dp
    val spaceXs = 8.dp
    val spaceSm = 12.dp
    val spaceMd = 16.dp
    val spaceLg = 20.dp
    val spaceXl = 24.dp
    val spaceXxl = 32.dp
}

// ========== 海拔层级 (Elevation Levels) ==========
object AppElevation {
    val level0 = 0.dp   // 平面（背景）
    val level1 = 1.dp   // 轻微浮起（普通卡片）
    val level2 = 3.dp   // 中度浮起（重要卡片）
    val level3 = 6.dp   // 高度浮起（交互卡片）
    val level4 = 12.dp  // 最高（弹窗/Sheet）
}

// ========== 可复用组件 ==========

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

// ==================== 观象台（占卜模块）— 玄墨子主题（追加） ====================
// 模块级子主题：进模块换氛围、退出即回清氧；照 LocalAppColors 模式，不污染全局 MaterialTheme。

@Immutable
data class DivinationColorScheme(
    val bgColors: List<Color>,          // 页面底渐变（宣纸/玄墨径向收束，取三段）
    val surface: Color,                 // 盘面卡片
    val surface2: Color,                // 次级面（中宫/嵌套卡）
    val line: Color,                    // 卡描边
    val hair: Color,                    // 发丝分隔
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val gold: Color,                    // 鎏金（文字级强调）
    val goldHi: Color,                  // 鎏金高光
    val goldSoft: Color,                // 8% 金底
    val gold14: Color,                  // 14% 金底（选中 glow）
    val goldLine: Color,                // 30% 金线
    val goldHair: Color,                // 14% 金发丝线
    val seal: Color,                    // 朱砂印
    val sealHi: Color,
    val jade: Color,                    // 青（吉/合/生标记）
    val yaoTop: Color,                  // 爻线渐变（暗=宣纸白爻，亮=白纸墨爻）
    val yaoBottom: Color,
    val spirits: List<Color>,           // 六神色点：青赤白黑黄紫
    val isDark: Boolean,
)

val DarkDivinationColors = DivinationColorScheme(
    bgColors = listOf(DivBgTopDark, DivBgMidDark, DivBgBottomDark),
    surface = DivSurfaceDark,
    surface2 = DivSurface2Dark,
    line = Color(0x38C9A961),           // 22% 金线
    hair = Color(0x1FC9A961),           // 12% 发丝
    textPrimary = DivTextPrimaryDark,
    textSecondary = DivTextSecondaryDark,
    textTertiary = DivTextTertiaryDark,
    gold = DivGoldDark,
    goldHi = DivGoldHiDark,
    goldSoft = Color(0x14C9A961),
    gold14 = Color(0x24C9A961),
    goldLine = Color(0x4DC9A961),
    goldHair = Color(0x24C9A961),
    seal = DivSealDark,
    sealHi = DivSealHiDark,
    jade = DivJadeDark,
    yaoTop = DivYaoTopDark,
    yaoBottom = DivYaoBottomDark,
    spirits = listOf(DivSpiritQing, DivSpiritChi, DivSpiritBai, DivSpiritHei, DivSpiritHuang, DivSpiritZi),
    isDark = true,
)

val LightDivinationColors = DivinationColorScheme(
    bgColors = listOf(DivBgTopLight, DivBgMidLight, DivBgBottomLight),
    surface = DivSurfaceLight,
    surface2 = DivSurface2Light,
    line = Color(0x38947434),
    hair = Color(0x1F947434),
    textPrimary = DivTextPrimaryLight,
    textSecondary = DivTextSecondaryLight,
    textTertiary = DivTextTertiaryLight,
    gold = DivGoldLight,
    goldHi = DivGoldHiLight,
    goldSoft = Color(0x14947434),
    gold14 = Color(0x26947434),
    goldLine = Color(0x52947434),
    goldHair = Color(0x2E947434),
    seal = DivSealLight,
    sealHi = DivSealHiLight,
    jade = DivJadeLight,
    yaoTop = DivYaoTopLight,
    yaoBottom = DivYaoBottomLight,
    spirits = listOf(DivSpiritQing, DivSpiritChi, DivSpiritBai, DivSpiritHei, DivSpiritHuang, DivSpiritZi),
    isDark = false,
)

val LocalDivinationColors = staticCompositionLocalOf { DarkDivinationColors }

object DivColors {
    val current: DivinationColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalDivinationColors.current
}
