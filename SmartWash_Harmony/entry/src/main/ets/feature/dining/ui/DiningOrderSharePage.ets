// 订单分享视图 — 对齐 Android DiningOrderSharePage.kt
//
// 订单信息卡（标题/菜品数/参与者数）+ 加入二维码（内容 {BASE_URL origin}/dining/join/{orderId}?t={token}，
// ScanKit 两段降级 + 纯 ArkTS 兜底，复用 toolbox QrFallback 先例）+ 复制加入链接
// + 重置加入凭证（二次确认 → POST reset → 新 token 重建二维码）。
// joinToken 来源：创建响应携带优先；发起者从列表/分享进入时由详情响应补发（仅发起者下发）。

import { image } from '@kit.ImageKit'
import { BusinessError, pasteboard } from '@kit.BasicServicesKit'
import { generateBarcode, scanCore } from '@kit.ScanKit'
import { RouteNames } from '../../../constant/RouteNames'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { DiningOrderDetailVo, getDiningOrderDetail, resetDiningJoinToken } from '../../../network/api/DiningApi'
import { buildDiningJoinUrl } from '../DiningLogic'
import { DiningText } from '../DiningText'
import { DiningOrderShareParam } from '../api'
import { AppButton, AppButtonVariant, AppConfirmDialog, EmptyState, SkeletonBlock } from '../../../view/common/AppComponents'
import { createQrPixelMap } from '../../toolbox/QrFallback'
import { logger } from '../../../utils/Logger'
import { getObjectParam, getNumberParam, pathStack } from '../../../utils/PathStackUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'
import BuildProfile from 'entry/BuildProfile'

/** 日志 TAG */
const TAG: string = 'diningOrderShare'

/** 二维码显示尺寸（vp） */
const QR_DISPLAY_SIZE: number = 200

@Builder
export function DiningOrderShareBuilder() {
  DiningOrderSharePage()
}

@ComponentV2
struct DiningOrderSharePage {
  private orderId: number = 0
  /** 创建响应携带的加入凭证（空串时由详情响应补发） */
  private carriedToken: string = ''

  @Local detail: DiningOrderDetailVo | null = null
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  /** 生效中的加入凭证（详情补发/重置后更新） */
  @Local joinToken: string = ''
  @Local qrPixelMap: image.PixelMap | null = null
  @Local qrLoading: boolean = false
  @Local qrFailed: boolean = false
  @Local resetting: boolean = false

