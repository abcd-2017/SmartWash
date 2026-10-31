// 工作台入口配置 + 深链路由字典接口（对齐安卓 app/network/api/{WorkbenchEntryApi,DeepLinkApi}.kt）
//
// 后端契约（SmartWash/src/main/java/com/smartwash/workbench/）：
// - GET /web/auth/workbench/entries  需 ROLE_USER，入口列表按 sort 升序、id 升序，不分页；
// - GET /web/deeplink/routes          公开（SecurityConfig permitAll），仅启用中的路由，按 id 升序。
// 严格模式：VO 一律 interface + 可选字段（Jackson 可能省略 null 字段），消费侧经转换函数兜底。

import { BaseResponse } from '../BaseResponse'
import { httpUtil } from '../HttpUtil'

/**
 * 工作台入口 VO（对齐后端 WorkbenchEntryVo / 安卓 WorkbenchEntryVo，字段名逐一勿改）。
 * icon/color 为端上白名单枚举 key，未知值显示默认样式（default 图标 + green 色组）。
 */
export interface WorkbenchEntryVo {
  /** 业务唯一标识，端上兜底列表对齐用（兼作列表稳定 key） */
  entryKey?: string
  /** 入口标题 */
  title?: string
  /** 副标题描述 */
  description?: string
  /** 图标 key（白名单：calendar/search/book/calculate/history/chat/link/default） */
  icon?: string
  /** 图标色组 key（白名单：green/blue/orange/pink/teal/purple） */
  color?: string
  /** 跳转 URI：smartwash://... 或 https://...；空 = 功能建设中 */
  target?: string | null
  /** 角标文案（如 新/热），空则不显示 */
  badge?: string | null
  /** 排序，越小越靠前（后端已排好，端上不再排序） */
  sort?: number
}

/**
 * 深链路由字典 VO（对齐后端 DeeplinkRouteVo / 安卓 DeeplinkRouteVo）。
 * 字典仅做未知 host 的元数据兜底与预热，不参与路由构造。
 */
export interface DeeplinkRouteVo {
  id?: number
  /** smartwash:// 的 host，如 login */
  host?: string
  /** 页面名称（管理端展示） */
  name?: string
  /** 1 Android 2 HarmonyOS 3 双端 */
  platform?: number
  /** 1 需要登录 0 不需要 */
  requireLogin?: number
  /** 说明（如 web/t 的 scheme 用法） */
  description?: string
  /** 1 启用 0 停用 */
  enabled?: number
}

/** 字典枚举值（对齐安卓 DeeplinkRouteDict；鸿蒙端消费 platform 2/3 与 enabled） */
export class DeeplinkRouteDict {
  /** enabled：1 启用 0 停用 */
  static readonly ENABLED: number = 1
  /** platform：1 Android */
  static readonly PLATFORM_ANDROID: number = 1
  /** platform：2 HarmonyOS */
  static readonly PLATFORM_HARMONY: number = 2
  /** platform：3 双端 */
  static readonly PLATFORM_BOTH: number = 3
}

/**
 * 工作台入口列表（需登录；请求拦截器按 /web 前缀注入 Bearer Token）。
 */
export async function getWorkbenchEntries(): Promise<BaseResponse<Array<WorkbenchEntryVo>>> {
  return httpUtil.get<Array<WorkbenchEntryVo>>('/web/auth/workbench/entries')
}

/**
 * 深链路由字典（公开接口）。
 * 注意：鸿蒙请求拦截器按 URL 前缀 /web 统一注入 token，且本地无 token 时直接拒绝请求并触发
 * 「请先登录」跳转副作用——调用方（DeepLinkHandler）必须在已登录前提下才拉取本接口，
 * 未登录场景本地解析结果已足够（未注册 host 统一「暂不支持该链接」）。
 */
export async function getDeeplinkRoutes(): Promise<BaseResponse<Array<DeeplinkRouteVo>>> {
  return httpUtil.get<Array<DeeplinkRouteVo>>('/web/deeplink/routes')
}
