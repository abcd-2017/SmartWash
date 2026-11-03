/**
 * 观象台四术盘面组件 —— 对齐安卓 ui/components/PanComponents.kt + MethodPans.kt。
 *
 * 六爻盘（PanComponents.LiuYaoPan）/ 梅花三卡（MethodPans.MeiHuaTripleCards）/
 * 奇门九宫（MethodPans.QimenGrid）/ 六壬式盘 + 四课三传（MethodPans.LiurenPan/LiurenCourses）。
 *
 * 说明：盘面数据一律来自调用方读库解码后的 chart 类实例（DivChartReader），本文件不重算。
 * 颜色即信息：动爻 ○ 鎏金 / × 朱砂；世鎏金 / 应青玉；六神六色点。
 * 约定：build / @Builder 内不写局部声明，逻辑一律收敛为方法（与仓库既有风格一致）。
 */

import { LiuYaoChart, LiuYaoLine, LiuYaoFacts } from '../../core/liuyao/LiuYaoChart'
import { MeiHuaChart } from '../../core/meihua/MeiHuaChart'
import { QiMenChart } from '../../core/qimen/QiMenChart'
import { LiuRenChart, LiuRenCourse } from '../../core/liuren/LiuRenChart'
import { Trigram } from '../../core/DivinationCommon'
import {
  DivCard,
  DivPalette,
  DivSerifText,
  divReduceMotion
} from './DivComponents'

@Builder
@Builder
function emptyContent() {
}

/** 六爻行显示顺序（自上爻到初爻） */
const DISPLAY_INDEXES: number[] = [5, 4, 3, 2, 1, 0]

/** 三传标签 */
const CHUAN_LABELS: string[] = ['初传', '中传', '末传']

/** 九宫布局（巽四 离九 坤二 / 震三 中五 兑七 / 艮八 坎一 乾六） */
const LAYOUT_GONG: number[][] = [[4, 9, 2], [3, 5, 7], [8, 1, 6]]

/** 九宫名（奇门口径） */
const GONG_NAMES: Record<number, string> = {
  1: '坎一', 2: '坤二', 3: '震三', 4: '巽四',
  5: '中五', 6: '乾六', 7: '兑七', 8: '艮八', 9: '离九',
}

/** 梅花单卡展示数据 */
interface MeiHuaCardData {
  label: string
  name: string
  mark: string
  barColor: number
  chip: string
  chipGold: boolean
}

// ========== 六爻盘（无卡容器直铺背景；自初爻向上 draw-on 入场） ==========

/** 六爻逐爻展开的单爻时长 — 350ms（规范 7：进入减速 LinearOutSlowIn） */
const YAO_DRAW_MS: number = 350
/** 六爻逐爻展开的交叠间隔 — 60ms（规范 7：逐项间隔 ≤60ms） */
const YAO_STAGGER_MS: number = 60

/** 爻线段（draw-on 进度 0..1 控制展开宽度） */
@Builder
function YaoSegment(progress: number) {
  Column() {
    Column()
      .width(`${Math.round(progress * 100)}%`)
      .height(9)
      .borderRadius(2)
      .linearGradient({
        direction: GradientDirection.Right,
        colors: [[DivPalette.yaoTop, 0.0], [DivPalette.yaoBottom, 1.0]]
      })
  }
  .width('100%')
  .height(9)
}

