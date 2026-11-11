/**
 * 游戏中心页 —— 对齐安卓 feature/game/.../ui/center/GameCenterPage.kt。
 *
 * 结构：PageHeader（标题「游戏中心」）→ 分类筛选 chips（全部/益智/街机/文字/桌版，本地过滤）
 * → 两列网格（封面主导卡：16:9 封面占卡顶 + 白底文字区（标题 + 两行描述）；封面两级均不可用时
 * 回退 44vp 图标色块卡；badge 白胶囊叠封面右上 / 图标卡内主色浅底胶囊）。
 * 数据来自 GET /web/auth/game/list（全量 <100 不分页），三态：骨架屏 / 空态 / 错误重试
 * （对齐安卓 when 分支顺序：Error 优先，其次 Loading 骨架，再次空态，最后网格）。
 * 卡片点击 → 既有 WebPage 页（后端下发 url 即最终可玩地址，端上零拼装；url 为空 toast 提示不跳转）。
 *
 * 域目录约定（feature/README.md）：本文件为域内实现，仅经 network/GameApi 对外暴露契约。
 */

import { BizCode } from '../../../network/HttpUtil'
import { RouteNames } from '../../../constant/RouteNames'
import { logger } from '../../../utils/Logger'
import { BusinessError } from '@kit.BasicServicesKit'
import { common } from '@kit.AbilityKit'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { pathStack } from '../../../utils/PathStackUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'
import { Debouncer } from '../../../utils/DebounceUtil'
import { showToastSafety } from '../../../utils/ToastUtil'
import { AppButton, AppPillTabRow, EmptyState, IconBox, PageHeader, SkeletonBlock } from '../../../view/common/AppComponents'
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
import { resColor } from '../../../utils/ResourceColor'
import { resString } from '../../../utils/ResourceString'

/** 网格列数（对齐安卓 GRID_COLUMNS = 2） */
const GRID_COLUMNS: number = 2

/** 网格卡片间距（对齐安卓 GRID_SPACING = cardSpacing 16；规范 §2.3 卡片间距档，≥12 防相邻卡片阴影叠画） */
const GRID_SPACING: number = DesignDimens.CARD_SPACING

/** 页面文案（对齐安卓 feature/game/values/strings.xml） */
class GameText {
  static get title(): string { return resString('game_center_title') }
  static get categoryAll(): string { return resString('game_category_all') }
  // 全量空态文案走资源文件 game_empty_message（「游戏整理中，先去洗件衣服吧」——
  // 安卓已认定「去后台上传」是运营视角文案，普通用户不可执行，故改面向 C 端可执行文案）
  static get emptyCategory(): string { return resString('game_empty_category') }
  static get errorLoad(): string { return resString('game_error_load') }
  static get actionRetry(): string { return resString('game_action_retry') }
  static get unavailable(): string { return resString('game_unavailable') }
}

/** 分类 chip 文案（全部之外的固定四类映射；OTHER 无 chip 无文案） */
function categoryChipLabel(category: GameCategory): string {
  switch (category) {
    case GameCategory.PUZZLE:
      return resString('game_category_puzzle')
    case GameCategory.ARCADE:
      return resString('game_category_arcade')
    case GameCategory.WORD:
      return resString('game_category_word')
    case GameCategory.BOARD:
      return resString('game_category_board')
    default:
      return ''
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

  /** 游戏卡点击：进既有 WebPage（url 为空给 toast 反馈，对齐安卓 navigateToGameWebView 防御） */
  private openGame(game: GameItem): void {
    if (game.url.length === 0) {
      // 数据残缺防御（后端契约保证非空，正常配置不会走到）：可见反馈替代静默
      showToastSafety(GameText.unavailable)
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
      // top ≥12：首行卡片阴影越出卡边 12vp，Scroll 视口会裁边界外内容，需留容身空间（对齐安卓）；
      // bottom 取安全区与 spaceXl 的较大值，保末行阴影呼吸（对齐安卓 contentPadding bottom spaceXl）
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_SM,
        bottom: Math.max(safeBottomVp(), DesignDimens.SPACE_XL)
      })
    }
    .scrollBar(BarState.Off)
    .width('100%')
    .layoutWeight(1)
    .align(Alignment.TopStart)
  }
}

