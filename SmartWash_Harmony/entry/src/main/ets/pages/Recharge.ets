import { CommonConstants } from "../constant/CommonConstants"
import { PaymentMethod } from "../constant/PaymentMethod"
import { ItemTitle } from "../view/ItemTitle"
import { promptAction } from "@kit.ArkUI"
import { userRecharge } from "../network/api/ReChargeApi"
import { HttpStatusCode } from "@ohos/axios"
import { pathStack } from "../utils/PathStackUtil"

@Builder
export function RechargeBuilder() {
  Recharge()
}

@Component
struct Recharge {
  @State payType: PaymentMethod | null = null
  @State payNum: number = 0
  private presetAmount: number[] = [10, 20, 50, 100, 200]
  @State inputPreset: string | null = null
  @State currItem: number | null = null
  @State text: string = ''
  @State checkPayType: boolean = false
  @State checkPayNumber: boolean = false

  @Builder
  PayTypeCard(method: PaymentMethod, title: string, icon: Resource,
    onClick: () => void) {
    RowSplit().height("16")
    Row() {
      Image(icon)
        .width(24)
        .height(24)
        .margin(8)

      Column() {
        Text(title)
      }.layoutWeight(5)
      .alignItems(HorizontalAlign.Start)

      Radio({ value: title, group: "PayType" })
        .checked(method == this.payType)
        .width(24)
        .height(24)
        .onChange((flag: boolean) => {
          if (flag) {
            this.payType = method
          }
        })
    }
    .height(64)
    .border(this.payType == method ?
      { width: 2, color: $r('sys.color.brand'), radius: CommonConstants.BORDER_RADIUS } : null)
    .borderRadius(CommonConstants.BORDER_RADIUS)
    .shadow(ShadowStyle.OUTER_DEFAULT_SM)
    .padding({ left: 8, right: 8 })
    .onClick(() => {
      onClick()
    })
  }

  aboutToDisappear(): void {
    AppStorage.setOrCreate("refreshUserInfo", true)
  }

  check() {
    if (this.currItem == null) {
      this.checkPayNumber = true
    } else if (this.currItem == 5) {
      if (this.inputPreset == null || this.inputPreset == "") {
        this.checkPayNumber = true
        return
      }
      this.payNum = parseInt(this.inputPreset)
    } else {
      this.checkPayNumber = false
    }

    this.checkPayType = this.payType == null
  }

  build() {
    NavDestination() {
      Column() {
        ItemTitle({ title: "充值金额" })

        //充值金额
        Grid() {
          ForEach(this.presetAmount, (item: number, i) => {
            GridItem() {
              Text(`${item}`)
            }
            .height(66)
            .width("100%")
            .shadow(ShadowStyle.OUTER_DEFAULT_SM)
            .border(this.currItem == i ?
              { width: 2, color: $r('sys.color.brand'), radius: CommonConstants.BORDER_RADIUS } : null)
            .borderRadius(CommonConstants.BORDER_RADIUS)
            .onClick(() => {
              this.payNum = item
              this.currItem = i
            })
          })

          GridItem() {
            TextInput({ text: $$this.inputPreset, placeholder: "自定义金额" })
              .backgroundColor($r('sys.color.background_primary'))
              .type(InputType.Number)
              .onFocus(() => {
                this.currItem = 5
              })
              .textAlign(TextAlign.Center)
              .maxLength(7)
              .placeholderFont({ size: 12 })
              .caretPosition(25)
          }
          .height(66)
          .width("100%")
          .border(this.currItem == 5 ?
            { width: 2, color: $r('sys.color.brand'), radius: CommonConstants.BORDER_RADIUS } : null)
          .shadow(ShadowStyle.OUTER_DEFAULT_SM)
          .borderRadius(CommonConstants.BORDER_RADIUS)
          .onClick(() => {
            this.currItem = 5
          })
        }
        .columnsTemplate('1fr 1fr 1fr')
        .rowsTemplate('1fr 1fr')
        .columnsGap(14)
        .rowsGap(15)
        .padding(8)
        .height("200")

        if (this.checkPayNumber) {
          Column() {
            Text("请选择获取输入充值金额")
              .fontColor($r('sys.color.warning'))
              .fontSize(13)
              .margin(12)
          }.width("100%")
          .alignItems(HorizontalAlign.Start)
        }
        RowSplit().height("16")

        ItemTitle({ title: "支付方式" })

        this.PayTypeCard(PaymentMethod.WECHAT, "微信支付", $r('app.media.wechat'), () => {
          this.payType = PaymentMethod.WECHAT
        })
        this.PayTypeCard(PaymentMethod.ALIPAY, "支付宝", $r('app.media.alipay'), () => {
          this.payType = PaymentMethod.ALIPAY
        })
        RowSplit().height("8")
        if (this.checkPayType) {
          Column() {
            Text("请选择支付方式")
              .fontColor($r('sys.color.warning'))
              .fontSize(13)
              .margin(12)
          }.width("100%")
          .alignItems(HorizontalAlign.Start)
        }

        RowSplit().height("16")

        Column().layoutWeight(1)

        Button(`确认支付￥${this.currItem == 5 ? this.inputPreset ?? "" : this.payNum}`, { type: ButtonType.Normal })
          .onClick((event: ClickEvent) => {
            promptAction.showDialog({
              title: "提示",
              message: "是否确认充值",
              buttons: [{ text: "取消", color: $r('sys.color.font_primary') },
                { text: "确认", color: $r('sys.color.font_primary') }],
            }, async (err, data) => {
              if (data.index == 1) {
                this.check()
                if (this.checkPayType || this.checkPayNumber) {
                  return
                }
                let res = await userRecharge({
                  amount: this.payNum,
                  rechargeType: this.payType!.toString()
                })
                if (res.code == HttpStatusCode.Ok) {
                  pathStack.pop()
                  promptAction.showToast({ message: "充值成功" })
                } else {
                  promptAction.showToast({ message: res.message })
                }
              }
            })
          })
          .width("100%")
          .borderRadius(CommonConstants.BORDER_RADIUS)
          .alignSelf(ItemAlign.End)
          .margin({ bottom: 8 })
      }.padding({ left: 16, right: 16 })
    }
    .title("充值")
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor($r('sys.color.background_primary'))
  }
}