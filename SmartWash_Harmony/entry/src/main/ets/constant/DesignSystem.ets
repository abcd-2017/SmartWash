// 清氧设计系统 — 鸿蒙端设计常量
// 参考 Android 端 Color.kt + AppDesign.kt + Type.kt（差异分析 2026-10-27 对齐后与安卓逐项一致）
// 决策 D1：鸿蒙当前固定亮色模式（EntryAbility setColorMode COLOR_MODE_LIGHT），
// 暗色主题整套 Dark* 令牌待后续专项补齐后再开放跟随系统。

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
  static readonly BACKGROUND: number = 0xFFEFF1EE // 微绿调米白页面底色（规范 §2.1）
  static readonly ON_BACKGROUND: number = 0xFF1C1C1E
  static readonly SURFACE: number = 0xFFFFFFFF // 纯白卡片
  static readonly ON_SURFACE: number = 0xFF1C1C1E
  static readonly SURFACE_VARIANT: number = 0xFFF5F6F3 // 卡内次级块（输入框底、嵌套卡）
  static readonly ON_SURFACE_VARIANT: number = 0xFF6B6E73
  static readonly GROUP_BACKGROUND: number = 0xFFE7EAE4 // 分组容器底（比页面底再深一档）
  static readonly HAIRLINE: number = 0xFFEFF1EE // 容器内部行分隔线底色

  // 边框 & 辅助
  static readonly OUTLINE: number = 0xFFE3E7E2 // 卡片描边（1px）
  static readonly OUTLINE_VARIANT: number = 0xFFF5F6F3

  // 错误
  static readonly ERROR: number = 0xFFFF3B30
  static readonly ON_ERROR: number = 0xFFFFFFFF
  static readonly ERROR_CONTAINER: number = 0xFFFFF1F0
  static readonly ON_ERROR_CONTAINER: number = 0xFF991B1B
  static readonly ERROR_LIGHT: number = 0xFFFFB4AB // 输入框错误态

  // 警告 — 规范语义橙（iOS 系统橙 on 白对比度不足，安卓已废弃）
  static readonly WARNING: number = 0xFFD98A26
  static readonly ON_WARNING: number = 0xFFFFFFFF
  static readonly WARNING_CONTAINER: number = 0xFFFFF7ED
  static readonly ON_WARNING_CONTAINER: number = 0xFF92400E

  // 状态语义（进行中 / 危险，规范 §6.5）
  static readonly ONGOING: number = 0xFFD98A26
  static readonly ONGOING_INK: number = 0xFF8F5B0C
  static readonly DANGER: number = 0xFFC9526B
  static readonly DANGER_INK: number = 0xFFA8435A

  // 成功
  static readonly SUCCESS: number = 0xFF34C759
  static readonly ON_SUCCESS: number = 0xFFFFFFFF
  static readonly SUCCESS_CONTAINER: number = 0xFFF0FAF5
  static readonly ON_SUCCESS_CONTAINER: number = 0xFF065F46

  // 文字色阶
  static readonly TEXT_PRIMARY: number = 0xFF1C1C1E
  static readonly TEXT_SECONDARY: number = 0xFF6B6E73 // 次文本（对比度更高）
  static readonly TEXT_TERTIARY: number = 0xFF9A9DA3 // 弱文本（对比度更高）

  // 分隔线
  static readonly DIVIDER: number = 0xFFEFF1EE

  // 水意象（次要强调、进度、水滴）
  static readonly WATER: number = 0xFF4A9FD4
  static readonly WATER_SOFT: number = 0xFFDCEFF7 // 水色浅底容器

  // 图标容器六色浅底（规范 §2.1）
  static readonly ICON_CONTAINER_GREEN: number = 0xFFE8F6EF // 洗护/主
  static readonly ICON_CONTAINER_BLUE: number = 0xFFEEF2FB // 物流/寄件
  static readonly ICON_CONTAINER_ORANGE: number = 0xFFFDF3E7 // 取件/待办
  static readonly ICON_CONTAINER_PINK: number = 0xFFFDEEF0 // 异常/取消
  static readonly ICON_CONTAINER_TEAL: number = 0xFFF0FBF4 // 账户/安全
  static readonly ICON_CONTAINER_PURPLE: number = 0xFFF4F0FB // 观象台/AI

  // 图标前景六色（规范 §2.1 六色深色版）
  static readonly ICON_FOREGROUND_GREEN: number = 0xFF2D9B6A
  static readonly ICON_FOREGROUND_BLUE: number = 0xFF4A67C9
  static readonly ICON_FOREGROUND_ORANGE: number = 0xFFD98A26
  static readonly ICON_FOREGROUND_PINK: number = 0xFFC9526B
  static readonly ICON_FOREGROUND_TEAL: number = 0xFF3F9D78
  static readonly ICON_FOREGROUND_PURPLE: number = 0xFF7C3AED

  // ========== 认证页渐变色（160deg 三段 #1A9E6E → #0D7A4A@50% → #0B5C3A） ==========
  static readonly AUTH_GRADIENT_TOP: number = 0xFF1A9E6E
  static readonly AUTH_GRADIENT_MID: number = 0xFF0D7A4A
  static readonly AUTH_GRADIENT_BOTTOM: number = 0xFF0B5C3A

  // ========== 毛玻璃效果色 ==========
  static readonly GLASS_BG_SUBTLE: number = 0x1AFFFFFF // 极淡底色（白 10%）
  static readonly GLASS_BG: number = 0x26FFFFFF // 标准底色（白 15%）
  static readonly GLASS_BORDER_SUBTLE: number = 0x2EFFFFFF // 淡边框（白 18%）
  static readonly GLASS_BORDER: number = 0x33FFFFFF // 标准边框（白 20%）
  static readonly GLASS_INPUT: number = 0x4DFFFFFF // 输入框/按钮底（白 30%）
  static readonly GLASS_TEXT_SECONDARY: number = 0x66FFFFFF // 次要文字（白 40%）
  static readonly GLASS_TEXT_HINT: number = 0x99FFFFFF // 提示文字（白 60%）
  static readonly GLASS_TEXT_DISABLED: number = 0xB3FFFFFF // 禁用文字（白 70%）
  static readonly GLASS_TEXT_ACTIVE: number = 0xCCFFFFFF // 激活文字（白 80%）

  // ========== 第三方品牌色 ==========
  static readonly WECHAT_GREEN: number = 0xFF07C160
  static readonly ALIPAY_BLUE: number = 0xFF1677FF
  // 品牌色 10% 透明底，用于支付渠道图标容器背景（评审 #18：收敛页面硬编码色值）
  static readonly WECHAT_GREEN_LIGHT: number = 0x1A07C160
  static readonly ALIPAY_BLUE_LIGHT: number = 0x1A1677FF

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
  // 圆角 token（规范 §2.3）：CARD_RADIUS 16 为卡片唯一值，RADIUS_XL 20 仅用于 hero/大容器
  static readonly RADIUS_XS: number = 4     // 标签、小角标
  static readonly RADIUS_SM: number = 8     // 输入框、小按钮
  static readonly RADIUS_SMALL: number = 10 // 小图标容器、徽标
  static readonly RADIUS_MD: number = 12    // 分组背景、图标容器、内嵌套块
  static readonly CARD_RADIUS: number = 16  // 卡片（唯一值，= Android radiusLg）
  static readonly RADIUS_XL: number = 20    // hero、大容器（= Android radiusXl）
  static readonly RADIUS_FULL: number = 999 // 头像、胶囊
  // 按钮 / 输入
  static readonly BUTTON_RADIUS: number = 14
  static readonly INPUT_RADIUS: number = 14
  static readonly ICON_CONTAINER_RADIUS: number = 12
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
}

// ========== 字体大小常量 ==========

/**
 * 清氧设计系统语义字阶（对齐 Android AppTextStyles 七档 + 正文辅助档）
 * 单位：fp (字体像素)
 */
export class DesignFontSize {
  /** 数据大字 — 余额、应付金额、取件码（Bold / tabular numbers） */
  static readonly DATA_LARGE: number = 36
  /** 状态大字 — hero 订单状态锚点字（Bold），每屏唯一锚点 */
  static readonly STATUS_LARGE: number = 21
  /** 金额中字 — 列表内价格、券面额（Bold / tabular numbers） */
  static readonly AMOUNT_MEDIUM: number = 20
  /** 根页大标题 — 首页/服务/我的（Bold） */
  static readonly ROOT_TITLE: number = 24
  /** 二级页页头（SemiBold） */
  static readonly PAGE_HEADER: number = 20
  /** 区块标题 — 页面内分区标题（SemiBold） */
  static readonly SECTION_TITLE: number = 17
  /** 卡片标题（Medium） */
  static readonly CARD_TITLE: number = 16
  /** 正文 */
  static readonly BODY: number = 15
  /** 辅助说明 */
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
