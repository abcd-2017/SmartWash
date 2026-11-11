// 工作台域契约 — 工具条目模型 / 白名单映射 / 服务端下发转换 / 渲染折块 / 内置兜底数据 / 文案表
//
// 对齐安卓 ui/page/workbench/{WorkbenchTool.kt,WorkbenchViewModel.kt,WorkbenchPage.kt}（类型化组件版）：
// 数据源以服务端下发为准（GET /web/auth/workbench/entries，经 network/api/workbench.ets），
// 失败/空配置给真实错误态/空态（对齐安卓 2026-11-01 缺陷 6，不再静默回落）；
// DEFAULT_WORKBENCH_TOOLS 仅留作开发参考与深链联调，页面不再消费。
// 点击统一按 target 走深链分发（DeepLinkHandler.handleDeepLink）。
// 按 feature/README.md 约定：本文件只放声明层（VO 映射 + 枚举 + 折块 + 内置数据 + 文案），
// UI 组件与点击分发实现放域内 Workbench.ets。

import { WorkbenchEntryVo } from '../../network/api/workbench'
import { DEEPLINK_SCHEME } from '../../utils/deeplink/DeepLinkParser'
import { resString } from '../../utils/ResourceString'

/**
 * 工具卡图标容器语义色组（规范 §2.1 六色浅底，与后端 color 白名单 key 一一对应，
 * 对齐安卓 WorkbenchTint 六枚举）。
 * 未知 color key 兜底 GREEN（契约：未知值显示默认样式）。
 */
export enum WorkbenchTint {
  GREEN,
  BLUE,
  ORANGE,
  PINK,
  TEAL,
  PURPLE
}

/**
 * 后端 color key → 色组（green/blue/orange/pink/teal/purple，大小写不敏感，未知兜底 GREEN）。
 * 对齐安卓 WorkbenchTint.fromKey。
 */
export function workbenchTintFromKey(key: string | null | undefined): WorkbenchTint {
  const normalized = key !== undefined && key !== null ? key.toLowerCase() : ''
  switch (normalized) {
    case 'blue':
      return WorkbenchTint.BLUE
    case 'orange':
      return WorkbenchTint.ORANGE
    case 'pink':
      return WorkbenchTint.PINK
    case 'teal':
      return WorkbenchTint.TEAL
    case 'purple':
      return WorkbenchTint.PURPLE
    default:
      return WorkbenchTint.GREEN
  }
}

/**
 * 卡片类型（对齐安卓 WorkbenchLayout，与后端 type 白名单 key 一一对应）：
 * TILE 两列小卡（默认）/ WIDE 通栏横卡 / ROW 列表行（连续归并一张白卡）/ NOTICE 公告条（可关闭）。
 * 未知 type key 兜底 TILE（契约：未知值显示默认样式）。
 */
export enum WorkbenchLayout {
  TILE,
  WIDE,
  ROW,
  NOTICE
}

/** 后端 type key → 卡片类型（tile/wide/row/notice，大小写不敏感，未知兜底 TILE） */
export function workbenchLayoutFromKey(key: string | null | undefined): WorkbenchLayout {
  const normalized = key !== undefined && key !== null ? key.toLowerCase() : ''
  switch (normalized) {
    case 'wide':
      return WorkbenchLayout.WIDE
    case 'row':
      return WorkbenchLayout.ROW
    case 'notice':
      return WorkbenchLayout.NOTICE
    default:
      return WorkbenchLayout.TILE
  }
}

/**
 * 后端 icon 白名单 key → sys.symbol 图标映射（规范 §4.1：功能图标保持系统符号库）。
 * 白名单：calendar/search/book/calculate/history/chat/link/science/gamepad/default，
 * 未知 key 与 default 同样兜底为「工具格」图标（对齐安卓 Icons.Default.Apps）。
 * 符号名已对照 SDK sysResource.js 核实存在（gamepad=gamecontroller、science=science_technology，
 * 2026-11-04 与后端 Form 白名单对齐补录）。
 */
