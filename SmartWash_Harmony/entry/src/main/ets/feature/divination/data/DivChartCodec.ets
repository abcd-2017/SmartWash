/**
 * 四术卦盘 JSON 编解码 —— 对应安卓 data/DivChartCodec.kt（Gson 反射序列化）。
 *
 * 安卓把 chart 对象交给 Gson 反射写进 DivChartBundle 的 chartJson 字段；ArkTS 无 Gson，
 * 且 QiMenChart / LiuRenChart / LiuYaoChart.hidden 内部含 Map，直接 JSON.stringify 会把
 * Map 序列化成 {}（字段丢失），复盘页读库后将无法还原九宫 / 天地盘。故统一经本文件的
 * Map 感知序列化：Map 编码为 `{ __map: [[k, v], ...] }`，解码时还原为 Map。
 *
 * 红线（与 DivinationStore 文件头一致）：排盘结果只在「起卦瞬间」计算一次并落库；
 * 卦盘 / 解读 / 追问 / 复盘一律读库解码，禁止重算（当前时刻已变，重算会漂移）。
 *
 * 说明：chartJson 的 schema 即各 chart 类的自有字段快照（类实例经 JSON.stringify 序列化
 * 自有可枚举属性），四术共用本编解码，新增字段无需改动本文件。
 */

/** Map → `{ __map: [[k, v], ...] }` 的标记键 */
const MAP_KEY = '__map'

/**
 * 编码卦盘为 JSON 字符串（Map 感知）。
 * @param chart 任一术数 chart 实例（LiuYaoChart / MeiHuaChart / QiMenChart / LiuRenChart）
 */
export function encodeChartJson(chart: Object): string {
  return JSON.stringify(chart, (key: string, value: Object): Object => {
    if (value instanceof Map) {
      const mapValue: Map<Object, Object> = value
      const entries: Array<[Object, Object]> = []
      mapValue.forEach((v: Object, k: Object): void => {
        entries.push([k, v])
      })
      const wrapped: Record<string, Object> = {}
      wrapped[MAP_KEY] = entries
      return wrapped
    }
    return value
  })
}

/**
 * 解码卦盘 JSON 字符串，还原 Map 字段（供卦盘页渲染九宫 / 天地盘 / 伏神）。
 * @returns 还原后的对象（Map 字段已还原为 Map，其余为普通对象 / 数组 / 原始值）
 */
export function decodeChartJson(json: string): Object {
  return reviveValue(JSON.parse(json))
}

/** 递归还原：数组逐项还原；对象中的 `__map` 还原为 Map，其余对象逐字段还原 */
function reviveValue(value: Object): Object {
  if (Array.isArray(value)) {
    return value.map((item: Object): Object => reviveValue(item))
  }
  if (value !== null && typeof value === 'object') {
    const raw = value as Record<string, Object>
    const mapEntries = raw[MAP_KEY]
    if (mapEntries !== undefined) {
      const entries = mapEntries as Array<[Object, Object]>
      const map = new Map<Object, Object>()
      for (let i = 0; i < entries.length; i++) {
        map.set(entries[i][0], reviveValue(entries[i][1]))
      }
      return map
    }
    const out: Record<string, Object> = {}
    Object.keys(raw).forEach((k: string): void => {
      out[k] = reviveValue(raw[k])
    })
    return out
  }
  return value
}
