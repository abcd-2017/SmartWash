// 清氧设计系统 — 通用 UI 组件
// 参考 Android 端 AppComponents.kt

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight, MotionTiming } from '../../constant/DesignSystem'
import { pathStack } from '../../utils/PathStackUtil'
import vibrator from '@ohos.vibrator'

// @BuilderParam 的默认空插槽（ArkTS 要求默认值必须是 @Builder 函数，不能用内联箭头函数）
@Builder
function emptyContent() {
}

// ========== PageHeader 页面头部 ==========

@ComponentV2
export struct PageHeader {
  @Param title: string = ''
  @Param showBack: boolean = true
  @Event backClick: () => void = () => {
    pathStack.pop()
  }

  build() {
    Row() {
      // 左侧返回按钮
      if (this.showBack) {
        SymbolGlyph($r('sys.symbol.chevron_left'))
          .fontSize(24)
          .fontColor([DesignColor.ON_BACKGROUND])
          .onClick(() => {
            this.backClick()
          })
          .padding(8)
      }

      // 标题
      Text(this.title)
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.BOLD)
        .fontColor(DesignColor.ON_BACKGROUND)
        .margin({ left: this.showBack ? 4 : 0 })

      Blank()

      // 右侧插槽（通过外部 Row 容器扩展）
    }
    .width('100%')
    .height(56)
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    .alignItems(VerticalAlign.Center)
  }
}

// ========== IconBox 图标容器模板（对齐 Android AppDesign.kt IconBox/CircleIconBox） ==========

/**
 * 方形图标容器 — 浅底 + 语义色前景图标，默认 44vp 容器 / 22fp 图标 / 12vp 圆角。
 * 六色浅底/前景令牌见 DesignColor.ICON_CONTAINER_* / ICON_FOREGROUND_*。
 */
@ComponentV2
export struct IconBox {
  @Require @Param icon: Resource
  @Param sizeValue: number = 44
  @Param iconSize: number = 22
  @Param containerColor: number = DesignColor.PRIMARY_LIGHT
  @Param iconColor: number = DesignColor.PRIMARY
  @Param radius: number = DesignDimens.ICON_CONTAINER_RADIUS

  build() {
    Column() {
      SymbolGlyph(this.icon)
        .fontSize(this.iconSize)
        .fontColor([this.iconColor])
    }
    .width(this.sizeValue)
    .height(this.sizeValue)
    .borderRadius(this.radius)
    .backgroundColor(this.containerColor)
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }
}

/**
 * 圆形图标容器 — 默认 40vp 容器 / 20fp 图标，圆角取容器尺寸一半。
 */
@ComponentV2
export struct CircleIconBox {
  @Require @Param icon: Resource
  @Param sizeValue: number = 40
  @Param iconSize: number = 20
  @Param containerColor: number = DesignColor.PRIMARY_LIGHT
  @Param iconColor: number = DesignColor.PRIMARY

  build() {
    Column() {
      SymbolGlyph(this.icon)
        .fontSize(this.iconSize)
        .fontColor([this.iconColor])
    }
    .width(this.sizeValue)
    .height(this.sizeValue)
    .borderRadius(this.sizeValue / 2)
    .backgroundColor(this.containerColor)
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }
}

// ========== AppCard 统一卡片容器 ==========

/**
 * 统一卡片容器 — DESIGN-SPEC v0.4 §3.1 唯一卡片画法：
 * surface 白底 + 1px outline 描边 + level1 阴影（0 2px rgba(20,40,30,.05)）+ 圆角 16
 *
 * 使用方式：
 * AppCard() {
 *   Text("卡片内容")
 * }
 */
@ComponentV2
export struct AppCard {
  // 命名注意：不能叫 borderRadius（与 CustomComponent 基类的同名属性方法冲突导致编译错误）
  @Param cardRadius: number = DesignDimens.CARD_RADIUS
  @Param paddingValue: number = DesignDimens.CARD_PADDING
  @BuilderParam content: () => void = emptyContent

