// 清氧设计系统 — 鸿蒙端设计常量
// 基准：Android common/ui/theme/Color.kt + AppDesign.kt + Type.kt（DESIGN-SPEC v0.4，2026-10-24 定稿）
// 2026-10-29 全量对齐安卓 v0.4：字阶收敛 8 档、background/surfaceVariant 互换、
// outline/textTertiary/error/danger/success 取值、图标容器绿色系改 #F0FBF4。
// 决策 D1（已更新）：应用深浅色跟随系统——颜色全部经 base/dark 资源限定符（color.json）
// 自动切换，UI 侧用 $r('app.color.XXX') / resColor('XXX') 读取，不再手动回写令牌。
// 认证页品牌渐变深浅同款（对齐 Android LoginPage.kt:89-94）。

import { curves } from '@kit.ArkUI'

// ========== 颜色常量 ==========

/**
 * 将 ARGB 数值色（0xAARRGGBB）转为 ArkUI 通用资源色可接受的 rgba() 字符串。
 * 使用场景：部分 SDK API（如 promptAction.showDialog 的按钮 color）仅接受 string | Resource，
 * 不接受 number 色值，需经此函数转换后传入。
 */
export function toColorString(color: number): string {
  const alpha = ((color >>> 24) & 0xFF) / 255
  const red = (color >>> 16) & 0xFF
  const green = (color >>> 8) & 0xFF
  const blue = color & 0xFF
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

// ========== 设计 Token ==========

/**
 * 清氧设计系统尺寸常量（对齐 Android AppDimens）
 * 单位：vp (虚拟像素)
 */
export class DesignDimens {
  // 页面边距（规范 §2.3）
  static readonly PAGE_PADDING: number = 24
  // 卡片体系
  static readonly CARD_PADDING: number = 20
  static readonly CARD_SPACING: number = 16
  static readonly SECTION_SPACING: number = 32
  // 圆角 token（规范 §2.3 五档：16 卡 / 20 大容器 / 14 按钮·输入 / 12 图标容器 / 10 小容器）
  static readonly RADIUS_SMALL: number = 10 // 小图标容器、徽标
  static readonly RADIUS_MD: number = 12    // 图标容器、内嵌套块
  static readonly CARD_RADIUS: number = 16  // 卡片（唯一值，= Android radiusLg；Hero 卡同档，见 U-110）
  static readonly RADIUS_XL: number = 20    // 大容器（= Android radiusXl；Hero 卡不用此档，收敛到 CARD_RADIUS 16）
  static readonly RADIUS_FULL: number = 999 // 头像、胶囊
  // 按钮 / 输入
  static readonly BUTTON_RADIUS: number = 14
  static readonly INPUT_RADIUS: number = 14
  static readonly ICON_CONTAINER_RADIUS: number = 12
  // 触控热区（规范 §2.3：可点元素 ≥44dp；返回箭头豁免入容器仍强制 48dp）
  static readonly TOUCH_TARGET_MIN: number = 44
  static readonly BACK_HIT_AREA: number = 48
  // 间距 token（8pt 网格）
  static readonly SPACE_XXS: number = 4
  static readonly SPACE_XS: number = 8
  static readonly SPACE_SM: number = 12
  static readonly SPACE_MD: number = 16
  static readonly SPACE_LG: number = 20
  static readonly SPACE_XL: number = 24
  static readonly SPACE_XXL: number = 32
}

// ========== 海拔层级 (Elevation Levels) ==========

/**
 * 清氧设计系统海拔层级常量（对齐 Android AppElevation：0/1/3/6/12 + 浮层档 10）
 * 用于 shadow 的 radius 值（vp）
 */
export class DesignElevation {
  static readonly LEVEL_0: number = 0   // 平面（背景）
  static readonly LEVEL_1: number = 1   // 轻微浮起（普通卡片）
  static readonly LEVEL_2: number = 3   // 中度浮起（重要卡片）
  static readonly LEVEL_3: number = 6   // 高度浮起（交互卡片）
  static readonly LEVEL_4: number = 12  // 最高（弹窗/Sheet）
  static readonly FLOAT_LAYER: number = 10 // 浮层（弹窗/对话框）— 规范 §2.4 浮层档

  // 阴影几何（规范 §2.4 CSS 值的 ArkUI 直译：卡片 0 2px 12px / 浮层 0 10px 24px）
  static readonly CARD_SHADOW_RADIUS: number = 12
  static readonly CARD_SHADOW_OFFSET: number = 2
  static readonly FLOAT_SHADOW_RADIUS: number = 24
  static readonly FLOAT_SHADOW_OFFSET: number = 10
}

// ========== 动效词汇（对齐安卓 common/utils/AnimationUtils.kt） ==========

/**
 * 动效 timing — 安卓弹簧参数（dampingRatio/stiffness）在 ArkUI 的等价物是
 * springMotion(response, dampingFraction)。弹簧从当前值起动画、天然可中断，
 * 不用固定时长 tween 描述位移动画（规范 §7）。
 */
export class MotionTiming {
  /** 标准弹簧 — 对齐安卓 defaultSpring(1.0, 350)：临界阻尼无回弹（curves 工厂返回 ICurve） */
  static readonly SPRING: ICurve = curves.springMotion(0.4, 1)

  /**
   * 弹窗入场 — 对齐安卓 dialogEnterMotion：scale 0.92→1 + fade 0→1 一次性进场。
   * 退出动画受弹窗窗口机制限制（窗口由系统直接移除），两端一致不做。
   */
  static readonly DIALOG_ENTER: AnimateParam = { duration: 220, curve: curves.springMotion(0.4, 1) }

  /** 内容进场（空态等状态到达时刻） — 同弹簧 */
  static readonly ENTER: AnimateParam = { duration: 220, curve: curves.springMotion(0.4, 1) }
}

// ========== 字体大小常量 ==========

/**
 * 清氧设计系统字阶（DESIGN-SPEC v0.4：收敛为 8 档，weight 仅 400/500/600/700）
 * 对齐 Android AppTextStyles 的语义别名。
 * **8 档字阶内不存在 15/17/20/21/24 档**；15/13 仅为按钮组件级规格（DESIGN.md §3.3，
 * 与 Android 同口径：按钮文字不在 8 档内），见 BUTTON / BUTTON_TEXT。
 * 单位：fp (字体像素)
 */
export class DesignFontSize {
  /** display — 数据大字：余额、应付金额、取件码（Bold / tabular numbers） */
  static readonly DATA_LARGE: number = 36
  /** headline — 保留档（根页大标题等超大标题场景） */
  static readonly HEADLINE: number = 28
  /** titleLarge — 状态大字（hero 锚点）/ 根页大标题（SemiBold） */
  static readonly STATUS_LARGE: number = 22
  static readonly ROOT_TITLE: number = 22
  /** title — 金额中字（+tabular）/ 内页标题 / 卡片标题 / 区块标题（SemiBold） */
  static readonly AMOUNT_MEDIUM: number = 18
  static readonly PAGE_HEADER: number = 18
  static readonly CARD_TITLE: number = 18
  static readonly SECTION_TITLE: number = 18
  /** bodyLarge — 正文 */
  static readonly BODY: number = 16
  /** body — 列表行主文案（§3.4 特许，配 FontWeight.MEDIUM） */
  static readonly BODY_SMALL: number = 14
  /** caption — 辅助 / 角标 / labelSmall */
  static readonly CAPTION: number = 12
  /** button — 按钮文字（DESIGN.md §3.3 组件级规格：15sp/600/ls1sp，不在 8 档字阶内） */
  static readonly BUTTON: number = 15
  /** button-text — 文字按钮（DESIGN.md §3.3：13sp/600） */
  static readonly BUTTON_TEXT: number = 13
  // 按钮文字字距已废（U-116：DESIGN-SPEC v0.4 字阶不再设字距，对齐安卓 AppButton）
}

// ========== 字体粗细常量 ==========

/**
 * 清氧设计系统字体粗细常量（对齐 Android：只允许 400/500/600/700）
 */
export class DesignFontWeight {
  static readonly BOLD: number = 700
  static readonly SEMI_BOLD: number = 600
  static readonly MEDIUM: number = 500
  static readonly NORMAL: number = 400
}

// ========== 字体特征常量 ==========

/**
 * 清氧设计系统字体特征常量（DESIGN.md「数字等宽规则 tnum Rule」）。
 * 金额、数量、取件码、订单号一律 tabular numbers——数字变化时左右跳即违规。
 * ArkUI `Text.fontFeature(value)` 自 API 12 起支持（SDK 5.0.5(17) 满足），取值同 OpenType feature tag。
 */
export class DesignFontFeature {
  /** tabular numbers — 数字等宽 */
  static readonly TNUM: string = 'tnum'
}
