package com.smartwash.feature.divination.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * 解读正文富文本（issue #78 轻量口径，不引 markdown 库）：
 * - 段内 **加粗**：SpanStyle(Bold)（boldColor 传主题强调色则同步着色）；
 * - 行首 `- ` 列表项：列表符替换为「· 」缩进引导；
 * - 行首 `#`/`###` 标题：降级为整行加粗（audit-23 #10：记号不再原样直出）；
 * - 行内 `` `code` ``：等宽 + codeBackground（消费点传 c.gold14 = 画板 14% 金底）；
 * - 行内 `[文本](url)`：提取链接文本，下划线 + linkColor（消费点传 c.goldText）；
 * - ``` 代码块围栏：围栏记号行移除，围栏内整行等宽 + codeBackground（最小闭环）。
 * 未成对的记号原样保留。
 */
fun buildDivRichText(
    content: String,
    boldColor: Color? = null,
    codeBackground: Color? = null,
    linkColor: Color? = null,
): AnnotatedString = buildAnnotatedString {
    var inCodeBlock = false
    content.lines().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')
        val trimmed = rawLine.trimStart()
        // ``` 围栏行：切换代码块状态，记号本身不呈现（audit-23 #10）
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            return@forEachIndexed
        }
        if (inCodeBlock) {
            // 围栏内整行等宽 + 浅金底（codeBackground 可空，未传时不着底）
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground ?: Color.Unspecified))
            append(rawLine)
            pop()
            return@forEachIndexed
        }
        // 行首 `- ` 列表项：列表符替换为「· 」
        var line = if (trimmed.startsWith("- ")) rawLine.replaceFirst("- ", "· ") else rawLine
        if (trimmed.startsWith("#")) {
            // 行首 #/### 标题：降级为整行加粗（`## ` 段落标题已由 DivMarkdownParser 消费，此处兜底其余层级）
            line = trimmed.dropWhile { it == '#' }.trim()
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            appendInlineSegments(line, boldColor, codeBackground, linkColor)
            pop()
            return@forEachIndexed
        }
        appendInlineSegments(line, boldColor, codeBackground, linkColor)
    }
}

private val DIV_LINK_REGEX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")

private const val TOKEN_BOLD = 0
private const val TOKEN_CODE = 1
private const val TOKEN_LINK = 2

/** 行内记号切分：`**加粗**` / `` `code` `` / `[文本](url)`；未成对的记号原样保留 */
private fun AnnotatedString.Builder.appendInlineSegments(
    line: String,
    boldColor: Color?,
    codeBackground: Color?,
    linkColor: Color?,
) {
    var rest = line
    while (rest.isNotEmpty()) {
        val boldIdx = rest.indexOf("**")
        val codeIdx = rest.indexOf('`')
        val linkMatch = DIV_LINK_REGEX.find(rest)
        val linkIdx = linkMatch?.range?.first ?: -1
        val earliest = listOfNotNull(
            if (boldIdx >= 0) boldIdx to TOKEN_BOLD else null,
            if (codeIdx >= 0) codeIdx to TOKEN_CODE else null,
            if (linkIdx >= 0) linkIdx to TOKEN_LINK else null,
        ).minByOrNull { it.first } ?: run {
            append(rest)
            return
        }
        append(rest.substring(0, earliest.first))
        rest = rest.substring(earliest.first)
        when (earliest.second) {
            TOKEN_BOLD -> {
                val end = rest.indexOf("**", 2)
                if (end < 0) {
                    append(rest)
                    return
                }
                val text = rest.substring(2, end)
                if (text.isNotEmpty()) {
                    pushStyle(
                        if (boldColor != null) {
                            SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)
                        } else {
                            SpanStyle(fontWeight = FontWeight.Bold)
                        },
                    )
                    append(text)
                    pop()
                }
                rest = rest.substring(end + 2)
            }

            TOKEN_CODE -> {
                val end = rest.indexOf('`', 1)
                if (end < 0) {
                    append(rest)
                    return
                }
                val text = rest.substring(1, end)
                if (text.isNotEmpty()) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground ?: Color.Unspecified))
                    append(text)
                    pop()
                }
                rest = rest.substring(end + 1)
            }

            else -> {
                val match = linkMatch ?: run {
                    append(rest)
                    return
                }
                // 链接只呈现文本（url 不上屏），下划线 + 主题强调色（linkColor 可空，未传时不着色）
                pushStyle(SpanStyle(color = linkColor ?: Color.Unspecified, textDecoration = TextDecoration.Underline))
                append(match.groupValues[1])
                pop()
                // match.range 是截断前字符串的坐标，换算为相对坐标推进
                rest = rest.substring(match.range.last + 1 - earliest.first)
            }
        }
    }
}
