package com.smartwash.feature.subscription.logic

import com.smartwash.feature.subscription.SubscriptionCycleType
import com.smartwash.feature.subscription.SubscriptionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 订阅表单与展示的纯逻辑（零 Android 依赖）：
 * 取消网址校验对齐后端 SaveSubscription（http/https 且非空白，后端仍终审），
 * 日期解析/进度环分母为纯函数，解析失败一律返回 null 由 UI 防御降级。
 */
object SubscriptionRules {

    /** 后端日期格式：yyyy-MM-dd（nextRenewDate/trialEndDate/remindDate/usageDate） */
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 后端时间戳格式：yyyy-MM-dd HH:mm:ss（createdAt/updatedAt） */
    private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val CANCEL_URL_REGEX = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

    fun isValidCancelUrl(url: String): Boolean = CANCEL_URL_REGEX.matches(url.trim())

    fun parseDate(raw: String?): LocalDate? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDate.parse(text, DATE_FORMAT) }.getOrNull()
    }

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)

    fun parseDateTime(raw: String?): LocalDateTime? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDateTime.parse(text, DATE_TIME_FORMAT) }.getOrNull()
    }

    /** 后端时间戳 → 「yyyy-MM-dd HH:mm」展示；不可解析原样返回 */
    fun formatDisplayDateTime(raw: String?): String {
        val parsed = parseDateTime(raw) ?: return raw.orEmpty()
        return parsed.format(DISPLAY_FORMAT)
    }

    /** DatePicker 的 UTC 毫秒 → LocalDate（DatePicker 内部按 UTC 取整日） */
    fun localDateFromPickerMillis(millis: Long?): LocalDate? {
        return millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    }

    /** 金额两位小数（amount 为 8,2 定点，直接 %.2f；负值不可能出现——后端校验 >0） */
    fun formatAmount(amount: Double): String = String.format(Locale.CHINA, "%.2f", amount)

    /**
     * 当期周期总天数（进度环分母）：从下个续费日按周期三元组回退一期，
     * 与后端 SubscriptionRenewJob 的推进方向互逆（月付 plusMonths/年付 plusYears 的 clamp 行为一致）。
     */
    fun cycleTotalDays(cycleType: SubscriptionCycleType, interval: Int, nextRenewDate: LocalDate): Int {
        val previous = when (cycleType) {
            SubscriptionCycleType.MONTH -> nextRenewDate.minusMonths(interval.toLong())
            SubscriptionCycleType.YEAR -> nextRenewDate.minusYears(interval.toLong())
        }
        val days = ChronoUnit.DAYS.between(previous, nextRenewDate)
        return if (days > 0) days.toInt() else 1
    }

    /**
     * 续费周期进度 0f..1f（进度环填充度）：已消耗天数 / 当期总天数。
     * daysUntilRenew 为负（已过期待推进）视为走满 1f；输入不可解析返回 null（UI 隐藏环）。
     */
    fun renewProgressFraction(
        cycleType: SubscriptionCycleType,
        interval: Int,
        nextRenewDate: String?,
        daysUntilRenew: Int?,
    ): Float? {
        val renewDate = parseDate(nextRenewDate) ?: return null
        val daysLeft = daysUntilRenew ?: return null
        val total = cycleTotalDays(cycleType, interval, renewDate).coerceAtLeast(1)
        val consumed = total - daysLeft
        return (consumed.toFloat() / total).coerceIn(0f, 1f)
    }

    /** 终态（canceled/finished）只读判断：UI 显隐编辑/打卡入口 */
    fun isTerminal(raw: String?): Boolean = SubscriptionStatus.fromRawOrDefault(raw).isTerminal
}
