import { HttpStatusCode } from "@ohos/axios"
import { BusinessError, pasteboard } from "@kit.BasicServicesKit"
import vibrator from '@ohos.vibrator'
import { RouteNames } from "../constant/RouteNames"
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionTiming } from "../constant/DesignSystem"
import { getOrderStatusDescription, OrderStatusEnum } from "../constant/OrderStatus"
import { getOrderInfo } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getNumberParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { AppButton, AppButtonVariant, EmptyState, IconBox, LoadingState } from "../view/common/AppComponents"
import { InfoRow } from "../view/common/InfoRow"
import { DrumIllustration } from "../view/DrumIllustration"
import { logger } from "../utils/Logger"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { ReminderSyncOrder, syncPickupReminderForOrder } from "../utils/ReminderService"
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

@Builder
export function OrderDetailBuilder() {
  OrderDetail()
}

/**
 * 订单状态区的展示元数据。
 * ArkTS @Builder 内仅允许 UI 语法（不允许 let/switch 局部语句），
 * 状态 → 文案/颜色/描述 的映射收敛为类 + 私有方法计算。
 * 文案对齐安卓 app/.../strings.xml（D-OD8）。
 */
class StatusMeta {
  text: string = ""
  color: number = resColor('PRIMARY')
  description: string = ""
}

/** 进度时间线节点（5 节点固定流程，对齐安卓 ProgressTimeline） */
class TimelineNode {
  status: string = ""
  label: string = ""

  constructor(status: string, label: string) {
    this.status = status
    this.label = label
  }
}

