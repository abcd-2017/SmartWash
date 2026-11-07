/**
 * 解读页 —— 对齐安卓同票 #78 的解读页结构（SSE 流式 + Markdown 章节卡）。
 *
 * 结构：页头右侧固定卦印 → 解读正文三态（Loading / Error / Success 渐进出卡）
 * → 免责条（服务端 `>` 引用行）→ 反馈区（done 后可提交评分与应验回填）
 * → 底部「继续追问」（soft 44 高 13 号通栏）→ 页脚免责声明常驻。
 *
 * 关键约束：
 * - 解读走 DivLlmRepository（SSE 流式优先 + 攒流兜底 + Mock 回退开关），
 *   delta 累积全文经 parseDivMarkdown 按 `## ` 标题切段映射章节卡（逐段渐现）；
 * - done 帧的 interpretationId 存下供追问与反馈；
 * - 服务端卦例 id：本地行已关联（serverRecordId>0）直接用；未关联（旧记录直进）先建档回写；
 * - 沿用原盘原 castAt，不重算；页脚免责声明 div_disclaimer_reading 常驻（不因 Error 消失）。
 */

import { createDivRecord, buildCreateRecordForm, submitDivFeedback } from '../../../network/DivinationApi'
import { interpretMarkdown, isLlmMock } from '../../../data/DivLlmRepository'
import { DivMdDoc, DivMdSection, parseDivMarkdown } from '../../../data/DivMarkdown'
import { chartNameOf } from '../../../data/DivChartReader'
import { decodeBundle, decodeBundleFromJson, DivRecordRepository, DivRecordRow } from '../../../data/DivinationStore'
import {
  DivCard,
  DivCategoryChip,
  DivFooterNote,
  DivGoldButton,
  DivPageHeader,
  DivPalette,
  DivSerifText,
  DivText,
  DivWaveIcon
} from '../../components/DivComponents'
import { DivRichText } from '../../components/DivRichText'
import { DivinationScreen } from '../../DivinationScreen'
import { RouteNames } from '../../../../../constant/RouteNames'
import { DesignDimens } from '../../../../../constant/DesignSystem'
import { getNumberParam, pathStack } from '../../../../../utils/PathStackUtil'
import { showToastSafety } from '../../../../../utils/ToastUtil'
import { logger } from '../../../../../utils/Logger'

/** 解读正文状态（对齐安卓 RequestState） */
type ReadingState = 'idle' | 'loading' | 'success' | 'error'

/** 章节卡 ForEach 稳定指纹：行数 + 列表项数 + 文本总量（SSE 追加式增长下已定型段不变键） */
function sectionFingerprint(section: DivMdSection): string {
  let weight = 0
  for (let i = 0; i < section.lines.length; i++) {
    const line = section.lines[i]
    weight += line.bullet ? 1 : 0
    for (let j = 0; j < line.spans.length; j++) {
      weight += line.spans[j].text.length
    }
  }
  return `${section.title}|${section.lines.length}|${weight}`
}

@Builder
export function DivReadingBuilder() {
  DivReadingPage()
}

/** 解读章节卡（画板：surface 底 + 1px 12% 金发丝线、r16、内边距 14、标题→正文 8）；
 *  正文经 DivRichText 渲染（**加粗** / `- ` 列表），首卡淡入由外层统一控制 */
@ComponentV2
struct ReadingSectionCard {
  @Param section: DivMdSection | null = null

  build() {
    if (this.section !== null) {
      DivCard({ lineColor: DivPalette.goldHair }) {
        Column() {
          if (this.section.title.length > 0) {
            DivSerifText({
              text: this.section.title,
              textSize: 14,
              weight: 600,
              spacing: 4,
              color: DivPalette.goldText
            })
            Column().height(8)
          }
          DivRichText({
            lines: this.section.lines,
            textSize: 12,
            color: DivPalette.textPrimary
          })
        }
        .width('100%')
        .padding(DesignDimens.CARD_PADDING)
        .margin({ top: DesignDimens.SPACE_XS, bottom: DesignDimens.SPACE_XS })
      }
    }
  }
}

@ComponentV2
struct DivReadingPage {
  @Local loadingRecord: boolean = true
  @Local record: DivRecordRow | null = null
  @Local chartName: string = ''
  @Local chartSeal: string = ''
  @Local readingState: ReadingState = 'idle'
  /** SSE 累积全文（流式逐帧 / 攒流一次性；渲染时经 parseDivMarkdown 切段） */
  @Local streamingMd: string = ''
  /** 切段结果缓存（onAccumulated 时同步刷新，避免 build 内重复解析） */
  @Local parsed: DivMdDoc = { sections: [], disclaimer: '', hasTitles: false }
  /** done 帧解读 id（>0 才可反馈；Mock 为 0） */
  @Local interpretationId: number = 0
  /** error 态用户文案（error 帧各码映射 / 传输失败兜底） */
  @Local errorText: string = DivText.readingError
  /** 反馈面板展开态 */
  @Local feedbackOpen: boolean = false
  /** 评分 1-5（0 未选）与应验回填 1/2/3（0 未选） */
  @Local rating: number = 0
  @Local outcome: number = 0
  @Local submittingFeedback: boolean = false
  /** SSE 会话序号：重试时旧流回调丢弃（防串流） */
  private sseSeq: number = 0

