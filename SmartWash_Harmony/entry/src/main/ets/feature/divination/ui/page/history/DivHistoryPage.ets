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
  DIV_PAGE_PADDING,
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
import { resString } from '../../../../../utils/ResourceString'

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
  // 三态判定（compare-08 #2，对齐安卓 audit-08 #3 修复）：true=首载未完成（渲染骨架行，
  // 不再以空态冒充首帧）；false 且筛选结果为空=数据库真空（空态）；false 且非空=列表。
  // 仅由初值 true → 首次加载完成置 false 单向翻转；refresh() 不再置回 true，
  // onShown 重拉静默不回亮骨架（对齐安卓 Flow null→数据 无回跳感）
  @Local isLoading: boolean = true

  aboutToAppear(): void {
    this.refresh()
  }

  private async refresh(): Promise<void> {
    // 静默重拉约定（compare-08 #2）：不在此处置 isLoading=true——骨架只挂首载，
    // onShown 从卦盘/复盘返回重拉时列表原地刷新、不回亮骨架
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
      // 画板：六爻印取「六」（2026-11-06 画板改版，compare-08 #5）
      return resString('div_seal_liuyao')
    }
    if (method === 'meihua') {
      return resString('div_seal_meihua')
    }
    if (method === 'qimen') {
      return resString('div_seal_qimen')
    }
    return resString('div_seal_liuren')
  }

  private openChart(row: DivRecordRow): void {
    // 复盘：animate=false（对齐安卓 navigate Chart/{id}?animate=false）
    pathStack.pushPathByName(RouteNames.DIV_CHART, row.id)
  }

  /** 加载骨架行（compare-08 #2，对齐安卓 HistorySkeletonRow）：与卦例卡同形——
   *  32×r8 印位浅块 + 60%×14 / 40%×12 文案浅块（DivPalette.hair 12% 金发丝静态浅块，
   *  即规范 §8 骨架微光的降级形态，无动效即免降级分支），内边距镜像卡面画板口径 14/8/12/12 */
  @Builder
  SkeletonRow() {
    DivCard() {
      Row() {
        Column()
          .width(32)
          .height(32)
          .borderRadius(8)
          .backgroundColor(DivPalette.hair)
        Column() {
          Column()
            .width('60%')
            .height(14)
            .backgroundColor(DivPalette.hair)
          Column()
            .width('40%')
            .height(12)
            .backgroundColor(DivPalette.hair)
            .margin({ top: 2 })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        .margin({ left: 12 })
      }
      .width('100%')
      .padding({ left: 14, right: 8, top: 12, bottom: 12 })
      .alignItems(VerticalAlign.Center)
    }
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
          .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })
        }
        .scrollable(ScrollDirection.Horizontal)
        .scrollBar(BarState.Off)
        .width('100%')
        .align(Alignment.TopStart)

        // 空态与列表互相替换走交叉淡入淡出（对齐安卓 Crossfade，避免 0ms 硬切）；
        // 三态：isLoading=骨架 / 真空=空态 / 非空=列表（compare-08 #2）。
        // 画板间距（compare-08 #11）：页头→筛选 10、筛选→内容 20 = Stack 顶 16 + 列表顶 padding 4
        Stack() {
          // 加载层：骨架行 ×3（仅首载期间可见；首载完成后该层恒隐，重拉不回亮）
          Column({ space: 8 }) {
            this.SkeletonRow()
            this.SkeletonRow()
            this.SkeletonRow()
          }
          .width('100%')
          .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING, top: DesignDimens.SPACE_XXS })
          .opacity(this.isLoading ? 1 : 0)
          .animation({ duration: 180, curve: Curve.EaseInOut })

          // 空态层（仅在首载完成且筛选结果为空时呈现，加载期不再以空态冒充首帧）
          Column() {
            // 卦历画板 = flat 平涂深朱口径（DivSeal 组件契约），印文对比 5.31:1 达标（compare-08 #1）
            DivSeal({ text: resString('div_history_page_1'), sealSize: 44, fontSize: 22, flat: true })
            Text(DivText.historyEmpty)
              .fontSize(12)
              .fontColor(DivPalette.textTertiary)
              .margin({ top: 16 })
          }
          .width('100%')
          .alignItems(HorizontalAlign.Center)
          .padding({ top: 80 })
          .opacity(!this.isLoading && this.filtered().length === 0 ? 1 : 0)
          .animation({ duration: 180, curve: Curve.EaseInOut })

          // 列表层（画板：卡间距 8 · 卡高 60，compare-08 #9 勿用全局 CARD_SPACING=16）
          List({ space: 8 }) {
            ForEach(this.filtered(), (row: DivRecordRow) => {
              ListItem() {
                DivCard({
                  clickable: true,
                  onAction: () => this.openChart(row)
                }) {
                  Row() {
                    // 卦历画板 = flat 平涂深朱口径（DivSeal 组件契约），印文对比 5.31:1 达标（compare-08 #1）
                    DivSeal({ text: this.sealOf(row.method), sealSize: 32, fontSize: 14, flat: true })
                    Column() {
                      Text(row.question.length > 0 ? row.question : '—')
                        .fontSize(14)
                        .lineHeight(20)
                        .fontColor(DivPalette.textPrimary)
                        .maxLines(1)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                        .width('100%')
                      Text(`${this.formatDateTime(row.castAt)} · ${divMethodName(row.method)} · ${this.summaryLine(row)}`)
                        .fontSize(12)
                        .fontColor(DivPalette.textSecondary)
                        // 与主文案同策略单行截断（compare-08 #4）：长摘要不换行撑破「卡高 60」画板口径
                        .maxLines(1)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                        .margin({ top: 2 })
                        .width('100%')
                    }
                    .layoutWeight(1)
                    .alignItems(HorizontalAlign.Start)
                    .margin({ left: 12 })
                    // 服务端建档状态（#80：本地为主 + 同步标记；serverRecordId>0 即已建档可解读）
                    if (row.serverRecordId > 0) {
                      Text(DivText.historySynced)
                        .fontSize(10)
                        .fontColor(DivPalette.goldText)
                        .opacity(0.75)
                        .margin({ right: 4 })
                    }
                    Text('›')
                      .fontSize(14)
                      .fontColor(DivPalette.textSecondary)
                  }
                  .width('100%')
                  // 画板（卡高 60）：印章距左 14（compare-08 #8）；右侧留给 › 箭头 8、上下 12
                  .padding({
                    left: 14,
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
          // 画板：内容左右 20（compare-08 #10），顶 4 / 底 4（compare-08 #14 补底 4）
          .padding({
            left: DIV_PAGE_PADDING,
            right: DIV_PAGE_PADDING,
            top: DesignDimens.SPACE_XXS,
            bottom: DesignDimens.SPACE_XXS
          })
          .opacity(!this.isLoading && this.filtered().length > 0 ? 1 : 0)
          .animation({ duration: 180, curve: Curve.EaseInOut })
        }
        .width('100%')
        .layoutWeight(1)
        // 画板间距（compare-08 #11）：筛选→内容 20 = 此处 16 + 列表自身顶 padding 4
        .margin({ top: 16 })

        DivFooterNote({ text: DivText.historyNote })
      }
      .width('100%')
      .height('100%')
      }
    }
    .hideTitleBar(true)
    .onShown(() => {
      // 从卦盘/复盘返回后重拉，保证卦历与最新排盘一致（NavDestination 可见性事件驱动）；
      // 重拉静默：refresh() 不回置 isLoading，骨架不回亮（对齐安卓 Flow 无回跳感）
      this.refresh()
    })
  }
}
