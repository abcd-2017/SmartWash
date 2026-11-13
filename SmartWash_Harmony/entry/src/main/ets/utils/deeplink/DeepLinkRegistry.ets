// 深链路由注册表 — host → 路由目标集中映射（对齐安卓 app 壳层 deeplink/DeepLinkRegistry.kt）
//
// 安卓按域经 Hilt 贡献者聚合（各域 *DeepLinkModule @IntoSet）；鸿蒙单 entry 本就是集中式路由
// （RouteNames.ets 为单一事实来源），故收敛为一张表。host 清单与安卓完全一致，
// 路由名直引 RouteNames 常量（禁止字面量）。
//
// host 清单（与安卓各域 DeepLinkModule 注册一致）：
//   login / register / setting / recharge / recharge-record / coupon / order(query tab) /
//   order-detail/{orderId}（鸿蒙端内：桌面洗衣卡片点击直达详情，安卓无此入口） /
//   laundry / pickup / divination / divination-history / toolbox-short-code / delivery /
//   t/{code} / game-center / lab / review / subscription / ai-work / points / messages /
//   dining（吃啥：无路径 → 域首页；order/{orderId}?t= → 加入入口页）/ web(内建)
//
// 未实现域的兜底（对齐「先兜底实现」排期）：
//   后端 workbench_entry 种子对全部端统一下发这些卡片（无 platform 过滤），未落地域注册到
//   ComingSoon 占位页（pages/ComingSoon.ets）——卡片点击有明确落点而非裸 toast；
//   各域页面落地后把对应 host 的 routeName 换成真实路由即可，ComingSoon 保留通用兜底。
//   （lab 已落地为 LabRoute.Home、review 已落地为
//    ReviewRoute.Queue、subscription 已落地为 SubscriptionRoute.List、
//    delivery 已落地为 ToolboxRoute.Delivery、ai-work 已落地为壳层 AiWorkPage，均不再走兜底。）
//
// 未注册 host 与原因（对齐安卓 §5 取舍）：
// - user-info / service：两页是 Home 页 Tabs 的内容页，非独立路由；跳转等价「进 Home 切 tab」，
//   需要改 Home 的路由注册形态并让 Home 从参数观察初始 tab，成本高于收益，故不注册；
// - web：由 DeepLinkParser 内建处理（WebView 分支），不经本表。

import { RouteNames } from '../../constant/RouteNames'
import { OrderStatusList } from '../../constant/ShowOrderStatus'
// 鸿蒙单模块无编译层界：t/{code} 需要构造详情页既有参数类（ToolboxShortCodeDetailParam），
// 对齐安卓由 toolbox 域贡献者自持参数构造的职责划分
import { ToolboxShortCodeDetailParam } from '../../feature/toolbox/api'
// dining 域深链参数（order/{orderId}?t= → 加入入口页），职责同上
import { DiningOrderJoinParam } from '../../feature/dining/api'
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
  // 工具箱域私密文本（阅后即焚）入口（对齐安卓 ToolboxDeepLinkModule：toolbox-secret → 列表页）
  routes.set('toolbox-secret', { routeName: RouteNames.TOOLBOX_SHORT_CODE_LIST, param: null })
  // 物品标签（一物一码 M2）入口（对齐安卓 ToolboxDeepLinkModule：toolbox-asset-tag → 标签列表）
  routes.set('toolbox-asset-tag', { routeName: RouteNames.TOOLBOX_ASSET_TAG_LIST, param: null })
  // 游戏中心域（对齐安卓 GameDeepLinkModule：game-center → GameRoute.Center）
  routes.set('game-center', { routeName: RouteNames.GAME_CENTER, param: null })
  // 工具箱域设备投递（对齐安卓 ToolboxDeepLinkModule：delivery → ToolboxRoute.Delivery）
  routes.set('delivery', { routeName: RouteNames.TOOLBOX_DELIVERY, param: null })
  // 新增域兜底（对齐安卓各域 DeepLinkModule 的 host 注册；鸿蒙端页面 P1-P3 排期未落地，
  // 统一落到 ComingSoon 占位页，保证后端下发的工作台卡片点击有明确落点）
  // lab 已落地（对齐安卓 LabDeepLinkModule：lab → LabRoute.Home）
  routes.set('lab', { routeName: RouteNames.LAB_HOME, param: null })
  // review 已落地（对齐安卓 ReviewDeepLinkModule：review → ReviewRoute.Queue 今日队列页）
  routes.set('review', { routeName: RouteNames.REVIEW_QUEUE, param: null })
  // subscription 已落地（对齐安卓 SubscriptionDeepLinkModule：subscription → SubscriptionRoute.List）
  routes.set('subscription', { routeName: RouteNames.SUBSCRIPTION_LIST, param: null })
  // ai-work 已落地（对齐安卓壳层 DeepLinkModule：ai-work → PageConstant.AiWork）
  routes.set('ai-work', { routeName: RouteNames.AI_WORK, param: null })
  // 积分域已落地（对齐安卓 PointsDeepLinkModule：points → PointsRoute.Home 签到有礼页，
  // 扩展提案功能 17/18 双端闭环；入口为我的页「签到有礼」行与深链 smartwash://points）
  routes.set('points', { routeName: RouteNames.POINTS, param: null })
  // messages 已落地（对齐安卓 NoticeDeepLinkModule：messages → NoticeRoute.Inbox）
  routes.set('messages', { routeName: RouteNames.MESSAGES, param: null })
  return routes
}

