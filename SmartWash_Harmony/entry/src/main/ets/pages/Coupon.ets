import { HttpStatusCode } from "@ohos/axios"
import { getAllCoupons, receiveCoupon } from "../network/api/CouponApi"
import { CouponVo } from "../network/vo/CouponVo"
import { UserCouponVo } from "../network/vo/UserCouponVo"
import { CouponStatus } from "../constant/CouponStatus"
import { showToastSafety } from "../utils/ToastUtil"
import { Debouncer } from "../utils/DebounceUtil"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { AppPillTabRow, EmptyState, LoadingState } from "../view/common/AppComponents"

// 领取动作的节流间隔（毫秒）：后端领券无幂等，连点会重复发请求（评审报告 P0 背景），
// 前端节流 + loading 是唯二防线（对齐安卓 rememberDebouncedClick）
const RECEIVE_THROTTLE_MS: number = 800

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
  // 领取请求进行中标记：防连点第二道防线
  @State receiving: boolean = false
  // 领取点击节流（防抖第一道防线）
  private receiveDebouncer: Debouncer = new Debouncer()

  // Tab 标题（可用 tab 的计数在 tabLabel 中动态拼接）
  private readonly tabTitles: string[] = ["可用", "已领取", "已使用"]

  async aboutToAppear() {
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
    let res = await getAllCoupons()
    this.isLoading = false
    if (res.code === HttpStatusCode.Ok && res.data !== null) {
      this.availableCoupons = res.data.available ?? []
      this.claimedCoupons = res.data.claimed ?? []
      this.historicalCoupons = res.data.historical ?? []
    } else {
      showToastSafety(res.message, "优惠券加载失败")
    }
  }

  // Tab 文案：可用 tab 带真实计数（对齐安卓 available_coupons_with_count）
  private tabLabel(index: number): string {
    return index === 0 ? `${this.tabTitles[0]} (${this.availableCoupons.length})` : this.tabTitles[index]
  }

  // 切换 Tab — 仅切内存列表，零请求（对齐安卓 CouponPage.kt:157-163）
  onTabChange(index: number) {
    this.selectedIndex = index
  }

  // 空态占位（统一 EmptyState 组件，居中展示）
  @Builder
  emptyPlaceholder(message: string) {
    Column() {
      EmptyState({ message: message })
    }
    .width("100%")
    .layoutWeight(1)
    .justifyContent(FlexAlign.Center)
  }

  // 领取优惠券（节流 + loading 双守卫，成功后全量刷新）
  onReceiveCoupon(couponId: number) {
    // 节流：连点只放行第一次（对齐安卓 rememberDebouncedClick）
    this.receiveDebouncer.throttle(() => {
      this.doReceiveCoupon(couponId)
    }, RECEIVE_THROTTLE_MS)
  }

  async doReceiveCoupon(couponId: number) {
    if (this.receiving) {
      return
    }
    this.receiving = true
    try {
      let res = await receiveCoupon(couponId)
      if (res.code === HttpStatusCode.Ok) {
        await this.loadAllCoupons()
        showToastSafety("领取成功")
      } else {
        showToastSafety(res.message, "领取失败")
      }
    } finally {
      this.receiving = false
    }
  }

  build() {
    NavDestination() {
      Column() {
        // ========== 自定义标题栏 ==========
        Row() {
          Text("优惠券")
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
        }
        .width("100%")
        .height(56)
        .justifyContent(FlexAlign.Center)
        .alignItems(VerticalAlign.Center)

        // ========== 自定义标签栏（胶囊分段形态，对齐安卓 AppPillTabRow） ==========
        AppPillTabRow({
          tabs: this.tabTitles.map((_: string, index: number): string => this.tabLabel(index)),
          selectedIndex: this.selectedIndex,
          tabSelected: (index: number) => {
            this.onTabChange(index)
          }
        })
        .height(48)
        .backgroundColor(DesignColor.SURFACE)
        .border({ width: { bottom: 1 }, color: DesignColor.DIVIDER })

        // ========== 内容区域 ==========
        if (this.isLoading && this.availableCoupons.length === 0) {
          // 加载态（对齐安卓 LoadingState：骨架屏，§3.6 禁转圈）
          LoadingState()
          .width("100%")
          .layoutWeight(1)
        } else if (this.selectedIndex === 0) {
          if (this.availableCoupons.length === 0) {
            this.emptyPlaceholder("暂无优惠券")
          } else {
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.availableCoupons, (coupon: CouponVo) => {
                ListItem() {
                  AvailableCouponCard({
                    coupon: coupon,
                    onReceive: (couponId: number) => {
                      this.onReceiveCoupon(couponId)
                    }
                  })
                }
              }, (coupon: CouponVo, index: number) => `${coupon.couponId ?? index}`)
            }
            .width("100%")
            .layoutWeight(1)
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING })
          }
        } else if (this.selectedIndex === 1) {
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
            .layoutWeight(1)
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING })
          }
        } else {
          if (this.historicalCoupons.length === 0) {
            this.emptyPlaceholder("暂无使用优惠券")
          } else {
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.historicalCoupons, (userCoupon: UserCouponVo) => {
                ListItem() {
                  CouponTicketCard({ userCoupon: userCoupon, isHistorical: true })
                }
              }, (userCoupon: UserCouponVo, index: number) => `${userCoupon.userCouponId ?? index}`)
            }
            .width("100%")
            .layoutWeight(1)
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING })
          }
        }
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .title("优惠券")
    .width("100%")
    .height("100%")
    .backgroundColor(DesignColor.BACKGROUND)
  }
}

