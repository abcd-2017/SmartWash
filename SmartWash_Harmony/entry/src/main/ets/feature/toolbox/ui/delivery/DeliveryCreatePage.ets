// 设备投递发起页 — 对齐安卓 feature/toolbox/ui/delivery/DeliveryCreatePage.kt
//
// 两态：表单态（有效期预设 + 可选进入密码 + 隐私明示）→ 会话态
// （投递码数据大字 + enterUrl 二维码 + 剩余倒计时 + 复制链接 / 进入会话 / 关闭会话）。
// 入口：深链 smartwash://delivery（DeepLinkRegistry）与工作台卡片。
//
// 已知坑遵守：
// - 二维码生成放 aboutToAppear 异步，不阻塞首帧；ScanKit 不可用时走纯 ArkTS 兜底（QrFallback）。
// - 倒计时 setInterval 在 aboutToDisappear 清理；关闭确认弹窗控制器由结构体字段持有（17c263c）。

import { generateBarcode, scanCore } from '@kit.ScanKit'
import { image } from '@kit.ImageKit'
import { BusinessError, pasteboard } from '@kit.BasicServicesKit'
import { HttpStatusCode } from '@ohos/axios'
import { RouteNames } from '../../../../constant/RouteNames'
import {
  DesignColor,
  DesignDimens,
  DesignElevation,
  DesignFontSize,
  DesignFontFeature,
  DesignFontWeight
} from '../../../../constant/DesignSystem'
import {
  AddDeliverySessionForm,
  createDeliverySession,
  closeDeliverySession,
  durationChipText,
  clicksFormat,
  DELIVERY_DURATION_PRESETS,
  DeliverySessionVo,
  DeliveryText,
  isValidPassword,
  remainingSeconds
} from '../../DeliveryApi'
import { createQrPixelMap } from '../../QrFallback'
import { DeliveryTagChip } from './DeliveryComponents'
import { AppButton, AppButtonVariant, AppConfirmDialog } from '../../../../view/common/AppComponents'
import { logger } from '../../../../utils/Logger'
import { pathStack } from '../../../../utils/PathStackUtil'
import { showToastSafety } from '../../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { ThemeRefresh } from '../../../../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'deliveryCreate'

@Builder
export function DeliveryCreateBuilder() {
  DeliveryCreatePage()
}

@Component
struct DeliveryCreatePage {
  // ---------- 表单态 ----------
  @State durationMinutes: number = 30
  @State password: string = ''
  @State showPassword: boolean = false
  @State passwordFocused: boolean = false
  @State submitting: boolean = false
  @State errorMessage: string = ''
  // ---------- 会话态 ----------
  @State session: DeliverySessionVo | null = null
  @State closing: boolean = false
  @State remainSeconds: number | null = null
  private tickTimerId: number = -1

  // 关闭会话确认弹窗（控制器由结构体字段持有：本地变量会在按钮回调抛 jscrash，见 17c263c）
  private closeDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: DeliveryText.toolbox_delivery_confirm_close_title,
      message: DeliveryText.toolbox_delivery_confirm_close_message,
      confirmText: DeliveryText.toolbox_delivery_action_close,
      cancelText: '取消',
      isDanger: true,
      onConfirm: () => {
        this.closeSession()
      },
      onDismiss: () => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  aboutToAppear(): void {
    // 会话态由创建成功驱动；倒计时在创建成功后启动
  }

  aboutToDisappear(): void {
    this.clearTickTimer()
    this.closeDialogController.close()
  }

  private clearTickTimer(): void {
    if (this.tickTimerId !== -1) {
      clearInterval(this.tickTimerId)
      this.tickTimerId = -1
    }
  }

  /** 启动本地递减倒计时（每次轮询/创建重新锚定 expireAt） */
  private startTick(): void {
    this.clearTickTimer()
    this.updateRemain()
    this.tickTimerId = setInterval(() => {
      this.updateRemain()
    }, 1000)
  }

  private updateRemain(): void {
    const session = this.session
    if (session === null) {
      this.remainSeconds = null
      return
    }
    this.remainSeconds = remainingSeconds(session.expireAt ?? null)
  }

