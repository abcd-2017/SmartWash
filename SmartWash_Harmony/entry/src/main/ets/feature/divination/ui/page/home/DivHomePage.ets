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
import { MeiHuaChart } from '../../../core/meihua/MeiHuaChart'
import { DivRecordRepository, DivRecordRow, encodeBundle, encodeBundleToJson } from '../../../data/DivinationStore'
import { encodeChartJson } from '../../../data/DivChartCodec'
import { getDivToday } from '../../../network/DivinationApi'
import {
  CompassDial,
  DIV_PAGE_PADDING,
  DivCard,
  DivFooterNote,
  DivHaptic,
  DivPalette,
  DivSectionTitle,
  DivSeal,
  DivSerifText,
  DivText,
  MethodGlyph,
  TianXiangHeader
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignDimens, DesignElevation } from '../../../../../constant/DesignSystem'
import { pathStack } from '../../../../../utils/PathStackUtil'
import { Debouncer } from '../../../../../utils/DebounceUtil'
import { logger } from '../../../../../utils/Logger'
import { DivChartParam } from '../../DivChartParam'
import { resColor } from '../../../../../utils/ResourceColor'
import { resString } from '../../../../../utils/ResourceString'

/** 四术玉牌展示元数据（对齐安卓 DivHomePage.kt:101-106 的 Quad 列表） */
const METHOD_LABELS: Record<string, string> = {
  'liuyao': DivText.methodLiuyaoName,
  'meihua': DivText.methodMeihuaName,
  'qimen': DivText.methodQimenName,
  'liuren': DivText.methodLiurenName,
}