/**
 * dining/{orderId} 带参形态：`order/{orderId}` + query `t` → 加入入口页（DiningOrderJoin）；
 * 无路径 / 非法 orderId → 域首页订单列表（对齐安卓 DiningDeepLinkModule 的降级策略）。
 * query `t` 大小写敏感（token 由创建响应下发，重置后旧值作废；缺失按非法参数走列表兜底）。
 */
function resolveDiningTarget(pathSegments: string[], query: Map<string, string>): DeepLinkRouteTarget | null {
  if (pathSegments.length === 0) {
    return { routeName: RouteNames.DINING_ORDER_LIST, param: null }
  }
  if (pathSegments.length !== 2 || pathSegments[0] !== 'order') {
    return null
  }
  const orderId = Number(pathSegments[1])
  if (!Number.isInteger(orderId) || orderId <= 0) {
    return null
  }
  const token = query.get('t') ?? ''
  if (token.trim().length === 0) {
    return null
  }
  const param = new DiningOrderJoinParam()
  param.orderId = orderId
  param.token = token
  return { routeName: RouteNames.DINING_ORDER_JOIN, param: param }
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
  if (host === 'order-detail') {
    return resolveOrderDetail(pathSegments)
  }
  if (host === 't') {
    return resolveShortCodeDetail(pathSegments)
  }
  // dining：无路径 → 域首页；order/{orderId}?t= → 加入入口页（DiningOrderJoin）
  if (host === 'dining') {
    return resolveDiningTarget(pathSegments, query)
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
 * order-detail/{orderId}：订单详情（桌面洗衣卡片「有进行中订单」的点击落点；
 * 引导态走 order?tab=0 列表页）。路由入参为 number —— 与 OrderList 页
 * pushPathByName(RouteNames.ORDER_DETAIL, order.orderId) 同构，
 * OrderDetail.aboutToAppear 经 getNumberParam(RouteNames.ORDER_DETAIL) 消费（Number()
 * 显式转换，number/数字字符串均可），不做裸强转。
 * 参数校验：仅接受单段正整数 path，非法返回 null（深链统一降级「暂不支持该链接」）。
 */
function resolveOrderDetail(pathSegments: string[]): DeepLinkRouteTarget | null {
  if (pathSegments.length !== 1) {
    return null
  }
  const orderId = Number(pathSegments[0])
  if (!Number.isInteger(orderId) || orderId <= 0) {
    return null
  }
  return { routeName: RouteNames.ORDER_DETAIL, param: orderId }
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
