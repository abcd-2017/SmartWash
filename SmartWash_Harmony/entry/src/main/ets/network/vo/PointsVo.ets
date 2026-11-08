/**
 * 积分域 VO（interface + 显式映射，不依赖 class 直接接 JSON）。
 *
 * 对齐后端 feat/point-signin 分支 com/smartwash/points/vo/ 四个 Vo 与安卓
 * feature/points/api/model 三个模型类，字段名逐一勿改：
 * - GET /web/auth/points/me          → PointsOverviewVo（余额/连签/今日已签/最近流水）
 * - POST /web/auth/points/sign-in    → SignInResultVo（幂等：重复请求 signed=false + alreadySigned=true）
 * - GET /web/auth/points/leaderboard → LeaderboardVo（Redis 月榜 TOP N + 我的排名）
 *
 * 后端注释要点（映射容错依据）：
 * - balance/totalEarned/streak/nextBonusIn 无账户或缺字段时端上兜底 0；
 * - streak 今日未签时为连签预览（昨日 streak+1），提示「今天签将达 N 天」；
 * - nextBonusIn 为距下次连签奖励还需的签到次数（含今日，若今日未签）；
 * - EntryVo.avatar 可空；maskedPhone 已由后端脱敏（138****1234）；
 * - my（我的排名）未上榜为 null；entries 缺失/null 兜底空数组；
 * - PointLedgerItemVo.reason 为枚举字符串（SIGN_IN / STREAK_BONUS），reasonDesc
 *   为后端中文描述（未知枚举时的展示兜底）。
 */
import { jsonBool, jsonNum, jsonObj, jsonStr, mapArray } from "./JsonParse"

/** 积分流水条目（/me 最近流水列表元素，对齐后端 PointLedgerItemVo） */
export interface PointLedgerItemVo {
  /** 积分变动（正=入账） */
  delta?: number
  /** 业务原因枚举串（SIGN_IN / STREAK_BONUS），未知值由展示层回退 reasonDesc */
  reason?: string
  /** 业务原因中文描述（后端直接下发，未知 reason 时的展示兜底） */
  reasonDesc?: string
  /** 业务日期维度（签到类=签到日 yyyy-MM-dd，端上当前不展示） */
  refDate?: string
  /** 本笔变动后余额快照 */
  balanceAfter?: number
  /** 入账时间（LocalDateTime ISO 串） */
  createdAt?: string
}

