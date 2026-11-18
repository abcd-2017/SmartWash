// 清氧设计系统 — 鸿蒙端设计常量
// 基准：Android common/ui/theme/Color.kt + AppDesign.kt + Type.kt（DESIGN-SPEC v0.4，2026-10-24 定稿）
// 2026-10-29 全量对齐安卓 v0.4：字阶收敛 8 档、background/surfaceVariant 互换、
// outline/textTertiary/error/danger/success 取值、图标容器绿色系改 #F0FBF4。
// 决策 D1（已更新）：应用深浅色跟随系统——颜色全部经 base/dark 资源限定符（color.json）
// 自动切换，UI 侧用 $r('app.color.XXX') / $r('app.color.XXX') 读取，不再手动回写令牌。
// 认证页品牌渐变深浅同款（对齐 Android LoginPage.kt:89-94）。

import { curves } from '@kit.ArkUI'


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
 * 清氧设计系统尺寸常量（对齐 Android AppDimens）
 * 单位：vp (虚拟像素)
 */
export class DesignDimens {
  // 页面边距（规范 §2.3）
  static readonly PAGE_PADDING: number = 24
  static readonly CARD_PADDING: number = 20
  static readonly CARD_SPACING: number = 16
  static readonly SECTION_SPACING: number = 32
  // 圆角 token（规范 §2.3 五档：16 卡 / 20 大容器 / 14 按钮·输入 / 12 图标容器 / 10 小容器）
  static readonly RADIUS_SMALL: number = 10 // 小图标容器、徽标
  static readonly RADIUS_MD: number = 12    // 图标容器、内嵌套块
  static readonly CARD_RADIUS: number = 16  // 卡片（唯一值，= Android radiusLg；Hero 卡同档，见 U-110）
  static readonly RADIUS_XL: number = 20    // 大容器（= Android radiusXl；Hero 卡不用此档，收敛到 CARD_RADIUS 16）
  static readonly RADIUS_FULL: number = 999 // 头像、胶囊
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

// ========== 动效词汇（对齐安卓 common/utils/AnimationUtils.kt + PressFeedbackModifier.kt） ==========
//
// 规格来源：安卓 AnimationUtils.kt 第 7.1 节弹簧参数表 + 时长层级（动效语言 v0.4）。
// 安卓弹簧参数（dampingRatio/stiffness）在 ArkUI 的等价物是 springMotion(response, dampingFraction)：
// 弹簧从当前值起动画、天然可中断，不用固定时长 tween 描述位移/缩放动画（规范 §7）。
// 注意：curve 为 springMotion 时 AnimateParam.duration 被系统忽略（弹簧无固定时长）。

/** 动效时长令牌（单位 ms）— 对齐安卓 PRESS_DURATION_MS / CROSSFADE_ALT_MS / DURATION_*_MS */
export class MotionDuration {
  /** 按压反馈 — 配合 scale 0.97，按下瞬间必须无延迟感知 */
  static readonly PressMs: number = 100
  /** reduced motion 降级时长（CrossfadeAlt）— 取消缩放/位移/回弹，仅保留颜色与淡入淡出 */
  static readonly CrossfadeAltMs: number = 180
  /** 小组件级 — 选中态、tint、微反馈时长上限 */
  static readonly MicroMs: number = 200
  /** 卡片与展开级 — 内容块 / 卡片过渡 */
  static readonly CardMs: number = 300
  /** 页面与弹层转场级（进入） */
  static readonly ScreenMs: number = 400
  /** 页面与弹层转场级（退出）— 退出 ≈ 进入的 70%（400 × 0.7 = 280） */
  static readonly ShellExitMs: number = 280
  /** 骨架微光周期 — Linear 无限循环 */
  static readonly ShimmerMs: number = 1400
  /** 底栏图标交叉淡变时间线（规范 §4.5，组件特例，不属通用时长层级） */
  static readonly BottomNavIconMs: number = 600
}

/** 动效缩放令牌 */
export class MotionScale {
  /** 按压缩放 — 对齐安卓 pressable(scaleFactor = 0.97f) */
  static readonly Press: number = 0.97
  /** 弹窗入场起始缩放 — scale 0.92 → 1 */
  static readonly DialogEnter: number = 0.92
  /** 页面转场起始缩放 — scale 0.95 → 1（壳层） */
  static readonly Shell: number = 0.95
  /** 底栏选中图标缩放 — scale 1 → 1.06 */
  static readonly BottomNavSelected: number = 1.06
}

/** 动效透明度令牌 */
export class MotionAlpha {
  /** reduced motion 按压降级透明度 — 取消缩放，改用 alpha 0.86 保留反馈（规范 §8） */
  static readonly PressReduced: number = 0.86
}

/**
 * 动效 timing — 全应用唯一动效词汇，页面禁止再写魔法值。
 * reduced motion 统一经 utils/ReduceMotion.ets 的 spec() 降级为 CrossfadeAlt（180ms）。
 */
export class MotionTiming {
  /** 标准弹簧 — 对齐安卓 defaultSpring(1.0, 350)：临界阻尼无回弹（curves 工厂返回 ICurve） */
  static readonly SPRING: ICurve = curves.springMotion(0.4, 1)

