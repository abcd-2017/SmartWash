/**
 * 游戏中心页 —— 对齐安卓 feature/game/.../ui/center/GameCenterPage.kt。
 *
 * 结构：PageHeader（标题「游戏中心」）→ 分类筛选 chips（全部/益智/街机/文字/桌版，本地过滤）
 * → 两列网格（44vp 图标色块 + 标题 + 描述两行 + badge 角标）。
 * 数据来自 GET /web/auth/game/list（全量 <100 不分页），三态：骨架屏 / 空态 / 错误重试
 * （对齐安卓 when 分支顺序：Error 优先，其次 Loading 骨架，再次空态，最后网格）。
 * 卡片点击 → 既有 WebPage 页（后端下发 url 即最终可玩地址，端上零拼装；url 为空不响应）。
 *
 * 域目录约定（feature/README.md）：本文件为域内实现，仅经 network/GameApi 对外暴露契约。
 */

import { BizCode } from '../../../network/HttpUtil'
import { RouteNames } from '../../../constant/RouteNames'
import { logger } from '../../../utils/Logger'
import { BusinessError } from '@kit.BasicServicesKit'
import {
  DesignColor,
  DesignDimens,
  DesignElevation,
  DesignFontSize,
  DesignFontWeight
} from '../../../constant/DesignSystem'
import { pathStack } from '../../../utils/PathStackUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { AppButton, EmptyState, IconBox, PageHeader, SkeletonBlock } from '../../../view/common/AppComponents'
import { WebPageParam } from '../../../pages/WebPage'
import {
  GAME_CHIP_ORDER,
  GameCategory,
  GameItem,
  GameTint,
  GameVo,
  gameVoToItem,
  getGameList,
  withUniqueKeys
} from '../network/GameApi'
import { ThemeRefreshV2 } from '../../../view/common/ThemeRefresh'

/** 网格列数（对齐安卓 GRID_COLUMNS = 2） */
const GRID_COLUMNS: number = 2

/** 网格卡片间距（对齐安卓 GRID_SPACING = spaceXs 8） */
const GRID_SPACING: number = DesignDimens.SPACE_XS

/** 骨架卡高度（vp）：与真实卡构成对齐（16:9 封面 ~94 + 描述区 ~64） */
const SKELETON_CARD_HEIGHT: number = 158

/** 页面文案（对齐安卓 feature/game/values/strings.xml） */
class GameText {
  static readonly title: string = '游戏中心'
  static readonly categoryAll: string = '全部'
  static readonly emptyMessage: string = '游戏架还空着，去后台上传几个'
  static readonly emptyCategory: string = '该分类暂无游戏，看看全部吧'
  static readonly errorLoad: string = '游戏加载失败，请检查网络后重试'
  static readonly actionRetry: string = '重试'
}

/** 分类 chip 文案（全部之外的固定四类映射；OTHER 无 chip 无文案） */
function categoryChipLabel(category: GameCategory): string {
  switch (category) {
    case GameCategory.PUZZLE:
      return '益智'
    case GameCategory.ARCADE:
      return '街机'
    case GameCategory.WORD:
      return '文字'
    case GameCategory.BOARD:
      return '桌版'
    default:
      return ''
  }
}

/**
 * 分类筛选 chip —— 用 @ComponentV2 + @Param 承载选中态（已知坑：@Builder 按值传参在
 * selectedCategory 变更后不重建，chip 背景定格、列表也不过滤）。点击经 @Event 上抛，
 * 由页面统一写 selectedCategory。
 */
@ComponentV2
struct GameCategoryChip {
  @Param label: string = ''
  @Param selected: boolean = false
  @Param chipIndex: number = 0
  @Event onSelect: (chipIndex: number) => void = (_: number) => {
  }

  build() {
    Column() {
      Row() {
        Text(this.label)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(this.selected ? DesignColor.ON_PRIMARY : DesignColor.TEXT_SECONDARY)
      }
      // 垂直内边距 9 → 14：视觉高对齐安卓 AppPillTabRow 的 44dp（文字行盒 16 + 上下 14）
      .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD, top: 14, bottom: 14 })
      .borderRadius(DesignDimens.RADIUS_FULL)
      .backgroundColor(this.selected ? DesignColor.PRIMARY : DesignColor.SURFACE)
      .border({ width: 1, color: this.selected ? DesignColor.PRIMARY : DesignColor.OUTLINE })
      .justifyContent(FlexAlign.Center)
    }
    .padding({ top: 5, bottom: 5 })
    .margin({ right: DesignDimens.SPACE_XS })
    .onClick(() => {
      this.onSelect(this.chipIndex)
    })
  }
}

