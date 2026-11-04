import { BusinessError } from '@kit.BasicServicesKit'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { RouteNames } from "../constant/RouteNames"
import { PaymentMethod } from "../constant/PaymentMethod"
import { RechargeType } from "../constant/RechargeType"
import { userRecharge } from "../network/api/ReChargeApi"
import { getUserInfo } from "../network/api/UserApi"
import { HttpStatusCode } from "@ohos/axios"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { bumpUserInfoRefresh } from "../utils/RefreshSignal"
import { AppButton, AppConfirmDialog, IconBox, SelectionDot } from "../view/common/AppComponents"

// 单笔充值金额上限：与后端 RechargeRecordsServiceImpl.MAX_RECHARGE_AMOUNT 保持一致（评审 #19）
const MAX_RECHARGE_AMOUNT: number = 10000
// 金额格式：正整数或最多两位小数，与后端 scale<=2 校验一致（评审 #19）
const AMOUNT_PATTERN: RegExp = /^\d+(\.\d{1,2})?$/
// 自定义输入白名单（对齐安卓 CustomAmountInput 的 Regex("^\\d*\\.?\\d{0,2}$")）：
// 非法字符即时拦截不落地；最终合法性仍由提交时 AMOUNT_PATTERN 兜底
const CUSTOM_INPUT_PATTERN: RegExp = /^\d*\.?\d{0,2}$/
// 选中态描边 1.5vp（对齐安卓 BorderStroke(1.5.dp)；未选中态仍为规范 §3.1 的 1px）
const SELECTED_BORDER_WIDTH: number = 1.5
// 金额格间距 10vp（对齐安卓设计稿 grid gap 10，非 8pt 间距令牌档）与整网高度 72×2+10
const AMOUNT_GRID_GAP: number = 10
const AMOUNT_GRID_HEIGHT: number = 154
// 自定义输入框焦点 key（focusControl.requestFocus 目标）
const CUSTOM_INPUT_KEY: string = "rechargeCustomAmountInput"

@Builder
export function RechargeBuilder() {
  Recharge()
}

@Component
struct Recharge {
  @State payType: PaymentMethod | null = null
  @State payNum: number = 0
  private presetAmount: number[] = [10, 20, 50, 100, 200]
  // 选中模型对齐安卓 RechargePage：selectedAmount（预设档）+ isCustomSelected（自定义档）+ customAmount（原始输入）
  @State selectedAmount: number | null = 10
  @State isCustomSelected: boolean = false
  @State customAmount: string = ""
  @State checkPayType: boolean = false
  @State checkPayNumber: boolean = false
  // 金额校验失败的具体文案（评审 #19：错误提示友好化）
  @State checkPayNumberMsg: string = "请选择或输入充值金额"
  // 充值请求进行中标记：按钮 loading 并拦截连点（评审 #12）
  @State isRecharging: boolean = false
  // 账户余额（安卓页顶直出，无卡片；取数失败按 0 兜底，见裁决 #5）
  @State balance: number = 0
  // 按压反馈（对齐安卓 pressable scale 0.97；-1 = 无按压）
  @State pressedKey: number = -1

