// 清氧设计系统 — 鸿蒙端设计常量
// 基准：Android common/ui/theme/Color.kt + AppDesign.kt + Type.kt（DESIGN-SPEC v0.4，2026-10-24 定稿）
// 2026-10-29 全量对齐安卓 v0.4：字阶收敛 8 档、background/surfaceVariant 互换、
// outline/textTertiary/error/danger/success 取值、图标容器绿色系改 #F0FBF4。
// 决策 D1（已更新）：应用深浅色跟随系统（EntryAbility 不再强制 COLOR_MODE_LIGHT，
// syncDarkMode 写 AppStorage('darkMode') + applyDesignTheme 回写全部可变令牌；
// 栈内页面的刷新经 view/common/ThemeRefresh 主题重建宿主感知 darkMode）。
// 例外：认证页品牌渐变在 Android 端为「D1 品牌渐变页深浅色同款」实现（LoginPage.kt:89-94），
// 鸿蒙端此处已备 Dark 变体（语义对应清氧·夜绿系），接入 applyDesignTheme 后
// 两端在暗色下的认证页背景将不一致——是否回退同款待两端设计确认（见深色模式审计报告）。

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
  static PRIMARY: number = 0xFF2D9B6A
  static ON_PRIMARY: number = 0xFFFFFFFF
  static PRIMARY_CONTAINER: number = 0xFFF0FBF4
  static ON_PRIMARY_CONTAINER: number = 0xFF17322B

  // 主色变体
  static PRIMARY_LIGHT: number = 0xFFF0FBF4 // 超浅绿，图标容器/票券底（= primaryContainer）
  // 浅绿选中底（DESIGN.md primary-soft）——与 PRIMARY_LIGHT 是两个绿，勿合并
  static PRIMARY_SOFT: number = 0xFFE8F6EF
  static ON_PRIMARY_SOFT: number = 0xFF0B3625 // 浅绿选中底上的深绿字
  static PRIMARY_DARK: number = 0xFF1E8C5C  // 深绿，按下态

  // 次要色
  static SECONDARY: number = 0xFF8E8E93
  static ON_SECONDARY: number = 0xFFFFFFFF
  static SECONDARY_CONTAINER: number = 0xFFF2F2F7
  static ON_SECONDARY_CONTAINER: number = 0xFF1C1C1E

  // 第三色
  static TERTIARY: number = 0xFF6B7280
  static ON_TERTIARY: number = 0xFFFFFFFF
  static TERTIARY_CONTAINER: number = 0xFFF2F2F7
  static ON_TERTIARY_CONTAINER: number = 0xFF1C1C1E

  // 背景 & 表面
  // v0.4：background 与 surfaceVariant 的取值相较 2026-10-06 版**互换**了，以安卓 Color.kt 为准
  static BACKGROUND: number = 0xFFF5F6F3        // 页面底色
  static ON_BACKGROUND: number = 0xFF1C1C1E
  static SURFACE: number = 0xFFFFFFFF           // 纯白卡片
  static ON_SURFACE: number = 0xFF1C1C1E
  static SURFACE_VARIANT: number = 0xFFEFF1EE   // 卡内次级块（输入框底、嵌套卡）
  static ON_SURFACE_VARIANT: number = 0xFF6B6E73
  static GROUP_BACKGROUND: number = 0xFFE7EAE4  // 分组容器底（比页面底再深一档）
  static HAIRLINE: number = 0xFFEFF1EE          // 容器内部行分隔线底色

  // 边框 & 辅助
  static OUTLINE: number = 0xFFE6E9E6           // 卡片描边（1px）
  static OUTLINE_VARIANT: number = 0xFFEFF1EE

  // 错误（v0.4 收敛为单一 error 角色 #A8435A，弃 iOS 红 #FF3B30）
  static ERROR: number = 0xFFA8435A
  static ON_ERROR: number = 0xFFFFFFFF
  static ERROR_CONTAINER: number = 0xFFF7E9EC
  static ON_ERROR_CONTAINER: number = 0xFFA8435A

  // 警告 — 规范语义橙
  static WARNING: number = 0xFFD98A26
  static ON_WARNING: number = 0xFFFFFFFF
  static WARNING_CONTAINER: number = 0xFFFDF3E7
  static ON_WARNING_CONTAINER: number = 0xFF92400E

  // 状态语义（进行中 / 危险）
  // v0.4：ongoingInk 与 ongoing 合并，不再单列小字变体
  static ONGOING: number = 0xFFD98A26
  static ONGOING_INK: number = 0xFFD98A26
  static DANGER: number = 0xFFA8435A
  static DANGER_INK: number = 0xFFA8435A

  // 成功（v0.4 收敛：success 与 done 合并为 #2D9B6A）
  static SUCCESS: number = 0xFF2D9B6A
  static ON_SUCCESS: number = 0xFFFFFFFF
  static SUCCESS_CONTAINER: number = 0xFFF0FBF4
  static ON_SUCCESS_CONTAINER: number = 0xFF17322B

  // 文字色阶
  static TEXT_PRIMARY: number = 0xFF1C1C1E
  static TEXT_SECONDARY: number = 0xFF6B6E73 // 次文本
  static TEXT_TERTIARY: number = 0xFFA9AEA9  // 弱文本

  // 分隔线
  static DIVIDER: number = 0xFFEFF1EE

  // 水意象（次要强调、进度、水滴）
  static WATER: number = 0xFF4A9FD4
  static WATER_SOFT: number = 0xFFDCEFF7     // 水色浅底容器

  // 图标容器六色浅底（规范 §2.1）
  // v0.4：绿与青同值 #F0FBF4（安卓 Color.kt 同名同值）
  static ICON_CONTAINER_GREEN: number = 0xFFF0FBF4   // 洗护/主
  static ICON_CONTAINER_BLUE: number = 0xFFEEF2FB    // 物流/寄件
  static ICON_CONTAINER_ORANGE: number = 0xFFFDF3E7  // 取件/待办
  static ICON_CONTAINER_PINK: number = 0xFFFDEEF0    // 异常/取消
  static ICON_CONTAINER_TEAL: number = 0xFFF0FBF4    // 账户/安全
  static ICON_CONTAINER_PURPLE: number = 0xFFF4F0FB  // 观象台/AI

  // 图标前景六色（规范 §2.1 六色深色版）
  static ICON_FOREGROUND_GREEN: number = 0xFF2D9B6A
  static ICON_FOREGROUND_BLUE: number = 0xFF4A67C9
  static ICON_FOREGROUND_ORANGE: number = 0xFFD98A26
  static ICON_FOREGROUND_PINK: number = 0xFFC9526B
  static ICON_FOREGROUND_TEAL: number = 0xFF3F9D78
  static ICON_FOREGROUND_PURPLE: number = 0xFF7C3AED

  // ========== 清氧设计系统 — 暗色主题（对齐 Android Color.kt Dark* 系） ==========

  static readonly DARK_PRIMARY: number = 0xFF5BC894
  static readonly DARK_ON_PRIMARY: number = 0xFF17322B
  static readonly DARK_PRIMARY_CONTAINER: number = 0xFF17322B
  static readonly DARK_ON_PRIMARY_CONTAINER: number = 0xFFF0FBF4
  static readonly DARK_PRIMARY_LIGHT: number = 0xFF17322B
  static readonly DARK_PRIMARY_SOFT: number = 0xFF1D3229   // 深色选中底（DESIGN.md dark-primary-soft）
  static readonly DARK_ON_PRIMARY_SOFT: number = 0xFFB9E8CF // 深色选中底上的亮绿字
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
  static AUTH_GRADIENT_TOP: number = 0xFF1A9E6E
  static AUTH_GRADIENT_MID: number = 0xFF0D7A4A
  static AUTH_GRADIENT_BOTTOM: number = 0xFF0B5C3A

  // 认证页渐变暗色变体（清氧·夜语义）：白字压底可读性优先，整体下沉到暗色绿系——
  // 顶段取 DARK_PRIMARY_SOFT 邻域（暗色绿阶最亮点），底段贴近 DARK_BACKGROUND 的绿调收尾；
  // 三段绿相保持 155 附近与亮色渐变一致，仅降亮度（暗色大面积高饱和绿刺眼）。
  // ⚠️ 跨端记录：Android 端认证页为 D1「深浅色同款」决策（LoginPage.kt:88-94），未实现暗色渐变；
  //    鸿蒙端接入后暗色下两端认证页背景不一致，是否回退同款待两端设计确认。
  static readonly DARK_AUTH_GRADIENT_TOP: number = 0xFF1D3229
  static readonly DARK_AUTH_GRADIENT_MID: number = 0xFF182A22
  static readonly DARK_AUTH_GRADIENT_BOTTOM: number = 0xFF12211B

  // ========== 毛玻璃效果色 ==========
  // 暗色变体（面/边/输入三类）：玻璃面压在暗色底上白透明度需提约一档才能维持「卡与底」的分层可见性
  // （白 10% 在亮绿渐变上清晰可辨，在深绿渐变上几乎隐形）；文字类白透明在暗底上对比天然更高，同值即可，不设 DARK 变体。
  static GLASS_BG_SUBTLE: number = 0x1AFFFFFF // 极淡底色（白 10%）
  static GLASS_BG: number = 0x26FFFFFF // 标准底色（白 15%）
  static GLASS_BORDER_SUBTLE: number = 0x2EFFFFFF // 淡边框（白 18%）
  static GLASS_BORDER: number = 0x33FFFFFF // 标准边框（白 20%）
  static GLASS_INPUT: number = 0x4DFFFFFF // 输入框/按钮底（白 30%）
  static GLASS_TEXT_SECONDARY: number = 0x66FFFFFF // 次要文字（白 40%）
  static GLASS_TEXT_HINT: number = 0x99FFFFFF // 提示文字（白 60%）
  static GLASS_TEXT_DISABLED: number = 0xB3FFFFFF // 禁用文字（白 70%）
  static GLASS_TEXT_ACTIVE: number = 0xCCFFFFFF // 激活文字（白 80%）

  static readonly DARK_GLASS_BG_SUBTLE: number = 0x2EFFFFFF // 白 18%
  static readonly DARK_GLASS_BG: number = 0x40FFFFFF // 白 25%
  static readonly DARK_GLASS_BORDER_SUBTLE: number = 0x47FFFFFF // 白 28%
  static readonly DARK_GLASS_BORDER: number = 0x52FFFFFF // 白 32%
  static readonly DARK_GLASS_INPUT: number = 0x66FFFFFF // 白 40%

  // ========== 第三方品牌色 ==========
  static WECHAT_GREEN: number = 0xFF07C160
  static ALIPAY_BLUE: number = 0xFF1677FF
  // 品牌色 10% 透明底，用于支付渠道图标容器背景（评审 #18：收敛页面硬编码色值）
  static WECHAT_GREEN_LIGHT: number = 0x1A07C160
  static ALIPAY_BLUE_LIGHT: number = 0x1A1677FF

  // ========== 服务类型色彩（页面自有语义，非令牌层） ==========
  static SERVICE_WASH: number = 0xFF2D9B6A       // 标准洗 — 自然绿
  static SERVICE_WASH_LIGHT: number = 0xFFF0FBF4
  // 干洗 — 取 Android 代码现状「干→蓝」（ServicePage.kt:305 iconForegroundBlue/iconContainerBlue）。
  // ⚠️ 冲突记录：DESIGN.md 服务五色写「干洗紫 #7C3AED」，与 Android 代码现状（蓝 #4A67C9）不一致；
  //    按「基准 = Android 代码」以蓝为准，待设计确认后再统一。
  static SERVICE_DRY: number = 0xFF4A67C9        // 干洗 — 蓝（对齐 Android iconForegroundBlue）
  static SERVICE_DRY_LIGHT: number = 0xFFEEF2FB  // 干洗浅底 — 蓝（对齐 Android iconContainerBlue）
  static SERVICE_PRESS: number = 0xFFE07B00      // 熨烫 — 暖橙
  static SERVICE_PRESS_LIGHT: number = 0xFFFFF7ED
  static SERVICE_LUXURY: number = 0xFF0EA5E9     // 奢护 — 清澈蓝
  static SERVICE_LUXURY_LIGHT: number = 0xFFEFF6FF
  static SERVICE_SHOES: number = 0xFFDC2626      // 洗鞋 — 活力红
  static SERVICE_SHOES_LIGHT: number = 0xFFFEF2F2

  // ========== 阴影色（规范 §2.4 全应用仅两档，替代组件内散落的写死色） ==========
  // 字符串类型：shadow.color 接受 ResourceColor，rgba() 直译设计稿 CSS 值
  static readonly SHADOW_COLOR_CARD: string = 'rgba(20, 40, 30, 0.05)'   // 卡片档
  static readonly SHADOW_COLOR_FLOAT: string = 'rgba(20, 40, 30, 0.10)'  // 浮层/弹窗档

  // ========== Hero 卡对角黑纱（对齐安卓 StatusHeroCard scrim：左上黑 20% → 右下透明） ==========
  // 白字 on 品牌绿渐变实测 3.72:1（<4.5），叠纱后文字区对比度 ≥5.1:1；
  // 纱为纯黑透明层，深浅色同值（未定义 DARK_ 变体）。
  // 透明端在渐变 stop 上用 Color.Transparent（数值 0x00000000 会被当作不透明黑）。
  static readonly SCRIM_BLACK_20: number = 0x33000000  // 黑 20%
  // 弹窗/对话框点击遮罩（NavDestination DIALOG 模式整页蒙层）：黑 50%，深浅色同值（遮罩语义不随主题）
  static readonly SCRIM_BLACK_50: number = 0x80000000
  // 图片上沉浸层（封面底部压暗渐变，对齐安卓 ImageScrim 深浅共用语义）：黑 55% 深端 + 全透明端
  // 透明端用 rgba 字符串（0x00000000 会被当作不透明黑，同 SCRIM_BLACK_20 注释）
  static readonly IMAGE_SCRIM: number = 0x8C000000       // 黑 55%
  static readonly IMAGE_SCRIM_FADE: string = 'rgba(0, 0, 0, 0)' // 全透明端
}


