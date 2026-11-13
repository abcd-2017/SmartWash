// 我的短链列表页 — 对齐安卓 feature/toolbox/ui/list/ToolboxListPage.kt
//
// 分页：鸿蒙无 Paging 3 等价物，按 02 文档结论用 PagingController 手动 loadMore（每页 10，无人工延时）。
// 四态：首刷 loading / 首刷失败重试 / 空态 / 列表 + 尾部（加载中/失败重试）。
// 刷新：创建/续期/删除成功回列表后由变更信号（ToolboxChangeSignal）重拉，等价安卓 savedStateHandle CHANGED_KEY。
// 刷新会话门控（对齐安卓 2026-11-06 闪空态回归修复）：刷新中不清空旧数据、空态仅在
// 「非刷新中且列表为空」时展示、下拉指示器保持到本轮加载返回再复位。
// 条目点击进详情：全量字段随路由携带（详情页不再请求）。
// 卡片右列含「复制短链」描边按钮（对齐安卓 CopyPillButton）。

import { HttpStatusCode } from '@ohos/axios'
import { BusinessError, pasteboard } from '@kit.BasicServicesKit'
import { RouteNames } from '../../constant/RouteNames'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../constant/DesignSystem'
import {
  CONTENT_TYPE_SHORT_LINK,
  ShortCodeVo,
  TOOLBOX_PAGE_SIZE,
  ToolboxShortCodeDetailParam,
  ToolboxText,
  VISIBILITY_PUBLIC,
  pageShortCodes,
  toolboxClicksFormat,
  toolboxExpiryUntil,
  toolboxSecretStateText
} from './api'
import {
  CONTENT_TYPE_SECRET_TEXT,
  ExpiryState,
  expiryStateOf,
  formatDisplayDateTime,
  isPublicVisibility,
  SecretState,
  secretStateOf
} from './ShortCodeRules'
import { consumeShortCodeChanged } from './ToolboxChangeSignal'
import { AppButton, EmptyState, IconBox, SkeletonBlock, SkeletonList } from '../../view/common/AppComponents'
import { logger } from '../../utils/Logger'
import { PagingController } from '../../utils/PagingUtil'
import { pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../utils/SafeArea'
import { ThemeRefresh } from '../../view/common/ThemeRefresh'
import { resColor } from '../../utils/ResourceColor'
import { resString } from '../../utils/ResourceString'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'toolboxShortCodeList'

@Builder
export function ToolboxShortCodeListBuilder() {
  ToolboxShortCodeList()
}

@Component
struct ToolboxShortCodeList {
  @State list: Array<ShortCodeVo> = []
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  @State isRefreshing: boolean = false
  // 刷新会话门控（对齐安卓 refreshSession）：下拉刷新/变更信号回列表期间为真，
  // 空态分支被门控（走骨架），会话在本轮加载返回后结束
  @State refreshingSession: boolean = false
  @State isLoadingMore: boolean = false
  // 追加加载失败态（对齐安卓 append Error → footer 文字重试）；重试经 paging.retry() 重拉同一页
  @State appendError: boolean = false
  // 按压反馈（对齐安卓 pressable scale 0.97）：卡片 / 复制钮共用一个 key
  @State pressedKey: string = ''
  // onShown 首次即触发，首屏加载由 aboutToAppear 承担；加锁避免首屏重复请求
  private hasShown: boolean = false
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchPage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )

  async aboutToAppear(): Promise<void> {
    logger.debug(TAG, '列表页挂载，开始首刷')
    await this.fetchPage(1)
    this.isLoadingFirst = false
  }

  /**
   * 按页拉取短码列表。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchPage(page: number): Promise<boolean> {
    try {
      const res = await pageShortCodes(page, TOOLBOX_PAGE_SIZE)
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        const records: Array<ShortCodeVo> = res.data.records
        if (records.length === 0) {
          logger.debug(TAG, `分页封底 page=${page} 本页无数据`)
          if (page === 1) {
            // 首页空结果 = 真实空（首刷本就为空 / 刷新会话后服务器已无数据）：清空旧数据，
            // 会话结束后由真实空态分支呈现（对齐安卓 Paging invalidate 空结果语义）
            this.list = []
            this.isFirstError = false
            this.appendError = false
            return false
          }
          if (this.list.length > 0) {
            showToastSafety(resString('toolbox_short_code_list_1'))
          }
          this.appendError = false
          return false
        }
        if (page === 1) {
          this.list = []
          this.isFirstError = false
        }
        this.list.push(...records)
        this.appendError = false
        logger.debug(TAG, `分页加载成功 page=${page} count=${records.length} total=${res.data.total}`)
        return true
      }
      if (page === 1) {
        // 首刷失败：说明 + 重试（规范 §3.6 错误态），并 toast 提示（对齐安卓 refresh Error toast）
        logger.warn(TAG, `短链首刷失败 page=1 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isFirstError = true
        // 错误类提示统一资源文案 + 长时长 3500（对齐安卓 Toast.LENGTH_LONG，规范 §8 错误 3–5s）
        showToastSafety(ToolboxText.toolbox_error_load, ToolboxText.toolbox_error_load, 3500)
        return false
      }
      // 追加失败：置失败态并 reject（分页器记失败页不封底，footer 重试重拉同一页，页码不推进）
      logger.warn(TAG, `短链追加失败 page=${page} code=${res.code ?? 0} msg=${res.message ?? ''}`)
      this.appendError = true
      showToastSafety(ToolboxText.toolbox_error_load, ToolboxText.toolbox_error_load, 3500)
      throw new Error(ToolboxText.toolbox_error_load)
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：与「追加失败」同语义（reject 供分页器记失败页）；
      // 首刷则进错误态，避免 isLoadingFirst 卡死永久骨架
      if (page === 1) {
        this.isFirstError = true
        showToastSafety(ToolboxText.toolbox_error_network, ToolboxText.toolbox_error_network, 3500)
        return false
      }
      this.appendError = true
      showToastSafety(ToolboxText.toolbox_error_network, ToolboxText.toolbox_error_network, 3500)
      throw new Error(ToolboxText.toolbox_error_load)
    }
  }

  /**
   * 整列表重置重拉（下拉刷新 + 变更信号回列表刷新共用）。
   * 刷新会话门控（对齐安卓 2026-11-06 闪空态回归修复）：开启会话后不清空旧数据、
   * 空态分支被门控（走骨架），下拉指示器保持到本轮加载返回（有数据/封底/失败）再复位，
   * 杜绝「清空后、新数据未返回」帧闪「还没有短链」空态。
   */
  refreshList(): void {
    this.refreshingSession = true
    this.appendError = false
    this.paging.reset()
    this.fetchPage(1).then(() => {
      this.isRefreshing = false
      this.refreshingSession = false
    }).catch(() => {
      this.isRefreshing = false
      this.refreshingSession = false
    })
  }

  /** 列表 → 详情：9 个基础字段随路由携带（对齐安卓 navigateToShortCodeDetail） */
  private goDetail(vo: ShortCodeVo): void {
    const param: ToolboxShortCodeDetailParam = new ToolboxShortCodeDetailParam()
    param.id = vo.id ?? 0
    param.code = vo.code ?? ""
    param.contentType = vo.contentType ?? CONTENT_TYPE_SHORT_LINK
    param.isPublic = vo.isPublic ?? VISIBILITY_PUBLIC
    param.clickCount = vo.clickCount ?? 0
    param.target = vo.target ?? ""
    param.expireAt = vo.expireAt ?? ""
    param.shortUrl = vo.shortUrl ?? ""
    param.createdAt = vo.createdAt ?? ""
    // 私密文本（contentType=7）：状态/焚毁时间/分享链接随路由携带
    param.secretState = vo.secretState ?? ""
    param.burnedAt = vo.burnedAt ?? ""
    param.secretUrl = vo.secretUrl ?? ""
    pathStack.pushPathByName(RouteNames.TOOLBOX_SHORT_CODE_DETAIL, param)
  }

  private goCreate(): void {
    pathStack.pushPathByName(RouteNames.TOOLBOX_SHORT_CODE_CREATE, null)
  }

  /** 复制短链/私密文本分享链接（空缺回落短码）+ 提示，与详情页复制行为一致（对齐安卓 onCopy） */
  private copyShortCode(vo: ShortCodeVo): void {
    const isSecret = (vo.contentType ?? CONTENT_TYPE_SHORT_LINK) === CONTENT_TYPE_SECRET_TEXT
    const link = isSecret ? (vo.secretUrl ?? "") : (vo.shortUrl ?? "")
    const text = link.length > 0 ? link : (vo.code ?? "")
    if (text.length === 0) {
      return
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(isSecret ? ToolboxText.toolbox_secret_copy_success : ToolboxText.toolbox_copy_success)
    }).catch((err: BusinessError) => {
      // 复制失败与「找不到浏览器」是两种失败，各用各的文案（原复用 open_failed 属误导）
      showToastSafety(ToolboxText.toolbox_copy_failed)
      logger.error(TAG, '短链复制到剪贴板失败', `${err.code} ${err.message}`)
    })
  }

  /** 可见性映射（非 2 一律公开，对齐后端 1公开/2私有） */
  private isPublicOf(vo: ShortCodeVo): boolean {
    return isPublicVisibility(vo.isPublic ?? VISIBILITY_PUBLIC)
  }

  /** 私密文本状态 chip 底色（ACTIVE=primaryContainer / BURNED=errorContainer / EXPIRED=warningContainer / 兜底 surfaceVariant） */
  private secretChipContainer(state: string | null | undefined): ResourceColor {
    const s: SecretState = secretStateOf(state)
    if (s === SecretState.ACTIVE) {
      return $r('app.color.PRIMARY_CONTAINER')
    }
    if (s === SecretState.BURNED) {
      return $r('app.color.ERROR_CONTAINER')
    }
    if (s === SecretState.EXPIRED) {
      return $r('app.color.WARNING_CONTAINER')
    }
    return $r('app.color.SURFACE_VARIANT')
  }

  /** 私密文本状态 chip 字色（与 secretChipContainer 一一配对，对齐安卓） */
  private secretChipContent(state: string | null | undefined): ResourceColor {
    const s: SecretState = secretStateOf(state)
    if (s === SecretState.ACTIVE) {
      return $r('app.color.ON_PRIMARY_CONTAINER')
    }
    if (s === SecretState.BURNED) {
      return $r('app.color.ON_ERROR_CONTAINER')
    }
    if (s === SecretState.EXPIRED) {
      return $r('app.color.ON_WARNING_CONTAINER')
    }
    return $r('app.color.TEXT_SECONDARY')
  }

  /** 过期态文字（列表页：永久/有效灰、已过期 dangerInk） */
  private expiryTextOf(vo: ShortCodeVo): string {
    if (expiryStateOf(vo.expireAt, new Date()) === ExpiryState.PERMANENT) {
      return ToolboxText.toolbox_expiry_permanent
    }
    if (expiryStateOf(vo.expireAt, new Date()) === ExpiryState.ACTIVE) {
      return toolboxExpiryUntil(formatDisplayDateTime(vo.expireAt))
    }
    return ToolboxText.toolbox_expiry_expired
  }

  /** 过期态颜色（对齐安卓：常态 textSecondary，过期 dangerInk） */
  private expiryColorOf(vo: ShortCodeVo): ResourceColor {
    return expiryStateOf(vo.expireAt, new Date()) === ExpiryState.EXPIRED
      ? $r('app.color.DANGER_INK') : $r('app.color.TEXT_SECONDARY')
  }

  /** 过期态字重（对齐安卓：过期 labelSmall 12/500，其余 bodySmall 12/400） */
  private expiryWeightOf(vo: ShortCodeVo): number {
    return expiryStateOf(vo.expireAt, new Date()) === ExpiryState.EXPIRED
      ? DesignFontWeight.MEDIUM : DesignFontWeight.NORMAL
  }

  /** 静态标签 chip（对齐安卓 ToolboxTagChip：胶囊 + labelSmall 12/500 captionStrong 档） */
  @Builder
  TagChip(text: string, containerColor: ResourceColor, contentColor: ResourceColor) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      .fontWeight(DesignFontWeight.MEDIUM)
      .fontColor(contentColor)
      .padding({ left: 8, right: 8, top: 2, bottom: 2 })
      .backgroundColor(containerColor)
      .borderRadius(DesignDimens.RADIUS_FULL)
  }

  /**
   * 复制描边按钮 — 对齐安卓 CopyPillButton：视觉胶囊（透明底 + 1px outline +
   * captionStrong 12/500 textGreen），外扩 4/8 热区至 60×48 满足 48dp 触控红线。
   * 字色走 TEXT_GREEN 文字绿档（浅 #177A4C on 白 5.35:1 达 AA；PRIMARY_DARK 4.23:1 为红线，
   * 对齐安卓 2026-11-06 评审 R18）。
   */
  @Builder
  CopyPillButton(text: string, vo: ShortCodeVo) {
    Column() {
      Text(text)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.TEXT_GREEN'))
        .padding({ left: 14, right: 14, top: 8, bottom: 8 })
        .border({ width: 1, color: $r('app.color.OUTLINE') })
        .borderRadius(DesignDimens.RADIUS_FULL)
    }
    .padding({ left: 4, right: 4, top: 8, bottom: 8 })
    .scale({
      x: this.pressedKey === `copy-${vo.id ?? 0}` ? 0.97 : 1,
      y: this.pressedKey === `copy-${vo.id ?? 0}` ? 0.97 : 1
    })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `copy-${vo.id ?? 0}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.copyShortCode(vo)
    })
  }

  /**
   * 空态/错误态（规范 §3.6：滚筒圆母题 + 引导文案 + 行动按钮）。
   * 统一复用组件层 EmptyState（不再自绘图标 + 裸 Button），isError=true 为重试态。
   */
  @Builder
  Placeholder(message: string, actionText: string, isError: boolean) {
    Column() {
      EmptyState({ message: message }) {
        // 间距由插槽内给出（EmptyState 自身只在插槽为空时零占位）
        Column() {
          AppButton({
            small: true,
            text: actionText,
            click: () => {
              if (isError) {
                this.refreshList()
              } else {
                this.goCreate()
              }
            }
          })
        }
        .margin({ top: DesignDimens.SPACE_MD })
      }
    }
    .width('100%')
    .layoutWeight(1)
    .justifyContent(FlexAlign.Center)
  }

  /** 短链条目卡（对齐安卓 ShortCodeCard） */
  @Builder
  ShortCodeCard(vo: ShortCodeVo) {
    Column() {
      Row() {
        IconBox({
          icon: $r('sys.symbol.link'),
          sizeValue: 36,
          iconSize: 20,
          containerColor: $r('app.color.ICON_CONTAINER_BLUE'),
          iconColor: $r('app.color.ICON_FOREGROUND_BLUE')
        })

        Column() {
          Text(vo.code ?? "")
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Row({ space: 4 }) {
            this.TagChip((vo.contentType ?? CONTENT_TYPE_SHORT_LINK) === CONTENT_TYPE_SECRET_TEXT
              ? ToolboxText.toolbox_create_mode_secret : ToolboxText.toolbox_type_short_link,
              $r('app.color.SURFACE_VARIANT'), $r('app.color.TEXT_SECONDARY'))
            if ((vo.contentType ?? CONTENT_TYPE_SHORT_LINK) === CONTENT_TYPE_SECRET_TEXT) {
              // 私密文本：状态 chip（未领取/已焚毁/已过期）替代可见性（恒公开无信息量，对齐安卓；
              // @Builder 内不做局部声明，色值经 secretChipContainer/Content 方法取）
              this.TagChip(toolboxSecretStateText(vo.secretState),
                this.secretChipContainer(vo.secretState), this.secretChipContent(vo.secretState))
            } else if (this.isPublicOf(vo)) {
              // 公开 = primaryContainer/onPrimaryContainer（对齐安卓）
              this.TagChip(ToolboxText.toolbox_visibility_public_value,
                $r('app.color.PRIMARY_CONTAINER'), $r('app.color.ON_PRIMARY_CONTAINER'))
            } else {
              // 私有 = warningContainer 底 + onWarningContainer 字（对齐安卓配对字色）——
              // 前景误用主档橙 WARNING（浅 #D98A26/橙底 2.52:1 触红线），改 ON_WARNING_CONTAINER
              // （浅 #92400E 6.47:1 / 深 #ECB36A 7.63:1 均达标）
              this.TagChip(ToolboxText.toolbox_visibility_private_value,
                $r('app.color.WARNING_CONTAINER'), $r('app.color.ON_WARNING_CONTAINER'))
            }
          }
          .margin({ top: DesignDimens.SPACE_XXS })

          // 有效期独占一行（对齐安卓重设计）：长文本「有效期至 yyyy-MM-dd HH:mm」不再与
          // chips 同行竞争宽度——原布局中该文本在右列，窄屏会把「公开/私有」chip 挤压换行；
          // maxLines(1)+ellipsis 兜底极端窄屏，完整到期时间在详情页可见。
          // 时间戳开 tnum（对齐安卓 audit-09 P2-6：与同卡短码/点击数口径一致，多卡纵向扫描日期对齐）
          Text(this.expiryTextOf(vo))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(this.expiryColorOf(vo))
            .fontWeight(this.expiryWeightOf(vo))
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .margin({ top: DesignDimens.SPACE_XS })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

        Column() {
          Text(toolboxClicksFormat(vo.clickCount ?? 0))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .fontFeature(DesignFontFeature.TNUM)
          this.CopyPillButton(ToolboxText.toolbox_action_copy, vo)
        }
        .alignItems(HorizontalAlign.End)
        .margin({ left: DesignDimens.SPACE_SM })
      }
      .width('100%')
      // 右列（点击数/复制钮）顶对齐：安卓 Row 默认 Top，原 Center 会让右列整体居中
      .alignItems(VerticalAlign.Top)

      if ((vo.target ?? "").length > 0) {
        Text(vo.target ?? "")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_SM })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
    // 画板实测：内边距左/上/右 20，底部 12 留给链接行（对齐安卓 ShortCodeCard）
    .padding({ left: DesignDimens.CARD_PADDING, top: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING, bottom: DesignDimens.SPACE_SM })
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    // 卡片唯一画法（规范 §3.1）：1px OUTLINE 描边 + 卡片阴影档（§2.4：0 2px 12px）——
    // blur 取 CARD_SHADOW_RADIUS(12)，非海拔档位号 LEVEL_1（1vp 模糊不可见，与 AppCard 统一口径）；
    // 阴影色随主题切深色档 rgba(0,0,0,0.28)
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    // 按压反馈（对齐安卓 pressable scale 0.97）
    .scale({
      x: this.pressedKey === `card-${vo.id ?? 0}` ? 0.97 : 1,
      y: this.pressedKey === `card-${vo.id ?? 0}` ? 0.97 : 1
    })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `card-${vo.id ?? 0}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.goDetail(vo)
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 页面标题 + 「新建」文字按钮（对齐安卓 PageHeader：标题 22/600，动作 18/600 primaryDark + 48 热区）
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

          Text(ToolboxText.toolbox_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_PRIMARY'))

          Blank()

          Text(ToolboxText.toolbox_action_new)
            .fontSize(DesignFontSize.PAGE_HEADER)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.PRIMARY_DARK'))
            .padding({ left: 12, right: 12, top: 12, bottom: 12 })
            .onClick(() => {
              this.goCreate()
            })
        }
        .width('100%')
        .height(56)
        // 行首让位 12：48 热区盒居中后图形左缘落在 24（对齐安卓 PageHeader）
        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
        .alignItems(VerticalAlign.Center)

        if (this.isLoadingFirst) {
          // 首刷加载态（对齐安卓 SkeletonList(rows=5)；骨架组件已补齐，不再降级转圈）
          SkeletonList({ rows: 5 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isFirstError && this.list.length === 0) {
          this.Placeholder(ToolboxText.toolbox_error_load, ToolboxText.toolbox_action_retry, true)
        } else if (this.list.length === 0 && this.refreshingSession) {
          // 刷新会话清空帧（对齐安卓 2026-11-06 门控）：会话未结束前空列表走骨架——
          // 覆盖「变更信号在空列表上发起的刷新」等窗口，杜绝闪「还没有短链」空态
          SkeletonList({ rows: 5 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.list.length === 0 && !this.refreshingSession) {
          // 真实空态：仅「非刷新中且列表为空」时可达
          this.Placeholder(ToolboxText.toolbox_empty_message, ToolboxText.toolbox_empty_action, false)
        } else {
          Refresh({ refreshing: $$this.isRefreshing }) {
            // 卡片间距对齐安卓 LazyColumn spacedBy(cardSpacing)（原 List 无 space，卡紧贴）
            List({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.list, (vo: ShortCodeVo) => {
                ListItem() {
                  this.ShortCodeCard(vo)
                }
              }, (vo: ShortCodeVo) => `${vo.id ?? 0}`)

              // 尾部：加载中 / 失败重试（对齐安卓 append LoadState：retry() 重拉失败页，页码不推进）
              ListItem() {
                if (this.isLoadingMore) {
                  // 骨架块而非转圈（规范 §3.6：分页尾部禁转圈；对齐安卓 AppendLoadingRow 120×14）
                  Row() {
                    SkeletonBlock()
                      .width(120)
                      .height(14)
                  }
                  .width('100%')
                  .padding({ top: 12, bottom: 12 })
                  .justifyContent(FlexAlign.Center)
                } else if (this.appendError) {
                  Text(ToolboxText.toolbox_error_load)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontWeight(DesignFontWeight.MEDIUM)
                    .fontColor($r('app.color.DANGER_INK'))
                    .width('100%')
                    .textAlign(TextAlign.Center)
                    // 分页失败唯一显式重试入口：上下 16 × 2 + 行高 16 = 48vp 热区
                    //（触控红线 ≥48，原 12×2 = 40 不达标；对齐安卓 audit-09 P0-1）
                    .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
                    // 按压反馈（对齐安卓 pressable scale 0.97，套用本页卡片同款模式）
                    .scale({
                      x: this.pressedKey === 'append-retry' ? 0.97 : 1,
                      y: this.pressedKey === 'append-retry' ? 0.97 : 1
                    })
                    .onTouch((event: TouchEvent) => {
                      if (event.type === TouchType.Down) {
                        this.pressedKey = 'append-retry'
                      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
                        this.pressedKey = ''
                      }
                    })
                    .onClick(() => {
                      this.paging.retry()
                    })
                }
              }
            }
            .width('100%')
            .height('100%')
            // 底部避让手势条：取安全区与 spaceXl(24) 较大值（对齐 GameCenterPage 修后写法，
            // 安卓 contentPadding bottom = spaceMd 16 + navigationBars）
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS, bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_XL) })
            .divider({ strokeWidth: 0, color: Color.Transparent })
            .onScrollIndex((start: number, end: number) => {
              // 刷新会话期间不触发触底追加：旧数据仍在展示而页码已重置，避免并发拉页把
              // 旧分页的第 2 页错插进新列表（原先靠「刷新即清空列表」间接触发该守卫）
              if (this.refreshingSession) {
                return
              }
              this.paging.onScrollIndex(end, this.list.length)
            })
            .onScrollFrameBegin((offset: number) => {
              this.paging.onScrollFrameBegin(offset)
              return { offsetRemain: offset }
            })
          }
          .onRefreshing(() => {
            this.refreshList()
          })
        }
      }
      .width('100%')
      .height('100%')
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 创建/续期/删除成功回列表 → 重拉（首次 onShown 与 aboutToAppear 去重）
      if (!this.hasShown) {
        this.hasShown = true
        return
      }
      if (consumeShortCodeChanged()) {
        logger.debug(TAG, '消费到短链变更信号，重拉列表')
        this.refreshList()
      }
    })
  }
}