/**
 * 游戏网格（两列，奇数末行补位；行 key 由行号 + 行内 gameKey 拼接，对齐安卓稳定 key）。
 * @ComponentV2 + @Param：原 @Builder 按值传参在筛选后不重建，列表定格（已知坑）。
 */
@ComponentV2
struct GameGrid {
  @Param games: GameItem[] = []
  // 服务端封面加载失败的 gameKey 集（onError 不可变追加，触发卡片回退本地封面）
  @Local serverCoverFailed: string[] = []
  // 本地兜底封面也失败的 gameKey 集（随包内置 30 款，正常为空；新上架未随包游戏会走到这）
  @Local localCoverFailed: string[] = []

  /** 服务端封面加载失败 → 该 gameKey 回退本地 rawfile 封面（不可变追加触发刷新） */
  private markServerCoverFailed(gameKey: string): void {
    if (this.serverCoverFailed.indexOf(gameKey) < 0) {
      this.serverCoverFailed = [...this.serverCoverFailed, gameKey]
    }
  }

  /** 本地兜底封面也失败（随包未内置的新游戏）→ 回退图标卡 */
  private markLocalCoverFailed(gameKey: string): void {
    if (this.localCoverFailed.indexOf(gameKey) < 0) {
      this.localCoverFailed = [...this.localCoverFailed, gameKey]
    }
  }

  /** 该游戏当前是否走服务端封面（下发非空且未失败） */
  private useServerCover(game: GameItem): boolean {
    return game.coverUrl.length > 0 && this.serverCoverFailed.indexOf(game.gameKey) < 0
  }

  /** 该游戏当前是否走本地兜底封面（服务端不可用且本地未失败） */
  private useLocalCover(game: GameItem): boolean {
    return !this.useServerCover(game) && this.localCoverFailed.indexOf(game.gameKey) < 0
  }

  /** 两列分行（对齐安卓 games.chunked(2)） */
  private buildRows(games: GameItem[]): GameItem[][] {
    const rows: GameItem[][] = []
    for (let i = 0; i < games.length; i += GRID_COLUMNS) {
      rows.push(games.slice(i, i + GRID_COLUMNS))
    }
    return rows
  }

  /** 游戏卡点击：进既有 WebPage（url 为空不响应，对齐安卓 navigateToGameWebView 防御） */
  private openGame(game: GameItem): void {
    if (game.url.length === 0) {
      return
    }
    // 游戏入场留痕（低频动作按约定记 debug；url 不落日志）
    logger.debug('gameCenterPage', `入场游戏 gameKey=${game.gameKey}`)
    const param: WebPageParam = new WebPageParam()
    param.url = game.url
    param.title = game.title
    pathStack.pushPathByName(RouteNames.WEB_PAGE, param)
  }

  build() {
    Scroll() {
      Column() {
        ForEach(this.buildRows(this.games), (row: GameItem[], index: number) => {
          Row({ space: GRID_SPACING }) {
            ForEach(row, (game: GameItem) => {
              Column() {
                GameCard({
                  game: game,
                  useServerCover: this.useServerCover(game),
                  useLocalCover: this.useLocalCover(game),
                  onOpen: (item: GameItem): void => {
                    this.openGame(item)
                  },
                  onServerCoverError: (gameKey: string): void => {
                    this.markServerCoverFailed(gameKey)
                  },
                  onLocalCoverError: (gameKey: string): void => {
                    this.markLocalCoverFailed(gameKey)
                  }
                })
                  .width('100%')
              }
              .layoutWeight(1)
            }, (game: GameItem) => game.gameKey)
            // 接口返回奇数个游戏时末行补位，保持两列网格
            if (row.length === 1) {
              Column() {
              }
              .layoutWeight(1)
            }
          }
          .width('100%')
          .padding({ bottom: GRID_SPACING })
        }, (row: GameItem[], index: number) =>
          `${index}_${row.length}_${row.length > 0 ? row[0].gameKey : ''}`)
      }
      .width('100%')
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XS, bottom: safeBottomVp() })
    }
    .scrollBar(BarState.Off)
    .width('100%')
    .layoutWeight(1)
    .align(Alignment.TopStart)
  }
}

