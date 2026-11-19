import { HttpStatusCode } from "@ohos/axios"
import {
  COUPON_PAGE_SIZE,
  COUPON_STATUS_USABLE,
  COUPON_STATUS_USED,
  getAllCoupons,
  getUserCoupon,
  receiveCoupon
} from "../network/api/CouponApi"
import { CouponVo } from "../network/vo/CouponVo"
import { UserCouponVo } from "../network/vo/UserCouponVo"
import { CouponStatus } from "../constant/CouponStatus"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { Debouncer } from "../utils/DebounceUtil"
import { LazyListDataSource, PagingController } from "../utils/PagingUtil"
import { LoadingFooter } from "../view/LoadingFooter"
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { ReduceMotion } from "../utils/ReduceMotion"
import { AppButton, AppButtonVariant, AppPillTabRow, EmptyState, LoadingState, PageHeader } from "../view/common/AppComponents"
import { TicketDashedLine, TicketNotch } from "../view/TicketNotchShape"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import vibrator from '@ohos.vibrator'
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

// 领取动作的节流间隔（毫秒）：后端领券无幂等，连点会重复发请求（评审报告 P0 背景），
// 前端节流 + loading 是唯二防线（对齐安卓 rememberDebouncedClick）
const RECEIVE_THROTTLE_MS: number = 800

/**
 * 券面额格式化（对齐安卓 String.format("%.0f") + 非整数保留 2 位）：
 * 整数不显示小数，非整数保留 2 位；¥ 前缀由调用方统一拼接。
 * 用于已领取/历史卡（安卓 UserCouponCard 走 currency_format "¥%s"）。
 */
function formatCouponAmount(value: number | null | undefined): string {
  const v = value ?? 0
  return Number.isInteger(v) ? v.toString() : v.toFixed(2)
}

/**
 * 浮点展示格式化（对齐安卓 `"${value}"` 模板 → Kotlin Float.toString 口径）：
 * 整数保留 1 位小数（10 → "10.0"），非整数取最短表示（0.01 → "0.01"）。
 * 用于可用卡面额（安卓 AvailableCouponsTab "%s元"）与门槛（"满%s元可用"）。
 */
function formatFloatShort(value: number | null | undefined): string {
  const v = value ?? 0
  return Number.isInteger(v) ? v.toFixed(1) : String(v)
}

@Builder
export function CouponBuilder() {
  Coupon()
}

