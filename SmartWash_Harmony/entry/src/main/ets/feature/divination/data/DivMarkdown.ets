/**
 * 观象台解读正文 Markdown 轻量解析（#80）—— ArkTS 自绘，不引第三方库。
 *
 * 后端解读正文为 Markdown 单文，固定四段 `##` 标题：
 *   ## 一句话断语 / ## 盘面要点 / ## 分项解读 / ## 结论与建议
 * 文末 `> 免责声明：…`；正文支持 **加粗** 与 `- ` 列表。
 *
 * 解析产物：
 * - sections：`## ` 标题切段（映射解读页章节卡）；
 * - disclaimer：`>` 引用行（剥「免责声明：」前缀，映射免责条）；
 * - hasTitles：无任何 `## ` 标题时为 false → 整文单卡兜底。
 *
 * 边界：解析永不抛错（畸形输入退化为纯文本行）；流式期间重复解析（O(文本长)，
 * 解读正文 < 4KB，delta 频率下开销可忽略）。
 */

/** 行内富文本片段（bold=**包裹**） */
export interface DivRichSpan {
  text: string
  bold: boolean
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

/** **加粗** 解析：按 `**` 切分，奇数下标为加粗段；空段丢弃；无 `**` 时单段普通文本 */
function parseSpans(line: string): Array<DivRichSpan> {
  const parts = line.split('**')
  if (parts.length === 1) {
    return [{ text: line, bold: false }]
  }
  const spans: Array<DivRichSpan> = []
  for (let i = 0; i < parts.length; i++) {
    const text = parts[i]
    if (text.length === 0) {
      continue
    }
    spans.push({ text: text, bold: i % 2 === 1 })
  }
  // 畸形（**未闭合）：末段 bold 标记退化为普通，避免整行强调
  if (parts.length % 2 === 0 && spans.length > 0) {
    const last = spans[spans.length - 1]
    if (last.bold) {
      spans[spans.length - 1] = { text: last.text, bold: false }
    }
  }
  return spans
}

function makeLine(line: string, bullet: boolean): DivRichLine {
  return { spans: parseSpans(line), bullet: bullet }
}

/** 免责行剥壳：去掉 `免责声明：` / `免责声明:` 前缀（映射免责条样式） */
function stripDisclaimerPrefix(text: string): string {
  if (text.startsWith('免责声明：')) {
    return text.substring('免责声明：'.length)
  }
  if (text.startsWith('免责声明:')) {
    return text.substring('免责声明:'.length)
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
  for (let i = 0; i < rawLines.length; i++) {
    const raw = rawLines[i]
    const line = raw.trim()
    if (line === '') {
      continue
    }
    if (line.charAt(0) === '#') {
      // 标题行：新开切段（# / ## / ### 同等处理，后端契约为 ## ）
      hasTitles = true
      current = { title: stripHeadingMarks(line), lines: [] }
      sections.push(current)
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
