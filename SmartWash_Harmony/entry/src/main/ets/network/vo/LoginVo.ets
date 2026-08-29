/**
 * 登录响应数据对象（interface + 手动取值，不依赖 class 直接接 JSON）。
 *
 * 后端破坏性变更适配（评审后端 #1）：登录接口（管理员/用户）响应信封 data
 * 由 token 字符串改为对象 { token, role }：
 * - token：JWT 令牌（7 天有效期），持久化后由请求拦截器注入 Authorization；
 * - role：登录者角色标识（普通用户固定 "user"），存入 AppStorage 备用。
 */
export interface LoginVo {
  token: string | null
  role: string | null
}
