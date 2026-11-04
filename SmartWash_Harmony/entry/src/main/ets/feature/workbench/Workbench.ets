// 工作台 — 底栏第 4 个 tab 根页（对齐安卓 ui/page/workbench/WorkbenchPage.kt，设计稿屏 15）
//
// 清氧页面里的一扇玄墨之窗：观象台特色卡 + 工具网格（占卜降为其中一个入口）。
// 工具列表来自服务端入口配置下发（GET /web/auth/workbench/entries，见 api.ets +
// network/api/workbench.ets）：失败出真实错误态 + 重试、空配置出真实空态
// （对齐安卓 WorkbenchViewModel.loadEntries + WorkbenchPage 的 RequestState 三态，
// 2026-11-01 缺陷 6：原「失败/空配置静默回落本地兜底假列表」已移除）；
// 点击统一走深链分发（DeepLinkHandler.handleDeepLink，target 空 = 功能建设中）。
// 观象台玄墨特色卡为端上固定品牌位，不参与服务端下发。
//
// 域目录约定（feature/README.md）：本文件为域内实现，仅经 api.ets 对外暴露契约。

import { BizCode } from "../../network/HttpUtil"
import { WorkbenchEntryVo, getWorkbenchEntries } from "../../network/api/workbench"
import { RouteNames } from "../../constant/RouteNames"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from "../../constant/DesignSystem"
import { DivColor } from "../../constant/DivTheme"
import { pathStack } from "../../utils/PathStackUtil"
import { showToastSafety } from "../../utils/ToastUtil"
import { handleDeepLink } from "../../utils/deeplink/DeepLinkHandler"
import { logger } from "../../utils/Logger"
import { BusinessError } from "@kit.BasicServicesKit"
import { safeBottomVp, safeTopVp } from "../../utils/SafeArea"
import { AppButton, AppButtonVariant, EmptyState, IconBox, SkeletonBlock } from "../../view/common/AppComponents"
import {
  WorkbenchText,
  WorkbenchTint,
  WorkbenchTool,
  withUniqueKeys,
  workbenchEntryVoToTool
} from "./api"

// 罗盘画布基准尺寸（vp）：安卓 64dp 画布按设计稿 66px 罗盘 SVG 等比换算
const COMPASS_SIZE: number = 64

// 骨架卡高度（vp）：对齐安卓骨架 SkeletonBlock 148dp，形状与真实网格一致
const SKELETON_TILE_HEIGHT: number = 148

// 工具网格列数（tile3 两列卡）
const GRID_COLUMNS: number = 2

// 错误态 / 空态 / 重试文案（对齐安卓 strings.xml workbench_entries_error /
// workbench_entries_empty / workbench_retry）。按本轮文件约束（仅改 Workbench.ets 与
// WebPage.ets）暂置域内常量，待 api.ets 开放编辑时并入 WorkbenchText 文案表。
const WORKBENCH_ENTRIES_ERROR: string = '工具列表加载失败，请检查网络后重试'
const WORKBENCH_ENTRIES_EMPTY: string = '工具还在准备中，稍后再来看看'
const WORKBENCH_RETRY: string = '重试'

// 玄墨金线 32%（对齐安卓 DivColors.goldLine 暗色 0x52C9A961 / DivGoldDark.copy(alpha = 0.32f)）。
// DivTheme.GOLD_LINE_DARK 为 30%（0x4DC9A961）旧值，令牌层修正属 DivTheme 专项，
// 此处按安卓基准在工作台玄墨卡内取 32%。
const DIVINATION_GOLD_LINE: number = 0x52C9A961

// 逐项错峰入场参数（对齐安卓 WorkbenchPage.kt StaggerIn：间隔 40ms、上限 8 项、单项淡入 150ms）
const TOOL_STAGGER_MS: number = 40
const STAGGER_MAX_INDEX: number = 8
const TOOL_ENTER_MS: number = 150

// reduced-motion 判定占位：与 divReduceMotion() 同口径（SDK 无系统动画时长开关可读，恒 false；
// 见 recap G3）。降级分支保留在 WorkbenchToolTile，待 API 可用或设置页接入后替换为真实读取。
const REDUCE_MOTION: boolean = false

