// 物品标签列表页（一物一码 M2）— 对齐安卓 feature/toolbox/ui/assettag/AssetTagListPage.kt
//
// 分页：PagingController 手动 loadMore（每页 10）；创建/状态流转/删除成功回列表由变更信号（ToolboxChangeSignal）重拉。
// 条目点击进详情：全量字段随路由携带（详情页不再请求）。
// 卡片右列含累计扫码数（失主线索）；标签链接的复制/打开在详情页承载。

import { HttpStatusCode } from '@ohos/axios'
import { RouteNames } from '../../constant/RouteNames'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../../constant/DesignSystem'
import {
  AssetTagVo,
  TOOLBOX_PAGE_SIZE,
  AssetTagDetailParam,
  ToolboxText,
  assetTagScansFormat,
  pageAssetTags,
  toolboxAssetTagStatusText
} from './api'
import { consumeAssetTagChanged } from './ToolboxChangeSignal'
import { AppButton, EmptyState, IconBox, SkeletonBlock, SkeletonList } from '../../view/common/AppComponents'
import { logger } from '../../utils/Logger'
import { PagingController } from '../../utils/PagingUtil'
import { pathStack } from '../../utils/PathStackUtil'
import { showToastSafety } from '../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../utils/SafeArea'
import { ThemeRefresh } from '../../view/common/ThemeRefresh'
import { resString } from '../../utils/ResourceString'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'toolboxAssetTagList'

@Builder
export function ToolboxAssetTagListBuilder() {
  ToolboxAssetTagList()
}

