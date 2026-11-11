import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
import { OrderInfo } from "../network/vo/OrderInfo"
import { formatOrderTime } from "../utils/TimeUtil"
import { Debouncer } from "../utils/DebounceUtil"
import vibrator from '@ohos.vibrator'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

/** 行内小按钮字号 — 对齐安卓 OrderActionPill 13sp/600；设计系统 8 档无 13 档，按安卓代码取值为文件内常量 */
const ACTION_FONT_SIZE: number = 13

/**
 * 单个自然月的订单合并卡（对齐安卓 MonthGroupCard，差异分析 02 §3.3）：
 * 每月一张卡，行间 0.5 发丝线分隔；行内含状态角标胶囊、订单号·套餐名、
 * 相对时间（今天/昨天/M月d日）、价格（已取消置灰）与按状态的操作按钮（节流防抖 + 触感）。
 * 卡壳不可点，行点击进详情；动作按钮自身消费点击，与行点击不冲突。
 *
 * 卡片画法（DESIGN-SPEC v0.4 §3.1 / §2.4）：surface 白底 + 1px outline 描边 +
 * level1(1vp) 阴影 ≈ CSS 0 2px 12px rgba(20,40,30,.05)。行间分隔线按 §1.3 保持 0.5dp
 * （仅卡内使用）。行上下内边距 12（对齐安卓 OrderPage.kt OrderRow 的 vertical = 12.dp）。
 */
@ComponentV2
export struct OrderMonthCard {
  @Param orders: Array<OrderInfo> = []
  @Event cancelClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  @Event paymentClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  @Event shipmentClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  @Event pickupClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  @Event itemClick: (order: OrderInfo) => void = (_: OrderInfo) => {
  }
  // 动作按钮节流（对齐安卓 rememberDebouncedClick：去支付/去寄件/去取件连点防护）
  private debouncer: Debouncer = new Debouncer()
  // 按压态（scale 0.97 + 100ms，对齐安卓 pressable；V2 组件内部状态用 @Local）
  @Local pressedRowId: number | null = null
  @Local pressedActionKey: string | null = null

  aboutToDisappear(): void {
    this.debouncer.cancel()
  }