export function workbenchIconFromKey(key: string | null | undefined): Resource {
  const normalized = key !== undefined && key !== null ? key.toLowerCase() : ''
  switch (normalized) {
    case 'calendar':
      return $r('sys.symbol.calendar')
    case 'search':
      return $r('sys.symbol.magnifyingglass')
    case 'book':
      return $r('sys.symbol.book_pages')
    case 'calculate':
      return $r('sys.symbol.calculator')
    case 'history':
      return $r('sys.symbol.clock_arrow_2_circlepath')
    case 'chat':
      return $r('sys.symbol.ellipsis_message')
    case 'link':
      return $r('sys.symbol.link')
    case 'science':
      return $r('sys.symbol.science_technology')
    case 'gamepad':
      return $r('sys.symbol.gamecontroller')
    default:
      return $r('sys.symbol.square_grid_2x2')
  }
}

/**
 * 工作台工具条目（后端 WorkbenchEntryVo 的客户端映射层，字段对齐安卓 WorkbenchTool）。
 * 数据源二选一：服务端下发（title/description 为服务端文案）或本地兜底（WorkbenchText 文案表）。
 * 点击按 target 走深链分发（DeepLinkHandler.handleDeepLink）；target 为空 = 功能建设中。
 */
export interface WorkbenchTool {
  /** 业务唯一标识（服务端下发 entryKey 或兜底 key），兼作网格稳定 key */
  entryKey: string
  /** 标题（已解析文案，非资源 id） */
  title: string
  /** 描述（已解析文案，非资源 id） */
  description: string
  /** 语义图标（sys.symbol，白名单映射产物） */
  icon: Resource
  /** 图标容器语义色组（白名单映射产物） */
  tint: WorkbenchTint
  /** 跳转 URI：smartwash://... 或 https://...；空串 = 功能建设中（点击提示） */
  target: string
  /** 角标文案（如 新/热），空串不显示 */
  badge: string
  /** 卡片类型（服务端 type 下发映射；页面按类型选组件渲染） */
  layout: WorkbenchLayout
  /** 分组标题（服务端下发；与前一 entry 不同时端上渲染区块标题），空串不分组 */
  sectionTitle: string
  /** 配图直链（服务端下发），空串 = 纯文字卡片 */
  imageUrl: string
}

/**
 * 服务端入口 VO → 工具卡模型（严格模式：逐字段显式兜底，不依赖 class 直连 JSON）。
 * entryKey 兼作网格稳定 key（空值防御回退 index key，对齐安卓 toTool）。
 */
export function workbenchEntryVoToTool(vo: WorkbenchEntryVo, index: number): WorkbenchTool {
  const entryKey = vo.entryKey ?? ''
  return {
    entryKey: entryKey.length > 0 ? entryKey : `entry_${index}`,
    title: vo.title ?? '',
    description: vo.description ?? '',
    icon: workbenchIconFromKey(vo.icon),
    tint: workbenchTintFromKey(vo.color),
    target: vo.target ?? '',
    badge: vo.badge ?? '',
    layout: workbenchLayoutFromKey(vo.type),
    sectionTitle: vo.sectionTitle ?? '',
    imageUrl: vo.imageUrl ?? ''
  }
}

/**
 * entryKey 稳定性防御（对齐安卓 withUniqueKeys）：服务端配置理论上唯一，但网格按 entryKey
 * 作 ForEach key，重复会导致复用错乱——重复时追加序号去重，宁可展示也别错乱。
 */
export function withUniqueKeys(tools: Array<WorkbenchTool>): Array<WorkbenchTool> {
  const seen = new Map<string, number>()
  return tools.map((tool: WorkbenchTool): WorkbenchTool => {
    const count = seen.get(tool.entryKey) ?? 0
    seen.set(tool.entryKey, count + 1)
    if (count === 0) {
      return tool
    }
    return {
      entryKey: `${tool.entryKey}_${count + 1}`,
      title: tool.title,
      description: tool.description,
      icon: tool.icon,
      tint: tool.tint,
      target: tool.target,
      badge: tool.badge,
      layout: tool.layout,
      sectionTitle: tool.sectionTitle,
      imageUrl: tool.imageUrl
    }
  })
}

