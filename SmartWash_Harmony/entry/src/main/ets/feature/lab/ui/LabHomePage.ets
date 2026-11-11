/**
 * 解压实验舱首页 — 对齐安卓 feature/lab/ui/home/LabHomePage.kt。
 *
 * 结构：PageHeader（标题「解压实验舱」）→ 入口卡纵排列表（10 个玩具入口，顺序即展示序）。
 * 模块为纯本地（无网络、无存储），入口配置为编译期固定列表；页面仍走「本地配置加载」
 * 状态机（加载骨架 / 失败重试 / 空态 / 内容），与后端配置页的三态形态一致。
 * 卡片点击经全局 pathStack 进对应玩具页（10 个实验均已实现）。
 *
 * 六色容器/前景对不随 LabEntry 固化（跨端对比 04 #1 P0 修复）：页面按 kind 实时读
 * 资源色令牌（base/dark 限定符）（对齐安卓每次 composition 读 scheme），并以 ThemeRefreshV2
 * 包裹内容层——页面存活期间切深浅色时子树重建，整列卡片颜色跟随刷新
 * （参照同模块 FishPage/GameCenterPage 等 6 个玩具页的接入先例）。
 */

import { DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { pathStack } from '../../../utils/PathStackUtil'
import { Debouncer } from '../../../utils/DebounceUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { AppButton, EmptyState, IconBox, PageHeader, SkeletonBlock } from '../../../view/common/AppComponents'
import { ThemeRefreshV2 } from '../../../view/common/ThemeRefresh'
import { LabEntry, LabEntryKind, labEntries } from '../core/LabConfig'
import { resColor } from '../../../utils/ResourceColor'
import { resString } from '../../../utils/ResourceString'

/** 页面文案（对齐安卓 feature/lab/values/strings.xml） */
class LabText {
  static get title(): string { return resString('lab_title') }
  static get errorLoad(): string { return resString('lab_text_error_load') }
  static get actionRetry(): string { return resString('retry') }
  static get emptyMessage(): string { return resString('lab_text_empty_message') }
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
  // 当前按压中的入口卡（以 routeName 为键）：@Builder 内 10 张卡共享页面状态，按键区分哪张缩放
  @Local pressedKey: string = ''
  // 连点节流（对齐安卓 rememberDebouncedClick 500ms；页面销毁时清理）
  private debouncer: Debouncer = new Debouncer()

  aboutToAppear(): void {
    this.loadEntries()
  }

  aboutToDisappear(): void {
    this.debouncer.cancel()
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

  /**
   * 入口卡图标容器浅底色（六色语义对齐安卓 LabHomePage.kt:113-124 的 when 映射）。
   * 实时读资源色而非构造期快照——深浅色切换经 ThemeRefreshV2
   * 子树重建后此处重读新值（跨端对比 04 #1 P0 修复）。
   */
  private containerColorOf(kind: LabEntryKind): number {
    switch (kind) {
      case LabEntryKind.BUBBLE_WRAP:
        return resColor('ICON_CONTAINER_GREEN')
      case LabEntryKind.BREATH:
        return resColor('ICON_CONTAINER_BLUE')
      case LabEntryKind.FISH:
        return resColor('ICON_CONTAINER_TEAL')
      case LabEntryKind.SAND:
        return resColor('ICON_CONTAINER_ORANGE')
      case LabEntryKind.FLUID:
        return resColor('ICON_CONTAINER_PURPLE')
      case LabEntryKind.DRUM:
        return resColor('ICON_CONTAINER_PINK')
      case LabEntryKind.PAINTING:
        return resColor('ICON_CONTAINER_GREEN')
      case LabEntryKind.SOUNDSCAPE:
        return resColor('ICON_CONTAINER_BLUE')
      case LabEntryKind.PET:
        return resColor('ICON_CONTAINER_PINK')
      case LabEntryKind.STAR_MAP:
        return resColor('ICON_CONTAINER_PURPLE')
      default:
        return resColor('ICON_CONTAINER_GREEN')
    }
  }

  /** 入口卡图标前景色（与 containerColorOf 成对，六色语义对齐安卓 LabHomePage.kt:113-124） */
  private iconColorOf(kind: LabEntryKind): number {
    switch (kind) {
      case LabEntryKind.BUBBLE_WRAP:
        return resColor('ICON_FOREGROUND_GREEN')
      case LabEntryKind.BREATH:
        return resColor('ICON_FOREGROUND_BLUE')
      case LabEntryKind.FISH:
        return resColor('ICON_FOREGROUND_TEAL')
      case LabEntryKind.SAND:
        return resColor('ICON_FOREGROUND_ORANGE')
      case LabEntryKind.FLUID:
        return resColor('ICON_FOREGROUND_PURPLE')
      case LabEntryKind.DRUM:
        return resColor('ICON_FOREGROUND_PINK')
      case LabEntryKind.PAINTING:
        return resColor('ICON_FOREGROUND_GREEN')
      case LabEntryKind.SOUNDSCAPE:
        return resColor('ICON_FOREGROUND_BLUE')
      case LabEntryKind.PET:
        return resColor('ICON_FOREGROUND_PINK')
      case LabEntryKind.STAR_MAP:
        return resColor('ICON_FOREGROUND_PURPLE')
      default:
        return resColor('ICON_FOREGROUND_GREEN')
    }
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

  /** 入口卡（规范 §3.1 唯一卡片画法：白 surface + 1px outline + 卡片档阴影 0 2px 12px + radiusLg） */
  @Builder
  ToyEntryCard(entry: LabEntry) {
    Row() {
      IconBox({
        icon: entry.icon,
        sizeValue: 44,
        iconSize: 24,
        containerColor: this.containerColorOf(entry.kind),
        iconColor: this.iconColorOf(entry.kind),
        radius: DesignDimens.ICON_CONTAINER_RADIUS
      })

      Column() {
        Text(entry.title)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(resColor('TEXT_PRIMARY'))
          // 行高显式化（跨端对比 04 #7）：对齐安卓 CardTitle 18/24/600
          .lineHeight(24)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')

        Text(entry.desc)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(resColor('TEXT_SECONDARY'))
          // 行高显式化（跨端对比 04 #7）：对齐安卓 bodyMedium 14/20/400
          .lineHeight(20)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XXS })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      // 图标盒-文字间距 12（DESIGN-SPEC v0.4.1 卡片口径，跨端对比 04 #4；
      // 注：安卓 LabHomePage.kt:157 当前同为 16，安卓侧应同步改 spaceSm）
      .margin({ left: DesignDimens.SPACE_SM })
    }
    .width('100%')
    .padding(DesignDimens.CARD_PADDING)
    .alignItems(VerticalAlign.Center)
    .backgroundColor(resColor('SURFACE'))
    .borderRadius(DesignDimens.CARD_RADIUS)
    .borderWidth(1)
    .borderColor(resColor('OUTLINE'))
    // 卡片阴影档（规范 §2.4：0 2px 12px）——blur 取 CARD_SHADOW_RADIUS(12)，非海拔档位号 LEVEL_1
    // （1vp 模糊不可见，与 GameCard/AppCard 统一口径，跨端对比 04 #3）；
    // 阴影色 SHADOW_COLOR_CARD 走资源限定符，深色自动切深色变体
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: resColor('SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    // 整卡按压反馈 + 连点节流（对齐安卓 pressable：scale 0.97 / 100ms + 500ms 防抖，
    // 参照 GameCard 先例，跨端对比 04 #2）；
    // reduced motion 降级不做（鸿蒙无系统动画开关可读，端限制见 BottomNavBar.ets:70）
    .scale({
      x: this.pressedKey === entry.routeName ? 0.97 : 1,
      y: this.pressedKey === entry.routeName ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = entry.routeName
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.debouncer.throttle((): void => {
        this.openEntry(entry)
      }, 500)
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计补齐）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets；
      // 页面级数据请求保持在宿主之外，页面组件本体不在子树内）
      ThemeRefreshV2() {
        Column() {
          PageHeader({ title: LabText.title })

          if (this.isError) {
            Column() {
              EmptyState({ message: LabText.errorLoad }) {
                AppButton({
                  small: true,
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
        .backgroundColor(resColor('BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }
}
