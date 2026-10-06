package com.smartwash.common.ui.theme

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
val Background = Color(0xFFEFF1EE)       // 微绿调米白页面底色（规范 §2.1）
val OnBackground = Color(0xFF1C1C1E)
val Surface = Color(0xFFFFFFFF)          // 纯白卡片
val OnSurface = Color(0xFF1C1C1E)
val SurfaceVariant = Color(0xFFF5F6F3)   // 卡内次级块（输入框底、嵌套卡）
val OnSurfaceVariant = Color(0xFF6B6E73)
val GroupBackground = Color(0xFFE7EAE4)  // 分组容器底（比页面底再深一档）
val Hairline = Color(0xFFEFF1EE)        // 容器内部行分隔线底色

// 边框 & 辅助
val Outline = Color(0xFFE3E7E2)         // 卡片描边（1px）
val OutlineVariant = Color(0xFFF5F6F3)

// 错误
val Error = Color(0xFFFF3B30)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFF1F0)
val OnErrorContainer = Color(0xFF991B1B)

// 警告
val Warning = Color(0xFFD98A26)          // 规范语义橙（iOS 系统橙 on 白对比度不足，已废弃）
val OnWarning = Color(0xFFFFFFFF)
val WarningContainer = Color(0xFFFFF7ED)
val OnWarningContainer = Color(0xFF92400E)

// 状态语义（进行中 / 危险，规范 §6.5）
val Ongoing = Color(0xFFD98A26)
val OngoingInk = Color(0xFF8F5B0C)
val Danger = Color(0xFFC9526B)
val DangerInk = Color(0xFFA8435A)

// 成功
val Success = Color(0xFF34C759)
val OnSuccess = Color(0xFFFFFFFF)
val SuccessContainer = Color(0xFFF0FAF5)
val OnSuccessContainer = Color(0xFF065F46)

// 文字色阶
val TextPrimary = Color(0xFF1C1C1E)
val TextSecondary = Color(0xFF6B6E73)   // 次文本（对比度更高）
val TextTertiary = Color(0xFF9A9DA3)    // 弱文本（对比度更高）

// 分隔线
val Divider = Color(0xFFEFF1EE)

// 水意象（次要强调、进度、水滴）
val Water = Color(0xFF4A9FD4)
val WaterSoft = Color(0xFFDCEFF7)       // 水色浅底容器

// 图标容器六色浅底（规范 §2.1）
val IconContainerGreen = Color(0xFFE8F6EF)   // 洗护/主
val IconContainerBlue = Color(0xFFEEF2FB)    // 物流/寄件
val IconContainerOrange = Color(0xFFFDF3E7)  // 取件/待办
val IconContainerPink = Color(0xFFFDEEF0)    // 异常/取消
val IconContainerTeal = Color(0xFFF0FBF4)    // 账户/安全
val IconContainerPurple = Color(0xFFF4F0FB)  // 观象台/AI

// 图标前景六色（规范 §2.1 六色深色版）
val IconForegroundGreen = Color(0xFF2D9B6A)
val IconForegroundBlue = Color(0xFF4A67C9)
val IconForegroundOrange = Color(0xFFD98A26)
val IconForegroundPink = Color(0xFFC9526B)
val IconForegroundTeal = Color(0xFF3F9D78)
val IconForegroundPurple = Color(0xFF7C3AED)

// ========== 清氧设计系统 — 暗色主题 ==========

// 主色 — 自然绿（规范 §10 红线：品牌绿深浅色一致）
val DarkPrimary = Color(0xFF2D9B6A)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0xFF1D3229)
val DarkOnPrimaryContainer = Color(0xFFB9E8CF)

// 主色变体
val DarkPrimaryLight = Color(0xFF1D3229)     // 浅绿背景 → 深色模式用深绿底
val DarkPrimaryDark = Color(0xFF5BC894)      // 按下态稍亮（primaryDeep 深色值）

// 次要色
val DarkSecondary = Color(0xFFA9AEA9)
val DarkOnSecondary = Color(0xFF141614)
val DarkSecondaryContainer = Color(0xFF262A27)
val DarkOnSecondaryContainer = Color(0xFFE6E9E6)

