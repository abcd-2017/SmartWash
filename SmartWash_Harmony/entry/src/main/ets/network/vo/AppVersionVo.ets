/**
 * 应用版本信息 VO（interface + 显式映射）。
 *
 * 事实源：后端 SmartWash/src/main/java/com/smartwash/vo/AppVersionVo.java
 * （GET /web/app/version 现读现返服务器磁盘 version.json，无缓存）。
 * 字段与安卓 feature/update/model/AppVersionVo.kt 逐字段一致。
 */
import { jsonStr, jsonNum, jsonBool } from "./JsonParse"

export interface AppVersionVo {
  /** 版本号（整数，用于比较新旧，如 10002） */
  versionCode: number | null
  /** 版本名称（展示用，如 "1.0.2"） */
  versionName: string | null
  /** APK 下载地址 */
  apkUrl: string | null
  /** MinIO 对象名（如 app-1.0.2.apk） */
  fileName: string | null
  /** APK 文件大小（字节） */
  fileSize: number | null
  /** APK 文件的 SHA-256 校验值 */
  sha256: string | null
  /** 是否强制更新 */
  forceUpdate: boolean | null
  /** 强制更新的最低版本号，低于此值必须升级 */
  minVersionCode: number | null
  /** 更新日志 */
  changelog: string | null
  /** 发布日期（格式 yyyy-MM-dd） */
  releaseDate: string | null
}

/**
 * JSON → AppVersionVo 显式映射。
 * 字段缺失（Jackson 省略 null）或类型漂移一律兜底 null。
 */
export function appVersionVoFromJSON(raw: Object | null | undefined): AppVersionVo {
  if (raw === null || raw === undefined) {
    return {
      versionCode: null,
      versionName: null,
      apkUrl: null,
      fileName: null,
      fileSize: null,
      sha256: null,
      forceUpdate: null,
      minVersionCode: null,
      changelog: null,
      releaseDate: null
    }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    versionCode: jsonNum(obj['versionCode']),
    versionName: jsonStr(obj['versionName']),
    apkUrl: jsonStr(obj['apkUrl']),
    fileName: jsonStr(obj['fileName']),
    fileSize: jsonNum(obj['fileSize']),
    sha256: jsonStr(obj['sha256']),
    forceUpdate: jsonBool(obj['forceUpdate']),
    minVersionCode: jsonNum(obj['minVersionCode']),
    changelog: jsonStr(obj['changelog']),
    releaseDate: jsonStr(obj['releaseDate'])
  }
}
