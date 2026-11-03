import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { AppButton, CircleIconBox, EmptyState, LoadingState } from "../view/common/AppComponents"
import { PickupDeliveryParam } from "./PickupDelivery"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionTiming } from "../constant/DesignSystem"

@Builder
export function PaymentSuccessBuilder() {
  PaymentSuccess()
}

@Component
struct PaymentSuccess {
  @State orderId: number | null = null
  @State orderInfo: OrderInfo | null = null
  // 加载/失败态（对齐安卓全屏 Loading + Error toast，失败不再静默）
  @State isLoading: boolean = true
  @State loadFailed: boolean = false
  // 对勾入场（recap §4-F3）：本页只由真实支付成功抵达，动画由真实事件驱动；
  // scale 0.6→1 + 淡入，弹簧一次性进场（reduced motion 全局未接入，见报告裁决记录）
  @State checkEntered: boolean = false
  // 按压反馈（G3）：文字链 scale 0.97 + onTouch，对齐安卓 pressable（PaySuccessPage.kt:334-340）
  @State pressedKey: string | null = null

  async aboutToAppear() {
    // 安全取参（评审 #31）：参数缺失/非法时返回 null，不再 as 强转崩溃
    this.orderId = getNumberParam(RouteNames.PAYMENT_SUCCESS)
    this.playCheckEnter()
    await this.loadOrder()
  }

  /** 拉取订单详情（首载与「重试」共用，recap §4-F1/S1） */
  async loadOrder() {
    if (this.orderId === null) {
      this.loadFailed = true
      this.isLoading = false
      return
    }
    this.isLoading = true
    this.loadFailed = false
    let res = await getOrderInfo(this.orderId)
    if (res.code === HttpStatusCode.Ok) {
      this.orderInfo = res.data
    } else {
      showToastSafety(res.message, "订单信息加载失败")
      this.loadFailed = true
    }
    this.isLoading = false
  }

  /** 对勾入场动画（首帧后再触发，保证初始 0.6/0 先上屏，过渡才可见） */
  private playCheckEnter() {
    setTimeout(() => {
      animateTo(MotionTiming.ENTER, () => {
        this.checkEntered = true
      })
    }, 16)
  }

  /** 返回首页：对齐 Android popBackStack(HOME) —— 回退到首页，避免支付成功页残留返回栈 */
  private backToHome() {
    const homeIndex = pathStack.popToName(RouteNames.HOME)
    if (homeIndex < 0) {
      pathStack.pop()
    }
  }

  /** 立即寄件：对齐 Android popUpTo(PaySuccess, inclusive) —— 回退到首页再入栈寄件页 */
  private goShipNow() {
    const param: PickupDeliveryParam = new PickupDeliveryParam()
    param.order = this.orderInfo
    param.isPickup = false
    const homeIndex = pathStack.popToName(RouteNames.HOME)
    if (homeIndex < 0) {
      pathStack.pop()
    }
    pathStack.pushPathByName(RouteNames.PICKUP_DELIVERY, param)
  }

