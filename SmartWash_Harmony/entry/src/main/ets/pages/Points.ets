/**
 * 签到有礼页 —— 扩展提案功能 17/18（双端闭环鸿蒙侧），对齐安卓
 * feature/points/impl/.../ui/points/PointsPage.kt 单页三分区结构：
 *
 * ① Hero 余额卡（品牌渐变 + 黑纱 20%→35%，余额大字 = 每页唯一锚点 + 累计获得 + 连签进度 +
 *    签到 CTA：hero 白色胶囊，未签白底绿字 / 已签置灰禁点 / loading 小 spinner 防重）；
 * ② 本月排行榜卡（TOP 榜名次/打码手机号/积分 + 我的排名行；榜单请求独立，
 *    失败仅卡内小态不拖垮余额/签到/流水区块）；
 * ③ 最近流水（reason 文案映射 + delta 正负色 + 时间）。
 *
 * 状态三态（对齐安卓 PointsContentState）：首载骨架 / 首载失败（整页错误 + 重试）/
 * 内容（签到后乐观更新 + 静默刷新，不回骨架）。纯逻辑（连签进度/名次档位/文案映射）
 * 收敛在 utils/PointsLogic.ets，本页只做状态编排与渲染。
 */

import { BizCode } from '../network/HttpUtil'
import { getPointsLeaderboard, getPointsOverview, signInToday } from '../network/api/PointsApi'
import { LeaderboardEntryVo, LeaderboardVo, PointLedgerItemVo, PointsOverviewVo } from '../network/vo/PointsVo'
import {
  LeaderboardMonth,
  pointsDeltaText,
  pointsReasonLabel,
  parseLeaderboardMonth,
  rankTier,
  RankTier,
  streakCycleProgress,
  streakProgressText,
  formatLedgerTime,
  overviewAfterSignIn
} from '../utils/PointsLogic'
import { DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from '../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../utils/SafeArea'
import { showToastSafety } from '../utils/ToastUtil'
import { logger } from '../utils/Logger'
import { BusinessError } from '@kit.BasicServicesKit'
import { AppButton, AppButtonVariant, DrumMark, EmptyState, LoadingState, PageHeader, SkeletonBlock } from '../view/common/AppComponents'
import { ThemeRefreshV2 } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

/** 页面文案（对齐安卓 feature/points/impl res/values/strings.xml） */
class PointsText {
  static get title(): string { return resString('points_title') }
  static get balanceLabel(): string { return resString('points_balance_label') }
  static get totalEarnedFormat(): string { return resString('points_text_total_earned_format') }
  static get signIn(): string { return resString('points_sign_in') }
  static get signedToday(): string { return resString('points_signed_today') }
  static get signInSuccess(): string { return resString('points_text_sign_in_success') }
  static get signInAlready(): string { return resString('points_sign_in_already') }
  static get signInFail(): string { return resString('points_text_sign_in_fail') }
  static get leaderboardTitle(): string { return resString('points_leaderboard_title') }
  static get leaderboardLoadFailed(): string { return resString('points_leaderboard_load_failed') }
  static get leaderboardEmpty(): string { return resString('points_leaderboard_empty') }
  static get myRankFormat(): string { return resString('points_text_my_rank_format') }
  static get myRankNone(): string { return resString('points_my_rank_none') }
  static get ledgerTitle(): string { return resString('points_ledger_title') }
  static get ledgerEmpty(): string { return resString('points_ledger_empty') }
  static get loadFailed(): string { return resString('points_load_failed') }
  static get actionRetry(): string { return resString('retry') }
  static get unit(): string { return resString('points_unit') }
}

/** 文案占位替换（0/1 双占位版，避免 ArkTS 模板方法限制） */
function formatText(template: string, arg0: string, arg1: string = ''): string {
  let result: string = template.replace('{0}', arg0)
  if (arg1 !== '') {
    result = result.replace('{1}', arg1)
  }
  return result
}

@Builder
export function PointsBuilder() {
  PointsPage()
}

/**
 * 榜单行（56 行）：名次（TOP3 品牌色强调）+ 头像（空则滚筒母题占位）+ 打码手机号 + 月积分。
 * @ComponentV2 + @Param：@Builder 按值传参在刷新后不重建（已知坑），列表行走组件。
 */
@ComponentV2
struct LeaderboardRow {
  @Require @Param entry: LeaderboardEntryVo

  /** 名次色：TOP3 品牌深绿强调（PRIMARY_DARK 与右侧积分值同色档，对齐安卓 audit-20 #9），其余中性 */
  private rankColor(): ResourceColor {
    const tier: RankTier = rankTier(this.entry.rank ?? null)
    return tier === RankTier.NORMAL ? $r('app.color.TEXT_SECONDARY') : $r('app.color.PRIMARY_DARK')
  }

  /** 头像是否走网络图（后端下发非空即用，加载失败回退母题占位） */
  private hasAvatar(): boolean {
    return (this.entry.avatar ?? '').length > 0
  }

  build() {
    Row() {
      Text(`#${this.entry.rank ?? 0}`)
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontFeature(DesignFontFeature.TNUM)
        .fontColor(this.rankColor())

      if (this.hasAvatar()) {
        Image(this.entry.avatar)
          .width(28)
          .height(28)
          .borderRadius(14)
          .objectFit(ImageFit.Cover)
          .margin({ left: DesignDimens.SPACE_SM })
      } else {
        Column() {
          DrumMark({ sizeValue: 16, tint: $r('app.color.PRIMARY_DARK') })
        }
        .width(28)
        .height(28)
        .borderRadius(14)
        .backgroundColor($r('app.color.PRIMARY_LIGHT'))
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .margin({ left: DesignDimens.SPACE_SM })
      }

      Text(this.entry.maskedPhone ?? '')
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontWeight(DesignFontWeight.MEDIUM)
        .fontColor($r('app.color.TEXT_PRIMARY'))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

      Text(`${this.entry.points ?? 0}`)
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontFeature(DesignFontFeature.TNUM)
        .fontColor($r('app.color.PRIMARY_DARK'))

      Text(PointsText.unit)
        .fontSize(DesignFontSize.CAPTION)
        .fontWeight(DesignFontWeight.MEDIUM)
        // 单位是数值读数的一部分（可读内容非装饰），textTertiary 压白卡仅 2.25:1 触 §10 红线，
        // 改 textSecondary（对齐安卓 audit-20 #3）+ labelMedium 12/500
        .fontColor($r('app.color.TEXT_SECONDARY'))
        .margin({ left: DesignDimens.SPACE_XXS })
    }
    .width('100%')
    .height(56)
    .alignItems(VerticalAlign.Center)
  }
}

/**
 * 我的排名行（56 行，固定卡底）：上榜 = 名次 + 月积分；未上榜 = 签到引导文案。
 */
@ComponentV2
struct MyRankRow {
  /** 上榜排名（null = 未上榜） */
  @Param myRank: number | null = null
  /** 月内积分（未上榜时忽略） */
  @Param myPoints: number | null = null

  build() {
    Row() {
      if (this.myRank !== null) {
        Text(formatText(PointsText.myRankFormat, `${this.myRank}`, `${this.myPoints ?? 0}`))
          .fontSize(DesignFontSize.BODY_SMALL)
          // 14 行内文案字重对齐安卓 titleSmall 14/500（SEMI_BOLD 是 titleLarge/SectionTitle 档）
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontFeature(DesignFontFeature.TNUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
      } else {
        Text(PointsText.myRankNone)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor($r('app.color.TEXT_SECONDARY'))
      }
      Blank()
    }
    .width('100%')
    .height(56)
    .alignItems(VerticalAlign.Center)
  }
}

/**
 * 流水行（56 行）：左 = reason 文案 + 时间；右 = delta（正绿负红，18/600 + tnum，
 * 对齐安卓 LedgerRow 与钱包流水页金额色先例）。
 */
@ComponentV2
struct LedgerRow {
  @Require @Param reason: string
  @Require @Param reasonDesc: string
  @Require @Param createdAt: string
  @Require @Param delta: number

  /** 时间展示（解析失败返回空串隐藏，不展示垃圾串；对齐安卓 formatLedgerTime null 隐藏） */
  private timeLabel(): string {
    return formatLedgerTime(this.createdAt) ?? ''
  }

  /** delta 色：正 = TEXT_GREEN 文字绿深档（白底 5.1:1 达 AA，对齐安卓 textGreen）/
   *  负 = DANGER_INK / 0 = 次文本 */
  private deltaColor(): ResourceColor {
    if (this.delta > 0) {
      return $r('app.color.TEXT_GREEN')
    }
    if (this.delta < 0) {
      return $r('app.color.DANGER_INK')
    }
    return $r('app.color.TEXT_SECONDARY')
  }

  build() {
    Row() {
      Column() {
        Text(pointsReasonLabel(this.reason, this.reasonDesc))
          .fontSize(DesignFontSize.BODY_SMALL)
          // 列表行主文案 14/500（对齐安卓 titleSmall，§3.4 特许配 MEDIUM）
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor($r('app.color.TEXT_PRIMARY'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        if (this.timeLabel() !== '') {
          Text(this.timeLabel())
            .fontSize(DesignFontSize.CAPTION)
            .fontFeature(DesignFontFeature.TNUM)
            // 时间戳是流水行唯一时序信息（可读正文），textTertiary 双板均 <4.5:1 触 §10 红线，
            // 改 textSecondary（对齐安卓 audit-20 #4）
            .fontColor($r('app.color.TEXT_SECONDARY'))
            .margin({ top: DesignDimens.SPACE_XXS })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .margin({ right: DesignDimens.SPACE_SM })

      Text(pointsDeltaText(this.delta))
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontFeature(DesignFontFeature.TNUM)
        .fontColor(this.deltaColor())
    }
    .width('100%')
    .height(56)
    .alignItems(VerticalAlign.Center)
  }
}

@ComponentV2
export struct PointsPage {
  /** 我的积分概览（余额/连签/最近流水），null = 首载未就绪 */
  @Local overview: PointsOverviewVo | null = null
  /** 概览首载失败标记（网络异常/401 拒绝 promise 会 throw，兜住进错误态） */
  @Local isError: boolean = false
  /** 概览首载中（骨架屏） */
  @Local isLoading: boolean = true
  /** 积分月榜（独立于概览加载，失败不拖垮整页） */
  @Local leaderboard: LeaderboardVo | null = null
  /** 榜单加载中（首载与重试且无数据时展示卡内骨架；签到后静默刷新不置位） */
  @Local leaderboardLoading: boolean = true
  /** 榜单加载失败（无数据时卡内小态 + 重试） */
  @Local leaderboardError: boolean = false
  /** 签到防重锁（CTA loading 禁点为第一层，后端幂等为最终兜底） */
  @Local signingIn: boolean = false

  aboutToAppear(): void {
    logger.debug('pointsPage', '签到有礼页挂载，并行拉取概览与月榜')
    this.loadOverview()
    this.loadLeaderboard()
  }

  /**
   * 拉取我的积分概览（首载/重试共用；失败进整页错误态给重试入口）。
   */
  private async loadOverview(): Promise<void> {
    this.isLoading = this.overview === null
    this.isError = false
    try {
      const res = await getPointsOverview()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.overview = res.data
        logger.debug('pointsPage', '积分概览加载成功')
      } else {
        logger.warn('pointsPage', `积分概览未生效 code=${res.code} message=${res.message ?? ''}`)
        if (this.overview === null) {
          this.isError = true
        }
        showToastSafety(res.message, PointsText.loadFailed)
      }
    } catch (err) {
      // 网络异常/401 拒绝：promise 会 throw，首载进错误态（已成功加载过则保留内容仅提示）
      const e: BusinessError = err as BusinessError
      logger.warn('pointsPage', `积分概览加载异常 err=${e.message}`)
      if (this.overview === null) {
        this.isError = true
      }
    }
    this.isLoading = false
  }

  /**
   * 拉取积分月榜（首载/重试/静默刷新共用；失败不污染整页，无数据时卡内小态）。
   * 开始时对「无数据」场景复位 loading——重试期间卡内恢复骨架反馈（对齐安卓 audit-20 #5：
   * VM 置 Loading 回骨架，不闪旧态/空榜）；entries 非空的重试不回骨架保留旧内容。
   */
  private async loadLeaderboard(): Promise<void> {
    this.leaderboardLoading = this.leaderboard === null || this.leaderboard.entries.length === 0
    try {
      const res = await getPointsLeaderboard()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.leaderboard = res.data
        this.leaderboardError = false
        logger.debug('pointsPage', '积分月榜加载成功')
      } else {
        logger.warn('pointsPage', `积分月榜未生效 code=${res.code} message=${res.message ?? ''}`)
        if (this.leaderboard === null) {
          this.leaderboardError = true
        }
      }
    } catch (err) {
      const e: BusinessError = err as BusinessError
      logger.warn('pointsPage', `积分月榜加载异常 err=${e.message}`)
      if (this.leaderboard === null) {
        this.leaderboardError = true
      }
    }
    this.leaderboardLoading = false
  }

  /**
   * 每日签到（防重：signingIn 上锁期间重复调用直接忽略）。
   * 成功 = 乐观并入概览（余额/连签/已签立即生效）+ toast 反馈 + 静默刷新概览与月榜
   * （流水与名次以后端为准，刷新失败保留乐观结果仅日志留痕，对齐安卓 refreshQuietly）。
   */
  private async onSignIn(): Promise<void> {
    if (this.signingIn) {
      return
    }
    this.signingIn = true
    try {
      const res = await signInToday()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const already: boolean = res.data.alreadySigned === true
        if (already) {
          showToastSafety(PointsText.signInAlready)
        } else {
          showToastSafety(formatText(PointsText.signInSuccess, `${res.data.pointsAwarded ?? 0}`))
        }
        // 乐观局部更新（幂等命中同样置已签，余额取签到后当前值）
        if (this.overview !== null) {
          this.overview = overviewAfterSignIn(this.overview, res.data)
        } else {
          // 概览缺失的极端场景（理论上不可达：错误态下 CTA 不渲染），保守拉一次概览
          await this.loadOverview()
        }
        await this.refreshQuietly()
        logger.info('pointsPage', `签到成功 already=${already}`)
      } else {
        logger.warn('pointsPage', `签到未生效 code=${res.code} message=${res.message ?? ''}`)
        showToastSafety(res.message, PointsText.signInFail)
      }
    } catch (err) {
      const e: BusinessError = err as BusinessError
      logger.warn('pointsPage', `签到异常 err=${e.message}`)
      showToastSafety(PointsText.signInFail)
    }
    this.signingIn = false
  }

  /**
   * 签到后静默刷新：概览（流水以服务端为准）+ 月榜（名次变化）。
   * 不置 loading、不回骨架，失败保留乐观结果仅日志留痕（对齐安卓 refreshQuietly）。
   */
  private async refreshQuietly(): Promise<void> {
    try {
      const res = await getPointsOverview()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.overview = res.data
      }
    } catch (err) {
      logger.warn('pointsPage', '签到后静默刷新概览失败（保留乐观结果）')
    }
    try {
      const res = await getPointsLeaderboard()
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        this.leaderboard = res.data
      }
    } catch (err) {
      logger.warn('pointsPage', '签到后静默刷新月榜失败')
    }
  }

  // ========== Hero 余额卡渲染辅助 ==========

  /** 余额大字（无账户/缺字段兜底 0） */
  private balanceText(): string {
    return `${this.overview?.balance ?? 0}`
  }

  /** 累计获得文案 */
  private totalEarnedText(): string {
    return formatText(PointsText.totalEarnedFormat, `${this.overview?.totalEarned ?? 0}`)
  }

  /** 今日已签（缺字段视为未签，保留签到入口） */
  private signedToday(): boolean {
    return this.overview?.signedToday === true
  }

  /** 连签行文案（streak<=0 返回空串不渲染整行，对齐安卓 safeStreak > 0 分支） */
  private streakText(): string {
    return streakProgressText(this.overview?.streak ?? null, this.overview?.nextBonusIn ?? null) ?? ''
  }

  /** 连签周期进度（0..1，进度条静态填充渲染用） */
  private cycleProgressRatio(): number {
    const cycle = streakCycleProgress(this.overview?.nextBonusIn ?? null)
    if (cycle.stepDays <= 0) {
      return 0
    }
    return cycle.currentInCycle / cycle.stepDays
  }

  /** 榜单月份角标（yyyyMM → yyyy年M月；解析失败返回空串不渲染） */
  private monthLabel(): string {
    const parsed: LeaderboardMonth | null = parseLeaderboardMonth(this.leaderboard?.month ?? null)
    if (parsed === null) {
      return ''
    }
    return `${parsed.year}年${parsed.month}月`
  }

  /** 榜单空态（无数据且非错误） */
  private leaderboardEmpty(): boolean {
    return this.leaderboardError === false && this.leaderboardLoading === false
      && (this.leaderboard === null || this.leaderboard.entries.length === 0)
  }

  /** 榜单骨架行下标（ArkTS 不在 build 内构造数组，收敛为方法，对齐 GameCenterPage） */
  private skeletonRows(): number[] {
    return [0, 1]
  }

  /** 流水列表（overview 缺失兜底空数组，收敛 build 内取链） */
  private ledgerItems(): Array<PointLedgerItemVo> {
    return this.overview?.recentLedger ?? []
  }

  // ========== 渲染 ==========

  /** 签到 CTA —— hero 白色胶囊（规范 §3.1 Hero 变体，对齐安卓 PointsHeroCard 自绘 CTA）：
   *  未签 = 纯白底 + 文字绿深档（TEXT_GREEN，安卓 AuthCtaText #177A4C 等值）15/600/tnum；
   *  已签 = 白 30%（GlassInput）底 + 白 80%（GLASS_TEXT_ACTIVE，近似安卓 OnImageSecondary
   *  白 78%）禁点；loading = 14vp 小 spinner + 文字 opacity 交叉淡变（安卓 CrossfadeAlt
   *  180ms 的 ArkUI 近似）。防重 = 已签/loading 条件禁点 + onSignIn 内 signingIn 上锁兜底。 */
  @Builder
  SignInCta() {
    Stack({ alignContent: Alignment.Center }) {
      LoadingProgress()
        .width(14)
        .height(14)
        .color($r('app.color.TEXT_GREEN'))
        .opacity(this.signingIn ? 1 : 0)
        .animation({ duration: 180, curve: Curve.EaseOut })

      Text(this.signedToday() ? PointsText.signedToday : PointsText.signIn)
        .fontSize(DesignFontSize.BUTTON)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontFeature(DesignFontFeature.TNUM)
        .fontColor(this.signedToday() ? $r('app.color.GLASS_TEXT_ACTIVE') : $r('app.color.TEXT_GREEN'))
        .opacity(this.signingIn ? 0 : 1)
        .animation({ duration: 180, curve: Curve.EaseOut })
    }
    .width('100%')
    .height(48)
    .borderRadius(DesignDimens.RADIUS_FULL)
    .backgroundColor(this.signedToday() ? $r('app.color.GLASS_INPUT') : Color.White)
    // CTA 上间距 16（对齐安卓 Spacer(spaceMd)，无连签块时为累计获得行与 CTA 的间隔）
    .margin({ top: DesignDimens.SPACE_MD })
    .onClick(() => {
      // 已签置灰禁点（pressable 条件挂载的安卓语义等价）+ loading 防重
      if (!this.signedToday() && !this.signingIn) {
        this.onSignIn()
      }
    })
  }

  @Builder
  HeroCard() {
    Stack() {
      Column() {
        Text(PointsText.balanceLabel)
          .fontSize(DesignFontSize.CAPTION)
          // hero 次级文字改纯白（对齐安卓 audit-20 #1 修后实现）：白 40% 压渐变起点仅 ~1.7:1，
          // 由黑纱保证 ≥4.5:1；字重对齐安卓 labelMedium 12/500
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(Color.White)

        Text(this.balanceText())
          .fontSize(DesignFontSize.DATA_LARGE)
          .lineHeight(DesignFontSize.DATA_LARGE + 8)
          .fontWeight(DesignFontWeight.BOLD)
          .fontFeature(DesignFontFeature.TNUM)
          .fontColor(Color.White)
          .margin({ top: DesignDimens.SPACE_XXS })

        Text(this.totalEarnedText())
          .fontSize(DesignFontSize.CAPTION)
          // 纯白（同上，白 40% 压渐变不可读）+ labelMedium 12/500
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(Color.White)
          .margin({ top: DesignDimens.SPACE_XXS })

        // 连签行 + 周期进度条（streak>0 才渲染，对齐安卓 PointsHeroCard 分支）
        if (this.streakText() !== '') {
          Text(this.streakText())
            .fontSize(DesignFontSize.CAPTION)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor(Color.White)
            .margin({ top: DesignDimens.SPACE_SM })

          Stack({ alignContent: Alignment.Start }) {
            // 轨道：恒定黑 18%（audit-20 #13：白 30% 轨道深板与白填充仅 ~1.6:1 近乎隐形，
            // 改后两板填充/轨道 ≥3:1 图形级）
            Row()
              .width('100%')
              .height(6)
              .borderRadius(DesignDimens.RADIUS_FULL)
              .backgroundColor($r('app.color.SCRIM_BLACK_18'))
            // 填充：白（静态，无动画负担）
            Row()
              .width(`${Math.round(this.cycleProgressRatio() * 100)}%`)
              .height(6)
              .borderRadius(DesignDimens.RADIUS_FULL)
              .backgroundColor(Color.White)
          }
          .width('100%')
          .margin({ top: DesignDimens.SPACE_XS })
        }

        this.SignInCta()
      }
      .width('100%')
      .padding(DesignDimens.CARD_PADDING)
      .alignItems(HorizontalAlign.Start)
      // 对角黑纱（左上黑 20% → 右下黑 35%，U-101 终版口径，对齐安卓 audit-20 #1 修后实现）：
      // 纱与渐变同对角线，浅板文字区 5.13–8.10:1 / 深板 4.62–5.13:1 全达标
      .linearGradient({
        angle: 135,
        colors: [[$r('app.color.SCRIM_BLACK_20'), 0], [$r('app.color.SCRIM_BLACK_35'), 1]]
      })
    }
    .width('100%')
    .borderRadius(DesignDimens.CARD_RADIUS)
    .clip(true)
    // 品牌渐变 135°（规范 §1.3 gradient.hero）：起点恒定 #2D9B6A（不走主题回写，
    // 防深色 PRIMARY 回写 #5BC894 后渐变两端塌缩成纯色）；终点 PRIMARY_DARK 深色自动
    // #5BC894，与安卓深色 heroGradientEnd 一致
    .linearGradient({
      angle: 135,
      colors: [[$r('app.color.HERO_GRADIENT_START'), 0], [$r('app.color.PRIMARY_DARK'), 1]]
    })
  }

  /** 榜单卡内统一行分隔线（发丝线只在同一容器内部行之间） */
  @Builder
  RowDivider() {
    Divider()
      .strokeWidth(0.5)
      .color($r('app.color.HAIRLINE'))
  }

  @Builder
  LeaderboardSection() {
    Column() {
      // 区块标题 + 月份角标（直接内联：@Builder 按值传参首帧定格，月份角标依赖
      // leaderboard 静默刷新后不更新的已知坑，参照 UserInfo.ets 校园卡行尾同款处理）
      Row() {
        Text(PointsText.leaderboardTitle)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
        Blank()
        if (this.monthLabel() !== '') {
          Text(this.monthLabel())
            .fontSize(DesignFontSize.CAPTION)
            // 月份是榜单口径信息非装饰角标，textTertiary 压页面底仅 2.08:1 触 §10 红线，
            // 改 textSecondary（对齐安卓 audit-20 #2）+ labelMedium 12/500
            .fontWeight(DesignFontWeight.MEDIUM)
            .fontColor($r('app.color.TEXT_SECONDARY'))
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Column() {
        if (this.leaderboardLoading && (this.leaderboard === null || this.leaderboard.entries.length === 0)) {
          // 首载/重试进行中且无数据：骨架行占位（禁止转圈，规范 §3.6；分支先于错误态判定，
          // 重试期间回骨架不闪旧错误态，对齐安卓 audit-20 #5）。行形状对齐真实榜单行
          //（§3.6 骨架形状 = 内容形状）：28 圆（名次/头像位）+ 权重条（手机号位）+ 宽块（积分位）
          Column() {
            ForEach(this.skeletonRows(), (row: number) => {
              Row() {
                SkeletonBlock({ blockRadius: 14 })
                  .width(28)
                  .height(28)
                SkeletonBlock()
                  .height(DesignDimens.SPACE_MD)
                  .layoutWeight(1)
                  .margin({ left: DesignDimens.SPACE_SM })
                SkeletonBlock()
                  .width(DesignDimens.SPACE_XL)
                  .height(DesignDimens.SPACE_MD)
                  .margin({ left: DesignDimens.SPACE_MD })
              }
              .width('100%')
              .height(56)
              .alignItems(VerticalAlign.Center)
            }, (row: number) => row.toString())
          }
          .width('100%')
          .padding({ top: DesignDimens.CARD_PADDING, bottom: DesignDimens.CARD_PADDING })
        } else if (this.leaderboardError && (this.leaderboard === null || this.leaderboard.entries.length === 0)) {
          // 首载失败且无数据：卡内错误小态（不伪装成空榜）；垂直留白由内层给（水平缩进
          // 由卡容器统一给，防双重缩进）
          Column() {
            Text(PointsText.leaderboardLoadFailed)
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor($r('app.color.TEXT_SECONDARY'))
            AppButton({
              text: PointsText.actionRetry,
              variant: AppButtonVariant.SECONDARY,
              click: (): void => {
                this.loadLeaderboard()
              }
            })
              .margin({ top: DesignDimens.SPACE_MD })
          }
          .width('100%')
          .padding({ top: DesignDimens.CARD_PADDING, bottom: DesignDimens.CARD_PADDING })
          .alignItems(HorizontalAlign.Center)
        } else if (this.leaderboardEmpty()) {
          // 空榜：滚筒母题空态
          EmptyState({ message: PointsText.leaderboardEmpty })
        } else {
          // TOP 榜 + 我的排名行（固定卡底）
          ForEach(this.leaderboard?.entries ?? [], (entry: LeaderboardEntryVo, index: number) => {
            if (index > 0) {
              this.RowDivider()
            }
            LeaderboardRow({ entry: entry })
          }, (entry: LeaderboardEntryVo, index: number) => `${index}_${entry.rank ?? 0}_${entry.maskedPhone ?? ''}`)
          this.RowDivider()
          MyRankRow({
            myRank: this.leaderboard?.my?.rank ?? null,
            myPoints: this.leaderboard?.my?.points ?? null
          })
        }
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_XS })
      // 行内容距描边 20（对齐安卓 GroupCard contentPadding horizontal cardPadding）：
      // 名次/reason 文字不得贴 1px 描边；发丝线随 padding 收窄至卡内容宽
      .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
      .backgroundColor($r('app.color.SURFACE'))
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 1, color: $r('app.color.OUTLINE') })
      .shadow({
        radius: DesignElevation.CARD_SHADOW_RADIUS,
        color: $r('app.color.SHADOW_COLOR_CARD'),
        offsetX: 0,
        offsetY: DesignElevation.CARD_SHADOW_OFFSET
      })
      // 空态等内嵌态给上下留白（EmptyState 自带垂直 padding，卡不额外加）
      .clip(true)
    }
    .width('100%')
    // 区块间距 32（对齐安卓 sectionSpacing；@Builder 调用返回 void 不能链属性，
    // 间距收敛进 Builder 内部根容器）
    .margin({ top: DesignDimens.SPACE_XXL })
  }

  @Builder
  LedgerSection() {
    Column() {
      // 区块标题（无角标；同上内联，规避 @Builder 按值传参定格坑）
      Row() {
        Text(PointsText.ledgerTitle)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor($r('app.color.TEXT_PRIMARY'))
        Blank()
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      Column() {
        if (this.ledgerItems().length === 0) {
          // 空流水：卡内滚筒母题空态引导（对齐安卓 audit-20 #7，points_ledger_empty 文案；
          // 区块恒驻不再整块隐藏，新用户也有签到引导）
          EmptyState({ message: PointsText.ledgerEmpty })
        } else {
          ForEach(this.ledgerItems(), (item: PointLedgerItemVo, index: number) => {
            if (index > 0) {
              this.RowDivider()
            }
            LedgerRow({
              reason: item.reason ?? '',
              reasonDesc: item.reasonDesc ?? '',
              createdAt: item.createdAt ?? '',
              delta: item.delta ?? 0
            })
          }, (item: PointLedgerItemVo, index: number) => `${index}_${item.createdAt ?? ''}_${item.delta ?? 0}`)
        }
      }
      .width('100%')
      .margin({ top: DesignDimens.SPACE_XS })
      // 行内容距描边 20（对齐安卓 GroupCard contentPadding horizontal cardPadding）
      .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
      .backgroundColor($r('app.color.SURFACE'))
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 1, color: $r('app.color.OUTLINE') })
      .shadow({
        radius: DesignElevation.CARD_SHADOW_RADIUS,
        color: $r('app.color.SHADOW_COLOR_CARD'),
        offsetX: 0,
        offsetY: DesignElevation.CARD_SHADOW_OFFSET
      })
      .clip(true)
    }
    .width('100%')
    // 区块间距 32（对齐安卓 sectionSpacing）
    .margin({ top: DesignDimens.SPACE_XXL })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容（机制见 ThemeRefresh.ets）
      ThemeRefreshV2() {
        Column() {
          PageHeader({ title: PointsText.title })

          if (this.isLoading) {
            // 首载骨架（概览未就绪；签到后静默刷新不回骨架——overview 非空即保留内容）
            LoadingState()
              .width('100%')
              .layoutWeight(1)
          } else if (this.isError) {
            // 首载失败：说明发生了什么 + 重试（对齐安卓 PointsContentState.Error）
            Column() {
              EmptyState({ message: PointsText.loadFailed }) {
                AppButton({
                  small: true,
                  text: PointsText.actionRetry,
                  variant: AppButtonVariant.SECONDARY,
                  click: (): void => {
                    this.loadOverview()
                  }
                })
                  .margin({ top: DesignDimens.SPACE_MD })
              }
            }
            .width('100%')
            .layoutWeight(1)
            .justifyContent(FlexAlign.Center)
          } else {
            Scroll() {
              Column() {
                this.HeroCard()
                this.LeaderboardSection()
                // 最近流水（append-only 时间倒序）：区块恒驻，空时卡内空态引导
                //（对齐安卓 audit-20 #7，条件渲染整块消失会导致新用户页面无引导）
                this.LedgerSection()
              }
              .width('100%')
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                // 顶距 12：对齐安卓 PageHeader 节奏补偿先例（安卓 top=spaceSm）
                top: DesignDimens.SPACE_SM,
                // 底部 = 导航条高度 + 24 基数（对齐安卓 spaceXl + navigationBars，
                // 无导航条设备末卡不贴屏幕底）
                bottom: safeBottomVp() + DesignDimens.SPACE_XL
              })
            }
            .width('100%')
            .height('100%')
            .scrollBar(BarState.Off)
            .align(Alignment.TopStart)
          }
        }
        .width('100%')
        .height('100%')
        // 沉浸式：内容避让状态栏（背景延伸）
        .padding({ top: safeTopVp() })
        .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .width('100%')
    .height('100%')
    .align(Alignment.Top)
    .backgroundColor($r('app.color.BACKGROUND'))
    .hideTitleBar(true)
  }
}
