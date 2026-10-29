// 观象台（占卜模块）子主题令牌 — 宣纸 / 玄墨色板
//
// 取值来源（逐项对照）：安卓 common/ui/src/main/java/com/smartwash/common/ui/theme/Color.kt:223-266
// 设计来源：docs/design/占卜模块视觉原型.html CSS 变量 + UI 设计方案第 10.3/10.5 节。
//
// 为什么独立于清氧 DesignSystem：玄墨是观象台专属深色皮肤（工作台玄墨特色卡在清氧页面里
// 开一扇通往玄墨世界的窗，浅色模式下同款深色，见 WorkbenchPage.kt:185-186），
// 与清氧亮色体系语义不同，混入会污染两套令牌，故单独成文件。
//
// 鸿蒙决策 D1：当前固定亮色模式（EntryAbility setColorMode COLOR_MODE_LIGHT），
// 故两套令牌均保留，玄墨（Dark）为工作台特色卡与观象台深色页实际消费方。

import { DesignColor } from './DesignSystem'

/**
 * 观象台子主题颜色常量（ARGB 0xAARRGGBB），逐项对应安卓 DivColors。
 *
 * 单一事实来源：玄墨/宣纸中「与清氧基础调色板同值」的条目一律引用 DesignColor，
 * 不再重复字面量（对齐安卓 Color.kt:223-266 —— DivTextSecondaryDark / DivTextTertiaryDark
 * 取基础 textTertiary，DivTextPrimaryLight / DivTextTertiaryLight 取基础 textPrimary，
 * DivTextSecondaryLight 取基础 textSecondary）。
 */
export class DivColor {
  // ========== 深色 · 玄墨（主推）==========
  static readonly BG_TOP_DARK: number = 0xFF201D18
  static readonly BG_MID_DARK: number = 0xFF1A1917
  static readonly BG_BOTTOM_DARK: number = 0xFF151412
  static readonly SURFACE_DARK: number = 0xFF1A1917
  static readonly SURFACE_2_DARK: number = 0xFF26241F
  static readonly TEXT_PRIMARY_DARK: number = 0xFFEFE9DA   // 宣纸主文本（暖调去塑料感）
  static readonly TEXT_SECONDARY_DARK: number = DesignColor.TEXT_TERTIARY  // = 安卓 DivTextSecondaryDark 0xFFA9AEA9
  static readonly TEXT_TERTIARY_DARK: number = DesignColor.TEXT_TERTIARY   // = 安卓 DivTextTertiaryDark 0xFFA9AEA9
  static readonly GOLD_DARK: number = 0xFFC9A961
  static readonly GOLD_HI_DARK: number = 0xFFD4B878
  static readonly SEAL_DARK: number = 0xFFC8504A
  static readonly SEAL_HI_DARK: number = 0xFFD06054
  static readonly JADE_DARK: number = 0xFF6FA593
  static readonly YAO_TOP_DARK: number = 0xFFEFE9DA        // 黑底宣纸白爻
  static readonly YAO_BOTTOM_DARK: number = 0xFFC9C2B0

  // ========== 浅色 · 宣纸（非简单反色：底色宣纸化、白字变墨字、金压暗、爻线反转为墨）==========
  static readonly BG_TOP_LIGHT: number = 0xFFF8F3E7
  static readonly BG_MID_LIGHT: number = 0xFFF5F1E8
  static readonly BG_BOTTOM_LIGHT: number = 0xFFEBE3D1
  static readonly SURFACE_LIGHT: number = 0xFFF8F3E7
  static readonly SURFACE_2_LIGHT: number = 0xFFF1ECDD
  static readonly TEXT_PRIMARY_LIGHT: number = DesignColor.TEXT_PRIMARY   // = 安卓 DivTextPrimaryLight 0xFF1C1C1E（墨）
  static readonly TEXT_SECONDARY_LIGHT: number = DesignColor.TEXT_SECONDARY  // = 安卓 DivTextSecondaryLight 0xFF6B6E73
  static readonly TEXT_TERTIARY_LIGHT: number = DesignColor.TEXT_PRIMARY  // = 安卓 DivTextTertiaryLight 0xFF1C1C1E（元信息保持全墨保证对比 §10）
  static readonly GOLD_LIGHT: number = 0xFF8F7233         // 文字级暗金
  static readonly GOLD_HI_LIGHT: number = 0xFF6E5626
  static readonly SEAL_LIGHT: number = 0xFFC8504A         // 朱砂印两主题通用
  static readonly SEAL_HI_LIGHT: number = 0xFFA63A30
  static readonly JADE_LIGHT: number = 0xFF3F7261
  static readonly YAO_TOP_LIGHT: number = 0xFF3A342A       // 白纸墨爻
  static readonly YAO_BOTTOM_LIGHT: number = 0xFF26231C

  // ========== 金线（卡片描边 / 分隔线，30% 金）==========
  // 安卓 DivColors.goldLine：暗色 0x4DC9A961 / 亮色 0x528F7233（AppDesign.kt:344,368）
  static readonly GOLD_LINE_DARK: number = 0x4DC9A961
  static readonly GOLD_LINE_LIGHT: number = 0x528F7233

  // ========== 金发丝 / 金底 / 金线（AppDesign.kt:335-345,359-369）==========
  // 安卓 DivinationColorScheme 的 line / hair / goldSoft / gold14 / goldHair
  static readonly LINE_DARK: number = 0x38C9A961
  static readonly LINE_LIGHT: number = 0x388F7233
  static readonly HAIR_DARK: number = 0x1FC9A961
  static readonly HAIR_LIGHT: number = 0x1F8F7233
  static readonly GOLD_SOFT_DARK: number = 0x14C9A961
  static readonly GOLD_SOFT_LIGHT: number = 0x148F7233
  static readonly GOLD_14_DARK: number = 0x24C9A961
  static readonly GOLD_14_LIGHT: number = 0x268F7233
  static readonly GOLD_HAIR_DARK: number = 0x24C9A961
  static readonly GOLD_HAIR_LIGHT: number = 0x2E8F7233

  // ========== 六神色点（青/赤/白/黑/黄/紫，两主题共用）==========
  static readonly SPIRIT_QING: number = 0xFF6FA593
  static readonly SPIRIT_CHI: number = 0xFFD06054
  static readonly SPIRIT_BAI: number = 0xFFC9C2B0
  static readonly SPIRIT_HEI: number = 0xFF54679C
  static readonly SPIRIT_HUANG: number = 0xFFC9A961
  static readonly SPIRIT_ZI: number = 0xFF8A7A9C
}