  @Builder
  PageHeader() {
    Row() {
      // 48dp 返回热区（recap G4：Android PageHeader 48dp 热区 + 22/600 标题）
      Column() {
        SymbolGlyph($r('sys.symbol.chevron_left'))
          .fontSize(24)
          .fontColor([DesignColor.TEXT_PRIMARY])
      }
      .width(48)
      .height(48)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .onClick(() => {
        pathStack.pop()
      })

      Text("支付成功")
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ left: 4 })
    }
    .width("100%")
    .height(56)
    .alignItems(VerticalAlign.Center)
  }

  /**
   * 卡内「标签 — 值」信息行（对齐 Android InfoRow：bodySmall 12 + tnum、行高 16）。
   * 字号取 CAPTION 12：Android `bodySmall` 槽位在 Type.kt:176-181 映射为 caption 12/16/400，
   * InfoRow.kt:33,45 标签与值均用 bodySmall（PaySuccessPage.kt:219-221 另传 valueStyle=bodySmall+tnum）。
   */
  @Builder
  InfoLine(title: string, value: string) {
    Row() {
      Text(title)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)

      Blank()

      Text(value)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .fontFeature(DesignFontFeature.TNUM)
    }
    .width("100%")
    .height(16)
    .justifyContent(FlexAlign.SpaceBetween)
    .alignItems(VerticalAlign.Center)
  }

  build() {
    NavDestination() {
      Stack() {
        Column() {
          this.PageHeader()

          // 庆祝卡（recap §4-V1/V2/V3）：CircleIconBox 44 primaryLight + 勾 24 primary；
          // 标题 headlineLarge 28/700 primaryDark；内边距上 40 / 下 44
          Column() {
            CircleIconBox({
              icon: $r('sys.symbol.checkmark'),
              sizeValue: 44,
              iconSize: 24,
              containerColor: DesignColor.PRIMARY_LIGHT,
              iconColor: DesignColor.PRIMARY
            })
              .scale({ x: this.checkEntered ? 1 : 0.6, y: this.checkEntered ? 1 : 0.6 })
              .opacity(this.checkEntered ? 1 : 0)

            Text("支付成功")
              .fontSize(DesignFontSize.HEADLINE)
              .fontWeight(DesignFontWeight.BOLD)
              .fontColor(DesignColor.PRIMARY_DARK)
              .margin({ top: 24 })

            Text("感谢使用智慧洗衣")
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .margin({ top: 8 })
          }
          .width("100%")
          .padding({ top: 40, bottom: 44 })
          // 对齐安卓 PaySuccessPage.kt:149-193：庆祝卡按 DESIGN-SPEC v0.4 §3.1 唯一卡片画法
          // 取 surface 白底 + 1px outline 描边 + level1 阴影（旧实现为无描边无阴影的 primaryLight 块）
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.LEVEL_1,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: 2
          })
          .margin({ top: DesignDimens.SECTION_SPACING })
          .alignItems(HorizontalAlign.Center)

          // 订单信息卡（recap §4-V4：bodySmall 12 + tnum、行高 16、行距 16）
          Text("订单信息")
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .width("100%")
            .margin({ top: DesignDimens.SECTION_SPACING, bottom: 12 })

          Column({ space: DesignDimens.SPACE_MD }) {
            this.InfoLine("订单编号", `${this.orderInfo?.orderNo ?? ""}`)
            this.InfoLine("支付金额", `¥${(this.orderInfo?.payPrice ?? 0).toFixed(2)}`)
            this.InfoLine("下单时间", `${this.orderInfo?.createdAt ?? ""}`)
          }
          .width("100%")
          .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING, top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_XL })
          // 对齐安卓 PaySuccessPage.kt:204-240：surface 白底 + 1px outline 描边 + level1 阴影
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.LEVEL_1,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: 2
          })

          // 寄存柜提示卡（recap §4-V5：补「已存入 X 号柜」行，共 4 行）
          Text("寄存柜信息")
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .width("100%")
            .margin({ top: DesignDimens.SPACE_XL, bottom: 12 })

          Row() {
            // 物流图标容器（对齐安卓 LocalShipping IconBox）
            Column() {
              SymbolGlyph($r('sys.symbol.truck_box_fill'))
                .fontSize(20)
                .fontColor([DesignColor.PRIMARY])
            }
            .width(36)
            .height(36)
            .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
            .backgroundColor(DesignColor.PRIMARY_LIGHT)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)

            Column({ space: DesignDimens.SPACE_XS }) {
              Text("寄存柜信息")
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_PRIMARY)

              // 柜号空值兜底显示 "-"（空值语义），不再误显示 "-1柜"（评审 #34，安卓待对齐）
              Text(`已存入 ${this.orderInfo?.lockersVo?.lockerNumber ?? "-"} 号柜`)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .fontFeature(DesignFontFeature.TNUM)

              Text(`请前往寄存柜 ${this.orderInfo?.lockersVo?.lockerNumber ?? "-"}柜存放衣物`)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .fontFeature(DesignFontFeature.TNUM)

              Text("寄存柜将在24小时内处理您的订单")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }
            .alignItems(HorizontalAlign.Start)
            .layoutWeight(1)
            .padding({ top: 8 })
            .margin({ left: 12 })
          }
          .width("100%")
          .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING, top: DesignDimens.SPACE_XL, bottom: DesignDimens.SPACE_MD })
          // 对齐安卓 PaySuccessPage.kt:252-300：surface 白底 + 1px outline 描边 + level1 阴影
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 1, color: DesignColor.OUTLINE })
          .shadow({
            radius: DesignElevation.LEVEL_1,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: 2
          })
          .alignItems(VerticalAlign.Top)

          Blank()

          // 底部操作区（recap §4-F2：主按钮 + 48dp 热区「返回首页」文字链）
          Column() {
            AppButton({
              text: "立即寄件",
              click: () => {
                this.goShipNow()
              }
            })

            Text("返回首页")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .width("100%")
              .textAlign(TextAlign.Center)
              .padding({ top: 16, bottom: 16 })
              // 按压反馈（G3）：scale 0.97 + onTouch，对齐安卓 pressable
              .scale({ x: this.pressedKey === 'back' ? 0.97 : 1, y: this.pressedKey === 'back' ? 0.97 : 1 })
              .onTouch((event: TouchEvent) => {
                if (event.type === TouchType.Down) {
                  this.pressedKey = 'back'
                } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                  this.pressedKey = null
                }
              })
              .onClick(() => {
                this.backToHome()
              })
          }
          .width("100%")
          .padding({ top: DesignDimens.SPACE_MD })
        }
        .width("100%")
        .height("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .alignItems(HorizontalAlign.Start)

        // 加载态
        if (this.isLoading) {
          // 加载态（对齐安卓 LoadingState：骨架屏，§3.6 禁转圈）；遮罩淡入淡出近似 Crossfade（recap §4-I1）
          LoadingState()
          .width("100%")
          .height("100%")
          .backgroundColor(DesignColor.BACKGROUND)
          .zIndex(2)
          .transition(TransitionEffect.OPACITY)
        }

        // 失败态（recap §4-F1/S1）：EmptyState 母题 + 「重试」行动按钮
        if (!this.isLoading && this.loadFailed) {
          Column() {
            EmptyState({
              message: "加载失败，请重试",
              action: () => {
                AppButton({
                  text: "重试",
                  click: () => {
                    this.loadOrder()
                  }
                })
                .margin({ top: DesignDimens.SPACE_MD })
              }
            })
          }
          .width("100%")
          .height("100%")
          .backgroundColor(DesignColor.BACKGROUND)
          .justifyContent(FlexAlign.Center)
          .zIndex(2)
          .transition(TransitionEffect.OPACITY)
        }
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}
