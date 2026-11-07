// 月度趋势页 — 对齐安卓 feature/mood/ui/trend/MoodTrendPage.kt
//
// 等级分布条形（stats.distribution，缺的等级端上补 0）+ 记录天数 + 月均值。
// 空月（recordedDays=0）走空态 + 「去记录今天」行动按钮（规范 §3.6）。
//
// 跨页刷新：保存今日心情后 bump 情绪 tick，本页 @StorageLink + @Watch 消费重拉
// （今日 → 趋势空态 → 去记录 → 保存 → 返回趋势，趋势需反映新记录）。

import { RouteNames } from '../../../constant/RouteNames'
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from '../../../constant/DesignSystem'
import { BizCode } from '../../../network/HttpUtil'
import { MOOD_LEVEL_MAX, MOOD_LEVEL_MIN, MoodLevelCountVo, MoodText, moodMonthStats } from '../api'
import {
  MoodDistributionBar,
  MoodErrorState,
  MoodMonthSwitcher,
  MoodSmallAction,
  MoodStatCell,
  currentYm,
  formatMonthText,
  moodLevelColor,
  moodLevelLabel,
  shiftYm
} from './MoodWidgets'
import { AppCard, EmptyState, SkeletonList } from '../../../view/common/AppComponents'
import { logger } from '../../../utils/Logger'
import { pathStack } from '../../../utils/PathStackUtil'
import { MOOD_TICK_KEY } from '../../../utils/RefreshSignal'
import { showToastSafety } from '../../../utils/ToastUtil'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'

/** 日志 TAG（Logger 统一拼接 SmartWash/ 前缀） */
const TAG: string = 'moodTrendPage'

/** 分布条形渲染模型（占比在 build 外预计算，避免在 UI 语法内写语句） */
interface MoodBarModel {
  level: number
  label: string
  color: number
  count: number
  fraction: number
}

@Builder
export function MoodTrendBuilder() {
  MoodTrendPage()
}

@Component
struct MoodTrendPage {
  @State ym: string = currentYm()
  @State distribution: Array<MoodLevelCountVo> = []
  @State recordedDays: number = 0
  @State averageLevel: number | null = null
  @State isLoading: boolean = true
  @State isError: boolean = false
  // 跨页刷新信号（自增 tick）：保存今日心情后 bump，返回本页时重拉
  @StorageLink(MOOD_TICK_KEY) @Watch('onMoodTick') moodTick: number = 0

  async aboutToAppear(): Promise<void> {
    await this.load(this.ym)
  }

  /** 消费情绪变更 tick：重拉当前月（自增保证每次变更都触发） */
  private onMoodTick(): void {
    logger.debug(TAG, '消费到情绪变更信号，重拉趋势')
    this.load(this.ym)
  }

  /** 拉当月统计：distribution 按等级升序补全 1..5（契约只含出现过的等级） */
  async load(ym: string): Promise<void> {
    this.isLoading = true
    this.isError = false
    try {
      const res = await moodMonthStats(ym)
      if (res.code === BizCode.SUCCESS && res.data !== null) {
        const stats = res.data
        const counts = new Map<number, number>()
        for (const item of stats.distribution ?? []) {
          if (item.level !== undefined) {
            counts.set(item.level, item.recordCount ?? 0)
          }
        }
        const full: Array<MoodLevelCountVo> = []
        for (let level = MOOD_LEVEL_MIN; level <= MOOD_LEVEL_MAX; level++) {
          full.push({ level: level, recordCount: counts.get(level) ?? 0 })
        }
        this.distribution = full
        this.recordedDays = stats.recordedDays ?? 0
        this.averageLevel = stats.averageLevel ?? null
        this.isError = false
        logger.debug(TAG, `趋势加载成功 ym=${ym} days=${this.recordedDays} avg=${this.averageLevel ?? 'null'}`)
      } else {
        logger.warn(TAG, `趋势加载失败 ym=${ym} code=${res.code ?? 0} msg=${res.message ?? ''}`)
        this.isError = true
        showToastSafety(res.message, MoodText.mood_trend_error_load)
      }
    } catch (err) {
      logger.error(TAG, '趋势加载异常', err instanceof Error ? err.message : '')
      this.isError = true
      showToastSafety('', MoodText.mood_trend_error_load)
    } finally {
      this.isLoading = false
    }
  }

  /** 切月（delta 正=往后，负=往前） */
  private shiftMonth(delta: number): void {
    this.ym = shiftYm(this.ym, delta)
    this.load(this.ym)
  }

  /** 分布条形最大计数（用于占比分母） */
  private maxCount(): number {
    let max = 0
    for (const item of this.distribution) {
      const count = item.recordCount ?? 0
      if (count > max) {
        max = count
      }
    }
    return max
  }

