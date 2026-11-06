import { BaseResponse } from "../BaseResponse"
import { httpUtil } from "../HttpUtil"
import { AppVersionVo, appVersionVoFromJSON } from "../vo/AppVersionVo"

/**
 * 应用版本接口（对齐安卓 feature/update/network/AppUpdateApi.kt）。
 *
 * GET /web/app/version 为公开接口（见 PublicPaths.EXACT_PUBLIC_PATHS），无需登录即可检查更新；
 * 后端现读现返服务器磁盘 version.json，发版后立即生效、无缓存。
 */

/** 获取最新版本信息（data 为空时由调用方按失败处理） */
async function getAppVersion(): Promise<BaseResponse<AppVersionVo>> {
  const raw: BaseResponse<Object> = await httpUtil.get<Object>("/web/app/version")
  const mapped: BaseResponse<AppVersionVo> = {
    code: raw.code,
    message: raw.message,
    data: appVersionVoFromJSON(raw.data)
  }
  return mapped
}

export { getAppVersion }
