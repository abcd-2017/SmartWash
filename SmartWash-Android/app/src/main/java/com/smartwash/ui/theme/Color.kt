package com.smartwash.ui.theme

import androidx.compose.ui.graphics.Color

// ========== 清氧设计系统 — 亮色主题 ==========

// 主色 — 自然绿
val Primary = Color(0xFF2D9B6A)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFE8F6EF)
val OnPrimaryContainer = Color(0xFF0B3625)

// 主色变体
val PrimaryLight = Color(0xFFF0FAF5)    // 超浅绿，选中态背景
val PrimaryDark = Color(0xFF1E8C5C)     // 深绿，按下态

// 次要色
val Secondary = Color(0xFF8E8E93)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFF2F2F7)
val OnSecondaryContainer = Color(0xFF1C1C1E)

// 第三色
val Tertiary = Color(0xFF6B7280)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFF2F2F7)
val OnTertiaryContainer = Color(0xFF1C1C1E)

// 背景 & 表面
val Background = Color(0xFFFAFAF8)       // 微暖米白页面底色
val OnBackground = Color(0xFF1C1C1E)     // iOS 系统黑
val Surface = Color(0xFFFFFFFF)          // 纯白卡片
val OnSurface = Color(0xFF1C1C1E)
val SurfaceVariant = Color(0xFFF2F2F7)
val OnSurfaceVariant = Color(0xFF8E8E93)

// 边框 & 辅助
val Outline = Color(0xFFE5E7EB)
val OutlineVariant = Color(0xFFF2F2F7)

// 错误
val Error = Color(0xFFFF3B30)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFF1F0)
val OnErrorContainer = Color(0xFF991B1B)

// 警告
val Warning = Color(0xFFFF9500)
val OnWarning = Color(0xFFFFFFFF)
val WarningContainer = Color(0xFFFFF7ED)
val OnWarningContainer = Color(0xFF92400E)

// 成功
val Success = Color(0xFF34C759)
val OnSuccess = Color(0xFFFFFFFF)
val SuccessContainer = Color(0xFFF0FAF5)
val OnSuccessContainer = Color(0xFF065F46)

// 文字色阶
val TextPrimary = Color(0xFF1C1C1E)
val TextSecondary = Color(0xFF8E8E93)
val TextTertiary = Color(0xFFC7C7CC)

// 分隔线
val Divider = Color(0xFFF2F2F7)

// ========== 清氧设计系统 — 暗色主题 ==========

// 主色 — 自然绿（暗色模式下稍亮，确保深色背景可读性）
val DarkPrimary = Color(0xFF4DD6A2)
val DarkOnPrimary = Color(0xFF003822)
val DarkPrimaryContainer = Color(0xFF0B3625)
val DarkOnPrimaryContainer = Color(0xFFC5F0DC)

// 主色变体
val DarkPrimaryLight = Color(0xFF1A3A2D)     // 浅绿背景 → 深色模式用深绿底
val DarkPrimaryDark = Color(0xFF6AE4B8)      // 按下态稍亮

// 次要色
val DarkSecondary = Color(0xFFB0B0B5)
val DarkOnSecondary = Color(0xFF1C1C1E)
val DarkSecondaryContainer = Color(0xFF3A3A3C)
val DarkOnSecondaryContainer = Color(0xFFE5E5EA)

// 第三色
val DarkTertiary = Color(0xFF8E8E93)
val DarkOnTertiary = Color(0xFF1C1C1E)
val DarkTertiaryContainer = Color(0xFF3A3A3C)
val DarkOnTertiaryContainer = Color(0xFFE5E5EA)

// 背景 & 表面
val DarkBackground = Color(0xFF1C1C1E)       // iOS 系统深黑
val DarkOnBackground = Color(0xFFF2F2F7)     // 浅色文字
val DarkSurface = Color(0xFF2C2C2E)          // 卡片底色（比背景稍亮）
val DarkOnSurface = Color(0xFFF2F2F7)
val DarkSurfaceVariant = Color(0xFF3A3A3C)
val DarkOnSurfaceVariant = Color(0xFFB0B0B5)

// 边框 & 辅助
val DarkOutline = Color(0xFF48484A)
val DarkOutlineVariant = Color(0xFF38383A)

// 错误
val DarkError = Color(0xFFFF6B6B)
val DarkOnError = Color(0xFF2D0000)
val DarkErrorContainer = Color(0xFF5C1A1A)
val DarkOnErrorContainer = Color(0xFFFFDADA)

// 警告
val DarkWarning = Color(0xFFFFB84D)
val DarkOnWarning = Color(0xFF2D1A00)
val DarkWarningContainer = Color(0xFF3A2A10)
val DarkOnWarningContainer = Color(0xFFFFE0B2)

// 成功
val DarkSuccess = Color(0xFF5AC98A)
val DarkOnSuccess = Color(0xFF003318)
val DarkSuccessContainer = Color(0xFF0B3625)
val DarkOnSuccessContainer = Color(0xFFB2F0D0)

// 文字色阶
val DarkTextPrimary = Color(0xFFF2F2F7)
val DarkTextSecondary = Color(0xFF8E8E93)
val DarkTextTertiary = Color(0xFF636366)

// 分隔线
val DarkDivider = Color(0xFF38383A)

// ========== 认证页渐变色 ==========
val AuthGradientTop = Color(0xFF1A9E6E)
val AuthGradientBottom = Color(0xFF0B5C3A)

// ========== 毛玻璃效果色 ==========
val GlassBgSubtle = Color.White.copy(alpha = 0.12f)     // 极淡底色
val GlassBg = Color.White.copy(alpha = 0.15f)           // 标准底色
val GlassBorderSubtle = Color.White.copy(alpha = 0.18f) // 淡边框
val GlassBorder = Color.White.copy(alpha = 0.2f)        // 标准边框
val GlassInput = Color.White.copy(alpha = 0.3f)         // 输入框/按钮底
val GlassTextSecondary = Color.White.copy(alpha = 0.4f)  // 次要文字
val GlassTextHint = Color.White.copy(alpha = 0.6f)      // 提示文字
val GlassTextDisabled = Color.White.copy(alpha = 0.7f)   // 禁用文字
val GlassTextActive = Color.White.copy(alpha = 0.8f)     // 激活文字

// ========== 错误色变体 ==========
val ErrorLight = Color(0xFFFFB4AB)   // 输入框错误态

// ========== 第三方品牌色 ==========
val WeChatGreen = Color(0xFF07C160)
val AlipayBlue = Color(0xFF1677FF)
