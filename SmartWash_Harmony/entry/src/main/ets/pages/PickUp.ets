import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getOrderList } from "../network/api/OrderApi"
import { OrderStatusEnum } from "../constant/OrderStatus"
import { PickupDeliveryParam } from "./PickupDelivery"
import { LoadingFooter } from "../view/LoadingFooter"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { LazyListDataSource, PagingController } from "../utils/PagingUtil"
import { BusinessError, pasteboard } from "@kit.BasicServicesKit"
import vibrator from "@ohos.vibrator"
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight, MotionScale, MotionTiming } from "../constant/DesignSystem"
import { ReduceMotion } from "../utils/ReduceMotion"
import { AppButtonVariant, EmptyState, IconBox, LoadingState, PageHeader } from "../view/common/AppComponents"
import { logger } from "../utils/Logger"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

const PAGE_SIZE: number = 10

@Builder
export function PickUpBuilder() {
  PickUp()
}

@Component
struct PickUp {
  @State orderList: Array<OrderInfo> = []
  @State isRefreshing: boolean = false
  @State isLoadingMore: boolean = false
  // 首载骨架门控（recap §8-F2/S1）：仅首屏且列表为空时展示骨架，避免首帧闪空态
  @State isFirstLoading: boolean = true
  // 首载失败态（对齐钱包流水页 isFirstError 范式，扫描 #2 同口径）：page 1 失败且无内容时
  // 置位，错误态与空态严格分离——原实现 fetchPickupPage 只有 try/finally 无 catch，第 1 页
  // 直调路径（aboutToAppear/refreshOrder）绕过分页器兜底，网络异常 = 未处理 Promise 拒绝 +
  // 静默落入「暂无可取订单」空态，无 toast、无重试
  @State isFirstError: boolean = false
  // 底部操作栏按压态（recap §8-I1，对齐安卓 pressable scale 0.97 + 100ms）
  @State contactPressed: boolean = false
  @State copyPressed: boolean = false
  // 卡片按压态：按 orderId 标记，动作按钮按下时行不缩放（对齐安卓 pressable）
  @State pressedOrderId: number | null = null
  @State actionPressed: boolean = false
  // LazyForEach 数据源（读取 orderList，实例稳定不可重建，避免整表重建）
  private dataSource: LazyListDataSource<OrderInfo> =
    new LazyListDataSource<OrderInfo>(() => this.orderList)
  // 轻量分页封装：触底加载/上锁防重/封底判定统一收敛（差异分析 01 §3.3）
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchPickupPage(page)
    },
    (loading: boolean) => {
      this.isLoadingMore = loading
    }
  )

  aboutToAppear(): void {
    logger.debug('pickup', '取件页挂载，拉取可取订单')
    this.fetchPickupPage(1)
  }

  /**
   * 首载重试：回骨架拉首页，结束（成功/失败）后复位加载态（page 1 失败路径不 throw）。
   */
  private async retryFirstLoad(): Promise<void> {
    this.isFirstLoading = true
    try {
      await this.fetchPickupPage(1)
    } finally {
      this.isFirstLoading = false
    }
  }

  /**
   * 按页拉取可取件订单。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchPickupPage(page: number): Promise<boolean> {
    logger.debug('pickup', `拉取可取订单 page=${page}`)
    try {
      let res = await getOrderList({
        status: OrderStatusEnum.READY_FOR_PICKUP,
        page: page, size: PAGE_SIZE
      })
      if (res.code === HttpStatusCode.Ok) {
        const records: Array<OrderInfo> = res.data ?? []
        if (records.length === 0) {
          // 第 1 页空结果：整体清空（stale-while-revalidate 语义不变）；成功空 = 真空态，复位失败态
          if (page === 1) {
            this.orderList = []
            this.dataSource.notifyReload()
            this.isFirstError = false
          } else if (this.orderList.length > 0) {
            showToastSafety(resString('pick_up_1'))
          }
          return false
        }
        if (page === 1) {
          // 第 1 页整体替换（请求期间保留旧列表，返回后整体换）；成功即复位失败态
          this.orderList = records
          this.dataSource.notifyReload()
          this.isFirstError = false
        } else {
          // 追加按新索引逐项通知，避免整表重建
          const startIndex: number = this.orderList.length
          this.orderList.push(...records)
          this.dataSource.notifyAppend(startIndex, records.length)
        }
        return true
      }
      logger.warn('pickup', `可取订单加载失败 page=${page} ` +
        `code=${res.code} message=${res.message ?? ""}`)
      showToastSafety(res.message, resString('pick_up_2'))
      // 首载业务失败且无内容：置错误态（不伪装「暂无可取订单」，给重试入口）
      if (page === 1 && this.orderList.length === 0) {
        this.isFirstError = true
      }
      return false
    } catch (err) {
      // 第 1 页直调路径（aboutToAppear/refreshOrder）绕过分页器兜底，必须自行 catch：
      // 原实现 try/finally 无 catch，网络异常上抛为未处理 Promise 拒绝且静默落空态
      logger.warn('pickup', `可取订单请求异常 page=${page}`)
      showToastSafety(resString('order_list_3'))
      if (page === 1 && this.orderList.length === 0) {
        this.isFirstError = true
      }
      return false
    } finally {
      this.isFirstLoading = false
    }
  }

  /** 取件码解析 — 统一取冒号分段第 3 段（D4，对齐安卓 getOrNull(2)） */
  private parsePickupCode(order: OrderInfo): string {
    return order.pickupCode?.split(":")[2] ?? ""
  }

  /**
   * 复制取件码到剪贴板（recap §8-F3/F4）。
   * 对齐安卓 clipboard + SELECTION 触感 + copy_success toast；无码时给原因提示，不再静默返回。
   */
  private copyPickupCode(code: string): void {
    if (code === "") {
      // 无码分支只记事实，不记码值（敏感红线）
      logger.debug('pickup', '复制取件码跳过：当前订单无取件码')
      showToastSafety(resString('no_pickup_code'))
      return
    }
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, code)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(resString('copy_success'))
    }).catch((err: BusinessError) => {
      logger.error('pickup', '取件码复制到剪贴板失败', `${err.code} ${err.message}`)
      showToastSafety(resString('pick_up_3'))
    })
  }

  /** 底部主行动：复制列表首单取件码（对齐安卓 orderList.peek(0)） */
  private copyFirstPickupCode(): void {
    const code = this.orderList.length > 0 ? this.parsePickupCode(this.orderList[0]) : ""
    this.copyPickupCode(code)
  }

  /**
   * 刷新列表（下拉刷新 / 返回页面）。
   * 下拉刷新契约（对齐工作台 loadEntries(force) 样板）：isRefreshing 全程翻转 = 触发置 true、
   * 请求完成（成功或失败）才复位——原实现第一步即复位，指示器随触发瞬间收起，无进度表达。
   * @param force 下拉触发时 true（指示器亮起）；onShown 重拉为 false（不亮指示器）
   */
  async refreshOrder(force: boolean = false) {
    this.isRefreshing = force
    this.paging.reset()
    try {
      await this.fetchPickupPage(1)
    } finally {
      this.isRefreshing = false
    }
  }

  @Builder
  PickupOrderCard(order: OrderInfo) {
    Column() {
      // 订单号 + 寄存柜号 + 价格（recap §8-V2：bodySmall 14 + tnum + 单行省略）
      Row() {
        Column() {
          Text(`订单号: ${order.orderNo ?? ""}`)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Text(`寄存柜: ${order.lockersVo?.lockerNumber ?? ""}`)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .margin({ top: 4 })
        }.alignItems(HorizontalAlign.Start)

        Blank()
        // 金额两位小数（N1）+ tnum（G5）+ primaryDark（绿字红线）
        Text(`¥${(order.totalPrice ?? 0).toFixed(2)}`)
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontFeature(DesignFontFeature.TNUM)
          .fontColor($r('app.color.PRIMARY_DARK'))
          .maxLines(1)
      }.width("100%")
      .alignItems(VerticalAlign.Center)

      // 分隔线（卡内 0.5dp 发丝线，上下 16）
      Row()
        .width("100%")
        .height(0.5)
        .backgroundColor($r('app.color.DIVIDER'))
        .margin({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })

      // 取件码（recap §8-V3：标签 12sp + DataLarge 36 tnum 大字，对齐安卓）
      Row() {
        Text($r('app.string.pickup_code'))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
        Blank()
        Text(this.parsePickupCode(order))
          .fontSize(DesignFontSize.DATA_LARGE)
          .fontColor($r('app.color.PRIMARY_DARK'))
          // DATA_LARGE（36）属 font.display 档（DESIGN-SPEC §2：取件码 700 + tnum），字重保持 BOLD；
          // 由裸 FontWeight 枚举收敛为设计令牌，与全站一致
          .fontWeight(DesignFontWeight.BOLD)
          .fontFeature(DesignFontFeature.TNUM)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }.width("100%")
      .alignItems(VerticalAlign.Center)

      // 复制取件码（N3：36dp 高 / r12 / surfaceVariant 胶囊 + labelSmall 12/500；右对齐，对齐安卓）
      if (this.parsePickupCode(order) !== "") {
        Row() {
          Row() {
            Text($r('app.string.copy_pickup_code'))
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD })
          }
          .height(36)
          .justifyContent(FlexAlign.Center)
          .borderRadius(DesignDimens.RADIUS_MD)
          .backgroundColor($r('app.color.SURFACE_VARIANT'))
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Down) {
              this.actionPressed = true
            } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
              this.actionPressed = false
            }
          })
          .onClick(() => {
            this.copyPickupCode(this.parsePickupCode(order))
          })
        }
        .width("100%")
        .justifyContent(FlexAlign.End)
        .margin({ top: 14 })
      }
    }
    .width("100%")
    .alignItems(HorizontalAlign.Center)
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: 2
    })
    // 卡片按压反馈（recap §8-I1/G3：scale 0.97 + 100ms；动作按钮按下时行不缩放）
    .scale({
      x: this.pressedOrderId === order.orderId && !this.actionPressed ? MotionScale.Press : 1,
      y: this.pressedOrderId === order.orderId && !this.actionPressed ? MotionScale.Press : 1
    })
    .animation(ReduceMotion.spec(MotionTiming.PRESS))
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedOrderId = order.orderId ?? null
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedOrderId = null
      }
    })
    .onClick(() => {
      // 显式类型入参（差异分析 02 §8.3）：不再由 status 推断取件/寄件模式
      const param: PickupDeliveryParam = new PickupDeliveryParam()
      param.order = order
      param.isPickup = true
      pathStack.pushPathByName(RouteNames.PICKUP_DELIVERY, param)
    })
  }

  /**
   * 底部操作栏（recap §8-F1）— 对齐安卓 PickupActionBar：
   * 次按钮（联系客服）透明底 + 1dp outline + primaryDark 字；主按钮（复制首单取件码）纯色 primary；
   * 双 48dp 胶囊、间距 12、底部留白 16，固定页面底部。
   */
  @Builder
  PickupActionBar() {
    Row() {
      // 次按钮 — 透明底 + 1dp outline 描边（安卓板实测无填充）
      Column() {
        Text($r('app.string.contact_service'))
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.PRIMARY_DARK'))
      }
      .layoutWeight(1)
      .height(48)
      .justifyContent(FlexAlign.Center)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .border({ width: 1, color: $r('app.color.OUTLINE') })
      .scale({ x: this.contactPressed ? MotionScale.Press : 1, y: this.contactPressed ? MotionScale.Press : 1 })
      .animation(ReduceMotion.spec(MotionTiming.PRESS))
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.contactPressed = true
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.contactPressed = false
        }
      })
      .onClick(() => {
        // 客服功能未立项，先以 Toast 兜底（与安卓 contact_service_unavailable 约定一致）
        showToastSafety(resString('contact_service_unavailable'))
      })

      // 主按钮 — 纯色 primary 胶囊 + onPrimary 字
      Column() {
        Text($r('app.string.copy_pickup_code'))
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.ON_PRIMARY'))
      }
      .layoutWeight(1)
      .height(48)
      .justifyContent(FlexAlign.Center)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .backgroundColor($r('app.color.PRIMARY'))
      .margin({ left: DesignDimens.SPACE_SM })
      .scale({ x: this.copyPressed ? MotionScale.Press : 1, y: this.copyPressed ? MotionScale.Press : 1 })
      .animation(ReduceMotion.spec(MotionTiming.PRESS))
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.copyPressed = true
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.copyPressed = false
        }
      })
      .onClick(() => {
        this.copyFirstPickupCode()
      })
    }
    .width("100%")
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: DesignDimens.SPACE_MD,
      bottom: DesignDimens.SPACE_MD + safeBottomVp()
    })
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
      Column() {
        // 页头（recap G4：22/600 + 48 返回热区，复用公共 PageHeader）
        PageHeader({ title: resString('service_pickup') })

        // 内容区四态：首载骨架 / 内容 / 首载失败 / 空态（recap §8-F2 + 扫描 #2 错误态补齐）
        if (this.isFirstLoading && this.orderList.length === 0) {
          // 首载骨架（对齐安卓 LoadingState；有旧数据时走 stale-while-revalidate 不闪骨架）
          LoadingState()
            .width("100%")
            .layoutWeight(1)
        } else if (this.orderList.length > 0) {
          Column() {
            // 学校信息行（recap §8-V1：IconBox 32/r12/20 蓝容器 + titleSmall 14）
            Row() {
              IconBox({
                icon: $r('sys.symbol.company'),
                sizeValue: 32,
                iconSize: 20,
                containerColor: $r('app.color.ICON_CONTAINER_BLUE'),
                iconColor: $r('app.color.ICON_FOREGROUND_BLUE')
              })
              Text(`${this.orderList[0]?.schoolsVo?.schoolName ?? ""}`)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor($r('app.color.TEXT_PRIMARY'))
                .margin({ left: DesignDimens.SPACE_XS })
            }
            .justifyContent(FlexAlign.Start)
            .width("100%")
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_XS,
              bottom: DesignDimens.SPACE_XS
            })

            Blank().height(DesignDimens.SPACE_SM)

            Refresh({ refreshing: $$this.isRefreshing }) {
              List({ space: DesignDimens.CARD_SPACING }) {
                LazyForEach(this.dataSource, (order: OrderInfo) => {
                  ListItem() {
                    this.PickupOrderCard(order)
                  }
                }, (order: OrderInfo) => order.orderId?.toString())
                ListItem() {
                  LoadingFooter({ isLoading: this.isLoadingMore })
                }
              }
              .width("100%")
              .height("100%")
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
              .onScrollIndex((start: number, end: number) => {
                // listLength 为 ListItem 真实数量（订单卡 + LoadingFooter）
                this.paging.onScrollIndex(end, this.orderList.length + 1)
              }).onScrollFrameBegin((offset: number, state: ScrollState) => {
                this.paging.onScrollFrameBegin(offset)
                return { offsetRemain: offset }
              })
            }
            .layoutWeight(1)
            .onRefreshing(() => {
              this.refreshOrder(true)
            })
          }
          .width("100%")
          .layoutWeight(1)

          // 底部操作栏（recap §8-F1：固定底部）
          this.PickupActionBar()
        } else if (this.isFirstError) {
          // 首载失败态（对齐钱包流水页错误态：EmptyState + 重试，错误态优先于空态判定，
          // 不得伪装「暂无可取订单」；重试回骨架重拉首页）
          Column() {
            EmptyState({
              message: resString('pick_up_2'),
              actionText: resString('retry'),
              actionVariant: AppButtonVariant.SECONDARY,
              onAction: () => {
                this.retryFirstLoad()
              }
            })
          }
          .width("100%")
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          // 空态（recap §8-V5：安卓 EmptyState DrumMark 母题；文案对齐 no_pickup_orders）
          Column() {
            EmptyState({ message: resString('no_pickup_orders') })
          }
          .width("100%")
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        }
      }
      .width("100%")
      .height("100%")
      .padding({ top: safeTopVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 每次展示先重置分页再拉取，避免从寄件页返回后 page>1 继续追加重复数据（评审 #14）
      this.refreshOrder()
    })
  }
}
