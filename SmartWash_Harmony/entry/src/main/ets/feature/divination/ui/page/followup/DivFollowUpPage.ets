/**
 * 追问页 —— 对齐安卓 ui/page/followup/DivFollowUpPage.kt + DivFollowUpViewModel.kt。
 *
 * 结构：页头（居中标题 + 副题）→ 聊天流（user 金底右对齐 / assistant 素底左对齐）
 * → 新消息自动滚底 + Loading 尾部「推演中…」→ 底部输入栏（胶囊输入 + 发送金按钮）。
 *
 * 关键约束：
 * - 记录读库（getById），沿用原盘原 castAt，不重算。
 * - followUp(form) 走 Mock；Loading 中防重入；发送后清输入框。
 * - 记录无则显示 div_chart_missing。
 */

import { DivMessage, followUp as requestFollowUp } from '../../../network/DivinationApi'
import { DivRecordRepository, DivRecordRow } from '../../../data/DivinationStore'
import {
  DivPageHeader,
  DivPalette,
  DivSerifText,
  DivText,
  DivWaveIcon,
  divReduceMotion
} from '../../components/DivComponents'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignElevation, DesignColor } from '../../../../../constant/DesignSystem'
import { getNumberParam, pathStack } from '../../../../../utils/PathStackUtil'
import { logger } from '../../../../../utils/Logger'

@Builder
export function DivFollowUpBuilder() {
  DivFollowUpPage()
}

/** 聊天气泡视图（用户实金底右对齐 / 回复卡面左对齐；新气泡首帧 120ms 淡入） */
@ComponentV2
struct ChatBubbleView {
  @Param turn: DivMessage = { role: 'user', content: '' }
  @Local alpha: number = 0

  aboutToAppear(): void {
    if (divReduceMotion()) {
      this.alpha = 1
    } else {
      animateTo({ duration: 120 }, () => {
        this.alpha = 1
      })
    }
  }

  build() {
    if (this.turn.role === 'user') {
      Row() {
        Column() {
          DivSerifText({
            text: this.turn.content,
            textSize: 12,
            weight: 400,
            spacing: 0,
            color: DivPalette.onGold
          })
        }
        .constraintSize({ maxWidth: 220 })
        .padding({ left: 16, right: 16, top: 12, bottom: 12 })
        .borderRadius(16)
        // 实金底（goldGradient 首色）+ 墨字 + r16 无描边；实色块免投影
        .backgroundColor(DivPalette.goldGradientStart)
      }
      .width('100%')
      .justifyContent(FlexAlign.End)
      .opacity(this.alpha)
    } else {
      Row() {
        Column() {
          DivSerifText({
            text: this.turn.content,
            textSize: 12,
            weight: 400,
            spacing: 0,
            color: DivPalette.textPrimary
          })
        }
        .constraintSize({ maxWidth: 240 })
        .padding({ left: 16, right: 16, top: 12, bottom: 12 })
        .borderRadius(16)
        .backgroundColor(DivPalette.surface)
        // 回复气泡 = 卡面：1px 发丝描边 + level1 轻阴影（宣纸侧）
        .shadow({
          radius: DesignElevation.LEVEL_1,
          color: DesignColor.SHADOW_COLOR_CARD,
          offsetX: 0,
          offsetY: 2
        })
        .border({ width: 1, color: DivPalette.hair })
      }
      .width('100%')
      .justifyContent(FlexAlign.Start)
      .opacity(this.alpha)
    }
  }
}

@ComponentV2
struct DivFollowUpPage {
  @Local loadingRecord: boolean = true
  @Local record: DivRecordRow | null = null
  @Local history: DivMessage[] = []
  @Local historyLength: number = 0
  @Local sending: boolean = false
  @Local input: string = ''
  private scroller: Scroller = new Scroller()

  async aboutToAppear(): Promise<void> {
    const recordId = getNumberParam(RouteNames.DIV_FOLLOW_UP)
    if (recordId === null) {
      logger.warn('divFollowUpPage', '追问页路由参数缺失 recordId，按空态呈现')
      this.loadingRecord = false
      return
    }
    try {
      const row = await DivRecordRepository.getById(recordId)
      this.record = row
      if (row !== null) {
        logger.debug('divFollowUpPage', `追问页记录加载完成 recordId=${recordId}`)
      } else {
        logger.warn('divFollowUpPage', `追问页卦象记录不存在 recordId=${recordId}，按空态呈现`)
      }
    } catch (err) {
      this.record = null
      logger.error('divFollowUpPage', '追问页读取卦象记录失败',
        err instanceof Error ? err.message : '')
    }
    this.loadingRecord = false
  }