// 第三色
val DarkTertiary = Color(0xFFA9AEA9)
val DarkOnTertiary = Color(0xFF141614)
val DarkTertiaryContainer = Color(0xFF262A27)
val DarkOnTertiaryContainer = Color(0xFFE6E9E6)

// 背景 & 表面（绿灰系，规范 §6.5 / 设计稿 .v3.dark）
val DarkBackground = Color(0xFF141614)       // 页面底色
val DarkOnBackground = Color(0xFFE6E9E6)     // 浅色文字
val DarkSurface = Color(0xFF1D201E)          // 卡片底色（比背景亮一档）
val DarkOnSurface = Color(0xFFE6E9E6)
val DarkSurfaceVariant = Color(0xFF262A27)
val DarkOnSurfaceVariant = Color(0xFFA9AEA9)
val DarkGroupBackground = Color(0xFF1A1E1B)  // 分组容器底（bg 与 surface 之间的绿灰中间档）
val DarkHairline = Color(0xFF262A27)         // 容器内部行分隔线底色

// 边框 & 辅助
val DarkOutline = Color(0xFF2C312D)
val DarkOutlineVariant = Color(0xFF262A27)

// 错误
val DarkError = Color(0xFFDB7186)
val DarkOnError = Color(0xFF141614)
val DarkErrorContainer = Color(0xFF341E24)
val DarkOnErrorContainer = Color(0xFFE795A5)

// 警告
val DarkWarning = Color(0xFFE2A44E)
val DarkOnWarning = Color(0xFF141614)
val DarkWarningContainer = Color(0xFF332917)
val DarkOnWarningContainer = Color(0xFFECB36A)

// 成功
val DarkSuccess = Color(0xFF4ADE6E)
val DarkOnSuccess = Color(0xFF141614)
val DarkSuccessContainer = Color(0xFF1D3229)
val DarkOnSuccessContainer = Color(0xFFB9E8CF)

// 文字色阶
val DarkTextPrimary = Color(0xFFE6E9E6)
val DarkTextSecondary = Color(0xFFA9AEA9)
val DarkTextTertiary = Color(0xFF7E837D)

// 分隔线
val DarkDivider = Color(0xFF262A27)

// 水意象（暗色下保持可读性）
val DarkWater = Color(0xFF5FAEE0)
val DarkWaterSoft = Color(0xFF1C2F3B)

// 状态语义（进行中 / 危险，深色版）
val DarkOngoing = Color(0xFFE2A44E)
val DarkOngoingInk = Color(0xFFECB36A)
val DarkDanger = Color(0xFFDB7186)
val DarkDangerInk = Color(0xFFE795A5)

// 图标容器六色浅底（暗色版）
val DarkIconContainerGreen = Color(0xFF1D3229)
val DarkIconContainerBlue = Color(0xFF1F2637)
val DarkIconContainerOrange = Color(0xFF332917)
val DarkIconContainerPink = Color(0xFF341E24)
val DarkIconContainerTeal = Color(0xFF17322B)
val DarkIconContainerPurple = Color(0xFF272038)

// 图标前景六色（暗色版，对应 .v3.dark --icon-*）
val DarkIconForegroundGreen = Color(0xFF5BC894)
val DarkIconForegroundBlue = Color(0xFF85A3E8)
val DarkIconForegroundOrange = Color(0xFFE5AC5C)
val DarkIconForegroundPink = Color(0xFFE58A9A)
val DarkIconForegroundTeal = Color(0xFF57C9A4)
val DarkIconForegroundPurple = Color(0xFFB49CE8)

// ========== 认证页渐变色（设计稿 .onauth：160deg 三段 #1A9E6E → #0D7A4A@50% → #0B5C3A） ==========
val AuthGradientTop = Color(0xFF1A9E6E)
val AuthGradientMid = Color(0xFF0D7A4A)
val AuthGradientBottom = Color(0xFF0B5C3A)

// ========== 毛玻璃效果色 ==========
val GlassBgSubtle = Color.White.copy(alpha = 0.10f)     // 极淡底色（设计稿 .glass 与品牌圆 bg 均为 rgba(255,255,255,.10)）
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