/** 图标容器浅底色（后端 color 白名单 → DesignColor.ICON_CONTAINER_*，未知兜底 GREEN） */
function workbenchContainerColor(tint: WorkbenchTint): number {
  switch (tint) {
    case WorkbenchTint.GREEN:
      return DesignColor.ICON_CONTAINER_GREEN
    case WorkbenchTint.BLUE:
      return DesignColor.ICON_CONTAINER_BLUE
    case WorkbenchTint.ORANGE:
      return DesignColor.ICON_CONTAINER_ORANGE
    case WorkbenchTint.PINK:
      return DesignColor.ICON_CONTAINER_PINK
    case WorkbenchTint.TEAL:
      return DesignColor.ICON_CONTAINER_TEAL
    case WorkbenchTint.PURPLE:
      return DesignColor.ICON_CONTAINER_PURPLE
    default:
      return DesignColor.ICON_CONTAINER_GREEN
  }
}

/** 图标前景色（后端 color 白名单 → DesignColor.ICON_FOREGROUND_*，未知兜底 GREEN） */
function workbenchIconColor(tint: WorkbenchTint): number {
  switch (tint) {
    case WorkbenchTint.GREEN:
      return DesignColor.ICON_FOREGROUND_GREEN
    case WorkbenchTint.BLUE:
      return DesignColor.ICON_FOREGROUND_BLUE
    case WorkbenchTint.ORANGE:
      return DesignColor.ICON_FOREGROUND_ORANGE
    case WorkbenchTint.PINK:
      return DesignColor.ICON_FOREGROUND_PINK
    case WorkbenchTint.TEAL:
      return DesignColor.ICON_FOREGROUND_TEAL
    case WorkbenchTint.PURPLE:
      return DesignColor.ICON_FOREGROUND_PURPLE
    default:
      return DesignColor.ICON_FOREGROUND_GREEN
  }
}

/**
 * 工具卡（设计稿 tile3）— 白卡 1px OUTLINE 描边 + level1 阴影 + 圆角 16 + 44vp 语义图标容器（icon 24）
 * + 14sp 标题 + 12sp 描述；右上角 badge 角标（清氧小胶囊：RADIUS_FULL + PRIMARY_LIGHT 底 + PRIMARY 文字）。
 * 触控目标 = 整卡（两列网格下远大于 48vp），按压反馈 scale 0.97 + 100ms（对齐安卓 pressable）。
 *
 * 自带逐项错峰入场（对齐安卓 StaggerIn）：40ms 间隔（上限 8 项）+ 淡入 150ms + 轻位移
 * （安卓 initialOffsetY = it/6 ≈ 16.7%，取 20vp 等比近似）；reduced motion（G3）时直接呈现。
 */
@ComponentV2
struct WorkbenchToolTile {
  @Require @Param tool: WorkbenchTool
  @Param index: number = 0
  @Param staggerMs: number = TOOL_STAGGER_MS
  @Param maxIndex: number = STAGGER_MAX_INDEX
  @Param enterMs: number = TOOL_ENTER_MS
  @Event onTileClick: (tool: WorkbenchTool) => void = (_: WorkbenchTool) => {
  }
  @Local shown: boolean = false
  @Local pressed: boolean = false
  private timerId: number = -1

  aboutToAppear(): void {
    if (REDUCE_MOTION) {
      this.shown = true
      return
    }
    const delay: number = Math.min(this.index, this.maxIndex) * this.staggerMs
    this.timerId = setTimeout(() => {
      this.shown = true
    }, delay)
  }

