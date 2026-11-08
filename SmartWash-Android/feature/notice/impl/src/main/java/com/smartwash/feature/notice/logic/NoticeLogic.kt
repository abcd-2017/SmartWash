package com.smartwash.feature.notice.logic

import androidx.annotation.StringRes
import com.smartwash.feature.notice.R
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * 站内信类型枚举（与后端 NoticeType 枚举名一一对应）。
 *
 * 文案映射在本模块 strings.xml（文案跟 UI 走，对齐 OrderStatus/PointsReason 的
 * descriptionRes 先例）；后端新增枚举值时 [from] 落 [UNKNOWN]，UI 回退展示通用
 * 「通知」文案，不崩不空白。
 */
enum class NoticeType(@StringRes val labelRes: Int) {
    /** 系统通知（维护/版本/安全类） */
    SYSTEM(R.string.notice_type_system),

    /** 运营公告（活动/规则类） */
    ANNOUNCEMENT(R.string.notice_type_announcement),

    /** 未收录类型（后端扩展枚举后端上未适配），回退通用文案 */
    UNKNOWN(R.string.notice_type_unknown);

    companion object {
        /** type 字符串 → 枚举（未收录/null 一律 UNKNOWN，不做异常分支） */
        fun from(code: String?): NoticeType = when (code) {
            "SYSTEM" -> SYSTEM
            "ANNOUNCEMENT" -> ANNOUNCEMENT
            else -> UNKNOWN
        }
    }
}

/** 时间展示分桶（今天/昨天/本年/更早），由 [noticeTimeParts] 计算 */
internal enum class NoticeTimeBucket { TODAY, YESTERDAY, THIS_YEAR, OLDER }

/**
 * 时间展示零件：分桶 + 供 strings.xml 格式化的原始分量。
 * 中文文案（今天/昨天/M月d日）由 UI 层经 stringResource 组装，纯逻辑不持有文案。
 */
internal data class NoticeTimeParts(
    val bucket: NoticeTimeBucket,
    /** HH:mm（今天/昨天用） */
    val time: String,
    val month: Int,
    val day: Int,
    val year: Int,
)

/** HH:mm 格式化器（线程安全，复用） */
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 站内信时间解析 + 相对分桶（ISO yyyy-MM-dd'T'HH:mm:ss，兼容历史空格分隔格式）。
 *
 * 容错：null/空白/格式漂移一律返回 null，UI 隐藏时间而非展示垃圾串。
 * [clock] 注入供 JVM 单测固定「今天」，生产默认系统默认时钟。
 */
internal fun noticeTimeParts(
    iso: String?,
    clock: Clock = Clock.systemDefaultZone(),
): NoticeTimeParts? {
    val raw = iso?.takeIf { it.isNotBlank() } ?: return null
    val parsed = try {
        LocalDateTime.parse(raw)
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(raw.replace(' ', 'T'))
        } catch (_: DateTimeParseException) {
            return null
        }
    }
    val today = LocalDate.now(clock)
    val date = parsed.toLocalDate()
    val bucket = when (ChronoUnit.DAYS.between(date, today)) {
        0L -> NoticeTimeBucket.TODAY
        1L -> NoticeTimeBucket.YESTERDAY
        else -> if (date.year == today.year) NoticeTimeBucket.THIS_YEAR else NoticeTimeBucket.OLDER
    }
    return NoticeTimeParts(
        bucket = bucket,
        time = parsed.format(TIME_FORMATTER),
        month = date.monthValue,
        day = date.dayOfMonth,
        year = date.year,
    )
}
