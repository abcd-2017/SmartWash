import { bundleManager } from '@kit.AbilityKit'

// 版本号读取失败时的兜底文案（对齐安卓 SettingPage.kt:80-86 的 PackageManager 兜底 1.0.0）
const FALLBACK_VERSION: string = "1.0.0"

// 版本号读取结果缓存：同步 binder 调用，避免设置页每次 build 重复读取
let cachedVersionName: string | null = null

/**
 * 读取应用版本号（同步）。
 * 设置页「关于我们」改读包管理器，发版后无需改代码即可展示真实版本，
 * 消除硬编码文案随发版漂移的问题（对齐安卓 SettingPage.kt:80-86）。
 */
export function getAppVersionName(): string {
  if (cachedVersionName !== null) {
    return cachedVersionName
  }
  try {
    const bundleInfo = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT)
    const version = bundleInfo.versionName
    cachedVersionName = version !== "" ? version : FALLBACK_VERSION
  } catch (err) {
    // 读取失败按兜底版本展示，不阻塞页面
    cachedVersionName = FALLBACK_VERSION
  }
  return cachedVersionName
}
