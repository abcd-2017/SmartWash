// 设备投递会话页（双向）— 对齐安卓 feature/toolbox/ui/delivery/DeliverySessionPage.kt
//
// 会话信息条（投递码 / 进入地址 / 手动关闭）+ 双向条目流（2-3s 轮询，失败指数退避）
// + 发送区（选图片 / 选文件 / 文本）。点条目：文本先非破坏读内容再「复制并取走」；
// 图片/文件出取走确认（取走即焚）。轮询遇业务码 404（过期/关闭）提示后退出。
//
// 已知坑遵守：
// - 轮询 setTimeout 在 aboutToDisappear 清理；连续失败指数退避封顶 10s（对齐安卓）。
// - 弹窗控制器由结构体字段持有（17c263c）；AppConfirmDialog 确认回调先关窗再由组件关闭。
// - 鸿蒙端图片/文件取走仅做「原子认领 + 提示」，不实现安卓的媒体预览/保存相册（见报告遗留项）。

import { fileIo as fs, picker } from '@kit.CoreFileKit'
import { photoAccessHelper } from '@kit.MediaLibraryKit'
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
  AddDeliveryTextForm,
  closeDeliverySession,
  DELIVERY_BIZ_CONFLICT,
  DELIVERY_BIZ_GONE,
  DELIVERY_MAX_FILE_BYTES,
  DELIVERY_MAX_POLL_INTERVAL_MS,
  DELIVERY_MAX_TEXT_LENGTH,
  DELIVERY_POLL_INTERVAL_MS,
  DeliveryItemKind,
  DeliveryItemVo,
  DeliverySessionVo,
  DeliveryText,
  fetchDeliverySessionDetail,
  fetchDeliveryTextContent,
  formatRemaining,
  kindFromCode,
  publishDeliveryFile,
  publishDeliveryText,
  remainingSeconds,
  takeDeliveryItem
} from '../../DeliveryApi'
import { DeliveryItemRow } from './DeliveryComponents'
import { AppButton, AppConfirmDialog, EmptyState, SkeletonList } from '../../../../view/common/AppComponents'
import { logger } from '../../../../utils/Logger'
import { getStringParam, pathStack } from '../../../../utils/PathStackUtil'
import { showToastSafety } from '../../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../../utils/SafeArea'
import { ThemeRefresh } from '../../../../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'deliverySession'

@Builder
export function DeliverySessionBuilder() {
  DeliverySessionPage()
}

@Component
struct DeliverySessionPage {
  @State code: string = ''
  @State session: DeliverySessionVo | null = null
  @State items: Array<DeliveryItemVo> = []
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  @State pollFailed: boolean = false
  @State draft: string = ''
  @State sending: boolean = false
  @State sendError: string = ''
  // 预览/取走动作 loading（对齐安卓 previewAction）
  @State actionLoading: boolean = false
  @State closing: boolean = false
  @State draftFocused: boolean = false

  private pollTimerId: number = -1
  private pollInterval: number = DELIVERY_POLL_INTERVAL_MS
  private isPolling: boolean = false
  private goneHandled: boolean = false
  // 页面销毁标记：防止在途 poll 完成后又排下一轮定时器（定时器泄漏）
  private isDestroyed: boolean = false

  // 关闭会话确认弹窗（静态文案，字段持有）
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
  // 动态文案弹窗（文本预览 / 媒体取走确认）：每次打开重建并持有字段
  private actionDialogController: CustomDialogController | null = null

  aboutToAppear(): void {
    const param = getStringParam(RouteNames.TOOLBOX_DELIVERY_SESSION)
    this.code = param !== null ? param : ''
    if (this.code.length === 0) {
      logger.warn(TAG, '会话码缺失，进入错误态')
      this.isFirstError = true
      this.isLoadingFirst = false
      return
    }
    logger.debug(TAG, `会话页挂载 code=${this.code}`)
    this.startPolling()
  }

  aboutToDisappear(): void {
    this.isDestroyed = true
    this.clearPollTimer()
    this.closeDialogController.close()
    if (this.actionDialogController !== null) {
      this.actionDialogController.close()
      this.actionDialogController = null
    }
  }