@Component
struct OrderDetail {
  @State orderInfo: OrderInfo | null = null
  @State orderId: number | null = null
  // 加载态：详情请求期间全屏 Loading（对齐安卓 LoadingState）
  @State isLoading: boolean = true
  // 失败态：请求失败置真 → EmptyState + 重试（§2-S1，对齐 §8 寄件页范式）
  @State loadFailed: boolean = false
  // 底部按钮按压态（scale 0.97 + 100ms，对齐安卓 pressable；V1 组件用 @State）
  @State contactPressed: boolean = false
  @State pickupPressed: boolean = false
  // 取件码弹窗入参（open 前赋值，弹窗以当次值初始化）
  @State pickupCodeText: string = ""
  @State lockerNumberText: number = 0
  // 取件码弹窗（N2：DataLarge 36 tnum + 柜号副行 + 复制按钮，对齐安卓 PickupCodeDialog）
  private pickupCodeDialogController: CustomDialogController = new CustomDialogController({
    builder: PickupCodeDialog({
      pickupCode: this.pickupCodeText,
      lockerNumber: this.lockerNumberText
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })
  // 时间线节点（按订单流程顺序，保持 5 个状态不变）
  private nodes: Array<TimelineNode> = [
    new TimelineNode(OrderStatusEnum.PENDING_PAYMENT, resString('pending_payment')),
    new TimelineNode(OrderStatusEnum.PENDING_SHIPMENT, resString('pending_shipment')),
    new TimelineNode(OrderStatusEnum.WASHING, resString('step_washing')),
    new TimelineNode(OrderStatusEnum.READY_FOR_PICKUP, resString('step_pickup')),
    new TimelineNode(OrderStatusEnum.COMPLETED, resString('completed'))
  ]

  async aboutToAppear() {
    // 安全取参（评审 #31）：参数缺失/非法时返回 null，不再 as 强转崩溃
    this.orderId = getNumberParam(RouteNames.ORDER_DETAIL)
    logger.debug('orderDetail', `订单详情页挂载 orderId=${this.orderId}`)
    await this.loadOrderDetail()
  }

  /**
   * 拉取订单详情（首载 / 重试共用）。
   * 失败置 loadFailed → 渲染 EmptyState + 重试（对齐 §8 寄件页范式；
   * 安卓失败驻留骨架无重试，此处按设计系统 EmptyState 母题补重试，见报告裁决）。
   */
  async loadOrderDetail(): Promise<void> {
    if (this.orderId === null) {
      // 参数缺失（异常路由进入）：提示后停留空态，不发起无效请求（鸿蒙更优，保留）
      logger.warn('orderDetail', '订单详情缺少路由参数 orderId，停留空态不发起请求')
      showToastSafety(resString('order_detail_1'))
      this.isLoading = false
      return
    }
    this.isLoading = true
    this.loadFailed = false
    try {
      let res = await getOrderInfo(this.orderId)
      if (res.code === HttpStatusCode.Ok) {
        logger.debug('orderDetail', `订单详情加载成功 orderId=${this.orderId} status=${res.data?.status ?? ""}`)
        this.orderInfo = res.data
        this.loadFailed = false
        // 取件提醒同步（fire-and-forget，调研候选 2 解耦原则）：详情是待取件单最准确的
        // 发现点（带柜号上下文，文案可落到具体柜位）；同步内部全量容错不影响详情主流程
        if (res.data !== null) {
          const syncInput: ReminderSyncOrder = {
            orderId: res.data.orderId,
            status: res.data.status,
            lockerNumber: res.data.lockersVo?.lockerNumber ?? null
          }
          syncPickupReminderForOrder(syncInput).catch((err: Object) => {
            // 双保险（sync 内部已兜底）：意外 throw 也不得影响详情
            logger.debug('orderDetail', `取件提醒同步意外失败（已忽略）${err instanceof Error ? err.message : ''}`)
          })
        }
      } else {
        logger.warn('orderDetail', `订单详情加载失败 orderId=${this.orderId} ` +
          `code=${res.code} message=${res.message ?? ""}`)
        this.loadFailed = true
        showToastSafety(res.message, resString('order_detail_2'))
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并进失败态，避免 isLoading 卡死永久骨架
      this.loadFailed = true
      showToastSafety(resString('order_detail_3'))
    } finally {
      this.isLoading = false
    }
  }

  /**
   * 取件码解析 — 统一取冒号分段第 3 段（D4，对齐安卓 split(":").getOrNull(2)），
   * 防后端分段格式加段后两端解析语义漂移。
   */
  private parsePickupCode(): string {
    return this.orderInfo?.pickupCode?.split(":")[2] ?? ""
  }

  /**
   * 是否展示「查看取件码」主按钮（对齐安卓 OrderDetailPage.kt:241-242：
   * showPickupCode = 待寄件/待取件；其余状态仅保留联系客服）。
   */
  private showPickupCode(): boolean {
    const status = this.orderInfo?.status
    return status === OrderStatusEnum.PENDING_SHIPMENT || status === OrderStatusEnum.READY_FOR_PICKUP
  }

  // 订单状态 → 展示元数据映射（原写在 @Builder 内的 switch 不满足 ArkTS 语法）
  private statusMeta(): StatusMeta {
    const meta: StatusMeta = new StatusMeta()
    switch (this.orderInfo?.status) {
      case OrderStatusEnum.PENDING_PAYMENT:
        meta.text = resString('pending_payment')
        meta.color = resColor('WARNING')
        meta.description = resString('please_pay_soon')
        break
      case OrderStatusEnum.PENDING_SHIPMENT:
        meta.text = resString('pending_shipment')
        meta.color = resColor('PRIMARY_DARK')
        meta.description = resString('please_ship_soon')
        break
      // 中间态（对齐安卓 OrderDetailPage.kt:539-550）：已收取/已烘干/配送中归入清洗→取件阶段，
      // 安卓均有提示语；鸿蒙原落 default 显示「-」
      case OrderStatusEnum.RECEIVED:
        meta.text = resString('order_status_received')
        meta.color = resColor('PRIMARY_DARK')
        meta.description = resString('received_hint')
        break
      case OrderStatusEnum.WASHING:
        meta.text = resString('step_washing')
        meta.color = resColor('PRIMARY_DARK')
        meta.description = resString('washing_in_progress_hint')
        break
      case OrderStatusEnum.DRIED:
        meta.text = resString('order_status_dried')
        meta.color = resColor('PRIMARY_DARK')
        meta.description = resString('dried_hint')
        break
      case OrderStatusEnum.IN_DELIVERY:
        meta.text = resString('order_status_in_delivery')
        meta.color = resColor('PRIMARY_DARK')
        meta.description = resString('in_delivery_hint')
        break
      case OrderStatusEnum.READY_FOR_PICKUP:
        meta.text = resString('step_pickup')
        meta.color = resColor('PRIMARY_DARK')
        meta.description = resString('clothes_in_locker')
        break
      case OrderStatusEnum.COMPLETED:
        meta.text = resString('completed')
        meta.color = resColor('PRIMARY_DARK')
        // 安卓此处为 completion_time「完成时间」（疑似占位文案），保留语义化文案
        meta.description = resString('order_detail_4')
        break
      case OrderStatusEnum.CANCELED:
        meta.text = resString('cancelled')
        meta.color = resColor('DANGER')
        meta.description = resString('cancelled')
        break
      case OrderStatusEnum.REFUNDED:
        meta.text = resString('order_status_refunded')
        meta.color = resColor('DANGER')
        meta.description = resString('refunded_hint')
        break
      default:
        meta.text = getOrderStatusDescription(this.orderInfo?.status)
        meta.color = resColor('TEXT_SECONDARY')
        meta.description = "-"
        break
    }
    return meta
  }

  /**
   * 当前状态在时间线中的节点下标（对齐安卓 OrderDetailPage ProgressTimeline 的 currentIndex）。
   * 中间态必须映射到所在阶段节点：已收取/清洗中→2，已烘干/配送中/待取件→3；
   * 原实现用 findIndex 精确匹配 5 节点，遇到 RECEIVED/DRIED/IN_DELIVERY 会落到兜底 0，
   * 导致进度线一直卡在「待支付」（问题 9「时间进度线位置不对」）。
   */
  private currentIndex(): number {
    switch (this.orderInfo?.status) {
      case OrderStatusEnum.PENDING_PAYMENT:
        return 0
      case OrderStatusEnum.PENDING_SHIPMENT:
        return 1
      case OrderStatusEnum.RECEIVED:
      case OrderStatusEnum.WASHING:
        return 2
      case OrderStatusEnum.DRIED:
      case OrderStatusEnum.IN_DELIVERY:
      case OrderStatusEnum.READY_FOR_PICKUP:
        return 3
      case OrderStatusEnum.COMPLETED:
        return 4
      default:
        return 0
    }
  }

  /** 时间线首节点副行：真实下单时间 HH:mm（格式异常不显示，对齐安卓 D-OD2） */
  private firstNodeTime(): string {
    const created = this.orderInfo?.createdAt ?? ""
    if (created.length >= 16) {
      const time = created.substring(11, 16)
      if (time.length === 5 && time.includes(":")) {
        return time
      }
    }
    return ""
  }

  /** 时间线 rail 段数（节点数 - 1） */
  private railKeys(): number[] {
    const keys: number[] = []
    for (let i = 0; i < this.nodes.length - 1; i++) {
      keys.push(i)
    }
    return keys
  }

  @Builder
  StatusHeader() {
    // 状态区（对齐安卓 OrderStatusHeader）：无卡片，居中 120 滚筒插画 + 状态大字 + 副行提示
    Column() {
      DrumIllustration({ sizeValue: 120 })

      Text(this.statusMeta().text)
        .fontSize(DesignFontSize.DATA_LARGE)
        .fontWeight(DesignFontWeight.BOLD)
        .fontColor(this.statusMeta().color)
        .margin({ top: 12 })

      if (this.statusMeta().description !== "") {
        Text(this.statusMeta().description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(resColor('TEXT_SECONDARY'))
          .margin({ top: 6 })
      }
    }
    .width("100%")
    .alignItems(HorizontalAlign.Center)
    .padding({ bottom: 16 })
  }

  /**
   * 进度时间线卡（对齐安卓 ProgressTimeline 水平 stepper）：
   * rail 2dp 节点连线（完成段 primary / 未完成 hairline）；当前节点 water 蓝光环 + 12 实心，
   * 已完成 primary 实心，未完成 outline 实心；节点下方 caption 标签，首节点下时间副行。
   */
  @Builder
  ProgressTimelineCard() {
    Column() {
      Text($r('app.string.progress_timeline'))
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(resColor('TEXT_PRIMARY'))
        .letterSpacing(0.3)
        .margin({ bottom: 6 })

      Stack({ alignContent: Alignment.TopStart }) {
        // rail 层（先绘，节点覆盖其上）：相邻节点圆心连线，2dp
        Row() {
          ForEach(this.railKeys(), (index: number) => {
            Column()
              .layoutWeight(1)
              // 每段左右各 6dp 内缩（对齐安卓 rail Box 的 padding(horizontal = 6.dp)），
              // 避免相邻段首尾相接成一条通线
              .padding({ left: 6, right: 6 })
              .height(2)
              .backgroundColor(index < this.currentIndex() ? resColor('PRIMARY') : resColor('HAIRLINE'))
          }, (index: number) => index.toString())
        }
        .width("100%")
        // 5 等分节点列：圆心位于 10%/30%/50%/70%/90%，左右各让 10% 后 4 段各跨 20%
        .padding({ left: "10%", right: "10%" })
        .offset({ y: 9 })

        // 节点 + 标签层
        Row() {
          ForEach(this.nodes, (node: TimelineNode, index: number) => {
            Column() {
              Stack() {
                if (index === this.currentIndex()) {
                  // now 节点：20dp water 光环（12%）+ 内层 12dp water 实心
                  Column()
                    .width(20)
                    .height(20)
                    .borderRadius(10)
                    .backgroundColor(resColor('WATER'))
                    .opacity(0.12)
                  Column()
                    .width(12)
                    .height(12)
                    .borderRadius(6)
                    .backgroundColor(resColor('WATER'))
                } else {
                  // done → primary 实心；未完成 → outline 实心
                  Column()
                    .width(12)
                    .height(12)
                    .borderRadius(6)
                    .backgroundColor(index < this.currentIndex() ? resColor('PRIMARY') : resColor('OUTLINE'))
                }
              }
              .width(20)
              .height(20)

              Text(node.label)
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(index === this.currentIndex() ? DesignFontWeight.SEMI_BOLD : DesignFontWeight.NORMAL)
                .fontColor(index === this.currentIndex() ? resColor('ON_PRIMARY_CONTAINER') :
                  (index < this.currentIndex() ? resColor('TEXT_PRIMARY') : resColor('TEXT_TERTIARY')))
                .textAlign(TextAlign.Center)
                .maxLines(1)
                .margin({ top: 6 })

              // 副行仅首节点（已接单）显示真实下单时间 HH:mm
              if (index === 0 && this.firstNodeTime() !== "") {
                Text(this.firstNodeTime())
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(resColor('TEXT_SECONDARY'))
                  .fontFeature(DesignFontFeature.TNUM)
                  .textAlign(TextAlign.Center)
                  .margin({ top: 2 })
              }
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Center)
          }, (node: TimelineNode) => node.status)
        }
        .width("100%")
      }
      .width("100%")
    }
    .width("100%")
    // 标题左对齐（对齐安卓 ProgressTimelineCard：ArkUI Column 默认 Center 会居中标题）
    .alignItems(HorizontalAlign.Start)
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .backgroundColor(resColor('SURFACE'))
    // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
    .border({ width: 1, color: resColor('OUTLINE') })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: resColor('SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: 2
    })
  }

  /**
   * 订单信息卡（对齐安卓 InfoSection 单卡 6 行）：标题 IconBox 36/20 蓝 + 二级 InfoRow 行高 40（行间 0）。
   * 安卓 InfoSection 将订单编号/套餐/价格/实付款/学校/地址合并在一张「订单信息」卡内，
   * 寄存柜信息仅经取件码弹窗承载，故此处不再拆 4 卡。
   */
  @Builder
  OrderInfoCard() {
    Column() {
      Row() {
        IconBox({
          icon: $r('sys.symbol.list_square_bill'),
          sizeValue: 36,
          iconSize: 20,
          containerColor: resColor('ICON_CONTAINER_BLUE'),
          iconColor: resColor('ICON_FOREGROUND_BLUE')
        })

        Text($r('app.string.order_info'))
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(resColor('TEXT_PRIMARY'))
          .margin({ left: 12 })
      }
      .width("100%")
      .margin({ bottom: 4 })

      // 二级信息行：行高 40 自身承担节奏，行间不加距（对齐安卓 InfoRow 行间 0）
      Column() {
        InfoRow({
          label: resString('order_number'),
          value: `${this.orderInfo?.orderNo ?? ""}`,
          valueSize: DesignFontSize.CAPTION,
          valueWeight: DesignFontWeight.MEDIUM,
          valueTnum: true
        })
        InfoRow({ label: resString('package_type'), value: `${this.orderInfo?.laundryPackageVo?.itemName ?? ""}` })
        InfoRow({
          label: resString('price'),
          value: `¥${(this.orderInfo?.totalPrice ?? 0).toFixed(2)}`,
          valueTnum: true
        })
        InfoRow({
          label: resString('actual_payment'),
          value: `¥${(this.orderInfo?.payPrice ?? 0).toFixed(2)}`,
          valueColor: resColor('PRIMARY_DARK'),
          valueSize: DesignFontSize.AMOUNT_MEDIUM,
          // 对齐安卓 OrderDetailPage.kt:215：AmountMedium.copy(fontWeight = Bold)
          valueWeight: DesignFontWeight.BOLD,
          valueTnum: true
        })
        InfoRow({ label: resString('school_name'), value: `${this.orderInfo?.schoolsVo?.schoolName ?? ""}` })
        InfoRow({ label: resString('address'), value: `${this.orderInfo?.schoolsVo?.location ?? ""}` })
      }
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .backgroundColor(resColor('SURFACE'))
    .border({ width: 1, color: resColor('OUTLINE') })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: resColor('SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: 2
    })
  }

  @Builder
  BottomActionBar() {
    Column() {
      Divider()
        .strokeWidth(1)
        .color(resColor('OUTLINE'))

      Row() {
        // 联系客服（次按钮：透明底 + 1dp outline 描边 + primaryDark 字，D-OD12 先以 toast 兜底）
        Column() {
          Text($r('app.string.contact_service'))
            .fontSize(DesignFontSize.AMOUNT_MEDIUM)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(resColor('PRIMARY_DARK'))
        }
        .layoutWeight(1)
        .height(48)
        .justifyContent(FlexAlign.Center)
        .borderRadius(DesignDimens.RADIUS_FULL)
        .border({ width: 1, color: resColor('OUTLINE') })
        .scale({ x: this.contactPressed ? 0.97 : 1, y: this.contactPressed ? 0.97 : 1 })
        .animation({ duration: 100, curve: Curve.EaseOut })
        .onTouch((event: TouchEvent) => {
          if (event.type === TouchType.Down) {
            this.contactPressed = true
          } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
            this.contactPressed = false
          }
        })
        .onClick(() => {
          showToastSafety(resString('contact_service_unavailable'))
        })

        // 查看取件码（主按钮）：仅在待寄件/待取件态渲染（对齐安卓 P-901：取件码语义只存在于
        // 这两态，已取消/已完成/待支付/清洗中等隐藏该键，仅保留联系客服）
        if (this.showPickupCode()) {
          Column() {
            Text($r('app.string.view_pickup_code'))
              .fontSize(DesignFontSize.AMOUNT_MEDIUM)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(resColor('ON_PRIMARY'))
              .letterSpacing(1)
          }
          .layoutWeight(1)
          .height(48)
          .justifyContent(FlexAlign.Center)
          .borderRadius(DesignDimens.RADIUS_FULL)
          .backgroundColor(resColor('PRIMARY'))
          .margin({ left: 12 })
          .scale({ x: this.pickupPressed ? 0.97 : 1, y: this.pickupPressed ? 0.97 : 1 })
          .animation({ duration: 100, curve: Curve.EaseOut })
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Down) {
              this.pickupPressed = true
            } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
              this.pickupPressed = false
            }
          })
          .onClick(() => {
            const code = this.parsePickupCode()
            if (code !== "") {
              // 取件码值不入日志（敏感红线），仅记 orderId 与柜号
              logger.debug('orderDetail', `打开取件码弹窗 orderId=${this.orderId} ` +
                `柜号=${this.orderInfo?.lockersVo?.lockerNumber ?? ""}`)
              this.pickupCodeText = code
              this.lockerNumberText = this.orderInfo?.lockersVo?.lockerNumber ?? 0
              this.pickupCodeDialogController.open()
            } else {
              logger.debug('orderDetail', `取件码暂不可用 orderId=${this.orderId}`)
              showToastSafety(resString('no_pickup_code'))
            }
          })
        }
      }
      .width("100%")
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_MD,
        bottom: DesignDimens.SPACE_MD + safeBottomVp()
      })
    }
    .width("100%")
    .backgroundColor(resColor('SURFACE'))
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Stack({ alignContent: Alignment.TopStart }) {
        Column() {
          Scroll() {
            Column() {
              // 页头（对齐安卓 PageHeader：22/600 + 48 返回热区）
              Row() {
                Column() {
                  SymbolGlyph($r('sys.symbol.chevron_left'))
                    .fontSize(24)
                    .fontColor([resColor('TEXT_PRIMARY')])
                }
                .width(48)
                .height(48)
                .justifyContent(FlexAlign.Center)
                .alignItems(HorizontalAlign.Center)
                .onClick(() => {
                  pathStack.pop()
                })

                Text($r('app.string.order_detail'))
                  .fontSize(DesignFontSize.ROOT_TITLE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor(resColor('TEXT_PRIMARY'))
              }
              .width("100%")
              .padding({ left: 12, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
              .alignItems(VerticalAlign.Center)

              // 内容区域
              Column() {
                // 状态区（120 滚筒插画 + 状态大字 + 副行）
                this.StatusHeader()

                // 进度时间线（5 节点水平 stepper）
                Column() {
                  this.ProgressTimelineCard()
                }
                .width("100%")
                .margin({ top: DesignDimens.CARD_SPACING })

                // 订单信息（单卡 6 行）
                Column() {
                  this.OrderInfoCard()
                }
                .width("100%")
                .margin({ top: DesignDimens.SPACE_SM })
              }
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                bottom: DesignDimens.SPACE_SM
              })
            }
          }
          .layoutWeight(1)
          .width("100%")
          .scrollBar(BarState.Off)
          // 内容不足一屏顶对齐（Scroll 默认居中）
          .align(Alignment.Top)

          // 底部操作条（联系客服 + 查看取件码）— 与滚动内容同列排布，不遮挡内容
          this.BottomActionBar()
        }
        .width("100%")
        .height("100%")
        // 沉浸式：内容避让状态栏（背景延伸）
        .padding({ top: safeTopVp() })
        .backgroundColor(resColor('BACKGROUND'))
        // 骨架↔内容 Crossfade 近似（对齐安卓 Crossfade 300ms）：加载态整列淡隐 + 禁命中，
        // 骨架层以 TransitionEffect.OPACITY 淡入淡出（§2-I2）
        .opacity(this.isLoading ? 0 : 1)
        .animation({ duration: 300, curve: Curve.EaseInOut })
        .enabled(!this.isLoading)

        // 加载态
        if (this.isLoading) {
          // 加载态（对齐安卓 LoadingState：骨架屏，§3.6 禁转圈）
          LoadingState()
            .width("100%")
            .height("100%")
            .backgroundColor(resColor('BACKGROUND'))
            .zIndex(2)
            .transition(TransitionEffect.OPACITY)
        }

        // 失败态（§2-S1）：EmptyState 母题 + 「重试」行动按钮，不留空白页
        if (!this.isLoading && this.loadFailed) {
          Column() {
            EmptyState({
              message: resString('order_detail_2'),
              action: () => {
                AppButton({
                  small: true,
                  text: resString('retry'),
                  variant: AppButtonVariant.SECONDARY,
                  click: () => {
                    this.loadOrderDetail()
                  }
                })
                  .margin({ top: DesignDimens.SPACE_MD })
              }
            })
          }
          .width("100%")
          .height("100%")
          .backgroundColor(resColor('BACKGROUND'))
          .justifyContent(FlexAlign.Center)
          .zIndex(3)
          .transition(TransitionEffect.OPACITY)
        }
      }
      }
    }
    .hideTitleBar(true)
  }
}