/**
 * 游戏卡（对齐安卓 GameCard）：白 surface + 1px outline 描边 + level1 阴影 + 圆角 16；
 * 44vp 图标色块（六色浅底 + 对应前景色，圆角 16）+ 标题（14/400，1 行截断）
 * + 描述（12/400，2 行截断）+ badge 文字角标（主色浅底胶囊）。
 * @ComponentV2 + @Param：避免 @Builder 按值传参在列表重建时定格。
 */
@ComponentV2
struct GameCard {
  @Require @Param game: GameItem
  @Param useServerCover: boolean = false
  @Param useLocalCover: boolean = false
  @Event onOpen: (game: GameItem) => void = (_: GameItem) => {
  }
  @Event onServerCoverError: (gameKey: string) => void = (_: string) => {
  }
  @Event onLocalCoverError: (gameKey: string) => void = (_: string) => {
  }

  /** 图标容器浅底色（后端 color 白名单 → DesignColor.ICON_CONTAINER_*，未知兜底 GREEN） */
  private containerColor(tint: GameTint): number {
    switch (tint) {
      case GameTint.GREEN:
        return DesignColor.ICON_CONTAINER_GREEN
      case GameTint.BLUE:
        return DesignColor.ICON_CONTAINER_BLUE
      case GameTint.ORANGE:
        return DesignColor.ICON_CONTAINER_ORANGE
      case GameTint.PINK:
        return DesignColor.ICON_CONTAINER_PINK
      case GameTint.TEAL:
        return DesignColor.ICON_CONTAINER_TEAL
      case GameTint.PURPLE:
        return DesignColor.ICON_CONTAINER_PURPLE
      default:
        return DesignColor.ICON_CONTAINER_GREEN
    }
  }