  build() {
    Column() {
      this.content()
    }
    .width('100%')
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(this.cardRadius)
    .borderWidth(1)
    .borderColor(DesignColor.OUTLINE)
    .padding(this.paddingValue)
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
  }
}

// ========== AppButton 统一按钮（三态变体，对齐安卓 AppButtonVariant §3.3） ==========

/** AppButton 形态 — 主（品牌渐变底）/ 次（surfaceVariant 底）/ 文字（无底无框） */
export enum AppButtonVariant {
  PRIMARY,
  SECONDARY,
  TEXT
}

/**
 * 统一按钮（规范 §3.3）— 自绘实现，业务层一律经本组件消费，禁止裸写可点击按钮：
 * 主 = 52vp 高 / 14vp 圆角 / 135° primary→primaryDark 渐变底 / onPrimary 文字 18 档 SemiBold；
 * 次 = 同尺寸 surfaceVariant 底 + 主文本色（「退出登录」等经 contentColor 换语义色，容器不变）；
 * 文字 = 主色无底无框，最小触控 44。
 *
 * 形态用 Normal 方角 + BUTTON_RADIUS：此前 Capsule 会让 borderRadius(14) 失效变胶囊，
 * 与安卓 AppButton 的 14 圆角矩形不一致（v0.4 §3.3）。
 * disabled/loading：整钮降透明度 0.5 + 禁点（对齐安卓禁用降透明，替代旧灰底 TEXT_TERTIARY）；
 * spinner 色随文字色，浅底次按钮上不再出现不可见的白圈。
 */
@ComponentV2
export struct AppButton {
  @Param text: string = ''
  // 命名注意：不能叫 enabled（与 CustomComponent 基类的同名属性方法冲突导致编译错误）
  @Param isEnabled: boolean = true
  @Param loading: boolean = false
  @Param variant: AppButtonVariant = AppButtonVariant.PRIMARY
  /** 次按钮文字语义色覆盖（如退出登录 dangerInk）；仅 SECONDARY 生效，容器形态不变 */
  @Param contentColor: number | null = null
  @Event click: () => void = () => {
  }

  private isActive(): boolean {
    return this.isEnabled && !this.loading
  }

  /** 文字/spinner 色 — 主=onPrimary；次=contentColor ?? textPrimary；文字态=primary */
  private contentTint(): number {
    if (this.variant === AppButtonVariant.PRIMARY) {
      return DesignColor.ON_PRIMARY
    }
    if (this.variant === AppButtonVariant.SECONDARY) {
      return this.contentColor ?? DesignColor.TEXT_PRIMARY
    }
    return DesignColor.PRIMARY
  }

  private triggerClick(): void {
    if (!this.isActive()) {
      return
    }
    // 触觉反馈
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 忽略不支持的设备
    }
    this.click()
  }

  build() {
    if (this.variant === AppButtonVariant.TEXT) {
      Row() {
        if (this.loading) {
          LoadingProgress()
            .width(14)
            .height(14)
            .color(this.contentTint())
        } else {
          Text(this.text)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(this.contentTint())
        }
      }
      .justifyContent(FlexAlign.Center)
      .constraintSize({ minWidth: 44, minHeight: 44 })
      .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM })
      .opacity(this.isActive() ? 1 : 0.5)
      .onClick(() => {
        this.triggerClick()
      })
    } else {
      Button() {
        if (this.loading) {
          LoadingProgress()
            .width(22)
            .height(22)
            .color(this.contentTint())
        } else {
          Text(this.text)
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(this.contentTint())
        }
      }
      .type(ButtonType.Normal)
      .width('100%')
      .height(52)
      .borderRadius(DesignDimens.BUTTON_RADIUS)
      // 次 = surfaceVariant 底；主 = 135° 品牌渐变（对齐安卓 Brush.linearGradient(primary→primaryDark)）
      .backgroundColor(this.variant === AppButtonVariant.SECONDARY ? DesignColor.SURFACE_VARIANT : Color.Transparent)
      .linearGradient({
        angle: 135,
        colors: this.variant === AppButtonVariant.PRIMARY
          ? [[DesignColor.PRIMARY, 0], [DesignColor.PRIMARY_DARK, 1]]
          : [[Color.Transparent, 0], [Color.Transparent, 1]]
      })
      .opacity(this.isActive() ? 1 : 0.5)
      .stateEffect(this.isActive())
      .enabled(this.isActive())
      .onClick(() => {
        this.triggerClick()
      })
    }
  }
}

