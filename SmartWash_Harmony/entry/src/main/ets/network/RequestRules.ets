/**
 * 网络拦截器判定规则（纯函数，无副作用，供 Axios.ets 拦截器与 hypium 单测共用）。
 *
 * 抽离动机：Axios.ets 的拦截器回调含 preferences/UI 副作用，无法在单测中直接驱动；
 * 其判定逻辑（公开路径白名单、token 注入条件、401 判定）是纯函数，抽到本模块后
 * 生产代码与测试共用同一份事实源，避免测试与被测逻辑漂移。
 *
 * 语义对齐：
 * - 公开路径（后端 SecurityConfig permitAll，清单见 PublicPaths）：不注入 token，
 *   也不因本地无 token 而拦截；
 * - 其余 `/web` 前缀接口：必须携带 Bearer token，本地无 token 时拒绝并触发登录跳转；
 * - 业务码 401（HTTP 200 信封内 code=401）与 HTTP 401 同语义：具备续签资格时
 *   静默续签并用新 token 重发原请求一次；不可续签时清 token → 跳登录；
 * - /auth/** 认证端点自身（登录/注册/验证码/刷新/登出）的 401 不触发续签。
 */

import { PublicPaths } from './PublicPaths'

/** 业务码：登录失效（与 HTTP 401 同语义） */
export const UNAUTHORIZED_CODE: number = 401

/** 业务信封成功码（与 HttpUtil.BizCode.SUCCESS 同值；独立定义避免 Axios→HttpUtil 循环 import） */
export const BUSINESS_SUCCESS_CODE: number = 200

/** 认证端点前缀（后端 LoginController @RequestMapping("/auth")：登录/注册/验证码/刷新/登出） */
const AUTH_ENDPOINT_PREFIX: string = "/auth/"

/**
 * 续签重发标记头：请求经「静默续签 + 原请求重发」通道发出时携带。
 * 该请求若再次 401，视为新 token 仍被拒，不再续签，直接走登出收尾（防刷新循环）。
 * 该头会随重发请求上送后端（服务端忽略未知头），仅用于端上通道内判定。
 */
export const RETRY_MARKER_HEADER: string = "X-SmartWash-Retried"

/** 续签重发标记头取值 */
export const RETRY_MARKER_VALUE: string = "1"

/**
 * 请求是否属于 /auth/** 认证端点。
 * 这些端点自身的 401（密码错误、refreshToken 失效等）不得触发静默续签。
 * @param url Axios 请求 URL（相对路径，可能带 query，可能为 undefined）
 */
export function isAuthEndpoint(url: string | undefined): boolean {
  if (url === undefined || url === "") {
    return false
  }
  const queryIndex = url.indexOf("?")
  const path = queryIndex >= 0 ? url.substring(0, queryIndex) : url
  return path.startsWith(AUTH_ENDPOINT_PREFIX)
}

/**
 * 是否命中公开路径白名单。
 * @param url Axios 请求 URL（相对路径，可能带 query，可能为 undefined）
 */
export function isPublicRequest(url: string | undefined): boolean {
  return PublicPaths.isPublic(url)
}

/**
 * 是否需要注入 Bearer token。
 * 判定：非公开路径 且 URL 以 `/web` 开头。
 * @param url Axios 请求 URL（相对路径，可能带 query，可能为 undefined）
 */
export function shouldInjectToken(url: string | undefined): boolean {
  if (isPublicRequest(url)) {
    return false
  }
  return url !== undefined && url.startsWith("/web")
}

/**
 * 从 preferences 缓存值解析 token 字符串。
 * preferences.ValueType 可能是 string/number/boolean 等，仅字符串视为有效 token。
 * @param cached preferences 读取结果（可能为 null）
 */
export function resolveToken(cached: Object | null): string {
  return typeof cached === "string" ? cached : ""
}

/**
 * 业务码是否为 401（登录失效）。
 * @param code 业务信封 code（可能为 null/undefined，如 body 非信封结构）
 */
export function isUnauthorizedCode(code: number | null | undefined): boolean {
  return code === UNAUTHORIZED_CODE
}

/**
 * HTTP 200 信封内的业务码是否属于「非 200 且非 401」的业务失败
 * （如 201/404/500，供响应拦截器 warn 留痕；401 已由 handleUnauthorized 统一处理）。
 * code 缺省（body 非信封结构）不视为业务失败，避免对非信封响应误报——
 * 信封缺省的转译兜底在 HttpUtil.decode，语义以该处为准。
 * @param code 业务信封 code（可能为 null/undefined）
 */
export function isBusinessFailureCode(code: number | null | undefined): boolean {
  return code !== undefined && code !== null &&
    code !== BUSINESS_SUCCESS_CODE && !isUnauthorizedCode(code)
}

/**
 * 构造 Authorization 头值（Bearer 方案）。
 * @param token 已解析的 JWT 字符串（调用方需保证非空）
 */
export function buildAuthorizationHeader(token: string): string {
  return `Bearer ${token}`
}