// ========== 可领取优惠券卡片 ==========
@ComponentV2
struct AvailableCouponCard {
  @Param coupon: CouponVo | null = null
  @Event onReceive: (couponId: number) => void = (couponId: number) => {}

  // ArkTS build() 内仅允许 UI 语法（不允许 let/局部语句），文案计算收敛为私有方法
  private isReceived(): boolean {
    // CouponStatus 已对齐安卓 string 契约（"0"/"1"/"2"），直接与后端 status 比较
    return (this.coupon?.status ?? CouponStatus.ACTIVE) === CouponStatus.RECEIVE
  }

  private thresholdText(): string {
    return this.coupon?.threshold === 0 ?
      "无门槛" :
      `满${this.coupon?.threshold?.toFixed(0)}元可用`
  }

  private dateText(): string {
    return `${this.coupon?.startTime?.split(" ")[0] ?? ""} - ${this.coupon?.endTime?.split(" ")[0] ?? ""}`
  }

  build() {
    Row() {
      // 左侧金额
      Column() {
        Text(`￥${this.coupon?.discount?.toFixed(0) ?? "0"}`)
          .fontSize(24)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(DesignColor.PRIMARY)
      }
      .width(80)
      .alignItems(HorizontalAlign.Center)
      .justifyContent(FlexAlign.Center)

      // 竖线分隔
      Divider()
        .width(0.5)
        .height(48)
        .color(DesignColor.DIVIDER)
        .vertical(true)

      // 右侧信息
      Column() {
        Text(this.coupon?.title ?? "")
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        Text(this.dateText())
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .margin({ top: 4 })

        Text(this.thresholdText())
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 2 })

        if (this.isReceived()) {
          Text("已领取")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(8)
            .padding({ left: 16, right: 16, top: 6, bottom: 6 })
            .margin({ top: 8 })
        } else {
          Text("领取")
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(Color.White)
            .backgroundColor(DesignColor.PRIMARY)
            .borderRadius(8)
            .padding({ left: 16, right: 16, top: 6, bottom: 6 })
            .margin({ top: 8 })
            .onClick(() => {
              this.onReceive(this.coupon?.couponId ?? -1)
            })
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .padding({ left: 16 })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
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
  }
}

// ========== 票券卡片（已领取 / 领取记录共用，对齐安卓 UserCouponCard ticket3） ==========
@ComponentV2
struct CouponTicketCard {
  @Param userCoupon: UserCouponVo | null = null
  @Param isHistorical: boolean = false

  // 面额区宽度（与安卓票券面额区 88dp 同源）
  private amountWidth: number = 88
  // 票券卡片高度（安卓 88dp，打孔圆心据此定位）
  private cardHeight: number = 88
  // 打孔半径（安卓 7dp）
  private notchRadius: number = 7

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
    return this.userCoupon?.couponVo?.threshold === 0 ?
      "无门槛" :
      `满${this.userCoupon?.couponVo?.threshold?.toFixed(0)}元可用`
  }

  // 虚线段（ArkUI 无原生虚线，按规范 §3.1 票券卡以分段实线拼出）
  @Builder
  dashedSegment() {
    Column()
      .width(1)
      .height(4)
      .backgroundColor(DesignColor.OUTLINE)
      .borderRadius(0.5)
  }

  // 票券中间的虚线撕裂线
  @Builder
  dashedDivider() {
    Column({ space: 4 }) {
      this.dashedSegment()
      this.dashedSegment()
      this.dashedSegment()
      this.dashedSegment()
      this.dashedSegment()
      this.dashedSegment()
    }
    .height(this.cardHeight - 32)
    .justifyContent(FlexAlign.Center)
    .position({ x: this.amountWidth - 1, y: 16 })
    .hitTestBehavior(HitTestMode.None)
  }

  // 打孔（用页面底色覆盖，形成半圆凹口）
  @Builder
  notch(x: number) {
    Column()
      .width(this.notchRadius * 2)
      .height(this.notchRadius * 2)
      .borderRadius(this.notchRadius)
      .backgroundColor(DesignColor.BACKGROUND)
      .position({ x: x, y: this.cardHeight / 2 - this.notchRadius })
      .hitTestBehavior(HitTestMode.None)
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      Row() {
        // 左侧金额区（历史态 surfaceVariant，其余 brand-soft 纯色底）
        Column() {
          Text(`￥${this.userCoupon?.couponVo?.discount?.toFixed(0) ?? "0"}`)
            .fontSize(DesignFontSize.AMOUNT_MEDIUM)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(this.amountColor())
          Text("券")
            .fontSize(DesignFontSize.CAPTION)
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
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(this.titleColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Text(this.thresholdText())
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 4 })

          Text(`有效期至 ${this.userCoupon?.expiredAt ?? ""}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 4 })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        .padding({ left: 20, top: 20, bottom: 20 })

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

      // 中间虚线撕裂线
      this.dashedDivider()

      // 左侧凹口（票券左边缘）
      this.notch(-this.notchRadius)
      // 右侧凹口（面额区右边缘）
      this.notch(this.amountWidth - this.notchRadius)
    }
    .width("100%")
    .height(this.cardHeight)
  }
}