/**
 * 页面渲染块（对齐安卓 WorkbenchBlock）：服务端 type + section_title 下发驱动的构图单元。
 * 按 sort 顺序把条目折成块——sectionTitle 变化处插区块标题，连续 row 归并进一张白卡，
 * 连续 tile 两列成行，wide/notice 各占一行。
 */
export enum WorkbenchBlockKind {
  SECTION,
  NOTICE,
  WIDE,
  TILE_ROW,
  ROW_GROUP
}

export interface WorkbenchBlock {
  kind: WorkbenchBlockKind
  /** 区块标题（kind=SECTION 用，其余空串） */
  title: string
  /**
   * Section 折块流水号（kind=SECTION 用）：同名分组在 A→B→A 序列下重现时递增，
   * key 永不重复（对齐安卓 WorkbenchBlock.Section.ordinal，2026-11-11 审查 #5）。
   * 非 SECTION 块恒 0。
   */
  ordinal: number
  /**
   * 前一块 kind（首块为 null）：渲染层间距责任模型按前置块判定顶距
   * （对齐安卓 blocks.getOrNull(index - 1)；@Builder 内不便回查数组，折块时随块携带）。
   */
  prevKind: WorkbenchBlockKind | null
  /** 公告/通栏单卡（kind=NOTICE/WIDE 用，其余取 tools[0] 占位，渲染时按 kind 分派） */
  tool: WorkbenchTool
  /** 两列小卡行 / 列表行组（kind=TILE_ROW/ROW_GROUP 用，其余空数组） */
  tools: Array<WorkbenchTool>
}

/**
 * 按 sort 序折块（对齐安卓 List<WorkbenchTool>.toBlocks）：sectionTitle 与前一 entry 不同 →
 * 插区块标题（并打断连续归并链，不同分组的 row/tile 各自成组）；随后按 layout 流式归并。
 * Section 维护折块流水号 ordinal（A→B→A 同名分组序列下 key 永不重复，审查 #5）；
 * 每块携带 prevKind（前一块 kind）供渲染层间距责任模型按前置块判定顶距（审查 #2/#3/#4）。
 */
export function buildWorkbenchBlocks(tools: Array<WorkbenchTool>): Array<WorkbenchBlock> {
  const blocks: WorkbenchBlock[] = []
  let currentSection: string = ''
  let sectionOrdinal: number = 0
  let prevKind: WorkbenchBlockKind | null = null
  tools.forEach((tool: WorkbenchTool): void => {
    if (tool.sectionTitle.length > 0 && tool.sectionTitle !== currentSection) {
      blocks.push({
        kind: WorkbenchBlockKind.SECTION,
        title: tool.sectionTitle,
        ordinal: sectionOrdinal,
        prevKind: prevKind,
        tool: tool,
        tools: []
      })
      sectionOrdinal++
      currentSection = tool.sectionTitle
      prevKind = WorkbenchBlockKind.SECTION
    }
    switch (tool.layout) {
      case WorkbenchLayout.NOTICE:
        blocks.push({ kind: WorkbenchBlockKind.NOTICE, title: '', ordinal: 0, prevKind: prevKind, tool: tool, tools: [] })
        prevKind = WorkbenchBlockKind.NOTICE
        break
      case WorkbenchLayout.WIDE:
        blocks.push({ kind: WorkbenchBlockKind.WIDE, title: '', ordinal: 0, prevKind: prevKind, tool: tool, tools: [] })
        prevKind = WorkbenchBlockKind.WIDE
        break
      case WorkbenchLayout.ROW: {
        const last = blocks.length > 0 ? blocks[blocks.length - 1] : null
        if (last !== null && last.kind === WorkbenchBlockKind.ROW_GROUP) {
          last.tools.push(tool)
        } else {
          blocks.push({ kind: WorkbenchBlockKind.ROW_GROUP, title: '', ordinal: 0, prevKind: prevKind, tool: tool, tools: [tool] })
          prevKind = WorkbenchBlockKind.ROW_GROUP
        }
        break
      }
      case WorkbenchLayout.TILE:
      default: {
        const last = blocks.length > 0 ? blocks[blocks.length - 1] : null
        if (last !== null && last.kind === WorkbenchBlockKind.TILE_ROW && last.tools.length < 2) {
          last.tools.push(tool)
        } else {
          blocks.push({ kind: WorkbenchBlockKind.TILE_ROW, title: '', ordinal: 0, prevKind: prevKind, tool: tool, tools: [tool] })
          prevKind = WorkbenchBlockKind.TILE_ROW
        }
        break
      }
    }
  })
  return blocks
}

