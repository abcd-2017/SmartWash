/**
 * 追问页 —— 对齐安卓 ui/page/followup/DivFollowUpPage.kt + 同票 #78 的 SSE 追问。
 *
 * 结构：页头（居中标题 + 副题）→ 聊天流（user 金底右对齐 / assistant 素底左对齐）
 * → 新消息自动滚底 + Loading 尾部「推演中…」+ 流式回复气泡（delta 渐进出字）
 * → 底部输入栏（胶囊输入 + 发送金按钮）。
 *
 * 关键约束：
 * - 记录读库（getById），沿用原盘原 castAt，不重算。
 * - followUp 走 DivLlmRepository（SSE 流式优先 + 攒流兜底 + Mock 回退开关），
 *   只发当前问题（历史解读上下文由服务端按 recordId 拼接）；
 * - 服务端卦例 id：本地行已关联直接用；未关联（旧记录）先建档回写；
 * - Loading 中防重入；发送后清输入框。记录无则显示 div_chart_missing。
 */

import { buildCreateRecordForm, createDivRecord, DivMessage } from '../../../network/DivinationApi'
import { followUpMarkdown, isLlmMock } from '../../../data/DivLlmRepository'
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
import { DesignDimens, DesignElevation, DesignColor } from '../../../../../constant/DesignSystem'
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
        .padding({
          left: DesignDimens.SPACE_MD,
          right: DesignDimens.SPACE_MD,
          top: DesignDimens.SPACE_SM,
          bottom: DesignDimens.SPACE_SM
        })
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
        .padding({
          left: DesignDimens.SPACE_MD,
          right: DesignDimens.SPACE_MD,
          top: DesignDimens.SPACE_SM,
          bottom: DesignDimens.SPACE_SM
        })
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
  /** 流式回复累积文本（尾部气泡渐进出字；done 后并入 history 并清空） */
  @Local streamingReply: string = ''
  /** 本轮流式错误文案（非空时尾部显示错误行） */
  @Local streamingError: string = ''
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
        logger.debug('divFollowUpPage', `追问页记录加载完成 localId=${recordId} ` +
          `serverRecordId=${row.serverRecordId}`)
      } else {
        logger.warn('divFollowUpPage', `追问页卦象记录不存在 localId=${recordId}，按空态呈现`)
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

  /** 流式回复增长滚底（频次与 delta 同步；smooth 滚动保证阅读连续） */
  @Monitor('streamingReply')
  onStreamingChange(): void {
    if (this.sending) {
      this.scroller.scrollToIndex(this.historyLength, false)
    }
  }

  /** 服务端卦例 id 就绪（未关联先建档回写；Mock 模式完全离线跳过） */
  private async ensureServerRecordId(record: DivRecordRow): Promise<number> {
    if (isLlmMock()) {
      return record.serverRecordId > 0 ? record.serverRecordId : -1
    }
    if (record.serverRecordId > 0) {
      return record.serverRecordId
    }
    const resp = await createDivRecord(buildCreateRecordForm(record))
    if (resp.code !== 200 || resp.data === null) {
      logger.warn('divFollowUpPage', `追问建档失败 localId=${record.id} code=${resp.code} ` +
        `message=${resp.message ?? ''}`)
      return 0
    }
    await DivRecordRepository.updateServerRecordId(record.id, resp.data)
    record.serverRecordId = resp.data
    this.record = record
    logger.info('divFollowUpPage', `追问建档成功 localId=${record.id} serverRecordId=${resp.data}`)
    return resp.data
  }

  /** 发送追问：追加 user 轮 → SSE 追问（流式渐进出字）→ done 后并入 assistant 轮；Loading 中防重入 */
  private async send(): Promise<void> {
    const q = this.input.trim()
    if (q.length === 0 || this.sending) {
      return
    }
    const record = this.record
    if (record === null) {
      return
    }
    const userTurn: DivMessage = { role: 'user', content: q }
    this.history = this.history.concat(userTurn)
    this.historyLength = this.history.length
    // 发送后清输入框
    this.input = ''
    this.sending = true
    this.streamingReply = ''
    this.streamingError = ''
    try {
      const serverId = await this.ensureServerRecordId(record)
      if (serverId === 0) {
        this.streamingError = '追问通道不可用，请稍后重试'
        this.sending = false
        return
      }
      const turn = this.history.length
      await followUpMarkdown(serverId, q, turn, {
        onAccumulated: (full: string): void => {
          this.streamingReply = full
        },
        onDone: (interpretationId: number, cached: boolean): void => {
          const reply: DivMessage = { role: 'assistant', content: this.streamingReply }
          this.history = this.history.concat(reply)
          this.historyLength = this.history.length
          this.streamingReply = ''
          this.streamingError = ''
          this.sending = false
          // 低频关键动作：追问提交成功（追问内容只记长度，不打全文）
          logger.info('divFollowUpPage', `追问提交成功 serverRecordId=${serverId} ` +
            `interpretationId=${interpretationId} turn=${turn} chars=${reply.content.length}`)
        },
        onSseError: (code: number, message: string): void => {
          this.streamingError = message
          this.sending = false
          logger.warn('divFollowUpPage', `追问 error 帧 serverRecordId=${serverId} code=${code}`)
        },
        onFail: (message: string): void => {
          this.streamingError = message
          this.sending = false
          logger.warn('divFollowUpPage', `追问通道失败 serverRecordId=${serverId}: ${message}`)
        }
      })
    } catch (err) {
      this.streamingError = '追问通道不可用，请稍后重试'
      this.sending = false
      logger.error('divFollowUpPage', `追问发起异常 localId=${record.id}, ` +
        `${err instanceof Error ? err.message : ''}`)
    }
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
          .padding({ left: DesignDimens.SPACE_MD, right: DesignDimens.SPACE_MD })
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
      .padding({
        left: DesignDimens.PAGE_PADDING,
        right: DesignDimens.PAGE_PADDING,
        top: DesignDimens.SPACE_MD,
        bottom: DesignDimens.SPACE_MD
      })
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
              .padding({ top: 80 })
          } else {
            // 聊天流（画板：项距 16）
            List({ scroller: this.scroller, space: DesignDimens.CARD_SPACING }) {
              if (this.history.length === 0) {
                ListItem() {
                  // ListItem 默认 Start 对齐，需外层通栏 Row 居中（对齐画板水平居中）
                  Row() {
                    DivSerifText({
                      text: DivText.followupEmpty,
                      textSize: 12,
                      weight: 400,
                      spacing: 0,
                      color: DivPalette.textTertiary,
                      textAlign: TextAlign.Center
                    })
                  }
                  .width('100%')
                  .justifyContent(FlexAlign.Center)
                  .padding({ top: 40 })
                }
              }

              ForEach(this.history, (turn: DivMessage, index: number) => {
                ListItem() {
                  this.ChatBubble(turn)
                }
              }, (turn: DivMessage, index: number) => `${index}-${turn.role}-${turn.content}`)

              if (this.sending && this.streamingReply.length > 0) {
                // 流式回复气泡（assistant 样式，delta 渐进出字；done 后并入聊天流）
                ListItem() {
                  ChatBubbleView({ turn: { role: 'assistant', content: this.streamingReply } })
                }
              } else if (this.sending) {
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

              if (this.streamingError.length > 0) {
                ListItem() {
                  Row() {
                    DivSerifText({
                      text: this.streamingError,
                      textSize: 12,
                      weight: 400,
                      spacing: 0,
                      color: DivPalette.textTertiary,
                      textAlign: TextAlign.Center
                    })
                  }
                  .width('100%')
                  .justifyContent(FlexAlign.Center)
                  .padding({ top: 8, bottom: 8 })
                }
              }

              // 流尾常驻脚注（画板：—— 继续追问将沿用本卦盘推演 ——）
              ListItem() {
                // 同上：通栏 Row 承载居中，避免 ListItem Start 对齐把脚注顶到左缘
                Row() {
                  DivSerifText({
                    text: DivText.followupFooter,
                    textSize: 12,
                    weight: 400,
                    spacing: 0,
                    color: DivPalette.textSecondary,
                    textAlign: TextAlign.Center
                  })
                }
                .width('100%')
                .justifyContent(FlexAlign.Center)
              }
            }
            .layoutWeight(1)
            .width('100%')
            .padding({
              left: DesignDimens.PAGE_PADDING,
              right: DesignDimens.PAGE_PADDING,
              top: DesignDimens.SPACE_XS,
              bottom: DesignDimens.SPACE_XS
            })
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