  /** 创建投递会话；客户端前置校验密码 4-64 位，业务错误内联展示（对齐安卓 createSession） */
  async submit(): Promise<void> {
    if (this.submitting) {
      logger.debug(TAG, '创建进行中，忽略重复触发')
      return
    }
    const password = this.password.trim()
    if (password.length > 0 && !isValidPassword(password)) {
      logger.warn(TAG, '创建校验拒绝: 密码位数不合法')
      this.errorMessage = DeliveryText.toolbox_delivery_error_invalid_password
      return
    }
    const form: AddDeliverySessionForm = {
      durationMinutes: this.durationMinutes,
      password: password.length > 0 ? password : undefined
    }
    this.errorMessage = ''
    this.submitting = true
    try {
      const res = await createDeliverySession(form)
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        logger.info(TAG, `投递会话创建成功 code=${res.data.code ?? ''} duration=${this.durationMinutes}min`)
        this.session = res.data
        this.errorMessage = ''
        this.startTick()
      } else {
        logger.warn(TAG, `创建被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.errorMessage = res.message ?? DeliveryText.toolbox_delivery_error_create
      }
    } catch (err) {
      logger.error(TAG, '创建请求异常', err instanceof Error ? err.message : '')
      this.errorMessage = DeliveryText.toolbox_delivery_error_create
    } finally {
      this.submitting = false
    }
  }

  /** 关闭会话（读路径立即失效）；成功回表单态，可重新发起 */
  async closeSession(): Promise<void> {
    const session = this.session
    if (session === null || this.closing) {
      return
    }
    const code = session.code ?? ''
    if (code.length === 0) {
      return
    }
    this.closing = true
    try {
      const res = await closeDeliverySession(code)
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `投递会话关闭成功 code=${code}`)
        showToastSafety(DeliveryText.toolbox_delivery_toast_close_success)
        this.resetToForm()
      } else {
        logger.warn(TAG, `关闭被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, DeliveryText.toolbox_delivery_error_close)
      }
    } catch (err) {
      logger.error(TAG, '关闭请求异常', err instanceof Error ? err.message : '')
      showToastSafety(DeliveryText.toolbox_delivery_error_close)
    } finally {
      this.closing = false
    }
  }

  /** 回表单态（关闭成功 / 已失效重新发起共用） */
  private resetToForm(): void {
    this.clearTickTimer()
    this.session = null
    this.remainSeconds = null
    this.durationMinutes = 30
    this.password = ''
    this.showPassword = false
    this.errorMessage = ''
  }

  /** 复制对端进入链接（enterUrl） */
  private copyLink(): void {
    const url = this.session?.enterUrl ?? ''
    if (url.length === 0) {
      return
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, url)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(DeliveryText.toolbox_delivery_toast_link_copied)
    }).catch((err: BusinessError) => {
      logger.error(TAG, '链接复制失败', `${err.code} ${err.message}`)
      showToastSafety(DeliveryText.toolbox_delivery_toast_link_copied)
    })
  }

  /** 进入会话页（会话码经路由携带） */
  private openSession(): void {
    const code = this.session?.code ?? ''
    if (code.length === 0) {
      return
    }
    pathStack.pushPathByName(RouteNames.TOOLBOX_DELIVERY_SESSION, code)
  }

  /** 表单字段标签（caption 12/500 次文本，规范 §3.4 副文案层级） */
  @Builder
  FormLabel(text: string) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(DesignColor.TEXT_SECONDARY)
      .margin({ bottom: DesignDimens.SPACE_XS })
  }

  /** 可选预设 chip（选中 = 品牌底白字，未选中 = 白底描边；对齐安卓 ToolboxSelectableChip） */
  private onChipSelect(chipId: number): void {
    this.durationMinutes = chipId
  }

  /** 是否已过期（剩余秒数归零） */
  private isExpired(): boolean {
    return this.remainSeconds !== null && this.remainSeconds <= 0
  }

  private clickCountOf(session: DeliverySessionVo): number {
    return session.clickCount ?? 0
  }

  /** 会话态：投递码 + 二维码 + 倒计时 + 操作（对齐安卓 CreatedSessionCard） */
  @Builder
  CreatedSessionCard(session: DeliverySessionVo) {
    Column() {
      Text(DeliveryText.toolbox_delivery_code_label)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(DesignColor.TEXT_SECONDARY)

      Text(session.code ?? '')
        .fontSize(DesignFontSize.DATA_LARGE)
        .fontWeight(DesignFontWeight.BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .fontFeature(DesignFontFeature.TNUM)
        .margin({ top: DesignDimens.SPACE_XXS })

      // 二维码为次级信息，尺寸收敛到 160vp，避免与上方投递码争抢本页唯一锚点（规范 §1.2）
      DeliveryQrImage({ url: session.enterUrl ?? '', sizeValue: 160 })
        .margin({ top: DesignDimens.SPACE_MD })

      Text(session.enterUrl ?? '')
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .fontFeature(DesignFontFeature.TNUM)
        .textAlign(TextAlign.Center)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
        .margin({ top: DesignDimens.SPACE_SM })

      Row({ space: DesignDimens.SPACE_XS }) {
        if (this.isExpired()) {
          Text(DeliveryText.toolbox_delivery_session_expired)
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.DANGER_INK)
        } else {
          Text(`${DeliveryText.toolbox_delivery_countdown_prefix}${this.remainSeconds !== null ? formatRemain(this.remainSeconds) : '--:--'}`)
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.ONGOING_INK)
            .fontFeature(DesignFontFeature.TNUM)
        }
        if (session.hasPassword === true) {
          DeliveryTagChip({ text: DeliveryText.toolbox_delivery_has_password,
            containerColor: DesignColor.ICON_CONTAINER_TEAL,
            contentColor: DesignColor.ICON_FOREGROUND_TEAL })
        }
        Blank()
        if (this.clickCountOf(session) > 0) {
          Text(clicksFormat(this.clickCountOf(session)))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_TERTIARY)
            .fontFeature(DesignFontFeature.TNUM)
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .margin({ top: DesignDimens.SPACE_MD })

      if (this.isExpired()) {
        AppButton({
          text: DeliveryText.toolbox_delivery_action_recreate,
          click: () => {
            this.resetToForm()
          }
        })
        .margin({ top: DesignDimens.SPACE_LG })
      } else {
        Row({ space: DesignDimens.SPACE_SM }) {
          AppButton({
            text: DeliveryText.toolbox_delivery_action_copy_link,
            variant: AppButtonVariant.SECONDARY,
            click: () => {
              this.copyLink()
            }
          })
          .layoutWeight(1)
          AppButton({
            text: DeliveryText.toolbox_delivery_action_open_session,
            click: () => {
              this.openSession()
            }
          })
          .layoutWeight(1)
        }
        .width('100%')
        .margin({ top: DesignDimens.SPACE_LG })

        AppButton({
          text: DeliveryText.toolbox_delivery_action_close,
          variant: AppButtonVariant.SECONDARY,
          contentColor: DesignColor.DANGER_INK,
          loading: this.closing,
          click: () => {
            this.closeDialogController.open()
          }
        })
        .margin({ top: DesignDimens.SPACE_SM })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Scroll() {
        Column() {
          // 页面标题（对齐安卓 PageHeader：标题 22/600 + 48 热区返回）
          Row() {
            Column() {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([DesignColor.TEXT_PRIMARY])
            }
            .width(DesignDimens.BACK_HIT_AREA)
            .height(DesignDimens.BACK_HIT_AREA)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              pathStack.pop()
            })

            Text(DeliveryText.toolbox_delivery_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          // 内容区与页头分离：页头自带 12/24 横向留白（对齐安卓 PageHeader），
          // 内容区统一 24 页留白 + 上 8（原页头位于 padded Column 内又被加 12，返回钮落到 36vp）
          Column() {
            if (this.session === null) {
              // ---------- 表单态 ----------
            Column() {
              this.FormLabel(DeliveryText.toolbox_delivery_duration_label)
              // 4 枚 chip 在窄屏（364.6vp）总宽超出卡内容宽（274.9vp）：改横向 Scroll，
              // 保持安卓 chip 规格（12/600 + 16 横内边距）不被压缩，末枚可横滑查看
              Scroll() {
                Row({ space: DesignDimens.SPACE_XS }) {
                  ForEach(DELIVERY_DURATION_PRESETS, (minutes: number, index: number) => {
                    DeliveryDurationChip({
                      text: durationChipText(minutes),
                      selected: this.durationMinutes === minutes,
                      chipId: minutes,
                      onSelect: (chipId: number): void => {
                        this.onChipSelect(chipId)
                      }
                    })
                  }, (minutes: number) => `${minutes}`)
                }
              }
              .scrollable(ScrollDirection.Horizontal)
              .scrollBar(BarState.Off)
              .align(Alignment.Start)
              .width('100%')
              Text(DeliveryText.toolbox_delivery_duration_desc)
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(DesignColor.TEXT_TERTIARY)
                .margin({ top: DesignDimens.SPACE_XS })

              Column()
                .height(DesignDimens.SPACE_LG)

              // ---------- 进入密码（可选） ----------
              this.FormLabel(DeliveryText.toolbox_delivery_password_label)
              Row() {
                TextInput({
                  text: this.password,
                  placeholder: DeliveryText.toolbox_delivery_password_hint
                })
                  .type(this.showPassword ? InputType.Normal : InputType.Password)
                  .height(48)
                  .fontSize(DesignFontSize.BODY)
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .borderRadius(DesignDimens.INPUT_RADIUS)
                  .borderWidth(1)
                  .borderColor(this.passwordFocused ? DesignColor.PRIMARY : DesignColor.OUTLINE)
                  .placeholderColor(DesignColor.TEXT_TERTIARY)
                  .placeholderFont({ size: DesignFontSize.BODY_SMALL })
                  .showPasswordIcon(false)
                  .layoutWeight(1)
                  .onFocus(() => {
                    this.passwordFocused = true
                  })
                  .onBlur(() => {
                    this.passwordFocused = false
                  })
                  .onChange((value: string) => {
                    // 超上限直接截断不报错（与安卓 updatePassword.take(MAX) 一致）
                    this.password = value.length > 64 ? value.substring(0, 64) : value
                  })

                Stack({ alignContent: Alignment.Center }) {
                  SymbolGlyph(this.showPassword ? $r('sys.symbol.eye_slash') : $r('sys.symbol.eye'))
                    .fontSize(20)
                    .fontColor([DesignColor.TEXT_SECONDARY])
                }
                .width(DesignDimens.BACK_HIT_AREA)
                .height(DesignDimens.BACK_HIT_AREA)
                // 无障碍：密码可见性切换补语义（原仅图形，读屏无标签；accessibilityRole 需 SDK 18+，目标 API 17 不用）
                .accessibilityDescription(this.showPassword ? '隐藏密码' : '显示密码')
                .onClick(() => {
                  this.showPassword = !this.showPassword
                })
              }
              .width('100%')
              .alignItems(VerticalAlign.Center)

              Text(DeliveryText.toolbox_delivery_password_helper)
                .fontSize(DesignFontSize.CAPTION)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(DesignColor.TEXT_TERTIARY)
                .margin({ top: DesignDimens.SPACE_XS })

              Column()
                .height(DesignDimens.SPACE_LG)

              // ---------- 隐私明示（契约要求：服务器中转可见明文 + 取走即焚） ----------
              Column() {
                Text(DeliveryText.toolbox_delivery_privacy_notice)
                  .fontSize(DesignFontSize.BODY_SMALL)
                  .fontColor(DesignColor.TEXT_SECONDARY)
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.SPACE_MD)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .borderRadius(DesignDimens.RADIUS_MD)
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .border({ width: 1, color: DesignColor.OUTLINE })
            .shadow({
              radius: DesignElevation.LEVEL_1,
              color: DesignColor.SHADOW_COLOR_CARD,
              offsetX: 0,
              offsetY: 2
            })

            // ---------- 提交错误（仅提交后展示，error 档） ----------
            if (this.errorMessage.length > 0) {
              Text(this.errorMessage)
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.ERROR)
                .width('100%')
                .margin({ top: DesignDimens.SPACE_SM })
            }

            Column()
              .height(DesignDimens.SPACE_LG)

            AppButton({
              text: DeliveryText.toolbox_delivery_submit_create,
              loading: this.submitting,
              click: () => {
                this.submit()
              }
            })
          } else {
            // ---------- 会话态 ----------
            Column() {
              this.CreatedSessionCard(this.session)
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .border({ width: 1, color: DesignColor.OUTLINE })
            .shadow({
              radius: DesignElevation.LEVEL_1,
              color: DesignColor.SHADOW_COLOR_CARD,
              offsetX: 0,
              offsetY: 2
            })
          }
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS, bottom: 24 + safeBottomVp() })
          .alignItems(HorizontalAlign.Start)
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
      }
      .scrollBar(BarState.Off)
      .width('100%')
      .height('100%')
      .align(Alignment.TopStart)
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
  }
}

/**
 * 有效期预设 chip —— @ComponentV2 + @Param 承载选中态（已知坑：@Builder 按值传参在
 * durationMinutes 变更后不重建，选中高亮定格）。按压反馈内聚为 @Local。
 * 垂直内边距 6 → 16：把触控热区从 27.7vp 抬到 ≥48vp（文字行盒 16 + 上下 16）。
 */
@ComponentV2
struct DeliveryDurationChip {
  @Param text: string = ''
  @Param selected: boolean = false
  @Param chipId: number = 0
  @Event onSelect: (chipId: number) => void = (_: number) => {
  }
  @Local pressed: boolean = false

  build() {
    Text(this.text)
      .fontSize(DesignFontSize.CAPTION)
      // 字重恒 SemiBold：字重随选中切换会让文字宽度跳变（对齐安卓注释）
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor(this.selected ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      .padding({ left: 16, right: 16, top: 16, bottom: 16 })
      .backgroundColor(this.selected ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .borderWidth(1)
      .borderColor(this.selected ? DesignColor.PRIMARY : DesignColor.OUTLINE)
      .borderRadius(DesignDimens.RADIUS_FULL)
      .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.pressed = true
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.pressed = false
        }
      })
      .onClick(() => {
        this.onSelect(this.chipId)
      })
      .animation({ duration: 200, curve: Curve.EaseInOut })
  }
}

/** 倒计时展示（mm:ss / H:MM:SS；对齐安卓 DeliveryRules.formatRemaining） */
function formatRemain(totalSeconds: number): string {
  const safe = totalSeconds > 0 ? totalSeconds : 0
  const hours = Math.floor(safe / 3600)
  const minutes = Math.floor((safe % 3600) / 60)
  const seconds = safe % 60
  const pad = (value: number): string => value < 10 ? `0${value}` : `${value}`
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
}

/**
 * 投递会话二维码 — ScanKit 两段降级 + 纯 ArkTS 兜底（对齐 ToolboxShortCodeDetail.loadQrCode）。
 * 尺寸为 160vp（次级信息，不与投递码争抢锚点）。
 */
@ComponentV2
struct DeliveryQrImage {
  @Require @Param url: string
  @Param sizeValue: number = 160
  @Local pixelMap: image.PixelMap | null = null
  @Local loading: boolean = true
  @Local failed: boolean = false

  aboutToAppear(): void {
    this.loadQr()
  }

  @Monitor('url')
  onUrlChange(): void {
    this.loadQr()
  }

  private async loadQr(): Promise<void> {
    if (this.url.length === 0) {
      this.failed = true
      this.loading = false
      return
    }
    this.loading = true
    this.failed = false
    try {
      const map: image.PixelMap = await generateBarcode.createBarcode(this.url, {
        scanType: scanCore.ScanType.QR_CODE,
        width: 512,
        height: 512,
        level: generateBarcode.ErrorCorrectionLevel.LEVEL_M,
        margin: 2
      })
      this.pixelMap = map
    } catch (err) {
      try {
        const fallbackMap: image.PixelMap = await generateBarcode.createBarcode(this.url, {
          scanType: scanCore.ScanType.QR_CODE,
          width: 512,
          height: 512
        })
        this.pixelMap = fallbackMap
      } catch (scanKitErr) {
        try {
          this.pixelMap = await createQrPixelMap(this.url)
          logger.debug(TAG, 'ScanKit 不可用，纯 ArkTS 兜底二维码已生成')
        } catch (fallbackErr) {
          this.failed = true
          logger.error(TAG, '二维码兜底生成失败', fallbackErr instanceof Error ? fallbackErr.message : '')
        }
      }
      logger.error(TAG, '二维码生成异常', err instanceof Error ? err.message : '')
    } finally {
      this.loading = false
    }
  }

  build() {
    Stack() {
      if (this.loading) {
        Column()
          .width('100%')
          .height('100%')
          .backgroundColor(DesignColor.SURFACE_VARIANT)
          .borderRadius(DesignDimens.RADIUS_MD)
      } else if (this.pixelMap !== null) {
        Image(this.pixelMap)
          .width('100%')
          .height('100%')
          .objectFit(ImageFit.Contain)
          .borderRadius(DesignDimens.RADIUS_MD)
          .backgroundColor(Color.White)
      } else {
        // 失败/无链接：占位块（不阻塞会话态其余信息）
        Column()
          .width('100%')
          .height('100%')
          .backgroundColor(DesignColor.SURFACE_VARIANT)
          .borderRadius(DesignDimens.RADIUS_MD)
      }
    }
    .width(this.sizeValue)
    .height(this.sizeValue)
  }
}
