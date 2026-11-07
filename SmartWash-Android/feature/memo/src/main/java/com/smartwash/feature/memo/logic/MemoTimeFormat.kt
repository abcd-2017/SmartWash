package com.smartwash.feature.memo.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 记录时间展示格式化（纯函数，照 ShortCodeRules 的容错解析先例）。
 * 后端时间串格式未在契约中定死：依次尝试 ISO（yyyy-MM-ddTHH:mm:ss）、
 * 「yyyy-MM-dd HH:mm:ss」「yyyy-MM-dd HH:mm」，全部失败回落原文展示。
 * 展示规则：本年 → 「M月d日 HH:mm」；跨年 → 「yyyy年M月d日」。
 */
object MemoTimeFormat {

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val DASH_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val DASH_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val SAME_YEAR = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    private val CROSS_YEAR = DateTimeFormatter.ofPattern("yyyy年M月d日")

    /** createdAt/updatedAt 原始串 → 展示文案（空/不可解析回落原文） */
    fun display(raw: String?): String {
        val text = raw?.takeIf { it.isNotBlank() } ?: return ""
        val time = parse(text) ?: return text
        return if (time.year == LocalDate.now().year) {
            time.format(SAME_YEAR)
        } else {
            time.format(CROSS_YEAR)
        }
    }

    private fun parse(text: String): LocalDateTime? {
        runCatching { LocalDateTime.parse(text, ISO) }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(text, DASH_SECONDS) }.getOrNull()?.let { return it }
        return runCatching { LocalDateTime.parse(text, DASH_MINUTES) }.getOrNull()
    }
}
