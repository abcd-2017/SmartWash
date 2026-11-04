import { HttpStatusCode } from "@ohos/axios"
import { getAllCoupons, receiveCoupon } from "../network/api/CouponApi"
import { CouponVo } from "../network/vo/CouponVo"
import { UserCouponVo } from "../network/vo/UserCouponVo"
import { CouponStatus } from "../constant/CouponStatus"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { Debouncer } from "../utils/DebounceUtil"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { AppButton, AppButtonVariant, AppPillTabRow, EmptyState, LoadingState, PageHeader } from "../view/common/AppComponents"
import { TicketDashedLine, TicketNotch } from "../view/TicketNotchShape"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import vibrator from '@ohos.vibrator'

// 领取动作的节流间隔（毫秒）：后端领券无幂等，连点会重复发请求（评审报告 P0 背景），
// 前端节流 + loading 是唯二防线（对齐安卓 rememberDebouncedClick）
const RECEIVE_THROTTLE_MS: number = 800

/**
 * 券面额格式化（对齐安卓 String.format("%.0f") + 非整数保留 2 位）：
 * 整数不显示小数，非整数保留 2 位；¥ 前缀由调用方统一拼接。
 */
function formatCouponAmount(value: number | null | undefined): string {
  const v = value ?? 0
  return Number.isInteger(v) ? v.toString() : v.toFixed(2)
}

@Builder
export function CouponBuilder() {
  Coupon()
}

@Component
struct Coupon {
  // 三类券数据（一次聚合拉取，切 tab 仅切内存列表）
  @State availableCoupons: Array<CouponVo> = []
  @State claimedCoupons: Array<UserCouponVo> = []
  @State historicalCoupons: Array<UserCouponVo> = []
  @State selectedIndex: number = 0
  // 首屏/刷新加载态（对齐安卓全屏 LoadingState）
  @State isLoading: boolean = false
  // 首载失败标记：三列表全空且失败时展示 EmptyState + 重试（对齐安卓 CouponContentState.Error）
  @State loadFailed: boolean = false
  // 领取请求进行中的券 ID（逐券标记，对齐安卓 claimingId；全页 receiving 会误伤其他卡）
  @State claimingCouponId: number | null = null
  // 领取点击节流（防抖第一道防线）
  private receiveDebouncer: Debouncer = new Debouncer()

  // Tab 标题（可用 tab 的计数在 tabLabel 中动态拼接；第三 tab 文案对齐安卓 historical_coupons「历史」）
  private readonly tabTitles: string[] = ["可用", "已领取", "历史"]

  async aboutToAppear() {
    logger.debug('coupon', '优惠券页挂载，拉取三类券列表')
    await this.loadAllCoupons()
  }

  aboutToDisappear() {
    // 页面销毁前清理未触发的节流任务（定时器硬规则）
    this.receiveDebouncer.cancel()
  }

  /**
   * 一次拉取三类券（对齐安卓 CouponViewModel.loadAllCoupons → /web/auth/userCoupon/allCoupons）。
   * 领券成功后复用本方法全量刷新，保证已领取/历史 tab 计数一致。
   */
  async loadAllCoupons() {
    this.isLoading = true
    try {
      let res = await getAllCoupons()
      this.isLoading = false
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        this.loadFailed = false
        this.availableCoupons = res.data.available ?? []
        this.claimedCoupons = res.data.claimed ?? []
        this.historicalCoupons = res.data.historical ?? []
      } else {
        logger.warn('coupon', `优惠券列表加载失败 code=${res.code} message=${res.message ?? ""}`)
        this.loadFailed = true
        showToastSafety(res.message, "优惠券加载失败")
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并进失败态，避免 isLoading 卡死永久骨架
      this.isLoading = false
      this.loadFailed = true
      showToastSafety("网络异常，请稍后重试")
    }
  }

  /** 三列表是否全空（对齐安卓 listsEmpty，用于骨架/失败态门控） */
  private isAllEmpty(): boolean {
    return this.availableCoupons.length === 0
      && this.claimedCoupons.length === 0
      && this.historicalCoupons.length === 0
  }