/**
 * 应用深浅色主题（问题17）：EntryAbility 在冷启动与系统色模式变更时调用。
 * 令牌字段为可变静态值，调用后页面 rebuild 读取即得新色值；
 * 未定义 DARK_ 变体的令牌（GLASS 文字类/品牌色/服务类型色/阴影色等）深浅色同值，不在此列出。
 * 注意：静态字段变更本身不驱动 UI 刷新——栈内页面经 view/common/ThemeRefresh
 * 订阅 darkMode 触发子树重建后，重读本函数回写的新值。
 */
export function applyDesignTheme(dark: boolean): void {
  DesignColor.PRIMARY = dark ? DesignColor.DARK_PRIMARY : 0xFF2D9B6A
  DesignColor.ON_PRIMARY = dark ? DesignColor.DARK_ON_PRIMARY : 0xFFFFFFFF
  DesignColor.PRIMARY_CONTAINER = dark ? DesignColor.DARK_PRIMARY_CONTAINER : 0xFFF0FBF4
  DesignColor.ON_PRIMARY_CONTAINER = dark ? DesignColor.DARK_ON_PRIMARY_CONTAINER : 0xFF17322B
  DesignColor.PRIMARY_LIGHT = dark ? DesignColor.DARK_PRIMARY_LIGHT : 0xFFF0FBF4 // 超浅绿，图标容器/票券底（= primaryContainer）
  DesignColor.PRIMARY_SOFT = dark ? DesignColor.DARK_PRIMARY_SOFT : 0xFFE8F6EF
  DesignColor.ON_PRIMARY_SOFT = dark ? DesignColor.DARK_ON_PRIMARY_SOFT : 0xFF0B3625 // 浅绿选中底上的深绿字
  DesignColor.PRIMARY_DARK = dark ? DesignColor.DARK_PRIMARY_DARK : 0xFF1E8C5C  // 深绿，按下态
  DesignColor.SECONDARY = dark ? DesignColor.DARK_SECONDARY : 0xFF8E8E93
  DesignColor.ON_SECONDARY = dark ? DesignColor.DARK_ON_SECONDARY : 0xFFFFFFFF
  DesignColor.SECONDARY_CONTAINER = dark ? DesignColor.DARK_SECONDARY_CONTAINER : 0xFFF2F2F7
  DesignColor.ON_SECONDARY_CONTAINER = dark ? DesignColor.DARK_ON_SECONDARY_CONTAINER : 0xFF1C1C1E
  DesignColor.TERTIARY = dark ? DesignColor.DARK_TERTIARY : 0xFF6B7280
  DesignColor.ON_TERTIARY = dark ? DesignColor.DARK_ON_TERTIARY : 0xFFFFFFFF
  DesignColor.TERTIARY_CONTAINER = dark ? DesignColor.DARK_TERTIARY_CONTAINER : 0xFFF2F2F7
  DesignColor.ON_TERTIARY_CONTAINER = dark ? DesignColor.DARK_ON_TERTIARY_CONTAINER : 0xFF1C1C1E
  DesignColor.BACKGROUND = dark ? DesignColor.DARK_BACKGROUND : 0xFFF5F6F3        // 页面底色
  DesignColor.ON_BACKGROUND = dark ? DesignColor.DARK_ON_BACKGROUND : 0xFF1C1C1E
  DesignColor.SURFACE = dark ? DesignColor.DARK_SURFACE : 0xFFFFFFFF           // 纯白卡片
  DesignColor.ON_SURFACE = dark ? DesignColor.DARK_ON_SURFACE : 0xFF1C1C1E
  DesignColor.SURFACE_VARIANT = dark ? DesignColor.DARK_SURFACE_VARIANT : 0xFFEFF1EE   // 卡内次级块（输入框底、嵌套卡）
  DesignColor.ON_SURFACE_VARIANT = dark ? DesignColor.DARK_ON_SURFACE_VARIANT : 0xFF6B6E73
  DesignColor.GROUP_BACKGROUND = dark ? DesignColor.DARK_GROUP_BACKGROUND : 0xFFE7EAE4  // 分组容器底（比页面底再深一档）
  DesignColor.HAIRLINE = dark ? DesignColor.DARK_HAIRLINE : 0xFFEFF1EE          // 容器内部行分隔线底色
  DesignColor.OUTLINE = dark ? DesignColor.DARK_OUTLINE : 0xFFE6E9E6           // 卡片描边（1px）
  DesignColor.OUTLINE_VARIANT = dark ? DesignColor.DARK_OUTLINE_VARIANT : 0xFFEFF1EE
  DesignColor.ERROR = dark ? DesignColor.DARK_ERROR : 0xFFA8435A
  DesignColor.ON_ERROR = dark ? DesignColor.DARK_ON_ERROR : 0xFFFFFFFF
  DesignColor.ERROR_CONTAINER = dark ? DesignColor.DARK_ERROR_CONTAINER : 0xFFF7E9EC
  DesignColor.ON_ERROR_CONTAINER = dark ? DesignColor.DARK_ON_ERROR_CONTAINER : 0xFFA8435A
  DesignColor.WARNING = dark ? DesignColor.DARK_WARNING : 0xFFD98A26
  DesignColor.ON_WARNING = dark ? DesignColor.DARK_ON_WARNING : 0xFFFFFFFF
  DesignColor.WARNING_CONTAINER = dark ? DesignColor.DARK_WARNING_CONTAINER : 0xFFFDF3E7
  DesignColor.ON_WARNING_CONTAINER = dark ? DesignColor.DARK_ON_WARNING_CONTAINER : 0xFF92400E
  DesignColor.ONGOING = dark ? DesignColor.DARK_ONGOING : 0xFFD98A26
  DesignColor.ONGOING_INK = dark ? DesignColor.DARK_ONGOING_INK : 0xFFD98A26
  DesignColor.DANGER = dark ? DesignColor.DARK_DANGER : 0xFFA8435A
  DesignColor.DANGER_INK = dark ? DesignColor.DARK_DANGER_INK : 0xFFA8435A
  DesignColor.SUCCESS = dark ? DesignColor.DARK_SUCCESS : 0xFF2D9B6A
  DesignColor.ON_SUCCESS = dark ? DesignColor.DARK_ON_SUCCESS : 0xFFFFFFFF
  DesignColor.SUCCESS_CONTAINER = dark ? DesignColor.DARK_SUCCESS_CONTAINER : 0xFFF0FBF4
  DesignColor.ON_SUCCESS_CONTAINER = dark ? DesignColor.DARK_ON_SUCCESS_CONTAINER : 0xFF17322B
  DesignColor.TEXT_PRIMARY = dark ? DesignColor.DARK_TEXT_PRIMARY : 0xFF1C1C1E
  DesignColor.TEXT_SECONDARY = dark ? DesignColor.DARK_TEXT_SECONDARY : 0xFF6B6E73 // 次文本
  DesignColor.TEXT_TERTIARY = dark ? DesignColor.DARK_TEXT_TERTIARY : 0xFFA9AEA9  // 弱文本
  DesignColor.DIVIDER = dark ? DesignColor.DARK_DIVIDER : 0xFFEFF1EE
  DesignColor.WATER = dark ? DesignColor.DARK_WATER : 0xFF4A9FD4
  DesignColor.WATER_SOFT = dark ? DesignColor.DARK_WATER_SOFT : 0xFFDCEFF7     // 水色浅底容器
  DesignColor.ICON_CONTAINER_GREEN = dark ? DesignColor.DARK_ICON_CONTAINER_GREEN : 0xFFF0FBF4   // 洗护/主
  DesignColor.ICON_CONTAINER_BLUE = dark ? DesignColor.DARK_ICON_CONTAINER_BLUE : 0xFFEEF2FB    // 物流/寄件
  DesignColor.ICON_CONTAINER_ORANGE = dark ? DesignColor.DARK_ICON_CONTAINER_ORANGE : 0xFFFDF3E7  // 取件/待办
  DesignColor.ICON_CONTAINER_PINK = dark ? DesignColor.DARK_ICON_CONTAINER_PINK : 0xFFFDEEF0    // 异常/取消
  DesignColor.ICON_CONTAINER_TEAL = dark ? DesignColor.DARK_ICON_CONTAINER_TEAL : 0xFFF0FBF4    // 账户/安全
  DesignColor.ICON_CONTAINER_PURPLE = dark ? DesignColor.DARK_ICON_CONTAINER_PURPLE : 0xFFF4F0FB  // 观象台/AI
  DesignColor.ICON_FOREGROUND_GREEN = dark ? DesignColor.DARK_ICON_FOREGROUND_GREEN : 0xFF2D9B6A
  DesignColor.ICON_FOREGROUND_BLUE = dark ? DesignColor.DARK_ICON_FOREGROUND_BLUE : 0xFF4A67C9
  DesignColor.ICON_FOREGROUND_ORANGE = dark ? DesignColor.DARK_ICON_FOREGROUND_ORANGE : 0xFFD98A26
  DesignColor.ICON_FOREGROUND_PINK = dark ? DesignColor.DARK_ICON_FOREGROUND_PINK : 0xFFC9526B
  DesignColor.ICON_FOREGROUND_TEAL = dark ? DesignColor.DARK_ICON_FOREGROUND_TEAL : 0xFF3F9D78
  DesignColor.ICON_FOREGROUND_PURPLE = dark ? DesignColor.DARK_ICON_FOREGROUND_PURPLE : 0xFF7C3AED
  // 认证页渐变暗色变体（⚠️ Android 端 D1 为深浅同款，此处启用暗色后两端暗色下不一致，见字段注释）
  DesignColor.AUTH_GRADIENT_TOP = dark ? DesignColor.DARK_AUTH_GRADIENT_TOP : 0xFF1A9E6E
  DesignColor.AUTH_GRADIENT_MID = dark ? DesignColor.DARK_AUTH_GRADIENT_MID : 0xFF0D7A4A
  DesignColor.AUTH_GRADIENT_BOTTOM = dark ? DesignColor.DARK_AUTH_GRADIENT_BOTTOM : 0xFF0B5C3A
  // 毛玻璃面/边/输入三类（文字类白透明暗底对比天然更高，保持同值不回写）
  DesignColor.GLASS_BG_SUBTLE = dark ? DesignColor.DARK_GLASS_BG_SUBTLE : 0x1AFFFFFF
  DesignColor.GLASS_BG = dark ? DesignColor.DARK_GLASS_BG : 0x26FFFFFF
  DesignColor.GLASS_BORDER_SUBTLE = dark ? DesignColor.DARK_GLASS_BORDER_SUBTLE : 0x2EFFFFFF
  DesignColor.GLASS_BORDER = dark ? DesignColor.DARK_GLASS_BORDER : 0x33FFFFFF
  DesignColor.GLASS_INPUT = dark ? DesignColor.DARK_GLASS_INPUT : 0x4DFFFFFF
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
