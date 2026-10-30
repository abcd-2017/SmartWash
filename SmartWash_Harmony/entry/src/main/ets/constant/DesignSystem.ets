// 清氧设计系统 — 鸿蒙端设计常量
// 基准：Android common/ui/theme/Color.kt + AppDesign.kt + Type.kt（DESIGN-SPEC v0.4，2026-10-24 定稿）
// 2026-10-29 全量对齐安卓 v0.4：字阶收敛 8 档、background/surfaceVariant 互换、
// outline/textTertiary/error/danger/success 取值、图标容器绿色系改 #F0FBF4。
// 决策 D1：鸿蒙当前固定亮色模式（EntryAbility setColorMode COLOR_MODE_LIGHT），
// 暗色主题整套 Dark* 令牌待后续专项补齐后再开放跟随系统。

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

/**
 * 清氧设计系统颜色常量（对齐 Android Color.kt）
 * 颜色值使用 ARGB 格式（0xAARRGGBB）
 */
export class DesignColor {
  // 主色 — 自然绿
  static readonly PRIMARY: number = 0xFF2D9B6A
  static readonly ON_PRIMARY: number = 0xFFFFFFFF
  static readonly PRIMARY_CONTAINER: number = 0xFFF0FBF4
  static readonly ON_PRIMARY_CONTAINER: number = 0xFF17322B

  // 主色变体
  static readonly PRIMARY_LIGHT: number = 0xFFF0FBF4 // 超浅绿，选中态背景（= primaryContainer）
  static readonly PRIMARY_DARK: number = 0xFF1E8C5C  // 深绿，按下态

  // 次要色
  static readonly SECONDARY: number = 0xFF8E8E93
  static readonly ON_SECONDARY: number = 0xFFFFFFFF
  static readonly SECONDARY_CONTAINER: number = 0xFFF2F2F7
  static readonly ON_SECONDARY_CONTAINER: number = 0xFF1C1C1E

  // 第三色
  static readonly TERTIARY: number = 0xFF6B7280
  static readonly ON_TERTIARY: number = 0xFFFFFFFF
  static readonly TERTIARY_CONTAINER: number = 0xFFF2F2F7
  static readonly ON_TERTIARY_CONTAINER: number = 0xFF1C1C1E

  // 背景 & 表面
  // v0.4：background 与 surfaceVariant 的取值相较 2026-10-06 版**互换**了，以安卓 Color.kt 为准
  static readonly BACKGROUND: number = 0xFFF5F6F3        // 页面底色
  static readonly ON_BACKGROUND: number = 0xFF1C1C1E
  static readonly SURFACE: number = 0xFFFFFFFF           // 纯白卡片
  static readonly ON_SURFACE: number = 0xFF1C1C1E
  static readonly SURFACE_VARIANT: number = 0xFFEFF1EE   // 卡内次级块（输入框底、嵌套卡）
  static readonly ON_SURFACE_VARIANT: number = 0xFF6B6E73
  static readonly GROUP_BACKGROUND: number = 0xFFE7EAE4  // 分组容器底（比页面底再深一档）
  static readonly HAIRLINE: number = 0xFFEFF1EE          // 容器内部行分隔线底色

  // 边框 & 辅助
  static readonly OUTLINE: number = 0xFFE6E9E6           // 卡片描边（1px）
  static readonly OUTLINE_VARIANT: number = 0xFFEFF1EE

  // 错误（v0.4 收敛为单一 error 角色 #A8435A，弃 iOS 红 #FF3B30）
  static readonly ERROR: number = 0xFFA8435A
  static readonly ON_ERROR: number = 0xFFFFFFFF
  static readonly ERROR_CONTAINER: number = 0xFFF7E9EC
  static readonly ON_ERROR_CONTAINER: number = 0xFFA8435A

  // 警告 — 规范语义橙
  static readonly WARNING: number = 0xFFD98A26
  static readonly ON_WARNING: number = 0xFFFFFFFF
  static readonly WARNING_CONTAINER: number = 0xFFFDF3E7
  static readonly ON_WARNING_CONTAINER: number = 0xFF92400E

  // 状态语义（进行中 / 危险）
  // v0.4：ongoingInk 与 ongoing 合并，不再单列小字变体
  static readonly ONGOING: number = 0xFFD98A26
  static readonly ONGOING_INK: number = 0xFFD98A26
  static readonly DANGER: number = 0xFFA8435A
  static readonly DANGER_INK: number = 0xFFA8435A