/**
 * 游戏卡（对齐安卓 GameCard）：白 surface + 1px outline 描边 + §2.4 卡片档阴影（blur 12）+ 圆角 16。
 * 两种构图按封面可用性分流：
 * - 有封面（服务端 MinIO 直链优先，加载失败回退本地 rawfile 兜底）：16:9 封面独立占卡顶
 *   （随卡片圆角裁顶角），文字区白底置于封面下方（标题 + 两行描述），文字永不遮图
 *   （2026-11-07 对齐安卓构图调整：原「底部渐变遮罩压白字」遮挡封面细节，弃用）；
 *   badge 白胶囊叠封面右上（surface 底 + textPrimary 字），任意封面底色下可读。
 * - 无封面：44vp 图标色块（六色浅底 + 对应前景色，默认档圆角 12）+ 标题 + 描述两行截断
 *   + badge 主色浅底深绿字胶囊。
 * 整卡按压反馈 + 连点节流（对齐安卓 pressable：scale 0.97 / 100ms + 500ms 防抖）；
 * a11y：整卡一个语义节点，contentDescription = 标题 + 描述。
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

  // 连点节流（对齐安卓 rememberDebouncedClick 500ms；页面/卡片销毁时清理）
  private debouncer: Debouncer = new Debouncer()
  // 按压态（scale 0.97 + 100ms，对齐安卓 pressable；V2 组件内部状态用 @Local）
  @Local pressed: boolean = false

  aboutToDisappear(): void {
    this.debouncer.cancel()
  }

  /** 图标容器浅底色（后端 color 白名单 → 资源色 ICON_CONTAINER_*，未知兜底 GREEN） */
  private containerColor(tint: GameTint): ResourceColor {
    switch (tint) {
      case GameTint.GREEN:
        return $r('app.color.ICON_CONTAINER_GREEN')
      case GameTint.BLUE:
        return $r('app.color.ICON_CONTAINER_BLUE')
      case GameTint.ORANGE:
        return $r('app.color.ICON_CONTAINER_ORANGE')
      case GameTint.PINK:
        return $r('app.color.ICON_CONTAINER_PINK')
      case GameTint.TEAL:
        return $r('app.color.ICON_CONTAINER_TEAL')
      case GameTint.PURPLE:
        return $r('app.color.ICON_CONTAINER_PURPLE')
      default:
        return $r('app.color.ICON_CONTAINER_GREEN')
    }
  }

  /** 图标前景色（后端 color 白名单 → 资源色 ICON_FOREGROUND_*，未知兜底 GREEN） */
  private iconColor(tint: GameTint): ResourceColor {
    switch (tint) {
      case GameTint.GREEN:
        return $r('app.color.ICON_FOREGROUND_GREEN')
      case GameTint.BLUE:
        return $r('app.color.ICON_FOREGROUND_BLUE')
      case GameTint.ORANGE:
        return $r('app.color.ICON_FOREGROUND_ORANGE')
      case GameTint.PINK:
        return $r('app.color.ICON_FOREGROUND_PINK')
      case GameTint.TEAL:
        return $r('app.color.ICON_FOREGROUND_TEAL')
      case GameTint.PURPLE:
        return $r('app.color.ICON_FOREGROUND_PURPLE')
      default:
        return $r('app.color.ICON_FOREGROUND_GREEN')
    }
  }

  build() {
    Column() {
      if (this.useServerCover || this.useLocalCover) {
        // 封面区：16:9 满宽，随卡片圆角裁顶角；badge 白胶囊叠右上角
        Stack({ alignContent: Alignment.TopEnd }) {
          // 封面两级兜底：服务端 coverUrl → 本地 rawfile（随包 30 款）；
          // onError 经事件上抛页面登记（失败集在页面状态，卡片无状态）
          if (this.useServerCover) {
            Image(this.game.coverUrl)
              .width('100%')
              .aspectRatio(1.78)
              .objectFit(ImageFit.Cover)
              // 加载中占位底（对齐安卓 placeholder = surfaceVariant 灰档）：弱网下封面区不留白板；
              // 深浅色档随主题切换（DARK_SURFACE_VARIANT）
              .backgroundColor($r('app.color.SURFACE_VARIANT'))
              .onError(() => {
                this.onServerCoverError(this.game.gameKey)
              })
          } else {
            Image(this.game.localCover)
              .width('100%')
              .aspectRatio(1.78)
              .objectFit(ImageFit.Cover)
              .onError(() => {
                this.onLocalCoverError(this.game.gameKey)
              })
          }
          if (this.game.badge.length > 0) {
            Text(this.game.badge)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor($r('app.color.TEXT_PRIMARY'))
              .backgroundColor($r('app.color.SURFACE'))
              .borderRadius(DesignDimens.RADIUS_FULL)
              // 1px outline 描边与卡片描边同语言：封面接近白色时胶囊轮廓不消失（对齐安卓）
              .border({ width: 1, color: $r('app.color.OUTLINE') })
              .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: 2, bottom: 2 })
              .margin({ top: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM })
          }
        }
        .width('100%')
        .borderRadius({ topLeft: DesignDimens.CARD_RADIUS, topRight: DesignDimens.CARD_RADIUS })
        .clip(true)

        // 文字区：封面下方独立白底，文字不遮图（2026-11-07 对齐安卓构图调整）
        Column() {
          Text(this.game.title)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
          if (this.game.description.length > 0) {
            Text(this.game.description)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .width('100%')
              .margin({ top: DesignDimens.SPACE_XXS })
          }
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        // 内边距对齐 §2.3 卡片内边距 cardPadding 20（与无封面卡同一令牌语言，消除 12/8 与 20 两套并存）
        .padding(DesignDimens.CARD_PADDING)
      } else {
        // 图标卡：44vp 图标色块（默认档圆角 12 = iconContainerRadius，v0.4 §3.2 图标容器·主）
        // + 标题 + 描述两行 + badge 主色浅底深绿字胶囊
        Column() {
          IconBox({
            icon: this.game.icon,
            sizeValue: 44,
            iconSize: 24,
            containerColor: this.containerColor(this.game.tint),
            iconColor: this.iconColor(this.game.tint)
          })
          Text(this.game.title)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
            .margin({ top: DesignDimens.SPACE_SM })
          if (this.game.description.length > 0) {
            Text(this.game.description)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .width('100%')
              .margin({ top: DesignDimens.SPACE_XXS })
          }
          if (this.game.badge.length > 0) {
            // badge 字色取 TEXT_GREEN_ON_TINT（规范 §10 对比度：primary 品牌绿在 primaryLight
            // 浅底小字号下 ≈3.3:1 不达 AA，textGreenOnTint ≈6.4:1 达标，对齐安卓 textGreenOnTint）
            Text(this.game.badge)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor($r('app.color.TEXT_GREEN_ON_TINT'))
              .backgroundColor($r('app.color.PRIMARY_LIGHT'))
              .borderRadius(DesignDimens.RADIUS_FULL)
              .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM, top: 2, bottom: 2 })
              .margin({ top: DesignDimens.SPACE_XXS })
          }
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        .padding(DesignDimens.CARD_PADDING)
      }
    }
    .width('100%')
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    .backgroundColor($r('app.color.SURFACE'))
    // 卡片阴影档（规范 §2.4：0 2px 12px）——blur 取 CARD_SHADOW_RADIUS(12)，非海拔档位号 LEVEL_1
    // （1vp 模糊不可见，与 AppCard 统一口径）；阴影色随主题切深色档 rgba(0,0,0,0.28)
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
    // 整卡按压反馈 + 连点节流（对齐安卓 pressable：scale 0.97 / 100ms + 500ms 防抖）
    .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    // 无障碍：整卡一个语义节点（对齐安卓 semantics contentDescription，封面为装饰图不出语义）
    .accessibilityDescription(this.game.description.length > 0
      ? `${this.game.title}，${this.game.description}`
      : this.game.title)
    .onClick(() => {
      this.debouncer.throttle((): void => {
        this.onOpen(this.game)
      }, 500)
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

  /** chip 点击分流：chipIndex=0 → 全部（null）；其余按 GAME_CHIP_ORDER 映射回分类 */
  private onChipSelect(chipIndex: number): void {
    this.selectedCategory = chipIndex === 0 ? null : GAME_CHIP_ORDER[chipIndex - 1]
  }

  /** 分类 chip 文案组（全部 + 四类；OTHER 无 chip，对齐安卓 GameCategoryChipsRow） */
  private chipTabs(): string[] {
    const tabs: string[] = [GameText.categoryAll]
    for (let i = 0; i < GAME_CHIP_ORDER.length; i++) {
      tabs.push(categoryChipLabel(GAME_CHIP_ORDER[i]))
    }
    return tabs
  }

  /** 选中分类对应 chip 下标（null → 0 全部；OTHER 无 chip → indexOf 得 -1 落回 0，对齐安卓） */
  private selectedChipIndex(): number {
    const selected: GameCategory | null = this.selectedCategory
    if (selected === null) {
      return 0
    }
    return GAME_CHIP_ORDER.indexOf(selected) + 1
  }

  /** 全量空态文案（走资源文件 game_empty_message，对齐安卓 strings.xml「游戏整理中，先去洗件衣服吧」） */
  private emptyMessage(): string {
    const context: common.Context = getContext(this)
    return context.resourceManager.getStringSync($r('app.string.game_empty_message').id)
  }

  /**
   * 分类筛选 chips 行 —— 复用公共 AppPillTabRow（对齐安卓 2026-11-01「自绘 chips 迁移到公共
   * 组件」：选中 = primary 实底 + onPrimary 字，未选中 = surface + 1px outline + textSecondary，
   * 胶囊视觉高 34 / 触控热区 48，自带选中态读屏播报与 SELECTION 触感）。
   */
  @Builder
  CategoryChipsRow() {
    Column() {
      AppPillTabRow({
        tabs: this.chipTabs(),
        selectedIndex: this.selectedChipIndex(),
        tabSelected: (index: number): void => {
          this.onChipSelect(index)
        }
      })
    }
    .width('100%')
    // 上下间距对齐安卓 GameCategoryChipsRow 的 modifier.padding(top = spaceXs, bottom = spaceSm)
    .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_SM })
  }

  /**
   * 加载骨架（规范 §3.6：骨架屏替代转圈，形状与真实网格一致；3 行 × 2 列）。
   * 与真实卡同构：16:9 封面块（顶角圆角）+ 下方标题/描述文字条块（对齐安卓骨架）。
   */
  @Builder
  GameGridSkeleton() {
    Column({ space: GRID_SPACING }) {
      ForEach(this.skeletonRows(), (row: number) => {
        Row({ space: GRID_SPACING }) {
          Column() {
            // 封面块：块体直角 + 外层裁剪出顶角弧度（对齐安卓 topStart/topEnd radiusLg）
            Stack() {
              SkeletonBlock({ blockRadius: 0 })
                .width('100%')
                .aspectRatio(1.78)
            }
            .width('100%')
            .borderRadius({ topLeft: DesignDimens.CARD_RADIUS, topRight: DesignDimens.CARD_RADIUS })
            .clip(true)
            // 文字条块高对齐真实行高（标题 14sp→20 行高 spaceLg、描述 12sp→16 行高 spaceMd，对齐安卓）
            SkeletonBlock({ blockRadius: DesignDimens.RADIUS_SMALL })
              .width('65%')
              .height(DesignDimens.SPACE_LG)
              .margin({ top: DesignDimens.SPACE_XS })
            SkeletonBlock({ blockRadius: DesignDimens.RADIUS_SMALL })
              .width('90%')
              .height(DesignDimens.SPACE_MD)
              .margin({ top: DesignDimens.SPACE_XXS })
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
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
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
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
                small: true,
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
              message: this.selectedCategory === null ? this.emptyMessage() : GameText.emptyCategory
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
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }
}
