/**
 * 卦盘页（四术共用）—— 对齐安卓 ui/page/chart/DivChartPage.kt + DivChartViewModel.kt。
 *
 * 结构：统一页头（印章卦名 + 干支/口径声明行）→ 四套盘面之一 → 底部「求解读」/「存入卦历」。
 *
 * 关键约束：
 * - 从 Room 读记录 + DivChartCodec 解码（DivChartReader 还原为类实例）；禁止重算（当前时刻已变会漂移）。
 * - loading 文案态 / missing 空态（对齐安卓 DivChartPage.kt:59-82）。
 * - 「求解读」跳 DivReading/{recordId}；「存入卦历」disabled 占位（入库已在起卦时完成）。
 * - 奇门专属口径徽标；六壬无三传时显示 unsupportedNote。
 */

import { LiuYaoChart, LiuYaoFacts } from '../../../core/liuyao/LiuYaoChart'
import { MeiHuaChart } from '../../../core/meihua/MeiHuaChart'
import { QiMenChart } from '../../../core/qimen/QiMenChart'
import { LiuRenChart } from '../../../core/liuren/LiuRenChart'
import { DivCategory } from '../../../core/DivinationCommon'
import {
  readLiuYaoChart,
  readMeiHuaChart,
  readQiMenChart,
  readLiuRenChart
} from '../../data/DivChartReader'
import { decodeBundle, decodeBundleFromJson, DivRecordRepository, DivRecordRow } from '../../data/DivinationStore'
import { DivChartParam } from '../../DivChartParam'
import {
  DivCard,
  DivFooterNote,
  DivGoldButton,
  DivPageHeader,
  DivPalette,
  DivSerifText,
  DivText
} from '../../components/DivComponents'
import { DivMiniCard, LiurenCourses, LiurenPan, LiuYaoPan, MeiHuaTripleCards, QimenGrid } from '../../components/DivPanels'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../constant/RouteNames'
import { getObjectParam, getNumberParam, getBooleanParam, pathStack } from '../../../../utils/PathStackUtil'

/** 爻位名（自初爻到上爻，索引 0..5） */
const POSITION_NAMES: string[] = ['初', '二', '三', '四', '五', '上']

/** 九宫名（奇门口径，与 DivPanels.GONG_NAMES 同值） */
const GONG_NAMES: Record<number, string> = {
  1: '坎一', 2: '坤二', 3: '震三', 4: '巽四',
  5: '中五', 6: '乾六', 7: '兑七', 8: '艮八', 9: '离九',
}

@Builder
export function DivChartBuilder() {
  DivChartPage()
}

@ComponentV2
struct DivChartPage {
  @Local loading: boolean = true
  @Local record: DivRecordRow | null = null
  @Local method: string = ''
  @Local liuYao: LiuYaoChart | null = null
  @Local meiHua: MeiHuaChart | null = null
  @Local qimen: QiMenChart | null = null
  @Local liuren: LiuRenChart | null = null
  @Local liuYaoFacts: LiuYaoFacts | null = null
  @Local animateEntry: boolean = false

  async aboutToAppear(): Promise<void> {
    // 路由入参：recordId 必填；animate 默认 false（对齐安卓 DivChart/{recordId}?animate={animate}）
    let recordId: number | null = null
    let animate: boolean = false
    const param = getObjectParam<DivChartParam>(RouteNames.DIV_CHART)
    if (param !== null) {
      recordId = param.recordId
      animate = param.animate
    } else {
      // 兼容旧调用点（裸 number 传参）：recordId / animate 分别回退安全取参
      recordId = getNumberParam(RouteNames.DIV_CHART)
      animate = getBooleanParam(RouteNames.DIV_CHART) ?? false
    }
    this.animateEntry = animate
    if (recordId === null) {
      this.loading = false
      return
    }
    try {
      const row = await DivRecordRepository.getById(recordId)
      this.record = row
      if (row !== null) {
        this.method = row.method
        this.decode(row)
      }
    } catch (err) {
      // 读取/解码失败按空态呈现
      this.record = null
    }
    this.loading = false
  }

  /** 读库解码（禁止重算）：chartJson → 类实例 + 六爻事实链 */
  private decode(row: DivRecordRow): void {
    const bundle = decodeBundleFromJson(row.chartJson)
    const chartJson = decodeBundle(bundle)
    if (row.method === 'liuyao') {
      const chart = readLiuYaoChart(chartJson)
      this.liuYao = chart
      this.liuYaoFacts = LiuYaoFacts.of(chart, DivCategory.fromId(row.category))
    } else if (row.method === 'meihua') {
      this.meiHua = readMeiHuaChart(chartJson)
    } else if (row.method === 'qimen') {
      this.qimen = readQiMenChart(chartJson)
    } else if (row.method === 'liuren') {
      this.liuren = readLiuRenChart(chartJson)
    }
  }