  /**
   * 弹跳弹簧 — 对齐安卓 MediumBouncy（dampingRatio 0.5；D11：收藏弹跳/加选 +→✓ 等状态
   * 变换反馈）：阻尼欠阻尼产生一次过冲回落，response 与标准弹簧同档。
   */
  static readonly BOUNCY: ICurve = curves.springMotion(0.4, 0.5)

  /** 选中态颜色 / 指示条 — 临界阻尼弹簧（对齐安卓 selectedColor = motionSpec(defaultSpring())） */
  static readonly SELECTED: ICurve = curves.springMotion(0.4, 1)

  /** 列表项进出场 / 位移 — 对齐安卓 animateItem(placementSpec = motionSpec(defaultSpring())) */
  static readonly ITEM: ICurve = curves.springMotion(0.4, 1)

  /** 数字变化 — 弹簧淡入淡出（对齐安卓 AnimatedContent numberSpec = defaultSpring()） */
  static readonly NUMBER: ICurve = curves.springMotion(0.4, 1)

  /** 按压反馈 — tween 而非 spring（按压要"立刻"而不是"弹一下"）；对齐安卓 pressTween() */
  static readonly PRESS: AnimateParam = { duration: MotionDuration.PressMs, curve: Curve.FastOutSlowIn }

  /** 内容进场 — 200ms 减速（对齐安卓 enterTween(DURATION_MICRO_MS)） */
  static readonly ENTER_CONTENT: AnimateParam = { duration: MotionDuration.MicroMs, curve: Curve.LinearOutSlowIn }

  /** 内容退出 — 200ms 加速（对齐安卓 exitTween(DURATION_MICRO_MS)） */
  static readonly EXIT_CONTENT: AnimateParam = { duration: MotionDuration.MicroMs, curve: Curve.FastOutLinearIn }

  /** 卡片级进场 — 300ms 减速（对齐安卓卡片与展开级） */
  static readonly ENTER_CARD: AnimateParam = { duration: MotionDuration.CardMs, curve: Curve.LinearOutSlowIn }

  /** 卡片级退出 — 300ms 加速 */
  static readonly EXIT_CARD: AnimateParam = { duration: MotionDuration.CardMs, curve: Curve.FastOutLinearIn }

  /** 页面转场进 — 400ms 减速（对齐安卓壳层 DURATION_SCREEN_MS） */
  static readonly ENTER_SCREEN: AnimateParam = { duration: MotionDuration.ScreenMs, curve: Curve.LinearOutSlowIn }

  /** 页面转场出 — 280ms 加速（对齐安卓壳层 400×7/10） */
  static readonly EXIT_SCREEN: AnimateParam = { duration: MotionDuration.ShellExitMs, curve: Curve.FastOutLinearIn }

  /** 交叉淡变降级档（CrossfadeAlt）— 180ms Linear */
  static readonly CROSSFADE_ALT: AnimateParam = { duration: MotionDuration.CrossfadeAltMs, curve: Curve.Linear }

  /**
   * 弹窗入场 — 对齐安卓 dialogEnterMotion：scale 0.92→1 + fade 0→1 一次性进场。
   * 退出动画受弹窗窗口机制限制（窗口由系统直接移除），两端一致不做。
   */
  static readonly DIALOG_ENTER: AnimateParam = { duration: MotionDuration.MicroMs, curve: curves.springMotion(0.4, 1) }

  /** 内容进场（空态等状态到达时刻）— 弹簧，reduced motion 降级为 180ms 纯淡入 */
  static readonly ENTER: AnimateParam = { duration: MotionDuration.MicroMs, curve: curves.springMotion(0.4, 1) }

  /**
   * 骨架微光 — 1400ms Linear 无限循环。
   * ArkUI 无法平移渐变偏移量，调用侧以高光层 opacity 呼吸近似安卓 shimmer 扫过；
   * reduced motion 时调用侧退化为静态底块（取消无限循环）。
   */
  static readonly SHIMMER: AnimateParam = {
    duration: MotionDuration.ShimmerMs,
    curve: Curve.Linear,
    iterations: -1,
    playMode: PlayMode.Alternate
  }
}


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


/**
 * 清氧设计系统字体粗细常量（对齐 Android：只允许 400/500/600/700）
 */
export class DesignFontWeight {
  static readonly BOLD: number = 700
  static readonly SEMI_BOLD: number = 600
  static readonly MEDIUM: number = 500
  static readonly NORMAL: number = 400
}


/**
 * 清氧设计系统字体特征常量（DESIGN.md「数字等宽规则 tnum Rule」）。
 * 金额、数量、取件码、订单号一律 tabular numbers——数字变化时左右跳即违规。
 * ArkUI `Text.fontFeature(value)` 自 API 12 起支持（SDK 5.0.5(17) 满足），取值同 OpenType feature tag。
 */
export class DesignFontFeature {
  static readonly TNUM: string = 'tnum'
}
