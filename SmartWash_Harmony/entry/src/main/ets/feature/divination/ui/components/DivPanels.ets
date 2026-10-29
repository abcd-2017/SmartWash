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
  divSpiritColor
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

// ========== 六爻盘 ==========

@ComponentV2
export struct LiuYaoPan {
  @Param chart: LiuYaoChart | null = null
  @Param facts: LiuYaoFacts | null = null
  @Param animateEntry: boolean = false
  // 动爻进场：自初爻向上逐条显现（60ms/条）；非动画直接全显
  @Local revealCount: number = 6
  private timerId: number = -1

  aboutToAppear(): void {
    if (this.chart === null) {
      return
    }
    if (this.animateEntry) {
      this.revealCount = 0
      this.timerId = setInterval(() => {
        this.revealCount = Math.min(6, this.revealCount + 1)
        if (this.revealCount >= 6 && this.timerId !== -1) {
          clearInterval(this.timerId)
          this.timerId = -1
        }
      }, 60)
    } else {
      this.revealCount = 6
    }
  }

  aboutToDisappear(): void {
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
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

  private typeFooterText(): string {
    const chart = this.chart
    if (chart === null) {
      return ''
    }
    let out = ''
    if (chart.type.length > 0) {
      out += chart.type + '卦'
    }
    if (chart.bianName !== null) {
      out += ' 之 ' + chart.bianName
    }
    if (chart.movingPositions.length > 0) {
      const parts: string[] = []
      for (const pos of chart.movingPositions) {
        parts.push(`${pos}爻动`)
      }
      out += ' · ' + parts.join('、')
    }
    return out
  }

  private bianTypeText(): string {
    const chart = this.chart
    return chart !== null && chart.bianType !== null ? `变卦${chart.bianType}` : ''
  }

  @Builder
  YaoBar(yang: boolean, revealed: boolean) {
    if (yang) {
      Column()
        .width('100%')
        .height(9)
        .borderRadius(4.5)
        .backgroundColor(DivPalette.yaoTop)
        .opacity(revealed ? 1 : 0)
    } else {
      Row({ space: 14 }) {
        Column()
          .layoutWeight(1)
          .height(9)
          .borderRadius(4.5)
          .backgroundColor(DivPalette.yaoTop)
          .opacity(revealed ? 1 : 0)
        Column()
          .layoutWeight(1)
          .height(9)
          .borderRadius(4.5)
          .backgroundColor(DivPalette.yaoTop)
          .opacity(revealed ? 1 : 0)
      }
      .width('100%')
    }
  }

  @Builder
  LiuYaoRow(line: LiuYaoLine, index: number) {
    Row() {
      // 六神（色点 + 名）
      Row() {
        Column()
          .width(6)
          .height(6)
          .borderRadius(3)
          .backgroundColor(divSpiritColor(line.liuShen))
        DivSerifText({
          text: line.liuShen,
          textSize: 10.5,
          weight: 400,
          spacing: 0,
          color: DivPalette.textSecondary,
          maxLines: 1
        })
          .padding({ left: 6 })
      }
      .width(52)
      .alignItems(VerticalAlign.Center)

      // 爻象 + 动爻标记
      Row() {
        Column() {
          this.YaoBar(line.yao.isYang, index < this.revealCount)
        }
        .layoutWeight(1)

        if (line.yao.isMoving) {
          // 老阳○ 鎏金 / 老阴× 朱砂 —— 颜色即信息
          DivSerifText({
            text: line.yao.code === 3 ? '○' : '×',
            textSize: 13,
            weight: 700,
            spacing: 0,
            color: line.yao.code === 3 ? DivPalette.gold : DivPalette.sealHi
          })
            .padding({ left: 4 })
        }
      }
      .layoutWeight(1)
      .padding({ right: 10 })
      .alignItems(VerticalAlign.Center)

      // 六亲 · 干支
      Row() {
        DivSerifText({
          text: line.liuQin,
          textSize: 12.5,
          weight: 500,
          spacing: 1,
          color: DivPalette.textPrimary
        })
        DivSerifText({
          text: line.ganZhi + line.zhiWuXing.label,
          textSize: 10.5,
          weight: 400,
          spacing: 0,
          color: DivPalette.gold
        })
          .padding({ left: 4 })
      }
      .alignItems(VerticalAlign.Center)

      // 世应徽标：世鎏金 / 应青玉
      DivSerifText({
        text: line.shiYing ?? '',
        textSize: 9.5,
        weight: 400,
        spacing: 0,
        color: line.shiYing === '世' ? DivPalette.gold : DivPalette.jade,
        textAlign: TextAlign.Center
      })
        .width(20)
        .padding({ left: 6 })
    }
    .width('100%')
    .padding({ top: 7, bottom: 7 })
    .alignItems(VerticalAlign.Center)
  }

  build() {
    if (this.hasChart()) {
      DivCard({ lineColor: DivPalette.goldLine }) {
        Column() {
          // 表头
          Row() {
            DivSerifText({ text: '六神', textSize: 9.5, weight: 400, spacing: 2, color: DivPalette.textTertiary })
              .width(52)
            DivSerifText({ text: '爻象', textSize: 9.5, weight: 400, spacing: 2, color: DivPalette.textTertiary })
              .layoutWeight(1)
            DivSerifText({ text: '六亲 · 干支', textSize: 9.5, weight: 400, spacing: 2, color: DivPalette.textTertiary })
            DivSerifText({ text: '世应', textSize: 9.5, weight: 400, spacing: 2, color: DivPalette.textTertiary })
              .width(20)
          }
          .width('100%')
          .padding({ bottom: 4 })

          // 自上爻到初爻（index 5 → 0）
          ForEach(DISPLAY_INDEXES, (index: number) => {
            Column() {
              this.LiuYaoRow(this.rowAt(index), index)
            }
            .width('100%')
          }, (index: number) => index.toString())

          // 类型脚注
          Row() {
            DivSerifText({
              text: this.typeFooterText(),
              textSize: 11,
              weight: 400,
              spacing: 0,
              color: DivPalette.textSecondary
            })
              .layoutWeight(1)
            DivSerifText({
              text: this.bianTypeText(),
              textSize: 11,
              weight: 600,
              spacing: 1,
              color: DivPalette.textPrimary
            })
          }
          .width('100%')
          .padding({ top: 10 })
          .alignItems(VerticalAlign.Center)
        }
        .width('100%')
        .padding(14)
      }
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
          // 爻象：自上爻到初爻
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
          Column().height(10)
          DivSerifText({
            text: item.chip,
            textSize: 10,
            weight: 400,
            spacing: 0,
            color: item.chipGold ? DivPalette.gold : DivPalette.textSecondary
          })
            .padding({ left: 8, right: 8, top: 3, bottom: 3 })
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
      Row() {
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

          // 暗干角标
          DivSerifText({ text: `暗·${this.cellAnGan(gong)}`, textSize: 9, weight: 400, spacing: 0, color: DivPalette.textTertiary })
            .align(Alignment.BottomEnd)
            .padding({ right: 8, bottom: 6 })

          // 空亡 / 驿马 badge
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
      .height(116)
      .padding({ left: 8, right: 8, top: 8, bottom: 8 })

      // 宫名
      DivSerifText({ text: this.gongNameText(gong), textSize: 9, weight: 400, spacing: 0, color: DivPalette.textTertiary })
        .align(Alignment.BottomStart)
        .padding({ left: 8, bottom: 6 })
    }
    .height(116)
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
      Column() {
        ForEach(LAYOUT_GONG, (row: number[]) => {
          Row() {
            ForEach(row, (gong: number) => {
              this.PalaceCell(gong)
            }, (gong: number) => gong.toString())
          }
          .width('100%')
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
        Row() {
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
          Row() {
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
