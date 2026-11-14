// 工作台 — 底栏第 4 个 tab 根页（对齐安卓 ui/page/workbench/WorkbenchPage.kt，设计稿屏 15）
//
// 清氧页面里的一扇玄墨之窗：观象台特色卡 + 类型化工具区。
// 工具列表来自服务端入口配置下发（GET /web/auth/workbench/entries，见 api.ets +
// network/api/workbench.ets）：type/section_title/image_url 驱动「公告条 / 通栏卡 /
// 两列小卡 / 列表行组」混合构图（折块逻辑在 api.ets buildWorkbenchBlocks）；
// 失败/空配置给真实错误态/空态（对齐安卓 2026-11-01 缺陷 6，不再静默兜底）。
// 端上自闭环：搜索过滤（Search 组件）、最近使用（Preferences）、下拉刷新（Refresh）。
// 观象台玄墨特色卡为端上固定品牌位，不参与服务端下发。
//
// 域目录约定（feature/README.md）：本文件为域内实现，仅经 api.ets 对外暴露契约。

import { workbenchCover } from './api'
import { BizCode } from "../../network/HttpUtil"
import { WorkbenchEntryVo, getWorkbenchEntries } from "../../network/api/workbench"
import { RouteNames } from "../../constant/RouteNames"
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from "../../constant/DesignSystem"
import { DivColor } from "../../constant/DivTheme"
import { pathStack } from "../../utils/PathStackUtil"
import { showToastSafety } from "../../utils/ToastUtil"
import { getValue, setValue } from "../../utils/StorageUtil"
import { handleDeepLink } from "../../utils/deeplink/DeepLinkHandler"
import { scanAndDispatch } from "../../utils/ScanDispatch"
import { resString } from "../../utils/ResourceString"
import { logger } from "../../utils/Logger"
import { BusinessError } from "@kit.BasicServicesKit"
import { common } from "@kit.AbilityKit"
import { KeyboardAvoidMode, LengthMetrics } from "@kit.ArkUI"
import { ValueType } from "@kit.ArkData"
import { safeBottomVp, safeTopVp } from "../../utils/SafeArea"
import { parse, DeepLinkKind } from "../../utils/deeplink/DeepLinkParser"
import { lookup } from "../../utils/deeplink/DeepLinkRegistry"
import { AppButton, AppButtonVariant, EmptyState, IconBox, SkeletonBlock } from "../../view/common/AppComponents"
import { resColor, resolveArgb } from '../../utils/ResourceColor'
import {
  WorkbenchBlock,
  WorkbenchBlockKind,
  WorkbenchLayout,
  WorkbenchText,
  WorkbenchTint,
  WorkbenchTool,
  buildWorkbenchBlocks,
  withUniqueKeys,
  workbenchEntryVoToTool
} from "./api"

// 罗盘画布基准尺寸（vp）：安卓 64dp 画布按设计稿 66px 罗盘 SVG 等比换算
const COMPASS_SIZE: number = 64

// 骨架卡高度（vp）：对齐安卓骨架 SkeletonBlock 148dp，形状与真实网格一致
const SKELETON_TILE_HEIGHT: number = 148

// 逐项错峰入场（对齐安卓 StaggerIn：间隔 40ms × 6 上限 + 单项 150ms 淡入，§7 总预算 400ms 内）
const STAGGER_INTERVAL_MS: number = 40
const STAGGER_MAX_INDEX: number = 6
const STAGGER_ENTER_MS: number = 150

// 最近使用保留条数（对齐安卓 WorkbenchViewModel.RECENT_MAX）
const RECENT_MAX: number = 6

// Preferences key（对齐安卓 WorkbenchViewModel companion 常量）
const KEY_RECENT_ENTRIES: string = 'workbench_recent_entries'
const KEY_DISMISSED_NOTICES: string = 'workbench_dismissed_notices'

// 加载状态机（对齐安卓 RequestState：Loading 且列表为空 → 骨架屏）
enum LoadState {
  LOADING,
  SUCCESS,
  ERROR
}

@ComponentV2
export struct Workbench {
  // 服务端下发列表（已折块渲染）；loading 且为空时展示骨架屏
  @Local tools: WorkbenchTool[] = []
  @Local loadState: LoadState = LoadState.LOADING
  // 下拉刷新状态（Refresh 组件双向绑定）
  @Local isRefreshing: boolean = false
  // 搜索关键词（空 = 非搜索态，页面回到分组/类型排版）
  @Local searchQuery: string = ''
  // 最近使用 entryKey（本地记忆，新的在前）+ 已关闭公告 entryKey 集合
  @Local recentKeys: string[] = []
  @Local dismissedKeys: string[] = []
  // 按压反馈 key（对齐安卓 pressable scale 0.97；项目口径同 OrderCard/Service：
  // @Local + onTouch(Down/Up/Cancel) + scale 0.97 + 100ms 属性动画，stateEffect 非通用属性）
  @Local pressedKey: string = ''
  // 画布上下文：64vp 罗盘画布，开启抗锯齿
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  aboutToAppear(): void {
    // TabContent 懒加载：首次切到工作台 tab 才拉取（对齐安卓 WorkbenchViewModel init 拉取）
    logger.debug('workbench', '工作台挂载，拉取服务端入口配置')
    // 键盘避让（问题13）：全局是 RESIZE，会把整窗压到键盘上沿、底部 tab 被裁出窗口；
    // 工作台只有顶部搜索框，不需要避让，改 NONE 让键盘覆盖内容、底栏保持原位
    this.applyKeyboardAvoidNone()
    this.loadEntries()
    // 最近使用/公告关闭记录异步回读（对齐安卓 init 回读 DataStore）
    this.loadLocalState()
  }

