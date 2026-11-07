package com.smartwash.feature.divination.data

/**
 * 解读 Markdown 切分器（issue #78）。
 *
 * 后端解读正文为 Markdown 单文，四段固定 `## ` 标题：
 * `## 一句话断语` / `## 盘面要点` / `## 分项解读` / `## 结论与建议`，文末 `> 免责声明：…`。
 *
 * 切分规则：
 * - `## ` 行开新段（标题取行内余文），其余行归当前段正文；
 * - `> ` 引用行归入免责条（多行以换行拼接）；
 * - 标题含「一句话断语」→ summary；含「结论与建议」（或「总结与行动」）→ action；
 *   其余段 → 章节卡列表（标题→卡题、正文→卡体）；
 * - 全文无任何 `## ` 标题 → 整文单卡兜底（卡题空串，UI 用兜底标题渲染）。
 *
 * 流式口径 [parseStreaming]：同一切分规则，最后一段若仍在生成（其后再无新标题）作为
 * pending 段返回，已完成段先上屏，实现逐段渲染。
 */
object DivMarkdownParser {

    private const val SUMMARY_TITLE = "一句话断语"
    private const val ACTION_TITLE = "结论与建议"
    private const val ACTION_TITLE_ALT = "总结与行动"

    /** 流式中间态：已完成各槽位 + 生成中段 */
    data class Streaming(
        val summary: String? = null,
        val sections: List<DivReadingSection> = emptyList(),
        val action: String? = null,
        val pending: DivReadingSection? = null,
        val disclaimer: String? = null,
    )

    /** 全量切分为 UI 模型（[DivReading]；无标题兜底单卡已内建） */
    fun parse(markdown: String): DivReading {
        val result = splitBlocks(markdown, tailIncomplete = false)
        val summary = result.blocks.firstOrNull { isSummary(it.title) }?.content
        val action = result.blocks.firstOrNull { isAction(it.title) }?.content
        val sections = result.blocks
            .filterNot { isSummary(it.title) || isAction(it.title) }
            .map { DivReadingSection(it.title, it.content) }
            .ifEmpty {
                // 无任何 ## 段：整文单卡兜底（免责行已剔除）
                val body = result.rawBodyWithoutDisclaimer
                if (body.isNotBlank()) {
                    listOf(DivReadingSection("", body))
                } else {
                    emptyList()
                }
            }
        return DivReading(
            summary = summary.orEmpty(),
            sections = sections,
            action = action.orEmpty(),
            disclaimer = result.disclaimer,
        )
    }

    /** 流式前缀切分：已完成段即时上屏，尾部未完段进 pending */
    fun parseStreaming(markdown: String): Streaming {
        val result = splitBlocks(markdown, tailIncomplete = true)
        if (result.blocks.isEmpty()) return Streaming()
        val settled = if (result.blocks.last().incomplete) result.blocks.dropLast(1) else result.blocks
        val pending = result.blocks.lastOrNull()
            ?.takeIf { it.incomplete }
            ?.let { DivReadingSection(it.title, it.content) }
        return Streaming(
            summary = settled.firstOrNull { isSummary(it.title) }?.content,
            sections = settled
                .filterNot { isSummary(it.title) || isAction(it.title) }
                .map { DivReadingSection(it.title, it.content) },
            action = settled.firstOrNull { isAction(it.title) }?.content,
            pending = pending,
            disclaimer = result.disclaimer,
        )
    }

    // ---- 内部 ----

    private data class Block(
        val title: String,
        val content: String,
        val incomplete: Boolean = false,
    )

    private class SplitResult(
        val blocks: List<Block>,
        val disclaimer: String?,
        /** 全文无标题时的兜底正文（免责行剔除后整文） */
        val rawBodyWithoutDisclaimer: String,
    )

    private fun splitBlocks(markdown: String, tailIncomplete: Boolean): SplitResult {
        val blocks = mutableListOf<Block>()
        val disclaimers = mutableListOf<String>()
        val bodyLines = mutableListOf<String>()
        var title = ""
        var content = StringBuilder()
        var sawHeading = false

        fun closeBlock() {
            val text = content.toString().trim()
            if (title.isNotEmpty() || text.isNotEmpty()) {
                blocks.add(Block(title, text))
            }
        }

        markdown.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.startsWith("## ") -> {
                    sawHeading = true
                    closeBlock()
                    title = line.removePrefix("## ").trim()
                    content = StringBuilder()
                }
                line.trimStart().startsWith(">") -> {
                    disclaimers.add(line.trimStart().removePrefix(">").trim())
                }
                else -> {
                    if (title.isNotEmpty() || line.isNotBlank()) {
                        content.appendLine(line)
                    }
                    if (title.isEmpty()) {
                        bodyLines.add(line)
                    }
                }
            }
        }
        if (tailIncomplete && (title.isNotEmpty() || content.isNotBlank())) {
            // 流式：尾部块视为仍在生成（其后不会再出现新标题）
            blocks.add(Block(title, content.toString().trim(), incomplete = true))
        } else {
            closeBlock()
        }
        return SplitResult(
            blocks = blocks,
            disclaimer = disclaimers.joinToString("\n").ifBlank { null },
            rawBodyWithoutDisclaimer = if (sawHeading) "" else bodyLines.joinToString("\n").trim(),
        )
    }

    private fun isSummary(title: String): Boolean = title == SUMMARY_TITLE

    private fun isAction(title: String): Boolean = title == ACTION_TITLE || title == ACTION_TITLE_ALT
}
