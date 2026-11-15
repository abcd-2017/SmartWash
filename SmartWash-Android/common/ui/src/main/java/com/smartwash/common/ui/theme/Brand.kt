package com.smartwash.common.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 品牌维度（变体级）。
 *
 * 项目有两个 App 变体（app 模块 flavor：full = SmartWash / lite = 吃啥 TodayEat）。
 * 设计系统按品牌区分：**full 走「清氧」绿（[LightAppColorScheme]），lite 走「暖橙」**（本文件）。
 *
 * 为什么品牌放在 common:ui 而不是各业务页判断：
 * - dining / game 等共享域的页面**零硬编码色**（全部读 [AppColors]），因此只要切换
 *   [LocalAppColors] 的值，两个变体的全部页面自动换色，业务页零改动；
 * - common 层不感知 app 的 BuildConfig / flavor，品牌由壳层（MainActivity）按
 *   `BuildConfig.APP_CHANNEL` 注入，保持 common → feature → app 的依赖单向。
 *
 * 只有配色差异（无布局差异）时**不需要**给 feature 模块加 flavor 维度；仅当某域页面
 * 布局/字段真要分变体，才用该域 impl 的 `src/full` / `src/lite` 源集。
 */
enum class AppBrand {
    /** SmartWash 洗衣主航道（清氧绿，规范主色 #2D9B6A 红线） */
    SmartWash,

    /** 吃啥 TodayEat（暖橙，独立品牌色板） */
    TodayEat,
}

/** 当前品牌（壳层注入，默认 [AppBrand.SmartWash]——common 层兜底不臆断变体）。 */
val LocalAppBrand = staticCompositionLocalOf { AppBrand.SmartWash }

object AppBrandTokens {
    val brand: AppBrand
        @Composable @ReadOnlyComposable
        get() = LocalAppBrand.current
}

// ========== 吃啥（TodayEat）品牌色板 — 亮色 ==========
// 暖橙 + 米白：食欲感、与清氧绿彻底区隔；危险色改用独立深红 #B3261E，避免与主橙混淆。
// 文字强调档 [TextGreen]/[TextGreenOnTint] 沿用「深档用于小号文字」的既有约定（on 白 ≥ 4.5:1）。

private val LitePrimary = Color(0xFFEA580C)          // 橙-600，品牌主色（渐变起点/图形着色）
private val LiteOnPrimary = Color(0xFFFFFFFF)
private val LitePrimaryContainer = Color(0xFFFDF0E7)
private val LiteOnPrimaryContainer = Color(0xFF7C2D12)   // 橙-900
private val LitePrimaryLight = Color(0xFFFFF7ED)     // 橙-50，图标容器/票券底
private val LitePrimarySoft = Color(0xFFFDF0E7)      // 选中底
private val LiteOnPrimarySoft = Color(0xFF7C2D12)
private val LitePrimaryDark = Color(0xFFC2410C)      // 橙-700，按下态/渐变暗端（on 白 5.2:1）

private val LiteBackground = Color(0xFFFFFBF7)       // 暖米白
private val LiteOnBackground = Color(0xFF292524)     // stone-800
private val LiteSurface = Color(0xFFFFFFFF)
private val LiteOnSurface = Color(0xFF292524)
private val LiteSurfaceVariant = Color(0xFFFDF3EC)
private val LiteOnSurfaceVariant = Color(0xFF57534E) // stone-600（on 底 ≥ 4.5:1）
private val LiteGroupBackground = Color(0xFFF7EDE4)
private val LiteHairline = Color(0xFFF5E8DD)

private val LiteOutline = Color(0xFFF0E2D5)
private val LiteOutlineVariant = Color(0xFFF5E8DD)

private val LiteError = Color(0xFFB3261E)            // 独立深红，不撞主橙
private val LiteOnError = Color(0xFFFFFFFF)
private val LiteErrorContainer = Color(0xFFFDECEA)
private val LiteOnErrorContainer = Color(0xFFB3261E)

private val LiteWarning = Color(0xFFB45309)          // 琥珀-700
private val LiteOnWarning = Color(0xFFFFFFFF)
private val LiteWarningContainer = Color(0xFFFDF3E0)
private val LiteOnWarningContainer = Color(0xFF7C4A03)

private val LiteSuccess = Color(0xFF1F7A4D)          // 语义绿（少量，仅成功态）
private val LiteOnSuccess = Color(0xFFFFFFFF)
private val LiteSuccessContainer = Color(0xFFE9F7EF)
private val LiteOnSuccessContainer = Color(0xFF17322B)

private val LiteTextPrimary = Color(0xFF292524)
private val LiteTextSecondary = Color(0xFF57534E)
private val LiteTextTertiary = Color(0xFFA8A29E)