// ========== AppInfoDialog 信息提示弹窗（对齐安卓 AppInfoDialog） ==========

/**
 * 单按钮信息弹窗
 * 用于：请选择套餐、更换学校需联系客服、查看取件码等纯提示场景
 *
 * 使用方式：
 * private infoDialogController: CustomDialogController = new CustomDialogController({
 *   builder: AppInfoDialog({ message: '请选择套餐', onDismiss: () => { ... } }),
 *   autoCancel: true,
 *   alignment: DialogAlignment.Center,
 *   customStyle: true
 * })
 */
@CustomDialog
export struct AppInfoDialog {
  // 由父组件注入控制器（空 builder 的自建控制器不合法）
  controller: CustomDialogController
  @Prop title: string = ''
  @Prop message: string = ''
  @Prop buttonText: string = '确认'
  @State entered: boolean = false
  onDismiss: () => void = () => {
  }

  /**
   * 弹窗统一入场（对齐安卓 dialogEnterMotion）：scale 0.92→1 + fade 0→1 弹簧一次性进场。
   * 首帧后再触发，保证初始 0.92/0 先上屏，过渡才可见；退出动画窗口层不支持，两端一致不做。
   */
  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  build() {
    Column() {
      Column() {
        if (this.title !== '') {
          Text(this.title)
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ bottom: 12 })
        }

        Text(this.message)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_SECONDARY)
      }
      .width('100%')
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: 20,
        bottom: 20
      })

      Divider()
        .color(DesignColor.DIVIDER)
        .strokeWidth(0.5)

      Button(this.buttonText)
        .type(ButtonType.Normal)
        .width('100%')
        .height(48)
        .backgroundColor(Color.Transparent)
        .fontColor(DesignColor.PRIMARY)
        .fontSize(DesignFontSize.BODY)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .borderRadius(0)
        .onClick(() => {
          this.onDismiss()
          this.controller.close()
        })
    }
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    // 浮层档阴影（§2.4 CSS 0 10px 24px rgba(20,40,30,.10)）；原 #33000000 黑 20% 不符
    .shadow({
      radius: DesignElevation.FLOAT_SHADOW_RADIUS,
      color: DesignColor.SHADOW_COLOR_FLOAT,
      offsetX: 0,
      offsetY: DesignElevation.FLOAT_SHADOW_OFFSET
    })
    .scale({ x: this.entered ? 1 : 0.92, y: this.entered ? 1 : 0.92 })
    .opacity(this.entered ? 1 : 0)
    .onAppear(() => {
      this.playEnterMotion()
    })
  }
}

// ========== AppTabBar 标签栏 ==========

/**
 * 标签栏（下划线指示条形态，对齐安卓 AppTabBar）
 * 两态同字号同字重（14/Medium），只差颜色与指示条 —— 避免选中瞬间字号跳变
 * 拉动兄弟节点位移；指示条常驻、宽度 0↔20 弹簧过渡，切换不再硬跳。
 */
@ComponentV2
export struct AppTabBar {
  @Param tabs: string[] = []
  @Param selectedIndex: number = 0
  @Event tabSelected: (index: number) => void = (_: number) => {
  }

