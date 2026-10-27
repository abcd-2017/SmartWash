// 工作台域契约 — 工具条目模型 / 点击行为枚举 / 内置默认数据 / 文案表
//
// 依据差异分析 04-new-features.md §3.3（对齐安卓 ui/page/workbench/{WorkbenchTool.kt,WorkbenchViewModel.kt}）。
// 按 feature/README.md 约定：本文件只放声明层（VO interface + 枚举 + 内置数据），
// UI 组件与点击分流实现放域内 Workbench.ets。
//
// 数据源说明：安卓当前为内置假数据（WorkbenchViewModel.defaultTools()），后端工作台接口就绪后
// 改为服务端下发顺序/文案/启停；本域 DEFAULT_WORKBENCH_TOOLS 即接口 VO 的客户端映射层，
// 换数据源时页面零改动。

/** 工具卡图标容器语义色组（规范 §2.1 六色浅底，与安卓 WorkbenchTint 的 GREEN/BLUE/TEAL/PURPLE 对应） */
export enum WorkbenchTint {
  GREEN,
  BLUE,
  TEAL,
  PURPLE
}

/**
 * 工具点击行为（对齐安卓 WorkbenchAction 四枚举）。
 * 前三个为已落地的真实页面，其余在接口就绪前占位。
 */
export enum WorkbenchAction {
  /** 观象台首页（玄墨特色卡与「洗衣黄历」无关，观象台专属入口） */
  DIVINATION_HOME,
  /** 我的卦历 → 观象台卦历库 */
  DIV_HISTORY,
  /** AI 助手 → AI 工具工作台 */
  AI_ASSISTANT,
  /**
   * 工具箱短链 → 短链管理（#28 新增）。
   * 与安卓差异：安卓工作台当前为 6 项假数据且无短链格（ToolboxRoute.kt:7-8 注释「当前无任何入口」），
   * 两端接入点都是工作台；鸿蒙先行追加第 7 项，待安卓工作台接口/入口落地后按 key 对齐。
   */
  TOOLBOX_SHORT_CODE,
  /** 功能建设中（等后端工作台接口） */
  COMING_SOON
}

/**
 * 工作台工具条目（对齐安卓 WorkbenchTool.kt:35-42）。
 * 当前为内置假数据；后端工作台接口就绪后 title/description 改服务端下发、
 * 跳转目标按 key 映射，本结构即接口 VO 的客户端映射层。
 */
export interface WorkbenchTool {
  /** 工具唯一键，后端接口就绪后按 key 映射跳转目标 */
  key: string
  /** 标题文案（取自 WorkbenchText，键对齐安卓 workbench_tool_* 资源名） */
  title: string
  /** 描述文案（取自 WorkbenchText，键对齐安卓 workbench_tool_*_desc 资源名） */
  description: string
  /** 语义图标 */
  icon: Resource
  /** 图标容器语义色组 */
  tint: WorkbenchTint
  /** 点击行为 */
  action: WorkbenchAction
}

/**
 * 工作台文案表 — 键名逐一对齐安卓 app/src/main/res/values/strings.xml 的 workbench_* 资源，
 * 便于跨端文案核对（安卓 workbench_title / workbench_subtitle / workbench_divination_name ...）。
 */
export const WorkbenchText = {
  workbench_title: "工作台",
  workbench_subtitle: "小工具，慢慢加",
  workbench_divination_name: "观象台",
  workbench_divination_subtitle: "六爻 · 梅花 · 奇门 · 大六壬",
  workbench_all_tools: "全部工具",
  workbench_tool_calendar: "洗衣黄历",
  workbench_tool_calendar_desc: "今天宜洗什么颜色",
  workbench_tool_stain: "渍迹识别",
  workbench_tool_stain_desc: "拍照认污渍类型",
  workbench_tool_fabric: "面料百科",
  workbench_tool_fabric_desc: "什么面料怎么洗",
  workbench_tool_estimate: "费用估算",
  workbench_tool_estimate_desc: "洗之前先算个账",
  workbench_tool_history: "我的卦历",
  workbench_tool_history_desc: "起过的卦都在这里",
  workbench_tool_ai: "AI 助手",
  workbench_tool_ai_desc: "洗护问题随口问",
  workbench_tool_short_code: "短链管理",
  workbench_tool_short_code_desc: "生成并管理跳转短链",
  workbench_footer: "工具会陆续加进来",
  workbench_coming_soon: "功能建设中，敬请期待"
}

// TODO(工作台接口): 接口就绪后移除内置列表，改为请求 /web 工作台配置（顺序、文案、启停由服务端下发）
/** 内置默认工具列表（设计稿屏 15 定稿六项，对齐安卓 WorkbenchViewModel.defaultTools()） */
export const DEFAULT_WORKBENCH_TOOLS: WorkbenchTool[] = [
  {
    key: "laundry_calendar",
    title: WorkbenchText.workbench_tool_calendar,
    description: WorkbenchText.workbench_tool_calendar_desc,
    icon: $r('sys.symbol.calendar'),
    tint: WorkbenchTint.GREEN,
    action: WorkbenchAction.COMING_SOON
  },
  {
    key: "stain_recognition",
    title: WorkbenchText.workbench_tool_stain,
    description: WorkbenchText.workbench_tool_stain_desc,
    icon: $r('sys.symbol.magnifyingglass'),
    tint: WorkbenchTint.BLUE,
    action: WorkbenchAction.COMING_SOON
  },
  {
    key: "fabric_encyclopedia",
    title: WorkbenchText.workbench_tool_fabric,
    description: WorkbenchText.workbench_tool_fabric_desc,
    icon: $r('sys.symbol.book_pages'),
    tint: WorkbenchTint.TEAL,
    action: WorkbenchAction.COMING_SOON
  },
  {
    key: "cost_estimate",
    title: WorkbenchText.workbench_tool_estimate,
    description: WorkbenchText.workbench_tool_estimate_desc,
    icon: $r('sys.symbol.calculator'),
    tint: WorkbenchTint.PURPLE,
    action: WorkbenchAction.COMING_SOON
  },
  {
    key: "my_history",
    title: WorkbenchText.workbench_tool_history,
    description: WorkbenchText.workbench_tool_history_desc,
    icon: $r('sys.symbol.clock_arrow_2_circlepath'),
    tint: WorkbenchTint.PURPLE,
    action: WorkbenchAction.DIV_HISTORY
  },
  {
    key: "ai_assistant",
    title: WorkbenchText.workbench_tool_ai,
    description: WorkbenchText.workbench_tool_ai_desc,
    icon: $r('sys.symbol.ellipsis_message'),
    tint: WorkbenchTint.GREEN,
    action: WorkbenchAction.AI_ASSISTANT
  },
  // 与安卓的差异说明：安卓工作台为 6 项假数据、无短链入口（ToolboxRoute.kt:7-8）；
  // 本项为鸿蒙侧新增的第 7 格，待安卓工作台落地后两端按 key（toolbox_short_code）对齐。
  {
    key: "toolbox_short_code",
    title: WorkbenchText.workbench_tool_short_code,
    description: WorkbenchText.workbench_tool_short_code_desc,
    icon: $r('sys.symbol.link'),
    tint: WorkbenchTint.BLUE,
    action: WorkbenchAction.TOOLBOX_SHORT_CODE
  }
]

/** 取工具列表副本（避免调用方就地改写内置常量） */
export function getWorkbenchTools(): WorkbenchTool[] {
  return DEFAULT_WORKBENCH_TOOLS.slice()
}
