import { preferences, ValueType } from '@kit.ArkData'

let preferencesStorage: preferences.Preferences | null = null
const preferencesName = "smartWash_pref"

export async function initPreferences(context: Context): Promise<void> {
  if (preferencesStorage !== null) {
    return
  }
  preferencesStorage = preferences.getPreferencesSync(context, { name: preferencesName })
}

/**
 * 读取偏好值。
 * 未初始化（initPreferences 未调用或调用失败）时返回 null，不抛异常——
 * 调用方多处于 Ability/组件早期生命周期，静默兜底优于崩溃。
 */
export async function getValue(key: string,
  defaultValue: preferences.ValueType): Promise<preferences.ValueType | null> {
  const storage = preferencesStorage
  if (storage === null) {
    return null
  }
  return await storage.get(key, defaultValue) ?? null
}

// 类型签名与 getValue 统一（差异分析 01 §4.3）：写入侧同样支持 preferences.ValueType 全类型，
// 消除"读侧声明多类型、写侧仅收 string"的类型面不一致
/**
 * 写入偏好值并 flush。
 * 未初始化时静默跳过（无存储可写），不抛异常；正常路径 put + flush 保证落盘。
 */
export async function setValue(key: string, value: preferences.ValueType): Promise<void> {
  const storage = preferencesStorage
  if (storage === null) {
    return
  }
  await storage.put(key, value)
  await storage.flush()
}
