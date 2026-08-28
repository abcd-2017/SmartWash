// 清氧设计系统 — 鸿蒙端设计常量
// 参考 Android 端 Color.kt + AppDesign.kt

// ========== 颜色常量 ==========

/**
 * 清氧设计系统颜色常量
 * 颜色值使用 ARGB 格式（0xAARRGGBB）
 */
export class DesignColor {
  // 主色 — 自然绿
  static readonly PRIMARY: number = 0xFF2D9B6A
  static readonly ON_PRIMARY: number = 0xFFFFFFFF
  static readonly PRIMARY_CONTAINER: number = 0xFFE8F6EF
  static readonly ON_PRIMARY_CONTAINER: number = 0xFF0B3625

  // 主色变体
  static readonly PRIMARY_LIGHT: number = 0xFFF0FAF5 // 超浅绿，选中态背景
  static readonly PRIMARY_DARK: number = 0xFF1E8C5C // 深绿，按下态

  // 次要色
  static readonly SECONDARY: number = 0xFF8E8E93
  static readonly ON_SECONDARY: number = 0xFFFFFFFF
  static readonly SECONDARY_CONTAINER: number = 0xFFF2F2F7
  static readonly ON_SECONDARY_CONTAINER: number = 0xFF1C1C1E

  // 背景 & 表面
  static readonly BACKGROUND: number = 0xFFFAFAF8 // 微暖米白页面底色
  static readonly ON_BACKGROUND: number = 0xFF1C1C1E // iOS 系统黑
  static readonly SURFACE: number = 0xFFFFFFFF // 纯白卡片
  static readonly ON_SURFACE: number = 0xFF1C1C1E
  static readonly SURFACE_VARIANT: number = 0xFFF2F2F7
  static readonly ON_SURFACE_VARIANT: number = 0xFF8E8E93

  // 边框 & 辅助
  static readonly OUTLINE: number = 0xFFE5E7EB
  static readonly OUTLINE_VARIANT: number = 0xFFF2F2F7

  // 错误
  static readonly ERROR: number = 0xFFFF3B30
  static readonly ON_ERROR: number = 0xFFFFFFFF
  static readonly ERROR_CONTAINER: number = 0xFFFFF1F0
  static readonly ON_ERROR_CONTAINER: number = 0xFF991B1B
  static readonly ERROR_LIGHT: number = 0xFFFFB4AB // 输入框错误态

  // 警告
  static readonly WARNING: number = 0xFFFF9500
  static readonly ON_WARNING: number = 0xFFFFFFFF
  static readonly WARNING_CONTAINER: number = 0xFFFFF7ED
  static readonly ON_WARNING_CONTAINER: number = 0xFF92400E

  // 成功
  static readonly SUCCESS: number = 0xFF34C759
  static readonly ON_SUCCESS: number = 0xFFFFFFFF
  static readonly SUCCESS_CONTAINER: number = 0xFFF0FAF5
  static readonly ON_SUCCESS_CONTAINER: number = 0xFF065F46

  // 文字色阶
  static readonly TEXT_PRIMARY: number = 0xFF1C1C1E
  static readonly TEXT_SECONDARY: number = 0xFF8E8E93
  static readonly TEXT_TERTIARY: number = 0xFFC7C7CC

  // 分隔线
  static readonly DIVIDER: number = 0xFFF2F2F7

  // ========== 认证页渐变色 ==========
  static readonly AUTH_GRADIENT_TOP: number = 0xFF1A9E6E
  static readonly AUTH_GRADIENT_BOTTOM: number = 0xFF0B5C3A

  // ========== 毛玻璃效果色 ==========
  static readonly GLASS_BG_SUBTLE: number = 0x1FFFFFFF // 极淡底色
  static readonly GLASS_BG: number = 0x26FFFFFF // 标准底色
  static readonly GLASS_BORDER_SUBTLE: number = 0x2EFFFFFF // 淡边框
  static readonly GLASS_BORDER: number = 0x33FFFFFF // 标准边框
  static readonly GLASS_INPUT: number = 0x4DFFFFFF // 输入框/按钮底
  static readonly GLASS_TEXT_SECONDARY: number = 0x66FFFFFF // 次要文字
  static readonly GLASS_TEXT_HINT: number = 0x99FFFFFF // 提示文字
  static readonly GLASS_TEXT_DISABLED: number = 0xB3FFFFFF // 禁用文字
  static readonly GLASS_TEXT_ACTIVE: number = 0xCCFFFFFF // 激活文字

