import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { generateQrCode, getOrderInfo, pickupOrder, shippingOrder } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getObjectParam, pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { AppButton, AppButtonVariant, AppConfirmDialog, EmptyState, IconBox, LoadingState, PageHeader } from "../view/common/AppComponents"
import { BaseResponse } from "../network/BaseResponse"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { logger } from "../utils/Logger"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { cancelPickupReminderForOrder } from "../utils/ReminderService"
import vibrator from "@ohos.vibrator"
import { ThemeRefresh } from '../view/common/ThemeRefresh'

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
  // 详情三态（recap §9-F1/S2）：加载中骨架 / 失败空态 + 重试 / 内容
  @State isLoading: boolean = true
  @State loadFailed: boolean = false
  // 二维码三态：加载中占位 / 成功渲染 / 失败兜底可重试（D7：保留后端 generateQrCode，补占位与失败兜底）
  @State qrCode: string = ""
  @State qrLoading: boolean = true
  @State qrFailed: boolean = false
  @State pickCode: string = ""
  // 确认弹窗文案（按类型区分，open 前赋值）
  @State confirmMessage: string = ""
  // 确认操作请求中标记：防确认弹窗重复提交 + 主按钮 loading（recap §9-F5）
  @State confirming: boolean = false
  @State submitting: boolean = false
  // 底部操作栏按压态（recap §9-I1，对齐安卓 pressable scale 0.97 + 100ms）
  @State contactPressed: boolean = false
  @State primaryPressed: boolean = false
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
   * 页面初始化（recap §9-F1）：读显式类型参数 → 重拉订单详情 → 计算取件码 → 生成二维码。
   * 三态门控：请求中展示骨架，失败展示空态 + 重试，成功渲染内容。
   */
  async initPage() {
    const param = getObjectParam<PickupDeliveryParam>(RouteNames.PICKUP_DELIVERY)
    this.isPickup = param?.isPickup ?? false
    const orderId = param?.order?.orderId ?? null
    logger.debug('pickupDelivery', `取寄件页初始化 isPickup=${this.isPickup} orderId=${orderId}`)
    if (orderId !== null) {
      this.isLoading = true
      this.loadFailed = false
      try {
        let detailRes = await getOrderInfo(orderId)
        if (detailRes.code === HttpStatusCode.Ok && detailRes.data !== null) {
          logger.debug('pickupDelivery', `订单详情加载成功 orderId=${orderId}`)
          this.orderInfo = detailRes.data
          this.loadFailed = false
        } else {
          logger.warn('pickupDelivery', `订单详情加载失败 orderId=${orderId} ` +
            `code=${detailRes.code} message=${detailRes.message ?? ""}`)
          this.orderInfo = null
          this.loadFailed = true
          showToastSafety(detailRes.message, "订单信息加载失败")
        }
      } catch (err) {
        // 网络异常/401 拒绝时 promise 会 throw：兜住并进失败态，避免 isLoading 卡死永久骨架
        this.orderInfo = null
        this.loadFailed = true
        showToastSafety("网络异常，请稍后重试")
      } finally {
        this.isLoading = false
      }
    } else {
      logger.warn('pickupDelivery', '取寄件页缺少路由参数 orderId，进入失败态')
      this.orderInfo = null
      this.loadFailed = true
      this.isLoading = false
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
      // 无码将导致无法扫码取寄件（空态预警）；码值不入日志（敏感红线）
      logger.warn('pickupDelivery', '取件码为空，跳过二维码生成')
      this.qrFailed = true
      this.qrLoading = false
      return
    }
    this.qrLoading = true
    this.qrFailed = false
    try {
      let res = await generateQrCode(this.pickCode)
      if (res.code === HttpStatusCode.Ok && (res.data ?? "") !== "") {
        logger.debug('pickupDelivery', '二维码生成成功')
        this.qrCode = res.data ?? ""
      } else {
        logger.warn('pickupDelivery', `二维码生成失败 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "取件码生成失败")
        this.qrFailed = true
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并进失败态，避免 qrLoading 卡死骨架常驻
      showToastSafety("网络异常，请稍后重试")
      this.qrFailed = true
    } finally {
      this.qrLoading = false
    }
  }

  /** 确认取件/寄件：pickupCode 传完整串（与安卓一致），日志不打码值（敏感红线） */
  async doConfirm() {
    if (this.confirming) {
      logger.debug('pickupDelivery', '确认操作进行中，忽略重复触发')
      return
    }
    this.confirming = true
    this.submitting = true
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
      // 后端 Result.ok(boolean) 直传：data=false（CAS 失败，订单状态已变更）必须按失败处理，
      // 不能只看 code=200 就弹「操作成功」（对齐 AddSchool 对 getUserByStudentId 的判法）
      if (res.code === HttpStatusCode.Ok && res.data === true) {
        logger.info('pickupDelivery',
          `${this.isPickup ? "取件" : "寄件"}确认成功 orderId=${this.orderInfo?.orderId ?? -1}`)
        // 取件成功：撤销该订单的取件提醒（订单转已完成，提醒已无意义；
        // fire-and-forget，撤销内部全量容错不影响确认主流程）
        if (this.isPickup) {
          const pickedOrderId = this.orderInfo?.orderId
          if (pickedOrderId !== null && pickedOrderId !== undefined) {
            cancelPickupReminderForOrder(pickedOrderId)
          }
        }
        // 确认成功触感（recap §9-F4，对齐安卓 HapticEffect.SUCCESS）
        try {
          vibrator.startVibration({ type: 'time', duration: 30 }, { id: 0, usage: 'unknown' })
        } catch (err) {
          // 忽略不支持的设备
        }
        showToastSafety("操作成功")
        pathStack.pop()
      } else {
        showToastSafety(res.message, "操作失败，订单状态可能已变更")
        logger.warn('pickupDelivery',
          `${this.isPickup ? "取件" : "寄件"}确认被拒 orderId=${this.orderInfo?.orderId ?? -1} ` +
          `code=${res.code} message=${res.message ?? ""}`)
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并提示，避免 confirming/submitting 卡死
      showToastSafety("网络异常，请稍后重试")
    } finally {
      this.confirming = false
      this.submitting = false
    }
  }

  /**
   * 指引步骤行（recap §9-V3）：
   * 序号盒 20dp 圆形 iconContainerGreen + 序号 12/400 onPrimaryContainer；盒-文 8；文本 12/400 textPrimary。
   */
  @Builder
  InstructionRow(i: string, content: string) {
    Row() {
      Column() {
        Text(`${i}`)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.ON_PRIMARY_CONTAINER)
          .fontWeight(DesignFontWeight.NORMAL)
      }
      .width(20)
      .height(20)
      .borderRadius(10)
      .justifyContent(FlexAlign.Center)
      .backgroundColor(DesignColor.ICON_CONTAINER_GREEN)
      .margin({ right: DesignDimens.SPACE_XS })

      Text(content)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .layoutWeight(1)
    }.width("100%")
    .alignItems(VerticalAlign.Center)
  }

  /** 二维码区（recap §9-V5）：80dp 成图 / 80dp 占位，成图交叉淡变（recap §9-I2） */
  @Builder
  QrCodeArea() {
    Stack() {
      if (this.qrCode !== "") {
        // 二维码 80dp（对齐安卓 PickupDeliveryPage.kt:296 .size(80.dp)）
        Image(`data:image/png;base64,${this.qrCode}`)
          .size({ width: 80, height: 80 })
          .borderRadius(DesignDimens.CARD_RADIUS)
          .transition(TransitionEffect.OPACITY)
      } else {
        // 占位/失败兜底（D7）：返回前不再空白，失败可点击重试
        Column() {
          if (this.qrLoading) {
            LoadingProgress()
              .width(28)
              .height(28)
              .color(DesignColor.PRIMARY)
            Text("加载中…")
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .margin({ top: 8 })
          } else {
            Text(this.qrFailed ? "二维码生成失败" : "暂无二维码")
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor(DesignColor.TEXT_SECONDARY)
            if (this.qrFailed) {
              Text("点击重试")
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.PRIMARY)
                .margin({ top: 8 })
            }
          }
        }
        .size({ width: 80, height: 80 })
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor(DesignColor.SURFACE)
        .borderRadius(DesignDimens.CARD_RADIUS)
        .transition(TransitionEffect.OPACITY)
        .onClick(() => {
          if (!this.qrLoading && this.qrFailed) {
            this.loadQrCode()
          }
        })
      }
    }
    .width(80)
    .height(80)
  }

  /**
   * 寄件注意事项卡（recap §9-V1 新形态）：
   * IconBox 36/20 橙容器（warning 语义）+ 标题 titleSmall 14/500 + 内容 caption 12/400。
   */
  @Builder
  NotesCard() {
    Column() {
      Row() {
        IconBox({
          icon: $r('sys.symbol.exclamationmark_triangle_fill'),
          sizeValue: 36,
          iconSize: 20,
          containerColor: DesignColor.ICON_CONTAINER_ORANGE,
          iconColor: DesignColor.ICON_FOREGROUND_ORANGE
        })
        Column() {
          // 标题 titleSmall 14/500（BODY_SMALL + MEDIUM）；正文 bodySmall 12/400 无顶部间距
          Text("注意事项")
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(DesignColor.ON_WARNING_CONTAINER)
          Text("• 请确保在30分钟内完成操作\n" +
            "• 如遇问题请联系客服\n" +
            "• 请勿将贵重物品放入寄存柜")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.ON_WARNING_CONTAINER)
            .lineHeight(16)
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        // 图标与文字间距 12（对齐安卓 InfoSection 的 Spacer(12)）
        .margin({ left: DesignDimens.SPACE_SM })
      }
      .width("100%")
      .alignItems(VerticalAlign.Top)
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.WARNING_CONTAINER)
    .borderRadius(DesignDimens.CARD_RADIUS)
    // DESIGN-SPEC v0.4 §3.1 / §2.4 唯一卡片画法：1px outline 描边 + level1 阴影
    .border({ width: 1, color: DesignColor.OUTLINE })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
    .alignItems(HorizontalAlign.Start)
  }

  /** 二维码卡（recap §9-V2/V5）：80dp 码图 + 18/600 标题（textPrimary，无字距）+ DataLarge 36 tnum 码 */
  @Builder
  QrCard() {
    Column() {
      this.QrCodeArea()

      Text(`${this.isPickup ? "取件码" : "寄件码"}`)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontSize(DesignFontSize.SECTION_TITLE)
        .fontColor(DesignColor.TEXT_PRIMARY)
        // 字距 0（对齐安卓 U-116：v0.4 字阶已废字距，原 0.3 已删）
        .margin({ top: 20 })

      Text(`${this.pickCode}`)
        .fontSize(DesignFontSize.DATA_LARGE)
        // DATA_LARGE（36）属 font.display 档（DESIGN-SPEC §2：寄件码 700 + tnum），字重保持 BOLD；
        // 由裸 FontWeight 枚举收敛为设计令牌，与全站一致
        .fontWeight(DesignFontWeight.BOLD)
        .fontColor(DesignColor.PRIMARY_DARK)
        .fontFeature(DesignFontFeature.TNUM)
        .maxLines(1)
    }
    .width("100%")
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
    .alignItems(HorizontalAlign.Center)
  }

  /**
   * 寄存柜信息行（对齐安卓 InfoRow）：标签 12/400 textSecondary，值 12/400 textPrimary，行高 56（§3.4）。
   * 仅柜号行开 tnum（编号类数字，DESIGN.md 数字等宽规则）；OrderItemRow 固定 14/13/Medium 且恒 tnum，
   * 不满足按行切换，故页内自绘（不改动 view/OrderItemRow.ets）。
   */
  @Builder
  LockerInfoRow(title: string, content: string, valueTnum: boolean) {
    Row() {
      Text(title)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.NORMAL)
        .fontColor(DesignColor.TEXT_SECONDARY)

      Text(content)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.NORMAL)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .fontFeature(valueTnum ? DesignFontFeature.TNUM : 'normal')
    }
    .width("100%")
    .height(56)
    .justifyContent(FlexAlign.SpaceBetween)
    .alignItems(VerticalAlign.Center)
  }

  /** 寄存柜信息卡（recap §9-F6/V6）：IconBox 36/20 + 3 行（学校/地址/柜号），码已在上方大字展示不再重复 */
  @Builder
  LockerInfoCard() {
    Column() {
      Row() {
        IconBox({
          icon: $r('sys.symbol.company'),
          sizeValue: 36,
          iconSize: 20,
          containerColor: DesignColor.PRIMARY_LIGHT,
          iconColor: DesignColor.PRIMARY
        })
        Text("寄存柜信息")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          // 图标与标题间距 12（对齐安卓 InfoSection 的 Spacer(12)）
          .margin({ left: DesignDimens.SPACE_SM })
      }
      .justifyContent(FlexAlign.Start)
      .width("100%")
      .margin({ bottom: 4 })

      Column({ space: 14 }) {
        this.LockerInfoRow("学校名称", `${this.orderInfo?.schoolsVo?.schoolName ?? ""}`, false)
        this.LockerInfoRow("地址", `${this.orderInfo?.schoolsVo?.location ?? ""}`, false)
        // 柜号是编号类数字，开 tnum（对齐安卓 InfoRow valueStyle = bodySmall.copy(tnum)）
        this.LockerInfoRow("寄存柜柜号", `${this.orderInfo?.lockersVo?.lockerNumber ?? ""}`, true)
      }
      .width("100%")
    }
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
  }

  /** 操作指引卡（recap §9-V3/V4）：标题 14/500 无图标 + 4 步（20dp 圆形序号盒 + 12/400 文本） */
  @Builder
  GuideCard() {
    Column() {
      Row() {
        Text("操作指引")
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
      }
      .justifyContent(FlexAlign.Start)
      .width("100%")
      .margin({ bottom: 4 })

      Column({ space: 4 }) {
        this.InstructionRow("1", `找到寄存柜 ${this.orderInfo?.lockersVo?.lockerNumber ?? ""}`)
        this.InstructionRow("2", `点击屏幕上的" ${this.isPickup ? "取件" : "寄件"} "按钮`)
        this.InstructionRow("3", `输入${this.isPickup ? "取件码" : "寄件码"}：${this.pickCode}`)
        this.InstructionRow("4", this.isPickup ? "等待柜门打开，取出衣物" : "等待柜门打开，放入衣物")
      }
      .width("100%")
    }
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
  }

  /**
   * 底部操作栏（recap §9-F3/F5）— 对齐安卓 PickupDeliveryActionBar：
   * 次按钮（联系客服）透明底 + 1dp outline + primaryDark 字；主按钮纯色 primary + onPrimary 字；
   * 双 48dp 胶囊、间距 12、底部留白 16，固定页面底部；请求在途主按钮转菊花并不再响应点击。
   */
  @Builder
  BottomActionBar() {
    Row() {
      // 次按钮 — 联系客服：透明底 + 1dp outline 描边
      Column() {
        // 文字 titleMedium 18/600（对齐安卓 PickupDeliveryActionBar：MaterialTheme.titleMedium）
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

      // 主按钮 — 确认已取件/确认已寄件：请求在途转菊花且不再响应点击
      Column() {
        if (this.submitting) {
          LoadingProgress()
            .width(20)
            .height(20)
            .color(DesignColor.ON_PRIMARY)
        } else {
          // 文字 titleMedium 18/600（对齐安卓 PickupDeliveryActionBar：MaterialTheme.titleMedium）
          Text(this.isPickup ? "确认已取件" : "确认已寄件")
            .fontSize(DesignFontSize.AMOUNT_MEDIUM)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.ON_PRIMARY)
        }
      }
      .layoutWeight(1)
      .height(48)
      .justifyContent(FlexAlign.Center)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .backgroundColor(DesignColor.PRIMARY)
      .margin({ left: DesignDimens.SPACE_SM })
      .scale({ x: this.primaryPressed ? 0.97 : 1, y: this.primaryPressed ? 0.97 : 1 })
      .animation({ duration: 100, curve: Curve.EaseOut })
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.primaryPressed = true
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.primaryPressed = false
        }
      })
      .onClick(() => {
        if (this.submitting) {
          return
        }
        // 确认弹窗文案按类型区分（recap §9-T1：对齐安卓「衣服」）
        this.confirmMessage = this.isPickup ? "确认已取出衣服" : "确认已将衣服放入寄存柜"
        this.confirmDialogController.open()
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
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 页头（recap G4：22/600 + 48 返回热区，复用公共 PageHeader）
        PageHeader({ title: this.isPickup ? "取件" : "寄件" })

        // 页头下 8dp 间距（对齐安卓 PickupDeliveryPage 的 Spacer(8)）
        Blank().height(DesignDimens.SPACE_XS)

        // 内容区三态（recap §9-F1/S2）
        if (this.isLoading) {
          // 首载骨架（对齐安卓 LoadingState）
          LoadingState()
            .width("100%")
            .layoutWeight(1)
        } else if (this.loadFailed) {
          // 失败态：EmptyState 母题 + 重试按钮（对齐安卓 ERROR 态）
          Column() {
            EmptyState({
              message: "请求失败",
              action: () => {
                AppButton({
                  text: "重试",
                  variant: AppButtonVariant.SECONDARY,
                  click: () => {
                    this.initPage()
                  }
                })
                  .margin({ top: DesignDimens.SPACE_MD })
              }
            })
          }
          .width("100%")
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          Scroll() {
            Column() {
              // 注意事项（仅寄件模式）
              if (!this.isPickup) {
                this.NotesCard()
                // 注意事项卡后间距 32（对齐安卓 PickupDeliveryPage.kt:268 Spacer(sectionSpacing)）
                Blank().height(DesignDimens.SECTION_SPACING)
              }

              // 二维码卡
              this.QrCard()

              // 卡片间距 16（对齐安卓 cardSpacing）
              Blank().height(DesignDimens.CARD_SPACING)

              // 寄存柜信息
              this.LockerInfoCard()

              Blank().height(DesignDimens.CARD_SPACING)

              // 操作指引
              this.GuideCard()
            }
            .width("100%")
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              bottom: DesignDimens.CARD_SPACING
            })
          }
          .layoutWeight(1)
          .width("100%")
          .align(Alignment.Top)
          .scrollBar(BarState.Off)
        }

        // 底部操作栏（recap §9-F3：固定底部）
        this.BottomActionBar()
      }
      .width("100%")
      .height("100%")
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
  }
}