  // ========== 头部文案 ==========

  private sealText(): string {
    if (this.method === 'liuyao') {
      const name = this.liuYao?.name ?? ''
      return name.length > 0 ? name.charAt(0) : '卦'
    }
    if (this.method === 'meihua') {
      const name = this.meiHua?.benName ?? ''
      return name.length > 0 ? name.charAt(name.length - 1) : '梅'
    }
    if (this.method === 'qimen') {
      return '奇'
    }
    return '壬'
  }

  private titleText(): string {
    if (this.method === 'liuyao') {
      const chart = this.liuYao
      if (chart === null) {
        return '六爻'
      }
      return chart.bianName !== null ? `${chart.name} 之 ${chart.bianName}` : chart.name
    }
    if (this.method === 'meihua') {
      const chart = this.meiHua
      return chart !== null ? `${chart.benName} · ${chart.movingYao}爻动` : '梅花'
    }
    if (this.method === 'qimen') {
      return '奇门遁甲'
    }
    return '大六壬'
  }

  private declarationText(): string {
    if (this.method === 'liuyao') {
      const chart = this.liuYao
      if (chart === null) {
        return ''
      }
      return `${chart.siZhu.labels().join(' ')} · 旬空 ${chart.xunKong} · ${chart.gongDeclaration()}`
    }
    if (this.method === 'meihua') {
      return this.meiHua?.derivation ?? ''
    }
    if (this.method === 'qimen') {
      const chart = this.qimen
      if (chart === null) {
        return ''
      }
      return `${chart.siZhu.labels().join(' ')} · ${chart.jieQiName}后 · 旬首${chart.xunShouYi}`
    }
    const chart = this.liuren
    if (chart === null) {
      return ''
    }
    return `${chart.dayGanZhi}日 ${chart.hourGanZhi}时 · 月将 ${chart.yueJiang}（${chart.yueJiangName}）· ` +
      `${chart.isDay ? '昼占' : '夜占'}贵${chart.guiRen}`
  }

  private juDeclaration(): string {
    const chart = this.qimen
    if (chart === null) {
      return ''
    }
    return `${chart.yinYang}${cnJuNumber(chart.juNumber)}局 · ${chart.yuan} · 拆补转盘`
  }

  private hasChart(): boolean {
    return this.liuYao !== null || this.meiHua !== null || this.qimen !== null || this.liuren !== null
  }

  // ========== 六爻：事实小卡内容 ==========

  private factsText(): string {
    const facts = this.liuYaoFacts
    if (facts === null) {
      return ''
    }
    const lines: string[] = []
    if (facts.xunKongYao.length > 0) {
      lines.push(`旬空${this.joinPositions(facts.xunKongYao)}`)
    }
    if (facts.yuePo.length > 0) {
      lines.push(`月破${this.joinPositions(facts.yuePo)}`)
    }
    if (facts.riChong.length > 0) {
      lines.push(`日冲${this.joinPositions(facts.riChong)}`)
    }
    facts.huiTou.forEach((rel: string, pos: number) => {
      lines.push(`${pos}爻${rel}`)
    })
    if (lines.length === 0) {
      return '未见动变冲空之象'
    }
    return lines.join('\n')
  }

  private joinPositions(positions: number[]): string {
    const parts: string[] = []
    for (const pos of positions) {
      parts.push(`${pos}爻`)
    }
    return parts.join('、')
  }

  // ========== 奇门：宫位事实 ==========

  private qimenGongName(gong: number): string {
    return GONG_NAMES[gong] ?? `${gong}宫`
  }

  // ========== 渲染 ==========

  @Builder
  MissingState() {
    Column() {
      DivPageHeader({
        title: DivText.chartTitle,
        onBack: () => {
          pathStack.pop()
        }
      })
      DivSerifText({
        text: DivText.chartMissing,
        size: 13,
        weight: 400,
        spacing: 0,
        color: DivPalette.textTertiary,
        align: TextAlign.Center
      })
        .width('100%')
        .padding({ top: 80 })
    }
    .width('100%')
  }