  async aboutToAppear(): Promise<void> {
    logger.debug('recharge', '充值页挂载，拉取账户余额')
    try {
      let res = await getUserInfo()
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        this.balance = res.data!.balance ?? 0
      } else {
        // 降级留痕：余额拉取失败按 0 兜底展示（不打断充值主流程）
        logger.warn('recharge', `余额加载失败，按 0 兜底展示 code=${res.code} message=${res.message ?? ""}`)
      }
    } catch (err) {
      // 余额仅作展示，取数失败不打断充值主流程（对齐安卓 balance ?: 0f）；降级补 warn 留痕（仅摘要，不序列化 error）
      const e: BusinessError = err as BusinessError
      logger.warn('recharge', `余额加载异常，按 0 兜底展示 err=${e.message}`)
    }
  }

  aboutToDisappear(): void {
    // 自增 tick（问题3）：布尔标记重复 setOrCreate(true) 不产生变更事件，首页 @Watch 收不到
    bumpUserInfoRefresh()
  }

  // 预设档是否选中（自定义档选中时预设档一律不高亮，对齐安卓 isCustomAmountSelected 互斥）
  private isPresetSelected(index: number): boolean {
    return !this.isCustomSelected && this.selectedAmount === this.presetAmount[index]
  }

  // 套餐赠送金额（与 presetAmount 同源硬编码；50 送 2 / 100 送 8 / 200 送 20）
  private bonusOf(amount: number): number {
    if (amount === 50) {
      return 2
    }
    if (amount === 100) {
      return 8
    }
    if (amount === 200) {
      return 20
    }
    return 0
  }

  private clearKeyboardFocus(): void {
    this.getUIContext().getFocusController().clearFocus()
  }

  @Builder
  AmountCell(amount: number, index: number) {
    Column() {
      Text(`¥${amount}`)
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(this.isPresetSelected(index) ? DesignColor.PRIMARY_DARK : DesignColor.TEXT_PRIMARY)
        .fontFeature(DesignFontFeature.TNUM)

      // 赠额随档常驻（对齐安卓 AmountGridCell：不只在选中时出现）
      if (this.bonusOf(amount) > 0) {
        Text(`送 ¥${this.bonusOf(amount)}`)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.ONGOING_INK)
          .fontFeature(DesignFontFeature.TNUM)
          .margin({ top: 2 })
      }
    }
    .height(72)
    .width("100%")
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor(this.isPresetSelected(index) ? DesignColor.PRIMARY_SOFT : DesignColor.SURFACE)
    .borderRadius(DesignDimens.BUTTON_RADIUS)
    .border({
      width: this.isPresetSelected(index) ? SELECTED_BORDER_WIDTH : 1,
      color: this.isPresetSelected(index) ? DesignColor.PRIMARY : DesignColor.OUTLINE,
      radius: DesignDimens.BUTTON_RADIUS
    })
    .scale({ x: this.pressedKey === index ? 0.97 : 1, y: this.pressedKey === index ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = index
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = -1
      }
    })
    .onClick(() => {
      this.selectedAmount = amount
      this.isCustomSelected = false
      this.checkPayNumber = false
      this.clearKeyboardFocus()
    })
  }

  @Builder
  CustomAmountCell() {
    Column() {
      Text("自定义")
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(this.isCustomSelected ? DesignColor.PRIMARY_DARK : DesignColor.TEXT_SECONDARY)
    }
    .height(72)
    .width("100%")
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor(this.isCustomSelected ? DesignColor.PRIMARY_SOFT : DesignColor.SURFACE)
    .borderRadius(DesignDimens.BUTTON_RADIUS)
    .border({
      width: this.isCustomSelected ? SELECTED_BORDER_WIDTH : 1,
      color: this.isCustomSelected ? DesignColor.PRIMARY : DesignColor.OUTLINE,
      radius: DesignDimens.BUTTON_RADIUS
    })
    .scale({ x: this.pressedKey === 5 ? 0.97 : 1, y: this.pressedKey === 5 ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = 5
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = -1
      }
    })
    .onClick(() => {
      this.isCustomSelected = true
      this.checkPayNumber = false
      focusControl.requestFocus(CUSTOM_INPUT_KEY)
    })
  }

  // 动态到账说明的赠额：仅预设档生效（对齐安卓 RechargeSummaryLine：isCustom 时 bonus = 0，
  // 否则自定义输入 50 会被误加预设档赠额 2）
  private summaryBonus(): number {
    if (this.isCustomSelected || this.selectedAmount === null) {
      return 0
    }
    return this.bonusOf(this.selectedAmount)
  }

  // 动态到账说明（对齐安卓 RechargeSummaryLine）：按当前选中档展示，未选/清空不渲染
  @Builder
  SummaryLine() {
    if (this.selectedAmount !== null && this.selectedAmount > 0) {
      Text() {
        Span(`充值 ¥${this.selectedAmount.toFixed(2)} · 到账 `)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)

        Span(`¥${(this.selectedAmount + this.summaryBonus()).toFixed(2)}`)
          .fontSize(DesignFontSize.CAPTION)
          // CAPTION（12）属 font.caption 档，规范 weight=400/500；原 BOLD(700) 属滥用，收敛
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.PRIMARY_DARK)

        if (this.summaryBonus() > 0) {
          Span(`（含赠送 ¥${this.summaryBonus().toFixed(2)}）`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
        }
      }
      .fontFeature(DesignFontFeature.TNUM)
      .margin({ top: 12 })
    }
  }

  @Builder
  CustomAmountInput() {
    Column() {
      TextInput({ text: $$this.customAmount, placeholder: "输入自定义金额" })
        .width("100%")
        .backgroundColor(Color.Transparent)
        .type(InputType.NUMBER_DECIMAL)
        .maxLength(8) // 8 位可完整输入上限金额 "10000.00"（含两位小数，评审 #19）
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .fontFeature(DesignFontFeature.TNUM)
        .placeholderColor(DesignColor.TEXT_SECONDARY)
        .placeholderFont({ size: DesignFontSize.BODY })
        .key(CUSTOM_INPUT_KEY)
        .onFocus(() => {
          this.isCustomSelected = true
        })
        .onChange((value: string) => {
          // 白名单即时过滤（对齐安卓 CustomAmountInput）：非法字符不落地
          if (value === "" || CUSTOM_INPUT_PATTERN.test(value)) {
            this.customAmount = value
            if (value !== "") {
              const parsed: number = parseFloat(value)
              this.selectedAmount = Number.isFinite(parsed) ? parsed : null
              this.isCustomSelected = true
            } else {
              // 清空即清选中态（对齐安卓缺陷 8c）
              this.selectedAmount = null
              this.isCustomSelected = false
            }
            this.checkPayNumber = false
          }
        })
    }
    .width("100%")
    .height(48)
    .padding({ left: 12, right: 12 })
    .justifyContent(FlexAlign.Center)
    .backgroundColor(this.isCustomSelected ? DesignColor.PRIMARY_SOFT : DesignColor.SURFACE_VARIANT)
    .borderRadius(DesignDimens.INPUT_RADIUS)
    .border({
      width: 1,
      color: this.isCustomSelected ? DesignColor.PRIMARY : DesignColor.SURFACE_VARIANT,
      radius: DesignDimens.INPUT_RADIUS
    })
    .margin({ top: 8 })
  }

  @Builder
  PayTypeCard(method: PaymentMethod, title: string, icon: Resource, index: number) {
    Row() {
      // 品牌图标容器（对齐安卓 PaymentMethodCard：36 容器 / 12 圆角 / 20 图标 + 六色浅底）
      Row() {
        Image(icon)
          .width(20)
          .height(20)
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(method === PaymentMethod.WECHAT ?
        DesignColor.ICON_CONTAINER_GREEN : DesignColor.ICON_CONTAINER_BLUE)
      .justifyContent(FlexAlign.Center)
      .alignItems(VerticalAlign.Center)

      Text(title)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .margin({ left: 12 })
        .layoutWeight(1)

      SelectionDot({ selected: this.payType === method, dotSize: 24 })
    }
    .width("100%")
    .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING, top: 15, bottom: 15 })
    .alignItems(VerticalAlign.Center)
    .scale({ x: this.pressedKey === index ? 0.97 : 1, y: this.pressedKey === index ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = index
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = -1
      }
    })
    .onClick(() => {
      this.payType = method
      this.checkPayType = false
    })
  }

  // 充值确认弹窗（对齐安卓 AppConfirmDialog，替代系统 promptAction 弹窗）
  private confirmDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: "提示",
      message: "是否确认充值？",
      onConfirm: () => {
        this.doRecharge()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  // 提交按钮文案（对齐安卓「立即充值 ¥xx.00」，未选金额时不拼金额）
  private submitText(): string {
    return this.selectedAmount === null ? "立即充值" : `立即充值 ¥${this.selectedAmount.toFixed(2)}`
  }

  check() {
    // 金额精确校验（评审 #19）：不合法时 checkPayNumberMsg 已写入具体原因
    this.checkPayNumber = !this.validateAmount()
    this.checkPayType = this.payType === null
  }

  /**
   * 充值金额精确校验（评审 #19）：必须为正数、最多两位小数、不超过单笔上限 10000（与后端一致）。
   * 校验顺序与文案逐条对齐安卓 RechargeAmountValidator.resolve/validateCustomAmount。
   */
  validateAmount(): boolean {
    if (this.isCustomSelected) {
      const raw = this.customAmount.trim()
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
    if (this.selectedAmount === null) {
      this.checkPayNumberMsg = "请选择或输入充值金额"
      return false
    }
    this.payNum = this.selectedAmount
    return true
  }

  /**
   * 提交充值（对齐安卓 RechargePage.kt:365-385：确认弹窗 onConfirm 内先校验，
   * 未选支付方式 / 金额非法则提示且不提交）。
   */
  async doRecharge() {
    this.check()
    if (this.checkPayType || this.checkPayNumber) {
      // 业务拒绝：提交校验未通过（未选支付方式/金额非法），仅记校验结论摘要
      logger.warn('recharge', `充值提交校验未通过 ` +
        `payType=${this.payType === null ? "未选" : "已选"} reason=${this.checkPayNumberMsg}`)
      return
    }
    // 防连点：请求进行中直接忽略
    if (this.isRecharging) {
      logger.debug('recharge', '充值请求进行中，忽略重复触发')
      return
    }
    this.isRecharging = true
    try {
      // 仅记金额与渠道摘要，不打完整请求体（敏感红线）
      logger.debug('recharge', `发起充值 amount=${this.payNum.toFixed(2)} rechargeType=${this.payType === PaymentMethod.WECHAT ? RechargeType.WECHAT : RechargeType.ALIPAY}`)
      let res = await userRecharge({
        amount: this.payNum,
        // 充值渠道类型码用具名常量，替代魔法值（评审 #30）：1=微信 2=支付宝
        rechargeType: this.payType === PaymentMethod.WECHAT ?
          RechargeType.WECHAT : RechargeType.ALIPAY
      })
      if (res.code === HttpStatusCode.Ok) {
        // 低频关键动作成功：充值完成留 info
        logger.info('recharge', `充值成功 amount=${this.payNum.toFixed(2)}`)
        pathStack.pop()
        showToastSafety("充值成功")
      } else {
        logger.warn('recharge', `充值被拒绝 amount=${this.payNum.toFixed(2)} ` +
          `code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "充值失败")
      }
    } finally {
      this.isRecharging = false
    }
  }

  build() {
    NavDestination() {
      Column() {
        // 页头（自绘：公共 PageHeader 无 actions 插槽，历史入口需自定义；
        // 规格对齐安卓 PageHeader——22/600 标题 + 两侧 48dp 热区）
        Row() {
          Column() {
            SymbolGlyph($r('sys.symbol.chevron_left'))
              .fontSize(24)
              .fontColor([DesignColor.ON_BACKGROUND])
          }
          .width(DesignDimens.BACK_HIT_AREA)
          .height(DesignDimens.BACK_HIT_AREA)
          .justifyContent(FlexAlign.Center)
          .alignItems(HorizontalAlign.Center)
          .onClick(() => {
            pathStack.pop()
          })

          Text("充值")
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.ON_BACKGROUND)

          Blank()

          // 充值记录入口：48dp 热区 + IconBox 36/12/20 青容器（对齐安卓 RechargePage.kt:139-156）
          Column() {
            IconBox({
              icon: $r('sys.symbol.clock_arrow_2_circlepath'),
              sizeValue: 36,
              iconSize: 20,
              containerColor: DesignColor.ICON_CONTAINER_TEAL,
              iconColor: DesignColor.ICON_FOREGROUND_TEAL
            })
          }
          .width(DesignDimens.BACK_HIT_AREA)
          .height(DesignDimens.BACK_HIT_AREA)
          .justifyContent(FlexAlign.Center)
          .alignItems(HorizontalAlign.Center)
          .onClick(() => {
            pathStack.pushPathByName(RouteNames.RECHARGE_RECORD, null)
          })
        }
        .width("100%")
        .height(56)
        // 行首让位 12：48dp 热区盒居中后图形左缘落在 24（对齐安卓 PageHeader）
        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING })
        .alignItems(VerticalAlign.Center)

        // 内容区（安卓 LazyColumn，此处 Scroll 承载，小屏可滚）
        Scroll() {
          Column({ space: DesignDimens.SPACE_XL }) {
            // 账户余额（安卓页顶直出，无卡片；bodySmall 12/400 + DataLarge 36 primaryDark）
            Column() {
              Text("账户余额")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)

              Text(`¥${this.balance.toFixed(2)}`)
                .fontSize(DesignFontSize.DATA_LARGE)
                .fontWeight(DesignFontWeight.BOLD)
                .fontColor(DesignColor.PRIMARY_DARK)
                .fontFeature(DesignFontFeature.TNUM)
                .margin({ top: 4 })
            }
            .width("100%")
            .alignItems(HorizontalAlign.Start)

            // 充值金额（卡壳 + 3×2 网格 + 动态到账 + 独立输入框）
            Column() {
              Text("充值金额")
                .fontSize(DesignFontSize.SECTION_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .margin({ bottom: 12 })

              Column() {
                Grid() {
                  ForEach(this.presetAmount, (item: number, index: number) => {
                    GridItem() {
                      this.AmountCell(item, index)
                    }
                  }, (item: number, index: number) => index.toString())

                  GridItem() {
                    this.CustomAmountCell()
                  }
                }
                .columnsTemplate('1fr 1fr 1fr')
                .rowsTemplate('1fr 1fr')
                .columnsGap(AMOUNT_GRID_GAP)
                .rowsGap(AMOUNT_GRID_GAP)
                .height(AMOUNT_GRID_HEIGHT)

                this.SummaryLine()

                Text("自定义金额")
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(DesignColor.TEXT_SECONDARY)
                  .margin({ top: 8 })

                this.CustomAmountInput()
              }
              .width("100%")
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
              .border({ width: 1, color: DesignColor.OUTLINE, radius: DesignDimens.CARD_RADIUS })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: 2
              })
              if (this.checkPayNumber) {
                Text(this.checkPayNumberMsg)
                  .fontColor(DesignColor.ERROR)
                  .fontSize(DesignFontSize.CAPTION)
                  .margin({ top: 8 })
                  .alignSelf(ItemAlign.Start)
                  .transition(TransitionEffect.OPACITY)
                  .animation({ duration: 200, curve: Curve.EaseOut })
              }
            }
            .width("100%")
            .alignItems(HorizontalAlign.Start)

            // 支付方式
            Column() {
              Text("支付方式")
                .fontSize(DesignFontSize.SECTION_TITLE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .margin({ bottom: 12 })

              Column() {
                this.PayTypeCard(PaymentMethod.WECHAT, "微信支付", $r('app.media.wechat'), 0)

                Divider()
                  .color(DesignColor.HAIRLINE)
                  .strokeWidth(0.5)
                  .padding({ left: 68, right: 20 })

                this.PayTypeCard(PaymentMethod.ALIPAY, "支付宝", $r('app.media.alipay'), 1)
              }
              .width("100%")
              .backgroundColor(DesignColor.SURFACE)
              .borderRadius(DesignDimens.CARD_RADIUS)
              // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
              .border({ width: 1, color: DesignColor.OUTLINE, radius: DesignDimens.CARD_RADIUS })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: DesignColor.SHADOW_COLOR_CARD,
                offsetX: 0,
                offsetY: 2
              })
              if (this.checkPayType) {
                Text("请选择充值方式")
                  .fontColor(DesignColor.ERROR)
                  .fontSize(DesignFontSize.CAPTION)
                  .margin({ top: 8 })
                  .alignSelf(ItemAlign.Start)
                  .transition(TransitionEffect.OPACITY)
                  .animation({ duration: 200, curve: Curve.EaseOut })
              }
            }
            .width("100%")
            .alignItems(HorizontalAlign.Start)
          }
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        }
        .width("100%")
        .layoutWeight(1)
        // 内容不足一屏顶对齐（Scroll 默认居中）
        .align(Alignment.Top)

        // 底部固定 CTA 条（对齐安卓 RechargePage.kt:344-361：surface 底 + 0.5 顶线 + 24/16/20）
        Column() {
          Divider()
            .color(DesignColor.OUTLINE)
            .strokeWidth(0.5)

          AppButton({
            text: this.submitText(),
            loading: this.isRecharging,
            click: () => {
              this.confirmDialogController.open()
            }
          })
          .margin({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 16, bottom: 20 + safeBottomVp() })
        }
        .width("100%")
        .backgroundColor(DesignColor.SURFACE)
      }
      .width("100%")
      .height("100%")
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
    }
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor(DesignColor.BACKGROUND)
    .hideTitleBar(true)
  }
}
