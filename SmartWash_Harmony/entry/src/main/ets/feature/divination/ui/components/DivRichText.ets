/**
 * 观象台解读富文本渲染（#80）—— 消费 DivMarkdown 解析产物（DivRichLine[]）。
 *
 * 规则（对齐解读章节卡排版：正文 14/400，行间距 8）：
 * - `- ` 列表项：金色「·」引导 + 悬挂缩进（文本左对齐，引导符独立列）；
 * - **加粗**：同字号 weight 600 墨色（鎏金只作图形强调不作正文色，见 DivComponents 文件头）；
 * - 行内 `code`：等宽 + 14% 金底；`[文本](url)`：金text + 下划线；
 * - 无记号的普通行 = 单 Span 渲染。
 *
 * 本组件无可点击行为（规范 §7，无 Material 涟漪）。
 */

import { DivRichLine, DivRichSpan } from '../../data/DivMarkdown'
import { DivPalette } from './DivComponents'

@ComponentV2
export struct DivRichText {
  @Param lines: Array<DivRichLine> = []
  @Param textSize: number = 12
  @Param color: ResourceColor = DivPalette.textPrimary

  /** 行内 Span 组：**加粗** → weight 600；`code` → 等宽 + 14% 金底；链接 → 金text + 下划线 */
  @Builder
  InlineSpans(spans: Array<DivRichSpan>) {
    ForEach(spans, (span: DivRichSpan, spanIndex: number) => {
      if (span.code) {
        Span(span.text)
          .fontSize(this.textSize)
          .fontColor(this.color)
          .fontFamily('monospace')
          .backgroundColor(DivPalette.gold14)
      } else if (span.link) {
        Span(span.text)
          .fontSize(this.textSize)
          .fontColor(DivPalette.goldText)
          .fontWeight(span.bold ? FontWeight.Bold : FontWeight.Normal)
          .decoration({ type: TextDecorationType.Underline })
      } else {
        Span(span.text)
          .fontSize(this.textSize)
          .fontColor(this.color)
          .fontWeight(span.bold ? FontWeight.Bold : FontWeight.Normal)
      }
    }, (span: DivRichSpan, spanIndex: number) => `${spanIndex}-${span.bold}-${span.code}-${span.link}`)
  }

  build() {
    // 行距 8（对齐 14/20 行盒，compare-15 #34）
    Column({ space: 8 }) {
      ForEach(this.lines, (line: DivRichLine, index: number) => {
        if (line.bullet) {
          Row({ space: 6 }) {
            // 列表引导符（金色圆点，图形强调口径）
            Text('·')
              .fontSize(this.textSize)
              .fontColor(DivPalette.gold)
              .fontWeight(FontWeight.Bold)
            Text() {
              this.InlineSpans(line.spans)
            }
            .fontSize(this.textSize)
            .fontColor(this.color)
            .textAlign(TextAlign.Start)
            .layoutWeight(1)
          }
          .width('100%')
          .alignItems(VerticalAlign.Top)
        } else {
          Text() {
            this.InlineSpans(line.spans)
          }
          .fontSize(this.textSize)
          .fontColor(this.color)
          .textAlign(TextAlign.Start)
          .width('100%')
        }
      }, (line: DivRichLine, index: number) => `${index}-${line.bullet}-${line.spans.length}`)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
