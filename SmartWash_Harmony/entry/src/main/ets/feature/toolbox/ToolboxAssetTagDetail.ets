// 物品标签详情页（一物一码 M2）— 对齐安卓 feature/toolbox/ui/assettag/AssetTagDetailPage.kt
//
// 基础字段由列表路由携带（getObjectParam，禁止二次请求）；信息卡 + 状态流转（按当前状态给唯一动作，裁定在后端）
// + 标签链接展示/复制（pasteboard）+ 删除（AppConfirmDialog；成功以 code===200 判定）。
// 隐私：联系方式仅回「是否已留」，详情页不展示明文/密文。

import { HttpStatusCode } from '@ohos/axios'
import { BusinessError, pasteboard } from '@kit.BasicServicesKit'
import { RouteNames } from '../../constant/RouteNames'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../constant/DesignSystem'
import {
  AssetTagDetailParam,
  ToolboxText,
  deleteAssetTag,
  toolboxAssetTagStatusText,
  updateAssetTag
} from './api'
import { markAssetTagChanged } from './ToolboxChangeSignal'
import { AppButton, AppConfirmDialog } from '../../view/common/AppComponents'
import { InfoRow } from '../../view/common/InfoRow'
import { formatDisplayDateTime } from './ShortCodeRules'
import { logger } from '../../utils/Logger'
import { getObjectParam, pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../utils/SafeArea'
import { ThemeRefresh } from '../../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'toolboxAssetTagDetail'

@Builder
export function ToolboxAssetTagDetailBuilder() {
  ToolboxAssetTagDetail()
}

@Component
struct ToolboxAssetTagDetail {
  private param: AssetTagDetailParam | null = null
  @State updating: boolean = false
  @State deleting: boolean = false
  @State pressedKey: string = ''

  // ========== 删除确认弹窗（字段控制器，builder 闭包在 open() 时读取最新文案） ==========
  private deleteDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: ToolboxText.asset_tag_confirm_delete_title,
      message: ToolboxText.asset_tag_confirm_delete_message.replace('%1$s', this.param?.itemName ?? ''),
      isDanger: true,
      confirmText: ToolboxText.asset_tag_action_delete,
      onConfirm: () => {
        this.doDelete()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  aboutToAppear(): void {
    this.param = getObjectParam<AssetTagDetailParam>(RouteNames.TOOLBOX_ASSET_TAG_DETAIL)
    logger.debug(TAG, `详情页挂载 code=${this.param?.code ?? ''} status=${this.param?.status ?? 0}`)
  }

  /** 状态流转动作：正常→寻物中→已寻回→正常（循环，唯一动作） */
  private nextStatusOf(status: number): number {
    if (status === 1) {
      return 2
    }
    if (status === 2) {
      return 0
    }
    return 1
  }

  private nextActionTextOf(status: number): string {
    if (status === 0) {
      return ToolboxText.asset_tag_action_set_lost
    }
    if (status === 1) {
      return ToolboxText.asset_tag_action_set_found
    }
    return ToolboxText.asset_tag_action_set_normal
  }

  private async doChangeStatus(): Promise<void> {
    if (this.updating) {
      return
    }
    const id = this.param?.id ?? 0
    if (id <= 0) {
      logger.warn(TAG, '状态流转中止: 标签 id 缺失')
      return
    }
    this.updating = true
    try {
      const res = await updateAssetTag(id, { status: this.nextStatusOf(this.param?.status ?? 0) })
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `标签状态流转成功 id=${id}`)
        showToastSafety(ToolboxText.asset_tag_toast_status_success)
        markAssetTagChanged()
        pathStack.pop()
      } else {
        logger.warn(TAG, `状态流转被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ToolboxText.asset_tag_error_submit)
      }
    } catch (err) {
      logger.error(TAG, '状态流转异常', err instanceof Error ? err.message : '')
      showToastSafety(ToolboxText.asset_tag_error_submit)
    } finally {
      this.updating = false
    }
  }

  private async doDelete(): Promise<void> {
    if (this.deleting) {
      return
    }
    const id = this.param?.id ?? 0
    if (id <= 0) {
      logger.warn(TAG, '删除中止: 标签 id 缺失')
      return
    }
    this.deleting = true
    try {
      const res = await deleteAssetTag(id)
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `标签删除成功 id=${id}`)
        showToastSafety(ToolboxText.asset_tag_toast_delete_success)
        markAssetTagChanged()
        pathStack.pop()
      } else {
        logger.warn(TAG, `删除被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ToolboxText.asset_tag_error_submit)
      }
    } catch (err) {
      logger.error(TAG, '删除请求异常', err instanceof Error ? err.message : '')
      showToastSafety(ToolboxText.asset_tag_error_submit)
    } finally {
      this.deleting = false
    }
  }

  /** 复制标签链接到剪贴板（失主线索链路的分享起点） */
  private copyTagUrl(): void {
    const url = this.param?.tagUrl ?? ''
    if (url.length === 0) {
      return
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, url)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(ToolboxText.asset_tag_copy_success)
    }).catch((err: BusinessError) => {
      showToastSafety(ToolboxText.asset_tag_copy_failed)
      logger.error(TAG, '标签链接复制失败', `${err.code} ${err.message}`)
    })
  }

  /** 状态 chip 底色（对齐安卓：寻物中 ERROR_CONTAINER / 已寻回 PRIMARY_CONTAINER / 正常 SURFACE_VARIANT） */
  private statusContainerColor(status: number): ResourceColor {
    if (status === 1) {
      return $r('app.color.ERROR_CONTAINER')
    }
    if (status === 2) {
      return $r('app.color.PRIMARY_CONTAINER')
    }
    return $r('app.color.SURFACE_VARIANT')
  }

  private statusContentColor(status: number): ResourceColor {
    if (status === 1) {
      return $r('app.color.ON_ERROR_CONTAINER')
    }
    if (status === 2) {
      return $r('app.color.ON_PRIMARY_CONTAINER')
    }
    return $r('app.color.TEXT_SECONDARY')
  }

  @Builder
  StatusChip(status: number) {
    Text(toolboxAssetTagStatusText(status))
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(this.statusContentColor(status))
      .padding({ left: 8, right: 8, top: 2, bottom: 2 })
      .backgroundColor(this.statusContainerColor(status))
      .borderRadius(DesignDimens.RADIUS_FULL)
  }

  build() {
    NavDestination() {
      ThemeRefresh() {
        Column() {
          // 页面标题 + 删除（对齐安卓 PageHeader：删除 dangerInk + 48 热区）
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

            Text(ToolboxText.asset_tag_detail_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()

            Text(ToolboxText.asset_tag_action_delete)
              .fontSize(DesignFontSize.PAGE_HEADER)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.DANGER_INK'))
              .padding({ left: 12, right: 12, top: 12, bottom: 12 })
              .scale({ x: this.pressedKey === 'delete' ? 0.97 : 1, y: this.pressedKey === 'delete' ? 0.97 : 1 })
              .onTouch((event: TouchEvent) => {
                if (event.type === TouchType.Down) {
                  this.pressedKey = 'delete'
                } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                  this.pressedKey = ''
                }
              })
              .onClick(() => {
                this.deleteDialogController.open()
              })
          }
          .width('100%')
          .height(56)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          Scroll() {
            Column({ space: DesignDimens.CARD_SPACING }) {
              // ---------- 信息卡 ----------
              Column() {
                InfoRow({ label: ToolboxText.asset_tag_item_name_label, value: this.param?.itemName ?? '', valueColor: $r('app.color.TEXT_PRIMARY'), valueTnum: false })
                Row() {
                  Text(ToolboxText.asset_tag_label_status)
                    .fontSize(DesignFontSize.BODY)
                    .fontColor($r('app.color.TEXT_SECONDARY'))
                  Blank()
                  this.StatusChip(this.param?.status ?? 0)
                }
                .width('100%')
                .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
                .alignItems(VerticalAlign.Center)

                InfoRow({ label: ToolboxText.asset_tag_label_code, value: this.param?.code ?? '', valueColor: $r('app.color.TEXT_PRIMARY'), valueTnum: true })
                InfoRow({ label: ToolboxText.asset_tag_label_scans, value: `${this.param?.scanCount ?? 0} 次`, valueColor: $r('app.color.TEXT_GREEN'), valueTnum: true })
                InfoRow({
                  label: ToolboxText.asset_tag_label_last_scanned,
                  value: formatDisplayDateTime(this.param?.lastScannedAt) || ToolboxText.toolbox_value_none,
                  valueColor: $r('app.color.TEXT_PRIMARY'),
                  valueTnum: true
                })
                if ((this.param?.itemDesc ?? '').length > 0) {
                  InfoRow({ label: ToolboxText.asset_tag_item_desc_label, value: this.param?.itemDesc ?? '', valueColor: $r('app.color.TEXT_PRIMARY'), valueTnum: false })
                }

                Text(ToolboxText.asset_tag_privacy_note)
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor($r('app.color.TEXT_SECONDARY'))
                  .width('100%')
                  .margin({ top: DesignDimens.SPACE_XS })
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor($r('app.color.SURFACE'))
              .borderRadius(DesignDimens.CARD_RADIUS)
              .border({ width: 1, color: $r('app.color.OUTLINE') })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: $r('app.color.SHADOW_COLOR_CARD'),
                offsetX: 0,
                offsetY: 2
              })

              // ---------- 状态流转（唯一动作） ----------
              Column() {
                Text(ToolboxText.asset_tag_label_status)
                  .fontSize(DesignFontSize.CARD_TITLE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .margin({ bottom: DesignDimens.SPACE_MD })
                AppButton({
                  text: this.nextActionTextOf(this.param?.status ?? 0),
                  loading: this.updating,
                  click: () => {
                    this.doChangeStatus()
                  }
                })
                .width('100%')
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor($r('app.color.SURFACE'))
              .borderRadius(DesignDimens.CARD_RADIUS)
              .border({ width: 1, color: $r('app.color.OUTLINE') })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: $r('app.color.SHADOW_COLOR_CARD'),
                offsetX: 0,
                offsetY:  2
              })

              // ---------- 标签链接卡 ----------
              Column() {
                Text(ToolboxText.asset_tag_link_label)
                  .fontSize(DesignFontSize.CARD_TITLE)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor($r('app.color.TEXT_PRIMARY'))
                  .margin({ bottom: DesignDimens.SPACE_MD })
                if ((this.param?.tagUrl ?? '').length === 0) {
                  Text(ToolboxText.asset_tag_link_unavailable)
                    .fontSize(DesignFontSize.BODY)
                    .fontColor($r('app.color.TEXT_SECONDARY'))
                    .width('100%')
                    .textAlign(TextAlign.Center)
                    .padding({ top: 16, bottom: 16 })
                } else {
                  Text(this.param?.tagUrl ?? '')
                    .fontSize(DesignFontSize.CAPTION)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .fontColor($r('app.color.TEXT_SECONDARY'))
                    .fontFeature(DesignFontFeature.TNUM)
                    .textAlign(TextAlign.Center)
                    .width('100%')
                  Text(ToolboxText.asset_tag_link_hint)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor($r('app.color.ON_WARNING_CONTAINER'))
                    .textAlign(TextAlign.Center)
                    .width('100%')
                    .margin({ top: DesignDimens.SPACE_XS })
                  AppButton({
                    text: ToolboxText.toolbox_action_copy,
                    click: () => {
                      this.copyTagUrl()
                    }
                  })
                  .width('100%')
                  .margin({ top: DesignDimens.SPACE_MD })
                }
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(DesignDimens.CARD_PADDING)
              .backgroundColor($r('app.color.SURFACE'))
              .borderRadius(DesignDimens.CARD_RADIUS)
              .border({ width: 1, color: $r('app.color.OUTLINE') })
              .shadow({
                radius: DesignElevation.LEVEL_1,
                color: $r('app.color.SHADOW_COLOR_CARD'),
                offsetX: 0,
                offsetY: 2
              })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM + safeBottomVp() })
          }
          .scrollBar(BarState.Off)
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
        .padding({ top: safeTopVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }
}