  aboutToDisappear(): void {
    if (this.timerId >= 0) {
      clearTimeout(this.timerId)
      this.timerId = -1
    }
  }

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column() {
        IconBox({
          icon: this.tool.icon,
          containerColor: workbenchContainerColor(this.tool.tint),
          iconColor: workbenchIconColor(this.tool.tint)
        })

        Text(this.tool.title)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .margin({ top: DesignDimens.SPACE_SM })

        Text(this.tool.description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: DesignDimens.SPACE_XXS })
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      .padding(DesignDimens.CARD_PADDING)
      .borderRadius(DesignDimens.CARD_RADIUS)
      // 规范 §2.4 卡片：1px OUTLINE 描边 + 0 2px 12px rgba(20,40,30,.05) 阴影（对齐安卓 v0.4）
      .border({ width: 1, color: DesignColor.OUTLINE })
      .backgroundColor(DesignColor.SURFACE)
      .shadow({
        radius: DesignElevation.LEVEL_1,
        color: DesignColor.SHADOW_COLOR_CARD,
        offsetX: 0,
        offsetY: 2
      })

      // badge 角标（服务端下发文案）：清氧小胶囊 — radiusFull + 主色浅底/主色文字（caption 档）
      if (this.tool.badge.length > 0) {
        Text(this.tool.badge)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.PRIMARY)
          .backgroundColor(DesignColor.PRIMARY_LIGHT)
          .borderRadius(DesignDimens.RADIUS_FULL)
          .padding({
            left: DesignDimens.SPACE_SM,
            right: DesignDimens.SPACE_SM,
            top: 2,
            bottom: 2
          })
          .margin({ top: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_SM })
      }
    }
    .width('100%')
    // 错峰入场：淡入 + 轻位移（位移量取安卓 it/6 的等比近似 20vp）
    .opacity(this.shown ? 1 : 0)
    .translate({ y: this.shown ? 0 : 20 })
    // 按压反馈（对齐安卓 pressable scale 0.97 + 100ms；indication = null 由自绘 scale 承载）
    .scale({ x: this.pressed ? 0.97 : 1, y: this.pressed ? 0.97 : 1 })
    .animation({ duration: this.pressed ? 100 : this.enterMs, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressed = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressed = false
      }
    })
    .onClick(() => {
      this.onTileClick(this.tool)
    })
  }
}

@ComponentV2
export struct Workbench {
  // 服务端下发列表（失败/空配置不再回落本地兜底，见 loadEntries）
  @Local tools: WorkbenchTool[] = []
  @Local isLoading: boolean = true
  // 请求失败标记（对齐安卓 RequestState.Error）：出真实错误态 + 重试
  @Local isError: boolean = false
  // 按压反馈（对齐安卓 pressable scale 0.97）
  @Local pressedDivination: boolean = false
  // 画布上下文：64vp 罗盘画布，开启抗锯齿
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  aboutToAppear(): void {
    // TabContent 懒加载：首次切到工作台 tab 才拉取（对齐安卓 WorkbenchViewModel init 拉取）
    logger.debug('workbench', '工作台挂载，拉取服务端入口配置')
    this.loadEntries()
  }

  /**
   * 拉取服务端入口配置（对齐安卓 WorkbenchViewModel.loadEntries 的 RequestState 三态）：
   * 成功且非空 → 服务端下发列表；成功但空配置 → 真实空态（无行动按钮）；
   * 失败 → 真实错误态 + 重试（2026-11-01 缺陷 6：移除本地兜底假列表）。
   */
  private async loadEntries(): Promise<void> {
    this.isLoading = true
    this.isError = false
    try {
      const res = await getWorkbenchEntries()
      if (res.code === BizCode.SUCCESS && res.data !== null && res.data.length > 0) {
        this.tools = withUniqueKeys(res.data.map((vo: WorkbenchEntryVo, index: number): WorkbenchTool =>
          workbenchEntryVoToTool(vo, index)))
      } else {
        // 空态预警留痕：业务拒绝或服务端未配置入口（成功但空列表），进真实空态
        logger.warn('workbench', `工具入口列表未生效，进空态 code=${res.code} message=${res.message ?? ""}`)
        this.tools = []
      }
    } catch (err) {
      // 失败：真实错误态 + 重试（对齐安卓 RequestState.Error(R.string.workbench_entries_error)）
      const e: BusinessError = err as BusinessError
      logger.warn('workbench', `工具入口列表加载异常，进错误态 err=${e.message}`)
      this.tools = []
      this.isError = true
    }
    this.isLoading = false
  }

