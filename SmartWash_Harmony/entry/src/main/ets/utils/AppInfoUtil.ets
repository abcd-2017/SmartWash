import { bundleManager } from '@kit.AbilityKit'

// 版本号读取失败时的兜底文案（对齐安卓 SettingPage.kt:80-86 的 PackageManager 兜底 1.0.0）
const FALLBACK_VERSION: string = "1.0.0"

// 版本号读取结果缓存：同步 binder 调用，避免设置页每次 build 重复读取
let cachedVersionName: string | null = null
let cachedVersionCode: number | null = null

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

/**
 * 读取应用版本号整数（同步，对齐安卓 AppUpdateRepository.getCurrentVersionCode）。
 * 用于「检查更新」与服务端 versionCode 比较新旧。
 * 读取失败兜底为最大安全整数：与安卓 Int.MAX_VALUE 同语义——系统异常时不误报新版本、不打扰用户。
 */
export function getAppVersionCode(): number {
  if (cachedVersionCode !== null) {
    return cachedVersionCode
  }
  try {
    const bundleInfo = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT)
    cachedVersionCode = bundleInfo.versionCode
  } catch (err) {
    // 读取失败兜底最大值：远程 versionCode 不可能更大，恒判定「已是最新」
    cachedVersionCode = Number.MAX_SAFE_INTEGER
  }
  return cachedVersionCode
}
