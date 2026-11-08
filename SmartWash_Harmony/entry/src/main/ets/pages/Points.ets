/**
 * 签到有礼页 —— 扩展提案功能 17/18（双端闭环鸿蒙侧），对齐安卓
 * feature/points/impl/.../ui/points/PointsPage.kt 单页三分区结构：
 *
 * ① Hero 余额卡（品牌渐变 + 黑纱，余额大字 = 每页唯一锚点 + 累计获得 + 连签进度 +
 *    签到 CTA：AppButton 已签置灰「已签到」/ loading 防重）；
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
import {
  DesignColor,
  DesignDimens,
  DesignElevation,
  DesignFontSize,
  DesignFontFeature,
  DesignFontWeight
} from '../constant/DesignSystem'
import { safeBottomVp, safeTopVp } from '../utils/SafeArea'
import { showToastSafety } from '../utils/ToastUtil'
import { logger } from '../utils/Logger'
import { BusinessError } from '@kit.BasicServicesKit'
import { AppButton, AppButtonVariant, DrumMark, EmptyState, LoadingState, PageHeader } from '../view/common/AppComponents'
import { ThemeRefreshV2 } from '../view/common/ThemeRefresh'

/** 页面文案（对齐安卓 feature/points/impl res/values/strings.xml） */
class PointsText {
  static readonly title: string = '签到有礼'
  static readonly balanceLabel: string = '当前积分'
  static readonly totalEarnedFormat: string = '累计获得 {0} 积分'
  static readonly signIn: string = '签到'
  static readonly signedToday: string = '已签到'
  static readonly signInSuccess: string = '签到成功 +{0} 积分'
  static readonly signInAlready: string = '今日已签到，明天再来吧'
  static readonly signInFail: string = '签到失败'
  static readonly leaderboardTitle: string = '本月排行榜'
  static readonly leaderboardLoadFailed: string = '排行榜加载失败，请检查网络后重试'
  static readonly leaderboardEmpty: string = '本月还没有人上榜，签到赢积分'
  static readonly myRankFormat: string = '我的排名 #{0} · {1} 积分'
  static readonly myRankNone: string = '本月暂未上榜，签到赢积分'
  static readonly ledgerTitle: string = '积分明细'
  static readonly loadFailed: string = '积分信息加载失败'
  static readonly actionRetry: string = '重试'
  static readonly unit: string = '积分'
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