  /**
   * 观象台特色卡点击（对齐安卓 WorkbenchPage.kt navigate(DivRoute.Home)）。
   * 观象台域 7 页已落地（#29），直接入栈。
   */
  private onDivinationCardClick(): void {
    // 工具入口点击跳转留痕（低频动作按约定记 debug）
    logger.debug('workbench', '点击观象台特色卡，入栈观象台首页')
    pathStack.pushPathByName(RouteNames.DIV_HOME, null)
  }

  /**
   * 工具点击分发（对齐安卓 WorkbenchPage.kt onToolClick）：
   * target 空 = 占位项，提示「功能建设中」（不走深链链路，保持现有文案）；
   * 已下发/已注册 target 统一交深链处理器（resolve → 导航/WebView/暂不支持 三态分发）。
   */
  private onToolClick(tool: WorkbenchTool): void {
    if (tool.target.length === 0) {
      // 占位项点击留痕（不走深链链路，仅提示建设中）
      logger.debug('workbench', `工具未配置跳转目标，提示建设中 entryKey=${tool.entryKey}`)
      showToastSafety(WorkbenchText.workbench_coming_soon)
      return
    }
    // 工具点击跳转留痕（深链分发内部另有留痕，此处只记入口）
    logger.debug('workbench', `点击工具，交深链分发 entryKey=${tool.entryKey}`)
    handleDeepLink(tool.target)
  }

  /** 工具两列分行（对齐安卓 tools.chunked(2)） */
  private buildRows(): WorkbenchTool[][] {
    const rows: WorkbenchTool[][] = []
    for (let i = 0; i < this.tools.length; i += GRID_COLUMNS) {
      rows.push(this.tools.slice(i, i + GRID_COLUMNS))
    }
    return rows
  }