  // 成功（v0.4 收敛：success 与 done 合并为 #2D9B6A）
  static readonly SUCCESS: number = 0xFF2D9B6A
  static readonly ON_SUCCESS: number = 0xFFFFFFFF
  static readonly SUCCESS_CONTAINER: number = 0xFFF0FBF4
  static readonly ON_SUCCESS_CONTAINER: number = 0xFF17322B

  // 文字色阶
  static readonly TEXT_PRIMARY: number = 0xFF1C1C1E
  static readonly TEXT_SECONDARY: number = 0xFF6B6E73 // 次文本
  static readonly TEXT_TERTIARY: number = 0xFFA9AEA9  // 弱文本

  // 分隔线
  static readonly DIVIDER: number = 0xFFEFF1EE

  // 水意象（次要强调、进度、水滴）
  static readonly WATER: number = 0xFF4A9FD4
  static readonly WATER_SOFT: number = 0xFFDCEFF7     // 水色浅底容器

  // 图标容器六色浅底（规范 §2.1）
  // v0.4：绿与青同值 #F0FBF4（安卓 Color.kt 同名同值）
  static readonly ICON_CONTAINER_GREEN: number = 0xFFF0FBF4   // 洗护/主
  static readonly ICON_CONTAINER_BLUE: number = 0xFFEEF2FB    // 物流/寄件
  static readonly ICON_CONTAINER_ORANGE: number = 0xFFFDF3E7  // 取件/待办
  static readonly ICON_CONTAINER_PINK: number = 0xFFFDEEF0    // 异常/取消
  static readonly ICON_CONTAINER_TEAL: number = 0xFFF0FBF4    // 账户/安全
  static readonly ICON_CONTAINER_PURPLE: number = 0xFFF4F0FB  // 观象台/AI

  // 图标前景六色（规范 §2.1 六色深色版）
  static readonly ICON_FOREGROUND_GREEN: number = 0xFF2D9B6A
  static readonly ICON_FOREGROUND_BLUE: number = 0xFF4A67C9
  static readonly ICON_FOREGROUND_ORANGE: number = 0xFFD98A26
  static readonly ICON_FOREGROUND_PINK: number = 0xFFC9526B
  static readonly ICON_FOREGROUND_TEAL: number = 0xFF3F9D78
  static readonly ICON_FOREGROUND_PURPLE: number = 0xFF7C3AED

  // ========== 清氧设计系统 — 暗色主题（对齐 Android Color.kt Dark* 系） ==========

  static readonly DARK_PRIMARY: number = 0xFF5BC894
  static readonly DARK_ON_PRIMARY: number = 0xFF17322B
  static readonly DARK_PRIMARY_CONTAINER: number = 0xFF17322B
  static readonly DARK_ON_PRIMARY_CONTAINER: number = 0xFFF0FBF4
  static readonly DARK_PRIMARY_LIGHT: number = 0xFF17322B
  static readonly DARK_PRIMARY_DARK: number = 0xFF5BC894

  static readonly DARK_SECONDARY: number = 0xFFA9AEA9
  static readonly DARK_ON_SECONDARY: number = 0xFF141614
  static readonly DARK_SECONDARY_CONTAINER: number = 0xFF262A27
  static readonly DARK_ON_SECONDARY_CONTAINER: number = 0xFFE6E9E6

  static readonly DARK_TERTIARY: number = 0xFFA9AEA9
  static readonly DARK_ON_TERTIARY: number = 0xFF141614
  static readonly DARK_TERTIARY_CONTAINER: number = 0xFF262A27
  static readonly DARK_ON_TERTIARY_CONTAINER: number = 0xFFE6E9E6

  static readonly DARK_BACKGROUND: number = 0xFF141614
  static readonly DARK_ON_BACKGROUND: number = 0xFFE6E9E6
  static readonly DARK_SURFACE: number = 0xFF1D201E
  static readonly DARK_ON_SURFACE: number = 0xFFE6E9E6
  static readonly DARK_SURFACE_VARIANT: number = 0xFF262A27
  static readonly DARK_ON_SURFACE_VARIANT: number = 0xFFA9AEA9
  static readonly DARK_GROUP_BACKGROUND: number = 0xFF1A1E1B
  static readonly DARK_HAIRLINE: number = 0xFF262A27

  static readonly DARK_OUTLINE: number = 0xFF2C312D
  static readonly DARK_OUTLINE_VARIANT: number = 0xFF262A27

  static readonly DARK_ERROR: number = 0xFFE795A5
  static readonly DARK_ON_ERROR: number = 0xFF141614
  static readonly DARK_ERROR_CONTAINER: number = 0xFF3A1F28
  static readonly DARK_ON_ERROR_CONTAINER: number = 0xFFE795A5