  /** 轻触感（对齐安卓 HapticEffect 体系在鸿蒙的最低成本近似；时长与公共组件一致） */
  private haptic(duration: number = 10): void {
    try {
      vibrator.startVibration({ type: 'time', duration: duration }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 忽略不支持的设备
    }
  }

  /**
   * 订单状态 → 左侧图标容器语义色（对齐安卓 OrderRow 397-405）：
   * 待寄件/清洗中 = 蓝；已完成 = primaryLight + primary；已取消 = errorContainer + error；其余 = 橙。
   */
  private iconColors(status: string | null | undefined): [number, number] {
    if (status === OrderStatusEnum.PENDING_SHIPMENT || status === OrderStatusEnum.WASHING) {
      return [resColor('ICON_CONTAINER_BLUE'), resColor('ICON_FOREGROUND_BLUE')]
    }
    if (status === OrderStatusEnum.COMPLETED) {
      return [resColor('PRIMARY_LIGHT'), resColor('PRIMARY')]
    }
    if (status === OrderStatusEnum.CANCELED) {
      return [resColor('ERROR_CONTAINER'), resColor('ERROR')]
    }
    return [resColor('WARNING_CONTAINER'), resColor('WARNING')]
  }

  /**
   * 状态角标胶囊语义色（对齐安卓 OrderRow 418-426）：
   * 与图标容器同语义，但已完成角标文字用 primaryDark（#1E8C5C）。
   */
  private badgeColors(status: string | null | undefined): [number, number] {
    if (status === OrderStatusEnum.PENDING_SHIPMENT || status === OrderStatusEnum.WASHING) {
      return [resColor('ICON_CONTAINER_BLUE'), resColor('ICON_FOREGROUND_BLUE')]
    }
    if (status === OrderStatusEnum.COMPLETED) {
      return [resColor('PRIMARY_LIGHT'), resColor('PRIMARY_DARK')]
    }
    if (status === OrderStatusEnum.CANCELED) {
      return [resColor('ERROR_CONTAINER'), resColor('ERROR')]
    }
    return [resColor('WARNING_CONTAINER'), resColor('WARNING')]
  }

  @Builder
  ActionButton(label: string, btnKey: string, action: () => void) {
    Row() {
      Text(label)
        .fontSize(ACTION_FONT_SIZE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(resColor('ON_PRIMARY'))
    }
    .height(36)
    .padding({ left: 16, right: 16 })
    .justifyContent(FlexAlign.Center)
    // 行内小按钮档：36 高 / radiusMd 12 / 13sp·600（对齐安卓 OrderActionPill）
    .borderRadius(DesignDimens.RADIUS_MD)
    .backgroundColor(resColor('PRIMARY'))
    // 按压反馈（对齐安卓 pressable scale 0.97 + 100ms）
    .scale({ x: this.pressedActionKey === btnKey ? 0.97 : 1, y: this.pressedActionKey === btnKey ? 0.97 : 1 })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedActionKey = btnKey
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedActionKey = null
      }
    })
    .onClick(() => {
      this.haptic()
      this.debouncer.throttle(action, 500)
    })
  }

  @Builder
  OrderRow(order: OrderInfo) {
    Row() {
      // 左侧图标容器（对齐安卓 IconBox 36/20，顶部 +4 与角标文字对齐）
      Column() {
        SymbolGlyph($r('sys.symbol.laundry'))
          .fontSize(20)
          .fontColor([this.iconColors(order.status)[1]])
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(this.iconColors(order.status)[0])
      .justifyContent(FlexAlign.Center)
      .margin({ top: 4, right: 4 })

      // 主体信息
      Column() {
        // 状态角标胶囊（对齐安卓 24dp 高 / radiusFull / 底色与文字成对取语义色）
        Row() {
          Text(getOrderStatusDescription(order.status))
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(this.badgeColors(order.status)[1])
        }
        .height(24)
        .padding({ left: 8, right: 8 })
        .borderRadius(DesignDimens.RADIUS_FULL)
        .backgroundColor(this.badgeColors(order.status)[0])
        .justifyContent(FlexAlign.Center)

        // 单号 · 套餐（bodySmall 12 + tnum；角标底→单号行 8）
        Text(`${order.orderNo ?? ""} · ${order.laundryPackageVo?.itemName ?? ""}`)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(resColor('TEXT_SECONDARY'))
          .fontFeature(DesignFontFeature.TNUM)
          .margin({ top: 8 })
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .maxLines(1)

        // 相对时间（bodySmall 12 + tnum；单号→时间 8）
        Text(formatOrderTime(order.createdAt))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(resColor('TEXT_SECONDARY'))
          .fontFeature(DesignFontFeature.TNUM)
          .margin({ top: 8 })
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .maxLines(1)
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      // 右侧价格 + 操作按钮（金额顶部与角标对齐，下距 20 后接操作区）
      Column() {
        Text(`¥${(order.payPrice ?? 0).toFixed(2)}`)
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          // 对齐安卓 OrderPage.kt OrderRow：AmountMedium.copy(fontWeight = Bold)
          .fontWeight(DesignFontWeight.BOLD)
          // 金额两位小数（N1）+ tnum（G5）；已取消置灰（对齐安卓 textSecondary），余态 primaryDark（绿字红线）
          .fontFeature(DesignFontFeature.TNUM)
          .fontColor(order.status === OrderStatusEnum.CANCELED ?
            resColor('TEXT_SECONDARY') : resColor('PRIMARY_DARK'))

        Column() {
          if (order.status === OrderStatusEnum.PENDING_PAYMENT) {
            // 待支付操作区（对齐安卓 OrderPage.kt:526-544）：去支付在上、取消订单在下，
            // 右对齐竖排，中间拉开 12；取消订单用 error 语义色（危险操作）
            Column() {
              this.ActionButton(resString('go_pay'), `${order.orderId}:pay`, () => {
                this.paymentClick(order)
              })

              // 取消订单 — 自绘文字动作，热区 44（安卓 OrderPage.kt:486-494，原 40 不达标）
              Row() {
                Text($r('app.string.cancel_order'))
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(resColor('ERROR'))
              }
              .constraintSize({ minHeight: 44 })
              .padding({ left: 4, right: 4 })
              // 与「去支付」胶囊间距 12（对齐安卓 Spacer(height = spaceSm)）
              .margin({ top: DesignDimens.SPACE_SM })
              .justifyContent(FlexAlign.Center)
              .scale({
                x: this.pressedActionKey === `${order.orderId}:cancel` ? 0.97 : 1,
                y: this.pressedActionKey === `${order.orderId}:cancel` ? 0.97 : 1
              })
              .animation({ duration: 100, curve: Curve.EaseOut })
              .onTouch((event: TouchEvent) => {
                if (event.type === TouchType.Down) {
                  this.pressedActionKey = `${order.orderId}:cancel`
                } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                  this.pressedActionKey = null
                }
              })
              .onClick(() => {
                this.haptic(20)
                this.cancelClick(order)
              })
            }
            .alignItems(HorizontalAlign.End)
          } else if (order.status === OrderStatusEnum.PENDING_SHIPMENT) {
            this.ActionButton(resString('go_ship'), `${order.orderId}:ship`, () => {
              this.shipmentClick(order)
            })
          } else if (order.status === OrderStatusEnum.WASHING) {
            Text($r('app.string.step_washing'))
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(resColor('TEXT_SECONDARY'))
          } else if (order.status === OrderStatusEnum.READY_FOR_PICKUP) {
            this.ActionButton(resString('go_pickup'), `${order.orderId}:pickup`, () => {
              this.pickupClick(order)
            })
          } else {
            Text(getOrderStatusDescription(order.status))
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(resColor('TEXT_SECONDARY'))
          }
        }
        .alignItems(HorizontalAlign.End)
        .margin({ top: 20 })
      }
      .alignItems(HorizontalAlign.End)
    }
    .width("100%")
    .alignItems(VerticalAlign.Top)
    .padding({
      left: DesignDimens.CARD_PADDING,
      right: DesignDimens.CARD_PADDING,
      top: 12,
      bottom: 12
    })
    // 行按压反馈（对齐安卓 pressable scale 0.97 + 100ms；动作按钮按下时行不缩放）
    .scale({
      x: this.pressedRowId === order.orderId && this.pressedActionKey === null ? 0.97 : 1,
      y: this.pressedRowId === order.orderId && this.pressedActionKey === null ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedRowId = order.orderId ?? null
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedRowId = null
      }
    })
    .onClick(() => {
      this.itemClick(order)
    })
  }

  build() {
    Column() {
      ForEach(this.orders, (order: OrderInfo, index: number) => {
        this.OrderRow(order)
        if (index < this.orders.length - 1) {
          Divider()
            .strokeWidth(0.5)
            .color(resColor('HAIRLINE'))
            .margin({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
        }
      }, (order: OrderInfo) => order.orderId?.toString())
    }
    .width("100%")
    .backgroundColor(resColor('SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 1, color: resColor('OUTLINE') })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: resColor('SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: 2
    })
  }
}