  private clearPollTimer(): void {
    if (this.pollTimerId !== -1) {
      clearTimeout(this.pollTimerId)
      this.pollTimerId = -1
    }
  }

  /** 启动/重启轮询（动作完成后立即补拉一次，退避一并复位） */
  private startPolling(): void {
    this.clearPollTimer()
    this.pollInterval = DELIVERY_POLL_INTERVAL_MS
    this.schedulePoll(0)
  }

  private schedulePoll(delay: number): void {
    if (this.isDestroyed) {
      return
    }
    this.clearPollTimer()
    this.pollTimerId = setTimeout(() => {
      this.poll()
    }, delay)
  }

  /** 拉取会话详情 + 条目流；404 = 会话失效立即退出，其余失败退避重试 */
  private async poll(): Promise<void> {
    if (this.isPolling) {
      return
    }
    this.isPolling = true
    try {
      const res = await fetchDeliverySessionDetail(this.code)
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        this.session = res.data.session ?? null
        this.items = res.data.items ?? []
        this.isLoadingFirst = false
        this.isFirstError = false
        this.pollFailed = false
        this.pollInterval = DELIVERY_POLL_INTERVAL_MS
        // 防御：倒计时归零即刻退出，不等下一次 404（后端读路径到期即失效）
        const remain = remainingSeconds(this.session?.expireAt ?? null)
        if (remain !== null && remain <= 0) {
          this.markSessionGone(DeliveryText.toolbox_delivery_session_gone_toast)
          return
        }
        this.schedulePoll(this.pollInterval)
      } else if (res.code === DELIVERY_BIZ_GONE) {
        this.markSessionGone(res.message ?? DeliveryText.toolbox_delivery_session_gone_toast)
      } else {
        this.onPollFailure(res.message)
        this.schedulePoll(this.nextBackoff())
      }
    } catch (err) {
      logger.error(TAG, '条目流请求异常', err instanceof Error ? err.message : '')
      this.onPollFailure(null)
      this.schedulePoll(this.nextBackoff())
    } finally {
      this.isPolling = false
    }
  }

  private nextBackoff(): number {
    const next = this.pollInterval * 2
    this.pollInterval = next > DELIVERY_MAX_POLL_INTERVAL_MS ? DELIVERY_MAX_POLL_INTERVAL_MS : next
    return this.pollInterval
  }

  private onPollFailure(message: string | null | undefined): void {
    if (this.isLoadingFirst) {
      logger.error(TAG, `首次拉取条目流失败 message=${message ?? ''}`)
      this.isFirstError = true
      this.isLoadingFirst = false
      return
    }
    this.pollFailed = true
  }

  /** 会话失效：提示后退出（不与全局 404/401 语义混淆） */
  private markSessionGone(message: string): void {
    if (this.goneHandled) {
      return
    }
    this.goneHandled = true
    this.clearPollTimer()
    logger.warn(TAG, `会话失效，退出会话页 message=${message}`)
    showToastSafety(message)
    pathStack.pop()
  }

  /** 发送文本条目（10 万字符上限端侧前置校验） */
  async sendText(): Promise<void> {
    if (this.sending) {
      return
    }
    const text = this.draft.trim()
    if (text.length === 0) {
      this.sendError = DeliveryText.toolbox_delivery_error_text_empty
      return
    }
    if (text.length > DELIVERY_MAX_TEXT_LENGTH) {
      this.sendError = DeliveryText.toolbox_delivery_error_text_too_long
      return
    }
    const form: AddDeliveryTextForm = { textContent: text }
    this.sendError = ''
    this.sending = true
    try {
      const res = await publishDeliveryText(this.code, form)
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `文本条目发布成功 code=${this.code} length=${text.length}`)
        this.draft = ''
        this.startPolling()
      } else {
        logger.warn(TAG, `文本发布被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.sendError = res.message ?? DeliveryText.toolbox_delivery_error_send
      }
    } catch (err) {
      logger.error(TAG, '文本发布请求异常', err instanceof Error ? err.message : '')
      this.sendError = DeliveryText.toolbox_delivery_error_send
    } finally {
      this.sending = false
    }
  }

  /** 选图片（相册选择器）→ 读为 ArrayBuffer → multipart 发布 */
  async pickImage(): Promise<void> {
    if (this.sending) {
      return
    }
    try {
      const photoPicker = new photoAccessHelper.PhotoViewPicker()
      const result = await photoPicker.select({
        MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
        maxSelectNumber: 1
      })
      const uri = result.photoUris.length > 0 ? result.photoUris[0] : ''
      if (uri.length === 0) {
        return
      }
      await this.publishPicked(uri, DeliveryItemKind.IMAGE, `delivery_image_${Date.now()}.jpg`, 'image/jpeg')
    } catch (err) {
      logger.error(TAG, '选图片流程异常', err instanceof Error ? err.message : '')
      this.sendError = DeliveryText.toolbox_delivery_error_send
    }
  }

  /** 选文件（文档选择器）→ 读为 ArrayBuffer → multipart 发布 */
  async pickFile(): Promise<void> {
    if (this.sending) {
      return
    }
    try {
      const documentPicker = new picker.DocumentViewPicker()
      const result = await documentPicker.select({
        maxSelectNumber: 1
      })
      const uri = result.length > 0 ? result[0] : ''
      if (uri.length === 0) {
        return
      }
      const fileName = uri.substring(uri.lastIndexOf('/') + 1)
      await this.publishPicked(uri, DeliveryItemKind.FILE, fileName, 'application/octet-stream')
    } catch (err) {
      logger.error(TAG, '选文件流程异常', err instanceof Error ? err.message : '')
      this.sendError = DeliveryText.toolbox_delivery_error_send
    }
  }

  /** 读取所选文件并发布（单文件 20MB 上限端侧前置校验） */
  private async publishPicked(uri: string, kind: DeliveryItemKind, fileName: string,
    mimeType: string): Promise<void> {
    let buffer: ArrayBuffer | null = null
    try {
      const stat = fs.statSync(uri)
      if (stat.size > DELIVERY_MAX_FILE_BYTES) {
        this.sendError = DeliveryText.toolbox_delivery_error_file_too_large
        return
      }
      const file = fs.openSync(uri, fs.OpenMode.READ_ONLY)
      try {
        buffer = new ArrayBuffer(stat.size)
        fs.readSync(file.fd, buffer)
      } finally {
        fs.closeSync(file)
      }
    } catch (err) {
      logger.error(TAG, '读取所选文件失败', err instanceof Error ? err.message : '')
      this.sendError = DeliveryText.toolbox_delivery_error_send
      return
    }
    if (buffer === null) {
      return
    }
    this.sendError = ''
    this.sending = true
    try {
      const res = await publishDeliveryFile(this.code, kind, fileName, mimeType, buffer)
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `文件条目发布成功 code=${this.code} kind=${kind} fileName=${fileName}`)
        this.startPolling()
      } else {
        logger.warn(TAG, `文件发布被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.sendError = res.message ?? DeliveryText.toolbox_delivery_error_send
      }
    } catch (err) {
      logger.error(TAG, '文件发布请求异常', err instanceof Error ? err.message : '')
      this.sendError = DeliveryText.toolbox_delivery_error_send
    } finally {
      this.sending = false
    }
  }

  /** 点条目：文本先非破坏读内容再出「复制并取走」；图片/文件出取走确认 */
  async openItem(item: DeliveryItemVo): Promise<void> {
    if ((item.taken ?? 0) !== 0 || this.actionLoading) {
      return
    }
    const itemId = item.id ?? 0
    if (itemId <= 0) {
      return
    }
    if (kindFromCode(item.kind ?? 1) === DeliveryItemKind.TEXT) {
      this.actionLoading = true
      try {
        const res = await fetchDeliveryTextContent(this.code, itemId)
        if (res.code === HttpStatusCode.Ok) {
          this.openTextPreview(item, res.data?.textContent ?? '')
        } else {
          showToastSafety(res.message, DeliveryText.toolbox_delivery_preview_error)
        }
      } catch (err) {
        logger.error(TAG, '文本内容读取异常', err instanceof Error ? err.message : '')
        showToastSafety(DeliveryText.toolbox_delivery_preview_error)
      } finally {
        this.actionLoading = false
      }
    } else {
      this.openTakeConfirm(item)
    }
  }

  /** 文本预览弹窗：正文 + 「复制并取走」（对齐安卓 PreviewState.TextReady） */
  private openTextPreview(item: DeliveryItemVo, text: string): void {
    const itemId = item.id ?? 0
    const controller: CustomDialogController = new CustomDialogController({
      builder: AppConfirmDialog({
        title: DeliveryText.toolbox_delivery_kind_text,
        message: text.length > 0 ? text : DeliveryText.toolbox_delivery_preview_error,
        confirmText: DeliveryText.toolbox_delivery_action_copy_take,
        cancelText: '关闭',
        onConfirm: () => {
          this.copyTextAndTake(itemId, text)
        },
        onDismiss: () => {
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true
    })
    this.actionDialogController = controller
    controller.open()
  }

  /** 媒体取走确认弹窗（取走即焚，对齐安卓 ConfirmImage/ConfirmFile 的确认语义） */
  private openTakeConfirm(item: DeliveryItemVo): void {
    const itemId = item.id ?? 0
    const controller: CustomDialogController = new CustomDialogController({
      builder: AppConfirmDialog({
        title: DeliveryText.toolbox_delivery_confirm_take_title,
        message: DeliveryText.toolbox_delivery_confirm_take_message,
        confirmText: DeliveryText.toolbox_delivery_action_take,
        cancelText: '取消',
        onConfirm: () => {
          this.takeItem(itemId)
        },
        onDismiss: () => {
        }
      }),
      autoCancel: true,
      alignment: DialogAlignment.Center,
      customStyle: true
    })
    this.actionDialogController = controller
    controller.open()
  }

  /** 复制正文并取走（认领响应正文优先，兜底预览正文） */
  async copyTextAndTake(itemId: number, previewText: string): Promise<void> {
    if (this.actionLoading) {
      return
    }
    this.actionLoading = true
    try {
      const res = await takeDeliveryItem(this.code, itemId)
      if (res.code === HttpStatusCode.Ok) {
        const text = res.data?.textContent ?? previewText
        const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text)
        pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
          showToastSafety(DeliveryText.toolbox_delivery_toast_copy_success)
        }).catch((err: BusinessError) => {
          logger.error(TAG, '文本复制失败', `${err.code} ${err.message}`)
          showToastSafety(DeliveryText.toolbox_delivery_toast_copy_success)
        })
        this.startPolling()
      } else {
        this.handleTakeFailure(res.code ?? 0, res.message)
      }
    } catch (err) {
      logger.error(TAG, '取走请求异常', err instanceof Error ? err.message : '')
      showToastSafety(DeliveryText.toolbox_delivery_preview_error)
    } finally {
      this.actionLoading = false
    }
  }

  /** 取走条目（原子认领 + 焚毁） */
  async takeItem(itemId: number): Promise<void> {
    if (this.actionLoading) {
      return
    }
    this.actionLoading = true
    try {
      const res = await takeDeliveryItem(this.code, itemId)
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `条目取走成功 itemId=${itemId}`)
        this.startPolling()
      } else {
        this.handleTakeFailure(res.code ?? 0, res.message)
      }
    } catch (err) {
      logger.error(TAG, '取走请求异常', err instanceof Error ? err.message : '')
      showToastSafety(DeliveryText.toolbox_delivery_preview_error)
    } finally {
      this.actionLoading = false
    }
  }

  /** 取走失败：409 = 已被取走；404 = 会话/条目失效，提示后退出 */
  private handleTakeFailure(code: number, message: string | null | undefined): void {
    if (code === DELIVERY_BIZ_CONFLICT) {
      showToastSafety(message ?? DeliveryText.toolbox_delivery_item_taken_toast)
      this.startPolling()
      return
    }
    if (code === DELIVERY_BIZ_GONE) {
      this.markSessionGone(message ?? DeliveryText.toolbox_delivery_session_gone_toast)
      return
    }
    showToastSafety(message ?? DeliveryText.toolbox_delivery_preview_error)
  }

  /** 关闭会话（读路径立即失效；成功后提示并退出） */
  async closeSession(): Promise<void> {
    if (this.closing) {
      return
    }
    this.closing = true
    try {
      const res = await closeDeliverySession(this.code)
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `会话关闭成功 code=${this.code}`)
        this.clearPollTimer()
        showToastSafety(DeliveryText.toolbox_delivery_toast_close_success)
        pathStack.pop()
      } else {
        logger.warn(TAG, `会话关闭被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, DeliveryText.toolbox_delivery_error_close)
      }
    } catch (err) {
      logger.error(TAG, '关闭请求异常', err instanceof Error ? err.message : '')
      showToastSafety(DeliveryText.toolbox_delivery_error_close)
    } finally {
      this.closing = false
    }
  }

  /** 复制投递码 */
  private copyCode(): void {
    const code = this.session?.code ?? this.code
    if (code.length === 0) {
      return
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, code)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(DeliveryText.toolbox_delivery_toast_code_copied)
    }).catch((err: BusinessError) => {
      logger.error(TAG, '投递码复制失败', `${err.code} ${err.message}`)
      showToastSafety(DeliveryText.toolbox_delivery_toast_code_copied)
    })
  }

  /** 页头倒计时文案（轮询驱动刷新，秒级精度可接受） */
  private headerCountdown(): string {
    const session = this.session
    if (session === null) {
      return ''
    }
    const remain = remainingSeconds(session.expireAt ?? null)
    if (remain === null) {
      return ''
    }
    return remain <= 0 ? DeliveryText.toolbox_delivery_session_expired
      : `${DeliveryText.toolbox_delivery_countdown_prefix}${formatRemaining(remain)}`
  }

  /** 会话信息条（对齐安卓 SessionStrip） */
  @Builder
  SessionStrip() {
    if (this.session !== null) {
      Row() {
        Column() {
            SymbolGlyph($r('sys.symbol.computer'))
            .fontSize(20)
            .fontColor([DesignColor.ICON_FOREGROUND_BLUE])
        }
        .width(36)
        .height(36)
        .borderRadius(DesignDimens.RADIUS_MD)
        .backgroundColor(DesignColor.ICON_CONTAINER_BLUE)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)

        Column() {
          Text(this.session?.code ?? '')
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Text((this.session?.enterUrl ?? '').length > 0 ? (this.session?.enterUrl ?? '')
            : DeliveryText.toolbox_delivery_url_fallback)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_TERTIARY)
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

        Text(DeliveryText.toolbox_delivery_action_close_session)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.DANGER_INK)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: 12, bottom: 12 })
          .onClick(() => {
            this.closeDialogController.open()
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.SPACE_SM, top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
      .onClick(() => {
        this.copyCode()
      })
    }
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 页头：返回 + 标题 + 倒计时（对齐安卓 PageHeader actions）
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

          Text(DeliveryText.toolbox_delivery_session_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)

          Blank()

          Text(this.headerCountdown())
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor(DesignColor.ONGOING_INK)
            .fontFeature(DesignFontFeature.TNUM)
            .margin({ right: DesignDimens.SPACE_XS })
        }
        .width('100%')
        .height(56)
        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
        .alignItems(VerticalAlign.Center)

        // 会话信息条（卡样：1px 描边 + level1 阴影）
        Column() {
          this.SessionStrip()
        }
        .width('100%')
        .backgroundColor(DesignColor.SURFACE)
        .borderRadius(DesignDimens.CARD_RADIUS)
        .border({ width: 1, color: DesignColor.OUTLINE })
        .shadow({
          radius: DesignElevation.LEVEL_1,
          color: DesignColor.SHADOW_COLOR_CARD,
          offsetX: 0,
          offsetY: 2
        })
        .margin({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })

        // 轮询失败横幅（列表非空时）
        if (this.pollFailed && this.items.length > 0) {
          Text(DeliveryText.toolbox_delivery_poll_error)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.DANGER_INK)
            .width('100%')
            .textAlign(TextAlign.Center)
            .padding({ top: DesignDimens.SPACE_XXS, bottom: DesignDimens.SPACE_XXS })
        }

        // 内容区：首刷骨架 / 首刷失败重试 / 空态 / 条目流
        if (this.isLoadingFirst) {
          SkeletonList({ rows: 4 })
            .layoutWeight(1)
        } else if (this.isFirstError && this.items.length === 0) {
          Column() {
            EmptyState({ message: DeliveryText.toolbox_delivery_error_load }) {
              Column() {
                AppButton({
                  text: DeliveryText.toolbox_delivery_action_retry,
                  click: () => {
                    this.isLoadingFirst = true
                    this.isFirstError = false
                    this.startPolling()
                  }
                })
              }
              .margin({ top: DesignDimens.SPACE_MD })
            }
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (this.items.length === 0) {
          Column() {
            EmptyState({ message: DeliveryText.toolbox_delivery_empty_message })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          List() {
            ForEach(this.items, (item: DeliveryItemVo) => {
              ListItem() {
                DeliveryItemRow({
                  item: item,
                  onRowClick: () => {
                    this.openItem(item)
                  }
                })
              }
            }, (item: DeliveryItemVo) => `${item.id ?? 0}`)
          }
          .width('100%')
          .layoutWeight(1)
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          .divider({ strokeWidth: 0.5, color: DesignColor.HAIRLINE })
        }

        // ---------- 发送区（浮层：顶线 + 选取入口 + 文本输入 + 发送） ----------
        Column() {
          Divider()
            .color(DesignColor.HAIRLINE)
            .strokeWidth(0.5)

          if (this.sending) {
            Text(DeliveryText.toolbox_delivery_sending)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_TERTIARY)
              .width('100%')
              .padding({ top: DesignDimens.SPACE_XS })
          } else if (this.sendError.length > 0) {
            Text(this.sendError)
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.ERROR)
              .width('100%')
              .padding({ top: DesignDimens.SPACE_XS })
          }

          Row() {
            // 选图片（44vp 热区 + 36vp 图标容器）
            Column() {
              SymbolGlyph($r('sys.symbol.shutter_photo'))
                .fontSize(20)
                .fontColor([DesignColor.ICON_FOREGROUND_BLUE])
            }
            .width(36)
            .height(36)
            .borderRadius(DesignDimens.RADIUS_MD)
            .backgroundColor(DesignColor.ICON_CONTAINER_BLUE)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              this.pickImage()
            })
            .padding({ top: 4, bottom: 4 })

            Column() {
              SymbolGlyph($r('sys.symbol.doc'))
                .fontSize(20)
                .fontColor([DesignColor.ICON_FOREGROUND_BLUE])
            }
            .width(36)
            .height(36)
            .borderRadius(DesignDimens.RADIUS_MD)
            .backgroundColor(DesignColor.ICON_CONTAINER_BLUE)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => {
              this.pickFile()
            })
            .padding({ top: 4, bottom: 4 })
            .margin({ left: DesignDimens.SPACE_XXS })

            TextInput({ text: this.draft, placeholder: DeliveryText.toolbox_delivery_send_hint })
              .height(48)
              .fontSize(DesignFontSize.BODY)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .borderWidth(1)
              .borderColor(this.draftFocused ? DesignColor.PRIMARY : DesignColor.OUTLINE)
              .placeholderColor(DesignColor.TEXT_TERTIARY)
              .placeholderFont({ size: DesignFontSize.BODY_SMALL })
              .layoutWeight(1)
              .margin({ left: DesignDimens.SPACE_XS })
              .onFocus(() => {
                this.draftFocused = true
              })
              .onBlur(() => {
                this.draftFocused = false
              })
              .onChange((value: string) => {
                this.draft = value
                if (this.sendError.length > 0) {
                  this.sendError = ''
                }
              })

            // 发送圆钮（44vp；草稿空或发送中降透明度 0.5）
            Column() {
              SymbolGlyph($r('sys.symbol.paperplane_fill'))
                .fontSize(20)
                .fontColor([DesignColor.ON_PRIMARY])
            }
            .width(44)
            .height(44)
            .borderRadius(22)
            .backgroundColor(DesignColor.PRIMARY)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .opacity(this.draft.trim().length > 0 && !this.sending ? 1 : 0.5)
            .onClick(() => {
              if (this.draft.trim().length > 0 && !this.sending) {
                this.sendText()
              }
            })
            .margin({ left: DesignDimens.SPACE_XS })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_SM + safeBottomVp() })
        }
        .width('100%')
        .backgroundColor(DesignColor.SURFACE)
        .shadow({
          radius: DesignElevation.LEVEL_1,
          color: DesignColor.SHADOW_COLOR_CARD,
          offsetX: 0,
          offsetY: 2
        })
      }
      .width('100%')
      .height('100%')
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
  }
}