  /** 图标前景色（后端 color 白名单 → DesignColor.ICON_FOREGROUND_*，未知兜底 GREEN） */
  private iconColor(tint: GameTint): number {
    switch (tint) {
      case GameTint.GREEN:
        return DesignColor.ICON_FOREGROUND_GREEN
      case GameTint.BLUE:
        return DesignColor.ICON_FOREGROUND_BLUE
      case GameTint.ORANGE:
        return DesignColor.ICON_FOREGROUND_ORANGE
      case GameTint.PINK:
        return DesignColor.ICON_FOREGROUND_PINK
      case GameTint.TEAL:
        return DesignColor.ICON_FOREGROUND_TEAL
      case GameTint.PURPLE:
        return DesignColor.ICON_FOREGROUND_PURPLE
      default:
        return DesignColor.ICON_FOREGROUND_GREEN
    }
  }

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column() {
        // 封面三级兜底：服务端 coverUrl → 本地 rawfile（随包 30 款）→ 图标色块；
        // 两级 onError 经事件上抛页面登记（失败集在页面状态，卡片无状态）
        Stack({ alignContent: Alignment.BottomStart }) {
          if (this.useServerCover) {
            Image(this.game.coverUrl)
              .width('100%')
              .aspectRatio(1.78)
              .objectFit(ImageFit.Cover)
              .onError(() => {
                this.onServerCoverError(this.game.gameKey)
              })
          } else if (this.useLocalCover) {
            Image(this.game.localCover)
              .width('100%')
              .aspectRatio(1.78)
              .objectFit(ImageFit.Cover)
              .onError(() => {
                this.onLocalCoverError(this.game.gameKey)
              })
          } else {
            Row() {
              IconBox({
                icon: this.game.icon,
                sizeValue: 44,
                iconSize: 24,
                containerColor: this.containerColor(this.game.tint),
                iconColor: this.iconColor(this.game.tint),
                radius: DesignDimens.RADIUS_MD
              })
            }
            .width('100%')
            .aspectRatio(1.78)
            .justifyContent(FlexAlign.Center)
            .backgroundColor(DesignColor.SURFACE_VARIANT)
          }

          // 底部渐变遮罩 + 白字标题（封面上直接读，省一排行高；对齐安卓卡片样式）
          if (this.useServerCover || this.useLocalCover) {
            Stack({ alignContent: Alignment.BottomStart }) {
              Column()
                .width('100%')
                .height(44)
                .linearGradient({
                  angle: 180,
                  // 封面压暗渐变令牌化（图片上沉浸层深浅共用，对齐安卓 ImageScrim 语义）
                  colors: [[DesignColor.IMAGE_SCRIM_FADE, 0], [DesignColor.IMAGE_SCRIM, 1]]
                })
              Text(this.game.title)
                .fontSize(DesignFontSize.BODY_SMALL)
                .fontWeight(DesignFontWeight.MEDIUM)
                .fontColor(DesignColor.ON_PRIMARY)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .width('100%')
                .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_XS })
            }
            .width('100%')
          }
        }
        .width('100%')
        .borderRadius({ topLeft: DesignDimens.CARD_RADIUS, topRight: DesignDimens.CARD_RADIUS })

        Text(this.game.description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .padding({
            left: DesignDimens.CARD_PADDING,
            right: DesignDimens.CARD_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.CARD_PADDING
          })
          .margin({ top: DesignDimens.SPACE_SM })

        // 描述字重对齐安卓 labelSmall 12/500（原 400）
        Text(this.game.description)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XXS })
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      .padding(DesignDimens.CARD_PADDING)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 1, color: DesignColor.OUTLINE })
      .backgroundColor(DesignColor.SURFACE)
      .shadow({
        radius: DesignElevation.LEVEL_1,
        color: DesignColor.SHADOW_COLOR_CARD,
        offsetX: 0,
        offsetY: 2
      })

      // badge 角标（服务端下发文案）：清氧小胶囊 — radiusFull + 主色浅底/主色文字
      if (this.game.badge.length > 0) {
        Text(this.game.badge)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .backgroundColor(DesignColor.SURFACE)
          .borderRadius(DesignDimens.RADIUS_FULL)
          .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: 2, bottom: 2 })
          .margin({ top: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM })
          .shadow({
            radius: DesignElevation.LEVEL_1,
            color: DesignColor.SHADOW_COLOR_CARD,
            offsetX: 0,
            offsetY: 1
          })
      }
    }
    .width('100%')
    .onClick(() => {
      this.onOpen(this.game)
    })
  }
}

@Builder
export function GameCenterBuilder() {
  GameCenterPage()
}

@ComponentV2
export struct GameCenterPage {
  @Local games: GameItem[] = []
  @Local isLoading: boolean = true
  @Local isError: boolean = false
  @Local selectedCategory: GameCategory | null = null
  // 服务端封面加载失败的 gameKey 集（onError 不可变追加，触发卡片回退本地封面）
  @Local serverCoverFailed: string[] = []
  // 本地兜底封面也失败的 gameKey 集（随包内置 30 款，正常为空；新上架未随包游戏会走到这）
  @Local localCoverFailed: string[] = []


  aboutToAppear(): void {
    logger.debug('gameCenterPage', '游戏中心挂载，拉取游戏列表')
    this.loadGames()
  }

