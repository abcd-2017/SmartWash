// 我的短链列表页 — 对齐安卓 feature/toolbox/ui/list/ToolboxListPage.kt
//
// 分页：鸿蒙无 Paging 3 等价物，按 02 文档结论用 PagingController 手动 loadMore（每页 10，无人工延时）。
// 四态：首刷 loading / 首刷失败重试 / 空态 / 列表 + 尾部（加载中/失败重试）。
// 刷新：创建/续期/删除成功回列表后由 NavDestination.onShown 重拉（等价安卓 savedStateHandle CHANGED_KEY 信号）。
// 条目点击进详情：全量字段随路由携带（详情页不再请求）。

import { HttpStatusCode } from '@ohos/axios'
import { RouteNames } from '../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../constant/DesignSystem'
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
import { AppButton, EmptyState, IconBox } from '../../view/common/AppComponents'
import { LoadingFooter } from '../../view/LoadingFooter'
import { PagingController } from '../../utils/PagingUtil'
import { pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'

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
    await this.fetchPage(1)
    this.isLoadingFirst = false
  }

  /**
   * 按页拉取短码列表。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchPage(page: number): Promise<boolean> {
    const res = await pageShortCodes(page, TOOLBOX_PAGE_SIZE)
    if (res.code === HttpStatusCode.Ok && res.data !== null) {
      const records: Array<ShortCodeVo> = res.data.records
      if (records.length === 0) {
        if (this.list.length > 0) {
          showToastSafety("没有更多数据了")
        }
        return false
      }
      if (page === 1) {
        this.list = []
        this.isFirstError = false
      }
      this.list.push(...records)
      return true
    }
    if (page === 1) {
      this.isFirstError = true
    }
    showToastSafety(res.message, ToolboxText.toolbox_error_load)
    return false
  }

  /** 整列表重置重拉（下拉刷新 + onShown 回列表刷新共用） */
  refreshList(): void {
    this.isRefreshing = false
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

  /** 过期态颜色 */
  private expiryColorOf(vo: ShortCodeVo): number {
    return expiryStateOf(vo.expireAt, new Date()) === ExpiryState.EXPIRED
      ? DesignColor.DANGER_INK : DesignColor.TEXT_TERTIARY
  }

  /** 静态标签 chip（对齐安卓 ToolboxTagChip：胶囊 + labelSmall） */
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
        .margin({ top: DesignDimens.SPACE_MD })
        }
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
          iconSize: 18,
          containerColor: DesignColor.ICON_CONTAINER_BLUE,
          iconColor: DesignColor.ICON_FOREGROUND_BLUE
        })

        Column() {
          Text(vo.code ?? "")
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })

          Row({ space: DesignDimens.SPACE_XS }) {
            this.TagChip(ToolboxText.toolbox_type_short_link,
              DesignColor.SURFACE_VARIANT, DesignColor.TEXT_SECONDARY)
            if (this.isPublicOf(vo)) {
              this.TagChip(ToolboxText.toolbox_visibility_public_value,
                DesignColor.ICON_CONTAINER_GREEN, DesignColor.PRIMARY_DARK)
            } else {
              this.TagChip(ToolboxText.toolbox_visibility_private_value,
                DesignColor.WARNING_CONTAINER, DesignColor.ON_WARNING_CONTAINER)
            }
          }
          .margin({ top: DesignDimens.SPACE_XS })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

        Column() {
          Text(toolboxClicksFormat(vo.clickCount ?? 0))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_TERTIARY)
          Text(this.expiryTextOf(vo))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(this.expiryColorOf(vo))
        }
        .alignItems(HorizontalAlign.End)
        .margin({ left: DesignDimens.SPACE_SM })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      if ((vo.target ?? "").length > 0) {
        Text(vo.target ?? "")
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_SM })
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
    .onClick(() => {
      this.goDetail(vo)
    })
  }

  build() {
    NavDestination() {
      Column() {
        // 页面标题 + 「新建」文字按钮（对齐安卓 PageHeader actions，主色 13fp/600 无底框）
        Row() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(24)
            .fontColor([DesignColor.TEXT_PRIMARY])
            .onClick(() => {
              pathStack.pop()
            })
            .padding(8)

          Text(ToolboxText.toolbox_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
            .margin({ left: 4 })

          Blank()

          Text(ToolboxText.toolbox_action_new)
            .fontSize(DesignFontSize.CAPTION)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.PRIMARY)
            .padding({ left: 12, right: 12, top: 8, bottom: 8 })
            .onClick(() => {
              this.goCreate()
            })
        }
        .width('100%')
        .height(56)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .alignItems(VerticalAlign.Center)

        if (this.isLoadingFirst) {
          // 首刷 loading：鸿蒙无骨架屏组件，降级为居中占位（对齐 04 §2.4「降级为 LoadingFooter + 空态」）
          Column() {
            LoadingProgress()
              .width(36)
              .height(36)
              .color(DesignColor.PRIMARY)
            Text("加载中")
              .fontSize(DesignFontSize.CAPTION)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .margin({ top: 8 })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (this.isFirstError && this.list.length === 0) {
          this.Placeholder(ToolboxText.toolbox_error_load, ToolboxText.toolbox_action_retry, true)
        } else if (this.list.length === 0) {
          this.Placeholder(ToolboxText.toolbox_empty_message, ToolboxText.toolbox_empty_action, false)
        } else {
          Refresh({ refreshing: $$this.isRefreshing }) {
            List() {
              ForEach(this.list, (vo: ShortCodeVo) => {
                ListItem() {
                  this.ShortCodeCard(vo)
                }
              }, (vo: ShortCodeVo) => `${vo.id ?? 0}`)

              // 尾部：加载中 / 失败重试（对齐安卓 append LoadState）
              ListItem() {
                if (this.isLoadingMore) {
                  LoadingFooter({ isLoading: true })
                } else if (this.isFirstError) {
                  Text(ToolboxText.toolbox_error_load)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor(DesignColor.DANGER_INK)
                    .width('100%')
                    .textAlign(TextAlign.Center)
                    .padding({ top: 12, bottom: 12 })
                    .onClick(() => {
                      this.fetchPage(this.paging.currentPage() + 1)
                    })
                }
              }
            }
            .width('100%')
            .height('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 8, bottom: 8 })
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
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 创建/续期/删除成功回列表 → 重拉（首次 onShown 与 aboutToAppear 去重）
      if (!this.hasShown) {
        this.hasShown = true
        return
      }
      this.refreshList()
    })
  }
}
