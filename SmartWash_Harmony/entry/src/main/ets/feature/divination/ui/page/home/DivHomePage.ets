/**
 * 观象台首页 —— 对齐安卓 ui/page/home/DivHomePage.kt + DivHomeViewModel.kt。
 *
 * 结构：干支天象头（当日四柱 + 旬空副行）→ 衬线标题 → 缓转罗盘 → 四术玉牌 2×2
 * → 今日一签卡（点击当日去重落库后跳卦盘）→ 案卷（近三条）→ 页脚免责声明。
 *
 * 关键约束：
 * - 天象头 / 今日一签在每次进入时计算（确定性，不落库）；落库只发生在点击今日一签时（去重复用）。
 * - 案卷读库近三条，不重算排盘。
 */

import { Solar } from '../../../core/lunar/lunar'
import { LunarAdapter } from '../../../core/LunarAdapter'
import { DivMethod } from '../../../core/DivinationCommon'
import { MeiHuaChart } from '../../../core/meihua/MeiHuaChart'
import { DivRecordRepository, DivRecordRow, encodeBundle, encodeBundleToJson } from '../../../data/DivinationStore'
import { encodeChartJson } from '../../../data/DivChartCodec'
import {
  CompassDial,
  DivCard,
  DivFooterNote,
  DivPalette,
  DivSectionTitle,
  DivSeal,
  DivSerifText,
  DivText,
  TianXiangHeader
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignElevation, DesignColor } from '../../../../../constant/DesignSystem'
import { pathStack } from '../../../../../utils/PathStackUtil'
import { Debouncer } from '../../../../../utils/DebounceUtil'
import { DivChartParam } from '../../DivChartParam'

/** 四术玉牌展示元数据（对齐安卓 DivHomePage.kt:101-106 的 Quad 列表） */
const METHOD_LABELS: Record<string, string> = {
  'liuyao': DivText.methodLiuyaoName,
  'meihua': DivText.methodMeihuaName,
  'qimen': DivText.methodQimenName,
  'liuren': DivText.methodLiurenName,
}

const TILE_LIST: Array<[string, string, string, string, string]> = [
  ['liuyao', DivText.methodLiuyaoName, DivText.methodLiuyaoDesc, DivText.methodLiuyaoUseFor, DivText.methodLiuyaoTag],
  ['meihua', DivText.methodMeihuaName, DivText.methodMeihuaDesc, DivText.methodMeihuaUseFor, ''],
  ['qimen', DivText.methodQimenName, DivText.methodQimenDesc, DivText.methodQimenUseFor, ''],
  ['liuren', DivText.methodLiurenName, DivText.methodLiurenDesc, DivText.methodLiurenUseFor, ''],
]

@Builder
export function DivHomeBuilder() {
  DivHomePage()
}

@ComponentV2
struct DivHomePage {
  @Local tianXiang: string = ''
  @Local xunKong: string = ''
  @Local hasTodaySign: boolean = false
  @Local todaySignName: string = ''
  @Local todaySignSub: string = ''
  @Local records: DivRecordRow[] = []
  // 玉牌按压缩放态（pressScale 0.97）+ 点击节流防抖（对齐安卓 rememberDebouncedClick）
  @Local pressedKey: string = ''
  private debouncer: Debouncer = new Debouncer()

  async aboutToAppear(): Promise<void> {
    await this.refresh()
  }

  /**
   * 每次进入计算天象头 + 今日一签（确定性，不落库；落库只在点击时，对齐 DivHomeViewModel.refresh）。
   */
  private async refresh(): Promise<void> {
    const solar = Solar.fromDate(new Date())
    const lunar = LunarAdapter.of(solar)
    this.tianXiang = lunar.siZhu.labels().join(' · ')
    this.xunKong = lunar.xunKong

    try {
      const chart = MeiHuaChart.timeChart(solar)
      this.todaySignName = chart.benName
      this.todaySignSub = `${chart.ti.label}${chart.ti.wuXing.label}体 · ` +
        `${chart.yong.label}${chart.yong.wuXing.label}用 · ${chart.tiYongRelation.label}`
      this.hasTodaySign = true
    } catch (err) {
      // 无签数据时不显示卡（对齐 DivHomePage.kt:129-160 的 sign != null 分支）
      this.hasTodaySign = false
    }

    try {
      this.records = await DivRecordRepository.recent(3)
    } catch (err) {
      this.records = []
    }
  }

