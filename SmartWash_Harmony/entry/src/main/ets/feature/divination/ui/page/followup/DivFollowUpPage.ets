/**
 * 追问页 —— 对齐安卓 ui/page/followup/DivFollowUpPage.kt + DivFollowUpViewModel.kt。
 *
 * 结构：盘面摘要条（紧凑单行）→ 聊天流（user 金底右对齐 / assistant 素底左对齐）
 * → 新消息自动滚底 + Loading 尾部「推演中…」→ 底部输入栏（胶囊输入 + 发送金按钮）。
 *
 * 关键约束：
 * - 记录读库（getById），沿用原盘原 castAt，不重算。
 * - followUp(form) 走 Mock；Loading 中防重入；发送后清输入框。
 * - 记录无则显示 div_chart_missing。
 */

import { DivMessage, followUp as requestFollowUp } from '../../../network/DivinationApi'
import { chartNameOf, divMethodName } from '../../../data/DivChartReader'
import { decodeBundle, decodeBundleFromJson, DivRecordRepository, DivRecordRow } from '../../../data/DivinationStore'
import {
  DivGoldButton,
  DivPageHeader,
  DivPalette,
  DivSeal,
  DivSerifText,
  DivText
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignElevation } from '../../../../../constant/DesignSystem'
import { getNumberParam, pathStack } from '../../../../../utils/PathStackUtil'

@Builder
export function DivFollowUpBuilder() {
  DivFollowUpPage()
}

@ComponentV2
struct DivFollowUpPage {
  @Local loadingRecord: boolean = true
  @Local record: DivRecordRow | null = null
  @Local chartName: string = ''
  @Local chartSeal: string = ''
  @Local timeText: string = ''
  @Local history: DivMessage[] = []
  @Local historyLength: number = 0
  @Local sending: boolean = false
  @Local input: string = ''
  private scroller: Scroller = new Scroller()

  async aboutToAppear(): Promise<void> {
    const recordId = getNumberParam(RouteNames.DIV_FOLLOW_UP)
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
  }

  /** 新消息自动滚底（对齐安卓 animateScrollToItem） */
  @Monitor('historyLength')
  onHistoryChange(): void {
    if (this.historyLength > 0) {
      this.scroller.scrollToIndex(this.historyLength - 1)
    }
  }

