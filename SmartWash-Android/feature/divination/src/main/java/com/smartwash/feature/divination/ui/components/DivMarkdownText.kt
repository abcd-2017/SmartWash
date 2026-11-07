package com.smartwash.feature.divination.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * 解读正文富文本（issue #78 轻量口径，不引 markdown 库）：
 * 仅支持任务契约的两种元素——
 * - 段内 **加粗**：SpanStyle(Bold)（boldColor 传主题强调色则同步着色）；
 * - 行首 `- ` 列表项：列表符替换为「· 」缩进引导；
 * 其余 markdown 记号原样保留（LLM 输出克制，四段正文基本为纯文本）。
 */
fun buildDivRichText(content: String, boldColor: Color? = null): AnnotatedString = buildAnnotatedString {
    content.lines().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')
        val line = if (rawLine.trimStart().startsWith("- ")) {
            rawLine.replaceFirst("- ", "· ")
        } else {
            rawLine
        }
        appendBoldSegments(line, boldColor)
    }
}

/** 行内 `**加粗**` 切分；未成对的 `**` 原样保留 */
private fun AnnotatedString.Builder.appendBoldSegments(line: String, boldColor: Color?) {
    var rest = line
    while (true) {
        val start = rest.indexOf("**")
        if (start < 0) {
            append(rest)
            return
        }
        val end = rest.indexOf("**", start + 2)
        if (end < 0) {
            append(rest)
            return
        }
        append(rest.substring(0, start))
        val bold = rest.substring(start + 2, end)
        if (bold.isNotEmpty()) {
            pushStyle(
                if (boldColor != null) {
                    SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)
                } else {
                    SpanStyle(fontWeight = FontWeight.Bold)
                },
            )
            append(bold)
            pop()
        }
        rest = rest.substring(end + 2)
    }
}
