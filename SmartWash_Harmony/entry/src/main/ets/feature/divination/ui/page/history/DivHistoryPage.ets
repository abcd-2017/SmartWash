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
import {
  DivCard,
  DivCategoryChip,
  DivFooterNote,
  DivPageHeader,
  DivPalette,
  DivSeal,
  DivText
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignDimens } from '../../../../../constant/DesignSystem'
import { pathStack } from '../../../../../utils/PathStackUtil'
import { logger } from '../../../../../utils/Logger'

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

  private async refresh(): Promise<void> {
    this.isLoading = true
    try {
      const all = await DivRecordRepository.observeAll()
      this.records = all
      // 全量拉取（无分页），成功仅 debug 留痕
      logger.debug('divHistoryPage', `卦历加载完成 total=${all.length}`)
    } catch (err) {
      this.records = []
      logger.warn('divHistoryPage', `卦历加载失败，按空态呈现, ${err instanceof Error ? err.message : ''}`)
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
    // 修复（问题14）：命名路由的 buildFunction 根节点必须是 NavDestination，
    // 本页原先裸渲染 DivinationScreen，push 进来直接整页空白
    NavDestination() {
      DivinationScreen({ scrollable: false }) {
      Column() {
        // 无返回页头标题左对齐 x20（alignStart）
        DivPageHeader({ title: DivText.historyTitle, showBack: false, alignStart: true })

        // 术数筛选（20 缩进 + 横向滚动 + 单行限高，窄屏滚动可见不折行）
        Scroll() {
          Row({ space: 8 }) {
            DivCategoryChip({
              text: DivText.filterAll,
              selected: this.filter === FILTER_ALL,
              horizontalPadding: 14,
              onAction: () => {
                this.filter = FILTER_ALL
              }
            })
            ForEach(METHODS, (method: string) => {
              DivCategoryChip({
                text: divMethodName(method),
                selected: this.filter === method,
                horizontalPadding: 14,
                onAction: () => {
                  this.filter = method
                }
              })
            }, (method: string) => method)
          }
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        }
        .scrollable(ScrollDirection.Horizontal)
        .scrollBar(BarState.Off)
        .width('100%')
        .align(Alignment.TopStart)
        .margin({ top: 16 })

        // 空态与列表互相替换走交叉淡入淡出（对齐安卓 Crossfade，避免 0ms 硬切）
        Stack() {
          // 空态层
          Column() {
            DivSeal({ text: '空', sealSize: 44, fontSize: 22 })
            Text(DivText.historyEmpty)
              .fontSize(12)
              .fontColor(DivPalette.textTertiary)
              .margin({ top: 16 })
          }
          .width('100%')
          .alignItems(HorizontalAlign.Center)
          .padding({ top: 80 })
          .opacity(this.filtered().length === 0 ? 1 : 0)
          .animation({ duration: 180, curve: Curve.EaseInOut })

          // 列表层（记录卡间距统一 CARD_SPACING）
          List({ space: DesignDimens.CARD_SPACING }) {
            ForEach(this.filtered(), (row: DivRecordRow) => {
              ListItem() {
                DivCard({
                  clickable: true,
                  onAction: () => this.openChart(row)
                }) {
                  Row() {
                    DivSeal({ text: this.sealOf(row.method), sealSize: 32, fontSize: 14 })
                    Column() {
                      Text(row.question.length > 0 ? row.question : '—')
                        .fontSize(14)
                        .fontColor(DivPalette.textPrimary)
                        .maxLines(1)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                        .width('100%')
                      Text(`${this.formatDateTime(row.castAt)} · ${divMethodName(row.method)} · ${this.summaryLine(row)}`)
                        .fontSize(12)
                        .fontColor(DivPalette.textSecondary)
                        .margin({ top: 2 })
                        .width('100%')
                    }
                    .layoutWeight(1)
                    .alignItems(HorizontalAlign.Start)
                    .margin({ left: 12 })
                    Text('›')
                      .fontSize(14)
                      .fontColor(DivPalette.textSecondary)
                  }
                  .width('100%')
                  // 左内边距 CARD_PADDING；右侧留给 › 箭头，仅 SPACE_XS
                  .padding({
                    left: DesignDimens.CARD_PADDING,
                    right: DesignDimens.SPACE_XS,
                    top: DesignDimens.SPACE_SM,
                    bottom: DesignDimens.SPACE_SM
                  })
                  .alignItems(VerticalAlign.Center)
                }
              }
            }, (row: DivRecordRow) => row.id.toString())
          }
          .width('100%')
          .height('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_XXS })
          .opacity(this.filtered().length === 0 ? 0 : 1)
          .animation({ duration: 180, curve: Curve.EaseInOut })
        }
        .width('100%')
        .layoutWeight(1)

        DivFooterNote({ text: DivText.historyNote })
      }
      .width('100%')
      .height('100%')
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 从卦盘/复盘返回后重拉，保证卦历与最新排盘一致（NavDestination 可见性事件驱动）
      this.refresh()
    })
  }
}