  // Tab 文案：可用 tab 带真实计数（对齐安卓 available_coupons_with_count「可用 %1$d」，无括号）
  private tabLabel(index: number): string {
    return index === 0 ? `${this.tabTitles[0]} ${this.availableCoupons.length}` : this.tabTitles[index]
  }

  // 切换 Tab — 仅切内存列表，零请求（对齐安卓 CouponPage.kt:157-163）
  onTabChange(index: number) {
    this.selectedIndex = index
  }

  /** 领取触感（对齐安卓 HapticEffect.MEDIUM 行内按钮档）：设备不支持时静默忽略 */
  private hapticSelection(): void {
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 不支持触感（模拟器 / 部分设备）时忽略
    }
  }

  // 领取优惠券（节流 + loading 双守卫，成功后全量刷新）
  onReceiveCoupon(couponId: number) {
    // 节流：连点只放行第一次（对齐安卓 rememberDebouncedClick）
    this.receiveDebouncer.throttle(() => {
      this.doReceiveCoupon(couponId)
    }, RECEIVE_THROTTLE_MS)
  }

  async doReceiveCoupon(couponId: number) {
    // 逐券防重：已有领取请求在进行时直接拦截（对齐安卓 claimingId 禁点）
    if (this.claimingCouponId !== null) {
      logger.debug('coupon', '领取请求进行中，忽略重复触发')
      return
    }
    this.claimingCouponId = couponId
    try {
      let res = await receiveCoupon(couponId)
      // 后端 Result.ok(boolean) 直传：data=false（并发/重复领取被拒）必须按失败处理，
      // 不能只看 code=200 就弹「领取成功」（对齐 AddSchool 对 getUserByStudentId 的判法）
      if (res.code === HttpStatusCode.Ok && res.data === true) {
        await this.loadAllCoupons()
        showToastSafety("领取成功")
      } else {
        logger.warn('coupon', `优惠券领取被拒 couponId=${couponId} code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "领取失败，请稍后重试")
        logger.info('coupon', `优惠券领取成功 couponId=${couponId}`)
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并按失败提示，避免 claimingCouponId 卡死
      showToastSafety("网络异常，请稍后重试")
    } finally {
      this.claimingCouponId = null
    }
  }

  // 空态占位（统一 EmptyState 组件，居中展示）
  @Builder
  emptyPlaceholder(message: string) {
    Column() {
      EmptyState({ message: message })
    }
    .width("100%")
    .height("100%")
    .justifyContent(FlexAlign.Center)
  }

  // ========== 可领取 tab（Swiper 第 0 页，常驻保留滚动位置） ==========
  @Builder
  availablePage() {
    if (this.availableCoupons.length === 0) {
      this.emptyPlaceholder("暂无优惠券")
    } else {
      List({ space: DesignDimens.CARD_SPACING }) {
        ForEach(this.availableCoupons, (coupon: CouponVo) => {
          ListItem() {
            AvailableCouponCard({
              coupon: coupon,
              isClaiming: this.claimingCouponId === coupon.couponId,
              onReceive: (couponId: number) => {
                this.onReceiveCoupon(couponId)
              }
            })
          }
        // 键必须含状态指纹（问题5 根因）：领取成功 loadAllCoupons 整表替换对象，
        // 仅用 couponId 时键不变 → ForEach 复用旧子组件、@Param 不同步 → 按钮定格「立即领取」
        }, (coupon: CouponVo, index: number) => `${coupon.couponId ?? index}:${coupon.status ?? ""}`)
      }
      .width("100%")
      .height("100%")
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING, bottom: safeBottomVp() })
    }
  }

  // ========== 已领取 tab（Swiper 第 1 页） ==========
  @Builder
  claimedPage() {
    if (this.claimedCoupons.length === 0) {
      this.emptyPlaceholder("暂无优惠券")
    } else {
      List({ space: DesignDimens.CARD_SPACING }) {
        ForEach(this.claimedCoupons, (userCoupon: UserCouponVo) => {
          ListItem() {
            CouponTicketCard({ userCoupon: userCoupon, isHistorical: false })
          }
        }, (userCoupon: UserCouponVo, index: number) => `${userCoupon.userCouponId ?? index}`)
      }
      .width("100%")
      .height("100%")
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING, bottom: safeBottomVp() })
    }
  }

  // ========== 历史 tab（Swiper 第 2 页） ==========
  @Builder
  historicalPage() {
    if (this.historicalCoupons.length === 0) {
      this.emptyPlaceholder("暂无使用优惠券")
    } else {
      List({ space: DesignDimens.CARD_SPACING }) {
        ForEach(this.historicalCoupons, (userCoupon: UserCouponVo) => {
          ListItem() {
            CouponTicketCard({ userCoupon: userCoupon, isHistorical: true })
          }
        // 键含 isUsed 指纹：核销状态变化后对象替换、键不变会复用旧子组件（同可用 tab 键问题）
        }, (userCoupon: UserCouponVo, index: number) => `${userCoupon.userCouponId ?? index}:${userCoupon.isUsed ? 1 : 0}`)
      }
      .width("100%")
      .height("100%")
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING, bottom: safeBottomVp() })
    }
  }

  build() {
    NavDestination() {
      Column() {
        // ========== 页头（公共 PageHeader：22/600 + 48 热区返回，对齐安卓 CouponPage.kt:148） ==========
        PageHeader({ title: "优惠券" })

        // ========== 胶囊分段标签栏（公共 AppPillTabRow，对齐安卓 AppPillTabRow） ==========
        AppPillTabRow({
          tabs: this.tabTitles.map((_: string, index: number): string => this.tabLabel(index)),
          selectedIndex: this.selectedIndex,
          tabSelected: (index: number) => {
            this.onTabChange(index)
          }
        })

        // ========== 内容区域（骨架 / 失败重试 / Swiper 三页） ==========
        if (this.isLoading && this.isAllEmpty()) {
          // 加载态（对齐安卓 LoadingState：骨架屏，§3.6 禁转圈）
          LoadingState()
            .width("100%")
            .layoutWeight(1)
        } else if (this.loadFailed && this.isAllEmpty()) {
          // 首载失败态（对齐安卓 CouponContentState.Error：EmptyState + 重试次按钮）
          Column() {
            EmptyState({
              message: "优惠券加载失败",
              action: () => {
                AppButton({
                  text: "重试",
                  variant: AppButtonVariant.SECONDARY,
                  click: () => {
                    this.loadAllCoupons()
                  }
                })
                .margin({ top: DesignDimens.SPACE_MD })
              }
            })
          }
          .width("100%")
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          // Swiper 三页常驻（对齐安卓 HorizontalPager + LazyLayout，保留各页滚动位置）
          Swiper() {
            this.availablePage()
            this.claimedPage()
            this.historicalPage()
          }
          .width("100%")
          .layoutWeight(1)
          .index(this.selectedIndex)
          .indicator(false)
          .loop(false)
          .autoPlay(false)
          .onChange((index: number) => {
            this.selectedIndex = index
          })
        }
      }
      .width("100%")
      .height("100%")
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .width("100%")
    .height("100%")
    .backgroundColor(DesignColor.BACKGROUND)
  }
}

// ========== 可领取优惠券卡片（票券形态，对齐安卓 AvailableCouponsTab.CouponCard） ==========
@ComponentV2
struct AvailableCouponCard {
  @Param coupon: CouponVo | null = null
  @Param isClaiming: boolean = false
  @Event onReceive: (couponId: number) => void = (couponId: number) => {}
  // 按压缩放（对齐安卓 pressable scale 0.97）
  @Local pressed: boolean = false

  // 面额区宽度（与安卓票券面额区 88dp 同源）
  private amountWidth: number = 88
  // 卡高固定 144：安卓为 wrap-content，ArkUI 无自定义 Shape / onAreaChange 反算，
  // 取固定值承载右区「三行文本 + 独立按钮行」（见 refix-07 报告裁决 #3）
  private cardHeight: number = 144

  private isReceived(): boolean {
    // CouponStatus 已对齐安卓 string 契约（"0"/"1"/"2"），直接与后端 status 比较
    return (this.coupon?.status ?? CouponStatus.ACTIVE) === CouponStatus.RECEIVE
  }

  // 门槛文案（对齐安卓 no_threshold / coupon_min_amount_format「满%s元可用」）
  private thresholdText(): string {
    return (this.coupon?.threshold ?? 0) === 0 ?
      "无门槛" :
      `满${this.coupon?.threshold?.toFixed(2)}元可用`
  }

  private dateText(): string {
    return `${this.coupon?.startTime?.split(" ")[0] ?? ""} - ${this.coupon?.endTime?.split(" ")[0] ?? ""}`
  }

  private claimLabel(): string {
    // 对齐安卓 claim_now / claimed
    return this.isReceived() ? "已领取" : "立即领取"
  }

  private hapticSelection(): void {
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 不支持触感（模拟器 / 部分设备）时忽略
    }
  }

  // 领取按钮（对齐安卓 ClaimAction：48 热区外层 + 36 高 / r12 视觉胶囊 + 13sp/600 白字；
  // 领取中原位换 14dp 圆环并禁点）
  @Builder
  claimAction() {
    Column() {
      Column() {
        Row() {
          if (this.isClaiming) {
            LoadingProgress()
              .width(14)
              .height(14)
              .color(Color.White)
          } else {
            Text(this.claimLabel())
              .fontSize(DesignFontSize.BUTTON_TEXT)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(Color.White)
          }
        }
        .width("100%")
        .height("100%")
        .justifyContent(FlexAlign.Center)
        .alignItems(VerticalAlign.Center)
      }
      .height(36)
      .borderRadius(DesignDimens.RADIUS_MD)
      .backgroundColor(this.isReceived() ? DesignColor.TEXT_SECONDARY : DesignColor.PRIMARY)
      // wrap-content：宽度随文字自适应（对齐安卓 ClaimAction，无最小宽度）
      .padding({ left: 16, right: 16 })
    }
    .height(48)
    .justifyContent(FlexAlign.Center)
    .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      if (this.isReceived() || this.isClaiming) {
        return
      }
      this.hapticSelection()
      this.onReceive(this.coupon?.couponId ?? -1)
    })
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      Row() {
        // 左侧金额区（票券 face：brand-soft 纯色底 + 面额 + 单位字）
        Column() {
          Text(`¥${formatCouponAmount(this.coupon?.discount)}`)
            .fontSize(DesignFontSize.AMOUNT_MEDIUM)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor(DesignColor.PRIMARY_DARK)
          Text("券")
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(DesignColor.PRIMARY)
            .margin({ top: 4 })
        }
        .width(this.amountWidth)
        .height(this.cardHeight)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor(DesignColor.PRIMARY_CONTAINER)

        // 右侧两行结构（对齐安卓 2026-11-01 实机修正）：上 = 标题/日期/门槛；下 = 领取按钮右对齐
        Column() {
          Text(this.coupon?.title ?? "")
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Text(this.dateText())
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 4 })

          Text(this.thresholdText())
            .fontSize(DesignFontSize.CAPTION)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 2 })

          Row() {
            this.claimAction()
          }
          .width("100%")
          .justifyContent(FlexAlign.End)
          .margin({ top: 8 })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        .padding({ left: 16, right: 16, top: 12, bottom: 12 })
      }
      .width("100%")
      .height(this.cardHeight)
      .alignItems(VerticalAlign.Center)
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      // 卡片唯一画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影（0 2px 12px rgba(20,40,30,.05)）
      .border({ width: 1, color: DesignColor.OUTLINE })
      .shadow({
        radius: DesignElevation.LEVEL_1,
        color: DesignColor.SHADOW_COLOR_CARD,
        offsetX: 0,
        offsetY: 2
      })
      .clip(true)

      // 中间虚线撕裂线（段 6 / 隔 4 / 上下留 12，对齐安卓 CouponPage.kt:250-266）
      TicketDashedLine({ dashHeight: this.cardHeight - 24, dashColor: DesignColor.OUTLINE })
        .position({ x: this.amountWidth - 0.5, y: 12 })

      // 左右缘中点打孔（φ14，对齐安卓 TicketShape）
      TicketNotch()
    }
    .width("100%")
    .height(this.cardHeight)
  }
}

// ========== 票券卡片（已领取 / 领取记录共用，对齐安卓 UserCouponCard ticket3） ==========
@ComponentV2
struct CouponTicketCard {
  @Param userCoupon: UserCouponVo | null = null
  @Param isHistorical: boolean = false

  // 面额区宽度（与安卓票券面额区 88dp 同源）
  private amountWidth: number = 88
  // 票券卡片高度（安卓 88dp）
  private cardHeight: number = 88

  private amountColor(): number {
    return this.isHistorical ? DesignColor.TEXT_TERTIARY : DesignColor.PRIMARY_DARK
  }

  private unitColor(): number {
    return this.isHistorical ? DesignColor.TEXT_TERTIARY : DesignColor.PRIMARY
  }

  private titleColor(): number {
    return this.isHistorical ? DesignColor.TEXT_SECONDARY : DesignColor.TEXT_PRIMARY
  }

  private statusColor(): number {
    return this.isHistorical ? DesignColor.TEXT_TERTIARY : DesignColor.PRIMARY
  }

  private statusText(): string {
    // 历史态按 isUsed 区分「已使用/已过期」，已领取态恒为「可用」（对齐安卓）
    return this.isHistorical ? (this.userCoupon?.isUsed ? "已使用" : "已过期") : "可用"
  }

  private thresholdText(): string {
    return (this.userCoupon?.couponVo?.threshold ?? 0) === 0 ?
      "无门槛" :
      `满${this.userCoupon?.couponVo?.threshold?.toFixed(2)}元可用`
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      Row() {
        // 左侧金额区（历史态 surfaceVariant，其余 brand-soft 纯色底）
        Column() {
          Text(`¥${formatCouponAmount(this.userCoupon?.couponVo?.discount)}`)
            .fontSize(DesignFontSize.AMOUNT_MEDIUM)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor(this.amountColor())
          Text("券")
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(this.unitColor())
            .margin({ top: 5 })
        }
        .width(this.amountWidth)
        .height(this.cardHeight)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor(this.isHistorical ? DesignColor.SURFACE_VARIANT : DesignColor.PRIMARY_CONTAINER)

        // 右侧内容区
        Column() {
          Text(this.userCoupon?.couponVo?.title ?? "")
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(this.titleColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Text(this.thresholdText())
            .fontSize(DesignFontSize.CAPTION)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 4 })

          Text(`有效期至 ${this.userCoupon?.expiredAt ?? ""}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 4 })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        // 仅水平内边距，无上下 padding：内容由外层 Row 的 VerticalAlign.Center 垂直居中（对齐安卓 padding(start=16, end=12)）
        .padding({ left: 16, right: 12 })

        // 状态标签
        Text(this.statusText())
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(this.statusColor())
          .padding({ right: 16 })
      }
      .width("100%")
      .height(this.cardHeight)
      .alignItems(VerticalAlign.Center)
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      // 票券卡同标准卡画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影
      .border({ width: 1, color: DesignColor.OUTLINE })
      .shadow({
        radius: DesignElevation.LEVEL_1,
        color: DesignColor.SHADOW_COLOR_CARD,
        offsetX: 0,
        offsetY: 2
      })
      .clip(true)
      .opacity(this.isHistorical ? 0.55 : 1.0)

      // 中间虚线撕裂线（段 6 / 隔 4 / 上下留 12）
      TicketDashedLine({ dashHeight: this.cardHeight - 24, dashColor: DesignColor.OUTLINE })
        .position({ x: this.amountWidth - 0.5, y: 12 })

      // 左右缘中点打孔（φ14，对齐安卓 TicketShape）
      TicketNotch()
    }
    .width("100%")
    .height(this.cardHeight)
  }
}