  // ========== 第三方品牌色 ==========
  static readonly WECHAT_GREEN: number = 0xFF07C160
  static readonly ALIPAY_BLUE: number = 0xFF1677FF

  // ========== 服务类型色彩 ==========
  static readonly SERVICE_WASH: number = 0xFF2D9B6A       // 标准洗 — 自然绿
  static readonly SERVICE_WASH_LIGHT: number = 0xFFE8F6EF
  static readonly SERVICE_DRY: number = 0xFF7C3AED        // 干洗 — 优雅紫
  static readonly SERVICE_DRY_LIGHT: number = 0xFFF3EFFE
  static readonly SERVICE_PRESS: number = 0xFFE07B00      // 熨烫 — 暖橙
  static readonly SERVICE_PRESS_LIGHT: number = 0xFFFFF7ED
  static readonly SERVICE_LUXURY: number = 0xFF0EA5E9     // 奢护 — 清澈蓝
  static readonly SERVICE_LUXURY_LIGHT: number = 0xFFEFF6FF
  static readonly SERVICE_SHOES: number = 0xFFDC2626      // 洗鞋 — 活力红
  static readonly SERVICE_SHOES_LIGHT: number = 0xFFFEF2F2
}

// ========== 设计 Token ==========

/**
 * 清氧设计系统尺寸常量
 * 单位：vp (虚拟像素)
 */
export class DesignDimens {
  static readonly PAGE_PADDING: number = 20
  static readonly CARD_PADDING: number = 16
  static readonly CARD_SPACING: number = 12
  static readonly SECTION_SPACING: number = 24
  static readonly CARD_RADIUS: number = 20
  static readonly SMALL_CARD_RADIUS: number = 16
  static readonly BUTTON_RADIUS: number = 14
  static readonly INPUT_RADIUS: number = 14
  static readonly ICON_CONTAINER_RADIUS: number = 12
}

// ========== 海拔层级 (Elevation Levels) ==========

/**
 * 清氧设计系统海拔层级常量
 * 用于 shadow 的 radius 值（vp）
 */
export class DesignElevation {
  static readonly LEVEL_0: number = 0   // 平面（背景）
  static readonly LEVEL_1: number = 4   // 轻微浮起（普通卡片）
  static readonly LEVEL_2: number = 8   // 中度浮起（重要卡片）
  static readonly LEVEL_3: number = 12  // 高度浮起（交互卡片）
  static readonly LEVEL_4: number = 20  // 最高（弹窗/Sheet）
}

// ========== 卡片圆角变体 ==========

/**
 * 清氧设计系统卡片圆角常量
 * 单位：vp
 */
export class DesignCardRadius {
  static readonly SMALL: number = 12
  static readonly MEDIUM: number = 16
  static readonly LARGE: number = 20
  static readonly XL: number = 24
}

// ========== 字体大小常量 ==========

/**
 * 清氧设计系统字体大小常量
 * 单位：fp (字体像素)
 */
export class DesignFontSize {
  static readonly PAGE_TITLE: number = 28 // Bold
  static readonly SECTION_TITLE: number = 18 // SemiBold
  static readonly CARD_TITLE: number = 16 // Medium
  static readonly BODY: number = 15 // Normal
  static readonly CAPTION: number = 13
}

// ========== 字体粗细常量 ==========

/**
 * 清氧设计系统字体粗细常量
 */
export class DesignFontWeight {
  static readonly BOLD: number = 700
  static readonly SEMI_BOLD: number = 600
  static readonly MEDIUM: number = 500
  static readonly NORMAL: number = 400
}
