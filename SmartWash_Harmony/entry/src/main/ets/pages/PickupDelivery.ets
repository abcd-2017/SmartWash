import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { generateQrCode, getOrderInfo, pickupOrder, shippingOrder } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getObjectParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { OrderItemRow } from "../view/OrderItemRow"
import { AppButton, AppConfirmDialog } from "../view/common/AppComponents"
import { BaseResponse } from "../network/BaseResponse"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

/**
 * PickupDelivery 路由入参：显式指定取件/寄件类型（差异分析 02 §8.3）。
 * 原 status 推断法在 PENDING_SHIPMENT 之外状态会误判，且无法表达安卓
 * 「去寄件」强制进入寄件模式的语义；type 由调用方显式传入。
 */
export class PickupDeliveryParam {
  order: OrderInfo | null = null
  isPickup: boolean = false
}

@Builder
export function PickupDeliveryBuilder() {
  PickupDelivery()
}

@Component
struct PickupDelivery {
  @State orderInfo: OrderInfo | null = null
  @State isPickup: boolean = false
  // 二维码三态：加载中占位 / 成功渲染 / 失败兜底可重试（D7：保留后端 generateQrCode，补占位与失败兜底）
  @State qrCode: string = ""
  @State qrLoading: boolean = true
  @State qrFailed: boolean = false
  @State pickCode: string = ""
  // 确认弹窗文案（按类型区分，open 前赋值）
  @State confirmMessage: string = ""
  // 确认操作请求中标记：防确认弹窗重复提交
  @State confirming: boolean = false
  // 确认弹窗（对齐安卓 AppConfirmDialog，替代系统 promptAction 弹窗）
  private confirmDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      message: this.confirmMessage,
      onConfirm: () => {
        this.doConfirm()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  aboutToAppear(): void {
    this.initPage()
  }

  /**
   * 页面初始化：读显式类型参数 → 重拉订单详情 → 计算取件码 → 生成二维码。
   * 列表快照可能陈旧（pickupCode/柜号状态以详情为准），对齐安卓每次进入重拉 getOrderInfo。
   */
  async initPage() {
    const param = getObjectParam<PickupDeliveryParam>(RouteNames.PICKUP_DELIVERY)
    this.isPickup = param?.isPickup ?? false
    // 先以路由快照兜底渲染，再用详情接口刷新
    this.orderInfo = param?.order ?? null
    const orderId = param?.order?.orderId ?? null
    if (orderId !== null) {
      let detailRes = await getOrderInfo(orderId)
      if (detailRes.code === HttpStatusCode.Ok && detailRes.data !== null) {
        this.orderInfo = detailRes.data
      } else {
        showToastSafety(detailRes.message, "订单信息加载失败")
      }
    }
    this.pickCode = this.parsePickupCode()
    await this.loadQrCode()
  }

  /** 取件码解析 — 统一取冒号分段第 3 段（D4，对齐安卓 getOrNull(2)） */
  private parsePickupCode(): string {
    return this.orderInfo?.pickupCode?.split(":")[2] ?? ""
  }

  /** 请求二维码（可重试）：生成期间占位，失败可点击重试 */
  async loadQrCode() {
    if (this.pickCode === "") {
      this.qrFailed = true
      this.qrLoading = false
      return
    }
    this.qrLoading = true
    this.qrFailed = false
    let res = await generateQrCode(this.pickCode)
    if (res.code == HttpStatusCode.Ok && (res.data ?? "") !== "") {
      this.qrCode = res.data ?? ""
    } else {
      showToastSafety(res.message, "取件码生成失败")
      this.qrFailed = true
    }
    this.qrLoading = false
  }

  /** 确认取件/寄件：pickupCode 传完整串（与安卓一致） */
  async doConfirm() {
    if (this.confirming) {
      return
    }
    this.confirming = true
    try {
      let res: BaseResponse<boolean>
      if (this.isPickup) {
        res = await pickupOrder({
          orderId: this.orderInfo?.orderId ?? -1,
          pickupCode: this.orderInfo?.pickupCode ?? ""
        })
      } else {
        res = await shippingOrder({
          orderId: this.orderInfo?.orderId ?? -1,
          pickupCode: this.orderInfo?.pickupCode ?? ""
        })
      }
      if (res.code == HttpStatusCode.Ok) {
        showToastSafety("操作成功")
        pathStack.pop()
      } else {
        showToastSafety(res.message, "操作失败")
      }
    } finally {
      this.confirming = false
    }
  }

  @Builder
  InstructionRow(i: string, content: string) {
    Row() {
      Column() {
        Text(`${i}`)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.PRIMARY)
          .fontWeight(FontWeight.Bold)
      }
      .width(32)
      .height(32)
      .borderRadius(10)
      .justifyContent(FlexAlign.Center)
      .backgroundColor(DesignColor.PRIMARY_LIGHT)
      .margin({ right: 12 })

      Text(content)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_PRIMARY)
    }.width("100%")
    .margin({ top: 4, bottom: 4 })
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  QrCodeArea() {
    if (this.qrCode !== "") {
      Image(`data:image/png;base64,${this.qrCode}`)
        .size({ width: 200, height: 200 })
        .borderRadius(DesignDimens.CARD_RADIUS)
    } else {
      // 占位/失败兜底（D7）：返回前不再空白，失败可点击重试
      Column() {
        if (this.qrLoading) {
          LoadingProgress()
            .width(28)
            .height(28)
            .color(DesignColor.PRIMARY)
          Text("加载中")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: 8 })
        } else {
          Text(this.qrFailed ? "二维码生成失败" : "暂无二维码")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
          if (this.qrFailed) {
            Text("点击重试")
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.PRIMARY)
              .margin({ top: 8 })
          }
        }
      }
      .size({ width: 200, height: 200 })
      .justifyContent(FlexAlign.Center)
      .backgroundColor(DesignColor.SURFACE_VARIANT)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .onClick(() => {
        if (!this.qrLoading && this.qrFailed) {
          this.loadQrCode()
        }
      })
    }
  }

  build() {
    NavDestination() {
      Scroll() {
        Column() {
          // 自定义 PageHeader
          Row() {
            SymbolGlyph($r('sys.symbol.chevron_left'))
              .fontSize(24)
              .fontColor([DesignColor.TEXT_PRIMARY])
              .onClick(() => {
                pathStack.pop() // NavPathStack 无 back()，出栈统一用 pop()
              })

            Text(this.isPickup ? "取件" : "寄件")
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(FontWeight.Bold)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .margin({ left: 8 })
          }
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, top: 12, bottom: 8 })

          // 注意事项卡片（仅寄件模式，文案对齐安卓 notes_content）
          if (!this.isPickup) {
            Column() {
              Row() {
                SymbolGlyph($r('sys.symbol.exclamationmark_triangle_fill'))
                  .fontColor([DesignColor.WARNING])
                  .fontSize(18)
                Text("注意事项")
                  .fontSize(DesignFontSize.CARD_TITLE)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DesignColor.ON_WARNING_CONTAINER)
                  .margin({ left: 10 })
              }.justifyContent(FlexAlign.Start)
              .width("100%")
              .margin({ bottom: 10 })

              Row() {
                Text("• 请确保在30分钟内完成操作\n" +
                  "• 如遇问题请联系客服\n" +
                  "• 请勿将贵重物品放入寄存柜")
                  .fontSize(DesignFontSize.BODY)
                  .fontColor(DesignColor.ON_WARNING_CONTAINER)
                  .lineHeight(20)
              }.justifyContent(FlexAlign.Start)
              .width("100%")
            }
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.WARNING_CONTAINER)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .alignItems(HorizontalAlign.Start)

            RowSplit().height("30")
          }

          // 二维码卡（生成期间占位，失败可重试）
          Column() {
            this.QrCodeArea()

            Row() {
              Text(`${this.isPickup ? "取件码" : "寄件码"}`)
                .fontWeight(FontWeight.Bold)
                .fontSize(DesignFontSize.SECTION_TITLE)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }.margin({ top: 20, bottom: 8 })

            // 寄件/取件码大字（对齐安卓 DataLarge）
            Row() {
              Text(`${this.pickCode}`)
                .fontSize(DesignFontSize.DATA_LARGE)
                .fontWeight(FontWeight.Bold)
                .fontColor(DesignColor.PRIMARY_DARK)
            }
          }
          .width("100%")
          .padding(DesignDimens.CARD_PADDING + 8)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 0.5, color: DesignColor.OUTLINE })
          .alignItems(HorizontalAlign.Center)

          RowSplit().height("20")

          // 确认按钮（点击弹按类型区分文案的确认弹窗）
          AppButton({
            text: this.isPickup ? "确认已取件" : "确认已寄件",
            click: () => {
              this.confirmMessage = this.isPickup ? "确认已取出衣物" : "确认已将衣物放入寄存柜"
              this.confirmDialogController.open()
            }
          })

          RowSplit().height("28")

          // 寄存柜信息卡片
          Column() {
            Row() {
              SymbolGlyph($r('sys.symbol.externaldrive_fill_3'))
                .fontColor([DesignColor.PRIMARY])
                .fontSize(DesignFontSize.SECTION_TITLE)
              Text("寄存柜信息")
                .fontSize(DesignFontSize.CARD_TITLE)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .margin({ left: 10 })
            }.justifyContent(FlexAlign.Start)
            .width("100%")
            .margin({ bottom: 10 })

            OrderItemRow({ title: "学校名称", content: `${this.orderInfo?.schoolsVo?.schoolName}` })
            OrderItemRow({ title: "地址", content: `${this.orderInfo?.schoolsVo?.location}` })
            OrderItemRow({ title: "寄存柜柜号", content: `${this.orderInfo?.lockersVo?.lockerNumber}` })
            OrderItemRow({ title: `${this.isPickup ? "取件码" : "寄件码"}`, content: `${this.pickCode}` })
          }
          .padding(DesignDimens.CARD_PADDING)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 0.5, color: DesignColor.OUTLINE })

          RowSplit().height("20")

          // 操作指引卡片（第 4 步按类型区分：取出/放入，修复寄件模式文案错误）
          Column() {
            Row() {
              SymbolGlyph($r('sys.symbol.info_circle'))
                .fontColor([DesignColor.PRIMARY])
                .fontSize(DesignFontSize.SECTION_TITLE)
              Text("操作指引")
                .fontSize(DesignFontSize.CARD_TITLE)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .margin({ left: 10 })
            }.justifyContent(FlexAlign.Start)
            .width("100%")
            .margin({ bottom: 10 })

            this.InstructionRow("1", `找到寄存柜 ${this.orderInfo?.lockersVo?.lockerNumber}`)
            this.InstructionRow("2", `点击屏幕上的\" ${this.isPickup ? "取件" : "寄件"} \"按钮`)
            this.InstructionRow("3", `输入${this.isPickup ? "取件码" : "寄件码"}：${this.pickCode}`)
            this.InstructionRow("4", this.isPickup ? "等待柜门打开，取出衣物" : "等待柜门打开，放入衣物")
          }
          .padding(DesignDimens.CARD_PADDING)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.CARD_RADIUS)
          .border({ width: 0.5, color: DesignColor.OUTLINE })
        }
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, bottom: 24 })
      }.scrollBar(BarState.Off)
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}
