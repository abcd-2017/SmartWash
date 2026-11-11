/**
 * 洗衣状态卡片 — 纯逻辑层（状态口径 / 主订单挑选 / 卡片数据装配）。
 *
 * 设计约束（任务红线 + 可测性）：
 * - 本文件不依赖任何 UI 组件与网络 Kit（零 import 自 @kit.*），全部导出为纯函数，
 *   供 hypium 单测直接驱动（WidgetOrderModel.test.ets）；
 * - 状态文案统一复用 constant/OrderStatus.ets 的 getOrderStatusDescription（单一事实来源，
 *   GLOSSARY §3.3 枚举口径：码为字符串 "-2"~"7"，卡片文案必须与 App 内一致）；
 * - 数据字段以后端 ShowOrderVo 映射出的 OrderInfo（network/vo/OrderInfo.ets）为准，
 *   不编造 VO 不存在的字段（如「预计完成时间」——VO 无此字段，改用 updatedAt 相对时间呈现）。
 *
 * 「进行中」数据口径：卡片数据源 = 清洗中(3) + 待取件(6) 两路 getOrderList 查询
 * （后端同返回 ShowOrderVo：清洗中路与 /web/auth/orders/getWashingOrder 固定 status=3
 * 的查询口径等价；待取件路与 PickUp 取件页口径一致）。两接口复用不改后端契约。
 */

import { OrderStatusEnum, getOrderStatusDescription } from "../constant/OrderStatus"
import { formatOrderTime } from "../utils/TimeUtil"
import { OrderInfo } from "../network/vo/OrderInfo"
import { resString } from '../utils/ResourceString'

/** 在途状态优先级表：越靠前越优先展示（用户视角「越接近可取件越优先」） */
const PROGRESS_PRIORITY: string[] = [
  OrderStatusEnum.READY_FOR_PICKUP, // 6 待取件 —— 该去取了，最高优先
  OrderStatusEnum.IN_DELIVERY,      // 5 配送中
  OrderStatusEnum.DRIED,            // 4 已烘干
  OrderStatusEnum.WASHING,          // 3 清洗中
  OrderStatusEnum.RECEIVED          // 2 已收取
]

/**
 * 状态是否属于卡片「进行中」口径（在途五态：2/3/4/5/6）。
 * 待支付/待寄件（用户尚未投衣）与终态（已完成/已取消/已退款）不作为卡片进度展示。
 */
export function isWidgetProgressStatus(status: string | null | undefined): boolean {
  if (status === null || status === undefined) {
    return false
  }
  return PROGRESS_PRIORITY.indexOf(status) >= 0
}

/**
 * 从候选订单中挑选卡片主展示订单（纯函数）。
 * 规则：先按在途优先级（PROGRESS_PRIORITY 序）取最高优先级状态；
 * 同状态存在多条时取 orderId 最大者（后端列表按时间倒序、ID 单调递增，最大 ID 即最新）。
 *
 * @param orders 候选订单（清洗中 + 待取件两路数据合并；null/undefined 视为空）
 * @returns 主展示订单；无在途订单返回 null（卡片呈现引导态）
 */
export function selectPrimaryOrder(orders: Array<OrderInfo> | null | undefined): OrderInfo | null {
  const candidates = orders ?? []
  let best: OrderInfo | null = null
  let bestPriority = PROGRESS_PRIORITY.length // 越小越优先
  for (const order of candidates) {
    if (!isWidgetProgressStatus(order.status)) {
      continue
    }
    const priority = PROGRESS_PRIORITY.indexOf(order.status ?? "")
    if (priority < bestPriority) {
      best = order
      bestPriority = priority
      continue
    }
    // 同优先级：取 orderId 更大（更新）的一条；orderId 缺失的不参与"更新"比较
    if (priority === bestPriority && best !== null &&
      order.orderId !== null && (best.orderId === null || order.orderId > best.orderId)) {
      best = order
    }
  }
  return best
}

/**
 * 订单号尾号（宽卡摘要用）：取后 4 位；不足 4 位返回全量；null/空串返回空串。
 */
export function orderNoTail(orderNo: string | null | undefined): string {
  const no = orderNo ?? ""
  if (no.length === 0) {
    return ""
  }
  return no.length <= 4 ? no : no.substring(no.length - 4)
}

/** 卡片状态主色（进行中）— 清氧设计系统 PRIMARY 0xFF2D9B6A 的 hex 字符串形态 */
export const WIDGET_STATUS_COLOR_ACTIVE: string = '#FF2D9B6A'
/** 卡片状态主色（待取件强调）— 深绿 PRIMARY_DARK 0xFF1E8C5C，提示用户该去取件 */
export const WIDGET_STATUS_COLOR_READY: string = '#FF1E8C5C'

/**
 * 卡片页 binding 数据（与 widget/pages/WidgetCard.ets 的 @LocalStorageProp 键一一对应；
 * 卡片进程经 formProvider.updateForm 推送、LocalStorage 承载，字段全部为简单类型）。
 */
export interface WidgetBinding {
  /** 0 = 窄卡（2x2）/ 1 = 宽卡（2x4）；卡片页拿不到自身尺寸，由提供方按 FormParam.DIMENSION_KEY 装配 */
  cardWide: boolean
  /** 是否有进行中订单（false = 引导态） */
  hasOrder: boolean
  /** 状态文案（统一枚举口径，如「清洗中」「待取件」） */
  statusText: string
  /** 状态色（8 位 ARGB hex 字符串，ArkUI fontColor 可直接消费） */
  statusColor: string
  /** 状态副行：待取件 → 取件码；清洗中 → 最近更新时间（formatOrderTime 相对化） */
  infoText: string
  /** 宽卡摘要行：学校 · 柜号 · 订单号尾号 · 实付；窄卡不渲染 */
  summaryText: string
  /** 点击跳转目标（smartwash:// 深链 uri，经 EntryAbility 暂存后复用深链分发） */
  targetUri: string
}

