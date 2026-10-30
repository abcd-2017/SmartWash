package com.smartwash.feature.toolbox.logic

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 设备投递的枚举映射与纯逻辑（零 Android 依赖，模式对齐 ShortCodeRules）：
 * direction/kind 编码对齐契约 docs/delivery-api.md §二-2，校验与格式化为纯函数，
 * 时间一律由调用方注入保证可测。
 */
object DeliveryRules {

    private val ITEM_TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")

    /** 发布方向：1 手机→电脑（owner 发布）、2 电脑→手机（对端发布）；未知码防御为对端发布（收件方向） */
    enum class Direction(val code: Int) {
        PHONE_TO_COMPUTER(1),
        COMPUTER_TO_PHONE(2);

        companion object {
            fun fromCode(code: Int): Direction = entries.firstOrNull { it.code == code } ?: COMPUTER_TO_PHONE
        }
    }

    /** 条目类型：1 文本、2 图片、3 文件；未知码防御为文件（信息量最全的兜底展示） */
    enum class ItemKind(val code: Int) {
        TEXT(1),
        IMAGE(2),
        FILE(3);

        companion object {
            fun fromCode(code: Int): ItemKind = entries.firstOrNull { it.code == code } ?: FILE
        }
    }

    /** 会话有效期范围（契约：缺省 30，1-120 分钟） */
    const val MIN_DURATION_MINUTES = 1
    const val MAX_DURATION_MINUTES = 120
    const val DEFAULT_DURATION_MINUTES = 30

    /** 对端进入密码范围（契约：可选，4-64 位） */
    const val MIN_PASSWORD_LENGTH = 4
    const val MAX_PASSWORD_LENGTH = 64

    /** 文本条目上限（契约：10 万字符） */
    const val MAX_TEXT_LENGTH = 100_000

    /** 单文件上限（契约 20MB；端侧前置校验与后端 413 终审一致） */
    const val MAX_FILE_BYTES = 20L * 1024 * 1024

    fun isValidPassword(password: String): Boolean =
        password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH

    fun isValidDuration(minutes: Int): Boolean = minutes in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES

    /**
     * 剩余秒数 → 「mm:ss」（≥1 小时为「H:MM:SS」）；倒计时/投递码同场景开 tnum，
     * 数字变化不左右跳（规范 §2.2 硬规则）。
     */
    fun formatRemaining(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** 文件大小展示：B / KB / MB（一位小数），与条目 objectSize、单文件上限提示共用 */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1fKB", kb)
        val mb = kb / 1024
        return String.format(Locale.US, "%.1fMB", mb)
    }

    /** ISO LocalDateTime 解析（不可解析返回 null）；与 ShortCodeRules 同样按本地时区朴素处理 */
    fun parseOrNull(iso: String?): LocalDateTime? {
        val raw = iso?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDateTime.parse(raw) }.getOrNull()
    }

    /** 条目时间展示：「MM-dd HH:mm」（会话为分钟级短会话，省略年份） */
    fun formatItemTime(iso: String?): String {
        val parsed = parseOrNull(iso) ?: return iso.orEmpty()
        return parsed.format(ITEM_TIME_FORMAT)
    }

    /**
     * 过期时刻 → 剩余秒数（不足 1 秒归 0；expireAt 不可解析返回 null）。
     * 每次轮询重新锚定，页面侧滴答只做本地递减。
     */
    fun remainingSeconds(expireAt: String?, now: LocalDateTime = LocalDateTime.now()): Long? {
        val parsed = parseOrNull(expireAt) ?: return null
        return ChronoUnit.SECONDS.between(now, parsed).coerceAtLeast(0)
    }
}
