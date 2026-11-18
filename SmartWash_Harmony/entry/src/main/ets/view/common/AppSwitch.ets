// 清氧设计系统 — 统一开关（对齐安卓 common/ui/components/AppSwitch.kt）
//
// 全库唯一开关：替代此前散落的两套自绘开关（SubscriptionToggle 44×24 灰实底、
// SoundToggle 48×28 浅底描边）与「文字状态冒充开关」的设置行（取件提醒 / 触感反馈）。
// 为什么自绘而不用 ArkUI 系统 Switch：规范 §7 禁 Material 交互组件 state layer（涟漪），
// 且既有订阅开关已是自绘，本组件把它的口径收敛为全库标准。
//
// 规格（与安卓 AppSwitch 逐项对齐）：
// - 轨道 44×24vp 胶囊、thumb 20vp 圆形；thumb 位移 2 ↔ 22vp 走弹簧
//   （ReduceMotion.specCurve(MotionTiming.SPRING)，规范 §7 位移用弹簧）；
// - 轨道：选中 = PRIMARY 实底；未选中 = SURFACE_VARIANT 轻底 + 1vp OUTLINE 描边
//   （轮廓由描边承担）；
// - thumb = SURFACE；
// - 外层最小 48vp 热区（视觉尺寸不变，ui-ux-pro-max：触控目标 ≥48）；
// - reduced motion：经 ReduceMotion 降级 180ms（规范 §8）；
// - 按压反馈：scale 0.97 + 100ms（对齐安卓 pressable）；reduced 改 alpha 0.86。
//
// 无障碍：安卓为 Role.Switch + stateDescription（开/关）。ArkUI API 17 无完全等价：
// accessibilityRole 需 since 18、accessibilityStateDescription 需 since 23，均不可用；
// 故取当前 SDK 最接近方案——accessibilityChecked 表达选中态（since 13），
// accessibilityText 拼接「标签 + 开/关状态」供读屏播报（since 11）。
// 后续若升级 SDK，应改回 accessibilityRole(AccessibilityRoleType.SWITCH) + stateDescription。

import { DesignDimens, DesignFontSize, DesignFontWeight, MotionAlpha, MotionScale, MotionTiming } from '../../constant/DesignSystem'
import { ReduceMotion } from '../../utils/ReduceMotion'
import { IconBox } from './AppComponents'

/**
 * 统一开关 — 44×24vp 胶囊轨道 + 20vp 圆形 thumb，整块可点切换。
 *
 * 用法：
 * ```
 * AppSwitch({
 *   checked: this.on,
 *   onChange: (checked: boolean): void => { this.on = checked },
 *   accessibilityLabel: '自动续费',
 *   onText: '开启',
 *   offText: '关闭',
 * })
 * ```
 */
@ComponentV2
export struct AppSwitch {
  @Param checked: boolean = false
  @Event onChange: (checked: boolean) => void = (_: boolean) => {
  }
  /** 禁用态：降透明 0.38 + 禁点（对齐安卓 alpha 0.38f） */
  // 命名注意：不能叫 enabled（与 CustomComponent 基类同名属性方法冲突，同 AppButton.isEnabled 约定）
  @Param isEnabled: boolean = true
  /** 读屏标签（如「自动续费」「雨声」）；空串则只报开/关状态 */
  @Param accessibilityLabel: string = ''
  /** 开态状态文案（可选；为空则只报标签） */
  @Param onText: string = ''
  /** 关态状态文案（可选；为空则只报标签） */
  @Param offText: string = ''
  // 按压反馈（对齐安卓 pressable scale 0.97 + 100ms；reduced 改 alpha 0.86）
  @Local pressed: boolean = false
  @Local reduceMotion: boolean = false

  aboutToAppear(): void {
    this.reduceMotion = ReduceMotion.isEnabled()
  }

  /** 读屏文案：标签 + 开/关状态（对齐安卓 contentDescription + stateDescription 的合并播报） */
  private announceText(): string {
    const state: string = this.checked ? this.onText : this.offText
    if (this.accessibilityLabel.length === 0) {
      return state
    }
    if (state.length === 0) {
      return this.accessibilityLabel
    }
    return `${this.accessibilityLabel}，${state}`
  }

