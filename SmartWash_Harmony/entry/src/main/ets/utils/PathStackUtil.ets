export const pathStack: NavPathStack = new NavPathStack();

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
  const value = Number(params[0])
  return Number.isNaN(value) ? null : value
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
  const value: Object | null = params[0]
  if (value !== null && typeof value === "object") {
    return value as T
  }
  return null
}
