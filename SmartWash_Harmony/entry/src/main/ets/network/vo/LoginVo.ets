/**
 * 登录/注册响应数据对象（interface + 显式映射，不依赖 class 直接接 JSON）。
 *
 * 后端破坏性变更适配（评审后端 #1 + 双 token 契约）：登录与注册接口响应信封 data
 * 统一为对象 { token, refreshToken, role }（注册不再返回 token 字符串）：
 * - token：访问令牌（typ=access，2 小时有效期），持久化后由请求拦截器注入 Authorization；
 * - refreshToken：刷新令牌（typ=refresh，7 天有效期），随 token 一起落盘，供 /auth/refresh 静默续期；
 * - role：登录者角色标识（普通用户固定 "user"），存入 AppStorage 备用。
 */
import { jsonStr } from "./JsonParse"

export interface LoginVo {
  token: string | null
  refreshToken: string | null
  role: string | null
}

/**
 * JSON → LoginVo 显式映射。
 * 字段缺失（Jackson 省略 null）或类型漂移一律兜底 null，与原 class 默认值语义一致。
 */
export function loginVoFromJSON(raw: Object | null | undefined): LoginVo {
  if (raw === null || raw === undefined) {
    return { token: null, refreshToken: null, role: null }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    token: jsonStr(obj['token']),
    refreshToken: jsonStr(obj['refreshToken']),
    role: jsonStr(obj['role'])
  }
}
