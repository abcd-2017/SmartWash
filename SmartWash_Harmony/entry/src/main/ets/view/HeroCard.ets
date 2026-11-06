import { DesignColor, DesignDimens, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionTiming } from '../constant/DesignSystem'
import { OrderVo } from '../network/vo/OrderVo'
import { OrderStatusEnum, getOrderStatusDescription } from '../constant/OrderStatus'

/**
 * 首页 Hero 状态卡 —— 对齐安卓 IndexPage.kt:288-453 `StatusHeroCard`（PR #47 后新形态）。
 *
 * 品牌渐变底 + 白字，全页唯一视觉锚点（DESIGN.md「一屏一锚点」）：
 * - 有进行中订单：滚筒圆（进度弧跟随真实状态）+ 状态大字 + 订单号 + 四步进度点行，整卡可点进订单详情；
 * - 无订单：滚筒圆静止态 + 「暂无订单」+ 引导文案 + 白色 CTA 胶囊（规范 §3.1 Hero 卡变体，
 *   §3.6 空态必须给行动按钮），CTA 点击进预约洗衣。
 *
 * 步骤点/连线在品牌渐变底上用白（激活）与 outline（未激活）——primary 绿在绿渐变上不可见，禁用。
 * 进度弧由真实订单状态驱动，真实状态变化时经 animateTo 弹簧推进（对齐安卓 animateFloatAsState）。
 */

/** Hero 步骤标签（对齐安卓 IndexPage.kt:370-375 step_received/washing/dried/pickup） */
const HERO_STEPS: string[] = ['已接单', '清洗中', '烘干中', '待取件']

/** 滚筒圆（Hero 专用，进度可驱动重绘）——几何基准 64，对齐安卓 DrumMark.kt */
@ComponentV2
struct HeroDrum {
  @Param progress: number = 0
  @Param sizeValue: number = 64
  @Param tint: number = Color.White
  @Param trackTint: number = 0x52FFFFFF      // 白 32%（安卓 Color.White.copy(alpha = 0.32f)）
  @Param innerRingTint: number = 0x73FFFFFF  // 白 45%（安卓 Color.White.copy(alpha = 0.45f)）
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  aboutToAppear(): void {
    this.drawMark()
  }

  // 真实状态驱动重绘（安卓 animateFloatAsState 的等价物；Canvas 不会随 build 自动重绘）
  @Monitor('progress')
  onProgressChange(): void {
    this.drawMark()
  }

  private drawMark(): void {
    const size: number = this.sizeValue
    const scaleFactor: number = size / 64
    const cx: number = size / 2
    const cy: number = size / 2

    this.ctx.clearRect(0, 0, size, size)
    this.ctx.lineCap = 'round'

    // 外环 track
    this.ctx.lineWidth = 2.5 * scaleFactor
    this.ctx.strokeStyle = this.trackTint
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, 28 * scaleFactor, 0, Math.PI * 2, false)
    this.ctx.stroke()

    // 进度弧（起始 -90°，扫角 360° × progress）
    const clamped: number = Math.min(1, Math.max(0, this.progress))
    if (clamped > 0) {
      this.ctx.lineWidth = 2.5 * scaleFactor
      this.ctx.strokeStyle = this.tint
      this.ctx.beginPath()
      this.ctx.arc(cx, cy, 28 * scaleFactor, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * clamped, false)
      this.ctx.stroke()
    }

    // 内环
    this.ctx.lineWidth = 2 * scaleFactor
    this.ctx.strokeStyle = this.innerRingTint
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, 17 * scaleFactor, 0, Math.PI * 2, false)
    this.ctx.stroke()

    // 单条水波线（穿过圆环）
    this.ctx.lineWidth = 2.4 * scaleFactor
    this.ctx.strokeStyle = this.tint
    this.ctx.beginPath()
    this.ctx.moveTo(cx - 9 * scaleFactor, cy)
    this.ctx.bezierCurveTo(
      cx - 6 * scaleFactor, cy - 3.5 * scaleFactor,
      cx - 3 * scaleFactor, cy + 2 * scaleFactor,
      cx, cy - 0.5 * scaleFactor
    )
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

@ComponentV2
export struct HeroCard {
  @Param orderList: Array<OrderVo> = []
  @Event onOrderClick: (orderId: number) => void = () => {}
  @Event onBookingClick: () => void = () => {}

  @Local hasOrder: boolean = false
  @Local orderId: number = 0
  @Local orderNo: string = ''
  @Local statusText: string = '暂无订单'
  @Local heroProgress: number = 0
  @Local currentStep: number = 0

  aboutToAppear(): void {
    this.syncOrder()
  }

  @Monitor('orderList')
  onOrderListChange(): void {
    this.syncOrder()
  }

  /** 订单状态 → hero 进度（0..1），对齐安卓 orderProgress（IndexPage.kt:269-279） */
  private progressOf(status: string | null): number {
    switch (status) {
      case OrderStatusEnum.PENDING_PAYMENT:
        return 0.1
      case OrderStatusEnum.PENDING_SHIPMENT:
        return 0.25
      case OrderStatusEnum.RECEIVED:
        return 0.35
      case OrderStatusEnum.WASHING:
        return 0.55
      case OrderStatusEnum.DRIED:
        return 0.7
      case OrderStatusEnum.IN_DELIVERY:
        return 0.85
      case OrderStatusEnum.READY_FOR_PICKUP:
        return 0.95
      case OrderStatusEnum.COMPLETED:
        return 1.0
      default:
        return 0
    }
  }