  /** 新消息自动滚底（对齐安卓 animateScrollToItem：smooth 动画） */
  @Monitor('historyLength')
  onHistoryChange(): void {
    if (this.historyLength > 0) {
      this.scroller.scrollToIndex(this.historyLength - 1, true)
    }
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
        // 低频关键动作：追问提交成功（追问内容只记长度，不打全文）
        logger.info('divFollowUpPage', `追问提交成功 recordId=${record.id} turn=${newHistory.length}`)
      } else {
        logger.warn('divFollowUpPage', `追问响应异常 recordId=${record.id} code=${resp.code}`)
      }
      this.sending = false
    }).catch((err: Object): void => {
      this.sending = false
      logger.error('divFollowUpPage', `追问提交失败 recordId=${record.id}`,
        err instanceof Error ? err.message : '')
    })
  }

  /**
   * 聊天气泡：用户实金底（goldGradient 首色）+ 墨字 + r16 无描边 + 宽上限 220 + padding 16/12；
   * 回复卡面（surface + 1px 发丝描边 + r16 + 宽上限 240 + level1 轻阴影）；文字 12/16，新气泡首帧 120ms 淡入。
   * 规范 §7：本组件无可点击行为，无 Material 涟漪（对齐安卓 indication = null）。
   */
  @Builder
  ChatBubble(turn: DivMessage) {
    ChatBubbleView({ turn: turn })
  }

  /** 是否可发送（Loading 中 / 空输入禁用） */
  private canSend(): boolean {
    return !this.sending && this.input.trim().length > 0
  }

  // 底部输入条（画板）：surface 底 + 1px 发丝顶线；输入框 48 高 r12 金线描边；48 圆形鎏金发送钮
  @Builder
  InputBar() {
    Column() {
      // 顶线（1px 12% 金发丝）
      Column()
        .width('100%')
        .height(1)
        .backgroundColor(DivPalette.hair)

      Row() {
        TextInput({ text: this.input, placeholder: DivText.followupInputHint })
          .layoutWeight(1)
          .height(48)
          .backgroundColor(DivPalette.surface2)
          .borderRadius(12)
          .border({ width: 1, color: DivPalette.line })
          .padding({ left: 16, right: 16 })
          .fontSize(14)
          .fontColor(DivPalette.textPrimary)
          .caretColor(DivPalette.gold)
          .onChange((value: string) => {
            this.input = value
          })

        Column().width(10)

        // 48 圆形鎏金发送钮（禁用退回 14% 金底）
        Column() {
          SymbolGlyph($r('sys.symbol.paperplane_fill'))
            .fontSize(20)
            .fontColor([this.canSend() ? DivPalette.onGold : DivPalette.goldText])
        }
        .width(48)
        .height(48)
        .borderRadius(24)
        .backgroundColor(this.canSend() ? DivPalette.gold : DivPalette.gold14)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .onClick(() => {
          if (this.canSend()) {
            this.send()
          }
        })
      }
      .width('100%')
      .padding({ left: 20, right: 20, top: 16, bottom: 16 })
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .backgroundColor(DivPalette.surface)
  }

  build() {
    NavDestination() {
      DivinationScreen({ scrollable: false }) {
        Column() {
          DivPageHeader({
            title: DivText.followupTitle,
            subtitle: DivText.followupSubtitle,
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
              textSize: 12,
              weight: 400,
              spacing: 0,
              color: DivPalette.textTertiary,
              textAlign: TextAlign.Center
            })
              .width('100%')
              .padding({ top: 80 })
          } else {
            // 聊天流（画板：项距 16）
            List({ scroller: this.scroller, space: 16 }) {
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
                    // 推演中的水波指示（金色 16）+ 文字 12
                    DivWaveIcon({ sizeValue: 16, tint: DivPalette.gold })
                    Column().width(8)
                    DivSerifText({
                      text: DivText.followupThinking,
                      textSize: 12,
                      weight: 400,
                      spacing: 0,
                      color: DivPalette.textTertiary
                    })
                  }
                  .width('100%')
                  .alignItems(VerticalAlign.Center)
                }
              }

              // 流尾常驻脚注（画板：—— 继续追问将沿用本卦盘推演 ——）
              ListItem() {
                DivSerifText({
                  text: DivText.followupFooter,
                  textSize: 12,
                  weight: 400,
                  spacing: 0,
                  color: DivPalette.textSecondary,
                  textAlign: TextAlign.Center
                })
                  .width('100%')
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
