// 我的短链列表页 — 对齐安卓 feature/toolbox/ui/list/ToolboxListPage.kt
//
// 分页：鸿蒙无 Paging 3 等价物，按 02 文档结论用 PagingController 手动 loadMore（每页 10，无人工延时）。
// 四态：首刷 loading / 首刷失败重试 / 空态 / 列表 + 尾部（加载中/失败重试）。
// 刷新：创建/续期/删除成功回列表后由变更信号（ToolboxChangeSignal）重拉，等价安卓 savedStateHandle CHANGED_KEY。
// 条目点击进详情：全量字段随路由携带（详情页不再请求）。
// 卡片右列含「复制短链」描边按钮（对齐安卓 CopyPillButton）。

import { HttpStatusCode } from '@ohos/axios'
import { BusinessError, pasteboard } from '@kit.BasicServicesKit'
import { RouteNames } from '../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../constant/DesignSystem'
import {
  CONTENT_TYPE_SHORT_LINK,
  ShortCodeVo,
  TOOLBOX_PAGE_SIZE,
  ToolboxShortCodeDetailParam,
  ToolboxText,
  VISIBILITY_PUBLIC,
  pageShortCodes,
  toolboxClicksFormat,
  toolboxExpiryUntil
} from './api'
import { ExpiryState, expiryStateOf, formatDisplayDateTime, isPublicVisibility } from './ShortCodeRules'
import { consumeShortCodeChanged } from './ToolboxChangeSignal'
import { AppButton, EmptyState, IconBox, SkeletonBlock, SkeletonList } from '../../view/common/AppComponents'
import { logger } from '../../utils/Logger'
import { PagingController } from '../../utils/PagingUtil'
import { pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../utils/SafeArea'

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
          if (this.list.length > 0) {
            showToastSafety("没有更多数据了")
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
        showToastSafety(res.message, ToolboxText.toolbox_error_load)
        return false
      }
      // 追加失败：置失败态并 reject（分页器记失败页不封底，footer 重试重拉同一页，页码不推进）
      logger.warn(TAG, `短链追加失败 page=${page} code=${res.code ?? 0} msg=${res.message ?? ''}`)
      this.appendError = true
      showToastSafety(res.message, ToolboxText.toolbox_error_load)
      throw new Error(ToolboxText.toolbox_error_load)
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：与「追加失败」同语义（reject 供分页器记失败页）；
      // 首刷则进错误态，避免 isLoadingFirst 卡死永久骨架
      if (page === 1) {
        this.isFirstError = true
        showToastSafety("网络异常，请稍后重试")
        return false
      }
      this.appendError = true
      showToastSafety("网络异常，请稍后重试")
      throw new Error(ToolboxText.toolbox_error_load)
    }
  }

  /** 整列表重置重拉（下拉刷新 + 变更信号回列表刷新共用） */
  refreshList(): void {
    this.isRefreshing = false
    this.appendError = false
    this.paging.reset()
    this.list = []
    this.fetchPage(1)
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
    pathStack.pushPathByName(RouteNames.TOOLBOX_SHORT_CODE_DETAIL, param)
  }

  private goCreate(): void {
    pathStack.pushPathByName(RouteNames.TOOLBOX_SHORT_CODE_CREATE, null)
  }

  /** 复制短链（空缺回落短码）+ 提示，与详情页复制行为一致（对齐安卓 onCopy） */
  private copyShortCode(vo: ShortCodeVo): void {
    const text = (vo.shortUrl ?? "").length > 0 ? (vo.shortUrl ?? "") : (vo.code ?? "")
    if (text.length === 0) {
      return
    }
    const pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text)
    pasteboard.getSystemPasteboard().setData(pasteData).then(() => {
      showToastSafety(ToolboxText.toolbox_copy_success)
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
  private expiryColorOf(vo: ShortCodeVo): number {
    return expiryStateOf(vo.expireAt, new Date()) === ExpiryState.EXPIRED
      ? DesignColor.DANGER_INK : DesignColor.TEXT_SECONDARY
  }

  /** 过期态字重（对齐安卓：过期 labelSmall 12/500，其余 bodySmall 12/400） */
  private expiryWeightOf(vo: ShortCodeVo): number {
    return expiryStateOf(vo.expireAt, new Date()) === ExpiryState.EXPIRED
      ? DesignFontWeight.MEDIUM : DesignFontWeight.NORMAL
  }

  /** 静态标签 chip（对齐安卓 ToolboxTagChip：胶囊 + labelSmall 12） */
  @Builder
  TagChip(text: string, containerColor: number, contentColor: number) {
    Text(text)
      .fontSize(DesignFontSize.CAPTION)
      .fontColor(contentColor)
      .padding({ left: 8, right: 8, top: 2, bottom: 2 })
      .backgroundColor(containerColor)
      .borderRadius(DesignDimens.RADIUS_FULL)
  }

  /**
   * 复制描边按钮 — 对齐安卓 CopyPillButton：视觉胶囊（透明底 + 1px outline +
   * captionStrong 12/500 primaryDark），外扩 4/8 热区至 60×48 满足 48dp 触控红线。
   */
  @Builder
  CopyPillButton(text: string, vo: ShortCodeVo) {
    Column() {
      Text(text)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor(DesignColor.PRIMARY_DARK)
        .padding({ left: 14, right: 14, top: 8, bottom: 8 })
        .border({ width: 1, color: DesignColor.OUTLINE })
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
          containerColor: DesignColor.ICON_CONTAINER_BLUE,
          iconColor: DesignColor.ICON_FOREGROUND_BLUE
        })

        Column() {
          Text(vo.code ?? "")
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Row({ space: 4 }) {
            this.TagChip(ToolboxText.toolbox_type_short_link,
              DesignColor.SURFACE_VARIANT, DesignColor.TEXT_SECONDARY)
            if (this.isPublicOf(vo)) {
              // 公开 = primaryContainer/onPrimaryContainer（对齐安卓）
              this.TagChip(ToolboxText.toolbox_visibility_public_value,
                DesignColor.PRIMARY_CONTAINER, DesignColor.ON_PRIMARY_CONTAINER)
            } else {
              // 私有 = warningContainer/warning（对齐安卓）
              this.TagChip(ToolboxText.toolbox_visibility_private_value,
                DesignColor.WARNING_CONTAINER, DesignColor.WARNING)
            }
          }
          .margin({ top: DesignDimens.SPACE_XS })

          // 有效期独占一行（对齐安卓重设计）：长文本「有效期至 yyyy-MM-dd HH:mm」不再与
          // chips 同行竞争宽度——原布局中该文本在右列，窄屏会把「公开/私有」chip 挤压换行；
          // maxLines(1)+ellipsis 兜底极端窄屏，完整到期时间在详情页可见
          Text(this.expiryTextOf(vo))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(this.expiryColorOf(vo))
            .fontWeight(this.expiryWeightOf(vo))
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
            .fontColor(DesignColor.TEXT_SECONDARY)
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
          .fontColor(DesignColor.TEXT_SECONDARY)
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
      Column() {
        // 页面标题 + 「新建」文字按钮（对齐安卓 PageHeader：标题 22/600，动作 18/600 primaryDark + 48 热区）
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

          Text(ToolboxText.toolbox_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)

          Blank()

          Text(ToolboxText.toolbox_action_new)
            .fontSize(DesignFontSize.PAGE_HEADER)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.PRIMARY_DARK)
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
        } else if (this.list.length === 0) {
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
