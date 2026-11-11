// 深链处理器 — 解析 + 导航分发（对齐安卓 app 壳层 deeplink/DeepLinkHandler.kt + DeepLinkRepository.kt）
//
// handle(uri) 统一入口：
// 1. parse（web 内建分支 + 注册表）→ ROUTE：pushPathByName(路由名, 参数对象)；
// 2. WEB：push WebPage（url/title 经 WebPageParam 携带）；
// 3. UNKNOWN：smartwash:// 未命中时查后端路由字典做合法性留痕（内存缓存、失败静默、
//    仅诊断与预热，不改变导航语义），随后统一 toast「暂不支持该链接」。
// 全程不抛异常（内部兜底），调用方无需 try-catch。

import { CommonConstants } from '../../constant/CommonConstants'
import { RouteNames } from '../../constant/RouteNames'
import { DeeplinkRouteDict, DeeplinkRouteVo, getDeeplinkRoutes } from '../../network/api/workbench'
import { BizCode } from '../../network/HttpUtil'
import { logger } from '../Logger'
import { pathStack } from '../PathStackUtil'
import { getValue } from '../StorageUtil'
import { showToastSafety } from '../ToastUtil'
import { WebPageParam } from '../../pages/WebPage'
import { clearLoginState } from '../../network/Axios'
import { DeepLinkKind, DeepLinkResult, parse } from './DeepLinkParser'
import { lookup } from './DeepLinkRegistry'
import { resString } from '../../utils/ResourceString'

/**
 * 深链 toast 文案（键名对齐安卓 strings.xml 的 deeplink_unsupported）。
 */
export class DeepLinkText {
  static get deeplink_unsupported(): string { return resString('deeplink_unsupported') }
}

/**
 * 字典内存缓存：null = 尚未拉取或历史失败（可重试），非 null = 已成功拉取（可能为空字典）。
 * 失败不缓存失败结果（对齐安卓 DeepLinkRepository：下次调用自动重试）。
 */
let routeDictCache: Array<DeeplinkRouteVo> | null = null

/**
 * 公开深链 host 白名单（未登录可放行，对齐安卓 PUBLIC_DEEP_LINK_HOSTS，P-2001）：
 * - web：内置浏览器分支，WebView 页不消费应用鉴权接口；
 * - login / register：认证页自身（拦截即循环跳登录）；
 * - lab：解压实验舱，纯本地玩具零网络请求。
 * 其余 host 对应页面入口均消费 /web/auth 鉴权接口或依赖会话语义，未登录不放行。
 */
const PUBLIC_DEEP_LINK_HOSTS: string[] = ['web', 'login', 'register', 'lab']

/**
 * 深链动作是否指向公开页面（未登录可放行，P-2001）。
 * WEB（http/https 与 smartwash://web）与 UNKNOWN（仅 toast 不导航）一律视为公开；
 * ROUTE 仅 host ∈ 白名单时公开。
 */
function isPublicAction(result: DeepLinkResult): boolean {
  if (result.kind !== DeepLinkKind.ROUTE) {
    return true
  }
  return PUBLIC_DEEP_LINK_HOSTS.indexOf(result.host) >= 0
}

/**
 * 供登出态消费端（Index）判定是否可立即分发：公开深链清暂存后立即导航，
 * 受保护深链保留暂存（pendingDeepLinkUri），待登录后 Home 挂载补消费。
 */
export function isPublicDeepLink(uri: string): boolean {
  return isPublicAction(parse(uri, lookup))
}

/** 本地会话态（对齐安卓 UserApi.hasSession 的挂起读口径；AppStorage 由 Index 维护） */
function hasSession(): boolean {
  return AppStorage.get<boolean>('isLoggedIn') === true
}

/**
 * 深链统一入口。冷启动/热启动的 Want.uri 经 AppStorage 暂存后由 Home 页挂载时调起，
 * 登出态由 Index 提前消费（见 Index.consumeLoggedOutDeepLink）。
 *
 * 登录守卫（P-2001，对齐安卓）：未登录 + 受保护 host → 落登录页并保留 deep link 待登录后消费
 * （不清 pendingDeepLinkUri，Home 挂载时补消费）；公开 host 直接分发。
 */