@ComponentV2
struct Coupon {
  // 三类券数据（一次聚合拉取，切 tab 仅切内存列表）
  @Local availableCoupons: Array<CouponVo> = []
  @Local claimedCoupons: Array<UserCouponVo> = []
  @Local historicalCoupons: Array<UserCouponVo> = []
  @Local selectedIndex: number = 0
  // 首屏/刷新加载态（对齐安卓全屏 LoadingState）
  @Local isLoading: boolean = false
  // 首载失败标记：三列表全空且失败时展示 EmptyState + 重试（对齐安卓 CouponContentState.Error）
  @Local loadFailed: boolean = false
  // 领取请求进行中的券 ID（逐券标记，对齐安卓 claimingId；全页 receiving 会误伤其他卡）
  @Local claimingCouponId: number | null = null
  // 已领取/历史触底加载态（各 tab 独立 footer spinner）
  @Local claimedLoadingMore: boolean = false
  @Local historicalLoadingMore: boolean = false
  // 领取点击节流（防抖第一道防线）
  private receiveDebouncer: Debouncer = new Debouncer()
  // LazyForEach 数据源（实例稳定，读取对应 @State 数组）
  private availableSource: LazyListDataSource<CouponVo> =
    new LazyListDataSource<CouponVo>(() => this.availableCoupons)
  private claimedSource: LazyListDataSource<UserCouponVo> =
    new LazyListDataSource<UserCouponVo>(() => this.claimedCoupons)
  private historicalSource: LazyListDataSource<UserCouponVo> =
    new LazyListDataSource<UserCouponVo>(() => this.historicalCoupons)
  // 已领取/历史分页控制器（可领取为有界全量，无分页）
  private claimedPaging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchUserCouponPage(COUPON_STATUS_USABLE, page)
    },
    (loading: boolean): void => {
      this.claimedLoadingMore = loading
    }
  )
  private historicalPaging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchUserCouponPage(COUPON_STATUS_USED, page)
    },
    (loading: boolean): void => {
      this.historicalLoadingMore = loading
    }
  )

  // Tab 标题（可领取 tab 的计数在 tabLabel 中动态拼接；第三 tab 文案对齐安卓 historical_coupons「历史」）
  // F1 语义修正（对齐安卓 a8e7bd9d，2026-11-19 实测定案）：第一 tab 绑定可领取广场券目录
  // （availableCoupons）计数，与用户手持可用券（已领取 tab 中标「可用」）语义错位，
  // 出现「可用 0」而已领取 1 张可使用的自相矛盾；对齐安卓无计数版既有「可领取」口径
  private readonly tabTitles: string[] = [resString('available_coupons'), resString('claimed_coupons'), resString('historical_coupons')]

  async aboutToAppear() {
    logger.debug('coupon', '优惠券页挂载，拉取三类券列表')
    await this.loadAllCoupons()
  }

  aboutToDisappear() {
    this.receiveDebouncer.cancel()
  }

  /**
   * 首屏加载：可领取仍走聚合接口 allCoupons（量小、为 tab 计数），
   * 已领取/历史改走分页接口 getUserCoupon(status) 首屏（对齐后端分页改造）。
   * 领券成功后复用本方法全量刷新，保证可领取 tab 计数与已领取/历史列表一致。
   */
  async loadAllCoupons() {
    this.isLoading = true
    try {
      let res = await getAllCoupons()
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        this.loadFailed = false
        this.availableCoupons = res.data.available ?? []
        this.availableSource.notifyReload()
      } else {
        logger.warn('coupon', `优惠券列表加载失败 code=${res.code} message=${res.message ?? ""}`)
        this.loadFailed = true
        showToastSafety(res.message, resString('coupon_load_failed'))
      }
    } catch (err) {
      this.loadFailed = true
      showToastSafety(resString('coupon_2'))
    }
    // 已领取/历史首屏（并行；失败仅日志 + 空态，不拖垮可领取 tab）
    // 先重置分页：领券后重拉首页，避免控制器残留旧页码导致触底跳页
    this.claimedPaging.reset()
    this.historicalPaging.reset()
    await Promise.all([
      this.fetchUserCouponPage(COUPON_STATUS_USABLE, 1),
      this.fetchUserCouponPage(COUPON_STATUS_USED, 1)
    ])
    this.isLoading = false
  }

  /**
   * 按状态分页拉取用户优惠券（0-可用，1-已使用或过期）。
   * 第 1 页整体替换并 notifyReload，后续页追加并 notifyAppend。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchUserCouponPage(status: string, page: number): Promise<boolean> {
    try {
      const res = await getUserCoupon(status, page, COUPON_PAGE_SIZE)
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        const records: Array<UserCouponVo> = res.data.records
        if (page === 1) {
          if (status === COUPON_STATUS_USABLE) {
            this.claimedCoupons = records
            this.claimedSource.notifyReload()
          } else {
            this.historicalCoupons = records
            this.historicalSource.notifyReload()
          }
        } else if (records.length > 0) {
          if (status === COUPON_STATUS_USABLE) {
            const startIndex: number = this.claimedCoupons.length
            this.claimedCoupons = this.claimedCoupons.concat(records)
            this.claimedSource.notifyAppend(startIndex, records.length)
          } else {
            const startIndex: number = this.historicalCoupons.length
            this.historicalCoupons = this.historicalCoupons.concat(records)
            this.historicalSource.notifyAppend(startIndex, records.length)
          }
        }
        logger.debug('coupon', `用户优惠券分页加载成功 status=${status} page=${page} count=${records.length}`)
        return records.length > 0
      }
      logger.warn('coupon', `用户优惠券分页未生效 status=${status} page=${page} code=${res.code}`)
      return false
    } catch (err) {
      logger.warn('coupon', `用户优惠券分页异常 status=${status} page=${page}`)
      return false
    }
  }

  /** 三列表是否全空（对齐安卓 listsEmpty，用于骨架/失败态门控） */
  private isAllEmpty(): boolean {
    return this.availableCoupons.length === 0
      && this.claimedCoupons.length === 0
      && this.historicalCoupons.length === 0
  }

  // Tab 文案：可领取 tab 带真实计数（对齐安卓 available_coupons_with_count 修后值「可领取 %1$d」，无括号）
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
        // 低频关键动作成功：领券完成留 info
        logger.info('coupon', `优惠券领取成功 couponId=${couponId}`)
        await this.loadAllCoupons()
        showToastSafety(resString('claim_success'))
      } else {
        logger.warn('coupon', `优惠券领取被拒 couponId=${couponId} code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, resString('coupon_3'))
      }
    } catch (err) {
      showToastSafety(resString('coupon_2'))
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
  // 可领取为 allCoupons 全量（量小），无分页；仍用 LazyForEach 懒加载渲染。
  @Builder
  availablePage() {
    if (this.availableCoupons.length === 0) {
      this.emptyPlaceholder(resString('no_coupons_available'))
    } else {
      List({ space: DesignDimens.CARD_SPACING }) {
        LazyForEach(this.availableSource, (coupon: CouponVo) => {
          ListItem() {
            AvailableCouponCard({
              coupon: coupon,
              isClaiming: this.claimingCouponId === coupon.couponId,
              onReceive: (couponId: number) => {
                this.onReceiveCoupon(couponId)
              }
            })
          }
          // 列表项进出场统一走 ReduceMotion.listItemTransition（对齐安卓 animatedListItem）
          .transition(ReduceMotion.listItemTransition())
        // 键必须含状态指纹（问题5 根因）：领取成功 loadAllCoupons 整表替换对象，
        // 仅用 couponId 时键不变 → 复用旧子组件、@Param 不同步 → 按钮定格「立即领取」
        }, (coupon: CouponVo, index: number) => `${coupon.couponId ?? index}:${coupon.status ?? ""}`)
      }
      .width("100%")
      .height("100%")
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING, bottom: safeBottomVp() })
    }
  }

  // ========== 已领取 tab（Swiper 第 1 页，分页 status=0 触底加载） ==========
  @Builder
  claimedPage() {
    if (this.claimedCoupons.length === 0) {
      this.emptyPlaceholder(resString('no_coupons_available'))
    } else {
      List({ space: DesignDimens.CARD_SPACING }) {
        LazyForEach(this.claimedSource, (userCoupon: UserCouponVo) => {
          ListItem() {
            CouponTicketCard({ userCoupon: userCoupon, isHistorical: false })
          }
          // 列表项进出场统一走 ReduceMotion.listItemTransition（对齐安卓 animatedListItem）
          .transition(ReduceMotion.listItemTransition())
        // 键含 isUsed 指纹：刷新换对象后键不变会复用旧子组件（同可用 tab 键问题）
        }, (userCoupon: UserCouponVo, index: number) =>
          `${userCoupon.userCouponId ?? index}:${userCoupon.isUsed ? 1 : 0}`)
        ListItem() {
          LoadingFooter({ isLoading: this.claimedLoadingMore })
        }
      }
      .width("100%")
      .height("100%")
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING, bottom: safeBottomVp() })
      .onScrollIndex((start: number, end: number) => {
        // listLength 为 ListItem 真实数量（券卡 + footer），不是数据条数
        this.claimedPaging.onScrollIndex(end, this.claimedCoupons.length + 1)
      })
      .onScrollFrameBegin((offset: number, state: ScrollState) => {
        this.claimedPaging.onScrollFrameBegin(offset)
        return { offsetRemain: offset }
      })
    }
  }

  // ========== 历史 tab（Swiper 第 2 页，分页 status=1 触底加载） ==========
  @Builder
  historicalPage() {
    if (this.historicalCoupons.length === 0) {
      this.emptyPlaceholder(resString('no_historical_coupons'))
    } else {
      List({ space: DesignDimens.CARD_SPACING }) {
        LazyForEach(this.historicalSource, (userCoupon: UserCouponVo) => {
          ListItem() {
            CouponTicketCard({ userCoupon: userCoupon, isHistorical: true })
          }
          // 列表项进出场统一走 ReduceMotion.listItemTransition（对齐安卓 animatedListItem）
          .transition(ReduceMotion.listItemTransition())
        // 键含 isUsed 指纹：核销状态变化后对象替换、键不变会复用旧子组件（同可用 tab 键问题）
        }, (userCoupon: UserCouponVo, index: number) =>
          `${userCoupon.userCouponId ?? index}:${userCoupon.isUsed ? 1 : 0}`)
        ListItem() {
          LoadingFooter({ isLoading: this.historicalLoadingMore })
        }
      }
      .width("100%")
      .height("100%")
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.CARD_SPACING, bottom: safeBottomVp() })
      .onScrollIndex((start: number, end: number) => {
        this.historicalPaging.onScrollIndex(end, this.historicalCoupons.length + 1)
      })
      .onScrollFrameBegin((offset: number, state: ScrollState) => {
        this.historicalPaging.onScrollFrameBegin(offset)
        return { offsetRemain: offset }
      })
    }
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
      Column() {
        // ========== 页头（公共 PageHeader：22/600 + 48 热区返回，对齐安卓 CouponPage.kt:148） ==========
        PageHeader({ title: resString('service_coupon') })

        // ========== 胶囊分段标签栏（公共 AppPillTabRow，对齐安卓 AppPillTabRow） ==========
        // 顶部间距对齐安卓：AppPillTabRow 自带 padding(top=12, bottom=4) + 其后 Spacer(8)
        AppPillTabRow({
          tabs: this.tabTitles.map((_: string, index: number): string => this.tabLabel(index)),
          selectedIndex: this.selectedIndex,
          tabSelected: (index: number) => {
            this.onTabChange(index)
          }
        })
          .padding({ top: DesignDimens.SPACE_SM, bottom: 4 })

        Column()
          .height(DesignDimens.SPACE_XS)

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
              message: resString('coupon_load_failed'),
              actionText: resString('retry'),
              actionVariant: AppButtonVariant.SECONDARY,
              onAction: () => {
                this.loadAllCoupons()
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
      .padding({ top: safeTopVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .width("100%")
    .height("100%")
    .backgroundColor($r('app.color.BACKGROUND'))
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
  // 卡高 wrap-content（对齐安卓 CouponCard 的 IntrinsicSize.Min）：由右列内容撑起，
  // onAreaChange 捕获实高供虚线撕裂线（= 卡高 - 24）与打孔定位使用
  @Local cardHeight: number = 0

  private isReceived(): boolean {
    // CouponStatus 已对齐安卓 string 契约（"0"/"1"/"2"），直接与后端 status 比较
    return (this.coupon?.status ?? CouponStatus.ACTIVE) === CouponStatus.RECEIVE
  }

  // 面额文案（对齐安卓 AvailableCouponsTab：discount_amount_format "%s元" + "${discount}"）
  private discountText(): string {
    return `${formatFloatShort(this.coupon?.discount)}元`
  }

  // 门槛文案（对齐安卓 no_threshold / coupon_min_amount_format「满%s元可用」）
  private thresholdText(): string {
    return (this.coupon?.threshold ?? 0) === 0 ?
      resString('no_threshold') :
      `满${formatFloatShort(this.coupon?.threshold)}元可用`
  }

  private dateText(): string {
    return `${this.coupon?.startTime?.split(" ")[0] ?? ""} - ${this.coupon?.endTime?.split(" ")[0] ?? ""}`
  }

  private claimLabel(): string {
    // 对齐安卓 claim_now / claimed
    return this.isReceived() ? resString('claimed_coupons') : resString('claim_now')
  }

  private hapticSelection(): void {
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
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
        // 真机缺陷修复（T4）：内层 Row 不可设 width("100%")——父 Column 为 wrap-content，
        // 百分比会取父约束最大宽度把胶囊撑满整行；去掉后恢复 wrap-content（对齐安卓 ClaimAction）
        .height("100%")
        .justifyContent(FlexAlign.Center)
        .alignItems(VerticalAlign.Center)
      }
      .height(36)
      .borderRadius(DesignDimens.RADIUS_MD)
      .backgroundColor(this.isReceived() ? $r('app.color.TEXT_SECONDARY') : $r('app.color.PRIMARY'))
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
          Text(this.discountText())
            .fontSize(DesignFontSize.AMOUNT_MEDIUM)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor($r('app.color.PRIMARY_DARK'))
          Text($r('app.string.service_coupon'))
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.PRIMARY'))
            .margin({ top: 4 })
        }
        .width(this.amountWidth)
        // wrap-content 行高由右列撑起，面额区用 alignSelf(Stretch) 拉伸到行高
        // （对齐安卓 IntrinsicSize.Min；percentage 高度在 wrap-content 父级内会取约束上限，不可用）
        .alignSelf(ItemAlign.Stretch)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor($r('app.color.PRIMARY_CONTAINER'))

        // 右侧两行结构（对齐安卓 2026-11-01 实机修正）：上 = 标题/日期/门槛；下 = 领取按钮右对齐
        Column() {
          Text(this.coupon?.title ?? "")
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Text(this.dateText())
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .margin({ top: 4 })

          Text(this.thresholdText())
            .fontSize(DesignFontSize.CAPTION)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor($r('app.color.TEXT_SECONDARY'))
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
      .alignItems(VerticalAlign.Center)
      .backgroundColor($r('app.color.SURFACE'))
      .borderRadius(DesignDimens.CARD_RADIUS)
      // 卡片唯一画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影（0 2px 12px rgba(20,40,30,.05)）
      .border({ width: 1, color: $r('app.color.OUTLINE') })
      .shadow({
        radius: DesignElevation.LEVEL_1,
        color: $r('app.color.SHADOW_COLOR_CARD'),
        offsetX: 0,
        offsetY: 2
      })
      .clip(true)

      // 中间虚线撕裂线（段 6 / 隔 4 / 上下留 12，对齐安卓 CouponPage.kt:250-266）
      // 卡高为 wrap-content，实高由 onAreaChange 捕获后再渲染（首帧 cardHeight=0 不绘制）
      if (this.cardHeight > 0) {
        TicketDashedLine({ dashHeight: this.cardHeight - 24, dashColor: $r('app.color.OUTLINE') })
          .position({ x: this.amountWidth - 0.5, y: 12 })
      }

      // 左右缘中点打孔（φ14，对齐安卓 TicketShape）；与虚线同为实高依赖，
      // 待 onAreaChange 捕获卡高后再渲染（避免 '100%' 在 wrap-content Stack 内撑满视口）
      if (this.cardHeight > 0) {
        TicketNotch({ heightValue: this.cardHeight })
      }
    }
    .width("100%")
    .onAreaChange((_oldValue: Area, newValue: Area) => {
      const height: number = typeof newValue.height === 'number' ? newValue.height : 0
      if (height > 0 && Math.abs(height - this.cardHeight) > 0.5) {
        this.cardHeight = height
      }
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
  // 票券卡片高度（安卓 88dp）
  private cardHeight: number = 88

  private amountColor(): ResourceColor {
    return this.isHistorical ? $r('app.color.TEXT_TERTIARY') : $r('app.color.PRIMARY_DARK')
  }

  private unitColor(): ResourceColor {
    return this.isHistorical ? $r('app.color.TEXT_TERTIARY') : $r('app.color.PRIMARY')
  }

  private titleColor(): ResourceColor {
    return this.isHistorical ? $r('app.color.TEXT_SECONDARY') : $r('app.color.TEXT_PRIMARY')
  }

  private statusColor(): ResourceColor {
    return this.isHistorical ? $r('app.color.TEXT_TERTIARY') : $r('app.color.PRIMARY')
  }

  private statusText(): string {
    // 历史态按 isUsed 区分「已使用/已过期」，已领取态恒为「可用」（对齐安卓）
    return this.isHistorical ? (this.userCoupon?.isUsed ? resString('used') : resString('expired')) : resString('coupon_1')
  }

  private thresholdText(): string {
    return (this.userCoupon?.couponVo?.threshold ?? 0) === 0 ?
      resString('no_threshold') :
      `满${formatFloatShort(this.userCoupon?.couponVo?.threshold)}元可用`
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
          // 单位对齐安卓 UserCouponCard 的 R.string.coupon「优惠券」
          Text($r('app.string.service_coupon'))
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(this.unitColor())
            .margin({ top: 5 })
        }
        .width(this.amountWidth)
        .height(this.cardHeight)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor(this.isHistorical ? $r('app.color.SURFACE_VARIANT') : $r('app.color.PRIMARY_CONTAINER'))

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
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .margin({ top: 4 })

          // 有效期文案对齐安卓 valid_until「有效期至：%s」（含全角冒号）
          Text(`有效期至：${this.userCoupon?.expiredAt ?? ""}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
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
      .backgroundColor($r('app.color.SURFACE'))
      .borderRadius(DesignDimens.CARD_RADIUS)
      // 票券卡同标准卡画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影
      .border({ width: 1, color: $r('app.color.OUTLINE') })
      .shadow({
        radius: DesignElevation.LEVEL_1,
        color: $r('app.color.SHADOW_COLOR_CARD'),
        offsetX: 0,
        offsetY: 2
      })
      .clip(true)
      .opacity(this.isHistorical ? 0.55 : 1.0)

      // 中间虚线撕裂线（段 6 / 隔 4 / 上下留 12）
      TicketDashedLine({ dashHeight: this.cardHeight - 24, dashColor: $r('app.color.OUTLINE') })
        .position({ x: this.amountWidth - 0.5, y: 12 })

      // 左右缘中点打孔（φ14，对齐安卓 TicketShape）
      TicketNotch()
    }
    .width("100%")
    .height(this.cardHeight)
  }
}
