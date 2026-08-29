import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { PaymentMethod } from "../constant/PaymentMethod"
import { RechargeType } from "../constant/RechargeType"
import { promptAction } from "@kit.ArkUI"
import { userRecharge } from "../network/api/ReChargeApi"
import { HttpStatusCode } from "@ohos/axios"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { AppButton } from "../view/common/AppComponents"

// 单笔充值金额上限：与后端 RechargeRecordsServiceImpl.MAX_RECHARGE_AMOUNT 保持一致（评审 #19）
const MAX_RECHARGE_AMOUNT: number = 10000
// 金额格式：正整数或最多两位小数，与后端 scale<=2 校验一致（评审 #19）
const AMOUNT_PATTERN: RegExp = /^\d+(\.\d{1,2})?$/

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
  // 金额校验失败的具体文案（评审 #19：错误提示友好化）
  @State checkPayNumberMsg: string = "请选择或输入充值金额"

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
          .maxLength(8) // 8 位可完整输入上限金额 "10000.00"（含两位小数，评审 #19）
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
  PayTypeCard(method: PaymentMethod, title: string, icon: Resource) {
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
      // 品牌浅色底收敛到 DesignSystem 常量（评审 #18）
      .backgroundColor(method == PaymentMethod.WECHAT ?
        DesignColor.WECHAT_GREEN_LIGHT : DesignColor.ALIPAY_BLUE_LIGHT)
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
    // 金额精确校验（评审 #19）：不合法时 checkPayNumberMsg 已写入具体原因
    this.checkPayNumber = !this.validateAmount()
    this.checkPayType = this.payType == null
  }

  /**
   * 充值金额精确校验（评审 #19）：必须为正数、最多两位小数、不超过单笔上限 10000（与后端一致）。
   * 替代原 parseInt 实现（会截断小数，且缺少 >0 与上限校验）。
   * 预设金额卡片取值固定合法；自定义金额逐项校验，失败原因写入 checkPayNumberMsg 友好提示。
   */
  validateAmount(): boolean {
    if (this.currItem == null) {
      this.checkPayNumberMsg = "请选择或输入充值金额"
      return false
    }
    if (this.currItem !== 5) {
      return true
    }
    const raw = (this.inputPreset ?? "").trim()
    if (raw === "") {
      this.checkPayNumberMsg = "请输入充值金额"
      return false
    }
    if (!AMOUNT_PATTERN.test(raw)) {
      this.checkPayNumberMsg = "金额格式不正确，最多支持两位小数"
      return false
    }
    const amount = parseFloat(raw)
    if (!Number.isFinite(amount) || amount <= 0) {
      this.checkPayNumberMsg = "充值金额必须大于 0 元"
      return false
    }
    if (amount > MAX_RECHARGE_AMOUNT) {
      this.checkPayNumberMsg = `单笔充值金额不能超过 ${MAX_RECHARGE_AMOUNT} 元`
      return false
    }
    // parseFloat 保留小数精度，替代原 parseInt 截断（评审 #19）
    this.payNum = amount
    return true
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
          Text(this.checkPayNumberMsg)
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
          this.PayTypeCard(PaymentMethod.WECHAT, "微信支付", $r('app.media.wechat'))

          Divider()
            .color(DesignColor.DIVIDER)
            .strokeWidth(0.5)
            .width("85%")

          this.PayTypeCard(PaymentMethod.ALIPAY, "支付宝", $r('app.media.alipay'))
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
                  // 充值渠道类型码用具名常量，替代魔法值（评审 #30）：1=微信 2=支付宝
                  rechargeType: this.payType == PaymentMethod.WECHAT ?
                    RechargeType.WECHAT : RechargeType.ALIPAY
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
