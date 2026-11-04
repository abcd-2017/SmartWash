import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getOrderList } from "../network/api/OrderApi"
import { OrderStatusEnum } from "../constant/OrderStatus"
import { PickupDeliveryParam } from "./PickupDelivery"
import { LoadingFooter } from "../view/LoadingFooter"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { PagingController } from "../utils/PagingUtil"
import { BusinessError, pasteboard } from "@kit.BasicServicesKit"
import vibrator from "@ohos.vibrator"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { EmptyState, IconBox, LoadingState, PageHeader } from "../view/common/AppComponents"
import { logger } from "../utils/Logger"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"

// 分页每页条数
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
  // 底部操作栏按压态（recap §8-I1，对齐安卓 pressable scale 0.97 + 100ms）
  @State contactPressed: boolean = false
  @State copyPressed: boolean = false
  // 卡片按压态：按 orderId 标记，动作按钮按下时行不缩放（对齐安卓 pressable）
  @State pressedOrderId: number | null = null
  @State actionPressed: boolean = false
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
        // 第 1 页整体替换（stale-while-revalidate：请求期间保留旧列表，返回后整体换）
        if (page === 1) {
          this.orderList = []
        }
        if (res.data === null || res.data.length === 0) {
          if (page > 1 && this.orderList.length > 0) {
            showToastSafety("没有更多数据了")
          }
          return false
        }
        this.orderList.push(...res.data)
        return true
      }
      logger.warn('pickup', `可取订单加载失败 page=${page} ` +
        `code=${res.code} message=${res.message ?? ""}`)
      showToastSafety(res.message, "取件订单加载失败")
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
      showToastSafety("暂无取件码，请稍后再试")
      return
    }
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 忽略不支持的设备
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, code)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety("取件码已复制")
    }).catch((err: BusinessError) => {
      logger.error('pickup', '取件码复制到剪贴板失败', `${err.code} ${err.message}`)
      showToastSafety("复制失败")
    })
  }

  /** 底部主行动：复制列表首单取件码（对齐安卓 orderList.peek(0)） */
  private copyFirstPickupCode(): void {
    const code = this.orderList.length > 0 ? this.parsePickupCode(this.orderList[0]) : ""
    this.copyPickupCode(code)
  }

  refreshOrder() {
    this.isRefreshing = false
    this.paging.reset()
    this.fetchPickupPage(1)
  }

  @Builder
  PickupOrderCard(order: OrderInfo) {
    Column() {
      // 订单号 + 寄存柜号 + 价格（recap §8-V2：bodySmall 14 + tnum + 单行省略）
      Row() {
        Column() {
          Text(`订单号: ${order.orderNo ?? ""}`)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Text(`寄存柜: ${order.lockersVo?.lockerNumber ?? ""}`)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor(DesignColor.TEXT_SECONDARY)
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
          .fontColor(DesignColor.PRIMARY_DARK)
          .maxLines(1)
      }.width("100%")
      .alignItems(VerticalAlign.Center)

      // 分隔线（卡内 0.5dp 发丝线，上下 16）
      Row()
        .width("100%")
        .height(0.5)
        .backgroundColor(DesignColor.DIVIDER)
        .margin({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })

      // 取件码（recap §8-V3：标签 12sp + DataLarge 36 tnum 大字，对齐安卓）
      Row() {
        Text("取件码")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
        Blank()
        Text(this.parsePickupCode(order))
          .fontSize(DesignFontSize.DATA_LARGE)
          .fontColor(DesignColor.PRIMARY_DARK)
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
            Text("复制取件码")
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD })
          }
          .height(36)
          .justifyContent(FlexAlign.Center)
          .borderRadius(DesignDimens.RADIUS_MD)
          .backgroundColor(DesignColor.SURFACE_VARIANT)
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
    // 卡片按压反馈（recap §8-I1/G3：scale 0.97 + 100ms；动作按钮按下时行不缩放）
    .scale({
      x: this.pressedOrderId === order.orderId && !this.actionPressed ? 0.97 : 1,
      y: this.pressedOrderId === order.orderId && !this.actionPressed ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
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
        Text("联系客服")
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.PRIMARY_DARK)
      }
      .layoutWeight(1)
      .height(48)
      .justifyContent(FlexAlign.Center)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .border({ width: 1, color: DesignColor.OUTLINE })
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
        // 客服功能未立项，先以 Toast 兜底（与安卓 contact_service_unavailable 约定一致）
        showToastSafety("客服暂未开通，请稍后再试")
      })

      // 主按钮 — 纯色 primary 胶囊 + onPrimary 字
      Column() {
        Text("复制取件码")
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.ON_PRIMARY)
      }
      .layoutWeight(1)
      .height(48)
      .justifyContent(FlexAlign.Center)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .backgroundColor(DesignColor.PRIMARY)
      .margin({ left: DesignDimens.SPACE_SM })
      .scale({ x: this.copyPressed ? 0.97 : 1, y: this.copyPressed ? 0.97 : 1 })
      .animation({ duration: 100, curve: Curve.EaseOut })
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
      Column() {
        // 页头（recap G4：22/600 + 48 返回热区，复用公共 PageHeader）
        PageHeader({ title: "取件" })

        // 内容区三态：首载骨架 / 空态 / 内容（recap §8-F2）
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
                containerColor: DesignColor.ICON_CONTAINER_BLUE,
                iconColor: DesignColor.ICON_FOREGROUND_BLUE
              })
              Text(`${this.orderList[0]?.schoolsVo?.schoolName ?? ""}`)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontColor(DesignColor.TEXT_PRIMARY)
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
                ForEach(this.orderList, (order: OrderInfo) => {
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
                this.paging.onScrollIndex(end, this.orderList.length)
              }).onScrollFrameBegin((offset: number, state: ScrollState) => {
                this.paging.onScrollFrameBegin(offset)
                return { offsetRemain: offset }
              })
            }
            .layoutWeight(1)
            .onRefreshing(() => {
              this.refreshOrder()
            })
          }
          .width("100%")
          .layoutWeight(1)

          // 底部操作栏（recap §8-F1：固定底部）
          this.PickupActionBar()
        } else {
          // 空态（recap §8-V5：安卓 EmptyState DrumMark 母题；文案对齐 no_pickup_orders）
          Column() {
            EmptyState({ message: "暂无可取订单" })
          }
          .width("100%")
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        }
      }
      .width("100%")
      .height("100%")
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 每次展示先重置分页再拉取，避免从寄件页返回后 page>1 继续追加重复数据（评审 #14）
      this.refreshOrder()
    })
  }
}