/** 文字强调（链接/小号强调）——on 白 5.2:1，达 AA。 */
private val LiteTextAccent = Color(0xFFC2410C)
private val LiteTextAccentOnTint = Color(0xFF9A3412) // 橙-800，tinted 底上用

private val LiteDivider = Color(0xFFF5E8DD)

private val LiteWater = Color(0xFF4A9FD4)
private val LiteWaterSoft = Color(0xFFDCEFF7)

private val LiteOngoing = Color(0xFFB45309)
private val LiteOngoingInk = Color(0xFFB45309)
private val LiteDanger = Color(0xFFB3261E)
private val LiteDangerInk = Color(0xFFB3261E)

// 吃啥图标容器六色（语义槽位沿用 AppColorScheme，值改为暖色语义）
private val LiteIconContainerGreen = Color(0xFFFDF0E7)   // 主流程（发起）
private val LiteIconContainerBlue = Color(0xFFEEF2FB)    // 加入/二维码/链接
private val LiteIconContainerOrange = Color(0xFFFDF3E7)  // 进行中/待办
private val LiteIconContainerPink = Color(0xFFFDEEF0)    // 异常/取消
private val LiteIconContainerTeal = Color(0xFFE9F7EF)    // 账户/我的
private val LiteIconContainerPurple = Color(0xFFF4F0FB)  // 游戏/娱乐

private val LiteIconForegroundGreen = Color(0xFFC2410C)
private val LiteIconForegroundBlue = Color(0xFF3B5BDB)
private val LiteIconForegroundOrange = Color(0xFFB45309)
private val LiteIconForegroundPink = Color(0xFFC9526B)
private val LiteIconForegroundTeal = Color(0xFF1F7A4D)
private val LiteIconForegroundPurple = Color(0xFF7C3AED)

// ========== 吃啥（TodayEat）品牌色板 — 暗色 ==========
// 结构令牌下移两档、强调色提亮一档（与清氧·夜同策略）；主色提亮为橙-400 #FB923C，
// 主按钮文字反转为深橙 #431407 保证对比度。

private val DarkLitePrimary = Color(0xFFFB923C)
private val DarkLiteOnPrimary = Color(0xFF431407)
private val DarkLitePrimaryContainer = Color(0xFF7C2D12)
private val DarkLiteOnPrimaryContainer = Color(0xFFFDBA74)
private val DarkLitePrimaryLight = Color(0xFF3B2417)
private val DarkLitePrimarySoft = Color(0xFF33201A)
private val DarkLiteOnPrimarySoft = Color(0xFFFDBA74)
private val DarkLitePrimaryDark = Color(0xFFFDBA74)

private val DarkLiteBackground = Color(0xFF1C1917)    // stone-900
private val DarkLiteOnBackground = Color(0xFFF5F5F4)
private val DarkLiteSurface = Color(0xFF292524)
private val DarkLiteOnSurface = Color(0xFFF5F5F4)
private val DarkLiteSurfaceVariant = Color(0xFF33302C)
private val DarkLiteOnSurfaceVariant = Color(0xFFA8A29E)
private val DarkLiteGroupBackground = Color(0xFF24211F)
private val DarkLiteHairline = Color(0xFF33302C)

private val DarkLiteOutline = Color(0xFF3B3734)
private val DarkLiteOutlineVariant = Color(0xFF33302C)

private val DarkLiteError = Color(0xFFF87171)
private val DarkLiteOnError = Color(0xFF1C1917)
private val DarkLiteErrorContainer = Color(0xFF3A1F24)
private val DarkLiteOnErrorContainer = Color(0xFFF87171)

private val DarkLiteWarning = Color(0xFFE5AC5C)
private val DarkLiteOnWarning = Color(0xFF1C1917)
private val DarkLiteWarningContainer = Color(0xFF332917)
private val DarkLiteOnWarningContainer = Color(0xFFECB36A)

private val DarkLiteSuccess = Color(0xFF4ADE80)
private val DarkLiteOnSuccess = Color(0xFF1C1917)
private val DarkLiteSuccessContainer = Color(0xFF17322B)
private val DarkLiteOnSuccessContainer = Color(0xFFD1FAE5)

private val DarkLiteTextPrimary = Color(0xFFF5F5F4)
private val DarkLiteTextSecondary = Color(0xFFA8A29E)
private val DarkLiteTextTertiary = Color(0xFF78716C)

private val DarkLiteTextAccent = Color(0xFFFB923C)
private val DarkLiteTextAccentOnTint = Color(0xFFFDBA74)

private val DarkLiteDivider = Color(0xFF33302C)

