import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { calculationOrder, getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { getCanUseCoupon } from "../network/api/CouponApi"
import { UserCouponVo } from "../network/vo/UserCouponVo"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { payment } from "../network/api/PaymentApi"
import { showToastSafety } from "../utils/ToastUtil"
import { PaymentType } from "../constant/PaymentType"
import { Debouncer } from "../utils/DebounceUtil"
import { AppButton, AppConfirmDialog, EmptyState, LoadingState, SelectionDot } from "../view/common/AppComponents"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"

@Builder
export function PaymentBuilder() {
  Payment()
}

@Component
struct Payment {
  @State orderId: number | null = null
  @State orderInfo: OrderInfo | null = null
  @State userCoupon: Array<UserCouponVo> | null = []
  @State userInfo: UserInfoVo | null = null
  @State selectCoupon: UserCouponVo | null = null
  @State showSheet: boolean = false
  @State selectCouponId: number | null = null
  // 支付方式选中态（当前仅余额支付，保留状态位与 Android selectedPaymentMethod 对齐）
  @State selectedPaymentMethod: string = "balance"
  // 首载骨架门控（对齐 Android Crossfade(orderInfo == null) → LoadingState）
  @State isLoading: boolean = true
  // 支付请求进行中标记：AppButton loading 展示并拦截连点，防止重复扣款（评审 #12）
  @State paying: boolean = false
  // 确认支付二次确认弹窗（资金链路）：确认后立即关窗再发起请求——
  // 勿复制安卓旧版「等 Success 才关窗」缺陷（确认键持续可点 + 后端无幂等 = 连点重复扣款入口）
  private confirmPayController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      message: "确定要支付？",
      onConfirm: () => {
        this.doPay()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })
  // 余额不足弹窗（对齐安卓 AppConfirmDialog「余额不足」→ 去充值）
  private insufficientDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: "余额不足",
      message: "当前余额不足，请先充值",
      confirmText: "去充值",
      onConfirm: () => {
        pathStack.pushPathByName(RouteNames.RECHARGE, null)
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })
  // 选券试算节流：calculationOrder 后端无防重，Radio onChange 与行点击可能双触发，500ms 内只发一次
  private calcThrottler: Debouncer = new Debouncer()
  // 「去领取」跳转节流（对齐 Android rememberDebouncedClick，recap §3-I4 / N8）
  private claimThrottler: Debouncer = new Debouncer()

  async aboutToAppear() {
    // 安全取参（评审 #31 / #6）：统一走 getNumberParam，缺失/非法返回 null（Number() 显式转换，NaN 同视为缺失）
    this.orderId = getNumberParam(RouteNames.PAYMENT)
    if (this.orderId !== null) {
      let res = await getOrderInfo(this.orderId)
      if (res.code === HttpStatusCode.Ok) {
        this.orderInfo = res.data
      } else {
        showToastSafety(res.message, "订单信息加载失败")
      }
    }
    this.isLoading = false
  }

  async getCurrUserInfo() {
    let res = await getUserInfo()
    if (res.code === HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      showToastSafety(res.message, "用户信息加载失败")
    }
  }

  async getUserCoupons() {
    let couponRes = await getCanUseCoupon(this.orderId ?? -1)
    if (couponRes.code === HttpStatusCode.Ok) {
      this.userCoupon = couponRes.data
    } else {
      showToastSafety(couponRes.message, "优惠券加载失败")
    }
  }

  /**
   * 试算订单金额（评审 #17）：coupon 传 null 表示"不使用优惠券"。
   *
   * 后端 OrdersServiceImpl.calculationOrder 仅认 userCouponId == null || 0 为不使用优惠券，
   * 传 -1 会抛"优惠券异常"（#31），故此处统一传 0。
   */
  async calculationOrder(coupon: UserCouponVo | null) {
    this.selectCouponId = coupon?.userCouponId ?? null
    this.showSheet = false
    this.selectCoupon = coupon
    if (this.orderId !== null) {
      let res = await calculationOrder(this.orderId, coupon?.userCouponId ?? 0)
      if (res.code === HttpStatusCode.Ok) {
        this.orderInfo = res.data
      } else {
        showToastSafety(res.message, "优惠券试算失败")
      }
    }
  }

  /** 选券试算入口（节流防连点，见 calcThrottler） */
  selectCouponWithThrottle(coupon: UserCouponVo | null) {
    this.calcThrottler.throttle(() => {
      this.calculationOrder(coupon)
    }, 500)
  }

  aboutToDisappear(): void {
    this.calcThrottler.cancel()
    this.claimThrottler.cancel()
  }

  /**
   * 发起支付（资金链路）：
   * 仅在「确认支付？」弹窗确认后调用——弹窗确认回调先关窗再进本方法，
   * 配合 paying 标记 + AppButton loading 形成两层防重。
   */
  async doPay() {
    if (this.paying) {
      return
    }
    this.paying = true
    try {
      let res = await payment({
        orderId: this.orderId,
        // PaymentType 已对齐安卓 string 契约，直接传枚举值
        paymentType: PaymentType.PURSE,
        userCouponId: this.selectCoupon?.userCouponId ?? null
      })
      if (res.code === HttpStatusCode.Ok) {
        // 对齐 Android：支付成功不弹 toast，直接导航（recap §3-T2）
        this.goToPaySuccess()
      } else {
        showToastSafety(res.message, "支付失败，请稍后重试")
      }
    } finally {
      this.paying = false
    }
  }

  /** 支付成功导航：对齐 Android popUpTo(HOME) —— 回退到首页再入栈支付成功页，避免支付页残留返回栈 */
  private goToPaySuccess() {
    const homeIndex = pathStack.popToName(RouteNames.HOME)
    if (homeIndex < 0) {
      pathStack.pop()
    }
    pathStack.pushPathByName(RouteNames.PAYMENT_SUCCESS, this.orderId)
  }

  /** 确认支付按钮入口：余额充足弹二次确认，不足弹「余额不足」去充值 */
  onPayClick() {
    if (this.paying) {
      return
    }
    if ((this.userInfo?.balance ?? 0) >= (this.orderInfo?.payPrice ?? 0)) {
      this.confirmPayController.open()
    } else {
      this.insufficientDialogController.open()
    }
  }

  /**
   * 门槛文案（recap §3-T1）：无门槛 / 满 X 元可用。
   * 对齐 Android coupon_min_amount_format 的正确语义（鸿蒙 Coupon.ets:218-222 已有实现）；
   * Android 支付页当前仍输出错误的「满X减X」，本端以正确语义为准。
   */
  private thresholdText(coupon: UserCouponVo): string {
    return coupon.couponVo?.threshold === 0 ?
      "无门槛" :
      `满${coupon.couponVo?.threshold?.toFixed(0)}元可用`
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

      Text("支付")
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ left: 4 })
    }
    .width("100%")
    .height(56)
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(DesignColor.BACKGROUND)
  }

  /** 卡内「标签 — 值」信息行（对齐 Android InfoLine：bodySmall 12 + tnum、纵向 padding 4） */
  @Builder
  InfoLine(label: string, value: string) {
    Row() {
      Text(label)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)

      Blank()

      Text(value)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .fontFeature(DesignFontFeature.TNUM)
    }
    .width("100%")
    .padding({ top: 4, bottom: 4 })
    .justifyContent(FlexAlign.SpaceBetween)
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  BottomSheet() {
    Column() {
      // 拖拽指示条 — 对齐安卓自绘 handle（32×4 outline 胶囊）：bindSheet 的 dragBar
      // 是系统样式不受令牌控制，故 dragBar:false + 内容顶部自绘
      Row()
        .width(32)
        .height(4)
        .borderRadius(DesignDimens.RADIUS_FULL)
        .backgroundColor(DesignColor.OUTLINE)
        .margin({ top: DesignDimens.SPACE_SM })

      // 标题栏（recap §3-V8：Android headlineSmall —— 按当前 AppTypography 该槽位为
      // 18sp/600，旧报告「24sp」为 Material3 默认值误读，此处对齐字重 600）
      Row() {
        Text("选择优惠券")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Blank()

        Row() {
          Text("去领取")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.PRIMARY)

          SymbolGlyph($r('sys.symbol.chevron_right'))
            .fontSize(14)
            .fontColor([DesignColor.PRIMARY])
            .margin({ left: 4 })
        }
        // N8：Android 垂直 12 + 热区 44dp+；constraintSize 兜底最小热区
        .constraintSize({ minWidth: 44, minHeight: 44 })
        .padding({ top: 12, bottom: 12 })
        .justifyContent(FlexAlign.Center)
        .alignItems(VerticalAlign.Center)
        .onClick(() => {
          this.claimThrottler.throttle(() => {
            this.showSheet = false
            pathStack.pushPathByName(RouteNames.COUPON, null)
          }, 300)
        })
      }
      .width("100%")
      .margin({ top: 8, bottom: 16 })
      .alignItems(VerticalAlign.Center)

      if (this.userCoupon === null || this.userCoupon.length === 0) {
        // 空态 — 规范 §3.6：滚筒圆母题 + 引导文案（替代裸 Text）
        EmptyState({ message: "暂无优惠券" })
          .width("100%")
          .height(160)
      } else {
        // "不使用优惠券"选项（评审 #17）：默认选中，用于从已选优惠券切换回不使用
        Row() {
          Column() {
            Text("不使用优惠券")
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)

            Text("按订单原价支付")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .margin({ top: 6 })
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)

          // 公共 SelectionDot（对齐安卓）：系统 Radio 的圈点样式/尺寸不受令牌控制；
          // 行 onClick 已承担选择，这里只做状态表达
          SelectionDot({ selected: this.selectCouponId === null })
        }
        .width("100%")
        .borderRadius(DesignDimens.CARD_RADIUS)
        .backgroundColor(DesignColor.SURFACE)
        // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
        .border({ width: 1, color: DesignColor.OUTLINE })
        .shadow({
          radius: DesignElevation.LEVEL_1,
          color: DesignColor.SHADOW_COLOR_CARD,
          offsetX: 0,
          offsetY: 2
        })
        .padding(DesignDimens.CARD_PADDING)
        .justifyContent(FlexAlign.SpaceBetween)
        .margin({ bottom: DesignDimens.CARD_SPACING })
        .onClick(() => {
          this.selectCouponWithThrottle(null)
        })

        List({ space: DesignDimens.CARD_SPACING }) {
          ForEach(this.userCoupon, (coupon: UserCouponVo) => {
            ListItem() {
              Row() {
                Column() {
                  Row() {
                    Text(`${coupon.couponVo?.discount?.toFixed(2)}元优惠券`)
                      .fontSize(DesignFontSize.CARD_TITLE)
                      .fontWeight(DesignFontWeight.SEMI_BOLD)
                      .fontColor(DesignColor.TEXT_PRIMARY)
                      .fontFeature(DesignFontFeature.TNUM)
                  }

                  Row() {
                    Text(this.thresholdText(coupon))
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.TEXT_SECONDARY)
                      .fontFeature(DesignFontFeature.TNUM)
                      .margin({ top: 6, bottom: 6 })
                  }

                  Row() {
                    Text(`有效期至${coupon.expiredAt}`)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.TEXT_TERTIARY)
                  }
                }
                .alignItems(HorizontalAlign.Start)
                .layoutWeight(1)

                // 公共 SelectionDot（对齐安卓）：行 onClick 已承担选择，这里只做状态表达
                SelectionDot({ selected: this.selectCouponId === coupon.userCouponId })
              }
              .width("100%")
              .borderRadius(DesignDimens.CARD_RADIUS)
              .backgroundColor(DesignColor.SURFACE)
              // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
              .border({ width: 1, color: DesignColor.OUTLINE })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: 2
              })
              .padding(DesignDimens.CARD_PADDING)
              .justifyContent(FlexAlign.SpaceBetween)
              .onClick(() => {
                this.selectCouponWithThrottle(coupon)
              })
            }
          })
        }
      }
    }
    .width("100%")
    .padding(DesignDimens.PAGE_PADDING)
  }

  build() {
    NavDestination() {
      Stack() {
        Column() {
          // 自定义页面标题
          this.PageHeader()

          // 分隔线
          Divider()
            .strokeWidth(1)
            .color(DesignColor.DIVIDER)

          // 内容区域（recap §3-V1：外层 sectionSpacing 32 + 内层 cardSpacing 16）
          Column() {
            // ========== 应付金额卡片（recap §3-F1/V2/V3）==========
            Column() {
              Text("应付金额")
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(DesignColor.TEXT_SECONDARY)

              // 金额两位小数（N1）：对齐 Android String.format("%.2f")；DataLarge 36/700 primaryDark + tnum
              Text(`¥${(this.orderInfo?.payPrice ?? 0).toFixed(2)}`)
                .fontSize(DesignFontSize.DATA_LARGE)
                .fontWeight(DesignFontWeight.BOLD)
                .fontColor(DesignColor.PRIMARY_DARK)
                .fontFeature(DesignFontFeature.TNUM)
                .margin({ top: 8 })

              // 卡内行分隔线 = 0.5dp hairline，上下 16（recap §3-V3）
              Divider()
                .strokeWidth(0.5)
                .color(DesignColor.HAIRLINE)
                .margin({ top: 16, bottom: 16 })

              this.InfoLine("订单编号", `${this.orderInfo?.orderNo ?? ""}`)
              this.InfoLine("服务类型", `${this.orderInfo?.laundryPackageVo?.itemName ?? ""}`)
              this.InfoLine("预计完成", "24小时内")
            }
            .width("100%")
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
            .border({ width: 1, color: DesignColor.OUTLINE })
            .shadow({
              radius: DesignElevation.LEVEL_1,
              color: DesignColor.SHADOW_COLOR_CARD,
              offsetX: 0,
              offsetY: 2
            })
            .alignItems(HorizontalAlign.Start)

            // ========== 支付方式 + 优惠券（recap §3-F2/V4/V5/V6）==========
            Column() {
              Text("支付方式")
                .fontSize(DesignFontSize.SECTION_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .margin({ bottom: 12 })

              // 余额支付卡：选中态 primarySoft 底（recap §3-F2），整行可点（recap §3-I3）
              Column() {
                Row() {
                  // IconBox 36/r12/20；选中盒变白 surface、未选中浅绿（recap §3-V4）
                  Column() {
                    SymbolGlyph($r('sys.symbol.creditcard'))
                      .fontSize(20)
                      .fontColor([DesignColor.PRIMARY])
                  }
                  .width(36)
                  .height(36)
                  .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
                  .backgroundColor(this.selectedPaymentMethod === "balance" ?
                    DesignColor.SURFACE :
                    DesignColor.ICON_CONTAINER_GREEN)
                  .justifyContent(FlexAlign.Center)
                  .alignItems(HorizontalAlign.Center)

                  Column() {
                    Text("余额支付")
                      .fontSize(DesignFontSize.CARD_TITLE)
                      .fontWeight(DesignFontWeight.SEMI_BOLD)
                      .fontColor(this.selectedPaymentMethod === "balance" ?
                        DesignColor.ON_PRIMARY_CONTAINER :
                        DesignColor.TEXT_PRIMARY)

                    // 余额两位小数（N1）+ tnum；文案对齐 Android current_balance_format「当前余额：¥%s」
                    Text(`当前余额：¥${(this.userInfo?.balance ?? 0).toFixed(2)}`)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontColor(DesignColor.TEXT_SECONDARY)
                      .fontFeature(DesignFontFeature.TNUM)
                      .margin({ top: 4 })
                  }
                  .alignItems(HorizontalAlign.Start)
                  .layoutWeight(1)
                  .margin({ left: 12 })

                  SelectionDot({ selected: this.selectedPaymentMethod === "balance" })
                }
                .width("100%")
                .padding({ left: 20, right: 16, top: 20, bottom: 20 })
                .alignItems(VerticalAlign.Center)
                .onClick(() => {
                  this.selectedPaymentMethod = "balance"
                })
              }
              .width("100%")
              .backgroundColor(this.selectedPaymentMethod === "balance" ?
                DesignColor.PRIMARY_SOFT :
                DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .border({ width: 1, color: DesignColor.OUTLINE })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: 2
              })
              .margin({ bottom: DesignDimens.CARD_SPACING })

              // 优惠券卡（recap §3-V5/V6）
              Row() {
                Column() {
                  SymbolGlyph($r('sys.symbol.label'))
                    .fontSize(20)
                    .fontColor([DesignColor.ICON_FOREGROUND_ORANGE])
                }
                .width(36)
                .height(36)
                .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
                .backgroundColor(DesignColor.ICON_CONTAINER_ORANGE)
                .justifyContent(FlexAlign.Center)
                .alignItems(HorizontalAlign.Center)

                Text("优惠券")
                  .fontSize(DesignFontSize.CARD_TITLE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor(DesignColor.TEXT_PRIMARY)
                  .layoutWeight(1)
                  .margin({ left: 12 })

                // 折扣两位小数（N1）+ tnum；金额符号统一半角 ¥（G7）；色 onPrimaryContainer（V6）
                Text(this.selectCoupon === null ? "不使用" : `-¥${this.selectCoupon.couponVo?.discount?.toFixed(2)}`)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(this.selectCoupon === null ? DesignColor.TEXT_SECONDARY : DesignColor.ON_PRIMARY_CONTAINER)
                  .fontFeature(this.selectCoupon === null ? 'normal' : DesignFontFeature.TNUM)

                SymbolGlyph($r('sys.symbol.chevron_right'))
                  .fontSize(14)
                  .fontColor([DesignColor.TEXT_TERTIARY])
                  .margin({ left: 4 })
              }
              .width("100%")
              .padding({ left: 20, right: 20, top: 15, bottom: 15 })
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
              .border({ width: 1, color: DesignColor.OUTLINE })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: 2
              })
              .bindSheet($$this.showSheet, this.BottomSheet(), {
                // 对齐安卓 ModalBottomSheet(fillMaxHeight) 的近全高选券浮层；
                // 留 10% 露出遮罩便于点空白处关闭（安卓点 scrim 关闭）。
                // 容器色对齐 Android containerColor = surface；
                // SheetOptions 未提供圆角令牌（V7 系统限制，保留系统默认顶部圆角）
                height: "90%",
                backgroundColor: DesignColor.SURFACE,
                showClose: false,
                dragBar: false
              })
              .onClick(() => {
                this.showSheet = true
              })
            }
            .width("100%")
            .alignItems(HorizontalAlign.Start)
            .margin({ top: DesignDimens.SECTION_SPACING })
          }
          .width("100%")
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: 12,
            bottom: 12
          })

          // ========== 底部固定 CTA 条（recap §3-V9）==========
          Column() {
            // 固定 CTA 条与滚动内容之间 0.5dp 分隔线（规范 §2.4 豁免）
            Divider()
              .strokeWidth(0.5)
              .color(DesignColor.OUTLINE)

            // AppButton 自带 loading 防重；点击走 onPayClick：余额充足弹「确定要支付？」
            // 二次确认（确认即关窗），不足弹「余额不足」去充值。
            // 金额两位小数（N1）+ 半角 ¥（G7），文案对齐 Android confirm_pay_format「确认支付 ¥%s」
            AppButton({
              text: `确认支付 ¥${(this.orderInfo?.payPrice ?? 0).toFixed(2)}`,
              loading: this.paying,
              click: () => {
                this.onPayClick()
              }
            })
          }
          .width("100%")
          .backgroundColor(DesignColor.SURFACE)
          .padding({ left: 24, right: 24, top: 16, bottom: 20 })
        }
        .width("100%")
        .height("100%")
        .backgroundColor(DesignColor.BACKGROUND)

        // 首载骨架（recap §3-F5/S1）：对齐 Android LoadingState，遮罩淡入淡出近似 Crossfade
        if (this.isLoading) {
          LoadingState()
            .width("100%")
            .height("100%")
            .backgroundColor(DesignColor.BACKGROUND)
            .zIndex(2)
            .transition(TransitionEffect.OPACITY)
        }
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      Promise.all([this.getUserCoupons(), this.getCurrUserInfo()])
    })
  }
}
