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
import { pasteboard } from "@kit.BasicServicesKit"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

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
    this.fetchPickupPage(1)
  }

  /**
   * 按页拉取可取件订单。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchPickupPage(page: number): Promise<boolean> {
    let res = await getOrderList({
      status: OrderStatusEnum.READY_FOR_PICKUP,
      page: page, size: PAGE_SIZE
    })
    if (res.code === HttpStatusCode.Ok) {
      if (res.data == null || res.data.length == 0) {
        if (this.orderList?.length > 0) {
          showToastSafety("没有更多数据了")
        }
        return false
      }
      if (page == 1) {
        this.orderList = []
      }
      this.orderList?.push(...res.data)
      return true
    }
    showToastSafety(res.message, "取件订单加载失败")
    return false
  }

  /** 取件码解析 — 统一取冒号分段第 3 段（D4，对齐安卓 getOrNull(2)） */
  private parsePickupCode(order: OrderInfo): string {
    return order.pickupCode?.split(":")[2] ?? ""
  }

  /** 复制取件码到剪贴板（用户需到柜机输入取件码，复制是刚需，对齐安卓 clipboard + toast） */
  private copyPickupCode(code: string): void {
    if (code === "") {
      return
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, code)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety("取件码已复制")
    }).catch(() => {
      showToastSafety("复制失败")
    })
  }

  refreshOrder() {
    this.isRefreshing = false
    this.paging.reset()
    this.orderList = []
    this.fetchPickupPage(1)
  }

  @Builder
  PickupOrderCard(order: OrderInfo) {
    Column() {
      Column() {
        // 订单号 + 寄存柜号 + 价格
        Row() {
          Column() {
            Row() {
              Text(`订单号：${order.orderNo}`)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }

            Row() {
              Text(`寄存柜：${order.lockersVo?.lockerNumber}`)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }.margin({ top: 4 })
          }.alignItems(HorizontalAlign.Start)

          ColumnSplit().layoutWeight(1)
          Column() {
            Row() {
              Text(`￥${order.totalPrice}`)
                .fontSize(DesignFontSize.AMOUNT_MEDIUM)
                .fontWeight(FontWeight.Bold)
                .fontColor(DesignColor.PRIMARY_DARK)
            }
          }
        }.width("100%")

        // 分隔线
        Row()
          .width("100%")
          .height(0.5)
          .backgroundColor(DesignColor.DIVIDER)
          .margin({ top: 12, bottom: 12 })

        // 取件码大字（对齐安卓 36sp DataLarge）
        Row() {
          Text("取件码")
            .fontSize(DesignFontSize.BODY)
            .fontColor(DesignColor.TEXT_SECONDARY)
          ColumnSplit().layoutWeight(1)
          Text(this.parsePickupCode(order))
            .fontSize(DesignFontSize.DATA_LARGE)
            .fontColor(DesignColor.PRIMARY_DARK)
            .fontWeight(FontWeight.Bold)
        }.width("100%")

        // 复制取件码（有码才显示）
        if (this.parsePickupCode(order) !== "") {
          Row() {
            Text("复制取件码")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .padding({ left: 16, right: 16, top: 8, bottom: 8 })
          }
          .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
          .backgroundColor(DesignColor.SURFACE_VARIANT)
          .margin({ top: 14 })
          .onClick(() => {
            this.copyPickupCode(this.parsePickupCode(order))
          })
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
      .onClick(() => {
        // 显式类型入参（差异分析 02 §8.3）：不再由 status 推断取件/寄件模式
        const param: PickupDeliveryParam = new PickupDeliveryParam()
        param.order = order
        param.isPickup = true
        pathStack.pushPathByName(RouteNames.PICKUP_DELIVERY, param)
      })
    }
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: 4,
      bottom: 4
    })
  }

  build() {
    NavDestination() {
      Column() {
        // 自定义 PageHeader
        Row() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DesignColor.TEXT_PRIMARY])
            .onClick(() => {
              pathStack.pop() // NavPathStack 无 back()，出栈统一用 pop()
            })

          Text("取件")
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(FontWeight.Bold)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ left: 8 })
        }
        .width("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, top: 12, bottom: 8 })

        // 学校信息行（对齐安卓：取首单的学校快照，无单不显示）
        if (this.orderList?.length > 0) {
          Row() {
            SymbolGlyph($r('sys.symbol.company'))
              .fontColor([DesignColor.PRIMARY])
              .fontSize(18)
              .margin({ right: 8 })
            Text(`${this.orderList[0]?.schoolsVo?.schoolName ?? ""}`)
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontWeight(FontWeight.Bold)
              .fontColor(DesignColor.TEXT_PRIMARY)
          }.justifyContent(FlexAlign.Start)
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 4, bottom: 4 })
        }

        if ((this.orderList?.length ?? 0) > 0) {
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
            }.onScrollIndex((start: number, end: number) => {
              this.paging.onScrollIndex(end, this.orderList?.length ?? 0)
            }).onScrollFrameBegin((offset: number, state: ScrollState) => {
              this.paging.onScrollFrameBegin(offset)
              return { offsetRemain: offset }
            })
          }.onRefreshing(() => {
            this.refreshOrder()
          }).height("100%")
          .padding({ top: 12, bottom: 8 })
        } else {
          Column() {
            SymbolGlyph($r('sys.symbol.square_slash'))
              .fontColor([DesignColor.TEXT_TERTIARY])
              .fontSize(48)
              .margin({ bottom: 16 })

            Text("暂无可取件订单")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }.height("100%")
          .width("100%")
          .justifyContent(FlexAlign.Center)
        }
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 每次展示先重置分页再拉取，避免从寄件页返回后 page>1 继续追加重复数据（评审 #14）
      this.refreshOrder()
    })
  }
}
