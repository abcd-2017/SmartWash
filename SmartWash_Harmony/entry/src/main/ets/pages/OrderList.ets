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
import { DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { logger } from "../utils/Logger"
import { safeTopVp } from "../utils/SafeArea"
import { cancelPickupReminderForOrder, ReminderSyncOrder, syncPickupReminderOrders } from "../utils/ReminderService"
import vibrator from '@ohos.vibrator'
import { bumpOrderRefresh, bumpUserInfoRefresh } from "../utils/RefreshSignal"
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

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
      message: resString('confirm_cancel_order'),
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
  // 请求序号：第 1 页 replace 加载时递增；慢响应后到时按序号丢弃，防旧数据回写新状态
  private reqSeq: number = 0

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
    // 竞态防护（问题：await 期间用户可切页签/再触发重拉，慢响应回来会以「当前 orderStatus」
    // 写缓存/覆盖列表，实测 A 页签响应污染 B 页签缓存与显示）：快照发起时的状态与序号，
    // 响应按快照归属；第 1 页 replace 每次递增序号，旧响应按序号作废。
    if (page === 1 && replace) {
      this.reqSeq++
    }
    const statusSnapshot = this.orderStatus
    const seqSnapshot = this.reqSeq
    try {
      let res = await getOrderList({ status: this.orderStatus, page: page, size: PAGE_SIZE })
      // 已有更新序号的第 1 页响应（连续下拉/重拉）：整体丢弃，防旧数据回写
      if (page === 1 && replace && seqSnapshot !== this.reqSeq) {
        logger.debug('orderList', `丢弃过期响应 page=${page} status=${statusSnapshot} seq=${seqSnapshot}`)
        return false
      }
      if (res.code === HttpStatusCode.Ok) {
        if (res.data === null || res.data === undefined || res.data.length === 0) {
          if (page === 1) {
            // 状态已切换：空结果只写快照页签的缓存，不覆盖当前页签的 UI
            if (this.orderStatus !== statusSnapshot) {
              this.sectionCache.set(statusSnapshot, [])
            } else {
              this.sections = []
              this.sectionCache.set(this.orderStatus, [])
            }
          } else if (this.orderListLength() > 0) {
            showToastSafety(resString('order_list_1'))
          }
          this.isLoading = false
          this.contentOpacity = 1
          return false
        }
        this.appendOrders(res.data, replace, statusSnapshot)
        this.isLoading = false
        this.contentOpacity = 1
        return true
      }
      logger.warn('orderList', `订单列表加载失败 page=${page} status=${statusSnapshot} ` +
        `code=${res.code} message=${res.message ?? ""}`)
      showToastSafety(res.message, resString('order_list_2'))
      this.isLoading = false
      this.contentOpacity = 1
      return false
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并复位加载态，避免 isLoading 卡死永久骨架
      logger.warn('orderList', `订单列表请求异常 page=${page} status=${statusSnapshot}`)
      if (page === 1 && this.orderStatus === statusSnapshot) {
        this.sections = []
      }
      showToastSafety(resString('order_list_3'))
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
   * List 真实项数（供 PagingController 判定触底）：每个月份段渲染「月头 + 月卡」2 个 ListItem，
   * 末尾另有 1 个 LoadingFooter。
   * 不能用 orderListLength()（订单条数）当 listLength——onScrollIndex 的 endIndex 是 ListItem
   * 下标，恒小于订单数（如 20 条订单只对应 3~5 个 ListItem），page2 永不触发。
   */
  private listItemCount(): number {
    return this.sections.length * 2 + 1
  }

  /**
   * 追加订单并重建月份分组（D2 决策：数据源维持单状态分页拉取，
   * 列表结构对齐安卓——按 createdAt 降序、按 yyyy-MM 分组，组间倒序排列）。
   * @param replace true 时仅用本页数据重建（第 1 页语义），false 时与现有列表合并（触底加载）
   * @param statusKey 发起请求时的页签状态快照：缓存一律按快照键写入；
   *   响应返回时用户已切走（orderStatus ≠ 快照）则只写缓存不覆盖当前页签 UI，
   *   防止跨页签响应污染（竞态修复）
   */
  private appendOrders(data: Array<OrderInfo>, replace: boolean, statusKey: string): void {
    // 快照页签与当前页签一致才允许合并当前 UI 列表；跨页签响应只能基于缓存重建
    const mergingCurrent = statusKey === this.orderStatus
    const flat: Array<OrderInfo> = []
    if (!replace) {
      const source = mergingCurrent ? this.sections : (this.sectionCache.get(statusKey) ?? [])
      for (const s of source) {
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
    // 缓存按快照页签写入（切页签直接复用，不再重建）
    this.sectionCache.set(statusKey, next)
    // 仅快照页签仍为当前页签时才驱动 UI（跨页签响应不覆盖当前列表）
    if (mergingCurrent) {
      this.sections = next
    }
    this.syncPickupReminders(flat)
  }

  /**
   * 取件提醒同步（fire-and-forget，对齐调研候选 2 解耦原则）：
   * 列表数据就绪后按状态逐单决策——待取件(6)未登记则发布 30 分钟倒计时提醒，
   * 已完成/已取消/已退款则撤销对应提醒。同步内部全量容错（失败仅日志），
   * 不 await、不向列表主流程抛出任何异常。
   */
  private syncPickupReminders(orders: Array<OrderInfo>): void {
    const inputs: Array<ReminderSyncOrder> = orders.map((order: OrderInfo): ReminderSyncOrder => {
      return {
        orderId: order.orderId,
        status: order.status,
        lockerNumber: order.lockersVo?.lockerNumber ?? null
      }
    })
    syncPickupReminderOrders(inputs).catch((err: Object) => {
      // 双保险（sync 内部已兜底）：意外 throw 也不得影响列表
      logger.debug('orderList', `取件提醒同步意外失败（已忽略）${err instanceof Error ? err.message : ''}`)
    })
  }

  aboutToDisappear(): void {
    this.clearFadeTimer()
    // 自增 tick（问题3）：布尔标记重复 setOrCreate(true) 不产生变更事件
    bumpOrderRefresh()
    bumpUserInfoRefresh()
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
        // 取件提醒防御性撤销：取消仅允许在待支付态（该态不登记提醒），此处兜底清除可能的历史登记
        cancelPickupReminderForOrder(this.pendingCancelOrderId)
        // 全量刷新语义（对齐安卓 loadAllOrders）：单状态分页下即整列表重置重拉，
        // 其余页签切换时本就会重新拉取；同时同步用户中心订单计数
        bumpOrderRefresh()
        this.refreshOrder()
        showToastSafety(resString('cancel_success'))
      } else {
        logger.warn('orderList', `取消订单被拒 orderId=${this.pendingCancelOrderId} ` +
          `code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, resString('order_list_4'))
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并提示，避免 pendingCancelOrderId 卡死
      showToastSafety(resString('order_list_3'))
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
          .fontColor([$r('app.color.TEXT_PRIMARY')])
      }
      .width(48)
      .height(48)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .onClick(() => {
        pathStack.pop()
      })

      Text($r('app.string.my_orders'))
        .fontSize(DesignFontSize.ROOT_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor($r('app.color.TEXT_PRIMARY'))
    }
    .width("100%")
    .padding({ left: 12, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
    .alignItems(VerticalAlign.Center)
    .backgroundColor($r('app.color.BACKGROUND'))
  }

  /**
   * 状态筛选胶囊条（对齐安卓 AppPillTabRow）：
   * 选中 = primary 实底 + 白字；未选中 = surface 白底 + 1px outline 描边 + textSecondary；
   * 双层结构对齐安卓「clip→background 在 padding 之前，只扩热区不改视觉」的语义：
   * 外层热区 Column 48 高（无背景），内层视觉胶囊 45（文字行盒 + 上下 15）；可横滚。
   * 鸿蒙屏宽（364.6vp）窄于安卓（426.7dp），5 颗安卓尺寸胶囊会溢出，故横向 padding 由 16 收到 8
   * 使 5 颗全可见（字号保持 12fp 与安卓 labelMedium 一致）。
   */
  @Builder
  TabBar() {
    Scroll() {
      Row() {
        ForEach(this.list, (title: string, index: number) => {
          Column() {
            Row() {
              Text(getShowOrderStatusDescription(title))
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(index === this.currIndex ? $r('app.color.ON_PRIMARY') : $r('app.color.TEXT_SECONDARY'))
            }
            .justifyContent(FlexAlign.Center)
            // 视觉胶囊 45（对齐安卓 AppPillTabRow：padding 在 background 之前，
            // 背景覆盖纵向 padding → 视觉高 = 行盒 + 上下 15 ≈ 45，与安卓 45.3dp 一致）
            .padding({ left: DesignDimens.SPACE_XS, right: DesignDimens.SPACE_XS, top: 15, bottom: 15 })
            .borderRadius(DesignDimens.RADIUS_FULL)
            .backgroundColor(index === this.currIndex ? $r('app.color.PRIMARY') : $r('app.color.SURFACE'))
            .border({
              width: 1,
              color: index === this.currIndex ? $r('app.color.PRIMARY') : $r('app.color.OUTLINE')
            })
          }
          // 触控热区 48（对齐安卓 clickable 最小热区）；背景画在内层胶囊上，热区透明不影响视觉
          .height(48)
          .justifyContent(FlexAlign.Center)
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
    .backgroundColor($r('app.color.BACKGROUND'))
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
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
                      .fontColor($r('app.color.TEXT_SECONDARY'))
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
                  // 键值必须含数据指纹：仅用 monthKey 时，同月订单集变化（下拉刷新换新数组、
                  // 切页签回切缓存）后 ForEach 键相同会复用旧 OrderMonthCard 实例且不重执行
                  // itemGenerator，@Param orders 不同步 → UI 停留旧数据（实测 3 条列表显示 1 条）。
                  // 拼接订单 id 串后，数据变则键变触发重建，数据不变键不变仍复用。
                }, (section: MonthSection) =>
                  `${section.monthKey}:${section.orders.map((o: OrderInfo) => o.orderId ?? 0).join(",")}`)

                ListItem() {
                  LoadingFooter({ isLoading: !!this.isLoadingMore })
                }
              }
              .onScrollIndex((start: number, end: number) => {
                // listLength 必须是 ListItem 真实数量（月头 + 月卡 + footer），不是订单条数
                this.paging.onScrollIndex(end, this.listItemCount())
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
              EmptyState({ message: resString('home_hero_no_order') })
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
      .backgroundColor($r('app.color.BACKGROUND'))
      }
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