  build() {
    Row() {
      ForEach(this.tabs, (title: string, index: number) => {
        Column() {
          Text(title)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(this.selectedIndex === index ? DesignColor.PRIMARY : DesignColor.TEXT_SECONDARY)
            .padding({ left: 12, right: 12, top: 8, bottom: 8 })
            .animation(MotionTiming.ENTER)

          // 底部指示器 — 常驻，宽度 0↔20 过渡
          Row()
            .width(this.selectedIndex === index ? 20 : 0)
            .height(3)
            .borderRadius(1.5)
            .backgroundColor(DesignColor.PRIMARY)
            .animation(MotionTiming.ENTER)
        }
        .alignItems(HorizontalAlign.Center)
        .onClick(() => {
          this.tabSelected(index)
        })
      })
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }
}

// ========== AppPillTabRow 胶囊分段选择器（对齐安卓 AppPillTabRow.kt） ==========

/**
 * 胶囊分段选择器 — 与 AppTabBar 的分工：本组件是「胶囊分段」形态（选中项有底色胶囊），
 * AppTabBar 是「下划线指示条」形态，页面按设计稿二选一（优惠券三 tab 用本组件）。
 *
 * 字重统一 SemiBold：字重渐变会让文字宽度在动画期间持续变化、兄弟节点连续位移，
 * 观感差于一次跳变；选中底/文字色经 .animation 弹簧过渡。触觉：选中档。
 */
@ComponentV2
export struct AppPillTabRow {
  @Param tabs: string[] = []
  @Param selectedIndex: number = 0
  @Event tabSelected: (index: number) => void = (_: number) => {
  }

  build() {
    Row() {
      ForEach(this.tabs, (title: string, index: number) => {
        Row() {
          Text(title)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(this.selectedIndex === index ? DesignColor.PRIMARY : DesignColor.TEXT_SECONDARY)
        }
        .justifyContent(FlexAlign.Center)
        .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 8, bottom: 8 })
        .borderRadius(DesignDimens.RADIUS_FULL)
        .backgroundColor(this.selectedIndex === index ? DesignColor.PRIMARY_LIGHT : Color.Transparent)
        .margin({ right: DesignDimens.SPACE_XS })
        .animation(MotionTiming.ENTER)
        .onClick(() => {
          try {
            vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
          } catch (err) {
            // 忽略不支持的设备
          }
          this.tabSelected(index)
        })
      }, (title: string) => title)
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }
}

// ========== SelectionDot 选中圆点（对齐安卓 SelectionDot.kt） ==========

/**
 * 公共选中圆点 — 单选/步骤态的标记（支付选券等）。
 * 结构：20vp 外环（描边色随选中过渡）+ 内圆（scale 0→1 弹簧长出）。
 * 无点击逻辑：按压反馈由调用方负责（本组件只做状态表达），替代系统 Radio
 * （其圈点样式/尺寸不受令牌控制）。
 */
@ComponentV2
export struct SelectionDot {
  @Param selected: boolean = false
  @Param dotSize: number = 20

  build() {
    Stack() {
      // 外环
      Column()
        .width(this.dotSize)
        .height(this.dotSize)
        .borderRadius(this.dotSize / 2)
        .border({
          width: 1,
          color: this.selected ? DesignColor.PRIMARY : DesignColor.OUTLINE,
          style: BorderStyle.Solid
        })
        .animation(MotionTiming.ENTER)

      // 内圆：选中时从圆心长出（scale 0→1）
      Column()
        .width(this.dotSize / 2)
        .height(this.dotSize / 2)
        .borderRadius(this.dotSize / 4)
        .backgroundColor(DesignColor.PRIMARY)
        .scale({ x: this.selected ? 1 : 0, y: this.selected ? 1 : 0 })
        .animation(MotionTiming.ENTER)
    }
    .width(this.dotSize)
    .height(this.dotSize)
  }
}

// ========== 骨架屏（规范 §3.6：加载态用骨架微光，禁止转圈占位列表） ==========

/**
 * 骨架块 — 底块 surfaceVariant + 线性渐变高光层做 1400ms 呼吸明暗。
 * ArkUI 无法平移渐变偏移量，用高光层 opacity 呼吸近似安卓 shimmer 的「微光扫过」；
 * 尺寸/圆角由调用方以属性链给定（对齐安卓 SkeletonBlock 的 modifier 用法）。
 */
@ComponentV2
export struct SkeletonBlock {
  @Param blockRadius: number = DesignDimens.RADIUS_MD
  @Local glow: boolean = false