private val DarkLiteWater = Color(0xFF5FAEE0)
private val DarkLiteWaterSoft = Color(0xFF1C2F3B)

private val DarkLiteOngoing = Color(0xFFE5AC5C)
private val DarkLiteOngoingInk = Color(0xFFECB36A)
private val DarkLiteDanger = Color(0xFFF87171)
private val DarkLiteDangerInk = Color(0xFFF87171)

private val DarkLiteIconContainerGreen = Color(0xFF3B2417)
private val DarkLiteIconContainerBlue = Color(0xFF1F2637)
private val DarkLiteIconContainerOrange = Color(0xFF332917)
private val DarkLiteIconContainerPink = Color(0xFF341E24)
private val DarkLiteIconContainerTeal = Color(0xFF17322B)
private val DarkLiteIconContainerPurple = Color(0xFF272038)

private val DarkLiteIconForegroundGreen = Color(0xFFFB923C)
private val DarkLiteIconForegroundBlue = Color(0xFF85A3E8)
private val DarkLiteIconForegroundOrange = Color(0xFFE5AC5C)
private val DarkLiteIconForegroundPink = Color(0xFFE58A9A)
private val DarkLiteIconForegroundTeal = Color(0xFF57C9A4)
private val DarkLiteIconForegroundPurple = Color(0xFFB49CE8)

/** 吃啥 · 亮色设计令牌（语义槽位与清氧同构，值换暖橙）。 */
val LiteAppColorScheme = AppColorScheme(
    primary = LitePrimary,
    onPrimary = LiteOnPrimary,
    primaryLight = LitePrimaryLight,
    primarySoft = LitePrimarySoft,
    onPrimarySoft = LiteOnPrimarySoft,
    primaryDark = LitePrimaryDark,
    brandBase = LitePrimary,
    heroGradientStart = LitePrimary,
    heroGradientEnd = LitePrimaryDark,
    background = LiteBackground,
    surface = LiteSurface,
    surfaceVariant = LiteSurfaceVariant,
    groupBackground = LiteGroupBackground,
    hairline = LiteHairline,
    onBackground = LiteOnBackground,
    onSurface = LiteOnSurface,
    textPrimary = LiteTextPrimary,
    textSecondary = LiteTextSecondary,
    textTertiary = LiteTextTertiary,
    textGreen = LiteTextAccent,
    textGreenOnTint = LiteTextAccentOnTint,
    divider = LiteDivider,
    outline = LiteOutline,
    error = LiteError,
    warning = LiteWarning,
    warningContainer = LiteWarningContainer,
    onWarningContainer = LiteOnWarningContainer,
    success = LiteSuccess,
    iconTint = LiteTextSecondary,
    water = LiteWater,
    waterSoft = LiteWaterSoft,
    iconContainerGreen = LiteIconContainerGreen,
    iconContainerBlue = LiteIconContainerBlue,
    iconContainerOrange = LiteIconContainerOrange,
    iconContainerPink = LiteIconContainerPink,
    iconContainerTeal = LiteIconContainerTeal,
    iconContainerPurple = LiteIconContainerPurple,
    ongoing = LiteOngoing,
    ongoingInk = LiteOngoingInk,
    danger = LiteDanger,
    dangerInk = LiteDangerInk,
    iconForegroundGreen = LiteIconForegroundGreen,
    iconForegroundBlue = LiteIconForegroundBlue,
    iconForegroundOrange = LiteIconForegroundOrange,
    iconForegroundPink = LiteIconForegroundPink,
    iconForegroundTeal = LiteIconForegroundTeal,
    iconForegroundPurple = LiteIconForegroundPurple,
)