/**
 * 四术玉牌元数据（对齐安卓 JadeEntryTile：术名 + 一句话定位 + 适用场景 + 推荐 tag）。
 * compare-10 #4 修正误判：安卓 JadeEntryTile 实际消费 div_method_*_use_for 渲染第三行
 * （DivHomePage.kt:393-399，desc 之下、tag 之上），本端同步补 useFor 行。
 */
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
  /** 服务端今日一签卦例 id（GET /today 幂等建档返回；0 = 未拉取/未登录/失败） */
  @Local serverTodayId: number = 0
  // 玉牌按压缩放态（pressScale 0.97）+ 点击节流防抖（对齐安卓 rememberDebouncedClick）
  @Local pressedKey: string = ''
  // 今日一签保存中（compare-10 #1 对齐安卓 signSaving 三件套：保存中禁点 + 尾缀「…」+ 同步置位防连点）
  @Local signSaving: boolean = false
  private debouncer: Debouncer = new Debouncer()

  async aboutToAppear(): Promise<void> {
    await this.refresh()
  }

  /**
   * 每次进入计算天象头 + 今日一签（确定性，不落库；落库只在点击时，对齐 DivHomeViewModel.refresh）。
   * #80：并行静默拉 GET /today（服务端按当日时间卦生成/复用，幂等），命中后把服务端卦例 id
   * 关联到本地今日一签记录，避免点击求解读时重复 POST /records；失败不阻塞本地展示。
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
      // 无签数据时不显示卡（对齐 DivHomePage.kt:129-160 的 sign !== null 分支）
      this.hasTodaySign = false
      logger.warn('divHomePage', `今日一签计算失败，隐藏签卡, ${err instanceof Error ? err.message : ''}`)
    }

    try {
      const resp = await getDivToday()
      if (resp.code === 200 && resp.data !== null) {
        this.serverTodayId = resp.data.id
        logger.debug('divHomePage', `服务端今日一签命中 serverRecordId=${this.serverTodayId}`)
      } else {
        this.serverTodayId = 0
      }
    } catch (err) {
      // 未登录 / 网络失败静默兜底：今日一签仍走本地，服务端建档延迟到求解读时
      this.serverTodayId = 0
      logger.debug('divHomePage', `服务端今日一签未获取（本地兜底）, ${err instanceof Error ? err.message : ''}`)
    }

    try {
      this.records = await DivRecordRepository.recent(3)
    } catch (err) {
      this.records = []
      logger.warn('divHomePage', `案卷加载失败，按空态呈现, ${err instanceof Error ? err.message : ''}`)
    }
    logger.debug('divHomePage', `观象台首页加载完成 hasTodaySign=${this.hasTodaySign} ` +
      `serverTodayId=${this.serverTodayId} records=${this.records.length}`)
  }

  /**
   * 今日一签入库（同日去重：当天已有同签记录则复用，不重复落库）→ 关联服务端卦例 id → 跳卦盘。
   * 对齐 DivHomeViewModel.ensureTodaySignSaved + DivRecordRepository.todaySign。
   * #80：服务端 /today 已幂等建档，本地建档后把 serverTodayId 回写关联，
   * 使求解读走 SSE 时不再重复 POST /records。
   */
  private async ensureTodaySignSaved(): Promise<void> {
    const solar = Solar.fromDate(new Date())
    const chart = MeiHuaChart.timeChart(solar)
    const question = resString('div_home_page_1')

    const existing = await DivRecordRepository.findTodaySign('meihua', question)
    let id: number
    if (existing !== null) {
      id = existing.id
      // 幂等命中：同日同签复用既有记录，不重复落库
      logger.debug('divHomePage', `今日一签幂等命中，复用当日记录 recordId=${existing.id}`)
    } else {
      const chartJson = encodeChartJson(chart)
      const bundleJson = encodeBundleToJson(encodeBundle('meihua', chartJson))
      id = await DivRecordRepository.save(
        'meihua', 'other', question, JSON.stringify(chart.lines), Date.now(), bundleJson)
      logger.info('divHomePage', `今日一签落库成功 recordId=${id}`)
    }
    // 服务端今日签命中且本地行未关联 → 回写（幂等：已关联不覆盖）
    if (this.serverTodayId > 0) {
      await DivRecordRepository.updateServerRecordId(id, this.serverTodayId)
      logger.debug('divHomePage', `今日一签关联服务端卦例 localId=${id} serverRecordId=${this.serverTodayId}`)
    }
    // 跳卦盘（DivChart 路由随卦盘批次注册；复盘入口不播动爻进场动画）
    const chartParam: DivChartParam = { recordId: id, animate: false }
    pathStack.pushPathByName(RouteNames.DIV_CHART, chartParam)
  }

  /**
   * 今日一签点击入口（compare-10 #1 对齐安卓 DivHomeViewModel.ensureTodaySignSaved 的 signSaving 保护）：
   * 同步置位 signSaving 兜住连点（DivCard clickable 同步禁点为第一道），链路结束（含异常）finally 复位；
   * 保存期间尾缀换「…」（DivText.signSaving）作轻量 loading 反馈，业务链路不变。
   */
  private async saveTodaySign(): Promise<void> {
    if (this.signSaving) {
      return
    }
    this.signSaving = true
    try {
      await this.ensureTodaySignSaved()
    } finally {
      this.signSaving = false
    }
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

  /** 六爻为首选术（有推荐 tag） */
  private isFeatured(tag: string): boolean {
    return tag.length > 0
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
      // 非仪式文本：普通 Text 无衬线无字距（compare-10 #21）；minWidth 44 让 130% 字体缩放自然加宽不截断（#23）
      Text(this.formatMonthDay(record.castAt))
        .fontSize(12)
        .fontColor(DivPalette.textTertiary)
        .constraintSize({ minWidth: 44 })

      Column().width(4)

      // 术数色点 8dp 档（规范 §3.5，compare-10 #3：7dp 墨 → 8dp 金，对齐安卓 audit-11 #17）
      Column()
        .width(8)
        .height(8)
        .borderRadius(4)
        .backgroundColor(DivPalette.gold)

      Column().width(12)

      // 摘要：caption 档 12/16，普通 Text 无衬线（compare-10 #21/#22）
      Text(record.question)
        .fontSize(12)
        .lineHeight(16)
        .fontColor(DivPalette.textPrimary)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .layoutWeight(1)

      Column().width(8)

      DivSerifText({
        text: this.methodName(record.method),
        textSize: 12,
        weight: 500,
        spacing: 0,
        color: DivPalette.goldText
      })
        .opacity(0.75)
    }
    .width('100%')
    .padding({ top: 12, bottom: 12 })
    .alignItems(VerticalAlign.Center)
  }

  /**
   * 四术玉牌：竖排术名 + 一句话定位 + 适用场景 + 推荐标（点击防抖 + pressScale + 触感）。
   * 对齐安卓 JadeEntryTile：desc 下补 useFor 第三行（compare-10 #4 修正「安卓不消费」误判），
   * tag 位固定 16 高随内容流。
   * 自绘卡（非 DivCard）：按规范 §2.4/§3.1 补 blur 12 轻阴影（compare-10 #12），玄墨侧同 DivCard 豁免。
   */
  @Builder
  JadeTile(method: string, name: string, desc: string, useFor: string, tag: string) {
    // 六爻为首选术：鎏金渐变描边 + 推荐 tag + glyph 40；其余 12% 发丝描边 + glyph 34
    Column() {
      Column() {
        Row() {
          MethodGlyph({ method: method, sizeValue: this.isFeatured(tag) ? 40 : 34, tint: DivPalette.gold })
          Blank()
          // 竖排术名（画板：18/600、行距 26、右上内缩 2；compare-10 #6 行距 26 由行盒承担，去逐字 margin）
          Column() {
            ForEach(name.split(''), (ch: string, index: number) => {
              DivSerifText({
                text: ch,
                textSize: 18,
                weight: 600,
                spacing: 0,
                color: DivPalette.textPrimary,
                lineHeight: 26
              })
            }, (ch: string, index: number) => index.toString())
          }
          .alignItems(HorizontalAlign.End)
        }
        .width('100%')
        .alignItems(VerticalAlign.Top)

        Column().height(8)

        // 一句话定位（compare-10 #24 去 maxLines：130% 字体/窄屏折行随内容撑开，不截断省略）
        DivSerifText({
          text: desc,
          textSize: 12,
          weight: 400,
          spacing: 0,
          color: DivPalette.textSecondary
        })
        Column().height(4)
        // 适用场景第三行（compare-10 #4：12/16 次要灰，desc 之下、tag 之上，对齐安卓 DivHomePage.kt:393-399）
        DivSerifText({
          text: useFor,
          textSize: 12,
          weight: 400,
          spacing: 0,
          color: DivPalette.textSecondary,
          lineHeight: 16
        })
        Column().height(6)
        // tag 位固定高度：四牌同构（无 tag 留空，同一行两牌行高一致）
        Column() {
          if (tag.length > 0) {
            // captionStrong 档 12/16/500（compare-10 #5：11sp「角标」档已作废，行内小字一律 12sp）
            DivSerifText({
              text: tag,
              textSize: 12,
              weight: 500,
              spacing: 2,
              color: DivPalette.goldText,
              lineHeight: 16
            })
              .opacity(0.75)
          }
        }
        .height(16)
      }
      .width('100%')
      .borderRadius(15)
      .backgroundColor(DivPalette.surface)
      // compare-10 #10：featured 内层四周 margin 1 露出外层渐变描边线（外 radius 16 / 内 radius 15 不变）
      .margin(this.isFeatured(tag)
        ? { left: 1, right: 1, top: 1, bottom: 1 }
        : { left: 0, right: 0, top: 0, bottom: 0 })
      .padding({ left: 16, right: 16, top: 14, bottom: 14 })
    }
    .width('100%')
    // 牌高由内容撑开（min 148 兜底），窄屏/大字号折行不裁切、不叠压
    .constraintSize({ minHeight: 148 })
    .borderRadius(16)
    // compare-10 #10 描边画法修复：featured 外层渐变当底、内层缩进 1 露线（原内层贴边直边段不可见）；
    // 次牌去渐变、直改 12% 发丝描边（对齐安卓 border(1.dp, c.hair) 全周连续）
    .border(this.isFeatured(tag)
      ? { width: 0, color: Color.Transparent }
      : { width: 1, color: DivPalette.hair })
    .linearGradient(this.isFeatured(tag)
      ? { angle: 135, colors: [[DivPalette.goldLine, 0.0], [DivPalette.goldHair, 1.0]] }
      : { angle: 135, colors: [[Color.Transparent, 0.0], [Color.Transparent, 1.0]] })
    // 规范 §2.4/§3.1 卡片唯一画法：1px 描边 + blur 12 轻阴影（compare-10 #12 对齐安卓 softCardShadow）
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: $r('app.color.SHADOW_COLOR_CARD'),
      offsetX: 0,
      offsetY: 2
    })
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
      DivHaptic.light()
      // 四牌并排且 navigate 无 launchSingleTop，连点会双推 Ask 页 → 节流防抖
      this.debouncer.throttle(() => {
        pathStack.pushPathByName(RouteNames.DIV_ASK, method)
      }, 600)
    })
  }

  build() {
    NavDestination() {
      // 观象台首页为全屏画布页：整页扩展到系统安全区外（页头不再额外避让，避免双重避让）
      DivinationScreen({ immersive: true }) {
        // 1. 干支天象头（compare-10 #19：状态栏下留 12，对齐安卓 statusBarsPadding + padding(top = 12.dp)）
        TianXiangHeader({ text: this.tianXiang.length > 0 ? this.tianXiang : '—' })
          .margin({ top: 12 })

        // 2. 衬线标题 + 旬空副行（左对齐：安卓 Column 默认 TopStart；ArkUI Column 默认 Center 会居中）
        Column() {
          DivSerifText({
            text: DivText.title,
            textSize: 22,
            weight: 600,
            spacing: 4,
            color: DivPalette.textPrimary
          })
            .margin({ top: 6 })

          DivSerifText({
            text: `旬空 ${this.xunKong.length > 0 ? this.xunKong : '—'}`,
            textSize: 12,
            weight: 400,
            spacing: 1,
            color: DivPalette.textSecondary
          })
            .margin({ top: 6 })
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })

        // 3. 罗盘（画板 104dp）
        Column() {
          CompassDial({ dialText: resString('div_home_page_2'), dialSize: 104 })
        }
        .width('100%')
        .padding({ top: 8, bottom: 12 })
        .alignItems(HorizontalAlign.Center)

        // 4. 四术玉牌 2×2（网格间距 12：画板 14，令牌网格收敛 12，对齐安卓 DivHomePage.kt:143）
        Column({ space: 12 }) {
          ForEach(this.buildRows(), (row: Array<[string, string, string, string, string]>, rowIndex: number) => {
            Row({ space: 12 }) {
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
        .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })

        // 5. 今日一签卡（无签数据时不显示；保存中禁点 + 尾缀「…」，compare-10 #1）
        if (this.hasTodaySign) {
          Column() {
          DivCard({
            clickable: !this.signSaving,
            lineColor: DivPalette.hair,
            onAction: () => {
              this.saveTodaySign()
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
                // 非仪式文本：普通 Text 无衬线无字距（compare-10 #21），caption 12/16（#22）
                Text(`${this.todaySignSub} —— ${DivText.signCardSource}`)
                  .fontSize(12)
                  .lineHeight(16)
                  .fontColor(DivPalette.textSecondary)
                  .margin({ top: 4 })
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)

              DivSerifText({
                text: this.signSaving ? DivText.signSaving : '›',
                textSize: 16,
                weight: 400,
                spacing: 0,
                color: DivPalette.goldText
              })
                .opacity(0.6)
            }
            .width('100%')
            .padding({ left: 18, right: 10, top: 10, bottom: 22 })
            .alignItems(VerticalAlign.Center)
          }
          }
          .width('100%')
          // 卡外边距：横向观象台域统一 20（compare-10 #16），纵向 SPACE_SM（12）
          .padding({
            left: DIV_PAGE_PADDING,
            right: DIV_PAGE_PADDING,
            top: DesignDimens.SPACE_SM,
            bottom: DesignDimens.SPACE_SM
          })
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

        Column() {
          DivCard({ lineColor: DivPalette.hair }) {
            // 空态与记录列表交叉淡入淡出（对齐安卓 Crossfade，避免 0ms 硬切）
            Stack() {
              // 空态层
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
                    .padding({ top: 18 })

                  // 空态行动按钮（compare-10 #2 对齐安卓 audit-11 #5）：48 热区文字按钮跳问卦
                  // （默认六爻 = 推荐首选术），与「卦历 ›」尾链双出口
                  Column() {
                    DivSerifText({
                      text: DivText.ledgerGoAsk,
                      textSize: 12,
                      weight: 600,
                      spacing: 2,
                      color: DivPalette.goldText
                    })
                  }
                  .width('100%')
                  .constraintSize({ minHeight: 48 })
                  .margin({ top: 4 })
                  .alignItems(HorizontalAlign.Start)
                  .justifyContent(FlexAlign.Center)
                  .onClick(() => {
                    DivHaptic.light()
                    this.debouncer.throttle(() => {
                      pathStack.pushPathByName(RouteNames.DIV_ASK, 'liuyao')
                    }, 600)
                  })

                  Column().height(18)
                }
              }
              .width('100%')
              .opacity(this.records.length === 0 ? 1 : 0)
              .animation({ duration: 180, curve: Curve.EaseInOut })

              // 记录列表层
              Column() {
                ForEach(this.records, (record: DivRecordRow, index: number) => {
                  Column() {
                    this.LedgerRow(record)
                  }
                  .width('100%')
                  if (index !== 2 && index !== this.records.length - 1) {
                    Divider()
                      .strokeWidth(0.5)
                      .color(DivPalette.hair)
                  }
                }, (record: DivRecordRow) => record.id.toString())
              }
              .width('100%')
              .opacity(this.records.length === 0 ? 0 : 1)
              .animation({ duration: 180, curve: Curve.EaseInOut })
            }
            .width('100%')
            // 案卷卡内层：左右 14、纵向 0（行自带 vertical 12；compare-10 #18 对齐安卓 padding(horizontal = 14.dp)）
            .padding({ left: 14, right: 14 })
          }
        }
        .width('100%')
        .padding({ left: DIV_PAGE_PADDING, right: DIV_PAGE_PADDING })

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