  /**
   * 拉取游戏列表（对齐安卓 GameCenterViewModel.loadGames）：
   * 失败进 Error 态（页面给重试入口），空列表视为成功空态。
   */
  private async loadGames(): Promise<void> {
    this.isLoading = true
    this.isError = false
    try {
      const res = await getGameList()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.games = withUniqueKeys(res.data.map((vo: GameVo, index: number): GameItem =>
          gameVoToItem(vo, index)))
      } else {
        // 业务拒绝留痕：非 200 或数据载荷缺失，进错误态（仅记 code/message 摘要）
        logger.warn('gameCenterPage', `游戏列表未生效，进错误态 code=${res.code} message=${res.message ?? ""}`)
        this.isError = true
      }
    } catch (err) {
      // 降级留痕：拉取异常进错误态给重试入口（仅 error.message 摘要，不序列化 error）
      const e: BusinessError = err as BusinessError
      logger.warn('gameCenterPage', `游戏列表加载异常，进错误态 err=${e.message}`)
      this.isError = true
    }
    this.isLoading = false
  }

  /** 分类过滤后的列表（本地过滤，无分页；对齐安卓 filteredGames） */
  private filteredGames(): GameItem[] {
    if (this.selectedCategory === null) {
      return this.games
    }
    return this.games.filter((game: GameItem): boolean => game.category === this.selectedCategory)
  }

  /** 骨架行下标（ArkTS 不在 build 内构造数组，收敛为方法） */
  private skeletonRows(): number[] {
    return [0, 1, 2]
  }

  /**
   * 分类筛选 chips 行（可横滚；对齐安卓 AppPillTabRow：选中 = primary 实底 + 白字，
   * 未选中 = surface 白底 + 1px outline 描边 + textSecondary，radiusFull，视觉高 34，
   * 外层上下 5 把触控热区抬到 44）。
   */
  /** chip 点击分流：chipIndex=0 → 全部（null）；其余按 GAME_CHIP_ORDER 映射回分类 */
  private onChipSelect(chipIndex: number): void {
    this.selectedCategory = chipIndex === 0 ? null : GAME_CHIP_ORDER[chipIndex - 1]
  }

  @Builder
  CategoryChipsRow() {
    Scroll() {
      Row() {
        GameCategoryChip({
          label: GameText.categoryAll,
          selected: this.selectedCategory === null,
          chipIndex: 0,
          onSelect: (chipIndex: number): void => {
            this.onChipSelect(chipIndex)
          }
        })
        ForEach(GAME_CHIP_ORDER, (category: GameCategory, index: number) => {
          GameCategoryChip({
            label: categoryChipLabel(category),
            selected: this.selectedCategory === category,
            chipIndex: index + 1,
            onSelect: (chipIndex: number): void => {
              this.onChipSelect(chipIndex)
            }
          })
        }, (category: GameCategory) => category)
      }
    }
    .scrollable(ScrollDirection.Horizontal)
    .scrollBar(BarState.Off)
    // 左对齐 + 左右页边距（对齐安卓 AppPillTabRow：horizontalScroll Row 内 padding(horizontal =
    // pagePadding)；Scroll 默认居中会把 chips 推到屏幕中央）
    .align(Alignment.Start)
    .width('100%')
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: DesignDimens.SPACE_XS,
      bottom: DesignDimens.SPACE_SM
    })
  }

  /**
   * 加载骨架（规范 §3.6：骨架屏替代转圈，形状与真实网格一致；3 行 × 2 列，
   * 卡高 152vp 与真实卡构成对齐）。
   */
  @Builder
  GameGridSkeleton() {
    Column({ space: GRID_SPACING }) {
      ForEach(this.skeletonRows(), (row: number) => {
        Row({ space: GRID_SPACING }) {
          SkeletonBlock({ blockRadius: DesignDimens.CARD_RADIUS })
            .layoutWeight(1)
            .height(SKELETON_CARD_HEIGHT)
          SkeletonBlock({ blockRadius: DesignDimens.CARD_RADIUS })
            .layoutWeight(1)
            .height(SKELETON_CARD_HEIGHT)
        }
        .width('100%')
      }, (row: number) => row.toString())
    }
    .width('100%')
    .layoutWeight(1)
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefreshV2() {
      Column() {
        PageHeader({ title: GameText.title })

        // 分类筛选 chips（数据无关常驻，骨架/错误态同样可见）
        this.CategoryChipsRow()

        if (this.isError) {
          // 首刷失败：说明发生了什么 + 重试（规范 §3.6 错误态）
          Column() {
            EmptyState({ message: GameText.errorLoad }) {
              AppButton({
                text: GameText.actionRetry,
                click: () => {
                  this.loadGames()
                }
              })
            }
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (this.isLoading && this.filteredGames().length === 0) {
          // 首刷加载：骨架屏占位（禁止转圈），形状与真实网格一致
          this.GameGridSkeleton()
        } else if (this.filteredGames().length === 0) {
          // 空态：全量空（后端未上传/未上架）与筛选无命中分别给文案
          Column() {
            EmptyState({
              message: this.selectedCategory === null ? GameText.emptyMessage : GameText.emptyCategory
            })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          // 游戏网格（两列，行 key 由 gameKey 拼接，服务端 sort 序）
          GameGrid({ games: this.filteredGames() })
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
