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

export async function setValue(key: string, value: string): Promise<void> {
  await preferencesStorage!.put(key, value)
  await preferencesStorage?.flush()
}