  build() {
    Stack() {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor(DesignColor.SURFACE_VARIANT)

      Column()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 90,
          colors: [['rgba(255,255,255,0)', 0], [DesignColor.SURFACE, 0.5], ['rgba(255,255,255,0)', 1]]
        })
        .opacity(this.glow ? 1 : 0.3)
        .animation({
          duration: 1400,
          curve: Curve.Linear,
          iterations: -1,
          playMode: PlayMode.Alternate
        })
    }
    .clip(true)
    .borderRadius(this.blockRadius)
    .onAppear(() => {
      this.glow = true
    })
  }
}

/**
 * 列表骨架 — 列表型页面（订单/券/收支/服务）的加载占位，替代居中转圈：
 * 转圈只说明「在忙」，骨架说明「马上出现什么形状」（规范 §3.6）。
 */
@ComponentV2
export struct SkeletonList {
  @Param rows: number = 4

  /** ForEach 数据源（ArkTS 不支持在 build 内构造数组，收敛到方法） */
  private rowKeys(): number[] {
    const keys: number[] = []
    for (let i = 0; i < this.rows; i++) {
      keys.push(i)
    }
    return keys
  }

  build() {
    Column({ space: DesignDimens.CARD_SPACING }) {
      ForEach(this.rowKeys(), (row: number) => {
        Row({ space: DesignDimens.SPACE_SM }) {
          SkeletonBlock()
            .width(40)
            .height(40)
          Column({ space: 8 }) {
            SkeletonBlock()
              .width('55%')
              .height(14)
            SkeletonBlock()
              .width('35%')
              .height(12)
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
      }, (row: number) => row.toString())
    }
    .width('100%')
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: DesignDimens.SPACE_SM,
      bottom: DesignDimens.SPACE_SM
    })
  }
}

/** 加载态（规范 §3.6：骨架屏，禁止转圈）— 列表骨架 4 行，对齐安卓 LoadingState */
@ComponentV2
export struct LoadingState {
  build() {
    SkeletonList({ rows: 4 })
  }
}

// ========== DrumMark 滚筒圆母题（规范 §5；对齐安卓 DrumMark.kt:35-102） ==========

/**
 * 滚筒圆 — 规范 §5 母题：同心圆环 + 一条穿过它的水波线。
 *
 * 几何基准 64，全部尺寸相对 [sizeValue] 等比缩放：
 * - 外环 track：r = 28/64，描边宽 2.5/64，颜色 [trackTint]（默认同 [tint]）
 * - 进度弧：同半径同宽，起始 -90°、扫角 360° × progress，颜色 [tint]（progress 为 null 或 ≤ 0 不绘制）
 * - 内环：r = 17/64，描边宽 2/64，颜色 [innerRingTint]（null 不绘制）
 * - 单条水波：描边宽 2.4/64，颜色 [tint]，对照设计稿 `M23 32c3-3.5 6 2 9-.5s6 1.5 9-1`
 *
 * 组件本身不带动画：进度环由真实状态传入；空态传 progress = null 即为静止滚筒。
 * 说明：ArkUI Canvas 只在 aboutToAppear 绘制一次（本组件当前仅作静态母题消费，无状态驱动）。
 */
@ComponentV2
export struct DrumMark {
  @Param sizeValue: number = 48
  @Param tint: number = DesignColor.PRIMARY
  @Param trackTint: number | null = null
  @Param innerRingTint: number | null = null
  @Param progress: number | null = null
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  aboutToAppear(): void {
    this.drawMark()
  }

  /** 滚筒圆绘制（外环 track + 可选进度弧 + 可选内环 + 单条水波线） */
  private drawMark(): void {
    const size: number = this.sizeValue
    const scaleFactor: number = size / 64
    const cx: number = size / 2
    const cy: number = size / 2
    const track: number = this.trackTint ?? this.tint

    this.ctx.clearRect(0, 0, size, size)
    this.ctx.lineCap = 'round'

    // 外环 track
    this.ctx.lineWidth = 2.5 * scaleFactor
    this.ctx.strokeStyle = track
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, 28 * scaleFactor, 0, Math.PI * 2, false)
    this.ctx.stroke()