  /**
   * 键盘避让 NONE 落位（问题13）：窗口默认 RESIZE（EntryAbility 统一设置）。
   * 2026-11-05 实测：仅挂载期设置一次，模拟器聚焦搜索框时窗口仍被压缩（dump 断言
   * root 底沿 = 键盘顶沿 1850）；在搜索框聚焦事件里键盘弹出前重设一次才稳定生效。
   * 回读留痕便于复核；失败静默不阻塞输入。
   */
  private applyKeyboardAvoidNone(): void {
    try {
      this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.NONE)
      logger.debug('workbench', `键盘避让模式已设 NONE，回读=${this.getUIContext().getKeyboardAvoidMode()}`)
    } catch (err) {
      logger.warn('workbench', '设置键盘避让模式失败')
    }
  }

  aboutToDisappear(): void {
    // 离开工作台恢复全局 RESIZE，避免影响登录/充值等需要避让的输入页
    try {
      this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE)
    } catch (err) {
      logger.warn('workbench', '恢复键盘避让模式失败')
    }
  }

  /**
   * 工具是否已落地（问题15）：后端 workbench_entry 对全部端统一下发，其中
   * lab/delivery/ai-work 在鸿蒙端未实现，深链注册表把它们指向 ComingSoon 占位页。
   * 这里在渲染前过滤掉，避免用户点进去看到「正在实现中」。
   * target 为空的公告条不算未实现（纯文案，不可点）。
   */
  private isImplementedTarget(target: string): boolean {
    if (target.length === 0) {
      return true
    }
    try {
      const result = parse(target, lookup)
      return !(result.kind === DeepLinkKind.ROUTE && result.routeName === RouteNames.COMING_SOON)
    } catch (err) {
      // 解析异常保守保留（顶多进了占位页，不应因为过滤逻辑把入口吞掉）
      return true
    }
  }

  /** 回读本地记忆：最近使用 + 已关闭公告（逗号连接存储） */
  private async loadLocalState(): Promise<void> {
    try {
      const recent = await getValue(KEY_RECENT_ENTRIES, '')
      // preferences.ValueType 含 bigint，与 ValuesBucket.ValueType 不兼容——先收窄 string
      this.recentKeys = this.splitLocalKeys(typeof recent === 'string' ? recent : null)
      const dismissed = await getValue(KEY_DISMISSED_NOTICES, '')
      this.dismissedKeys = this.splitLocalKeys(typeof dismissed === 'string' ? dismissed : null)
    } catch (err) {
      // 本地记忆读取失败不阻塞主流程（等同无记忆）
    }
  }

  /**
   * string → string[] 安全收窄（替代 as 强转，严格模式）：逗号分隔 key 列表，
   * 非字符串值按无记忆处理。
   */
  private splitLocalKeys(value: string | null): string[] {
    if (typeof value === 'string' && value.length > 0) {
      return value.split(',').filter((k: string) => k.length > 0)
    }
    return []
  }

  /**
   * 拉取服务端入口配置（下拉刷新与首载共用）：
   * 成功（含空配置）→ Success 真实渲染；失败 → 真实 Error 态 + 重试（不再静默兜底）。
   * force=true（下拉刷新）才亮 Refresh 指示器——首载由骨架屏表达加载态，重试由按钮 loading 表达。
   */
  private async loadEntries(force: boolean = false): Promise<void> {
    this.isRefreshing = force
    try {
      const res = await getWorkbenchEntries()
      if (res.code === BizCode.SUCCESS) {
        const all: Array<WorkbenchTool> = (res.data ?? []).map((vo: WorkbenchEntryVo, index: number): WorkbenchTool =>
          workbenchEntryVoToTool(vo, index))
        // 过滤未落地域（问题15）：避免卡片点进「正在实现中」占位页
        const implemented: Array<WorkbenchTool> = all.filter((tool: WorkbenchTool) => this.isImplementedTarget(tool.target))
        this.tools = withUniqueKeys(implemented)
        this.loadState = LoadState.SUCCESS
      } else {
        logger.warn('workbench', `工具入口列表未生效，进错误态 code=${res.code} message=${res.message ?? ""}`)
        this.tools = []
        this.loadState = LoadState.ERROR
      }
    } catch (err) {
      // 失败：真实错误态（Error 仅日志留痕，页面出重试）
      const e: BusinessError = err as BusinessError
      logger.warn('workbench', `工具入口列表加载异常，进错误态 err=${e.message}`)
      this.tools = []
      this.loadState = LoadState.ERROR
    }
    this.isRefreshing = false
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

  /** 搜索关键词回写（Search 组件 onChange 直连） */
  private onSearchChange(query: string): void {
    this.searchQuery = query
  }

  /**
   * 工具点击分发（对齐安卓 WorkbenchPage.kt handleToolClick）：
   * target 空 = 兜底占位项，提示「功能建设中」（不走深链链路）；
   * 已下发 target 先记最近使用，再统一交深链处理器（resolve → 导航/WebView/暂不支持）。
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
    this.recordRecent(tool.entryKey)
    handleDeepLink(tool.target)
  }

  /** 关闭公告条：内存集合即时生效 + Preferences 持久化（对齐安卓 dismissNotice） */
  private dismissNotice(tool: WorkbenchTool): void {
    if (this.dismissedKeys.indexOf(tool.entryKey) >= 0) {
      return
    }
    this.dismissedKeys.push(tool.entryKey)
    setValue(KEY_DISMISSED_NOTICES, this.dismissedKeys.join(','))
  }

  /** 记录最近使用：新的在前、去重、封顶 RECENT_MAX（对齐安卓 recordRecent） */
  private recordRecent(entryKey: string): void {
    const updated: string[] = [entryKey].concat(this.recentKeys.filter((k: string) => k !== entryKey))
    this.recentKeys = updated.slice(0, RECENT_MAX)
    setValue(KEY_RECENT_ENTRIES, this.recentKeys.join(','))
  }

  // ---------- 派生数据（渲染前一次计算，对齐安卓页面级 remember 派生） ----------

  /** 展示列表 = 下发列表 − 已关闭公告 − 搜索过滤（搜索匹配 title/description，大小写不敏感） */
  private visibleTools(): WorkbenchTool[] {
    const dismissed = new Set<string>(this.dismissedKeys)
    const base = this.tools.filter((tool: WorkbenchTool): boolean =>
      !(tool.layout === WorkbenchLayout.NOTICE && dismissed.has(tool.entryKey)))
    const query = this.searchQuery.trim()
    if (query.length === 0) {
      return base
    }
    const lower = query.toLowerCase()
    return base.filter((tool: WorkbenchTool): boolean =>
      tool.title.toLowerCase().includes(lower) || tool.description.toLowerCase().includes(lower))
  }

  /** 最近使用 = 本地记忆 key 按序映射回当次下发列表（已下线的入口自动消失） */
  private recentTools(): WorkbenchTool[] {
    const result: WorkbenchTool[] = []
    this.recentKeys.forEach((key: string): void => {
      const hit = this.tools.find((tool: WorkbenchTool): boolean => tool.entryKey === key)
      if (hit !== undefined) {
        result.push(hit)
      }
    })
    return result
  }

  /** 工具两列分行（搜索态平铺用，对齐安卓 chunked(2)） */
  private buildRows(list: WorkbenchTool[]): WorkbenchTool[][] {
    const rows: WorkbenchTool[][] = []
    for (let i = 0; i < list.length; i += 2) {
      rows.push(list.slice(i, i + 2))
    }
    return rows
  }

  /** 图标容器浅底色（后端 color 白名单 → 资源色 ICON_CONTAINER_*，未知兜底 GREEN） */
  private containerColor(tint: WorkbenchTint): ResourceColor {
    switch (tint) {
      case WorkbenchTint.GREEN:
        return $r('app.color.ICON_CONTAINER_GREEN')
      case WorkbenchTint.BLUE:
        return $r('app.color.ICON_CONTAINER_BLUE')
      case WorkbenchTint.ORANGE:
        return $r('app.color.ICON_CONTAINER_ORANGE')
      case WorkbenchTint.PINK:
        return $r('app.color.ICON_CONTAINER_PINK')
      case WorkbenchTint.TEAL:
        return $r('app.color.ICON_CONTAINER_TEAL')
      case WorkbenchTint.PURPLE:
        return $r('app.color.ICON_CONTAINER_PURPLE')
      default:
        return $r('app.color.ICON_CONTAINER_GREEN')
    }
  }

  /** 图标前景色（后端 color 白名单 → 资源色 ICON_FOREGROUND_*，未知兜底 GREEN） */
  private iconColor(tint: WorkbenchTint): ResourceColor {
    switch (tint) {
      case WorkbenchTint.GREEN:
        return $r('app.color.ICON_FOREGROUND_GREEN')
      case WorkbenchTint.BLUE:
        return $r('app.color.ICON_FOREGROUND_BLUE')
      case WorkbenchTint.ORANGE:
        return $r('app.color.ICON_FOREGROUND_ORANGE')
      case WorkbenchTint.PINK:
        return $r('app.color.ICON_FOREGROUND_PINK')
      case WorkbenchTint.TEAL:
        return $r('app.color.ICON_FOREGROUND_TEAL')
      case WorkbenchTint.PURPLE:
        return $r('app.color.ICON_FOREGROUND_PURPLE')
      default:
        return $r('app.color.ICON_FOREGROUND_GREEN')
    }
  }

  /**
   * 卡片白卡统一描边/阴影（规范 §2.4：1px OUTLINE + 卡片阴影档 0 2px 12px）。
   * blur 取 CARD_SHADOW_RADIUS(12)——LEVEL_1 是海拔档位号（1vp）非阴影几何值，
   * 1vp 模糊肉眼不可见（与 AppCard 统一口径）；阴影色随主题切深色档 rgba(0,0,0,0.28)。
   */
  @Styles
  cardChrome() {
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    .backgroundColor($r('app.color.SURFACE'))
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
  }

  /**
   * 加载骨架（规范 §3.6：骨架屏替代转圈，形状与主流 tile 网格一致；行距同真实网格 CARD_SPACING，
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
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: DesignDimens.SECTION_SPACING
    })
  }

  /**
   * 真实错误态 / 空态（对齐安卓 EmptyState + 行动按钮）。
   * @param actionText 行动按钮文案；空串 = 不渲染按钮（真空态无出路时）
   * @param onAction 行动按钮点击回调
   */
  @Builder
  StateHint(message: string, actionText: string, onAction: () => void) {
    EmptyState({ message: message, icon: $r('sys.symbol.square_grid_2x2') }) {
      if (actionText.length > 0) {
        Column() {
          AppButton({
            small: true,
            text: actionText,
            variant: AppButtonVariant.SECONDARY,
            click: onAction
          })
        }
        .width('100%')
        // 左右页边距已由 EmptyState 的 action 插槽统一给出，这里只补上间距（问题14）
        .padding({ top: DesignDimens.SPACE_MD })
      }
    }
  }

  /** 区块标题（服务端 section_title 分组边界） */
  @Builder
  SectionHeader(title: string) {
    // U-116：v0.4 字阶已废字距，删除 letterSpacing(0.3)（对齐安卓 SectionHeader）
    Text(title)
      .fontSize(DesignFontSize.SECTION_TITLE)
      .fontWeight(DesignFontWeight.SEMI_BOLD)
      .fontColor($r('app.color.TEXT_PRIMARY'))
      .width('100%')
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
      .margin({ top: DesignDimens.SECTION_SPACING, bottom: DesignDimens.SPACE_SM })
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
        // 虚线中环（设计 dasharray 3 5，金 26%）——对齐安卓 PathEffect.dashPathEffect(3, 5)，
        // 需显式 setLineDash 才会画成虚线（原缺少该调用，中环被画成实线）
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
        this.ctx.strokeStyle = resolveArgb(DivColor.GOLD_DARK)
        const ticks: number[][] = [[0, -1], [0, 1], [-1, 0], [1, 0]]
        ticks.forEach((tick: number[]) => {
          this.ctx.beginPath()
          this.ctx.moveTo(cx + tick[0] * 24, cy + tick[1] * 24)
          this.ctx.lineTo(cx + tick[0] * 19, cy + tick[1] * 19)
          this.ctx.stroke()
        })
        // 水波线（穿过环心的观象台波纹，金高光）
        this.ctx.lineWidth = 2
        this.ctx.strokeStyle = resolveArgb(DivColor.GOLD_HI_DARK)
        this.ctx.beginPath()
        this.ctx.moveTo(cx - 7, cy)
        this.ctx.quadraticCurveTo(cx - 4, cy - 3, cx - 1, cy)
        this.ctx.quadraticCurveTo(cx + 2, cy + 3, cx + 5, cy)
        this.ctx.stroke()
        // 中心点
        this.ctx.fillStyle = resolveArgb(DivColor.GOLD_DARK)
        this.ctx.beginPath()
        this.ctx.arc(cx, cy, 2.5, 0, Math.PI * 2, false)
        this.ctx.fill()
      })
  }

  /**
   * 观象台玄墨特色卡（设计稿 darkcard3）：清氧页面里开一扇通往玄墨世界的窗。
   * 深色自带分层不加投影；皮肤取 DivColors 玄墨暗色令牌，浅深模式同款
   * （设计稿深色副本对此卡无覆盖，故浅色模式下仍为深色卡）。
   * 内部节奏：罗盘→文字→箭头统一 16（Row space spaceMd，对齐安卓 spacedBy）。
   */
  @Builder
  DivinationEntryCard() {
    Row({ space: DesignDimens.SPACE_MD }) {
      this.CompassCanvas()

      Column() {
        // U-116：v0.4 字阶已废字距，删除 letterSpacing(0.3)（对齐安卓）
        Text(WorkbenchText.workbench_divination_name)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DivColor.TEXT_PRIMARY_DARK)
        // 副标题与标题同色（宣纸主文本），弱层级靠字号 12/字重 400（对齐安卓 DivTextPrimaryDark）
        Text(WorkbenchText.workbench_divination_subtitle)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DivColor.TEXT_PRIMARY_DARK)
          .margin({ top: DesignDimens.SPACE_XS })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      // 32dp 圆形描边箭头（金线令牌 30%）；内 chevron 20 对齐安卓 icon 20dp
      Column() {
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
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
    // 圆角对齐安卓 radiusLg 16（卡片唯一档，原 20 不在规范五档内）
    .borderRadius(DesignDimens.CARD_RADIUS)
    // 描边 = 主题 outline（2026-11-05 P07 画板复裁弃鎏金，对齐安卓 1px 实线随主题换色）
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    // 渐变三段色标 0 / 0.55 / 1 = 规范 §1.5 divination.ink：#201D18→#1A1917@55%→#151412
    //（对齐安卓 DivBgTop/Mid/BottomDark；140deg ≈ 左上→右下，ArkUI RightBottom）
    .linearGradient({
      direction: GradientDirection.RightBottom,
      colors: [[DivColor.BG_TOP_DARK, 0.0], [DivColor.BG_MID_DARK, 0.55], [DivColor.BG_BOTTOM_DARK, 1.0]]
    })
    // 按压反馈（对齐安卓 pressable scale 0.97）
    .scale({
      x: this.pressedKey === 'divination' ? 0.97 : 1,
      y: this.pressedKey === 'divination' ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = 'divination'
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.onDivinationCardClick()
    })
  }

  /** 配图（网络图）：SURFACE_VARIANT 底兜位 + Cover 裁切（安卓 Coil 同语义） */
  @Builder
  WorkbenchImage(url: string) {
    Stack() {
      Image(url)
        .width('100%')
        .height('100%')
        .objectFit(ImageFit.Cover)
        .draggable(false)
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.SURFACE_VARIANT'))
  }

  /** 配图（本地自绘封面兜底，对齐安卓 LocalCoverArt）：无服务端下发/无网络时占位，避免退化为纯文字卡 */
  @Builder
  WorkbenchCoverImage(image: Resource) {
    Stack() {
      Image(image)
        .width('100%')
        .height('100%')
        .objectFit(ImageFit.Cover)
        .draggable(false)
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.SURFACE_VARIANT'))
  }

  /**
   * 公告条（type=notice）：PRIMARY_SOFT 浅绿底（对齐安卓 scheme.primarySoft，非 SUCCESS_CONTAINER）
   * + 白色小图标容器 + 可选跳转箭头 + 48vp 关闭钮；内部节奏统一 12（Row space spaceSm）。
   * 文案 56vp 固定行高内单行省略（对齐安卓 maxLines=1，长文案不再折行裁切）。
   * target 为空 = 纯文案公告（整条不可点）；关闭即从列表过滤并落 Preferences。
   */
  @Builder
  NoticeBar(tool: WorkbenchTool) {
    Row({ space: DesignDimens.SPACE_SM }) {
      // 图标仍在容器里（§3.2）：白色小容器浮在浅绿条上，分层清晰
      IconBox({
        icon: tool.icon,
        sizeValue: 32,
        iconSize: 20,
        containerColor: $r('app.color.SURFACE'),
        iconColor: $r('app.color.PRIMARY')
      })

      Text(tool.title)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.ON_PRIMARY_SOFT'))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .layoutWeight(1)

      if (tool.target.length > 0) {
        // alpha 0.7 对齐安卓 onPrimarySoft.copy(alpha = 0.7)
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(16)
          .fontColor([$r('app.color.ON_PRIMARY_SOFT')])
          .opacity(0.7)
      }

      // 关闭钮：48vp 方形热区（行高 56 内余量充足）
      Column() {
        // alpha 0.8 对齐安卓（原 0.6 对比不足，0.8 合成后约 7:1）
        SymbolGlyph($r('sys.symbol.xmark'))
          .fontSize(16)
          .fontColor([$r('app.color.ON_PRIMARY_SOFT')])
          .opacity(0.8)
      }
      .width(48)
      .height(48)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .onClick(() => {
        this.dismissNotice(tool)
      })
    }
    .width('100%')
    .height(56)
    .alignItems(VerticalAlign.Center)
    .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_XS })
    .borderRadius(DesignDimens.RADIUS_MD)
    .backgroundColor($r('app.color.PRIMARY_SOFT'))
    // 按压反馈仅整条可点时生效（对齐安卓 pressable 条件包裹）
    .scale({
      x: tool.target.length > 0 && this.pressedKey === `notice_${tool.entryKey}` ? 0.97 : 1,
      y: tool.target.length > 0 && this.pressedKey === `notice_${tool.entryKey}` ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `notice_${tool.entryKey}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      // 整条可点 = 有跳转目标时（内层关闭钮点击经手势竞争优先消费，不触发整条）
      if (tool.target.length > 0) {
        this.onToolClick(tool)
      }
    })
  }

  /** 通栏横卡（type=wide）：可选配图（16:9 顶部通铺）+ 图标容器行 + 右箭头；行内节奏统一 16（Row space spaceMd） */
  @Builder
  WideCard(tool: WorkbenchTool) {
    Column() {
      if (tool.imageUrl.length > 0) {
        Stack() {
          this.WorkbenchImage(tool.imageUrl)
        }
        .width('100%')
        .aspectRatio(1.78)
      } else {
        Stack() {
          this.WorkbenchCoverImage(tool.cover)
        }
        .width('100%')
        .aspectRatio(1.78)
      }
      Row({ space: DesignDimens.SPACE_MD }) {
        IconBox({
          icon: tool.icon,
          containerColor: this.containerColor(tool.tint),
          iconColor: this.iconColor(tool.tint)
        })

        Column() {
          Text(tool.title)
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_PRIMARY'))
          if (tool.description.length > 0) {
            Text(tool.description)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .margin({ top: DesignDimens.SPACE_XXS })
          }
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
          .fontColor([$r('app.color.TEXT_TERTIARY')])
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      .padding(DesignDimens.CARD_PADDING)
    }
    .width('100%')
    .clip(true)
    .cardChrome()
    .scale({
      x: this.pressedKey === `wide_${tool.entryKey}` ? 0.97 : 1,
      y: this.pressedKey === `wide_${tool.entryKey}` ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `wide_${tool.entryKey}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.onToolClick(tool)
    })
  }

  /**
   * 两列小卡行（连续 tile 归并产物；奇数个末位补空保持网格）。
   * 等高机制（对齐安卓 IntrinsicSize.Min + fillMaxHeight，2026-11-11 审查 #4 镜像）：
   * Flex alignItems=ItemAlign.Stretch 使同行两卡拉伸至行高（行高取内容较高卡），
   * 短卡底部自动补齐；描述自身限两行截断从源头杜绝超长文案拉高卡片（ToolTile 内）。
   * topSpacing：间距责任模型由调用方按前置块判定（观象台卡→tile 32、Section 后 0 等）；
   * enterDelay：逐项错峰入场延迟。
   */
  @Builder
  TileRowContent(row: WorkbenchTool[], topSpacing: number, enterDelay: number) {
    Flex({
      direction: FlexDirection.Row,
      alignItems: ItemAlign.Stretch,
      space: { main: LengthMetrics.vp(DesignDimens.CARD_SPACING) }
    }) {
      ForEach(row, (tool: WorkbenchTool) => {
        Column() {
          this.ToolTile(tool)
        }
        .layoutWeight(1)
        // 卡片 chrome + 按压交互放 Flex 直接子项：ItemAlign.Stretch 拉伸本列后
        // 白卡/描边/阴影随之铺满行高实现等高（ToolTile 根不设 height('100%')，
        // 否则行高↔子项高度循环解析回退为整屏高度——真机回归发现的撑满屏 bug）
        .cardChrome()
        .clip(true)
        .scale({
          x: this.pressedKey === `tile_${tool.entryKey}` ? 0.97 : 1,
          y: this.pressedKey === `tile_${tool.entryKey}` ? 0.97 : 1
        })
        .animation({ duration: 100, curve: Curve.EaseOut })
        .onTouch((event: TouchEvent) => {
          if (event.type === TouchType.Down) {
            this.pressedKey = `tile_${tool.entryKey}`
          } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
            this.pressedKey = ''
          }
        })
        .onClick(() => {
          this.onToolClick(tool)
        })
      }, (tool: WorkbenchTool) => tool.entryKey)
      // 奇数个末位补位，保持两列网格
      if (row.length === 1) {
        Column() {
        }
        .layoutWeight(1)
      }
    }
    .width('100%')
    .padding({
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING,
      top: topSpacing,
      bottom: DesignDimens.CARD_SPACING
    })
    .transition(TransitionEffect.OPACITY.animation({
      duration: STAGGER_ENTER_MS,
      delay: enterDelay,
      curve: Curve.Friction
    }))
  }

  /** 列表行：36vp 次级图标容器 + 14/500 主文案 + 角标 + 箭头（行高 56，规范 §3.4） */
  @Builder
  RowLine(tool: WorkbenchTool) {
    Row() {
      IconBox({
        icon: tool.icon,
        sizeValue: 36,
        iconSize: 20,
        containerColor: this.containerColor(tool.tint),
        iconColor: this.iconColor(tool.tint)
      })

      Text(tool.title)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

      if (tool.badge.length > 0) {
        // U-104：原 #2D9B6A on #F0FBF4 仅 3.30:1；角标 12/500 不适用绿字红线（要求 ≥16sp/600），
        // 对齐安卓 labelSmall 12/500 + textPrimary #1C1C1E（16.05:1）
        Text(tool.badge)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .backgroundColor($r('app.color.PRIMARY_LIGHT'))
          .borderRadius(DesignDimens.RADIUS_FULL)
          .padding({
            left: DesignDimens.SPACE_SM,
            right: DesignDimens.SPACE_SM,
            top: 2,
            bottom: 2
          })
      }

      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(16)
        .fontColor([$r('app.color.TEXT_TERTIARY')])
        .margin({ left: DesignDimens.SPACE_SM })
    }
    .width('100%')
    .height(56)
    .alignItems(VerticalAlign.Center)
    .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
    .scale({
      x: this.pressedKey === `row_${tool.entryKey}` ? 0.97 : 1,
      y: this.pressedKey === `row_${tool.entryKey}` ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `row_${tool.entryKey}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.onToolClick(tool)
    })
  }

  /** 列表行组卡（type=row 连续归并产物）：一张白卡 + 0.5vp 发丝线行分隔（规范 §3.4 卡内） */
  @Builder
  RowGroupCard(tools: WorkbenchTool[]) {
    Column() {
      ForEach(tools, (tool: WorkbenchTool, index: number) => {
        if (index > 0) {
          // 发丝线只出现在同一容器内部（§1.3/§3.4）
          Divider()
            .color($r('app.color.HAIRLINE'))
            .strokeWidth(0.5)
            .margin({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
        }
        this.RowLine(tool)
      }, (tool: WorkbenchTool) => tool.entryKey)
    }
    .width('100%')
    .clip(true)
    .cardChrome()
  }

  /**
   * 工具卡（type=tile，设计稿 tile3）：白卡 1px OUTLINE 描边 + 卡片阴影 + 圆角 16
   * + 可选配图（顶部 96vp 通铺，图卡不再叠图标容器）+ 44vp 语义图标容器 + 标题 + 描述；
   * 标题 14/500、描述 12/500（对齐安卓审查 #11 字重统一）；描述限两行省略（审查 #4B，
   * 超长描述不再无限拉高卡片）。卡片 chrome 与按压交互由 TileRowContent 的直接
   * Column 承载（Flex Stretch 等高），本 Builder 只渲染内容。
   * 右上角 badge 角标（清氧小胶囊）。描述为空不渲染占位行（卡内节奏不因空文案错位）。
   */
  @Builder
  ToolTile(tool: WorkbenchTool) {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column() {
          if (tool.imageUrl.length > 0) {
          Stack() {
            this.WorkbenchImage(tool.imageUrl)
          }
          .width('100%')
          .height(96)
        } else {
          Stack() {
            this.WorkbenchCoverImage(tool.cover)
          }
          .width('100%')
          .height(96)
        }

        Column() {
          // 有封面（网络图或本地兜底）时不再叠图标容器，仅标题（对齐安卓 TileCard hasCover 分支）
          Text(tool.title)
            .fontSize(DesignFontSize.BODY_SMALL)
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_PRIMARY'))

          if (tool.description.length > 0) {
            Text(tool.description)
              .fontSize(DesignFontSize.CAPTION)
              .fontWeight(DesignFontWeight.MEDIUM)
              .fontColor($r('app.color.TEXT_SECONDARY'))
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .margin({ top: DesignDimens.SPACE_XXS })
          }
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        .padding(DesignDimens.CARD_PADDING)
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)

      // badge 角标（服务端下发文案）：清氧小胶囊 — radiusFull + 主色浅底（caption 档）
      // U-104：文字对齐安卓 labelSmall 12/500 + textPrimary #1C1C1E（原 #2D9B6A on #F0FBF4 仅 3.30:1）
      if (tool.badge.length > 0) {
        Text(tool.badge)
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .backgroundColor($r('app.color.PRIMARY_LIGHT'))
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
  }

  /**
   * 最近使用 chip：白胶囊 + 32vp 小图标容器，整颗 48vp 高热区。
   * 自带 margin left 移除（P1-9：原与行 space 12 叠加成 36），
   * 左页边距由横滑行 padding left 24 承担、chip 间 12 由行 space 承担。
   */
  @Builder
  RecentChip(tool: WorkbenchTool) {
    Row() {
      IconBox({
        icon: tool.icon,
        sizeValue: 32,
        iconSize: 20,
        containerColor: this.containerColor(tool.tint),
        iconColor: this.iconColor(tool.tint)
      })

      Text(tool.title)
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .margin({ left: DesignDimens.SPACE_SM })
    }
    .alignItems(VerticalAlign.Center)
    .padding({
      left: DesignDimens.SPACE_XXS,
      right: DesignDimens.SPACE_SM,
      top: DesignDimens.SPACE_XS,
      bottom: DesignDimens.SPACE_XS
    })
    .borderRadius(DesignDimens.RADIUS_FULL)
    .border({ width: 1, color: $r('app.color.OUTLINE') })
    .backgroundColor($r('app.color.SURFACE'))
    .scale({
      x: this.pressedKey === `chip_${tool.entryKey}` ? 0.97 : 1,
      y: this.pressedKey === `chip_${tool.entryKey}` ? 0.97 : 1
    })
    .animation({ duration: 100, curve: Curve.EaseOut })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = `chip_${tool.entryKey}`
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.onToolClick(tool)
    })
  }

  /** 常态内容体（Refresh 内滚动区）：标题 → 搜索 → 状态/工具区 → 页脚 */
  @Builder
  PageContent() {
    Column() {
      // 1. 标题区（设计稿顶部 16；底部对齐安卓 spaceLg 20）
      Column() {
        Text(WorkbenchText.workbench_title)
          .fontSize(DesignFontSize.ROOT_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
        // 大标题→副标题 4（对齐安卓 spaceXxs，原 8 偏大）
        Text(WorkbenchText.workbench_subtitle)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor($r('app.color.TEXT_SECONDARY'))
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

      // 2. 搜索框 + 扫一扫常驻入口（系统 Search 组件：自带搜索图标与清除钮；底/圆角对齐安卓
      //    §3.7 输入框；正文/占位 16、占位色 textSecondary、光标主色显式声明（对齐安卓显式控制，
      //    审查 P2-22），清除钮热区走系统默认属端限制；Search 直接 margin + width('100%') 会
      //    溢出父容器被居中抵消，故外包一层。扫一扫为端上固定工具位（不参与服务端下发与搜索
      //    过滤，对齐安卓工作台搜索框右侧同位），点击拉起系统扫码页并统一分发）
      Row() {
        Column() {
          Search({
            value: this.searchQuery,
            placeholder: WorkbenchText.workbench_search_hint
          })
            .onChange((value: string) => {
              this.onSearchChange(value)
            })
            .onFocus(() => {
              // 键盘弹出前重设 NONE（模拟器上 ArkUI 避让引擎不跟随 NONE，见
              // applyKeyboardAvoidNone 注释；真机语义正确，保留为契约兜底）
              this.applyKeyboardAvoidNone()
            })
            .width('100%')
            .height(48)
            .backgroundColor($r('app.color.SURFACE_VARIANT'))
            .borderRadius(DesignDimens.INPUT_RADIUS)
            .placeholderColor($r('app.color.TEXT_SECONDARY'))
            .placeholderFont({ size: DesignFontSize.BODY })
            .textFont({ size: DesignFontSize.BODY })
            // SearchAttribute 无 caretColor 属性（SDK 17 编译核实），光标色不可自定义
            // 图标/文案内边距对齐安卓：start spaceSm / end spaceXs
            .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.SPACE_XS })
        }
        .layoutWeight(1)

        // 扫一扫入口：48vp 热区包 44vp 图标容器（§3.2 图标进容器），热区左右留出与搜索框间距
        Column() {
          IconBox({
            icon: $r('sys.symbol.barcode_viewfinder'),
            accessibilityLabel: resString('scan_title')
          })
        }
        .height(48)
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          scanAndDispatch(getContext(this) as common.UIAbilityContext)
        })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      // 底部间距对齐安卓：安卓搜索框无底距，其下区块自带 sectionSpacing(32) 顶距；
      // 原 bottom: SPACE_MD(16) 会让下方内容整体多下移 16vp
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING
      })

      // 3. 骨架 / 错误 / 空态 / 工具区
      if (this.loadState === LoadState.LOADING && this.tools.length === 0) {
        this.ToolsSkeleton()
      } else if (this.loadState === LoadState.ERROR) {
        Column() {
          this.StateHint(WorkbenchText.workbench_entries_error, WorkbenchText.workbench_retry, (): void => {
            this.loadEntries()
          })
        }
        .width('100%')
        .padding({ top: DesignDimens.SECTION_SPACING })
      } else if (this.visibleTools().length === 0) {
        Column() {
          // 搜索无结果空态给「清空搜索」出路（对齐安卓 P2：空态缺行动按钮，与错误态重试同规格）
          this.StateHint(
            this.searchQuery.trim().length > 0
              ? WorkbenchText.workbench_search_empty
              : WorkbenchText.workbench_entries_empty,
            this.searchQuery.trim().length > 0 ? WorkbenchText.workbench_search_clear : '',
            (): void => {
              this.searchQuery = ''
            }
          )
        }
        .width('100%')
        .padding({ top: DesignDimens.SECTION_SPACING })
      } else {
        this.WorkbenchBody()
      }

      // 4. 页脚弱提示（对齐安卓 spaceXs 8/8；原 24/8 为旧设计稿口径未随安卓更新）
      // U-105：原 textTertiary #A9AEA9 on #F5F6F3 仅 2.08:1（正文红线 ≥4.5）；
      // 对齐安卓 labelSmall 12/500 + textSecondary #6B6E73（实测 4.72:1）
      Text(WorkbenchText.workbench_footer)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .textAlign(TextAlign.Center)
        .width('100%')
        .padding({
          left: DesignDimens.PAGE_PADDING,
          right: DesignDimens.PAGE_PADDING,
          top: DesignDimens.SPACE_XS,
          bottom: DesignDimens.SPACE_XS
        })
    }
    .width('100%')
    .padding({ top: safeTopVp(), bottom: safeBottomVp() })
  }

  /** 工具区主体：搜索态平铺两列；常态 = 玄墨品牌位 → 最近使用 → 类型化分区 */
  @Builder
  WorkbenchBody() {
    if (this.searchQuery.trim().length > 0) {
      // 搜索态：平铺两列（快速扫视优先，分组/类型排版让位）；首行顶距 24（对齐安卓 spaceXl）
      Column({ space: 0 }) {
        ForEach(this.buildRows(this.visibleTools()), (row: WorkbenchTool[], rowIndex: number) => {
          this.TileRowContent(row, rowIndex === 0 ? DesignDimens.SPACE_XL : 0,
            Math.min(rowIndex, STAGGER_MAX_INDEX) * STAGGER_INTERVAL_MS)
        }, (row: WorkbenchTool[]) => `search_${row.length}_${row.length > 0 ? row[0].entryKey : ''}`)
      }
      .width('100%')
    } else {
      // 最近使用（本地记忆与当次下发求交集，非空才显示）
      if (this.recentTools().length > 0) {
        Column() {
          // U-116：v0.4 字阶已废字距，删除 letterSpacing(0.3)（对齐安卓 RecentSection 标题）
          Text(WorkbenchText.workbench_recent_title)
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor($r('app.color.TEXT_PRIMARY'))
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          Scroll() {
            Row({ space: DesignDimens.SPACE_SM }) {
              ForEach(this.recentTools(), (tool: WorkbenchTool) => {
                this.RecentChip(tool)
              }, (tool: WorkbenchTool) => tool.entryKey)
              // 行尾补右侧页边距（Scroll 裁切；补位 12 + 行 space 12 = 24 对齐安卓 contentPadding）
              Column() {
              }
              .width(DesignDimens.PAGE_PADDING - DesignDimens.SPACE_SM)
            }
            // 左页边距由行 padding 承担（chip 自带 margin 已移除，消除 chip 间 36 叠加）
            .padding({ left: DesignDimens.PAGE_PADDING })
          }
          .scrollable(ScrollDirection.Horizontal)
          .scrollBar(BarState.Off)
          // chips 少于一行宽时左对齐（Scroll 默认居中，安卓 LazyRow 从页边距起排）
          .align(Alignment.Start)
          .width('100%')
          .margin({ top: DesignDimens.SPACE_SM })
        }
        .width('100%')
        .margin({ top: DesignDimens.SECTION_SPACING })
      }

      // 观象台玄墨特色卡（端上固定品牌位，不随服务端下发）
      // 顶距：有「最近使用」按区块 32；无则取 12（设计稿 P07 搜索框底→观象台卡顶 = 12，
      // 对齐安卓 2026-11-06 复验修复，原 0 导致卡片与搜索框紧贴）
      Column() {
        this.DivinationEntryCard()
      }
      .width('100%')
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
      .margin({ top: this.recentTools().length > 0 ? DesignDimens.SECTION_SPACING : DesignDimens.SPACE_SM })

      // 类型化分区（折块渲染）；无 section_title 时显示「全部工具」总标题
      if (this.hasSections()) {
        ForEach(buildWorkbenchBlocks(this.visibleTools()), (block: WorkbenchBlock, index: number) => {
          if (block.kind === WorkbenchBlockKind.SECTION) {
            this.SectionHeader(block.title)
          } else if (block.kind === WorkbenchBlockKind.NOTICE) {
            Column() {
              this.NoticeBar(block.tool)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            .margin({ top: this.blockTop(block), bottom: DesignDimens.CARD_SPACING })
            .transition(this.staggerEnter(index))
          } else if (block.kind === WorkbenchBlockKind.WIDE) {
            Column() {
              this.WideCard(block.tool)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            .margin({ top: this.blockTop(block), bottom: DesignDimens.CARD_SPACING })
            .transition(this.staggerEnter(index))
          } else if (block.kind === WorkbenchBlockKind.TILE_ROW) {
            this.TileRowContent(block.tools, this.tileRowTop(block),
              Math.min(index, STAGGER_MAX_INDEX) * STAGGER_INTERVAL_MS)
          } else {
            Column() {
              this.RowGroupCard(block.tools)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            .margin({ top: this.blockTop(block), bottom: DesignDimens.CARD_SPACING })
            .transition(this.staggerEnter(index))
          }
        }, (block: WorkbenchBlock) => this.blockKey(block))
      } else {
        // U-116：v0.4 字阶已废字距，删除 letterSpacing(0.3)（对齐安卓「全部工具」标题）
        Text(WorkbenchText.workbench_all_tools)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          .margin({ top: DesignDimens.SECTION_SPACING, bottom: DesignDimens.SPACE_SM })

        ForEach(buildWorkbenchBlocks(this.visibleTools()), (block: WorkbenchBlock, index: number) => {
          if (block.kind === WorkbenchBlockKind.TILE_ROW) {
            this.TileRowContent(block.tools, this.tileRowTop(block),
              Math.min(index, STAGGER_MAX_INDEX) * STAGGER_INTERVAL_MS)
          } else if (block.kind === WorkbenchBlockKind.ROW_GROUP) {
            Column() {
              this.RowGroupCard(block.tools)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            .margin({ top: this.blockTop(block), bottom: DesignDimens.CARD_SPACING })
            .transition(this.staggerEnter(index))
          } else if (block.kind === WorkbenchBlockKind.WIDE) {
            Column() {
              this.WideCard(block.tool)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            .margin({ top: this.blockTop(block), bottom: DesignDimens.CARD_SPACING })
            .transition(this.staggerEnter(index))
          } else if (block.kind === WorkbenchBlockKind.NOTICE) {
            Column() {
              this.NoticeBar(block.tool)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
            .margin({ top: this.blockTop(block), bottom: DesignDimens.CARD_SPACING })
            .transition(this.staggerEnter(index))
          }
        }, (block: WorkbenchBlock) => this.blockKey(block))
      }
    }
  }

  /**
   * 逐项错峰入场（对齐安卓 StaggerIn：150ms 淡入 + 40ms×6 上限错峰）。
   * reduced motion 降级：鸿蒙端暂无统一 reduceMotion 开关（端限制，对齐报告口径），
   * 已选用无位移纯淡入将动效敏感影响降至最低。
   */
  private staggerEnter(index: number): TransitionEffect {
    return TransitionEffect.OPACITY.animation({
      duration: STAGGER_ENTER_MS,
      delay: Math.min(index, STAGGER_MAX_INDEX) * STAGGER_INTERVAL_MS,
      curve: Curve.Friction
    })
  }

  /**
   * 内容块顶距（间距责任模型，对齐安卓 WorkbenchPage.kt:400-410 审查 #1/#2 镜像）：
   * notice/wide/rowgroup 按前置块判定——Section 后 = 0（标题 bottom spaceSm 12 已承担
   * 标题→内容节奏）；无分组时「全部工具」总标题打头（bottom 12 同 Section）首块亦为 0；
   * 其余前置（观象台卡 / 其他卡片块）自担 sectionSpacing(32)，消除 44dp 跳变。
   */
  private blockTop(block: WorkbenchBlock): number {
    const afterSection = block.prevKind === WorkbenchBlockKind.SECTION
    const afterToolsHeader = block.prevKind === null && !this.hasSections()
    return afterSection || afterToolsHeader ? 0 : DesignDimens.SECTION_SPACING
  }

  /**
   * tile 行顶距（对齐安卓 WorkbenchPage.kt:436-441 审查 #3/#4 镜像）：
   * Section 后 / 连续 tile 行 / 无分组首块 = 0（标题→内容 12、行距 16 由上一块 bottom 承担）；
   * 其余前置（观象台卡、wide/notice/rowgroup）自担 sectionSpacing(32)，
   * 消除观象台卡→tile 0dp 贴合叠影与 48→16 跳变。
   */
  private tileRowTop(block: WorkbenchBlock): number {
    const afterSection = block.prevKind === WorkbenchBlockKind.SECTION
    const afterTileRow = block.prevKind === WorkbenchBlockKind.TILE_ROW
    const afterToolsHeader = block.prevKind === null && !this.hasSections()
    return afterSection || afterTileRow || afterToolsHeader ? 0 : DesignDimens.SECTION_SPACING
  }

  /** 是否存在分组标题（决定「全部工具」总标题显隐，对齐安卓 hasSections） */
  private hasSections(): boolean {
    return this.visibleTools().some((tool: WorkbenchTool): boolean => tool.sectionTitle.length > 0)
  }

  /**
   * 折块 ForEach key（对齐安卓 WorkbenchBlock.key）。Section 追加折块流水号 ordinal：
   * 服务端可配 A→B→A 同名分组序列，仅凭 title 拼 key 会重复导致 ForEach 复用错乱（审查 #5）。
   */
  private blockKey(block: WorkbenchBlock): string {
    if (block.kind === WorkbenchBlockKind.SECTION) {
      return `section_${block.ordinal}_${block.title}`
    }
    if (block.kind === WorkbenchBlockKind.TILE_ROW || block.kind === WorkbenchBlockKind.ROW_GROUP) {
      return `${block.kind}_${block.tools.map((tool: WorkbenchTool): string => tool.entryKey).join('-')}`
    }
    return `${block.kind}_${block.tool.entryKey}`
  }

  build() {
    // 双向绑定对齐项目 Refresh 统一口径（NoticeInboxPage 等 7 处 $$this），替代非空断言
    Refresh({ refreshing: $$this.isRefreshing }) {
      Scroll() {
        this.PageContent()
      }
      .scrollBar(BarState.Off)
      .width('100%')
      .height('100%')
      .align(Alignment.TopStart)
      .edgeEffect(EdgeEffect.Spring)
    }
    .onRefreshing(() => {
      this.loadEntries(true)
    })
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.BACKGROUND'))
  }
}

// 路由导出（router_map.json 注册用；当前作为底栏 tab 内联渲染，路由供外部入口预留）
@Builder
export function WorkbenchBuilder() {
  Workbench()
}
