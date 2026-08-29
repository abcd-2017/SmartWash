import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { PaymentMethod } from "../constant/PaymentMethod"
import { promptAction } from "@kit.ArkUI"
import { userRecharge } from "../network/api/ReChargeApi"
import { HttpStatusCode } from "@ohos/axios"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { AppButton } from "../view/common/AppComponents"

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
  @State checkPayType: boolean = false
  @State checkPayNumber: boolean = false

  @Builder
  AmountCard(amount: number, index: number) {
    Column() {
      Text(`¥${amount}`)
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(this.currItem == index ? DesignColor.PRIMARY : DesignColor.TEXT_PRIMARY)
    }
    .height(66)
    .width("100%")
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor(this.currItem == index ? DesignColor.PRIMARY_LIGHT : DesignColor.SURFACE)
    .borderRadius(DesignDimens.SMALL_CARD_RADIUS)
    .border(this.currItem == index ?
      { width: 1.5, color: DesignColor.PRIMARY, radius: DesignDimens.SMALL_CARD_RADIUS } :
      { width: 0.5, color: DesignColor.OUTLINE, radius: DesignDimens.SMALL_CARD_RADIUS })
    .onClick(() => {
      this.payNum = amount
      this.currItem = index
      this.checkPayNumber = false
    })
  }

  @Builder
  CustomAmountCard() {
    Column() {
      if (this.currItem == 5) {
        TextInput({ text: $$this.inputPreset, placeholder: "自定义金额" })
          .backgroundColor(Color.Transparent)
          .type(InputType.Number)
          .textAlign(TextAlign.Center)
          .maxLength(7)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .placeholderFont({ size: DesignFontSize.BODY, color: DesignColor.TEXT_TERTIARY })
          .caretPosition(25)
          .onFocus(() => {
            this.currItem = 5
          })
          .onChange((value: string) => {
            this.inputPreset = value
            if (value != null && value != "") {
              this.payNum = parseFloat(value)
            }
            this.checkPayNumber = false
          })
      } else {
        Text("自定义金额")
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_SECONDARY)
      }
    }
    .height(66)
    .width("100%")
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor(this.currItem == 5 ? DesignColor.PRIMARY_LIGHT : DesignColor.SURFACE)
    .borderRadius(DesignDimens.SMALL_CARD_RADIUS)
    .border(this.currItem == 5 ?
      { width: 1.5, color: DesignColor.PRIMARY, radius: DesignDimens.SMALL_CARD_RADIUS } :
      { width: 0.5, color: DesignColor.OUTLINE, radius: DesignDimens.SMALL_CARD_RADIUS })
    .onClick(() => {
      this.currItem = 5
    })
  }

  @Builder
  PayTypeCard(method: PaymentMethod, title: string, icon: Resource,
    brandColor: number) {
    Row() {
      // 品牌图标容器
      Row() {
        Image(icon)
          .width(20)
          .height(20)
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(method == PaymentMethod.WECHAT ?
        0x1A07C160 : 0x1A1677FF)
      .justifyContent(FlexAlign.Center)
      .alignItems(VerticalAlign.Center)

      Text(title)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ left: 12 })
        .layoutWeight(1)

      Radio({ value: title, group: "PayType" })
        .checked(method == this.payType)
        .width(22)
        .height(22)
        .onChange((flag: boolean) => {
          if (flag) {
            this.payType = method
            this.checkPayType = false
          }
        })
    }
    .width("100%")
    .height(56)
    .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
    .alignItems(VerticalAlign.Center)
    .onClick(() => {
      this.payType = method
      this.checkPayType = false
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
        // 页面标题行
        Row() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DesignColor.ON_BACKGROUND])
            .onClick(() => {
              pathStack.pop()
            })
            .padding(8)

          Text("充值")
            .fontSize(DesignFontSize.PAGE_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(DesignColor.ON_BACKGROUND)
            .margin({ left: 4 })

          Blank()

          // 历史记录图标按钮
          SymbolGlyph($r('sys.symbol.clock_arrow_circlepath'))
            .fontSize(22)
            .fontColor([DesignColor.TEXT_SECONDARY])
            .onClick(() => {
              pathStack.pushPathByName("RechargeRecord", null)
            })
            .padding(8)
        }
        .width("100%")
        .height(56)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .alignItems(VerticalAlign.Center)

        // 充值金额区域
        Text("充值金额")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .margin({ top: DesignDimens.SECTION_SPACING, bottom: 12 })
          .alignSelf(ItemAlign.Start)

        Grid() {
          ForEach(this.presetAmount, (item: number, i) => {
            GridItem() {
              this.AmountCard(item, i)
            }
          })

          GridItem() {
            this.CustomAmountCard()
          }
        }
        .columnsTemplate('1fr 1fr 1fr')
        .rowsTemplate('1fr 1fr')
        .columnsGap(12)
        .rowsGap(12)
        .height(150)

        if (this.checkPayNumber) {
          Text("请选择或输入充值金额")
            .fontColor(DesignColor.ERROR)
            .fontSize(DesignFontSize.CAPTION)
            .margin({ top: 8 })
            .alignSelf(ItemAlign.Start)
        }

        // 支付方式区域
        Text("支付方式")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .margin({ top: DesignDimens.SECTION_SPACING, bottom: 12 })
          .alignSelf(ItemAlign.Start)

        Column() {
          this.PayTypeCard(PaymentMethod.WECHAT, "微信支付", $r('app.media.wechat'),
            DesignColor.WECHAT_GREEN)

          Divider()
            .color(DesignColor.DIVIDER)
            .strokeWidth(0.5)
            .width("85%")

          this.PayTypeCard(PaymentMethod.ALIPAY, "支付宝", $r('app.media.alipay'),
            DesignColor.ALIPAY_BLUE)
        }
        .width("100%")
        .backgroundColor(DesignColor.SURFACE)
        .borderRadius(DesignDimens.CARD_RADIUS)
        .border({ width: 0.5, color: DesignColor.OUTLINE, radius: DesignDimens.CARD_RADIUS })

        if (this.checkPayType) {
          Text("请选择支付方式")
            .fontColor(DesignColor.ERROR)
            .fontSize(DesignFontSize.CAPTION)
            .margin({ top: 8 })
            .alignSelf(ItemAlign.Start)
        }

        // 底部弹性空间
        Column().layoutWeight(1)

        // 确认支付按钮
        AppButton({
          text: `确认支付 ¥${this.currItem == 5 ? this.inputPreset ?? "0" : this.payNum}`,
          click: () => {
            promptAction.showDialog({
              title: "提示",
              message: "是否确认充值",
              buttons: [{ text: "取消", color: DesignColor.TEXT_PRIMARY },
                { text: "确认", color: DesignColor.TEXT_PRIMARY }],
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
                  showToastSafety("充值成功")
                } else {
                  showToastSafety(res.message, "充值失败")
                }
              }
            })
          }
        })
        .margin({ bottom: DesignDimens.PAGE_PADDING })
      }
      .width("100%")
      .height("100%")
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    }
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor(DesignColor.BACKGROUND)
    .hideTitleBar(true)
  }
}