export function pointLedgerItemFromJSON(raw: Object | null | undefined): PointLedgerItemVo {
  if (raw === null || raw === undefined) {
    return {}
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const vo: PointLedgerItemVo = {}
  const delta: number | null = jsonNum(obj['delta'])
  const reason: string | null = jsonStr(obj['reason'])
  const reasonDesc: string | null = jsonStr(obj['reasonDesc'])
  const refDate: string | null = jsonStr(obj['refDate'])
  const balanceAfter: number | null = jsonNum(obj['balanceAfter'])
  const createdAt: string | null = jsonStr(obj['createdAt'])
  if (delta !== null) {
    vo.delta = delta
  }
  if (reason !== null) {
    vo.reason = reason
  }
  if (reasonDesc !== null) {
    vo.reasonDesc = reasonDesc
  }
  if (refDate !== null) {
    vo.refDate = refDate
  }
  if (balanceAfter !== null) {
    vo.balanceAfter = balanceAfter
  }
  if (createdAt !== null) {
    vo.createdAt = createdAt
  }
  return vo
}

/** 我的积分概览（对齐后端 PointsOverviewVo） */
export interface PointsOverviewVo {
  /** 当前积分余额（无账户=0） */
  balance?: number
  /** 累计获得积分（无账户=0） */
  totalEarned?: number
  /** 今日是否已签 */
  signedToday?: boolean
  /** 当前连签天数（今日未签时为连签预览，语义见文件头注释） */
  streak?: number
  /** 距下次连签奖励还需的签到次数（含今日，若今日未签） */
  nextBonusIn?: number
  /** 最近流水（时间倒序，条数后端配置 recent-ledger-size=20） */
  recentLedger: Array<PointLedgerItemVo>
}

export function pointsOverviewFromJSON(raw: Object | null | undefined): PointsOverviewVo {
  const empty: PointsOverviewVo = { recentLedger: [] }
  if (raw === null || raw === undefined) {
    return empty
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const vo: PointsOverviewVo = { recentLedger: [] }
  const balance: number | null = jsonNum(obj['balance'])
  const totalEarned: number | null = jsonNum(obj['totalEarned'])
  const signedToday: boolean | null = jsonBool(obj['signedToday'])
  const streak: number | null = jsonNum(obj['streak'])
  const nextBonusIn: number | null = jsonNum(obj['nextBonusIn'])
  if (balance !== null) {
    vo.balance = balance
  }
  if (totalEarned !== null) {
    vo.totalEarned = totalEarned
  }
  if (signedToday !== null) {
    vo.signedToday = signedToday
  }
  if (streak !== null) {
    vo.streak = streak
  }
  if (nextBonusIn !== null) {
    vo.nextBonusIn = nextBonusIn
  }
  vo.recentLedger = mapArray(obj['recentLedger'],
    (item: Object): PointLedgerItemVo => pointLedgerItemFromJSON(item))
  return vo
}

/** 签到结果（对齐后端 SignInResultVo；幂等命中 signed=false + alreadySigned=true，code=200） */
export interface SignInResultVo {
  /** 本次是否真正签上（幂等命中=false） */
  signed?: boolean
  /** 是否幂等命中（当日已签，含并发冲突方） */
  alreadySigned?: boolean
  /** 签到时点的连签天数（幂等命中=既有记录的 streak） */
  streak?: number
  /** 本次实得积分（幂等命中=0） */
  pointsAwarded?: number
  /** 签到后余额（幂等命中=当前余额） */
  balanceAfter?: number
}

export function signInResultFromJSON(raw: Object | null | undefined): SignInResultVo {
  if (raw === null || raw === undefined) {
    return {}
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const vo: SignInResultVo = {}
  const signed: boolean | null = jsonBool(obj['signed'])
  const alreadySigned: boolean | null = jsonBool(obj['alreadySigned'])
  const streak: number | null = jsonNum(obj['streak'])
  const pointsAwarded: number | null = jsonNum(obj['pointsAwarded'])
  const balanceAfter: number | null = jsonNum(obj['balanceAfter'])
  if (signed !== null) {
    vo.signed = signed
  }
  if (alreadySigned !== null) {
    vo.alreadySigned = alreadySigned
  }
  if (streak !== null) {
    vo.streak = streak
  }
  if (pointsAwarded !== null) {
    vo.pointsAwarded = pointsAwarded
  }
  if (balanceAfter !== null) {
    vo.balanceAfter = balanceAfter
  }
  return vo
}

/** 榜单条目（对齐后端 LeaderboardVo.EntryVo） */
export interface LeaderboardEntryVo {
  /** 名次（1 起） */
  rank?: number
  /** 用户 ID（端上不消费，契约保留） */
  userId?: number
  /** 脱敏手机号（138****1234，后端已脱敏） */
  maskedPhone?: string
  /** 头像 URL（可空） */
  avatar?: string
  /** 月内签到积分 */
  points?: number
}

function leaderboardEntryFromJSON(raw: Object | null | undefined): LeaderboardEntryVo {
  if (raw === null || raw === undefined) {
    return {}
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const vo: LeaderboardEntryVo = {}
  const rank: number | null = jsonNum(obj['rank'])
  const userId: number | null = jsonNum(obj['userId'])
  const maskedPhone: string | null = jsonStr(obj['maskedPhone'])
  const avatar: string | null = jsonStr(obj['avatar'])
  const points: number | null = jsonNum(obj['points'])
  if (rank !== null) {
    vo.rank = rank
  }
  if (userId !== null) {
    vo.userId = userId
  }
  if (maskedPhone !== null) {
    vo.maskedPhone = maskedPhone
  }
  if (avatar !== null) {
    vo.avatar = avatar
  }
  if (points !== null) {
    vo.points = points
  }
  return vo
}

/** 我的排名（对齐后端 LeaderboardVo.MyRankVo，未上榜=null） */
export interface MyRankVo {
  /** 名次（1 起） */
  rank?: number
  /** 月内签到积分 */
  points?: number
}

function myRankFromJSON(raw: Object | null | undefined): MyRankVo | null {
  if (raw === null || raw === undefined) {
    return null
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const vo: MyRankVo = {}
  const rank: number | null = jsonNum(obj['rank'])
  const points: number | null = jsonNum(obj['points'])
  if (rank !== null) {
    vo.rank = rank
  }
  if (points !== null) {
    vo.points = points
  }
  return vo
}

/** 积分月榜（对齐后端 LeaderboardVo，数据源 Redis ZSet，榜与库允许短暂不一致） */
export interface LeaderboardVo {
  /** 榜单口径（当前仅 month） */
  scope?: string
  /** 榜单月份（yyyyMM） */
  month?: string
  /** TOP N（N 见后端配置 points.leaderboard.size=50） */
  entries: Array<LeaderboardEntryVo>
  /** 我的排名（未上榜=null） */
  my?: MyRankVo
}

export function leaderboardFromJSON(raw: Object | null | undefined): LeaderboardVo {
  const empty: LeaderboardVo = { entries: [] }
  if (raw === null || raw === undefined) {
    return empty
  }
  const obj: Record<string, Object> = raw as Record<string, Object>
  const vo: LeaderboardVo = {
    entries: mapArray(obj['entries'],
      (item: Object): LeaderboardEntryVo => leaderboardEntryFromJSON(item))
  }
  const scope: string | null = jsonStr(obj['scope'])
  const month: string | null = jsonStr(obj['month'])
  if (scope !== null) {
    vo.scope = scope
  }
  if (month !== null) {
    vo.month = month
  }
  const my = myRankFromJSON(jsonObj(obj['my']))
  if (my !== null) {
    vo.my = my
  }
  return vo
}