  async aboutToAppear(): Promise<void> {
    const localId = getNumberParam(RouteNames.DIV_READING)
    if (localId === null) {
      logger.warn('divReadingPage', '解读页路由参数缺失 recordId，按空态呈现')
      this.loadingRecord = false
      return
    }
    try {
      const row = await DivRecordRepository.getById(localId)
      this.record = row
      if (row !== null) {
        this.chartName = this.resolveName(row)
        this.chartSeal = this.resolveSeal(row.method, this.chartName)
        logger.debug('divReadingPage', `解读页记录加载完成 localId=${localId} method=${row.method} ` +
          `serverRecordId=${row.serverRecordId}`)
      } else {
        logger.warn('divReadingPage', `解读页卦象记录不存在 localId=${localId}，按空态呈现`)
      }
    } catch (err) {
      this.record = null
      logger.error('divReadingPage', '解读页读取卦象记录失败',
        err instanceof Error ? err.message : '')
    }
    this.loadingRecord = false
    if (this.record !== null) {
      await this.startReading()
    }
  }

  /** 盘面卦名（读库解码；失败回退术数名） */
  private resolveName(row: DivRecordRow): string {
    const chartJson = decodeBundle(decodeBundleFromJson(row.chartJson))
    return chartNameOf(row.method, chartJson)
  }

  /** 页头卦印文字（对齐安卓 readingSealText：六爻取卦名首字 / 梅 / 奇 / 壬） */
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

  /** 解读正文 Markdown 切段（onAccumulated 时调用，结果缓存到 @Local parsed） */
  private refreshParsed(): void {
    this.parsed = parseDivMarkdown(this.streamingMd)
  }

  /** 服务端卦例 id 就绪（未关联则先建档回写；失败返回 0 并呈现错误态；Mock 模式完全离线跳过） */
  private async ensureServerRecordId(row: DivRecordRow): Promise<number> {
    if (isLlmMock()) {
      // Mock 回归：不出网，本地 id 直作会话标识
      return row.serverRecordId > 0 ? row.serverRecordId : -1
    }
    if (row.serverRecordId > 0) {
      return row.serverRecordId
    }
    const resp = await createDivRecord(buildCreateRecordForm(row))
    if (resp.code !== 200 || resp.data === null) {
      logger.warn('divReadingPage', `解读建档失败 localId=${row.id} code=${resp.code} ` +
        `message=${resp.message ?? ''}`)
      return 0
    }
    await DivRecordRepository.updateServerRecordId(row.id, resp.data)
    row.serverRecordId = resp.data
    this.record = row
    logger.info('divReadingPage', `解读建档成功 localId=${row.id} serverRecordId=${resp.data}`)
    return resp.data
  }

  /** 发起解读（可重试重入；seq guard 防旧流回调） */
  private async startReading(): Promise<void> {
    const record = this.record
    if (record === null) {
      return
    }
    const seq = ++this.sseSeq
    this.readingState = 'loading'
    this.readingErrorReset()
    try {
      const serverId = await this.ensureServerRecordId(record)
      if (seq !== this.sseSeq) {
        return
      }
      if (serverId === 0) {
        this.readingState = 'error'
        this.errorText = DivText.chartBuildFailed
        return
      }
      const that = this
      await interpretMarkdown(serverId, record.method, record.question, {
        onAccumulated: (fullMarkdown: string): void => {
          if (seq !== that.sseSeq) {
            return
          }
          that.streamingMd = fullMarkdown
          that.refreshParsed()
          that.readingState = 'success'
        },
        onDone: (interpretationId: number, cached: boolean): void => {
          if (seq !== that.sseSeq) {
            return
          }
          that.interpretationId = interpretationId
          that.readingState = 'success'
          // 低频关键动作：解读生成成功（流式/攒流完成）
          logger.info('divReadingPage', `解读生成成功 serverRecordId=${serverId} ` +
            `interpretationId=${interpretationId} cached=${cached} chars=${that.streamingMd.length}`)
        },
        onSseError: (code: number, message: string): void => {
          if (seq !== that.sseSeq) {
            return
          }
          that.errorText = message
          that.readingState = 'error'
          logger.warn('divReadingPage', `解读 error 帧 serverRecordId=${serverId} code=${code}`)
        },
        onFail: (message: string): void => {
          if (seq !== that.sseSeq) {
            return
          }
          that.errorText = message
          that.readingState = 'error'
          logger.warn('divReadingPage', `解读通道失败 serverRecordId=${serverId}: ${message}`)
        }
      })
    } catch (err) {
      if (seq !== this.sseSeq) {
        return
      }
      this.errorText = DivText.readingError
      this.readingState = 'error'
      logger.error('divReadingPage', `解读发起异常 localId=${record.id}, ${err instanceof Error ? err.message : ''}`)
    }
  }