/** 有进行中订单时的点击跳转：订单详情（深链 order-detail/{orderId}，DeepLinkRegistry 注册） */
const URI_ORDER_DETAIL_PREFIX: string = 'smartwash://order-detail/'
/** 引导态点击跳转：订单列表（深链 order?tab=0 全部页签，DeepLinkRegistry 已注册） */
const URI_ORDER_LIST: string = 'smartwash://order?tab=0'

/** 引导态主文案（无进行中订单） */
export function WIDGET_EMPTY_TITLE(): string { return resString('widget_order_model_1') }
/** 引导态副文案（提示可点击） */
export function WIDGET_EMPTY_HINT(): string { return resString('widget_order_model_2') }

/** 实付金额展示（元）；null 兜底「--」 */
function payPriceText(payPrice: number | null): string {
  return payPrice === null ? '--' : `¥${payPrice}`
}

/**
 * 装配「有进行中订单」的卡片数据（纯函数）。
 *
 * 副行口径（VO 无预计完成时间字段，不编造）：
 * - 待取件(6)：取件码最有行动价值 → `取件码 {pickupCode}`（pickupCode 缺失回退更新时间）；
 * - 其他在途态：`更新于 {formatOrderTime(updatedAt)}`（相对时间，对齐 App 内订单时间口径）。
 *
 * 宽卡摘要行：学校名（schoolsVo.schoolName）· 柜号（lockersVo.lockerNumber）·
 * 订单号尾号 · 实付金额，缺失字段跳过不占位。
 *
 * @param order 主展示订单（内部按字段缺失逐项兜底，null 字段不产生崩溃）
 */
export function buildOrderBinding(order: OrderInfo): WidgetBinding {
  const status = order.status ?? ""
  const ready = status === OrderStatusEnum.READY_FOR_PICKUP
  const updatedAt = formatOrderTime(order.updatedAt)
  const pickupCode = order.pickupCode ?? ""
  const infoText = ready && pickupCode.length > 0
    ? `取件码 ${pickupCode}`
    : `更新于 ${updatedAt.length > 0 ? updatedAt : '--'}`
  const summaryParts: string[] = []
  const schoolName = order.schoolsVo?.schoolName ?? ""
  if (schoolName.length > 0) {
    summaryParts.push(schoolName)
  }
  const lockerNumber = order.lockersVo?.lockerNumber ?? null
  if (lockerNumber !== null) {
    summaryParts.push(`${lockerNumber}号柜`)
  }
  const tail = orderNoTail(order.orderNo)
  if (tail.length > 0) {
    summaryParts.push(`尾号 ${tail}`)
  }
  if (order.payPrice !== null) {
    summaryParts.push(payPriceText(order.payPrice))
  }
  const orderId = order.orderId
  return {
    cardWide: false,
    hasOrder: true,
    statusText: getOrderStatusDescription(status),
    statusColor: ready ? WIDGET_STATUS_COLOR_READY : WIDGET_STATUS_COLOR_ACTIVE,
    infoText: infoText,
    summaryText: summaryParts.join(' · '),
    // orderId 缺失（后端异常数据）时点击回退订单列表，不让卡片点击失效
    targetUri: orderId !== null ? `${URI_ORDER_DETAIL_PREFIX}${orderId}` : URI_ORDER_LIST
  }
}

/**
 * 装配引导态卡片数据（纯函数）：无进行中订单 / 未登录 / 数据拉取失败统一走此形态
 * （未登录与失败不区分文案，避免在卡片上暴露登录态细节）。
 */
export function buildEmptyBinding(): WidgetBinding {
  return {
    cardWide: false,
    hasOrder: false,
    statusText: WIDGET_EMPTY_TITLE(),
    statusColor: WIDGET_STATUS_COLOR_ACTIVE,
    infoText: WIDGET_EMPTY_HINT(),
    summaryText: '',
    targetUri: URI_ORDER_LIST
  }
}

/** 加载中占位（onAddForm 必须同步返回，先给占位再异步覆盖刷新） */
export function buildLoadingBinding(): WidgetBinding {
  return {
    cardWide: false,
    hasOrder: false,
    statusText: resString('widget_order_model_3'),
    statusColor: WIDGET_STATUS_COLOR_ACTIVE,
    infoText: '',
    summaryText: '',
    targetUri: URI_ORDER_LIST
  }
}

/**
 * 按卡片尺寸设置版式标记（纯函数）：FormDimension.Dimension_2_4 = 3 → 宽卡，其余 → 窄卡。
 * @param dimension 系统卡片尺寸枚举值（formInfo.FormDimension；onAddForm 的 DIMENSION_KEY）
 */
export function withCardWide(binding: WidgetBinding, dimension: number): WidgetBinding {
  // 仅 2x4 宽卡（枚举值 3）走宽版式；2x2（2）与未知值一律窄版式，保证有默认形态
  const wide = dimension === 3
  return {
    cardWide: wide,
    hasOrder: binding.hasOrder,
    statusText: binding.statusText,
    statusColor: binding.statusColor,
    infoText: binding.infoText,
    summaryText: binding.summaryText,
    targetUri: binding.targetUri
  }
}
