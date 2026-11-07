// 短链详情页 — 对齐安卓 feature/toolbox/ui/detail/ShortCodeDetailPage.kt
//
// 基础字段由列表路由携带（getObjectParam，禁止二次请求）；二维码本地生成（内容为完整 shortUrl）；
// 复制短链 / 打开链接（公开码直开 shortUrl，私有码经 resolve 取 target 再开——计一次点击属预期）；
// 续期（PUT expireAt，无永久选项）/ 删除（DELETE，成功以 code===200 为准）+ 访问明细分页（/stats）。
// 一镜到底：NavDestination.customTransition 近似（信息卡展开/收拢，见 ToolboxCardTransition）。
//
// 红线：resolve 有点击统计副作用，仅用于「私有码打开」，禁止用于详情刷新/列表。

import { HttpStatusCode } from '@ohos/axios'
import { BusinessError, pasteboard } from '@kit.BasicServicesKit'
import { common, Want } from '@kit.AbilityKit'
import { image } from '@kit.ImageKit'
import { generateBarcode, scanCore } from '@kit.ScanKit'
import { RouteNames } from '../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../constant/DesignSystem'
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
import { markShortCodeChanged } from './ToolboxChangeSignal'
import { toolboxCardTransition } from './ToolboxCardTransition'
import { ToolboxDatePickerDialog } from './ToolboxDatePickerDialog'
import { ToolboxRenewDialog } from './ToolboxRenewDialog'
import { AppButton, AppButtonVariant, AppConfirmDialog, SkeletonBlock, SkeletonList } from '../../view/common/AppComponents'
import { logger } from '../../utils/Logger'
import { PagingController } from '../../utils/PagingUtil'
import { getObjectParam, pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../utils/SafeArea'
import BuildProfile from 'entry/BuildProfile'
import { createQrPixelMap } from './QrFallback'
import { ThemeRefresh } from '../../view/common/ThemeRefresh'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'toolboxShortCodeDetail'

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
  // 追加失败内联错误态（对齐安卓 footer 内联错误可重试）
  @State statsAppendError: boolean = false
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
  // 一镜到底近似转场：信息卡展开/收拢
  @State cardEntered: boolean = false
  // 按压反馈（对齐安卓 pressable scale 0.97）
  @State pressedKey: string = ''
  // 转场兜底定时器（customTransition 事件未触发时保证信息卡可见）
  private fallbackTimer: number = -1

  // ========== 弹窗控制器 ==========
  // 控制器必须由结构体字段持有（对齐 DivAskPage.ets:244 / SubscriptionDetailPage.ets:313）：
  // 在方法内临时 new 的控制器，框架不会把 controller 注入弹窗内容实例，弹窗内 this.controller
  // 为 undefined，点击「确定/删除」即抛「Cannot read property close of undefined」jscrash。
  // builder 为闭包，入参（续期预设/日期/短码/loading）在 open() 时读取，保证内容最新。
  private renewDialogController: CustomDialogController = new CustomDialogController({
    builder: ToolboxRenewDialog({
      preset: this.renewPreset,
      customDate: this.renewDate ?? "",
      loading: this.renewing,
      onPresetChange: (preset: ExpiryPreset): void => {
        this.renewPreset = preset
      },
      // 日期选择器由本页持有（@CustomDialog 内不能再挂控制器）：把弹窗的回填回调转交本方法
      onPickDate: (onPicked: (date: string) => void): void => {
        this.openRenewDatePicker(onPicked)
      },
      // 选定后回写本页（供 doRenew 读取）
      onDatePicked: (date: string): void => {
        this.renewDate = date
      },
      onConfirm: (): void => {
        this.doRenew()
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

  // 续期自定义日期弹窗的「选中回填回调」：由续期弹窗经 onPickDate 传入，
  // 日期弹窗确定时回调续期弹窗更新其内部状态（@CustomDialog 内容不被父状态重建）
  private renewDatePickedCallback: ((date: string) => void) | null = null

  private renewDatePickerController: CustomDialogController = new CustomDialogController({
    builder: ToolboxDatePickerDialog({
      initialDate: this.renewDate ?? "",
      title: ToolboxText.toolbox_expiry_custom_pick,
      onConfirm: (date: string): void => {
        this.renewDate = date
        const callback = this.renewDatePickedCallback
        if (callback !== null) {
          this.renewDatePickedCallback = null
          callback(date)
        }
      },
      onDismiss: (): void => {
        this.renewDatePickedCallback = null
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  private deleteDialogController: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: ToolboxText.toolbox_confirm_delete_title,
      message: toolboxConfirmDeleteMessage(this.param?.code ?? ""),
      // 危险操作确认键走 dangerInk（对齐安卓 AppConfirmDialog(isDanger = true)）
      isDanger: true,
      confirmText: ToolboxText.toolbox_action_delete,
      onConfirm: () => {
        this.doDelete()
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear(): Promise<void> {
    // 基础字段由列表路由携带（红线：禁止经 resolve 拉取详情——resolve 会计点击）
    this.param = getObjectParam<ToolboxShortCodeDetailParam>(RouteNames.TOOLBOX_SHORT_CODE_DETAIL)
    // 兜底定时器：customTransition 事件未触发时也要让信息卡展开（问题15观感：
    // 600ms 才出现像"没做转场"，缩到 120ms 保证动画在转场期内接管）
    logger.debug(TAG, `详情页挂载 code=${this.param?.code ?? ''} 公开=${this.isPublicCode()}`)
    this.fallbackTimer = setTimeout(() => {
      if (!this.cardEntered) {
        this.cardEntered = true
      }
    }, 120)
    await this.loadQrCode()
    await this.fetchStats(1)
    this.statsLoadingFirst = false
  }

  aboutToDisappear(): void {
    if (this.fallbackTimer !== -1) {
      clearTimeout(this.fallbackTimer)
      this.fallbackTimer = -1
    }
  }

  private getShortUrl(): string {
    const carried = this.param?.shortUrl ?? ""
    if (carried.length > 0) {
      return carried
    }
    // 深链 smartwash://t/{code} 入口只带 code（见 DeepLinkRegistry.resolveShortCodeDetail），
    // 不带 shortUrl 会让本地二维码生成必然失败。这里用与后端 shortUrlOf 同构的
    // `{public-base-url}/web/t/{code}` 兜底（public-base-url 未配置时后端同样回落到请求域名）。
    const code = this.param?.code ?? ""
    if (code.length === 0) {
      return ""
    }
    return `${BuildProfile.BASE_URL}/web/t/${code}`
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
      logger.warn(TAG, 'shortUrl 缺失，二维码进入失败态')
      this.qrFailed = true
      this.qrLoading = false
      return
    }
    this.qrLoading = true
    this.qrFailed = false
    // 问题15：部分设备/系统版本对扩展参数（level/margin）校验更严会整单抛错，
    // 两段降级——先全参生成，失败后用最小参数集重试，仍失败才进可重试失败态
    try {
      const pixelMap: image.PixelMap = await generateBarcode.createBarcode(url, {
        scanType: scanCore.ScanType.QR_CODE,
        width: 512,
        height: 512,
        level: generateBarcode.ErrorCorrectionLevel.LEVEL_M,
        margin: 2
      })
      this.qrPixelMap = pixelMap
      logger.debug(TAG, '二维码生成成功')
    } catch (err) {
      try {
        const fallbackMap: image.PixelMap = await generateBarcode.createBarcode(url, {
          scanType: scanCore.ScanType.QR_CODE,
          width: 512,
          height: 512
        })
        this.qrPixelMap = fallbackMap
      } catch (scanKitErr) {
        // ScanKit 在模拟器等环境直接抛 "Emulator is not supported"（两段降级全失败）；
        // 用纯 ArkTS 兜底编码器出图，保证任何环境都有二维码可扫（见 QrFallback 注释）
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

  /** 访问明细分页拉取 */
  async fetchStats(page: number): Promise<boolean> {
    const id = this.param?.id ?? 0
    if (id <= 0) {
      logger.warn(TAG, '访问明细拉取中止: 短码 id 缺失')
      return false
    }
    try {
      const res = await pageShortCodeStats(id, page, TOOLBOX_PAGE_SIZE)
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        const records: Array<ShortCodeStatsVo> = res.data.records
        if (records.length === 0) {
          logger.debug(TAG, `访问明细封底 page=${page} 本页无数据`)
          return false
        }
        if (page === 1) {
          this.statsList = []
          this.statsError = false
        }
        this.statsList.push(...records)
        this.statsAppendError = false
        logger.debug(TAG, `访问明细加载成功 page=${page} count=${records.length}`)
        return true
      }
      if (page === 1) {
        // 首刷失败：无数据走整卡错误态，有数据走 footer 内联错误（对齐安卓）
        logger.warn(TAG, `访问明细首刷失败 page=1 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.statsError = true
        showToastSafety(res.message, ToolboxText.toolbox_stats_error_load)
        return false
      }
      // 追加失败：置内联错误态并 reject（分页器记失败页不封底，footer 重试重拉同一页）
      logger.warn(TAG, `访问明细追加失败 page=${page} code=${res.code ?? 0} msg=${res.message ?? ''}`)
      this.statsAppendError = true
      showToastSafety(res.message, ToolboxText.toolbox_stats_error_load)
      throw new Error(ToolboxText.toolbox_stats_error_load)
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：与「追加失败」同语义（reject 供分页器记失败页）；
      // 首刷则进错误态，避免 statsLoadingFirst 卡死明细骨架常驻
      if (page === 1) {
        this.statsError = true
        showToastSafety("网络异常，请稍后重试")
        return false
      }
      this.statsAppendError = true
      showToastSafety("网络异常，请稍后重试")
      throw new Error(ToolboxText.toolbox_stats_error_load)
    }
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
    }).catch((err: BusinessError) => {
      // 复制失败与「找不到浏览器」是两种失败，各用各的文案（原复用 open_failed 属误导）
      showToastSafety(ToolboxText.toolbox_copy_failed)
      logger.error(TAG, '短链复制到剪贴板失败', `${err.code} ${err.message}`)
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
        logger.debug(TAG, '私有码解析成功，打开目标链接')
        this.openInBrowser(res.data.target ?? "")
      } else {
        logger.warn(TAG, `私有码解析被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ToolboxText.toolbox_resolve_failed)
      }
    } catch (err) {
      logger.error(TAG, '私有码解析异常', err instanceof Error ? err.message : '')
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
      // startAbility 返回 Promise：异步 reject（无浏览器/参数非法）try/catch 抓不到，必须 .catch 兜底
      context.startAbility(want).catch(() => {
        showToastSafety(ToolboxText.toolbox_open_failed)
      })
    } catch (err) {
      logger.error(TAG, '浏览器拉起失败', err instanceof Error ? err.message : '')
      showToastSafety(ToolboxText.toolbox_open_failed)
    }
  }

  /** 打开续期弹窗（字段控制器，builder 闭包在 open() 时读取最新 preset/日期） */
  private openRenewDialog(): void {
    this.renewDialogController.open()
  }

  /**
   * 打开续期自定义日期弹窗（字段控制器）。
   * @param onPicked 选定后的回填回调（转交续期弹窗更新其内部自定义日期显示）
   */
  private openRenewDatePicker(onPicked: (date: string) => void): void {
    this.renewDatePickedCallback = onPicked
    this.renewDatePickerController.open()
  }

  /** 续期（PUT expireAt；预设 7 天 / 30 天 / 自定义日期；无永久选项） */
  private async doRenew(): Promise<void> {
    if (this.renewing) {
      logger.debug(TAG, '续期进行中，忽略重复确认')
      return
    }
    const id = this.param?.id ?? 0
    if (id <= 0) {
      logger.warn(TAG, '续期中止: 短码 id 缺失')
      return
    }
    if (this.renewPreset === ExpiryPreset.CUSTOM &&
      (this.renewDate === null || this.renewDate.length === 0)) {
      logger.warn(TAG, '续期校验拒绝: 自定义有效期未选日期')
      showToastSafety(ToolboxText.toolbox_error_custom_date_required)
      return
    }
    this.renewing = true
    try {
      const res = await updateShortCode(id, {
        expireAt: expireAtFor(this.renewPreset, this.renewDate, new Date()) ?? ""
      })
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `短链续期成功 id=${id} preset=${this.renewPreset}`)
        showToastSafety(ToolboxText.toolbox_toast_renew_success)
        // 置变更信号：列表 onShown 据此重拉（对齐安卓 notifyShortCodeChanged）
        markShortCodeChanged()
        this.renewDialogController.close()
        pathStack.pop()
      } else {
        logger.warn(TAG, `续期被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ToolboxText.toolbox_error_submit)
      }
    } catch (err) {
      logger.error(TAG, '续期请求异常', err instanceof Error ? err.message : '')
      showToastSafety(ToolboxText.toolbox_error_submit)
    } finally {
      this.renewing = false
    }
  }

  /** 打开删除确认弹窗（字段控制器，builder 闭包在 open() 时读取最新短码文案） */
  private openDeleteDialog(): void {
    this.deleteDialogController.open()
  }

  /** 删除（DELETE；后端 data=null，成功一律以 code===200 判定） */
  private async doDelete(): Promise<void> {
    if (this.deleting) {
      logger.debug(TAG, '删除进行中，忽略重复确认')
      return
    }
    const id = this.param?.id ?? 0
    if (id <= 0) {
      logger.warn(TAG, '删除中止: 短码 id 缺失')
      return
    }
    this.deleting = true
    try {
      const res = await deleteShortCode(id)
      // 红线：DELETE 响应 data 为 null，只判 code
      if (res.code === HttpStatusCode.Ok) {
        logger.info(TAG, `短链删除成功 id=${id}`)
        showToastSafety(ToolboxText.toolbox_toast_delete_success)
        // 置变更信号：列表 onShown 据此重拉（对齐安卓 notifyShortCodeChanged）
        markShortCodeChanged()
        pathStack.pop()
      } else {
        logger.warn(TAG, `删除被拒 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, ToolboxText.toolbox_error_submit)
      }
    } catch (err) {
      logger.error(TAG, '删除请求异常', err instanceof Error ? err.message : '')
      showToastSafety(ToolboxText.toolbox_error_submit)
    } finally {
      this.deleting = false
    }
  }

  /**
   * 信息行（对齐安卓 InfoRow：行高 56、标签 bodySmall 12 textSecondary、值 bodySmall 12）。
   * @param monospace 值是否走 tabular numbers（短码/点击数/时间等数字槽位）
   */
  @Builder
  InfoRow(label: string, value: string, valueColor: number, monospace: boolean) {
    Row() {
      Text(label)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)

      Blank()

      Text(value)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(valueColor)
        .fontFeature(monospace ? DesignFontFeature.TNUM : 'normal')
        .textAlign(TextAlign.End)
    }
    .width('100%')
    .height(56)
    .alignItems(VerticalAlign.Center)
  }

  /** 基础信息卡（6 行 + 目标链接全文 + 「修改有效期」文字按钮） */
  @Builder
  InfoCard() {
    Column() {
      this.InfoRow(ToolboxText.toolbox_label_code, this.param?.code ?? "", DesignColor.TEXT_PRIMARY, true)
      this.InfoRow(ToolboxText.toolbox_label_type, ToolboxText.toolbox_type_short_link, DesignColor.TEXT_PRIMARY, false)
      this.InfoRow(ToolboxText.toolbox_label_visibility,
        this.isPublicCode() ? ToolboxText.toolbox_visibility_public_value : ToolboxText.toolbox_visibility_private_value,
        this.isPublicCode() ? DesignColor.SUCCESS : DesignColor.ONGOING_INK, false)
      this.InfoRow(ToolboxText.toolbox_label_clicks,
        toolboxClicksFormat(this.param?.clickCount ?? 0), DesignColor.PRIMARY, true)
      this.InfoRow(ToolboxText.toolbox_label_created, this.displayCreated(), DesignColor.TEXT_PRIMARY, true)

      if (expiryStateOf(this.param?.expireAt, new Date()) === ExpiryState.PERMANENT) {
        this.InfoRow(ToolboxText.toolbox_label_expiry, ToolboxText.toolbox_expiry_permanent, DesignColor.SUCCESS, false)
      } else if (expiryStateOf(this.param?.expireAt, new Date()) === ExpiryState.ACTIVE) {
        this.InfoRow(ToolboxText.toolbox_label_expiry,
          toolboxExpiryUntil(formatDisplayDateTime(this.param?.expireAt)), DesignColor.TEXT_SECONDARY, true)
      } else {
        this.InfoRow(ToolboxText.toolbox_label_expiry, ToolboxText.toolbox_expiry_expired, DesignColor.DANGER_INK, false)
      }

      if ((this.param?.target ?? "").length > 0) {
        Text(ToolboxText.toolbox_label_target)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: DesignDimens.SPACE_XS })
        Text(this.param?.target ?? "")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .fontFeature(DesignFontFeature.TNUM)
          .maxLines(5)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: 4 })
      }

      Row() {
        Blank()
        // 字重对齐安卓 labelMedium 12/500（原 600）
        Text(ToolboxText.toolbox_action_renew)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.PRIMARY)
          .padding({ top: 4, bottom: 4 })
          .scale({ x: this.pressedKey === 'renew' ? 0.97 : 1, y: this.pressedKey === 'renew' ? 0.97 : 1 })
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Down) {
              this.pressedKey = 'renew'
            } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
              this.pressedKey = ''
            }
          })
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
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
    // 一镜到底近似：信息卡由列表卡展开（进入 scale 0.94→1 + fade，退出反向）
    .scale({ x: this.cardEntered ? 1 : 0.94, y: this.cardEntered ? 1 : 0.94 })
    .opacity(this.cardEntered ? 1 : 0)
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
          // 二维码白底（对齐安卓 Image background(Color.White)）
          .backgroundColor(Color.White)
      } else if (this.qrLoading) {
        // 加载态：200×200 骨架块（对齐安卓 SkeletonBlock，规范 §3.6 禁转圈）
        SkeletonBlock()
          .width(200)
          .height(200)
      } else {
        // 失败重试态（保留，对齐 recap §4 F2：设备无 ScanKit 能力时可重试）
        Column() {
          Text(ToolboxText.toolbox_qr_failed)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
          Text(ToolboxText.toolbox_action_retry)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.PRIMARY)
            .margin({ top: 8 })
        }
        .width(200)
        .height(200)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor(DesignColor.SURFACE_VARIANT)
        .borderRadius(DesignDimens.RADIUS_MD)
        .scale({ x: this.pressedKey === 'qr-retry' ? 0.97 : 1, y: this.pressedKey === 'qr-retry' ? 0.97 : 1 })
        .onTouch((event: TouchEvent) => {
          if (event.type === TouchType.Down) {
            this.pressedKey = 'qr-retry'
          } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
            this.pressedKey = ''
          }
        })
        .onClick(() => {
          if (!this.qrLoading && this.qrFailed) {
            this.loadQrCode()
          }
        })
      }

      // 字重对齐安卓 labelSmall 12/500（原 400）
      Text(this.getShortUrl())
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .fontFeature(DesignFontFeature.TNUM)
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
        // 复制 = SECONDARY（surfaceVariant 底 + 主文本色），打开 = PRIMARY（对齐安卓）
        AppButton({
          text: ToolboxText.toolbox_action_copy,
          variant: AppButtonVariant.SECONDARY,
          click: () => {
            this.copyShortUrl()
          }
        })
        .layoutWeight(1)

        AppButton({
          text: ToolboxText.toolbox_action_open,
          loading: this.resolving,
          click: () => {
            this.openLink()
          }
        })
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
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
  }

  /** 访问明细行（时间 + IP 摘要，次行 UA / Referer 均脱敏弱化展示） */
  @Builder
  StatRow(stat: ShortCodeStatsVo) {
    Column() {
      Row() {
        // 状态点（水蓝，对齐安卓 StatusDot(water)）
        Circle()
          .width(8)
          .height(8)
          .fill(DesignColor.WATER)
        Text(formatDisplayDateTime(stat.createdAt))
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .fontFeature(DesignFontFeature.TNUM)
          .margin({ left: DesignDimens.SPACE_XS })
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
          .margin({ top: 2, left: 12 })
      }
      if ((stat.referer ?? "").length > 0) {
        Text(stat.referer ?? "")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: 2, left: 12 })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
    .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 页面标题 + 右上「删除」危险文字按钮（对齐安卓 PageHeader：删除 18/600 dangerInk + 48 热区）
        // 沉浸式：内容避让状态栏（背景延伸）
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

          Text(ToolboxText.toolbox_detail_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)

          Blank()

          Text(ToolboxText.toolbox_action_delete)
            .fontSize(DesignFontSize.PAGE_HEADER)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.DANGER_INK)
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
              this.openDeleteDialog()
            })
        }
        .width('100%')
        .height(56)
        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
        .alignItems(VerticalAlign.Center)

        // 卡片间距统一 16（对齐安卓 LazyColumn spacedBy(cardSpacing)）
        List({ space: DesignDimens.CARD_SPACING }) {
          ListItem() {
            this.InfoCard()
          }

          ListItem() {
            this.QrCard()
          }

          ListItem() {
            Text(ToolboxText.toolbox_stats_title)
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .width('100%')
          }

          ListItem() {
            Column() {
              if (this.statsLoadingFirst) {
                // 统计首刷骨架（对齐安卓 ShortCodeDetailPage：SkeletonList(rows=3)，§3.6 禁转圈）
                SkeletonList({ rows: 3 })
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
                // 字号对齐安卓 bodyMedium 14sp（原 BODY 16fp）
                Text(ToolboxText.toolbox_stats_empty)
                  .fontSize(DesignFontSize.BODY_SMALL)
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
                  // 追加加载：通宽骨架条（对齐安卓 append SkeletonBlock 14 高）
                  SkeletonBlock()
                    .width('100%')
                    .height(14)
                }
                if (this.statsAppendError || this.statsError) {
                  // footer 内联错误可重试（对齐安卓 statsRefresh Error + itemCount > 0）
                  Text(ToolboxText.toolbox_stats_error_load)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor(DesignColor.DANGER_INK)
                    .width('100%')
                    .textAlign(TextAlign.Center)
                    .padding({ top: 8, bottom: 8 })
                    .onClick(() => {
                      if (this.statsError) {
                        this.statsPaging.reset()
                        this.fetchStats(1)
                      } else {
                        this.statsPaging.retry()
                      }
                    })
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
              color: DesignColor.SHADOW_COLOR_CARD,
              offsetX: 0,
              offsetY: 2
            })
          }
        }
        .width('100%')
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM + safeBottomVp() })
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
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
    // 一镜到底近似转场（对齐安卓 sharedElement("shortcode-card-{id}")）
    .customTransition((operation: NavigationOperation, isEnter: boolean): Array<NavDestinationTransition> | undefined => {
      return toolboxCardTransition(operation, isEnter,
        () => {
          this.cardEntered = true
        },
        () => {
          this.cardEntered = false
        })
    })
  }
}
