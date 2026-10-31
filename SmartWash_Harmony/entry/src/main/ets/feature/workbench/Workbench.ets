// 工作台 — 底栏第 4 个 tab 根页（对齐安卓 ui/page/workbench/WorkbenchPage.kt，设计稿屏 15）
//
// 清氧页面里的一扇玄墨之窗：观象台特色卡 + 工具网格（占卜降为其中一个入口）。
// 工具列表来自服务端入口配置下发（GET /web/auth/workbench/entries，见 api.ets +
// network/api/workbench.ets）：下发失败/空列表回落本地兜底列表；
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
import { IconBox, SkeletonBlock } from "../../view/common/AppComponents"
import {
  WorkbenchText,
  WorkbenchTint,
  WorkbenchTool,
  getWorkbenchTools,
  withUniqueKeys,
  workbenchEntryVoToTool
} from "./api"

// 罗盘画布基准尺寸（vp）：安卓 64dp 画布按设计稿 66px 罗盘 SVG 等比换算
const COMPASS_SIZE: number = 64

// 骨架卡高度（vp）：对齐安卓骨架 SkeletonBlock 148dp，形状与真实网格一致
const SKELETON_TILE_HEIGHT: number = 148

@ComponentV2
export struct Workbench {
  // 服务端下发为空/失败时回落兜底列表；loading 期间网格区展示骨架屏
  @Local tools: WorkbenchTool[] = []
  @Local isLoading: boolean = true
  // 画布上下文：64vp 罗盘画布，开启抗锯齿
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  aboutToAppear(): void {
    // TabContent 懒加载：首次切到工作台 tab 才拉取（对齐安卓 WorkbenchViewModel init 拉取）
    this.loadEntries()
  }

  /**
   * 拉取服务端入口配置；失败或空列表走本地兜底（静默降级，tab 根页不打断用户、不弹错——
   * 兜底数据已可用，对齐安卓 WorkbenchViewModel.loadEntries）。
   */
  private async loadEntries(): Promise<void> {
    this.isLoading = true
    try {
      const res = await getWorkbenchEntries()
      if (res.code === BizCode.SUCCESS && res.data !== null && res.data.length > 0) {
        this.tools = withUniqueKeys(res.data.map((vo: WorkbenchEntryVo, index: number): WorkbenchTool =>
          workbenchEntryVoToTool(vo, index)))
      } else {
        // 服务端未配置：视为成功但走兜底列表（不显示错误态）
        this.tools = getWorkbenchTools()
      }
    } catch (err) {
      // 失败：本地兜底（Error 态仅日志留痕，页面静默消费）
      this.tools = getWorkbenchTools()
    }
    this.isLoading = false
  }

  /**
   * 观象台特色卡点击（对齐安卓 WorkbenchPage.kt navigate(DivRoute.Home)）。
   * 观象台域 7 页已落地（#29），直接入栈。
   */
  private onDivinationCardClick(): void {
    pathStack.pushPathByName(RouteNames.DIV_HOME, null)
  }

  /**
   * 工具点击分发（对齐安卓 WorkbenchPage.kt onToolClick）：
   * target 空 = 兜底占位项，提示「功能建设中」（不走深链链路，保持现有文案）；
   * 已下发/已注册 target 统一交深链处理器（resolve → 导航/WebView/暂不支持 三态分发）。
   */
  private onToolClick(tool: WorkbenchTool): void {
    if (tool.target.length === 0) {
      showToastSafety(WorkbenchText.workbench_coming_soon)
      return
    }
    handleDeepLink(tool.target)
  }