/**
 * 工作台文案表 — 键名逐一对齐安卓 app/src/main/res/values/strings.xml 的 workbench_* 资源，
 * 便于跨端文案核对（安卓 workbench_title / workbench_subtitle / workbench_divination_name ...）。
 */
export class WorkbenchText {
  static get workbench_title(): string { return resString('workbench_title') }
  static get workbench_subtitle(): string { return resString('workbench_subtitle') }
  static get workbench_divination_name(): string { return resString('workbench_divination_name') }
  static get workbench_divination_subtitle(): string { return resString('workbench_divination_subtitle') }
  static get workbench_all_tools(): string { return resString('workbench_all_tools') }
  static get workbench_tool_calendar(): string { return resString('workbench_text_workbench_tool_calendar') }
  static get workbench_tool_calendar_desc(): string { return resString('workbench_text_workbench_tool_calendar_desc') }
  static get workbench_tool_stain(): string { return resString('workbench_text_workbench_tool_stain') }
  static get workbench_tool_stain_desc(): string { return resString('workbench_text_workbench_tool_stain_desc') }
  static get workbench_tool_fabric(): string { return resString('workbench_text_workbench_tool_fabric') }
  static get workbench_tool_fabric_desc(): string { return resString('workbench_text_workbench_tool_fabric_desc') }
  static get workbench_tool_estimate(): string { return resString('ai_task_recharge') }
  static get workbench_tool_estimate_desc(): string { return resString('workbench_text_workbench_tool_estimate_desc') }
  static get workbench_tool_history(): string { return resString('workbench_text_workbench_tool_history') }
  static get workbench_tool_history_desc(): string { return resString('workbench_text_workbench_tool_history_desc') }
  static get workbench_tool_ai(): string { return resString('workbench_text_workbench_tool_ai') }
  static get workbench_tool_ai_desc(): string { return resString('workbench_text_workbench_tool_ai_desc') }
  static get workbench_tool_short_code(): string { return resString('toolbox_title') }
  static get workbench_tool_short_code_desc(): string { return resString('workbench_text_workbench_tool_short_code_desc') }
  static get workbench_footer(): string { return resString('workbench_footer') }
  static get workbench_coming_soon(): string { return resString('workbench_coming_soon') }
  // 搜索 / 最近使用 / 公告条 / 真实错误空态（2026-11-04 类型化组件批，键名对齐安卓 strings.xml）
  static get workbench_entries_error(): string { return resString('workbench_entries_error') }
  static get workbench_entries_empty(): string { return resString('workbench_entries_empty') }
  static get workbench_retry(): string { return resString('workbench_retry') }
  static get workbench_search_hint(): string { return resString('workbench_search_hint') }
  static get workbench_search_empty(): string { return resString('workbench_search_empty') }
  static get workbench_recent_title(): string { return resString('workbench_recent_title') }
  static get workbench_notice_dismiss(): string { return resString('workbench_notice_dismiss') }
}

/**
 * 本地兜底工具列表（设计稿屏 15 六项 + 短链工具箱，对齐安卓 WorkbenchViewModel.defaultTools）。
 * 服务端入口配置与端上兜底按 entryKey 对齐（后端 WorkbenchEntryVo 注释约定）；
 * 跳转目标全部走深链（host 由 utils/deeplink/DeepLinkRegistry 注册，host 串就地书写并注释），
 * target 空串 = 功能建设中。
 */
