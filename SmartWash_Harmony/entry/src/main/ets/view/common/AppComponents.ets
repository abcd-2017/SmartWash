// 清氧设计系统 — 通用 UI 组件
// 参考 Android 端 AppComponents.kt

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../constant/DesignSystem'
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
      radius: DesignElevation.LEVEL_1,
      color: 'rgba(20,40,30,0.05)',
      offsetX: 0,
      offsetY: 2
    })
  }
}

// ========== AppButton 统一按钮 ==========

/**
 * 统一按钮（规范 §3.3）
 * 默认 = 主按钮：绿底(#2D9B6A)、14vp 圆角、52vp 高度、文字 18 档 SemiBold
 * 支持 loading 状态显示 LoadingProgress
 *
 * 次按钮形态（设置页「退出登录」等）：传 bgColor / textColor / textSize 覆盖；
 * 三者不传时与主按钮完全一致，既有调用方行为不变。
 */
@ComponentV2
export struct AppButton {
  @Param text: string = ''
  // 命名注意：不能叫 enabled（与 CustomComponent 基类的同名属性方法冲突导致编译错误）
  @Param isEnabled: boolean = true
  @Param loading: boolean = false
  // 次按钮形态的可选覆盖项（默认 null / 原值 = 主按钮逻辑）
  @Param textColor: ResourceColor | null = null
  @Param bgColor: ResourceColor | null = null
  @Param textSize: number = DesignFontSize.SECTION_TITLE
  @Event click: () => void = () => {
  }

  build() {
    Button() {
      if (this.loading) {
        LoadingProgress()
          .width(22)
          .height(22)
          .color(Color.White)
      } else {
        Text(this.text)
          .fontSize(this.textSize)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(this.textColor ?? DesignColor.ON_PRIMARY)
      }
    }
    .type(ButtonType.Capsule)
    .width('100%')
    .height(52)
    .borderRadius(DesignDimens.BUTTON_RADIUS)
    .backgroundColor(this.bgColor ?? (this.isEnabled && !this.loading ? DesignColor.PRIMARY : DesignColor.TEXT_TERTIARY))
    .stateEffect(this.isEnabled && !this.loading)
    .enabled(this.isEnabled && !this.loading)
    .onClick(() => {
      if (this.isEnabled && !this.loading) {
        // 触觉反馈
        try {
          vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
        } catch (err) {
          // 忽略不支持的设备
        }
        this.click()
      }
    })
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
  onDismiss: () => void = () => {
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
      .shadow({
        radius: DesignElevation.FLOAT_LAYER,
        color: '#33000000',
        offsetX: 0,
        offsetY: 4
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
  }
}

// ========== AppTabBar 标签栏 ==========

/**
 * 标签栏
 * 文字标签 + 底部短横线指示器
 * 选中项绿色加粗，未选中灰色
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
            .fontSize(this.selectedIndex === index ? DesignFontSize.CARD_TITLE : DesignFontSize.BODY)
            .fontWeight(this.selectedIndex === index ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
            .fontColor(this.selectedIndex === index ? DesignColor.PRIMARY : DesignColor.TEXT_SECONDARY)
            .padding({ left: 12, right: 12, top: 8, bottom: 8 })

          // 底部指示器
          if (this.selectedIndex === index) {
            Row()
              .width(20)
              .height(3)
              .borderRadius(1.5)
              .backgroundColor(DesignColor.PRIMARY)
          } else {
            Row()
              .width(20)
              .height(3)
          }
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
  onConfirm: () => void = () => {
  }
  onDismiss: () => void = () => {
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
      .shadow({
        radius: DesignElevation.LEVEL_4,
        color: '#33000000',
        offsetX: 0,
        offsetY: 4
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
  }
}
