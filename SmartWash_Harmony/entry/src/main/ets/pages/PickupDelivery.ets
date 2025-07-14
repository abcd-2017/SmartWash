import { HttpStatusCode } from "@ohos/axios"
import { generateQrCode, pickupOrder, shippingOrder } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { CircleShape, prompt, promptAction } from "@kit.ArkUI"
import { OrderStatusEnum } from "../constant/OrderStatus"
import { pathStack } from "../utils/PathStackUtil"
import { CommonConstants } from "../constant/CommonConstants"
import { OrderItemRow } from "../view/OrderItemRow"
import { BaseResponse } from "../network/BaseResponse"

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
    this.pickCode = this.orderInfo?.pickupCode?.split(":")[2] ?? "error"
    this.isPickup = this.orderInfo?.status == OrderStatusEnum.READY_FOR_PICKUP

    let res = await generateQrCode(this.pickCode)
    if (res.code == HttpStatusCode.Ok) {
      this.qrCode = res.data ?? ""
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  @Builder
  InstructionRow(i: string, content: string) {
    Row() {
      Column() {
        Text(`${i}`)
          .fontSize(14)
      }.borderRadius(100)
      .padding(6)
      .margin({ right: 12 })
      .backgroundColor($r('sys.color.icon_sub_emphasize'))

      Text(content)
        .fontSize(14)
    }.width("100%")
    .margin({ top: 4, bottom: 4 })
  }

  build() {
    NavDestination() {
      Scroll() {
        Column() {
          if (!this.isPickup) {
            Column() {
              Row() {
                SymbolGlyph($r('sys.symbol.exclamationmark_triangle_fill'))
                  .fontColor([$r('sys.color.font_on_primary')])
                  .fontSize(18)
                Text("注意事项")
                  .fontSize(16)
                  .fontWeight(FontWeight.Bold)
                  .fontColor($r('sys.color.font_on_primary'))
                  .margin({ left: 10 })
              }.justifyContent(FlexAlign.Start)
              .width("100%")
              .margin({ bottom: 10 })

              Row() {
                Text("• 请确保在30分钟内完成操作\n" +
                  "• 如遇问题请联系客服\n" +
                  "• 请勿将贵重物品放入寄存柜")
                  .fontSize(14)
                  .fontColor($r('sys.color.font_on_primary'))
                  .lineHeight(18)
              }.justifyContent(FlexAlign.Start)
              .width("100%")
            }.padding(16)
            .backgroundColor($r('sys.color.alert'))
            .borderRadius(CommonConstants.BORDER_RADIUS)
            .shadow(ShadowStyle.OUTER_DEFAULT_SM)

            RowSplit().height("30")
          }
          Image(`data:image/png;base64,${this.qrCode}`)
            .size({ width: 180, height: 180 })
            .borderRadius(CommonConstants.BORDER_RADIUS * 2)
            .shadow(ShadowStyle.OUTER_DEFAULT_SM)

          RowSplit().height("24")
          Row() {
            Text(`${this.isPickup ? "取件码" : "寄件码"}`)
              .fontWeight(FontWeight.Bold)
              .fontSize(16)
          }.margin({ bottom: 8 })

          Row() {
            Text(`${this.pickCode}`)
              .fontSize(20)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('sys.color.brand'))
          }

          RowSplit().height("20")
          Button(this.isPickup ? "确认已取件" : "确认已寄件").onClick((event: ClickEvent) => {
            promptAction.showDialog({
              message: `${this.isPickup ? "确认已将衣服放入寄存柜" : "确认已取出衣服"}`,
              buttons: [{ text: "取消", color: $r('sys.color.font_primary') },
                { text: "确认", color: $r('sys.color.font_primary') }],
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
                  promptAction.showToast({ message: "操作成功" })
                  pathStack.pop(11)
                } else {
                  promptAction.showToast({ message: res.message })
                }
              }
            })
          }).width("100%")
            .fontSize(16)

          RowSplit().height("28")
          Column() {
            Row() {
              SymbolGlyph($r('sys.symbol.externaldrive_fill_3'))
                .fontColor([$r('sys.color.font_primary')])
                .fontSize(18)
              Text("寄存柜信息")
                .fontSize(16)
                .margin({ left: 10 })
            }.justifyContent(FlexAlign.Start)
            .width("100%")
            .margin({ bottom: 10 })

            OrderItemRow({ title: "学校名称", content: `${this.orderInfo?.schoolsVo?.schoolName}` })
            OrderItemRow({ title: "地址", content: `${this.orderInfo?.schoolsVo?.location}` })
            OrderItemRow({ title: "寄存柜柜号", content: `${this.orderInfo?.lockersVo?.lockerNumber}` })
            OrderItemRow({ title: `${this.isPickup ? "取件码" : "寄件码"}`, content: `${this.pickCode}` })
          }.padding(16)
          .backgroundColor($r('sys.color.comp_background_primary'))
          .borderRadius(CommonConstants.BORDER_RADIUS)
          .shadow(ShadowStyle.OUTER_DEFAULT_SM)

          RowSplit().height("20")
          Column() {
            Row() {
              SymbolGlyph($r('sys.symbol.info_circle'))
                .fontColor([$r('sys.color.font_primary')])
                .fontSize(18)
              Text("操作指引")
                .fontSize(16)
                .margin({ left: 10 })
            }.justifyContent(FlexAlign.Start)
            .width("100%")
            .margin({ bottom: 10 })

            this.InstructionRow("1", `找到寄存柜 ${this.orderInfo?.lockersVo?.lockerNumber}`)
            this.InstructionRow("2", `点击屏幕上的\" ${this.isPickup ? "取件" : "寄件"} \"按钮`)
            this.InstructionRow("3", `输入${this.isPickup ? "取件码" : "寄件码"}：${this.pickCode}`)
            this.InstructionRow("4", "等待柜门打开，取出衣物")
          }.padding(16)
          .backgroundColor($r('sys.color.comp_background_primary'))
          .borderRadius(CommonConstants.BORDER_RADIUS)
          .shadow(ShadowStyle.OUTER_DEFAULT_SM)
        }.padding(16)
      }.scrollBar(BarState.Off)
    }.title(this.isPickup ? "取件" : "寄件")
  }
}