// ========== 认证页 CTA（玻璃卡白色按钮的品牌深绿文字，两页共用） ==========
val AuthCtaText = Color(0xFF1E8C5C)

// ========== 第三方品牌色 ==========
val WeChatGreen = Color(0xFF07C160)
val AlipayBlue = Color(0xFF1677FF)

// ========== 服务类型色彩 ==========
val ServiceWash = Color(0xFF2D9B6A)       // 标准洗 — 自然绿
val ServiceWashLight = Color(0xFFE8F6EF)
val ServiceDry = Color(0xFF7C3AED)        // 干洗 — 优雅紫
val ServiceDryLight = Color(0xFFF3EFFE)
val ServicePress = Color(0xFFE07B00)      // 熨烫 — 暖橙
val ServicePressLight = Color(0xFFFFF7ED)
val ServiceLuxury = Color(0xFF0EA5E9)     // 奢护 — 清澈蓝
val ServiceLuxuryLight = Color(0xFFEFF6FF)
val ServiceShoes = Color(0xFFDC2626)      // 洗鞋 — 活力红
val ServiceShoesLight = Color(0xFFFEF2F2)

// ==================== 观象台（占卜模块）— 宣纸/玄墨令牌（追加） ====================
// 取值来源：docs/design/占卜模块视觉原型.html CSS 变量 + UI 设计方案第 10.3/10.5 节。

// 深色 · 玄墨（主推）
val DivBgTopDark = Color(0xFF201D18)
val DivBgMidDark = Color(0xFF1A1917)
val DivBgBottomDark = Color(0xFF151412)
val DivSurfaceDark = Color(0xFF1E1D1B)
val DivSurface2Dark = Color(0xFF26241F)
val DivTextPrimaryDark = Color(0xFFEFE9DA)   // 宣纸主文本（暖调去塑料感）
val DivTextSecondaryDark = Color(0xFFB9B2A2)
val DivTextTertiaryDark = Color(0xFF847E6F)
val DivGoldDark = Color(0xFFC9A961)
val DivGoldHiDark = Color(0xFFD4B878)
val DivSealDark = Color(0xFFC8504A)
val DivSealHiDark = Color(0xFFD06054)
val DivJadeDark = Color(0xFF6FA593)
val DivYaoTopDark = Color(0xFFEFE9DA)        // 黑底宣纸白爻
val DivYaoBottomDark = Color(0xFFC9C2B0)

// 浅色 · 宣纸（非简单反色：底色宣纸化、白字变墨字、金压暗、爻线反转为墨）
val DivBgTopLight = Color(0xFFF8F3E7)
val DivBgMidLight = Color(0xFFF5F1E8)
val DivBgBottomLight = Color(0xFFEBE3D1)
val DivSurfaceLight = Color(0xFFFBF9F3)
val DivSurface2Light = Color(0xFFF1ECDD)
val DivTextPrimaryLight = Color(0xFF2A251C)  // 暖墨
val DivTextSecondaryLight = Color(0xFF6B6353)
val DivTextTertiaryLight = Color(0xFF98907C)
val DivGoldLight = Color(0xFF8F7233)         // 文字级暗金
val DivGoldHiLight = Color(0xFF6E5626)
val DivSealLight = Color(0xFFB8433A)         // 朱砂印两主题通用
val DivSealHiLight = Color(0xFFA63A30)
val DivJadeLight = Color(0xFF3F7261)
val DivYaoTopLight = Color(0xFF3A342A)       // 白纸墨爻
val DivYaoBottomLight = Color(0xFF26231C)

// 六神色点（青/赤/白/黑/黄/紫，两主题共用）
val DivSpiritQing = Color(0xFF6FA593)
val DivSpiritChi = Color(0xFFD06054)
val DivSpiritBai = Color(0xFFC9C2B0)
val DivSpiritHei = Color(0xFF54679C)
val DivSpiritHuang = Color(0xFFC9A961)
val DivSpiritZi = Color(0xFF8A7A9C)