  private resolveName(row: DivRecordRow): string {
    const chartJson = decodeBundle(decodeBundleFromJson(row.chartJson))
    return chartNameOf(row.method, chartJson)
  }

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
    return `${this.pad2(month)}-${this.pad2(day)} ${this.pad2(hour)}:${this.pad2(minute)}`
  }

  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }

  /** 发送追问：追加 user 轮 → followUp → 追加 assistant 轮；Loading 中防重入 */
  private send(): void {
    const q = this.input.trim()
    if (q.length === 0 || this.sending) {
      return
    }
    const record = this.record
    if (record === null) {
      return
    }
    const userTurn: DivMessage = { role: 'user', content: q }
    const newHistory = this.history.concat(userTurn)
    this.history = newHistory
    this.historyLength = newHistory.length
    // 发送后清输入框
    this.input = ''
    this.sending = true
    requestFollowUp({
      recordId: record.id,
      question: q,
      history: newHistory
    }).then((resp): void => {
      if (resp.code === 200 && resp.data !== null) {
        const reply: DivMessage = { role: 'assistant', content: resp.data.reply }
        this.history = this.history.concat(reply)
        this.historyLength = this.history.length
      }
      this.sending = false
    }).catch((err: Object): void => {
      this.sending = false
    })
  }

  @Builder
  SummaryBar() {
    Row() {
      DivSeal({ text: this.chartSeal, sealSize: 32, fontSize: 12 })
      Column().width(10)
      DivSerifText({
        text: this.chartName.length > 0 ? this.chartName : divMethodName(this.record?.method ?? ''),
        textSize: 11.5,
        weight: 500,
        spacing: 2,
        color: DivPalette.textPrimary,
        maxLines: 1,
        overflow: TextOverflow.Ellipsis
      })
        .layoutWeight(1)
      DivSerifText({
        text: this.timeText,
        textSize: 9.5,
        weight: 400,
        spacing: 0,
        color: DivPalette.textTertiary
      })
    }
    .width('100%')
    .padding({ left: 20, right: 20, top: 6, bottom: 6 })
    .borderRadius(12)
    .backgroundColor(DivPalette.surface)
    .alignItems(VerticalAlign.Center)
  }

  /**
   * 聊天气泡：回复气泡即卡面（宣纸 surface + 1px 金线 + level1 轻阴影，规范 §2.4/§3.1）；
   * 用户气泡为浅金块（非实色卡），不加投影，但保留 1px 金线以界定形状。
   * 规范 §7：本组件无可点击行为，无 Material 涟漪（对齐安卓 indication = null）。
   */
  @Builder
  ChatBubble(turn: DivMessage) {
    if (turn.role === 'user') {
      Row() {
        Column() {
          DivSerifText({
            text: turn.content,
            textSize: 12.5,
            weight: 400,
            spacing: 0,
            color: DivPalette.textPrimary
          })
        }
        .constraintSize({ maxWidth: 260 })
        .padding({ left: 13, right: 13, top: 9, bottom: 9 })
        .borderRadius({ topLeft: 14, topRight: 14, bottomLeft: 4, bottomRight: 14 })
        .backgroundColor(DivPalette.goldSoft)
        // 描边宽度归一到 1px（0.5dp 按 §1.3 只保留给卡内行分隔线）；浅金底非实色块，不加投影
        .border({ width: 1, color: DivPalette.goldHair })
      }
      .width('100%')
      .justifyContent(FlexAlign.End)
    } else {
      Row() {
        Column() {
          DivSerifText({
            text: turn.content,
            textSize: 12.5,
            weight: 400,
            spacing: 0,
            color: DivPalette.textPrimary
          })
        }
        .constraintSize({ maxWidth: 280 })
        .padding({ left: 13, right: 13, top: 11, bottom: 11 })
        .borderRadius({ topLeft: 14, topRight: 14, bottomLeft: 14, bottomRight: 4 })
        .backgroundColor(DivPalette.surface)
        // 回复气泡 = 卡面：1px 描边 + level1 轻阴影（宣纸侧；安卓 ChatBubble 同此）
        .shadow({
          radius: DesignElevation.LEVEL_1,
          color: 'rgba(20,40,30,0.05)',
          offsetX: 0,
          offsetY: 2
        })
        .border({ width: 1, color: DivPalette.goldHair })
      }
      .width('100%')
      .justifyContent(FlexAlign.Start)
    }
  }

  @Builder
  InputBar() {
    Row() {
      TextInput({ text: this.input, placeholder: DivText.followupInputHint })
        .layoutWeight(1)
        .height(40)
        .backgroundColor(DivPalette.surface2)
        .borderRadius(20)
        .padding({ left: 14, right: 14 })
        .fontSize(13)
        .fontColor(DivPalette.textPrimary)
        .caretColor(DivPalette.gold)
        .onChange((value: string) => {
          this.input = value
        })

      Column().width(10)

      Column() {
        DivGoldButton({
          text: DivText.followupSend,
          isEnabled: !this.sending && this.input.trim().length > 0,
          btnHeight: 38,
          fontSize: 13,
          onAction: () => {
            this.send()
          }
        })
      }
      .width(72)
    }
    .width('100%')
    .padding({ left: 20, right: 20, top: 10, bottom: 10 })
    .alignItems(VerticalAlign.Center)
  }

  build() {
    NavDestination() {
      DivinationScreen({ scrollable: false }) {
        Column() {
          DivPageHeader({
            title: DivText.followupTitle,
            tail: DivText.followupSubtitle,
            onBack: () => {
              pathStack.pop()
            }
          })

          if (this.loadingRecord) {
            DivSerifText({
              text: DivText.chartLoading,
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
            // 盘面摘要条
            this.SummaryBar()

            // 聊天流
            List({ scroller: this.scroller }) {
              if (this.history.length === 0) {
                ListItem() {
                  DivSerifText({
                    text: DivText.followupEmpty,
                    textSize: 12,
                    weight: 400,
                    spacing: 0,
                    color: DivPalette.textTertiary,
                    textAlign: TextAlign.Center
                  })
                    .width('100%')
                    .padding({ top: 40 })
                }
              }

              ForEach(this.history, (turn: DivMessage, index: number) => {
                ListItem() {
                  this.ChatBubble(turn)
                }
              }, (turn: DivMessage, index: number) => `${index}-${turn.role}-${turn.content}`)

              if (this.sending) {
                ListItem() {
                  Row() {
                    DivSerifText({
                      text: DivText.followupThinking,
                      textSize: 11,
                      weight: 400,
                      spacing: 0,
                      color: DivPalette.textTertiary
                    })
                  }
                  .width('100%')
                }
              }
            }
            .layoutWeight(1)
            .width('100%')
            .padding({ left: 20, right: 20, top: 8, bottom: 8 })
            .scrollBar(BarState.Off)

            // 底部输入栏
            this.InputBar()
          }
        }
        .width('100%')
        .height('100%')
      }
    }
    .hideTitleBar(true)
  }
}
