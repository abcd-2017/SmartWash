// 工作台域契约 — 工具条目模型 / 白名单映射 / 服务端下发转换 / 内置兜底数据 / 文案表
//
// 对齐安卓 ui/page/workbench/{WorkbenchTool.kt,WorkbenchViewModel.kt}（服务端入口下发版）：
// 数据源二选一——服务端下发（GET /web/auth/workbench/entries，经 network/api/workbench.ets）
// 或本地兜底 DEFAULT_WORKBENCH_TOOLS；点击统一按 target 走深链分发（DeepLinkHandler.handleDeepLink）。
// 按 feature/README.md 约定：本文件只放声明层（VO 映射 + 枚举 + 内置数据 + 文案），
// UI 组件与点击分发实现放域内 Workbench.ets。

import { WorkbenchEntryVo } from '../../network/api/workbench'
import { DEEPLINK_SCHEME } from '../../utils/deeplink/DeepLinkParser'

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
 * 后端 icon 白名单 key → sys.symbol 图标映射（规范 §4.1：功能图标保持系统符号库）。
 * 白名单：calendar/search/book/calculate/history/chat/link/default，未知 key 与 default
 * 同样兜底为「工具格」图标（对齐安卓 Icons.Default.Apps）。
 * 符号名已对照 SDK sysResource.js 核实存在（除 default 外均沿用既有工作台在用符号）。
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
    badge: vo.badge ?? ''
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
      badge: tool.badge
    }
  })
}

/**
 * 工作台文案表 — 键名逐一对齐安卓 app/src/main/res/values/strings.xml 的 workbench_* 资源，
 * 便于跨端文案核对（安卓 workbench_title / workbench_subtitle / workbench_divination_name ...）。
 */
export class WorkbenchText {
  static readonly workbench_title: string = "工作台"
  static readonly workbench_subtitle: string = "小工具，慢慢加"
  static readonly workbench_divination_name: string = "观象台"
  static readonly workbench_divination_subtitle: string = "六爻 · 梅花 · 奇门 · 大六壬"
  static readonly workbench_all_tools: string = "全部工具"
  static readonly workbench_tool_calendar: string = "洗衣黄历"
  static readonly workbench_tool_calendar_desc: string = "今天宜洗什么颜色"
  static readonly workbench_tool_stain: string = "渍迹识别"
  static readonly workbench_tool_stain_desc: string = "拍照认污渍类型"
  static readonly workbench_tool_fabric: string = "面料百科"
  static readonly workbench_tool_fabric_desc: string = "什么面料怎么洗"
  static readonly workbench_tool_estimate: string = "费用估算"
  static readonly workbench_tool_estimate_desc: string = "洗之前先算个账"
  static readonly workbench_tool_history: string = "我的卦历"
  static readonly workbench_tool_history_desc: string = "起过的卦都在这里"
  static readonly workbench_tool_ai: string = "AI 助手"
  static readonly workbench_tool_ai_desc: string = "洗护问题随口问"
  static readonly workbench_tool_short_code: string = "短链管理"
  static readonly workbench_tool_short_code_desc: string = "生成并管理跳转短链"
  static readonly workbench_footer: string = "工具会陆续加进来"
  static readonly workbench_coming_soon: string = "功能建设中，敬请期待"
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
    badge: ""
  },
  {
    entryKey: "stain_recognition",
    title: WorkbenchText.workbench_tool_stain,
    description: WorkbenchText.workbench_tool_stain_desc,
    icon: workbenchIconFromKey("search"),
    tint: WorkbenchTint.BLUE,
    target: "", // 渍迹识别：功能建设中
    badge: ""
  },
  {
    entryKey: "fabric_encyclopedia",
    title: WorkbenchText.workbench_tool_fabric,
    description: WorkbenchText.workbench_tool_fabric_desc,
    icon: workbenchIconFromKey("book"),
    tint: WorkbenchTint.TEAL,
    target: "", // 面料百科：功能建设中
    badge: ""
  },
  {
    entryKey: "cost_estimate",
    title: WorkbenchText.workbench_tool_estimate,
    description: WorkbenchText.workbench_tool_estimate_desc,
    icon: workbenchIconFromKey("calculate"),
    tint: WorkbenchTint.PURPLE,
    target: "", // 费用估算：功能建设中
    badge: ""
  },
  {
    entryKey: "my_history",
    title: WorkbenchText.workbench_tool_history,
    description: WorkbenchText.workbench_tool_history_desc,
    icon: workbenchIconFromKey("history"),
    tint: WorkbenchTint.PURPLE,
    target: `${DEEPLINK_SCHEME}://divination-history`, // host 由观象台注册（DeepLinkRegistry）
    badge: ""
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
    badge: ""
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
    badge: ""
  }
]

/** 取兜底工具列表副本（避免调用方就地改写内置常量） */
export function getWorkbenchTools(): WorkbenchTool[] {
  return DEFAULT_WORKBENCH_TOOLS.slice()
}
