import { resColor } from '../utils/ResourceColor'
// 观象台（占卜模块）子主题令牌 — 宣纸 / 玄墨色板
//
// 取值来源（逐项对照）：安卓 common/ui/src/main/java/com/smartwash/common/ui/theme/Color.kt:223-266
// 设计来源：docs/design/占卜模块视觉原型.html CSS 变量 + UI 设计方案第 10.3/10.5 节。
//
// 为什么独立于清氧 DesignSystem：玄墨是观象台专属深色皮肤（工作台玄墨特色卡在清氧页面里
// 开一扇通往玄墨世界的窗，浅色模式下同款深色，见 WorkbenchPage.kt:185-186），
// 与清氧亮色体系语义不同，混入会污染两套令牌，故单独成文件。
//
// 主题现状（compare-08 #12 更新，原「决策 D1 固定亮色」注释已过时）：应用已随系统深浅色
// （EntryAbility 问题 17，清氧域走资源限定符自动切换），但观象台域
// （DivPalette / DivinationScreen）仍固定消费 *_LIGHT 宣纸令牌，*_DARK 玄墨档在观象台域
// 零消费——系统深色下域级主题断层。玄墨接入为域级重构待办，见 DivPalette TODO 与
// docs/review/2026-11-11/compare-08-div-history.md #12。



/**
 * 观象台子主题颜色常量（ARGB 0xAARRGGBB），逐项对应安卓 DivColors。
 *
 * 单一事实来源：玄墨/宣纸中「与清氧基础调色板同值」的条目一律引用设计系统资源色，
 * 不再重复字面量（对齐安卓 Color.kt:223-266 —— DivTextSecondaryDark / DivTextTertiaryDark
 * 取基础 textTertiary，DivTextPrimaryLight / DivTextTertiaryLight 取基础 textPrimary；
 * DivTextSecondaryLight 原取基础 textSecondary，2026-11-06 安卓 R21 独立改值 0xFF5A5E5A，鸿蒙同步）。
 */
