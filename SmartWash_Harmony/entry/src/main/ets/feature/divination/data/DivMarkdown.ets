/**
 * 观象台解读正文 Markdown 轻量解析（#80）—— ArkTS 自绘，不引第三方库。
 *
 * 后端解读正文为 Markdown 单文，固定四段 `##` 标题：
 *   ## 一句话断语 / ## 盘面要点 / ## 分项解读 / ## 结论与建议
 * 文末 `> 免责声明：…`；正文支持 **加粗**、`- ` 列表、行内 `code`、`[文本](url)` 链接，
 * `#`/`###` 标题降级为整行加粗，``` 围栏代码块移除记号后等宽 + 金底呈现。
 *
 * 解析产物：
 * - sections：`## ` 标题切段（映射解读页章节卡）；
 * - disclaimer：`>` 引用行（剥「免责声明：」前缀，映射免责条）；
 * - hasTitles：无任何 `## ` 标题时为 false → 整文单卡兜底。
 *
 * 边界：解析永不抛错（畸形输入退化为纯文本行）；流式期间重复解析（O(文本长)，
 * 解读正文 < 4KB，delta 频率下开销可忽略）。
 */

/** 行内富文本片段（bold=**包裹** / code=`包裹` / link=[文本](url)） */
import { resString } from '../../../utils/ResourceString'
export interface DivRichSpan {
  text: string
  bold: boolean
  code: boolean
  link: boolean
}

/** 解析后的行：bullet = `- ` 列表项 */
export interface DivRichLine {
  spans: Array<DivRichSpan>
  bullet: boolean
}

/** `## ` 标题切段（title 为空 = 无标题兜底/前导段落） */
export interface DivMdSection {
  title: string
  lines: Array<DivRichLine>
}

/** 解读正文解析结果 */
export interface DivMdDoc {
  sections: Array<DivMdSection>
  disclaimer: string
  hasTitles: boolean
}

/** 剥行首 Markdown 标记（#/空格） */
function stripHeadingMarks(line: string): string {
  let text = line
  while (text.length > 0 && text.charAt(0) === '#') {
    text = text.substring(1)
  }
  return text.trim()
}

/** 行内链接记号 `[文本](url)` */
const DIV_LINK_REGEX: RegExp = new RegExp('\\[([^\\]]+)\\]\\(([^)]+)\\)')

/**
 * 行内记号切分：`**加粗**` / `` `code` `` / `[文本](url)`（对齐安卓 DivMarkdownText.appendInlineSegments）。
 * 取最早出现的记号推进；未成对的记号原样保留（不吞字符）。
 */
function parseSpans(line: string): Array<DivRichSpan> {
  const spans: Array<DivRichSpan> = []
  let rest = line
  while (rest.length > 0) {
    const boldIdx = rest.indexOf('**')
    const codeIdx = rest.indexOf('`')
    const linkMatch = DIV_LINK_REGEX.exec(rest)
    const linkIdx = linkMatch !== null ? linkMatch.index : -1
    let earliestIdx = -1
    let token = -1
    if (boldIdx >= 0 && (earliestIdx < 0 || boldIdx < earliestIdx)) {
      earliestIdx = boldIdx
      token = 0
    }
    if (codeIdx >= 0 && (earliestIdx < 0 || codeIdx < earliestIdx)) {
      earliestIdx = codeIdx
      token = 1
    }
    if (linkIdx >= 0 && (earliestIdx < 0 || linkIdx < earliestIdx)) {
      earliestIdx = linkIdx
      token = 2
    }
    if (earliestIdx < 0) {
      spans.push({ text: rest, bold: false, code: false, link: false })
      break
    }
    if (earliestIdx > 0) {
      spans.push({ text: rest.substring(0, earliestIdx), bold: false, code: false, link: false })
    }
    rest = rest.substring(earliestIdx)
    if (token === 0) {
      const end = rest.indexOf('**', 2)
      if (end < 0) {
        spans.push({ text: rest, bold: false, code: false, link: false })
        break
      }
      const text = rest.substring(2, end)
      if (text.length > 0) {
        spans.push({ text: text, bold: true, code: false, link: false })
      }
      rest = rest.substring(end + 2)
    } else if (token === 1) {
      const end = rest.indexOf('`', 1)
      if (end < 0) {
        spans.push({ text: rest, bold: false, code: false, link: false })
        break
      }
      const text = rest.substring(1, end)
      if (text.length > 0) {
        spans.push({ text: text, bold: false, code: true, link: false })
      }
      rest = rest.substring(end + 1)
    } else {
      if (linkMatch === null) {
        spans.push({ text: rest, bold: false, code: false, link: false })
        break
      }
      const text = linkMatch[1]
      if (text.length > 0) {
        spans.push({ text: text, bold: false, code: false, link: true })
      }
      rest = rest.substring(linkMatch[0].length)
    }
  }
  return spans
}

