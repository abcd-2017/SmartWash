/**
 * 洗衣订单状态卡片 — 数据装配层（在卡片提供方 EntryFormAbility 进程内执行）。
 *
 * 数据链路（全部复用现有 network 层，零接口改动）：
 *   EntryFormAbility.onAddForm / onUpdateForm
 *     → loadWidgetBinding()
 *     → StorageUtil(preferences) 读 token（与 App 进程同一 smartWash_pref 文件）
 *     → token 为空：直接返回引导态（零网络请求，未登录不报错刷屏）
 *     → token 非空：getOrderList({status:"3"})（清洗中，与 getWashingOrder 后端查询口径等价：
 *       OrdersServiceImpl.getWashingOrderShowVo 固定按 status=3 调同一 mapper）+
 *       getOrderList({status:"6"})（待取件，PickUp 页同款口径）两路现有接口
 *     → selectPrimaryOrder 挑主订单 → buildOrderBinding 装配卡片数据
 *     → formProvider.updateForm 推送（由 EntryFormAbility 执行，本层不触 form API）
 *
 * 数据源选 getOrderList 而非 getWashingOrder：两接口同返回后端 ShowOrderVo 结构，
 * 但鸿蒙端 getOrderList 映射为 OrderInfo（携带 schoolsVo.schoolName 学校名与
 * lockersVo.lockerNumber 柜位编号），宽卡摘要行可展示真实语义字段；
 * getWashingOrder 映射的 OrderVo 仅含 schoolId/lockerId 数字 ID。
 *
 * 跨进程约束（查证结论，实现注释同步见 EntryFormAbility）：
 * - 卡片跑在独立的 FormExtensionAbility 进程，AppStorage / 内存单例互不可见；
 *   StorageUtil 的 preferences 单例在本进程首次 initPreferences 时从磁盘加载，
 *   进程按需拉起、用完可回收，30 分钟刷新粒度下 token 快照旧值风险可忽略；
 * - 401 语义：token 过期时请求走 Axios 拦截器统一 401 处理（清 token → 复位登录态）。
 *   卡片进程无 UI，跳登录与 toast 天然无效果（promptAction 无窗口上下文静默失败）；
 *   清 token 与 App 内 401 语义一致（token 已失效），用户下次进 App 即落登录页。
 */

import { CommonConstants } from "../constant/CommonConstants"
import { OrderStatusEnum } from "../constant/OrderStatus"
import { OrderListForm } from "../network/entity/OrderListForm"
import { getOrderList } from "../network/api/OrderApi"
import { OrderInfo } from "../network/vo/OrderInfo"
import { getValue, initPreferences } from "../utils/StorageUtil"
import { logger } from "../utils/Logger"
import {
  WidgetBinding,
  buildEmptyBinding,
  buildOrderBinding,
  selectPrimaryOrder
} from "./WidgetOrderModel"

const TAG: string = 'widgetData'

/** 单路查询条数：足够挑选主订单（对齐 getWashingOrder 后端默认 size=5） */
const PROGRESS_PAGE_SIZE: number = 5

/**
 * 从 preferences 读取 token 字符串（空串 = 未登录）。
 * 与 App 进程共用同一 preferences 文件与键（CommonConstants.USER_TOKEN = "token"），
 * 读取语义对齐 network/RequestRules.resolveToken（仅字符串视为有效）。
 */
async function readToken(): Promise<string> {
  const cached = await getValue(CommonConstants.USER_TOKEN, "")
  return typeof cached === "string" ? cached : ""
}

/**
 * 按状态拉取一页在途订单（page=1）。
 * @param status 订单状态码（"3" 清洗中 / "6" 待取件，常量取 OrderStatusEnum）
 */
async function fetchProgressOrders(status: string): Promise<Array<OrderInfo>> {
  try {
    const form: OrderListForm = {
      status: status,
      page: 1,
      size: PROGRESS_PAGE_SIZE
    }
    const res = await getOrderList(form)
    if (res.code === 200 && res.data !== null) {
      return res.data
    }
    // 业务失败（含 401/网络类信封转译）：debug 留痕后按空数据处理，不让单路失败污染整卡
    logger.debug(TAG, `在途订单拉取未成功 status=${status} code=${res.code}`)
    return []
  } catch (err) {
    logger.debug(TAG, `在途订单拉取异常 status=${status}`)
    return []
  }
}

/**
 * 拉取并装配卡片数据。任何失败路径都收敛为合法的引导态 binding（不抛异常），
 * 保证 EntryFormAbility 拿到的数据始终可直接 updateForm。
 *
 * @param context 卡片提供方上下文（FormExtensionAbility.context），供 preferences 初始化
 */
export async function loadWidgetBinding(context: Context): Promise<WidgetBinding> {
  // 卡片进程与 App 进程独立：preferences 单例在本进程可能尚未初始化，幂等兜底
  await initPreferences(context)
  // 未登录（无 token）：降级引导态，零网络请求——不触发 401 链路、不报错刷屏（任务红线）
  const token = await readToken()
  if (token.length === 0) {
    logger.debug(TAG, '本地无 token，卡片走引导态')
    return buildEmptyBinding()
  }
  // 两路在途数据（清洗中 + 待取件）；单路失败返回空数组不阻塞另一路
  const washing: Array<OrderInfo> =
    await fetchProgressOrders(OrderStatusEnum.WASHING)
  const pickup: Array<OrderInfo> =
    await fetchProgressOrders(OrderStatusEnum.READY_FOR_PICKUP)
  const merged: Array<OrderInfo> = washing.concat(pickup)
  const primary = selectPrimaryOrder(merged)
  if (primary === null) {
    // 两路均空：无进行中订单（也可能是网络全败——引导态文案不区分，避免卡片暴露失败细节）
    logger.debug(TAG, '无进行中订单，卡片走引导态')
    return buildEmptyBinding()
  }
  const binding = buildOrderBinding(primary)
  logger.debug(TAG, `卡片数据装配完成：${binding.statusText} ${binding.infoText}`)
  return binding
}