  /** 名次色：TOP3 品牌色强调，其余中性（对齐安卓 LeaderboardRow） */
  private rankColor(): number {
    const tier: RankTier = rankTier(this.entry.rank ?? null)
    return tier === RankTier.NORMAL ? DesignColor.TEXT_SECONDARY : DesignColor.PRIMARY
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
          DrumMark({ sizeValue: 16, tint: DesignColor.PRIMARY_DARK })
        }
        .width(28)
        .height(28)
        .borderRadius(14)
        .backgroundColor(DesignColor.PRIMARY_LIGHT)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .margin({ left: DesignDimens.SPACE_SM })
      }

      Text(this.entry.maskedPhone ?? '')
        .fontSize(DesignFontSize.BODY_SMALL)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .layoutWeight(1)
        .margin({ left: DesignDimens.SPACE_SM })

      Text(`${this.entry.points ?? 0}`)
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontFeature(DesignFontFeature.TNUM)
        .fontColor(DesignColor.PRIMARY_DARK)

      Text(PointsText.unit)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_TERTIARY)
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
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontFeature(DesignFontFeature.TNUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
      } else {
        Text(PointsText.myRankNone)
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_SECONDARY)
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

  /** delta 色：正 = SUCCESS 绿（大字号允许 3.5:1，对齐钱包流水先例）/ 负 = DANGER_INK / 0 = 次文本 */
  private deltaColor(): number {
    if (this.delta > 0) {
      return DesignColor.SUCCESS
    }
    if (this.delta < 0) {
      return DesignColor.DANGER_INK
    }
    return DesignColor.TEXT_SECONDARY
  }

  build() {
    Row() {
      Column() {
        Text(pointsReasonLabel(this.reason, this.reasonDesc))
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        if (this.timeLabel() !== '') {
          Text(this.timeLabel())
            .fontSize(DesignFontSize.CAPTION)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor(DesignColor.TEXT_TERTIARY)
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
  /** 榜单加载中（仅首载展示卡内小态；签到后静默刷新不置位） */
  @Local leaderboardLoading: boolean = true
  /** 榜单加载失败（无数据时卡内小态 + 重试） */
  @Local leaderboardError: boolean = false
  /** 签到防重锁（AppButton loading 禁点为第一层，后端幂等为最终兜底） */
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
   */
  private async loadLeaderboard(): Promise<void> {
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
    return [0, 1, 2]
  }

  /** 流水列表（overview 缺失兜底空数组，收敛 build 内取链） */
  private ledgerItems(): Array<PointLedgerItemVo> {
    return this.overview?.recentLedger ?? []
  }

  // ========== 渲染 ==========

  /** 签到 CTA —— AppButton 次变体 + 品牌深绿字（hero 渐变卡上的近似白胶囊形态）；
   *  已签置灰禁点（AppButton isEnabled=false 整钮降透明 + 禁点），loading 防重内置。 */
  @Builder
  SignInCta() {
    AppButton({
      text: this.signedToday() ? PointsText.signedToday : PointsText.signIn,
      isEnabled: !this.signedToday(),
      loading: this.signingIn,
      variant: AppButtonVariant.SECONDARY,
      contentColor: DesignColor.PRIMARY_DARK,
      click: (): void => {
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
          .fontColor(DesignColor.GLASS_TEXT_SECONDARY)

        Text(this.balanceText())
          .fontSize(DesignFontSize.DATA_LARGE)
          .lineHeight(DesignFontSize.DATA_LARGE + 8)
          .fontWeight(DesignFontWeight.BOLD)
          .fontFeature(DesignFontFeature.TNUM)
          .fontColor(Color.White)
          .margin({ top: DesignDimens.SPACE_XXS })

        Text(this.totalEarnedText())
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.GLASS_TEXT_SECONDARY)
          .margin({ top: DesignDimens.SPACE_XXS })

        // 连签行 + 周期进度条（streak>0 才渲染，对齐安卓 PointsHeroCard 分支）
        if (this.streakText() !== '') {
          Text(this.streakText())
            .fontSize(DesignFontSize.CAPTION)
            .fontFeature(DesignFontFeature.TNUM)
            .fontColor(DesignColor.GLASS_TEXT_SECONDARY)
            .margin({ top: DesignDimens.SPACE_SM })

          Stack({ alignContent: Alignment.Start }) {
            // 轨道：白 30%（对齐安卓 GlassInput）
            Row()
              .width('100%')
              .height(6)
              .borderRadius(DesignDimens.RADIUS_FULL)
              .backgroundColor(DesignColor.GLASS_INPUT)
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
      // 对角黑纱（左上黑 20% → 右下透明）：白字 on 品牌绿渐变仅 3.72:1，
      // 叠纱后文字区 ≥5.1:1（对齐 view/HeroCard.ets 同款可读性修正）
      .linearGradient({
        angle: 135,
        colors: [[DesignColor.SCRIM_BLACK_20, 0], [Color.Transparent, 1]]
      })
    }
    .width('100%')
    .borderRadius(DesignDimens.CARD_RADIUS)
    .clip(true)
    // 品牌渐变 135°（规范 §1.3 gradient.hero，对齐安卓 heroGradientStart/End）
    .linearGradient({
      angle: 135,
      colors: [[DesignColor.PRIMARY, 0], [DesignColor.PRIMARY_DARK, 1]]
    })
  }

  /** 榜单卡内统一行分隔线（发丝线只在同一容器内部行之间） */
  @Builder
  RowDivider() {
    Divider()
      .strokeWidth(0.5)
      .color(DesignColor.HAIRLINE)
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
          .fontColor(DesignColor.TEXT_PRIMARY)
        Blank()
        if (this.monthLabel() !== '') {
          Text(this.monthLabel())
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_TERTIARY)
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      Column() {
        if (this.leaderboardError && (this.leaderboard === null || this.leaderboard.entries.length === 0)) {
          // 首载失败且无数据：卡内错误小态（不伪装成空榜）
          Column() {
            Text(PointsText.leaderboardLoadFailed)
              .fontSize(DesignFontSize.BODY_SMALL)
              .fontColor(DesignColor.TEXT_SECONDARY)
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
          .padding(DesignDimens.CARD_PADDING)
          .alignItems(HorizontalAlign.Center)
        } else if (this.leaderboardLoading && (this.leaderboard === null || this.leaderboard.entries.length === 0)) {
          // 首载中：骨架行占位（禁止转圈，规范 §3.6）
          Column({ space: DesignDimens.SPACE_SM }) {
            ForEach(this.skeletonRows(), (row: number) => {
              Row()
                .width('100%')
                .height(48)
                .borderRadius(DesignDimens.RADIUS_MD)
                .backgroundColor(DesignColor.SURFACE_VARIANT)
            }, (row: number) => row.toString())
          }
          .width('100%')
          .padding(DesignDimens.CARD_PADDING)
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
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 1, color: DesignColor.OUTLINE })
      .shadow({
        radius: DesignElevation.CARD_SHADOW_RADIUS,
        color: DesignColor.SHADOW_COLOR_CARD,
        offsetX: 0,
        offsetY: DesignElevation.CARD_SHADOW_OFFSET
      })
      // 空态等内嵌态给上下留白（EmptyState 自带垂直 padding，卡不额外加）
      .clip(true)
    }
    .width('100%')
    // 卡间距（@Builder 调用返回 void 不能链属性，间距收敛进 Builder 内部根容器）
    .margin({ top: DesignDimens.SPACE_LG })
  }

  @Builder
  LedgerSection() {
    Column() {
      // 区块标题（无角标；同上内联，规避 @Builder 按值传参定格坑）
      Row() {
        Text(PointsText.ledgerTitle)
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
        Blank()
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      Column() {
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
      .width('100%')
      .margin({ top: DesignDimens.SPACE_XS })
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 1, color: DesignColor.OUTLINE })
      .shadow({
        radius: DesignElevation.CARD_SHADOW_RADIUS,
        color: DesignColor.SHADOW_COLOR_CARD,
        offsetX: 0,
        offsetY: DesignElevation.CARD_SHADOW_OFFSET
      })
      .clip(true)
    }
    .width('100%')
    .margin({ top: DesignDimens.SPACE_LG })
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
                // 最近流水（append-only 时间倒序）：无记录整块不渲染，对齐安卓 ledger.isNotEmpty 分支
                if (this.overview !== null && this.overview.recentLedger.length > 0) {
                  this.LedgerSection()
                }
              }
              .width('100%')
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                top: DesignDimens.SPACE_XS,
                bottom: safeBottomVp()
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
        .backgroundColor(DesignColor.BACKGROUND)
      }
    }
    .width('100%')
    .height('100%')
    .align(Alignment.Top)
    .backgroundColor(DesignColor.BACKGROUND)
    .hideTitleBar(true)
  }
}