// 信息行统一用公共二级组件 InfoRow（view/common/InfoRow.ets，40vp / 12sp）

/**
 * 取件码弹窗（N2，对齐安卓 OrderDetailPage.kt PickupCodeDialog）：
 * 标题 + 柜号副行 + DataLarge 36 tnum 取件码（每屏唯一锚点）+ 底部「取消 / 复制取件码」分区动作。
 * 骨架与 AppInfoDialog 同款：radiusLg + floatLayer + dialogEnterMotion 入场，0.5dp 分隔线切动作区。
 * 复制复用取件页同款剪贴板 + 选中触感。
 */
@CustomDialog
export struct PickupCodeDialog {
  // 由父组件注入控制器（与 AppInfoDialog 同款：空 builder 的自建控制器不合法）
  controller: CustomDialogController
  @Prop pickupCode: string = ''
  @Prop lockerNumber: number = 0
  @State entered: boolean = false
  onDismiss: () => void = () => {
  }

  /** 弹窗统一入场（对齐安卓 dialogEnterMotion）：scale 0.92→1 + fade 0→1 弹簧一次性进场 */
  private playEnterMotion(): void {
    setTimeout(() => {
      animateTo(MotionTiming.DIALOG_ENTER, () => {
        this.entered = true
      })
    }, 16)
  }

