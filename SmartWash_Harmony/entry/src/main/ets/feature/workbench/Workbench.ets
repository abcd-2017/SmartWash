// 工作台 — 底栏第 4 个 tab 根页（对齐安卓 ui/page/workbench/WorkbenchPage.kt，设计稿屏 15）
//
// 清氧页面里的一扇玄墨之窗：观象台特色卡 + 工具网格（占卜降为其中一个入口）。
// 工具列表来自 api.ets 内置默认数据（后端工作台接口就绪后替换数据源，页面零改动）。
//
// 域目录约定（feature/README.md）：本文件为域内实现，仅经 api.ets 对外暴露契约。

import { RouteNames } from "../../constant/RouteNames"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from "../../constant/DesignSystem"
import { DivColor } from "../../constant/DivTheme"
import { pathStack } from "../../utils/PathStackUtil"
import { showToastSafety } from "../../utils/ToastUtil"
import { IconBox } from "../../view/common/AppComponents"
import { WorkbenchAction, WorkbenchText, WorkbenchTint, WorkbenchTool, getWorkbenchTools } from "./api"

// 罗盘画布基准尺寸（vp）：安卓 64dp 画布按设计稿 66px 罗盘 SVG 等比换算
const COMPASS_SIZE: number = 64

@ComponentV2
export struct Workbench {
  @Local tools: WorkbenchTool[] = getWorkbenchTools()
  // 画布上下文：64vp 罗盘画布，开启抗锯齿
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  /**
   * 观象台特色卡点击（对齐安卓 WorkbenchPage.kt:104-107 navigate(DivRoute.Home)）。
   * 观象台域 7 页已落地（#29），直接入栈。
   */
  private onDivinationCardClick(): void {
    pathStack.pushPathByName(RouteNames.DIV_HOME, null)
  }

  /**
   * 工具点击分流（对齐安卓 WorkbenchPage.kt:138-150）。
   * 短链管理（#28）与观象台（#29）均为已落地真实页面，直接入栈；
   * AI 工具工作台鸿蒙暂未实现，按 COMING_SOON 处理。
   */
  private onToolClick(tool: WorkbenchTool): void {
    switch (tool.action) {
      case WorkbenchAction.DIVINATION_HOME:
        pathStack.pushPathByName(RouteNames.DIV_HOME, null)
        break
      case WorkbenchAction.DIV_HISTORY:
        pathStack.pushPathByName(RouteNames.DIV_HISTORY, null)
        break
      case WorkbenchAction.AI_ASSISTANT:
        // TODO(#29): AI 工具工作台（安卓 PageConstant.AiWork）鸿蒙未实现，暂按建设中处理
        showToastSafety(WorkbenchText.workbench_coming_soon)
        break
      case WorkbenchAction.TOOLBOX_SHORT_CODE:
        // 短链管理（#28）：工具箱域三页已落地，点击进列表页
        pathStack.pushPathByName(RouteNames.TOOLBOX_SHORT_CODE_LIST, null)
        break
      default:
        showToastSafety(WorkbenchText.workbench_coming_soon)
        break
    }
  }

  /** 图标容器浅底色（对齐安卓 WorkbenchTint → scheme.iconContainer*） */
  private containerColor(tint: WorkbenchTint): number {
    switch (tint) {
      case WorkbenchTint.GREEN:
        return DesignColor.ICON_CONTAINER_GREEN
      case WorkbenchTint.BLUE:
        return DesignColor.ICON_CONTAINER_BLUE
      case WorkbenchTint.TEAL:
        return DesignColor.ICON_CONTAINER_TEAL
      default:
        return DesignColor.ICON_CONTAINER_PURPLE
    }
  }

  /** 图标前景色（对齐安卓 WorkbenchTint → scheme.iconForeground*） */
  private iconColor(tint: WorkbenchTint): number {
    switch (tint) {
      case WorkbenchTint.GREEN:
        return DesignColor.ICON_FOREGROUND_GREEN
      case WorkbenchTint.BLUE:
        return DesignColor.ICON_FOREGROUND_BLUE
      case WorkbenchTint.TEAL:
        return DesignColor.ICON_FOREGROUND_TEAL
      default:
        return DesignColor.ICON_FOREGROUND_PURPLE
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
   * 罗盘圆 — 玄墨金线几何母题（规范 §5：滚筒圆同一几何的观象台变体）。
   * 对齐安卓 WorkbenchPage.kt:249-311 DivinationCompass：外环 26 / 虚线中环 18 / 内环 9
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

  /** 工具卡（设计稿 tile3）：白卡 + 44vp 语义图标容器 + 15fp 标题 + 13fp 描述 */
  @Builder
  ToolTile(tool: WorkbenchTool) {
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
      color: 'rgba(20,40,30,0.05)',
      offsetX: 0,
      offsetY: 2
    })
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

        // 2. 观象台玄墨特色卡
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

        // 4. 工具网格 — 2 列 tile3（奇数末行补位）
        Column({ space: DesignDimens.CARD_SPACING }) {
          ForEach(this.buildRows(), (row: WorkbenchTool[]) => {
            Row({ space: DesignDimens.CARD_SPACING }) {
              ForEach(row, (tool: WorkbenchTool) => {
                Column() {
                  this.ToolTile(tool)
                }
                .layoutWeight(1)
              }, (tool: WorkbenchTool) => tool.key)
              // 接口返回奇数个工具时末行补位，保持两列网格
              if (row.length === 1) {
                Column() {
                }
                .layoutWeight(1)
              }
            }
            .width('100%')
          }, (row: WorkbenchTool[], index: number) => index.toString())
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

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