  /** 图标容器浅底色（后端 color 白名单 → DesignColor.ICON_CONTAINER_*，未知兜底 GREEN） */
  private containerColor(tint: WorkbenchTint): number {
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
  private iconColor(tint: WorkbenchTint): number {
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

  /** 工具两列分行（对齐安卓 tools.chunked(2)） */
  private buildRows(): WorkbenchTool[][] {
    const rows: WorkbenchTool[][] = []
    for (let i = 0; i < this.tools.length; i += 2) {
      rows.push(this.tools.slice(i, i + 2))
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
   * 深色自带分层不加投影；皮肤取 DivColors 玄墨暗色令牌，浅深模式同款
   * （设计稿深色副本对此卡无覆盖，故浅色模式下仍为深色卡）。
   */
  @Builder
  DivinationEntryCard() {
    Row() {
      this.CompassCanvas()

      Column() {
        Text(WorkbenchText.workbench_divination_name)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DivColor.TEXT_PRIMARY_DARK)
          .letterSpacing(0.3)
        Text(WorkbenchText.workbench_divination_subtitle)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DivColor.TEXT_SECONDARY_DARK)
          .margin({ top: DesignDimens.SPACE_XS })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      // 30dp 圆形描边箭头（设计稿 30px + rgba(gold,.4) → 金线令牌 30%）
      Column() {
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(16)
          .fontColor([DivColor.GOLD_DARK])
      }
      .width(32)
      .height(32)
      .borderRadius(16)
      .border({ width: 1, color: DivColor.GOLD_LINE_DARK })
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding(DesignDimens.CARD_PADDING)
    .borderRadius(DesignDimens.RADIUS_XL)
    .border({ width: 1, color: DivColor.GOLD_LINE_DARK })
    // 设计稿渐变 140deg #26241F→#1E1D1B 55%→#191713；140deg ≈ 左上→右下（ArkUI RightBottom）
    .linearGradient({
      direction: GradientDirection.RightBottom,
      colors: [[DivColor.SURFACE_2_DARK, 0.0], [DivColor.SURFACE_DARK, 0.55], [DivColor.BG_MID_DARK, 1.0]]
    })
    .onClick(() => {
      this.onDivinationCardClick()
    })
  }

  /**
   * 工具卡（设计稿 tile3）：白卡 1px OUTLINE 描边 + level1 阴影 + 圆角 16
   * + 44vp 语义图标容器 + 标题 + 描述；右上角 badge 角标（服务端下发文案：
   * 清氧小胶囊 — RADIUS_FULL + PRIMARY_LIGHT 底 + PRIMARY 文字）。
   * 触控目标 = 整卡（两列网格下远大于 48vp），按压无独立动效（遵循项目既有卡片习惯）。
   */
  @Builder
  ToolTile(tool: WorkbenchTool) {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column() {
        IconBox({
          icon: tool.icon,
          containerColor: this.containerColor(tool.tint),
          iconColor: this.iconColor(tool.tint)
        })

        Text(tool.title)
          .fontSize(DesignFontSize.BODY)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .margin({ top: DesignDimens.SPACE_SM })

        Text(tool.description)
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
      if (tool.badge.length > 0) {
        Text(tool.badge)
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
    .onClick(() => {
      this.onToolClick(tool)
    })
  }

  build() {
    Scroll() {
      Column() {
        // 1. 标题区（设计稿顶部 16）
        Column() {
          Text(WorkbenchText.workbench_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontColor(DesignColor.TEXT_PRIMARY)
          Text(WorkbenchText.workbench_subtitle)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ top: DesignDimens.SPACE_XS })
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: 16,
          bottom: 16
        })

        // 2. 观象台玄墨特色卡（端上固定品牌位，不参与服务端下发）
        Column() {
          this.DivinationEntryCard()
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

        // 3. 「全部工具」标题（设计稿 margin 32/12）
        Text(WorkbenchText.workbench_all_tools)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          .margin({ top: DesignDimens.SECTION_SPACING, bottom: DesignDimens.SPACE_SM })

        // 4. 工具网格 — 2 列 tile3（奇数末行补位）；加载中展示骨架屏（形状与真实网格一致）
        if (this.isLoading) {
          this.ToolsSkeleton()
        } else {
          Column({ space: DesignDimens.CARD_SPACING }) {
            ForEach(this.buildRows(), (row: WorkbenchTool[]) => {
              Row({ space: DesignDimens.CARD_SPACING }) {
                ForEach(row, (tool: WorkbenchTool) => {
                  Column() {
                    this.ToolTile(tool)
                  }
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