  build() {
    Column() {
      // 轨道 + thumb：thumb 左缘 2 ↔ 22（位移量 = 20），垂直居中
      Row() {
        Column()
          .width(20)
          .height(20)
          .borderRadius(10)
          .backgroundColor($r('app.color.SURFACE'))
          .translate({ x: this.checked ? 22 : 2 })
          // thumb 位移走弹簧（规范 §7 位移动画禁固定时长 tween；对齐安卓 animateDpAsState(defaultSpring)）
          .animation(ReduceMotion.specCurve(MotionTiming.SPRING))
      }
      .width(44)
      .height(24)
      .borderRadius(DesignDimens.RADIUS_FULL)
      // 选中 = PRIMARY 实底；未选中 = SURFACE_VARIANT 轻底 + 1vp OUTLINE 描边
      .backgroundColor(this.checked ? $r('app.color.PRIMARY') : $r('app.color.SURFACE_VARIANT'))
      .border({
        width: 1,
        color: this.checked ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE')
      })
      .justifyContent(FlexAlign.Start)
      .alignItems(VerticalAlign.Center)
      // 轨道底色 / 描边走选中态弹簧（对齐安卓 selectedColor，reduced 180ms）
      .animation(ReduceMotion.specCurve(MotionTiming.SELECTED))
    }
    // 外层 48vp 最小热区（视觉胶囊 44×24 不变）
    .constraintSize({ minWidth: 48, minHeight: 48 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    // 按压反馈（对齐安卓 pressable）：scale 0.97 + 100ms FastOutSlowIn；reduced 改 alpha 0.86
    .scale({
      x: !this.reduceMotion && this.pressed && this.isEnabled ? MotionScale.Press : 1,
      y: !this.reduceMotion && this.pressed && this.isEnabled ? MotionScale.Press : 1
    })
    .opacity(this.isEnabled
      ? (this.reduceMotion && this.pressed ? MotionAlpha.PressReduced : 1)
      : 0.38)
    .animation(MotionTiming.PRESS)
    .onTouch((event: TouchEvent) => {
      if (!this.isEnabled) {
        return
      }
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      if (this.isEnabled) {
        this.onChange(!this.checked)
      }
    })
    // 无障碍：checked 状态 + 标签/状态文案（API 17 无 Role.Switch / stateDescription，见文件头说明）
    .accessibilityChecked(this.checked)
    .accessibilityText(this.announceText())
  }
}

/**
 * 设置页「开关行」—— 图标盒（可选）+ 标题/副标题 + 右侧 [AppSwitch]，整行点击切换。
 *
 * 用于把「用文字状态冒充开关」的设置项（取件提醒、触感反馈等）升级为真实开关语义：
 * 整行可点、右侧开关有 checked 状态 + 开/关状态播报。结构对齐安卓 SwitchRow。
 *
 * 字号说明：安卓 SwitchRow 标题走 bodyMedium(16)，鸿蒙设置行既有口径为 bodySmall(14)
 * （SettingRow / PermissionRow 一致），为避免改造设置页时行高与排版漂移，本组件沿用
 * 鸿蒙设置行字号（标题 14 / 副标题 12）。
 */
@ComponentV2
export struct SwitchRow {
  @Param title: string = ''
  @Param checked: boolean = false
  @Event onChange: (checked: boolean) => void = (_: boolean) => {
  }
  /** 可选图标；不传则无图标盒（对齐安卓 icon: ImageVector?） */
  @Param icon: Resource | null = null
  @Param iconContainerColor: ResourceColor = $r('app.color.ICON_CONTAINER_TEAL')
  @Param iconTint: ResourceColor = $r('app.color.ICON_FOREGROUND_TEAL')
  /** 副标题（可选） */
  @Param description: string = ''
  /** 行高：默认 64；带副标题且需更高行时可传 72（对齐安卓） */
  @Param rowHeight: number = 64
  // 命名注意：不能叫 enabled（与 CustomComponent 基类同名属性方法冲突，同 AppButton.isEnabled 约定）
  @Param isEnabled: boolean = true
  @Param onText: string = ''
  @Param offText: string = ''

  build() {
    Row() {
      // ArkTS 对 this.x 的 null 收窄在 build 闭包内有效（同 Service.ets 对 this.laundryItem 的写法）
      if (this.icon !== null) {
        IconBox({
          icon: this.icon,
          sizeValue: 32,
          iconSize: 20,
          containerColor: this.iconContainerColor,
          iconColor: this.iconTint
        })
          .margin({ right: DesignDimens.SPACE_SM })
      }

      Column() {
        Text(this.title)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.NORMAL)
          .fontColor($r('app.color.TEXT_PRIMARY'))

        if (this.description.length > 0) {
          Text(this.description)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .margin({ top: DesignDimens.SPACE_XXS })
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      AppSwitch({
        checked: this.checked,
        onChange: (checked: boolean): void => {
          this.onChange(checked)
        },
        isEnabled: this.isEnabled,
        accessibilityLabel: this.title,
        onText: this.onText,
        offText: this.offText
      })
    }
    .width('100%')
    .height(this.rowHeight)
    .alignItems(VerticalAlign.Center)
    .opacity(this.isEnabled ? 1 : 0.5)
    .onClick(() => {
      if (this.isEnabled) {
        this.onChange(!this.checked)
      }
    })
  }
}
