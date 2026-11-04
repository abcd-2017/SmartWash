import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { OrderStatusList, getShowOrderStatusDescription } from "../constant/ShowOrderStatus"
import { cancelOrder, getOrderList } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { OrderMonthCard } from "../view/OrderCard"
import { PickupDeliveryParam } from "./PickupDelivery"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { LoadingFooter } from "../view/LoadingFooter"
import { showToastSafety } from "../utils/ToastUtil"
import { PagingController } from "../utils/PagingUtil"
import { AppConfirmDialog, EmptyState, LoadingState } from "../view/common/AppComponents"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { logger } from "../utils/Logger"
import { safeTopVp } from "../utils/SafeArea"
import vibrator from '@ohos.vibrator'

// 分页每页条数
const PAGE_SIZE: number = 10

/** 月份分组段（对齐安卓 OrderListItem 混合列表项：月份标题 + 整月订单组） */
interface MonthSection {
  monthKey: string
  orders: Array<OrderInfo>
}

@Builder
export function OrderListBuilder() {
  OrderList()
}

@Component
struct OrderList {
  @State sections: Array<MonthSection> = []
  private list = OrderStatusList()
  private orderStatus: string = "001"
  @State currIndex: number = 0
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  // 首载/页码 1 加载态：骨架门控（对齐安卓 showSkeleton = Loading && list.isEmpty()）
  @State isLoading: boolean = false
  // 切 tab 内容淡出/淡入（对齐安卓 AnimatedContent 300/200；ArkUI 无等价组件，用透明度近似）
  @State contentOpacity: number = 1
  private fadeTimer: number = -1
  // 各页签已加载列表缓存（对齐安卓 VM 按状态保留列表：切页签不重建，仅首载请求）
  private sectionCache: Map<string, Array<MonthSection>> = new Map()
  // 取消订单确认弹窗（对齐安卓 AppConfirmDialog，替代系统 promptAction 弹窗）
  private confirmCancelController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      message: "确定要取消订单？",
      onConfirm: () => {
        this.doCancelOrder()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })
  // 待取消的订单 id（确认弹窗回调用）
  private pendingCancelOrderId: number | null = null
  // 轻量分页封装：触底加载/上锁防重/封底判定统一收敛（差异分析 01 §3.3）
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      // 第 1 页整体替换，后续页追加（对齐安卓 loadAllOrders / loadMore）
      return this.fetchOrderPage(page, page === 1)
    },
    (loading: boolean) => {
      this.isLoadingMore = loading
    }
  )

  aboutToAppear(): void {
    // 安全取参（评审 #31）：参数缺失/非法时兜底第 0 个页签
    this.currIndex = getNumberParam(RouteNames.ORDER_LIST) ?? 0
    this.orderStatus = this.list[this.currIndex]
    logger.debug('orderList', `订单列表页挂载 初始页签=${this.currIndex}`)
    this.fetchOrderPage(1, true)
  }

  /**
   * 按页拉取订单列表。
   * @param replace 是否整体替换（第 1 页 true；触底加载 false）
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchOrderPage(page: number, replace: boolean): Promise<boolean> {
    logger.debug('orderList', `拉取订单列表 page=${page} status=${this.orderStatus} replace=${replace}`)
    if (page === 1) {
      this.isLoading = true
    }
    try {
      let res = await getOrderList({ status: this.orderStatus, page: page, size: PAGE_SIZE })
      if (res.code === HttpStatusCode.Ok) {
        if (res.data === null || res.data === undefined || res.data.length === 0) {
          if (page === 1) {
            this.sections = []
            this.sectionCache.set(this.orderStatus, [])
          } else if (this.orderListLength() > 0) {
            showToastSafety("没有更多数据了")
          }
          this.isLoading = false
          this.contentOpacity = 1
          return false
        }
        this.appendOrders(res.data, replace)
        this.isLoading = false
        this.contentOpacity = 1
        return true
      }
      logger.warn('orderList', `订单列表加载失败 page=${page} status=${this.orderStatus} ` +
        `code=${res.code} message=${res.message ?? ""}`)
      showToastSafety(res.message, "订单加载失败")
      this.isLoading = false
      this.contentOpacity = 1
      return false
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并复位加载态，避免 isLoading 卡死永久骨架
      if (page === 1) {
        this.sections = []
      }
      showToastSafety("网络异常，请稍后重试")
      this.isLoading = false
      this.contentOpacity = 1
      return false
    }
  }

  /** 当前状态下的订单总数（跨月份段累计） */
  private orderListLength(): number {
    let total = 0
    for (const s of this.sections) {
      total += s.orders.length
    }
    return total
  }

  /**
   * 追加订单并重建月份分组（D2 决策：数据源维持单状态分页拉取，
   * 列表结构对齐安卓——按 createdAt 降序、按 yyyy-MM 分组，组间倒序排列）。
   * @param replace true 时仅用本页数据重建（第 1 页语义），false 时与现有列表合并（触底加载）
   */
  private appendOrders(data: Array<OrderInfo>, replace: boolean): void {
    const flat: Array<OrderInfo> = []
    if (!replace) {
      for (const s of this.sections) {
        flat.push(...s.orders)
      }
    }
    flat.push(...data)
    flat.sort((a: OrderInfo, b: OrderInfo) => (b.createdAt ?? "").localeCompare(a.createdAt ?? ""))

    const grouped: Map<string, Array<OrderInfo>> = new Map()
    for (const order of flat) {
      const key = (order.createdAt ?? "").substring(0, 7)
      const bucket = grouped.get(key)
      if (bucket === undefined) {
        grouped.set(key, [order])
      } else {
        bucket.push(order)
      }
    }
    const keys = Array.from(grouped.keys()).sort((a: string, b: string) => b.localeCompare(a))
    const next: Array<MonthSection> = []
    for (const key of keys) {
      const orders = grouped.get(key)
      if (orders !== undefined) {
        const section: MonthSection = { monthKey: key, orders: orders }
        next.push(section)
      }
    }
    this.sections = next
    // 缓存当前页签列表（切页签直接复用，不再重建）
    this.sectionCache.set(this.orderStatus, next)
  }

  aboutToDisappear(): void {
    this.clearFadeTimer()
    AppStorage.setOrCreate("refreshOrderCount", true)
    AppStorage.setOrCreate("refreshUserInfo", true)
  }

  /** 清理切 tab 淡入定时器（避免页面销毁后回调改状态） */
  private clearFadeTimer(): void {
    if (this.fadeTimer !== -1) {
      clearTimeout(this.fadeTimer)
      this.fadeTimer = -1
    }
  }

  /**
   * 刷新当前页签（下拉刷新 / 返回页面 / 取消订单后）。
   * 不清空列表：请求返回前保留旧列表（对齐安卓 stale-while-revalidate，取消后不闪空）。
   */
  refreshOrder() {
    this.isRefreshing = false
    this.paging.reset()
    this.fetchOrderPage(1, true)
  }

  /** 确认取消订单：成功后 toast + 当前状态全量重拉，并打标记让订单计数同步刷新 */
  async doCancelOrder() {
    if (this.pendingCancelOrderId === null) {
      logger.debug('orderList', '取消订单确认回调缺少待取消订单 id，忽略本次触发')
      return
    }
    try {
      let res = await cancelOrder(this.pendingCancelOrderId)
        logger.info('orderList', `取消订单成功 orderId=${this.pendingCancelOrderId}`)
      // 后端 Result.ok(boolean) 直传：data=false（CAS 失败，订单已被支付/取消）必须按失败处理，
      // 不能只看 code=200 就弹「取消成功」（对齐 AddSchool 对 getUserByStudentId 的判法）
      if (res.code === HttpStatusCode.Ok && res.data === true) {
        // 全量刷新语义（对齐安卓 loadAllOrders）：单状态分页下即整列表重置重拉，
        // 其余页签切换时本就会重新拉取；同时同步用户中心订单计数
        AppStorage.setOrCreate("refreshOrderCount", true)
        this.refreshOrder()
        showToastSafety("取消成功")
      } else {
        logger.warn('orderList', `取消订单被拒 orderId=${this.pendingCancelOrderId} ` +
          `code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "取消失败，订单状态可能已变更")
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并提示，避免 pendingCancelOrderId 卡死
      showToastSafety("网络异常，请稍后重试")
    } finally {
      this.pendingCancelOrderId = null
    }
  }

  @Builder
  PageHeader() {
    Row() {
      // 返回热区 48（安卓 PageHeader：行首让位 12，图形左缘落在 24）
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

      Text("我的订单")
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
    }
    .width("100%")
    .padding({ left: 12, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(DesignColor.BACKGROUND)
  }

  /**
   * 状态筛选胶囊条（对齐安卓 AppPillTabRow）：
   * 选中 = primary 实底 + 白字；未选中 = surface 白底 + 1px outline 描边 + textSecondary；
   * radiusFull，视觉高 34（文字行盒 16 + 上下 9），垂直 padding 9 → 14 把触控热区抬到 44；可横滚。
   */
  @Builder
  TabBar() {
    Scroll() {
      Row() {
        ForEach(this.list, (title: string, index: number) => {
          Row() {
            Text(getShowOrderStatusDescription(title))
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor(index === this.currIndex ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
          }
          .justifyContent(FlexAlign.Center)
          // 垂直 9 → 14 把触控热区抬到 44dp（规范 §2.3；胶囊视觉高 34 不变）
          .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 14, bottom: 14 })
          .borderRadius(DesignDimens.RADIUS_FULL)
          .backgroundColor(index === this.currIndex ? DesignColor.PRIMARY : DesignColor.SURFACE)
          .border({
            width: 1,
            color: index === this.currIndex ? DesignColor.PRIMARY : DesignColor.OUTLINE
          })
          .margin({ right: DesignDimens.SPACE_XS })
          .onClick(() => {
            if (this.currIndex !== index) {
              // tab 切换 SELECTION 触感（对齐安卓 AppPillTabRow）
              try {
                vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
              } catch (err) {
                // 忽略不支持的设备
              }
              this.currIndex = index
              this.orderStatus = this.list[index]
              logger.debug('orderList', `切换订单页签 index=${index} status=${this.orderStatus}`)
              this.paging.reset()
              // 切 tab 内容淡出 → 数据到位淡入（对齐安卓 AnimatedContent 300/200）
              this.contentOpacity = 0
              const cached = this.sectionCache.get(this.orderStatus)
              if (cached !== undefined) {
                // 已加载页签：直接复用缓存（对齐安卓 VM 按状态保留列表）
                this.sections = cached
                this.clearFadeTimer()
                this.fadeTimer = setTimeout(() => {
                  this.contentOpacity = 1
                }, 200)
              } else {
                this.sections = []
                this.fetchOrderPage(1, true)
              }
            }
          })
        }, (title: string) => title)
      }
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    }
    .width("100%")
    .scrollable(ScrollDirection.Horizontal)
    .scrollBar(BarState.Off)
    .backgroundColor(DesignColor.BACKGROUND)
  }

  build() {
    NavDestination() {
      Column() {
        // 自定义页面标题
        this.PageHeader()

        Blank()
          .height(DesignDimens.SPACE_SM)

        // 状态筛选胶囊条
        this.TabBar()

        Blank()
          .height(DesignDimens.SPACE_XL)

        // 内容区域（切 tab 淡入淡出容器，对齐安卓 AnimatedContent 300/200）
        Column() {
          if (this.isLoading && this.orderListLength() === 0) {
            // 首载骨架（对齐安卓 LoadingState；有旧数据时走 stale-while-revalidate 不闪骨架）
            LoadingState()
              .width("100%")
              .layoutWeight(1)
          } else if (this.orderListLength() > 0) {
            Refresh({ refreshing: $$this.isRefreshing }) {
              List({ space: DesignDimens.CARD_SPACING }) {
                ForEach(this.sections, (section: MonthSection) => {
                  // 月份标题（对齐安卓 labelSmall 12/Medium/ls0.3，卡间距由 List space 统一给出）
                  ListItem() {
                    Text(this.monthLabel(section.monthKey))
                      .fontSize(DesignFontSize.CAPTION)
                      .fontWeight(DesignFontWeight.MEDIUM)
                      .fontColor(DesignColor.TEXT_SECONDARY)
                      .letterSpacing(0.3)
                      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
                  }

                  // 整月订单合并卡
                  ListItem() {
                    OrderMonthCard({
                      orders: section.orders,
                      cancelClick: (order: OrderInfo) => {
                        this.pendingCancelOrderId = order.orderId
                        this.confirmCancelController.open()
                      },
                      paymentClick: (order: OrderInfo) => {
                        // 返回时刷新交给 NavDestination.onShown（pop() 无 result 时 onPop 不触发，评审 #8）
                        pathStack.pushPathByName(RouteNames.PAYMENT, order.orderId)
                      },
                      shipmentClick: (order: OrderInfo) => {
                        const param: PickupDeliveryParam = new PickupDeliveryParam()
                        param.order = order
                        param.isPickup = false
                        pathStack.pushPathByName(RouteNames.PICKUP_DELIVERY, param)
                      },
                      pickupClick: (order: OrderInfo) => {
                        const param: PickupDeliveryParam = new PickupDeliveryParam()
                        param.order = order
                        param.isPickup = true
                        pathStack.pushPathByName(RouteNames.PICKUP_DELIVERY, param)
                      },
                      itemClick: (order: OrderInfo) => {
                        pathStack.pushPathByName(RouteNames.ORDER_DETAIL, order.orderId)
                      },
                    })
                  }
                  .padding({
                    left: DesignDimens.PAGE_PADDING,
                    right: DesignDimens.PAGE_PADDING
                  })
                }, (section: MonthSection) => section.monthKey)

                ListItem() {
                  LoadingFooter({ isLoading: !!this.isLoadingMore })
                }
              }
              .onScrollIndex((start: number, end: number) => {
                this.paging.onScrollIndex(end, this.orderListLength())
              })
              .onScrollFrameBegin((offset: number, state: ScrollState) => {
                this.paging.onScrollFrameBegin(offset)
                return { offsetRemain: offset }
              })
              .width("100%")
              .height("100%")
            }
            .layoutWeight(1)
            .onRefreshing(() => {
              this.refreshOrder()
            })
          } else {
            // 空状态（对齐安卓 EmptyState：DrumMark 48 + 引导文案）
            Column() {
              EmptyState({ message: "暂无订单" })
            }
            .width("100%")
            .layoutWeight(1)
            .justifyContent(FlexAlign.Center)
          }
        }
        .width("100%")
        .layoutWeight(1)
        .opacity(this.contentOpacity)
        .animation({ duration: 200, curve: Curve.EaseOut })
      }
      .width("100%")
      .height("100%")
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 支付/寄件等 pop() 均不带 result，原 onPop 回调不触发（评审 #8）；
      // 改为每次展示时重置分页后重拉（保留旧列表，请求返回后整体替换，避免追加重复数据）
      this.refreshOrder()
    })
  }

  /** "yyyy-MM" → "yyyy年M月"（解析失败兜底原串） */
  private monthLabel(monthKey: string): string {
    const parts = monthKey.split("-")
    if (parts.length === 2) {
      return `${parts[0]}年${Number(parts[1])}月`
    }
    return monthKey
  }
}
