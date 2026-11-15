package com.smartwash.feature.review.logic

import java.time.Duration
import java.time.LocalDateTime

/**
 * 复习模块展示换算（纯 JVM 逻辑，无 Android 依赖，配套锚点单测）。
 * 到期文案只做时长量级换算，具体文案由页面按 res 映射（纯逻辑不可持 Context）。
 */
object ReviewFormat {

    /** 到期时长量级（页面按此映射「已到期 / N 分钟后到期 / N 小时后到期 / N 天后到期」） */
    sealed class DueText {
        /** 已到期（due <= now） */
        data object DueNow : DueText()

        /** 分钟级（Learning/Relearning 短步长典型量级） */
        data class InMinutes(val minutes: Long) : DueText()

        /** 小时级 */
        data class InHours(val hours: Long) : DueText()

        /** 天级（Review 间隔典型量级） */
        data class InDays(val days: Long) : DueText()

        /** due 缺失或解析失败 */
        data object Unknown : DueText()
    }

    /** ISO LocalDateTime（yyyy-MM-dd'T'HH:mm:ss）安全解析，失败返回 null */
    fun parseDateTime(text: String?): LocalDateTime? {
        if (text.isNullOrBlank()) return null
        return runCatching { LocalDateTime.parse(text) }.getOrNull()
    }

    /**
     * 到期时刻 → 时长量级：now 为基准（契约约定复习/到期判断以服务端时刻为准，
     * 客户端本地时钟仅做展示换算）。分钟以下进位为「已到期」。
     */
    fun dueTextOf(dueAt: String?, now: LocalDateTime): DueText {
        val due = parseDateTime(dueAt) ?: return DueText.Unknown
        val duration = Duration.between(now, due)
        if (!duration.isNegative) {
            val minutes = duration.toMinutes()
            if (minutes < 1) return DueText.DueNow
            if (minutes < 60) return DueText.InMinutes(minutes)
            val hours = duration.toHours()
            if (hours < 24) return DueText.InHours(hours)
            return DueText.InDays(duration.toDays())
        }
        return DueText.DueNow
    }

    /** createdAt（ISO LocalDateTime）→ 展示日期 yyyy-MM-dd，缺失/非法回落占位符 */
    fun createdDateOf(createdAt: String?, fallback: String): String {
        val date = parseDateTime(createdAt)?.toLocalDate()?.toString()
        return date ?: fallback
    }
}
