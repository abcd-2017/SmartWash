import { HttpStatusCode } from "@ohos/axios"
import { generateQrCode, pickupOrder, shippingOrder } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { promptAction } from "@kit.ArkUI"
import { OrderStatusEnum } from "../constant/OrderStatus"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { OrderItemRow } from "../view/OrderItemRow"
import { BaseResponse } from "../network/BaseResponse"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

@Builder
export function PickupDeliveryBuilder() {
  PickupDelivery()
}

@Component
struct PickupDelivery {
  @State orderInfo: OrderInfo | null = null
  @State isPickup: boolean = false
  @State qrCode: string = ""
  @State pickCode: string = ""

  async aboutToAppear() {
    this.orderInfo = pathStack.getParamByName("PickupDelivery")[0] as OrderInfo
    this.pickCode = this.orderInfo?.pickupCode?.split(":").pop() ?? "error"
    this.isPickup = this.orderInfo?.status == OrderStatusEnum.READY_FOR_PICKUP

    let res = await generateQrCode(this.pickCode)
    if (res.code == HttpStatusCode.Ok) {
      this.qrCode = res.data ?? ""
    } else {
      showToastSafety(res.message, "取件码生成失败")
    }
  }

  @Builder
  InstructionRow(i: string, content: string) {
    Row() {
      Column() {
        Text(`${i}`)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.ON_PRIMARY)
          .fontWeight(FontWeight.Bold)
      }
      .width(28)
      .height(28)
      .borderRadius(14)
      .justifyContent(FlexAlign.Center)
      .backgroundColor(DesignColor.PRIMARY)
      .margin({ right: 12 })

      Text(content)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_PRIMARY)
    }.width("100%")
    .margin({ top: 4, bottom: 4 })
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
                pathStack.back()
              })

            Text(this.isPickup ? "取件" : "寄件")
              .fontSize(DesignFontSize.PAGE_TITLE)
              .fontWeight(FontWeight.Bold)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .margin({ left: 8 })
          }
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, top: 12, bottom: 8 })

          // 注意事项卡片（仅寄件模式）
          if (!this.isPickup) {
            Column() {
              Row() {
                SymbolGlyph($r('sys.symbol.exclamationmark_triangle_fill'))
                  .fontColor([DesignColor.ON_WARNING])
                  .fontSize(18)
                Text("注意事项")
                  .fontSize(DesignFontSize.CARD_TITLE)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DesignColor.ON_WARNING)
                  .margin({ left: 10 })
              }.justifyContent(FlexAlign.Start)
              .width("100%")
              .margin({ bottom: 10 })

              Row() {
                Text("请确保在30分钟内完成操作\n" +
                  "如遇问题请联系客服\n" +
                  "请勿将贵重物品放入寄存柜")
                  .fontSize(DesignFontSize.BODY)
                  .fontColor(DesignColor.ON_WARNING)
                  .lineHeight(20)
              }.justifyContent(FlexAlign.Start)
              .width("100%")
            }
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.WARNING)
            .borderRadius(DesignDimens.CARD_RADIUS)

            RowSplit().height("30")
          }

          // 二维码显示
          Image(`data:image/png;base64,${this.qrCode}`)
            .size({ width: 180, height: 180 })
            .borderRadius(DesignDimens.CARD_RADIUS)

          RowSplit().height("24")

          // 寄件/取件码标题
          Row() {
            Text(`${this.isPickup ? "取件码" : "寄件码"}`)
              .fontWeight(FontWeight.Bold)
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontColor(DesignColor.TEXT_SECONDARY)
          }.margin({ bottom: 8 })

          // 寄件/取件码大字
          Row() {
            Text(`${this.pickCode}`)
              .fontSize(28)
              .fontWeight(FontWeight.Bold)
              .fontColor(DesignColor.PRIMARY)
          }

          RowSplit().height("20")

          // 确认按钮
          Button(this.isPickup ? "确认已取件" : "确认已寄件")
            .onClick((event: ClickEvent) => {
              promptAction.showDialog({
                message: `${this.isPickup ? "确认已将衣服放入寄存柜" : "确认已取出衣服"}`,
                buttons: [{ text: "取消", color: DesignColor.TEXT_PRIMARY },
                  { text: "确认", color: DesignColor.TEXT_PRIMARY }],
              }, async (err, data) => {
                if (data.index == 1) {
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
                }
              })
            })
            .width("100%")
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontColor(DesignColor.ON_PRIMARY)
            .backgroundColor(DesignColor.PRIMARY)
            .borderRadius(DesignDimens.BUTTON_RADIUS)

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

          // 操作指引卡片
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
            this.InstructionRow("4", "等待柜门打开，取出衣物")
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