/** 爻线：阳爻六段、阴爻两组四段（3/3/中缝2/3/3 权重，随槽宽自适应居中） */
@Builder
function YaoBar(yang: boolean, progress: number) {
  if (yang) {
    Row({ space: 2 }) {
      ForEach([0, 1, 2, 3, 4, 5], (item: number) => {
        Column() {
          YaoSegment(progress)
        }
        .layoutWeight(1)
      }, (item: number) => item.toString())
    }
    .width('100%')
  } else {
    Row() {
      Column() {
        YaoSegment(progress)
      }
      .layoutWeight(3)
      Column() {
        YaoSegment(progress)
      }
      .layoutWeight(3)
      Column()
        .layoutWeight(2)
      Column() {
        YaoSegment(progress)
      }
      .layoutWeight(3)
      Column() {
        YaoSegment(progress)
      }
      .layoutWeight(3)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
  }
}

/** 六爻装卦一行：六神（弱化灰 12）/ 爻线 / 六亲纳甲 / 世应（12/600 金） */
@ComponentV2
struct LiuYaoRow {
  @Param line: LiuYaoLine | null = null
  @Param revealed: number = 1
  // 动爻标记：爻线展开满后延迟 80ms 弹簧盖上（对齐安卓）
  @Local markVisible: boolean = false
  @Local markScale: number = 0
  private markTimer: number = -1

  aboutToAppear(): void {
    if (this.line !== null && this.line.yao.isMoving && this.revealed >= 1) {
      this.markTimer = setTimeout(() => {
        this.markVisible = true
        animateTo({ duration: 200, curve: Curve.EaseOut }, () => {
          this.markScale = 1
        })
      }, 80)
    }
  }

  aboutToDisappear(): void {
    if (this.markTimer !== -1) {
      clearTimeout(this.markTimer)
      this.markTimer = -1
    }
  }

  build() {
    if (this.line !== null) {
      Row() {
        // 六神（画板：无色点，弱化灰 12）
        DivSerifText({
          text: this.line.liuShen,
          textSize: 12,
          weight: 400,
          spacing: 0,
          color: DivPalette.textSecondary,
          maxLines: 1
        })
          .width(44)

        Column().width(12)

        // 爻象（权重自适应）
        Column() {
          YaoBar(this.line.yao.isYang, this.revealed)
        }
        .layoutWeight(1)

        Column().width(6)

        // 六亲 · 纳甲（动爻标记 ○/⨯ 延迟 80ms 弹簧盖上，同文字色）
        Row() {
          DivSerifText({
            text: `${this.line.liuQin}${this.line.ganZhi}${this.line.zhiWuXing.label}`,
            textSize: 12,
            weight: 400,
            spacing: 0,
            color: DivPalette.textPrimary,
            maxLines: 1
          })
          if (this.line.yao.isMoving && this.markVisible) {
            DivSerifText({
              text: this.line.yao.code === 3 ? ' ○' : ' ⨯',
              textSize: 12,
              weight: 400,
              spacing: 0,
              color: DivPalette.textPrimary
            })
              .scale({ x: this.markScale, y: this.markScale })
          }
        }
        .layoutWeight(1.2)
        .alignItems(VerticalAlign.Center)

        // 世应徽标（画板：12/600 主题金）
        DivSerifText({
          text: this.line.shiYing ?? '',
          textSize: 12,
          weight: 600,
          spacing: 0,
          color: DivPalette.goldText,
          textAlign: TextAlign.End
        })
          .width(24)
      }
      .width('100%')
      .padding({ top: 14, bottom: 14 })
      .alignItems(VerticalAlign.Center)
    }
  }
}

@ComponentV2
export struct LiuYaoPan {
  @Param chart: LiuYaoChart | null = null
  @Param facts: LiuYaoFacts | null = null
  @Param animateEntry: boolean = false
  // 逐爻展开进度（0..1），自初爻(index 0)向上交叠展开
  @Local progresses: number[] = [1, 1, 1, 1, 1, 1]
  private timers: number[] = []

  aboutToAppear(): void {
    if (this.animateEntry && !divReduceMotion()) {
      this.progresses = [0, 0, 0, 0, 0, 0]
      for (let i = 0; i < 6; i++) {
        const id = setTimeout(() => {
          animateTo({ duration: YAO_DRAW_MS, curve: Curve.FastOutSlowIn }, () => {
            const next = this.progresses.slice()
            next[i] = 1
            this.progresses = next
          })
        }, i * YAO_STAGGER_MS)
        this.timers.push(id)
      }
    } else {
      this.progresses = [1, 1, 1, 1, 1, 1]
    }
  }

  aboutToDisappear(): void {
    for (const id of this.timers) {
      clearTimeout(id)
    }
    this.timers = []
  }

  /** 点按跳过：进度直接置满（对齐安卓 pressable skip） */
  private skip(): void {
    animateTo({ duration: 120 }, () => {
      this.progresses = [1, 1, 1, 1, 1, 1]
    })
  }

  private hasChart(): boolean {
    return this.chart !== null
  }

  private rowAt(index: number): LiuYaoLine {
    const rows = this.chart?.rows ?? []
    const row = rows[index]
    if (row === undefined) {
      throw new Error(`六爻行缺失: ${index}`)
    }
    return row
  }

  build() {
    if (this.hasChart()) {
      // 无卡容器直铺背景；轨道左 28 / 右 20
      Column() {
        ForEach(DISPLAY_INDEXES, (index: number) => {
          Column() {
            // 行间 1px 12% 金发丝分隔（右缩 8 与卡片轨对齐）
            if (index < 5) {
              Row() {
                Column()
                  .width('100%')
                  .height(1)
                  .backgroundColor(DivPalette.hair)
              }
              .width('100%')
              .padding({ right: 8 })
            }
            LiuYaoRow({ line: this.rowAt(index), revealed: this.progresses[index] })
          }
          .width('100%')
        }, (index: number) => index.toString())
      }
      .width('100%')
      .padding({ left: 28, right: 20 })
      .onClick(() => {
        this.skip()
      })
    }
  }
}

// ========== 事实小卡（用神 / 规则事实） ==========

@ComponentV2
export struct DivMiniCard {
  @Param label: string = ''
  @BuilderParam content: () => void = emptyContent

  build() {
    DivCard({ lineColor: DivPalette.goldHair }) {
      Column() {
        DivSerifText({
          text: this.label,
          textSize: 9.5,
          weight: 400,
          spacing: 2,
          color: DivPalette.textTertiary
        })
        this.content()
      }
      .width('100%')
      .padding({ left: 12, right: 12, top: 10, bottom: 10 })
      .alignItems(HorizontalAlign.Start)
    }
  }
}

// ========== 梅花本互变三卡 ==========

@ComponentV2
export struct MeiHuaTripleCards {
  @Param chart: MeiHuaChart | null = null

  private hasChart(): boolean {
    return this.chart !== null
  }

  /** 三张卡展示数据（本=鎏金、互=弱文本、变=青玉） */
  private cardData(): MeiHuaCardData[] {
    const chart = this.chart
    if (chart === null) {
      return []
    }
    return [
      {
        label: '本卦 · 体',
        name: chart.benName,
        mark: chart.benMark,
        barColor: DivPalette.gold,
        chip: `${chart.ti.label}${chart.ti.wuXing.label}体 · ${chart.yong.label}${chart.yong.wuXing.label}用`,
        chipGold: true
      },
      {
        label: '互卦 · 过程',
        name: chart.huName,
        mark: chart.huMark,
        barColor: DivPalette.textTertiary,
        chip: `${Trigram.fromBinary(chart.huMark.substring(3, 6)).label}${chart.huWuXing.label} · 用`,
        chipGold: false
      },
      {
        label: '变卦 · 结局',
        name: chart.bianName,
        mark: chart.bianMark,
        barColor: DivPalette.jade,
        chip: `${Trigram.fromBinary(chart.bianMark.substring(3, 6)).label}${chart.bianWuXing.label} · 用`,
        chipGold: false
      }
    ]
  }

  @Builder
  MeiHuaCard(item: MeiHuaCardData) {
    DivCard({ bgColor: DivPalette.surface, lineColor: DivPalette.goldHair }) {
      Column() {
        Column()
          .width('100%')
          .height(2.5)
          .backgroundColor(item.barColor)

        Column() {
          DivSerifText({ text: item.label, textSize: 9.5, weight: 400, spacing: 2, color: DivPalette.textTertiary })
          Column().height(8)
          DivSerifText({ text: item.name, textSize: 17, weight: 600, spacing: 2, color: DivPalette.textPrimary })
          Column().height(10)
          // 爻象：自上爻到初爻（行距 3）
          Column({ space: 3 }) {
            ForEach(DISPLAY_INDEXES, (index: number) => {
              if (item.mark.charAt(index) === '1') {
                Column()
                  .width('100%')
                  .height(4)
                  .borderRadius(2)
                  .backgroundColor(DivPalette.yaoTop)
              } else {
                Row({ space: 6 }) {
                  Column().layoutWeight(1).height(4).borderRadius(2).backgroundColor(DivPalette.yaoTop)
                  Column().layoutWeight(1).height(4).borderRadius(2).backgroundColor(DivPalette.yaoTop)
                }
                .width('100%')
              }
            }, (index: number) => index.toString())
          }
          .width('100%')
          Column().height(10)
          // 体用 chip：限两行居中 + 收横 padding 6（窄卡不溢出）
          DivSerifText({
            text: item.chip,
            textSize: 10,
            weight: 400,
            spacing: 0,
            color: item.chipGold ? DivPalette.gold : DivPalette.textSecondary,
            textAlign: TextAlign.Center,
            maxLines: 2
          })
            .padding({ left: 6, right: 6, top: 3, bottom: 3 })
            .borderRadius(10)
            .backgroundColor(item.chipGold ? DivPalette.goldSoft : DivPalette.surface2)
        }
        .width('100%')
        .padding({ top: 14, bottom: 14, left: 6, right: 6 })
        .alignItems(HorizontalAlign.Center)
      }
      .width('100%')
    }
  }

  build() {
    if (this.hasChart()) {
      Row({ space: 10 }) {
        ForEach(this.cardData(), (item: MeiHuaCardData) => {
          Column() {
            this.MeiHuaCard(item)
          }
          .layoutWeight(1)
        }, (item: MeiHuaCardData) => item.label)
      }
      .width('100%')
    }
  }
}

// ========== 奇门九宫格 ==========

@ComponentV2
export struct QimenGrid {
  @Param chart: QiMenChart | null = null

  private hasChart(): boolean {
    return this.chart !== null
  }

  private gongNameText(gong: number): string {
    return GONG_NAMES[gong] ?? ''
  }

  private chartValue(map: Map<number, string>, gong: number): string {
    return map.get(gong) ?? ''
  }

  private hasBadge(gong: number): boolean {
    const chart = this.chart
    if (chart === null) {
      return false
    }
    return chart.kongWangGong.indexOf(gong) >= 0 || chart.maGong === gong
  }

  private badgeText(gong: number): string {
    const chart = this.chart
    if (chart === null) {
      return ''
    }
    const badges: string[] = []
    if (chart.kongWangGong.indexOf(gong) >= 0) {
      badges.push('空亡')
    }
    if (chart.maGong === gong) {
      badges.push('驿马')
    }
    return badges.join('·')
  }

  @Builder
  PalaceCell(gong: number) {
    Stack() {
      Column() {
        if (gong === 5) {
          Column() {
            DivSerifText({ text: '二 · 五', textSize: 17, weight: 600, spacing: 3, color: DivPalette.gold })
            Column().height(6)
            DivSerifText({ text: '寄 坤 二 宫', textSize: 9.5, weight: 400, spacing: 2, color: DivPalette.textTertiary })
          }
          .width('100%')
          .height('100%')
          .justifyContent(FlexAlign.Center)
          .alignItems(HorizontalAlign.Center)
        } else {
          Column() {
            DivSerifText({ text: this.cellBaShen(gong), textSize: 10, weight: 400, spacing: 1, color: DivPalette.gold })
            Column().height(4)
            DivSerifText({ text: this.cellJiuXing(gong), textSize: 13, weight: 500, spacing: 1, color: DivPalette.textPrimary })
            DivSerifText({ text: this.cellTianPan(gong), textSize: 15, weight: 600, spacing: 0, color: DivPalette.gold })
            Column().height(4)
            Row() {
              DivSerifText({ text: this.cellBaMen(gong), textSize: 10.5, weight: 400, spacing: 0, color: DivPalette.textSecondary })
              Column().width(6)
              DivSerifText({ text: this.cellDiPan(gong), textSize: 12.5, weight: 400, spacing: 0, color: DivPalette.textSecondary })
            }
            .alignItems(VerticalAlign.Center)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)

          // 空亡 / 驿马 badge（TopEnd）
          if (this.hasBadge(gong)) {
            DivSerifText({ text: this.badgeText(gong), textSize: 9, weight: 400, spacing: 0, color: DivPalette.sealHi })
              .align(Alignment.TopEnd)
              .padding({ left: 5, right: 5, top: 1, bottom: 1 })
              .borderRadius(8)
              .backgroundColor(DivPalette.hair)
              .margin({ right: 8, top: 8 })
          }
        }
      }
      .width('100%')
      // 宫高 min 124 自适应（大字体下五层文本不叠压，对齐安卓 heightIn(min=124)）
      .constraintSize({ minHeight: 124 })
      .padding({ left: 8, right: 8, top: 8, bottom: 8 })

      // 底部行：宫名（左）+ 暗干（右）同行合并（原两处绝对定位角标大字号重叠）
      Row() {
        DivSerifText({ text: this.gongNameText(gong), textSize: 9, weight: 400, spacing: 0, color: DivPalette.textTertiary })
          .layoutWeight(1)
          .opacity(0.85)
        if (gong !== 5) {
          DivSerifText({ text: `暗·${this.cellAnGan(gong)}`, textSize: 9, weight: 400, spacing: 0, color: DivPalette.textTertiary })
            .opacity(0.75)
        }
      }
      .width('100%')
      .align(Alignment.BottomStart)
      .padding({ left: 8, right: 8, bottom: 6 })
    }
    .constraintSize({ minHeight: 124 })
    .borderRadius(10)
    .backgroundColor(gong === 5 ? DivPalette.surface2 : DivPalette.surface)
    .border({ width: 1, color: DivPalette.goldHair, radius: 10 })
    .layoutWeight(1)
  }

  private cellBaShen(gong: number): string {
    const chart = this.chart
    return chart === null ? '' : this.chartValue(chart.baShen, gong)
  }

  private cellJiuXing(gong: number): string {
    const chart = this.chart
    return chart === null ? '' : this.chartValue(chart.jiuXing, gong)
  }

  private cellTianPan(gong: number): string {
    const chart = this.chart
    return chart === null ? '' : this.chartValue(chart.tianPan, gong)
  }

  private cellBaMen(gong: number): string {
    const chart = this.chart
    return chart === null ? '' : this.chartValue(chart.baMen, gong)
  }

  private cellDiPan(gong: number): string {
    const chart = this.chart
    return chart === null ? '' : this.chartValue(chart.diPan, gong)
  }

  private cellAnGan(gong: number): string {
    const chart = this.chart
    return chart === null ? '' : this.chartValue(chart.anGan, gong)
  }

  build() {
    if (this.hasChart()) {
      Column({ space: 5 }) {
        ForEach(LAYOUT_GONG, (row: number[]) => {
          Row({ space: 5 }) {
            ForEach(row, (gong: number) => {
              this.PalaceCell(gong)
            }, (gong: number) => gong.toString())
          }
          .width('100%')
          .alignItems(VerticalAlign.Top)
        }, (row: number[], index: number) => index.toString())
      }
      .width('100%')
    }
  }
}

// ========== 大六壬式盘（天地盘双环） ==========

const LIUREN_ZHI: string[] = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']
const LIUREN_SIZE: number = 228

@ComponentV2
export struct LiurenPan {
  @Param chart: LiuRenChart | null = null
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  aboutToAppear(): void {
    this.draw()
  }

  aboutToDisappear(): void {
  }

  private draw(): void {
    const chart = this.chart
    if (chart === null) {
      return
    }
    const size: number = LIUREN_SIZE
    const cx: number = size / 2
    const cy: number = size / 2
    const rOuter: number = size / 2
    const rInner: number = rOuter * 0.54

    this.ctx.clearRect(0, 0, size, size)

    // 外环天盘 / 中环 / 内环地盘
    this.ctx.lineWidth = 1.2
    this.ctx.strokeStyle = DivPalette.goldLine
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, rOuter - 0.6, 0, Math.PI * 2, false)
    this.ctx.stroke()

    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = DivPalette.goldHair
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, rOuter * 0.72, 0, Math.PI * 2, false)
    this.ctx.stroke()

    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = DivPalette.goldLine
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, rInner, 0, Math.PI * 2, false)
    this.ctx.stroke()

    this.ctx.fillStyle = DivPalette.surface2
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, rInner - 1, 0, Math.PI * 2, false)
    this.ctx.fill()

    // 外环（天盘支）：由地盘支逆映射定位
    for (let index = 0; index < LIUREN_ZHI.length; index++) {
      const label = LIUREN_ZHI[index]
      const diLabel = this.diOfTian(chart, label)
      if (diLabel === null) {
        continue
      }
      const diIndex = LIUREN_ZHI.indexOf(diLabel)
      const angle = (diIndex * 30 - 90) * Math.PI / 180
      const posX = cx + rOuter * 0.84 * Math.cos(angle)
      const posY = cy + rOuter * 0.84 * Math.sin(angle)
      this.drawCenterText(label, posX, posY, 13, DivPalette.gold)
    }

    // 内环（地盘支，固定）
    for (let index = 0; index < LIUREN_ZHI.length; index++) {
      const angle = (index * 30 - 90) * Math.PI / 180
      const posX = cx + rInner * 0.72 * Math.cos(angle)
      const posY = cy + rInner * 0.72 * Math.sin(angle)
      this.drawCenterText(LIUREN_ZHI[index], posX, posY, 12, DivPalette.textTertiary)
    }

    // 四正刻线
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = DivPalette.goldHair
    const ticks: number[] = [0, 90, 180, 270]
    for (let i = 0; i < ticks.length; i++) {
      const rad = ticks[i] * Math.PI / 180
      this.ctx.beginPath()
      this.ctx.moveTo(cx + rInner * Math.cos(rad), cy + rInner * Math.sin(rad))
      this.ctx.lineTo(cx + rOuter * 0.72 * Math.cos(rad), cy + rOuter * 0.72 * Math.sin(rad))
      this.ctx.stroke()
    }

    // 天心
    this.ctx.fillStyle = DivPalette.gold
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, 3.5, 0, Math.PI * 2, false)
    this.ctx.fill()
  }

  /** 天地盘逆映射：天盘支 → 地盘支 */
  private diOfTian(chart: LiuRenChart, tianLabel: string): string | null {
    const reverse = new Map<string, string>()
    chart.tianPan.forEach((v: string, k: string): void => {
      reverse.set(v, k)
    })
    const found = reverse.get(tianLabel)
    return found === undefined ? null : found
  }

  private drawCenterText(text: string, cx: number, cy: number, size: number, color: number): void {
    this.ctx.font = `${size}px serif`
    this.ctx.fillStyle = color
    const metrics = this.ctx.measureText(text)
    this.ctx.fillText(text, cx - metrics.width / 2, cy + metrics.actualBoundingBoxAscent / 2)
  }

  build() {
    Canvas(this.ctx)
      .width(LIUREN_SIZE)
      .height(LIUREN_SIZE)
  }
}