  /** 重试前清空流式中间态 */
  private readingErrorReset(): void {
    this.streamingMd = ''
    this.parsed = { sections: [], disclaimer: '', hasTitles: false }
    this.interpretationId = 0
    this.errorText = DivText.readingError
    this.feedbackOpen = false
    this.rating = 0
    this.outcome = 0
  }

  // ========== 反馈（done 帧后的评分与应验回填） ==========

  private canSubmitFeedback(): boolean {
    return this.rating > 0 && !this.submittingFeedback
  }

  private async submitFeedback(): Promise<void> {
    const record = this.record
    if (record === null || !this.canSubmitFeedback()) {
      return
    }
    this.submittingFeedback = true
    try {
      const resp = await submitDivFeedback(record.serverRecordId, {
        interpretationId: this.interpretationId,
        rating: this.rating,
        outcome: this.outcome,
        outcomeNote: ''
      })
      if (resp.code === 200) {
        showToastSafety(DivText.feedbackDone)
        this.feedbackOpen = false
        logger.info('divReadingPage', `反馈提交成功 serverRecordId=${record.serverRecordId} ` +
          `interpretationId=${this.interpretationId} rating=${this.rating} outcome=${this.outcome}`)
      } else {
        showToastSafety(resp.message, DivText.readingError)
        logger.warn('divReadingPage', `反馈提交失败 code=${resp.code} message=${resp.message ?? ''}`)
      }
    } catch (err) {
      showToastSafety(null, DivText.readingError)
      logger.error('divReadingPage', '反馈提交异常', err instanceof Error ? err.message : '')
    }
    this.submittingFeedback = false
  }

  @Builder
  FeedbackPanel() {
    DivCard({ lineColor: DivPalette.goldHair }) {
      Column({ space: 8 }) {
        DivSerifText({
          text: DivText.feedbackTitle,
          textSize: 14,
          weight: 600,
          spacing: 4,
          color: DivPalette.goldText
        })

        DivSerifText({
          text: DivText.ratingLabel,
          textSize: 12,
          weight: 400,
          spacing: 0,
          color: DivPalette.textSecondary
        })
        Row({ space: 8 }) {
          ForEach(DivText.ratingScores, (label: string, index: number) => {
            DivCategoryChip({
              text: label,
              selected: this.rating === index + 1,
              horizontalPadding: 14,
              onAction: () => {
                this.rating = index + 1
              }
            })
          }, (label: string, index: number) => `rating-${index}-${this.rating === index + 1}`)
        }
        .width('100%')

        DivSerifText({
          text: DivText.outcomeLabel,
          textSize: 12,
          weight: 400,
          spacing: 0,
          color: DivPalette.textSecondary
        })
        Row({ space: 8 }) {
          DivCategoryChip({
            text: DivText.outcomeYing,
            selected: this.outcome === 1,
            horizontalPadding: 14,
            onAction: () => {
              this.outcome = 1
            }
          })
          DivCategoryChip({
            text: DivText.outcomeWei,
            selected: this.outcome === 2,
            horizontalPadding: 14,
            onAction: () => {
              this.outcome = 2
            }
          })
          DivCategoryChip({
            text: DivText.outcomeNan,
            selected: this.outcome === 3,
            horizontalPadding: 14,
            onAction: () => {
              this.outcome = 3
            }
          })
        }
        .width('100%')

        DivGoldButton({
          text: DivText.feedbackSubmit,
          soft: true,
          btnHeight: 36,
          fontSize: 12,
          isEnabled: this.canSubmitFeedback(),
          onAction: () => {
            this.submitFeedback()
          }
        })
      }
      .width('100%')
      .padding(DesignDimens.CARD_PADDING)
      .alignItems(HorizontalAlign.Start)
    }
  }