    // 进度弧（起始 -90°，扫角 360° × progress）
    const progressValue: number | null = this.progress
    if (progressValue !== null) {
      const clamped: number = Math.min(1, Math.max(0, progressValue))
      if (clamped > 0) {
        this.ctx.lineWidth = 2.5 * scaleFactor
        this.ctx.strokeStyle = this.tint
        this.ctx.beginPath()
        this.ctx.arc(cx, cy, 28 * scaleFactor, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * clamped, false)
        this.ctx.stroke()
      }
    }

    // 内环
    const innerRing: number | null = this.innerRingTint
    if (innerRing !== null) {
      this.ctx.lineWidth = 2 * scaleFactor
      this.ctx.strokeStyle = innerRing
      this.ctx.beginPath()
      this.ctx.arc(cx, cy, 17 * scaleFactor, 0, Math.PI * 2, false)
      this.ctx.stroke()
    }

    // 单条水波线（穿过圆环）— 设计稿 M23 32c3-3.5 6 2 9-.5s6 1.5 9-1，中心 32,32
    this.ctx.lineWidth = 2.4 * scaleFactor
    this.ctx.strokeStyle = this.tint
    this.ctx.beginPath()
    this.ctx.moveTo(cx - 9 * scaleFactor, cy)
    this.ctx.bezierCurveTo(
      cx - 6 * scaleFactor, cy - 3.5 * scaleFactor,
      cx - 3 * scaleFactor, cy + 2 * scaleFactor,
      cx, cy - 0.5 * scaleFactor
    )
    // s 指令：第一控制点为上一段第二控制点关于当前点的反射 → (3, -3)
    this.ctx.bezierCurveTo(
      cx + 3 * scaleFactor, cy - 3 * scaleFactor,
      cx + 6 * scaleFactor, cy + 1 * scaleFactor,
      cx + 9 * scaleFactor, cy - 1.5 * scaleFactor
    )
    this.ctx.stroke()
  }

  build() {
    Canvas(this.ctx)
      .width(this.sizeValue)
      .height(this.sizeValue)
  }
}

// ========== EmptyState 空状态占位 ==========

/**
 * 空状态占位（规范 §3.6）— 滚筒圆母题（DrumMark）静止态 + 一句引导文案 + 可选行动按钮。
 *
 * 规范 §3.6 禁止「用一个灰色 Material 图标了事」，故本组件只渲染滚筒圆母题，
 * 不再提供回退到裸 SymbolGlyph 图标的分支。
 *
 * 垂直内边距 32 / 母题 → 文案 spaceMd(16) / 文案 → 行动按钮由插槽自行给出
 * （对齐安卓 EmptyState：padding vertical 32dp + DrumMark(size 48dp, primary)）。
 *
 * @param icon 不渲染，仅为兼容既有调用点的占位参数（对齐安卓 EmptyState 的 icon 占位）
 */
@ComponentV2
export struct EmptyState {
  @Param message: string = '暂无数据'
  @Param icon: Resource = $r('sys.symbol.square_slash')
  // 行动按钮插槽（规范 §3.6：空态 = 滚筒圆母题 + 引导文案 + 行动按钮）。
  // 默认走空插槽 emptyContent，不渲染任何内容，故既有只传 message 的调用点视觉不变。
  @BuilderParam action: () => void = emptyContent
  // 空态是"状态到达"的时刻，给一次进场（fade + 上移归位，对齐安卓 EmptyState 入场）。
  // V2 组件内部状态用 @Local（@State 是 V1 装饰器）
  @Local entered: boolean = false

