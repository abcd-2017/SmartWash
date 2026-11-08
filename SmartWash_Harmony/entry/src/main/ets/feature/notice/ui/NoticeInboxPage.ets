// 消息中心页 — 对齐安卓 feature/notice/ui/inbox/NoticeInboxPage.kt
//
// 顶部未读数 + 「全部已读」（二次确认 + 防重）+ 列表（未读高亮/已读灰化；类型 tag/标题/内容摘要/时间）。
// 点击条目 → 标记已读（乐观更新）→ deep_link 跳转（smartwash:// 走既有 DeepLink 链路；http(s) 走 WebPage）。
// 分页加载更多（PagingController）；首载骨架 / 首载失败重试 / 空态由列表内承载。
//
// 跨页刷新：标记已读 / 全部已读成功后 bump 站内信 tick，我的页入口角标经 @StorageLink + @Watch 重拉。
//
// 已知坑遵守：
// - 条目卡内容依赖状态 → @ComponentV2 + @Param/@Event（@Builder 按值传参不响应更新）；
// - 组合期无副作用：标记已读/全部已读只在点击回调与事件回调中触发；
// - 无页面级定时器（无轮询），无需 aboutToDisappear 清理。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { NOTICE_PAGE_SIZE, getNotices, getUnreadCount, markNoticeRead, readAllNotices } from '../../../network/api/NoticeApi'
import { NoticeVo } from '../../../network/vo/NoticeVo'
import {
  NoticeText,
  NoticeType,
  noticeTimeLabel,
  noticeTimeParts,
  noticeTypeFromRaw,
  noticeTypeText
} from '../../../utils/NoticeLogic'
import { NoticeErrorState, NoticePageHeader, NoticeTagChip } from './NoticeUiCommon'
import { AppButton, AppButtonVariant, AppConfirmDialog, EmptyState, SkeletonBlock, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { PagingController } from '../../../utils/PagingUtil'
import { pathStack } from '../../../utils/PathStackUtil'
import { bumpNoticeRefresh } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { ThemeRefresh } from '../../../view/common/ThemeRefresh'
import { handleDeepLink } from '../../../utils/deeplink/DeepLinkHandler'
import { WebPageParam } from '../../../pages/WebPage'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'noticeInboxPage'

/** 类型 tag 配色（容器底 + 文字色） */
interface NoticeTagColors {
  container: number
  content: number
}

@Builder
export function NoticeInboxBuilder() {
  NoticeInboxPage()
}

@Component
struct NoticeInboxPage {
  @State list: Array<NoticeVo> = []
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  @State firstErrorMessage: string = ''
  @State isRefreshing: boolean = false
  @State unreadCount: number = 0
  @State readAllLoading: boolean = false
  @State isLoadingMore: boolean = false
  @State appendError: boolean = false
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchPage(page)
    },
    (loading: boolean): void => {
      this.isLoadingMore = loading
    }
  )
  // 全部已读二次确认弹窗（先关窗再回调，弹窗内 confirm 自带 close）
  private readAllDialog: CustomDialogController = new CustomDialogController({
    builder: AppConfirmDialog({
      title: NoticeText.notice_read_all_confirm_title,
      message: NoticeText.notice_read_all_confirm_message,
      onConfirm: (): void => {
        this.readAll()
      },
      onDismiss: (): void => {
      }
    }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear(): Promise<void> {
    logger.debug(TAG, '消息中心页挂载，开始首载')
    this.isLoadingFirst = true
    // 未读数独立拉取（失败仅留痕），不与列表主流程互相拖垮
    this.loadUnreadCount()
    await this.loadFirst()
    this.isLoadingFirst = false
  }

  /** 首载/重试/下拉刷新：重置分页并拉取第 1 页 */
  async loadFirst(): Promise<void> {
    this.appendError = false
    this.paging.reset()
    this.list = []
    this.isFirstError = false
    try {
      const res = await getNotices(1, NOTICE_PAGE_SIZE)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.list = res.data.records
        logger.debug(TAG, `收件箱首载成功 count=${this.list.length} total=${res.data.total}`)
      } else {
        logger.warn(TAG, `收件箱首载失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isFirstError = true
        this.firstErrorMessage = res.message ?? NoticeText.notice_load_failed
        showToastSafety(res.message, NoticeText.notice_load_failed)
      }
    } catch (err) {
      logger.error(TAG, '收件箱首载异常', err instanceof Error ? err.message : '')
      this.isFirstError = true
      this.firstErrorMessage = NoticeText.notice_load_failed
      showToastSafety('', NoticeText.notice_load_failed)
    }
  }

  /**
   * 按页拉取（供 PagingController 追加，page >= 2）。
   * @returns 本页是否有数据（false = 封底，后续触底不再发请求）
   */
  async fetchPage(page: number): Promise<boolean> {
    try {
      const res = await getNotices(page, NOTICE_PAGE_SIZE)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const records: Array<NoticeVo> = res.data.records
        if (records.length === 0) {
          logger.debug(TAG, `分页封底 page=${page} 本页无数据`)
          return false
        }
        this.list.push(...records)
        logger.debug(TAG, `分页加载成功 page=${page} count=${records.length} total=${res.data.total}`)
        return true
      }
      logger.warn(TAG, `分页加载失败 page=${page} code=${res.code ?? 0} msg=${res.message ?? ''}`)
      throw new Error(NoticeText.notice_load_more_failed)
    } catch (err) {
      logger.warn(TAG, `分页加载异常 page=${page}`)
      throw new Error(NoticeText.notice_load_more_failed)
    }
  }

  /** 下拉刷新：保持列表语义由 Refresh 指示器承担 */
  private async refreshList(): Promise<void> {
    this.isRefreshing = true
    await this.loadFirst()
    this.isRefreshing = false
  }

  /** 未读数独立拉取（失败仅留痕，计数隐藏，不拖垮列表主流程） */
  async loadUnreadCount(): Promise<void> {
    try {
      const res = await getUnreadCount()
      if (res.code === BizCode.SUCCESS) {
        this.unreadCount = res.data ?? 0
        logger.debug(TAG, `未读数拉取成功 count=${this.unreadCount}`)
      } else {
        logger.warn(TAG, `未读数拉取失败 code=${res.code ?? 0}`)
      }
    } catch (err) {
      logger.warn(TAG, '未读数拉取异常（计数隐藏）')
    }
  }

  /** 按 id 查找列表下标（-1 = 不存在） */
  private indexOfNotice(id: number): number {
    for (let i = 0; i < this.list.length; i++) {
      if (this.list[i].id === id) {
        return i
      }
    }
    return -1
  }

  /** 复制并替换指定下标的已读标记（interface 不可变更新，返回新数组） */
  private listWithRead(index: number, read: boolean): Array<NoticeVo> {
    const next = this.list.slice()
    next[index] = this.noticeWithRead(next[index], read)
    return next
  }

  /** 复制 VO 并覆盖已读标记 */
  private noticeWithRead(vo: NoticeVo, read: boolean): NoticeVo {
    return {
      id: vo.id,
      title: vo.title,
      content: vo.content,
      type: vo.type,
      deepLink: vo.deepLink,
      isRead: read,
      createdAt: vo.createdAt
    }
  }

  /**
   * 标记已读（乐观更新：立即置已读 + 未读减一；失败回滚并静默重拉未读数）。
   * 已读条目重复点击不再请求（后端幂等，端上省一次请求）。
   */
  async markRead(id: number): Promise<void> {
    if (id <= 0) {
      return
    }
    const index = this.indexOfNotice(id)
    if (index < 0) {
      return
    }
    if (this.list[index].isRead) {
      return
    }
    this.list = this.listWithRead(index, true)
    this.unreadCount = Math.max(0, this.unreadCount - 1)
    try {
      await markNoticeRead(id)
      bumpNoticeRefresh()
    } catch (err) {
      logger.warn(TAG, '标记已读失败，回滚乐观更新')
      const currentIndex = this.indexOfNotice(id)
      if (currentIndex >= 0) {
        this.list = this.listWithRead(currentIndex, false)
      }
      this.loadUnreadCount()
    }
  }

  /** 全部已读（防重：加载中直接忽略；成功后整列表置已读 + 未读清零） */
  async readAll(): Promise<void> {
    if (this.readAllLoading) {
      return
    }
    this.readAllLoading = true
    try {
      const res = await readAllNotices()
      if (res.code === BizCode.SUCCESS) {
        this.list = this.list.map((vo: NoticeVo): NoticeVo => this.noticeWithRead(vo, true))
        this.unreadCount = 0
        bumpNoticeRefresh()
        showToastSafety(NoticeText.notice_read_all_success)
        logger.info(TAG, '全部已读成功')
      } else {
        logger.warn(TAG, `全部已读失败 code=${res.code ?? 0} msg=${res.message ?? ''}`)
        showToastSafety(res.message, NoticeText.notice_read_all_failed)
      }
    } catch (err) {
      logger.error(TAG, '全部已读异常', err instanceof Error ? err.message : '')
      showToastSafety('', NoticeText.notice_read_all_failed)
    } finally {
      this.readAllLoading = false
    }
  }

  /**
   * 深链分发（复用壳层既有入口，对齐安卓 handleDeepLink）：
   * - smartwash:// → 既有 DeepLinkHandler（登录守卫 + 注册表分发）；
   * - http(s):// → 内置浏览器页（WebPage，与安卓 ShellRoute.WebView 同语义）；
   * - 其余 scheme（后端契约外）忽略，仅标记已读不跳转。
   */
  private dispatchDeepLink(link: string): void {
    if (link.length === 0) {
      return
    }
    const schemeEnd = link.indexOf(':')
    if (schemeEnd <= 0) {
      return
    }
    const scheme = link.substring(0, schemeEnd).toLowerCase()
    if (scheme === 'smartwash') {
      // 既有深链链路（登录守卫 + 注册表分发），内部全量兜底不抛异常
      handleDeepLink(link)
    } else if (scheme === 'http' || scheme === 'https') {
      const param: WebPageParam = new WebPageParam()
      param.url = link
      param.title = ''
      pathStack.pushPathByName(RouteNames.WEB_PAGE, param)
    }
  }

  /** 点击条目：先标记已读（乐观），再按需深链跳转 */
  private onNoticeClick(vo: NoticeVo): void {
    this.markRead(vo.id)
    this.dispatchDeepLink(vo.deepLink)
  }

  /** 页头未读数文案 */
  private unreadText(): string {
    return NoticeText.notice_unread_count_format.replace('%d', `${this.unreadCount}`)
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容
      ThemeRefresh() {
      Column() {
        NoticePageHeader({
          title: NoticeText.notice_title,
          unreadText: this.unreadText(),
          readAllLoading: this.readAllLoading,
          onBack: (): void => {
            pathStack.pop()
          },
          onReadAll: (): void => {
            this.readAllDialog.open()
          }
        })

        if (this.isLoadingFirst) {
          SkeletonList({ rows: 5 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isFirstError && this.list.length === 0) {
          // 首载失败：说明发生了什么 + 重试（规范 §3.6）
          NoticeErrorState({
            message: this.firstErrorMessage,
            onRetry: (): void => {
              this.loadFirst()
            }
          })
            .width('100%')
            .layoutWeight(1)
        } else if (this.list.length === 0) {
          // 空态：滚筒圆母题 + 引导文案
          Column() {
            EmptyState({ message: NoticeText.notice_inbox_empty })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          Refresh({ refreshing: $$this.isRefreshing }) {
            List() {
              ForEach(this.list, (vo: NoticeVo) => {
                ListItem() {
                  NoticeCard({
                    vo: vo,
                    onCardClick: (): void => {
                      this.onNoticeClick(vo)
                    }
                  })
                }
              }, (vo: NoticeVo) => `${vo.id}`)

              // 尾部：加载中骨架 / 失败重试（对齐安卓 append LoadState）
              ListItem() {
                if (this.isLoadingMore) {
                  Row() {
                    SkeletonBlock()
                      .width(120)
                      .height(14)
                  }
                  .width('100%')
                  .padding({ top: 12, bottom: 12 })
                  .justifyContent(FlexAlign.Center)
                } else if (this.appendError) {
                  Text(NoticeText.notice_load_more_failed)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor(DesignColor.DANGER_INK)
                    .width('100%')
                    .textAlign(TextAlign.Center)
                    .padding({ top: 12, bottom: 12 })
                    .onClick(() => {
                      this.paging.retry()
                    })
                }
              }
            }
            .width('100%')
            .height('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 8, bottom: 8 + safeBottomVp() })
            .divider({ strokeWidth: 0, color: Color.Transparent })
            .onScrollIndex((start: number, end: number) => {
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
      .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .hideTitleBar(true)
  }
}

// ==================== 通知卡片（内容依赖状态 → @ComponentV2 + @Param/@Event） ====================

/**
 * 通知卡片（规范 §3.1 唯一卡片画法 + 整卡点击）：
 * 顶行 = 类型 tag（语义色胶囊）+ 相对时间；主体 = 未读圆点 + 标题（未读主色加粗 /
 * 已读次文本灰化）+ 内容摘要（两行省略）。
 */
@ComponentV2
struct NoticeCard {
  @Require @Param vo: NoticeVo
  @Event onCardClick: () => void = () => {
  }

  /** 类型（防御回落 UNKNOWN） */
  private type(): NoticeType {
    return noticeTypeFromRaw(this.vo.type)
  }

  /** 类型 tag 配色（系统通知=橙 / 运营公告=蓝 / 未收录=中性灰） */
  private tagColors(): NoticeTagColors {
    const type = this.type()
    if (type === NoticeType.SYSTEM) {
      return { container: DesignColor.ICON_CONTAINER_ORANGE, content: DesignColor.ICON_FOREGROUND_ORANGE }
    }
    if (type === NoticeType.ANNOUNCEMENT) {
      return { container: DesignColor.ICON_CONTAINER_BLUE, content: DesignColor.ICON_FOREGROUND_BLUE }
    }
    return { container: DesignColor.SURFACE_VARIANT, content: DesignColor.TEXT_SECONDARY }
  }

  /** 相对时间文案（解析失败返回空串，UI 隐藏时间） */
  private timeLabel(): string {
    const parts = noticeTimeParts(this.vo.createdAt)
    return parts === null ? '' : noticeTimeLabel(parts)
  }

  build() {
    Column() {
      Row() {
        NoticeTagChip({
          text: noticeTypeText(this.type()),
          containerColor: this.tagColors().container,
          contentColor: this.tagColors().content
        })

        Blank()

        if (this.timeLabel().length > 0) {
          Text(this.timeLabel())
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_TERTIARY)
            .fontFeature(DesignFontFeature.TNUM)
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Row() {
        if (!this.vo.isRead) {
          // 未读状态色点（规范 §3.5：8vp 圆点，提醒语义取粉/异常色）
          Column()
            .width(8)
            .height(8)
            .borderRadius(4)
            .backgroundColor(DesignColor.ICON_FOREGROUND_PINK)
            .margin({ right: DesignDimens.SPACE_XS })
        }

        Text(this.vo.title)
          .fontSize(this.vo.isRead ? DesignFontSize.BODY_SMALL : DesignFontSize.CARD_TITLE)
          .fontWeight(this.vo.isRead ? DesignFontWeight.NORMAL : DesignFontWeight.SEMI_BOLD)
          .fontColor(this.vo.isRead ? DesignColor.TEXT_SECONDARY : DesignColor.TEXT_PRIMARY)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .margin({ top: DesignDimens.SPACE_SM })

      if (this.vo.content.length > 0) {
        Text(this.vo.content)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(this.vo.isRead ? DesignColor.TEXT_TERTIARY : DesignColor.TEXT_SECONDARY)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XXS })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor(DesignColor.OUTLINE)
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    .onClick(() => {
      this.onCardClick()
    })
  }
}