  static readonly DARK_WARNING: number = 0xFFE5AC5C
  static readonly DARK_ON_WARNING: number = 0xFF141614
  static readonly DARK_WARNING_CONTAINER: number = 0xFF332917
  static readonly DARK_ON_WARNING_CONTAINER: number = 0xFFECB36A

  static readonly DARK_ONGOING: number = 0xFFE5AC5C
  static readonly DARK_ONGOING_INK: number = 0xFFECB36A
  static readonly DARK_DANGER: number = 0xFFE795A5
  static readonly DARK_DANGER_INK: number = 0xFFE795A5

  static readonly DARK_SUCCESS: number = 0xFF5BC894
  static readonly DARK_ON_SUCCESS: number = 0xFF141614
  static readonly DARK_SUCCESS_CONTAINER: number = 0xFF17322B
  static readonly DARK_ON_SUCCESS_CONTAINER: number = 0xFFF0FBF4

  static readonly DARK_TEXT_PRIMARY: number = 0xFFE6E9E6
  static readonly DARK_TEXT_SECONDARY: number = 0xFFA9AEA9
  static readonly DARK_TEXT_TERTIARY: number = 0xFF7E837D

  static readonly DARK_DIVIDER: number = 0xFF262A27

  static readonly DARK_WATER: number = 0xFF5FAEE0
  static readonly DARK_WATER_SOFT: number = 0xFF1C2F3B

  static readonly DARK_ICON_CONTAINER_GREEN: number = 0xFF1D3229
  static readonly DARK_ICON_CONTAINER_BLUE: number = 0xFF1F2637
  static readonly DARK_ICON_CONTAINER_ORANGE: number = 0xFF332917
  static readonly DARK_ICON_CONTAINER_PINK: number = 0xFF341E24
  static readonly DARK_ICON_CONTAINER_TEAL: number = 0xFF17322B
  static readonly DARK_ICON_CONTAINER_PURPLE: number = 0xFF272038

  static readonly DARK_ICON_FOREGROUND_GREEN: number = 0xFF5BC894
  static readonly DARK_ICON_FOREGROUND_BLUE: number = 0xFF85A3E8
  static readonly DARK_ICON_FOREGROUND_ORANGE: number = 0xFFE5AC5C
  static readonly DARK_ICON_FOREGROUND_PINK: number = 0xFFE58A9A
  static readonly DARK_ICON_FOREGROUND_TEAL: number = 0xFF57C9A4
  static readonly DARK_ICON_FOREGROUND_PURPLE: number = 0xFFB49CE8

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

  // ========== 服务类型色彩（页面自有语义，非令牌层） ==========
  static readonly SERVICE_WASH: number = 0xFF2D9B6A       // 标准洗 — 自然绿
  static readonly SERVICE_WASH_LIGHT: number = 0xFFF0FBF4
  static readonly SERVICE_DRY: number = 0xFF7C3AED        // 干洗 — 优雅紫
  static readonly SERVICE_DRY_LIGHT: number = 0xFFF3EFFE
  static readonly SERVICE_PRESS: number = 0xFFE07B00      // 熨烫 — 暖橙
  static readonly SERVICE_PRESS_LIGHT: number = 0xFFFFF7ED
  static readonly SERVICE_LUXURY: number = 0xFF0EA5E9     // 奢护 — 清澈蓝
  static readonly SERVICE_LUXURY_LIGHT: number = 0xFFEFF6FF
  static readonly SERVICE_SHOES: number = 0xFFDC2626      // 洗鞋 — 活力红
  static readonly SERVICE_SHOES_LIGHT: number = 0xFFFEF2F2

  // ========== 阴影色（规范 §2.4 全应用仅两档，替代组件内散落的写死色） ==========
  // 字符串类型：shadow.color 接受 ResourceColor，rgba() 直译设计稿 CSS 值
  static readonly SHADOW_COLOR_CARD: string = 'rgba(20, 40, 30, 0.05)'   // 卡片档
  static readonly SHADOW_COLOR_FLOAT: string = 'rgba(20, 40, 30, 0.10)'  // 浮层/弹窗档
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
  // 圆角 token（规范 §2.3 五档：16 卡 / 20 hero / 14 按钮·输入 / 12 图标容器 / 10 小容器）
  static readonly RADIUS_SMALL: number = 10 // 小图标容器、徽标
  static readonly RADIUS_MD: number = 12    // 图标容器、内嵌套块
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
 * **不存在 15/17/20/21/24 档。**
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