  build() {
    Column() {
      // 母题 — 滚筒圆（DrumMark）静止态；48vp + primary（规范 §3.6 母题色）
      DrumMark({
        sizeValue: 48,
        tint: DesignColor.PRIMARY
      })
        .margin({ bottom: DesignDimens.SPACE_MD })

      // 引导文案 — 空态主文案档 bodyLarge(16)
      Text(this.message)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_SECONDARY)

      // 行动按钮（可选；间距由调用方在插槽内自行给出，未传时零占位）
      this.action()
    }
    .width('100%')
    .padding({ top: 32, bottom: 32 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .opacity(this.entered ? 1 : 0)
    .translate({ y: this.entered ? 0 : 12 })
    .onAppear(() => {
      // 首帧后再触发，保证初始态先上屏，弹簧进场才可见
      setTimeout(() => {
        animateTo(MotionTiming.ENTER, () => {
          this.entered = true
        })
      }, 16)
    })
  }
}

// ========== AppConfirmDialog 确认弹窗 ==========

/**
 * 确认弹窗
 * 用于：取消订单、确认支付、解绑校园卡、退出登录等
 *
 * 使用方式：
 * private dialogController: CustomDialogController = new CustomDialogController({
 *   builder: AppConfirmDialog({
 *     title: '提示',
 *     message: '确定要取消订单吗？',
 *     onConfirm: () => { ... },
 *     onDismiss: () => { ... }
 *   }),
 *   autoCancel: true,
 *   alignment: DialogAlignment.Center,
 *   customStyle: true
 * })
 */
@CustomDialog
export struct AppConfirmDialog {
  // 由父组件注入控制器（与 Setting.ets 的弹窗写法一致；空 builder 的自建控制器不合法）
  controller: CustomDialogController
  @Prop title: string = ''
  @Prop message: string = ''
  @Prop confirmText: string = '确认'
  @Prop cancelText: string = '取消'
  // 危险操作（退出登录/解绑等）：确认按钮走 dangerInk，与安卓 AppConfirmDialog(isDanger = true) 对齐
  @Prop isDanger: boolean = false
  @State entered: boolean = false
  onConfirm: () => void = () => {
  }
  onDismiss: () => void = () => {
  }

  /** 弹窗统一入场（对齐安卓 dialogEnterMotion）：首帧后再触发，弹簧 scale 0.92→1 + fade */
  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  build() {
    // build 只允许单根节点：内容 Column / 分隔线 / 按钮行统一收进一个 Column（原多根节点不满足 UI 语法）
    Column() {
      // 内容区
      Column() {
        // 标题
        if (this.title !== '') {
          Text(this.title)
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ bottom: 12 })
        }

        // 内容
        Text(this.message)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ bottom: 20 })
      }
      .width('100%')
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: 20,
        bottom: 0
      })

      // 分隔线
      Divider()
        .color(DesignColor.DIVIDER)
        .strokeWidth(0.5)

      // 按钮行
      Row() {
        Button(this.cancelText)
          .type(ButtonType.Normal)
          .layoutWeight(1)
          .height(48)
          .backgroundColor(Color.Transparent)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .fontSize(DesignFontSize.BODY)
          .borderRadius(0)
          .onClick(() => {
            this.onDismiss()
            this.controller.close()
          })

        Divider()
          .vertical(true)
          .color(DesignColor.DIVIDER)
          .strokeWidth(0.5)
          .height(48)

        Button(this.confirmText)
          .type(ButtonType.Normal)
          .layoutWeight(1)
          .height(48)
          .backgroundColor(Color.Transparent)
          // 危险操作确认键用 dangerInk（#A8435A），普通确认用品牌绿
          .fontColor(this.isDanger ? DesignColor.DANGER_INK : DesignColor.PRIMARY)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .borderRadius(0)
          .onClick(() => {
            this.onConfirm()
            this.controller.close()
          })
      }
      .width('100%')
    }
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    // 浮层档阴影（§2.4 CSS 0 10px 24px rgba(20,40,30,.10)）；原 #33000000 黑 20% 不符
    .shadow({
      radius: DesignElevation.FLOAT_SHADOW_RADIUS,
      color: DesignColor.SHADOW_COLOR_FLOAT,
      offsetX: 0,
      offsetY: DesignElevation.FLOAT_SHADOW_OFFSET
    })
    .scale({ x: this.entered ? 1 : 0.92, y: this.entered ? 1 : 0.92 })
    .opacity(this.entered ? 1 : 0)
    .onAppear(() => {
      this.playEnterMotion()
    })
  }
}
