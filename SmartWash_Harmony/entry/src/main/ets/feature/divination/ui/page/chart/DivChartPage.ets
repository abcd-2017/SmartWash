/**
 * 卦盘页（四术共用）—— 对齐安卓 ui/page/chart/DivChartPage.kt + DivChartViewModel.kt。
 *
 * 结构：统一页头（印章卦名 + 干支/口径声明行）→ 四套盘面之一 → 底部「求解读」/「存入卦历」。
 *
 * 关键约束：
 * - 从 Room 读记录 + DivChartCodec 解码（DivChartReader 还原为类实例）；禁止重算（当前时刻已变会漂移）。
 * - loading 文案态 / missing 空态（对齐安卓 DivChartPage.kt:59-82）。
 * - 「求解读」先前置拦截解读模型配置（观星台零兜底：未配置跳设置页 / 平台总开关关闭提示），
 *   再建档（幂等）跳 DivReading/{recordId}；「存入卦历」disabled 占位（入库已在起卦时完成）。
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
} from '../../../data/DivChartReader'
import { decodeBundle, decodeBundleFromJson, DivRecordRepository, DivRecordRow } from '../../../data/DivinationStore'
import { buildCreateRecordForm, createDivRecord } from '../../../network/DivinationApi'
import { getDivUserApiConfig } from '../../../network/DivSettingsApi'
import { showToastSafety } from '../../../../../utils/ToastUtil'
import { DivChartParam } from '../../DivChartParam'
import {
  DivCard,
  DivFooterNote,
  DivGoldButton,
  DivPageHeader,
  DivPalette,
  DivSerifText,
  DivText,
  DivWaveIcon,
  DIV_PAGE_PADDING
} from '../../components/DivComponents'
import { DivMiniCard, LiurenCourses, LiurenPan, LiuYaoPan, MeiHuaTripleCards, QimenGrid } from '../../components/DivPanels'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignDimens } from '../../../../../constant/DesignSystem'
import { getObjectParam, getNumberParam, getBooleanParam, pathStack } from '../../../../../utils/PathStackUtil'
import { logger } from '../../../../../utils/Logger'
import { resString } from '../../../../../utils/ResourceString'

/** 爻位名（自初爻到上爻，索引 0..5） */
function POSITION_NAMES(): string[] { return [resString('div_chart_page_1'), resString('mood_week_tue'), resString('mood_week_wed'), resString('mood_week_thu'), resString('mood_week_fri'), resString('div_chart_page_2')] }

