import { preferences, ValueType } from '@kit.ArkData'

let preferencesStorage: preferences.Preferences | null = null
const preferencesName = "smartWash_pref"

export async function initPreferences(context: Context): Promise<void> {
  if (preferencesStorage !== null) {
    return
  }
  preferencesStorage = preferences.getPreferencesSync(context, { name: preferencesName })
}

export async function getValue(key: string,
  defaultValue: preferences.ValueType): Promise<preferences.ValueType | null> {
  return await preferencesStorage!.get(key, defaultValue) ?? null
}

// 类型签名与 getValue 统一（差异分析 01 §4.3）：写入侧同样支持 preferences.ValueType 全类型，
// 消除"读侧声明多类型、写侧仅收 string"的类型面不一致
export async function setValue(key: string, value: preferences.ValueType): Promise<void> {
  await preferencesStorage!.put(key, value)
  await preferencesStorage?.flush()
}