  /** 用神说明副行（含旺衰·爻位；无则空串） */
  private yongShenExtraText(): string {
    const facts = this.liuYaoFacts
    if (facts === null) {
      return ''
    }
    const lines: string[] = []
    if (facts.yongShenNote !== null) {
      lines.push(facts.yongShenNote)
    }
    if (facts.yongShenPosition !== null) {
      const pos = facts.yongShenPosition
      lines.push(`${facts.strength.get(pos) ?? ''} · ${POSITION_NAMES[pos - 1]}爻`)
    }
    return lines.join('\n')
  }

  @Builder
  LiuYaoCards() {
    Row() {
      Column() {
        DivMiniCard({ label: DivText.liuyaoYongshen }) {
          DivSerifText({
            text: this.liuYaoFacts?.yongShenLabel ?? '—',
            size: 12.5,
            weight: 500,
            spacing: 1,
            color: DivPalette.gold
          })
          if (this.yongShenExtraText().length > 0) {
            DivSerifText({
              text: this.yongShenExtraText(),
              size: 10,
              weight: 400,
              spacing: 0,
              color: DivPalette.textSecondary
            })
              .margin({ top: 4 })
          }
        }
      }
      .layoutWeight(1)

      Column() {
        DivMiniCard({ label: DivText.liuyaoFacts }) {
          DivSerifText({
            text: this.factsText(),
            size: 11,
            weight: 400,
            spacing: 0,
            color: DivPalette.textPrimary
          })
        }
      }
      .layoutWeight(1)
    }
    .width('100%')
    .padding({ left: 20, right: 20 })
  }

  @Builder
  MeiHuaTiyongCard() {
    if (this.hasMeiHua()) {
      DivCard({ borderColor: DivPalette.goldHair }) {
        Column() {
          DivSerifText({
            text: DivText.meihuaTiyong,
            size: 9.5,
            weight: 400,
            spacing: 2,
            color: DivPalette.textTertiary
          })
          DivSerifText({
            text: this.meiHuaTiyongText(),
            size: 11,
            weight: 400,
            spacing: 0,
            color: DivPalette.textPrimary
          })
            .margin({ top: 5 })
        }
        .width('100%')
        .padding({ left: 12, right: 12, top: 10, bottom: 10 })
      }
      .margin({ left: 20, right: 20 })
    }
  }

