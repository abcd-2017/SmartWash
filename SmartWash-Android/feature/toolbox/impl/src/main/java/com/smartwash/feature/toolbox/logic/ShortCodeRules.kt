package com.smartwash.feature.toolbox.logic

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.smartwash.feature.toolbox.api.ToolboxConstant

/**
 * 短链表单与展示的纯逻辑（零 Android 依赖，JVM 单测锚点）：
 * target/别名校验规则对齐后端 AddShortCodeFrom（方案 §5.1/§5.5），
 * 过期计算与展示映射为纯函数，时间一律由调用方注入保证可测。
 */
object ShortCodeRules {

    /** 自定义别名：4-16 位字母数字及 -_（对齐后端 ShortCodeGenerator.CUSTOM_CODE） */
    val CUSTOM_CODE_REGEX = Regex("^[0-9a-zA-Z-_]{4,16}$")

    /** 目标链接：必须 http/https 且非空白（防开放重定向的端上前置校验，后端仍终审） */
    private val TARGET_REGEX = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

    /** 入参/展示格式：ISO LocalDateTime，恒含秒（避免 ISO_LOCAL_DATE_TIME 省略秒的歧义） */
    private val REQUEST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun isValidTarget(target: String): Boolean = TARGET_REGEX.matches(target.trim())

    fun isValidCustomCode(code: String): Boolean = CUSTOM_CODE_REGEX.matches(code)

    /** 过期预设（永久 / 7 天 / 30 天 / 自定义日期），M1 更新侧仅暴露 7/30/自定义 */
    enum class ExpiryPreset(val days: Int?) {
        PERMANENT(null),
        DAYS_7(7),
        DAYS_30(30),
        CUSTOM(Int.MIN_VALUE),
    }

    /**
     * 预设 → 过期时刻 ISO 串：固定时刻取 now+n天，自定义取所选日期当天 23:59:59（过期语义为「当天可用完」）。
     * 永久 → null（不传 = 永久）；自定义未选日期 → null（UI 侧须先校验日期已选）。
     */
    fun expireAtFor(preset: ExpiryPreset, customDate: LocalDate?, now: LocalDateTime): String? {
        return when (preset) {
            ExpiryPreset.PERMANENT -> null
            ExpiryPreset.DAYS_7 -> now.plusDays(7).format(REQUEST_FORMAT)
            ExpiryPreset.DAYS_30 -> now.plusDays(30).format(REQUEST_FORMAT)
            ExpiryPreset.CUSTOM -> customDate?.atTime(23, 59, 59)?.format(REQUEST_FORMAT)
        }
    }

    /** 私密文本时效预设（1 小时 / 1 天 / 7 天）——阅后即焚必须有终点，无「永久」 */
    enum class SecretExpiryPreset(val hours: Long) {
        HOURS_1(1),
        DAYS_1(24),
        DAYS_7(168),
    }

    /** 私密文本预设 → 过期时刻 ISO 串（now + n 小时，对齐后端 expireAt 必填语义） */
    fun secretExpireAtFor(preset: SecretExpiryPreset, now: LocalDateTime): String =
        now.plusHours(preset.hours).format(REQUEST_FORMAT)

    /** DatePicker 的 UTC 毫秒 → LocalDate（DatePicker 内部按 UTC 取整日） */
    fun localDateFromPickerMillis(millis: Long?): LocalDate? {
        return millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    }

    /**
     * 自定义过期时刻（所选日 23:59:59）是否早于 now——日期选择器已限今日起选，
     * 提交前再拦一次（防深链/状态残留把过去日期带进提交，产生「生来已过期」短码）。
     */
    fun isCustomExpiryInPast(customDate: LocalDate, now: LocalDateTime): Boolean =
        customDate.atTime(23, 59, 59).isBefore(now)

    /** 过期时刻展示态：永久（null/不可解析防御为永久）/ 有效中 / 已过期 */
    sealed interface ExpiryState {
        data object Permanent : ExpiryState
        data class Active(val expireAt: String) : ExpiryState
        data object Expired : ExpiryState
    }

    fun expiryStateOf(expireAt: String?, now: LocalDateTime): ExpiryState {
        val parsed = parseOrNull(expireAt) ?: return ExpiryState.Permanent
        return if (parsed.isBefore(now)) ExpiryState.Expired else ExpiryState.Active(expireAt.orEmpty())
    }

    /** ISO LocalDateTime → 「yyyy-MM-dd HH:mm」展示；不可解析原样返回 */
    fun formatDisplayDateTime(iso: String?): String {
        val parsed = parseOrNull(iso) ?: return iso.orEmpty()
        return parsed.format(DISPLAY_FORMAT)
    }

    /** LocalDate → 「yyyy-MM-dd」（自定义过期日期按钮的已选展示） */
    fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)

    /** 可见性映射：非 2（私有）一律按公开处理（对齐后端 1公开/2私有） */
    fun isPublicVisibility(isPublic: Int): Boolean = isPublic != ToolboxConstant.VISIBILITY_PRIVATE

    // ==================== 私密文本（阅后即焚，contentType=7） ====================

    /** 内容类型：私密文本（阅后即焚），对齐后端 ShortCodeContentType.SECRET_TEXT */
    const val CONTENT_TYPE_SECRET_TEXT = 7

    /** 正文上限（对齐后端 AddShortCodeFrom @Size，2000 字符） */
    const val SECRET_MAX_CHARS = 2000

    /** 口令位数（对齐后端 @Size(min=4, max=32)） */
    const val SECRET_PASSWORD_MIN = 4
    const val SECRET_PASSWORD_MAX = 32

    /** 私密文本状态（对齐后端 SecretState：ACTIVE/BURNED/EXPIRED） */
    sealed interface SecretState {
        /** 未领取：内容可被一次性揭示 */
        data object Active : SecretState
        /** 已焚毁：已被成功揭示，内容不可找回 */
        data object Burned : SecretState
        /** 已过期：到期未领取，内容不可找回 */
        data object Expired : SecretState
        /** 未知值兜底（后端新增状态时前端兜底展示） */
        data object Unknown : SecretState
    }

    /** 后端 secretState 字符串 → 展示态（未知值兜底 Unknown，对齐端上白名单惯例） */
    fun secretStateOf(state: String?): SecretState = when (state) {
        "ACTIVE" -> SecretState.Active
        "BURNED" -> SecretState.Burned
        "EXPIRED" -> SecretState.Expired
        else -> SecretState.Unknown
    }

    /** 私密文本正文端上前置校验（后端终审）：非空 ≤2000 字符 */
    fun isValidSecretContent(content: String): Boolean =
        content.isNotBlank() && content.length <= SECRET_MAX_CHARS

    /** 口令校验（创建侧前置，可空 = 不设口令）：4-32 位 */
    fun isValidSecretPassword(password: String): Boolean =
        password.length in SECRET_PASSWORD_MIN..SECRET_PASSWORD_MAX

    private fun parseOrNull(expireAt: String?): LocalDateTime? {
        val raw = expireAt?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDateTime.parse(raw) }.getOrNull()
    }
}