  /**
   * 加载骨架（规范 §3.6：骨架屏替代转圈，形状与真实网格一致；行距同真实网格 CARD_SPACING，
   * 对齐安卓两行 × 两列 SkeletonBlock）。
   */
  @Builder
  ToolsSkeleton() {
    Column({ space: DesignDimens.CARD_SPACING }) {
      Row({ space: DesignDimens.CARD_SPACING }) {
        SkeletonBlock({ blockRadius: DesignDimens.CARD_RADIUS }).layoutWeight(1).height(SKELETON_TILE_HEIGHT)
        SkeletonBlock({ blockRadius: DesignDimens.CARD_RADIUS }).layoutWeight(1).height(SKELETON_TILE_HEIGHT)
      }
      .width('100%')

      Row({ space: DesignDimens.CARD_SPACING }) {
        SkeletonBlock({ blockRadius: DesignDimens.CARD_RADIUS }).layoutWeight(1).height(SKELETON_TILE_HEIGHT)
        SkeletonBlock({ blockRadius: DesignDimens.CARD_RADIUS }).layoutWeight(1).height(SKELETON_TILE_HEIGHT)
      }
      .width('100%')
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }

  /**
   * 罗盘圆 — 玄墨金线几何母题（规范 §5：滚筒圆同一几何的观象台变体）。
   * 对齐安卓 WorkbenchPage.kt DivinationCompass：外环 26 / 虚线中环 18 / 内环 9
   * + 四向刻度 + 水波线 + 中心点；画布 64vp，圆心取画布中心。
   * 金色一律取 DivColor.GOLD_DARK / GOLD_HI_DARK（= 安卓 DivColors.current.gold / goldHi 玄墨档），
   * 不再散写字面量。
   */
  @Builder
  CompassCanvas() {
    Canvas(this.ctx)
      .width(COMPASS_SIZE)
      .height(COMPASS_SIZE)
      .onReady(() => {
        const cx: number = COMPASS_SIZE / 2
        const cy: number = COMPASS_SIZE / 2
        this.ctx.lineCap = 'round'
        // 外环（金 45%）
        this.ctx.lineWidth = 2
        this.ctx.strokeStyle = 0x73C9A961
        this.ctx.beginPath()
        this.ctx.arc(cx, cy, 26, 0, Math.PI * 2, false)
        this.ctx.stroke()
        // 虚线中环（设计 dasharray 3 5，金 26%）
        this.ctx.lineWidth = 1.5
        this.ctx.strokeStyle = 0x42C9A961
        this.ctx.setLineDash([3, 5])
        this.ctx.beginPath()
        this.ctx.arc(cx, cy, 18, 0, Math.PI * 2, false)
        this.ctx.stroke()
        this.ctx.setLineDash([])
        // 内环（金 50%）
        this.ctx.lineWidth = 1.5
        this.ctx.strokeStyle = 0x80C9A961
        this.ctx.beginPath()
        this.ctx.arc(cx, cy, 9, 0, Math.PI * 2, false)
        this.ctx.stroke()
        // 四向刻度（12/6/3/9 点位，自外缘向内 5）
        this.ctx.lineWidth = 2
        this.ctx.strokeStyle = DivColor.GOLD_DARK
        const ticks: number[][] = [[0, -1], [0, 1], [-1, 0], [1, 0]]
        ticks.forEach((tick: number[]) => {
          this.ctx.beginPath()
          this.ctx.moveTo(cx + tick[0] * 24, cy + tick[1] * 24)
          this.ctx.lineTo(cx + tick[0] * 19, cy + tick[1] * 19)
          this.ctx.stroke()
        })
        // 水波线（穿过环心的观象台波纹，金高光）
        this.ctx.lineWidth = 2
        this.ctx.strokeStyle = DivColor.GOLD_HI_DARK
        this.ctx.beginPath()
        this.ctx.moveTo(cx - 7, cy)
        this.ctx.quadraticCurveTo(cx - 4, cy - 3, cx - 1, cy)
        this.ctx.quadraticCurveTo(cx + 2, cy + 3, cx + 5, cy)
        this.ctx.stroke()
        // 中心点
        this.ctx.fillStyle = DivColor.GOLD_DARK
        this.ctx.beginPath()
        this.ctx.arc(cx, cy, 2.5, 0, Math.PI * 2, false)
        this.ctx.fill()
      })
  }

  /**
   * 观象台玄墨特色卡（设计稿 darkcard3）：清氧页面里开一扇通往玄墨世界的窗。
   * 深色自带分层不加投影；皮肤取 DivColor 玄墨暗色令牌，浅深模式同款
   * （设计稿深色副本对此卡无覆盖，故浅色模式下仍为深色卡）。
   * 度量对齐安卓 DivinationEntryCard：圆角 16（radiusLg 卡片唯一档，非 hero 20）、
   * 内边距 20、罗盘↔文字列间距 16（spaceMd）、副标题宣纸主文本 #EFE9DA、
   * 32dp 圆形箭头（金线 32% + chevron 20）。
   */
  @Builder
  DivinationEntryCard() {
    Row({ space: DesignDimens.SPACE_MD }) {
      this.CompassCanvas()

      Column() {
        Text(WorkbenchText.workbench_divination_name)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DivColor.TEXT_PRIMARY_DARK)
          .letterSpacing(0.3)
        Text(WorkbenchText.workbench_divination_subtitle)
          .fontSize(DesignFontSize.CAPTION)
          // 设计稿两板副标题同为宣纸主文本（弱层级靠字号 12，字重 400）
          .fontColor(DivColor.TEXT_PRIMARY_DARK)
          .margin({ top: DesignDimens.SPACE_XS })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      // 32dp 圆形描边箭头（金线 32% = 安卓 div.gold.copy(alpha = 0.32f)），内 chevron 20 鎏金
      Column() {
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
          .fontColor([DivColor.GOLD_DARK])
      }
      .width(32)
      .height(32)
      .borderRadius(16)
      .border({ width: 1, color: DIVINATION_GOLD_LINE })
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 1, color: DIVINATION_GOLD_LINE })
    // 设计稿渐变 140deg #26241F→#1E1D1B 55%→#191713；140deg ≈ 左上→右下（ArkUI RightBottom）
    .linearGradient({
      direction: GradientDirection.RightBottom,
      colors: [[DivColor.SURFACE_2_DARK, 0.0], [DivColor.SURFACE_DARK, 0.55], [DivColor.BG_MID_DARK, 1.0]]
    })
    // 按压反馈（对齐安卓 pressable scale 0.97 + 100ms）
    .scale({ x: this.pressedDivination ? 0.97 : 1, y: this.pressedDivination ? 0.97 : 1 })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedDivination = true
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedDivination = false
      }
    })
    .onClick(() => {
      this.onDivinationCardClick()
    })
  }

  build() {
    Scroll() {
      Column() {
        // 1. 标题区（设计稿顶部 16、标题→副标题 4、底→特色卡 20）
        Column() {
          Text(WorkbenchText.workbench_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            // RootTitle = 22/600（安卓 Type.kt:56-61），鸿蒙原缺字重（默认 400）
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
          Text(WorkbenchText.workbench_subtitle)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: DesignDimens.SPACE_XXS })
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: 16,
          bottom: DesignDimens.SPACE_LG
        })

        // 2. 观象台玄墨特色卡（端上固定品牌位，不参与服务端下发）
        Column() {
          this.DivinationEntryCard()
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

        // 3. 「全部工具」标题（设计稿 margin 32/12；字距 0.3 与观象台卡标题同款）
        Text(WorkbenchText.workbench_all_tools)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .letterSpacing(0.3)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          .margin({ top: DesignDimens.SECTION_SPACING, bottom: DesignDimens.SPACE_SM })

        // 4. 三态 + 工具网格（对齐安卓 RequestState.Loading / Error / 空 / 内容 四分支）
        if (this.isLoading) {
          // 加载中：骨架屏（形状与真实网格一致）
          this.ToolsSkeleton()
        } else if (this.isError) {
          // 真实错误态：说明 + 重试（对齐安卓 RequestState.Error + AppButton SECONDARY）
          Column() {
            EmptyState({ message: WORKBENCH_ENTRIES_ERROR }) {
              AppButton({
                text: WORKBENCH_RETRY,
                variant: AppButtonVariant.SECONDARY,
                click: () => {
                  this.loadEntries()
                }
              })
            }
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        } else if (this.tools.length === 0) {
          // 真实空态：引导文案，无行动按钮（对齐安卓 tools.isEmpty() 分支）
          Column() {
            EmptyState({ message: WORKBENCH_ENTRIES_EMPTY })
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        } else {
          // 工具网格 — 2 列 tile3（奇数末行补位）；逐项错峰入场（对齐安卓 StaggerIn）
          Column({ space: DesignDimens.CARD_SPACING }) {
            ForEach(this.buildRows(), (row: WorkbenchTool[], rowIndex: number) => {
              Row({ space: DesignDimens.CARD_SPACING }) {
                ForEach(row, (tool: WorkbenchTool, colIndex: number) => {
                  WorkbenchToolTile({
                    tool: tool,
                    index: rowIndex * GRID_COLUMNS + colIndex,
                    onTileClick: (clicked: WorkbenchTool) => {
                      this.onToolClick(clicked)
                    }
                  })
                  .layoutWeight(1)
                }, (tool: WorkbenchTool) => tool.entryKey)
                // 接口返回奇数个工具时末行补位，保持两列网格
                if (row.length === 1) {
                  Column() {
                  }
                  .layoutWeight(1)
                }
              }
              .width('100%')
            }, (row: WorkbenchTool[], index: number) => `${index}_${row.length}_${row.length > 0 ? row[0].entryKey : ''}`)
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        }

        // 5. 页脚弱提示（设计稿 margin 26/10 → 24/8）
        Text(WorkbenchText.workbench_footer)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .textAlign(TextAlign.Center)
          .width('100%')
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: 24,
            bottom: 8
          })
      }
      .width('100%')
      // 沉浸式：内容避让状态栏，底部手势条留白（背景延伸）
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
    }
    .scrollBar(BarState.Off)
    .width('100%')
    .height('100%')
    .align(Alignment.TopStart)
    .backgroundColor(DesignColor.BACKGROUND)
  }
}

// 路由导出（router_map.json 注册用；当前作为底栏 tab 内联渲染，路由供外部入口预留）
@Builder
export function WorkbenchBuilder() {
  Workbench()
}