  @Builder
  QimenCards() {
    if (this.hasQimen()) {
      Row() {
        Column() {
          DivMiniCard({ label: DivText.qimenYongshen }) {
            DivSerifText({
              text: this.qimenYongshenMain(),
              size: 12,
              weight: 500,
              spacing: 0,
              color: DivPalette.gold
            })
            DivSerifText({
              text: this.qimenYongshenSub(),
              size: 10,
              weight: 400,
              spacing: 0,
              color: DivPalette.textSecondary
            })
              .margin({ top: 4 })
          }
        }
        .layoutWeight(1)

        Column() {
          DivMiniCard({ label: DivText.qimenFacts }) {
            DivSerifText({
              text: this.qimenFactsText(),
              size: 11,
              weight: 400,
              spacing: 0,
              color: DivPalette.textPrimary
            })
          }
        }
        .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 20, right: 20 })
    }
  }

  @Builder
  LiurenFooter() {
    if (this.hasLiuren()) {
      DivSerifText({
        text: this.liurenFooterText(),
        size: 11,
        weight: 400,
        spacing: 0,
        color: DivPalette.textTertiary,
        align: TextAlign.Center
      })
        .width('100%')
        .padding({ left: 20, right: 20 })
        .margin({ top: 8 })
    }
  }

  // ========== 盘面文案（供 @Builder 调用，避免 build 内声明） ==========

  private hasMeiHua(): boolean {
    return this.meiHua !== null
  }

  private hasQimen(): boolean {
    return this.qimen !== null
  }

  private hasLiuren(): boolean {
    return this.liuren !== null
  }

  private meiHuaTiyongText(): string {
    const chart = this.meiHua
    if (chart === null) {
      return ''
    }
    return `动爻在${chart.movingYao}爻 → 下卦${chart.lower.label}${chart.lower.wuXing.label}为用，` +
      `上卦${chart.upper.label}${chart.upper.wuXing.label}为体：${chart.tiYongRelation.label}，${chart.tiYongRelation.phrase}。`
  }

  private qimenYongshenMain(): string {
    const chart = this.qimen
    return chart === null ? '' : `日干${chart.siZhu.day.gan.label} · 时干${chart.siZhu.hour.gan.label}`
  }

  private qimenYongshenSub(): string {
    const chart = this.qimen
    return chart === null
      ? ''
      : `值符${chart.zhiFuXing}落${this.qimenGongName(chart.zhiFuLuoGong)} · ` +
        `值使${chart.zhiShiMen}落${this.qimenGongName(chart.zhiShiGong)}`
  }

  private qimenFactsText(): string {
    const chart = this.qimen
    if (chart === null) {
      return ''
    }
    const gongParts: string[] = []
    for (const gong of chart.kongWangGong) {
      gongParts.push(this.qimenGongName(gong))
    }
    return `空亡${chart.kongWangZhi.join('')}落${gongParts.join('、')}\n` +
      `驿马${chart.maZhi}在${this.qimenGongName(chart.maGong)}`
  }

  private liurenFooterText(): string {
    const chart = this.liuren
    if (chart === null) {
      return ''
    }
    return chart.unsupportedNote ?? `课体 · ${chart.keTi ?? ''} · 九宗门 ${chart.jiuZongMen ?? ''}`
  }

  build() {
    NavDestination() {
      DivinationScreen() {
        if (this.loading) {
          Column() {
            DivPageHeader({ title: DivText.chartTitle })
            DivSerifText({
              text: DivText.chartLoading,
              size: 12,
              weight: 400,
              spacing: 0,
              color: DivPalette.textTertiary,
              align: TextAlign.Center
            })
              .width('100%')
              .padding({ top: 120 })
          }
          .width('100%')
        } else if (this.record === null || !this.hasChart()) {
          this.MissingState()
        } else {
          DivPageHeader({
            title: this.titleText(),
            seal: this.sealText(),
            onBack: () => {
              pathStack.pop()
            }
          })

          // 干支/口径声明行
          DivSerifText({
            text: this.declarationText(),
            size: 11,
            weight: 400,
            spacing: 2,
            color: DivPalette.gold,
            align: TextAlign.Center
          })
            .width('100%')
            .padding({ left: 20, right: 20, top: 4, bottom: 4 })

          // 奇门口径徽标
          if (this.method === 'qimen') {
            DivSerifText({
              text: this.juDeclaration(),
              size: 12,
              weight: 500,
              spacing: 3,
              color: DivPalette.gold,
              align: TextAlign.Center
            })
              .width('100%')
              .padding({ top: 8, bottom: 8 })
          }

          Column().height(8)

          // 四套盘面
          if (this.liuYao !== null) {
            LiuYaoPan({ chart: this.liuYao, facts: this.liuYaoFacts, animateEntry: this.animateEntry })
              .margin({ left: 20, right: 20 })
            Column().height(12)
            this.LiuYaoCards()
          } else if (this.meiHua !== null) {
            MeiHuaTripleCards({ chart: this.meiHua })
              .margin({ left: 20, right: 20 })
            Column().height(12)
            this.MeiHuaTiyongCard()
          } else if (this.qimen !== null) {
            QimenGrid({ chart: this.qimen })
              .margin({ left: 20, right: 20 })
            Column().height(12)
            this.QimenCards()
          } else if (this.liuren !== null) {
            Column() {
              LiurenPan({ chart: this.liuren })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Center)
            Column().height(12)
            LiurenCourses({ chart: this.liuren })
              .margin({ left: 20, right: 20 })
            this.LiurenFooter()
          }

          Column().height(16)

          // 底部双按钮
          Row() {
            Column() {
              DivGoldButton({
                text: DivText.chartInterpret,
                isEnabled: true,
                onClick: () => {
                  const id = this.record?.id ?? 0
                  pathStack.pushPathByName(RouteNames.DIV_READING, id)
                }
              })
            }
            .layoutWeight(1)
            Column().width(10)
            Column() {
              DivGoldButton({
                text: DivText.chartSaved,
                isEnabled: false,
                onClick: () => {
                }
              })
            }
            .layoutWeight(1)
          }
          .width('100%')
          .padding({ left: 20, right: 20 })

          DivFooterNote({ text: DivText.disclaimerFooter })
        }
      }
    }
    .hideTitleBar(true)
  }
}

/** 局数中文数字（1..9 → 一..九） */
function cnJuNumber(n: number): string {
  switch (n) {
    case 1: return '一'
    case 2: return '二'
    case 3: return '三'
    case 4: return '四'
    case 5: return '五'
    case 6: return '六'
    case 7: return '七'
    case 8: return '八'
    case 9: return '九'
    default: return `${n}`
  }
}
