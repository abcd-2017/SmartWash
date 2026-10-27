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

/**
 * 观象台子主题颜色常量（ARGB 0xAARRGGBB），逐项对应安卓 DivColors。
 */
export class DivColor {
  // ========== 深色 · 玄墨（主推）==========
  static readonly BG_TOP_DARK: number = 0xFF201D18
  static readonly BG_MID_DARK: number = 0xFF1A1917
  static readonly BG_BOTTOM_DARK: number = 0xFF151412
  static readonly SURFACE_DARK: number = 0xFF1E1D1B
  static readonly SURFACE_2_DARK: number = 0xFF26241F
  static readonly TEXT_PRIMARY_DARK: number = 0xFFEFE9DA   // 宣纸主文本（暖调去塑料感）
  static readonly TEXT_SECONDARY_DARK: number = 0xFFB9B2A2
  static readonly TEXT_TERTIARY_DARK: number = 0xFF847E6F
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
  static readonly SURFACE_LIGHT: number = 0xFFFBF9F3
  static readonly SURFACE_2_LIGHT: number = 0xFFF1ECDD
  static readonly TEXT_PRIMARY_LIGHT: number = 0xFF2A251C  // 暖墨
  static readonly TEXT_SECONDARY_LIGHT: number = 0xFF6B6353
  static readonly TEXT_TERTIARY_LIGHT: number = 0xFF98907C
  static readonly GOLD_LIGHT: number = 0xFF8F7233         // 文字级暗金
  static readonly GOLD_HI_LIGHT: number = 0xFF6E5626
  static readonly SEAL_LIGHT: number = 0xFFB8433A         // 朱砂印两主题通用
  static readonly SEAL_HI_LIGHT: number = 0xFFA63A30
  static readonly JADE_LIGHT: number = 0xFF3F7261
  static readonly YAO_TOP_LIGHT: number = 0xFF3A342A       // 白纸墨爻
  static readonly YAO_BOTTOM_LIGHT: number = 0xFF26231C

  // ========== 金线（卡片描边 / 分隔线，30% 金）==========
  // 安卓 DivColors.goldLine：暗色 0x4DC9A961 / 亮色 0x52947434（AppDesign.kt:344,368）
  static readonly GOLD_LINE_DARK: number = 0x4DC9A961
  static readonly GOLD_LINE_LIGHT: number = 0x52947434

  // ========== 金发丝 / 金底 / 金线（AppDesign.kt:335-345,359-369）==========
  // 安卓 DivinationColorScheme 的 line / hair / goldSoft / gold14 / goldHair
  static readonly LINE_DARK: number = 0x38C9A961
  static readonly LINE_LIGHT: number = 0x38947434
  static readonly HAIR_DARK: number = 0x1FC9A961
  static readonly HAIR_LIGHT: number = 0x1F947434
  static readonly GOLD_SOFT_DARK: number = 0x14C9A961
  static readonly GOLD_SOFT_LIGHT: number = 0x14947434
  static readonly GOLD_14_DARK: number = 0x24C9A961
  static readonly GOLD_14_LIGHT: number = 0x26947434
  static readonly GOLD_HAIR_DARK: number = 0x24C9A961
  static readonly GOLD_HAIR_LIGHT: number = 0x2E947434

  // ========== 六神色点（青/赤/白/黑/黄/紫，两主题共用）==========
  static readonly SPIRIT_QING: number = 0xFF6FA593
  static readonly SPIRIT_CHI: number = 0xFFD06054
  static readonly SPIRIT_BAI: number = 0xFFC9C2B0
  static readonly SPIRIT_HEI: number = 0xFF54679C
  static readonly SPIRIT_HUANG: number = 0xFFC9A961
  static readonly SPIRIT_ZI: number = 0xFF8A7A9C
}