export async function handleDeepLink(uri: string): Promise<void> {
  try {
    const result: DeepLinkResult = parse(uri, lookup)
    if (!isPublicAction(result) && !hasSession()) {
      // 受保护深链 + 未登录：落登录页（会话态置假触发 Index 切登录根页 + 清栈），
      // 保留 AppStorage 暂存供登录后消费
      logger.debug('deeplink', `深链登录守卫拦截：未登录 + 受保护 host=${result.host}`)
      clearLoginState()
      if (pathStack.size() > 0) {
        pathStack.clear()
      }
      return
    }
    switch (result.kind) {
      case DeepLinkKind.ROUTE:
        navigateRoute(result)
        break
      case DeepLinkKind.WEB:
        navigateWeb(result)
        break
      default:
        await handleUnknown(uri, result)
        break
    }
  } catch (err) {
    // 兜底：任何意外异常降级为「暂不支持」，深链永不击穿导航栈崩溃
    showToastSafety(DeepLinkText.deeplink_unsupported)
  }
}

/** 注册表命中：push 目标路由（参数对象由页面经 PathStackUtil.getXxxParam 消费） */
function navigateRoute(result: DeepLinkResult): void {
  try {
    // smartwash://login：走会话态切换（Login 作为根内容渲染 + 清栈），
    // 不能 push —— 否则 Home 仍存活，侧滑可回（与退出登录同一坑）
    if (result.routeName === RouteNames.LOGIN) {
      clearLoginState()
      pathStack.clear()
      return
    }
    pathStack.pushPathByName(result.routeName, result.routeParam)
  } catch (err) {
    // 路由名与 router_map 漂移时兜底提示（对齐安卓 IllegalArgumentException 捕获降级）
    logger.warn('deeplink', `深链路由未注册：route=${result.routeName}`)
    showToastSafety(DeepLinkText.deeplink_unsupported)
  }
}

/** web 分支：进 WebView 页（url 已由 parser 白名单校验为 http/https） */
function navigateWeb(result: DeepLinkResult): void {
  const param: WebPageParam = new WebPageParam()
  param.url = result.url
  param.title = result.title
  pathStack.pushPathByName(RouteNames.WEB_PAGE, param)
}

/** 未命中：smartwash:// 查后端字典留痕（静默），统一「暂不支持该链接」 */
async function handleUnknown(uri: string, result: DeepLinkResult): Promise<void> {
  if (result.host.length > 0) {
    const hostKnown = await isHostKnownToBackend(result.host)
    logger.info('deeplink',
      `deeplink 本地未命中：host=${result.host} 后端字典合法=${hostKnown ? resString('deep_link_handler_1') : resString('deep_link_handler_2')} ` +
      resString('deep_link_handler_3'))
  }
  showToastSafety(DeepLinkText.deeplink_unsupported)
}

/**
 * 查后端路由字典判断 host 合法性（首次拉取 + 内存缓存；失败静默返回 false）。
 * 仅在已登录时拉取：/web 前缀请求在本地无 token 时会被请求拦截器拒绝并触发跳登录副作用，
 * 而字典只是诊断位，不能带来导航副作用（对齐安卓「失败静默不影响本地解析」契约）。
 */
async function isHostKnownToBackend(host: string): Promise<boolean> {
  if (routeDictCache !== null) {
    return dictContainsHost(routeDictCache, host)
  }
  const cachedToken = await getValue(CommonConstants.USER_TOKEN, '')
  const token = typeof cachedToken === 'string' ? cachedToken : ''
  if (token.length === 0) {
    return false
  }
  try {
    const res = await getDeeplinkRoutes()
    if (res.code === BizCode.SUCCESS && res.data !== null) {
      routeDictCache = res.data
      return dictContainsHost(routeDictCache, host)
    }
    return false
  } catch (err) {
    // 失败静默：本地解析不受影响；不缓存失败结果，下次调用自动重试
    return false
  }
}

/** 字典命中判定：host 相等（忽略大小写）且启用中且面向鸿蒙（platform 2/3） */
function dictContainsHost(routes: Array<DeeplinkRouteVo>, host: string): boolean {
  return routes.some((route: DeeplinkRouteVo) => {
    const routeHost = route.host ?? ''
    return routeHost.toLowerCase() === host.toLowerCase() &&
      route.enabled === DeeplinkRouteDict.ENABLED &&
      (route.platform === DeeplinkRouteDict.PLATFORM_HARMONY ||
        route.platform === DeeplinkRouteDict.PLATFORM_BOTH)
  })
}