@Component
struct ToolboxAssetTagList {
  @State list: Array<AssetTagVo> = []
  @State isLoadingFirst: boolean = true
  @State isFirstError: boolean = false
  @State isRefreshing: boolean = false
  @State refreshingSession: boolean = false
  @State isLoadingMore: boolean = false
  @State appendError: boolean = false
  @State pressedKey: string = ''
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
    logger.debug(TAG, '物品标签列表挂载，开始首刷')
    await this.fetchPage(1)
    this.isLoadingFirst = false
  }

  async fetchPage(page: number): Promise<boolean> {
    try {
      const res = await pageAssetTags(page, TOOLBOX_PAGE_SIZE)
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        const records: Array<AssetTagVo> = res.data.records
        if (records.length === 0) {
          if (page === 1) {
            this.list = []
            this.isFirstError = false
            this.appendError = false
            return false
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
        logger.debug(TAG, `分页加载成功 page=${page} count=${records.length}`)
        return true
      }
      if (page === 1) {
        this.isFirstError = true
        showToastSafety(ToolboxText.toolbox_error_load, ToolboxText.toolbox_error_load, 3500)
        return false
      }
      this.appendError = true
      throw new Error(ToolboxText.toolbox_error_load)
    } catch (err) {
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

  private goDetail(vo: AssetTagVo): void {
    const param: AssetTagDetailParam = new AssetTagDetailParam()
    param.id = vo.id ?? 0
    param.code = vo.code ?? ""
    param.itemName = vo.itemName ?? ""
    param.itemDesc = vo.itemDesc ?? ""
    param.status = vo.status ?? 0
    param.hasContact = vo.hasContact ?? false
    param.tagUrl = vo.tagUrl ?? ""
    param.scanCount = vo.scanCount ?? 0
    param.lastScannedAt = vo.lastScannedAt ?? ""
    pathStack.pushPathByName(RouteNames.TOOLBOX_ASSET_TAG_DETAIL, param)
  }

  private goCreate(): void {
    pathStack.pushPathByName(RouteNames.TOOLBOX_ASSET_TAG_CREATE, null)
  }

  /** 静态标签 chip（对齐安卓 ToolboxTagChip） */
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

  /** 状态 chip 配色：寻物中 = ERROR_CONTAINER / 已寻回 = PRIMARY_CONTAINER / 正常 = SURFACE_VARIANT */
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
  ShortCodeCard(vo: AssetTagVo) {
    Column() {
      Row() {
        IconBox({
          icon: $r('sys.symbol.link'),
          sizeValue: 36,
          iconSize: 20,
          containerColor: $r('app.color.ICON_CONTAINER_TEAL'),
          iconColor: $r('app.color.ICON_FOREGROUND_TEAL')
        })
        Column() {
          Text(vo.itemName ?? '')
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Row({ space: 4 }) {
            this.TagChip(toolboxAssetTagStatusText(vo.status),
              this.statusContainerColor(vo.status ?? 0), this.statusContentColor(vo.status ?? 0))
          }
          .margin({ top: DesignDimens.SPACE_XXS })
          Text(assetTagScansFormat(vo.scanCount ?? 0))
            .fontSize(DesignFontSize.CAPTION)
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .fontFeature(DesignFontFeature.TNUM)
            .margin({ top: DesignDimens.SPACE_XS })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

        Text(vo.code ?? '')
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
          .fontFeature(DesignFontFeature.TNUM)
          .margin({ left: DesignDimens.SPACE_SM })
      }
      .width('100%')
      .alignItems(VerticalAlign.Top)

      if ((vo.itemDesc ?? '').length > 0) {
        Text(vo.itemDesc ?? '')
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
    .padding({ left: DesignDimens.CARD_PADDING, top: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING, bottom: DesignDimens.SPACE_SM })
    .backgroundColor($r('app.color.SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
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

  @Builder
  Placeholder(message: string, actionText: string, isError: boolean) {
    Column() {
      EmptyState({ message: message }) {
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

  build() {
    NavDestination() {
      ThemeRefresh() {
        Column() {
          // 页面标题 + 「新建标签」文字按钮（对齐安卓 PageHeader）
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

            Text(ToolboxText.asset_tag_title)
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor($r('app.color.TEXT_PRIMARY'))

            Blank()

            Text(ToolboxText.asset_tag_action_new)
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
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
          .alignItems(VerticalAlign.Center)

          if (this.isLoadingFirst) {
            SkeletonList({ rows: 4 })
              .width('100%')
              .layoutWeight(1)
          } else if (this.isFirstError && this.list.length === 0) {
            this.Placeholder(ToolboxText.toolbox_error_load, ToolboxText.toolbox_action_retry, true)
          } else if (this.list.length === 0 && this.refreshingSession) {
            SkeletonList({ rows: 4 })
              .width('100%')
              .layoutWeight(1)
          } else if (this.list.length === 0 && !this.refreshingSession) {
            this.Placeholder(ToolboxText.asset_tag_empty_message, ToolboxText.asset_tag_action_new, false)
          } else {
            Refresh({ refreshing: $$this.isRefreshing }) {
              List({ space: DesignDimens.CARD_SPACING }) {
                ForEach(this.list, (vo: AssetTagVo) => {
                  ListItem() {
                    this.ShortCodeCard(vo)
                  }
                }, (vo: AssetTagVo) => `${vo.id ?? 0}`)

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
                    Text(ToolboxText.toolbox_error_load)
                      .fontSize(DesignFontSize.CAPTION)
                      .fontWeight(DesignFontWeight.MEDIUM)
                      .fontColor($r('app.color.DANGER_INK'))
                      .width('100%')
                      .textAlign(TextAlign.Center)
                      .padding({ top: DesignDimens.SPACE_MD, bottom: DesignDimens.SPACE_MD })
                      .onClick(() => {
                        this.paging.retry()
                      })
                  }
                }
              }
              .width('100%')
              .height('100%')
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS, bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_XL) })
              .divider({ strokeWidth: 0, color: Color.Transparent })
              .onScrollIndex((start: number, end: number) => {
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
        .padding({ top: safeTopVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      if (!this.hasShown) {
        this.hasShown = true
        return
      }
      if (consumeAssetTagChanged()) {
        logger.debug(TAG, '消费到标签变更信号，重拉列表')
        this.refreshList()
      }
    })
  }
}
