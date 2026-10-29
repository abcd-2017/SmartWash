/**
 * 解读页 —— 对齐安卓 ui/page/reading/DivReadingPage.kt + DivReadingViewModel.kt。
 *
 * 结构：盘面摘要卡（印章 + 卦名 + 起卦时刻）→ 解读正文三态（Loading / Error / Success）
 * → 金边「总结与行动」卡 → 落款印 → 底部「继续追问」/「分享」→ 页脚免责声明常驻。
 *
 * 关键约束：
 * - 记录读库（getById），解读走 interpret(form)（本期 Mock；后端就绪后仅换实现，页面不变）。
 * - 沿用原盘原 castAt，不重算。
 * - 页脚免责声明 div_disclaimer_reading 常驻（不因 Error 消失）。
 */

import { DivReadingSection, DivReadingVo, interpret as requestInterpret } from '../../../network/DivinationApi'
import { chartNameOf, divMethodName } from '../../../data/DivChartReader'
import { decodeBundle, decodeBundleFromJson, DivRecordRepository, DivRecordRow } from '../../../data/DivinationStore'
import {
  DivCard,
  DivFooterNote,
  DivGoldButton,
  DivPageHeader,
  DivPalette,
  DivSeal,
  DivSerifText,
  DivText
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { getNumberParam, pathStack } from '../../../../../utils/PathStackUtil'

/** 解读正文状态（对齐安卓 RequestState） */
type ReadingState = 'idle' | 'loading' | 'success' | 'error'

@Builder
export function DivReadingBuilder() {
  DivReadingPage()
}

@ComponentV2
struct DivReadingPage {
  @Local loadingRecord: boolean = true
  @Local record: DivRecordRow | null = null
  @Local chartName: string = ''
  @Local chartSeal: string = ''
  @Local timeText: string = ''
  @Local readingState: ReadingState = 'idle'
  @Local reading: DivReadingVo | null = null

  async aboutToAppear(): Promise<void> {
    const recordId = getNumberParam(RouteNames.DIV_READING)
    if (recordId === null) {
      this.loadingRecord = false
      return
    }
    try {
      const row = await DivRecordRepository.getById(recordId)
      this.record = row
      if (row !== null) {
        this.chartName = this.resolveName(row)
        this.chartSeal = this.resolveSeal(row.method, this.chartName)
        this.timeText = this.formatTime(row.castAt)
      }
    } catch (err) {
      this.record = null
    }
    this.loadingRecord = false
    if (this.record !== null) {
      this.runInterpret(this.record)
    }
  }

  /** 盘面卦名（读库解码；失败回退术数名） */
  private resolveName(row: DivRecordRow): string {
    const chartJson = decodeBundle(decodeBundleFromJson(row.chartJson))
    return chartNameOf(row.method, chartJson)
  }

  /** 摘要印章（对齐安卓 ReadingSummaryCard：六爻取首字 / 梅 / 奇 / 壬） */
  private resolveSeal(method: string, name: string): string {
    if (method === 'liuyao') {
      return name.length > 0 ? name.charAt(0) : '卦'
    }
    if (method === 'meihua') {
      return '梅'
    }
    if (method === 'qimen') {
      return '奇'
    }
    return '壬'
  }

  private formatTime(castAt: number): string {
    const d = new Date(castAt)
    const month = d.getMonth() + 1
    const day = d.getDate()
    const hour = d.getHours()
    const minute = d.getMinutes()
    return `${this.pad2(month)}-${this.pad2(day)} ${this.pad2(hour)}:${this.pad2(minute)} · 卜`
  }

  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }

  private hasReading(): boolean {
    return this.reading !== null
  }

  private readingSections(): DivReadingSection[] {
    return this.reading?.sections ?? []
  }

  private readingAction(): string {
    return this.reading?.action ?? ''
  }

  /** 解读（runCatching 包裹；Error 携带文案） */
  private runInterpret(record: DivRecordRow): void {
    this.readingState = 'loading'
    this.reading = null
    requestInterpret({
      recordId: record.id,
      method: record.method,
      category: record.category,
      question: record.question,
      castAt: record.castAt,
      chartJson: record.chartJson,
      stream: false
    }).then((resp): void => {
      if (resp.code === 200 && resp.data !== null) {
        this.reading = resp.data
        this.readingState = 'success'
      } else {
        this.readingState = 'error'
      }
    }).catch((err: Object): void => {
      this.readingState = 'error'
    })
  }

  @Builder
  SummaryCard() {
    Row() {
      DivSeal({ text: this.chartSeal, sealSize: 32, fontSize: 15 })
      Column().width(12)
      DivSerifText({
        text: this.chartName.length > 0 ? this.chartName : divMethodName(this.record?.method ?? ''),
        textSize: 15,
        weight: 600,
        spacing: 3,
        color: DivPalette.textPrimary
      })
        .layoutWeight(1)
      DivSerifText({
        text: this.timeText,
        textSize: 10,
        weight: 400,
        spacing: 0,
        color: DivPalette.textTertiary
      })
    }
    .width('100%')
    .padding({ left: 14, right: 14, top: 12, bottom: 12 })
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  SectionCard(section: DivReadingSection) {
    DivCard({ lineColor: DivPalette.goldHair }) {
      Column() {
        DivSerifText({
          text: section.title,
          textSize: 13.5,
          weight: 600,
          spacing: 3,
          color: DivPalette.gold
        })
        Column().height(6)
        DivSerifText({
          text: section.content,
          textSize: 12.5,
          weight: 400,
          spacing: 0,
          color: DivPalette.textPrimary
        })
      }
      .width('100%')
      .padding({ left: 14, right: 14, top: 12, bottom: 12 })
    .margin({ top: 6, bottom: 6 })
    }
  }

  @Builder
  ActionCard(action: string) {
    DivCard({ bgColor: DivPalette.goldSoft, lineColor: DivPalette.goldLine }) {
      Column() {
        DivSerifText({
          text: DivText.readingAction,
          textSize: 13,
          weight: 600,
          spacing: 3,
          color: DivPalette.gold
        })
        Column().height(6)
        DivSerifText({
          text: action,
          textSize: 12.5,
          weight: 400,
          spacing: 0,
          color: DivPalette.textPrimary
        })
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 14, bottom: 14 })
    .margin({ left: 20, right: 20, top: 8, bottom: 8 })
    }
  }

  @Builder
  EndSeal() {
    Column() {
      DivSeal({ text: DivText.readingEndseal, sealSize: 36, fontSize: 18 })
    }
    .width('100%')
    .padding({ top: 8 })
    .alignItems(HorizontalAlign.Center)
  }

  build() {
    NavDestination() {
      DivinationScreen() {
        DivPageHeader({
          title: DivText.readingTitle,
          onBack: () => {
            pathStack.pop()
          }
        })

        if (this.loadingRecord) {
          DivSerifText({
            text: DivText.readingLoading,
            textSize: 12,
            weight: 400,
            spacing: 0,
            color: DivPalette.textTertiary,
            textAlign: TextAlign.Center
          })
            .width('100%')
            .padding({ top: 120 })
        } else if (this.record === null) {
          DivSerifText({
            text: DivText.chartMissing,
            textSize: 13,
            weight: 400,
            spacing: 0,
            color: DivPalette.textTertiary,
            textAlign: TextAlign.Center
          })
            .width('100%')
            .padding({ top: 80 })
        } else {
          // 盘面摘要卡
          DivCard({ lineColor: DivPalette.goldLine }) {
            Column() {
              this.SummaryCard()
            }
            .margin({ left: 20, right: 20 })
          }

          // 解读正文三态
          if (this.readingState === 'loading' || this.readingState === 'idle') {
            DivCard() {
              DivSerifText({
                text: DivText.readingLoading,
                textSize: 12,
                weight: 400,
                spacing: 0,
                color: DivPalette.textTertiary,
                textAlign: TextAlign.Center
              })
                .width('100%')
                .padding({ top: 24, bottom: 24 })
            .margin({ left: 20, right: 20 })
            }
          } else if (this.readingState === 'error') {
            DivCard() {
              DivSerifText({
                text: DivText.readingError,
                textSize: 12,
                weight: 400,
                spacing: 0,
                color: DivPalette.textTertiary,
                textAlign: TextAlign.Center
              })
                .width('100%')
                .padding({ top: 24, bottom: 24 })
            .margin({ left: 20, right: 20 })
            }
          } else if (this.hasReading()) {
            // 章节卡列表
            Column() {
              ForEach(this.readingSections(), (section: DivReadingSection) => {
                this.SectionCard(section)
              }, (section: DivReadingSection, index: number) => index.toString())
            }
            .width('100%')

            // 金边「总结与行动」卡
            this.ActionCard(this.readingAction())

            // 落款印
            this.EndSeal()
          }

          // 底部双按钮
          Row() {
            Column() {
              DivGoldButton({
                text: DivText.readingFollowup,
                isEnabled: true,
                onAction: () => {
                  const id = this.record?.id ?? 0
                  pathStack.pushPathByName(RouteNames.DIV_FOLLOW_UP, id)
                }
              })
            }
            .layoutWeight(1)
            Column().width(10)
            Column() {
              DivGoldButton({
                text: DivText.readingShare,
                isEnabled: false,
                onAction: () => {
                }
              })
            }
            .layoutWeight(1)
          }
          .width('100%')
          .padding({ left: 20, right: 20 })

          // 页脚免责声明（常驻）
          DivFooterNote({ text: DivText.disclaimerReading })
        }
      }
    }
    .hideTitleBar(true)
  }
}