// ========== 六壬四课 / 三传 ==========

@ComponentV2
export struct LiurenCourses {
  @Param chart: LiuRenChart | null = null

  private hasChart(): boolean {
    return this.chart !== null
  }

  private courseList(): LiuRenCourse[] {
    return this.chart?.courses ?? []
  }

  private hasSanChuan(): boolean {
    return this.chart !== null && this.chart.sanChuan !== null
  }

  private chuanAt(index: number): string {
    return this.chart?.sanChuan?.[index] ?? ''
  }

  private chuanJiangAt(index: number): string {
    return this.chart?.sanChuanJiang?.[index] ?? ''
  }

  @Builder
  CourseCard(course: LiuRenCourse) {
    DivCard({ lineColor: DivPalette.goldHair }) {
      Column() {
        DivSerifText({ text: course.label, textSize: 9.5, weight: 400, spacing: 2, color: DivPalette.textTertiary })
        Column().height(8)
        DivSerifText({ text: course.upper, textSize: 15, weight: 600, spacing: 0, color: DivPalette.textPrimary })
        Column().height(6)
        DivSerifText({ text: course.lower, textSize: 15, weight: 600, spacing: 0, color: DivPalette.gold })
      }
      .width('100%')
      .padding({ top: 10, bottom: 10 })
      .alignItems(HorizontalAlign.Center)
    }
  }