function makeLine(line: string, bullet: boolean): DivRichLine {
  return { spans: parseSpans(line), bullet: bullet }
}

/** 免责行剥壳：去掉 `免责声明：` / `免责声明:` 前缀（映射免责条样式） */
function stripDisclaimerPrefix(text: string): string {
  if (text.startsWith(resString('div_markdown_1'))) {
    return text.substring(resString('div_markdown_1').length)
  }
  if (text.startsWith(resString('div_markdown_2'))) {
    return text.substring(resString('div_markdown_2').length)
  }
  return text
}

/**
 * 解析解读正文 Markdown。
 * @param markdown 解读全文（流式期间传当前累积全文亦可）
 */
export function parseDivMarkdown(markdown: string): DivMdDoc {
  const sections: Array<DivMdSection> = []
  let disclaimer = ''
  let hasTitles = false
  // 当前段落（无标题前导段 title=''）
  let current: DivMdSection = { title: '', lines: [] }
  sections.push(current)

  const rawLines = markdown.split('\n')
  let inCodeBlock = false
  for (let i = 0; i < rawLines.length; i++) {
    const raw = rawLines[i]
    const line = raw.trim()
    // ``` 围栏行：切换代码块状态，记号本身不呈现（对齐安卓 audit-23 #10 最小闭环）
    if (line.startsWith('```')) {
      inCodeBlock = !inCodeBlock
      continue
    }
    if (inCodeBlock) {
      // 围栏内整行等宽 + 金底（不做行内记号解析，保留原文）
      if (line.length > 0) {
        current.lines.push({ spans: [{ text: line, bold: false, code: true, link: false }], bullet: false })
      }
      continue
    }
    if (line === '') {
      continue
    }
    if (line.startsWith('## ')) {
      // `## ` 标题行：新开切段（后端契约为 ## ）
      hasTitles = true
      current = { title: line.substring(3).trim(), lines: [] }
      sections.push(current)
      continue
    }
    if (line.charAt(0) === '#') {
      // 其余层级标题（# / ### 等）：降级为整行加粗（对齐安卓 audit-23 #10，记号不再原样直出）
      const text = stripHeadingMarks(line)
      if (text.length > 0) {
        current.lines.push({ spans: [{ text: text, bold: true, code: false, link: false }], bullet: false })
      }
      continue
    }
    if (line.startsWith('>')) {
      // 引用行 = 免责条（取首条；剥壳去前缀）
      if (disclaimer === '') {
        const quoted = stripHeadingMarks(line.startsWith('> ') ? line.substring(2) : line.substring(1))
        disclaimer = stripDisclaimerPrefix(quoted)
      }
      continue
    }
    if (line.startsWith('- ')) {
      current.lines.push(makeLine(line.substring(2).trim(), true))
      continue
    }
    current.lines.push(makeLine(line, false))
  }

  // 丢弃空段（无内容的无名段/标题段——标题行紧跟下一标题的空段）
  const filled: Array<DivMdSection> = []
  for (let i = 0; i < sections.length; i++) {
    if (sections[i].lines.length > 0) {
      filled.push(sections[i])
    }
  }
  // 理论上只要有输入必有内容段；全空（纯空行输入）时保留一个空兜底段
  if (filled.length === 0) {
    filled.push({ title: '', lines: [] })
  }
  return { sections: filled, disclaimer: disclaimer, hasTitles: hasTitles }
}