/** 九宫名（奇门口径，与 DivPanels.GONG_NAMES 同值） */
const GONG_NAMES: Record<number, string> = {
  1: resString('div_chart_page_3'), 2: resString('div_chart_page_4'), 3: resString('div_chart_page_5'), 4: resString('div_chart_page_6'),
  5: resString('div_chart_page_7'), 6: resString('div_chart_page_8'), 7: resString('div_chart_page_9'), 8: resString('div_chart_page_10'), 9: resString('div_chart_page_11'),
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
  /** 求解读建档进行中（防重入；按钮同步置灰） */
  @Local building: boolean = false

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
      logger.warn('divChartPage', '卦盘页路由参数缺失 recordId，按空态呈现')
      this.loading = false
      return
    }
    try {
      const row = await DivRecordRepository.getById(recordId)
      this.record = row
      if (row !== null) {
        this.method = row.method
        this.decode(row)
        logger.debug('divChartPage', `卦盘解码成功 recordId=${recordId} method=${this.method}`)
      } else {
        logger.warn('divChartPage', `卦盘记录不存在 recordId=${recordId}，按空态呈现`)
      }
    } catch (err) {
      // 读取/解码失败按空态呈现
      this.record = null
      logger.warn('divChartPage', `卦盘记录读取/解码失败 recordId=${recordId}, ` +
        `${err instanceof Error ? err.message : ''}`)
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
      // 六爻取本卦名末字（对齐安卓 divSealText takeLast(1)，compare-15 #24）
      return name.length > 0 ? name.charAt(name.length - 1) : resString('div_seal_liuyao')
    }
    if (this.method === 'meihua') {
      const name = this.meiHua?.benName ?? ''
      return name.length > 0 ? name.charAt(name.length - 1) : resString('div_seal_meihua')
    }
    if (this.method === 'qimen') {
      return resString('div_seal_qimen')
    }
    return resString('div_seal_liuren')
  }

  private titleText(): string {
    if (this.method === 'liuyao') {
      const chart = this.liuYao
      if (chart === null) {
        return resString('div_method_liuyao_name')
      }
      return chart.bianName !== null ? `${chart.name} 之 ${chart.bianName}` : chart.name
    }
    if (this.method === 'meihua') {
      const chart = this.meiHua
      return chart !== null ? `${chart.benName} · ${chart.movingYao}爻动` : resString('div_method_meihua_name')
    }
    if (this.method === 'qimen') {
      return resString('div_chart_page_12')
    }
    return resString('div_chart_page_13')
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
      `${chart.isDay ? resString('div_chart_page_14') : resString('div_chart_page_15')}贵${chart.guiRen}`
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

  // ========== 求解读：先服务端建档（幂等）再进解读页 ==========

  /**
   * 求解读前置拦截（观星台零兜底：用户未配置模型时服务端解读一律 428，无平台默认模型）。
   * - GET settings/api 查询失败 → 视为无法确认可解读，阻断并提示（不静默放行）；
   * - data 为 null 或 apiKeyMask 为空 → 未配置：toast 引导 + 跳设置页（不自动弹层）；
   * - divUseEnabled === 0（旧响应缺省视为开启）→ 平台总开关关闭：toast 提示，不跳转；
   * - 已配置且开启 → 放行走原建档+跳解读链路。
   * 返回 true = 放行。日志只记判定结果，不落 baseUrl/model/key（敏感红线）。
   */
  private async ensureModelConfigReady(): Promise<boolean> {
    try {
      const resp = await getDivUserApiConfig()
      if (resp.code !== 200) {
        logger.warn('divChartPage', `解读配置获取失败 code=${resp.code} message=${resp.message ?? ''}`)
        showToastSafety(resp.message, DivText.chartConfigCheckFailed)
        return false
      }
      const config = resp.data
      if (config === null) {
        showToastSafety(null, DivText.chartModelMissing)
        pathStack.pushPathByName(RouteNames.SETTING, null)
        return false
      }
      if (config.divUseEnabled === 0) {
        // 观星台 AI 解读总开关关闭（旧响应缺省视为开启）：提示但不跳设置页
        showToastSafety(null, DivText.chartDivUseClosed)
        return false
      }
      if ((config.apiKeyMask ?? '').length === 0) {
        showToastSafety(null, DivText.chartModelMissing)
        pathStack.pushPathByName(RouteNames.SETTING, null)
        return false
      }
      return true
    } catch (err) {
      logger.error('divChartPage', '解读配置获取异常',
        err instanceof Error ? err.message : '')
      showToastSafety(null, DivText.chartConfigCheckFailed)
      return false
    }
  }

  /**
   * 「求解读」入口（#80）：
   * 0. 前置拦截解读模型配置（观星台零兜底，见 ensureModelConfigReady）；
   * 1. 本地行已关联服务端卦例（serverRecordId>0）→ 直接进解读页；
   * 2. 未关联 → POST /records 建档（lines/clientChart 由 DivChartCodec 盘面映射），
   *    成功后回写本地 serverRecordId 再进解读页（保证解读/追问/反馈用服务端 id）；
   * 3. 建档失败 → toast 错误态，停留卦盘页可重试。
   * 解读页路由参数仍传本地 id：解读/追问页按本地行关联的服务端 id 走 SSE。
   */
  private async startInterpret(): Promise<void> {
    const record = this.record
    if (record === null || this.building) {
      return
    }
    this.building = true
    try {
      // ① 前置拦截：未配置模型 / 平台总开关关闭均不进解读链路
      if (!(await this.ensureModelConfigReady())) {
        this.building = false
        return
      }
      // ② 已关联服务端卦例 → 直接进解读页
      if (record.serverRecordId > 0) {
        this.building = false
        this.openReading(record.id)
        return
      }
      // ③ 未关联 → 建档回写后进解读页
      const resp = await createDivRecord(buildCreateRecordForm(record))
      if (resp.code !== 200 || resp.data === null) {
        showToastSafety(resp.message, DivText.chartBuildFailed)
        this.building = false
        logger.warn('divChartPage', `解读建档失败 recordId=${record.id} code=${resp.code} ` +
          `message=${resp.message ?? ''}`)
        return
      }
      const serverId = resp.data
      await DivRecordRepository.updateServerRecordId(record.id, serverId)
      record.serverRecordId = serverId
      // 低频关键动作：解读建档成功（服务端卦例创建）
      logger.info('divChartPage', `解读建档成功 localId=${record.id} serverRecordId=${serverId}`)
      this.building = false
      this.openReading(record.id)
    } catch (err) {
      this.building = false
      logger.error('divChartPage', `解读建档异常 localId=${record.id}, ` +
        `${err instanceof Error ? err.message : ''}`)
      showToastSafety(null, DivText.chartBuildFailed)
    }
  }

  private openReading(localRecordId: number): void {
    pathStack.pushPathByName(RouteNames.DIV_READING, localRecordId)
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
      return resString('div_chart_page_16')
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
      lines.push(`${facts.strength.get(pos) ?? ''} · ${POSITION_NAMES()[pos - 1]}爻`)
    }
    return lines.join('\n')
  }

  @Builder
  LiuYaoCards() {
    Row({ space: 8 }) {
      Column() {
        DivMiniCard({ label: DivText.liuyaoYongshen }) {
          DivSerifText({
            text: this.liuYaoFacts?.yongShenLabel ?? '—',
            textSize: 12,
            weight: 500,
            spacing: 1,
            color: DivPalette.goldText
          })
          if (this.yongShenExtraText().length > 0) {
            DivSerifText({
              text: this.yongShenExtraText(),
              // 迷你卡注释正文 14/20（对齐安卓 audit-23 #3，compare-15 #26）
              textSize: 14,
              weight: 400,
              spacing: 0,
              color: DivPalette.textSecondary,
              lineHeight: 20
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
            // 迷你卡事实正文 14/20（对齐安卓 audit-23 #3，compare-15 #26）
            textSize: 14,
            weight: 400,
            spacing: 0,
            color: DivPalette.textPrimary,
            lineHeight: 20
          })
        }
      }
      .layoutWeight(1)
    }
    .width('100%')
    .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })
  }

  @Builder
  MeiHuaTiyongCard() {
    if (this.hasMeiHua()) {
      // 卡左右内缩：margin 必须挂在卡外容器（原误挂卡内层 Column，卡面贴屏幕左右边）
      Column() {
        DivCard({ lineColor: DivPalette.goldHair }) {
          Column() {
            DivSerifText({
              text: DivText.meihuaTiyong,
              textSize: 12,
              weight: 400,
              spacing: 2,
              color: DivPalette.textSecondary
            })
            DivSerifText({
              text: this.meiHuaTiyongText(),
              textSize: 12,
              weight: 400,
              spacing: 0,
              color: DivPalette.textPrimary,
              // 体用卡正文行高 20（对齐安卓 DivChartPage.kt:223-225，compare-15 #33）
              lineHeight: 20
            })
              .margin({ top: 4 })
          }
          .width('100%')
          .padding({
            left: DesignDimens.CARD_PADDING,
            right: DesignDimens.CARD_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_SM
          })
        }
      }
      .width('100%')
      .margin({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })
    }
  }

  @Builder
  QimenCards() {
    if (this.hasQimen()) {
      Row({ space: 8 }) {
        Column() {
          DivMiniCard({ label: DivText.qimenYongshen }) {
            DivSerifText({
              text: this.qimenYongshenMain(),
              textSize: 12,
              weight: 500,
              spacing: 0,
              color: DivPalette.goldText
            })
            DivSerifText({
              text: this.qimenYongshenSub(),
              // 值符值使正文 14/20（对齐安卓 audit-23 #3，compare-15 #26）
              textSize: 14,
              weight: 400,
              spacing: 0,
              color: DivPalette.textSecondary,
              lineHeight: 20
            })
              .margin({ top: 4 })
          }
        }
        .layoutWeight(1)

        Column() {
          DivMiniCard({ label: DivText.qimenFacts }) {
            DivSerifText({
              text: this.qimenFactsText(),
              // 宫位事实正文 14/20（对齐安卓 audit-23 #3，compare-15 #26）
              textSize: 14,
              weight: 400,
              spacing: 0,
              color: DivPalette.textPrimary,
              lineHeight: 20
            })
          }
        }
        .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })
    }
  }

  @Builder
  LiurenFooter() {
    if (this.hasLiuren()) {
      DivSerifText({
        text: this.liurenFooterText(),
        textSize: 12,
        weight: 400,
        spacing: 0,
        color: DivPalette.textTertiary,
        textAlign: TextAlign.Center
      })
        .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })
        .margin({ top: DesignDimens.SPACE_XS })
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
      // 卦盘为全屏画布页：整页扩展到系统安全区外（页头不再额外避让，避免双重避让）
      DivinationScreen({ immersive: true }) {
        // 页头外提到 loading/missing/主分支之外（对齐安卓 DivChartPage.kt:62-99，compare-15 #23）：
        // 加载/缺失期间保留返回出口；标题与卦印待数据就绪后回落纯文字
        DivPageHeader({
          title: this.record !== null && this.hasChart() ? this.titleText() : DivText.chartTitle,
          seal: this.record !== null && this.hasChart() ? this.sealText() : '',
          // 画板（卦盘双板一致）：平涂深朱印 radius 8 印文 14/700；长卦名标题字距 2
          flatSeal: true,
          titleSpacing: 2,
          onBack: () => {
            pathStack.pop()
          }
        })

        if (this.loading) {
          // 金色水波指示 + 文字 12（页头已由上方统一提供）
          Column() {
            Row() {
              DivWaveIcon({ sizeValue: 18, tint: DivPalette.gold })
              Column().width(10)
              DivSerifText({
                text: DivText.chartLoading,
                textSize: 12,
                weight: 400,
                spacing: 0,
                color: DivPalette.textTertiary
              })
            }
            .alignItems(VerticalAlign.Center)
          }
          .width('100%')
          .padding({ top: 120 })
          .alignItems(HorizontalAlign.Center)
        } else if (this.record === null || !this.hasChart()) {
          DivSerifText({
            text: DivText.chartMissing,
            textSize: 12,
            weight: 400,
            spacing: 0,
            color: DivPalette.textTertiary,
            textAlign: TextAlign.Center
          })
            .padding({ top: 80 })
        } else {
          // 干支/口径声明行（画板：12/16 字距 2 居中，主题金 75%）
          DivSerifText({
            text: this.declarationText(),
            textSize: 12,
            weight: 400,
            spacing: 2,
            color: DivPalette.goldText,
            textAlign: TextAlign.Center
          })
            .opacity(0.75)
            .padding({
              left: DIV_PAGE_PADDING,
              right: DIV_PAGE_PADDING,
              top: DesignDimens.SPACE_XXS,
              bottom: DesignDimens.SPACE_XXS
            })

          // 奇门口径徽标（文字取语义金 goldText，对齐安卓 c.goldText）
          if (this.method === 'qimen') {
            DivSerifText({
              text: this.juDeclaration(),
              textSize: 12,
              weight: 500,
              spacing: 3,
              color: DivPalette.goldText,
              textAlign: TextAlign.Center
            })
              .padding({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
          }

          Column().height(DesignDimens.SPACE_XS)

          // 四套盘面
          if (this.liuYao !== null) {
            // 六爻盘不再加页留白：盘体自带轨道内缩（左 28 / 右 20），与安卓无 modifier 一致
            // （原多套一层 PAGE_PADDING，内容左内缩 52vp vs 安卓 28dp）
            LiuYaoPan({ chart: this.liuYao, facts: this.liuYaoFacts, animateEntry: this.animateEntry })
            // 画板：末行到事实卡间距 SPACE_XL（24）
            Column().height(DesignDimens.SPACE_XL)
            this.LiuYaoCards()
          } else if (this.meiHua !== null) {
            MeiHuaTripleCards({ chart: this.meiHua })
              .margin({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })
            Column().height(DesignDimens.SPACE_SM)
            this.MeiHuaTiyongCard()
          } else if (this.qimen !== null) {
            QimenGrid({ chart: this.qimen })
              .margin({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })
            Column().height(DesignDimens.SPACE_SM)
            this.QimenCards()
          } else if (this.liuren !== null) {
            Column() {
              LiurenPan({ chart: this.liuren })
            }
            .width('100%')
            .alignItems(HorizontalAlign.Center)
            Column().height(DesignDimens.SPACE_SM)
            LiurenCourses({ chart: this.liuren })
              .margin({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })
            this.LiurenFooter()
          }

          Column().height(DesignDimens.SPACE_MD)

          // 底部双按钮（对齐安卓 DivGoldButton 默认 52/r14/15sp，卡距 8，compare-15 #28）
          Row({ space: 8 }) {
            Column() {
              DivGoldButton({
                text: this.building ? DivText.chartBuilding : DivText.chartInterpret,
                isEnabled: !this.building,
                btnHeight: 52,
                fontSize: 15,
                onAction: () => {
                  this.startInterpret()
                }
              })
            }
            .layoutWeight(1)
            Column() {
              DivGoldButton({
                text: DivText.chartSaved,
                isEnabled: false,
                btnHeight: 52,
                fontSize: 15,
                onAction: () => {
                }
              })
            }
            .layoutWeight(1)
          }
          .width('100%')
          // 底部按钮行左右对称（统一 DIV_PAGE_PADDING 20）
          .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })

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
    case 1: return resString('mood_week_mon')
    case 2: return resString('mood_week_tue')
    case 3: return resString('mood_week_wed')
    case 4: return resString('mood_week_thu')
    case 5: return resString('mood_week_fri')
    case 6: return resString('div_seal_liuyao')
    case 7: return resString('div_chart_page_17')
    case 8: return resString('div_chart_page_18')
    case 9: return resString('div_chart_page_19')
    default: return `${n}`
  }
}