  @Builder
  ChuanCard(label: string, value: string, jiang: string) {
    DivCard({ lineColor: DivPalette.goldLine }) {
      Column() {
        DivSerifText({ text: label, textSize: 9.5, weight: 400, spacing: 2, color: DivPalette.textTertiary })
        Column().height(8)
        DivSerifText({ text: value, textSize: 15, weight: 600, spacing: 0, color: DivPalette.textPrimary })
        Column().height(6)
        DivSerifText({ text: jiang, textSize: 9.5, weight: 400, spacing: 1, color: DivPalette.gold })
      }
      .width('100%')
      .padding({ top: 10, bottom: 10 })
      .alignItems(HorizontalAlign.Center)
    }
  }

  build() {
    if (this.hasChart()) {
      Column() {
        // 四课
        Row({ space: 10 }) {
          ForEach(this.courseList(), (course: LiuRenCourse) => {
            Column() {
              this.CourseCard(course)
            }
            .layoutWeight(1)
          }, (course: LiuRenCourse, index: number) => index.toString())
        }
        .width('100%')

        // 三传（或暂不支持说明）
        if (this.hasSanChuan()) {
          Row({ space: 10 }) {
            ForEach(CHUAN_LABELS, (label: string, index: number) => {
              Column() {
                this.ChuanCard(label, this.chuanAt(index), this.chuanJiangAt(index))
              }
              .layoutWeight(1)
            }, (label: string, index: number) => index.toString())
          }
          .width('100%')
        }
      }
      .width('100%')
    }
  }
}
