/**
 * 盘感页（占卜闭环三）—— 卦例统计复盘。
 *
 * 蓝本：安卓 ui/page/stats/DivStatsPage.kt + DivStatsViewModel.kt。
 * 结构：数字卡（总卦数/已反馈/应验率%）+ 三张图表 —— 全部 Canvas 自绘
 * （清氧规范：禁图表库；术数/类别为横向条形，月度趋势为纵向条形），零新增依赖。
 * 口径：verifyRate 为比例 0-1，端上 ×100 取整展示（>1 视为后端已给百分数，防御性兼容）。
 */

import { DivStatsVo, getDivStats, DivNameCountVo, DivMonthCountVo } from '../../../network/DivinationApi'
import { DivLoopHint, DivLoopText } from '../../components/DivLoopComponents'
import {
  DivCard,
  DivFooterNote,
  DivGoldButton,
  DivPageHeader,
  DivPalette,
  DivSectionTitle,
  DivSerifText
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { DesignDimens } from '../../../../../constant/DesignSystem'
import { pathStack } from '../../../../../utils/PathStackUtil'
import { logger } from '../../../../../utils/Logger'

/** 盘感页状态（对齐安卓 RequestState） */
type StatsState = 'loading' | 'success' | 'error'

@Builder
export function DivStatsBuilder() {
  DivStatsPage()
}

@ComponentV2
export struct DivStatsPage {
  @Local stats: DivStatsVo | null = null
  @Local phase: StatsState = 'loading'

  aboutToAppear(): void {
    this.load()
  }

  private async load(): Promise<void> {
    this.phase = 'loading'
    try {
      const resp = await getDivStats()
      if (resp.code === 200 && resp.data !== null) {
        this.stats = resp.data
        this.phase = 'success'
      } else {
        this.stats = null
        this.phase = 'error'
        logger.warn('divStatsPage', `盘感拉取业务失败 code=${resp.code}`)
      }
    } catch (err) {
      this.stats = null
      this.phase = 'error'
      logger.warn('divStatsPage', `盘感拉取失败, ${err instanceof Error ? err.message : ''}`)
    }
  }

  /** 应验率展示值：契约 verifyRate 为比例 0-1；>1 视为后端已给百分数，防御性兼容（对齐安卓） */
  private verifyRatePercent(): number {
    if (this.stats === null) {
      return 0
    }
    const rate = this.stats.verifyRate
    return Math.round(rate > 1 ? rate : rate * 100)
  }

  build() {
    NavDestination() {
      DivinationScreen() {
        Column() {
          DivPageHeader({
            title: DivLoopText.statsTitle,
            alignStart: true,
            onBack: () => {
              pathStack.pop()
            }
          })

          if (this.phase === 'loading') {
            DivLoopHint({ seal: '盘', text: DivLoopText.statsLoading })
          } else if (this.phase === 'error') {
            Column() {
              DivLoopHint({ seal: '盘', text: DivLoopText.statsError })
              DivGoldButton({
                text: DivLoopText.statsRetry,
                onAction: () => {
                  this.load()
                }
              })
                .margin({
                  left: 40,
                  right: 40,
                  top: DesignDimens.SPACE_LG
                })
            }
            .width('100%')
          } else if (this.stats === null) {
            DivLoopHint({ seal: '盘', text: DivLoopText.statsEmpty })
          } else {
            // 顶部数字卡（画板：三卡并排，卡内数字衬线 22 / 标签 12）
            Row({ space: DesignDimens.SPACE_XS }) {
              StatNumberCard({
                label: DivLoopText.statsTotal,
                valueText: `${this.stats.totalRecords}`
              })
                .layoutWeight(1)
              StatNumberCard({
                label: DivLoopText.statsFeedback,
                valueText: `${this.stats.feedbackTotal}`
              })
                .layoutWeight(1)
              StatNumberCard({
                label: DivLoopText.statsVerify,
                valueText: `${this.verifyRatePercent()}`,
                suffix: '%'
              })
                .layoutWeight(1)
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            // 术数分布（横向条形：名称 + 金条 + 数量）
            DivSectionTitle({ title: DivLoopText.statsMethodTitle })
            Column() {
              DivCard() {
                HBarChart({ rows: this.stats.methodCounts })
                  .padding(DesignDimens.CARD_PADDING)
              }
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            // 月度趋势（纵向条形：单 Canvas 画多柱 + 底部月标签行）
            DivSectionTitle({ title: DivLoopText.statsMonthTitle })
            Column() {
              DivCard() {
                MonthTrendChart({ trend: this.stats.monthTrend })
                  .padding(DesignDimens.CARD_PADDING)
              }
            }
            .width('100%')
            .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            // 所问领域分布（横向条形）
            DivSectionTitle({ title: DivLoopText.statsCategoryTitle })
            Column() {
              DivCard() {
                HBarChart({ rows: this.stats.categoryCounts })
                  .padding(DesignDimens.CARD_PADDING)
              }
            }
            .width('100%')
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              bottom: DesignDimens.SPACE_XS
            })
          }

          DivFooterNote({ text: DivLoopText.statsNote })
        }
        .width('100%')
      }
    }
    .hideTitleBar(true)
  }
}

// ========== 数字卡：大数字（衬线）+ 标签；suffix 承载百分号 ==========

@ComponentV2
struct StatNumberCard {
  @Param label: string = ''
  @Param valueText: string = ''
  @Param suffix: string = ''

  build() {
    DivCard() {
      Column() {
        Row() {
          DivSerifText({
            text: this.valueText,
            textSize: 22,
            weight: 700,
            spacing: 0,
            color: DivPalette.textPrimary
          })
          if (this.suffix.length > 0) {
            DivSerifText({
              text: this.suffix,
              textSize: 12,
              weight: 400,
              spacing: 0,
              color: DivPalette.textSecondary
            })
              .margin({ left: 2, bottom: 3 })
          }
        }
        .alignItems(VerticalAlign.Bottom)

        DivSerifText({
          text: this.label,
          textSize: 12,
          weight: 400,
          spacing: 0,
          color: DivPalette.textSecondary
        })
          .margin({ top: 6 })
      }
      .width('100%')
      .alignItems(HorizontalAlign.Center)
      .padding({ top: 14, bottom: 14 })
    }
  }
}

// ========== 横向条形图：名称 + 金条（gold14 轨道 + gold 实条，Canvas 自绘）+ 数量 ==========

@ComponentV2
struct HBarChart {
  @Param rows: Array<DivNameCountVo> = []

  private maxCount(): number {
    let max: number = 1
    for (let i = 0; i < this.rows.length; i++) {
      max = Math.max(max, this.rows[i].count)
    }
    return max
  }

  build() {
    if (this.rows.length === 0) {
      Text(DivLoopText.statsEmptyChart)
        .fontSize(12)
        .fontColor(DivPalette.textTertiary)
        .textAlign(TextAlign.Center)
        .width('100%')
        .padding({ top: 12, bottom: 12 })
    } else {
      Column({ space: 10 }) {
        ForEach(this.rows, (row: DivNameCountVo) => {
          Row() {
            Text(row.name ?? '')
              .fontSize(12)
              .fontColor(DivPalette.textSecondary)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .width(52)
            HBarCanvas({ fraction: row.count / this.maxCount() })
              .layoutWeight(1)
              .margin({ left: 8, right: 8 })
            Text(`${row.count}`)
              .fontSize(12)
              .fontColor(DivPalette.textPrimary)
              .textAlign(TextAlign.End)
              .width(34)
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
        }, (row: DivNameCountVo) => `${row.name ?? ''}-${row.count}`)
      }
      .width('100%')
    }
  }
}

/** 圆角矩形填充（roundRect 需 SDK 20，当前 17，用 arcTo 手绘，同 DivComponents.strokeRoundRect 口径） */
function fillRoundRect(ctx: CanvasRenderingContext2D, x: number, y: number,
  w: number, h: number, r: number, color: number): void {
  if (w <= 0 || h <= 0) {
    return
  }
  const radius: number = Math.min(r, w / 2, h / 2)
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + w - radius, y)
  ctx.arcTo(x + w, y, x + w, y + radius, radius)
  ctx.lineTo(x + w, y + h - radius)
  ctx.arcTo(x + w, y + h, x + w - radius, y + h, radius)
  ctx.lineTo(x + radius, y + h)
  ctx.arcTo(x, y + h, x, y + h - radius, radius)
  ctx.lineTo(x, y + radius)
  ctx.arcTo(x, y, x + radius, y, radius)
  ctx.closePath()
  ctx.fill()
}

/** 单行金条画布：轨道满宽 gold14；实条按占比 gold（0 占比不画，避免 0 宽圆角闪烁） */
@ComponentV2
struct HBarCanvas {
  // 0-1 占比（父组件按 maxCount 归一）
  @Param fraction: number = 0
  private widthVp: number = 0
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  /** 数据刷新重绘（V2 无 onDidUpdate，@Monitor 监听 @Param 变化） */
  @Monitor('fraction')
  onFractionChange(): void {
    this.draw()
  }

  private draw(): void {
    const w = this.widthVp
    const h = 12
    this.ctx.clearRect(0, 0, w, h)
    fillRoundRect(this.ctx, 0, 0, w, h, 6, DivPalette.gold14)
    if (this.fraction > 0 && w > 0) {
      // 实条最小宽度保底一个圆角直径（极小占比时圆角不塌陷）
      const barW: number = Math.min(w, Math.max(w * this.fraction, h))
      fillRoundRect(this.ctx, 0, 0, barW, h, 6, DivPalette.gold)
    }
  }

  build() {
    Canvas(this.ctx)
      .width('100%')
      .height(12)
      .onAreaChange((_oldValue: Area, newValue: Area) => {
        this.widthVp = Number(newValue.width)
        this.draw()
      })
      .onReady(() => {
        this.draw()
      })
  }
}

// ========== 纵向月度趋势（Canvas 自绘多柱）：柱高按占比，底部标签行与柱一一对应 ==========

@ComponentV2
struct MonthTrendChart {
  @Param trend: Array<DivMonthCountVo> = []
  private widthVp: number = 0
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  @Monitor('trend')
  onTrendChange(): void {
    this.draw()
  }

  private draw(): void {
    const w = this.widthVp
    const h = 120
    if (w <= 0 || this.trend.length === 0) {
      return
    }
    let max: number = 1
    for (let i = 0; i < this.trend.length; i++) {
      max = Math.max(max, this.trend[i].count)
    }
    const slot: number = w / this.trend.length
    const barWidth: number = slot * 0.42
    this.ctx.clearRect(0, 0, w, h)
    for (let i = 0; i < this.trend.length; i++) {
      const count = this.trend[i].count
      if (count <= 0) {
        continue
      }
      const barHeight: number = h * (count / max)
      fillRoundRect(this.ctx, slot * i + (slot - barWidth) / 2, h - barHeight, barWidth, barHeight, 3,
        DivPalette.gold)
    }
  }

  /** 月标签（yyyy-MM → N月；非标准格式透传前 7 字符兜底，对齐安卓 shortMonth） */
  private shortMonth(month: string | null): string {
    const raw = month ?? ''
    if (raw.length >= 7 && raw.charAt(4) === '-') {
      return `${raw.substring(5, 7)}月`
    }
    return raw.substring(0, Math.min(7, raw.length))
  }

  build() {
    if (this.trend.length === 0) {
      Text(DivLoopText.statsEmptyChart)
        .fontSize(12)
        .fontColor(DivPalette.textTertiary)
        .textAlign(TextAlign.Center)
        .width('100%')
        .padding({ top: 12, bottom: 12 })
    } else {
      Column() {
        Canvas(this.ctx)
          .width('100%')
          .height(120)
          .onAreaChange((_oldValue: Area, newValue: Area) => {
            this.widthVp = Number(newValue.width)
            this.draw()
          })
          .onReady(() => {
            this.draw()
          })

        Column().height(6)

        Row() {
          ForEach(this.trend, (item: DivMonthCountVo) => {
            Text(this.shortMonth(item.month))
              .fontSize(10)
              .fontColor(DivPalette.textTertiary)
              .maxLines(1)
              .textAlign(TextAlign.Center)
              .layoutWeight(1)
          }, (item: DivMonthCountVo) => `${item.month ?? ''}`)
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
      }
      .width('100%')
    }
  }
}