  build() {
    NavDestination() {
      DivinationScreen() {
        DivPageHeader({
          title: DivText.readingTitle,
          // 页头右侧固定卦印（对齐安卓 sealTrailing：右缘 20、30 方印 r8 平涂深朱、印文 14/700）
          sealTrailing: this.chartSeal,
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
            .padding({ top: 80 })
        } else {
          // 内容统一左右 20（对齐安卓页边距；卡片不再通栏）
          Column() {
            // 解读正文三态：loading（无内容帧）/ error / success（流式渐进 / 攒流整段）
            if (this.readingState === 'loading' || this.readingState === 'idle') {
              DivCard() {
                Column() {
                  DivWaveIcon({ sizeValue: 18, tint: DivPalette.gold })
                  Column().height(8)
                  DivSerifText({
                    text: DivText.readingLoading,
                    textSize: 12,
                    weight: 400,
                    spacing: 0,
                    color: DivPalette.textTertiary,
                    textAlign: TextAlign.Center
                  })
                }
                .width('100%')
                .padding({ top: 24, bottom: 24 })
                .alignItems(HorizontalAlign.Center)
              }
            } else if (this.readingState === 'error' && this.streamingMd.length === 0) {
              DivCard() {
                Column({ space: 12 }) {
                  DivSerifText({
                    text: this.errorText,
                    textSize: 12,
                    weight: 400,
                    spacing: 0,
                    color: DivPalette.textTertiary,
                    textAlign: TextAlign.Center
                  })
                  DivGoldButton({
                    text: DivText.readingRetry,
                    soft: true,
                    btnHeight: 36,
                    fontSize: 12,
                    isEnabled: true,
                    onAction: () => {
                      this.startReading()
                    }
                  })
                }
                .width('100%')
                .padding({ top: 24, bottom: 24 })
                .alignItems(HorizontalAlign.Center)
              }
            } else if (this.streamingMd.length > 0) {
              // 章节卡列表（`## ` 标题切段；无标题 hasTitles=false 时整文单卡兜底；
              // 键含段落指纹，流式期间已定型段复用节点、增长中段单独重建）
              Column() {
                ForEach(this.parsed.sections, (section: DivMdSection, index: number) => {
                  ReadingSectionCard({ section: section })
                }, (section: DivMdSection, index: number) =>
                  `${index}-${sectionFingerprint(section)}`)

                // 免责条（服务端 `>` 引用行；剥「免责声明：」前缀）
                if (this.parsed.disclaimer.length > 0) {
                  DivSerifText({
                    text: this.parsed.disclaimer,
                    textSize: 12,
                    weight: 400,
                    spacing: 1,
                    color: DivPalette.textTertiary,
                    textAlign: TextAlign.Center
                  })
                    .width('100%')
                    .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
                }

                // 流中断（已有部分内容后又失败）：错误条 + 重试
                if (this.readingState === 'error') {
                  Column({ space: 12 }) {
                    DivSerifText({
                      text: this.errorText,
                      textSize: 12,
                      weight: 400,
                      spacing: 0,
                      color: DivPalette.textTertiary,
                      textAlign: TextAlign.Center
                    })
                    DivGoldButton({
                      text: DivText.readingRetry,
                      soft: true,
                      btnHeight: 36,
                      fontSize: 12,
                      isEnabled: true,
                      onAction: () => {
                        this.startReading()
                      }
                    })
                  }
                  .width('100%')
                  .padding({ top: DesignDimens.SPACE_SM, bottom: DesignDimens.SPACE_SM })
                  .alignItems(HorizontalAlign.Center)
                }
              }
              .width('100%')

              // 反馈区（真连 done 后显示入口 / 面板）
              if (this.readingState === 'success' && this.interpretationId > 0) {
                if (this.feedbackOpen) {
                  this.FeedbackPanel()
                } else {
                  DivGoldButton({
                    text: DivText.feedbackTitle,
                    soft: true,
                    btnHeight: 36,
                    fontSize: 12,
                    isEnabled: true,
                    onAction: () => {
                      this.feedbackOpen = true
                    }
                  })
                    .padding({ top: DesignDimens.SPACE_SM })
                }
              }
            }

            // 主行动：单按钮「继续追问」（对齐安卓 soft 44 高 13 号通栏；画板「分享」暂无实现，不落占位）
            DivGoldButton({
              text: DivText.readingFollowup,
              soft: true,
              btnHeight: 44,
              fontSize: 13,
              isEnabled: this.record !== null,
              onAction: () => {
                const id = this.record?.id ?? 0
                pathStack.pushPathByName(RouteNames.DIV_FOLLOW_UP, id)
              }
            })
              .padding({
                left: DesignDimens.PAGE_PADDING,
                right: DesignDimens.PAGE_PADDING,
                top: DesignDimens.SPACE_LG
              })

            // 页脚免责声明（常驻）
            DivFooterNote({ text: DivText.disclaimerReading })
          }
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        }
      }
    }
    .hideTitleBar(true)
  }
}
