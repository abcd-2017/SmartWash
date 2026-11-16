/**
 * 积分域 API —— 对齐后端 feat/point-signin 分支 PointsWebController（路径前缀
 * /web/auth/points/**，SecurityConfig 由既有 /web/auth/** → ROLE_USER 通配覆盖，无需新增规则）
 * 与安卓 feature/points/api/PointsServiceApi.kt 逐接口一致：
 * - GET  /web/auth/points/me           我的积分概览（余额 + 连签 + 今日已签 + 最近流水）
 * - POST /web/auth/points/sign-in      每日签到（后端幂等：重复请求返回 alreadySigned=true 不重复加分）
 * - GET  /web/auth/points/ledger       积分流水分页（时间倒序，page/size，size 上限 50）
 * - GET  /web/auth/points/leaderboard  积分月榜（当前仅 scope=month）
 *
 * 认证：/web 前缀经 Axios 请求拦截器自动注入 Bearer token（network/RequestRules.ets），
 * 401 由响应拦截器统一处理（清 token → 清内存登录态 → replace 登录页）。
 */

import { BaseResponse } from '../BaseResponse'
import { httpUtil } from '../HttpUtil'
import {
  LeaderboardVo,
  PointLedgerPageData,
  PointsOverviewVo,
  SignInResultVo,
  leaderboardFromJSON,
  pointLedgerPageFromJSON,
  pointsOverviewFromJSON,
  signInResultFromJSON
} from '../vo/PointsVo'

/** 榜单口径常量（后端当前仅支持 month，契约保留 query 参数位） */
export const LEADERBOARD_SCOPE_MONTH: string = 'month'

/** 积分流水单页条数（后端 size 上限 50，取 10 与订单/优惠券列表一致） */
export const POINTS_LEDGER_PAGE_SIZE: number = 10

/**
 * 我的积分概览。
 * 映射说明：泛型声明为 VO 类型但返回前经 fromJSON 显式映射——axios 反序列化产物
 * 字段可能缺失（Jackson 省略 null），消费侧统一走映射后的兜底对象。
 */
export async function getPointsOverview(): Promise<BaseResponse<PointsOverviewVo>> {
  const raw = await httpUtil.get<PointsOverviewVo>('/web/auth/points/me')
  const decoded: BaseResponse<PointsOverviewVo> = {
    data: raw.data === null || raw.data === undefined ? pointsOverviewFromJSON(null) : pointsOverviewFromJSON(raw.data),
    code: raw.code,
    message: raw.message
  }
  return decoded
}

/**
 * 每日签到。
 * 幂等语义（后端契约）：当日已签（含并发冲突方）仍返回 code=200 + alreadySigned=true，
 * 不重复加分；双端按 alreadySigned 渲染「今日已签到」，按 signed/pointsAwarded 渲染实得。
 */
export async function signInToday(): Promise<BaseResponse<SignInResultVo>> {
  const raw = await httpUtil.post<SignInResultVo>('/web/auth/points/sign-in')
  const decoded: BaseResponse<SignInResultVo> = {
    data: raw.data === null || raw.data === undefined ? signInResultFromJSON(null) : signInResultFromJSON(raw.data),
    code: raw.code,
    message: raw.message
  }
  return decoded
}

/**
 * 积分流水分页（时间倒序）。余额/概览仍取 /me，本接口只承载流水列表，
 * 避免 /me 的 recentLedger 有界（20 条）导致历史流水无法翻取。
 * @param page 页码（从 1 起）
 * @param size 每页条数（默认 POINTS_LEDGER_PAGE_SIZE，后端上限 50）
 */
export async function getPointsLedger(page: number,
  size: number = POINTS_LEDGER_PAGE_SIZE): Promise<BaseResponse<PointLedgerPageData>> {
  // ArkTS 禁止裸对象字面量作参数（arkts-no-untyped-obj-literals），查询参数用 Record 并按索引赋值
  const params: Record<string, Object> = {}
  params['page'] = page
  params['size'] = size
  const raw = await httpUtil.get<PointLedgerPageData>('/web/auth/points/ledger', params)
  const decoded: BaseResponse<PointLedgerPageData> = {
    data: raw.data === null || raw.data === undefined ? pointLedgerPageFromJSON(null) : pointLedgerPageFromJSON(raw.data),
    code: raw.code,
    message: raw.message
  }
  return decoded
}

/**
 * 积分月榜（TOP 50 + 我的排名；榜单请求与概览相互独立，失败不拖垮余额/签到/流水区块）。
 * @param scope 榜单口径，当前仅 month（LEADERBOARD_SCOPE_MONTH）
 */
export async function getPointsLeaderboard(scope: string = LEADERBOARD_SCOPE_MONTH): Promise<BaseResponse<LeaderboardVo>> {
  // ArkTS 禁止裸对象字面量作参数（arkts-no-untyped-obj-literals），查询参数用 Record 并按索引赋值
  const params: Record<string, Object> = {}
  params['scope'] = scope
  const raw = await httpUtil.get<LeaderboardVo>('/web/auth/points/leaderboard', params)
  const decoded: BaseResponse<LeaderboardVo> = {
    data: raw.data === null || raw.data === undefined ? leaderboardFromJSON(null) : leaderboardFromJSON(raw.data),
    code: raw.code,
    message: raw.message
  }
  return decoded
}
