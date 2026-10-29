/**
 * 卦历案卷 —— 术数筛选 + 卦历卡列表；点击进只读复盘（原盘原时刻）。
 *
 * 蓝本：安卓 feature/divination/.../ui/page/history/DivHistoryPage.kt。
 * 数据：DivRecordRepository.observeAll() 一次性取回后内存过滤（HarmonyOS 无 Flow，
 * 列表刷新沿用项目现有 onShown 重拉模式）。
 *
 * 红线：只读库中已落盘的记录，绝不在此页重算排盘。
 */

import { DivRecordRow, DivRecordRepository, decodeBundleFromJson } from '../../../data/DivinationStore'
import { readLiuYaoChart, readMeiHuaChart, readQiMenChart, readLiuRenChart, divMethodName }
  from '../../../data/DivChartReader'
import { DivCard, DivCategoryChip, DivFooterNote, DivPageHeader, DivSeal } from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { pathStack } from '../../../../../utils/PathStackUtil'
import { DesignDimens } from '../../../../../constant/DesignSystem'
import { DivColor } from '../../../../../constant/DivTheme'

/** 术数筛选选项（「全部」+ 四术） */
const FILTER_ALL = 'all'
const METHODS: string[] = ['liuyao', 'meihua', 'qimen', 'liuren']

@Builder
export function DivHistoryBuilder() {
  DivHistoryPage()
}

@ComponentV2
export struct DivHistoryPage {
  @Local records: DivRecordRow[] = []
  @Local filter: string = FILTER_ALL
  @Local isLoading: boolean = true

  aboutToAppear(): void {
    this.refresh()
  }

  onShown(): void {
    // 从卦盘/复盘返回后重拉，保证卦历与最新排盘一致（项目现有列表页统一模式）
    this.refresh()
  }

  private async refresh(): Promise<void> {
    this.isLoading = true
    try {
      const all = await DivRecordRepository.observeAll()
      this.records = all
    } catch (err) {
      this.records = []
    }
    this.isLoading = false
  }

  private filtered(): DivRecordRow[] {
    if (this.filter === FILTER_ALL) {
      return this.records
    }
    return this.records.filter((r) => r.method === this.filter)
  }

  private formatDateTime(castAt: number): string {
    const d = new Date(castAt)
    const month = `${d.getMonth() + 1}`.padStart(2, '0')
    const day = `${d.getDate()}`.padStart(2, '0')
    const hour = `${d.getHours()}`.padStart(2, '0')
    const minute = `${d.getMinutes()}`.padStart(2, '0')
    return `${month}-${day} ${hour}:${minute}`
  }

  /** 列表摘要行：卦名 / 本卦 / 局数 / 月将（对齐安卓 DivRecordEntity.summaryLine） */
  private summaryLine(row: DivRecordRow): string {
    try {
      const bundle = decodeBundleFromJson(row.chartJson)
      if (bundle.liuyao !== undefined) {
        const chart = readLiuYaoChart(bundle.liuyao)
        return chart.bianName !== null && chart.bianName.length > 0
          ? `${chart.name} 之 ${chart.bianName}` : chart.name
      }
      if (bundle.meihua !== undefined) {
        return readMeiHuaChart(bundle.meihua).benName
      }
      if (bundle.qimen !== undefined) {
        return `阴遁${readQiMenChart(bundle.qimen).juNumber}局`
      }
      if (bundle.liuren !== undefined) {
        return `月将${readLiuRenChart(bundle.liuren).yueJiang}`
      }
    } catch (err) {
      // 解码失败回退空串（与安卓一致）
    }
    return ''
  }

  private sealOf(method: string): string {
    if (method === 'liuyao') {
      return '卦'
    }
    if (method === 'meihua') {
      return '梅'
    }
    if (method === 'qimen') {
      return '奇'
    }
    return '壬'
  }

  private openChart(row: DivRecordRow): void {
    // 复盘：animate=false（对齐安卓 navigate Chart/{id}?animate=false）
    pathStack.pushPathByName(RouteNames.DIV_CHART, row.id)
  }

  build() {
    DivinationScreen({ scrollable: false }) {
      Column() {
        DivPageHeader({ title: '卦历案卷', showBack: false })

        // 术数筛选
        Row() {
          DivCategoryChip({
            text: '全部',
            selected: this.filter === FILTER_ALL,
            onAction: () => {
              this.filter = FILTER_ALL
            }
          })
          ForEach(METHODS, (method: string) => {
            DivCategoryChip({
              text: divMethodName(method),
              selected: this.filter === method,
              onAction: () => {
                this.filter = method
              }
            })
          }, (method: string) => method)
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .margin({ top: DesignDimens.CARD_SPACING })

        if (this.filtered().length === 0) {
          // 空态
          Column() {
            DivSeal({ text: '空', sealSize: 44, fontSize: 22 })
            Text('暂无卦象记录')
              .fontSize(12)
              .fontColor(DivColor.TEXT_TERTIARY_LIGHT)
              .margin({ top: 16 })
          }
          .width('100%')
          .alignItems(HorizontalAlign.Center)
          .padding({ top: 80 })
        } else {
          List({ space: 10 }) {
            ForEach(this.filtered(), (row: DivRecordRow) => {
              ListItem() {
                DivCard({
                  clickable: true,
                  onAction: () => this.openChart(row)
                }) {
                  Row() {
                    DivSeal({ text: this.sealOf(row.method), sealSize: 32, fontSize: 15 })
                    Column() {
                      Text(row.question.length > 0 ? row.question : '—')
                        .fontSize(13.5)
                        .fontColor(DivColor.TEXT_PRIMARY_LIGHT)
                        .maxLines(1)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                        .width('100%')
                      Text(`${this.formatDateTime(row.castAt)} · ${divMethodName(row.method)} · ${this.summaryLine(row)}`)
                        .fontSize(10.5)
                        .fontColor(DivColor.TEXT_TERTIARY_LIGHT)
                        .margin({ top: 4 })
                        .width('100%')
                    }
                    .layoutWeight(1)
                    .alignItems(HorizontalAlign.Start)
                    .margin({ left: 12 })
                    Text('›')
                      .fontSize(14)
                      .fontColor(DivColor.TEXT_TERTIARY_LIGHT)
                  }
                  .width('100%')
                  .padding({ left: 14, right: 14, top: 13, bottom: 13 })
                  .alignItems(VerticalAlign.Center)
                }
              }
            }, (row: DivRecordRow) => row.id.toString())
          }
          .width('100%')
          .layoutWeight(1)
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 4 })
        }

        DivFooterNote({ text: '卦历仅保存本机排盘记录，只读复盘不改写原盘。' })
      }
      .width('100%')
      .height('100%')
    }
  }
}
