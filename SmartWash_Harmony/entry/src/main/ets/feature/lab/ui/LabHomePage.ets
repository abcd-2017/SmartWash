/**
 * 解压实验舱首页 — 对齐安卓 feature/lab/ui/home/LabHomePage.kt。
 *
 * 结构：PageHeader（标题「解压实验舱」）→ 入口卡纵排列表（10 个玩具入口，顺序即展示序）。
 * 模块为纯本地（无网络、无存储），入口配置为编译期固定列表；页面仍走「本地配置加载」
 * 状态机（加载骨架 / 失败重试 / 空态 / 内容），与后端配置页的三态形态一致。
 * 卡片点击经全局 pathStack 进对应玩具页（后 5 项目前为占位页）。
 */

import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { pathStack } from '../../../utils/PathStackUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { AppButton, EmptyState, IconBox, PageHeader, SkeletonBlock } from '../../../view/common/AppComponents'
import { LabEntry, labEntries } from '../core/LabConfig'

/** 页面文案（对齐安卓 feature/lab/values/strings.xml） */
class LabText {
  static readonly title: string = '解压实验舱'
  static readonly errorLoad: string = '入口配置加载失败，请重试'
  static readonly actionRetry: string = '重试'
  static readonly emptyMessage: string = '实验舱还空着，去看看别的吧'
}

/** 骨架卡高度（vp）：与真实入口卡构成对齐（20 + 44 + 20） */
const SKELETON_CARD_HEIGHT: number = 84

/** 骨架行数 */
const SKELETON_ROWS: number[] = [0, 1, 2, 3]

@Builder
export function LabHomeBuilder() {
  LabHomePage()
}

@ComponentV2
export struct LabHomePage {
  @Local entries: LabEntry[] = []
  @Local loading: boolean = true
  @Local isError: boolean = false

  aboutToAppear(): void {
    this.loadEntries()
  }

  /**
   * 加载入口配置（本地固定列表，模块无网络）。
   * 失败进错误态并给重试入口；空列表视为成功空态。
   */
  private async loadEntries(): Promise<void> {
    this.loading = true
    this.isError = false
    try {
      // 本地配置读取（异步形态与后端配置页一致；短延迟让骨架态可感知）
      await new Promise<void>((resolve: () => void) => {
        setTimeout(resolve, 150)
      })
      this.entries = labEntries()
    } catch (err) {
      this.isError = true
    } finally {
      this.loading = false
    }
  }

  /** 入口卡点击：进对应玩具页 */
  private openEntry(entry: LabEntry): void {
    pathStack.pushPathByName(entry.routeName, null)
  }

  /** 加载骨架（规范 §3.6：骨架屏替代转圈，形状与真实列表一致） */
  @Builder
  EntrySkeleton() {
    Column({ space: DesignDimens.CARD_SPACING }) {
      ForEach(SKELETON_ROWS, (row: number) => {
        SkeletonBlock({ blockRadius: DesignDimens.CARD_RADIUS })
          .width('100%')
          .height(SKELETON_CARD_HEIGHT)
      }, (row: number) => row.toString())
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }

  /** 入口卡（规范 §3.1 唯一卡片画法：白 surface + 1px outline + level1 阴影 + radiusLg） */
  @Builder
  ToyEntryCard(entry: LabEntry) {
    Row() {
      IconBox({
        icon: entry.icon,
        sizeValue: 44,
        iconSize: 24,
        containerColor: entry.containerColor,
        iconColor: entry.iconColor,
        radius: DesignDimens.ICON_CONTAINER_RADIUS
      })

      Column() {
        Text(entry.title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')

        Text(entry.desc)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XXS })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .margin({ left: DesignDimens.SPACE_MD })
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
    .alignItems(VerticalAlign.Center)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor(DesignColor.OUTLINE)
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
    .onClick(() => {
      this.openEntry(entry)
    })
  }

  build() {
    NavDestination() {
      Column() {
        PageHeader({ title: LabText.title })

        if (this.isError) {
          Column() {
            EmptyState({ message: LabText.errorLoad }) {
              AppButton({
                text: LabText.actionRetry,
                click: () => {
                  this.loadEntries()
                }
              })
            }
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (this.loading) {
          Scroll() {
            this.EntrySkeleton()
          }
          .width('100%')
          .layoutWeight(1)
          .align(Alignment.TopStart)
          .scrollBar(BarState.Off)
        } else if (this.entries.length === 0) {
          Column() {
            EmptyState({ message: LabText.emptyMessage })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          Scroll() {
            Column({ space: DesignDimens.CARD_SPACING }) {
              ForEach(this.entries, (entry: LabEntry) => {
                this.ToyEntryCard(entry)
              }, (entry: LabEntry) => entry.kind.toString())
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_SM, bottom: DesignDimens.SECTION_SPACING })
          }
          .width('100%')
          .layoutWeight(1)
          .align(Alignment.TopStart)
          .scrollBar(BarState.Off)
        }
      }
      .width('100%')
      .height('100%')
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}