/** 吃啥 · 暗色设计令牌。 */
val DarkLiteAppColorScheme = AppColorScheme(
    primary = DarkLitePrimary,
    onPrimary = DarkLiteOnPrimary,
    primaryLight = DarkLitePrimaryLight,
    primarySoft = DarkLitePrimarySoft,
    onPrimarySoft = DarkLiteOnPrimarySoft,
    primaryDark = DarkLitePrimaryDark,
    brandBase = LitePrimary,
    heroGradientStart = DarkLitePrimary,
    heroGradientEnd = Color(0xFFF97316),
    background = DarkLiteBackground,
    surface = DarkLiteSurface,
    surfaceVariant = DarkLiteSurfaceVariant,
    groupBackground = DarkLiteGroupBackground,
    hairline = DarkLiteHairline,
    onBackground = DarkLiteOnBackground,
    onSurface = DarkLiteOnSurface,
    textPrimary = DarkLiteTextPrimary,
    textSecondary = DarkLiteTextSecondary,
    textTertiary = DarkLiteTextTertiary,
    textGreen = DarkLiteTextAccent,
    textGreenOnTint = DarkLiteTextAccentOnTint,
    divider = DarkLiteDivider,
    outline = DarkLiteOutline,
    error = DarkLiteError,
    warning = DarkLiteWarning,
    warningContainer = DarkLiteWarningContainer,
    onWarningContainer = DarkLiteOnWarningContainer,
    success = DarkLiteSuccess,
    iconTint = DarkLiteTextSecondary,
    water = DarkLiteWater,
    waterSoft = DarkLiteWaterSoft,
    iconContainerGreen = DarkLiteIconContainerGreen,
    iconContainerBlue = DarkLiteIconContainerBlue,
    iconContainerOrange = DarkLiteIconContainerOrange,
    iconContainerPink = DarkLiteIconContainerPink,
    iconContainerTeal = DarkLiteIconContainerTeal,
    iconContainerPurple = DarkLiteIconContainerPurple,
    ongoing = DarkLiteOngoing,
    ongoingInk = DarkLiteOngoingInk,
    danger = DarkLiteDanger,
    dangerInk = DarkLiteDangerInk,
    iconForegroundGreen = DarkLiteIconForegroundGreen,
    iconForegroundBlue = DarkLiteIconForegroundBlue,
    iconForegroundOrange = DarkLiteIconForegroundOrange,
    iconForegroundPink = DarkLiteIconForegroundPink,
    iconForegroundTeal = DarkLiteIconForegroundTeal,
    iconForegroundPurple = DarkLiteIconForegroundPurple,
)

/**
 * 吃啥 · Material3 配色（供 [androidx.compose.material3.MaterialTheme] 使用）。
 *
 * 必须与 [LiteAppColorScheme] 成对提供：部分 Material 组件（HorizontalDivider / TabRow /
 * PullToRefresh / TextField 聚焦色等）直接读 `MaterialTheme.colorScheme`，只换 AppColors
 * 会让这些位置在 lite 仍透清氧绿。
 */
val LiteLightColorScheme = lightColorScheme(
    primary = LitePrimary,
    onPrimary = LiteOnPrimary,
    primaryContainer = LitePrimaryContainer,
    onPrimaryContainer = LiteOnPrimaryContainer,
    secondary = Color(0xFF79716B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0EBE5),
    onSecondaryContainer = Color(0xFF292524),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LiteWarningContainer,
    onTertiaryContainer = LiteOnWarningContainer,
    background = LiteBackground,
    onBackground = LiteOnBackground,
    surface = LiteSurface,
    onSurface = LiteOnSurface,
    surfaceVariant = LiteSurfaceVariant,
    onSurfaceVariant = LiteOnSurfaceVariant,
    outline = Color(0xFFD6C8BC),
    outlineVariant = LiteOutlineVariant,
    error = LiteError,
    onError = LiteOnError,
    errorContainer = LiteErrorContainer,
    onErrorContainer = LiteOnErrorContainer,
)

/** 吃啥 · 暗色 Material3 配色。 */
val LiteDarkColorScheme = darkColorScheme(
    primary = DarkLitePrimary,
    onPrimary = DarkLiteOnPrimary,
    primaryContainer = DarkLitePrimaryContainer,
    onPrimaryContainer = DarkLiteOnPrimaryContainer,
    secondary = Color(0xFFA8A29E),
    onSecondary = Color(0xFF1C1917),
    secondaryContainer = Color(0xFF33302C),
    onSecondaryContainer = Color(0xFFF5F5F4),
    tertiary = Color(0xFFE5AC5C),
    onTertiary = Color(0xFF1C1917),
    tertiaryContainer = DarkLiteWarningContainer,
    onTertiaryContainer = DarkLiteOnWarningContainer,
    background = DarkLiteBackground,
    onBackground = DarkLiteOnBackground,
    surface = DarkLiteSurface,
    onSurface = DarkLiteOnSurface,
    surfaceVariant = DarkLiteSurfaceVariant,
    onSurfaceVariant = DarkLiteOnSurfaceVariant,
    outline = Color(0xFF57534E),
    outlineVariant = DarkLiteOutlineVariant,
    error = DarkLiteError,
    onError = DarkLiteOnError,
    errorContainer = DarkLiteErrorContainer,
    onErrorContainer = DarkLiteOnErrorContainer,
)

// ========== 吃啥 · 认证页渐变（160° 三段，与清氧认证渐变同构换色） ==========
val LiteAuthGradientTop = Color(0xFFEA580C)
val LiteAuthGradientMid = Color(0xFFC2410C)
val LiteAuthGradientBottom = Color(0xFF9A3412)
