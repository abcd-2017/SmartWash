/**
 * 积分域纯逻辑 —— 连签周期进度 / 签到后 nextBonusIn 重算 / 榜单名次档位 / 月份与时间
 * 解析格式化 / reason 文案映射 / 签到结果乐观并入概览。
 *
 * 对齐安卓 feature/points/domain/PointsDomainLogic.kt + PointsReason.kt，全部为
 * 纯函数（不触碰 ArkUI/网络/存储），供签到有礼页消费与 hypium 锚点单测覆盖。
 * 奖励发放以后端为准，端上常量仅用于进度展示与签后本地重算（静默刷新以后端为准）。
 */

import { PointsOverviewVo, SignInResultVo } from '../network/vo/PointsVo'
import { resString } from '../utils/ResourceString'

/** 连签奖励周期（对齐后端 points.sign-in.streak-bonus-step 默认值 7：连签第 7/14/21…天发奖励） */
export const STREAK_BONUS_STEP: number = 7

/** 连签周期进度：currentInCycle = 周期内已签天数（0..stepDays），进度条渲染用 */
export interface StreakCycleProgress {
  /** 周期内已签天数（0..stepDays） */
  currentInCycle: number
  /** 周期总天数 */
  stepDays: number
}

/**
 * 由 nextBonusIn 反推周期内进度：距奖励还差 n 天 → 周期内已进 step-n 天。
 * 容错（对齐安卓）：null/<=0（字段缺失或已达成）与 >stepDays（契约漂移）一律回周期起点
 * 0/step，进度条不越界不出负。
 */
export function streakCycleProgress(nextBonusIn: number | null, stepDays: number = STREAK_BONUS_STEP): StreakCycleProgress {
  if (nextBonusIn === null || nextBonusIn <= 0 || nextBonusIn > stepDays) {
    return { currentInCycle: 0, stepDays: stepDays }
  }
  return { currentInCycle: stepDays - nextBonusIn, stepDays: stepDays }
}

/**
 * 签到成功后的 nextBonusIn 本地重算（签后立即更新余额/连签用，静默刷新以后端为准）：
 * 连签第 N×step 天发放奖励，故 streak 落在周期边界（余 0）时下一轮为整周期 stepDays，
 * 否则为 stepDays - 余数。
 */
export function nextBonusAfterSignIn(streak: number, stepDays: number = STREAK_BONUS_STEP): number {
  const inCycle: number = streak % stepDays
  if (inCycle === 0) {
    return stepDays
  }
  return stepDays - inCycle
}

/** 榜单名次档位：TOP3 品牌色强调，其余中性（0/null/负数等异常一律 NORMAL，对齐安卓 rankTier） */
export enum RankTier {
  TOP1 = 'top1',
  TOP2 = 'top2',
  TOP3 = 'top3',
  NORMAL = 'normal'
}

export function rankTier(rank: number | null): RankTier {
  if (rank === 1) {
    return RankTier.TOP1
  }
  if (rank === 2) {
    return RankTier.TOP2
  }
  if (rank === 3) {
    return RankTier.TOP3
  }
  return RankTier.NORMAL
}

/** 榜单月份解析产物（yyyyMM → 年/月二元组，非法返回 null） */
export interface LeaderboardMonth {
  year: number
  month: number
}

/**
 * 榜单月份解析（后端 month 为 yyyyMM）：长度 6 且全数字、月份 1-12 才合法，
 * 非法（长度/非数字/月份越界）返回 null，UI 不展示月份角标。
 */
export function parseLeaderboardMonth(month: string | null): LeaderboardMonth | null {
  if (month === null || month.length !== 6) {
    return null
  }
  if (!/^\d{6}$/.test(month)) {
    return null
  }
  const year: number = Number(month.substring(0, 4))
  const monthOfYear: number = Number(month.substring(4, 6))
  if (Number.isNaN(year) || Number.isNaN(monthOfYear) || monthOfYear < 1 || monthOfYear > 12) {
    return null
  }
  return { year: year, month: monthOfYear }
}

/**
 * 流水时间格式化（ISO yyyy-MM-dd'T'HH:mm:ss → MM-dd HH:mm，对齐安卓 formatLedgerTime）。
 * 解析失败（旧格式/字段漂移）返回 null，UI 隐藏时间而非展示垃圾串。
 */