  // 重置凭证二次确认弹窗
  private resetDialog: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: DiningText.dining_order_share_reset_action,
      message: DiningText.dining_order_share_reset_confirm_message,
      confirmText: DiningText.dining_order_share_reset_confirm,
      onConfirm: (): void => {
        this.resetToken()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear(): Promise<void> {
    // 参数双通道：列表/详情跳转传 number；创建页 replace 传 DiningOrderShareParam
    const param = getObjectParam<DiningOrderShareParam>(RouteNames.DINING_ORDER_SHARE)
    if (param !== null) {
      this.orderId = param.orderId
      this.carriedToken = param.joinToken
    } else {
      this.orderId = getNumberParam(RouteNames.DINING_ORDER_SHARE) ?? 0
    }
    if (this.orderId <= 0) {
      logger.warn(TAG, '分享页参数缺失 orderId')
      this.isLoading = false
      this.isError = true
      return
    }
    this.joinToken = this.carriedToken
    await this.loadDetail()
    this.isLoading = false
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
        Column() {
          Row() {
            Column() {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([$r('app.color.TEXT_PRIMARY')])
            }
            .width(DesignDimens.BACK_HIT_AREA)
            .height(DesignDimens.BACK_HIT_AREA)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              pathStack.pop()
            })

            Text(DiningText.dining_order_share_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoading) {
            Column({ space: DesignDimens.CARD_SPACING }) {
              SkeletonBlock().height(96)
              SkeletonBlock().height(QR_DISPLAY_SIZE)
              SkeletonBlock().height(48)
            }
            .width('100%')
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_MD
            })
            .layoutWeight(1)
          } else if (this.isError || this.detail === null) {
            Column() {
              EmptyState({ message: DiningText.dining_order_share_detail_failed })
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            Scroll() {
              Column() {
                this.OrderInfoCard()
                this.QrSection()
                this.ActionArea()
              }
              .width('100%')
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                top: DesignDimens.SPACE_SM,
                bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_XL)
              })
            }
            .scrollBar(BarState.Off)
            .width('100%')
            .height('100%')
            .align(Alignment.TopStart)
            .edgeEffect(EdgeEffect.Spring)
          }
        }
        .width('100%')
        .height('100%')
        .padding({ top: safeTopVp(), bottom: safeBottomVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }

  /** 订单信息卡：标题 + 菜品数 + 参与者数 */
  @Builder
  OrderInfoCard() {
    Column({ space: DesignDimens.SPACE_XS }) {
      Text(this.detail?.title ?? '')
        .fontSize(DesignFontSize.CARD_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .width('100%')
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Text(`${DiningText.menuCount(this.detail?.menu.length ?? 0)} · ${DiningText.memberCount(this.detail?.members.length ?? 0)}`)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .width('100%')
    }
    .alignItems(HorizontalAlign.Start)
    .width('100%')
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .padding(DesignDimens.CARD_PADDING)
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
  }

  /** 二维码区：卡片白底 + 二维码图 + 提示文案；无凭证降级提示 */
  @Builder
  QrSection() {
    Column({ space: DesignDimens.SPACE_SM }) {
      Text(DiningText.dining_order_share_qr_section)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor($r('app.color.TEXT_PRIMARY'))

      if (this.joinToken.length === 0) {
        Text(DiningText.dining_order_share_qr_unavailable)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .width('100%')
          .textAlign(TextAlign.Center)
          .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
      } else if (this.qrLoading) {
        Column() {
          SkeletonBlock()
            .width(QR_DISPLAY_SIZE)
            .height(QR_DISPLAY_SIZE)
            .borderRadius(DesignDimens.RADIUS_MD)
        }
        .width('100%')
        .justifyContent(FlexAlign.Center)
        .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
      } else if (this.qrFailed || this.qrPixelMap === null) {
        Column() {
          Text(DiningText.dining_order_share_qr_failed)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
        }
        .width('100%')
        .justifyContent(FlexAlign.Center)
        .padding({
          top: DesignDimens.SPACE_MD,
          bottom: DesignDimens.SPACE_MD,
          left: DesignDimens.SPACE_MD,
          right: DesignDimens.SPACE_MD
        })
        .borderRadius(DesignDimens.RADIUS_MD)
        .backgroundColor($r('app.color.SURFACE_VARIANT'))
        .onClick(() => {
          this.loadQrCode()
        })
      } else {
        Image(this.qrPixelMap)
          .width(QR_DISPLAY_SIZE)
          .height(QR_DISPLAY_SIZE)
          .objectFit(ImageFit.Contain)
          .draggable(false)
          .borderRadius(DesignDimens.RADIUS_MD)
      }

      Text(DiningText.dining_order_share_qr_hint)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .textAlign(TextAlign.Center)
        .width('100%')
    }
    .width('100%')
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor($r('app.color.OUTLINE'))
    .padding(DesignDimens.CARD_PADDING)
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    .margin({ top: DesignDimens.CARD_SPACING })
  }

  /** 操作区：复制加入链接 + 重置加入凭证 */
  @Builder
  ActionArea() {
    Column({ space: DesignDimens.SPACE_MD }) {
      AppButton({
        text: DiningText.dining_order_share_copy_link,
        variant: AppButtonVariant.SECONDARY,
        isEnabled: this.joinToken.length > 0,
        click: () => {
          this.copyJoinLink()
        }
      })

      AppButton({
        text: DiningText.dining_order_share_reset_action,
        variant: AppButtonVariant.SECONDARY,
        contentColor: $r('app.color.DANGER_INK'),
        loading: this.resetting,
        click: () => {
          this.resetDialog.open()
        }
      })
    }
    .width('100%')
    .margin({ top: DesignDimens.SECTION_SPACING })
  }

  /** 拉订单详情（全量；发起者响应携带 joinToken 时补齐本地凭证） */
  private async loadDetail(): Promise<void> {
    try {
      const res = await getDiningOrderDetail(this.orderId, null)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.detail = res.data
        // 仅发起者详情响应下发 joinToken；创建响应已携带则不覆盖（同值）
        if (this.joinToken.length === 0 && res.data.joinToken.length > 0) {
          this.joinToken = res.data.joinToken
        }
        if (this.joinToken.length > 0) {
          this.loadQrCode()
        }
        logger.debug(TAG, `分享页详情加载成功 menu=${res.data.menu.length} members=${res.data.members.length}`)
      } else {
        logger.warn(TAG, `分享页详情加载失败 code=${res.code} message=${res.message ?? ''}`)
        this.isError = true
        showToastSafety(res.message, DiningText.dining_order_share_detail_failed)
      }
    } catch (err) {
      logger.error(TAG, '分享页详情加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      showToastSafety('', DiningText.dining_order_share_detail_failed)
    }
  }

  /** 二维码生成（ScanKit 两段降级 + 纯 ArkTS 兜底，对齐 ToolboxShortCodeDetail.loadQrCode） */
  private async loadQrCode(): Promise<void> {
    const url = buildDiningJoinUrl(BuildProfile.BASE_URL, this.orderId, this.joinToken)
    if (url === null) {
      logger.warn(TAG, '加入链接构造失败（token/origin 缺失），二维码进失败态')
      this.qrFailed = true
      return
    }
    logger.debug(TAG, '开始生成加入二维码')
    this.qrLoading = true
    this.qrFailed = false
    try {
      const pixelMap: image.PixelMap = await generateBarcode.createBarcode(url, {
        scanType: scanCore.ScanType.QR_CODE,
        width: 512,
        height: 512,
        level: generateBarcode.ErrorCorrectionLevel.LEVEL_M,
        margin: 2
      })
      this.qrPixelMap = pixelMap
      logger.debug(TAG, '加入二维码生成成功')
    } catch (err) {
      try {
        const fallbackMap: image.PixelMap = await generateBarcode.createBarcode(url, {
          scanType: scanCore.ScanType.QR_CODE,
          width: 512,
          height: 512
        })
        this.qrPixelMap = fallbackMap
      } catch (scanKitErr) {
        // 模拟器等环境 ScanKit 直接抛 "Emulator is not supported"，纯 ArkTS 兜底出图
        try {
          this.qrPixelMap = await createQrPixelMap(url)
          logger.debug(TAG, 'ScanKit 不可用，纯 ArkTS 兜底二维码已生成')
        } catch (fallbackErr) {
          this.qrFailed = true
          logger.error(TAG, '二维码兜底生成失败', fallbackErr instanceof Error ? fallbackErr.message : '')
        }
      }
      logger.error(TAG, '二维码生成异常', err instanceof Error ? err.message : '')
    } finally {
      this.qrLoading = false
    }
  }

  /** 复制加入链接（pasteboard，对齐 toolbox 列表复制先例） */
  private copyJoinLink(): void {
    const url = buildDiningJoinUrl(BuildProfile.BASE_URL, this.orderId, this.joinToken)
    if (url === null) {
      showToastSafety(DiningText.dining_order_share_qr_unavailable)
      return
    }
    try {
      const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, url)
      pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
        showToastSafety(DiningText.dining_order_share_copy_done)
        logger.debug(TAG, '加入链接已复制')
      }).catch((err: BusinessError) => {
        logger.warn(TAG, `加入链接复制失败 code=${err.code}`)
        showToastSafety(DiningText.dining_copy_failed)
      })
    } catch (err) {
      logger.error(TAG, '加入链接复制异常', err instanceof Error ? err.message : '')
      showToastSafety(DiningText.dining_copy_failed)
    }
  }

  /** 重置加入凭证：旧码全部作废，新 token 落地后重建二维码 */
  private async resetToken(): Promise<void> {
    if (this.resetting) {
      return
    }
    this.resetting = true
    try {
      const res = await resetDiningJoinToken(this.orderId)
      if (res.code === BizCode.SUCCESS && res.data !== null && res.data.length > 0) {
        this.joinToken = res.data
        logger.info(TAG, `加入凭证已重置 orderId=${this.orderId}`)
        showToastSafety(DiningText.dining_order_share_reset_done)
        this.loadQrCode()
      } else {
        logger.warn(TAG, `加入凭证重置未生效 code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, DiningText.dining_order_detail_write_failed)
      }
    } catch (err) {
      logger.error(TAG, '加入凭证重置异常', err instanceof Error ? err.message : '')
      showToastSafety('', DiningText.dining_order_detail_write_failed)
    }
    this.resetting = false
  }
}
