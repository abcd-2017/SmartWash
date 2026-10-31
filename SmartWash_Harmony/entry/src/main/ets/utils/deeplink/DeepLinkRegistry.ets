// 深链路由注册表 — host → 路由目标集中映射（对齐安卓 app 壳层 deeplink/DeepLinkRegistry.kt）
//
// 安卓按域经 Hilt 贡献者聚合；鸿蒙单 entry 本就是集中式路由（RouteNames.ets 为单一事实来源），
// 故收敛为一张表。host 清单与安卓完全一致，路由名直引 RouteNames 常量（禁止字面量）。
//
// host 清单（与安卓 docs/deeplink-对接文档.md §4 对齐）：
//   login / register / setting / recharge / recharge-record / coupon / order(query tab) /
//   pickup / laundry / divination / divination-history / toolbox-short-code / t/{code} / web(内建)
//
// 未注册 host 与原因（对齐安卓 §5 取舍）：
// - user-info / service：两页是 Home 页 Tabs 的内容页，非独立路由；跳转等价「进 Home 切 tab」，
//   需要改 Home 的路由注册形态并让 Home 从参数观察初始 tab，成本高于收益，故不注册；
// - ai-work：AI 工具工作台鸿蒙未实现（安卓 PageConstant.AiWork），无目标路由可映射，
//   未命中统一走「后端字典留痕 + 暂不支持该链接」提示；
// - web：由 DeepLinkParser 内建处理（WebView 分支），不经本表。

import { RouteNames } from '../../constant/RouteNames'
import { OrderStatusList } from '../../constant/ShowOrderStatus'
// 鸿蒙单模块无编译层界：t/{code} 需要构造详情页既有参数类（ToolboxShortCodeDetailParam），
// 对齐安卓由 toolbox 域贡献者自持参数构造的职责划分
import { ToolboxShortCodeDetailParam } from '../../feature/toolbox/api'
import { DeepLinkRouteLookup, DeepLinkRouteTarget } from './DeepLinkParser'

/** 短链码规则：4-16 位字母数字（URL path 段安全集，对齐安卓 ToolboxDeepLinkModule.SHORT_CODE_REGEX） */
const SHORT_CODE_REGEX: RegExp = /^[0-9a-zA-Z]{4,16}$/

/** 订单页签上限（下标即 OrderList 路由入参语义，越界回退 0，对齐安卓「夹取 0-4」） */
const ORDER_TAB_MAX: number = OrderStatusList().length - 1

/**
 * host → 路由目标总表。重复注册在编译期不可见（鸿蒙无 @IntoSet 聚合顺序问题），
 * Map 字面量重复 key 直接由 TS 编译器报错兜底，天然规避安卓「后到者忽略」的运行时告警场景。
 */
const HOST_ROUTES: Map<string, DeepLinkRouteTarget> = buildHostRoutes()

function buildHostRoutes(): Map<string, DeepLinkRouteTarget> {
  const routes = new Map<string, DeepLinkRouteTarget>()
  // user 域
  routes.set('login', { routeName: RouteNames.LOGIN, param: null })
  routes.set('register', { routeName: RouteNames.REGISTER, param: null })
  routes.set('setting', { routeName: RouteNames.SETTING, param: null })
  // payment 域
  routes.set('recharge', { routeName: RouteNames.RECHARGE, param: null })
  routes.set('recharge-record', { routeName: RouteNames.RECHARGE_RECORD, param: null })
  // coupon / order / laundry / pickup
  // order 带动态 query 参数（初始页签），在 lookup 中按参数构造，不进静态表；
  // t 同理（path 段携带 code，需校验）。
  routes.set('coupon', { routeName: RouteNames.COUPON, param: null })
  routes.set('laundry', { routeName: RouteNames.LAUNDRY, param: null })
  routes.set('pickup', { routeName: RouteNames.PICK_UP, param: null })
  // 观象台域
  routes.set('divination', { routeName: RouteNames.DIV_HOME, param: null })
  routes.set('divination-history', { routeName: RouteNames.DIV_HISTORY, param: null })
  // 工具箱域
  routes.set('toolbox-short-code', { routeName: RouteNames.TOOLBOX_SHORT_CODE_LIST, param: null })
  return routes
}

/**
 * host → 路由目标查找（供 DeepLinkParser.parse 的 routeLookup 消费）。
 * 带参 host（order/t）在表外按参数动态构造目标，参数校验失败返回 null。
 *
 * @param host 已归一化小写的 scheme host
 * @param pathSegments host 之后的剩余 path 段（保留原始大小写）
 * @param query query 参数（value 已百分号解码）
 */
export const lookup: DeepLinkRouteLookup = (host: string, pathSegments: string[],
  query: Map<string, string>): DeepLinkRouteTarget | null => {
  if (host === 'order') {
    const tab = resolveOrderTab(query)
    return { routeName: RouteNames.ORDER_LIST, param: tab }
  }
  if (host === 't') {
    return resolveShortCodeDetail(pathSegments)
  }
  const fixed = HOST_ROUTES.get(host)
  return fixed !== undefined ? fixed : null
}

/**
 * order?tab=N：初始页签（缺省 0），非法/越界夹取 0 - ORDER_TAB_MAX。
 * OrderList 页 aboutToAppear 用页签下标查 OrderStatusList，越界会取到 undefined，必须夹取。
 */
function resolveOrderTab(query: Map<string, string>): number {
  const raw = query.get('tab') ?? ''
  let tab = Number(raw)
  if (Number.isNaN(tab)) {
    tab = 0
  }
  return Math.min(Math.max(Math.trunc(tab), 0), ORDER_TAB_MAX)
}

/**
 * t/{code}：仅接受 t + code 两段形态（parser 已滤空段）；code 4-16 位字母数字。
 * 详情路由其余字段走类内缺省值（id=0 等）——深链场景详情展示字段不完整为既定取舍
 * （对齐安卓 §5），完整数据路径仍走工作台/列表页入口。
 */
function resolveShortCodeDetail(pathSegments: string[]): DeepLinkRouteTarget | null {
  if (pathSegments.length !== 1) {
    return null
  }
  const code = pathSegments[0]
  if (!SHORT_CODE_REGEX.test(code)) {
    return null
  }
  const param: ToolboxShortCodeDetailParam = new ToolboxShortCodeDetailParam()
  param.code = code
  // 其余字段保持类内缺省值（contentType=1 短链 / isPublic=1 公开），与安卓注册缺省值策略一致
  return { routeName: RouteNames.TOOLBOX_SHORT_CODE_DETAIL, param: param }
}
