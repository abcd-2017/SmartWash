/**
 * 登录响应数据对象（interface + 显式映射，不依赖 class 直接接 JSON）。
 *
 * 后端破坏性变更适配（评审后端 #1）：登录接口（管理员/用户）响应信封 data
 * 由 token 字符串改为对象 { token, role }：
 * - token：JWT 令牌（7 天有效期），持久化后由请求拦截器注入 Authorization；
 * - role：登录者角色标识（普通用户固定 "user"），存入 AppStorage 备用。
 */
import { jsonStr } from "./JsonParse"

export interface LoginVo {
  token: string | null
  role: string | null
}

/**
 * JSON → LoginVo 显式映射。
 * 字段缺失（Jackson 省略 null）或类型漂移一律兜底 null，与原 class 默认值语义一致。
 */
export function loginVoFromJSON(raw: Object | null | undefined): LoginVo {
  if (raw === null || raw === undefined) {
    return { token: null, role: null }
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  return {
    token: jsonStr(obj['token']),
    role: jsonStr(obj['role'])
  }
}