export class DivColor {
  static get BG_BOTTOM_DARK(): ResourceColor { return $r('app.color.DIV_BG_BOTTOM_DARK') }
  static get BG_BOTTOM_LIGHT(): ResourceColor { return $r('app.color.DIV_BG_BOTTOM_LIGHT') }
  static get BG_MID_DARK(): ResourceColor { return $r('app.color.DIV_BG_MID_DARK') }
  static get BG_MID_LIGHT(): ResourceColor { return $r('app.color.DIV_BG_MID_LIGHT') }
  static get BG_TOP_DARK(): ResourceColor { return $r('app.color.DIV_BG_TOP_DARK') }
  static get BG_TOP_LIGHT(): ResourceColor { return $r('app.color.DIV_BG_TOP_LIGHT') }
  static get GOLD_14_DARK(): ResourceColor { return $r('app.color.DIV_GOLD_14_DARK') }
  static get GOLD_14_LIGHT(): ResourceColor { return $r('app.color.DIV_GOLD_14_LIGHT') }
  static get GOLD_DARK(): ResourceColor { return $r('app.color.DIV_GOLD_DARK') }
  static get GOLD_HAIR_DARK(): ResourceColor { return $r('app.color.DIV_GOLD_HAIR_DARK') }
  static get GOLD_HAIR_LIGHT(): ResourceColor { return $r('app.color.DIV_GOLD_HAIR_LIGHT') }
  static get GOLD_HI_DARK(): ResourceColor { return $r('app.color.DIV_GOLD_HI_DARK') }
  static get GOLD_HI_LIGHT(): ResourceColor { return $r('app.color.DIV_GOLD_HI_LIGHT') }
  static get GOLD_LIGHT(): ResourceColor { return $r('app.color.DIV_GOLD_LIGHT') }
  static get GOLD_LINE_DARK(): ResourceColor { return $r('app.color.DIV_GOLD_LINE_DARK') }
  static get GOLD_LINE_LIGHT(): ResourceColor { return $r('app.color.DIV_GOLD_LINE_LIGHT') }
  static get GOLD_SOFT_DARK(): ResourceColor { return $r('app.color.DIV_GOLD_SOFT_DARK') }
  static get GOLD_SOFT_LIGHT(): ResourceColor { return $r('app.color.DIV_GOLD_SOFT_LIGHT') }
  static get HAIR_DARK(): ResourceColor { return $r('app.color.DIV_HAIR_DARK') }
  static get HAIR_LIGHT(): ResourceColor { return $r('app.color.DIV_HAIR_LIGHT') }
  static get JADE_DARK(): ResourceColor { return $r('app.color.DIV_JADE_DARK') }
  static get JADE_LIGHT(): ResourceColor { return $r('app.color.DIV_JADE_LIGHT') }
  static get LINE_DARK(): ResourceColor { return $r('app.color.DIV_LINE_DARK') }
  static get LINE_LIGHT(): ResourceColor { return $r('app.color.DIV_LINE_LIGHT') }
  static get SEAL_DARK(): ResourceColor { return $r('app.color.DIV_SEAL_DARK') }
  static get SEAL_HI_DARK(): ResourceColor { return $r('app.color.DIV_SEAL_HI_DARK') }
  static get SEAL_HI_LIGHT(): ResourceColor { return $r('app.color.DIV_SEAL_HI_LIGHT') }
  static get SEAL_LIGHT(): ResourceColor { return $r('app.color.DIV_SEAL_LIGHT') }
  static get SPIRIT_BAI(): ResourceColor { return $r('app.color.DIV_SPIRIT_BAI') }
  static get SPIRIT_CHI(): ResourceColor { return $r('app.color.DIV_SPIRIT_CHI') }
  static get SPIRIT_HEI(): ResourceColor { return $r('app.color.DIV_SPIRIT_HEI') }
  static get SPIRIT_HUANG(): ResourceColor { return $r('app.color.DIV_SPIRIT_HUANG') }
  static get SPIRIT_QING(): ResourceColor { return $r('app.color.DIV_SPIRIT_QING') }
  static get SPIRIT_ZI(): ResourceColor { return $r('app.color.DIV_SPIRIT_ZI') }
  static get SURFACE_2_DARK(): ResourceColor { return $r('app.color.DIV_SURFACE_2_DARK') }
  static get SURFACE_2_LIGHT(): ResourceColor { return $r('app.color.DIV_SURFACE_2_LIGHT') }
  static get SURFACE_DARK(): ResourceColor { return $r('app.color.DIV_SURFACE_DARK') }
  static get SURFACE_LIGHT(): ResourceColor { return $r('app.color.DIV_SURFACE_LIGHT') }
  static get TEXT_PRIMARY_DARK(): ResourceColor { return $r('app.color.DIV_TEXT_PRIMARY_DARK') }
  static get TEXT_PRIMARY_LIGHT(): ResourceColor { return $r('app.color.DIV_TEXT_PRIMARY_LIGHT') }
  static get TEXT_SECONDARY_DARK(): ResourceColor { return $r('app.color.DIV_TEXT_SECONDARY_DARK') }
  static get TEXT_SECONDARY_LIGHT(): ResourceColor { return $r('app.color.DIV_TEXT_SECONDARY_LIGHT') }
  static get TEXT_TERTIARY_DARK(): ResourceColor { return $r('app.color.DIV_TEXT_TERTIARY_DARK') }
  static get TEXT_TERTIARY_LIGHT(): ResourceColor { return $r('app.color.DIV_TEXT_TERTIARY_LIGHT') }
  static get YAO_BOTTOM_DARK(): ResourceColor { return $r('app.color.DIV_YAO_BOTTOM_DARK') }
  static get YAO_BOTTOM_LIGHT(): ResourceColor { return $r('app.color.DIV_YAO_BOTTOM_LIGHT') }
  static get YAO_TOP_DARK(): ResourceColor { return $r('app.color.DIV_YAO_TOP_DARK') }
  static get YAO_TOP_LIGHT(): ResourceColor { return $r('app.color.DIV_YAO_TOP_LIGHT') }
}
