// 短链详情页 — 对齐安卓 feature/toolbox/ui/detail/ShortCodeDetailPage.kt
//
// 基础字段由列表路由携带（getObjectParam，禁止二次请求）；二维码本地生成（内容为完整 shortUrl）；
// 复制短链 / 打开链接（公开码直开 shortUrl，私有码经 resolve 取 target 再开——计一次点击属预期）；
// 续期（PUT expireAt，无永久选项）/ 删除（DELETE，成功以 code===200 为准）+ 访问明细分页（/stats）。
//
// 红线：resolve 有点击统计副作用，仅用于「私有码打开」，禁止用于详情刷新/列表。

import { HttpStatusCode } from '@ohos/axios'
import { pasteboard } from '@kit.BasicServicesKit'
import { common, Want } from '@kit.AbilityKit'
import { image } from '@kit.ImageKit'
import { generateBarcode, scanCore } from '@kit.ScanKit'
import { RouteNames } from '../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../constant/DesignSystem'
import {
  ShortCodeStatsVo,
  TOOLBOX_PAGE_SIZE,
  ToolboxShortCodeDetailParam,
  ToolboxText,
  VISIBILITY_PUBLIC,
  deleteShortCode,
  pageShortCodeStats,
  resolveShortCode,
  toolboxClicksFormat,
  toolboxConfirmDeleteMessage,
  toolboxExpiryUntil,
  updateShortCode
} from './api'
import {
  ExpiryPreset,
  ExpiryState,
  expiryStateOf,
  expireAtFor,
  formatDisplayDateTime,
  isPublicVisibility
} from './ShortCodeRules'
import { ToolboxDatePickerDialog } from './ToolboxDatePickerDialog'
import { ToolboxRenewDialog } from './ToolboxRenewDialog'
import { AppButton, AppConfirmDialog } from '../../view/common/AppComponents'
import { LoadingFooter } from '../../view/LoadingFooter'
import { PagingController } from '../../utils/PagingUtil'
import { getObjectParam, pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'

@Builder
export function ToolboxShortCodeDetailBuilder() {
  ToolboxShortCodeDetail()
}

@Component
struct ToolboxShortCodeDetail {
  // 路由携带的基础字段（禁止二次请求）
  private param: ToolboxShortCodeDetailParam | null = null
  // 访问明细分页
  @State statsList: Array<ShortCodeStatsVo> = []
  @State statsLoadingFirst: boolean = true
  @State statsError: boolean = false
  @State statsLoadingMore: boolean = false
  private statsPaging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchStats(page)
    },
    (loading: boolean): void => {
      this.statsLoadingMore = loading
    }
  )
  // 二维码三态：加载中占位 / 成功渲染 PixelMap / 失败兜底可重试
  @State qrPixelMap: image.PixelMap | null = null
  @State qrLoading: boolean = true
  @State qrFailed: boolean = false
  // 请求态：续期 / 删除 / 私有码解析（各自独立，避免互相阻塞）
  @State renewing: boolean = false
  @State deleting: boolean = false
  @State resolving: boolean = false
  // 续期弹窗表单（无永久选项——已创建的码不可改回永久）
  @State renewPreset: ExpiryPreset = ExpiryPreset.DAYS_7
  @State renewDate: string | null = null

  async aboutToAppear(): Promise<void> {
    // 基础字段由列表路由携带（红线：禁止经 resolve 拉取详情——resolve 会计点击）
    this.param = getObjectParam<ToolboxShortCodeDetailParam>(RouteNames.TOOLBOX_SHORT_CODE_DETAIL)
    await this.loadQrCode()
    await this.fetchStats(1)
    this.statsLoadingFirst = false
  }

  private getShortUrl(): string {
    return this.param?.shortUrl ?? ""
  }

  private isPublicCode(): boolean {
    return isPublicVisibility(this.param?.isPublic ?? VISIBILITY_PUBLIC)
  }

  /** 创建时间展示：不可解析/缺失时兜底「—」（对齐安卓 ifBlank { value_none }） */
  private displayCreated(): string {
    const text = formatDisplayDateTime(this.param?.createdAt)
    return text.length > 0 ? text : ToolboxText.toolbox_value_none
  }

  /** 二维码本地生成（内容为完整 shortUrl；对齐安卓 generateQrCodeBitmap(shortUrl, 512)） */
  private async loadQrCode(): Promise<void> {
    const url = this.getShortUrl()
    if (url.length === 0) {
      this.qrFailed = true
      this.qrLoading = false
      return
    }
    this.qrLoading = true
    this.qrFailed = false
    try {
      // 方案取舍：优先用系统 ScanKit（零三方依赖、直接产出 PixelMap）；
      // 设备无 ScanKit 能力时进入失败态并可点击重试（不阻塞页面其余部分）。
      const pixelMap: image.PixelMap = await generateBarcode.createBarcode(url, {
        scanType: scanCore.ScanType.QR_CODE,
        width: 512,
        height: 512,
        level: generateBarcode.ErrorCorrectionLevel.LEVEL_M,
        margin: 2
      })
      this.qrPixelMap = pixelMap
    } catch (err) {
      this.qrFailed = true
    } finally {
      this.qrLoading = false
    }
  }

  /** 访问明细分页拉取 */
  async fetchStats(page: number): Promise<boolean> {
    const id = this.param?.id ?? 0
    if (id <= 0) {
      return false
    }
    const res = await pageShortCodeStats(id, page, TOOLBOX_PAGE_SIZE)
    if (res.code === HttpStatusCode.Ok && res.data !== null) {
      const records: Array<ShortCodeStatsVo> = res.data.records
      if (records.length === 0) {
        return false
      }
      if (page === 1) {
        this.statsList = []
        this.statsError = false
      }
      this.statsList.push(...records)
      return true
    }
    if (page === 1) {
      this.statsError = true
    }
    showToastSafety(res.message, ToolboxText.toolbox_stats_error_load)
    return false
  }

  /** 复制短链到剪贴板 */
  private copyShortUrl(): void {
    const url = this.getShortUrl()
    if (url.length === 0) {
      return
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, url)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(ToolboxText.toolbox_copy_success)
    }).catch(() => {
      showToastSafety(ToolboxText.toolbox_open_failed)
    })
  }

  /** 打开链接：公开码直开 shortUrl；私有码先 resolve 取 target（计一次点击属预期） */
  private async openLink(): Promise<void> {
    const url = this.getShortUrl()
    if (this.isPublicCode()) {
      if (url.length > 0) {
        this.openInBrowser(url)
      }
      return
    }
    if (this.resolving) {
      return
    }
    const code = this.param?.code ?? ""
    if (code.length === 0) {
      return
    }
    this.resolving = true
    try {
      const res = await resolveShortCode(code)
      if (res.code === HttpStatusCode.Ok && res.data !== null && (res.data.target ?? "").length > 0) {
        this.openInBrowser(res.data.target ?? "")
      } else {
        showToastSafety(res.message, ToolboxText.toolbox_resolve_failed)
      }
    } catch (err) {
      showToastSafety(ToolboxText.toolbox_resolve_failed)
    } finally {
      this.resolving = false
    }
  }

  /** 浏览器打开（无浏览器兜底提示，对齐安卓 openInBrowser） */
  private openInBrowser(url: string): void {
    if (url.length === 0) {
      return
    }
    try {
      const context = getContext(this) as common.UIAbilityContext
      const want: Want = {
        action: 'ohos.want.action.viewData',
        uri: url,
        entities: ['entity.system.browsable']
      }
      context.startAbility(want)
    } catch (err) {
      showToastSafety(ToolboxText.toolbox_open_failed)
    }
  }

  /** 打开续期弹窗（每次新建控制器，保证 preset/日期入参为最新） */
  private openRenewDialog(): void {
    const controller: CustomDialogController = new CustomDialogController({
      builder: ToolboxRenewDialog({
        preset: this.renewPreset,
        customDate: this.renewDate ?? "",
        loading: this.renewing,
        onPresetChange: (preset: ExpiryPreset): void => {
          this.renewPreset = preset
        },
        onPickDate: (): void => {
          this.openRenewDatePicker()
        },
        onConfirm: (): void => {
          this.doRenew(controller)
        },
        onDismiss: (): void => {
          this.renewDate = null
          this.renewPreset = ExpiryPreset.DAYS_7
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true
    })
    controller.open()
  }

  /** 续期自定义日期弹窗 */
  private openRenewDatePicker(): void {
    const controller: CustomDialogController = new CustomDialogController({
      builder: ToolboxDatePickerDialog({
        initialDate: this.renewDate ?? "",
        title: ToolboxText.toolbox_expiry_custom_pick,
        onConfirm: (date: string): void => {
          this.renewDate = date
        },
        onDismiss: (): void => {
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true
    })
    controller.open()
  }

  /** 续期（PUT expireAt；预设 7 天 / 30 天 / 自定义日期；无永久选项） */
  private async doRenew(controller: CustomDialogController): Promise<void> {
    if (this.renewing) {
      return
    }
    const id = this.param?.id ?? 0
    if (id <= 0) {
      return
    }
    if (this.renewPreset === ExpiryPreset.CUSTOM &&
      (this.renewDate === null || this.renewDate.length === 0)) {
      showToastSafety(ToolboxText.toolbox_error_custom_date_required)
      return
    }
    this.renewing = true
    try {
      const res = await updateShortCode(id, {
        expireAt: expireAtFor(this.renewPreset, this.renewDate, new Date()) ?? ""
      })
      if (res.code === HttpStatusCode.Ok) {
        showToastSafety(ToolboxText.toolbox_toast_renew_success)
        controller.close()
        pathStack.pop()
      } else {
        showToastSafety(res.message, ToolboxText.toolbox_error_submit)
      }
    } catch (err) {
      showToastSafety(ToolboxText.toolbox_error_submit)
    } finally {
      this.renewing = false
    }
  }

  /** 打开删除确认弹窗（文案含短码，故每次新建控制器） */
  private openDeleteDialog(): void {
    const controller: CustomDialogController = new CustomDialogController({
      builder: AppConfirmDialog({
        title: ToolboxText.toolbox_confirm_delete_title,
        message: toolboxConfirmDeleteMessage(this.param?.code ?? ""),
        confirmText: ToolboxText.toolbox_action_delete,
        onConfirm: () => {
          this.doDelete()
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true
    })
    controller.open()
  }

  /** 删除（DELETE；后端 data=null，成功一律以 code===200 判定） */
  private async doDelete(): Promise<void> {
    if (this.deleting) {
      return
    }
    const id = this.param?.id ?? 0
    if (id <= 0) {
      return
    }
    this.deleting = true
    try {
      const res = await deleteShortCode(id)
      // 红线：DELETE 响应 data 为 null，只判 code
      if (res.code === HttpStatusCode.Ok) {
        showToastSafety(ToolboxText.toolbox_toast_delete_success)
        pathStack.pop()
      } else {
        showToastSafety(res.message, ToolboxText.toolbox_error_submit)
      }
    } catch (err) {
      showToastSafety(ToolboxText.toolbox_error_submit)
    } finally {
      this.deleting = false
    }
  }

  /** 信息行（对齐安卓 InfoRow） */
  @Builder
  InfoRow(label: string, value: string, valueColor: number) {
    Row() {
      Text(label)
        .fontSize(DesignFontSize.BODY)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .width(88)

      Text(value)
        .fontSize(DesignFontSize.BODY)
        .fontColor(valueColor)
        .layoutWeight(1)
        .textAlign(TextAlign.End)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .margin({ top: 6, bottom: 6 })
  }

  /** 基础信息卡（6 行 + 目标链接全文 + 「修改有效期」文字按钮） */
  @Builder
  InfoCard() {
    Column() {
      this.InfoRow(ToolboxText.toolbox_label_code, this.param?.code ?? "", DesignColor.TEXT_PRIMARY)
      this.InfoRow(ToolboxText.toolbox_label_type, ToolboxText.toolbox_type_short_link, DesignColor.TEXT_PRIMARY)
      this.InfoRow(ToolboxText.toolbox_label_visibility,
        this.isPublicCode() ? ToolboxText.toolbox_visibility_public_value : ToolboxText.toolbox_visibility_private_value,
        this.isPublicCode() ? DesignColor.SUCCESS : DesignColor.ONGOING_INK)
      this.InfoRow(ToolboxText.toolbox_label_clicks,
        toolboxClicksFormat(this.param?.clickCount ?? 0), DesignColor.PRIMARY)
      this.InfoRow(ToolboxText.toolbox_label_created, this.displayCreated(), DesignColor.TEXT_PRIMARY)

      if (expiryStateOf(this.param?.expireAt, new Date()) === ExpiryState.PERMANENT) {
        this.InfoRow(ToolboxText.toolbox_label_expiry, ToolboxText.toolbox_expiry_permanent, DesignColor.SUCCESS)
      } else if (expiryStateOf(this.param?.expireAt, new Date()) === ExpiryState.ACTIVE) {
        this.InfoRow(ToolboxText.toolbox_label_expiry,
          toolboxExpiryUntil(formatDisplayDateTime(this.param?.expireAt)), DesignColor.TEXT_SECONDARY)
      } else {
        this.InfoRow(ToolboxText.toolbox_label_expiry, ToolboxText.toolbox_expiry_expired, DesignColor.DANGER_INK)
      }

      if ((this.param?.target ?? "").length > 0) {
        Text(ToolboxText.toolbox_label_target)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: DesignDimens.SPACE_XS })
        Text(this.param?.target ?? "")
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(5)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: 4 })
      }

      Row() {
        Blank()
        Text(ToolboxText.toolbox_action_renew)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.PRIMARY)
          .padding({ top: 4, bottom: 4 })
          .onClick(() => {
            this.openRenewDialog()
          })
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_XS })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    // 卡片唯一画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影
    .border({ width: 1, color: DesignColor.OUTLINE })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: 'rgba(20,40,30,0.05)',
      offsetX: 0,
      offsetY: 2
    })
  }

  /** 二维码卡（本地生成 + 复制 + 打开） */
  @Builder
  QrCard() {
    Column() {
      Text(ToolboxText.toolbox_qr_section_title)
        .fontSize(DesignFontSize.CARD_TITLE)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .width('100%')
        .margin({ bottom: DesignDimens.SPACE_MD })

      if (this.qrPixelMap !== null) {
        Image(this.qrPixelMap)
          .width(200)
          .height(200)
          .borderRadius(DesignDimens.RADIUS_MD)
          .objectFit(ImageFit.Contain)
      } else {
        Column() {
          if (this.qrLoading) {
            LoadingProgress()
              .width(28)
              .height(28)
              .color(DesignColor.PRIMARY)
            Text(ToolboxText.toolbox_qr_loading)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .margin({ top: 8 })
          } else {
            Text(ToolboxText.toolbox_qr_failed)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
            Text(ToolboxText.toolbox_action_retry)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.PRIMARY)
              .margin({ top: 8 })
          }
        }
        .width(200)
        .height(200)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor(DesignColor.SURFACE_VARIANT)
        .borderRadius(DesignDimens.RADIUS_MD)
        .onClick(() => {
          if (!this.qrLoading && this.qrFailed) {
            this.loadQrCode()
          }
        })
      }

      Text(this.getShortUrl())
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .textAlign(TextAlign.Center)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
        .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD })
        .margin({ top: DesignDimens.SPACE_SM })

      if (!this.isPublicCode()) {
        Text(ToolboxText.toolbox_private_open_hint)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.ONGOING_INK)
          .textAlign(TextAlign.Center)
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XS })
      }

      Row({ space: DesignDimens.SPACE_SM }) {
        Button(ToolboxText.toolbox_action_copy)
          .type(ButtonType.Capsule)
          .layoutWeight(1)
          .height(48)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.PRIMARY)
          .backgroundColor(DesignColor.SURFACE)
          .borderWidth(1)
          .borderColor(DesignColor.PRIMARY)
          .onClick(() => {
            this.copyShortUrl()
          })

        Column() {
          AppButton({
            text: ToolboxText.toolbox_action_open,
            loading: this.resolving,
            click: () => {
              this.openLink()
            }
          })
        }
        .layoutWeight(1)
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_MD })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    // 卡片唯一画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影
    .border({ width: 1, color: DesignColor.OUTLINE })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: 'rgba(20,40,30,0.05)',
      offsetX: 0,
      offsetY: 2
    })
  }

  /** 访问明细行（时间 + IP 摘要，次行 UA / Referer 均脱敏弱化展示） */
  @Builder
  StatRow(stat: ShortCodeStatsVo) {
    Column() {
      Row() {
        Text(formatDisplayDateTime(stat.createdAt))
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_PRIMARY)
        Blank()
        Text(stat.ipHash ?? "—")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .padding({ left: 8, right: 8, top: 2, bottom: 2 })
          .backgroundColor(DesignColor.SURFACE_VARIANT)
          .borderRadius(DesignDimens.RADIUS_FULL)
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      if ((stat.userAgent ?? "").length > 0) {
        Text(stat.userAgent ?? "")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: 2 })
      }
      if ((stat.referer ?? "").length > 0) {
        Text(stat.referer ?? "")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: 2 })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
    .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
  }

  build() {
    NavDestination() {
      Column() {
        // 页面标题 + 右上「删除」危险文字按钮
        Row() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DesignColor.TEXT_PRIMARY])
            .onClick(() => {
              pathStack.pop()
            })
            .padding(8)

          Text(ToolboxText.toolbox_detail_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ left: 4 })

          Blank()

          Text(ToolboxText.toolbox_action_delete)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.DANGER_INK)
            .padding({ left: 12, right: 12, top: 8, bottom: 8 })
            .onClick(() => {
              this.openDeleteDialog()
            })
        }
        .width('100%')
        .height(56)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .alignItems(VerticalAlign.Center)

        List() {
          ListItem() {
            this.InfoCard()
          }
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 8 })

          ListItem() {
            this.QrCard()
          }
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 12 })

          ListItem() {
            Text(ToolboxText.toolbox_stats_title)
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .width('100%')
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
              .margin({ top: 8, bottom: 4 })
          }

          ListItem() {
            Column() {
              if (this.statsLoadingFirst) {
                LoadingProgress()
                  .width(28)
                  .height(28)
                  .color(DesignColor.PRIMARY)
                  .margin({ top: 12 })
              } else if (this.statsError && this.statsList.length === 0) {
                Text(ToolboxText.toolbox_stats_error_load)
                  .fontSize(DesignFontSize.BODY)
                  .fontColor(DesignColor.TEXT_SECONDARY)
                  .width('100%')
                  .textAlign(TextAlign.Center)
                  .padding({ top: 8 })
                Text(ToolboxText.toolbox_action_retry)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor(DesignColor.PRIMARY)
                  .width('100%')
                  .textAlign(TextAlign.Center)
                  .padding({ top: 8 })
                  .onClick(() => {
                    this.statsPaging.reset()
                    this.fetchStats(1)
                  })
              } else if (this.statsList.length === 0) {
                Text(ToolboxText.toolbox_stats_empty)
                  .fontSize(DesignFontSize.BODY)
                  .fontColor(DesignColor.TEXT_TERTIARY)
                  .width('100%')
                  .textAlign(TextAlign.Center)
                  .padding({ top: 16, bottom: 16 })
              } else {
                ForEach(this.statsList, (stat: ShortCodeStatsVo, index: number) => {
                  if (index > 0) {
                    Divider()
                      .strokeWidth(0.5)
                      .color(DesignColor.HAIRLINE)
                  }
                  this.StatRow(stat)
                }, (stat: ShortCodeStatsVo, index: number) => `${index}`)
                if (this.statsLoadingMore) {
                  LoadingFooter({ isLoading: true })
                }
              }
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            // 卡片唯一画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影
            .border({ width: 1, color: DesignColor.OUTLINE })
            .shadow({
              radius: DesignElevation.LEVEL_1,
              color: 'rgba(20,40,30,0.05)',
              offsetX: 0,
              offsetY: 2
            })
          }
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 4, bottom: 24 })
        }
        .width('100%')
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        .divider({ strokeWidth: 0, color: Color.Transparent })
        // 触底加载：列表项 = 信息卡 + 二维码卡 + 明细标题 + 明细行
        .onScrollIndex((start: number, end: number) => {
          this.statsPaging.onScrollIndex(end, 3 + this.statsList.length)
        })
        .onScrollFrameBegin((offset: number) => {
          this.statsPaging.onScrollFrameBegin(offset)
          return { offsetRemain: offset }
        })
      }
      .width('100%')
      .height('100%')
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}