  /** 订单状态 → 当前步骤（0..3），对齐安卓 StatusHeroCard 的 currentStep 映射 */
  private stepOf(status: string | null): number {
    switch (status) {
      case OrderStatusEnum.WASHING:
        return 1
      case OrderStatusEnum.DRIED:
        return 2
      case OrderStatusEnum.READY_FOR_PICKUP:
        return 3
      default:
        return 0
    }
  }

  /** 订单数据 → 展示态（含进度弧动画目标） */
  private syncOrder(): void {
    const order: OrderVo | null = this.orderList.length > 0 ? this.orderList[0] : null
    this.hasOrder = order !== null
    if (order !== null) {
      this.orderId = order.orderId ?? 0
      this.orderNo = order.orderNo ?? ''
      this.statusText = getOrderStatusDescription(order.status)
      this.currentStep = this.stepOf(order.status)
    } else {
      this.orderId = 0
      this.orderNo = ''
      this.statusText = '暂无订单'
      this.currentStep = 0
    }
    const target: number = order !== null ? this.progressOf(order.status) : 0
    animateTo(MotionTiming.ENTER, () => {
      this.heroProgress = target
    })
  }

  build() {
    Column() {
      Stack() {
        // 内容（滚筒圆 + 状态/订单号 + 步骤/CTA）
        // 对角黑纱作为内容列背景叠在 Stack 品牌渐变底上（对齐安卓 StatusHeroCard scrim：
        // 白字 on 品牌绿渐变仅 3.72:1，叠纱后 ≥5.1:1）。用内容列背景而非独立覆盖层，
        // 避免 wrap-content Stack 内百分比高度解析不稳的问题。
        Column() {
          // 滚筒圆 + 状态/订单号
          Row() {
            HeroDrum({ progress: this.heroProgress })
              .margin({ right: DesignDimens.SPACE_MD })

            Column() {
              Text(this.statusText)
                .fontSize(DesignFontSize.STATUS_LARGE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(Color.White)
                .maxLines(1)

              Text(this.hasOrder ? `订单号 ${this.orderNo}` : '从下方快捷服务开始预约')
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(Color.White)
                .fontFeature(DesignFontFeature.TNUM)
                .maxLines(1)
                .margin({ top: 4 })
            }
            .alignItems(HorizontalAlign.Start)
            .layoutWeight(1)
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)

          if (this.hasOrder) {
            // 步骤点行 —— 激活白 / 未激活 outline（绿渐变底上 primary 不可见）
            Row() {
              ForEach(HERO_STEPS, (_label: string, index: number) => {
                Circle()
                  .width(8)
                  .height(8)
                  .backgroundColor(index <= this.currentStep ? Color.White : DesignColor.OUTLINE)
                  .animation(MotionTiming.ENTER)

                if (index < HERO_STEPS.length - 1) {
                  Row()
                    .layoutWeight(1)
                    .height(2)
                    .backgroundColor(index < this.currentStep ? Color.White : DesignColor.OUTLINE)
                    .animation(MotionTiming.ENTER)
                }
              }, (label: string) => label)
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)
            .margin({ top: DesignDimens.SPACE_XL })

            // 步骤标签行 —— 首尾贴边、中间居中
            Row() {
              ForEach(HERO_STEPS, (label: string, index: number) => {
                Text(label)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(Color.White)
                  .layoutWeight(1)
                  .textAlign(index === 0 ? TextAlign.Start : (index === HERO_STEPS.length - 1 ? TextAlign.End : TextAlign.Center))
              }, (label: string) => label)
            }
            .width('100%')
            .margin({ top: DesignDimens.SPACE_XS })
          } else {
            // 空态 CTA —— 规范 §3.1 Hero 卡变体「白色 CTA 胶囊」：白底 + 品牌深绿字。
            // 对齐安卓 U-102：16sp/600（§10 绿色文字红线：绿色文字仅限 ≥16sp 且 weight ≥600），
            // 上下 padding 12 → 行高 24 + 24 = 48 高（≥44 触控）；左对齐（安卓 Column 默认 Start）。
            Text('预约洗衣')
              .fontSize(DesignFontSize.BODY)
              // 行盒对齐安卓 bodyLarge 16/24：上下 padding 12 + 行高 24 = 48 高（对齐安卓 U-102）
              .lineHeight(DesignFontSize.BODY + 8)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.PRIMARY_DARK)
              .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
              .borderRadius(DesignDimens.RADIUS_FULL)
              .backgroundColor(Color.White)
              .margin({ top: DesignDimens.SPACE_MD })
              .onClick(() => {
                this.onBookingClick()
              })
          }
        }
        .width('100%')
        .padding(DesignDimens.CARD_PADDING)
        // CTA 左对齐：内容列显式 Start（ArkUI Column 默认 Center 会居中 CTA）
        .alignItems(HorizontalAlign.Start)
        // 对角黑纱（左上黑 20% → 右下透明），作为内容列背景叠在品牌渐变底上。
        // 透明端必须用 Color.Transparent：数值 0x00000000 在渐变 stop 上会被当作不透明黑。
        .linearGradient({
          angle: 135,
          colors: [[DesignColor.SCRIM_BLACK_20, 0], [Color.Transparent, 1]]
        })
      }
      .width('100%')
      // 品牌渐变 135°（规范 §1.3 gradient.hero）：浅 #2D9B6A → #1E8C5C
      .borderRadius(DesignDimens.CARD_RADIUS)
      .clip(true)
      .linearGradient({
        angle: 135,
        colors: [[DesignColor.PRIMARY, 0], [DesignColor.PRIMARY_DARK, 1]]
      })
      .onClick(() => {
        if (this.hasOrder) {
          this.onOrderClick(this.orderId)
        }
      })
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 8 })
  }
}