export const DEFAULT_WORKBENCH_TOOLS: WorkbenchTool[] = [
  {
    entryKey: "laundry_calendar",
    title: WorkbenchText.workbench_tool_calendar,
    description: WorkbenchText.workbench_tool_calendar_desc,
    icon: workbenchIconFromKey("calendar"),
    tint: WorkbenchTint.GREEN,
    target: "", // 洗衣黄历：功能建设中
    badge: "",
    layout: WorkbenchLayout.TILE,
    sectionTitle: "",
    imageUrl: ""
  },
  {
    entryKey: "stain_recognition",
    title: WorkbenchText.workbench_tool_stain,
    description: WorkbenchText.workbench_tool_stain_desc,
    icon: workbenchIconFromKey("search"),
    tint: WorkbenchTint.BLUE,
    target: "", // 渍迹识别：功能建设中
    badge: "",
    layout: WorkbenchLayout.TILE,
    sectionTitle: "",
    imageUrl: ""
  },
  {
    entryKey: "fabric_encyclopedia",
    title: WorkbenchText.workbench_tool_fabric,
    description: WorkbenchText.workbench_tool_fabric_desc,
    icon: workbenchIconFromKey("book"),
    tint: WorkbenchTint.TEAL,
    target: "", // 面料百科：功能建设中
    badge: "",
    layout: WorkbenchLayout.TILE,
    sectionTitle: "",
    imageUrl: ""
  },
  {
    entryKey: "cost_estimate",
    title: WorkbenchText.workbench_tool_estimate,
    description: WorkbenchText.workbench_tool_estimate_desc,
    icon: workbenchIconFromKey("calculate"),
    tint: WorkbenchTint.PURPLE,
    target: "", // 费用估算：功能建设中
    badge: "",
    layout: WorkbenchLayout.TILE,
    sectionTitle: "",
    imageUrl: ""
  },
  {
    entryKey: "my_history",
    title: WorkbenchText.workbench_tool_history,
    description: WorkbenchText.workbench_tool_history_desc,
    icon: workbenchIconFromKey("history"),
    tint: WorkbenchTint.PURPLE,
    target: `${DEEPLINK_SCHEME}://divination-history`, // host 由观象台注册（DeepLinkRegistry）
    badge: "",
    layout: WorkbenchLayout.TILE,
    sectionTitle: "",
    imageUrl: ""
  },
  {
    entryKey: "ai_assistant",
    title: WorkbenchText.workbench_tool_ai,
    description: WorkbenchText.workbench_tool_ai_desc,
    icon: workbenchIconFromKey("chat"),
    tint: WorkbenchTint.GREEN,
    // 与安卓差异：安卓 target=smartwash://ai-work（壳层 AiWork 页）；鸿蒙 AI 工具工作台未实现，
    // host 不注册，target 留空走「功能建设中」提示（保持既有行为）
    target: "",
    badge: "",
    layout: WorkbenchLayout.TILE,
    sectionTitle: "",
    imageUrl: ""
  },
  // 与安卓的历史差异说明：安卓工作台此前为 6 项假数据、无短链入口；
  // 本项为鸿蒙侧先行追加的第 7 格，安卓工作台服务端下发版已按 entryKey（toolbox_short_code）对齐。
  {
    entryKey: "toolbox_short_code",
    title: WorkbenchText.workbench_tool_short_code,
    description: WorkbenchText.workbench_tool_short_code_desc,
    icon: workbenchIconFromKey("link"),
    tint: WorkbenchTint.BLUE,
    target: `${DEEPLINK_SCHEME}://toolbox-short-code`, // host 由工具箱注册（DeepLinkRegistry）
    badge: "",
    layout: WorkbenchLayout.TILE,
    sectionTitle: "",
    imageUrl: ""
  }
]

/** 取兜底工具列表副本（避免调用方就地改写内置常量） */
export function getWorkbenchTools(): WorkbenchTool[] {
  return DEFAULT_WORKBENCH_TOOLS.slice()
}