  private averageText(): string {
    return this.averageLevel !== null ? this.averageLevel.toFixed(2) : MoodText.mood_trend_value_none
  }

  /** 分布条形渲染模型（1..5 全量，占比按最大计数归一） */
  private barModels(): Array<MoodBarModel> {
    const max = this.maxCount()
    const models: Array<MoodBarModel> = []
    for (const item of this.distribution) {
      const level = item.level ?? 0
      const count = item.recordCount ?? 0
      models.push({
        level: level,
        label: moodLevelLabel(level),
        color: moodLevelColor(level),
        count: count,
        fraction: max > 0 ? count / max : 0
      })
    }
    return models
  }

  build() {
    NavDestination() {
      Column() {
        // 页面标题（对齐安卓 PageHeader）
        Row() {
          Column() {
            SymbolGlyph($r('sys.symbol.chevron_left'))
              .fontSize(24)
              .fontColor([DesignColor.TEXT_PRIMARY])
          }
          .width(DesignDimens.BACK_HIT_AREA)
          .height(DesignDimens.BACK_HIT_AREA)
          .justifyContent(FlexAlign.Center)
          .alignItems(HorizontalAlign.Center)
          .onClick(() => {
            pathStack.pop()
          })

          Text(MoodText.mood_trend_title)
            .fontSize(DesignFontSize.ROOT_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)
        }
        .width('100%')
        .height(56)
        .padding({ left: DesignDimens.SPACE_SM, right: DesignDimens.PAGE_PADDING, top: 2, bottom: 6 })
        .alignItems(VerticalAlign.Center)

        if (this.isLoading) {
          SkeletonList({ rows: 4 })
            .width('100%')
            .layoutWeight(1)
        } else if (this.isError) {
          MoodErrorState({
            message: MoodText.mood_trend_error_load,
            onRetry: (): void => {
              this.load(this.ym)
            }
          })
            .width('100%')
            .layoutWeight(1)
        } else if (this.recordedDays <= 0) {
          // 空月：滚筒圆母题 + 引导 + 行动按钮（规范 §3.6）
          Column() {
            MoodMonthSwitcher({
              monthText: formatMonthText(this.ym),
              onPrev: (): void => {
                this.shiftMonth(-1)
              },
              onNext: (): void => {
                this.shiftMonth(1)
              }
            })

            EmptyState({ message: MoodText.mood_trend_empty_message }) {
              Column() {
                MoodSmallAction({
                  text: MoodText.mood_trend_empty_action,
                  containerColor: DesignColor.PRIMARY_LIGHT,
                  contentColor: DesignColor.PRIMARY,
                  onAction: (): void => {
                    pathStack.pushPathByName(RouteNames.MOOD_TODAY, null)
                  }
                })
              }
              .margin({ top: DesignDimens.SPACE_MD })
            }
          }
          .width('100%')
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_LG + safeBottomVp()
          })
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
        } else {
          Column() {
            MoodMonthSwitcher({
              monthText: formatMonthText(this.ym),
              onPrev: (): void => {
                this.shiftMonth(-1)
              },
              onNext: (): void => {
                this.shiftMonth(1)
              }
            })

            // ---------- 记录天数 + 月均值（月均值为数据大字锚点，tnum） ----------
            AppCard() {
              Row() {
                MoodStatCell({
                  label: MoodText.mood_trend_stats_days,
                  value: `${this.recordedDays}`,
                  suffix: MoodText.mood_trend_days_unit,
                  valueSize: DesignFontSize.AMOUNT_MEDIUM
                })
                  .layoutWeight(1)

                MoodStatCell({
                  label: MoodText.mood_trend_stats_average,
                  value: this.averageText()
                })
                  .layoutWeight(1)
              }
              .width('100%')
              .alignItems(VerticalAlign.Bottom)
            }

            // ---------- 等级分布条形（1..5 全量补 0，缺级显 0） ----------
            AppCard() {
              Column() {
                Text(MoodText.mood_trend_distribution_title)
                  .fontSize(DesignFontSize.BODY)
                  .fontWeight(DesignFontWeight.SEMI_BOLD)
                  .fontColor(DesignColor.TEXT_PRIMARY)

                Column({ space: DesignDimens.SPACE_SM }) {
                  ForEach(this.barModels(), (model: MoodBarModel) => {
                    MoodDistributionBar({
                      label: model.label,
                      color: model.color,
                      count: model.count,
                      fraction: model.fraction
                    })
                  }, (model: MoodBarModel) => `${model.level}`)
                }
                .margin({ top: DesignDimens.SPACE_SM })
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
            }
          }
          .width('100%')
          .padding({
            left: DesignDimens.PAGE_PADDING,
            right: DesignDimens.PAGE_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_LG + safeBottomVp()
          })
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
        }
      }
      .width('100%')
      .height('100%')
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}