  /**
   * 今日一签入库（同日去重：当天已有同签记录则复用，不重复落库）→ 跳卦盘。
   * 对齐 DivHomeViewModel.ensureTodaySignSaved + DivRecordRepository.todaySign。
   */
  private async ensureTodaySignSaved(): Promise<void> {
    const solar = Solar.fromDate(new Date())
    const chart = MeiHuaChart.timeChart(solar)
    const question = '今日一签'

    const existing = await DivRecordRepository.findTodaySign('meihua', question)
    let id: number
    if (existing !== null) {
      id = existing.id
    } else {
      const chartJson = encodeChartJson(chart)
      const bundleJson = encodeBundleToJson(encodeBundle('meihua', chartJson))
      id = await DivRecordRepository.save(
        'meihua', 'other', question, JSON.stringify(chart.lines), Date.now(), bundleJson)
    }
    // 跳卦盘（DivChart 路由随卦盘批次注册；复盘入口不播动爻进场动画）
    const chartParam: DivChartParam = { recordId: id, animate: false }
    pathStack.pushPathByName(RouteNames.DIV_CHART, chartParam)
  }

  /** 日期 MM-dd（对齐安卓 SimpleDateFormat("MM-dd")） */
  private formatMonthDay(castAt: number): string {
    const d = new Date(castAt)
    return `${this.pad2(d.getMonth() + 1)}-${this.pad2(d.getDate())}`
  }

  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }

  private methodName(id: string): string {
    const label = METHOD_LABELS[id]
    return label !== undefined ? label : id
  }

  /** 四术玉牌分行（对齐安卓 methods.chunked(2)） */
  private buildRows(): Array<Array<[string, string, string, string, string]>> {
    const rows: Array<Array<[string, string, string, string, string]>> = []
    for (let i = 0; i < TILE_LIST.length; i += 2) {
      rows.push(TILE_LIST.slice(i, i + 2))
    }
    return rows
  }

  /** 案卷行（对齐安卓 LedgerRow：日期 + 术数色点 + 问题摘要 + 术数名衬线金） */
  @Builder
  LedgerRow(record: DivRecordRow) {
    Row() {
      DivSerifText({
        text: this.formatMonthDay(record.castAt),
        textSize: 11,
        weight: 400,
        spacing: 0,
        color: DivPalette.textTertiary
      })
        .width(44)

      Column()
        .width(7)
        .height(7)
        .borderRadius(3.5)
        .backgroundColor(record.method === DivMethod.LIU_YAO.id ? DivPalette.gold : DivPalette.jade)

      Column().width(12)

      DivSerifText({
        text: record.question,
        textSize: 13,
        weight: 400,
        spacing: 0,
        color: DivPalette.textPrimary,
        maxLines: 1,
        overflow: TextOverflow.Ellipsis
      })
        .layoutWeight(1)

      Column().width(8)

      DivSerifText({
        text: this.methodName(record.method),
        textSize: 12,
        weight: 500,
        spacing: 2,
        color: DivPalette.gold
      })
    }
    .width('100%')
    .padding({ top: 12, bottom: 12 })
    .alignItems(VerticalAlign.Center)
  }

  /**
   * 四术玉牌：竖排术名 + 一句话定位 + 适用问题 + 推荐标（点击防抖 + pressScale + 触感）。
   * 自绘卡（非 DivCard）：按规范 §2.4/§3.1 补 1px 金线描边同款的 level1 轻阴影，玄墨侧同 DivCard 豁免。
   */
  @Builder
  JadeTile(method: string, name: string, desc: string, useFor: string, tag: string) {
    Stack() {
      // 左下：一句话定位 + 适用问题
      Column() {
        DivSerifText({
          text: desc,
          textSize: 11,
          weight: 400,
          spacing: 0,
          color: DivPalette.textSecondary,
          maxLines: 2,
          overflow: TextOverflow.Ellipsis
        })
        if (useFor.length > 0) {
          DivSerifText({
            text: useFor,
            textSize: 10,
            weight: 400,
            spacing: 0,
            color: DivPalette.textTertiary,
            maxLines: 1,
            overflow: TextOverflow.Ellipsis
          })
            .margin({ top: 4 })
        }
      }
      .width('100%')
      .height('100%')
      .padding(16)
      .alignItems(HorizontalAlign.Start)
      .justifyContent(FlexAlign.End)

      // 右上：竖排术名（逐字换行）
      Column() {
        ForEach(name.split(''), (ch: string, index: number) => {
          DivSerifText({ text: ch, textSize: 19, weight: 600, spacing: 7, color: DivPalette.textPrimary })
        }, (ch: string, index: number) => index.toString())
      }
      .padding(14)
      .alignItems(HorizontalAlign.End)

      if (tag.length > 0) {
        DivSerifText({ text: tag, textSize: 10, weight: 400, spacing: 2, color: DivPalette.gold })
          .position({ x: 16, y: 156 - 10 - 16 })
      }
    }
    .width('100%')
    .height(156)
    .borderRadius(16)
    .backgroundColor(DivPalette.surface)
    // 规范 §2.4/§3.1 卡片唯一画法：1px 描边 + level1 轻阴影（宣纸侧；安卓 JadeTile 同此）
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
    .border({ width: 1, color: DivPalette.goldLine })
    .scale({ x: this.pressedKey === method ? 0.97 : 1, y: this.pressedKey === method ? 0.97 : 1 })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedKey = method
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedKey = ''
      }
    })
    .onClick(() => {
      this.pressedKey = ''
      // 四牌并排且 navigate 无 launchSingleTop，连点会双推 Ask 页 → 节流防抖
      this.debouncer.throttle(() => {
        pathStack.pushPathByName(RouteNames.DIV_ASK, method)
      }, 600)
    })
  }

  build() {
    NavDestination() {
      DivinationScreen() {
        // 1. 干支天象头
        TianXiangHeader({ text: this.tianXiang.length > 0 ? this.tianXiang : '—' })

        // 2. 衬线标题 + 旬空副行
        Column() {
          DivSerifText({
            text: DivText.title,
            textSize: 26,
            weight: 600,
            spacing: 4,
            color: DivPalette.textPrimary
          })
            .margin({ top: 10 })

          DivSerifText({
            text: `旬空 ${this.xunKong.length > 0 ? this.xunKong : '—'}`,
            textSize: 11,
            weight: 400,
            spacing: 1,
            color: DivPalette.textTertiary
          })
            .margin({ top: 5 })
        }
        .width('100%')
        .padding({ left: 20, right: 20 })

        // 3. 罗盘
        Column() {
          CompassDial({ dialText: '卜' })
        }
        .width('100%')
        .padding({ top: 4, bottom: 4 })
        .alignItems(HorizontalAlign.Center)

        // 4. 四术玉牌 2×2
        Column({ space: 10 }) {
          ForEach(this.buildRows(), (row: Array<[string, string, string, string, string]>, rowIndex: number) => {
            Row({ space: 10 }) {
              ForEach(row, (item: [string, string, string, string, string]) => {
                Column() {
                  this.JadeTile(item[0], item[1], item[2], item[3], item[4])
                }
                .layoutWeight(1)
              }, (item: [string, string, string, string, string]) => item[0])
              if (row.length === 1) {
                Column()
                  .layoutWeight(1)
              }
            }
            .width('100%')
          }, (row: Array<[string, string, string, string, string]>, index: number) => index.toString())
        }
        .width('100%')
        .padding({ left: 20, right: 20 })

        // 5. 今日一签卡（无签数据时不显示）
        if (this.hasTodaySign) {
          DivCard({
            clickable: true,
            lineColor: DivPalette.goldHair,
            onAction: () => {
              this.ensureTodaySignSaved()
            }
          }) {
            Row() {
              DivSeal({ text: DivText.signSeal, sealSize: 44, fontSize: 22 })
              Column().width(16)
              Column() {
                DivSerifText({
                  text: `今日一签 · ${this.todaySignName}`,
                  textSize: 18,
                  weight: 600,
                  spacing: 4,
                  color: DivPalette.textPrimary
                })
                DivSerifText({
                  text: `${this.todaySignSub} —— ${DivText.signCardSource}`,
                  textSize: 11,
                  weight: 400,
                  spacing: 1,
                  color: DivPalette.textSecondary
                })
                  .margin({ top: 5 })
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)

              DivSerifText({ text: '›', textSize: 16, weight: 400, spacing: 0, color: DivPalette.gold })
            }
            .width('100%')
            .padding({ left: 18, right: 18, top: 16, bottom: 16 })
            .alignItems(VerticalAlign.Center)
          .margin({ left: 20, right: 20, top: 14, bottom: 14 })
          }
        }

        // 6. 案卷
        DivSectionTitle({
          title: DivText.homeLedger,
          tail: DivText.homeLedgerMore,
          tailClickable: true,
          onTailClick: () => {
            this.debouncer.throttle(() => {
              pathStack.pushPathByName(RouteNames.DIV_HISTORY, null)
            }, 600)
          }
        })

        DivCard({ lineColor: DivPalette.goldHair }) {
          Column() {
            if (this.records.length === 0) {
              DivSerifText({
                text: DivText.historyEmpty,
                textSize: 12,
                weight: 400,
                spacing: 0,
                color: DivPalette.textTertiary
              })
                .width('100%')
                .padding({ top: 18, bottom: 18 })
            } else {
              ForEach(this.records, (record: DivRecordRow, index: number) => {
                Column() {
                  this.LedgerRow(record)
                }
                .width('100%')
                if (index !== 2 && index !== this.records.length - 1) {
                  Divider()
                    .strokeWidth(0.5)
                    .color(DivPalette.goldHair)
                }
              }, (record: DivRecordRow) => record.id.toString())
            }
          }
          .width('100%')
          .padding({ left: 14, top: 4, bottom: 4 })
        .margin({ left: 20, right: 20 })
        }

        // 7. 页脚免责声明
        DivFooterNote({ text: DivText.disclaimerFooter })
      }
    }
    .hideTitleBar(true)
  }

  aboutToDisappear(): void {
    this.debouncer.cancel()
  }
}
