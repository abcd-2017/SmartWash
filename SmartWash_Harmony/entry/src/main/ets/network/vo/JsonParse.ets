/**
 * JSON 字段安全读取辅助（interface + 显式映射模式的基础工具）。
 *
 * 背景：ArkTS 严格模式下 interface 只是类型声明，运行时不做任何字段填充；
 * axios 反序列化出的 JSON 对象字段可能缺失（Jackson 省略 null 字段）或类型漂移，
 * 消费点直接读 `obj.field` 会拿到 undefined 而类型系统认为存在。
 * 故 VO 一律 interface + fromJSON 显式映射，字段读取统一经本工具的取值函数兜底。
 *
 * 设计约束（与 PathStackUtil 既有风格一致）：
 * - 入参一律 Object | null | undefined，先判空再取值；
 * - 缺失/为 null 一律返回 null，与原 class VO 字段默认值 `= null` 语义一致，
 *   页面 `xx?.xx` 空安全链不受影响；
 * - 只做「存在性」收窄，不做数值/字符串类型强转（后端类型漂移属另一问题域）。
 */

/** 读取字符串字段：仅当值确为 string 时返回，否则 null */
export function jsonStr(value: Object | null | undefined): string | null {
  if (value === null || value === undefined) {
    return null
  }
  return typeof value === 'string' ? value : null
}

/** 读取数字字段：仅当值确为 number 时返回，否则 null */
export function jsonNum(value: Object | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null
  }
  return typeof value === 'number' ? value : null
}

/** 读取布尔字段：仅当值确为 boolean 时返回，否则 null */
export function jsonBool(value: Object | null | undefined): boolean | null {
  if (value === null || value === undefined) {
    return null
  }
  return typeof value === 'boolean' ? value : null
}

/** 读取对象字段：仅当值确为非 null 对象时返回，否则 null */
export function jsonObj(value: Object | null | undefined): Object | null {
  if (value === null || value === undefined) {
    return null
  }
  return typeof value === 'object' ? value : null
}

/**
 * 数组逐项映射：缺失/非数组兜底空数组，null/非对象项跳过。
 * @param raw 原始数组字段（Object | null | undefined）
 * @param mapper 单项映射函数（入参为非 null 对象）
 */
export function mapArray<T>(raw: Object | null | undefined,
  mapper: (item: Object) => T): Array<T> {
  if (raw === null || raw === undefined || !Array.isArray(raw)) {
    return []
  }
  // 显式索引循环（不用 for-of）：Array.isArray 收窄后元素为 any，触发 arkts-no-any-unknown
  const arr: Array<Object | null> = raw as Array<Object | null>
  const result: Array<T> = []
  for (let i = 0; i < arr.length; i++) {
    const item: Object | null = arr[i]
    if (item !== null && typeof item === 'object') {
      result.push(mapper(item))
    }
  }
  return result
}

/**
 * 字符串数组安全读取：仅当元素确为字符串时收进结果，其余跳过（空串保留，清洗由调用方裁决）。
 * mapArray 只放行 object 元素，对 List<String> 载荷（如 dining_recipe.ingredients）会把
 * 整组字符串项全部跳过——字符串数组必须走本函数。
 */
export function jsonStrArray(raw: Object | null | undefined): Array<string> {
  if (raw === null || raw === undefined || !Array.isArray(raw)) {
    return []
  }
  const arr: Array<Object | null> = raw as Array<Object | null>
  const result: Array<string> = []
  for (let i = 0; i < arr.length; i++) {
    const item: Object | null = arr[i]
    if (typeof item === 'string') {
      result.push(item)
    }
  }
  return result
}