  /** 复制取件码（对齐安卓 clipboard + SELECTION 触感 + copy_success toast） */
  private copyCode(): void {
    if (this.pickupCode === '') {
      return
    }
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 忽略不支持的设备
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, this.pickupCode)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(resString('copy_success'))
    }).catch((err: BusinessError) => {
      logger.error('orderDetail', '取件码复制到剪贴板失败', `${err.code} ${err.message}`)
      showToastSafety(resString('order_detail_5'))
    })
    this.onDismiss()
    this.controller.close()
  }

  build() {
    // 外层留白容器：customStyle 弹窗铺满屏幕宽度，卡片背景画在内层并留左右边距（问题16）
    Column() {
      Column() {
        // 内容区
        Column() {
          Text($r('app.string.pickup_code'))
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(resColor('TEXT_PRIMARY'))

          Text(`寄存柜: ${this.lockerNumber}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(resColor('TEXT_SECONDARY'))
            .fontFeature(DesignFontFeature.TNUM)
            .margin({ top: 4 })

          if (this.pickupCode !== '') {
            // 取件码大字 — DataLarge（36/44/700 + tnum，规范 §2.2 取件码槽位）
            Text(this.pickupCode)
              .fontSize(DesignFontSize.DATA_LARGE)
              .fontWeight(DesignFontWeight.BOLD)
              .fontColor(resColor('PRIMARY_DARK'))
              .fontFeature(DesignFontFeature.TNUM)
              .maxLines(1)
              .margin({ top: 12 })
          } else {
            Text($r('app.string.no_pickup_code'))
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor(resColor('TEXT_SECONDARY'))
              .margin({ top: 12 })
          }
        }
        .width("100%")
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: 20,
          bottom: 20
        })
        .alignItems(HorizontalAlign.Center)

        // 分隔线（0.5dp，切动作区）
        Divider()
          .color(resColor('DIVIDER'))
          .strokeWidth(0.5)

        // 底部分区动作（对齐安卓 DialogAction：48 高 / bodyLarge 16/500）
        Row() {
          Text($r('app.string.cancel'))
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(resColor('TEXT_SECONDARY'))
            .layoutWeight(1)
            .height(48)
            .textAlign(TextAlign.Center)
            .onClick(() => {
              this.onDismiss()
              this.controller.close()
            })

          Divider()
            .vertical(true)
            .color(resColor('DIVIDER'))
            .strokeWidth(0.5)
            .height(48)

          Text($r('app.string.copy_pickup_code'))
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(resColor('PRIMARY'))
            .layoutWeight(1)
            .height(48)
            .textAlign(TextAlign.Center)
            .onClick(() => {
              this.copyCode()
            })
        }
        .width("100%")
      }
      .width("100%")
      .backgroundColor(resColor('SURFACE'))
      .borderRadius(DesignDimens.CARD_RADIUS)
      // 浮层档阴影（§2.4 CSS 0 10px 24px rgba(20,40,30,.10)）
      .shadow({
        radius: DesignElevation.FLOAT_SHADOW_RADIUS,
        color: resColor('SHADOW_COLOR_FLOAT'),
        offsetX: 0,
        offsetY: DesignElevation.FLOAT_SHADOW_OFFSET
      })
      .scale({ x: this.entered ? 1 : 0.92, y: this.entered ? 1 : 0.92 })
      .opacity(this.entered ? 1 : 0)
      .onAppear(() => {
        this.playEnterMotion()
      })
    }
    .width("100%")
    .justifyContent(FlexAlign.Center)
    .padding({ left: DesignDimens.SPACE_XL, right: DesignDimens.SPACE_XL })
  }
}
