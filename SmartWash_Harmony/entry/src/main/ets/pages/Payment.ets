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
import { logger } from "../utils/Logger"
import { PaymentType } from "../constant/PaymentType"
import { Debouncer } from "../utils/DebounceUtil"
import { AppButton, AppButtonVariant, AppConfirmDialog, EmptyState, LoadingState, SelectionDot } from "../view/common/AppComponents"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { ThemeRefresh } from '../view/common/ThemeRefresh'

@Builder
export function PaymentBuilder() {
  Payment()
}

/**
 * 卡内「标签 — 值」信息行（对齐 Android InfoLine：bodySmall 12 + tnum、纵向 padding 4）。
 * 必须是组件而非 @Builder：支付页 loading 是覆盖层、内容树在 orderInfo 为 null 时就已构建，
 * @Builder 按值传参只在首帧求值，数据到达后不会刷新——订单号会永远定格在「—」（问题6）。
 */
@ComponentV2
struct PayInfoLine {
  @Param label: string = ""
  @Param value: string = ""

  build() {
    Row() {
      // 行盒 16（对齐安卓 bodySmall 12/16）+ 上下 4 = 行高 24（原 12fp 默认行盒 14 → 22）
      Text(this.label)
        .fontSize(DesignFontSize.CAPTION)
        .lineHeight(16)
        .fontColor(DesignColor.TEXT_SECONDARY)

      // 值侧占满剩余宽度 + 单行省略：长单号不得把标签挤没或溢出（问题6）
      Text(this.value)
        .fontSize(DesignFontSize.CAPTION)
        .lineHeight(16)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .fontFeature(DesignFontFeature.TNUM)
        .layoutWeight(1)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .textAlign(TextAlign.End)
    }
    .width("100%")
    .padding({ top: 4, bottom: 4 })
    .justifyContent(FlexAlign.SpaceBetween)
    .alignItems(VerticalAlign.Center)
  }
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
  // 首载骨架门控（对齐 Android Crossfade(orderInfo === null) → LoadingState）
  @State isLoading: boolean = true
  // 失败态：getOrderInfo 失败或 data 为空 → EmptyState + 重试（问题6，对齐 OrderDetail.loadFailed）
  @State loadFailed: boolean = false
  // 支付请求进行中标记：AppButton loading 展示并拦截连点，防止重复扣款（评审 #12）
  @State paying: boolean = false
  // 按压反馈（recap §3-I1 / G3）：可点行 scale 0.97 + onTouch，对齐安卓 pressable
  @State pressedKey: string | null = null
  // 金额/券变化淡入（recap §3-I2）：对齐安卓 AnimatedContent fade；ArkUI 无等价组件，用 opacity 淡入近似
  @State amountVisible: boolean = true
  private amountFadeTimer: number = -1
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
    logger.debug('payment', `支付页挂载 orderId=${this.orderId}`)
    await this.loadOrderInfo()
  }

  /**
   * 拉取订单详情（首载 / 「重试」共用）。
   * 失败或 data 为空置 loadFailed → 渲染 EmptyState + 重试，不再静默显示「—」空卡（问题6）。
   */
  async loadOrderInfo(): Promise<void> {
    if (this.orderId === null) {
      logger.warn('payment', '缺少路由参数 orderId，进入失败态')
      this.loadFailed = true
      this.isLoading = false
      return
    }
    this.isLoading = true
    this.loadFailed = false
    try {
      let res = await getOrderInfo(this.orderId)
      // 显式收窄为可空类型，兼容后端漏传 data（运行期 undefined）与 null 两种空态
      let data: OrderInfo | null | undefined = res.data
      if (res.code === HttpStatusCode.Ok && data !== null && data !== undefined) {
        this.orderInfo = data
        logger.debug('payment', `支付页订单信息加载成功 orderId=${this.orderId}`)
      } else {
        logger.warn('payment', `支付页订单信息加载失败 orderId=${this.orderId} ` +
          `code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "订单信息加载失败")
        this.loadFailed = true
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并进失败态，避免 isLoading 卡死永久骨架
      this.loadFailed = true
      showToastSafety("网络异常，请稍后重试")
    } finally {
      this.isLoading = false
    }
  }

  async getCurrUserInfo() {
    let res = await getUserInfo()
    if (res.code === HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      logger.warn('payment', `用户信息加载失败 code=${res.code} message=${res.message ?? ""}`)
      showToastSafety(res.message, "用户信息加载失败")
    }
  }

  async getUserCoupons() {
    let couponRes = await getCanUseCoupon(this.orderId ?? -1)
    if (couponRes.code === HttpStatusCode.Ok) {
      this.userCoupon = couponRes.data
    } else {
      logger.warn('payment', `可用优惠券加载失败 orderId=${this.orderId} ` +
        `code=${couponRes.code} message=${couponRes.message ?? ""}`)
      showToastSafety(couponRes.message, "优惠券加载失败")
    }
  }

  /**
   * 试算订单金额（评审 #17）：coupon 传 null 表示"不使用优惠券"。
   *
   * 后端 OrdersServiceImpl.calculationOrder 仅认 userCouponId === null || 0 为不使用优惠券，
   * 传 -1 会抛"优惠券异常"（#31），故此处统一传 0。
   */
  async calculationOrder(coupon: UserCouponVo | null) {
    this.selectCouponId = coupon?.userCouponId ?? null
    this.showSheet = false
    this.selectCoupon = coupon
    // 金额/券变化淡入（recap §3-I2）：先隐去旧值，试算返回后淡入新值（对齐安卓 AnimatedContent fade）
    this.amountVisible = false
    if (this.orderId !== null) {
      let res = await calculationOrder(this.orderId, coupon?.userCouponId ?? 0)
      if (res.code === HttpStatusCode.Ok) {
        this.orderInfo = res.data
        this.fadeAmountIn()
      } else {
        logger.warn('payment', `优惠券试算失败 orderId=${this.orderId} ` +
          `couponId=${coupon?.userCouponId ?? 0} code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "优惠券试算失败")
        this.amountVisible = true
      }
    } else {
      this.amountVisible = true
    }
  }

  /**
   * 金额/券淡入：60ms 后 180ms EaseOut，近似安卓 fadeIn(enterTween 200) togetherWith fadeOut(exitTween 120)。
   * 定时器在 aboutToDisappear 中清理（定时器硬规则）。
   */
  private fadeAmountIn(): void {
    if (this.amountFadeTimer !== -1) {
      clearTimeout(this.amountFadeTimer)
    }
    this.amountFadeTimer = setTimeout(() => {
      animateTo({ duration: 180, curve: Curve.EaseOut }, () => {
        this.amountVisible = true
      })
    }, 60)
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
    if (this.amountFadeTimer !== -1) {
      clearTimeout(this.amountFadeTimer)
      this.amountFadeTimer = -1
    }
  }

  /**
   * 发起支付（资金链路）：
   * 仅在「确认支付？」弹窗确认后调用——弹窗确认回调先关窗再进本方法，
   * 配合 paying 标记 + AppButton loading 形成两层防重。
   */
  async doPay() {
    if (this.paying) {
      logger.debug('payment', '支付请求进行中，忽略重复触发')
      return
    }
    this.paying = true
    try {
      // 发起前留痕：仅记 orderId/金额/券 id 摘要，不打完整请求体（敏感红线）
      logger.debug('payment', `发起支付 orderId=${this.orderId} ` +
        `payPrice=${(this.orderInfo?.payPrice ?? 0).toFixed(2)} ` +
        `couponId=${this.selectCoupon?.userCouponId ?? null}`)
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
        logger.warn('payment', `支付被拒绝 orderId=${this.orderId} ` +
          `code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "支付失败，请稍后重试")
      }
    } finally {
      this.paying = false
    }
  }

  /** 支付成功导航：对齐 Android popUpTo(HOME) —— 回退到首页再入栈支付成功页，避免支付页残留返回栈 */
  private goToPaySuccess() {
    // 低频关键动作成功：支付完成在跳转处留 info（支付成功页不再重复记录）
    logger.info('payment', `支付成功，跳转支付成功页 orderId=${this.orderId} ` +
      `payPrice=${(this.orderInfo?.payPrice ?? 0).toFixed(2)}`)
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
      // 业务拒绝：余额不足拦截（金额可打，不含手机号等敏感字段）
      logger.warn('payment', `余额不足拦截 orderId=${this.orderId} ` +
        `balance=${(this.userInfo?.balance ?? 0).toFixed(2)} ` +
        `payPrice=${(this.orderInfo?.payPrice ?? 0).toFixed(2)}`)
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

  /** 空值兜底展示（问题8）：null/undefined/空串 → 「—」 */
  private displayValue(value: string | null | undefined): string {
    return value !== null && value !== undefined && value.length > 0 ? value : "—"
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
    }
    .width("100%")
    .height(56)
    // 行首让位 12：48dp 热区盒居中后图形左缘落在 24，标题紧贴热区右缘（对齐安卓 PageHeader）
    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(DesignColor.BACKGROUND)
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
        // 按压反馈（recap §3-I1 / G3）：scale 0.97 + onTouch，对齐安卓 pressable
        .scale({ x: this.pressedKey === 'claim' ? 0.97 : 1, y: this.pressedKey === 'claim' ? 0.97 : 1 })
        .onTouch((event: TouchEvent) => {
          if (event.type === TouchType.Down) {
            this.pressedKey = 'claim'
          } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
            this.pressedKey = null
          }
        })
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
        // 按压反馈（recap §3-I1 / G3）：scale 0.97 + onTouch，对齐安卓 pressable
        .scale({ x: this.pressedKey === 'noCoupon' ? 0.97 : 1, y: this.pressedKey === 'noCoupon' ? 0.97 : 1 })
        .onTouch((event: TouchEvent) => {
          if (event.type === TouchType.Down) {
            this.pressedKey = 'noCoupon'
          } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
            this.pressedKey = null
          }
        })
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
              // 按压反馈（recap §3-I1 / G3）：scale 0.97 + onTouch，对齐安卓 pressable
              .scale({
                x: this.pressedKey === (coupon.userCouponId?.toString() ?? '') ? 0.97 : 1,
                y: this.pressedKey === (coupon.userCouponId?.toString() ?? '') ? 0.97 : 1
              })
              .onTouch((event: TouchEvent) => {
                if (event.type === TouchType.Down) {
                  this.pressedKey = coupon.userCouponId?.toString() ?? ''
                } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                  this.pressedKey = null
                }
              })
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
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
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
                // 金额变化淡入（recap §3-I2）
                .opacity(this.amountVisible ? 1 : 0)
                .margin({ top: 8 })

              // 卡内行分隔线 = 0.5dp hairline，上下 16（recap §3-V3）
              Divider()
                .strokeWidth(0.5)
                .color(DesignColor.HAIRLINE)
                .margin({ top: 16, bottom: 16 })

              // 订单号/服务类型缺失时兜底「—」（问题8：老版后端可能不下发 orderNo/laundryPackageVo，
              // 空串观感是"数据丢了"，占位符语义为"暂无"）
              PayInfoLine({ label: "订单编号", value: this.displayValue(this.orderInfo?.orderNo) })
              PayInfoLine({ label: "服务类型", value: this.displayValue(this.orderInfo?.laundryPackageVo?.itemName) })
              PayInfoLine({ label: "预计完成", value: "24小时内" })
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
                  // 图标语义对齐安卓 PaymentPage.kt:240 Icons.Default.LocalOffer（标签/优惠），
                  // 与原 creditcard（银行卡）形状不同；券卡亦用同款
                  Column() {
                    SymbolGlyph($r('sys.symbol.label'))
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

                  // 选中圆点顶对齐（对齐安卓 PaymentPage.kt:273-278 CheckMark：
                  // Modifier.align(Alignment.Top)，28dp 盒内居中 → 圆点中心距文本列顶 14dp）
                  Column() {
                    SelectionDot({ selected: this.selectedPaymentMethod === "balance" })
                  }
                  .width(28)
                  .height(28)
                  .justifyContent(FlexAlign.Center)
                  .alignSelf(ItemAlign.Start)
                }
                .width("100%")
                .padding({ left: 20, right: 16, top: 20, bottom: 20 })
                .alignItems(VerticalAlign.Center)
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
              // 按压反馈（recap §3-I1 / G3）：scale 0.97 + onTouch，对齐安卓 pressable
              .scale({ x: this.pressedKey === 'balance' ? 0.97 : 1, y: this.pressedKey === 'balance' ? 0.97 : 1 })
              .onTouch((event: TouchEvent) => {
                if (event.type === TouchType.Down) {
                  this.pressedKey = 'balance'
                } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                  this.pressedKey = null
                }
              })
              .onClick(() => {
                this.selectedPaymentMethod = "balance"
              })

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
                  // 券变化淡入（recap §3-I2，与金额共用同一淡入态）
                  .opacity(this.amountVisible ? 1 : 0)

                // 箭头 20dp + 前间距 8dp（对齐安卓 PaymentPage.kt:338-344：
                // Spacer(8.dp) + Icon(ChevronRight, 20.dp)）
                SymbolGlyph($r('sys.symbol.chevron_right'))
                  .fontSize(20)
                  .fontColor([DesignColor.TEXT_TERTIARY])
                  .margin({ left: DesignDimens.SPACE_XS })
              }
              .width("100%")
              // 行内边距 16（对齐安卓 U-118：原 15 不在 §3 间距允许集）
              .padding({ left: 20, right: 20, top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
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
              // 按压反馈（recap §3-I1 / G3）：scale 0.97 + onTouch，对齐安卓 pressable
              .scale({ x: this.pressedKey === 'coupon' ? 0.97 : 1, y: this.pressedKey === 'coupon' ? 0.97 : 1 })
              .onTouch((event: TouchEvent) => {
                if (event.type === TouchType.Down) {
                  this.pressedKey = 'coupon'
                } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                  this.pressedKey = null
                }
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
          .padding({ left: 24, right: 24, top: 16, bottom: 20 + safeBottomVp() })
        }
        .width("100%")
        .height("100%")
        // 沉浸式：内容避让状态栏（背景延伸）
        .padding({ top: safeTopVp() })
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

        // 失败态（问题6）：EmptyState 母题 + 「重试」行动按钮，不再静默显示「—」空卡
        if (!this.isLoading && this.loadFailed) {
          Column() {
            EmptyState({
              message: "订单信息加载失败",
              action: () => {
                AppButton({
                  small: true,
                  text: "重试",
                  variant: AppButtonVariant.SECONDARY,
                  click: () => {
                    this.loadOrderInfo()
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
    }
    .hideTitleBar(true)
    .onShown(() => {
      Promise.all([this.getUserCoupons(), this.getCurrUserInfo()])
    })
  }
}
