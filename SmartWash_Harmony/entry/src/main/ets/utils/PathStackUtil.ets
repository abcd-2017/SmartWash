import { KeyboardAvoidMode, UIContext, uiObserver } from '@kit.ArkUI'
import { logger } from './Logger'

export const pathStack: NavPathStack = new NavPathStack();

// ========== 键盘避让路由守卫（P1 全局污染修复）==========
// 背景：Workbench 是 Home 的常驻 TabContent，挂载时把窗口级 KeyboardAvoidMode 设为 NONE，
// 但 tab 切换不触发 aboutToDisappear，NONE 会永久泄漏，导致后续输入页键盘弹出时窗口不压缩、
// 输入区被整条遮住（对齐安卓 adjustResize 口径，全局默认应为 RESIZE）。
// 这里在路由切面统一收口：任何 NavDestination 切换 / tabContent 显隐，都把窗口级避让复位为
// 默认 RESIZE。窗口级 UIContext 全局唯一，由 EntryAbility 注入。
let keyboardAvoidContext: UIContext | null = null

/** 注入窗口级 UIContext（EntryAbility.setupImmersiveWindow 调用，全局唯一） */
export function initKeyboardAvoidContext(uiContext: UIContext): void {
  keyboardAvoidContext = uiContext
}

/**
 * 复位窗口级键盘避让为默认 RESIZE（与 EntryAbility 口径一致）。
 * 幂等：目标页若需自定义避让，可在自身生命周期内再次覆盖（如 Workbench 搜索聚焦设 NONE）。
 * 失败静默不阻塞导航，仅留 warn 摘要（不序列化 error）。
 */
export function resetKeyboardAvoidMode(): void {
  if (keyboardAvoidContext === null) {
    return
  }
  try {
    keyboardAvoidContext.setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE)
  } catch (err) {
    logger.warn('pathStack', '复位键盘避让模式失败')
  }
}

/**
 * 安装路由切面守卫（EntryAbility 启动时调用一次）：
 * - navDestinationSwitch：任何页面入栈 / 出栈 / 替换后复位 RESIZE；
 * - tabContentUpdate：tab 切换（Workbench 常驻 tab 离开）后复位 RESIZE。
 */
export function installKeyboardAvoidGuard(): void {
  if (keyboardAvoidContext === null) {
    logger.warn('pathStack', '键盘避让守卫未安装：窗口 UIContext 未注入')
    return
  }
  try {
    uiObserver.on('navDestinationSwitch', keyboardAvoidContext, () => {
      resetKeyboardAvoidMode()
    })
    uiObserver.on('tabContentUpdate', () => {
      resetKeyboardAvoidMode()
    })
  } catch (err) {
    logger.warn('pathStack', '键盘避让守卫安装失败')
  }
}

// ========== 安全取参工具（评审 #31）==========
// 背景：裸写 pathStack.getParamByName(name)[0] as X 存在两类崩溃风险——
// 1) 页面栈中不存在同名路由时 getParamByName 返回空数组，直接下标 [0] 取到 undefined；
// 2) 参数缺失/类型不符时 as 强转得不到保护，下游访问属性崩溃。
// 统一经以下工具取参：缺失/非法一律返回 null 或默认值，不崩溃。

/**
 * 按路由名安全读取 number 类型参数。
 * 页面入栈时可能传 number 或数字字符串，统一经 Number() 显式转换；NaN 视为参数缺失/非法。
 *
 * @param name 路由页面名（router_map 中注册的 name）
 * @returns 参数值；同名路由不存在、参数缺失或非法时返回 null
 */
export function getNumberParam(name: string): number | null {
  // SDK 将 getParamByName 返回类型声明为 Array<unknown>，而 ArkTS 禁用 unknown 类型，
  // 此处 as 仅做 SDK 类型桥接（收敛为 Object 数组），参数值本身仍经 Number() 显式转换校验
  const params = pathStack.getParamByName(name) as Array<Object>
  if (params.length === 0) {
    return null
  }
  // 取栈内最末（栈顶）同名路由参数：栈内存在 2+ 同名路由时 [0] 会读到最旧那条，
  // 导致新页面拿到旧参数（实测深链推 Home 时卦盘页读到旧 recordId）
  const value = Number(params[params.length - 1])
  return Number.isNaN(value) ? null : value
}

/**
 * 按路由名安全读取 string 类型参数（如路由 query 参数 method/category）。
 * 仅在运行时 typeof 校验确认其为字符串后才收窄类型；空串视为合法值（调用方自行兜底默认值）。
 *
 * @param name 路由页面名（router_map 中注册的 name）
 * @returns 参数值；同名路由不存在、参数缺失或非字符串时返回 null
 */
export function getStringParam(name: string): string | null {
  // 同 getNumberParam：SDK 返回 Array<unknown>，此处仅做类型桥接，见上注释
  const params = pathStack.getParamByName(name) as Array<Object>
  if (params.length === 0) {
    return null
  }
  const value: Object | null = params[params.length - 1]
  if (typeof value === "string") {
    return value
  }
  return null
}

/**
 * 按路由名安全读取 boolean 类型参数（如 DivChart 的 animate 开关）。
 * 页面入栈时可能传 boolean 或字符串 "true"/"false"，统一显式转换；其余值视为缺失/非法。
 *
 * @param name 路由页面名（router_map 中注册的 name）
 * @returns 参数值；同名路由不存在、参数缺失或非法时返回 null
 */
export function getBooleanParam(name: string): boolean | null {
  // 同 getNumberParam：SDK 返回 Array<unknown>，此处仅做类型桥接，见上注释
  const params = pathStack.getParamByName(name) as Array<Object>
  if (params.length === 0) {
    return null
  }
  const value: Object | null = params[params.length - 1]
  if (typeof value === 'boolean') {
    return value
  }
  if (value === 'true') {
    return true
  }
  if (value === 'false') {
    return false
  }
  return null
}

/**
 * 按路由名安全读取对象类型参数（如 VO）。
 * 仅在运行时 typeof 校验确认其为对象后才收窄类型，避免对缺失参数做裸 as 强转（评审 #31）。
 *
 * @param name 路由页面名（router_map 中注册的 name）
 * @returns 参数对象；同名路由不存在、参数缺失或非对象时返回 null
 */
export function getObjectParam<T extends Object>(name: string): T | null {
  // 同 getNumberParam：SDK 返回 Array<unknown>，此处仅做类型桥接，见上注释
  const params = pathStack.getParamByName(name) as Array<Object>
  if (params.length === 0) {
    return null
  }
  const value: Object | null = params[params.length - 1]
  if (value !== null && typeof value === "object") {
    return value as T
  }
  return null
}