export function formatLedgerTime(iso: string | null): string | null {
  if (iso === null || iso.length < 16) {
    return null
  }
  // ISO 头 16 位（yyyy-MM-ddTHH:mm）校验：日期段可解析 + T 分隔 + 时间段两位数字且在有效范围
  // （小时 0-23 / 分钟 0-59；对齐安卓 LocalDateTime.parse 对 T25:30 这类漂移值抛异常→null 的行为）
  const dateHead: string = iso.substring(0, 10)
  if (iso.charAt(10) !== 'T') {
    return null
  }
  const parsed = new Date(`${dateHead}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) {
    return null
  }
  const timePart: string = iso.substring(11, 16)
  if (!/^\d{2}:\d{2}$/.test(timePart)) {
    return null
  }
  const hour: number = Number(timePart.substring(0, 2))
  const minute: number = Number(timePart.substring(3, 5))
  if (hour > 23 || minute > 59) {
    return null
  }
  return `${dateHead.substring(5, 7)}-${dateHead.substring(8, 10)} ${timePart}`
}

/**
 * 流水 reason 文案映射（对齐安卓 PointsReason + LedgerRow 展示策略）：
 * SIGN_IN=每日签到 / STREAK_BONUS=连签奖励；后端扩展新枚举端上未适配时，
 * 回退展示后端下发的 reasonDesc（非空），再兜底「积分变动」，不崩不空白。
 */
export function pointsReasonLabel(reason: string | null, reasonDesc: string | null): string {
  if (reason === 'SIGN_IN') {
    return resString('points_reason_sign_in')
  }
  if (reason === 'STREAK_BONUS') {
    return resString('points_reason_streak_bonus')
  }
  if (reasonDesc !== null && reasonDesc.length > 0) {
    return reasonDesc
  }
  return resString('points_reason_unknown')
}

/** 流水 delta 展示文案：正数带 + 号（+5），负数后端自带符号（-3），0 展示 0 */
export function pointsDeltaText(delta: number | null): string {
  const value: number = delta ?? 0
  if (value > 0) {
    return `+${value}`
  }
  return `${value}`
}

/**
 * 连签提示文案（hero 卡第三行，对齐安卓 strings points_streak_format +
 * points_next_bonus_format）：「已连签 N 天 · 再签 M 天得连签奖励」。
 * streak<=0（尚无连签）返回 null 不渲染整行；nextBonusIn 缺失时只渲染连签段。
 */
export function streakProgressText(streak: number | null, nextBonusIn: number | null): string | null {
  const signedDays: number = streak ?? 0
  if (signedDays <= 0) {
    return null
  }
  const streakPart: string = `已连签 ${signedDays} 天`
  if (nextBonusIn === null || nextBonusIn <= 0) {
    return streakPart
  }
  return `${streakPart} · 再签 ${nextBonusIn} 天得连签奖励`
}

/**
 * 签到结果并入概览的乐观局部更新（对齐安卓 updatedWith；成功后余额/连签/已签立即生效，
 * 不等静默刷新）：
 * - signedToday 置 true（幂等命中同样成立——当日已签）；
 * - balance 取 balanceAfter（缺失保留原值）、streak 以签到结果为准（缺失保留原值）；
 * - nextBonusIn 本地重算（见 nextBonusAfterSignIn）；
 * - totalEarned 乐观累加本次实得（只增不减，负向防御 clamp 0）。
 * recentLedger 不并入：流水以后端静默刷新为准，本函数返回值不携带流水。
 */
export function overviewAfterSignIn(current: PointsOverviewVo, result: SignInResultVo): PointsOverviewVo {
  const newStreak: number = result.streak ?? current.streak ?? 0
  const awarded: number = Math.max(0, result.pointsAwarded ?? 0)
  return {
    balance: result.balanceAfter ?? current.balance ?? 0,
    totalEarned: (current.totalEarned ?? 0) + awarded,
    signedToday: true,
    streak: newStreak,
    nextBonusIn: nextBonusAfterSignIn(newStreak),
    recentLedger: current.recentLedger
  }
}
